package de.caritas.cob.agencyservice.api.admin.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.caritas.cob.agencyservice.api.repository.agencyidreservation.AgencyIdReservationRepository;
import de.caritas.cob.agencyservice.api.service.TenantService;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Actor grouping preserves owned setup, tenant contacts and the separate Matrix boundary. */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@TestPropertySource(
    properties = {
      "spring.profiles.active=testing",
      "feature.demographics.enabled=false",
      "csrf.header.property=csrfHeader",
      "csrf.cookie.property=csrfCookie",
      "TASK_IDENTITY_MODEL=FIVE_ACTORS",
      "TASK_IDENTITY_REQUIRED_TASKS=CONFIG_WIZARD,INVITE_RESERVATIONS,NOTIFICATION_DISPATCH,MATRIX_AGENCY",
      "TASK_IDENTITY_AUDIENCE=agencyservice",
      "IDENTITY_SETUP_CLIENT_ID=backend-setup",
      "IDENTITY_SETUP_SERVICE_SUBJECT=setup-subject",
      "IDENTITY_MAIL_CLIENT_ID=backend-mail",
      "IDENTITY_MAIL_SERVICE_SUBJECT=mail-subject",
      "IDENTITY_RUNTIME_CLIENT_ID=backend-runtime",
      "IDENTITY_RUNTIME_SERVICE_SUBJECT=runtime-subject",
      "IDENTITY_ACCOUNT_ADMIN_CLIENT_ID=backend-account-admin",
      "IDENTITY_ACCOUNT_ADMIN_SERVICE_SUBJECT=account-admin-subject",
      "IDENTITY_TECHNICAL_CLIENT_ID=backend-technical",
      "TECHNICAL_SERVICE_SUBJECT=legacy-subject",
      "service.encryption.appkey=synthetic-agency-matrix-encryption-key"
    })
class GroupedTaskServiceIdentityAuthorizationIT {
  private static final List<String> SETUP_ROLES =
      List.of("config-wizard", "invitation-reservations", "consultant-import");
  private static final List<String> MAIL_ROLES =
      List.of(
          "smtp-sync", "system-email-delivery", "notification-dispatch", "notifications-technical");
  private static final List<String> RUNTIME_ROLES =
      List.of(
          "runtime-policy",
          "matrix-agency",
          "matrix-agency-provision",
          "appointment-sync",
          "appointment-participant-cleanup");
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final jakarta.servlet.http.Cookie CSRF =
      new jakarta.servlet.http.Cookie("csrfCookie", "test");
  @Autowired private MockMvc mvc;
  @Autowired private de.caritas.cob.agencyservice.config.security.JwtAuthConverter converter;
  @Autowired private AgencyIdReservationRepository reservations;

  @Autowired
  @org.springframework.beans.factory.annotation.Qualifier("agencyRepository")
  private de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository agencies;

  @MockitoBean private TenantService tenants;
  @MockitoBean private de.caritas.cob.agencyservice.api.service.TopicService topics;
  @MockitoBean private de.caritas.cob.agencyservice.api.service.ConsultingTypeService types;
  @MockitoBean private de.caritas.cob.agencyservice.api.service.AppointmentService appointments;

  @MockitoBean
  private de.caritas.cob.agencyservice.api.service.matrix.MatrixProvisioningService matrix;

  @BeforeEach
  void setup() {
    when(tenants.getPermissionPolicies(anyLong()))
        .thenAnswer(call -> new de.caritas.cob.agencyservice.tenantservice.generated.web.model.TenantPermissionPolicies()
            .tenantId(call.getArgument(0)).policies(Map.of()));
    when(tenants.getRestrictedTenantDataByTenantId(anyLong()))
        .thenAnswer(call -> new RestrictedTenantDTO().id(call.getArgument(0)));
    when(types.getExtendedConsultingTypeResponseDTO(anyInt()))
        .thenReturn(
            de.caritas.cob.agencyservice.testHelper.TestConstants.CONSULTING_TYPE_SETTINGS_AIDS);
    when(matrix.ensureAgencyAccount(anyString(), anyString())).thenReturn(Optional.empty());
  }

  @AfterEach
  void cleanup() {
    reservations.deleteById(9528L);
    reservations.deleteById(9529L);
    agencies.deleteById(9528L);
  }

