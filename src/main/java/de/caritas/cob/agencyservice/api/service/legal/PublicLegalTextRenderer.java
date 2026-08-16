package de.caritas.cob.agencyservice.api.service.legal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.caritas.cob.agencyservice.api.admin.service.legal.LegalTextTokens;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.service.TopicService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * ADR-021 decision 5: placeholder substitution is split by who owns the data. The server fills in
 * what it knows — the Beratungsstelle and the Fachbereich — and deliberately leaves {@code
 * {{legal_links}}} standing, because the link targets come from the frontend deployment
 * configuration ({@code LegalLinksProvider} / {@code settings.legalLinks}) and the backend does not
 * know them.
 *
 * <p>This closes the gap CONTEXT-legal-documents records against
 * {@code /agencies/{id}/topics/{tid}/legal}: it returned raw, unrendered text, so a placeholder
 * could reach a help-seeker verbatim. (The same defect class as
 * {@code DepartmentLegalSection.tsx:104} showing an unsubstituted {@code ${responsible}}.)
 *
 * <h2>Never Freemarker</h2>
 *
 * <p>Substitution here is {@link LegalTextTokens#substitute}, a literal string replacement.
 * Träger-authored text must never pass through {@code TemplateRenderer}: Freemarker's {@code
 * ${...}} can invoke methods on the objects in the data model, which would make tenant-authored
 * content a template-injection surface (ADR-021 decision 6). A {@code {{key}}} replacement cannot
 * do that by construction.
 *
 * <h2>Unknown tokens are left standing, never blanked</h2>
 *
 * <p>The Fachbereich name lives in ORISO-TopicService, and that client sends the caller's bearer
 * token — which the public, unauthenticated legal endpoint does not have. When the name cannot be
 * resolved the token is left intact rather than replaced with an empty string: the client knows
 * which topic it navigated to and can finish the substitution, exactly as it already does for
 * {@code {{legal_links}}}. Emitting "zum Thema  " would be a silent corruption of a legal sentence;
 * leaving a token is a visible, recoverable state.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PublicLegalTextRenderer {

  private static final String META_KEY_SUFFIX = "__meta";
  private static final TypeReference<LinkedHashMap<String, String>> LANGUAGE_MAP =
      new TypeReference<>() {};

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Optional: the topics feature is toggleable, and this must not become a hard dependency of the
   * public legal path.
   */
  @Autowired(required = false)
  private TopicService topicService;

  /**
   * Substitutes the server-owned tokens in a resolved text and its consent sentence.
   *
   * <p>The stored form is a JSON language→HTML map string, and substitution runs <b>inside the
   * parsed values</b>, never over the serialised string. Replacing text in the JSON representation
   * looks tempting and is wrong twice over: a perfectly ordinary name like {@code Caritas "Mitte"}
   * would break out of its JSON string and hand clients an unparseable document, and a name
   * containing markup would land in already-sanitised HTML without ever meeting the sanitiser.
   * Values are therefore {@linkplain LegalTextTokens#escapeForHtml HTML-escaped} and the map is
   * re-serialised properly.
   *
   * <p>{@code __meta} keys carry translation metadata as JSON, not prose, and are passed through
   * untouched — substituting into them would corrupt the payload the same way sanitising them
   * would.
   */
  public ResolvedLegalText render(ResolvedLegalText resolved, AgencyTopic department) {
    if (resolved == null || department == null) {
      return resolved;
    }
    var values = serverOwnedValues(department);
    if (values.isEmpty()) {
      return resolved;
    }
    return new ResolvedLegalText(
        substituteInLanguageMap(resolved.content(), values),
        substituteInLanguageMap(resolved.consentText(), values),
        resolved.sourceLevel(),
        resolved.versionId());
  }

  /**
   * Parses the language→text map, substitutes inside each value, and serialises it again.
   *
   * <p>A stored map that does not parse is returned unchanged rather than partially rewritten: a
   * legal document that is already damaged must not additionally be handed to a help-seeker with
   * half-substituted placeholders, and this is a public read path that must not fail.
   */
  private String substituteInLanguageMap(String storedJson, Map<String, String> values) {
    if (storedJson == null || storedJson.isBlank()) {
      return storedJson;
    }
    final Map<String, String> byLanguage;
    try {
      byLanguage = objectMapper.readValue(storedJson, LANGUAGE_MAP);
    } catch (Exception e) {
      log.warn("Stored legal content is not a readable language map; serving it unsubstituted");
      return storedJson;
    }
    var substituted = new LinkedHashMap<String, String>();
    byLanguage.forEach(
        (language, text) ->
            substituted.put(
                language,
                language != null && language.endsWith(META_KEY_SUFFIX)
                    ? text
                    : LegalTextTokens.substitute(text, values)));
    try {
      return objectMapper.writeValueAsString(substituted);
    } catch (Exception e) {
      log.warn("Could not re-serialise the substituted legal content; serving it unsubstituted");
      return storedJson;
    }
  }

  /**
   * Only tokens the server can actually answer; the rest is the client's half. Values are escaped
   * here, at the one place they enter sanitised HTML.
   */
  private Map<String, String> serverOwnedValues(AgencyTopic department) {
    var values = new LinkedHashMap<String, String>();
    var agency = department.getAgency();
    if (agency != null && agency.getName() != null) {
      values.put(LegalTextTokens.BERATUNGSSTELLE, LegalTextTokens.escapeForHtml(agency.getName()));
    }
    var topicName = resolveTopicName(department.getTopicId());
    if (topicName != null) {
      values.put(LegalTextTokens.THEMA, LegalTextTokens.escapeForHtml(topicName));
    }
    return values;
  }

  private String resolveTopicName(Long topicId) {
    if (topicService == null || topicId == null) {
      return null;
    }
    try {
      var topics = topicService.getAllTopics();
      if (topics == null) {
        return null;
      }
      return topics.stream()
          .filter(topic -> topicId.equals(topic.getId()))
          .map(topic -> topic.getName())
          .filter(name -> name != null && !name.isBlank())
          .findFirst()
          .orElse(null);
    } catch (Exception e) {
      log.debug(
          "Could not resolve the Fachbereich name for topic {}; leaving its placeholder for the"
              + " client to substitute: {}",
          topicId,
          e.getMessage());
      return null;
    }
  }
}
