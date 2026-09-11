package de.caritas.cob.agencyservice.api.admin.service.legal;

import de.caritas.cob.agencyservice.api.repository.agencytopic.PublicationStatus;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;

/**
 * Admin view of a shared legal text (ADR-014): the stored object plus how many departments
 * currently reference it ("used by N departments").
 */
public record LegalTextAdminView(
    Long id,
    LegalTextKind kind,
    String label,
    String content,
    String consentText,
    PublicationStatus publicationStatus,
    long usageCount) {}
