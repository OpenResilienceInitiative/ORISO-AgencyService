package de.caritas.cob.agencyservice.api.repository.casesharingchange;

import org.springframework.data.repository.Repository;

/**
 * Append-only access to {@link AgencyCaseSharingChange}: insert, nothing else. Reads for the Admin
 * log go through {@code AgencyCaseSharingChangeQueryService}, which applies the caller's scope.
 */
public interface AgencyCaseSharingChangeRepository
    extends Repository<AgencyCaseSharingChange, Long> {

  AgencyCaseSharingChange save(AgencyCaseSharingChange change);
}
