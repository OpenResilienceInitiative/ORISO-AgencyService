package de.caritas.cob.agencyservice.api.admin.service.agency;

/**
 * Optional narrowing of the agency search. It only ever narrows: the caller's role scope still
 * applies, so a tenant outside that scope yields an empty result.
 *
 * @param excludeDeleted leave soft-deleted agencies out
 * @param tenantId only agencies of this tenant (Träger); null = every tenant in scope
 */
public record AgencySearchFilter(boolean excludeDeleted, Long tenantId) {

  public static AgencySearchFilter none() {
    return new AgencySearchFilter(false, null);
  }
}
