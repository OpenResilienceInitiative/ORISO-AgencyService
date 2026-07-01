package de.caritas.cob.agencyservice.api.repository.agency;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Self-contained persistence test for the Beratungszentrum (agency centre) address/contact fields
 * that the admin create/edit forms need. Overrides the dialect to H2 so Hibernate builds the schema
 * from the entities and the test is runnable in a bare local build.
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
  }
}
