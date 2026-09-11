package de.caritas.cob.agencyservice.api.repository.agency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
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
 * Self-contained persistence test for the Beratungszentrum (agency centre) address/contact fields
 * that the admin create/edit forms need. Uses the {@code testing} profile (see
 * {@code application-testing.properties}) so Hibernate builds the schema from the entities on H2.
 */
@TestPropertySource(properties = {"spring.profiles.active=testing"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ExtendWith(SpringExtension.class)
@DataJpaTest(excludeAutoConfiguration = LiquibaseAutoConfiguration.class)
class AgencyAddressRepositoryTest {

  @Autowired private TestEntityManager em;

  @Test
  void persist_Should_storeAddressAndContactFields() {
    // given
    var now = LocalDateTime.now();
    Agency agency =
        Agency.builder()
            .name("Beratungszentrum")
            .consultingTypeId(1)
            .dataProtectionResponsibleEntity(DataProtectionResponsibleEntity.AGENCY_RESPONSIBLE)
            .dataProtectionOfficerContactData("officer")
            .dataProtectionAlternativeContactData("alternative")
            .dataProtectionAgencyResponsibleContactData("agency")
            .street("Musterstraße")
            .houseNumber("12a")
            .floorBuilding("2. Stock, Haus A")
            .country("Deutschland")
            .phone("+49301234567")
            .phoneSecondary("+49301234568")
            .email("kontakt@zentrum.de")
            .openingHours("Mo-Fr 9-17 Uhr\nSa 10-12 Uhr")
            .createDate(now)
            .updateDate(now)
            .build();

    // when
    Long id = em.persistFlushFind(agency).getId();
    em.clear();

    // then
    Agency reloaded = em.find(Agency.class, id);
    assertThat(reloaded.getStreet()).isEqualTo("Musterstraße");
    assertThat(reloaded.getHouseNumber()).isEqualTo("12a");
    assertThat(reloaded.getFloorBuilding()).isEqualTo("2. Stock, Haus A");
    assertThat(reloaded.getCountry()).isEqualTo("Deutschland");
    assertThat(reloaded.getPhone()).isEqualTo("+49301234567");
    assertThat(reloaded.getPhoneSecondary()).isEqualTo("+49301234568");
    assertThat(reloaded.getEmail()).isEqualTo("kontakt@zentrum.de");
    assertThat(reloaded.getOpeningHours()).isEqualTo("Mo-Fr 9-17 Uhr\nSa 10-12 Uhr");
  }
}
