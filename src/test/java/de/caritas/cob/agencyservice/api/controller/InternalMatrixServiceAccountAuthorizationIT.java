package de.caritas.cob.agencyservice.api.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import de.caritas.cob.agencyservice.api.service.matrix.MatrixProvisioningService;
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
      "service.encryption.appkey=test-agency-matrix-encryption-key",
      "TASK_IDENTITY_AUDIENCE=agencyservice",
      "IDENTITY_NOTIFICATION_DISPATCH_CLIENT_ID=backend-notification-dispatch",
      "IDENTITY_NOTIFICATION_DISPATCH_SERVICE_SUBJECT=dispatch-subject",
      "IDENTITY_MATRIX_AGENCY_CLIENT_ID=backend-matrix-agency",
      "IDENTITY_MATRIX_AGENCY_SERVICE_SUBJECT=matrix-subject"
    })
class InternalMatrixServiceAccountAuthorizationIT {

  private static final String MATRIX_CREDENTIALS_PATH =
      "/internal/agencies/42/matrix-service-account";
  private static final String CONTACT_DETAILS_PATH = "/internal/agencies/42/contact-details";
  private static final String CSRF_HEADER = "csrfHeader";
  private static final String CSRF_VALUE = "test";
  private static final Cookie CSRF_COOKIE = new Cookie("csrfCookie", CSRF_VALUE);

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @MockitoBean private AgencyRepository agencyRepository;

  @MockitoBean private MatrixProvisioningService matrixProvisioningService;

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

