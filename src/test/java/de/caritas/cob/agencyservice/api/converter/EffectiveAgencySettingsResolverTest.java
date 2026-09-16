package de.caritas.cob.agencyservice.api.converter;

import static org.assertj.core.api.Assertions.assertThat;

import de.caritas.cob.agencyservice.api.model.Settings;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Truth tables for the effective feature flags served on the public agency response
 * (ORISO-AgencyService#293, ADR-013): a Beratungsstelle may only restrict what its Träger allows,
 * uniformly for every flag both levels share.
 */
class EffectiveAgencySettingsResolverTest {

  private static final String NULL = "null";

  private static final Class<?> TRAEGER_SETTINGS =
      de.caritas.cob.agencyservice.tenantservice.generated.web.model.Settings.class;

  /**
   * The permission registry: every boolean flag present in both the Träger (TenantService) and
   * the agency Settings schema, minus {@link #NOT_CASCADED}. Mirrors the TenantService {@code
   * PermissionFeature} names. Adding a shared flag to either contract must be a conscious
   * decision here as well.
   */
  private static final Set<String> EXPECTED_COMMON_FLAGS =
      Set.of(
          "featureAnonymousChatEnabled", "featureAudioCallsAnonymousChatsEnabled",
          "featureAudioCallsEnabled", "featureAudioCallsGroupChatsEnabled",
          "featureAudioCallsOneOnOneChatsEnabled", "featureAudioCallsSupervisionChatsEnabled",
          "featureCallsEnabled", "featureGroupChatV2Enabled", "featureInternalGroupChatEnabled",
          "featureMediaAiScanAnonymousChatsEnabled", "featureMediaAiScanEnabled",
          "featureMediaAiScanGroupChatsEnabled", "featureMediaAiScanOneOnOneChatsEnabled",
          "featureMediaAiScanSupervisionChatsEnabled",
          "featureMediaInlineDisplayAnonymousChatsEnabled", "featureMediaInlineDisplayEnabled",
          "featureMediaInlineDisplayGroupChatsEnabled",
          "featureMediaInlineDisplayOneOnOneChatsEnabled",
          "featureMediaInlineDisplaySupervisionChatsEnabled",
          "featureMediaUploadAnonymousChatsEnabled", "featureMediaUploadEnabled",
          "featureMediaUploadGroupChatsEnabled", "featureMediaUploadOneOnOneChatsEnabled",
          "featureMediaUploadSupervisionChatsEnabled", "featureSelfHelpGroupsEnabled",
          "featureSupervisionAnonymousChatsEnabled", "featureSupervisionEnabled",
          "featureSupervisionOneOnOneChatsEnabled", "featureThreadsAnonymousChatsEnabled",
          "featureThreadsEnabled", "featureThreadsGroupChatsEnabled",
          "featureThreadsOneOnOneEnabled", "featureThreadsSupervisionChatsEnabled",
          "featureVideoCallsAnonymousChatsEnabled", "featureVideoCallsEnabled",
          "featureVideoCallsGroupChatsEnabled", "featureVideoCallsOneOnOneChatsEnabled",
          "featureVideoCallsSupervisionChatsEnabled", "featureVoiceMessagesAnonymousChatsEnabled",
          "featureVoiceMessagesEnabled", "featureVoiceMessagesGroupChatsEnabled",
          "featureVoiceMessagesOneOnOneChatsEnabled",
          "featureVoiceMessagesSupervisionChatsEnabled");

  /**
   * Shared flags deliberately not cascaded: the eight operational tenant-level flags (served as
   * stored, {@code null} stays {@code null}) and the two flags with a schema default of
   * {@code false} on both models.
   */
  private static final Set<String> NOT_CASCADED =
      Set.of(
          "featureStatisticsEnabled",
          "featureTopicsEnabled",
          "topicsInRegistrationEnabled",
          "featureDemographicsEnabled",
          "featureAppointmentsEnabled",
          "featureToolsEnabled",
          "featureCentralDataProtectionTemplateEnabled",
          "featureSystemNotificationEmailsEnabled",
          "showAskerProfile",
          "isVideoCallAllowed");

  // ---------------------------------------------------------------------------------------------
  // Registry coverage: every common flag is resolved, nothing more, nothing less.
  // ---------------------------------------------------------------------------------------------

  @Test
  void registry_should_coverEveryFlagSharedByTraegerAndAgencySettings() {
    Set<String> discovered = new TreeSet<>();
    for (Method method : Settings.class.getMethods()) {
      if (method.getName().startsWith("get")
          && method.getParameterCount() == 0
          && method.getReturnType() == Boolean.class
          && hasBooleanGetter(TRAEGER_SETTINGS, method.getName())) {
        String suffix = method.getName().substring(3);
        discovered.add(Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1));
      }
    }
    discovered.removeAll(NOT_CASCADED);

    assertThat(EffectiveAgencySettingsResolver.flagNames())
        .containsExactlyInAnyOrderElementsOf(discovered)
        .containsExactlyInAnyOrderElementsOf(EXPECTED_COMMON_FLAGS)
        .doesNotContainAnyElementsOf(NOT_CASCADED);
  }

  @Test
  void registry_should_matchTheResolversOwnExclusionList() {
    assertThat(EffectiveAgencySettingsResolver.NOT_CASCADED)
        .containsExactlyInAnyOrderElementsOf(NOT_CASCADED);
  }

  @Test
  void applyTo_should_leaveExcludedFlagsAsTheAgencyStoresThem() {
    // Träger off must NOT win and unset must NOT inherit for excluded flags.
    for (String flag : NOT_CASCADED) {
      var traegerOff = newTraeger();
      var agencyOn = new Settings();
      set(traegerOff, flag, false);
      set(agencyOn, flag, true);
      EffectiveAgencySettingsResolver.applyTo(agencyOn, traegerOff);
      assertThat(get(agencyOn, flag)).as("%s: served as stored", flag).isTrue();

      var traegerOn = newTraeger();
      var agencyUnset = new Settings();
      set(traegerOn, flag, true);
      set(agencyUnset, flag, null);
      EffectiveAgencySettingsResolver.applyTo(agencyUnset, traegerOn);
      assertThat(get(agencyUnset, flag)).as("%s: null stays null", flag).isNull();
    }
  }

  @Test
  void applyTo_should_applyTheCascadeToEveryRegisteredFlag() {
    for (String flag : EffectiveAgencySettingsResolver.flagNames()) {
      var traegerOff = newTraeger();
      var agencyOn = new Settings();
      set(traegerOff, flag, false);
      set(agencyOn, flag, true);
      EffectiveAgencySettingsResolver.applyTo(agencyOn, traegerOff);
      assertThat(get(agencyOn, flag)).as("%s: Träger off must win", flag).isFalse();

      var traegerOn = newTraeger();
      var agencyUnset = new Settings();
      set(traegerOn, flag, true);
      EffectiveAgencySettingsResolver.applyTo(agencyUnset, traegerOn);
      assertThat(get(agencyUnset, flag)).as("%s: unset agency must inherit", flag).isTrue();
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Full truth table: flag x Träger (on/off/unset) x agency (on/off/unset).
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
    "featureCallsEnabled, true, true, true",
    "featureCallsEnabled, true, false, false",
    "featureCallsEnabled, true, null, true",
    "featureCallsEnabled, false, true, false",
    "featureCallsEnabled, false, false, false",
    "featureCallsEnabled, false, null, false",
    "featureCallsEnabled, null, true, true",
    "featureCallsEnabled, null, false, false",
    "featureCallsEnabled, null, null, null",
    "featureVideoCallsGroupChatsEnabled, true, true, true",
    "featureVideoCallsGroupChatsEnabled, true, false, false",
    "featureVideoCallsGroupChatsEnabled, true, null, true",
    "featureVideoCallsGroupChatsEnabled, false, true, false",
    "featureVideoCallsGroupChatsEnabled, false, false, false",
    "featureVideoCallsGroupChatsEnabled, false, null, false",
    "featureVideoCallsGroupChatsEnabled, null, true, true",
    "featureVideoCallsGroupChatsEnabled, null, false, false",
    "featureVideoCallsGroupChatsEnabled, null, null, null",
    "featureSupervisionOneOnOneChatsEnabled, true, true, true",
    "featureSupervisionOneOnOneChatsEnabled, true, false, false",
    "featureSupervisionOneOnOneChatsEnabled, true, null, true",
    "featureSupervisionOneOnOneChatsEnabled, false, true, false",
    "featureSupervisionOneOnOneChatsEnabled, false, false, false",
    "featureSupervisionOneOnOneChatsEnabled, false, null, false",
    "featureSupervisionOneOnOneChatsEnabled, null, true, true",
    "featureSupervisionOneOnOneChatsEnabled, null, false, false",
    "featureSupervisionOneOnOneChatsEnabled, null, null, null",
    "featureMediaAiScanSupervisionChatsEnabled, true, true, true",
    "featureMediaAiScanSupervisionChatsEnabled, true, false, false",
    "featureMediaAiScanSupervisionChatsEnabled, true, null, true",
    "featureMediaAiScanSupervisionChatsEnabled, false, true, false",
    "featureMediaAiScanSupervisionChatsEnabled, false, false, false",
    "featureMediaAiScanSupervisionChatsEnabled, false, null, false",
    "featureMediaAiScanSupervisionChatsEnabled, null, true, true",
    "featureMediaAiScanSupervisionChatsEnabled, null, false, false",
    "featureMediaAiScanSupervisionChatsEnabled, null, null, null",
    "featureAnonymousChatEnabled, true, true, true",
    "featureAnonymousChatEnabled, true, false, false",
    "featureAnonymousChatEnabled, true, null, true",
    "featureAnonymousChatEnabled, false, true, false",
    "featureAnonymousChatEnabled, false, false, false",
    "featureAnonymousChatEnabled, false, null, false",
    "featureAnonymousChatEnabled, null, true, true",
    "featureAnonymousChatEnabled, null, false, false",
    "featureAnonymousChatEnabled, null, null, null",
  })
  void applyTo_should_letAgencyOnlyRestrictTheTraeger(
      String flag, String traeger, String agency, String expected) {
    var traegerSettings = newTraeger();
    var agencySettings = new Settings();
    set(traegerSettings, flag, bool(traeger));
    set(agencySettings, flag, bool(agency));

    EffectiveAgencySettingsResolver.applyTo(agencySettings, traegerSettings);

    assertThat(get(agencySettings, flag)).isEqualTo(bool(expected));
  }

  // ---------------------------------------------------------------------------------------------
  // Group-chat special case: featureGroupChatV2Enabled is master and fallback per level.
  // Both formats are set to the same value on a level; both are asserted.
  // ---------------------------------------------------------------------------------------------

  @ParameterizedTest(
      name = "Träger v2={0}, formats={1}; agency v2={2}, formats={3} -> formats {4}")
  @CsvSource({
    // master off on a level => formats off on that level, whatever the formats say
    "false, true, true, true, false",
    "true, true, false, true, false",
    "null, true, false, true, false",
    "false, null, null, null, false",
    // master on or unset => explicit format value counts
    "true, true, true, true, true",
    "true, false, true, true, false",
    "true, true, true, false, false",
    "null, null, null, true, true",
    "true, true, null, false, false",
    // missing format falls back to the master on the same level
    "true, null, true, null, true",
    "true, null, false, null, false",
    "false, null, true, null, false",
    "null, null, true, null, true",
    "true, true, null, null, true",
    "null, true, true, null, true",
    "true, null, null, null, true",
  })
  void applyTo_should_treatGroupChatV2AsMasterAndFallbackForBothFormats(
      String traegerV2,
      String traegerFormats,
      String agencyV2,
      String agencyFormats,
      String expected) {
    var traeger =
        newTraeger()
            .featureGroupChatV2Enabled(bool(traegerV2))
            .featureInternalGroupChatEnabled(bool(traegerFormats))
            .featureSelfHelpGroupsEnabled(bool(traegerFormats));
    var agency =
        new Settings()
            .featureGroupChatV2Enabled(bool(agencyV2))
            .featureInternalGroupChatEnabled(bool(agencyFormats))
            .featureSelfHelpGroupsEnabled(bool(agencyFormats));

    EffectiveAgencySettingsResolver.applyTo(agency, traeger);

    assertThat(agency.getFeatureInternalGroupChatEnabled()).isEqualTo(bool(expected));
    assertThat(agency.getFeatureSelfHelpGroupsEnabled()).isEqualTo(bool(expected));
  }

  @Test
  void applyTo_should_restrictOnlyTheSwitchedOffFormat() {
    var traeger =
        newTraeger()
            .featureGroupChatV2Enabled(true)
            .featureInternalGroupChatEnabled(true)
            .featureSelfHelpGroupsEnabled(true);
    var agency = new Settings().featureSelfHelpGroupsEnabled(false);

    EffectiveAgencySettingsResolver.applyTo(agency, traeger);

    assertThat(agency.getFeatureSelfHelpGroupsEnabled()).isFalse();
    assertThat(agency.getFeatureInternalGroupChatEnabled()).isTrue();
    assertThat(agency.getFeatureGroupChatV2Enabled()).isTrue();
  }

  // ---------------------------------------------------------------------------------------------
  // Degraded input
  // ---------------------------------------------------------------------------------------------

  @Test
  void applyTo_should_keepAgencyValues_whenTraegerSettingsAreUnavailable() {
    var agency =
        new Settings()
            .featureGroupChatV2Enabled(true)
            .featureInternalGroupChatEnabled(false)
            .featureCallsEnabled(false);

    EffectiveAgencySettingsResolver.applyTo(agency, null);

    assertThat(agency.getFeatureGroupChatV2Enabled()).isTrue();
    assertThat(agency.getFeatureInternalGroupChatEnabled()).isFalse();
    assertThat(agency.getFeatureSelfHelpGroupsEnabled()).isTrue();
    assertThat(agency.getFeatureCallsEnabled()).isFalse();
    assertThat(agency.getFeatureVideoCallsEnabled()).isNull();
  }

  @Test
  void applyTo_should_doNothing_whenAgencySettingsAreNull() {
    // Must not throw.
    EffectiveAgencySettingsResolver.applyTo(null, newTraeger());
  }

  // ---------------------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------------------

  private static de.caritas.cob.agencyservice.tenantservice.generated.web.model.Settings
      newTraeger() {
    return new de.caritas.cob.agencyservice.tenantservice.generated.web.model.Settings();
  }

  private static Boolean bool(String value) {
    return NULL.equals(value) ? null : Boolean.valueOf(value);
  }

  private static boolean hasBooleanGetter(Class<?> type, String name) {
    try {
      return type.getMethod(name).getReturnType() == Boolean.class;
    } catch (NoSuchMethodException exception) {
      return false;
    }
  }

  private static String capitalize(String flag) {
    return Character.toUpperCase(flag.charAt(0)) + flag.substring(1);
  }

  private static void set(Object settings, String flag, Boolean value) {
    try {
      settings.getClass().getMethod("set" + capitalize(flag), Boolean.class).invoke(settings, value);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(flag, exception);
    }
  }

  private static Boolean get(Object settings, String flag) {
    try {
      return (Boolean) settings.getClass().getMethod("get" + capitalize(flag)).invoke(settings);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(flag, exception);
    }
  }
}
