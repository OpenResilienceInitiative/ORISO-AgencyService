package de.caritas.cob.agencyservice.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import de.caritas.cob.agencyservice.api.admin.service.agency.AgencySettingsService;
import de.caritas.cob.agencyservice.api.exception.httpresponses.ServiceUnavailableException;
import de.caritas.cob.agencyservice.api.manager.consultingtype.ConsultingTypeManager;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.repository.agencytopic.PublicationStatus;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextVersionRepository;
import de.caritas.cob.agencyservice.api.service.legal.LegalTextInheritanceResolver;
import de.caritas.cob.agencyservice.api.service.legal.LegalTextSourceLevel;
import de.caritas.cob.agencyservice.api.service.legal.ResolvedLegalText;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.model.ApplicationSettingsDTO;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.model.FeatureToggleDTO;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.model.SettingDTO;
import de.caritas.cob.agencyservice.config.CacheManagerConfig;
import de.caritas.cob.agencyservice.config.apiclient.TenantServiceApiControllerFactory;
import de.caritas.cob.agencyservice.consultingtypeservice.generated.web.model.ExtendedConsultingTypeResponseDTO;
import de.caritas.cob.agencyservice.consultingtypeservice.generated.web.model.WhiteSpotDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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

/** Actual generated Tenant HTTP client and real public conversion/rendering/resolution code. */
@SpringBootTest(classes = {TenantService.class, TenantServiceApiControllerFactory.class,
    AgencyPublicLegalTenantReuseHttpTest.Config.class}, properties = {
      "multitenancy.enabled=false", "feature.multitenancy.with.single.domain.enabled=false",
      "tenant.service.api.url=http://tenant.test"}, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AgencyPublicLegalTenantReuseHttpTest {
  @Autowired TenantService tenants;
  @Autowired RestTemplate restTemplate;
  @Autowired CacheManager cacheManager;
  @MockitoBean ApplicationSettingsService applicationSettings;
  private MockRestServiceServer server;
  private final Map<String, ResolvedLegalText> renderedLegal = new LinkedHashMap<>();
  private CentralDataProtectionTemplateService privacy;
  private LegalTextInheritanceResolver legal;

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
  void setup() throws Exception {
    server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
    cacheManager.getCache(CacheManagerConfig.TENANT_CACHE).clear();
    ReflectionTestUtils.setField(tenants, "multitenancy", false);
    ReflectionTestUtils.setField(tenants, "multitenancyWithSingleDomain", false);
    var configuration = new de.caritas.cob.agencyservice.config.FreeMarkerConfig()
        .freemarkerConfiguration();
    privacy = new CentralDataProtectionTemplateService(tenants,
        new TemplateRenderer(configuration), applicationSettings);
    legal = org.mockito.Mockito.mock(LegalTextInheritanceResolver.class,
        org.mockito.Mockito.withSettings().useConstructor(
            mock(LegalTextVersionRepository.class), tenants, applicationSettings)
            .defaultAnswer(call -> {
              Object actual = call.callRealMethod();
              if (actual instanceof ResolvedLegalText resolved
                  && call.getMethod().getName().startsWith("resolve")) {
                AgencyTopic topic = call.getArgument(0);
                renderedLegal.put(topic.getAgency().getId() + "/" + topic.getTopicId()
                    + "/" + call.getMethod().getName(), resolved);
              }
              return actual;
            }));
  }

  @Test
  void configuredSingleProjectionRendersCentralPrivacyForAnUnchangedNullTenantAgency() {
    var agency = agency(9L, null, 0);
    expect("single", 7L, true, true, "SINGLE-7");
    var response = service(List.of(agency), false).getAgencies(List.of(9L)).getFirst();
    assertThat(response.getAgencySpecificPrivacy()).isEqualTo("SINGLE-7 privacy");
    assertThat(agency.getTenantId()).isNull();
    server.verify();
  }

  @Test
  void basicListSharesEachTenantAcrossPrivacyAndAllDepartmentsAndRefreshesNextRequest() {
    var first = agency(9L, 7L, 2);
    var sameTenant = agency(10L, 7L, 2);
    var other = agency(11L, 8L, 2);
    var service = service(List.of(first, sameTenant, other), false);
    expect("id/7", 7L, true, true, "OWN-7");
    expect("id/8", 8L, true, false, "OWN-8");
    var responses = service.getAgencies(List.of(9L, 10L, 11L));
    assertThat(responses).extracting(value -> value.getAgencySpecificPrivacy())
        .containsExactly("OWN-7 privacy", "OWN-7 privacy", "OWN-8 privacy");
    assertLegal(first, "OWN-7", LegalTextSourceLevel.TENANT);
    assertLegal(sameTenant, "OWN-7", LegalTextSourceLevel.TENANT);
    assertLegal(other, "OWN-8", LegalTextSourceLevel.TENANT);
    server.verify();
    server.reset();
    expect("id/7", 7L, true, false, "UPDATED-7");
    expect("id/8", 8L, true, true, "UPDATED-8");
    assertThat(service.getAgencies(List.of(9L, 10L, 11L)))
        .extracting(value -> value.getAgencySpecificPrivacy())
        .containsExactly("UPDATED-7 privacy", "UPDATED-7 privacy", "UPDATED-8 privacy");
    server.verify();
  }

  @ParameterizedTest
  @ValueSource(strings = {"consulting-search", "tenant-topic-search", "white-spot"})
  void registrationAndWhiteSpotPathsBoundLegalReadsAndKeepTenantSentinels(String path) {
    var first = agency(9L, 7L, 3);
    var agencies = path.equals("white-spot") ? List.of(first)
        : List.of(first, agency(10L, 7L, 2), agency(11L, 8L, 2));
    var service = service(agencies, path.equals("white-spot"));
    expect("id/7", 7L, false, true, "LEGAL-7");
    if (!path.equals("white-spot")) {
      expect("id/8", 8L, false, false, "LEGAL-8");
    }
    var result = path.equals("tenant-topic-search") ? service.getAgencies("12345", 1)
        : service.getAgencies(Optional.of("12345"), 1, Optional.empty());
    assertThat(result).hasSize(agencies.size());
    agencies.forEach(agency -> assertLegal(agency,
        agency.getTenantId() == 7L ? "LEGAL-7" : "LEGAL-8", LegalTextSourceLevel.TENANT));
    server.verify();
  }

  @Test
  void mainLegalFallbackIsSharedWithoutBecomingAnotherTenantsFeatureAuthority() {
    final var first = agency(9L, 7L, 2);
    final var other = agency(11L, 8L, 2);
    singleDomain(false);
    expect("id/7", 7L, true, true, "OWN-7");
    expect("id/8", 8L, true, false, "OWN-8");
    expect("main", 1L, true, false, "MAIN");
    var response = service(List.of(first, other), false).getAgencies(List.of(9L, 11L));
    assertThat(response).extracting(value -> value.getAgencySpecificPrivacy())
        .containsExactly("MAIN privacy", "MAIN privacy");
    assertThat(response.getFirst().getSettings().getFeatureVideoCallsOneOnOneChatsEnabled()).isTrue();
    assertThat(response.getLast().getSettings().getFeatureVideoCallsOneOnOneChatsEnabled()).isFalse();
    assertLegal(first, "MAIN", LegalTextSourceLevel.PLATFORM);
    assertLegal(other, "MAIN", LegalTextSourceLevel.PLATFORM);
    server.verify();
  }

  @Test
  void publishedDepartmentAndAgencyPriorityNeverPullsInAnUnneededMainTenant() {
    var agency = agency(9L, 7L, 2);
    agency.setContentDpp("{\"de\":\"AGENCY-DPP\"}");
    agency.setContentImprint("{\"de\":\"AGENCY-IMPRINT\"}");
    var department = agency.getAgencyTopics().getFirst();
    department.setContentDpp("{\"de\":\"DEPARTMENT-DPP\"}");
    department.setPublicationStatus(PublicationStatus.PUBLISHED);
    singleDomain(false);
    expect("id/7", 7L, false, true, "UNUSED-TENANT");
    service(List.of(agency), false).getAgencies(List.of(9L));
    assertThat(renderedLegal.get("9/900/resolveDpp").content()).contains("DEPARTMENT-DPP");
    assertThat(renderedLegal.get("9/900/resolveDpp").sourceLevel())
        .isEqualTo(LegalTextSourceLevel.DEPARTMENT);
    assertThat(renderedLegal.get("9/901/resolveDpp").content()).contains("AGENCY-DPP");
    assertThat(renderedLegal.get("9/901/resolveDpp").sourceLevel()).isEqualTo(LegalTextSourceLevel.AGENCY);
    server.verify();
  }

  @ParameterizedTest
  @ValueSource(strings = {"foreign", "zero", "multitenant-null"})
  void invalidAuthorityStillRefusesBeforePrivacyOrLegalRendering(String invalid) {
    Long tenant = invalid.equals("multitenant-null") ? null
        : Long.valueOf(invalid.equals("zero") ? 0L : 7L);
    if (invalid.equals("foreign")) {
      expect("id/7", 99L, true, true, "FOREIGN");
    }
    if (invalid.equals("multitenant-null")) {
      ReflectionTestUtils.setField(tenants, "multitenancy", true);
    }
    var service = service(List.of(agency(9L, tenant, 2)), false);
    assertThatThrownBy(() -> service.getAgencies(List.of(9L)))
        .isInstanceOf(invalid.equals("foreign") ? ServiceUnavailableException.class : AccessDeniedException.class);
    assertThat(renderedLegal).isEmpty();
    server.verify();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @ExtendWith(OutputCaptureExtension.class)
  void failedLegalSourceIsReadOnceAndRetainsTheExistingGap(boolean main, CapturedOutput output) {
    final var agency = agency(9L, 7L, 3);
    singleDomain(false);
    // The own tenant read also carries the Träger operational status, so it must succeed for the
    // centre to be offered at all; only the separate MAIN legal source may fail into a gap.
    expect("id/7", 7L, true, true, "OWN-7");
    server.expect(requestTo("http://tenant.test/tenant/public/main"))
        .andRespond(withServerError().body("PRIVATE_PROVIDER_RESPONSE_SENTINEL"));
    var response = service(List.of(agency), false).getAgencies("12345", 1);
    assertThat(response).hasSize(1);
    assertThat(renderedLegal).hasSize(6);
    assertThat(renderedLegal.values()).allMatch(value -> !value.isPresent());
    assertThat(output.getAll()).doesNotContain("PRIVATE_PROVIDER_RESPONSE_SENTINEL");
    server.verify();
  }

  @Test
  @ExtendWith(OutputCaptureExtension.class)
  void failedOwnTenantReadLeavesTraegerStatusUnknownAndOffersNoCentre(CapturedOutput output) {
    server.expect(requestTo("http://tenant.test/tenant/public/id/7"))
        .andRespond(withServerError().body("PRIVATE_PROVIDER_RESPONSE_SENTINEL"));
    var service = service(List.of(agency(9L, 7L, 3)), false);
    assertThatThrownBy(() -> service.getAgencies("12345", 1))
        .isInstanceOf(ServiceUnavailableException.class);
    assertThat(renderedLegal).isEmpty();
    assertThat(output.getAll()).doesNotContain("PRIVATE_PROVIDER_RESPONSE_SENTINEL");
    server.verify();
  }

  @Test
  void nullLegalSourceIsReadOnceAndRetainsTheExistingGap() {
    final var agency = agency(9L, 7L, 3);
    singleDomain(false);
    expect("id/7", 7L, true, true, "OWN-7");
    server.expect(requestTo("http://tenant.test/tenant/public/main"))
        .andRespond(withSuccess("", MediaType.APPLICATION_JSON));
    assertThat(service(List.of(agency), false).getAgencies("12345", 1)).hasSize(1);
    assertThat(renderedLegal).hasSize(6);
    assertThat(renderedLegal.values()).allMatch(value -> !value.isPresent());
    server.verify();
  }

  @Test
  void emptyOwnTenantReadIsMissingStatusAuthorityAndOffersNoCentre() {
    server.expect(requestTo("http://tenant.test/tenant/public/id/7"))
        .andRespond(withSuccess("", MediaType.APPLICATION_JSON));
    var service = service(List.of(agency(9L, 7L, 3)), false);
    assertThatThrownBy(() -> service.getAgencies("12345", 1))
        .isInstanceOf(TenantService.MissingOperationalTenantException.class);
    assertThat(renderedLegal).isEmpty();
    server.verify();
  }

  @Test
  void failedFeatureAuthorityRefusesBeforeAnyLegalFallback() {
    server.expect(requestTo("http://tenant.test/tenant/public/id/7"))
        .andRespond(withServerError());
    var service = service(List.of(agency(9L, 7L, 3)), false);
    assertThatThrownBy(() -> service.getAgencies(List.of(9L)))
        .isInstanceOf(ServiceUnavailableException.class);
    assertThat(renderedLegal).isEmpty();
    server.verify();
  }

  private void singleDomain(boolean tenantMayOverride) {
    ReflectionTestUtils.setField(tenants, "multitenancyWithSingleDomain", true);
    ReflectionTestUtils.setField(privacy, "multitenancyWithSingleDomain", true);
    ReflectionTestUtils.setField(legal, "multitenancyWithSingleDomain", true);
    org.mockito.Mockito.when(applicationSettings.getApplicationSettings()).thenReturn(
        new ApplicationSettingsDTO()
            .mainTenantSubdomainForSingleDomainMultitenancy(new SettingDTO().value("main"))
            .legalContentChangesBySingleTenantAdminsAllowed(new FeatureToggleDTO().value(tenantMayOverride)));
  }

  private Agency agency(long id, Long tenant, int departments) {
    var agency = Agency.builder().id(id).tenantId(tenant).name("Agency " + id)
        .consultingTypeId(1).settings("{}").agencyTopics(new ArrayList<>()).build();
    for (int i = 0; i < departments; i++) {
      agency.getAgencyTopics().add(AgencyTopic.builder()
          .id(id * 100 + i).topicId(id * 100 + i).agency(agency).build());
    }
    return agency;
  }

  private AgencyService service(List<Agency> agencies, boolean whiteSpot) {
    var result = mock(AgencyService.class, Answers.CALLS_REAL_METHODS);
    var repository = mock(AgencyRepository.class, call -> switch (call.getMethod().getName()) {
      case "findByIdIn", "findByConsultingTypeId" -> new ArrayList<>(agencies);
      case "searchWithoutTopic", "searchWithTopic" -> whiteSpot ? List.of() : new ArrayList<>(agencies);
      case "findByIdAndDeleteDateNull" -> Optional.of(agencies.getFirst());
      default -> Answers.RETURNS_DEFAULTS.answer(call);
    });
    var consulting = mock(ConsultingTypeManager.class, call ->
        call.getMethod().getName().equals("getConsultingTypeSettings")
            ? new ExtendedConsultingTypeResponseDTO().whiteSpot(new WhiteSpotDTO()
                .whiteSpotAgencyAssigned(whiteSpot).whiteSpotAgencyId(9))
            : Answers.RETURNS_DEFAULTS.answer(call));
    ReflectionTestUtils.setField(result, "tenantService", tenants);
    ReflectionTestUtils.setField(result, "agencyRepository", repository);
    ReflectionTestUtils.setField(result, "agencyTenantUnawareRepository", repository);
    ReflectionTestUtils.setField(result, "agencySettingsService", new AgencySettingsService());
    ReflectionTestUtils.setField(result, "centralDataProtectionTemplateService", privacy);
    ReflectionTestUtils.setField(result, "legalTextInheritanceResolver", legal);
    ReflectionTestUtils.setField(result, "consultingTypeManager", consulting);
    return result;
  }

  private void expect(String route, long id, boolean central, boolean video, String sentinel) {
    server.expect(requestTo("http://tenant.test/tenant/public/" + route)).andRespond(withSuccess("""
        {"id":%d,"settings":{"featureCentralDataProtectionTemplateEnabled":%s,
        "featureVideoCallsOneOnOneChatsEnabled":%s},"permissionPolicies":{
        "featureVideoCallsOneOnOneChatsEnabled":{"value":%s,"mode":"ENFORCED"}},
        "content":{"privacy":"%s privacy","impressum":"%s imprint"}}
        """.formatted(id, central, video, video, sentinel, sentinel), MediaType.APPLICATION_JSON));
  }

  private void assertLegal(Agency agency, String sentinel, LegalTextSourceLevel level) {
    for (var topic : agency.getAgencyTopics()) {
      var key = agency.getId() + "/" + topic.getTopicId() + "/";
      assertThat(renderedLegal.get(key + "resolveDpp").content()).contains(sentinel + " privacy");
      assertThat(renderedLegal.get(key + "resolveImprint").content()).contains(sentinel + " imprint");
      assertThat(renderedLegal.get(key + "resolveDpp").sourceLevel()).isEqualTo(level);
      assertThat(renderedLegal.get(key + "resolveImprint").sourceLevel()).isEqualTo(level);
    }
  }
}
