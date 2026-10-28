package de.caritas.cob.agencyservice.api.service;

import de.caritas.cob.agencyservice.config.CacheManagerConfig;
import de.caritas.cob.agencyservice.config.apiclient.TenantServiceApiControllerFactory;
import de.caritas.cob.agencyservice.tenantservice.generated.web.TenantControllerApi;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.TenantPermissionPolicies;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantService {

  private final @NonNull TenantServiceApiControllerFactory tenantServiceApiControllerFactory;

  private final @NonNull ApplicationSettingsService applicationSettingsService;

  @Value("${multitenancy.enabled}")
  private boolean multitenancy;


  @Value("${feature.multitenancy.with.single.domain.enabled}")
  private boolean multitenancyWithSingleDomain;

  @Cacheable(cacheNames = CacheManagerConfig.TENANT_CACHE, key = "#subdomain")
  public RestrictedTenantDTO getRestrictedTenantDataBySubdomain(String subdomain) {
    TenantControllerApi controllerApi = tenantServiceApiControllerFactory.createControllerApi();
    return controllerApi.getRestrictedTenantDataBySubdomain(subdomain, null);
  }

  // Effective policies must reflect a just-saved requirement on the next agency read.
  // Public response lists deduplicate these reads per tenant within that request.
  public RestrictedTenantDTO getRestrictedTenantDataByTenantId(Long tenantId) {
    TenantControllerApi controllerApi = tenantServiceApiControllerFactory.createControllerApi();
    return controllerApi.getRestrictedTenantDataByTenantId(tenantId);
  }

  /** The public tenant projection carries the same resolved boolean policies for every caller. */
  public TenantPermissionPolicies getPermissionPolicies(Long tenantId) {
    var tenant = getRestrictedTenantDataByTenantId(tenantId);
    if (tenant == null || tenant.getPermissionPolicies() == null) {
      throw new de.caritas.cob.agencyservice.api.exception.httpresponses.ServiceUnavailableException(
          de.caritas.cob.agencyservice.api.exception.httpresponses.HttpStatusExceptionReason.SETTINGS_UNAVAILABLE);
    }
    return new TenantPermissionPolicies().tenantId(tenantId).policies(tenant.getPermissionPolicies());
  }

  @Cacheable(cacheNames = CacheManagerConfig.TENANT_CACHE, key = "'single-tenant'")
  public RestrictedTenantDTO getRestrictedTenantDataForSingleTenant() {
    TenantControllerApi controllerApi = tenantServiceApiControllerFactory.createControllerApi();
    return controllerApi.getRestrictedSingleTenancyTenantData();
  }

  public RestrictedTenantDTO getMainTenant() {
    if (multitenancyWithSingleDomain) {
      return getRestrictedTenantDataBySubdomain(getMainTenantSubdomain());
    }
    throw new IllegalStateException("Main tenant can only be retrieved if multitenancy with single domain is enabled.");
  }

  private String getMainTenantSubdomain() {
    return applicationSettingsService
        .getApplicationSettings()
        .getMainTenantSubdomainForSingleDomainMultitenancy().getValue();
  }
}
