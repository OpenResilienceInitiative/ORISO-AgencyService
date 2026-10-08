package de.caritas.cob.agencyservice.api.admin.service.agency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import de.caritas.cob.agencyservice.AgencyServiceApplication;
import de.caritas.cob.agencyservice.api.admin.service.UserAdminService;
import de.caritas.cob.agencyservice.api.service.TopicEnrichmentService;
import de.caritas.cob.agencyservice.testHelper.JwtAuthenticatedUserHelper;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import de.caritas.cob.agencyservice.api.model.Sort;
import de.caritas.cob.agencyservice.api.model.Sort.FieldEnum;
import de.caritas.cob.agencyservice.api.service.securityheader.SecurityHeaderSupplier;
import de.caritas.cob.agencyservice.config.apiclient.UserAdminServiceApiControllerFactory;
import de.caritas.cob.agencyservice.useradminservice.generated.web.model.AdminAgencyResponseDTO;
import de.caritas.cob.agencyservice.useradminservice.generated.web.model.AgencyAdminFullResponseDTO;
import de.caritas.cob.agencyservice.useradminservice.generated.web.model.AgencyAdminResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringRunner;
import jakarta.persistence.EntityManagerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = AgencyServiceApplication.class)
@TestPropertySource(properties = {"spring.profiles.active=testing", "feature.topics.enabled=false"})
@AutoConfigureTestDatabase(replace = Replace.ANY)
@Sql(scripts = "/database/AgencyDatabase.sql")
class AgencyAdminSearchServiceIT {

  private static final long FIRST_AGENCY_ID = 2L;
  @Autowired
  private AgencyAdminSearchService agencyAdminSearchService;

  @Autowired
  private EntityManagerFactory entityManagerFactory;

  @Autowired
  private UserAdminService userAdminService;

  @MockitoBean
  private TopicEnrichmentService topicEnrichmentService;

  @MockitoBean
  private AuthenticatedUser authenticatedUser;

  @MockitoBean
  private de.caritas.cob.agencyservice.useradminservice.generated.web.AdminUserControllerApi adminUserControllerApi;

  @MockitoBean
  private UserAdminServiceApiControllerFactory userAdminServiceApiControllerFactory;

  @MockitoBean
  private SecurityHeaderSupplier securityHeaderSupplier;

  @Test
  void searchAgency_Should_FindAgencies() {
    // given, when
    var agencySearchResult = agencyAdminSearchService.searchAgencies("", 1, 10, new Sort());
    // then
    assertThat(agencySearchResult.getEmbedded()).isNotEmpty();
    assertThat(agencySearchResult.getEmbedded()).hasSize(10);
  }

  @Test
  void searchAgency_Should_FindOnlyAgenciesManagedByTheAdmin_WhenUserIsAgencyAdmin() {
    // given
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(true);
    when(authenticatedUser.requireUserId()).thenReturn("userId");
    when(securityHeaderSupplier.getKeycloakAndCsrfHttpHeaders()).thenReturn(new HttpHeaders());
    when(userAdminServiceApiControllerFactory.createControllerApi()).thenReturn(adminUserControllerApi);
    when(adminUserControllerApi.getAdminAgencies("userId")).thenReturn(Lists.newArrayList(2L, 3L));

    // when
    var agencySearchResult = agencyAdminSearchService.searchAgencies("", 1, 10, new Sort());

    // then
    assertThat(agencySearchResult.getEmbedded()).hasSize(2);
    assertThat(agencySearchResult.getEmbedded()).extracting("embedded.id").containsOnly(2L, 3L);
  }

  @Test
  void searchAgency_Should_FindOnlyAgenciesManagedByTheAdmin_WhenUserIsAgencyAdmin_AndSortByPostcodeAscending() {
    // given
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    when(authenticatedUser.requireUserId()).thenReturn("userId");
    when(securityHeaderSupplier.getKeycloakAndCsrfHttpHeaders()).thenReturn(new HttpHeaders());
    when(userAdminServiceApiControllerFactory.createControllerApi()).thenReturn(adminUserControllerApi);
    when(adminUserControllerApi.getAdminAgencies("userId")).thenReturn(Lists.newArrayList(2L, 3L));

    // when
    var agencySearchResult = agencyAdminSearchService.searchAgencies("", 1, 20, new Sort().field(Sort.FieldEnum.POST_CODE).order(Sort.OrderEnum.ASC));

    // then
    assertThat(agencySearchResult.getEmbedded()).hasSize(20);

    List<String> collect = agencySearchResult.getEmbedded().stream().filter(result -> result.getEmbedded().getPostcode() != null).map(p -> p.getEmbedded().getPostcode()).collect(Collectors.toList());
    assertThat(collect).isSorted();
  }

