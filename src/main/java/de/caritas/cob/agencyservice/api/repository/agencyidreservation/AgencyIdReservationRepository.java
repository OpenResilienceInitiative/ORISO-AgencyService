package de.caritas.cob.agencyservice.api.repository.agencyidreservation;

import de.caritas.cob.agencyservice.api.repository.TenantUnaware;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for {@link AgencyIdReservation} plus the taken-ID queries of the shared allocation
 * contract (TEN-INV-U2).
 *
 * <p>An agency ID counts as taken when it is ASSIGNED (a row in {@code agency}, including
 * soft-deleted ones — IDs are never re-issued) or RESERVED (a row in
 * {@code agency_id_reservation}). The queries are plain native SQL over both tables so they work
 * identically on MariaDB and the H2 testing profile, and they deliberately bypass the Hibernate
 * tenant filter: the agency ID space is global. The repository is additionally marked
 * {@link TenantUnaware} so the TenantAspect never enables the tenant filter as a side effect of
 * allocation calls.
 */
@TenantUnaware
public interface AgencyIdReservationRepository extends JpaRepository<AgencyIdReservation, Long> {

  String TAKEN_SUCCESSORS =
      "SELECT a.id + 1 AS candidate FROM agency a "
          + "UNION SELECT r.agency_id + 1 FROM agency_id_reservation r";

  String TAKEN_PREDECESSORS =
      "SELECT a.id - 1 AS candidate FROM agency a "
          + "UNION SELECT r.agency_id - 1 FROM agency_id_reservation r";

  String IS_FREE =
      "c.candidate NOT IN (SELECT id FROM agency) "
          + "AND c.candidate NOT IN (SELECT agency_id FROM agency_id_reservation)";

  /**
   * Returns the smallest free agency ID (at least 1). The candidate set is every taken ID plus
   * one, plus 1 itself, so the minimum free candidate is always the smallest gap start.
   */
  @Query(
      value = "SELECT MIN(c.candidate) FROM (" + TAKEN_SUCCESSORS + " UNION SELECT 1) c "
          + "WHERE c.candidate >= 1 AND " + IS_FREE,
      nativeQuery = true)
  Long findSmallestFreeId();

  /** Returns the next free agency ID strictly above {@code fromId}, or {@code null}. */
  @Query(
      value = "SELECT MIN(c.candidate) FROM (" + TAKEN_SUCCESSORS
          + " UNION SELECT :fromId + 1) c "
          + "WHERE c.candidate > :fromId AND " + IS_FREE,
      nativeQuery = true)
  Long findNextFreeIdAbove(@Param("fromId") long fromId);

  /**
   * Returns the next free agency ID strictly below {@code fromId} (at least 1), or {@code null}
   * when everything below is taken.
   */
  @Query(
      value = "SELECT MAX(c.candidate) FROM (" + TAKEN_PREDECESSORS
          + " UNION SELECT :fromId - 1) c "
          + "WHERE c.candidate < :fromId AND c.candidate >= 1 AND " + IS_FREE,
      nativeQuery = true)
  Long findNextFreeIdBelow(@Param("fromId") long fromId);

  /**
   * Native check whether an agency row (any tenant, including soft-deleted agencies) already
   * occupies the given ID. Deliberately native SQL so the Hibernate tenant filter of a
   * tenant-scoped agency admin can never hide another tenant's agency from an allocation
   * decision — the assignment check must be authoritative for the global agency ID space.
   */
  @Query(value = "SELECT COUNT(*) FROM agency WHERE id = :agencyId", nativeQuery = true)
  long countAssignedAgencyRows(@Param("agencyId") long agencyId);
}
