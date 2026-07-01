package de.caritas.cob.agencyservice.api.admin.service.legal;

import de.caritas.cob.agencyservice.api.repository.agencytopic.PublicationStatus;

/**
 * Read view of a department's (Fachbereich = agency × topic) data privacy policy: the stored
 * multilingual JSON language→HTML {@code content} (may be {@code null} if never authored) and its
 * current {@link PublicationStatus}. Used to prefill the admin editor.
 */
public record DepartmentDataProtectionView(String content, PublicationStatus publicationStatus) {}
