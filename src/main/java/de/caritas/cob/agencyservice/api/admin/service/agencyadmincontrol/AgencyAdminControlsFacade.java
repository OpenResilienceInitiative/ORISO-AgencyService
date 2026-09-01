package de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol;

import de.caritas.cob.agencyservice.api.model.AgencyAdminControls;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgencyAdminControlsFacade {

  private final @NonNull AgencyAdminControlsService agencyAdminControlsService;
  private final @NonNull AuthenticatedUser authenticatedUser;

  public AgencyAdminControls getAgencyAdminControls() {
    assertSuperAdmin();
    return agencyAdminControlsService.getControls(requireTenantId());
  }

  public AgencyAdminControls updateAgencyAdminControls(AgencyAdminControls agencyAdminControls) {
    assertSuperAdmin();
    return agencyAdminControlsService.updateControls(requireTenantId(), agencyAdminControls);
  }

  private void assertSuperAdmin() {
    if (!authenticatedUser.isTenantSuperAdmin()) {
      throw new AccessDeniedException("Only super admin can manage platform agency admin controls");
    }
  }

  private Long requireTenantId() {
    Long tenantId = authenticatedUser.getTenantId();
    if (tenantId == null || tenantId <= 0) {
      throw new AccessDeniedException("A tenant admin can manage only its own tenant");
    }
    return tenantId;
  }
}