  @Test
  void searchAgency_Should_FindOnlyAgenciesManagedByTheAdmin_WhenUserIsAgencyAdmin_AndSortByOffline() {
    // given
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    when(authenticatedUser.requireUserId()).thenReturn("userId");
    when(securityHeaderSupplier.getKeycloakAndCsrfHttpHeaders()).thenReturn(new HttpHeaders());
    when(userAdminServiceApiControllerFactory.createControllerApi()).thenReturn(adminUserControllerApi);
    when(adminUserControllerApi.getAdminAgencies("userId")).thenReturn(Lists.newArrayList(2L, 3L));

    // when
    var agencySearchResult = agencyAdminSearchService.searchAgencies("", 1, 20, new Sort().field(
        FieldEnum.OFFLINE).order(Sort.OrderEnum.ASC));

    // then
    assertThat(agencySearchResult.getEmbedded()).hasSize(20);

    List<Boolean> collect = agencySearchResult.getEmbedded().stream().filter(result -> result.getEmbedded().getOffline() != null).map(p -> p.getEmbedded().getOffline()).collect(Collectors.toList());
    assertThat(collect).isSorted();
  }

  @Test
  void searchAgency_Should_FindOnlyAgenciesManagedByTheAdmin_WhenUserIsAgencyAdmin_AndSortByPostcodeDescending() {
    // given
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
    when(authenticatedUser.requireUserId()).thenReturn("userId");
    when(securityHeaderSupplier.getKeycloakAndCsrfHttpHeaders()).thenReturn(new HttpHeaders());
    when(userAdminServiceApiControllerFactory.createControllerApi()).thenReturn(adminUserControllerApi);
    when(adminUserControllerApi.getAdminAgencies("userId")).thenReturn(Lists.newArrayList(2L, 3L));

    // when
    var agencySearchResult = agencyAdminSearchService.searchAgencies("", 1, 20, new Sort().field(Sort.FieldEnum.POST_CODE).order(Sort.OrderEnum.DESC));

    // then
    assertThat(agencySearchResult.getEmbedded()).hasSize(20);

    List<String> collect = agencySearchResult.getEmbedded().stream().filter(result -> result.getEmbedded().getPostcode() != null).map(p -> p.getEmbedded().getPostcode()).collect(Collectors.toList());
    assertThat(collect).isSortedAccordingTo(Comparator.reverseOrder());
  }

  @Test
  void searchAgency_Should_SortByIdAscending() {
    // given, when
    var agencySearchResult = agencyAdminSearchService.searchAgencies("", 1, 20,
        new Sort().field(FieldEnum.ID).order(Sort.OrderEnum.ASC));

    // then
    assertThat(agencySearchResult.getEmbedded()).hasSize(20);
    List<Long> collect = agencySearchResult.getEmbedded().stream()
        .map(p -> p.getEmbedded().getId()).collect(Collectors.toList());
    assertThat(collect).isSorted();
  }

  @Test
  void searchAgency_Should_SortByIdDescending() {
    // given, when
    var agencySearchResult = agencyAdminSearchService.searchAgencies("", 1, 20,
        new Sort().field(FieldEnum.ID).order(Sort.OrderEnum.DESC));

    // then
    assertThat(agencySearchResult.getEmbedded()).hasSize(20);
    List<Long> collect = agencySearchResult.getEmbedded().stream()
        .map(p -> p.getEmbedded().getId()).collect(Collectors.toList());
    assertThat(collect).isSortedAccordingTo(Comparator.reverseOrder());
  }

  @Test
  void searchAgency_Should_SortByCreateDateAscending() {
    // given, when
    var agencySearchResult = agencyAdminSearchService.searchAgencies("", 1, 20,
        new Sort().field(FieldEnum.CREATE_DATE).order(Sort.OrderEnum.ASC));

    // then
    assertThat(agencySearchResult.getEmbedded()).hasSize(20);
    List<String> collect = agencySearchResult.getEmbedded().stream()
        .filter(result -> result.getEmbedded().getCreateDate() != null)
        .map(p -> p.getEmbedded().getCreateDate()).collect(Collectors.toList());
    assertThat(collect).isSorted();
  }

  @Test
  void searchAgency_Should_SortByCreateDateDescending() {
    // given, when
    var agencySearchResult = agencyAdminSearchService.searchAgencies("", 1, 20,
        new Sort().field(FieldEnum.CREATE_DATE).order(Sort.OrderEnum.DESC));

    // then
    assertThat(agencySearchResult.getEmbedded()).hasSize(20);
    List<String> collect = agencySearchResult.getEmbedded().stream()
        .filter(result -> result.getEmbedded().getCreateDate() != null)
        .map(p -> p.getEmbedded().getCreateDate()).collect(Collectors.toList());
    assertThat(collect).isSortedAccordingTo(Comparator.reverseOrder());
  }

  @Test
  void searchAgency_Should_NotFindAnyAgencies_WhenUserIsAgencyAdminButDoesntManageAnyAgencies() {
    // given
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(true);
    when(authenticatedUser.requireUserId()).thenReturn("userId");
    when(securityHeaderSupplier.getKeycloakAndCsrfHttpHeaders()).thenReturn(new HttpHeaders());
    when(userAdminServiceApiControllerFactory.createControllerApi()).thenReturn(adminUserControllerApi);
    when(adminUserControllerApi.getAdminAgencies("userId")).thenReturn(Lists.newArrayList());

    // when
    var agencySearchResult = agencyAdminSearchService.searchAgencies("", 1, 10, new Sort());

    // then
    assertThat(agencySearchResult.getEmbedded()).isEmpty();

  }

