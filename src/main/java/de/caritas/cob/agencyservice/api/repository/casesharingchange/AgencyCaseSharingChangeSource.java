package de.caritas.cob.agencyservice.api.repository.casesharingchange;

/** What changed a centre's effective case sharing model (ORISO-Admin#2494). */
public enum AgencyCaseSharingChangeSource {
  /** The centre's own {@code settings.caseSharingMode} was changed in the agency settings. */
  AGENCY_SETTINGS,
  /** The centre's {@code teamAgency} flag was changed via the agency type. */
  AGENCY_TYPE
}
