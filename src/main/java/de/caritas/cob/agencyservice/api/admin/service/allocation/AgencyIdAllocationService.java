package de.caritas.cob.agencyservice.api.admin.service.allocation;

import static de.caritas.cob.agencyservice.api.exception.httpresponses.HttpStatusExceptionReason.AGENCY_ID_NOT_AVAILABLE;

import de.caritas.cob.agencyservice.api.exception.httpresponses.BadRequestException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.ConflictException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.InternalServerErrorException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.repository.agencyidreservation.AgencyIdReservation;
import de.caritas.cob.agencyservice.api.repository.agencyidreservation.AgencyIdReservationRepository;
import de.caritas.cob.agencyservice.api.service.TenantService;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
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
    if (requestedAgencyId != null && requestedAgencyId < 1) {
      throw new BadRequestException("agencyId must be a positive number");
    }
    validateTenant(tenantId);
    return requestedAgencyId != null
        ? reserveSpecificId(requestedAgencyId, tenantId)
        : reserveSmallestFreeId(tenantId);
  }

  /**
   * Releases an open reservation, making the ID assignable again.
   *
   * <p>Known limitations, deferred to the invite-wiring chunks (U3/U6, parent
   * OpenResilienceInitiative/ORISO-Admin#569): there is no per-tenant ownership check (any
   * agency admin may release any reservation) and no TTL, so an abandoned reservation blocks
   * its ID until released. Both need the invite linkage that does not exist yet in this repo.
   */
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
   * Claims a pre-reserved agency ID for the agency row that is about to be written: consumes the
   * reservation and inserts the skeleton row carrying exactly that ID, both inside the caller's
   * creation transaction. The caller then fills the row through the ordinary JPA update path, so
   * an invite-created agency ends up byte-identical to a sequence-created one.
   *
   * <p>Why a native skeleton insert instead of saving an entity with a preset ID: {@code Agency}
   * declares {@code @GeneratedValue(SEQUENCE)}, so {@code save()} on an entity carrying an ID
   * runs through {@code merge()} and — with no row to merge into — would insert under a freshly
   * generated ID, silently ignoring the reservation. Writing the row first turns the follow-up
   * {@code save()} into a plain UPDATE of the claimed ID.
   *
   * <p>The reservation is the authorisation anchor: no open reservation (already consumed,
   * released, or never reserved) and an ID that is already assigned both answer 409. The primary
   * key of {@code agency} arbitrates the residual race — a concurrent creation that wins the
   * insert rolls this whole transaction back.
   *
   * <p>The claim is tenant-scoped: the reservation is only consumed when it belongs to the same
   * tenant as the agency being created. A mismatching (or absent) tenant leaves the reservation
   * untouched and answers 409 <em>before</em> any agency row is written, so a caller cannot burn
   * another tenant's reservation — the delete itself carries the tenant predicate, so the check
   * and the consumption are one atomic statement rather than a check-then-act.
   *
   * @param agencyId the reserved ID to claim
   * @param tenantId the tenant the agency belongs to; matched against the reservation and written
   *     immediately so the follow-up update is visible through the Hibernate tenant filter
   * @param name the agency name (the only other NOT NULL column without a default)
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void claimReservedId(long agencyId, Long tenantId, String name) {
    if (!consumeReservationOfTenant(agencyId, tenantId)) {
      log.warn(
          "Agency ID {} is not held by an open reservation of tenant {} and cannot be claimed",
          agencyId,
          tenantId);
      throw new ConflictException(AGENCY_ID_NOT_AVAILABLE);
    }
    try {
      jdbcTemplate.update(
          "INSERT INTO agency (id, tenant_id, name, create_date, update_date)"
              + " VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
          agencyId, tenantId, name);
    } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
      log.warn("Agency ID {} was assigned concurrently while claiming its reservation", agencyId);
      throw new ConflictException(AGENCY_ID_NOT_AVAILABLE);
    }
    advanceIdSequenceBeyond(agencyId);
  }

  /**
   * Keeps {@code sequence_agency} from ever handing out an ID that was just assigned manually.
   * The sequence knows nothing about assigned rows — it is declared {@code START WITH 0} in
   * changeset 0001 — so without this an ordinary (sequence-generated) creation could later
   * collide with a claimed ID and fail on the primary key.
   *
   * <p>Best effort by design: MariaDB/MySQL {@code SETVAL} only ever raises a sequence (a lower
   * value is a no-op returning NULL), which is exactly the semantics wanted here. Other engines
   * — the H2 testing profile — have no equivalent no-op-safe statement, and their sequences start
   * far above any reserved ID, so the advance is skipped there instead of guessed. A failure is
   * logged and never fails the creation: the collision it prevents is a later, retryable insert
   * error, not a corrupted agency.
   */
  private void advanceIdSequenceBeyond(long agencyId) {
    try {
      var product = jdbcTemplate.execute(
          (ConnectionCallback<String>) connection ->
              connection.getMetaData().getDatabaseProductName());
      var engine = product == null ? "" : product.toLowerCase(Locale.ROOT);
      if (!engine.contains("maria") && !engine.contains("mysql")) {
        log.debug("Skipping the agency ID sequence advance on database engine '{}'", product);
        return;
      }
      jdbcTemplate.queryForObject("SELECT SETVAL(sequence_agency, ?, 1)", Long.class, agencyId);
    } catch (RuntimeException e) {
      // Never swallowed: committing the agency row with a stale sequence would hand the same ID
      // out again later and break an unrelated creation at the primary key. Failing here rolls
      // the whole claim back — reservation included — so the invite stays retryable.
      log.error(
          "Could not advance sequence_agency beyond the claimed agency ID {} — rolling the"
              + " creation back rather than leaving the sequence able to re-issue it",
          agencyId,
          e);
      throw e;
    }
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

  /**
   * Tenant-scoped consumption: the tenant predicate rides along in the DELETE, so a reservation
   * belonging to another tenant is left intact and simply reports "nothing consumed". A null
   * tenant never matches — an unscoped claim must not be able to take a scoped reservation.
   */
  private boolean consumeReservationOfTenant(long agencyId, Long tenantId) {
    if (tenantId == null) {
      return false;
    }
    return jdbcTemplate.update(
        "DELETE FROM agency_id_reservation WHERE agency_id = ? AND tenant_id = ?",
        agencyId, tenantId) > 0;
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
      // TenantService (U1), never here. An inactive-tenant check is currently unimplementable:
      // the consumed RestrictedTenantDTO (services/tenantservice.yaml) exposes no active/status
      // field. Once the TenantService contract exposes it, this seam is the single place to
      // tighten the check.
      var tenant = tenantService.getRestrictedTenantDataByTenantId(tenantId);
      if (tenant == null || tenant.getId() == null) {
        throw new BadRequestException("Tenant " + tenantId + " does not exist");
      }
    } catch (HttpClientErrorException.NotFound e) {
      // only a definite 404 from TenantService means nonexistence
      throw new BadRequestException("Tenant " + tenantId + " does not exist");
    } catch (RestClientException e) {
      // any other client failure (outage, 5xx, timeout) is an infrastructure problem — never
      // report it as tenant nonexistence (fail closed, but with the honest status class)
      throw new InternalServerErrorException(
          "Tenant " + tenantId + " could not be validated against TenantService", e);
    }
  }
}
