package de.caritas.cob.agencyservice.api.repository.legaltext;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

@TestPropertySource(properties = {"spring.profiles.active=testing"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ExtendWith(SpringExtension.class)
@DataJpaTest(excludeAutoConfiguration = LiquibaseAutoConfiguration.class)
class AgencyLegalDraftRepositoryTest {

  @Autowired private AgencyLegalDraftRepository repository;

  @Test
  @Transactional
  void compareAndSwap_Should_allowOnlyOneWriterForTheSameRevision() {
    var draft = save(7L, LegalTextKind.DPP, "first");
    var firstSavedAt = LocalDateTime.now();

    assertThat(
            repository.compareAndSwap(
                7L,
                LegalTextKind.DPP,
                draft.getRowId(),
                0L,
                "winner",
                "consent",
                firstSavedAt))
        .isOne();
    assertThat(
            repository.compareAndSwap(
                7L,
                LegalTextKind.DPP,
                draft.getRowId(),
                0L,
                "loser",
                "stale consent",
                LocalDateTime.now()))
        .isZero();

    var stored = repository.findByAgencyIdAndKind(7L, LegalTextKind.DPP).orElseThrow();
    assertThat(stored.getContent()).isEqualTo("winner");
    assertThat(stored.getConsentText()).isEqualTo("consent");
    assertThat(stored.getVersion()).isEqualTo(1L);
  }

  @Test
  @Transactional
  void deletedRowRevision_Should_notMatchARecreatedDraft() {
    var deleted = save(7L, LegalTextKind.IMPRINT, "old");
    assertThat(
            repository.compareAndDelete(
                7L, LegalTextKind.IMPRINT, deleted.getRowId(), deleted.getVersion()))
        .isOne();

    var recreated = save(7L, LegalTextKind.IMPRINT, "new");
    assertThat(recreated.getRowId()).isNotEqualTo(deleted.getRowId());

    assertThat(
            repository.compareAndSwap(
                7L,
                LegalTextKind.IMPRINT,
                deleted.getRowId(),
                deleted.getVersion(),
                "stale overwrite",
                null,
                LocalDateTime.now()))
        .isZero();
    assertThat(
            repository.compareAndDelete(
                7L, LegalTextKind.IMPRINT, deleted.getRowId(), deleted.getVersion()))
        .isZero();
    assertThat(repository.findByAgencyIdAndKind(7L, LegalTextKind.IMPRINT))
        .get()
        .extracting(AgencyLegalDraft::getContent)
        .isEqualTo("new");
  }

  private AgencyLegalDraft save(Long agencyId, LegalTextKind kind, String content) {
    return repository.saveAndFlush(
        AgencyLegalDraft.builder()
            .rowId(UUID.randomUUID().toString())
            .agencyId(agencyId)
            .kind(kind)
            .content(content)
            .savedAt(LocalDateTime.now())
            .build());
  }
}
