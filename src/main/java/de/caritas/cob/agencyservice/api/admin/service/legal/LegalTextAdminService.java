package de.caritas.cob.agencyservice.api.admin.service.legal;

import de.caritas.cob.agencyservice.api.admin.service.UserAdminService;
import de.caritas.cob.agencyservice.api.exception.httpresponses.AgencyAccessDeniedException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.BadRequestException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopicRepository;
import de.caritas.cob.agencyservice.api.repository.agencytopic.PublicationStatus;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalText;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextLevel;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextRepository;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR-014 legal-text library: tenant-scoped CRUD over the shared {@link LegalText} objects plus
 * the per-department assignment. A department may share a tenant-wide text, carry its own, or stay
 * unassigned (= it falls back to its own inline {@code content_dpp}/{@code content_imprint};
 * tenant-level fallback is future work, ADR-014 / #136).
 *
 * <p>Sanitisation mirrors {@link DepartmentDataProtectionService} (the strict variant):
 * multilingual HTML is OWASP-sanitised per translation, {@code __meta}-suffixed keys carry
 * translation metadata as JSON and are schema-validated instead of sanitised.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LegalTextAdminService {

  private final @NonNull LegalTextRepository legalTextRepository;
  private final @NonNull AgencyTopicRepository agencyTopicRepository;
  private final @NonNull LegalContentSanitizer legalContentSanitizer;
  private final @NonNull AuthenticatedUser authenticatedUser;
  private final @NonNull UserAdminService userAdminService;
  private final @NonNull LegalTextVersionService legalTextVersionService;

  /**
   * Lists the caller tenant's legal texts of one kind, each with its department usage count.
   *
   * <p>Library CRUD is full-agency-admin only: a shared text applies to every referencing
   * department, so a restricted admin (scoped to specific agencies) must not read or change
   * tenant-wide legal content. Restricted admins keep the per-department publish endpoints and the
   * agency-scoped assignment below.
   */
  @Transactional(readOnly = true)
  public List<LegalTextAdminView> listLegalTexts(LegalTextKind kind) {
    assertFullAgencyAdmin();
    Long tenantId = resolveEffectiveTenantId();
    List<LegalText> texts =
        isUnrestricted(tenantId)
            ? legalTextRepository.findByKindOrderByLabelAsc(kind)
            : legalTextRepository.findByTenantIdAndKindOrderByLabelAsc(tenantId, kind);
    return texts.stream().map(this::toView).toList();
  }

  /** Creates a legal text owned by the caller's tenant (full agency admins only). */
  @Transactional
  public LegalTextAdminView createLegalText(
      LegalTextKind kind, String label, Map<String, String> content, boolean publish) {
    assertFullAgencyAdmin();
    assertValidLabel(label);
    var now = LocalDateTime.now();
    var text =
        LegalText.builder()
            .tenantId(resolveEffectiveTenantId())
            .kind(kind)
            .label(label)
            .content(legalContentSanitizer.sanitizeToJson(content))
            .publicationStatus(publish ? PublicationStatus.PUBLISHED : PublicationStatus.DRAFT)
            .createDate(now)
            .updateDate(now)
            .build();
    var saved = legalTextRepository.save(text);
    recordPublicationIfPublished(saved, publish);
    return toView(saved);
  }

  /**
   * Updates label, content and (optionally) publication status of a caller-tenant text (full
   * agency admins only). A {@code null} publish flag preserves the current publication status — a
   * label/content-only update must never silently unpublish a published text.
   */
  @Transactional
  public LegalTextAdminView updateLegalText(
      Long legalTextId, String label, Map<String, String> content, Boolean publish) {
    assertFullAgencyAdmin();
    LegalText text =
        legalTextRepository.findById(legalTextId).orElseThrow(NotFoundException::new);
    assertCallerTenantOwnsText(text);
    assertValidLabel(label);

    text.setLabel(label);
    text.setContent(legalContentSanitizer.sanitizeToJson(content));
    if (publish != null) {
      text.setPublicationStatus(publish ? PublicationStatus.PUBLISHED : PublicationStatus.DRAFT);
    }
    text.setUpdateDate(LocalDateTime.now());
    var saved = legalTextRepository.save(text);
    recordPublicationIfPublished(saved, saved.getPublicationStatus() == PublicationStatus.PUBLISHED);
    return toView(saved);
  }

  /**
   * ADR-021 decision 3 for the ADR-014 shared objects. A shared text is the document in force for
   * every department referencing it, so its wording needs the same provable history as an inline
   * one; leaving it out would mean a Träger loses its evidence precisely by doing the tidy thing
   * and maintaining one document instead of N copies.
   *
   * <p>An update that leaves the text {@code DRAFT} records nothing, and re-saving an unchanged
   * published wording is deduplicated inside {@link LegalTextVersionService}.
   */
  private void recordPublicationIfPublished(LegalText text, boolean published) {
    if (published) {
      legalTextVersionService.recordPublication(
          LegalTextLevel.SHARED,
          text.getId(),
          text.getKind(),
          text.getTenantId(),
          text.getContent());
    }
  }

  /**
   * Library CRUD guard: shared texts apply tenant-wide, so restricted agency admins (scoped to
   * specific agencies) are rejected here and use the per-department endpoints instead.
   */
  private void assertFullAgencyAdmin() {
    if (authenticatedUser.hasRestrictedAgencyPriviliges()) {
      log.warn(
          "Restricted admin user {} may not manage the tenant-wide legal-text library",
          authenticatedUser.requireUserId());
      throw new AgencyAccessDeniedException();
    }
  }

  /**
   * Assigns a shared legal text to a department's DPP or imprint slot, or clears the slot when
   * {@code legalTextId} is {@code null} (the department falls back to its own inline {@code
   * content_dpp}/{@code content_imprint}; tenant-level fallback is future work, ADR-014 / #136).
   *
   * <p>Guards: the restricted-admin IDOR check and the cross-tenant agency guard mirror the
   * department publish endpoints; additionally the text's kind must match the slot and the text
   * must belong to the agency's tenant.
   */
  @Transactional
  public void assignDepartmentLegalText(
      Long agencyId, Long topicId, LegalTextKind kind, Long legalTextId) {
    assertRestrictedAdminOwnsAgency(agencyId);

    AgencyTopic department =
        agencyTopicRepository
            .findByAgency_IdAndTopicId(agencyId, topicId)
            .orElseThrow(NotFoundException::new);

    assertCallerTenantMatches(department.getAgency());

    LegalText text = null;
    if (legalTextId != null) {
      text = legalTextRepository.findById(legalTextId).orElseThrow(NotFoundException::new);
      if (text.getKind() != kind) {
        throw new BadRequestException(
            String.format(
                "Legal text %d has kind %s and cannot be assigned to the %s slot",
                legalTextId, text.getKind(), kind));
      }
      assertTextBelongsToAgencyTenant(text, department.getAgency());
    }

    if (kind == LegalTextKind.DPP) {
      department.setDpp(text);
    } else {
      department.setImprint(text);
    }
    department.setUpdateDate(LocalDateTime.now());
    agencyTopicRepository.save(department);
  }

  private LegalTextAdminView toView(LegalText text) {
    long usage =
        text.getId() == null
            ? 0L
            : (text.getKind() == LegalTextKind.DPP
                ? agencyTopicRepository.countByDpp_Id(text.getId())
                : agencyTopicRepository.countByImprint_Id(text.getId()));
    return new LegalTextAdminView(
        text.getId(),
        text.getKind(),
        text.getLabel(),
        text.getContent(),
        text.getPublicationStatus(),
        usage);
  }

  private void assertValidLabel(String label) {
    if (label == null || label.isBlank()) {
      throw new BadRequestException("Legal text label must not be blank");
    }
  }

  /** A text may only be edited by its owning tenant (technical tenant 0 is unrestricted). */
  private void assertCallerTenantOwnsText(LegalText text) {
    Long effectiveTenantId = resolveEffectiveTenantId();
    if (isUnrestricted(effectiveTenantId)) {
      return;
    }
    if (!effectiveTenantId.equals(text.getTenantId())) {
      log.warn(
          "Admin user {} (tenant {}) may not edit legal text {} (tenant {})",
          authenticatedUser.requireUserId(),
          effectiveTenantId,
          text.getId(),
          text.getTenantId());
      throw new AgencyAccessDeniedException();
    }
  }

  /** A department must never reference another Träger's document. */
  private void assertTextBelongsToAgencyTenant(LegalText text, Agency agency) {
    Long agencyTenant = agency == null ? null : agency.getTenantId();
    // Single-tenant / technical agencies (tenant null or 0) carry no cross-tenant risk.
    if (isUnrestricted(agencyTenant)) {
      return;
    }
    // A tenant-scoped agency may only reference a text of its own Träger. A null-tenant
    // (global/orphan) or different-tenant text is rejected — a null tenant must not bypass the
    // cross-tenant guard and leak into a specific Träger's department in multi-tenant mode.
    Long textTenant = text.getTenantId();
    if (!agencyTenant.equals(textTenant)) {
      throw new BadRequestException(
          String.format(
              "Legal text %d belongs to tenant %s, not to the agency's tenant %d",
              text.getId(), textTenant, agencyTenant));
    }
  }

  /**
   * Restricted agency admins may only touch agencies they administer (mirrors {@code
   * DepartmentDataProtectionService}).
   */
  private void assertRestrictedAdminOwnsAgency(Long agencyId) {
    if (authenticatedUser.hasRestrictedAgencyPriviliges()) {
      var adminAgencyIds = userAdminService.getAdminUserAgencyIds(authenticatedUser.requireUserId());
      if (adminAgencyIds == null || !adminAgencyIds.contains(agencyId)) {
        log.warn(
            "Admin user {} may not assign legal texts of agency {}",
            authenticatedUser.requireUserId(),
            agencyId);
        throw new AgencyAccessDeniedException();
      }
    }
  }

  /** Cross-tenant guard, mirrors the department publish endpoints. */
  private void assertCallerTenantMatches(Agency agency) {
    Long effectiveTenantId = resolveEffectiveTenantId();
    if (isUnrestricted(effectiveTenantId)) {
      return;
    }
    if (agency == null || !effectiveTenantId.equals(agency.getTenantId())) {
      log.warn(
          "Admin user {} (tenant {}) may not assign legal texts of agency {} (tenant {})",
          authenticatedUser.requireUserId(),
          effectiveTenantId,
          agency == null ? null : agency.getId(),
          agency == null ? null : agency.getTenantId());
      throw new AgencyAccessDeniedException();
    }
  }

  private boolean isUnrestricted(Long effectiveTenantId) {
    return effectiveTenantId == null || effectiveTenantId.equals(0L);
  }

  private Long resolveEffectiveTenantId() {
    Long tenantIdFromAuth = authenticatedUser.getTenantId();
    return tenantIdFromAuth != null ? tenantIdFromAuth : TenantContext.getCurrentTenant();
  }
}
