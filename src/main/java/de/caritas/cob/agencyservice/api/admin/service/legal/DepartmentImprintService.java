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
 * <p>One deviation from the DPP twin: {@code __meta}-suffixed keys (the platform-wide
 * translation-metadata convention) carry JSON, not HTML. They are passed through unsanitised —
 * running them through the HTML sanitizer would destroy the payload — but validated to be
 * well-formed JSON so no unsanitised free text can hide behind the suffix.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DepartmentImprintService {

  private static final String META_KEY_SUFFIX = "__meta";

  private final @NonNull AgencyTopicRepository agencyTopicRepository;
  private final @NonNull InputSanitizer inputSanitizer;
  private final @NonNull AuthenticatedUser authenticatedUser;
  private final @NonNull UserAdminService userAdminService;

  private final ObjectMapper objectMapper = new ObjectMapper();

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

    var sanitizedJson = toJson(sanitizeTranslations(content));
    var status = publish ? PublicationStatus.PUBLISHED : PublicationStatus.DRAFT;
    var now = LocalDateTime.now();

    LegalText referenced = department.getImprint();
    if (referenced != null) {
      // ADR-014: the referenced shared object is the truth — write through to it; the inline
      // column stays untouched (the read path ignores it once a reference exists).
      referenced.setContent(sanitizedJson);
      referenced.setPublicationStatus(status);
      referenced.setUpdateDate(now);
    } else {
      department.setContentImprint(sanitizedJson);
      department.setPublicationStatusImprint(status);
    }
    department.setUpdateDate(now);
    agencyTopicRepository.save(department);

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
    if (referenced != null) {
      return new DepartmentImprintView(
          referenced.getContent(), referenced.getPublicationStatus());
    }
    return new DepartmentImprintView(
        department.getContentImprint(), department.getPublicationStatusImprint());
  }

  /**
   * Restricted agency admins may only touch agencies they administer (mirrors {@code
   * AgencyUpdatePermissionValidator}). Full agency admins are handled by the tenant guard below.
   */
  private void assertRestrictedAdminOwnsAgency(Long agencyId) {
    if (authenticatedUser.hasRestrictedAgencyPriviliges()) {
      var adminAgencyIds = userAdminService.getAdminUserAgencyIds(authenticatedUser.getUserId());
      if (adminAgencyIds == null || !adminAgencyIds.contains(agencyId)) {
        log.warn(
            "Admin user {} may not edit the imprint of agency {}",
            authenticatedUser.getUserId(),
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
          authenticatedUser.getUserId(),
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

  private Map<String, String> sanitizeTranslations(Map<String, String> content) {
    if (content == null) {
      return Map.of();
    }
    return content.entrySet().stream()
        .filter(entry -> entry.getKey() != null)
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                entry -> sanitizeOrValidateValue(entry.getKey(), entry.getValue()),
                (existing, replacement) -> replacement,
                LinkedHashMap::new));
  }

  private String sanitizeOrValidateValue(String key, String value) {
    var safeValue = value == null ? "" : value;
    if (key.endsWith(META_KEY_SUFFIX)) {
      assertValidJson(key, safeValue);
      return safeValue;
    }
    return inputSanitizer.sanitizeAllowingFormattingAndLinks(safeValue);
  }

  private void assertValidJson(String key, String value) {
    try {
      objectMapper.readTree(value);
    } catch (JsonProcessingException e) {
      throw new BadRequestException(
          String.format("Translation metadata key '%s' does not contain valid JSON", key));
    }
  }

  private String toJson(Map<String, String> sanitized) {
    try {
      return objectMapper.writeValueAsString(sanitized);
    } catch (JsonProcessingException e) {
      throw new InternalServerErrorException(
          "Could not serialize department imprint content", e);
    }
  }
}
