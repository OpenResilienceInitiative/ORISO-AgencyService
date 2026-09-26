package de.caritas.cob.agencyservice.api.repository.legaltext;

/** Lifecycle of one forwarded template at one Beratungsstelle. */
public enum AgencyLegalProposalStatus {
  PENDING,
  DISMISSED,
  ADOPTED,
  SUPERSEDED
}
