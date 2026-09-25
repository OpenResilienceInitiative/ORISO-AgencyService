package de.caritas.cob.agencyservice.api.admin.service.agency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.AgencyServiceApplication;
import de.caritas.cob.agencyservice.api.admin.service.UserAdminService;
import de.caritas.cob.agencyservice.api.model.AgencyAdminFullResponseDTO;
import de.caritas.cob.agencyservice.api.model.AgencyAdminResponseDTO;
import de.caritas.cob.agencyservice.api.model.Sort;
import de.caritas.cob.agencyservice.api.service.TenantService;
import de.caritas.cob.agencyservice.api.service.TopicService;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import de.caritas.cob.agencyservice.topicservice.generated.web.model.TopicDTO;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

/** Real database; only remote services are mocked, and topics are answered per tenant. */
@SpringBootTest(classes = AgencyServiceApplication.class)
@TestPropertySource(
    properties = {
        "spring.profiles.active=testing",
        "feature.topics.enabled=true",
        // Production runs multi-tenant, so this hits the @Primary tenant-support search.
        "multitenancy.enabled=true"})
@AutoConfigureTestDatabase(replace = Replace.ANY)
@Sql(scripts = {"/database/AgencyDatabase.sql", "/database/AgencyPickerSearch.sql"})
class AgencyAdminSearchPickerIT {

  private static final long OWN_TENANT = 1L;
  private static final long OTHER_TENANT = 2L;

  @Autowired private AgencyAdminSearchService agencyAdminSearchService;

  @MockitoBean private AuthenticatedUser authenticatedUser;
  @MockitoBean private UserAdminService userAdminService;
  @MockitoBean private TopicService topicService;
  @MockitoBean private TenantService tenantService;

  /** Constructor dependency of AgencyController; not exercised here. */
  @MockitoBean
  private de.caritas.cob.agencyservice.api.service.TopicEnrichmentService topicEnrichmentService;

  @BeforeEach
  void topicsAndTenantsPerTenant() {
    when(topicService.getAllTopicsOfTenant(OWN_TENANT)).thenReturn(topicsOf(OWN_TENANT));
    when(topicService.getAllTopicsOfTenant(OTHER_TENANT)).thenReturn(topicsOf(OTHER_TENANT));
    // The "current tenant" lookup answers for whatever tenant the request runs in.
    when(topicService.getAllTopics())
        .thenAnswer(invocation -> topicsOf(TenantContext.getCurrentTenant()));
    when(tenantService.getRestrictedTenantDataByTenantId(OWN_TENANT))
        .thenReturn(new RestrictedTenantDTO().id(OWN_TENANT).name("Träger Köln"));
    when(tenantService.getRestrictedTenantDataByTenantId(OTHER_TENANT))
        .thenReturn(new RestrictedTenantDTO().id(OTHER_TENANT).name("Träger Nord"));
    when(authenticatedUser.requireUserId()).thenReturn("agency-admin-id");
    when(authenticatedUser.getAccessToken()).thenReturn("token");
  }

  @Test
  void search_Should_MatchTheTopicName_When_TheAgencyNameDoesNotMatch() {
    actAsTenantAdmin();

    var ids = ids(search("schuldner", false));

    // 9001 and 9004 carry topic 501 "Schuldnerberatung", 9005 "Lotsenhaus" only through it.
    assertThat(ids).containsExactlyInAnyOrder(9001L, 9004L, 9005L);
  }

  @Test
  void search_Should_LeaveOutDeletedAgencies_When_ExcludeDeletedIsSet() {
    actAsTenantAdmin();

    assertThat(ids(search("schuldner", true))).containsExactlyInAnyOrder(9001L, 9005L);
  }

  @Test
  void search_Should_ShowOnlyTheOwnTenant_When_TenantAdminSearches() {
    actAsTenantAdmin();

    assertThat(ids(search("zebrafink", false))).containsExactlyInAnyOrder(9001L, 9002L, 9004L);
  }

  @Test
  void search_Should_ShowOnlyAdministeredAgencies_When_AgencyAdminSearches() {
    actAsAgencyAdminOf(9002L);

    assertThat(ids(search("zebrafink", false))).containsExactly(9002L);
    // 9002 offers "Suchtberatung" only, so a debt-counselling search finds nothing.
    assertThat(ids(search("schuldner", false))).isEmpty();
  }

  @Test
  void search_Should_MatchTopicsOfEveryTenant_When_PlatformAdminSearches() {
    actAsPlatformAdmin();

    var ids = ids(search("schuldnerberatung nord", true));

    // Topic 601 belongs to tenant 2 — found although the platform admin's own tenant is 0.
    assertThat(ids).containsExactly(9003L);
  }

