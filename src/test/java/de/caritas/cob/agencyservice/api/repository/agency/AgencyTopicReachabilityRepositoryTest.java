package de.caritas.cob.agencyservice.api.repository.agency;

import static org.assertj.core.api.Assertions.assertThat;

import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * {@code findAllAgenciesTopics} answers "which topics actually have agencies?" and drives what
 * registration offers a help-seeker. A topic whose only agencies are offline or soft-deleted is a
 * guaranteed dead end — the help-seeker picks it and lands on "Keine Online-Beratungsstelle
 * gefunden" with no explanation (ORISO-Frontend#245).
 */
@TestPropertySource(properties = {"spring.profiles.active=testing"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ExtendWith(SpringExtension.class)
@DataJpaTest(excludeAutoConfiguration = LiquibaseAutoConfiguration.class)
class AgencyTopicReachabilityRepositoryTest {

  @Autowired private TestEntityManager em;
  @Autowired private AgencyRepository agencyRepository;

  private Agency persistAgency(String name, boolean offline, LocalDateTime deleteDate) {
    var now = LocalDateTime.now();
    return em.persistFlushFind(
        Agency.builder()
            .name(name)
            .consultingTypeId(1)
            .offline(offline)
            .deleteDate(deleteDate)
            .dataProtectionResponsibleEntity(DataProtectionResponsibleEntity.AGENCY_RESPONSIBLE)
            .dataProtectionOfficerContactData("officer")
            .dataProtectionAlternativeContactData("alternative")
            .dataProtectionAgencyResponsibleContactData("agency")
            .createDate(now)
            .updateDate(now)
            .build());
  }

  private void persistDepartment(Agency agency, long topicId) {
    var now = LocalDateTime.now();
    em.persistFlushFind(
        AgencyTopic.builder()
            .agency(agency)
            .topicId(topicId)
            .createDate(now)
            .updateDate(now)
            .build());
  }

  @Test
  void findAllAgenciesTopics_Should_reportTopic_When_itHasAReachableAgency() {
    persistDepartment(persistAgency("Reachable", false, null), 4711L);
    em.clear();

    assertThat(agencyRepository.findAllAgenciesTopics(null)).contains(4711);
  }

  @Test
  void findAllAgenciesTopics_Should_omitTopic_When_itsOnlyAgencyIsOffline() {
    persistDepartment(persistAgency("Offline only", true, null), 4712L);
    em.clear();

    assertThat(agencyRepository.findAllAgenciesTopics(null)).doesNotContain(4712);
  }

  @Test
  void findAllAgenciesTopics_Should_omitTopic_When_itsOnlyAgencyIsSoftDeleted() {
    persistDepartment(persistAgency("Deleted only", false, LocalDateTime.now()), 4713L);
    em.clear();

    assertThat(agencyRepository.findAllAgenciesTopics(null)).doesNotContain(4713);
  }

  @Test
  void findAllAgenciesTopics_Should_reportTopic_When_atLeastOneOfItsAgenciesIsReachable() {
    // A topic stays offerable as long as one agency behind it can actually take the help-seeker.
    persistDepartment(persistAgency("Offline sibling", true, null), 4714L);
    persistDepartment(persistAgency("Reachable sibling", false, null), 4714L);
    em.clear();

    assertThat(agencyRepository.findAllAgenciesTopics(null)).contains(4714);
  }
}