    verifyNoInteractions(agencyRepository);
  }

  @Test
  void getContactDetailsRequiresAuthentication() throws Exception {
    mockMvc.perform(get(CONTACT_DETAILS_PATH).param("tenantId", "7")).andExpect(status().isUnauthorized());
    verifyNoInteractions(agencyRepository);
  }

  @Test
  @WithMockUser(authorities = {"AUTHORIZATION_AGENCY_ADMIN"})
  void getContactDetailsRejectsNonTechnicalUser() throws Exception {
    mockMvc.perform(get(CONTACT_DETAILS_PATH).param("tenantId", "7")).andExpect(status().isForbidden());
    verifyNoInteractions(agencyRepository);
  }

  @Test
  @WithMockUser(authorities = {"AUTHORIZATION_TECHNICAL_USER"})
  void getContactDetailsReturnsTenantScopedFieldsToTechnicalUser() throws Exception {
    when(agencyRepository.findByIdAndDeleteDateNull(42L))
        .thenReturn(
            Optional.of(
                Agency.builder().id(42L).tenantId(7L).name("Centre").consultingTypeId(1)
                    .phone("+49 30 123").email("centre@example.org").openingHours("Mon-Fri 9-17").build()));
    mockMvc
        .perform(get(CONTACT_DETAILS_PATH).param("tenantId", "7").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(authorities = {"AUTHORIZATION_AGENCY_ADMIN"})
  void getMatrixCredentialsShouldReturnForbiddenForNonTechnicalUser() throws Exception {
    mockMvc
        .perform(get(MATRIX_CREDENTIALS_PATH).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());

    verifyNoInteractions(agencyRepository);
  }

  @Test
  @WithMockUser(authorities = {"AUTHORIZATION_TECHNICAL_USER"})
  void getMatrixCredentialsShouldReturnOkForTechnicalUser() throws Exception {
    when(agencyRepository.findById(42L)).thenReturn(Optional.of(existingAgencyAccount()));

    mockMvc
        .perform(get(MATRIX_CREDENTIALS_PATH).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matrixUserId").value("@agency:matrix.local"))
        .andExpect(jsonPath("$.matrixPassword").doesNotExist());
  }

  @Test
  @WithMockUser(authorities = {"AUTHORIZATION_TECHNICAL_USER"})
  void provisionMatrixCredentialsShouldReturnOkForTechnicalUser() throws Exception {
    when(agencyRepository.findById(42L)).thenReturn(Optional.of(existingAgencyAccount()));

    mockMvc
        .perform(
            post(MATRIX_CREDENTIALS_PATH)
                .cookie(CSRF_COOKIE)
                .header(CSRF_HEADER, CSRF_VALUE)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matrixUserId").value("@agency:matrix.local"))
        .andExpect(jsonPath("$.matrixPassword").doesNotExist());
  }

  @Test
  @WithMockUser(authorities = {"AUTHORIZATION_AGENCY_ADMIN"})
  void provisionMatrixIdentityRejectsNonTechnicalUser() throws Exception {
    mockMvc.perform(post(MATRIX_CREDENTIALS_PATH).cookie(CSRF_COOKIE)
        .header(CSRF_HEADER, CSRF_VALUE)).andExpect(status().isForbidden());
    verifyNoInteractions(agencyRepository);
  }

  @Test
  void provisionMatrixIdentityRequiresAuthentication() throws Exception {
    mockMvc.perform(post(MATRIX_CREDENTIALS_PATH).cookie(CSRF_COOKIE)
        .header(CSRF_HEADER, CSRF_VALUE)).andExpect(status().isUnauthorized());
    verifyNoInteractions(agencyRepository);
  }

  @Test
  void dispatcherReadsOnlyMatchingTenantContactsAndNeverMatrixCredentials() throws Exception {
    when(agencyRepository.findByIdAndDeleteDateNull(42L)).thenReturn(Optional.of(
        Agency.builder().id(42L).tenantId(7L).name("Centre").consultingTypeId(1)
            .email("centre@example.org").build()));
    var caller = task("dispatch-subject", "backend-notification-dispatch", "notification-dispatch", "agencyservice");
    mockMvc.perform(get(CONTACT_DETAILS_PATH).param("tenantId", "7").with(caller))
        .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("centre@example.org"));
    mockMvc.perform(get(CONTACT_DETAILS_PATH).param("tenantId", "8").with(caller))
        .andExpect(status().isNotFound());
    mockMvc.perform(get(MATRIX_CREDENTIALS_PATH).with(caller)).andExpect(status().isForbidden());
    mockMvc.perform(post(MATRIX_CREDENTIALS_PATH).cookie(CSRF_COOKIE).header(CSRF_HEADER, CSRF_VALUE).with(caller))
        .andExpect(status().isForbidden());
    mockMvc.perform(get(CONTACT_DETAILS_PATH).param("tenantId", "7")
        .with(task("foreign", "backend-notification-dispatch", "notification-dispatch", "agencyservice")))
        .andExpect(status().isForbidden());
  }

  @Test
  void matrixReadRoleCannotProvisionUnlessProvisionCapabilityIsPresent() throws Exception {
    when(agencyRepository.findById(42L)).thenReturn(Optional.of(existingAgencyAccount()));
    var reader = task("matrix-subject", "backend-matrix-agency", "matrix-agency", "agencyservice");
    mockMvc.perform(get(MATRIX_CREDENTIALS_PATH).with(reader)).andExpect(status().isOk())
        .andExpect(jsonPath("$.matrixPassword").doesNotExist());
    mockMvc.perform(post(MATRIX_CREDENTIALS_PATH).cookie(CSRF_COOKIE).header(CSRF_HEADER, CSRF_VALUE).with(reader))
        .andExpect(status().isForbidden());
    var provisioner = task("matrix-subject", "backend-matrix-agency", "matrix-agency,matrix-agency-provision", "agencyservice");
    mockMvc.perform(post(MATRIX_CREDENTIALS_PATH).cookie(CSRF_COOKIE).header(CSRF_HEADER, CSRF_VALUE).with(provisioner))
        .andExpect(status().isOk()).andExpect(jsonPath("$.matrixPassword").doesNotExist());
    mockMvc.perform(get(CONTACT_DETAILS_PATH).param("tenantId", "7").with(provisioner))
        .andExpect(status().isForbidden());
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor task(String subject, String client, String role, String audience) {
    return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
        .jwt(token -> token.subject(subject).claim("azp", client).audience(java.util.List.of(audience))
            .issuedAt(java.time.Instant.now().minusSeconds(10)).expiresAt(java.time.Instant.now().plusSeconds(60))
            .claim("tenantId", 0L).claim("realm_access", java.util.Map.of("roles", java.util.Arrays.asList(role.split(",")))));
  }

  private Agency existingAgencyAccount() {
    return Agency.builder().id(42L).name("Synthetic centre").consultingTypeId(1)
        .matrixUserId("@agency:matrix.local").matrixPassword("enc:must-not-be-decrypted").build();
  }

  @Test
  @WithMockUser(authorities = {"AUTHORIZATION_TECHNICAL_USER"})
  void newAccountProvisionResponseAlsoContainsOnlyIdentity() throws Exception {
    var agency = Agency.builder().id(42L).name("Synthetic centre").consultingTypeId(1).build();
    when(agencyRepository.findById(42L)).thenReturn(Optional.of(agency));
    when(matrixProvisioningService.ensureAgencyAccount("agency-42", "Synthetic centre"))
        .thenReturn(Optional.of(new MatrixProvisioningService.MatrixCredentials(
            "@agency:matrix.local", "public-disposable-provision-fixture")));
    mockMvc.perform(post(MATRIX_CREDENTIALS_PATH).cookie(CSRF_COOKIE)
        .header(CSRF_HEADER, CSRF_VALUE).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matrixUserId").value("@agency:matrix.local"))
        .andExpect(jsonPath("$.matrixPassword").doesNotExist());
    org.mockito.Mockito.verify(agencyRepository).updateMatrixCredentials(
        org.mockito.ArgumentMatchers.eq(42L), org.mockito.ArgumentMatchers.eq("@agency:matrix.local"),
        org.mockito.ArgumentMatchers.startsWith("enc:"));
  }

  @Test
  @WithMockUser(authorities = {"AUTHORIZATION_TECHNICAL_USER"})
  void absentAgencyIdentityReturnsNotFound() throws Exception {
    when(agencyRepository.findById(42L)).thenReturn(Optional.empty());
    mockMvc.perform(get(MATRIX_CREDENTIALS_PATH)).andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(authorities = {"AUTHORIZATION_TECHNICAL_USER"})
  void unavailableProvisioningPreservesAcceptedResponseWithoutPassword() throws Exception {
    var agency = Agency.builder().id(42L).name("Synthetic centre").consultingTypeId(1).build();
    when(agencyRepository.findById(42L)).thenReturn(Optional.of(agency));
    when(matrixProvisioningService.ensureAgencyAccount("agency-42", "Synthetic centre"))
        .thenReturn(Optional.empty());
    mockMvc.perform(post(MATRIX_CREDENTIALS_PATH).cookie(CSRF_COOKIE)
        .header(CSRF_HEADER, CSRF_VALUE)).andExpect(status().isAccepted());
  }
}
