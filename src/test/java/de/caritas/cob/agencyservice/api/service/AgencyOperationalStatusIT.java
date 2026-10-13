package de.caritas.cob.agencyservice.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.AgencyServiceApplication;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.config.CacheManagerConfig;
import de.caritas.cob.agencyservice.config.apiclient.TenantServiceApiControllerFactory;
import de.caritas.cob.agencyservice.tenantservice.generated.web.TenantControllerApi;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import de.caritas.cob.agencyservice.config.apiclient.ConsultingTypeServiceApiControllerFactory;
import de.caritas.cob.agencyservice.consultingtypeservice.generated.ApiClient;
import de.caritas.cob.agencyservice.consultingtypeservice.generated.web.ConsultingTypeControllerApi;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.CONSULTING_TYPE_SETTINGS_PREGNANCY;
import java.util.List;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = AgencyServiceApplication.class)
@AutoConfigureTestDatabase
@TestPropertySource(properties = {
    "spring.profiles.active=testing",
    "multitenancy.enabled=true",
    "feature.topics.enabled=true"
})
@Sql({"/database/AgencyDatabase.sql", "/setTenants.sql"})
@Transactional
public class AgencyOperationalStatusIT {

  @Autowired private AgencyService agencyService;
  @Autowired private CacheManager cacheManager;
  @MockitoBean private TenantServiceApiControllerFactory tenantClientFactory;
  @MockitoBean private ConsultingTypeServiceApiControllerFactory consultingTypeClientFactory;
  private TenantControllerApi tenantClient;

  @Before
  public void setUp() {
    TenantContext.setCurrentTenant(1L);
    cacheManager.getCache(CacheManagerConfig.TENANT_CACHE).clear();
    cacheManager.getCache(CacheManagerConfig.CONSULTING_TYPE_CACHE).clear();
    var consultingClient = mock(ConsultingTypeControllerApi.class);
    when(consultingTypeClientFactory.createControllerApi()).thenReturn(consultingClient);
    when(consultingClient.getApiClient()).thenReturn(new ApiClient());
    when(consultingClient.getExtendedConsultingTypeById(2))
        .thenReturn(CONSULTING_TYPE_SETTINGS_PREGNANCY);
    tenantClient = mock(TenantControllerApi.class);
    when(tenantClientFactory.createControllerApi()).thenReturn(tenantClient);
    when(tenantClient.getRestrictedTenantDataByTenantId(1L))
        .thenReturn(new RestrictedTenantDTO().id(1L).active(false));
  }

  @After
  public void tearDown() {
    TenantContext.clear();
  }

  @Test
  public void publicAgencyReadReportsInactiveWhenItsTenantIsPaused() {
    assertThat(agencyService.getAgencies(List.of(883L))).singleElement()
        .satisfies(agency -> assertThat(agency.getOffline()).isTrue());
  }

  @Test
  public void registrationSearchDoesNotOfferPausedTenantOrItsWhiteSpot() {
    assertThat(agencyService.getAgencies(Optional.of("88662"), 2, Optional.empty()))
        .isEmpty();
  }

  @Test
  public void topicChoicesDoNotAdvertiseCentresOfPausedTenant() {
    assertThat(agencyService.getAgenciesTopics()).isEmpty();
  }

  @Test
  public void topicSearchDoesNotOfferCentresOfPausedTenant() {
    assertThat(agencyService.getAgencies("45501", 1)).isEmpty();
  }

  @Test
  public void tenantReactivationIsFreshAndPreservesIndependentlyPausedCentre() {
    assertThat(agencyService.getAgencies(List.of(883L))).singleElement()
        .satisfies(agency -> assertThat(agency.getOffline()).isTrue());
    when(tenantClient.getRestrictedTenantDataByTenantId(1L))
        .thenReturn(new RestrictedTenantDTO().id(1L).active(true));
    assertThat(agencyService.getAgencies(List.of(883L))).singleElement()
        .satisfies(agency -> assertThat(agency.getOffline()).isFalse());
    agencyService.setAgencyOffline(883L);
    assertThat(agencyService.getAgencies(List.of(883L))).singleElement()
        .satisfies(agency -> assertThat(agency.getOffline()).isTrue());
  }

  @Test
  public void legacyTenantResponseWithoutStatusRemainsAvailable() {
    when(tenantClient.getRestrictedTenantDataByTenantId(1L))
        .thenReturn(new RestrictedTenantDTO().id(1L));
    assertThat(agencyService.getAgencies(List.of(883L))).singleElement()
        .satisfies(agency -> assertThat(agency.getOffline()).isFalse());
  }

  @Test
  public void missingTenantResponseCannotAppearAsConfirmedAvailable() {
    when(tenantClient.getRestrictedTenantDataByTenantId(1L)).thenReturn(null);
    assertThatThrownBy(() -> agencyService.getAgencies(List.of(883L)))
        .isInstanceOf(TenantService.MissingOperationalTenantException.class)
        .hasMessage("Tenant operational status is unavailable");
  }

  @Test
  public void activeTenantTopicChoicesKeepSortedUniqueIds() {
    when(tenantClient.getRestrictedTenantDataByTenantId(1L))
        .thenReturn(new RestrictedTenantDTO().id(1L).active(true));
    assertThat(agencyService.getAgenciesTopics()).containsExactly(0, 1, 2);
  }

  @Test
  public void publicAvailabilityUsesEachCentresOwnTenant() {
    TenantContext.setCurrentTenant(0L);
    when(tenantClient.getRestrictedTenantDataByTenantId(2L))
        .thenReturn(new RestrictedTenantDTO().id(2L).active(true));
    assertThat(agencyService.getAgencies(List.of(883L, 1734L)))
        .extracting(agency -> agency.getId(), agency -> agency.getOffline())
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(883L, true),
            org.assertj.core.groups.Tuple.tuple(1734L, false));
  }

  @Test
  public void consultingTypeReadReportsEffectiveAvailability() {
    assertThat(agencyService.getAgencies(2)).isNotEmpty()
        .allSatisfy(agency -> assertThat(agency.getOffline()).isTrue());
  }

  @Test
  public void independentlyPausedWhiteSpotCannotBeOfferedAfterTenantReactivation() {
    when(tenantClient.getRestrictedTenantDataByTenantId(1L))
        .thenReturn(new RestrictedTenantDTO().id(1L).active(true));
    agencyService.setAgencyOffline(100L);
    assertThat(agencyService.getAgencies(Optional.of("00000"), 2, Optional.empty()))
        .isEmpty();
  }

}
