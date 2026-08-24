package de.caritas.cob.agencyservice.api.admin.service.legal;

import static org.assertj.core.api.Assertions.assertThat;

import de.caritas.cob.agencyservice.api.validation.InputSanitizer;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * ADR-021 decision 6 in code: the {@code {{key}}} dialect, and its collision with the OWASP
 * sanitizer.
 */
class LegalTextTokensTest {

  private final InputSanitizer inputSanitizer = new InputSanitizer();

  /**
   * The measurement behind the whole {@link LegalTextTokens#restoreKnownTokens} mechanism. OWASP's
   * {@code java-html-sanitizer} splits {@code &#123;&#123;} with an HTML comment so that sanitised
   * output cannot be re-interpreted as a mustache template downstream. Pinning it here means the
   * day the library changes that behaviour, this test says so instead of the repair silently
   * becoming dead code.
   */
  @Test
  void owaspSanitizer_Should_splitDoubleBraces_whichIsWhyTheRepairExists() {
    var sanitized = inputSanitizer.sanitizeAllowingFormattingAndLinks("<p>{{legal_links}}</p>");

    assertThat(sanitized).doesNotContain("{{legal_links}}");
    assertThat(sanitized).contains("{<!-- -->{legal_links}}");
  }

  @Test
  void restoreKnownTokens_Should_repairEveryProductToken() {
    var sanitized =
        inputSanitizer.sanitizeAllowingFormattingAndLinks(
            "<p>{{legal_links}} {{Beratungsstelle}} {{Thema}}</p>");

    var restored = LegalTextTokens.restoreKnownTokens(sanitized);

    assertThat(restored).contains("{{legal_links}}", "{{Beratungsstelle}}", "{{Thema}}");
  }

  /**
   * The repair is an allowlist, not a blanket undo. Restoring every mustache would hand a Träger
   * the ability to smuggle an arbitrary client-side template expression through admin-authored
   * legal text — which is exactly what the sanitizer's comment injection exists to stop.
   */
  @Test
  void restoreKnownTokens_Should_leaveUnknownMustachesSplit() {
    var sanitized =
        inputSanitizer.sanitizeAllowingFormattingAndLinks("<p>{{constructor.name}} {{7*7}}</p>");

    var restored = LegalTextTokens.restoreKnownTokens(sanitized);

    assertThat(restored).doesNotContain("{{constructor.name}}");
    assertThat(restored).doesNotContain("{{7*7}}");
  }

  @Test
  void substitute_Should_replaceOnlyTheGivenTokens_andLeaveTheRestStanding() {
    var text = "Ich habe die {{legal_links}} der {{Beratungsstelle}} zum Thema {{Thema}} gelesen.";

    var result =
        LegalTextTokens.substitute(
            text,
            Map.of(
                LegalTextTokens.BERATUNGSSTELLE, "Caritas Freiburg",
                LegalTextTokens.THEMA, "Suchtberatung"));

    assertThat(result).contains("Caritas Freiburg").contains("Suchtberatung");
    // ADR-021 decision 5: the client owns the link targets, so its token survives the server.
    assertThat(result).contains("{{legal_links}}");
  }

  /**
   * The substitution is a literal replacement, not a template evaluation. A value that happens to
   * look like a token or a regex group reference is inserted as text and nothing re-reads it.
   */
  @Test
  void substitute_Should_treatValuesAsLiteralText() {
    var result =
        LegalTextTokens.substitute(
            "Hallo {{Beratungsstelle}}",
            Map.of(LegalTextTokens.BERATUNGSSTELLE, "$1 {{Thema}} ${responsible}"));

    assertThat(result).isEqualTo("Hallo $1 {{Thema}} ${responsible}");
  }

  @Test
  void substitute_Should_tolerateNullsAndEmptyInput() {
    assertThat(LegalTextTokens.substitute(null, Map.of("a", "b"))).isNull();
    assertThat(LegalTextTokens.substitute("x", null)).isEqualTo("x");
    assertThat(LegalTextTokens.substitute("x", Map.of())).isEqualTo("x");
  }
}
