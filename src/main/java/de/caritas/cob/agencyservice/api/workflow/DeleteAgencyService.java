package de.caritas.cob.agencyservice.api.workflow;

import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import de.caritas.cob.agencyservice.api.repository.deletionaudit.DeletionAuditOutboxRepository;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeleteAgencyService {

  private final @NonNull AgencyRepository agencyRepository;

  private final @NonNull AgencyPurgeTransaction agencyPurgeTransaction;

  private final @NonNull DeletionAuditOutboxRepository deletionAuditOutboxRepository;

  /**
   * Purges every agency marked for deletion.
   *
   * <p>Deliberately not transactional. The class previously carried {@code @Transactional}, which
   * meant the whole batch shared one transaction and the deletes were only flushed at commit —
   * outside the per-agency {@code try/catch}. Each agency is now purged in its own transaction by
   * {@link AgencyPurgeTransaction}, so a failure is attributable, logged, and confined to that
   * agency.
   *
   * <p>The run is logged with its match count because a silent job and an empty job used to look
   * identical in the logs.
   */
  public void deleteAgenciesMarkedForDeletion() {
    dropAcknowledgedDeletionLogEntries();
    List<Agency> agenciesMarkedForDeletion = agencyRepository.findAllByDeleteDateNotNull();
    log.info(
        "Agency deletion workflow started, {} agencies marked for deletion.",
        agenciesMarkedForDeletion.size());
    agenciesMarkedForDeletion.forEach(this::deleteAgency);
  }

  private void deleteAgency(Agency agency) {
    try {
      agencyPurgeTransaction.purge(agency);
      log.info("agency with id {} has been deleted.", agency.getId());
    } catch (Exception ex) {
      log.error("Error while deleting agency with id {}", agency.getId(), ex);
    }
  }

  /**
   * Rows UserService has acknowledged are already in its deletion log; they live until the next
   * nightly run at most. A failure here never stops the purge.
   */
  private void dropAcknowledgedDeletionLogEntries() {
    try {
      int dropped = deletionAuditOutboxRepository.deleteDelivered();
      log.info("Dropped {} acknowledged deletion-log outbox rows.", dropped);
    } catch (Exception ex) {
      log.error("Could not drop acknowledged deletion-log outbox rows", ex);
    }
  }
}
