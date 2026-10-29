package de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol;

import de.caritas.cob.agencyservice.api.model.AgencyAdminControls;
import de.caritas.cob.agencyservice.api.model.Settings;
import de.caritas.cob.agencyservice.api.service.TenantService;
import de.caritas.cob.agencyservice.api.converter.EffectiveAgencySettingsResolver;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.PermissionPolicyMode;
import org.springframework.security.access.AccessDeniedException;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
    throw new de.caritas.cob.agencyservice.api.exception.httpresponses.BadRequestException(
        "Legacy controls are read-only; use /tenantadmin/{id}/permission-policies");
  }

  public Settings enrichSettingsWithAgencyAdminControls(Settings settings) {
    return enrichSettingsWithAgencyAdminControls(settings, requireTenantId(TenantContext.getCurrentTenant()));
  }

  public Settings enrichSettingsWithAgencyAdminControls(Settings settings, Long tenantId) {
    Settings enrichedSettings = settings != null ? settings : new Settings();
    var policies = tenantService.getPermissionPolicies(requireTenantId(tenantId)).getPolicies();
    enrichedSettings.setAgencyAdminControls(policyMapper.toLegacyControls(policies));
    enrichedSettings.setPermissionPolicies(EffectiveAgencySettingsResolver.resolvePolicies(enrichedSettings, policies));
    return enrichedSettings;
  }

  public void assertSettingsUpdateAllowed(Settings stored, Settings requested, Long tenantId) {
    var changes = EffectiveAgencySettingsResolver.changedOverrides(stored, requested);
    if (changes.isEmpty()) {
      return;
    }
    var policies = EffectiveAgencySettingsResolver.withTransitionFallbacks(
        tenantService.getPermissionPolicies(requireTenantId(tenantId)).getPolicies());
    changes.forEach((field, value) -> {
      var policy = policies == null ? null : policies.get(field);
      if (policy == null || policy.getValue() == null || policy.getMode() == null) {
        throw new IllegalStateException("Tenant permission policy is unavailable: " + field);
      }
      if (policy.getMode() == PermissionPolicyMode.ENFORCED && !value.equals(policy.getValue())) {
        throw new AccessDeniedException("The tenant requires a different value for " + field);
      }
    });
  }

  private Long requireTenantId(Long tenantId) {
    if ((tenantId == null && multitenancyEnabled) || (tenantId != null && tenantId <= 0)) {
      throw new org.springframework.security.access.AccessDeniedException(
          "A concrete tenant is required for permission policies");
    }
    return tenantId;
  }

}
