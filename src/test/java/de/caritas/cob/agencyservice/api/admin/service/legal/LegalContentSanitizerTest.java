package de.caritas.cob.agencyservice.api.admin.service.legal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import de.caritas.cob.agencyservice.api.exception.httpresponses.BadRequestException;
import de.caritas.cob.agencyservice.api.validation.InputSanitizer;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The one canonical sanitisation path for admin-authored legal content (DPP, Impressum, shared
 * legal texts): every translation value is OWASP-sanitised, {@code __meta}-suffixed keys carry
 * translation metadata as JSON and are validated against the strict schema (only {@code
 * mt:boolean}, {@code src:string}, {@code at:string}, non-blank, no unknown fields) because they
 * are stored verbatim and served publicly.
 */
class LegalContentSanitizerTest {

  private LegalContentSanitizer sanitizer;

  @BeforeEach
  void setUp() {
    sanitizer = new LegalContentSanitizer(new InputSanitizer());
  }

  @Test
  void sanitizeToJson_Should_stripDangerousMarkup_andKeepText() {
    var json =
        sanitizer.sanitizeToJson(
            Map.of("de", "<p onclick=\"steal()\">Impressum <script>bad()</script></p>"));

    assertThat(json).startsWith("{").contains("\"de\":");
    assertThat(json).contains("Impressum").doesNotContain("script").doesNotContain("onclick");
  }

  @Test
  void sanitizeToJson_Should_returnEmptyJsonObject_When_contentNull() {
    assertThat(sanitizer.sanitizeToJson(null)).isEqualTo("{}");
  }

  @Test
  void sanitizeToJson_Should_passThroughValidStrictMeta_Unsanitised() {
    var metaJson = "{\"mt\":true,\"src\":\"de\",\"at\":\"2026-07-16T10:00:00Z\"}";

    var json = sanitizer.sanitizeToJson(Map.of("de", "<p>x</p>", "de__meta", metaJson));

    assertThat(json).contains("\"de__meta\":");
    // quotes survive as JSON, not HTML entities
    assertThat(json).contains("mt");
  }

  @Test
  void sanitizeToJson_Should_rejectMetaWithUnknownFields() {
    // the formerly lenient imprint path accepted arbitrary JSON here — no longer
    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(
            () ->
                sanitizer.sanitizeToJson(
                    Map.of("de__meta", "{\"source\":\"machine\",\"reviewed\":false}")));
  }

  @Test
  void sanitizeToJson_Should_rejectNonObjectMeta() {
    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(() -> sanitizer.sanitizeToJson(Map.of("de__meta", "[\"de\",\"en\"]")));
    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(() -> sanitizer.sanitizeToJson(Map.of("de__meta", "\"just-a-string\"")));
    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(() -> sanitizer.sanitizeToJson(Map.of("de__meta", "not-json{")));
    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(() -> sanitizer.sanitizeToJson(Map.of("de__meta", "")));
  }

  @Test
  void sanitizeToJson_Should_rejectWrongMetaFieldTypes() {
    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(() -> sanitizer.sanitizeToJson(Map.of("de__meta", "{\"mt\":\"true\"}")));
    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(() -> sanitizer.sanitizeToJson(Map.of("de__meta", "{\"src\":\"   \"}")));
  }

  @Test
  void sanitizeToJson_Should_treatNullValuesAsEmpty_andSkipNullKeys() {
    var content = new java.util.HashMap<String, String>();
    content.put("de", null);
    content.put(null, "<p>ignored</p>");

    var json = sanitizer.sanitizeToJson(content);

    assertThat(json).contains("\"de\":\"\"");
    assertThat(json).doesNotContain("ignored");
  }
}
