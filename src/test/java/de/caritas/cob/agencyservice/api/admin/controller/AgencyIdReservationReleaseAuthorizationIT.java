package de.caritas.cob.agencyservice.api.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.caritas.cob.agencyservice.api.admin.service.allocation.AgencyIdAllocationService;
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
import org.springframework.test.web.servlet.MockMvc;

/**
 * Releasing an agency ID reservation through the real filter chain, JWT converter and
 * method security (ORISO-Helm#367).
 *
 * <p>UserService releases reservations of revoked or expired invites from a scheduler, so it
 * calls as the Keycloak service identity (realm role {@code technical}). That identity may only
 * release a reservation that was never consumed; everything else on the agency ID allocation
 * endpoints stays with agency admins.
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
class AgencyIdReservationReleaseAuthorizationIT {

  private static final String RELEASE_PATH = "/agencyadmin/agencyids/reservations/21";

  @Autowired private MockMvc mvc;

  @MockitoBean private JwtDecoder jwtDecoder;

  @MockitoBean private AgencyIdAllocationService agencyIdAllocationService;

  private void tokenWithRealmRoles(String... roles) {
    Jwt jwt =
        Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .claim("sub", "subject")
            .claim("realm_access", Map.of("roles", List.of(roles)))
            .build();
    when(jwtDecoder.decode(any(String.class))).thenReturn(jwt);
  }

  @Test
  void release_Should_releaseOnlyUnconsumedReservation_When_callerIsTechnicalUser()
      throws Exception {
    tokenWithRealmRoles("technical");

    mvc.perform(delete(RELEASE_PATH).header("Authorization", "Bearer test-token"))
        .andExpect(status().isNoContent());

    verify(agencyIdAllocationService).releaseUnconsumed(21L);
    verify(agencyIdAllocationService, never()).release(anyLong());
  }

  @Test
  void release_Should_keepAdminReleaseUnchanged_When_callerIsAgencyAdmin() throws Exception {
    tokenWithRealmRoles("agency-admin");

    mvc.perform(delete(RELEASE_PATH).header("Authorization", "Bearer test-token"))
        .andExpect(status().isNoContent());

    verify(agencyIdAllocationService).release(21L);
    verify(agencyIdAllocationService, never()).releaseUnconsumed(anyLong());
  }

  @Test
  void release_Should_beForbidden_When_callerHasNeitherRole() throws Exception {
    tokenWithRealmRoles("user");

    mvc.perform(delete(RELEASE_PATH).header("Authorization", "Bearer test-token"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(agencyIdAllocationService);
  }

  @Test
  void release_Should_beForbidden_When_callerIsRestrictedAgencyAdmin() throws Exception {
    tokenWithRealmRoles("restricted-agency-admin");

    mvc.perform(delete(RELEASE_PATH).header("Authorization", "Bearer test-token"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(agencyIdAllocationService);
  }

  @Test
  void reserve_Should_stayForbidden_When_callerIsTechnicalUser() throws Exception {
    tokenWithRealmRoles("technical");

    mvc.perform(
            post("/agencyadmin/agencyids/reservations")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"agencyId\":21}"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(agencyIdAllocationService);
  }

  @Test
  void availability_Should_stayForbidden_When_callerIsTechnicalUser() throws Exception {
    tokenWithRealmRoles("technical");

    mvc.perform(
            get("/agencyadmin/agencyids/21/availability")
                .header("Authorization", "Bearer test-token"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(agencyIdAllocationService);
  }

  @Test
  void deleteAgency_Should_stayForbidden_When_callerIsTechnicalUser() throws Exception {
    tokenWithRealmRoles("technical");

    mvc.perform(delete("/agencyadmin/agencies/21").header("Authorization", "Bearer test-token"))
        .andExpect(status().isForbidden());
  }
}
