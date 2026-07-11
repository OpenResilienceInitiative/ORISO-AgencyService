package de.caritas.cob.agencyservice.api.repository.agency;

import static org.assertj.core.api.Assertions.assertThat;

import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.jdbc.Sql;

@TestPropertySource(properties = {"spring.profiles.active=testing"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ExtendWith(SpringExtension.class)
@DataJpaTest
@Sql(scripts = "/database/AgencyDatabase.sql")
class AgencyRepositoryIT {

  @Autowired
  private AgencyRepository agencyRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void findById_Should_loadAgencyWithTopics() {
    // given, when
    var optionalAgency = agencyRepository.findById(0L);
    var agency = optionalAgency.orElseThrow(RuntimeException::new);
    // then
    assertThat(agency.getId()).isZero();
    assertThat(agency.getAgencyTopics())
        .hasSize(2)
        .extracting(AgencyTopic::getId)
        .containsExactly(0L, 1L);
  }

  @Test
  void findById_Should_loadAgencyWithDemographics() {
    // given, when
    var optionalAgency = agencyRepository.findById(1736L);
    var agency = optionalAgency.orElseThrow(RuntimeException::new);
    // then
    assertThat(agency.getId()).isEqualTo(1736);
    assertThat(agency.getAgeFrom()).isEqualTo((short) 15);
    assertThat(agency.getAgeTo()).isEqualTo((short) 100);
    assertThat(agency.getGenders()).isEqualTo(Gender.MALE.toString());
  }

  @Test
  void searchWithoutTopic_Should_findAgencyByPostcodeAndConsultingType() {
    // given, when
    var agencyList = agencyRepository.searchWithoutTopic("53113", 5, 0, null, null, null, 1L);
    // then
    assertThat(agencyList).hasSize(2);
  }

  @Test
  void searchWithoutTopic_Should_findAgencyByOnlyConsultingTypeSkippingPostCodeFiltering() {
    // given, when
    var agencyList = agencyRepository.searchWithoutTopic(null, 5, 0, null, null, null, 1L);
    // then
    assertThat(agencyList).hasSize(104);
  }


  @Test
  void searchWithTopic_Should_findAgencyByPostcodeAndConsultingTypeAndTopicId() {
    // given, when
    var agencyList = agencyRepository.searchWithTopic("53113", 5, 0, 1, null, null, null, 1L);
    // then
    assertThat(agencyList).hasSize(1);
    assertThat(agencyList.get(0).getId()).isZero();
    assertThat(agencyList.get(0).getAgencyTopics()).extracting("topicId").containsExactly(0L, 1L);
  }

  @Test
  void searchWithTopic_Should_findAgencyConsultingTypeAndTopicIdSkippingPostCode() {
    // given, when
    var agencyList = agencyRepository.searchWithTopic(null, 5, 0, 1, null, null, null, 1L);
    // then
    assertThat(agencyList).hasSize(2);
  }

  @Test
  void searchWithTopic_Should_findAgencyByPostcodeAndConsultingTypeAndTopicId_When_ConsultingTypeIsNotProvided() {
    // given, when
    var agencyList = agencyRepository.searchWithTopic("53113", 5, null, 1, null, null, null, 1L);
    // then
    assertThat(agencyList).hasSize(1);
    assertThat(agencyList.get(0).getId()).isZero();
    assertThat(agencyList.get(0).getAgencyTopics()).extracting("topicId").containsExactly(0L, 1L);
  }

  @Test
  void demoBaselineSync_Should_beIdempotentAndMakeStandardRegistrationTopicsVisible() {
    // given, when
    syncDemoBaseline();
    syncDemoBaseline();

    // then
    var parentsAndFamilyAgencies =
        agencyRepository.searchWithTopic("88885", 5, 1, 10, null, null, null, 1L);
    var childrenAndYouthAgencies =
        agencyRepository.searchWithTopic("88885", 5, 1, 2, null, null, null, 1L);

    assertThat(parentsAndFamilyAgencies)
        .extracting(Agency::getId)
        .contains(246L);
    assertThat(childrenAndYouthAgencies)
        .extracting(Agency::getId)
        .contains(246L);

    Integer duplicateAgencyTopics = jdbcTemplate.queryForObject(
        """
            SELECT COUNT(*)
            FROM (
              SELECT AGENCY_ID, TOPIC_ID
              FROM AGENCY_TOPIC
              WHERE AGENCY_ID = 246 AND TOPIC_ID IN (2, 10)
              GROUP BY AGENCY_ID, TOPIC_ID
              HAVING COUNT(*) > 1
            )
            """,
        Integer.class);

    assertThat(duplicateAgencyTopics).isZero();
  }

  @Test
  void searchWithTopic_Should_notFindAgencyByPostcodeAndConsultingTypeAndTopicId_When_ConsultingTypeDoesNotMatch() {
    // given, when
    var agencyList = agencyRepository.searchWithTopic("53113", 5, 1, 1, null, null, null, 1L);
    // then
    assertThat(agencyList).isEmpty();
  }

  private void syncDemoBaseline() {
    var populator = new ResourceDatabasePopulator(
        new ClassPathResource("/database/DemoBaselineAgencyVisibility.sql"));
    populator.execute(jdbcTemplate.getDataSource());
  }


  @Test
  void searchWithoutTopic_Should_findAgenciesByPostcodeAndConsultingTypeAndAgeAndGender_WhenGenderIsMale() {
    // given, when
    var agencyList = agencyRepository.searchWithoutTopic("99999", 5, 19, 30, "MALE", null, 1L);
    // then
    assertThat(agencyList).hasSize(2);
    assertThat(agencyList).extracting(a -> a.getId()).containsExactly(1736L, 1738L);
  }

  @Test
  void searchWithoutTopic_Should_findDifferentAgenciesByPostcodeAndConsultingTypeAndAgeAndGender_WhenGenderIsDivers() {
    // given, when
    var agencyList = agencyRepository.searchWithoutTopic("99999", 5, 19, 30, "DIVERS", null, 1L);
    // then
    assertThat(agencyList).hasSize(2);
    assertThat(agencyList).extracting(a -> a.getId()).containsExactly(1737L, 1738L);
  }

  @Test
  void searchWithoutTopic_Should_notFindAnyAgenciesByPostcodeAndConsultingTypeAndAgeAndGender_WhenGenderIsNotMatchingAnyAgency() {
    // given, when
    var agencyList = agencyRepository.searchWithoutTopic("99999", 5, 19, 30, "NOTMATCHING", null, 1L);
    // then
    assertThat(agencyList).isEmpty();
  }

  @Test
  void searchWithoutTopic_Should_ignoreConsultingTypeId_WhenConsultingTypeIdIsNotProvided() {
    // given, when
    var agencyList = agencyRepository.searchWithoutTopic("99999", 5, null, null, null, null, 1L);
    // then
    assertThat(agencyList).hasSize(6);
  }


  @ParameterizedTest
  @ValueSource(strings = {"\";", "';", ";"})
  void searchWithoutTopic_Should_searchForGenderBeProtectedAgainstSqlInjection_WhenGenderIsProvided(String prefix) {
    // given, when
    var agencyList = agencyRepository.searchWithoutTopic("99999", 5, 19, 30, prefix + "DROP TABLE AGENCY;", null, 1L);
    // then
    var existingAgencyList = agencyRepository.searchWithoutTopic("99999", 5, 19, 30, "DIVERS", null, 1L);

    assertThat(agencyList).isEmpty();
    assertThat(existingAgencyList).isNotEmpty();
  }

  @Test
  void searchWithoutTopic_Should_findExactlyOneAgencyByPostcodeAndConsultingTypeAndAge_WhenAgeMatchesWithJustOneAgency() {
    // given, when
    var agencyList = agencyRepository.searchWithoutTopic("99999", 5, 19, 15, "MALE", null, 1L);
    // then
    assertThat(agencyList).hasSize(1);
    assertThat(agencyList).extracting(a -> a.getId()).containsExactly(1736L);
  }

  @Test
  void searchWithoutTopic_Should_notFindAnyAgencyByPostcodeAndConsultingTypeAndAge_WhenAgeDoesNotMatchWithAnyAgency() {
    // given, when
    var agencyList = agencyRepository.searchWithoutTopic("99999", 5, 19, 5, "MALE", null, 1L);
    // then
    assertThat(agencyList).isEmpty();
  }

  @Test
  void save_Should_cascadeTopics_When_newTopicIsAdded() {
    // given
    var optionalAgency = agencyRepository.findById(0L);
    var agency = optionalAgency.orElseThrow(RuntimeException::new);
    // when
    AgencyTopic newAssignedTopic = AgencyTopic.builder().agency(agency).topicId(1L).build();
    agency.getAgencyTopics().add(newAssignedTopic);
    agencyRepository.save(agency);
    // then
    var agencyUpdated = agencyRepository.findById(0L).orElseThrow(RuntimeException::new);
    assertThat(agencyUpdated.getAgencyTopics())
        .hasSize(3)
        .extracting(AgencyTopic::getId)
        .containsExactly(0L, 1L, 100000L);
  }

  @Test
  void save_Should_cascadeTopics_When_existingTopicIsRemoved() {
    // given
    var optionalAgency = agencyRepository.findById(0L);
    var agency = optionalAgency.orElseThrow(RuntimeException::new);
    // when
    agency.getAgencyTopics().remove(0);
    agencyRepository.save(agency);
    // then
    var optionalAgencyUpdated = agencyRepository.findById(0L);
    assertThat(optionalAgencyUpdated).isPresent();
    var agencyUpdated = optionalAgencyUpdated.get();
    assertThat(agencyUpdated.getAgencyTopics())
        .hasSize(1)
        .extracting(AgencyTopic::getId)
        .containsExactly(1L);
  }

  @Test
  void save_Should_cascadeTopics_When_allTopicsAreRemoved() {
    // given
    var optionalAgency = agencyRepository.findById(0L);
    var agency = optionalAgency.orElseThrow(RuntimeException::new);
    // when
    agency.getAgencyTopics().clear();
    agencyRepository.save(agency);
    // then
    var optionalAgencyUpdated = agencyRepository.findById(0L);
    assertThat(optionalAgencyUpdated).isPresent();
    var agencyUpdated = optionalAgencyUpdated.get();
    assertThat(agencyUpdated.getAgencyTopics()).isEmpty();
  }
}
