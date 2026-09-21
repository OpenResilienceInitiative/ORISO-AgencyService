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

/**
 * The agency type-ahead of the invite bar (ORISO-Admin#1026, slice 2) runs on the existing admin
 * search {@code GET /agencyadmin/agencies}: it matches the agency name OR the name of one of its
 * topics, is scoped server-side per role, can leave soft-deleted agencies out and carries what the
 * picker shows (id, name, postcode, city, tenant id + name, topic names).
 *
 * <p>Real database; only the remote services (ConsultingTypeService topics, TenantService,
 * UserService) are replaced. Topics live in ConsultingTypeService per tenant, so the mock answers
 * per tenant as the real service does.
 */
@SpringBootTest(classes = AgencyServiceApplication.class)
@TestPropertySource(
    properties = {
        "spring.profiles.active=testing",
        "feature.topics.enabled=true",
        // Production runs multi-tenant: the @Primary AgencyAdminSearchTenantSupportService and
        // per-tenant topics are what the type-ahead really hits.
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

  /**
   * AgencyController needs it as a constructor argument; it is not what this suite exercises.
   */
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
