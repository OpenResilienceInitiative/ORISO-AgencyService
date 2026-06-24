package de.caritas.cob.agencyservice.api.service;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.config.CacheManagerConfig;
import de.caritas.cob.agencyservice.config.apiclient.TenantServiceApiControllerFactory;
import de.caritas.cob.agencyservice.tenantservice.generated.web.TenantControllerApi;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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

  @MockBean private TenantServiceApiControllerFactory tenantServiceApiControllerFactory;

  @MockBean private ApplicationSettingsService applicationSettingsService;

  @Autowired private TenantService tenantService;

  @Test
  void getRestrictedTenantDataByTenantId_shouldCacheResult() {
    TenantControllerApi controllerApi = org.mockito.Mockito.mock(TenantControllerApi.class);
    when(tenantServiceApiControllerFactory.createControllerApi()).thenReturn(controllerApi);
    when(controllerApi.getRestrictedTenantDataByTenantId(TENANT_ID))
        .thenReturn(new RestrictedTenantDTO().id(TENANT_ID));

    tenantService.getRestrictedTenantDataByTenantId(TENANT_ID);
    tenantService.getRestrictedTenantDataByTenantId(TENANT_ID);

    verify(controllerApi, times(1)).getRestrictedTenantDataByTenantId(TENANT_ID);
  }
}
