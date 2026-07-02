package de.caritas.cob.agencyservice.api.admin.service.agency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.RuntimeJsonMappingException;
import de.caritas.cob.agencyservice.api.model.AgencyAdminControls;
import de.caritas.cob.agencyservice.api.model.Settings;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class AgencySettingsServiceTest {

  private AgencySettingsService agencySettingsService;

  private final ObjectMapper testObjectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    agencySettingsService = new AgencySettingsService();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t"})
  void toSettings_Should_ReturnEmptySettings_When_InputIsBlank(String settingsJson) {
    Settings result = agencySettingsService.toSettings(settingsJson);

    assertThat(result).isNotNull();
    assertThat(result.getFeatureStatisticsEnabled()).isNull();
    assertThat(result.getFeatureTopicsEnabled()).isNull();
    assertThat(result.getAgencyAdminControls()).isNull();
  }

  @Test
  void toSettings_Should_ReturnEmptySettings_When_InputIsNull() {
    Settings result = agencySettingsService.toSettings(null);

    assertThat(result).isNotNull();
    assertThat(result.getFeatureStatisticsEnabled()).isNull();
    assertThat(result.getFeatureTopicsEnabled()).isNull();
    assertThat(result.getAgencyAdminControls()).isNull();
  }

  @Test
  void toSettings_Should_ReturnParsedSettings_When_InputIsValidJson() {
    String settingsJson =
        "{\"featureStatisticsEnabled\":true,\"featureTopicsEnabled\":false,"
            + "\"activeLanguages\":[\"de\",\"en\"]}";

    Settings result = agencySettingsService.toSettings(settingsJson);

    assertThat(result.getFeatureStatisticsEnabled()).isTrue();
    assertThat(result.getFeatureTopicsEnabled()).isFalse();
    assertThat(result.getActiveLanguages()).containsExactly("de", "en");
  }

  @Test
  void toSettings_Should_ReturnEmptySettings_When_InputIsJsonNullLiteral() {
    Settings result = agencySettingsService.toSettings("null");

    assertThat(result).isNotNull();
    assertThat(result.getFeatureStatisticsEnabled()).isNull();
    assertThat(result.getFeatureTopicsEnabled()).isNull();
    assertThat(result.getAgencyAdminControls()).isNull();
  }

  @Test
  void toSettings_Should_ThrowRuntimeJsonMappingException_When_InputIsInvalidJson() {
    assertThrows(
        RuntimeJsonMappingException.class,
        () -> agencySettingsService.toSettings("{not-valid-json"));
  }

  @Test
  void toSettingsJson_Should_ReturnNull_When_SettingsIsNull() {
    assertThat(agencySettingsService.toSettingsJson(null)).isNull();
  }

  @Test
  void toSettingsJson_Should_ReturnJsonWithFeatureFlags_When_SettingsIsValid() throws Exception {
    Settings settings = buildSettingsWithoutControls();

    String json = agencySettingsService.toSettingsJson(settings);
    JsonNode parsed = parseJson(json);

    assertThat(json).isNotBlank();
    assertThat(parsed.get("featureStatisticsEnabled").booleanValue()).isTrue();
    assertThat(parsed.get("featureTopicsEnabled").booleanValue()).isFalse();
    assertThat(agencyAdminControlsAbsent(parsed)).isTrue();
  }

  @Test
  void toSettingsJson_Should_ThrowRuntimeJsonMappingException_When_SettingsCannotBeSerialized() {
    ObjectMapper failingMapper = new FailingObjectMapper();
    ReflectionTestUtils.setField(agencySettingsService, "objectMapper", failingMapper);

    assertThrows(
        RuntimeJsonMappingException.class,
        () -> agencySettingsService.toSettingsJson(buildSettingsWithoutControls()));
  }

  @Test
  void toSettingsJson_Should_OmitAgencyAdminControls_When_SettingsHasAgencyAdminControls() {
    Settings settings = buildSettingsWithControls();

    JsonNode parsed = parseJson(agencySettingsService.toSettingsJson(settings));

    assertThat(parsed.get("featureStatisticsEnabled").booleanValue()).isTrue();
    assertThat(agencyAdminControlsAbsent(parsed)).isTrue();
  }

  @Test
  void toSettingsJson_Should_PreserveAllOtherFields_When_StrippingAgencyAdminControls() {
    Settings settings = buildSettingsWithControls();

    JsonNode parsed = parseJson(agencySettingsService.toSettingsJson(settings));

    assertThat(parsed.get("featureStatisticsEnabled").booleanValue()).isTrue();
    assertThat(parsed.get("activeLanguages")).isNotNull();
    assertThat(parsed.get("activeLanguages").get(0).asText()).isEqualTo("de");
    assertThat(parsed.get("activeLanguages").get(1).asText()).isEqualTo("en");
    assertThat(agencyAdminControlsAbsent(parsed)).isTrue();
  }

  private Settings buildSettingsWithControls() {
    return new Settings()
        .featureStatisticsEnabled(true)
        .activeLanguages(List.of("de", "en"))
        .agencyAdminControls(new AgencyAdminControls().permissionsPageEnabled(true));
  }

  private Settings buildSettingsWithoutControls() {
    return new Settings().featureStatisticsEnabled(true).featureTopicsEnabled(false);
  }

  private boolean agencyAdminControlsAbsent(JsonNode parsed) {
    JsonNode controls = parsed.path("agencyAdminControls");
    return controls.isMissingNode() || controls.isNull();
  }

  private JsonNode parseJson(String json) {
    try {
      return testObjectMapper.readTree(json);
    } catch (JsonProcessingException exception) {
      throw new RuntimeException(exception);
    }
  }

  private static final class FailingObjectMapper extends ObjectMapper {

    @Override
    public String writeValueAsString(Object value) throws JsonProcessingException {
      throw new JsonMappingException(null, "serialization failed");
    }
  }
}
