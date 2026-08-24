package de.caritas.cob.agencyservice.api.admin.service.legal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import de.caritas.cob.agencyservice.api.admin.service.UserAdminService;
import de.caritas.cob.agencyservice.api.exception.httpresponses.AgencyAccessDeniedException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.BadRequestException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.InternalServerErrorException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopicRepository;
import de.caritas.cob.agencyservice.api.repository.agencytopic.PublicationStatus;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalText;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextLevel;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import de.caritas.cob.agencyservice.api.validation.InputSanitizer;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes a department's ({@code Fachbereich} = agency × topic) own data privacy policy (DPP).
 *
 * <p>This is the AgencyService counterpart to the TenantService DPA publish flow: the admin authors
 * multilingual HTML in the panel's editor; here it is (a) authorised against the caller's agencies
 * so a restricted agency admin cannot write another agency's Fachbereich (IDOR guard), (b) OWASP
 * sanitised per translation, and (c) stored as a JSON language→HTML map in {@code
 * agency_topic.content_dpp} — mirroring how the tenant privacy/DPA content is persisted so the admin
 * UI can reuse the same language-aware viewer.
 *
 * <p>Like {@link DepartmentImprintService}, {@code __meta}-suffixed keys (the platform-wide
 * translation-metadata convention) carry JSON, not HTML: running them through the HTML sanitizer
 * would corrupt the payload (quotes become entities → invalid JSON), so they are passed through
 * unsanitised. Because this metadata is stored verbatim and later served publicly, it is validated
 * against a strict schema (see {@link LegalContentSanitizer}) rather than a mere parse check, so no
 * unsanitised free text can hide behind the suffix.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DepartmentDataProtectionService {

  private final @NonNull AgencyTopicRepository agencyTopicRepository;
  private final @NonNull LegalContentSanitizer legalContentSanitizer;
  private final @NonNull AuthenticatedUser authenticatedUser;
  private final @NonNull UserAdminService userAdminService;
  private final @NonNull LegalTextVersionService legalTextVersionService;
  private final @NonNull ConsentTextService consentTextService;

  /**
   * Sanitises and stores the department's DPP for the given agency × topic. When {@code publish} is
   * true the department is marked {@link PublicationStatus#PUBLISHED}, otherwise it is kept as a
   * {@link PublicationStatus#DRAFT} (draft-save).
   *
   * @return the resulting publication status
   */
  @Transactional
  public PublicationStatus publishDepartmentDataPrivacy(
      Long agencyId,
      Long topicId,
      Map<String, String> content,
      Map<String, String> consentText,
      boolean publish) {
    assertRestrictedAdminOwnsAgency(agencyId);

    AgencyTopic department =
        agencyTopicRepository
            .findByAgency_IdAndTopicId(agencyId, topicId)
            .orElseThrow(NotFoundException::new);

    assertCallerTenantMatches(department.getAgency());

    var sanitizedJson = legalContentSanitizer.sanitizeToJson(content);
    // ADR-021 decision 2: validated BEFORE anything is written, so a consent text missing the
    // mandatory token leaves the stored document untouched instead of half-updating it.
    // "Absent keeps": an Admin build that predates this field must not wipe the Träger's sentence
    // by publishing a policy body (see ConsentTextService#resolveForUpdate).
    var sanitizedConsent =
        consentTextService.resolveForUpdate(department.getConsentText(), consentText, publish);
    final var wasPublished = department.getPublicationStatus() == PublicationStatus.PUBLISHED;
    final var status = publish ? PublicationStatus.PUBLISHED : PublicationStatus.DRAFT;

    // ADR-014 amendment 2026-07-28: never write through to the referenced shared object. The 0026
    // backfill merged byte-identical departments onto one row, so writing through would silently
    // republish every sibling Fachbereich. Publishing means "this department leaves the shared
    // text": the reference is dropped and the text becomes the department's own. A draft keeps the
    // reference, so the public still resolves the inherited text until the admin publishes.
    if (publish) {
      department.setDpp(null);
    }
    department.setContentDpp(sanitizedJson);
    department.setConsentText(sanitizedConsent);
    department.setPublicationStatus(status);
    department.setUpdateDate(LocalDateTime.now());
    agencyTopicRepository.save(department);

    // ADR-021 decision 3: only a real publish snapshots the wording. A draft-save deliberately
    // writes no version — that is the #212 distinction update_date could never make.
    if (publish) {
      legalTextVersionService.recordPublication(
          LegalTextLevel.DEPARTMENT,
          department.getId(),
          LegalTextKind.DPP,
          department.getAgency() == null ? null : department.getAgency().getTenantId(),
          sanitizedJson,
          sanitizedConsent);
    } else if (wasPublished) {
      // Withdrawing a published policy back to DRAFT is not a new version, but the old one has
      // stopped applying — leaving it open would have the history claim it is still in force.
      legalTextVersionService.supersedeCurrent(
          LegalTextLevel.DEPARTMENT, department.getId(), LegalTextKind.DPP);
    }

    return status;
  }

  /**
   * Reads the department's stored DPP (content + status) to prefill the admin editor. Same
   * authorisation as the write path: restricted admins scoped to their own agencies plus the
   * cross-tenant guard.
   */
  @Transactional(readOnly = true)
  public DepartmentDataProtectionView getDepartmentDataPrivacy(Long agencyId, Long topicId) {
    assertRestrictedAdminOwnsAgency(agencyId);

    AgencyTopic department =
        agencyTopicRepository
            .findByAgency_IdAndTopicId(agencyId, topicId)
            .orElseThrow(NotFoundException::new);

    assertCallerTenantMatches(department.getAgency());

    LegalText referenced = department.getDpp();
    if (referenced != null && !hasPendingDraft(department, referenced)) {
      return new DepartmentDataProtectionView(
          referenced.getContent(), referenced.getConsentText(), referenced.getPublicationStatus());
    }
    return new DepartmentDataProtectionView(
        department.getContentDpp(), department.getConsentText(), department.getPublicationStatus());
  }

  /**
   * Tells an admin's unpublished draft apart from a {@code 0026} backfill leftover, both of which
   * appear as inline content next to a live reference.
   *
   * <p>The backfill copied the inline text into the shared object <em>verbatim</em> and left the
   * column as a rollback anchor, so a leftover is byte-identical to what it references and must
   * keep resolving through the reference. A draft is inline content that differs from the
   * referenced text and is still {@code DRAFT} — the editor has to show the admin their own
   * unfinished work, while the public keeps seeing the inherited text.
   */
  private boolean hasPendingDraft(AgencyTopic department, LegalText referenced) {
    var draft = department.getContentDpp();
    return draft != null
        && department.getPublicationStatus() == PublicationStatus.DRAFT
        && !draft.equals(referenced.getContent());
  }

  /**
   * Restricted agency admins may only touch agencies they administer (mirrors {@code
   * AgencyUpdatePermissionValidator}). Full agency admins are handled by the tenant guard below.
   */
  private void assertRestrictedAdminOwnsAgency(Long agencyId) {
    if (authenticatedUser.hasRestrictedAgencyPriviliges()) {
      var adminAgencyIds = userAdminService.getAdminUserAgencyIds(authenticatedUser.requireUserId());
      if (adminAgencyIds == null || !adminAgencyIds.contains(agencyId)) {
        log.warn(
            "Admin user {} may not edit the data privacy policy of agency {}",
            authenticatedUser.requireUserId(),
            agencyId);
        throw new AgencyAccessDeniedException();
      }
    }
  }

  /**
   * Cross-tenant guard (mirrors {@code AgencyTenantValidator}): a full agency admin of tenant A must
   * not edit tenant B's Fachbereich. Necessary because the Hibernate tenant filter is not installed
   * when {@code multitenancy.enabled=false} (all deployed profiles), so agency-id membership alone
   * does not scope full admins. Tenant {@code 0} (super/technical) and single-tenant mode (no tenant
   * in context) are unrestricted.
   */
  private void assertCallerTenantMatches(Agency agency) {
    Long effectiveTenantId = resolveEffectiveTenantId();
    if (effectiveTenantId == null || effectiveTenantId.equals(0L)) {
      return;
    }
    if (agency == null || !effectiveTenantId.equals(agency.getTenantId())) {
      log.warn(
          "Admin user {} (tenant {}) may not edit the data privacy policy of agency {} (tenant {})",
          authenticatedUser.requireUserId(),
          effectiveTenantId,
          agency == null ? null : agency.getId(),
          agency == null ? null : agency.getTenantId());
      throw new AgencyAccessDeniedException();
    }
  }

  private Long resolveEffectiveTenantId() {
    Long tenantIdFromAuth = authenticatedUser.getTenantId();
    return tenantIdFromAuth != null ? tenantIdFromAuth : TenantContext.getCurrentTenant();
  }
}
