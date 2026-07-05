package de.caritas.cob.agencyservice.api.repository.agencytopic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agency.DataProtectionResponsibleEntity;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Self-contained persistence test for ADR-003's "department = unique (agency × topic)" rule: the
 * {@code agency_topic} table carries {@code UNIQUE(agency_id, topic_id)} plus the department's own
 * imprint ({@code content_imprint} / {@code publication_status_imprint}). Uses the {@code testing}
 * profile (see {@code application-testing.properties}) so Hibernate builds the schema from the
 * entities on H2.
 */
@TestPropertySource(properties = {"spring.profiles.active=testing"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ExtendWith(SpringExtension.class)
@DataJpaTest(excludeAutoConfiguration = LiquibaseAutoConfiguration.class)
class AgencyTopicDepartmentConstraintRepositoryTest {

  @Autowired private TestEntityManager em;

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

  private AgencyTopic department(Agency agency, long topicId) {
    var now = LocalDateTime.now();
    return AgencyTopic.builder()
        .agency(agency)
        .topicId(topicId)
        .createDate(now)
        .updateDate(now)
        .build();
  }

  @Test
  void insertingTheSameAgencyTopicPairTwice_Should_violateUniqueConstraint() {
    Agency agency = persistAgency("Beratungszentrum Unique");
    em.persist(department(agency, 42L));
    em.flush();

    // the duplicate (agency_id, topic_id) row must be rejected by UNIQUE(agency_id, topic_id)
    Throwable thrown =
        catchThrowable(
            () -> {
              em.persist(department(agency, 42L));
              em.flush();
            });
    assertThat(thrown).isNotNull();
    assertThat(ExceptionUtils.getStackTrace(thrown)).containsIgnoringCase("uq_agency_topic");
  }

  @Test
  void sameTopicOnDifferentAgencies_Should_bothPersist() {
    Agency agency = persistAgency("Zentrum A");
    Agency otherAgency = persistAgency("Zentrum B");
    em.persist(department(agency, 42L));
    em.persist(department(otherAgency, 42L));

    assertThatCode(() -> em.flush()).doesNotThrowAnyException();
  }

  @Test
  void dedup_Should_keepLowestIdPerPair_andAllowAddingTheUniqueConstraintAfterwards() {
    // replay the 0023 migration order on a schema WITHOUT the constraint: duplicates exist ->
    // dedup keeps the lowest id per (agency_id, topic_id) -> the UNIQUE constraint can be added
    EntityManager entityManager = em.getEntityManager();
    entityManager
        .createNativeQuery("ALTER TABLE agency_topic DROP CONSTRAINT uq_agency_topic")
        .executeUpdate();

    Agency agency = persistAgency("Zentrum Dedup");
    final Long keptId = em.persistAndFlush(department(agency, 42L)).getId();
    final Long duplicateId = em.persistAndFlush(department(agency, 42L)).getId();
    final Long unrelatedId = em.persistAndFlush(department(agency, 43L)).getId();
    assertThat(duplicateId).isGreaterThan(keptId);

    // H2-portable equivalent of the 0023 dedup DELETE (the changeset itself uses MariaDB's
    // multi-table "DELETE t1 FROM ... INNER JOIN" syntax with identical semantics)
    int deleted =
        entityManager
            .createNativeQuery(
                "DELETE FROM agency_topic t1 WHERE EXISTS (SELECT 1 FROM agency_topic t2 "
                    + "WHERE t2.agency_id = t1.agency_id AND t2.topic_id = t1.topic_id "
                    + "AND t2.id < t1.id)")
            .executeUpdate();
    assertThat(deleted).isEqualTo(1);
    em.clear();

    assertThat(em.find(AgencyTopic.class, keptId)).isNotNull();
    assertThat(em.find(AgencyTopic.class, duplicateId)).isNull();
    assertThat(em.find(AgencyTopic.class, unrelatedId)).isNotNull();

    // this is the exact ALTER statement the 0023 changeset applies after the dedup
    assertThatCode(
            () ->
                entityManager
                    .createNativeQuery(
                        "ALTER TABLE agency_topic "
                            + "ADD CONSTRAINT uq_agency_topic UNIQUE (agency_id, topic_id)")
                    .executeUpdate())
        .doesNotThrowAnyException();
  }

  @Test
  void persist_Should_storeDepartmentImprintAndItsPublicationStatus() {
    Agency agency = persistAgency("Zentrum Impressum");
    var now = LocalDateTime.now();
    AgencyTopic department =
        AgencyTopic.builder()
            .agency(agency)
            .topicId(7L)
            .contentImprint("{\"de\":\"<p>Impressum Fachbereich</p>\"}")
            .publicationStatusImprint(PublicationStatus.PUBLISHED)
            .createDate(now)
            .updateDate(now)
            .build();

    Long id = em.persistFlushFind(department).getId();
    em.clear();

    AgencyTopic reloaded = em.find(AgencyTopic.class, id);
    assertThat(reloaded.getContentImprint()).isEqualTo("{\"de\":\"<p>Impressum Fachbereich</p>\"}");
    assertThat(reloaded.getPublicationStatusImprint()).isEqualTo(PublicationStatus.PUBLISHED);
  }

  @Test
  void imprintPublicationStatus_Should_defaultToDraft_When_notSet() {
    Agency agency = persistAgency("Zentrum Entwurf");
    Long id = em.persistFlushFind(department(agency, 7L)).getId();
    em.clear();

    assertThat(em.find(AgencyTopic.class, id).getPublicationStatusImprint())
        .isEqualTo(PublicationStatus.DRAFT);
  }
}
