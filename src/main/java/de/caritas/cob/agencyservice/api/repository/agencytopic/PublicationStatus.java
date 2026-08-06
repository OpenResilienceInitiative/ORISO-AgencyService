package de.caritas.cob.agencyservice.api.repository.agencytopic;

/**
 * Publication state of a department's (Fachbereich = agency × topic) legal text. Mirrors the {@code
 * agency_topic.publication_status} column (default {@code DRAFT}) and aligns with ADR-003. A
 * {@code DRAFT} department-level data privacy policy may exist while counselling starts; only a
 * {@code PUBLISHED} one is presented to clients as a finalised legal document.
 */
public enum PublicationStatus {
  DRAFT,
  PUBLISHED
}
