package de.caritas.cob.agencyservice.api.workflow;

import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.api.tenant.TenantContextProvider;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeleteAgenciesMarkedForDeletionScheduler {

  private final @NonNull DeleteAgencyService deleteAgencyService;
  private final @NonNull TenantContextProvider tenantContextProvider;
  private final @NonNull AgencyPurgeRunLock purgeRunLock;

  @Scheduled(cron = "${agency.deleteworkflow.cron}")
  public void performDeletionWorkflow() {
    TenantContext.clear();
    try {
      tenantContextProvider.setTechnicalContextIfMultiTenancyIsEnabled();
      purgeRunLock.runIfAcquired(deleteAgencyService::deleteAgenciesMarkedForDeletion);
    } finally {
      TenantContext.clear();
    }
  }
}
