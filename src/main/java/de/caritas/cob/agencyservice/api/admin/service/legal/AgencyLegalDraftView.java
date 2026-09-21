package de.caritas.cob.agencyservice.api.admin.service.legal;

import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import java.time.LocalDateTime;
import java.util.Map;

/** Complete editable state returned to an authorised agency administrator. */
public record AgencyLegalDraftView(
    LegalTextKind kind,
    Map<String, String> content,
    Map<String, String> consentText,
    String revision,
    LocalDateTime savedAt) {}
