package de.caritas.cob.agencyservice.api.repository.agencytopic;

import static org.assertj.core.api.Assertions.assertThat;

import de.caritas.cob.agencyservice.api.repository.agency.Agency;
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
 * Self-contained persistence test for changeset 0030 (ORISO-Admin#197): a department (Fachbereich
 * = agency × topic) carries its own nullable contact detail overrides — {@code opening_hours},
 * {@code phone_extension}, {@code floor_location}. Null means "inherits the Beratungsstelle
 * value". Uses the {@code testing} profile (see {@code application-testing.properties}) so
 * Hibernate builds the schema from the entities on H2.
 */
@TestPropertySource(properties = {"spring.profiles.active=testing"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ExtendWith(SpringExtension.class)
@DataJpaTest(excludeAutoConfiguration = LiquibaseAutoConfiguration.class)
class AgencyTopicDetailsRepositoryTest {

  @Autowired private TestEntityManager em;

  private Agency persistAgency() {
    var now = LocalDateTime.now();
    return em.persistFlushFind(
        Agency.builder()
            .name("Beratungszentrum Details")
            .consultingTypeId(1)
            .openingHours("Mo-Fr 9-17 Uhr")
            .floorBuilding("Haus B")
            .createDate(now)
            .updateDate(now)
            .build());
  }

  @Test
  void departmentDetailOverrides_Should_roundTrip() {
    Agency agency = persistAgency();
    var now = LocalDateTime.now();
    var department =
        AgencyTopic.builder()
            .agency(agency)
            .topicId(42L)
            .openingHours("Di+Do 14-18 Uhr")
            .phoneExtension("-23")
            .floorLocation("3. OG, Raum 312")
            .createDate(now)
            .updateDate(now)
            .build();

    var saved = em.persistFlushFind(department);

    assertThat(saved.getOpeningHours()).isEqualTo("Di+Do 14-18 Uhr");
    assertThat(saved.getPhoneExtension()).isEqualTo("-23");
    assertThat(saved.getFloorLocation()).isEqualTo("3. OG, Raum 312");
  }

  @Test
  void departmentDetailOverrides_Should_defaultToNull_meaningInherited() {
    Agency agency = persistAgency();
    var now = LocalDateTime.now();
    var department =
        AgencyTopic.builder().agency(agency).topicId(43L).createDate(now).updateDate(now).build();

    var saved = em.persistFlushFind(department);

    assertThat(saved.getOpeningHours()).isNull();
    assertThat(saved.getPhoneExtension()).isNull();
    assertThat(saved.getFloorLocation()).isNull();
  }
}
