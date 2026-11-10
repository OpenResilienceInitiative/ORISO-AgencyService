package de.caritas.cob.agencyservice.api.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;

import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import de.caritas.cob.agencyservice.api.repository.agencypostcoderange.AgencyPostcodeRangeRepository;
import de.caritas.cob.agencyservice.api.repository.deletionaudit.DeletionAuditOutboxEntry;
import de.caritas.cob.agencyservice.api.repository.deletionaudit.DeletionAuditOutboxRepository;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgencyPurgeTransactionTest {

  @Mock AgencyRepository agencyRepository;

  @Mock AgencyPostcodeRangeRepository agencyPostcodeRangeRepository;

  @Mock DeletionAuditOutboxRepository deletionAuditOutboxRepository;

  @InjectMocks AgencyPurgeTransaction agencyPurgeTransaction;

  /**
   * Postcode ranges hold a RESTRICT foreign key on agency and are not cascaded by JPA, so they have
   * to be removed first. Departments are not asserted here: they are removed by the {@code cascade =
   * ALL, orphanRemoval = true} mapping on {@code Agency.agencyTopics}, which a mock-based unit test
   * cannot observe — that ordering is Hibernate's, not this class's.
   */
  @Test
  void purge_Should_deletePostcodeRangesBeforeTheAgency() {
    // given
    var agency = new Agency();
    agency.setId(268L);

    // when
    agencyPurgeTransaction.purge(agency);

    // then
    var inOrder = inOrder(agencyPostcodeRangeRepository, agencyRepository);
    inOrder.verify(agencyPostcodeRangeRepository).deleteAllByAgencyId(268L);
    inOrder.verify(agencyRepository).delete(agency);
  }

  /**
   * The outbox row is written by the same transaction as the delete (OBP 2.1 #130): it exists if
   * and only if the purge committed, and UserService later pulls it into the deletion log.
   */
  @Test
  void purge_Should_queueAnExecutedDeletionLogEntryAfterTheDelete() {
    // given
    var agency = new Agency();
    agency.setId(268L);
    agency.setTenantId(4L);

    // when
    agencyPurgeTransaction.purge(agency);

    // then
    var inOrder = inOrder(agencyRepository, deletionAuditOutboxRepository);
    inOrder.verify(agencyRepository).delete(agency);
    var entry = ArgumentCaptor.forClass(DeletionAuditOutboxEntry.class);
    inOrder.verify(deletionAuditOutboxRepository).save(entry.capture());
    assertThat(entry.getValue().getAgencyId()).isEqualTo(268L);
    assertThat(entry.getValue().getTenantId()).isEqualTo(4L);
    assertThat(entry.getValue().getOutcome()).isEqualTo("EXECUTED");
    assertThat(entry.getValue().getCreatedAt()).isNotNull();
    assertThat(entry.getValue().getDeliveredAt()).isNull();
  }
}
