package de.caritas.cob.agencyservice.api.converter;

import de.caritas.cob.agencyservice.api.model.Settings;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Resolves the effective feature flags a Beratungsstelle serves on the public agency response
 * (ORISO-AgencyService#293, ADR-013): a Beratungsstelle may only restrict what its Träger allows.
 *
 * <p>The same rule applies uniformly to every permission flag that exists in both the Träger
 * (TenantService) settings and the agency settings — the registry is discovered from the two
 * generated models minus a literal exclusion list ({@link #NOT_CASCADED}), never hand-written
 * per field:
 *
 * <ul>
 *   <li>Träger "off" always wins;
 *   <li>otherwise the agency value applies; an unset agency inherits the Träger value;
 *   <li>Träger unset (not provided, or tenant lookup failed) → agency value.
 * </ul>
 *
 * <p>The only special case are the two group-chat formats {@code featureInternalGroupChatEnabled}
 * and {@code featureSelfHelpGroupsEnabled}: on each level {@code featureGroupChatV2Enabled} is the
 * master switch (master off ⇒ both formats off on that level, whatever the format says) and the
 * fallback for a missing format flag. Both are applied per level before the two levels are
 * combined, so this works with TenantService versions that do not yet send the format flags
 * (ORISO-TenantService#250).
 */
public final class EffectiveAgencySettingsResolver {

  private static final String GROUP_CHAT_MASTER = "featureGroupChatV2Enabled";

  /**
   * Shared flags deliberately NOT cascaded (decision for #293). The eight operational tenant-level
   * flags are read from the tenant by their consumers; {@code null} on an agency is meaningful
   * there and stays {@code null}. {@code showAskerProfile} and {@code isVideoCallAllowed} carry a
   * schema default of {@code false} on both models, so "unset → inherit" cannot be expressed.
   * All ten are served as the agency stores them.
   */
  static final Set<String> NOT_CASCADED =
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
  private static final Set<String> GROUP_CHAT_FORMATS =
      Set.of("featureInternalGroupChatEnabled", "featureSelfHelpGroupsEnabled");

  private record FlagBinding(
      String name, Method traegerGetter, Method agencyGetter, Method agencySetter) {}

  private static final List<FlagBinding> BINDINGS = discoverCommonBooleanFlags();

  private EffectiveAgencySettingsResolver() {}

  /** JSON property names of every flag this resolver covers, sorted. */
  public static List<String> flagNames() {
    return BINDINGS.stream().map(FlagBinding::name).toList();
  }

  /**
   * Overwrites every common flag of {@code agencySettings} with its effective value.
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
    // Read both master values before anything is overwritten.
    final Boolean traegerMaster = traeger.getFeatureGroupChatV2Enabled();
    final Boolean agencyMaster = agencySettings.getFeatureGroupChatV2Enabled();

    for (FlagBinding binding : BINDINGS) {
      Boolean traegerValue = read(binding.traegerGetter(), traeger);
      Boolean agencyValue = read(binding.agencyGetter(), agencySettings);
      if (GROUP_CHAT_FORMATS.contains(binding.name())) {
        traegerValue = formatOnLevel(traegerValue, traegerMaster);
        agencyValue = formatOnLevel(agencyValue, agencyMaster);
      }
      write(binding.agencySetter(), agencySettings, combine(traegerValue, agencyValue));
    }
  }

  private static Boolean combine(Boolean traeger, Boolean agency) {
    // Träger off always wins; otherwise the Träger is on or unset, so the agency value decides
    // and an unset agency inherits the Träger value.
    if (Boolean.FALSE.equals(traeger)) {
      return false;
    }
    return orElse(agency, traeger);
  }

  private static Boolean formatOnLevel(Boolean format, Boolean master) {
    if (Boolean.FALSE.equals(master)) {
      return false;
    }
    return orElse(format, master);
  }

  private static Boolean orElse(Boolean value, Boolean fallback) {
    return value != null ? value : fallback;
  }

  /**
   * Every getter returning {@code Boolean} that exists on both models, minus {@link #NOT_CASCADED}.
   * This mirrors the TenantService permission registry ({@code PermissionFeature}); a new shared
   * flag lands in the cascade unless it is listed there, and the coverage test pins the result.
   */
  private static List<FlagBinding> discoverCommonBooleanFlags() {
    Class<?> traegerClass = de.caritas.cob.agencyservice.tenantservice.generated.web.model.Settings.class;
    List<FlagBinding> bindings = new ArrayList<>();
    for (Method agencyGetter : Settings.class.getMethods()) {
      if (!isBooleanGetter(agencyGetter)) {
        continue;
      }
      String suffix = agencyGetter.getName().substring(3);
      Method traegerGetter = findBooleanGetter(traegerClass, agencyGetter.getName());
      Method agencySetter = findSetter(Settings.class, "set" + suffix);
      if (traegerGetter == null || agencySetter == null) {
        continue;
      }
      String name = Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
      if (NOT_CASCADED.contains(name)) {
        continue;
      }
      bindings.add(new FlagBinding(name, traegerGetter, agencyGetter, agencySetter));
    }
    bindings.sort(Comparator.comparing(FlagBinding::name));
    if (!bindings.stream().map(FlagBinding::name).toList().contains(GROUP_CHAT_MASTER)) {
      throw new IllegalStateException(GROUP_CHAT_MASTER + " missing from both settings models");
    }
    return List.copyOf(bindings);
  }

  private static boolean isBooleanGetter(Method method) {
    return method.getName().startsWith("get")
        && method.getParameterCount() == 0
        && method.getReturnType() == Boolean.class;
  }

  private static Method findBooleanGetter(Class<?> type, String name) {
    try {
      Method method = type.getMethod(name);
      return method.getReturnType() == Boolean.class ? method : null;
    } catch (NoSuchMethodException exception) {
      return null;
    }
  }

  private static Method findSetter(Class<?> type, String name) {
    try {
      return type.getMethod(name, Boolean.class);
    } catch (NoSuchMethodException exception) {
      return null;
    }
  }

  private static Boolean read(Method getter, Object target) {
    try {
      return (Boolean) getter.invoke(target);
    } catch (IllegalAccessException | InvocationTargetException exception) {
      throw new IllegalStateException("Cannot read " + getter.getName(), exception);
    }
  }

  private static void write(Method setter, Object target, Boolean value) {
    try {
      setter.invoke(target, value);
    } catch (IllegalAccessException | InvocationTargetException exception) {
      throw new IllegalStateException("Cannot write " + setter.getName(), exception);
    }
  }
}
