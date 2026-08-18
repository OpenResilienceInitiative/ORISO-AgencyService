package de.caritas.cob.agencyservice.api.admin.service.legal;

import de.caritas.cob.agencyservice.api.repository.agencytopic.PublicationStatus;

/**
 * Read view of a department's (Fachbereich = agency × topic) data privacy policy: the stored
 * multilingual JSON language→HTML {@code content} (may be {@code null} if never authored), the
 * consent sentence stored with it (ADR-021 decision 4 — a field of the policy, not a document of
 * its own; {@code null} means the platform default applies) and their shared {@link
 * PublicationStatus}. Used to prefill the admin editor.
 */
public record DepartmentDataProtectionView(
    String content, String consentText, PublicationStatus publicationStatus) {}
