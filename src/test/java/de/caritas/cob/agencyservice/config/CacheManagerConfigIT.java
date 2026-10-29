package de.caritas.cob.agencyservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.service.ApplicationSettingsService;
import de.caritas.cob.agencyservice.api.service.TenantService;
import de.caritas.cob.agencyservice.api.service.TenantHeaderSupplier;
import de.caritas.cob.agencyservice.api.service.securityheader.SecurityHeaderSupplier;
import de.caritas.cob.agencyservice.config.apiclient.TenantServiceApiControllerFactory;
import de.caritas.cob.agencyservice.tenantservice.generated.web.TenantControllerApi;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration test that boots the <b>real</b> {@link CacheManagerConfig} — not a test-local
 * cache manager — and asserts that a Spring {@link CacheManager} SPI bean actually exists and
 * resolves every named cache, and that a real {@code @Cacheable} method is cached.
 *
 * <p>This is the test the "Test Quality Audit 2026-07-04 — live AgencyService ehcache/SB4 finding"
 * flagged as missing: the existing {@code *CacheTest} classes each supply their own
 * {@code ConcurrentMapCacheManager} and therefore never exercise the production
 * {@code CacheManagerConfig}. Under Spring Boot 4 that config wired only a
 * {@code net.sf.ehcache.CacheManager} (ehcache2) bean, but the Spring bridge
 * {@code org.springframework.cache.ehcache.EhCacheCacheManager} was removed in Spring Framework 6,
 * so no Spring {@link CacheManager} SPI bean existed and every {@code @Cacheable} call threw
 * {@code IllegalArgumentException: Cannot find cache named ...} at runtime.
 */
@SpringBootTest(
    classes = {
      CacheManagerConfig.class,
      TenantService.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(
    properties = {
      "multitenancy.enabled=false",
      "feature.multitenancy.with.single.domain.enabled=false",
      "cache.consulting.type.configuration.maxEntriesLocalHeap=1000",
      "cache.consulting.type.configuration.eternal=false",
      "cache.consulting.type.configuration.timeToIdleSeconds=300",
      "cache.consulting.type.configuration.timeToLiveSeconds=600",
      "cache.tenant.configuration.maxEntriesLocalHeap=1000",
      "cache.tenant.configuration.eternal=false",
      "cache.tenant.configuration.timeToIdleSeconds=300",
      "cache.tenant.configuration.timeToLiveSeconds=600",
      "cache.topic.configuration.maxEntriesLocalHeap=1000",
      "cache.topic.configuration.eternal=false",
      "cache.topic.configuration.timeToIdleSeconds=300",
      "cache.topic.configuration.timeToLiveSeconds=600",
      "cache.applicationsettings.configuration.maxEntriesLocalHeap=100",
      "cache.applicationsettings.configuration.eternal=false",
      "cache.applicationsettings.configuration.timeToIdleSeconds=300",
      "cache.applicationsettings.configuration.timeToLiveSeconds=600"
    })
class CacheManagerConfigIT {

  @Autowired private CacheManager cacheManager;

  @Autowired private TenantService tenantService;

  @MockitoBean private TenantServiceApiControllerFactory tenantServiceApiControllerFactory;

  @MockitoBean private ApplicationSettingsService applicationSettingsService;

  @MockitoBean private SecurityHeaderSupplier securityHeaderSupplier;

  @MockitoBean private TenantHeaderSupplier tenantHeaderSupplier;

  @Test
  void springCacheManagerSpiBean_shouldExistAndResolveEveryNamedCache() {
    assertThat(cacheManager).isNotNull();
    assertThat(cacheManager.getCache(CacheManagerConfig.CONSULTING_TYPE_CACHE)).isNotNull();
    assertThat(cacheManager.getCache(CacheManagerConfig.TENANT_CACHE)).isNotNull();
    assertThat(cacheManager.getCache(CacheManagerConfig.TOPICS_CACHE)).isNotNull();
    assertThat(cacheManager.getCache(CacheManagerConfig.APPLICATION_SETTINGS_CACHE)).isNotNull();
  }

  @Test
  void cacheableMethod_shouldServeSecondCallFromCache() {
    TenantControllerApi controllerApi = org.mockito.Mockito.mock(TenantControllerApi.class);
    when(tenantServiceApiControllerFactory.createControllerApi()).thenReturn(controllerApi);
    when(controllerApi.getRestrictedSingleTenancyTenantData())
        .thenReturn(new RestrictedTenantDTO().id(1L));

    tenantService.getRestrictedTenantDataForSingleTenant();
    tenantService.getRestrictedTenantDataForSingleTenant();

    // Second invocation must be served from the cache, so the collaborator is hit only once.
    verify(controllerApi, times(1)).getRestrictedSingleTenancyTenantData();
  }
}