  private AdminAgencyResponseDTO getAdminAgencies(Long... agencyIds) {
    AdminAgencyResponseDTO adminAgencyResponseDTO = new AdminAgencyResponseDTO();
    for (Long agencyId : agencyIds) {
      adminAgencyResponseDTO.addEmbeddedItem(new AgencyAdminFullResponseDTO().embedded(new AgencyAdminResponseDTO().id(agencyId)));
    }
    return adminAgencyResponseDTO;
  }

  // --- scoping driven by a real token, not by a mocked AuthenticatedUser ---

  private static final String ADMIN_SUBJECT = "8ed43c2c-1f51-4169-a7d9-c75de7eaf830";

  /**
   * Same service wiring as the Spring bean, but with an {@link AuthenticatedUser} that was built
   * from a JWT by {@code AuthenticatedUserConfig} — so the test exercises how the user id is
   * resolved from the token instead of stubbing {@code requireUserId()}.
   */
  private AgencyAdminSearchService searchServiceFor(AuthenticatedUser tokenUser) {
    return new AgencyAdminSearchService(entityManagerFactory, tokenUser, userAdminService);
  }

  @Test
  void searchAgency_Should_ScopeToAdminsAgenciesBySubject_WhenRestrictedAdminTokenLacksUserIdClaim() {
    // given: the custom userId claim is gone (Keycloak attribute wiped by a profile update),
    // only the subject identifies the admin
    var admin = JwtAuthenticatedUserHelper.userWithoutUserIdClaim(
        ADMIN_SUBJECT, JwtAuthenticatedUserHelper.RESTRICTED_AGENCY_ADMIN_ROLE);
    when(securityHeaderSupplier.getKeycloakAndCsrfHttpHeaders()).thenReturn(new HttpHeaders());
    when(userAdminServiceApiControllerFactory.createControllerApi()).thenReturn(adminUserControllerApi);
    when(adminUserControllerApi.getAdminAgencies(ADMIN_SUBJECT)).thenReturn(Lists.newArrayList(2L, 3L));

    // when
    var agencySearchResult = searchServiceFor(admin).searchAgencies("", 1, 10, new Sort());

    // then
    assertThat(agencySearchResult.getEmbedded()).extracting("embedded.id").containsOnly(2L, 3L);
    // the predicate is built once for the data query and once for the count query
    verify(adminUserControllerApi, atLeastOnce()).getAdminAgencies(ADMIN_SUBJECT);
  }

  @Test
  void searchAgency_Should_PreferUserIdClaimOverSubject_WhenRestrictedAdminTokenHasBoth() {
    var admin = JwtAuthenticatedUserHelper.userWithUserIdClaim(
        ADMIN_SUBJECT, "domain-admin-id", JwtAuthenticatedUserHelper.RESTRICTED_AGENCY_ADMIN_ROLE);
    when(securityHeaderSupplier.getKeycloakAndCsrfHttpHeaders()).thenReturn(new HttpHeaders());
    when(userAdminServiceApiControllerFactory.createControllerApi()).thenReturn(adminUserControllerApi);
    when(adminUserControllerApi.getAdminAgencies("domain-admin-id")).thenReturn(Lists.newArrayList(3L));

    var agencySearchResult = searchServiceFor(admin).searchAgencies("", 1, 10, new Sort());

    assertThat(agencySearchResult.getEmbedded()).extracting("embedded.id").containsOnly(3L);
    verify(adminUserControllerApi, never()).getAdminAgencies(ADMIN_SUBJECT);
  }

  @Test
  void searchAgency_Should_SeeEveryAgency_WhenPlatformAdminTokenLacksUserIdClaim() {
    // a platform admin (agency-admin role) is never scoped by agency ids, with or without claim
    var platformAdmin = JwtAuthenticatedUserHelper.userWithoutUserIdClaim(
        ADMIN_SUBJECT, JwtAuthenticatedUserHelper.AGENCY_ADMIN_ROLE);

    var agencySearchResult = searchServiceFor(platformAdmin).searchAgencies("", 1, 10, new Sort());

    assertThat(agencySearchResult.getEmbedded()).hasSize(10);
    verifyNoInteractions(adminUserControllerApi);
  }

  @Test
  void searchAgency_Should_DenyRestrictedAdmin_WhenTokenHasNeitherUserIdClaimNorSubject() {
    // the fallback must not turn "no identity" into "see everything"
    var admin = JwtAuthenticatedUserHelper.userWithoutAnyIdentity(
        JwtAuthenticatedUserHelper.RESTRICTED_AGENCY_ADMIN_ROLE);

    assertThatExceptionOfType(AccessDeniedException.class)
        .isThrownBy(() -> searchServiceFor(admin).searchAgencies("", 1, 10, new Sort()));
    verifyNoInteractions(adminUserControllerApi);
  }
}
