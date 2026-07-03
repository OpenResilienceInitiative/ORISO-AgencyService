package de.caritas.cob.agencyservice.api.admin.service.legal;

import de.caritas.cob.agencyservice.api.repository.agencytopic.PublicationStatus;

/**
 * Read view of a department's (Fachbereich = agency × topic) imprint (Impressum): the stored
 * multilingual JSON language→HTML {@code content} (may be {@code null} if never authored) and its
 * current {@link PublicationStatus}. Used to prefill the admin editor.
 */
public record DepartmentImprintView(String content, PublicationStatus publicationStatus) {}
