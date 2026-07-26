package de.caritas.cob.agencyservice.api.admin.service.legal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import de.caritas.cob.agencyservice.api.exception.httpresponses.BadRequestException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.InternalServerErrorException;
import de.caritas.cob.agencyservice.api.validation.InputSanitizer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The one canonical sanitisation path for admin-authored legal content (department DPP, department
 * Impressum and the shared ADR-014 legal texts): every translation value is OWASP HTML-sanitised;
 * {@code __meta}-suffixed keys (the platform-wide translation-metadata convention) carry JSON, not
 * HTML — running them through the HTML sanitizer would corrupt the payload, so they are passed
 * through verbatim after a <em>strict</em> schema validation ({@code mt:boolean},
 * {@code src:string}, {@code at:string}, non-blank, no unknown fields). Because the metadata is
 * stored verbatim and later served publicly, a mere "parses as JSON" check is not enough — that
 * used to be the imprint path's behaviour and allowed arbitrary unsanitised JSON payloads behind
 * the suffix.
 */
@Component
@RequiredArgsConstructor
public class LegalContentSanitizer {

  private static final String META_KEY_SUFFIX = "__meta";

  private final @NonNull InputSanitizer inputSanitizer;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ObjectReader metaJsonReader =
      objectMapper.readerFor(JsonNode.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

  /**
   * Sanitises the multilingual content map and serialises it to the stored JSON language→HTML map
   * string. {@code null} content becomes an empty JSON object.
   */
  public String sanitizeToJson(Map<String, String> content) {
    return toJson(sanitizeTranslations(content));
  }

  private Map<String, String> sanitizeTranslations(Map<String, String> content) {
    if (content == null) {
      return Map.of();
    }
    return content.entrySet().stream()
        .filter(entry -> entry.getKey() != null)
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                entry -> sanitizeOrValidateValue(entry.getKey(), entry.getValue()),
                (existing, replacement) -> replacement,
                LinkedHashMap::new));
  }

  private String sanitizeOrValidateValue(String key, String value) {
    var safeValue = value == null ? "" : value;
    if (key.endsWith(META_KEY_SUFFIX)) {
      assertValidMeta(key, safeValue);
      return safeValue;
    }
    return inputSanitizer.sanitizeAllowingFormattingAndLinks(safeValue);
  }

  /**
   * Strictly validates a {@code __meta} translation-metadata payload: a non-blank JSON object
   * holding only {@code mt} (boolean), {@code src} (string) and {@code at} (string), with
   * {@code src}/{@code at} non-blank. Blanks, scalars, arrays, trailing tokens and any unknown
   * field are rejected with a 400.
   */
  private void assertValidMeta(String key, String value) {
    if (value == null || value.isBlank()) {
      throw invalidMeta(key);
    }
    final JsonNode node;
    try {
      node = metaJsonReader.readValue(value);
    } catch (JsonProcessingException e) {
      throw invalidMeta(key);
    }
    if (node == null || !node.isObject()) {
      throw invalidMeta(key);
    }
    int knownFields = 0;
    if (node.has("mt")) {
      requireBoolean(key, node.get("mt"));
      knownFields++;
    }
    if (node.has("src")) {
      requireNonBlankText(key, node.get("src"));
      knownFields++;
    }
    if (node.has("at")) {
      requireNonBlankText(key, node.get("at"));
      knownFields++;
    }
    if (node.size() != knownFields) {
      throw invalidMeta(key);
    }
  }

  private void requireBoolean(String key, JsonNode value) {
    if (value == null || !value.isBoolean()) {
      throw invalidMeta(key);
    }
  }

  private void requireNonBlankText(String key, JsonNode value) {
    if (value == null || !value.isTextual() || value.asText().isBlank()) {
      throw invalidMeta(key);
    }
  }

  private BadRequestException invalidMeta(String key) {
    return new BadRequestException(
        String.format("Translation metadata key '%s' does not contain valid JSON", key));
  }

  private String toJson(Map<String, String> sanitized) {
    try {
      return objectMapper.writeValueAsString(sanitized);
    } catch (JsonProcessingException e) {
      throw new InternalServerErrorException("Could not serialize legal text content", e);
    }
  }
}
