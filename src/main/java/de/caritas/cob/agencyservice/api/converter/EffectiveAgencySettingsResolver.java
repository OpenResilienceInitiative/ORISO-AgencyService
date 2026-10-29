package de.caritas.cob.agencyservice.api.converter;

import de.caritas.cob.agencyservice.api.model.Settings;
import de.caritas.cob.agencyservice.api.model.AgencyPermissionPolicy;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.BooleanPermissionPolicy;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.PermissionPolicyMode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.Objects;

/**
 * Resolves agency leaf values against typed parent policies. Enforced values always win;
 * recommendations may be overridden. The boolean-only overload supports older tenant responses.
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

  private static final List<FlagBinding> BINDINGS = discoverBooleanFlags(true);
  private static final Map<String, FlagBinding> AGENCY_BINDINGS = discoverBooleanFlags(false)
      .stream().collect(Collectors.toUnmodifiableMap(FlagBinding::name, binding -> binding));

  private EffectiveAgencySettingsResolver() {}

  /** JSON property names of every flag this resolver covers, sorted. */
  public static List<String> flagNames() {
    return BINDINGS.stream().map(FlagBinding::name).toList();
  }

  /** Canonical policies are per feature. A recommendation may be overridden at the leaf. */
  public static Map<String, AgencyPermissionPolicy> resolvePolicies(
      Settings agencySettings, Map<String, BooleanPermissionPolicy> parentPolicies) {
    Map<String, AgencyPermissionPolicy> resolved = new LinkedHashMap<>();
    if (parentPolicies == null) {
      return resolved;
    }
    withTransitionFallbacks(parentPolicies).forEach((field, parent) -> {
      if (parent == null || parent.getValue() == null || parent.getMode() == null) {
        throw new IllegalStateException("Incomplete tenant permission policy: " + field);
      }
      FlagBinding binding = AGENCY_BINDINGS.get(field);
      Boolean ownValue = binding != null && agencySettings != null
          ? read(binding.agencyGetter(), agencySettings) : null;
      boolean enforced = parent.getMode() == PermissionPolicyMode.ENFORCED;
      resolved.put(field, new AgencyPermissionPolicy()
          .value(enforced || ownValue == null ? parent.getValue() : ownValue)
          .mode(enforced ? AgencyPermissionPolicy.ModeEnum.ENFORCED : AgencyPermissionPolicy.ModeEnum.SUGGESTED)
          .inherited(enforced || ownValue == null));
    });
    return resolved;
  }

  /** Older providers may omit split formats; an explicit child policy always takes precedence. */
  public static Map<String, BooleanPermissionPolicy> withTransitionFallbacks(
      Map<String, BooleanPermissionPolicy> policies) {
    Map<String, BooleanPermissionPolicy> expanded = new LinkedHashMap<>(policies);
    var group = policies.get(GROUP_CHAT_MASTER);
    if (group != null) {
      GROUP_CHAT_FORMATS.forEach(field -> expanded.putIfAbsent(field, group));
    }
    return expanded;
  }

  /** Only explicit changed leaf values need authorization; unchanged dormant values may be echoed. */
  public static Map<String, Boolean> changedOverrides(Settings stored, Settings requested) {
    Map<String, Boolean> changes = new LinkedHashMap<>();
    if (requested == null) {
      return changes;
    }
    AGENCY_BINDINGS.forEach((field, binding) -> {
      Boolean next = read(binding.agencyGetter(), requested);
      Boolean previous = stored == null ? null : read(binding.agencyGetter(), stored);
      if (next != null && !Objects.equals(next, previous)) {
        changes.put(field, next);
      }
    });
    return changes;
  }

  /** Applies the same resolved values served to Admin; legacy booleans are fallback only. */
  public static void applyTo(Settings agencySettings,
      de.caritas.cob.agencyservice.tenantservice.generated.web.model.Settings parentSettings,
      Map<String, BooleanPermissionPolicy> parentPolicies) {
    if (agencySettings == null) {
      return;
    }
    if (parentPolicies == null || parentPolicies.isEmpty()) {
      applyTo(agencySettings, parentSettings);
      return;
    }
    var policies = resolvePolicies(agencySettings, parentPolicies);
    for (FlagBinding binding : AGENCY_BINDINGS.values()) {
      var policy = policies.get(binding.name());
      if (policy != null) {
        write(binding.agencySetter(), agencySettings, policy.getValue());
      }
    }
    agencySettings.setPermissionPolicies(policies);
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
  private static List<FlagBinding> discoverBooleanFlags(boolean requireParentGetter) {
    Class<?> traegerClass = de.caritas.cob.agencyservice.tenantservice.generated.web.model.Settings.class;
    List<FlagBinding> bindings = new ArrayList<>();
    for (Method agencyGetter : Settings.class.getMethods()) {
      if (!isBooleanGetter(agencyGetter)) {
        continue;
      }
      String suffix = agencyGetter.getName().substring(3);
      Method traegerGetter = findBooleanGetter(traegerClass, agencyGetter.getName());
      Method agencySetter = findSetter(Settings.class, "set" + suffix);
      if ((requireParentGetter && traegerGetter == null) || agencySetter == null) {
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
