package de.caritas.cob.agencyservice.api.admin.service.allocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.exception.httpresponses.BadRequestException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.ConflictException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.repository.agencyidreservation.AgencyIdReservationRepository;
import de.caritas.cob.agencyservice.api.service.TenantService;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

/**
 * Integration test for the shared agency ID allocation contract (TEN-INV-U2, parent
 * OpenResilienceInitiative/ORISO-Admin#569).
 *
 * <p>Runs without a wrapping test transaction because the service manages its own transactions
 * (REQUIRES_NEW per reservation attempt) and the concurrency tests need real parallel
 * transactions against the shared H2 database.
 */
@TestPropertySource(properties = {"spring.profiles.active=testing"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ExtendWith(SpringExtension.class)
@DataJpaTest
@Import(AgencyIdAllocationService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AgencyIdAllocationServiceIT {

  private static final long EXISTING_TENANT_ID = 1L;
  private static final long UNKNOWN_TENANT_ID = 99L;

  @Autowired private AgencyIdAllocationService allocationService;

  @Autowired private AgencyIdReservationRepository reservationRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @MockitoBean private TenantService tenantService;

  @AfterEach
  void cleanDatabase() {
    jdbcTemplate.update("DELETE FROM agency_id_reservation");
    jdbcTemplate.update("DELETE FROM agency");
  }

  /* Worked example from the parent bug: IDs 1-20 and 30-35 are taken. Here 1-20 are ASSIGNED
   * (real agency rows) and 30-35 are RESERVED (open invites). */
  private void seedWorkedExample() {
    seedAgencies(1, 20);
    seedReservations(30, 35);
  }

  private void seedAgencies(long fromId, long toId) {
    for (long id = fromId; id <= toId; id++) {
      jdbcTemplate.update(
          "INSERT INTO agency (id, name, is_team_agency, consulting_type, is_offline, is_external,"
              + " create_date, update_date, data_protection_responsible_entity)"
              + " VALUES (?, 'Seeded agency', 0, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,"
              + " 'AGENCY_RESPONSIBLE')",
          id);
    }
  }

  private void seedReservations(long fromId, long toId) {
    for (long id = fromId; id <= toId; id++) {
      allocationService.reserve(id, null);
    }
  }

  @Test
  void reserve_Should_assignSmallestFreeId_When_autoModeIsRequested() {
    seedWorkedExample();

    var reservedId = allocationService.reserve(null, null);

    assertThat(reservedId).isEqualTo(21L);
    assertThat(allocationService.checkAvailability(21L)).isEqualTo(AgencyIdStatus.RESERVED);
  }

  @Test
  void checkAvailability_Should_distinguishAssignedReservedAndFree() {
    seedWorkedExample();

    assertThat(allocationService.checkAvailability(5L)).isEqualTo(AgencyIdStatus.ASSIGNED);
    assertThat(allocationService.checkAvailability(30L)).isEqualTo(AgencyIdStatus.RESERVED);
    assertThat(allocationService.checkAvailability(21L)).isEqualTo(AgencyIdStatus.FREE);
  }

  @Test
  void nextFreeId_Should_skipAssignedAndReservedIds() {
    seedWorkedExample();

    // stepping up from 29 jumps over the reserved block 30-35
    assertThat(allocationService.nextFreeId(29L, AgencyIdStepDirection.UP)).contains(36L);
    // stepping down from 36 jumps back to 29
    assertThat(allocationService.nextFreeId(36L, AgencyIdStepDirection.DOWN)).contains(29L);
    // plain neighbour steps
    assertThat(allocationService.nextFreeId(21L, AgencyIdStepDirection.UP)).contains(22L);
    // no free ID below 21: 1-20 are all assigned
    assertThat(allocationService.nextFreeId(21L, AgencyIdStepDirection.DOWN)).isEmpty();
  }

  @Test
  void reserve_Should_rejectManualRequest_When_idIsAssignedOrReserved() {
    seedWorkedExample();

    assertThatThrownBy(() -> allocationService.reserve(5L, null))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> allocationService.reserve(30L, null))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void release_Should_makeReservedIdAssignableAgain() {
    seedWorkedExample();

    var reservedId = allocationService.reserve(21L, null);
    assertThat(reservedId).isEqualTo(21L);

    allocationService.release(21L);
    assertThat(allocationService.checkAvailability(21L)).isEqualTo(AgencyIdStatus.FREE);

    // a released ID can be reserved again
    assertThat(allocationService.reserve(21L, null)).isEqualTo(21L);
  }

  @Test
  void release_Should_throwNotFound_When_noReservationExists() {
    assertThatThrownBy(() -> allocationService.release(4711L))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void consumeReservation_Should_removeReservationExactlyOnce() {
    allocationService.reserve(21L, null);

    assertThat(allocationService.consumeReservation(21L)).isTrue();
    assertThat(allocationService.consumeReservation(21L)).isFalse();
    assertThat(reservationRepository.existsById(21L)).isFalse();
  }

  @Test
  void reserve_Should_rejectRequest_When_tenantDoesNotExist() {
    when(tenantService.getRestrictedTenantDataByTenantId(UNKNOWN_TENANT_ID))
        .thenThrow(new RestClientException("404 tenant not found"));

    assertThatThrownBy(() -> allocationService.reserve(21L, UNKNOWN_TENANT_ID))
        .isInstanceOf(BadRequestException.class);
    assertThat(reservationRepository.existsById(21L)).isFalse();
  }

  @Test
  void reserve_Should_succeed_When_tenantExists() {
    when(tenantService.getRestrictedTenantDataByTenantId(EXISTING_TENANT_ID))
        .thenReturn(new RestrictedTenantDTO().id(EXISTING_TENANT_ID));

    var reservedId = allocationService.reserve(null, EXISTING_TENANT_ID);

    assertThat(reservedId).isEqualTo(1L);
    var reservation = reservationRepository.findById(1L).orElseThrow();
    assertThat(reservation.getTenantId()).isEqualTo(EXISTING_TENANT_ID);
  }

  @Test
  void reserve_Should_returnDifferentIds_When_twoAutoRequestsRunInParallel() throws Exception {
    seedWorkedExample();

    List<Long> reservedIds = runInParallel(
        () -> allocationService.reserve(null, null),
        () -> allocationService.reserve(null, null));

    assertThat(reservedIds).hasSize(2).doesNotHaveDuplicates().containsExactlyInAnyOrder(21L, 22L);
  }

  @Test
  void reserve_Should_letExactlyOneWin_When_twoManualRequestsRaceForTheSameId() throws Exception {
    seedWorkedExample();

    var successes = new ArrayList<Long>();
    var conflicts = new ArrayList<Exception>();
    collectParallelOutcomes(
        List.of(() -> allocationService.reserve(21L, null),
            () -> allocationService.reserve(21L, null)),
        successes, conflicts);

    assertThat(successes).containsExactly(21L);
    assertThat(conflicts).hasSize(1);
    assertThat(conflicts.get(0)).isInstanceOf(ConflictException.class);
  }

  /* --- creation-vs-reservation bridge (guardAssignmentAgainstOpenReservations) --- */

  @Test
  void guardAssignment_Should_throwConflictAndKeepReservation_When_idIsReservedByOpenInvite() {
    allocationService.reserve(21L, null);
    var creationTransaction = new TransactionTemplate(transactionManager);

    assertThatThrownBy(() -> creationTransaction.execute(status -> {
      seedAgencies(21, 21);
      allocationService.guardAssignmentAgainstOpenReservations(21L);
      return null;
    })).isInstanceOf(ConflictException.class);

    // the creation transaction rolled back, the open reservation survives untouched
    assertThat(allocationService.checkAvailability(21L)).isEqualTo(AgencyIdStatus.RESERVED);
  }

  @Test
  void guardAssignment_Should_leaveNoReservationBehind_When_idIsFree() {
    var creationTransaction = new TransactionTemplate(transactionManager);

    creationTransaction.execute(status -> {
      seedAgencies(21, 21);
      allocationService.guardAssignmentAgainstOpenReservations(21L);
      return null;
    });

    assertThat(allocationService.checkAvailability(21L)).isEqualTo(AgencyIdStatus.ASSIGNED);
    assertThat(reservationRepository.existsById(21L)).isFalse();
  }

  @Test
  void guardAssignment_Should_rejectUsageOutsideOfCreationTransaction() {
    // MANDATORY propagation: outside the creating transaction the guard row would commit on its
    // own and arbitrate nothing, so standalone usage is a programming error
    assertThatThrownBy(() -> allocationService.guardAssignmentAgainstOpenReservations(21L))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  /**
   * Reserve-vs-createAgency race (TOCTOU review finding): a manual reservation runs while an
   * agency creation for the very same ID is mid-transaction (row inserted + guard held, not yet
   * committed). The primary key of agency_id_reservation arbitrates: the reservation must lose
   * with a conflict — regardless of whether it collides with the guard row directly or passes
   * the lock and then sees the committed agency row — and the ID must never end up ASSIGNED and
   * RESERVED at the same time.
   */
  @Test
  void reserve_Should_conflict_When_creationOfTheSameIdIsInFlight() throws Exception {
    var creationTransaction = new TransactionTemplate(transactionManager);
    var guardHeld = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> creation = executor.submit(() -> creationTransaction.execute(status -> {
        seedAgencies(21, 21);
        allocationService.guardAssignmentAgainstOpenReservations(21L);
        guardHeld.countDown();
        try {
          // hold the creation transaction open while the reservation attempt runs into it
          Thread.sleep(750);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        return null;
      }));
      assertThat(guardHeld.await(30, TimeUnit.SECONDS)).isTrue();

      Future<Long> reservation = executor.submit(() -> allocationService.reserve(21L, null));

      assertThatThrownBy(() -> reservation.get(30, TimeUnit.SECONDS))
          .hasCauseInstanceOf(ConflictException.class);
      creation.get(30, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
      executor.awaitTermination(10, TimeUnit.SECONDS);
    }

    // invariant: assigned, and no reservation row survived the lost race
    assertThat(allocationService.checkAvailability(21L)).isEqualTo(AgencyIdStatus.ASSIGNED);
    assertThat(reservationRepository.existsById(21L)).isFalse();
  }

  private List<Long> runInParallel(ReservationCall first, ReservationCall second)
      throws Exception {
    var successes = new ArrayList<Long>();
    var failures = new ArrayList<Exception>();
    collectParallelOutcomes(List.of(first, second), successes, failures);
    assertThat(failures).isEmpty();
    return successes;
  }

  private void collectParallelOutcomes(List<ReservationCall> calls, List<Long> successes,
      List<Exception> failures) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(calls.size());
    try {
      var startLatch = new CountDownLatch(1);
      List<Future<Long>> futures = new ArrayList<>();
      for (ReservationCall call : calls) {
        futures.add(executor.submit(() -> {
          startLatch.await();
          return call.reserve();
        }));
      }
      startLatch.countDown();
      for (Future<Long> future : futures) {
        try {
          successes.add(future.get(30, TimeUnit.SECONDS));
        } catch (java.util.concurrent.ExecutionException e) {
          failures.add((Exception) e.getCause());
        }
      }
    } finally {
      executor.shutdownNow();
      executor.awaitTermination(10, TimeUnit.SECONDS);
    }
  }

  @FunctionalInterface
  private interface ReservationCall {
    Long reserve();
  }
}
