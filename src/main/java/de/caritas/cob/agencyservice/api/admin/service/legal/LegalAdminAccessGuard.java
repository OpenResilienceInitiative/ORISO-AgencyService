package de.caritas.cob.agencyservice.api.admin.service.legal;

import de.caritas.cob.agencyservice.api.admin.service.UserAdminService;
import de.caritas.cob.agencyservice.api.exception.httpresponses.AgencyAccessDeniedException;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The two authorisation checks every admin-facing legal-text endpoint has to pass, extracted so
 * the ADR-021 version endpoints cannot drift from the publish endpoints they mirror.
 *
 * <p>The wording of a published legal document is not secret, but <em>who runs which
 * Beratungsstelle</em> and what their drafts look like is; the version list would otherwise be an
 * IDOR hole around the guards {@code DepartmentDataProtectionService} and {@code
 * DepartmentImprintService} already apply on the write side.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LegalAdminAccessGuard {

  private final @NonNull AuthenticatedUser authenticatedUser;
  private final @NonNull UserAdminService userAdminService;

  /**
   * Restricted agency admins may only touch agencies they administer (mirrors {@code
   * AgencyUpdatePermissionValidator}). Full agency admins are covered by {@link
   * #assertCallerTenantMatches}.
   */
  public void assertRestrictedAdminOwnsAgency(Long agencyId) {
    if (authenticatedUser.hasRestrictedAgencyPriviliges()) {
      var adminAgencyIds = userAdminService.getAdminUserAgencyIds(authenticatedUser.requireUserId());
      if (adminAgencyIds == null || !adminAgencyIds.contains(agencyId)) {
        log.warn(
            "Admin user {} may not read the legal texts of agency {}",
            authenticatedUser.requireUserId(),
            agencyId);
        throw new AgencyAccessDeniedException();
      }
    }
  }

  /**
   * Cross-tenant guard (mirrors {@code AgencyTenantValidator}): a full agency admin of tenant A
   * must not read tenant B's legal texts. Necessary because the Hibernate tenant filter is not
   * installed when {@code multitenancy.enabled=false} (all deployed profiles), so agency-id
   * membership alone does not scope full admins. Tenant {@code 0} (super/technical) and
   * single-tenant mode (no tenant in context) are unrestricted.
   */
  public void assertCallerTenantMatches(Agency agency) {
    if (agency == null) {
      throw new AgencyAccessDeniedException();
    }
    assertCallerTenantIs(agency.getTenantId());
  }

  /**
   * The tenant half of {@link #assertCallerTenantMatches} for objects that are owned by a Träger
   * without belonging to one agency — the ADR-014 shared legal texts and their version history.
   */
  public void assertCallerTenantIs(Long ownerTenantId) {
    Long effectiveTenantId = resolveEffectiveTenantId();
    if (effectiveTenantId == null || effectiveTenantId.equals(0L)) {
      return;
    }
    if (!effectiveTenantId.equals(ownerTenantId)) {
      log.warn(
          "Admin user {} (tenant {}) may not read legal texts owned by tenant {}",
          authenticatedUser.getUserId(),
          effectiveTenantId,
          ownerTenantId);
      throw new AgencyAccessDeniedException();
    }
  }

  /** The tenant the caller acts for: the token claim, else the resolved request tenant. */
  public Long resolveEffectiveTenantId() {
    Long tenantIdFromAuth = authenticatedUser.getTenantId();
    return tenantIdFromAuth != null ? tenantIdFromAuth : TenantContext.getCurrentTenant();
  }
}
