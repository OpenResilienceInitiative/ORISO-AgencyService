package de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol;

import de.caritas.cob.agencyservice.api.exception.httpresponses.BadRequestException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.HttpStatusExceptionReason;
import de.caritas.cob.agencyservice.api.exception.httpresponses.ServiceUnavailableException;
import de.caritas.cob.agencyservice.api.model.AgencyAdminControls;
import de.caritas.cob.agencyservice.api.model.AgencyCaseSharingMode;
import de.caritas.cob.agencyservice.api.model.AgencyPermissionPolicy;
import de.caritas.cob.agencyservice.api.model.Settings;
import de.caritas.cob.agencyservice.api.service.TenantService;
import de.caritas.cob.agencyservice.api.converter.AgencyCaseSharingResolver;
import de.caritas.cob.agencyservice.api.converter.EffectiveAgencySettingsResolver;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgencyAdminControlsService {

  private final @NonNull TenantService tenantService;
  private final @NonNull AgencyPermissionPolicyMapper policyMapper;

  @Value("${multitenancy.enabled}")
  private boolean multitenancyEnabled = true;

  public AgencyAdminControls getControls() {
    return getControls(requireTenantId(TenantContext.getCurrentTenant()));
  }

  public AgencyAdminControls getControls(Long tenantId) {
    return policyMapper.toLegacyControls(
        tenantService.getPermissionPolicies(requireTenantId(tenantId)).getPolicies());
  }

  public AgencyAdminControls getResolvedControls(Long tenantId) {
    return policyMapper.toLegacyControls(
        tenantService
            .getRestrictedTenantDataForPermissionPolicies(requireTenantId(tenantId))
            .getPermissionPolicies());
  }

  public AgencyAdminControls updateControls(AgencyAdminControls agencyAdminControls) {
    return updateControls(requireTenantId(TenantContext.getCurrentTenant()), agencyAdminControls);
  }

  public AgencyAdminControls updateControls(
      Long tenantId, AgencyAdminControls agencyAdminControls) {
    requireTenantId(tenantId);
    throw new BadRequestException(
        "Legacy controls are read-only; use /tenantadmin/{id}/permission-policies");
  }

  public Settings enrichSettingsWithAgencyAdminControls(Settings settings) {
    return enrichSettingsWithAgencyAdminControls(settings, requireTenantId(TenantContext.getCurrentTenant()));
  }

  public Settings enrichSettingsWithAgencyAdminControls(Settings settings, Long tenantId) {
    return enrichSettingsWithAgencyAdminControls(settings, tenantId, false);
  }

  /** As above, plus the centre's effective case sharing mode (#374), read from {@code teamAgency}. */
  public Settings enrichSettingsWithAgencyAdminControls(
      Settings settings, Long tenantId, boolean teamAgency) {
    Settings enrichedSettings = settings != null ? settings : new Settings();
    var tenant = tenantService.getRestrictedTenantDataForPermissionPolicies(requireTenantId(tenantId));
    if (tenant == null || tenant.getPermissionPolicies() == null) {
      throw new ServiceUnavailableException(HttpStatusExceptionReason.SETTINGS_UNAVAILABLE);
    }
    var policies = tenant.getPermissionPolicies();
    enrichedSettings.setAgencyAdminControls(policyMapper.toLegacyControls(policies));
    enrichedSettings.setPermissionPolicies(EffectiveAgencySettingsResolver.resolvePolicies(enrichedSettings, tenant.getSettings(), policies));
    AgencyCaseSharingResolver.applyTo(enrichedSettings, teamAgency, tenant);
    return enrichedSettings;
  }

  public void assertSettingsUpdateAllowed(Settings stored, Settings requested, Long tenantId) {
    var changes = EffectiveAgencySettingsResolver.changedOverrides(stored, requested);
    var caseSharingChange = changedCaseSharingMode(stored, requested);
    if (changes.isEmpty() && caseSharingChange == null) {
      return;
    }
    var tenant = tenantService.getRestrictedTenantDataForPermissionPolicies(requireTenantId(tenantId));
    if (caseSharingChange != null) {
      if (tenant == null) {
        throw new ServiceUnavailableException(HttpStatusExceptionReason.SETTINGS_UNAVAILABLE);
      }
      if (AgencyCaseSharingResolver.conflictingRequirement(tenant, caseSharingChange) != null) {
        throw new AccessDeniedException("The tenant requires a different value for caseSharingMode");
      }
      if (changes.isEmpty()) {
        return;
      }
    }
    if (tenant == null || tenant.getPermissionPolicies() == null) {
      throw new ServiceUnavailableException(HttpStatusExceptionReason.SETTINGS_UNAVAILABLE);
    }
    // Authorize the same enforced value exposed in Admin/public reads, including direct-link caps.
    var policies = EffectiveAgencySettingsResolver.resolvePolicies(
        null, tenant.getSettings(), tenant.getPermissionPolicies());
    changes.forEach((field, value) -> {
      var policy = policies == null ? null : policies.get(field);
      if (policy == null || policy.getValue() == null || policy.getMode() == null) {
        throw new ServiceUnavailableException(HttpStatusExceptionReason.SETTINGS_UNAVAILABLE);
      }
      if (policy.getMode() == AgencyPermissionPolicy.ModeEnum.ENFORCED && !value.equals(policy.getValue())) {
        throw new AccessDeniedException("The tenant requires a different value for " + field);
      }
    });
  }

  /** The centre's new own case sharing choice (#374), or null when the request leaves it as it is. */
  private static AgencyCaseSharingMode changedCaseSharingMode(Settings stored, Settings requested) {
    var next = requested == null ? null : requested.getCaseSharingMode();
    var previous = stored == null ? null : stored.getCaseSharingMode();
    return next != null && next != previous ? next : null;
  }

  private Long requireTenantId(Long tenantId) {
    if ((tenantId == null && multitenancyEnabled) || (tenantId != null && tenantId <= 0)) {
      throw new AccessDeniedException(
          "A concrete tenant is required for permission policies");
    }
    return tenantId;
  }

}
