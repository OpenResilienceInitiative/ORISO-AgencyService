package de.caritas.cob.agencyservice.api.converter;

import de.caritas.cob.agencyservice.api.model.Settings;

/**
 * Resolves the effective group-chat values a Beratungsstelle serves on the public agency response
 * (ORISO-AgencyService#293, ADR-013): a Beratungsstelle may only restrict what its Träger allows.
 *
 * <ul>
 *   <li>effective = Träger value AND agency value;
 *   <li>agency unset → Träger value; Träger "off" always wins;
 *   <li>Träger unset (not provided, or tenant lookup failed) → agency value.
 * </ul>
 *
 * <p>Resolved separately for {@code featureGroupChatV2Enabled}, {@code
 * featureInternalGroupChatEnabled} and {@code featureSelfHelpGroupsEnabled}. A missing format flag
 * falls back to {@code featureGroupChatV2Enabled} on the same level (Träger or agency) before the
 * two levels are combined, so this works with TenantService versions that do not yet send the
 * format flags (ORISO-TenantService#250).
 */
public final class EffectiveGroupChatFormatsResolver {

  private EffectiveGroupChatFormatsResolver() {}

  /**
   * Overwrites the three group-chat flags of {@code agencySettings} with their effective values.
   *
   * @param agencySettings the agency's own settings; modified in place, ignored when {@code null}
   * @param traegerSettings the Träger (tenant) settings; {@code null} keeps the agency values
   */
  public static void applyTo(
      Settings agencySettings,
      de.caritas.cob.agencyservice.tenantservice.generated.web.model.Settings traegerSettings) {
    if (agencySettings == null) {
      return;
    }
    var traeger =
        traegerSettings != null
            ? traegerSettings
            : new de.caritas.cob.agencyservice.tenantservice.generated.web.model.Settings();
    // Read both legacy values before anything is overwritten; they are the per-level fallback.
    final Boolean traegerLegacy = traeger.getFeatureGroupChatV2Enabled();
    final Boolean agencyLegacy = agencySettings.getFeatureGroupChatV2Enabled();

    agencySettings.setFeatureInternalGroupChatEnabled(
        combine(
            orElse(traeger.getFeatureInternalGroupChatEnabled(), traegerLegacy),
            orElse(agencySettings.getFeatureInternalGroupChatEnabled(), agencyLegacy)));
    agencySettings.setFeatureSelfHelpGroupsEnabled(
        combine(
            orElse(traeger.getFeatureSelfHelpGroupsEnabled(), traegerLegacy),
            orElse(agencySettings.getFeatureSelfHelpGroupsEnabled(), agencyLegacy)));
    agencySettings.setFeatureGroupChatV2Enabled(combine(traegerLegacy, agencyLegacy));
  }

  private static Boolean combine(Boolean traeger, Boolean agency) {
    // Träger off always wins; otherwise the Träger is on or unset, so the agency value decides
    // and an unset agency inherits the Träger value.
    if (Boolean.FALSE.equals(traeger)) {
      return false;
    }
    return orElse(agency, traeger);
  }

  private static Boolean orElse(Boolean value, Boolean fallback) {
    return value != null ? value : fallback;
  }
}
