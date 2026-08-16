package de.caritas.cob.agencyservice.api.admin.service.legal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Publishes a department's ({@code Fachbereich} = agency × topic) own imprint (Impressum).
 *
 * <p>Mirrors {@link DepartmentDataProtectionService}, ADR-003's DPP half: the admin authors
 * multilingual HTML in the panel's editor; here it is (a) authorised against the caller's agencies
 * so a restricted agency admin cannot write another agency's Fachbereich (IDOR guard), (b) OWASP
 * sanitised per translation, and (c) stored as a JSON language→HTML map in {@code
 * agency_topic.content_imprint} with its own draft/published lifecycle, independent of the DPP's.
 *
 * <p>{@code __meta}-suffixed keys are handled by the shared {@link LegalContentSanitizer}: passed
 * through unsanitised but validated against the strict metadata schema — the former lenient
 * "parses as JSON" check allowed arbitrary unsanitised payloads behind the suffix.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DepartmentImprintService {

  private final @NonNull AgencyTopicRepository agencyTopicRepository;
  private final @NonNull LegalContentSanitizer legalContentSanitizer;
  private final @NonNull AuthenticatedUser authenticatedUser;
  private final @NonNull UserAdminService userAdminService;
  private final @NonNull LegalTextVersionService legalTextVersionService;

  /**
   * Sanitises and stores the department's imprint for the given agency × topic. When {@code
   * publish} is true the imprint is marked {@link PublicationStatus#PUBLISHED}, otherwise it is
   * kept as a {@link PublicationStatus#DRAFT} (draft-save).
   *
   * @return the resulting publication status
   */
  @Transactional
  public PublicationStatus publishDepartmentImprint(
      Long agencyId, Long topicId, Map<String, String> content, boolean publish) {
    assertRestrictedAdminOwnsAgency(agencyId);

    AgencyTopic department =
        agencyTopicRepository
            .findByAgency_IdAndTopicId(agencyId, topicId)
            .orElseThrow(NotFoundException::new);

    assertCallerTenantMatches(department.getAgency());

    var sanitizedJson = legalContentSanitizer.sanitizeToJson(content);
    final var wasPublished =
        department.getPublicationStatusImprint() == PublicationStatus.PUBLISHED;
    var status = publish ? PublicationStatus.PUBLISHED : PublicationStatus.DRAFT;

    // ADR-014 amendment 2026-07-28: never write through to the referenced shared object. The 0026
    // backfill merged byte-identical departments onto one row, so writing through would silently
    // republish every sibling Fachbereich. Publishing means "this department leaves the shared
    // text": the reference is dropped and the text becomes the department's own. A draft keeps the
    // reference, so the public still resolves the inherited text until the admin publishes.
    if (publish) {
      department.setImprint(null);
    }
    department.setContentImprint(sanitizedJson);
    department.setPublicationStatusImprint(status);
    department.setUpdateDate(LocalDateTime.now());
    agencyTopicRepository.save(department);

    // ADR-021 decision 3: the imprint gets the same history as the DPP. It is an information duty,
    // never a consent gate (decision 7), but "which imprint was reachable when" is still a question
    // the platform has to be able to answer.
    if (publish) {
      legalTextVersionService.recordPublication(
          LegalTextLevel.DEPARTMENT,
          department.getId(),
          LegalTextKind.IMPRINT,
          department.getAgency() == null ? null : department.getAgency().getTenantId(),
          sanitizedJson,
          // An imprint is an information duty, never consent-bearing (ADR-021 decision 7).
          null);
    } else if (wasPublished) {
      legalTextVersionService.supersedeCurrent(
          LegalTextLevel.DEPARTMENT, department.getId(), LegalTextKind.IMPRINT);
    }

    return status;
  }

  /**
   * Reads the department's stored imprint (content + status) to prefill the admin editor. Same
   * authorisation as the write path: restricted admins scoped to their own agencies plus the
   * cross-tenant guard.
   */
  @Transactional(readOnly = true)
  public DepartmentImprintView getDepartmentImprint(Long agencyId, Long topicId) {
    assertRestrictedAdminOwnsAgency(agencyId);

    AgencyTopic department =
        agencyTopicRepository
            .findByAgency_IdAndTopicId(agencyId, topicId)
            .orElseThrow(NotFoundException::new);

    assertCallerTenantMatches(department.getAgency());

    LegalText referenced = department.getImprint();
    if (referenced != null && !hasPendingDraft(department, referenced)) {
      return new DepartmentImprintView(
          referenced.getContent(), referenced.getPublicationStatus());
    }
    return new DepartmentImprintView(
        department.getContentImprint(), department.getPublicationStatusImprint());
  }

  /**
   * Tells an admin's unpublished draft apart from a {@code 0026} backfill leftover, both of which
   * appear as inline content next to a live reference. Mirrors {@code
   * DepartmentDataProtectionService#hasPendingDraft}: the backfill copied the text verbatim, so a
   * leftover is byte-identical to what it references and keeps resolving through the reference.
   */
  private boolean hasPendingDraft(AgencyTopic department, LegalText referenced) {
    var draft = department.getContentImprint();
    return draft != null
        && department.getPublicationStatusImprint() == PublicationStatus.DRAFT
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
            "Admin user {} may not edit the imprint of agency {}",
            authenticatedUser.requireUserId(),
            agencyId);
        throw new AgencyAccessDeniedException();
      }
    }
  }

  /**
   * Cross-tenant guard (mirrors {@code AgencyTenantValidator}): a full agency admin of tenant A
   * must not edit tenant B's Fachbereich. Necessary because the Hibernate tenant filter is not
   * installed when {@code multitenancy.enabled=false} (all deployed profiles), so agency-id
   * membership alone does not scope full admins. Tenant {@code 0} (super/technical) and
   * single-tenant mode (no tenant in context) are unrestricted.
   */
  private void assertCallerTenantMatches(Agency agency) {
    Long effectiveTenantId = resolveEffectiveTenantId();
    if (effectiveTenantId == null || effectiveTenantId.equals(0L)) {
      return;
    }
    if (agency == null || !effectiveTenantId.equals(agency.getTenantId())) {
      log.warn(
          "Admin user {} (tenant {}) may not edit the imprint of agency {} (tenant {})",
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
