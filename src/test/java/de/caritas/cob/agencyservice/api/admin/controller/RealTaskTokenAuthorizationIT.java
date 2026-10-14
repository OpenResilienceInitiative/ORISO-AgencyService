package de.caritas.cob.agencyservice.api.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.caritas.cob.agencyservice.api.repository.agencyidreservation.AgencyIdReservationRepository;
import de.caritas.cob.agencyservice.api.service.TenantService;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Actual issued bearer passes the native resource-server decoder and HTTP server. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
@ActiveProfiles("testing")
@EnabledIfEnvironmentVariable(named = "ORISO_TASK_TOKEN_FIXTURE", matches = ".+")
@TestPropertySource(properties = {
    "multitenancy.enabled=true", "feature.demographics.enabled=false",
    "feature.multitenancy.with.single.domain.enabled=true",
    "csrf.header.property=csrfHeader", "csrf.cookie.property=csrfCookie",
    "TASK_IDENTITY_AUDIENCE=agencyservice",
    "service.encryption.appkey=synthetic-agency-matrix-encryption-key"
})
class RealTaskTokenAuthorizationIT {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final HttpClient HTTP = HttpClient.newHttpClient();
  @LocalServerPort private int port;
  @Autowired private AgencyIdReservationRepository reservations;
  @Autowired @org.springframework.beans.factory.annotation.Qualifier("agencyRepository")
  private de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository agencies;
  @MockitoBean private TenantService tenantService;
  @MockitoBean private de.caritas.cob.agencyservice.api.service.TopicService topics;
  @MockitoBean private de.caritas.cob.agencyservice.api.service.ConsultingTypeService types;
  @MockitoBean private de.caritas.cob.agencyservice.api.service.AppointmentService appointments;
  @MockitoBean private de.caritas.cob.agencyservice.api.service.matrix.MatrixProvisioningService matrix;

  private static JsonNode fixture() {
    try {
      return JSON.readTree(Files.readString(Path.of(System.getenv("ORISO_TASK_TOKEN_FIXTURE"))));
    } catch (Exception failure) {
      throw new IllegalStateException("Synthetic native task fixture unavailable");
    }
  }

