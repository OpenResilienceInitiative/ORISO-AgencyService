package de.caritas.cob.agencyservice.api.service.matrix;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatrixProvisioningService {

  private static final String ENDPOINT_REGISTER_USER = "/_synapse/admin/v1/register";
  private static final String ENDPOINT_LOGIN = "/_matrix/client/r0/login";
  private static final String ENDPOINT_RESET_PASSWORD = "/_synapse/admin/v1/reset_password/";

  private final @NonNull MatrixConfig matrixConfig;
  private final @NonNull RestTemplate restTemplate;

  private final SecureRandom secureRandom = new SecureRandom();

  public Optional<MatrixCredentials> ensureAgencyAccount(String baseUsername, String displayName) {
    try {
      String username = sanitizeUsername(baseUsername + "-service");
      String password = generatePassword();

      String userId = registerUser(username, password, displayName);
      if (userId == null) {
        return Optional.empty();
      }

      return Optional.of(new MatrixCredentials(userId, password));
    } catch (Exception ex) {
      log.warn("Matrix provisioning for agency '{}' failed: {}", baseUsername, ex.getMessage());
      return Optional.empty();
    }
  }

  private String registerUser(String username, String password, String displayName)
      throws NoSuchAlgorithmException, InvalidKeyException {
    String nonceUrl = matrixConfig.getApiUrl(ENDPOINT_REGISTER_USER);

    ResponseEntity<String> nonceResponse;
    try {
      HttpHeaders nonceHeaders = getAdminHeaders();
      HttpEntity<Void> nonceRequest = new HttpEntity<>(nonceHeaders);
      nonceResponse =
          restTemplate.exchange(nonceUrl, HttpMethod.GET, nonceRequest, String.class);
    } catch (ResponseStatusException ex) {
      log.error(
          "Matrix provisioning: failed to obtain nonce for user {} (status {}): {}",
          username,
          ex.getStatusCode(),
          ex.getReason());
      return null;
    }
    String nonce = extractNonce(nonceResponse.getBody());
    if (nonce == null) {
      log.warn("Matrix provisioning: nonce missing for user {}", username);
      return null;
    }

    HttpHeaders headers = getAdminHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    String mac = generateMac(nonce, username, password, false);

    Map<String, Object> payload = new HashMap<>();
    payload.put("username", username);
    payload.put("password", password);
    payload.put("displayname", displayName);
    payload.put("admin", false);
    payload.put("nonce", nonce);
    payload.put("mac", mac);

    HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

    try {
      ResponseEntity<Map> response = restTemplate.postForEntity(nonceUrl, request, Map.class);
      Object body = response.getBody() != null ? response.getBody().get("user_id") : null;
      if (body instanceof String userId) {
        log.info("Provisioned Matrix agency user: {} -> {}", username, userId);
        return userId;
      }
      log.warn("Matrix provisioning succeeded without user_id for {}", username);
    } catch (HttpClientErrorException ex) {
      if (isUserAlreadyExisting(ex.getStatusCode(), ex.getResponseBodyAsString())) {
        String userId = String.format("@%s:%s", username, matrixConfig.getServerName());
        if (resetExistingUserPassword(userId, password)) {
          log.info("Matrix user {} already existed. Password rotated.", username);
          return userId;
        }
      } else {
        log.error(
            "Matrix returned {} while creating user {}: {}",
            ex.getStatusCode(),
            username,
            ex.getResponseBodyAsString());
      }
    } catch (ResponseStatusException ex) {
      if (isUserAlreadyExisting(ex.getStatusCode(), null)) {
        String userId = String.format("@%s:%s", username, matrixConfig.getServerName());
        if (resetExistingUserPassword(userId, password)) {
          log.info("Matrix user {} already existed. Password rotated.", username);
          return userId;
        }
      } else {
        log.error(
            "Matrix returned {} while creating user {}: {}",
            ex.getStatusCode(),
            username,
            ex.getReason());
      }
    }

    return null;
  }

  private HttpHeaders getAdminHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + matrixConfig.getRegistrationSharedSecret());
    return headers;
  }

  private String sanitizeUsername(String base) {
    String sanitized = base.replaceAll("[^a-zA-Z0-9._-]", "-").toLowerCase();
    if (sanitized.length() > 30) {
      sanitized = sanitized.substring(0, 30);
    }
    return sanitized;
  }

  private String generatePassword() {
    byte[] bytes = new byte[18];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private boolean isUserAlreadyExisting(HttpStatusCode status, String responseBody) {
    if (status != null && status.value() == HttpStatus.CONFLICT.value()) {
      return true;
    }
    if (status != null && status.value() == HttpStatus.BAD_REQUEST.value()) {
      if (responseBody == null) {
        return true;
      }
      return responseBody.contains("M_USER_IN_USE");
    }
    return false;
  }

  private boolean resetExistingUserPassword(String userId, String newPassword) {
    if (!matrixConfig.hasAdminCredentials()) {
      log.warn("Cannot rotate password for {}: admin credentials missing", userId);
      return false;
    }

    try {
      String accessToken = fetchAdminAccessToken();
      if (accessToken == null) {
        log.warn("Failed to obtain admin access token while rotating password for {}", userId);
        return false;
      }

      HttpHeaders headers = new HttpHeaders();
      headers.setBearerAuth(accessToken);
      headers.setContentType(MediaType.APPLICATION_JSON);

      Map<String, Object> body = new HashMap<>();
      body.put("new_password", newPassword);
      body.put("logout_devices", Boolean.FALSE);

      String resetPasswordUrl =
          UriComponentsBuilder.fromHttpUrl(matrixConfig.getApiUrl(ENDPOINT_RESET_PASSWORD))
              .pathSegment(userId)
              .toUriString();

      restTemplate.exchange(
          resetPasswordUrl,
          HttpMethod.POST,
          new HttpEntity<>(body, headers),
          Void.class);
      return true;
    } catch (Exception resetEx) {
      log.error("Failed to rotate password for {}: {}", userId, resetEx.getMessage());
      return false;
    }
  }

  private String fetchAdminAccessToken() {
    Map<String, Object> payload = new HashMap<>();
    payload.put("type", "m.login.password");
    payload.put("user", matrixConfig.getAdminUsername());
    payload.put("password", matrixConfig.getAdminPassword());

    try {
      ResponseEntity<Map> response =
          restTemplate.postForEntity(matrixConfig.getApiUrl(ENDPOINT_LOGIN), payload, Map.class);
      Object token = response.getBody() != null ? response.getBody().get("access_token") : null;
      if (token instanceof String accessToken) {
        return accessToken;
      }
    } catch (Exception ex) {
      log.error("Failed to login Matrix admin user {}: {}", matrixConfig.getAdminUsername(), ex.getMessage());
    }
    return null;
  }

  private String extractNonce(String responseBody) {
    if (responseBody == null || !responseBody.contains("\"nonce\"")) {
      return null;
    }
    int start = responseBody.indexOf("\"nonce\":\"") + 9;
    int end = responseBody.indexOf('"', start);
    if (start < 9 || end <= start) {
      return null;
    }
    return responseBody.substring(start, end);
  }

  private String generateMac(String nonce, String username, String password, boolean admin)
      throws NoSuchAlgorithmException, InvalidKeyException {
    String message =
        nonce + "\0" + username + "\0" + password + "\0" + (admin ? "admin" : "notadmin");

    Mac hmacSha1 = Mac.getInstance("HmacSHA1");
    SecretKeySpec secretKey =
        new SecretKeySpec(
            matrixConfig.getRegistrationSharedSecret().getBytes(StandardCharsets.UTF_8),
            "HmacSHA1");
    hmacSha1.init(secretKey);

    byte[] macBytes = hmacSha1.doFinal(message.getBytes(StandardCharsets.UTF_8));
    return bytesToHex(macBytes);
  }

  private String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  @Value
  public static class MatrixCredentials {
    String userId;
    String password;
  }
}


