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
    return agencyAdminControlsService.getControls();
  }

  public AgencyAdminControls updateAgencyAdminControls(AgencyAdminControls agencyAdminControls) {
    assertSuperAdmin();
    return agencyAdminControlsService.updateControls(agencyAdminControls);
  }

  private void assertSuperAdmin() {
    if (!authenticatedUser.isTenantSuperAdmin()) {
      throw new AccessDeniedException("Only super admin can manage platform agency admin controls");
    }
  }
}
