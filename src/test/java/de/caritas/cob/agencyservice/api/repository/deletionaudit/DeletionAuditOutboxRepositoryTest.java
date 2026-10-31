package de.caritas.cob.agencyservice.api.repository.deletionaudit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@TestPropertySource(properties = {"spring.profiles.active=testing"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ExtendWith(SpringExtension.class)
@DataJpaTest(excludeAutoConfiguration = LiquibaseAutoConfiguration.class)
class DeletionAuditOutboxRepositoryTest {

  @Autowired private DeletionAuditOutboxRepository repository;

  @Test
  void pendingRowsComeOldestFirstAndOnlyUntilAcknowledged() {
    var first = repository.save(DeletionAuditOutboxEntry.executed(11L, 1L));
    var second = repository.save(DeletionAuditOutboxEntry.executed(12L, null));
    var third = repository.save(DeletionAuditOutboxEntry.executed(13L, 2L));

    assertThat(repository.findPending(PageRequest.of(0, 2)))
        .extracting(DeletionAuditOutboxEntry::getAgencyId)
        .containsExactly(11L, 12L);

    assertThat(
            repository.markDelivered(
                List.of(first.getId(), second.getId(), 999L), LocalDateTime.now()))
        .isEqualTo(2);
    // A repeated acknowledgement does not move the delivery time.
    assertThat(repository.markDelivered(List.of(first.getId()), LocalDateTime.now())).isZero();

    assertThat(repository.findPending(PageRequest.of(0, 10)))
        .extracting(DeletionAuditOutboxEntry::getId)
        .containsExactly(third.getId());
  }

  @Test
  void deleteDeliveredKeepsRowsUserServiceHasNotAcknowledged() {
    var delivered = repository.save(DeletionAuditOutboxEntry.executed(21L, 1L));
    var pending = repository.save(DeletionAuditOutboxEntry.executed(22L, 1L));
    repository.markDelivered(List.of(delivered.getId()), LocalDateTime.now());

    assertThat(repository.deleteDelivered()).isEqualTo(1);

    assertThat(repository.findAll())
        .extracting(DeletionAuditOutboxEntry::getId)
        .containsExactly(pending.getId());
  }
}
