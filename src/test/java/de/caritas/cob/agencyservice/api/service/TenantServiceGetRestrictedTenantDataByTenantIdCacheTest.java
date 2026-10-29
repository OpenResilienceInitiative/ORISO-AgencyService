package de.caritas.cob.agencyservice.api.service;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.config.CacheManagerConfig;
import de.caritas.cob.agencyservice.config.apiclient.TenantServiceApiControllerFactory;
import de.caritas.cob.agencyservice.api.service.securityheader.SecurityHeaderSupplier;
import de.caritas.cob.agencyservice.tenantservice.generated.web.TenantControllerApi;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(
    classes = {
      TenantService.class,
      TenantServiceGetRestrictedTenantDataByTenantIdCacheTest.CacheTestConfig.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(
    properties = {
      "multitenancy.enabled=false",
      "feature.multitenancy.with.single.domain.enabled=false"
    })
class TenantServiceGetRestrictedTenantDataByTenantIdCacheTest {

  private static final Long TENANT_ID = 42L;

  @Configuration
  @EnableCaching
  static class CacheTestConfig {

    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager(CacheManagerConfig.TENANT_CACHE);
    }
  }

  @MockitoBean private TenantServiceApiControllerFactory tenantServiceApiControllerFactory;

  @MockitoBean private ApplicationSettingsService applicationSettingsService;
  @MockitoBean private SecurityHeaderSupplier securityHeaderSupplier;
  @MockitoBean private TenantHeaderSupplier tenantHeaderSupplier;

  @Autowired private TenantService tenantService;

  @Test
  void getRestrictedTenantDataByTenantId_shouldReadFreshPoliciesAfterAParentChange() {
    TenantControllerApi controllerApi = org.mockito.Mockito.mock(TenantControllerApi.class);
    when(tenantServiceApiControllerFactory.createControllerApi()).thenReturn(controllerApi);
    when(controllerApi.getRestrictedTenantDataByTenantId(TENANT_ID))
        .thenReturn(
            new RestrictedTenantDTO(java.util.Map.of("featureVideoCallsEnabled",
                new de.caritas.cob.agencyservice.tenantservice.generated.web.model.BooleanPermissionPolicy()
                    .value(true).mode(de.caritas.cob.agencyservice.tenantservice.generated.web.model.PermissionPolicyMode.SUGGESTED)), null),
            new RestrictedTenantDTO(java.util.Map.of("featureVideoCallsEnabled",
                new de.caritas.cob.agencyservice.tenantservice.generated.web.model.BooleanPermissionPolicy()
                    .value(false).mode(de.caritas.cob.agencyservice.tenantservice.generated.web.model.PermissionPolicyMode.ENFORCED)), null));

    var before = tenantService.getRestrictedTenantDataByTenantId(TENANT_ID);
    var after = tenantService.getRestrictedTenantDataByTenantId(TENANT_ID);

    org.assertj.core.api.Assertions.assertThat(before.getPermissionPolicies().get("featureVideoCallsEnabled").getValue()).isTrue();
    org.assertj.core.api.Assertions.assertThat(after.getPermissionPolicies().get("featureVideoCallsEnabled").getValue()).isFalse();
    verify(controllerApi, times(2)).getRestrictedTenantDataByTenantId(TENANT_ID);
  }
}
