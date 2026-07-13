package de.caritas.cob.agencyservice.api.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.caritas.cob.agencyservice.api.model.AgencyMatrixCredentialsDTO;
import de.caritas.cob.agencyservice.api.service.AgencyService;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = Replace.ANY)
@TestPropertySource(
    properties = {
      "spring.profiles.active=testing",
      "csrf.header.property=csrfHeader",
      "csrf.cookie.property=csrfCookie",
      "service.encryption.appkey=test-agency-matrix-encryption-key"
    })
class InternalMatrixServiceAccountAuthorizationIT {

  private static final String MATRIX_CREDENTIALS_PATH =
      "/internal/agencies/42/matrix-service-account";
  private static final String CSRF_HEADER = "csrfHeader";
  private static final String CSRF_VALUE = "test";
  private static final Cookie CSRF_COOKIE = new Cookie("csrfCookie", CSRF_VALUE);

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @MockitoBean private AgencyService agencyService;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void getMatrixCredentialsShouldReturnUnauthorizedWhenNoBearerTokenPresent() throws Exception {
    mockMvc
        .perform(get(MATRIX_CREDENTIALS_PATH).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(agencyService);
  }

  @Test
  @WithMockUser(authorities = {"AUTHORIZATION_AGENCY_ADMIN"})
  void getMatrixCredentialsShouldReturnForbiddenForNonTechnicalUser() throws Exception {
    mockMvc
        .perform(get(MATRIX_CREDENTIALS_PATH).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());

    verifyNoInteractions(agencyService);
  }

  @Test
  @WithMockUser(authorities = {"AUTHORIZATION_TECHNICAL_USER"})
  void getMatrixCredentialsShouldReturnOkForTechnicalUser() throws Exception {
    when(agencyService.getMatrixCredentials(42L))
        .thenReturn(Optional.of(new AgencyMatrixCredentialsDTO("@agency:matrix.local", "secret")));

    mockMvc
        .perform(get(MATRIX_CREDENTIALS_PATH).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(authorities = {"AUTHORIZATION_TECHNICAL_USER"})
  void provisionMatrixCredentialsShouldReturnOkForTechnicalUser() throws Exception {
    when(agencyService.provisionMatrixCredentials(42L))
        .thenReturn(Optional.of(new AgencyMatrixCredentialsDTO("@agency:matrix.local", "secret")));

    mockMvc
        .perform(
            post(MATRIX_CREDENTIALS_PATH)
                .cookie(CSRF_COOKIE)
                .header(CSRF_HEADER, CSRF_VALUE)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }
}
