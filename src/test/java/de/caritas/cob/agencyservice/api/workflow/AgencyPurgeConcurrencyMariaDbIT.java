package de.caritas.cob.agencyservice.api.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.caritas.cob.agencyservice.api.tenant.TenantContextProvider;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;

@Testcontainers
class AgencyPurgeConcurrencyMariaDbIT {

  @Container
  private static final MariaDBContainer DATABASE = new MariaDBContainer("mariadb:10.11.18")
      .withDatabaseName("agencyservice");

  @Test
  void skipsAnOverlappingRunThenAllowsTheNextRun() throws Exception {
    var entered = new CountDownLatch(1);
    var finish = new CountDownLatch(1);
    var attempts = new AtomicInteger();
    try (var firstPool = pool(); var secondPool = pool();
        var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = scheduler(firstPool, () -> {
        entered.countDown();
        await(finish);
      });
      var second = scheduler(secondPool, attempts::incrementAndGet);
      var running = executor.submit(first::performDeletionWorkflow);
      try {
        await(entered);
        executor.submit(second::performDeletionWorkflow).get(10, TimeUnit.SECONDS);
        assertThat(attempts.get()).isZero();
      } finally {
        finish.countDown();
      }
      running.get(10, TimeUnit.SECONDS);
      second.performDeletionWorkflow();
      assertThat(attempts.get()).isEqualTo(1);
    }
  }

  @Test
  void releasesTheClaimWhenTheWorkflowThrows() {
    var failure = new IllegalStateException("synthetic purge failure");
    var attempts = new AtomicInteger();
    try (var firstPool = pool(); var secondPool = pool()) {
      var first = scheduler(firstPool, () -> {
        throw failure;
      });
      var second = scheduler(secondPool, attempts::incrementAndGet);

      assertThatThrownBy(first::performDeletionWorkflow).isSameAs(failure);
      second.performDeletionWorkflow();

      assertThat(attempts.get()).isEqualTo(1);
    }
  }

  @Test
  void doesNotRunThePurgeWhenTheClaimCannotBeChecked() {
    var attempts = new AtomicInteger();
    try (var pool = pool()) {
      var scheduler = scheduler(pool, attempts::incrementAndGet);
      pool.close();

      assertThatThrownBy(scheduler::performDeletionWorkflow)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Could not coordinate the agency deletion workflow");
      assertThat(attempts.get()).isZero();
    }
  }

  private DeleteAgenciesMarkedForDeletionScheduler scheduler(HikariDataSource pool, Runnable run) {
    var service = mock(DeleteAgencyService.class);
    doAnswer(invocation -> {
      run.run();
      return null;
    }).when(service).deleteAgenciesMarkedForDeletion();
    return new DeleteAgenciesMarkedForDeletionScheduler(
        service, new TenantContextProvider(), new AgencyPurgeRunLock(pool));
  }

  private HikariDataSource pool() {
    var config = new HikariConfig();
    config.setJdbcUrl(DATABASE.getJdbcUrl());
    config.setUsername(DATABASE.getUsername());
    config.setPassword(DATABASE.getPassword());
    config.setMaximumPoolSize(2);
    config.setMinimumIdle(0);
    return new HikariDataSource(config);
  }

  private void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(15, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    }
  }
}
