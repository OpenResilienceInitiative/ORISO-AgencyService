package de.caritas.cob.agencyservice.api.repository.agencytopic;

import static org.assertj.core.api.Assertions.assertThat;

import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agency.DataProtectionResponsibleEntity;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Self-contained persistence test for the per-Fachbereich (department = agency × topic) legal model:
 * each {@code agency_topic} carries its own data privacy policy ({@code content_dpp}) and a
 * publication status. The module's other {@code @DataJpaTest} tests assume an externally provisioned
 * schema (MariaDB dialect), so this test overrides the dialect to H2 and lets Hibernate build the
 * schema from the entities — runnable and meaningful in a bare local build.
 */
@TestPropertySource(
    properties = {
      "spring.profiles.active=testing",
      "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ExtendWith(SpringExtension.class)
@DataJpaTest
class AgencyTopicLegalRepositoryTest {

  @Autowired private TestEntityManager em;
  @Autowired private AgencyTopicRepository agencyTopicRepository;

  private Agency persistAgency(String name) {
    var now = LocalDateTime.now();
    return em.persistFlushFind(
        Agency.builder()
            .name(name)
            .consultingTypeId(1)
            .dataProtectionResponsibleEntity(DataProtectionResponsibleEntity.AGENCY_RESPONSIBLE)
            .dataProtectionOfficerContactData("officer")
            .dataProtectionAlternativeContactData("alternative")
            .dataProtectionAgencyResponsibleContactData("agency")
            .createDate(now)
            .updateDate(now)
            .build());
  }

  @Test
  void persist_Should_storeFachbereichDppAndPublicationStatus() {
    // given a Beratungszentrum (agency) and a Fachbereich (agency × topic) with its own DSE
    var now = LocalDateTime.now();
    Agency agency = persistAgency("Beratungszentrum Test");
    AgencyTopic department =
        AgencyTopic.builder()
            .agency(agency)
            .topicId(42L)
            .contentDpp("<p>Datenschutzerklärung Fachbereich</p>")
            .publicationStatus(PublicationStatus.PUBLISHED)
            .createDate(now)
            .updateDate(now)
            .build();

    // when
    Long id = em.persistFlushFind(department).getId();
    em.clear();

    // then
    AgencyTopic reloaded = em.find(AgencyTopic.class, id);
    assertThat(reloaded.getContentDpp()).isEqualTo("<p>Datenschutzerklärung Fachbereich</p>");
    assertThat(reloaded.getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
  }

  @Test
  void publicationStatus_Should_defaultToDraft_When_notSet() {
    // given
    var now = LocalDateTime.now();
    Agency agency = persistAgency("Zentrum 2");
    AgencyTopic department =
        AgencyTopic.builder().agency(agency).topicId(7L).createDate(now).updateDate(now).build();

    // when
    Long id = em.persistFlushFind(department).getId();
    em.clear();

    // then
    assertThat(em.find(AgencyTopic.class, id).getPublicationStatus())
        .isEqualTo(PublicationStatus.DRAFT);
  }

  @Test
  void findByAgencyIdAndTopicId_Should_returnTheMatchingFachbereich() {
    // given two departments on the same agency plus one on another agency
    var now = LocalDateTime.now();
    Agency agency = persistAgency("Zentrum 3");
    Agency otherAgency = persistAgency("Zentrum 4");
    em.persist(
        AgencyTopic.builder().agency(agency).topicId(10L).createDate(now).updateDate(now).build());
    em.persist(
        AgencyTopic.builder().agency(agency).topicId(20L).createDate(now).updateDate(now).build());
    em.persist(
        AgencyTopic.builder()
            .agency(otherAgency)
            .topicId(10L)
            .createDate(now)
            .updateDate(now)
            .build());
    em.flush();
    em.clear();

    // when
    var found = agencyTopicRepository.findByAgency_IdAndTopicId(agency.getId(), 20L);
    var wrongTopic = agencyTopicRepository.findByAgency_IdAndTopicId(agency.getId(), 99L);

    // then only the exact (agency, topic) pair matches
    assertThat(found).isPresent();
    assertThat(found.get().getTopicId()).isEqualTo(20L);
    assertThat(found.get().getAgency().getId()).isEqualTo(agency.getId());
    assertThat(wrongTopic).isEmpty();
  }
}
