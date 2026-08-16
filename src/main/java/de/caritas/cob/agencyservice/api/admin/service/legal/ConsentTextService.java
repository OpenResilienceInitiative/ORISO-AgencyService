package de.caritas.cob.agencyservice.api.admin.service.legal;

import de.caritas.cob.agencyservice.api.exception.httpresponses.BadRequestException;
import java.util.Map;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Sanitises and validates the consent sentence that belongs to a data protection policy (ADR-021
 * decision 4: it is a field of the DPP, not a legal-text kind of its own).
 *
 * <h2>The mandatory token</h2>
 *
 * <p>ADR-021 decision 2 lets a Träger's consent text <em>replace</em> the platform's rather than be
 * appended to it — two documents governing the same processing eventually contradict each other,
 * and two checkboxes is a worse experience. What protects the platform's mandatory disclosures is
 * therefore not a legal stacking rule but this technical one: <b>a consent text cannot be published
 * unless every authored translation contains {@code {{legal_links}}}</b>. Without it the
 * help-seeker would tick a sentence with no route to the documents it refers to.
 *
 * <p>The rejection is a 400 with a message the editor can show, never a 500. An admin who typed a
 * sentence and forgot the token has made an ordinary mistake, and the response has to say which
 * language is missing what.
 *
 * <p><b>Authoring nothing is allowed.</b> A Träger that publishes a DPP without a consent sentence
 * keeps the platform's default sentence; the validator only governs text that exists.
 *
 * <p>The cookie/authentication notice is not handled here at all — it is a fixed, non-editable
 * addendum rendered by the client and is deliberately never persisted per Träger.
 */
@Component
@RequiredArgsConstructor
public class ConsentTextService {

  private final @NonNull LegalContentSanitizer legalContentSanitizer;

  /**
   * Sanitises the multilingual consent text and, when {@code publish} is true, enforces the
   * mandatory token. Returns the stored JSON language→text map, or {@code null} when nothing was
   * authored (so an absent consent text stays absent instead of becoming an empty document).
   */
  public String sanitizeAndValidate(Map<String, String> consentText, boolean publish) {
    if (isEmpty(consentText)) {
      return null;
    }
    if (publish) {
      assertMandatoryTokenPresent(consentText);
    }
    return legalContentSanitizer.sanitizeToJson(consentText);
  }

  /**
   * Every authored translation must carry the token. Checking only one language would let a Träger
   * publish a compliant German sentence and an English one that leads nowhere.
   *
   * <p>{@code __meta} keys carry translation metadata, not prose, and are skipped.
   */
  private void assertMandatoryTokenPresent(Map<String, String> consentText) {
    consentText.forEach(
        (language, text) -> {
          if (language == null || language.endsWith("__meta") || isBlank(text)) {
            return;
          }
          if (!text.contains(LegalTextTokens.LEGAL_LINKS_TOKEN)) {
            throw new BadRequestException(
                String.format(
                    "The consent text for language '%s' must contain the %s placeholder before it"
                        + " can be published: without it the help-seeker has no way to reach the"
                        + " documents they are agreeing to.",
                    language, LegalTextTokens.LEGAL_LINKS_TOKEN));
          }
        });
  }

  private boolean isEmpty(Map<String, String> consentText) {
    return consentText == null
        || consentText.isEmpty()
        || consentText.values().stream().allMatch(this::isBlank);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
