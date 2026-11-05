package de.caritas.cob.agencyservice.api.service;

import de.caritas.cob.agencyservice.api.exception.httpresponses.HttpStatusExceptionReason;
import de.caritas.cob.agencyservice.api.exception.httpresponses.ServiceUnavailableException;
import de.caritas.cob.agencyservice.config.CacheManagerConfig;
import de.caritas.cob.agencyservice.config.apiclient.TenantServiceApiControllerFactory;
import de.caritas.cob.agencyservice.tenantservice.generated.web.TenantControllerApi;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.TenantPermissionPolicies;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
@Slf4j
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
    if (tenantId == null || tenantId <= 0) {
      throw new AccessDeniedException("A concrete tenant is required for permission policies");
    }
    TenantControllerApi controllerApi = tenantServiceApiControllerFactory.createControllerApi();
    var tenant = controllerApi.getRestrictedTenantDataByTenantId(tenantId);
    if (tenant != null && tenant.getId() != null && !tenantId.equals(tenant.getId())) {
      throw settingsUnavailable();
    }
    return tenant;
  }

  /** The public tenant projection carries the same resolved boolean policies for every caller. */
  public TenantPermissionPolicies getPermissionPolicies(Long tenantId) {
    var tenant = getRestrictedTenantDataForPermissionPolicies(tenantId);
    if (tenant == null || tenant.getPermissionPolicies() == null) {
      throw settingsUnavailable();
    }
    return new TenantPermissionPolicies().tenantId(tenantId != null ? tenantId : tenant.getId())
        .policies(tenant.getPermissionPolicies());
  }

  /** Fresh authority for stored legacy agencies without a tenant, only in single-tenant mode. */
  public RestrictedTenantDTO getRestrictedTenantDataForPermissionPolicies(Long tenantId) {
    if (tenantId != null) {
      // Preserve the older public projection contract for explicit, positive tenant IDs.
      return getRestrictedTenantDataByTenantId(tenantId);
    }
    if (multitenancy) {
      throw new AccessDeniedException("A concrete tenant is required for permission policies");
    }
    RestrictedTenantDTO tenant;
    try {
      var api = tenantServiceApiControllerFactory.createControllerApi();
      if (multitenancyWithSingleDomain) {
        var applicationSettings = applicationSettingsService.getApplicationSettings();
        var main = applicationSettings == null ? null
            : applicationSettings.getMainTenantSubdomainForSingleDomainMultitenancy();
        if (main == null || main.getValue() == null || main.getValue().isBlank()) {
          throw settingsUnavailable();
        }
        tenant = api.getRestrictedTenantDataBySubdomain(main.getValue(), null);
      } else {
        tenant = api.getRestrictedSingleTenancyTenantData();
      }
    } catch (RestClientException exception) {
      log.warn("Single-tenant permission policies unavailable: type={} status={}",
          exception.getClass().getSimpleName(),
          exception instanceof RestClientResponseException response
              ? response.getStatusCode().value() : "unavailable");
      throw settingsUnavailable();
    }
    if (tenant == null || tenant.getId() == null || tenant.getId() <= 0
        || tenant.getSettings() == null || tenant.getPermissionPolicies() == null
        || tenant.getPermissionPolicies().isEmpty()
        || tenant.getPermissionPolicies().values().stream().anyMatch(policy ->
            policy == null || policy.getValue() == null || policy.getMode() == null)) {
      throw settingsUnavailable();
    }
    return tenant;
  }

  /** The non-cascaded topic registration flag retains the existing tenant cache lifetime. */
  @Cacheable(cacheNames = CacheManagerConfig.TENANT_CACHE, key = "'topics-registration:' + #tenantId")
  public boolean isTopicsInRegistrationEnabled(Long tenantId) {
    var tenant = getRestrictedTenantDataByTenantId(tenantId);
    return tenant != null && tenant.getSettings() != null
        && Boolean.TRUE.equals(tenant.getSettings().getTopicsInRegistrationEnabled());
  }

  private ServiceUnavailableException settingsUnavailable() {
    return new ServiceUnavailableException(HttpStatusExceptionReason.SETTINGS_UNAVAILABLE);
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
