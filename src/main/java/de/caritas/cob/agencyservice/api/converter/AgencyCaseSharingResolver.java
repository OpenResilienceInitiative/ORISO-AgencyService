package de.caritas.cob.agencyservice.api.converter;

import de.caritas.cob.agencyservice.api.model.AgencyCaseSharingMode;
import de.caritas.cob.agencyservice.api.model.AgencyCaseSharingPolicy;
import de.caritas.cob.agencyservice.api.model.Settings;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.BooleanPermissionPolicy;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.CaseSharingModePermissionPolicy;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.PermissionPolicyMode;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;

/**
 * Effective case sharing mode of one counselling centre (ORISO-TenantService#374): case handover,
 * team counselling or neither, never two at once.
 *
 * <ol>
 *   <li>A requirement (ENFORCED) of the Traeger or platform always wins.
 *   <li>Otherwise the centre's own {@code caseSharingMode}.
 *   <li>Otherwise today's data: under a Traeger with case handover the centre has case handover,
 *       even with {@code teamAgency} (Frank, 2026-11-24: one counsellor keeps the case, team
 *       counselling has to be chosen again); otherwise {@code teamAgency} reads as team
 *       counselling; otherwise the Traeger's choice.
 * </ol>
 */
public final class AgencyCaseSharingResolver {

  static final String CASE_HANDOVER_KEY = "caseHandoverEnabled";

  private AgencyCaseSharingResolver() {}

  public static AgencyCaseSharingPolicy resolve(
      Settings own, boolean teamAgency, RestrictedTenantDTO tenant) {
    Parent parent = parentOf(tenant);
    if (parent.enforced()) {
      return policy(parent.mode(), true, true);
    }
    AgencyCaseSharingMode ownMode = own == null ? null : own.getCaseSharingMode();
    if (ownMode != null) {
      return policy(ownMode, false, false);
    }
    if (parent.mode() != AgencyCaseSharingMode.CASE_HANDOVER && teamAgency) {
      return policy(AgencyCaseSharingMode.TEAM_COUNSELLING, false, false);
    }
    return policy(parent.mode(), false, true);
  }

  /** Writes the effective mode into {@code settings} and returns it. */
  public static AgencyCaseSharingPolicy applyTo(
      Settings settings, boolean teamAgency, RestrictedTenantDTO tenant) {
    var resolved = resolve(settings, teamAgency, tenant);
    if (settings != null) {
      settings.setCaseSharing(resolved);
    }
    return resolved;
  }

  /** Whether new sessions and counsellor assignments of this centre are team ones. */
  public static boolean isTeamCounselling(AgencyCaseSharingPolicy policy) {
    return policy != null && policy.getValue() == AgencyCaseSharingMode.TEAM_COUNSELLING;
  }

  /** The Traeger's requirement, when it differs from {@code requested}; null otherwise. */
  public static AgencyCaseSharingMode conflictingRequirement(
      RestrictedTenantDTO tenant, AgencyCaseSharingMode requested) {
    Parent parent = parentOf(tenant);
    return parent.enforced() && parent.mode() != requested ? parent.mode() : null;
  }

  private record Parent(AgencyCaseSharingMode mode, boolean enforced) {}

  private static Parent parentOf(RestrictedTenantDTO tenant) {
    CaseSharingModePermissionPolicy typed =
        tenant == null || tenant.getCaseSharing() == null ? null : tenant.getCaseSharing().getMode();
    if (typed != null && typed.getValue() != null && typed.getMode() != null) {
      return new Parent(
          AgencyCaseSharingMode.fromValue(typed.getValue().getValue()),
          typed.getMode() == PermissionPolicyMode.ENFORCED);
    }
    // A TenantService before #374 serves only caseHandoverEnabled.
    BooleanPermissionPolicy legacy =
        tenant == null || tenant.getPermissionPolicies() == null
            ? null
            : tenant.getPermissionPolicies().get(CASE_HANDOVER_KEY);
    if (legacy != null && legacy.getValue() != null && legacy.getMode() != null) {
      return new Parent(
          Boolean.TRUE.equals(legacy.getValue())
              ? AgencyCaseSharingMode.CASE_HANDOVER
              : AgencyCaseSharingMode.NONE,
          legacy.getMode() == PermissionPolicyMode.ENFORCED);
    }
    return new Parent(AgencyCaseSharingMode.CASE_HANDOVER, false);
  }

  private static AgencyCaseSharingPolicy policy(
      AgencyCaseSharingMode value, boolean enforced, boolean inherited) {
    return new AgencyCaseSharingPolicy()
        .value(value)
        .mode(
            enforced
                ? AgencyCaseSharingPolicy.ModeEnum.ENFORCED
                : AgencyCaseSharingPolicy.ModeEnum.SUGGESTED)
        .inherited(inherited);
  }
}
