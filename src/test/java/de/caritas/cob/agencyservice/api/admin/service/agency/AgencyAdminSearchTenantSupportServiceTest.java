package de.caritas.cob.agencyservice.api.admin.service.agency;

import com.google.common.collect.Lists;
import de.caritas.cob.agencyservice.AgencyServiceApplication;
import de.caritas.cob.agencyservice.api.service.TopicEnrichmentService;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import de.caritas.cob.agencyservice.api.model.Sort;
import de.caritas.cob.agencyservice.api.service.securityheader.SecurityHeaderSupplier;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.config.apiclient.UserAdminApiClient;
import de.caritas.cob.agencyservice.config.apiclient.UserAdminServiceApiControllerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import de.caritas.cob.agencyservice.useradminservice.generated.web.model.AdminAgencyResponseDTO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = AgencyServiceApplication.class)
@TestPropertySource(properties = "spring.profiles.active=testing")
@TestPropertySource(properties = "multitenancy.enabled=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class AgencyAdminSearchTenantSupportServiceTest {
  private static final long FIRST_AGENCY_ID = 2L;
  @Autowired
  private AgencyAdminSearchTenantSupportService agencyAdminSearchTenantSupportService;

  @MockBean
  private TopicEnrichmentService topicEnrichmentService;

  @MockBean
  private AuthenticatedUser authenticatedUser;

  @MockBean
  private de.caritas.cob.agencyservice.useradminservice.generated.web.AdminUserControllerApi adminUserControllerApi;

  @MockBean
  private UserAdminServiceApiControllerFactory userAdminServiceApiControllerFactory;

  @MockBean
  private SecurityHeaderSupplier securityHeaderSupplier;

  @Mock
  private UserAdminApiClient userAdminApiClient;

  @BeforeEach
  public void setUp() {
    TenantContext.setCurrentTenant(1L);
    when(userAdminServiceApiControllerFactory.createControllerApi()).thenReturn(adminUserControllerApi);
    when(adminUserControllerApi.getApiClient()).thenReturn(userAdminApiClient);
  }

  @Test
  void searchAgency_Should_FindAllAgenciesForGivenTenant() {
    // given, when
    var agencySearchResult = agencyAdminSearchTenantSupportService.searchAgencies("", 1, 10, new Sort());

    // then
    assertThat(agencySearchResult.getTotal()).isEqualTo(2);
    assertThat(agencySearchResult.getEmbedded())
            .isNotEmpty()
            .hasSize(2)
            .extracting("embedded.id").containsOnly(1735L, 1737L);
  }

  @Test
  void searchAgency_Should_FindAllAgenciesForAllTenants_When_UserIsSuperAdmin() {
    // given
    when(authenticatedUser.isTenantSuperAdmin()).thenReturn(true);
    when(securityHeaderSupplier.getKeycloakAndCsrfHttpHeaders()).thenReturn(new HttpHeaders());

    // when
    var agencySearchResult = agencyAdminSearchTenantSupportService.searchAgencies("", 1, 10, new Sort());

    // then
    assertThat(agencySearchResult.getTotal()).isEqualTo(3);
    assertThat(agencySearchResult.getEmbedded())
            .isNotEmpty()
            .hasSize(3)
            .extracting("embedded.id").contains(1735L, 1737L, 1738L);
  }

  @Test
  void searchAgency_Should_FindOnlyAgenciesManagedByTheAdminAndInTheTenant_When_UserIsAgencyAdmin() {
    // given
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(true);
    when(authenticatedUser.getUserId()).thenReturn("userId");
    when(securityHeaderSupplier.getKeycloakAndCsrfHttpHeaders()).thenReturn(new HttpHeaders());
    when(adminUserControllerApi.getAdminAgencies("userId")).thenReturn(Lists.newArrayList(1735L));

    // when
    var agencySearchResult = agencyAdminSearchTenantSupportService.searchAgencies("", 1, 10, new Sort());

    // then
    assertThat(agencySearchResult.getTotal()).isEqualTo(1);
    assertThat(agencySearchResult.getEmbedded())
            .isNotEmpty()
            .hasSize(1)
            .extracting("embedded.id").containsOnly(1735L);
  }

  @Test
  void searchAgency_Should_NotFindAnyAgencies_When_UserIsAgencyAdminButDoesntManageAnyAgencies() {
    // given
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(true);
    when(authenticatedUser.getUserId()).thenReturn("userId");
    when(securityHeaderSupplier.getKeycloakAndCsrfHttpHeaders()).thenReturn(new HttpHeaders());
    when(userAdminServiceApiControllerFactory.createControllerApi()).thenReturn(adminUserControllerApi);
    when(adminUserControllerApi.getAdminAgencies("userId")).thenReturn(Lists.newArrayList());

    // when
    var agencySearchResult = agencyAdminSearchTenantSupportService.searchAgencies("", 1, 10, new Sort());

    // then
    assertThat(agencySearchResult.getEmbedded()).isEmpty();
  }

  private AdminAgencyResponseDTO getAdminAgencies(Long... agencyIds) {
    AdminAgencyResponseDTO adminAgencyResponseDTO = new de.caritas.cob.agencyservice.useradminservice.generated.web.model.AdminAgencyResponseDTO();
    for (Long agencyId : agencyIds) {
      adminAgencyResponseDTO.addEmbeddedItem(new de.caritas.cob.agencyservice.useradminservice.generated.web.model.AgencyAdminFullResponseDTO().embedded(new de.caritas.cob.agencyservice.useradminservice.generated.web.model.AgencyAdminResponseDTO().id(agencyId)));
    }
    return adminAgencyResponseDTO;
  }
}