  @Test
  void search_Should_NotFindATopicOfAForeignTenant_When_TenantAdminSearches() {
    actAsTenantAdmin();

    // Topic 601 "Schuldnerberatung Nord" exists only in tenant 2.
    assertThat(ids(search("schuldnerberatung nord", false))).isEmpty();
  }

  @Test
  void search_Should_FindNothingAcrossTenants_When_TenantZeroCallerIsNoPlatformAdmin() {
    TenantContext.setCurrentTenant(0L);
    when(authenticatedUser.getTenantId()).thenReturn(0L);
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);

    assertThat(ids(search("zebrafink", false))).isEmpty();
    assertThat(ids(search("schuldnerberatung nord", false))).isEmpty();
  }

  @Test
  void search_Should_SeeEveryTenant_When_TechnicalUserSearches() {
    TenantContext.setCurrentTenant(0L);
    when(authenticatedUser.getTenantId()).thenReturn(null);
    when(authenticatedUser.isTechnicalUser()).thenReturn(true);
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);

    assertThat(ids(search("zebrafink", false)))
        .containsExactlyInAnyOrder(9001L, 9002L, 9003L, 9004L);
  }

  @Test
  void search_Should_TreatPercentAndUnderscoreAsPlainText() {
    actAsTenantAdmin();

    // Unescaped, "%" and "_" are LIKE wildcards and would match "Zebrafink Mitte".
    assertThat(ids(search("zebra%mitte", false))).isEmpty();
    assertThat(ids(search("zebrafink_mitte", false))).isEmpty();
    assertThat(ids(search("zebrafink mitte", false))).containsExactly(9001L);
  }

  @Test
  void search_Should_CarryWhatThePickerShows() {
    actAsPlatformAdmin();

    AgencyAdminResponseDTO agency =
        search("zebrafink hafen", true).get(0).getEmbedded();

    assertThat(agency.getId()).isEqualTo(9003L);
    assertThat(agency.getName()).isEqualTo("Zebrafink Hafen");
    assertThat(agency.getPostcode()).isEqualTo("20457");
    assertThat(agency.getCity()).isEqualTo("Hamburg");
    assertThat(agency.getTenantId()).isEqualTo(OTHER_TENANT);
    assertThat(agency.getTenantName()).isEqualTo("Träger Nord");
    assertThat(agency.getTopics()).extracting("name").containsExactly("Schuldnerberatung Nord");
  }

  @Test
  void search_Should_KeepDeletedAgencies_When_ExcludeDeletedIsNotSet() {
    actAsTenantAdmin();

    // Backwards compatible: the agency list of the Admin still shows soft-deleted agencies.
    assertThat(ids(agencyAdminSearchService.searchAgencies("zebrafink alt", 1, 10, null)
            .getEmbedded()))
        .containsExactly(9004L);
  }

  private List<AgencyAdminFullResponseDTO> search(String q, boolean excludeDeleted) {
    return agencyAdminSearchService
        .searchAgencies(
            q, 1, 50, new Sort().field(Sort.FieldEnum.NAME).order(Sort.OrderEnum.ASC),
            excludeDeleted)
        .getEmbedded();
  }

  private static List<Long> ids(List<AgencyAdminFullResponseDTO> result) {
    return result.stream().map(AgencyAdminFullResponseDTO::getEmbedded)
        .map(AgencyAdminResponseDTO::getId).toList();
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  private void actAsTenantAdmin() {
    TenantContext.setCurrentTenant(OWN_TENANT);
    when(authenticatedUser.getTenantId()).thenReturn(OWN_TENANT);
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
  }

  private void actAsPlatformAdmin() {
    TenantContext.setCurrentTenant(0L);
    when(authenticatedUser.getTenantId()).thenReturn(0L);
    when(authenticatedUser.isPlatformAdmin()).thenReturn(true);
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(false);
  }

  private void actAsAgencyAdminOf(Long agencyId) {
    TenantContext.setCurrentTenant(OWN_TENANT);
    when(authenticatedUser.getTenantId()).thenReturn(OWN_TENANT);
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(true);
    when(userAdminService.getAdminUserAgencyIds(anyString())).thenReturn(List.of(agencyId));
  }

  private static List<TopicDTO> topicsOf(Long tenantId) {
    if (Long.valueOf(OWN_TENANT).equals(tenantId)) {
      return List.of(topic(501L, "Schuldnerberatung"), topic(502L, "Suchtberatung"));
    }
    if (Long.valueOf(OTHER_TENANT).equals(tenantId)) {
      return List.of(topic(601L, "Schuldnerberatung Nord"));
    }
    return List.of();
  }

  private static TopicDTO topic(Long id, String name) {
    return new TopicDTO().id(id).name(name);
  }
}
