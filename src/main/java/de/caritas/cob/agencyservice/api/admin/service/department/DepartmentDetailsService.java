package de.caritas.cob.agencyservice.api.admin.service.department;

import de.caritas.cob.agencyservice.api.admin.service.UserAdminService;
import de.caritas.cob.agencyservice.api.exception.httpresponses.AgencyAccessDeniedException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopicRepository;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import java.time.LocalDateTime;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores a department's ({@code Fachbereich} = agency × topic) contact detail overrides:
 * opening hours, phone extension (Durchwahl) and floor/location detail (Etage) — ORISO-Admin#197
 * ("a center may run several Fachbereiche at different floors/areas with different hours").
 *
 * <p>Only overrides are persisted: a {@code null}/blank member clears the column so the department
 * inherits the Beratungsstelle value again. The public API resolves {@code Fachbereich value ??
 * Beratungsstelle value}; the admin read here returns the raw overrides so the panel can render
 * its "inherits from Beratungsstelle unless set" affordance.
 *
 * <p>Authorisation mirrors {@link
 * de.caritas.cob.agencyservice.api.admin.service.legal.DepartmentDataProtectionService}: (a) a
 * restricted agency admin may only touch agencies they administer (IDOR guard) and (b) a full
 * agency admin of tenant A must not edit tenant B's Fachbereich (cross-tenant guard).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DepartmentDetailsService {

  private final @NonNull AgencyTopicRepository agencyTopicRepository;
  private final @NonNull AuthenticatedUser authenticatedUser;
  private final @NonNull UserAdminService userAdminService;

  /**
   * Stores the department's contact detail overrides. Blank values are normalised to {@code null}
   * ("no override") so the public resolution can never fall onto an empty override.
   *
   * @return the stored overrides after the update
   */
  @Transactional
  public DepartmentDetailsView updateDepartmentDetails(
      Long agencyId, Long topicId, String openingHours, String phoneExtension,
      String floorLocation) {
    assertRestrictedAdminOwnsAgency(agencyId);

    AgencyTopic department = loadDepartment(agencyId, topicId);
    assertCallerTenantMatches(department.getAgency());

    department.setOpeningHours(normalize(openingHours));
    department.setPhoneExtension(normalize(phoneExtension));
    department.setFloorLocation(normalize(floorLocation));
    department.setUpdateDate(LocalDateTime.now());
    agencyTopicRepository.save(department);

    return toView(department);
  }

  /**
   * Reads the department's stored overrides ({@code null} member = inherits) to prefill the admin
   * form. Same authorisation as the write path.
   */
  @Transactional(readOnly = true)
  public DepartmentDetailsView getDepartmentDetails(Long agencyId, Long topicId) {
    assertRestrictedAdminOwnsAgency(agencyId);

    AgencyTopic department = loadDepartment(agencyId, topicId);
    assertCallerTenantMatches(department.getAgency());

    return toView(department);
  }

  private AgencyTopic loadDepartment(Long agencyId, Long topicId) {
    return agencyTopicRepository
        .findByAgency_IdAndTopicId(agencyId, topicId)
        .orElseThrow(NotFoundException::new);
  }

  private DepartmentDetailsView toView(AgencyTopic department) {
    return new DepartmentDetailsView(
        department.getOpeningHours(),
        department.getPhoneExtension(),
        department.getFloorLocation());
  }

  private String normalize(String value) {
    if (value == null) {
      return null;
    }
    var trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * Restricted agency admins may only touch agencies they administer (mirrors {@code
   * AgencyUpdatePermissionValidator}). Full agency admins are handled by the tenant guard below.
   */
  private void assertRestrictedAdminOwnsAgency(Long agencyId) {
    if (authenticatedUser.hasRestrictedAgencyPriviliges()) {
      var adminAgencyIds =
          userAdminService.getAdminUserAgencyIds(authenticatedUser.requireUserId());
      if (adminAgencyIds == null || !adminAgencyIds.contains(agencyId)) {
        log.warn(
            "Admin user {} may not edit the department details of agency {}",
            authenticatedUser.requireUserId(),
            agencyId);
        throw new AgencyAccessDeniedException();
      }
    }
  }

  /**
   * Cross-tenant guard (mirrors {@code AgencyTenantValidator}): a full agency admin of tenant A
   * must not edit tenant B's Fachbereich. Necessary because the Hibernate tenant filter is not
   * installed when {@code multitenancy.enabled=false} (all deployed profiles). Tenant {@code 0}
   * (super/technical) and single-tenant mode (no tenant in context) are unrestricted.
   */
  private void assertCallerTenantMatches(Agency agency) {
    Long effectiveTenantId = resolveEffectiveTenantId();
    if (effectiveTenantId == null || effectiveTenantId.equals(0L)) {
      return;
    }
    if (agency == null || !effectiveTenantId.equals(agency.getTenantId())) {
      log.warn(
          "Admin user {} (tenant {}) may not edit the department details of agency {} (tenant {})",
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
