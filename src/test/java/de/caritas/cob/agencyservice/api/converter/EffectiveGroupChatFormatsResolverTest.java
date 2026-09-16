package de.caritas.cob.agencyservice.api.converter;

import static org.assertj.core.api.Assertions.assertThat;

import de.caritas.cob.agencyservice.api.model.Settings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Truth table for the effective group-chat formats served on the public agency response
 * (ORISO-AgencyService#293, ADR-013): a Beratungsstelle may only restrict what its Träger allows.
 */
class EffectiveGroupChatFormatsResolverTest {

  private static final String NULL = "null";

  // ---------------------------------------------------------------------------------------------
  // Full truth table: format x Träger (on/off/unset) x agency (on/off/unset).
  // Each level carries only the tested flag; every other flag on that level is unset.
  // ---------------------------------------------------------------------------------------------

  @ParameterizedTest(name = "{0}: Träger={1}, agency={2} -> {3}")
  @CsvSource({
    "featureGroupChatV2Enabled, true, true, true",
    "featureGroupChatV2Enabled, true, false, false",
    "featureGroupChatV2Enabled, true, null, true",
    "featureGroupChatV2Enabled, false, true, false",
    "featureGroupChatV2Enabled, false, false, false",
    "featureGroupChatV2Enabled, false, null, false",
    "featureGroupChatV2Enabled, null, true, true",
    "featureGroupChatV2Enabled, null, false, false",
    "featureGroupChatV2Enabled, null, null, null",
    "featureInternalGroupChatEnabled, true, true, true",
    "featureInternalGroupChatEnabled, true, false, false",
    "featureInternalGroupChatEnabled, true, null, true",
    "featureInternalGroupChatEnabled, false, true, false",
    "featureInternalGroupChatEnabled, false, false, false",
    "featureInternalGroupChatEnabled, false, null, false",
    "featureInternalGroupChatEnabled, null, true, true",
    "featureInternalGroupChatEnabled, null, false, false",
    "featureInternalGroupChatEnabled, null, null, null",
    "featureSelfHelpGroupsEnabled, true, true, true",
    "featureSelfHelpGroupsEnabled, true, false, false",
    "featureSelfHelpGroupsEnabled, true, null, true",
    "featureSelfHelpGroupsEnabled, false, true, false",
    "featureSelfHelpGroupsEnabled, false, false, false",
    "featureSelfHelpGroupsEnabled, false, null, false",
    "featureSelfHelpGroupsEnabled, null, true, true",
    "featureSelfHelpGroupsEnabled, null, false, false",
    "featureSelfHelpGroupsEnabled, null, null, null",
  })
  void applyTo_should_letAgencyOnlyRestrictTheTraeger(
      String format, String traeger, String agency, String expected) {
    var traegerSettings =
        new de.caritas.cob.agencyservice.tenantservice.generated.web.model.Settings();
    var agencySettings = new Settings();
    setTraeger(traegerSettings, format, bool(traeger));
    setAgency(agencySettings, format, bool(agency));

    EffectiveGroupChatFormatsResolver.applyTo(agencySettings, traegerSettings);

    assertThat(getAgency(agencySettings, format)).isEqualTo(bool(expected));
  }

  // ---------------------------------------------------------------------------------------------
  // Legacy fallback: a missing format flag follows featureGroupChatV2Enabled on the same level.
  // ---------------------------------------------------------------------------------------------

  @ParameterizedTest(name = "Träger v2={0}, agency v2={1} -> both formats {2}")
  @CsvSource({
    "true, true, true",
    "true, false, false",
    "false, true, false",
    "false, false, false",
    "null, true, true",
    "true, null, true",
    "false, null, false",
  })
  void applyTo_should_fallBackToGroupChatV2OnEachLevel_whenFormatFlagsAreMissing(
      String traegerV2, String agencyV2, String expectedFormats) {
    var traeger =
        new de.caritas.cob.agencyservice.tenantservice.generated.web.model.Settings()
            .featureGroupChatV2Enabled(bool(traegerV2));
    var agency = new Settings().featureGroupChatV2Enabled(bool(agencyV2));

    EffectiveGroupChatFormatsResolver.applyTo(agency, traeger);

    assertThat(agency.getFeatureInternalGroupChatEnabled()).isEqualTo(bool(expectedFormats));
    assertThat(agency.getFeatureSelfHelpGroupsEnabled()).isEqualTo(bool(expectedFormats));
  }

  @Test
  void applyTo_should_letTraegerFormatFlagWin_overAgencyLegacyFallback() {
    // TenantService after ORISO-TenantService#250 sends the format flag; the agency was saved
    // before the format flags existed and only carries the legacy flag.
    var traeger =
        new de.caritas.cob.agencyservice.tenantservice.generated.web.model.Settings()
            .featureGroupChatV2Enabled(true)
            .featureInternalGroupChatEnabled(true)
            .featureSelfHelpGroupsEnabled(false);
    var agency = new Settings().featureGroupChatV2Enabled(true);

    EffectiveGroupChatFormatsResolver.applyTo(agency, traeger);

    assertThat(agency.getFeatureInternalGroupChatEnabled()).isTrue();
    assertThat(agency.getFeatureSelfHelpGroupsEnabled()).isFalse();
    assertThat(agency.getFeatureGroupChatV2Enabled()).isTrue();
  }

  // ---------------------------------------------------------------------------------------------
  // Independence and degraded input
  // ---------------------------------------------------------------------------------------------

  @Test
  void applyTo_should_restrictOnlyTheSwitchedOffFormat() {
    var traeger =
        new de.caritas.cob.agencyservice.tenantservice.generated.web.model.Settings()
            .featureGroupChatV2Enabled(true)
            .featureInternalGroupChatEnabled(true)
            .featureSelfHelpGroupsEnabled(true);
    var agency = new Settings().featureSelfHelpGroupsEnabled(false);

    EffectiveGroupChatFormatsResolver.applyTo(agency, traeger);

    assertThat(agency.getFeatureSelfHelpGroupsEnabled()).isFalse();
    assertThat(agency.getFeatureInternalGroupChatEnabled()).isTrue();
    assertThat(agency.getFeatureGroupChatV2Enabled()).isTrue();
  }

  @Test
  void applyTo_should_keepAgencyValues_whenTraegerSettingsAreUnavailable() {
    var agency =
        new Settings().featureGroupChatV2Enabled(true).featureInternalGroupChatEnabled(false);

    EffectiveGroupChatFormatsResolver.applyTo(agency, null);

    assertThat(agency.getFeatureGroupChatV2Enabled()).isTrue();
    assertThat(agency.getFeatureInternalGroupChatEnabled()).isFalse();
    assertThat(agency.getFeatureSelfHelpGroupsEnabled()).isTrue();
  }

  @Test
  void applyTo_should_doNothing_whenAgencySettingsAreNull() {
    // Must not throw.
    EffectiveGroupChatFormatsResolver.applyTo(
        null, new de.caritas.cob.agencyservice.tenantservice.generated.web.model.Settings());
  }

  private static Boolean bool(String value) {
    return NULL.equals(value) ? null : Boolean.valueOf(value);
  }

  private static void setTraeger(
      de.caritas.cob.agencyservice.tenantservice.generated.web.model.Settings settings,
      String format,
      Boolean value) {
    switch (format) {
      case "featureGroupChatV2Enabled" -> settings.setFeatureGroupChatV2Enabled(value);
      case "featureInternalGroupChatEnabled" -> settings.setFeatureInternalGroupChatEnabled(value);
      case "featureSelfHelpGroupsEnabled" -> settings.setFeatureSelfHelpGroupsEnabled(value);
      default -> throw new IllegalArgumentException(format);
    }
  }

  private static void setAgency(Settings settings, String format, Boolean value) {
    switch (format) {
      case "featureGroupChatV2Enabled" -> settings.setFeatureGroupChatV2Enabled(value);
      case "featureInternalGroupChatEnabled" -> settings.setFeatureInternalGroupChatEnabled(value);
      case "featureSelfHelpGroupsEnabled" -> settings.setFeatureSelfHelpGroupsEnabled(value);
      default -> throw new IllegalArgumentException(format);
    }
  }

  private static Boolean getAgency(Settings settings, String format) {
    return switch (format) {
      case "featureGroupChatV2Enabled" -> settings.getFeatureGroupChatV2Enabled();
      case "featureInternalGroupChatEnabled" -> settings.getFeatureInternalGroupChatEnabled();
      case "featureSelfHelpGroupsEnabled" -> settings.getFeatureSelfHelpGroupsEnabled();
      default -> throw new IllegalArgumentException(format);
    };
  }
}
