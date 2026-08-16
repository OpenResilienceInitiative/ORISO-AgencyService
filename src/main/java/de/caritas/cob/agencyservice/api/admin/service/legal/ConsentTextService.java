package de.caritas.cob.agencyservice.api.admin.service.legal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.caritas.cob.agencyservice.api.exception.httpresponses.BadRequestException;
import java.util.LinkedHashMap;
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

  private static final TypeReference<LinkedHashMap<String, String>> LANGUAGE_MAP =
      new TypeReference<>() {};

  private final @NonNull LegalContentSanitizer legalContentSanitizer;

  private final ObjectMapper objectMapper = new ObjectMapper();

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

  /**
   * Resolves the consent text for an update where the field may simply not have been sent.
   *
   * <p><b>Absent keeps.</b> The generated request models initialise map properties to an empty map,
   * so a client that never heard of this field is indistinguishable from one clearing it — and an
   * independently deployed Admin updating a label would silently delete the Träger's consent
   * sentence. That is the same silent-wipe trap {@code AgencyAdminService#resolveLegalTextForUpdate}
   * already documents for the policy body, resolved the same way: clearing is expressed by sending
   * the language key with empty content ({@code {"de": ""}}), not by omitting the field.
   *
   * <p>A retained sentence is still validated when the update publishes, otherwise omitting the
   * field would be a way to publish a stored sentence that never passed the token check.
   */
  public String resolveForUpdate(String storedConsentJson, Map<String, String> sent, boolean publish) {
    // Absent is "the map has no entries at all". A map that DOES carry a language key with empty
    // content is an explicit clear, and must not be confused with the two — it is the only way a
    // client can ever delete the sentence.
    if (sent == null || sent.isEmpty()) {
      if (publish) {
        assertStoredTextPublishable(storedConsentJson);
      }
      return storedConsentJson;
    }
    return sanitizeAndValidate(sent, publish);
  }

  /**
   * The token check applied to an already-stored sentence, so the "absent keeps" rule above cannot
   * become a bypass of {@link #assertMandatoryTokenPresent}.
   *
   * <p>An unreadable stored value is left alone rather than rejected: it predates this validator,
   * and refusing to publish a policy because an old consent column will not parse would block the
   * admin on something they cannot fix from the editor.
   */
  public void assertStoredTextPublishable(String storedConsentJson) {
    if (storedConsentJson == null || storedConsentJson.isBlank()) {
      return;
    }
    final Map<String, String> byLanguage;
    try {
      byLanguage = objectMapper.readValue(storedConsentJson, LANGUAGE_MAP);
    } catch (Exception e) {
      return;
    }
    assertMandatoryTokenPresent(byLanguage);
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