  @Test
  void oneSetupActorReservesAndCreatesOnlyTheOwnedAgencyInTheOriginalTenant() throws Exception {
    var setup = actor("setup-subject", "backend-setup", SETUP_ROLES, "agencyservice");
    String proof = reserve(setup, 9528);
    var dto =
        (com.fasterxml.jackson.databind.node.ObjectNode)
            JSON.readTree(de.caritas.cob.agencyservice.testHelper.TestConstants.VALID_AGENCY_DTO);
    dto.put("tenantId", 7).put("reservedAgencyId", 9528).put("reservationToken", "foreign");
    mvc.perform(
            post("/agencyadmin/agencies")
                .with(setup)
                .cookie(CSRF)
                .header("csrfHeader", "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(dto.toString()))
        .andExpect(status().isConflict());
    dto.put("reservationToken", proof).put("tenantId", 8);
    mvc.perform(
            post("/agencyadmin/agencies")
                .with(setup)
                .cookie(CSRF)
                .header("csrfHeader", "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(dto.toString()))
        .andExpect(status().isConflict());
    dto.put("tenantId", 7);
    mvc.perform(
            post("/agencyadmin/agencies")
                .with(setup)
                .cookie(CSRF)
                .header("csrfHeader", "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(dto.toString()))
        .andExpect(status().isCreated());
    mvc.perform(get("/agencyadmin/agencies/9528").with(setup)).andExpect(status().isOk());
    mvc.perform(get("/agencyadmin/agencies").with(setup)).andExpect(status().isForbidden());
    mvc.perform(
            delete("/agencyadmin/agencyids/reservations/9528")
                .param("reservationToken", proof)
                .with(setup)
                .cookie(CSRF)
                .header("csrfHeader", "test"))
        .andExpect(status().isForbidden());
  }

  @Test
  void groupedSetupStillRequiresTheExactUnconsumedReservationProofForRelease() throws Exception {
    var setup = actor("setup-subject", "backend-setup", SETUP_ROLES, "agencyservice");
    String proof = reserve(setup, 9529);
    String path = "/agencyadmin/agencyids/reservations/9529";
    mvc.perform(delete(path).with(setup).cookie(CSRF).header("csrfHeader", "test"))
        .andExpect(status().isForbidden());
    mvc.perform(
            delete(path)
                .with(setup)
                .param("reservationToken", "foreign")
                .cookie(CSRF)
                .header("csrfHeader", "test"))
        .andExpect(status().isForbidden());
    mvc.perform(
            delete(path)
                .with(setup)
                .param("reservationToken", proof)
                .cookie(CSRF)
                .header("csrfHeader", "test"))
        .andExpect(status().isNoContent());
  }

  @Test
  void mailReadsOnlyTenantContactsWhileRuntimeProvisionsMatrixWithoutExposingCredentials()
      throws Exception {
    var setup = actor("setup-subject", "backend-setup", SETUP_ROLES, "agencyservice");
    String proof = reserve(setup, 9528);
    var agency =
        (com.fasterxml.jackson.databind.node.ObjectNode)
            JSON.readTree(de.caritas.cob.agencyservice.testHelper.TestConstants.VALID_AGENCY_DTO);
    agency
        .put("tenantId", 7)
        .put("reservedAgencyId", 9528)
        .put("reservationToken", proof)
        .put("name", "Grouped agency")
        .put("email", "centre@example.org");
    mvc.perform(
            post("/agencyadmin/agencies")
                .with(setup)
                .cookie(CSRF)
                .header("csrfHeader", "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(agency.toString()))
        .andExpect(status().isCreated());
    var mail = actor("mail-subject", "backend-mail", MAIL_ROLES, "agencyservice");
    var runtime = actor("runtime-subject", "backend-runtime", RUNTIME_ROLES, "agencyservice");
    String contacts = "/internal/agencies/9528/contact-details";
    String matrixPath = "/internal/agencies/9528/matrix-service-account";
    mvc.perform(get(contacts).with(mail).param("tenantId", "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("centre@example.org"));
    mvc.perform(get(contacts).with(mail).param("tenantId", "8")).andExpect(status().isNotFound());
    mvc.perform(get(matrixPath).with(mail)).andExpect(status().isForbidden());
    mvc.perform(post(matrixPath).with(mail).cookie(CSRF).header("csrfHeader", "test"))
        .andExpect(status().isForbidden());
    when(matrix.ensureAgencyAccount("agency-9528", "Grouped agency"))
        .thenReturn(
            Optional.of(
                new de.caritas.cob.agencyservice.api.service.matrix.MatrixProvisioningService
                    .MatrixCredentials(
                    "@grouped:matrix.local", "public-disposable-grouped-fixture")));
    mvc.perform(post(matrixPath).with(runtime).cookie(CSRF).header("csrfHeader", "test"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matrixPassword").doesNotExist());
    mvc.perform(get(matrixPath).with(runtime))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matrixUserId").value("@grouped:matrix.local"))
        .andExpect(jsonPath("$.matrixPassword").doesNotExist());
    mvc.perform(get(contacts).with(runtime).param("tenantId", "7"))
        .andExpect(status().isForbidden());
  }

  @Test
  void wrongActorAudienceAndInheritedHumanRolesCannotUseSetupOrHumanRoutes() throws Exception {
    for (var caller :
        List.of(
            actor("mail-subject", "backend-mail", SETUP_ROLES, "agencyservice"),
            actor("foreign", "backend-setup", SETUP_ROLES, "agencyservice"),
            actor("setup-subject", "backend-setup", SETUP_ROLES, "tenantservice"),
            actor(
                "setup-subject",
                "backend-setup",
                List.of(
                    "config-wizard",
                    "invitation-reservations",
                    "consultant-import",
                    "agency-admin"),
                "agencyservice"),
            actor(
                "account-admin-subject",
                "backend-account-admin",
                List.of("agency-admin"),
                "agencyservice"))) {
      mvc.perform(
              post("/agencyadmin/agencyids/reservations")
                  .with(caller)
                  .cookie(CSRF)
                  .header("csrfHeader", "test")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"agencyId\":9529,\"tenantId\":7}"))
          .andExpect(status().isForbidden());
      mvc.perform(get("/agencyadmin/agencies").with(caller)).andExpect(status().isForbidden());
    }
  }

  private String reserve(RequestPostProcessor caller, long agencyId) throws Exception {
    String body =
        mvc.perform(
                post("/agencyadmin/agencyids/reservations")
                    .with(caller)
                    .cookie(CSRF)
                    .header("csrfHeader", "test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"agencyId\":" + agencyId + ",\"tenantId\":7}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(body).path("token").asText();
  }

  private RequestPostProcessor actor(
      String subject, String client, List<String> roles, String audience) {
    return jwt()
        .jwt(
            token ->
                token
                    .subject(subject)
                    .claim("azp", client)
                    .claim("tenantId", 0L)
                    .claim("username", "grouped-fixture")
                    .audience(List.of(audience))
                    .expiresAt(Instant.now().plusSeconds(60))
                    .claim("realm_access", Map.of("roles", roles)))
        .authorities(token -> converter.convert(token).getAuthorities());
  }
}
