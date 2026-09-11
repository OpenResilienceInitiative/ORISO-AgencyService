package de.caritas.cob.agencyservice.api.service.legal;

/**
 * Which rung of the ADR-021 ladder a resolved legal text actually came from.
 *
 * <p>ADR-021 decision 1: "the privacy policy" without a level is not a valid statement. Every
 * resolution therefore reports where its answer came from, so a caller — and a support engineer
 * reading a bug report — can tell a Fachbereich's own document from an inherited one without
 * guessing.
 */
public enum LegalTextSourceLevel {

  /** Level 4, Fachbereich (agency × topic) — the normal case at Gate 2. */
  DEPARTMENT,

  /** Level 3, Beratungsstelle — the agency-wide text every department inherits. */
  AGENCY,

  /** Level 2, Träger — {@code tenant.content_privacy} / {@code content_impressum}. */
  TENANT,

  /** Level 1, platform operator — the "main tenant" under single-domain multitenancy. */
  PLATFORM,

  /** Nothing is authored anywhere on the chain. */
  NONE
}
