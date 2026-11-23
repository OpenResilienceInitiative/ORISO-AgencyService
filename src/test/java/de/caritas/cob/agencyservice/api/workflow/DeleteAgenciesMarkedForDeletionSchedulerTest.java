package de.caritas.cob.agencyservice.api.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.api.tenant.TenantContextProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DeleteAgenciesMarkedForDeletionSchedulerTest {

  private final DeleteAgencyService deleteAgencyService = mock(DeleteAgencyService.class);

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  @Test
  void scopesTechnicalTenantToTheDeletionRun() {
    var scheduler = scheduler(true);
    doAnswer(invocation -> {
      assertThat(TenantContext.getCurrentTenant()).isEqualTo(0L);
      return null;
    }).when(deleteAgencyService).deleteAgenciesMarkedForDeletion();

    scheduler.performDeletionWorkflow();

    assertThat(TenantContext.getCurrentTenant()).isNull();
    verify(deleteAgencyService).deleteAgenciesMarkedForDeletion();
  }

  @Test
  void clearsTechnicalTenantWhenDeletionFails() {
    var scheduler = scheduler(true);
    var failure = new IllegalStateException("synthetic deletion failure");
    doAnswer(invocation -> {
      assertThat(TenantContext.getCurrentTenant()).isEqualTo(0L);
      throw failure;
    }).when(deleteAgencyService).deleteAgenciesMarkedForDeletion();

    assertThatThrownBy(scheduler::performDeletionWorkflow).isSameAs(failure);

    assertThat(TenantContext.getCurrentTenant()).isNull();
  }

  @Test
  void disabledMultitenancyRunsWithoutAnInheritedContext() {
    TenantContext.setCurrentTenant(99L);
    var scheduler = scheduler(false);
    doAnswer(invocation -> {
      assertThat(TenantContext.getCurrentTenant()).isNull();
      return null;
    }).when(deleteAgencyService).deleteAgenciesMarkedForDeletion();

    scheduler.performDeletionWorkflow();

    assertThat(TenantContext.getCurrentTenant()).isNull();
    verify(deleteAgencyService).deleteAgenciesMarkedForDeletion();
  }

  private DeleteAgenciesMarkedForDeletionScheduler scheduler(boolean multiTenancyEnabled) {
    var tenantContextProvider = new TenantContextProvider();
    ReflectionTestUtils.setField(tenantContextProvider, "multiTenancyEnabled", multiTenancyEnabled);
    var runLock = mock(AgencyPurgeRunLock.class);
    doAnswer(invocation -> {
      invocation.<Runnable>getArgument(0).run();
      return null;
    }).when(runLock).runIfAcquired(any(Runnable.class));
    return new DeleteAgenciesMarkedForDeletionScheduler(
        deleteAgencyService, tenantContextProvider, runLock);
  }
}
