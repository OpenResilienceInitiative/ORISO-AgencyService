package de.caritas.cob.agencyservice.api.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.caritas.cob.agencyservice.api.repository.deletionaudit.DeletionAuditOutboxEntry;
import de.caritas.cob.agencyservice.api.repository.deletionaudit.DeletionAuditOutboxRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Only the RUNTIME actor, declared for DELETION_AUDIT_READ, drains the deletion-log outbox. */
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
      "TASK_IDENTITY_REQUIRED_TASKS=MATRIX_AGENCY,DELETION_AUDIT_READ",
      "TASK_IDENTITY_AUDIENCE=agencyservice",
      "IDENTITY_MAIL_CLIENT_ID=backend-mail",
      "IDENTITY_MAIL_SERVICE_SUBJECT=mail-subject",
      "IDENTITY_RUNTIME_CLIENT_ID=backend-runtime",
      "IDENTITY_RUNTIME_SERVICE_SUBJECT=runtime-subject",
      "IDENTITY_TECHNICAL_CLIENT_ID=backend-technical",
      "TECHNICAL_SERVICE_SUBJECT=legacy-subject",
      "service.encryption.appkey=synthetic-agency-matrix-encryption-key"
    })
class DeletionAuditOutboxAuthorizationIT {

  static final List<String> RUNTIME_ROLES =
      List.of(
          "runtime-policy",
          "matrix-agency",
          "matrix-agency-provision",
          "appointment-sync",
          "appointment-participant-cleanup",
          "deletion-audit-read");
  static final String OUTBOX = "/internal/deletion-audit-outbox";
  static final String ACK = OUTBOX + "/acknowledgements";
  private static final jakarta.servlet.http.Cookie CSRF =
      new jakarta.servlet.http.Cookie("csrfCookie", "test");

  @Autowired private MockMvc mvc;
  @Autowired private de.caritas.cob.agencyservice.config.security.JwtAuthConverter converter;
  @Autowired private DeletionAuditOutboxRepository outbox;

  @AfterEach
  void cleanup() {
    outbox.deleteAll();
  }

  @Test
  void runtimeActorReadsPendingRowsAndAcknowledgesThem() throws Exception {
    var row = outbox.save(DeletionAuditOutboxEntry.executed(9530L, 7L));
    var runtime = actor("runtime-subject", "backend-runtime", RUNTIME_ROLES, "agencyservice");

    mvc.perform(get(OUTBOX).param("limit", "10").with(runtime))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(row.getId()))
        .andExpect(jsonPath("$[0].agencyId").value(9530))
        .andExpect(jsonPath("$[0].tenantId").value(7))
        .andExpect(jsonPath("$[0].outcome").value("EXECUTED"));

    mvc.perform(
            post(ACK)
                .with(runtime)
                .cookie(CSRF)
                .header("csrfHeader", "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[" + row.getId() + "]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.acknowledged").value(1));

    mvc.perform(get(OUTBOX).with(runtime))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void acknowledgementRejectsAnEmptyOrOversizedBatch() throws Exception {
    var runtime = actor("runtime-subject", "backend-runtime", RUNTIME_ROLES, "agencyservice");
    mvc.perform(
            post(ACK)
                .with(runtime)
                .cookie(CSRF)
                .header("csrfHeader", "test")
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[]}"))
        .andExpect(status().isBadRequest());
    var tooMany = new StringBuilder("{\"ids\":[");
    for (int i = 1; i <= 101; i++) {
      tooMany.append(i).append(i < 101 ? "," : "]}");
    }
    mvc.perform(
            post(ACK)
                .with(runtime)
                .cookie(CSRF)
                .header("csrfHeader", "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(tooMany.toString()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void otherActorsIncompleteRoleSetsWrongAudienceAndTheLegacyTechnicalUserAreRefused()
      throws Exception {
    outbox.save(DeletionAuditOutboxEntry.executed(9531L, 7L));
    for (var caller :
        List.of(
            actor("mail-subject", "backend-mail", RUNTIME_ROLES, "agencyservice"),
            actor("runtime-subject", "backend-runtime", RUNTIME_ROLES.subList(0, 5), "agencyservice"),
            actor("runtime-subject", "backend-runtime", RUNTIME_ROLES, "tenantservice"),
            actor(
                "runtime-subject",
                "backend-runtime",
                List.of("deletion-audit-read"),
                "agencyservice"),
            actor("legacy-subject", "backend-technical", List.of("technical"), "agencyservice"),
            actor("admin", "app", List.of("agency-admin", "tenant-admin"), "agencyservice"))) {
      mvc.perform(get(OUTBOX).with(caller)).andExpect(status().isForbidden());
      mvc.perform(
              post(ACK)
                  .with(caller)
                  .cookie(CSRF)
                  .header("csrfHeader", "test")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"ids\":[1]}"))
          .andExpect(status().isForbidden());
    }
    mvc.perform(get(OUTBOX)).andExpect(status().isUnauthorized());
  }

  RequestPostProcessor actor(String subject, String client, List<String> roles, String audience) {
    return jwt()
        .jwt(
            token ->
                token
                    .subject(subject)
                    .claim("azp", client)
                    .claim("tenantId", 0L)
                    .claim("username", "outbox-fixture")
                    .audience(List.of(audience))
                    .expiresAt(Instant.now().plusSeconds(60))
                    .claim("realm_access", Map.of("roles", roles)))
        .authorities(token -> converter.convert(token).getAuthorities());
  }
}
