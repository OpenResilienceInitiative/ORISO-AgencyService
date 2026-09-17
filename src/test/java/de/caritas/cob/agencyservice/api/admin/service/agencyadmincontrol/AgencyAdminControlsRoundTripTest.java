package de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.model.AgencyAdminAllowedPermissionToggles;
import de.caritas.cob.agencyservice.api.model.AgencyAdminControls;
import de.caritas.cob.agencyservice.api.repository.agencyadmincontrol.AgencyAdminControlEntity;
import de.caritas.cob.agencyservice.api.repository.agencyadmincontrol.AgencyAdminControlRepository;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Save-and-reload at the admin-controls seam (ORISO-AgencyService#293): the two group-chat format
 * toggles survive the JSON round trip, and toggles that were never set stay unset so they keep
 * following {@code groupChat}.
 */
class AgencyAdminControlsRoundTripTest {

  private AgencyAdminControlsService service;

  @BeforeEach
  void setUp() {
    var repository = mock(AgencyAdminControlRepository.class);
    var stored = new AtomicReference<AgencyAdminControlEntity>();
    when(repository.findTopByOrderByIdAsc()).thenAnswer(i -> Optional.ofNullable(stored.get()));
    when(repository.save(any(AgencyAdminControlEntity.class)))
        .thenAnswer(
            invocation -> {
              stored.set(invocation.getArgument(0));
              return stored.get();
            });
    service = new AgencyAdminControlsService(repository, new AgencyAdminControlsConverter());
  }

  @Test
  void updateControls_then_getControls_should_keepGroupChatFormatToggles() {
    service.updateControls(
        new AgencyAdminControls()
            .allowedPermissionToggles(
                new AgencyAdminAllowedPermissionToggles()
                    .groupChat(true)
                    .internalGroupChat(true)
                    .selfHelpGroups(false))
            .enforcedPermissionToggles(
                new AgencyAdminAllowedPermissionToggles().internalGroupChat(true)));

    AgencyAdminControls reloaded = service.getControls();

    assertThat(reloaded.getAllowedPermissionToggles().getGroupChat()).isTrue();
    assertThat(reloaded.getAllowedPermissionToggles().getInternalGroupChat()).isTrue();
    assertThat(reloaded.getAllowedPermissionToggles().getSelfHelpGroups()).isFalse();
    assertThat(reloaded.getEnforcedPermissionToggles().getInternalGroupChat()).isTrue();
  }

  @Test
  void updateControls_then_getControls_should_leaveUnsetFormatTogglesUnset() {
    service.updateControls(
        new AgencyAdminControls()
            .allowedPermissionToggles(new AgencyAdminAllowedPermissionToggles().groupChat(false))
            .enforcedPermissionToggles(new AgencyAdminAllowedPermissionToggles()));

    AgencyAdminControls reloaded = service.getControls();

    // groupChat itself is defaulted (allowed -> true unless set, enforced -> false unless set);
    // the format toggles are stored as given so a missing value keeps following groupChat.
    assertThat(reloaded.getAllowedPermissionToggles().getGroupChat()).isFalse();
    assertThat(reloaded.getAllowedPermissionToggles().getInternalGroupChat()).isNull();
    assertThat(reloaded.getAllowedPermissionToggles().getSelfHelpGroups()).isNull();
    assertThat(reloaded.getEnforcedPermissionToggles().getGroupChat()).isFalse();
    assertThat(reloaded.getEnforcedPermissionToggles().getInternalGroupChat()).isNull();
    assertThat(reloaded.getEnforcedPermissionToggles().getSelfHelpGroups()).isNull();
  }
}
