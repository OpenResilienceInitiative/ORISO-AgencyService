package de.caritas.cob.agencyservice.api.model;

/** Contact fields maintained by one agency, for an explicitly requested contact sheet. */
public record AgencyContactDetailsDTO(
    Long agencyId,
    Long tenantId,
    String name,
    String phone,
    String email,
    String openingHours) {}
