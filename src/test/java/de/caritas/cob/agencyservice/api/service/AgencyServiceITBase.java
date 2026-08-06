package de.caritas.cob.agencyservice.api.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import de.caritas.cob.agencyservice.api.exception.MissingConsultingTypeException;
import de.caritas.cob.agencyservice.api.manager.consultingtype.ConsultingTypeManager;
import de.caritas.cob.agencyservice.api.model.AgencyResponseDTO;
import de.caritas.cob.agencyservice.api.model.FullAgencyResponseDTO;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;

import static de.caritas.cob.agencyservice.testHelper.TestConstants.CONSULTING_TYPE_PREGNANCY;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.CONSULTING_TYPE_SETTINGS_PREGNANCY;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.CONSULTING_TYPE_U25;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class AgencyServiceITBase {

  @Autowired
  private AgencyService agencyService;
  @Autowired
  private AgencyRepository agencyRepository;
  @MockitoBean
  private ConsultingTypeManager consultingTypeManager;
  @MockitoBean
  private TopicEnrichmentService topicEnrichmentService;

  @Autowired
  private PlatformTransactionManager transactionManager;

  public void getAgencies_Should_returnMatchingAgencies_When_postcodeAndConsultingTypeIsGiven()
      throws MissingConsultingTypeException {

    when(consultingTypeManager.getConsultingTypeSettings(CONSULTING_TYPE_PREGNANCY)).thenReturn(CONSULTING_TYPE_SETTINGS_PREGNANCY);
    String postCode = "88662";

    // The response mapping walks Agency.agencyTopics lazily. A real request has an open
    // session for the whole request (open-in-view); a plain test method does not, so without
    // this boundary the call dies with LazyInitializationException (#205).
    List<FullAgencyResponseDTO> resultAgencies = new TransactionTemplate(transactionManager)
        .execute(status -> agencyService.getAgencies(Optional.of(postCode),
            CONSULTING_TYPE_PREGNANCY, Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty()));

    assertThat(resultAgencies, hasSize(1));
    FullAgencyResponseDTO resultAgency = resultAgencies.get(0);
    assertThat(resultAgency.getId(), is(883L));
  }

  public void setAgencyOffline_Should_FlagAgencyAsOfflineAndSetUpdateDate() {

    Agency agencyBefore = agencyRepository.findById(883L)
        .orElseThrow(RuntimeException::new);
    agencyService.setAgencyOffline(883L);
    Agency agencyAfter = agencyRepository.findById(883L)
        .orElseThrow(RuntimeException::new);
    assertTrue(agencyAfter.isOffline());
    assertNotEquals(agencyBefore.getUpdateDate(), agencyAfter.getUpdateDate());
  }

  public void getAgenciesByConsultingType_Should_returnResults_When_ConsultingTypeIsValid() {
    List<AgencyResponseDTO> agencies = this.agencyService.getAgencies(CONSULTING_TYPE_U25);

    assertThat(agencies, hasSize(greaterThan(0)));
  }

  public void getAgenciesTopics_Should_ReturnResults_When_topics_exist() {
    List<Integer> topicIds = this.agencyService.getAgenciesTopics();

    assertThat(topicIds, hasSize(greaterThan(0)));
  }

  public void getAgencies_Should_returnMatchingAgencies_When_postcodeAndTopicIdIsGiven() {

    String postCode = "45501";
    Integer topicId = 1;

    // Same lazy agencyTopics mapping as above — needs a session boundary (#205).
    List<FullAgencyResponseDTO> resultAgencies = new TransactionTemplate(transactionManager)
        .execute(status -> agencyService.getAgencies(postCode, topicId));

    assertThat(resultAgencies, hasSize(1));
    FullAgencyResponseDTO resultAgency = resultAgencies.get(0);
    // 14352 is the AGENCY_POSTCODE_RANGE id covering 45501-45600; the agency it belongs to is
    // 1735. The old expectation asserted the range id against an agency id (#205).
    assertThat(resultAgency.getId(), is(1735L));
  }

}
