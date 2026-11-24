package de.caritas.cob.agencyservice.api.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.caritas.cob.agencyservice.AgencyServiceApplication;
import de.caritas.cob.agencyservice.config.apiclient.ConsultingTypeServiceApiControllerFactory;
import de.caritas.cob.agencyservice.config.apiclient.TenantServiceApiControllerFactory;
import de.caritas.cob.agencyservice.config.apiclient.TopicServiceApiControllerFactory;
import de.caritas.cob.agencyservice.config.apiclient.UserAdminServiceApiControllerFactory;
import de.caritas.cob.agencyservice.consultingtypeservice.generated.web.ConsultingTypeControllerApi;
import de.caritas.cob.agencyservice.consultingtypeservice.generated.web.model.ExtendedConsultingTypeResponseDTO;
import de.caritas.cob.agencyservice.tenantservice.generated.web.TenantControllerApi;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.CaseSharingMode;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.CaseSharingModePermissionPolicy;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.CaseSharingPolicies;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.PermissionPolicyMode;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import de.caritas.cob.agencyservice.topicservice.generated.web.TopicControllerApi;
import de.caritas.cob.agencyservice.useradminservice.generated.ApiClient;
import de.caritas.cob.agencyservice.useradminservice.generated.web.AdminUserControllerApi;
import de.caritas.cob.agencyservice.useradminservice.generated.web.model.AgencyConsultantResponseDTO;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * ORISO-Frontend#2494 *4 (Frank, 2026-11-24): the admin of a single Beratungsstelle (restricted
 * agency admin) may change the centre's case sharing choice, unless the Träger or the platform has
 * made a different choice a requirement.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = AgencyServiceApplication.class)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@TestPropertySource(
    properties = {
      "spring.profiles.active=testing",
      "multitenancy.enabled=true",
      "feature.topics.enabled=true",
      "feature.appointment.enabled=false"
    })
@Sql(value = {"/database/AgencyDatabase.sql", "/setTenants.sql"})
@Transactional
public class AgencyCaseSharingRestrictedAdminIT {

  private static final long CENTRE = 883L;

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean private JwtDecoder jwtDecoder;
  @MockitoBean private UserAdminServiceApiControllerFactory userClientFactory;
  @MockitoBean private ConsultingTypeServiceApiControllerFactory consultingClientFactory;
  @MockitoBean private TopicServiceApiControllerFactory topicClientFactory;
  @MockitoBean private TenantServiceApiControllerFactory tenantClientFactory;
  private TenantControllerApi tenantClient;

  @Before
  public void setUp() {
    var jwt =
        Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .claim("sub", "centre-admin")
            .claim("username", "centre-admin")
            .claim("tenantId", 1L)
            .claim("realm_access", Map.of("roles", List.of("restricted-agency-admin")))
            .build();
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);
    tenantClient = mock(TenantControllerApi.class);
    when(tenantClientFactory.createControllerApi()).thenReturn(tenantClient);
    var userClient = mock(AdminUserControllerApi.class);
    when(userClientFactory.createControllerApi()).thenReturn(userClient);
    when(userClient.getApiClient()).thenReturn(new ApiClient());
    when(userClient.getAdminAgencies("centre-admin")).thenReturn(List.of(CENTRE));
    when(userClient.getAgencyConsultants(String.valueOf(CENTRE)))
        .thenReturn(new AgencyConsultantResponseDTO().embedded(List.of()));
    var consultingClient = mock(ConsultingTypeControllerApi.class);
    when(consultingClientFactory.createControllerApi()).thenReturn(consultingClient);
    when(consultingClient.getApiClient())
        .thenReturn(new de.caritas.cob.agencyservice.consultingtypeservice.generated.ApiClient());
    when(consultingClient.getExtendedConsultingTypeById(2))
        .thenReturn(new ExtendedConsultingTypeResponseDTO().id(2));
    var topicClient = mock(TopicControllerApi.class);
    when(topicClientFactory.createControllerApi()).thenReturn(topicClient);
    when(topicClient.getApiClient())
        .thenReturn(new de.caritas.cob.agencyservice.topicservice.generated.ApiClient());
    when(topicClient.getAllTopics()).thenReturn(List.of());
  }

  @Test
  public void restrictedAgencyAdminMayChooseWhileTheTraegerHasNoRequirement() throws Exception {
    traegerCaseSharing(CaseSharingMode.CASE_HANDOVER, PermissionPolicyMode.SUGGESTED);

    mvc.perform(chooseTeamCounselling()).andExpect(status().isOk());

    assertThat(storedSettings()).contains("\"caseSharingMode\":\"TEAM_COUNSELLING\"");
  }

  @Test
  public void restrictedAgencyAdminIsRefusedAgainstATraegerRequirement() throws Exception {
    traegerCaseSharing(CaseSharingMode.CASE_HANDOVER, PermissionPolicyMode.ENFORCED);

    mvc.perform(chooseTeamCounselling()).andExpect(status().isForbidden());

    assertThat(storedSettings()).doesNotContain("TEAM_COUNSELLING");
  }

  private void traegerCaseSharing(CaseSharingMode value, PermissionPolicyMode mode) {
    when(tenantClient.getRestrictedTenantDataByTenantId(anyLong()))
        .thenReturn(
            new RestrictedTenantDTO(
                    Map.of(),
                    null,
                    new CaseSharingPolicies()
                        .mode(new CaseSharingModePermissionPolicy().value(value).mode(mode)))
                .id(1L)
                .active(true));
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      chooseTeamCounselling() {
    return put("/agencyadmin/agencies/" + CENTRE)
        .header("Authorization", "Bearer test-token")
        .header("tenantId", "1")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            "{\"name\":\"Katholische Schwangerschaftsberatungsstelle Überlingen\","
                + "\"external\":false,"
                + "\"settings\":{\"caseSharingMode\":\"TEAM_COUNSELLING\"}}");
  }

  private String storedSettings() {
    var settings =
        jdbcTemplate.queryForObject(
            "SELECT SETTINGS FROM AGENCY WHERE ID = ?", String.class, CENTRE);
    return settings == null ? "" : settings;
  }
}
