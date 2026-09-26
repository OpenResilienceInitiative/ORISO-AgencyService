package de.caritas.cob.agencyservice.api.repository.legaltext;

/** How an adoption treats an existing agency draft; the same modes as TenantService#266. */
public enum AgencyLegalProposalAdoptionMode {
  CREATE_IF_EMPTY,
  ARCHIVE_AND_REPLACE
}
