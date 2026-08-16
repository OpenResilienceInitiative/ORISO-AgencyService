package de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol;

import de.caritas.cob.agencyservice.api.model.AgencyAdminControls;
import de.caritas.cob.agencyservice.api.model.Settings;
import de.caritas.cob.agencyservice.api.service.TenantService;
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
    if (isSingleTenantWithoutTenantId(tenantId)) {
      return new AgencyAdminControls();
    }
    return policyMapper.toLegacyControls(
        tenantService.getPermissionPolicies(requireTenantId(tenantId)).getPolicies());
  }

  public AgencyAdminControls getResolvedControls(Long tenantId) {
    if (isSingleTenantWithoutTenantId(tenantId)) {
      return new AgencyAdminControls();
    }
    return policyMapper.toLegacyControls(
        tenantService
            .getRestrictedTenantDataByTenantId(requireTenantId(tenantId))
            .getPermissionPolicies());
  }

  public AgencyAdminControls updateControls(AgencyAdminControls agencyAdminControls) {
    return updateControls(requireTenantId(TenantContext.getCurrentTenant()), agencyAdminControls);
  }

  public AgencyAdminControls updateControls(
      Long tenantId, AgencyAdminControls agencyAdminControls) {
    Long effectiveTenantId = requireTenantId(tenantId);
    var existing = tenantService.getPermissionPolicies(effectiveTenantId);
    var updated = policyMapper.applyLegacyUpdate(existing, agencyAdminControls);
    return policyMapper.toLegacyControls(
        tenantService.updatePermissionPolicies(effectiveTenantId, updated).getPolicies());
  }

  public Settings enrichSettingsWithAgencyAdminControls(Settings settings) {
    return enrichSettingsWithAgencyAdminControls(settings, requireTenantId(TenantContext.getCurrentTenant()));
  }

  public Settings enrichSettingsWithAgencyAdminControls(Settings settings, Long tenantId) {
    Settings enrichedSettings = settings != null ? settings : new Settings();
    enrichedSettings.setAgencyAdminControls(getControls(tenantId));
    return enrichedSettings;
  }

  private Long requireTenantId(Long tenantId) {
    if (tenantId == null || tenantId <= 0) {
      throw new org.springframework.security.access.AccessDeniedException(
          "A concrete tenant is required for permission policies");
    }
    return tenantId;
  }

  private boolean isSingleTenantWithoutTenantId(Long tenantId) {
    return !multitenancyEnabled && (tenantId == null || tenantId <= 0);
  }
}
