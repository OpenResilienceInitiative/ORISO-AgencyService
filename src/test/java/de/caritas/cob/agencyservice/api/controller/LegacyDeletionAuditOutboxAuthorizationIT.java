package de.caritas.cob.agencyservice.api.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Without TASK_IDENTITY_MODEL=FIVE_ACTORS nobody, not even the technical user, reads the outbox. */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@TestPropertySource(
    properties = {
      "spring.profiles.active=testing",
      "feature.demographics.enabled=false",
      "csrf.header.property=csrfHeader",
      "csrf.cookie.property=csrfCookie",
      "TASK_IDENTITY_AUDIENCE=agencyservice",
      "IDENTITY_MATRIX_AGENCY_CLIENT_ID=backend-matrix-agency",
      "IDENTITY_MATRIX_AGENCY_SERVICE_SUBJECT=matrix-subject",
      "IDENTITY_TECHNICAL_CLIENT_ID=backend-technical",
      "TECHNICAL_SERVICE_SUBJECT=legacy-subject",
      "service.encryption.appkey=synthetic-agency-matrix-encryption-key"
    })
class LegacyDeletionAuditOutboxAuthorizationIT {

  @Autowired private MockMvc mvc;
  @Autowired private de.caritas.cob.agencyservice.config.security.JwtAuthConverter converter;

  @Test
  void legacyModeRefusesEveryCaller() throws Exception {
    for (var caller :
        List.of(
            actor("legacy-subject", "backend-technical", List.of("technical")),
            actor("matrix-subject", "backend-matrix-agency", List.of("deletion-audit-read")),
            actor(
                "runtime-subject",
                "backend-runtime",
                DeletionAuditOutboxAuthorizationIT.RUNTIME_ROLES))) {
      mvc.perform(get(DeletionAuditOutboxAuthorizationIT.OUTBOX).with(caller))
          .andExpect(status().isForbidden());
    }
  }

  private RequestPostProcessor actor(String subject, String client, List<String> roles) {
    return jwt()
        .jwt(
            token ->
                token
                    .subject(subject)
                    .claim("azp", client)
                    .claim("tenantId", 0L)
                    .claim("username", "outbox-fixture")
                    .audience(List.of("agencyservice"))
                    .expiresAt(Instant.now().plusSeconds(60))
                    .claim("realm_access", Map.of("roles", roles)))
        .authorities(token -> converter.convert(token).getAuthorities());
  }
}
