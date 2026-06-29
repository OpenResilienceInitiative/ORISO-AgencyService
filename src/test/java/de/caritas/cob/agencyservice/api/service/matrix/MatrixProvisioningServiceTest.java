package de.caritas.cob.agencyservice.api.service.matrix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatrixProvisioningServiceTest {

  private static final String API_BASE = "http://matrix-synapse:8008";
  private static final String REGISTER_PATH = "/_synapse/admin/v1/register";
  private static final String LOGIN_PATH = "/_matrix/client/r0/login";
  private static final String REGISTER_URL = API_BASE + REGISTER_PATH;
  private static final String LOGIN_URL = API_BASE + LOGIN_PATH;
  private static final String SERVER_NAME = "caritas.local";
  private static final String SHARED_SECRET = "test-registration-secret";
  private static final String ADMIN_USERNAME = "matrix-admin";
  private static final String ADMIN_PASSWORD = "matrix-admin-password";
  private static final String NONCE = "test-nonce-abc";
  private static final String NONCE_JSON = "{\"nonce\":\"" + NONCE + "\"}";
  private static final String URL_SAFE_PASSWORD_PATTERN = "^[A-Za-z0-9_-]{24}$";

  @Mock
  private MatrixConfig matrixConfig;

  @Mock
  private RestTemplate restTemplate;

  @InjectMocks
  private MatrixProvisioningService matrixProvisioningService;

  @Captor
  private ArgumentCaptor<HttpEntity<Map<String, Object>>> registerRequestCaptor;

  @BeforeEach
  void setUp() {
    when(matrixConfig.getApiUrl(any(String.class)))
        .thenAnswer(invocation -> API_BASE + invocation.getArgument(0, String.class));
    when(matrixConfig.getRegistrationSharedSecret()).thenReturn(SHARED_SECRET);
    when(matrixConfig.getServerName()).thenReturn(SERVER_NAME);
    when(matrixConfig.hasAdminCredentials()).thenReturn(false);
  }

  private void stubSuccessfulNonce(String body) {
    when(restTemplate.exchange(
            eq(REGISTER_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenReturn(ResponseEntity.ok(body));
  }

  private void stubSuccessfulRegistration(String userId) {
    when(restTemplate.postForEntity(eq(REGISTER_URL), any(), eq(Map.class)))
        .thenReturn(ResponseEntity.ok(Map.of("user_id", userId)));
  }

  private void stubRegistrationHttpError(HttpStatus status, String body) {
    when(restTemplate.postForEntity(eq(REGISTER_URL), any(), eq(Map.class)))
        .thenThrow(
            new HttpClientErrorException(
                status,
                status.getReasonPhrase(),
                null,
                body.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8));
  }

  private void stubRegistrationResponseStatusError(HttpStatus status) {
    when(restTemplate.postForEntity(eq(REGISTER_URL), any(), eq(Map.class)))
        .thenThrow(new ResponseStatusException(status, status.getReasonPhrase()));
  }

  private void stubAdminCredentialsPresent() {
    when(matrixConfig.hasAdminCredentials()).thenReturn(true);
    when(matrixConfig.getAdminUsername()).thenReturn(ADMIN_USERNAME);
    when(matrixConfig.getAdminPassword()).thenReturn(ADMIN_PASSWORD);
  }

  private void stubSuccessfulAdminLogin() {
    when(restTemplate.postForEntity(eq(LOGIN_URL), any(), eq(Map.class)))
        .thenReturn(ResponseEntity.ok(Map.of("access_token", "admin-token")));
  }

  private void stubSuccessfulPasswordReset() {
    when(restTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class)))
        .thenReturn(ResponseEntity.ok().build());
  }

  private String computeExpectedMac(String nonce, String username, String password)
      throws NoSuchAlgorithmException, InvalidKeyException {
    String message = nonce + "\0" + username + "\0" + password + "\0" + "notadmin";

    Mac hmacSha1 = Mac.getInstance("HmacSHA1");
    SecretKeySpec secretKey =
        new SecretKeySpec(SHARED_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
    hmacSha1.init(secretKey);

    byte[] macBytes = hmacSha1.doFinal(message.getBytes(StandardCharsets.UTF_8));
    StringBuilder sb = new StringBuilder(macBytes.length * 2);
    for (byte b : macBytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  @Test
  void ensureAgencyAccount_Should_ReturnCredentials_When_NewUserRegisteredSuccessfully() {
    // given
    stubSuccessfulNonce(NONCE_JSON);
    stubSuccessfulRegistration("@agency-1-service:caritas.local");

    // when
    var result = matrixProvisioningService.ensureAgencyAccount("agency-1", "Display Name");

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getUserId()).isEqualTo("@agency-1-service:caritas.local");
    assertThat(result.get().getPassword()).isNotBlank();
  }

  @Test
  void ensureAgencyAccount_Should_SanitizeUsernameWithServiceSuffix_When_BaseContainsSpecialChars() {
    // given
    stubSuccessfulNonce(NONCE_JSON);
    when(restTemplate.postForEntity(eq(REGISTER_URL), any(), eq(Map.class)))
        .thenReturn(ResponseEntity.ok(Map.of("user_id", "@agency-1-service:caritas.local")));

    // when
    matrixProvisioningService.ensureAgencyAccount("Agency@1", "Display Name");

    // then
    verify(restTemplate)
        .postForEntity(eq(REGISTER_URL), registerRequestCaptor.capture(), eq(Map.class));
    assertThat(registerRequestCaptor.getValue().getBody())
        .containsEntry("username", "agency-1-service");
  }

  @Test
  void ensureAgencyAccount_Should_TruncateSanitizedUsernameTo30Chars_When_BaseIsTooLong() {
    // given — 23-char base + "-service" = 31 chars before truncation
    final String baseUsername = "abcdefghijklmnopqrstuvw";
    final String beforeTruncation = "abcdefghijklmnopqrstuvw-service";
    final String expectedUsername = "abcdefghijklmnopqrstuvw-servic";
    assertThat(beforeTruncation).hasSize(31);
    assertThat(expectedUsername).hasSize(30);

    stubSuccessfulNonce(NONCE_JSON);
    when(restTemplate.postForEntity(eq(REGISTER_URL), any(), eq(Map.class)))
        .thenReturn(
            ResponseEntity.ok(Map.of("user_id", "@" + expectedUsername + ":" + SERVER_NAME)));

    // when
    matrixProvisioningService.ensureAgencyAccount(baseUsername, "Display Name");

    // then
    verify(restTemplate)
        .postForEntity(eq(REGISTER_URL), registerRequestCaptor.capture(), eq(Map.class));
    assertThat(registerRequestCaptor.getValue().getBody())
        .containsEntry("username", expectedUsername)
        .extracting(body -> body.get("username"))
        .asString()
        .hasSize(30);
  }

  @Test
  void ensureAgencyAccount_Should_RotatePasswordAndReturnCredentials_When_UserAlreadyExistsWith409Conflict() {
    // given
    stubSuccessfulNonce(NONCE_JSON);
    stubRegistrationHttpError(HttpStatus.CONFLICT, "");
    stubAdminCredentialsPresent();
    stubSuccessfulAdminLogin();
    stubSuccessfulPasswordReset();

    // when
    var result = matrixProvisioningService.ensureAgencyAccount("agency-1", "Display Name");

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getUserId()).isEqualTo("@agency-1-service:caritas.local");
    assertThat(result.get().getPassword()).isNotBlank();
    verify(restTemplate).postForEntity(eq(LOGIN_URL), any(), eq(Map.class));
    verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), any(), eq(Void.class));
  }

  @Test
  void ensureAgencyAccount_Should_RotatePasswordAndReturnCredentials_When_UserAlreadyExistsWith400UserInUse() {
    // given
    stubSuccessfulNonce(NONCE_JSON);
    stubRegistrationHttpError(HttpStatus.BAD_REQUEST, "{\"errcode\":\"M_USER_IN_USE\"}");
    stubAdminCredentialsPresent();
    stubSuccessfulAdminLogin();
    stubSuccessfulPasswordReset();

    // when
    var result = matrixProvisioningService.ensureAgencyAccount("agency-1", "Display Name");

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getUserId()).isEqualTo("@agency-1-service:caritas.local");
    assertThat(result.get().getPassword()).isNotBlank();
    verify(restTemplate).postForEntity(eq(LOGIN_URL), any(), eq(Map.class));
    verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), any(), eq(Void.class));
  }

  @Test
  void ensureAgencyAccount_Should_RotatePasswordAndReturnCredentials_When_DuplicateUserViaResponseStatusException() {
    // given — production-realistic path via CustomResponseErrorHandler (registerUser lines 122-136)
    stubSuccessfulNonce(NONCE_JSON);
    stubRegistrationResponseStatusError(HttpStatus.CONFLICT);
    stubAdminCredentialsPresent();
    stubSuccessfulAdminLogin();
    stubSuccessfulPasswordReset();

    // when
    var result = matrixProvisioningService.ensureAgencyAccount("agency-1", "Display Name");

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getUserId()).isEqualTo("@agency-1-service:caritas.local");
    assertThat(result.get().getPassword()).isNotBlank();
    verify(restTemplate).postForEntity(eq(LOGIN_URL), any(), eq(Map.class));
    verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), any(), eq(Void.class));
  }

  @Test
  void ensureAgencyAccount_Should_ReturnEmpty_When_NonceRequestThrowsException() {
    // given — ResponseStatusException from GET exchange (CustomResponseErrorHandler behavior)
    when(restTemplate.exchange(
            eq(REGISTER_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Matrix unavailable"));

    // when
    var result = matrixProvisioningService.ensureAgencyAccount("agency-1", "Display Name");

    // then
    assertThat(result).isEmpty();
    verify(restTemplate, never()).postForEntity(eq(REGISTER_URL), any(), eq(Map.class));
  }

  @Test
  void ensureAgencyAccount_Should_ReturnEmpty_When_NonceMissingFromResponse() {
    // given
    stubSuccessfulNonce("{}");

    // when
    var result = matrixProvisioningService.ensureAgencyAccount("agency-1", "Display Name");

    // then
    assertThat(result).isEmpty();
    verify(restTemplate, never()).postForEntity(eq(REGISTER_URL), any(), eq(Map.class));
  }

  @Test
  void ensureAgencyAccount_Should_ReturnEmpty_When_NonceResponseBodyIsNull() {
    // given
    when(restTemplate.exchange(
            eq(REGISTER_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenReturn(ResponseEntity.ok(null));

    // when
    var result = matrixProvisioningService.ensureAgencyAccount("agency-1", "Display Name");

    // then
    assertThat(result).isEmpty();
    verify(restTemplate, never()).postForEntity(eq(REGISTER_URL), any(), eq(Map.class));
  }

  @Test
  void ensureAgencyAccount_Should_ReturnEmpty_When_RegistrationFailsWithNonConflictError() {
    // given
    stubSuccessfulNonce(NONCE_JSON);
    stubRegistrationHttpError(HttpStatus.FORBIDDEN, "Forbidden");

    // when
    var result = matrixProvisioningService.ensureAgencyAccount("agency-1", "Display Name");

    // then
    assertThat(result).isEmpty();
    verify(restTemplate, never()).postForEntity(eq(LOGIN_URL), any(), eq(Map.class));
    verify(restTemplate, never()).exchange(anyString(), eq(HttpMethod.POST), any(), eq(Void.class));
  }

  @Test
  void ensureAgencyAccount_Should_ReturnEmpty_When_DuplicateUserButAdminCredentialsMissing() {
    // given
    stubSuccessfulNonce(NONCE_JSON);
    stubRegistrationHttpError(HttpStatus.CONFLICT, "");
    when(matrixConfig.hasAdminCredentials()).thenReturn(false);

    // when
    var result = matrixProvisioningService.ensureAgencyAccount("agency-1", "Display Name");

    // then
    assertThat(result).isEmpty();
    verify(restTemplate, never()).postForEntity(eq(LOGIN_URL), any(), eq(Map.class));
    verify(restTemplate, never()).exchange(anyString(), eq(HttpMethod.POST), any(), eq(Void.class));
  }

  @Test
  void ensureAgencyAccount_Should_ReturnEmpty_When_DuplicateUserButAdminLoginFails() {
    // given
    stubSuccessfulNonce(NONCE_JSON);
    stubRegistrationHttpError(HttpStatus.CONFLICT, "");
    stubAdminCredentialsPresent();
    when(restTemplate.postForEntity(eq(LOGIN_URL), any(), eq(Map.class)))
        .thenReturn(ResponseEntity.ok(new HashMap<>()));

    // when
    var result = matrixProvisioningService.ensureAgencyAccount("agency-1", "Display Name");

    // then
    assertThat(result).isEmpty();
    verify(restTemplate).postForEntity(eq(LOGIN_URL), any(), eq(Map.class));
    verify(restTemplate, never()).exchange(anyString(), eq(HttpMethod.POST), any(), eq(Void.class));
  }

  @Test
  void ensureAgencyAccount_Should_ReturnEmpty_When_DuplicateUserButAdminLoginThrows() {
    // given — fetchAdminAccessToken exception path (lines 225-227)
    stubSuccessfulNonce(NONCE_JSON);
    stubRegistrationHttpError(HttpStatus.CONFLICT, "");
    stubAdminCredentialsPresent();
    when(restTemplate.postForEntity(eq(LOGIN_URL), any(), eq(Map.class)))
        .thenThrow(new RuntimeException("login failed"));

    // when
    var result = matrixProvisioningService.ensureAgencyAccount("agency-1", "Display Name");

    // then
    assertThat(result).isEmpty();
    verify(restTemplate).postForEntity(eq(LOGIN_URL), any(), eq(Map.class));
    verify(restTemplate, never()).exchange(anyString(), eq(HttpMethod.POST), any(), eq(Void.class));
  }

  @Test
  void ensureAgencyAccount_Should_ReturnEmpty_When_PasswordResetFails() {
    // given
    stubSuccessfulNonce(NONCE_JSON);
    stubRegistrationHttpError(HttpStatus.CONFLICT, "");
    stubAdminCredentialsPresent();
    stubSuccessfulAdminLogin();
    when(restTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class)))
        .thenThrow(new RuntimeException("reset failed"));

    // when
    var result = matrixProvisioningService.ensureAgencyAccount("agency-1", "Display Name");

    // then
    assertThat(result).isEmpty();
    verify(restTemplate).postForEntity(eq(LOGIN_URL), any(), eq(Map.class));
    verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), any(), eq(Void.class));
  }

  @Test
  void ensureAgencyAccount_Should_ReturnEmpty_When_UnexpectedExceptionOccurs() {
    // given
    when(restTemplate.exchange(
            eq(REGISTER_URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenThrow(new RuntimeException("unexpected"));

    // when
    var result = matrixProvisioningService.ensureAgencyAccount("agency-1", "Display Name");

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void ensureAgencyAccount_Should_ExtractNonceFromValidJson_When_RegisteringUser() {
    // given
    stubSuccessfulNonce(NONCE_JSON);
    stubSuccessfulRegistration("@agency-1-service:caritas.local");

    // when
    var result = matrixProvisioningService.ensureAgencyAccount("agency-1", "Display Name");

    // then
    assertThat(result).isPresent();
    verify(restTemplate)
        .postForEntity(eq(REGISTER_URL), registerRequestCaptor.capture(), eq(Map.class));
    assertThat(registerRequestCaptor.getValue().getBody()).containsEntry("nonce", NONCE);
  }

  @Test
  void ensureAgencyAccount_Should_NotTreat400WithoutUserInUseAsExistingUser() {
    // given
    stubSuccessfulNonce(NONCE_JSON);
    stubRegistrationHttpError(HttpStatus.BAD_REQUEST, "invalid request");

    // when
    var result = matrixProvisioningService.ensureAgencyAccount("agency-1", "Display Name");

    // then
    assertThat(result).isEmpty();
    verify(restTemplate, never()).postForEntity(eq(LOGIN_URL), any(), eq(Map.class));
    verify(restTemplate, never()).exchange(anyString(), eq(HttpMethod.POST), any(), eq(Void.class));
  }

  @Test
  void ensureAgencyAccount_Should_NotTreatSuccessfulRegistrationAsDuplicate() {
    // given — HTTP 200 success path; isUserAlreadyExisting must not trigger rotation
    stubSuccessfulNonce(NONCE_JSON);
    stubSuccessfulRegistration("@agency-1-service:caritas.local");

    // when
    var result = matrixProvisioningService.ensureAgencyAccount("agency-1", "Display Name");

    // then
    assertThat(result).isPresent();
    verify(restTemplate, never()).postForEntity(eq(LOGIN_URL), any(), eq(Map.class));
    verify(restTemplate, never()).exchange(anyString(), eq(HttpMethod.POST), any(), eq(Void.class));
  }

  @Test
  void ensureAgencyAccount_Should_GenerateUrlSafeBase64PasswordWith24Chars() {
    // given
    stubSuccessfulNonce(NONCE_JSON);
    stubSuccessfulRegistration("@agency-1-service:caritas.local");

    // when
    var result = matrixProvisioningService.ensureAgencyAccount("agency-1", "Display Name");

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getPassword()).matches(URL_SAFE_PASSWORD_PATTERN);
  }

  @Test
  void ensureAgencyAccount_Should_SendValidHmacSha1MacInRegisterPayload() throws Exception {
    // given
    stubSuccessfulNonce(NONCE_JSON);
    when(restTemplate.postForEntity(eq(REGISTER_URL), any(), eq(Map.class)))
        .thenReturn(ResponseEntity.ok(Map.of("user_id", "@agency-1-service:caritas.local")));

    // when
    matrixProvisioningService.ensureAgencyAccount("agency-1", "Display Name");

    // then
    verify(restTemplate)
        .postForEntity(eq(REGISTER_URL), registerRequestCaptor.capture(), eq(Map.class));
    Map<String, Object> payload = registerRequestCaptor.getValue().getBody();
    assertThat(payload).isNotNull();

    String mac = (String) payload.get("mac");
    String username = (String) payload.get("username");
    String password = (String) payload.get("password");
    String nonce = (String) payload.get("nonce");

    assertThat(mac).matches("[0-9a-f]{40}");
    assertThat(mac).isEqualTo(computeExpectedMac(nonce, username, password));
  }

  @Test
  void ensureAgencyAccount_Should_ReturnEmpty_When_RegistrationSucceedsWithoutUserId() {
    // given
    stubSuccessfulNonce(NONCE_JSON);
    when(restTemplate.postForEntity(eq(REGISTER_URL), any(), eq(Map.class)))
        .thenReturn(ResponseEntity.ok(new HashMap<>()));

    // when
    var result = matrixProvisioningService.ensureAgencyAccount("agency-1", "Display Name");

    // then
    assertThat(result).isEmpty();
    verify(restTemplate, never()).postForEntity(eq(LOGIN_URL), any(), eq(Map.class));
    verify(restTemplate, never()).exchange(anyString(), eq(HttpMethod.POST), any(), eq(Void.class));
  }

  @Test
  void ensureAgencyAccount_Should_RegisterWithDashServiceUsername_When_BaseUsernameIsBlank() {
    // given
    stubSuccessfulNonce(NONCE_JSON);
    stubSuccessfulRegistration("@-service:caritas.local");

    // when
    var result = matrixProvisioningService.ensureAgencyAccount("", "Display Name");

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getUserId()).isEqualTo("@-service:caritas.local");
    verify(restTemplate)
        .postForEntity(eq(REGISTER_URL), registerRequestCaptor.capture(), eq(Map.class));
    assertThat(registerRequestCaptor.getValue().getBody())
        .containsEntry("username", "-service")
        .extracting(body -> body.get("username"))
        .asString()
        .hasSize(8);
  }

  @Test
  void ensureAgencyAccount_Should_NotTruncateUsername_When_SanitizedResultIsExactly30Chars() {
    // given — 22-char base + "-service" = exactly 30 chars; no truncation
    final String baseUsername = "abcdefghijklmnopqrstuv";
    final String expectedUsername = "abcdefghijklmnopqrstuv-service";
    final String truncatedFromTest3 = "abcdefghijklmnopqrstuvw-servic";
    assertThat(baseUsername + "-service").hasSize(30);
    assertThat(expectedUsername).hasSize(30);

    stubSuccessfulNonce(NONCE_JSON);
    stubSuccessfulRegistration("@abcdefghijklmnopqrstuv-service:caritas.local");

    // when
    matrixProvisioningService.ensureAgencyAccount(baseUsername, "Display Name");

    // then
    verify(restTemplate)
        .postForEntity(eq(REGISTER_URL), registerRequestCaptor.capture(), eq(Map.class));
    assertThat(registerRequestCaptor.getValue().getBody())
        .containsEntry("username", expectedUsername)
        .extracting(body -> body.get("username"))
        .asString()
        .hasSize(30)
        .isNotEqualTo(truncatedFromTest3);
  }

  @Test
  void ensureAgencyAccount_Should_ReturnEmpty_When_NonceJsonHasExtraWhitespace() {
    // given — string-based extractNonce requires exact "nonce":" (no spaces); pretty JSON fails
    stubSuccessfulNonce("{ \"nonce\" : \"test-nonce-abc\" }");

    // when
    var result = matrixProvisioningService.ensureAgencyAccount("agency-1", "Display Name");

    // then
    assertThat(result).isEmpty();
    verify(restTemplate, never()).postForEntity(eq(REGISTER_URL), any(), eq(Map.class));
  }

  @Test
  void ensureAgencyAccount_Should_ExtractNonce_When_ResponseBodyHasMultipleFields() {
    // given
    stubSuccessfulNonce("{\"other\":\"value\",\"nonce\":\"test-nonce-abc\"}");
    stubSuccessfulRegistration("@agency-1-service:caritas.local");

    // when
    var result = matrixProvisioningService.ensureAgencyAccount("agency-1", "Display Name");

    // then
    assertThat(result).isPresent();
    verify(restTemplate)
        .postForEntity(eq(REGISTER_URL), registerRequestCaptor.capture(), eq(Map.class));
    assertThat(registerRequestCaptor.getValue().getBody()).containsEntry("nonce", NONCE);
  }

  @Test
  void ensureAgencyAccount_Should_ReturnEmpty_When_ResponseStatusException400WithNullBody() {
    // given — ResponseStatusException path passes null body to isUserAlreadyExisting
    stubSuccessfulNonce(NONCE_JSON);
    stubRegistrationResponseStatusError(HttpStatus.BAD_REQUEST);

    // when
    var result = matrixProvisioningService.ensureAgencyAccount("agency-1", "Display Name");

    // then
    assertThat(result).isEmpty();
    verify(restTemplate, never()).postForEntity(eq(LOGIN_URL), any(), eq(Map.class));
  }
}
