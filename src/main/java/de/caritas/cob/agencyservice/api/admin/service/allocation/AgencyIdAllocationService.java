package de.caritas.cob.agencyservice.api.admin.service.allocation;

import static de.caritas.cob.agencyservice.api.exception.httpresponses.HttpStatusExceptionReason.AGENCY_ID_NOT_AVAILABLE;

import de.caritas.cob.agencyservice.api.exception.httpresponses.BadRequestException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.ConflictException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.repository.agencyidreservation.AgencyIdReservation;
import de.caritas.cob.agencyservice.api.repository.agencyidreservation.AgencyIdReservationRepository;
import de.caritas.cob.agencyservice.api.service.TenantService;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

/**
 * Shared allocation contract for agency IDs (TEN-INV-U2, parent
 * OpenResilienceInitiative/ORISO-Admin#569).
 *
 * <p>Semantics, aligned with the tenant ID allocation in TenantService (TEN-INV-U1):
 *
 * <ul>
 *   <li>An ID is FREE, RESERVED (held by an open invite) or ASSIGNED (a real agency row,
 *       including soft-deleted agencies — IDs are never re-issued).
 *   <li>The agency ID space is global: every assignment check runs as native SQL (see
 *       {@link AgencyIdReservationRepository#countAssignedAgencyRows(long)}), so the Hibernate
 *       tenant filter of a tenant-scoped agency admin can never hide another tenant's agency and
 *       report a taken ID as FREE.
 *   <li>AUTO reservation assigns the smallest currently free ID.
 *   <li>Concurrency safety comes from the database, not from application-level checks: the
 *       reserved ID is the primary key of {@code agency_id_reservation}, every reservation
 *       attempt runs in its own transaction and inserts <em>before</em> it checks for an
 *       assigned agency (write-then-read, so an agency row committed mid-flight is always
 *       seen), and agency creation runs {@link #guardAssignmentAgainstOpenReservations(long)}
 *       inside its own insert transaction so the primary key arbitrates
 *       creation-vs-reservation races in both directions.
 * </ul>
 *
 * <p>This service reserves <strong>agency IDs only — never tenant IDs</strong>. A tenant ID
 * passed alongside a reservation is validated (the tenant must exist), nothing more; tenant ID
 * reservation lives in TenantService (U1).
 */
@Service
@Slf4j
public class AgencyIdAllocationService {

  private static final int MAX_AUTO_ATTEMPTS = 5;

  private final AgencyIdReservationRepository reservationRepository;
  private final TenantService tenantService;
  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate reservationAttemptTransaction;

