package de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol;

import static org.assertj.core.api.Assertions.assertThat;

import de.caritas.cob.agencyservice.api.model.AgencyAdminAllowedPermissionToggles;
import de.caritas.cob.agencyservice.api.model.AgencyAdminControls;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.BooleanPermissionPolicy;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.PermissionPolicyMode;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.TenantPermissionPolicies;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgencyPermissionPolicyMapperTest {

  private final AgencyPermissionPolicyMapper mapper = new AgencyPermissionPolicyMapper();

  @Test
  void toLegacyControls_shouldRepresentAllFourBooleanStatesWithoutForcingSuggestions() {
    var controls =
        mapper.toLegacyControls(
            Map.of(
                "featureVideoCallsEnabled",
                policy(true, PermissionPolicyMode.ENFORCED),
                "featureAudioCallsEnabled",
                policy(false, PermissionPolicyMode.ENFORCED),
                "featureThreadsEnabled",
                policy(true, PermissionPolicyMode.SUGGESTED),
                "featureMediaUploadEnabled",
                policy(false, PermissionPolicyMode.SUGGESTED)));

    assertThat(controls.getEnforcedPermissionToggles().getVideoCalls()).isTrue();
    assertThat(controls.getAllowedPermissionToggles().getAudioCalls()).isFalse();
    assertThat(controls.getEnforcedPermissionToggles().getThreads()).isFalse();
    assertThat(controls.getAllowedPermissionToggles().getMediaUpload()).isTrue();
  }

  @Test
  void applyLegacyUpdate_shouldUnlockToSuggestionAndPreserveCurrentValue() {
    var existing =
        new TenantPermissionPolicies()
            .tenantId(7L)
            .policies(
                Map.of(
                "featureSupervisionEnabled",
                    policy(true, PermissionPolicyMode.ENFORCED)));
    var controls =
        new AgencyAdminControls()
            .allowedPermissionToggles(
                new AgencyAdminAllowedPermissionToggles().supervision(true))
            .enforcedPermissionToggles(
                new AgencyAdminAllowedPermissionToggles().supervision(false));

    mapper.applyLegacyUpdate(existing, controls);

    assertThat(existing.getPolicies().get("featureSupervisionEnabled"))
        .isEqualTo(policy(true, PermissionPolicyMode.SUGGESTED));
  }

  @Test
  void registry_shouldCoverEveryLegacyPolicyFeatureIncludingMedia() {
    assertThat(mapper.supportedFeatures()).hasSize(42);
    assertThat(mapper.supportedFeatures())
        .contains(
            "featureMediaAiScanSupervisionChatsEnabled",
            "featureThreadsOneOnOneEnabled",
            "appearance");
  }

  private static BooleanPermissionPolicy policy(boolean value, PermissionPolicyMode mode) {
    return new BooleanPermissionPolicy().value(value).mode(mode);
  }
}
