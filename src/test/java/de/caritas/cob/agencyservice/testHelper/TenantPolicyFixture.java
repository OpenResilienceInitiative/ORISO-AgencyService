package de.caritas.cob.agencyservice.testHelper;

import de.caritas.cob.agencyservice.tenantservice.generated.web.model.BooleanPermissionPolicy;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.PermissionPolicyMode;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.TenantPermissionPolicies;
import java.util.Map;

/** Minimal configured authority for integration tests unrelated to policy resolution. */
public final class TenantPolicyFixture {
  private TenantPolicyFixture() {}

  public static TenantPermissionPolicies configuredPolicies(Long storedTenantId) {
    long resolvedId = storedTenantId == null ? 1L : storedTenantId;
    if (resolvedId <= 0) {
      throw new IllegalArgumentException("A fixture tenant must have a positive ID");
    }
    return new TenantPermissionPolicies().tenantId(resolvedId).policies(Map.of(
        "featureVideoCallsEnabled", new BooleanPermissionPolicy()
            .value(false).mode(PermissionPolicyMode.ENFORCED)));
  }
}
