package de.caritas.cob.agencyservice.api.repository.legaltext;

/**
 * Which level of the ADR-021 ladder a stored legal text belongs to. AgencyService owns the lower
 * three of the four levels; the Träger (tenant) and platform-operator levels live in
 * ORISO-TenantService and are resolved, not stored, here.
 *
 * <p>Every history row names its level explicitly: ADR-021 decision 1 forbids referring to "the
 * privacy policy" without saying which level it is.
 */
public enum LegalTextLevel {

  /** Level 4, Fachbereich: {@code agency_topic.id}. The normal case. */
  DEPARTMENT,

  /** Level 3, Beratungsstelle: {@code agency.id}. */
  AGENCY,

  /** The ADR-014 shared legal-text object: {@code legal_text.id}. */
  SHARED
}
