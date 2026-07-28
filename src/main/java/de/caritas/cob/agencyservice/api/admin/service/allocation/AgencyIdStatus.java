package de.caritas.cob.agencyservice.api.admin.service.allocation;

/**
 * State of one agency ID in the shared allocation contract (TEN-INV-U2): FREE (assignable),
 * RESERVED (held by an open invite) or ASSIGNED (consumed by a real agency, including
 * soft-deleted ones — IDs are never re-issued).
 */
public enum AgencyIdStatus {
  FREE,
  RESERVED,
  ASSIGNED
}
