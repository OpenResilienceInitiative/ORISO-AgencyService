package de.caritas.cob.agencyservice.api.admin.service.allocation;

import static de.caritas.cob.agencyservice.api.exception.httpresponses.HttpStatusExceptionReason.AGENCY_ID_NOT_AVAILABLE;

import de.caritas.cob.agencyservice.api.exception.httpresponses.BadRequestException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.ConflictException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import de.caritas.cob.agencyservice.api.repository.agencyidreservation.AgencyIdReservation;
import de.caritas.cob.agencyservice.api.repository.agencyidreservation.AgencyIdReservationRepository;
import de.caritas.cob.agencyservice.api.service.TenantService;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
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
 *   <li>AUTO reservation assigns the smallest currently free ID.
 *   <li>Concurrency safety comes from the database, not from application-level checks: the
 *       reserved ID is the primary key of {@code agency_id_reservation}, every reservation
 *       attempt runs in its own transaction, and a lost race surfaces as a constraint violation
 *       which is mapped to a conflict (manual mode) or retried with the next candidate (AUTO).
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
  private final AgencyRepository agencyRepository;
  private final TenantService tenantService;
  private final TransactionTemplate reservationAttemptTransaction;

  public AgencyIdAllocationService(AgencyIdReservationRepository reservationRepository,
      AgencyRepository agencyRepository, TenantService tenantService,
      PlatformTransactionManager transactionManager) {
    this.reservationRepository = reservationRepository;
    this.agencyRepository = agencyRepository;
    this.tenantService = tenantService;
    // each reservation attempt commits (or fails) on its own, so a unique-key collision of one
    // attempt can never poison an enclosing transaction and AUTO mode can simply retry
    this.reservationAttemptTransaction = new TransactionTemplate(transactionManager);
    this.reservationAttemptTransaction
        .setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /** Returns the authoritative state of the given agency ID. */
  public AgencyIdStatus checkAvailability(long agencyId) {
    if (agencyRepository.existsById(agencyId)) {
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
   * in the caller's transaction so entity creation and reservation consumption are atomic.
   *
   * @return whether an open reservation existed and was consumed
   */
  @Transactional
  public boolean consumeReservation(long agencyId) {
    return reservationRepository.deleteByAgencyId(agencyId) > 0;
  }

  /** Returns whether the given agency ID is currently reserved by an open invite. */
  public boolean isReserved(long agencyId) {
    return reservationRepository.existsById(agencyId);
  }

  private Long reserveSpecificId(long agencyId, Long tenantId) {
    try {
      return reservationAttemptTransaction.execute(status -> {
        if (agencyRepository.existsById(agencyId)) {
          throw new ConflictException(AGENCY_ID_NOT_AVAILABLE);
        }
        reservationRepository.saveAndFlush(
            AgencyIdReservation.newReservation(agencyId, tenantId));
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
          if (agencyRepository.existsById(candidate)) {
            return null; // lost a race against a concurrent agency creation, recompute
          }
          reservationRepository.saveAndFlush(
              AgencyIdReservation.newReservation(candidate, tenantId));
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
