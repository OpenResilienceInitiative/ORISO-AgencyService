package de.caritas.cob.agencyservice.api.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Anchor-navigation support on the legal-content write path: the OWASP policy must keep {@code id}
 * (slug charset only) and {@code data-anchor-removed="true"} on headings, exactly like
 * ORISO-TenantService's {@code InputSanitizer} does since its commit {@code 7f37f30}. Without this
 * rule every agency/department legal text loses its chapter anchors on save, and anchors the author
 * explicitly removed resurrect after a reload.
 */
class InputSanitizerTest {

  private final InputSanitizer inputSanitizer = new InputSanitizer();

  // --- Anchor navigation support (Admin anchor feature): id + data-anchor-removed on headings ---

  @Test
  void sanitizeAllowingFormattingAndLinks_should_keepIdOnHeadings() {
    var input = "<h2 id=\"intro\">Intro</h2><h3 id=\"data-usage_2\">Data usage</h3>";

    var result = inputSanitizer.sanitizeAllowingFormattingAndLinks(input);

    assertThat(result).contains("<h2 id=\"intro\">");
    assertThat(result).contains("<h3 id=\"data-usage_2\">");
  }

  @Test
  void sanitizeAllowingFormattingAndLinks_should_keepDataAnchorRemovedTrueOnHeadings() {
    var input = "<h2 data-anchor-removed=\"true\">Old chapter</h2>";

    var result = inputSanitizer.sanitizeAllowingFormattingAndLinks(input);

    assertThat(result).contains("data-anchor-removed=\"true\"");
  }

  @Test
  void sanitizeAllowingFormattingAndLinks_should_dropIdWithUnsafeCharacters() {
    var javascriptLike = "<h2 id=\"javascript:alert(1)\">x</h2>";
    var withQuotes = "<h2 id=\"intro&quot;onmouseover=alert(1)\">x</h2>";
    var withSpaces = "<h2 id=\"intro chapter\">x</h2>";

    assertThat(inputSanitizer.sanitizeAllowingFormattingAndLinks(javascriptLike))
        .doesNotContain("id=");
    assertThat(inputSanitizer.sanitizeAllowingFormattingAndLinks(withQuotes)).doesNotContain("id=");
    assertThat(inputSanitizer.sanitizeAllowingFormattingAndLinks(withSpaces)).doesNotContain("id=");
  }

  @Test
  void sanitizeAllowingFormattingAndLinks_should_dropDataAnchorRemovedWithOtherValues() {
    var input = "<h2 data-anchor-removed=\"false\">x</h2>";

    var result = inputSanitizer.sanitizeAllowingFormattingAndLinks(input);

    assertThat(result).doesNotContain("data-anchor-removed");
  }

  @Test
  void sanitizeAllowingFormattingAndLinks_should_keepIdDisallowedOnNonHeadingElements() {
    var input = "<p id=\"not-a-heading\">text</p><a id=\"link\" href=\"https://example.org\">a</a>";

    var result = inputSanitizer.sanitizeAllowingFormattingAndLinks(input);

    assertThat(result).doesNotContain("id=");
    assertThat(result).contains("href=\"https://example.org\"");
  }

  @Test
  void sanitizeAllowingFormattingAndLinks_should_stillStripScriptAndEventHandlers() {
    var input = "<h2 id=\"intro\" onclick=\"alert(1)\">Intro</h2><script>alert(1)</script>";

    var result = inputSanitizer.sanitizeAllowingFormattingAndLinks(input);

    assertThat(result).contains("<h2 id=\"intro\">Intro</h2>");
    assertThat(result).doesNotContain("onclick");
    assertThat(result).doesNotContain("<script>");
  }
}
