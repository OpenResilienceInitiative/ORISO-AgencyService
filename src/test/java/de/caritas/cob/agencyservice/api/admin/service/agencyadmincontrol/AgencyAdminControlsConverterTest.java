package de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol;

import static org.assertj.core.api.Assertions.assertThat;

import de.caritas.cob.agencyservice.api.model.AgencyAdminAllowedPermissionToggles;
import de.caritas.cob.agencyservice.api.model.AgencyAdminControls;
import org.junit.jupiter.api.Test;

class AgencyAdminControlsConverterTest {

  private final AgencyAdminControlsConverter converter = new AgencyAdminControlsConverter();

  @Test
  void toAgencyAdminControls_should_preserveEnforcedToggle() {
    var controls =
        new AgencyAdminControls()
            .enforcedPermissionToggles(new AgencyAdminAllowedPermissionToggles().videoCalls(true));

    var settings = converter.toAgencyAdminControlsSettings(controls);
    var back = converter.toAgencyAdminControls(settings);

    assertThat(back.getEnforcedPermissionToggles().getVideoCalls()).isTrue();
  }

  @Test
  void toAgencyAdminControlsSettings_should_defaultMissingEnforcedToggleToFalse() {
    // unlike allowed (defaults true), an unset enforced flag means "not enforced" = false
    var controls =
        new AgencyAdminControls().enforcedPermissionToggles(new AgencyAdminAllowedPermissionToggles());

    var settings = converter.toAgencyAdminControlsSettings(controls);

    assertThat(settings.getEnforcedPermissionToggles().getVideoCalls()).isFalse();
  }

  @Test
  void toAgencyAdminControlsSettings_should_keepEnforcedNullWhenAbsent() {
    var controls =
        new AgencyAdminControls().allowedPermissionToggles(new AgencyAdminAllowedPermissionToggles());

    var settings = converter.toAgencyAdminControlsSettings(controls);

    assertThat(settings.getEnforcedPermissionToggles()).isNull();
  }
}
