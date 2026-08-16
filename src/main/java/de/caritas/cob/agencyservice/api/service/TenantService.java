package de.caritas.cob.agencyservice.api.service;

import de.caritas.cob.agencyservice.config.CacheManagerConfig;
import de.caritas.cob.agencyservice.config.apiclient.TenantServiceApiControllerFactory;
import de.caritas.cob.agencyservice.api.service.securityheader.SecurityHeaderSupplier;
import de.caritas.cob.agencyservice.tenantservice.generated.web.TenantControllerApi;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.TenantPermissionPolicies;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantService {

  private final @NonNull TenantServiceApiControllerFactory tenantServiceApiControllerFactory;

  private final @NonNull ApplicationSettingsService applicationSettingsService;
  private final @NonNull SecurityHeaderSupplier securityHeaderSupplier;
  private final @NonNull TenantHeaderSupplier tenantHeaderSupplier;

  @Value("${multitenancy.enabled}")
  private boolean multitenancy;


  @Value("${feature.multitenancy.with.single.domain.enabled}")
  private boolean multitenancyWithSingleDomain;

  @Cacheable(cacheNames = CacheManagerConfig.TENANT_CACHE, key = "#subdomain")
  public RestrictedTenantDTO getRestrictedTenantDataBySubdomain(String subdomain) {
    TenantControllerApi controllerApi = tenantServiceApiControllerFactory.createControllerApi();
    return controllerApi.getRestrictedTenantDataBySubdomain(subdomain, null);
  }

  @Cacheable(cacheNames = CacheManagerConfig.TENANT_CACHE, key = "'id:' + #tenantId")
  public RestrictedTenantDTO getRestrictedTenantDataByTenantId(Long tenantId) {
    TenantControllerApi controllerApi = tenantServiceApiControllerFactory.createControllerApi();
    return controllerApi.getRestrictedTenantDataByTenantId(tenantId);
  }

  public TenantPermissionPolicies getPermissionPolicies(Long tenantId) {
    TenantControllerApi controllerApi = tenantServiceApiControllerFactory.createControllerApi();
    addAuthenticatedHeaders(controllerApi);
    return controllerApi.getTenantPermissionPolicies(tenantId);
  }

  @CacheEvict(cacheNames = CacheManagerConfig.TENANT_CACHE, allEntries = true)
  public TenantPermissionPolicies updatePermissionPolicies(
      Long tenantId, TenantPermissionPolicies policies) {
    TenantControllerApi controllerApi = tenantServiceApiControllerFactory.createControllerApi();
    addAuthenticatedHeaders(controllerApi);
    return controllerApi.updateTenantPermissionPolicies(tenantId, policies);
  }

  private void addAuthenticatedHeaders(TenantControllerApi controllerApi) {
    var headers = securityHeaderSupplier.getKeycloakAndCsrfHttpHeaders();
    tenantHeaderSupplier.addTenantHeader(headers);
    headers.forEach(
        (key, value) -> controllerApi.getApiClient().addDefaultHeader(key, value.getFirst()));
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
