package de.caritas.cob.agencyservice.api.repository.legaltext;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * ADR-021 decision 3 persistence model: an append-only publication history addressed by a
 * surrogate id, generic over kind and level.
 */
@TestPropertySource(properties = {"spring.profiles.active=testing"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ExtendWith(SpringExtension.class)
@DataJpaTest(excludeAutoConfiguration = LiquibaseAutoConfiguration.class)
class LegalTextVersionRepositoryTest {

  @Autowired private LegalTextVersionRepository repository;

  private LegalTextVersion persist(
      LegalTextLevel level, Long ownerId, LegalTextKind kind, String content, LocalDateTime at) {
    return repository.save(
        LegalTextVersion.builder()
            .tenantId(1L)
            .kind(kind)
            .ownerLevel(level)
            .ownerId(ownerId)
            .content(content)
            .publishedAt(at)
            .publishedBy("admin-uuid")
            .build());
  }

  @Test
  void save_Should_assignSurrogateId_soIdentityNeverDependsOnTheTimestamp() {
    var sameSecond = LocalDateTime.of(2026, 8, 16, 12, 0, 0);

    var first = persist(LegalTextLevel.DEPARTMENT, 5L, LegalTextKind.DPP, "v1", sameSecond);
    var second = persist(LegalTextLevel.DEPARTMENT, 5L, LegalTextKind.DPP, "v2", sameSecond);

    // The AVV defect in one line: two publications inside the same second are indistinguishable by
    // timestamp on MariaDB DATETIME(0). They must still be two addressable versions.
    assertThat(first.getId()).isNotNull().isNotEqualTo(second.getId());
  }

  @Test
  void findHistory_Should_returnNewestFirst_andScopeToOwnerAndKind() {
    persist(
        LegalTextLevel.DEPARTMENT,
        5L,
        LegalTextKind.DPP,
        "old",
        LocalDateTime.of(2026, 1, 1, 0, 0));
    persist(
        LegalTextLevel.DEPARTMENT,
        5L,
        LegalTextKind.DPP,
        "new",
        LocalDateTime.of(2026, 6, 1, 0, 0));
    persist(
        LegalTextLevel.DEPARTMENT,
        5L,
        LegalTextKind.IMPRINT,
        "imprint",
        LocalDateTime.of(2026, 7, 1, 0, 0));
    persist(
        LegalTextLevel.AGENCY, 5L, LegalTextKind.DPP, "agency", LocalDateTime.of(2026, 7, 1, 0, 0));

    var history =
        repository.findByOwnerLevelAndOwnerIdAndKindOrderByPublishedAtDescIdDesc(
            LegalTextLevel.DEPARTMENT, 5L, LegalTextKind.DPP);

    // Same owner id on a different level is a different document — that is the whole point of
    // naming the level (ADR-021 decision 1).
    assertThat(history).extracting(LegalTextVersion::getContent).containsExactly("new", "old");
  }

  @Test
  void findCurrent_Should_returnOnlyTheNotYetSupersededVersion() {
    var superseded =
        persist(
            LegalTextLevel.AGENCY, 9L, LegalTextKind.DPP, "old", LocalDateTime.of(2026, 1, 1, 0, 0));
    superseded.setSupersededAt(LocalDateTime.of(2026, 6, 1, 0, 0));
    repository.save(superseded);
    persist(
        LegalTextLevel.AGENCY, 9L, LegalTextKind.DPP, "new", LocalDateTime.of(2026, 6, 1, 0, 0));

    var current =
        repository.findFirstByOwnerLevelAndOwnerIdAndKindAndSupersededAtIsNullOrderByIdDesc(
            LegalTextLevel.AGENCY, 9L, LegalTextKind.DPP);

    assertThat(current).isPresent();
    assertThat(current.get().getContent()).isEqualTo("new");
  }

  @Test
  void storedWording_Should_beReadableVerbatimAfterItWasSuperseded() {
    var archived =
        persist(
            LegalTextLevel.DEPARTMENT,
            3L,
            LegalTextKind.DPP,
            "{\"de\":\"<p>Fassung 2025</p>\"}",
            LocalDateTime.of(2025, 5, 1, 0, 0));
    archived.setSupersededAt(LocalDateTime.of(2026, 5, 1, 0, 0));
    repository.save(archived);

    // The epic's first acceptance criterion: a superseded version is still retrievable verbatim.
    assertThat(repository.findById(archived.getId()))
        .get()
        .extracting(LegalTextVersion::getContent)
        .isEqualTo("{\"de\":\"<p>Fassung 2025</p>\"}");
  }
}
