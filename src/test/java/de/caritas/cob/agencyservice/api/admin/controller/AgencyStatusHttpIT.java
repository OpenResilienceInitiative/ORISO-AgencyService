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
@Sql(value = {"/database/AgencyDatabase.sql", "/setTenants.sql"}, config =
    @org.springframework.test.context.jdbc.SqlConfig(transactionMode =
        org.springframework.test.context.jdbc.SqlConfig.TransactionMode.ISOLATED))
@Transactional
public class AgencyStatusHttpIT {

  @Autowired private MockMvc mvc;
  @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;
  @Autowired private de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository agencyRepository;
  @MockitoBean private JwtDecoder jwtDecoder;
  @MockitoBean private UserAdminServiceApiControllerFactory userClientFactory;
  @MockitoBean private ConsultingTypeServiceApiControllerFactory consultingClientFactory;
  @MockitoBean private TopicServiceApiControllerFactory topicClientFactory;
  @MockitoBean private de.caritas.cob.agencyservice.config.apiclient
      .TenantServiceApiControllerFactory tenantClientFactory;
  private de.caritas.cob.agencyservice.tenantservice.generated.web.TenantControllerApi tenantClient;
  private AdminUserControllerApi userClient;
  private Long selectedTenant;

  @Before
  public void setUp() {
    authenticate("restricted-agency-admin", 1L);
    tenantClient = mock(
        de.caritas.cob.agencyservice.tenantservice.generated.web.TenantControllerApi.class);
    when(tenantClientFactory.createControllerApi()).thenReturn(tenantClient);
    when(tenantClient.getRestrictedTenantDataByTenantId(org.mockito.ArgumentMatchers.anyLong()))
        .thenReturn(new de.caritas.cob.agencyservice.tenantservice.generated.web.model
            .RestrictedTenantDTO().active(true));
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
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  public void staleMetadataRequestPreservesASeparatelyCommittedPauseWhenOfflineIsOmitted()
      throws Exception {
    authenticate("agency-admin", 1L);
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    var json = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(
        mvc.perform(read(883L)).andExpect(status().isOk()).andReturn()
            .getResponse().getContentAsString()).path("_embedded");
    json.remove(List.of("offline", "active", "ownActive", "tenantActive", "restrictions"));
    json.put("name", "Metadata after pause");
    var payload = mapper.writeValueAsString(json);
    var metadata = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    var narrowStatus = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    narrowStatus.setPropagationBehavior(
        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    metadata.executeWithoutResult(transaction -> {
      de.caritas.cob.agencyservice.api.tenant.TenantContext.setCurrentTenant(1L);
      org.junit.Assert.assertFalse(agencyRepository.findById(883L).orElseThrow().isOffline());
      narrowStatus.executeWithoutResult(other -> {
        try {
          mvc.perform(update(883L, true)).andExpect(status().isOk());
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
      });
      try {
        mvc.perform(put("/agencyadmin/agencies/883")
                .header("Authorization", "Bearer test-token").header("tenantId", "1")
                .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isOk());
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    });
    mvc.perform(read(883L)).andExpect(status().isOk())
        .andExpect(jsonPath("$._embedded.offline").value(true))
        .andExpect(jsonPath("$._embedded.name").value("Metadata after pause"));
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
        .andExpect(jsonPath("$.active").value(false));
    mvc.perform(read(883L)).andExpect(status().isOk())
        .andExpect(jsonPath("$._embedded.offline").value(true));
    mvc.perform(update(883L, false)).andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true));
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

  @Test
  public void statusOnlyPauseAndReactivationPreserveAllUnrelatedAgencyData() throws Exception {
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    var before = mapper.readTree(mvc.perform(read(883L)).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString()).get("_embedded");
    var unchanged = before.deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode) unchanged).remove(
        List.of("offline", "updateDate", "active", "ownActive", "tenantActive", "restrictions"));
    for (boolean active : List.of(false, true)) {
      mvc.perform(put("/agencyadmin/agencies/883/status")
          .header("Authorization", "Bearer test-token").header("tenantId", "1")
          .contentType(MediaType.APPLICATION_JSON).content("{\"active\":" + active + "}"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.agencyId").value(883))
          .andExpect(jsonPath("$.active").value(active));
      var after = mapper.readTree(mvc.perform(read(883L)).andExpect(status().isOk())
          .andExpect(jsonPath("$._embedded.offline").value(!active))
          .andReturn().getResponse().getContentAsString()).get("_embedded");
      ((com.fasterxml.jackson.databind.node.ObjectNode) after).remove(
          List.of("offline", "updateDate", "active", "ownActive", "tenantActive", "restrictions"));
      org.assertj.core.api.Assertions.assertThat(after).isEqualTo(unchanged);
    }
  }

  @Test
  public void missingActiveIsRejectedWithoutChangingAnySavedState() throws Exception {
    mvc.perform(put("/agencyadmin/agencies/883/status")
        .header("Authorization", "Bearer test-token").header("tenantId", "1")
        .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
    mvc.perform(read(883L)).andExpect(status().isOk())
        .andExpect(jsonPath("$._embedded.offline").value(false));
  }

  @Test
  public void centreStatusReadCannotClaimActiveWhenItsTenantIsPaused() throws Exception {
    when(tenantClient.getRestrictedTenantDataByTenantId(1L)).thenReturn(
        new de.caritas.cob.agencyservice.tenantservice.generated.web.model
            .RestrictedTenantDTO().active(false));
    mvc.perform(get("/agencyadmin/agencies/883/status")
        .header("Authorization", "Bearer test-token").header("tenantId", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.ownActive").value(true))
        .andExpect(jsonPath("$.tenantActive").value(false))
        .andExpect(jsonPath("$.restrictions[0]").value("tenant_inactive"));
    mvc.perform(read(883L)).andExpect(status().isOk())
        .andExpect(jsonPath("$._embedded.offline").value(false));
  }

  @Test
  public void unavailableTenantAuthorityReadsUnknownWithoutClaimingAnActiveCentre() throws Exception {
    when(tenantClient.getRestrictedTenantDataByTenantId(1L))
        .thenThrow(new RestClientException("TenantService is unavailable"));
    mvc.perform(get("/agencyadmin/agencies/883/status")
        .header("Authorization", "Bearer test-token").header("tenantId", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").isEmpty())
        .andExpect(jsonPath("$.ownActive").value(true))
        .andExpect(jsonPath("$.tenantActive").isEmpty())
        .andExpect(jsonPath("$.restrictions[0]").value("tenant_status_unknown"));
  }

  @Test
  public void actualRemoteStatusExceptionsRemainUnknownAndDiagnosticsExposeOnlyClassAndStatus() throws Exception {
    var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(
        de.caritas.cob.agencyservice.api.admin.service.AgencyAvailabilityService.class);
    var events = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    events.start();
    logger.addAppender(events);
    try {
      for (var code : List.of(org.springframework.http.HttpStatus.BAD_GATEWAY,
          org.springframework.http.HttpStatus.NOT_FOUND)) {
        org.mockito.Mockito.doThrow(
            new org.springframework.web.server.ResponseStatusException(code, "private provider body and URL"))
            .when(tenantClient).getRestrictedTenantDataByTenantId(1L);
        mvc.perform(get("/agencyadmin/agencies/883/status")
            .header("Authorization", "Bearer test-token").header("tenantId", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").isEmpty())
            .andExpect(jsonPath("$.tenantActive").isEmpty())
            .andExpect(jsonPath("$.restrictions[0]").value("tenant_status_unknown"));
      }
      org.assertj.core.api.Assertions.assertThat(events.list).hasSize(2);
      org.assertj.core.api.Assertions.assertThat(events.list.get(0).getFormattedMessage())
          .contains("ResponseStatusException", "502").doesNotContain("private", "URL");
      org.assertj.core.api.Assertions.assertThat(events.list.get(1).getFormattedMessage())
          .contains("404").doesNotContain("private", "URL");
      org.assertj.core.api.Assertions.assertThat(events.list)
          .allSatisfy(event -> org.assertj.core.api.Assertions.assertThat(event.getThrowableProxy()).isNull());
    } finally {
      logger.detachAppender(events);
      events.stop();
    }
  }

  @Test
  public void missingParentMetadataRemainsExplicitlyUnknown() throws Exception {
    when(tenantClient.getRestrictedTenantDataByTenantId(1L)).thenReturn(null);
    mvc.perform(get("/agencyadmin/agencies/883/status")
        .header("Authorization", "Bearer test-token").header("tenantId", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").isEmpty())
        .andExpect(jsonPath("$.tenantActive").isEmpty())
        .andExpect(jsonPath("$.restrictions[0]").value("tenant_status_unknown"));
  }

  @Test
  public void unexpectedProgrammingFailureCannotBecomeASuccessfulUnknownStatus() throws Exception {
    when(tenantClient.getRestrictedTenantDataByTenantId(1L))
        .thenThrow(new NullPointerException("unexpected mapping defect"));
    mvc.perform(get("/agencyadmin/agencies/883/status")
        .header("Authorization", "Bearer test-token").header("tenantId", "1"))
        .andExpect(status().isInternalServerError());
  }

  @Test
  public void pausedTenantRefusesActivationBeforeOtherDependenciesAndKeepsOwnStatus() throws Exception {
    mvc.perform(update(883L, true)).andExpect(status().isOk());
    when(tenantClient.getRestrictedTenantDataByTenantId(1L)).thenReturn(
        new de.caritas.cob.agencyservice.tenantservice.generated.web.model
            .RestrictedTenantDTO().active(false));
    when(userClient.getAgencyConsultants("883"))
        .thenThrow(new RestClientException("UserService is unavailable"));
    mvc.perform(update(883L, false)).andExpect(status().isConflict())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
            .header().string("X-Reason", "TENANT_INACTIVE"));
    mvc.perform(read(883L)).andExpect(status().isOk())
        .andExpect(jsonPath("$._embedded.offline").value(true));
  }

  @Test
  public void listAndDetailShowSameEffectiveStatusWithoutChangingCentreOwnedOffline() throws Exception {
    when(tenantClient.getRestrictedTenantDataByTenantId(1L)).thenReturn(
        new de.caritas.cob.agencyservice.tenantservice.generated.web.model
            .RestrictedTenantDTO().active(false));
    mvc.perform(get("/agencyadmin/agencies").param("page", "1").param("perPage", "10")
        .header("Authorization", "Bearer test-token").header("tenantId", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$._embedded[0]._embedded.id").value(883))
        .andExpect(jsonPath("$._embedded[0]._embedded.offline").value(false))
        .andExpect(jsonPath("$._embedded[0]._embedded.active").value(false))
        .andExpect(jsonPath("$._embedded[0]._embedded.ownActive").value(true))
        .andExpect(jsonPath("$._embedded[0]._embedded.tenantActive").value(false));
    mvc.perform(read(883L)).andExpect(status().isOk())
        .andExpect(jsonPath("$._embedded.active").value(false))
        .andExpect(jsonPath("$._embedded.offline").value(false));
  }

  @Test
  @Sql(value = {"/database/AgencyDatabase.sql", "/setTenants.sql"}, statements =
      "UPDATE agency SET is_offline=1, delete_date='2026-10-01 10:00:00' WHERE id=883", config =
      @org.springframework.test.context.jdbc.SqlConfig(transactionMode =
          org.springframework.test.context.jdbc.SqlConfig.TransactionMode.ISOLATED))
  public void deletedCentreCannotBeReactivatedOrHaveDeletionMetadataCleared() throws Exception {
    when(userClient.getAgencyConsultants("883"))
        .thenThrow(new RestClientException("UserService is unavailable"));
    mvc.perform(update(883L, false)).andExpect(status().isConflict())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
            .header().string("X-Reason", "AGENCY_DELETED"));
    mvc.perform(get("/agencyadmin/agencies/883/status")
        .header("Authorization", "Bearer test-token").header("tenantId", "1"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.ownActive").value(false))
        .andExpect(jsonPath("$.restrictions[0]").value("deleted"));
    mvc.perform(read(883L)).andExpect(status().isOk())
        .andExpect(jsonPath("$._embedded.offline").value(true))
        .andExpect(jsonPath("$._embedded.deleteDate").isNotEmpty());
  }

  @Test
  public void unknownParentCannotReactivateAndKnownOwnPauseRemainsInactive() throws Exception {
    mvc.perform(update(883L, true)).andExpect(status().isOk());
    when(tenantClient.getRestrictedTenantDataByTenantId(1L))
        .thenThrow(new RestClientException("TenantService is unavailable"));
    mvc.perform(get("/agencyadmin/agencies/883/status")
        .header("Authorization", "Bearer test-token").header("tenantId", "1"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.ownActive").value(false))
        .andExpect(jsonPath("$.tenantActive").isEmpty());
    mvc.perform(update(883L, false)).andExpect(status().is5xxServerError());
    mvc.perform(read(883L)).andExpect(status().isOk())
        .andExpect(jsonPath("$._embedded.offline").value(true));
  }

  @Test
  public void unavailableParentIsUnknownInListsWithBoundedAuthorityCalls() throws Exception {
    authenticate("agency-admin", 1L);
    when(tenantClient.getRestrictedTenantDataByTenantId(1L))
        .thenThrow(new RestClientException("TenantService is unavailable"));
    mvc.perform(get("/agencyadmin/agencies").param("page", "1").param("perPage", "2")
        .header("Authorization", "Bearer test-token").header("tenantId", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$._embedded.length()").value(2))
        .andExpect(jsonPath("$._embedded[0]._embedded.active").isEmpty())
        .andExpect(jsonPath("$._embedded[1]._embedded.active").isEmpty())
        .andExpect(jsonPath("$._embedded[0]._embedded.tenantActive").isEmpty())
        .andExpect(jsonPath("$._embedded[1]._embedded.tenantActive").isEmpty());
    // Two maximum: one fresh operational read and one optional display-name read per parent.
    org.mockito.Mockito.verify(tenantClient, org.mockito.Mockito.atMost(2))
        .getRestrictedTenantDataByTenantId(1L);
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
    return put("/agencyadmin/agencies/" + agencyId + "/status")
        .header("Authorization", "Bearer test-token")
        .header("tenantId", selectedTenant.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"active\":" + !offline + "}");
  }

  private MockHttpServletRequestBuilder read(Long agencyId) {
    return get("/agencyadmin/agencies/" + agencyId)
        .header("Authorization", "Bearer test-token")
        .header("tenantId", selectedTenant.toString());
  }
}
