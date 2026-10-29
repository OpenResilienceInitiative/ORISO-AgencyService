package de.caritas.cob.agencyservice.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol.AgencyAdminControlsService;
import de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol.AgencyPermissionPolicyMapper;
import de.caritas.cob.agencyservice.api.exception.httpresponses.ServiceUnavailableException;
import de.caritas.cob.agencyservice.api.model.Settings;
import de.caritas.cob.agencyservice.config.CacheManagerConfig;
import de.caritas.cob.agencyservice.config.apiclient.TenantServiceApiControllerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(classes = {TenantService.class, TenantServiceApiControllerFactory.class,
    AgencyAdminControlsService.class, AgencyPermissionPolicyMapper.class,
    TenantPolicySingleModeHttpTest.Config.class}, properties = {
      "multitenancy.enabled=false", "feature.multitenancy.with.single.domain.enabled=false",
      "tenant.service.api.url=http://tenant.test"},
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TenantPolicySingleModeHttpTest {
  private static final String SINGLE = "http://tenant.test/tenant/public/single";
  @Autowired private TenantService tenants;
  @Autowired private AgencyAdminControlsService controls;
  @Autowired private RestTemplate restTemplate;
  @Autowired private CacheManager cacheManager;
  @MockitoBean private ApplicationSettingsService applicationSettingsService;
  private MockRestServiceServer server;

  @Configuration
  @EnableCaching
  static class Config {
    @Bean RestTemplate restTemplate() {
      return new RestTemplate();
    }

    @Bean CacheManager cacheManager() {
      return new ConcurrentMapCacheManager(CacheManagerConfig.TENANT_CACHE);
    }
  }

  @BeforeEach
  void setup() {
    server = MockRestServiceServer.bindTo(restTemplate).build();
    cacheManager.getCache(CacheManagerConfig.TENANT_CACHE).clear();
    ReflectionTestUtils.setField(tenants, "multitenancy", false);
    ReflectionTestUtils.setField(tenants, "multitenancyWithSingleDomain", false);
    ReflectionTestUtils.setField(controls, "multitenancyEnabled", false);
  }

  @Test
  void legacyNullTenantReadsAndWritesUseFreshConfiguredCanonicalAuthority() {
    expectSingle("SUGGESTED", false);
    expectSingle("ENFORCED", false);
    expectSingle("ENFORCED", false);
    var raw = new Settings().featureVideoCallsOneOnOneChatsEnabled(true);
    assertThatCode(() -> controls.assertSettingsUpdateAllowed(new Settings(), raw, null))
        .doesNotThrowAnyException();
    var enriched = controls.enrichSettingsWithAgencyAdminControls(raw, null);
    assertThat(enriched.getFeatureVideoCallsOneOnOneChatsEnabled()).isTrue();
    assertThat(enriched.getPermissionPolicies().get("featureVideoCallsOneOnOneChatsEnabled")
        .getValue()).isFalse();
    assertThatThrownBy(() -> controls.assertSettingsUpdateAllowed(new Settings(), raw, null))
        .isInstanceOf(AccessDeniedException.class);
    server.verify();
  }

  @Test
  void nullSingleTenantCannotFallBackToRawAgencySettingsWhenCanonicalMapIsMissing() {
    server.expect(requestTo(SINGLE)).andRespond(withSuccess(
        "{\"id\":7,\"settings\":{}}", MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> controls.enrichSettingsWithAgencyAdminControls(new Settings(), null))
        .isInstanceOf(ServiceUnavailableException.class);
    server.verify();
  }

  @Test
  void topicCachingDoesNotCachePolicyBearingReads() {
    server.expect(requestTo("http://tenant.test/tenant/public/id/7")).andRespond(withSuccess(
        "{\"id\":7,\"settings\":{\"topicsInRegistrationEnabled\":true}}", MediaType.APPLICATION_JSON));
    server.expect(requestTo("http://tenant.test/tenant/public/id/7")).andRespond(withSuccess(
        "{\"id\":7,\"settings\":{\"topicsInRegistrationEnabled\":false}}", MediaType.APPLICATION_JSON));
    server.expect(requestTo("http://tenant.test/tenant/public/id/7")).andRespond(withSuccess(
        "{\"id\":7,\"settings\":{\"topicsInRegistrationEnabled\":true}}", MediaType.APPLICATION_JSON));
    final var agencies = org.mockito.Mockito.mock(AgencyService.class, org.mockito.Mockito.CALLS_REAL_METHODS);
    ReflectionTestUtils.setField(agencies, "tenantService", tenants);
    ReflectionTestUtils.setField(agencies, "multitenancy", true);
    de.caritas.cob.agencyservice.api.tenant.TenantContext.setCurrentTenant(7L);
    Object first;
    Object cached;
    try {
      first = ReflectionTestUtils.invokeMethod(agencies, "isTopicFeatureActivatedInRegistration");
      cached = ReflectionTestUtils.invokeMethod(agencies, "isTopicFeatureActivatedInRegistration");
    } finally {
      de.caritas.cob.agencyservice.api.tenant.TenantContext.clear();
    }
    assertThat(first).isEqualTo(true);
    assertThat(cached).isEqualTo(true);
    assertThat(tenants.getRestrictedTenantDataByTenantId(7L).getSettings()
        .getTopicsInRegistrationEnabled()).isFalse();
    assertThat(tenants.getRestrictedTenantDataByTenantId(7L).getSettings()
        .getTopicsInRegistrationEnabled()).isTrue();
    server.verify();
  }

  @Test
  void nonPositiveIdsAndMultitenantAbsenceNeverResolveSingleTenant() {
    for (Long id : new Long[] {0L, -1L}) {
      assertThatThrownBy(() -> controls.getControls(id)).isInstanceOf(AccessDeniedException.class);
    }
    ReflectionTestUtils.setField(controls, "multitenancyEnabled", true);
    assertThatThrownBy(() -> controls.getControls(null)).isInstanceOf(AccessDeniedException.class);
    server.verify();
  }

  @Test
  void publicLegacyAgencyReadUsesTheSameFreshSingleTenantRequirement() {
    expectSingle("ENFORCED", false);
    expectSingle("ENFORCED", true);
    var agencies = agencyReadService(null);
    assertThat(agencies.getAgencies(java.util.List.of(9L)).getFirst().getSettings()
        .getFeatureVideoCallsOneOnOneChatsEnabled()).isFalse();
    assertThat(agencies.getAgencies(java.util.List.of(9L)).getFirst().getSettings()
        .getFeatureVideoCallsOneOnOneChatsEnabled()).isTrue();
    server.verify();
  }

  @Test
  void positiveTenantPublicReadsKeepLegacyMapAbsentCompatibility() {
    server.expect(requestTo("http://tenant.test/tenant/public/id/7")).andRespond(withSuccess(
        "{\"id\":7,\"settings\":{\"featureVideoCallsEnabled\":false}}", MediaType.APPLICATION_JSON));
    assertThat(agencyReadService(7L).getAgencies(java.util.List.of(9L)).getFirst().getSettings()
        .getFeatureVideoCallsEnabled()).isFalse();
    server.verify();
  }

  @Test
  void presentForeignTenantIdCannotBeMaskedByThePolicyAdapter() {
    server.expect(requestTo("http://tenant.test/tenant/public/id/42")).andRespond(
        withSuccess(projection("ENFORCED", false).replace("\"id\":7", "\"id\":99"),
            MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> tenants.getPermissionPolicies(42L))
        .isInstanceOf(ServiceUnavailableException.class).hasNoCause();
    server.verify();
  }

  @Test
  void presentForeignTenantIdCannotSupplyAnotherAgencysPublicPermissions() {
    server.expect(requestTo("http://tenant.test/tenant/public/id/42")).andRespond(
        withSuccess(projection("ENFORCED", false).replace("\"id\":7", "\"id\":99"),
            MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> agencyReadService(42L).getAgencies(java.util.List.of(9L)))
        .isInstanceOf(ServiceUnavailableException.class).hasNoCause();
    server.verify();
  }

  @Test
  void optionalMissingPositiveProjectionIdentityKeepsTheOlderPublicReadContract() {
    server.expect(requestTo("http://tenant.test/tenant/public/id/7")).andRespond(
        withSuccess("{\"settings\":{\"featureVideoCallsEnabled\":false}}", MediaType.APPLICATION_JSON));
    assertThat(agencyReadService(7L).getAgencies(java.util.List.of(9L)).getFirst().getSettings()
        .getFeatureVideoCallsEnabled()).isFalse();
    server.verify();
  }

  @Test
  void singleDomainModeUsesFreshConfiguredMainTenantInsteadOfTheAmbiguousSingleEndpoint() {
    ReflectionTestUtils.setField(tenants, "multitenancyWithSingleDomain", true);
    org.mockito.Mockito.when(applicationSettingsService.getApplicationSettings()).thenReturn(
        new de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.model.ApplicationSettingsDTO()
            .mainTenantSubdomainForSingleDomainMultitenancy(
                new de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.model.SettingDTO()
                    .value("configured-main")));
    for (int i = 0; i < 2; i++) {
      server.expect(requestTo("http://tenant.test/tenant/public/configured-main")).andRespond(
          withSuccess(projection("ENFORCED", false), MediaType.APPLICATION_JSON));
    }
    assertThat(tenants.getPermissionPolicies(null).getTenantId()).isEqualTo(7L);
    assertThat(tenants.getPermissionPolicies(null).getTenantId()).isEqualTo(7L);
    server.verify();
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {
      "null", "{}", "{\"id\":0,\"settings\":{},\"permissionPolicies\":{}}",
      "{\"id\":-1,\"settings\":{},\"permissionPolicies\":{}}",
      "{\"id\":7,\"permissionPolicies\":{\"flag\":{\"value\":true,\"mode\":\"ENFORCED\"}}}",
      "{\"id\":7,\"settings\":{},\"permissionPolicies\":{}}",
      "{\"id\":7,\"settings\":{},\"permissionPolicies\":{\"flag\":null}}",
      "{\"id\":7,\"settings\":{},\"permissionPolicies\":{\"flag\":{\"value\":true}}}",
      "{\"id\":7,\"settings\":{},\"permissionPolicies\":{\"flag\":{\"mode\":\"ENFORCED\"}}}",
      "{\"id\":7,\"settings\":{},\"permissionPolicies\":{\"flag\":{\"value\":true,\"mode\":\"OTHER\"}}}",
      "not-json"})
  void malformedSingleAuthorityNeverBecomesAnUnconstrainedOverride(String body) {
    server.expect(requestTo(SINGLE)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> tenants.getPermissionPolicies(null))
        .isInstanceOf(ServiceUnavailableException.class).hasNoCause();
    server.verify();
  }

  @Test
  void singleAuthorityOutageReturnsFixedUnavailableWithoutUpstreamCause() {
    server.expect(requestTo(SINGLE)).andRespond(
        org.springframework.test.web.client.response.MockRestResponseCreators.withServerError()
            .body("private-upstream-body"));
    assertThatThrownBy(() -> tenants.getPermissionPolicies(null))
        .isInstanceOf(ServiceUnavailableException.class).hasNoCause();
    server.verify();
  }

  private AgencyService agencyReadService(Long tenantId) {
    final var agencies = org.mockito.Mockito.mock(AgencyService.class, org.mockito.Mockito.CALLS_REAL_METHODS);
    var repository = org.mockito.Mockito.mock(
        de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository.class);
    var settings = org.mockito.Mockito.mock(
        de.caritas.cob.agencyservice.api.admin.service.agency.AgencySettingsService.class);
    var agency = de.caritas.cob.agencyservice.api.repository.agency.Agency.builder()
        .id(9L).name("Legacy agency").consultingTypeId(1).tenantId(tenantId).build();
    ReflectionTestUtils.setField(agency, "agencyTopics", java.util.List.of());
    org.mockito.Mockito.when(repository.findByIdIn(java.util.List.of(9L)))
        .thenReturn(java.util.List.of(agency));
    org.mockito.Mockito.when(settings.toSettings(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(ignored -> new Settings().featureVideoCallsEnabled(true).featureVideoCallsOneOnOneChatsEnabled(true));
    ReflectionTestUtils.setField(agencies, "agencyRepository", repository);
    ReflectionTestUtils.setField(agencies, "agencySettingsService", settings);
    ReflectionTestUtils.setField(agencies, "tenantService", tenants);
    return agencies;
  }

  private String projection(String mode, boolean value) {
    return """
        {"id":7,"settings":{},"permissionPolicies":{
          "featureVideoCallsOneOnOneChatsEnabled":{"value":%s,"mode":"%s"}}}
        """.formatted(value, mode);
  }

  private void expectSingle(String mode, boolean value) {
    server.expect(requestTo(SINGLE)).andRespond(
        withSuccess(projection(mode, value), MediaType.APPLICATION_JSON));
  }
}
