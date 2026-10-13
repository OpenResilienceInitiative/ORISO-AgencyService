package de.caritas.cob.agencyservice.api.admin.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.caritas.cob.agencyservice.api.service.TopicService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

/**
 * UserService re-checks an invite's agency when the anonymous invitee accepts (ORISO-Admin#1026),
 * so it asks as the Keycloak technical user. Only the admin detail view carries the delete date;
 * the public by-id lookup returns soft-deleted agencies without saying so.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = Replace.ANY)
@TestPropertySource(
    properties = {
      "spring.profiles.active=testing",
      "csrf.header.property=csrfHeader",
      "csrf.cookie.property=csrfCookie",
      "IDENTITY_TECHNICAL_CLIENT_ID=backend-technical",
      "TECHNICAL_SERVICE_SUBJECT=11111111-1111-4111-8111-111111111111"
    })
@Sql(
    statements =
        "INSERT INTO AGENCY (ID, TENANT_ID, NAME, POSTCODE, CITY, IS_TEAM_AGENCY,"
            + " CONSULTING_TYPE, IS_OFFLINE, IS_EXTERNAL, CREATE_DATE, UPDATE_DATE, DELETE_DATE)"
            + " VALUES (9101, 7, 'Soft-deleted Beratungsstelle', '12345', 'Springfield', 0, 0, 0,"
            + " 0, '2026-09-01 10:00:00', '2026-09-20 10:00:00', '2026-09-20 10:00:00')")
@Sql(
    statements = "DELETE FROM AGENCY WHERE ID = 9101",
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class TechnicalUserAgencyReadAuthorizationIT {

  private static final String SOFT_DELETED_AGENCY = "/agencyadmin/agencies/9101";

  @Autowired private MockMvc mvc;

  @MockitoBean private JwtDecoder jwtDecoder;

  /** Topic names come from ConsultingTypeService, which this test does not run. */
  @MockitoBean private TopicService topicService;

  private void tokenWithRealmRoles(String... roles) {
    var token = serviceToken().claim("realm_access", Map.of("roles", List.of(roles)));
    if (!List.of(roles).contains("technical")) {
      token.subject("human-subject").claim("azp", "app").claim("username", "human-profile");
    }
    when(jwtDecoder.decode(any(String.class))).thenReturn(token.build());
  }

  private Jwt.Builder serviceToken() {
    return Jwt.withTokenValue("test-token").header("alg", "none")
        .subject("11111111-1111-4111-8111-111111111111")
        .claim("azp", "backend-technical")
        .expiresAt(Instant.now().plusSeconds(60))
        .claim("realm_access", Map.of("roles", List.of("technical")));
  }

  @Test
  void getAgency_Should_revealDeletion_When_callerIsTechnicalUser() throws Exception {
    tokenWithRealmRoles("technical");

    mvc.perform(get(SOFT_DELETED_AGENCY).header("Authorization", "Bearer test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$._embedded.id").value(9101))
        .andExpect(jsonPath("$._embedded.tenantId").value(7))
        .andExpect(jsonPath("$._embedded.deleteDate").value(startsWith("2026-09-20")));
  }

  @Test
  void getAgency_Should_answerNotFound_When_technicalUserAsksForUnknownAgency() throws Exception {
    tokenWithRealmRoles("technical");

    mvc.perform(get("/agencyadmin/agencies/9999999").header("Authorization", "Bearer test-token"))
        .andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @ValueSource(strings = {"wrong-subject", "wrong-client", "missing-subject", "missing-client",
      "missing-roles", "empty-roles", "malformed-realm", "malformed-roles", "extra-role",
      "task-role", "admin-role", "resource-roles", "malformed-resources", "expired", "missing-expiry"})
  void getAgency_Should_rejectUnboundServicePrincipal(String invalidClaim) throws Exception {
    var token = serviceToken().claim("username", "human-looking-profile");
    switch (invalidClaim) {
      case "wrong-subject" -> token.subject("foreign");
      case "wrong-client" -> token.claim("azp", "foreign");
      case "missing-subject" -> token.claims(claims -> claims.remove("sub"));
      case "missing-client" -> token.claims(claims -> claims.remove("azp"));
      case "missing-roles" -> token.claims(claims -> claims.remove("realm_access"));
      case "empty-roles" -> token.claim("realm_access", Map.of("roles", List.of()));
      case "malformed-realm" -> token.claim("realm_access", "technical");
      case "malformed-roles" -> token.claim("realm_access", Map.of("roles", "technical"));
      case "extra-role" -> token.claim("realm_access", Map.of("roles", List.of("technical", "agency-admin")));
      case "task-role" -> token.claim("realm_access", Map.of("roles", List.of("technical", "config-wizard")));
      case "admin-role" -> token.claim("realm_access", Map.of("roles", List.of("otp-config-admin")));
      case "resource-roles" -> token.claim("resource_access", Map.of("realm-management", Map.of("roles", List.of("manage-users"))));
      case "malformed-resources" -> token.claim("resource_access", "realm-management");
      case "expired" -> token.expiresAt(Instant.now().minusSeconds(60));
      case "missing-expiry" -> token.claims(claims -> claims.remove("exp"));
      default -> throw new IllegalArgumentException(invalidClaim);
    }
    when(jwtDecoder.decode(any(String.class))).thenReturn(token.build());

    mvc.perform(get(SOFT_DELETED_AGENCY).header("Authorization", "Bearer test-token"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void searchAgencies_Should_stayForbidden_When_callerIsTechnicalUser() throws Exception {
    tokenWithRealmRoles("technical");

    mvc.perform(
            get("/agencyadmin/agencies")
                .param("page", "1")
                .param("perPage", "10")
                .header("Authorization", "Bearer test-token"))
        .andExpect(status().isForbidden());
  }

  @Test
  void agencySubResources_Should_stayForbidden_When_callerIsTechnicalUser() throws Exception {
    tokenWithRealmRoles("technical");

    mvc.perform(
            get(SOFT_DELETED_AGENCY + "/postcoderanges").header("Authorization", "Bearer test-token"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/agencyadmin/agencies/tenant/7").header("Authorization", "Bearer test-token"))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateAgency_Should_stayForbidden_When_callerIsTechnicalUser() throws Exception {
    tokenWithRealmRoles("technical");

    mvc.perform(
            put(SOFT_DELETED_AGENCY)
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"renamed\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void getAgency_Should_stayForbidden_When_callerIsAdviceSeeker() throws Exception {
    tokenWithRealmRoles("user");

    mvc.perform(get(SOFT_DELETED_AGENCY).header("Authorization", "Bearer test-token"))
        .andExpect(status().isForbidden());
  }
}
