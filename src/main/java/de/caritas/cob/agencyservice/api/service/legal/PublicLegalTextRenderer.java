package de.caritas.cob.agencyservice.api.service.legal;

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

  /**
   * Optional: the topics feature is toggleable, and this must not become a hard dependency of the
   * public legal path.
   */
  @Autowired(required = false)
  private TopicService topicService;

  /**
   * Substitutes the server-owned tokens in a resolved text and its consent sentence.
   *
   * <p>The stored form is a JSON language→HTML map string and the substitution is applied to that
   * string as a whole. That is safe because the tokens are literal and only ever occur inside
   * values — a language key is a code like {@code de}, never prose — and it avoids a
   * parse/serialise round trip that would re-order keys and re-escape entities on a hot public
   * path.
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
        LegalTextTokens.substitute(resolved.content(), values),
        LegalTextTokens.substitute(resolved.consentText(), values),
        resolved.sourceLevel(),
        resolved.versionId());
  }

  /** Only tokens the server can actually answer; the rest is the client's half. */
  private Map<String, String> serverOwnedValues(AgencyTopic department) {
    var values = new LinkedHashMap<String, String>();
    var agency = department.getAgency();
    if (agency != null && agency.getName() != null) {
      values.put(LegalTextTokens.BERATUNGSSTELLE, agency.getName());
    }
    var topicName = resolveTopicName(department.getTopicId());
    if (topicName != null) {
      values.put(LegalTextTokens.THEMA, topicName);
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
