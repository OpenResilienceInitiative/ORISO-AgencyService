package de.caritas.cob.agencyservice.api.admin.service.legal;

import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Complete editable state returned to an authorised agency administrator. {@code originProposalId}
 * names the forwarded Träger template the draft was adopted from, if any.
 */
public record AgencyLegalDraftView(
    LegalTextKind kind,
    Map<String, String> content,
    Map<String, String> consentText,
    String revision,
    LocalDateTime savedAt,
    Long originProposalId) {

  public AgencyLegalDraftView(
      LegalTextKind kind,
      Map<String, String> content,
      Map<String, String> consentText,
      String revision,
      LocalDateTime savedAt) {
    this(kind, content, consentText, revision, savedAt, null);
  }
}
