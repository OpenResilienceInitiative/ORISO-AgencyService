package de.caritas.cob.agencyservice.api.converter;

import static org.assertj.core.api.Assertions.assertThat;

import de.caritas.cob.agencyservice.api.model.AgencyAdminAllowedPermissionToggles;
import de.caritas.cob.agencyservice.api.model.AgencyAdminControls;
import de.caritas.cob.agencyservice.api.model.Settings;
import org.junit.jupiter.api.Test;

class AgencyEffectivePermissionSettingsApplierTest {

  private final AgencyEffectivePermissionSettingsApplier applier =
      new AgencyEffectivePermissionSettingsApplier();

  @Test
  void applyTo_should_forceFeatureOff_whenUpperRoleDisallowsIt() {
    var settings = new Settings().featureVideoCallsEnabled(true);
    var controls =
        new AgencyAdminControls()
            .allowedPermissionToggles(
                new AgencyAdminAllowedPermissionToggles().videoCalls(false));

    applier.applyTo(settings, controls);

    assertThat(settings.getFeatureVideoCallsEnabled()).isFalse();
  }

  @Test
  void applyTo_should_forceFeatureOn_whenUpperRoleEnforcesIt() {
    var settings = new Settings().featureAudioCallsEnabled(false);
    var controls =
        new AgencyAdminControls()
            .enforcedPermissionToggles(
                new AgencyAdminAllowedPermissionToggles().audioCalls(true));

    applier.applyTo(settings, controls);

    assertThat(settings.getFeatureAudioCallsEnabled()).isTrue();
  }

  @Test
  void applyTo_should_leaveUnconstrainedFeatureUnchanged() {
    var settings = new Settings().featureThreadsEnabled(true);
    var controls =
        new AgencyAdminControls()
            .allowedPermissionToggles(new AgencyAdminAllowedPermissionToggles());

    applier.applyTo(settings, controls);

    assertThat(settings.getFeatureThreadsEnabled()).isTrue();
  }

  @Test
  void applyTo_should_doNothing_whenControlsAreNull() {
    var settings = new Settings().featureVideoCallsEnabled(true);

    applier.applyTo(settings, null);

    assertThat(settings.getFeatureVideoCallsEnabled()).isTrue();
  }

  @Test
  void applyTo_should_doNothing_whenSettingsAreNull() {
    var controls =
        new AgencyAdminControls()
            .enforcedPermissionToggles(
                new AgencyAdminAllowedPermissionToggles().audioCalls(true));

    // Must not throw.
    applier.applyTo(null, controls);
  }

  @Test
  void applyTo_should_letEnforcedOnWin_whenBothAllowedOffAndEnforcedOnAreSet() {
    var settings = new Settings().featureCallsEnabled(true);
    var togglesAllowed = new AgencyAdminAllowedPermissionToggles().calls(false);
    var togglesEnforced = new AgencyAdminAllowedPermissionToggles().calls(true);
    var controls =
        new AgencyAdminControls()
            .allowedPermissionToggles(togglesAllowed)
            .enforcedPermissionToggles(togglesEnforced);

    applier.applyTo(settings, controls);

    assertThat(settings.getFeatureCallsEnabled()).isTrue();
  }

  @Test
  void applyTo_should_governTeamDiscussionLikeSupervision() {
    // ORISO-Frontend#2491: Träger off forces the Beratungsstelle team chat off, a requirement on.
    var settings =
        new Settings()
            .featureTeamDiscussionEnabled(true)
            .featureMediaUploadTeamDiscussionsEnabled(false);
    var controls =
        new AgencyAdminControls()
            .allowedPermissionToggles(
                new AgencyAdminAllowedPermissionToggles().teamDiscussion(false))
            .enforcedPermissionToggles(
                new AgencyAdminAllowedPermissionToggles().mediaUploadTeamDiscussions(true));

    applier.applyTo(settings, controls);

    assertThat(settings.getFeatureTeamDiscussionEnabled()).isFalse();
    assertThat(settings.getFeatureMediaUploadTeamDiscussionsEnabled()).isTrue();
  }
}
