package de.caritas.cob.agencyservice.api.admin.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.caritas.cob.agencyservice.AgencyServiceApplication;
import de.caritas.cob.agencyservice.config.apiclient.ConsultingTypeServiceApiControllerFactory;
import de.caritas.cob.agencyservice.config.apiclient.TopicServiceApiControllerFactory;
import de.caritas.cob.agencyservice.config.apiclient.UserAdminServiceApiControllerFactory;
import de.caritas.cob.agencyservice.consultingtypeservice.generated.web.ConsultingTypeControllerApi;
import de.caritas.cob.agencyservice.topicservice.generated.web.TopicControllerApi;
import de.caritas.cob.agencyservice.useradminservice.generated.ApiClient;
import de.caritas.cob.agencyservice.useradminservice.generated.web.AdminUserControllerApi;
import de.caritas.cob.agencyservice.useradminservice.generated.web.model.AgencyConsultantResponseDTO;
import de.caritas.cob.agencyservice.useradminservice.generated.web.model.ConsultantAdminResponseDTO;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = AgencyServiceApplication.class)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@TestPropertySource(properties = {
    "spring.profiles.active=testing", "multitenancy.enabled=true",
    "feature.topics.enabled=true", "feature.appointment.enabled=false"
})
@Sql({"/database/AgencyDatabase.sql", "/setTenants.sql"})
@Transactional
public class AgencyStatusHttpIT {

  @Autowired private MockMvc mvc;
  @MockitoBean private JwtDecoder jwtDecoder;
  @MockitoBean private UserAdminServiceApiControllerFactory userClientFactory;
  @MockitoBean private ConsultingTypeServiceApiControllerFactory consultingClientFactory;
  @MockitoBean private TopicServiceApiControllerFactory topicClientFactory;
  private AdminUserControllerApi userClient;
  private Long selectedTenant;

  @Before
  public void setUp() {
    authenticate("restricted-agency-admin", 1L);
    userClient = mock(AdminUserControllerApi.class);
    when(userClientFactory.createControllerApi()).thenReturn(userClient);
    when(userClient.getApiClient()).thenReturn(new ApiClient());
    when(userClient.getAdminAgencies("centre-admin")).thenReturn(List.of(883L));
    when(userClient.getAgencyConsultants("883")).thenReturn(
        new AgencyConsultantResponseDTO().embedded(List.of(new ConsultantAdminResponseDTO())));
    var consultingClient = mock(ConsultingTypeControllerApi.class);
    when(consultingClientFactory.createControllerApi()).thenReturn(consultingClient);
    when(consultingClient.getApiClient()).thenReturn(
        new de.caritas.cob.agencyservice.consultingtypeservice.generated.ApiClient());
    when(consultingClient.getExtendedConsultingTypeById(2)).thenReturn(
        new de.caritas.cob.agencyservice.consultingtypeservice.generated.web.model
            .ExtendedConsultingTypeResponseDTO().id(2));
    when(consultingClient.getExtendedConsultingTypeById(19)).thenReturn(
        new de.caritas.cob.agencyservice.consultingtypeservice.generated.web.model
            .ExtendedConsultingTypeResponseDTO().id(19));
    var topicClient = mock(TopicControllerApi.class);
    when(topicClientFactory.createControllerApi()).thenReturn(topicClient);
    when(topicClient.getApiClient()).thenReturn(
        new de.caritas.cob.agencyservice.topicservice.generated.ApiClient());
    when(topicClient.getAllTopics()).thenReturn(List.of());
  }

  @Test
  public void activationCannotSucceedWhenConsultantPrerequisiteCannotBeChecked() throws Exception {
    mvc.perform(update(883L, true)).andExpect(status().isOk());
    when(userClient.getAgencyConsultants("883"))
        .thenThrow(new RestClientException("UserService is unavailable"));
    mvc.perform(update(883L, false)).andExpect(status().is5xxServerError());
    mvc.perform(read(883L)).andExpect(status().isOk())
        .andExpect(jsonPath("$._embedded.offline").value(true));
  }

