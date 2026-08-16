package de.caritas.cob.agencyservice.api.admin.service.legal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import de.caritas.cob.agencyservice.api.exception.httpresponses.BadRequestException;
import de.caritas.cob.agencyservice.api.validation.InputSanitizer;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * ADR-021 decision 2: a Träger's consent sentence replaces the platform's rather than being
 * appended to it, and what keeps the platform's mandatory disclosures reachable is this validator —
 * not a legal stacking rule.
 */
class ConsentTextServiceTest {

  private final ConsentTextService service =
      new ConsentTextService(new LegalContentSanitizer(new InputSanitizer()));

  @Test
  void publish_Should_beRejected_When_theMandatoryTokenIsMissing() {
    var withoutToken = Map.of("de", "Ich bin einverstanden.");

    var thrown =
        assertThatExceptionOfType(BadRequestException.class)
            .isThrownBy(() -> service.sanitizeAndValidate(withoutToken, true));

    // A 400 the editor can render, never a 500: forgetting a placeholder is an ordinary mistake,
    // and the message has to name the language and the missing token.
    thrown.withMessageContaining("de").withMessageContaining("{{legal_links}}");
  }

  @Test
  void publish_Should_beRejected_When_onlyOneTranslationCarriesTheToken() {
    var mixed = new HashMap<String, String>();
    mixed.put("de", "Ich habe die {{legal_links}} gelesen.");
    mixed.put("en", "I agree.");

    // Checking one language only would let a Träger ship a compliant German sentence next to an
    // English one that leads nowhere.
    assertThatExceptionOfType(BadRequestException.class)
        .isThrownBy(() -> service.sanitizeAndValidate(mixed, true))
        .withMessageContaining("en");
  }

  @Test
  void publish_Should_storeTheSentence_When_theTokenIsPresent() {
    var stored =
        service.sanitizeAndValidate(
            Map.of("de", "Ich habe die {{legal_links}} der {{Beratungsstelle}} gelesen."), true);

    assertThat(stored).contains("{{legal_links}}").contains("{{Beratungsstelle}}");
  }

  /**
   * The regression this pairs with: the OWASP sanitizer splits {@code &#123;&#123;} with an HTML
   * comment. Without {@link LegalTextTokens#restoreKnownTokens} the stored sentence would come back
   * broken even though the admin typed it correctly.
   */
  @Test
  void storedSentence_Should_surviveSanitisationWithItsTokensIntact() {
    var stored =
        service.sanitizeAndValidate(
            Map.of("de", "<p>Ich habe die {{legal_links}} gelesen.</p>"), true);

    assertThat(stored).contains("{{legal_links}}").doesNotContain("<!-- -->");
  }

  @Test
  void publish_Should_stripDangerousMarkup_butKeepFormattingAndLinks() {
    var stored =
        service.sanitizeAndValidate(
            Map.of("de", "<p onclick=\"x()\"><b>Ja</b> {{legal_links}}<script>bad()</script></p>"),
            true);

    assertThat(stored).contains("<b>Ja</b>").contains("{{legal_links}}");
    assertThat(stored).doesNotContain("script").doesNotContain("onclick");
  }

  @Test
  void draftSave_Should_notEnforceTheToken() {
    // A half-written sentence must be storable; the gate is publication, not typing.
    var stored = service.sanitizeAndValidate(Map.of("de", "Ich bin"), false);

    assertThat(stored).contains("Ich bin");
  }

  @Test
  void noConsentText_Should_stayAbsent_ratherThanBecomingAnEmptyDocument() {
    // Authoring none is allowed: the platform's default sentence then applies.
    assertThat(service.sanitizeAndValidate(null, true)).isNull();
    assertThat(service.sanitizeAndValidate(Map.of(), true)).isNull();
    assertThat(service.sanitizeAndValidate(Map.of("de", "   "), true)).isNull();
  }

  @Test
  void metaKeys_Should_beSkippedByTheTokenCheck() {
    var withMeta = new HashMap<String, String>();
    withMeta.put("de", "Ich habe die {{legal_links}} gelesen.");
    withMeta.put("de__meta", "{\"mt\":true,\"src\":\"de\",\"at\":\"2026-08-16T00:00:00\"}");

    // Translation metadata carries JSON, not prose - demanding a placeholder in it would reject
    // every machine-translated consent sentence.
    assertThat(service.sanitizeAndValidate(withMeta, true)).contains("de__meta");
  }
}
