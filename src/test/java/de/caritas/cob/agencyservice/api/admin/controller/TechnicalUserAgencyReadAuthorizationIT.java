package de.caritas.cob.agencyservice.api.admin.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.caritas.cob.agencyservice.api.service.TopicService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
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
      "csrf.cookie.property=csrfCookie"
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
    Jwt jwt =
        Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .claim("sub", "subject")
            .claim("username", "technical")
            .claim("realm_access", Map.of("roles", List.of(roles)))
            .build();
    when(jwtDecoder.decode(any(String.class))).thenReturn(jwt);
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
