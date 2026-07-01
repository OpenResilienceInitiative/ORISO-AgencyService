package de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.model.AgencyAdminControls;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class AgencyAdminControlsFacadeTest {

  @InjectMocks
  AgencyAdminControlsFacade agencyAdminControlsFacade;

  @Mock
  AgencyAdminControlsService agencyAdminControlsService;

  @Mock
  AuthenticatedUser authenticatedUser;

  @Test
  void getAgencyAdminControls_Should_ReturnControls_When_UserIsSuperAdmin() {
    when(authenticatedUser.isTenantSuperAdmin()).thenReturn(true);
    AgencyAdminControls expected = new AgencyAdminControls();
    when(agencyAdminControlsService.getControls()).thenReturn(expected);

    AgencyAdminControls result = agencyAdminControlsFacade.getAgencyAdminControls();

    assertThat(result).isEqualTo(expected);
    verify(agencyAdminControlsService).getControls();
  }

  @Test
  void getAgencyAdminControls_Should_ThrowAccessDeniedException_When_UserIsNotSuperAdmin() {
    when(authenticatedUser.isTenantSuperAdmin()).thenReturn(false);

    assertThatThrownBy(() -> agencyAdminControlsFacade.getAgencyAdminControls())
        .isInstanceOf(AccessDeniedException.class);

    verify(agencyAdminControlsService, never()).getControls();
  }

  @Test
  void updateAgencyAdminControls_Should_ReturnUpdatedControls_When_UserIsSuperAdmin() {
    when(authenticatedUser.isTenantSuperAdmin()).thenReturn(true);
    AgencyAdminControls controls = new AgencyAdminControls();
    when(agencyAdminControlsService.updateControls(controls)).thenReturn(controls);

    AgencyAdminControls result = agencyAdminControlsFacade.updateAgencyAdminControls(controls);

    assertThat(result).isEqualTo(controls);
    verify(agencyAdminControlsService).updateControls(controls);
  }

  @Test
  void updateAgencyAdminControls_Should_ThrowAccessDeniedException_When_UserIsNotSuperAdmin() {
    when(authenticatedUser.isTenantSuperAdmin()).thenReturn(false);
    AgencyAdminControls controls = new AgencyAdminControls();

    assertThatThrownBy(() -> agencyAdminControlsFacade.updateAgencyAdminControls(controls))
        .isInstanceOf(AccessDeniedException.class);

    verify(agencyAdminControlsService, never()).updateControls(any());
  }
}