  public AgencyIdAllocationService(AgencyIdReservationRepository reservationRepository,
      TenantService tenantService, JdbcTemplate jdbcTemplate,
      PlatformTransactionManager transactionManager) {
    this.reservationRepository = reservationRepository;
    this.tenantService = tenantService;
    this.jdbcTemplate = jdbcTemplate;
    // each reservation attempt commits (or fails) on its own, so a unique-key collision of one
    // attempt can never poison an enclosing transaction and AUTO mode can simply retry
    this.reservationAttemptTransaction = new TransactionTemplate(transactionManager);
    this.reservationAttemptTransaction
        .setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /** Returns the authoritative state of the given agency ID. */
  public AgencyIdStatus checkAvailability(long agencyId) {
    if (isAssigned(agencyId)) {
      return AgencyIdStatus.ASSIGNED;
    }
    if (reservationRepository.existsById(agencyId)) {
      return AgencyIdStatus.RESERVED;
    }
    return AgencyIdStatus.FREE;
  }

  /**
   * Returns the next free agency ID from {@code fromId} in the given direction, skipping
   * ASSIGNED and RESERVED IDs, or an empty optional when no free ID exists in that direction.
   */
  public Optional<Long> nextFreeId(long fromId, AgencyIdStepDirection direction) {
    var nextFreeId = direction == AgencyIdStepDirection.UP
        ? reservationRepository.findNextFreeIdAbove(fromId)
        : reservationRepository.findNextFreeIdBelow(fromId);
    return Optional.ofNullable(nextFreeId);
  }

  /**
   * Reserves an agency ID. With a {@code requestedAgencyId} the exact ID is reserved or the call
   * fails with a conflict (409) — the server-side check is authoritative, a stale UI state grants
   * nothing. Without one (AUTO mode) the smallest currently free ID is reserved.
   *
   * @param requestedAgencyId the manually picked ID, or {@code null} for AUTO
   * @param tenantId optional tenant scope; validated against TenantService, never reserved here
   * @return the reserved agency ID
   */
  public Long reserve(Long requestedAgencyId, Long tenantId) {
    validateTenant(tenantId);
    return requestedAgencyId != null
        ? reserveSpecificId(requestedAgencyId, tenantId)
        : reserveSmallestFreeId(tenantId);
  }

  /** Releases an open reservation, making the ID assignable again. */
  @Transactional
  public void release(long agencyId) {
    var reservation = reservationRepository.findById(agencyId)
        .orElseThrow(NotFoundException::new);
    reservationRepository.delete(reservation);
  }

  /**
   * Consumes a reservation because the real agency is being created with that ID. Participates
   * in the caller's transaction so entity creation and reservation consumption are atomic. The
   * delete runs as plain JDBC so it is effective immediately — a subsequent
   * {@link #guardAssignmentAgainstOpenReservations(long)} in the same transaction must not
   * collide with a delete that is still pending in the persistence context.
   *
   * @return whether an open reservation existed and was consumed
   */
  @Transactional
  public boolean consumeReservation(long agencyId) {
    return jdbcTemplate.update(
        "DELETE FROM agency_id_reservation WHERE agency_id = ?", agencyId) > 0;
  }

  /**
   * Database-level guard for agency creation: must run inside the transaction that inserts the
   * agency row, directly after the ID has been generated. It inserts a reservation row for the
   * fresh ID — letting the primary key of {@code agency_id_reservation} arbitrate against every
   * concurrent reservation attempt — and removes it again right away; the row lock is held until
   * the surrounding creation transaction commits. If an open reservation already holds the ID,
   * the insert collides and the whole creation transaction rolls back with a conflict, so an ID
   * can never end up ASSIGNED and RESERVED at the same time.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void guardAssignmentAgainstOpenReservations(long agencyId) {
    try {
      // plain JDBC on the surrounding transaction's connection: arbitration must not force an
      // entity flush of the caller's persistence context, and native SQL keeps the guard
      // independent of the Hibernate tenant filter
      jdbcTemplate.update(
          "INSERT INTO agency_id_reservation (agency_id, create_date)"
              + " VALUES (?, CURRENT_TIMESTAMP)",
          agencyId);
    } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
      log.warn("Agency creation generated ID {} which is reserved by an open invite", agencyId);
      throw new ConflictException(AGENCY_ID_NOT_AVAILABLE);
    }
    jdbcTemplate.update("DELETE FROM agency_id_reservation WHERE agency_id = ?", agencyId);
  }

  private Long reserveSpecificId(long agencyId, Long tenantId) {
    try {
      return reservationAttemptTransaction.execute(status -> {
        // write first, check afterwards: the primary-key insert is the arbitration point, so an
        // agency row committed while this attempt is in flight is always visible to the check
        // below (write-then-read instead of check-then-act)
        reservationRepository.saveAndFlush(
            AgencyIdReservation.newReservation(agencyId, tenantId));
        if (isAssigned(agencyId)) {
          throw new ConflictException(AGENCY_ID_NOT_AVAILABLE);
        }
        return agencyId;
      });
    } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
      log.info("Agency ID {} was reserved or assigned concurrently", agencyId);
      throw new ConflictException(AGENCY_ID_NOT_AVAILABLE);
    }
  }

  private Long reserveSmallestFreeId(Long tenantId) {
    for (var attempt = 0; attempt < MAX_AUTO_ATTEMPTS; attempt++) {
      var candidate = reservationRepository.findSmallestFreeId();
      try {
        var reservedId = reservationAttemptTransaction.execute(status -> {
          reservationRepository.saveAndFlush(
              AgencyIdReservation.newReservation(candidate, tenantId));
          if (isAssigned(candidate)) {
            // lost a race against a concurrent agency creation: roll the insert back and
            // recompute the smallest free ID
            status.setRollbackOnly();
            return null;
          }
          return candidate;
        });
        if (reservedId != null) {
          return reservedId;
        }
      } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
        log.info("Lost AUTO reservation race for agency ID {}, retrying", candidate);
      }
    }
    log.warn("Could not auto-reserve an agency ID within {} attempts", MAX_AUTO_ATTEMPTS);
    throw new ConflictException(AGENCY_ID_NOT_AVAILABLE);
  }

  /**
   * Tenant-filter-proof assignment check: native SQL over the global agency ID space, so it sees
   * every tenant's agencies regardless of the caller's tenant scope.
   */
  private boolean isAssigned(long agencyId) {
    return reservationRepository.countAssignedAgencyRows(agencyId) > 0;
  }

  private void validateTenant(Long tenantId) {
    if (tenantId == null) {
      return;
    }
    try {
      // Existence check only: agency invites carry a tenant, but tenant IDs are reserved in
      // TenantService (U1), never here. TenantService's restricted endpoint does not expose an
      // active flag yet; once U1 lands, this seam is the single place to tighten the check.
      var tenant = tenantService.getRestrictedTenantDataByTenantId(tenantId);
      if (tenant == null || tenant.getId() == null) {
        throw new BadRequestException("Tenant " + tenantId + " does not exist");
      }
    } catch (RestClientException e) {
      throw new BadRequestException(
          "Tenant " + tenantId + " does not exist or cannot be validated");
    }
  }
}
