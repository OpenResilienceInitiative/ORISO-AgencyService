package de.caritas.cob.agencyservice.api.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.caritas.cob.agencyservice.api.repository.agencyidreservation.AgencyIdReservationRepository;
import de.caritas.cob.agencyservice.api.service.TenantService;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
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

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@TestPropertySource(properties = {
    "spring.profiles.active=testing", "feature.demographics.enabled=false", "csrf.header.property=csrfHeader", "csrf.cookie.property=csrfCookie", "TASK_IDENTITY_AUDIENCE=agencyservice",
    "IDENTITY_INVITE_RESERVATIONS_CLIENT_ID=backend-invite-reservations",
    "IDENTITY_INVITE_RESERVATIONS_SERVICE_SUBJECT=reservation-subject",
    "IDENTITY_CONFIG_WIZARD_CLIENT_ID=backend-config-wizard",
    "IDENTITY_CONFIG_WIZARD_SERVICE_SUBJECT=wizard-subject"
})
class TaskReservationAuthorityIT {
  @Autowired private MockMvc mvc;
  @Autowired private de.caritas.cob.agencyservice.config.security.JwtAuthConverter jwtAuthConverter;
  @Autowired private AgencyIdReservationRepository reservations;
  @MockitoBean private TenantService tenantService;
  @MockitoBean private de.caritas.cob.agencyservice.api.service.TopicService topics;
  @MockitoBean private de.caritas.cob.agencyservice.api.service.ConsultingTypeService consultingTypes;
  @MockitoBean private de.caritas.cob.agencyservice.api.service.AppointmentService appointments;
  @MockitoBean private de.caritas.cob.agencyservice.api.service.matrix.MatrixProvisioningService matrix;
  @Autowired @org.springframework.beans.factory.annotation.Qualifier("agencyRepository") private de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository agencies;


  @AfterEach
  void cleanup() {
    reservations.deleteAll();
    agencies.deleteById(9110L);
  }

