package de.caritas.cob.agencyservice.api.service;

import de.caritas.cob.agencyservice.AgencyServiceApplication;
import de.caritas.cob.agencyservice.api.exception.MissingConsultingTypeException;
import de.caritas.cob.agencyservice.api.manager.consultingtype.ConsultingTypeManager;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = AgencyServiceApplication.class)
@TestPropertySource(properties = "spring.profiles.active=testing")
@AutoConfigureTestDatabase(replace = Replace.ANY)
@Sql(scripts = "/database/AgencyDatabase.sql")
public class AgencyServiceIT extends AgencyServiceITBase {

  /**
   * Without this the suite reaches the real TenantService on localhost:8089 and fails with
   * "Connection refused" (#205). The tenant-aware sibling already mocks it.
   */
  @MockitoBean
  private TenantService tenantService;

  @Test
  public void getAgencies_Should_returnMatchingAgencies_When_postcodeAndConsultingTypeIsGiven()
      throws MissingConsultingTypeException {
    super.getAgencies_Should_returnMatchingAgencies_When_postcodeAndConsultingTypeIsGiven();
  }

  @Test
  public void setAgencyOffline_Should_FlagAgencyAsOfflineAndSetUpdateDate() {
    super.setAgencyOffline_Should_FlagAgencyAsOfflineAndSetUpdateDate();
  }

  @Test
  public void getAgenciesByConsultingType_Should_returnResults_When_ConsultingTypeIsValid() {
    super.getAgenciesByConsultingType_Should_returnResults_When_ConsultingTypeIsValid();
  }

  @Test
  public void getAgenciesTopics_Should_ReturnResults_When_topics_exist() {
    super.getAgenciesTopics_Should_ReturnResults_When_topics_exist();
  }

  @Test
  public void getAgencies_Should_returnMatchingAgencies_When_postcodeAndTopicIdIsGiven() {
    super.getAgencies_Should_returnMatchingAgencies_When_postcodeAndTopicIdIsGiven();
  }
}