  @DynamicPropertySource
  static void nativeBindings(DynamicPropertyRegistry properties) {
    JsonNode fixture = fixture();
    String issuer = fixture.path("issuer").asText();
    properties.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> issuer);
    properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
        () -> issuer + "/protocol/openid-connect/certs");
    for (JsonNode task : fixture.path("tasks")) {
      String prefix = "IDENTITY_" + task.path("key").asText();
      properties.add(prefix + "_CLIENT_ID", () -> task.path("clientId").asText());
      properties.add(prefix + "_SERVICE_SUBJECT", () -> task.path("subject").asText());
    }
  }

  private String token(String key) throws Exception {
    JsonNode fixture = fixture();
    JsonNode task = null;
    for (JsonNode candidate : fixture.path("tasks")) {
      if (key.equals(candidate.path("key").asText())) {
        task = candidate;
      }
    }
    if (task == null) {
      throw new IllegalArgumentException("Unknown synthetic task");
    }
    String form = "grant_type=client_credentials&client_id="
        + URLEncoder.encode(task.path("clientId").asText(), StandardCharsets.UTF_8)
        + "&client_secret=" + URLEncoder.encode(task.path("secret").asText(), StandardCharsets.UTF_8);
    var request = HttpRequest.newBuilder(URI.create(fixture.path("issuer").asText()
            + "/protocol/openid-connect/token"))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(form)).build();
    var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    return JSON.readTree(response.body()).path("access_token").asText();
  }

  private HttpResponse<String> call(String method, String path, String bearer, String body)
      throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
        .header("Content-Type", "application/json").header("tenantId", "0")
        .header("csrfHeader", "test").header("Cookie", "csrfCookie=test");
    if (bearer != null) {
      request.header("Authorization", "Bearer " + bearer);
    }
    request.method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
    return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  @BeforeEach
  void setup() {
    when(tenantService.getRestrictedTenantDataByTenantId(anyLong()))
        .thenAnswer(call -> new RestrictedTenantDTO().id(call.getArgument(0)));
  }

  @AfterEach
  void cleanup() {
    reservations.deleteById(9129L);
    reservations.deleteById(9128L);
    agencies.deleteById(9128L);
  }

  @Test
  void realReservationActorWithoutTenantClaimCanReleaseOnlyItsOwnProof() throws Exception {
    String actor = token("INVITE_RESERVATIONS");
    var created = call("POST", "/agencyadmin/agencyids/reservations", actor,
        "{\"agencyId\":9129,\"tenantId\":7}");
    assertThat(created.statusCode()).isEqualTo(201);
    String proof = JSON.readTree(created.body()).path("token").asText();
    assertThat(reservations.existsById(9129L)).isTrue();
    assertThat(call("DELETE", "/agencyadmin/agencyids/reservations/9129?reservationToken=foreign",
        actor, null).statusCode()).isEqualTo(403);
    assertThat(reservations.existsById(9129L)).isTrue();
    assertThat(call("DELETE", "/agencyadmin/agencyids/reservations/9129?reservationToken="
        + URLEncoder.encode(proof, StandardCharsets.UTF_8), actor, null).statusCode()).isEqualTo(204);
    assertThat(reservations.existsById(9129L)).isFalse();
  }

  @Test
  void actualWizardCreatesOnlyOwnedTargetAndContactReaderCannotReadMatrix() throws Exception {
    when(types.getExtendedConsultingTypeResponseDTO(org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(de.caritas.cob.agencyservice.testHelper.TestConstants.CONSULTING_TYPE_SETTINGS_AIDS);
    when(matrix.ensureAgencyAccount(org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.Optional.empty());
    String worker = token("INVITE_RESERVATIONS");
    var reservation = call("POST", "/agencyadmin/agencyids/reservations", worker,
        "{\"agencyId\":9128,\"tenantId\":7}");
    assertThat(reservation.statusCode()).isEqualTo(201);
    final String proof = JSON.readTree(reservation.body()).path("token").asText();
    var dto = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(
        de.caritas.cob.agencyservice.testHelper.TestConstants.VALID_AGENCY_DTO);
    dto.put("tenantId", 7).put("reservedAgencyId", 9128).put("reservationToken", "foreign");
    String wizard = token("CONFIG_WIZARD");
    assertThat(call("POST", "/agencyadmin/agencies", wizard, dto.toString()).statusCode())
        .isEqualTo(409);
    assertThat(reservations.existsById(9128L)).isTrue();
    dto.put("reservationToken", proof);
    assertThat(call("POST", "/agencyadmin/agencies", wizard, dto.toString()).statusCode())
        .isEqualTo(201);
    assertThat(agencies.findById(9128L)).isPresent();
    assertThat(call("GET", "/agencyadmin/agencies/9128", wizard, null).statusCode()).isEqualTo(200);
    assertThat(call("DELETE", "/agencyadmin/agencyids/reservations/9128?reservationToken="
        + URLEncoder.encode(proof, StandardCharsets.UTF_8), worker, null).statusCode())
        .isEqualTo(403);
    String dispatch = token("NOTIFICATION_DISPATCH");
    assertThat(call("GET", "/internal/agencies/9128/contact-details?tenantId=7", dispatch, null)
        .statusCode()).isEqualTo(200);
    assertThat(call("GET", "/internal/agencies/9128/contact-details?tenantId=8", dispatch, null)
        .statusCode()).isEqualTo(404);
    String matrixPath = "/internal/agencies/9128/matrix-service-account";
    assertThat(call("GET", matrixPath, dispatch, null).statusCode()).isEqualTo(403);
    assertThat(call("POST", matrixPath, dispatch, null).statusCode()).isEqualTo(403);
    when(matrix.ensureAgencyAccount("agency-9128", dto.path("name").asText()))
        .thenReturn(java.util.Optional.of(
            new de.caritas.cob.agencyservice.api.service.matrix.MatrixProvisioningService.MatrixCredentials(
                "@synthetic-agency:matrix.local", "synthetic-matrix-transport-credential")));
    String matrixActor = token("MATRIX_AGENCY");
    var provision = call("POST", matrixPath, matrixActor, null);
    assertThat(provision.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(provision.body()).has("matrixPassword")).isFalse();
    var read = call("GET", matrixPath, matrixActor, null);
    assertThat(read.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(read.body()).has("matrixPassword")).isFalse();
    assertThat(agencies.findById(9128L).orElseThrow().getMatrixUserId())
        .isEqualTo("@synthetic-agency:matrix.local");
  }

  @Test
  void wrongTaskAndGeneralAdministrationRemainDenied() throws Exception {
    assertThat(call("POST", "/agencyadmin/agencyids/reservations", token("NOTIFICATION_DISPATCH"),
        "{\"agencyId\":9129,\"tenantId\":7}").statusCode()).isEqualTo(403);
    assertThat(call("GET", "/agencyadmin/agencies", token("CONFIG_WIZARD"), null)
        .statusCode()).isEqualTo(403);
    assertThat(reservations.existsById(9129L)).isFalse();
  }

  @Test
  void nativeSignedWrongBindingsAndUnrelatedGrantsAreDeniedWithoutMutation() throws Exception {
    for (String fault : java.util.List.of("mixedRoles", "wrongAudience", "wrongSubject")) {
      String actor = fixture().path("variants").path("INVITE_RESERVATIONS").path(fault).asText();
      assertThat(actor).isNotBlank();
      assertThat(call("POST", "/agencyadmin/agencyids/reservations", actor,
          "{\"agencyId\":9129,\"tenantId\":7}").statusCode()).isEqualTo(403);
      assertThat(reservations.existsById(9129L)).isFalse();
      String matrixActor = fixture().path("variants").path("MATRIX_AGENCY").path(fault).asText();
      assertThat(call("POST", "/internal/agencies/9128/matrix-service-account", matrixActor, null)
          .statusCode()).isEqualTo(403);
    }
  }

  @Test
  void tamperedNativeSignatureIsRejectedBeforeTheReceiver() throws Exception {
    String actor = token("INVITE_RESERVATIONS");
    int signature = actor.lastIndexOf('.') + 1;
    String changed = actor.substring(0, signature)
        + (actor.charAt(signature) == 'A' ? 'B' : 'A') + actor.substring(signature + 1);
    assertThat(call("POST", "/agencyadmin/agencyids/reservations", changed,
        "{\"agencyId\":9129,\"tenantId\":7}").statusCode()).isEqualTo(401);
    assertThat(reservations.existsById(9129L)).isFalse();
  }
}
