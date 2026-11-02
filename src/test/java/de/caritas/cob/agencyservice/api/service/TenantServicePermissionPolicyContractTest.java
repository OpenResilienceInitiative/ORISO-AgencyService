package de.caritas.cob.agencyservice.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class TenantServicePermissionPolicyContractTest {

  @Test
  @SuppressWarnings("unchecked")
  void tenantServiceClient_shouldMirrorTheExplicitCaseHandoverConsentContract()
      throws IOException {
    Map<String, Object> specification =
        new Yaml().load(Files.readString(Path.of("services/tenantservice.yaml")));
    var components = (Map<String, Object>) specification.get("components");
    var schemas = (Map<String, Object>) components.get("schemas");
    var consentValue = (Map<String, Object>) schemas.get("CaseHandoverConsentValue");
    var consentPolicy = (Map<String, Object>) schemas.get("ConsentPermissionPolicy");
    var reasonPolicy = (Map<String, Object>) schemas.get("CaseHandoverReasonPolicy");
    var reasonRequired = (List<String>) reasonPolicy.get("required");
    var reasonProperties = (Map<String, Object>) reasonPolicy.get("properties");

    assertThat((List<String>) consentValue.get("enum"))
        .containsExactly("OPT_IN", "OPT_OUT", "NONE");
    assertThat((List<String>) consentPolicy.get("required")).containsExactly("value", "mode");
    // The current provider keeps the legacy mirror required until old clients migrate.
    assertThat(reasonRequired).contains("clientConsentRequired");
    assertThat(reasonRequired).doesNotContain("clientConsent");
    assertThat((Map<String, Object>) reasonProperties.get("clientConsent"))
        .containsEntry("$ref", "#/components/schemas/ConsentPermissionPolicy");
    assertThat((Map<String, Object>) reasonProperties.get("clientConsentRequired"))
        .containsEntry("deprecated", true);
  }

  @Test
  @SuppressWarnings("unchecked")
  void collectionPolicies_shouldBeDistinctTenantDefaultsWithoutAgencyOverrides() throws IOException {
    Map<String, Object> specification =
        new Yaml().load(Files.readString(Path.of("services/tenantservice.yaml")));
    var schemas = (Map<String, Object>) ((Map<String, Object>) specification.get("components")).get("schemas");
    var properties = (Map<String, Object>) ((Map<String, Object>) schemas.get("Settings")).get("properties");
    var featureKeys = (List<String>) ((Map<String, Object>) schemas.get("PermissionFeatureKey")).get("enum");
    var keys = List.of("featureAskerEmailCollectionAgencyCounsellingEnabled",
        "featureAskerEmailCollectionLiveChatEnabled", "featureAskerEmailCollectionSelfHelpEnabled");
    for (String key : keys) {
      assertThat(featureKeys).contains(key);
      assertThat((Map<String, Object>) properties.get(key))
          .containsEntry("type", "boolean").containsEntry("default", true);
    }
    assertThat((Map<String, Object>) properties.get("featureAskerEmailLiveChatEnabled"))
        .containsEntry("default", false);
    Map<String, Object> agencySpec =
        new Yaml().load(Files.readString(Path.of("api/components/agency-settings.yaml")));
    var agencySchemas = (Map<String, Object>) ((Map<String, Object>) agencySpec.get("components")).get("schemas");
    var agencyProperties = (Map<String, Object>) ((Map<String, Object>) agencySchemas.get("Settings")).get("properties");
    assertThat(agencyProperties).doesNotContainKeys(keys.toArray(String[]::new));
  }
}