  @Test
  void workerGetsFreshProofAndCanReleaseOnlyItsUnconsumedReservation() throws Exception {
    when(tenantService.getRestrictedTenantDataByTenantId(anyLong())).thenReturn(new RestrictedTenantDTO().id(7L));
    var caller = task("reservation-subject", "backend-invite-reservations", "invitation-reservations");
    String response = mvc.perform(post("/agencyadmin/agencyids/reservations").with(caller).cookie(new jakarta.servlet.http.Cookie("csrfCookie", "test")).header("csrfHeader", "test")
        .contentType(MediaType.APPLICATION_JSON).content("{\"agencyId\":9109,\"tenantId\":7}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String token = new ObjectMapper().readTree(response).path("token").asText();
    assertThat(token).isNotBlank();
    mvc.perform(get("/agencyadmin/agencyids/9109/availability").with(caller).cookie(new jakarta.servlet.http.Cookie("csrfCookie", "test")).header("csrfHeader", "test")).andExpect(status().isOk());
    mvc.perform(delete("/agencyadmin/agencyids/reservations/9109").with(caller).cookie(new jakarta.servlet.http.Cookie("csrfCookie", "test")).header("csrfHeader", "test")).andExpect(status().isForbidden());
    mvc.perform(delete("/agencyadmin/agencyids/reservations/9109").param("reservationToken", "foreign").with(caller).cookie(new jakarta.servlet.http.Cookie("csrfCookie", "test")).header("csrfHeader", "test"))
        .andExpect(status().isForbidden());
    assertThat(reservations.existsById(9109L)).isTrue();
    mvc.perform(delete("/agencyadmin/agencyids/reservations/9109").param("reservationToken", token).with(caller).cookie(new jakarta.servlet.http.Cookie("csrfCookie", "test")).header("csrfHeader", "test"))
        .andExpect(status().isNoContent());
    assertThat(reservations.existsById(9109L)).isFalse();
    mvc.perform(get("/agencyadmin/agencyids/next-free").param("fromId", "1").param("direction", "UP").with(caller).cookie(new jakarta.servlet.http.Cookie("csrfCookie", "test")).header("csrfHeader", "test"))
        .andExpect(status().isForbidden());
    mvc.perform(post("/agencyadmin/agencies").with(caller).cookie(new jakarta.servlet.http.Cookie("csrfCookie", "test")).header("csrfHeader", "test").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void wizardCreatesOnlyOwnedReservationAndCanRecheckSingleAgency() throws Exception {
    when(tenantService.getRestrictedTenantDataByTenantId(anyLong())).thenReturn(new RestrictedTenantDTO().id(7L));
    when(consultingTypes.getExtendedConsultingTypeResponseDTO(org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(de.caritas.cob.agencyservice.testHelper.TestConstants.CONSULTING_TYPE_SETTINGS_AIDS);
    when(matrix.ensureAgencyAccount(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(java.util.Optional.empty());
    var worker = task("reservation-subject", "backend-invite-reservations", "invitation-reservations");
    var csrf = new jakarta.servlet.http.Cookie("csrfCookie", "test");
    String response = mvc.perform(post("/agencyadmin/agencyids/reservations").with(worker).cookie(csrf).header("csrfHeader", "test")
        .contentType(MediaType.APPLICATION_JSON).content("{\"agencyId\":9110,\"tenantId\":7}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String proof = new ObjectMapper().readTree(response).path("token").asText();
    var wizard = task("wizard-subject", "backend-config-wizard", "config-wizard");
    var dto = new ObjectMapper().readTree(de.caritas.cob.agencyservice.testHelper.TestConstants.VALID_AGENCY_DTO);
    var object = (com.fasterxml.jackson.databind.node.ObjectNode) dto;
    object.put("tenantId", 7).put("reservedAgencyId", 9110).put("reservationToken", "foreign");
    mvc.perform(post("/agencyadmin/agencies").with(wizard).cookie(csrf).header("csrfHeader", "test")
        .contentType(MediaType.APPLICATION_JSON).content(dto.toString())).andExpect(status().isConflict());
    assertThat(reservations.existsById(9110L)).isTrue();
    object.put("reservationToken", proof).put("tenantId", 8);
    mvc.perform(post("/agencyadmin/agencies").with(wizard).cookie(csrf).header("csrfHeader", "test")
        .contentType(MediaType.APPLICATION_JSON).content(dto.toString())).andExpect(status().isConflict());
    object.put("tenantId", 7);
    mvc.perform(post("/agencyadmin/agencies").with(wizard).cookie(csrf).header("csrfHeader", "test")
        .contentType(MediaType.APPLICATION_JSON).content(dto.toString())).andExpect(status().isCreated());
    mvc.perform(get("/agencyadmin/agencies/9110").with(wizard)).andExpect(status().isOk());
    mvc.perform(get("/agencyadmin/agencies").with(wizard)).andExpect(status().isForbidden());
    mvc.perform(delete("/agencyadmin/agencyids/reservations/9110").param("reservationToken", proof)
        .with(worker).cookie(csrf).header("csrfHeader", "test")).andExpect(status().isForbidden());
    assertThat(agencies.findById(9110L)).isPresent();
  }

  @Test
  void taskWithInheritedAdministrativeGrantCannotUseHumanBranch() throws Exception {
    for (String roles : List.of("config-wizard,agency-admin", "config-wizard,tenant-admin", "config-wizard,realm-admin", "config-wizard,technical")) {
      var caller = task("wizard-subject", "backend-config-wizard", roles);
      mvc.perform(get("/agencyadmin/agencies/9110").with(caller)).andExpect(status().isForbidden());
      mvc.perform(post("/agencyadmin/agencies").with(caller)
          .cookie(new jakarta.servlet.http.Cookie("csrfCookie", "test")).header("csrfHeader", "test")
          .contentType(MediaType.APPLICATION_JSON).content("{}"))
          .andExpect(status().isForbidden());
    }
  }

  private RequestPostProcessor task(String subject, String client, String role) {
    return jwt().jwt(token -> token.subject(subject).claim("azp", client).claim("tenantId", 0L).claim("username", "task-fixture")
        .audience(List.of("agencyservice")).expiresAt(Instant.now().plusSeconds(60))
        .claim("realm_access", Map.of("roles", java.util.Arrays.asList(role.split(",")))))
        .authorities(jwt -> jwtAuthConverter.convert(jwt).getAuthorities());
  }
}