  @Test
  public void assignedCentreAdminCanPauseAndReactivateItsCentreAcrossReads() throws Exception {
    mvc.perform(update(883L, true)).andExpect(status().isOk())
        .andExpect(jsonPath("$._embedded.offline").value(true));
    mvc.perform(read(883L)).andExpect(status().isOk())
        .andExpect(jsonPath("$._embedded.offline").value(true));
    mvc.perform(update(883L, false)).andExpect(status().isOk())
        .andExpect(jsonPath("$._embedded.offline").value(false));
    mvc.perform(read(883L)).andExpect(status().isOk())
        .andExpect(jsonPath("$._embedded.offline").value(false));
  }

  @Test
  public void unassignedCentreAdminCannotChangeTheSavedStatus() throws Exception {
    when(userClient.getAdminAgencies("centre-admin")).thenReturn(List.of());
    mvc.perform(update(883L, true)).andExpect(status().is4xxClientError());
    mvc.perform(read(883L)).andExpect(status().isOk())
        .andExpect(jsonPath("$._embedded.offline").value(false));
  }

  @Test
  public void administratorCannotChangeACentreInAnotherTenant() throws Exception {
    authenticate("agency-admin", 2L);
    mvc.perform(update(883L, true)).andExpect(status().is4xxClientError());
    authenticate("agency-admin", 1L);
    mvc.perform(read(883L)).andExpect(status().isOk())
        .andExpect(jsonPath("$._embedded.offline").value(false));
  }

  @Test
  public void counsellorCannotChangeTheSavedCentreStatus() throws Exception {
    authenticate("consultant", 1L);
    mvc.perform(update(883L, true)).andExpect(status().isForbidden());
    authenticate("restricted-agency-admin", 1L);
    mvc.perform(read(883L)).andExpect(status().isOk())
        .andExpect(jsonPath("$._embedded.offline").value(false));
  }

  @Test
  public void activationStillRequiresAnAssignedConsultant() throws Exception {
    mvc.perform(update(883L, true)).andExpect(status().isOk());
    when(userClient.getAgencyConsultants("883"))
        .thenReturn(new AgencyConsultantResponseDTO().embedded(List.of()));
    mvc.perform(update(883L, false)).andExpect(status().isBadRequest());
    mvc.perform(read(883L)).andExpect(status().isOk())
        .andExpect(jsonPath("$._embedded.offline").value(true));
  }

  @Test
  public void activationStillRequiresPostcodeCoverage() throws Exception {
    authenticate("restricted-agency-admin", 2L);
    when(userClient.getAdminAgencies("centre-admin")).thenReturn(List.of(1734L));
    mvc.perform(update(1734L, true)).andExpect(status().isOk());
    mvc.perform(update(1734L, false)).andExpect(status().isBadRequest());
    mvc.perform(read(1734L)).andExpect(status().isOk())
        .andExpect(jsonPath("$._embedded.offline").value(true));
  }

  @Test
  public void platformAdministratorCanManageCentreStatus() throws Exception {
    authenticate(List.of("agency-admin", "tenant-admin"), 0L);
    mvc.perform(update(883L, true)).andExpect(status().isOk());
    mvc.perform(read(883L)).andExpect(status().isOk())
        .andExpect(jsonPath("$._embedded.offline").value(true));
    mvc.perform(update(883L, false)).andExpect(status().isOk());
    mvc.perform(read(883L)).andExpect(status().isOk())
        .andExpect(jsonPath("$._embedded.offline").value(false));
  }

  private void authenticate(String role, Long tenantId) {
    authenticate(List.of(role), tenantId);
  }

  private void authenticate(List<String> roles, Long tenantId) {
    selectedTenant = tenantId;
    var jwt = Jwt.withTokenValue("test-token").header("alg", "none")
        .claim("sub", "centre-admin").claim("username", "centre-admin")
        .claim("tenantId", tenantId).claim("realm_access", Map.of("roles", roles))
        .build();
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);
  }

  private MockHttpServletRequestBuilder update(Long agencyId, boolean offline) {
    return put("/agencyadmin/agencies/" + agencyId)
        .header("Authorization", "Bearer test-token")
        .header("tenantId", selectedTenant.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"Test centre\",\"postcode\":\"88662\",\"city\":\"Test city\","
            + "\"external\":false,\"offline\":" + offline + "}");
  }

  private MockHttpServletRequestBuilder read(Long agencyId) {
    return get("/agencyadmin/agencies/" + agencyId)
        .header("Authorization", "Bearer test-token")
        .header("tenantId", selectedTenant.toString());
  }
}
