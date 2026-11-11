package de.caritas.cob.agencyservice.api.admin.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.transaction.annotation.Propagation;
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
    "spring.profiles.active=testing", "multitenancy.enabled=true", "spring.jpa.open-in-view=false",
    "feature.topics.enabled=true", "feature.appointment.enabled=false"
})
@Sql(value = {"/database/AgencyDatabase.sql", "/setTenants.sql"}, config =
    @org.springframework.test.context.jdbc.SqlConfig(transactionMode =
        org.springframework.test.context.jdbc.SqlConfig.TransactionMode.ISOLATED))
@Transactional
public class AgencyDetachedMetadataStatusIT {

  @Autowired protected MockMvc mvc;
  @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;
  @Autowired private de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository agencyRepository;
  @MockitoBean private JwtDecoder jwtDecoder;
  @MockitoBean private UserAdminServiceApiControllerFactory userClientFactory;
  @MockitoBean private ConsultingTypeServiceApiControllerFactory consultingClientFactory;
  @MockitoBean private TopicServiceApiControllerFactory topicClientFactory;
  @MockitoBean private de.caritas.cob.agencyservice.config.apiclient
      .TenantServiceApiControllerFactory tenantClientFactory;
  private de.caritas.cob.agencyservice.tenantservice.generated.web.TenantControllerApi tenantClient;
  protected AdminUserControllerApi userClient;
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
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void typeChangePreservesAConcurrentPauseAndTheRequestedType() throws Exception {
    authenticate("agency-admin", 1L);
    var loaded = new CountDownLatch(1);
    var finish = new CountDownLatch(1);
    doAnswer(invocation -> {
      loaded.countDown();
      assertTrue("metadata write released", finish.await(10, TimeUnit.SECONDS));
      return null;
    }).when(userClient).changeAgencyType(eq(883L), any());
    runInterleaving(post("/agencyadmin/agencies/883/changetype")
        .contentType(MediaType.APPLICATION_JSON).content("{\"agencyType\":\"TEAM_AGENCY\"}"),
        loaded, finish);
    new org.springframework.transaction.support.TransactionTemplate(transactionManager)
        .executeWithoutResult(transaction -> {
          de.caritas.cob.agencyservice.api.tenant.TenantContext.setCurrentTenant(1L);
          var fresh = agencyRepository.findById(883L).orElseThrow();
          assertTrue(fresh.isOffline());
          assertTrue(fresh.isTeamAgency());
        });
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void deletionPreservesAConcurrentPauseAndTheDeletionDate() throws Exception {
    authenticate("agency-admin", 1L);
    var loaded = new CountDownLatch(1);
    var finish = new CountDownLatch(1);
    when(userClient.getAgencyConsultants("883")).thenAnswer(invocation -> {
      loaded.countDown();
      assertTrue("metadata write released", finish.await(10, TimeUnit.SECONDS));
      return new AgencyConsultantResponseDTO().embedded(List.of());
    });
    runInterleaving(delete("/agencyadmin/agencies/883"), loaded, finish);
    new org.springframework.transaction.support.TransactionTemplate(transactionManager)
        .executeWithoutResult(transaction -> {
          de.caritas.cob.agencyservice.api.tenant.TenantContext.setCurrentTenant(1L);
          var fresh = agencyRepository.findById(883L).orElseThrow();
          assertTrue(fresh.isOffline());
          org.junit.Assert.assertNotNull(fresh.getDeleteDate());
        });
  }

  private void runInterleaving(MockHttpServletRequestBuilder request, CountDownLatch loaded,
      CountDownLatch finish) throws Exception {
    try (var executor = Executors.newFixedThreadPool(2)) {
      var metadata = executor.submit(() -> {
        mvc.perform(request.header("Authorization", "Bearer test-token").header("tenantId", "1"))
            .andExpect(status().isOk());
        return null;
      });
      try {
        assertTrue("ordinary request reached its external boundary", loaded.await(10, TimeUnit.SECONDS));
        var pause = executor.submit(() -> {
          mvc.perform(update(883L, true)).andExpect(status().isOk());
          return null;
        });
        try {
          // An unprotected writer lets status commit here; a repaired row lock serializes it.
          pause.get(500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException serializedWriter) {
          // The paused writer may proceed after the metadata transaction releases its row lock.
        }
        finish.countDown();
        metadata.get(10, TimeUnit.SECONDS);
        pause.get(10, TimeUnit.SECONDS);
      } finally {
        finish.countDown();
      }
    }
  }

  protected void authenticate(String role, Long tenantId) {
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

  protected MockHttpServletRequestBuilder update(Long agencyId, boolean offline) {
    return put("/agencyadmin/agencies/" + agencyId + "/status")
        .header("Authorization", "Bearer test-token")
        .header("tenantId", selectedTenant.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"active\":" + !offline + "}");
  }

  protected MockHttpServletRequestBuilder read(Long agencyId) {
    return get("/agencyadmin/agencies/" + agencyId)
        .header("Authorization", "Bearer test-token")
        .header("tenantId", selectedTenant.toString());
  }
}
