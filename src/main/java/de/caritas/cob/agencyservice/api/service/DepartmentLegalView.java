package de.caritas.cob.agencyservice.api.service;

import de.caritas.cob.agencyservice.api.service.legal.ResolvedLegalText;

/**
 * Public read view of a department's (Fachbereich = agency × topic) legal texts, each resolved
 * across the full ADR-021 ladder. Both carry the level they came from and — where AgencyService
 * owns the history for that level — the version id the wording corresponds to.
 */
public record DepartmentLegalView(ResolvedLegalText dpp, ResolvedLegalText imprint) {

  /** Convenience for callers that only want the wording. */
  public String dppContent() {
    return dpp == null ? null : dpp.content();
  }

  public String imprintContent() {
    return imprint == null ? null : imprint.content();
  }
}
