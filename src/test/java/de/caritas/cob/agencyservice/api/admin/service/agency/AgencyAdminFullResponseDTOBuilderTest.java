package de.caritas.cob.agencyservice.api.admin.service.agency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.caritas.cob.agencyservice.api.model.AgencyAdminDepartmentDTO;
import de.caritas.cob.agencyservice.api.model.AgencyAdminFullResponseDTO;
import de.caritas.cob.agencyservice.api.model.AgencyAdminResponseDTO;
import de.caritas.cob.agencyservice.api.model.AgencyDTO;
import de.caritas.cob.agencyservice.api.model.AgencyLinks;
import de.caritas.cob.agencyservice.api.model.DataProtectionContactDTO;
import de.caritas.cob.agencyservice.api.model.HalLink.MethodEnum;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agency.DataProtectionResponsibleEntity;
import de.caritas.cob.agencyservice.api.repository.agency.Gender;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.repository.agencytopic.PublicationStatus;
import de.caritas.cob.agencyservice.api.util.JsonConverter;
import java.util.List;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgencyAdminFullResponseDTOBuilderTest {

  private static final Long TENANT_ID = 1L;
  AgencyAdminFullResponseDTOBuilder agencyAdminFullResponseDTOBuilder;
  Agency agency;

  @BeforeEach
  public void init() {
    EasyRandom easyRandom = new EasyRandom();
    this.agency = easyRandom.nextObject(Agency.class);
    this.agency.setDataProtectionResponsibleEntity(DataProtectionResponsibleEntity.AGENCY_RESPONSIBLE);
    this.agency.setDataProtectionAgencyResponsibleContactData(JsonConverter.convertToJson(new DataProtectionContactDTO()));
    this.agency.setDataProtectionOfficerContactData(JsonConverter.convertToJson(new DataProtectionContactDTO()));
    this.agency.setDataProtectionAlternativeContactData(JsonConverter.convertToJson(new DataProtectionContactDTO()));
    this.agency.setTenantId(TENANT_ID);
    this.agencyAdminFullResponseDTOBuilder = new AgencyAdminFullResponseDTOBuilder(agency);
    this.agency.setCounsellingRelations(AgencyDTO.CounsellingRelationsEnum.PARENTAL_COUNSELLING.getValue() + "," + AgencyDTO.CounsellingRelationsEnum.RELATIVE_COUNSELLING.getValue());
  }

  @Test
  void fromAgency_Should_Return_ValidAgency() {

    var result = agencyAdminFullResponseDTOBuilder.fromAgency();

    assertBaseDTOAttributesAreMapped(result);
  }

  /**
   * ORISO-Admin#583: the Fachbereich switcher marks who has left the inherited text. The admin
   * read is the one the admin page uses, so the flags have to be on THIS response — reaching
   * across to the public agency read just to render a marker is the wrong shape.
   */
  @Test
  void fromAgency_Should_Report_WhichDepartmentsCarryTheirOwnPublishedTexts() {
    var withOwnDpp = AgencyTopic.builder()
        .topicId(7L)
        .contentDpp("<p>eigene Richtlinie</p>")
        .publicationStatus(PublicationStatus.PUBLISHED)
        .build();
    var stillInheriting = AgencyTopic.builder()
        .topicId(8L)
        .contentDpp("<p>noch Entwurf</p>")
        .publicationStatus(PublicationStatus.DRAFT)
        .build();
    var withOwnImprint = AgencyTopic.builder()
        .topicId(9L)
        .contentImprint("<p>eigenes Impressum</p>")
        .publicationStatusImprint(PublicationStatus.PUBLISHED)
        .build();
    agency.setAgencyTopics(List.of(withOwnDpp, stillInheriting, withOwnImprint));

    var departments = new AgencyAdminFullResponseDTOBuilder(agency).fromAgency()
        .getEmbedded().getDepartments();

    assertThat(departments).hasSize(3);
    assertThat(departments).extracting(
            AgencyAdminDepartmentDTO::getTopicId,
            AgencyAdminDepartmentDTO::getHasPublishedDpp,
            AgencyAdminDepartmentDTO::getHasPublishedImprint)
        .containsExactly(
            tuple(7L, true, false),
            tuple(8L, false, false),
            tuple(9L, false, true));
  }

  @Test
  void fromAgency_Should_Return_EmptyDepartments_When_TheAgencyHasNoTopics() {
    agency.setAgencyTopics(null);

    assertThat(new AgencyAdminFullResponseDTOBuilder(agency).fromAgency()
        .getEmbedded().getDepartments()).isEmpty();
  }

  private void assertBaseDTOAttributesAreMapped(AgencyAdminFullResponseDTO result) {
    assertEquals(agency.getId(), result.getEmbedded().getId());
    assertEquals(agency.getName(), result.getEmbedded().getName());
    assertEquals(agency.getDescription(), result.getEmbedded().getDescription());
    assertEquals(agency.isTeamAgency(), result.getEmbedded().getTeamAgency());
    assertEquals(agency.getPostCode(), result.getEmbedded().getPostcode());
    assertEquals(agency.getCity(), result.getEmbedded().getCity());
    assertEquals(agency.isOffline(), result.getEmbedded().getOffline());
    assertEquals(agency.getUrl(), result.getEmbedded().getUrl());
    assertEquals(agency.isExternal(), result.getEmbedded().getExternal());
    assertEquals(agency.getConsultingTypeId(), result.getEmbedded().getConsultingType());
    assertEquals(agency.getAgencyLogo(), result.getEmbedded().getAgencyLogo());
    assertEquals(agency.getOpeningHours(), result.getEmbedded().getOpeningHours());
    assertThat(result.getEmbedded().getCounsellingRelations()).containsOnly(AgencyAdminResponseDTO.CounsellingRelationsEnum.PARENTAL_COUNSELLING, AgencyAdminResponseDTO.CounsellingRelationsEnum.RELATIVE_COUNSELLING);
    assertEquals(String.valueOf(agency.getCreateDate()), result.getEmbedded().getCreateDate());
    assertEquals(String.valueOf(agency.getUpdateDate()), result.getEmbedded().getUpdateDate());
    assertEquals(String.valueOf(agency.getDeleteDate()), result.getEmbedded().getDeleteDate());
  }

  @Test
  void fromAgency_Should_Return_ValidAgencyWithDemographics_IfAtLeastOneDemographicsAttributeIsAdded() {
    // given
    agency.setAgeFrom((short) 15);
    agency.setAgeTo(null);
    agency.setGenders(Gender.MALE.toString());

    // when
    var result = agencyAdminFullResponseDTOBuilder.fromAgency();

    // then
    assertBaseDTOAttributesAreMapped(result);
    assertEquals(toInteger(agency.getAgeFrom()), result.getEmbedded().getDemographics().getAgeFrom());
    assertEquals(toInteger(agency.getAgeTo()), result.getEmbedded().getDemographics().getAgeTo());
    assertTrue(result.getEmbedded().getDemographics().getGenders().contains(agency.getGenders()));
  }

  @Test
  void fromAgency_Should_Return_ValidAgency_WithoutDemographics_IfNoneDemographicsAttributeAreSet() {
    // given
    agency.setAgeFrom(null);
    agency.setAgeTo(null);
    agency.setGenders(null);
    // when
    var result = agencyAdminFullResponseDTOBuilder.fromAgency();

    // then
    assertBaseDTOAttributesAreMapped(result);
    assertNull(result.getEmbedded().getDemographics());
  }

  private Integer toInteger(Short value) {
    return value != null ? value.intValue() : null;
  }

  @Test
  void fromAgency_Should_Return_ValidHalLinks() {

    AgencyAdminFullResponseDTO result = agencyAdminFullResponseDTOBuilder.fromAgency();
    AgencyLinks agencyLinks = result.getLinks();

    assertThat(result).isNotNull();
    assertEquals(TENANT_ID, result.getEmbedded().getTenantId());
    assertThat(agencyLinks.getSelf()).isNotNull();
    assertThat(agencyLinks.getSelf().getMethod()).isEqualTo(MethodEnum.GET);
    assertThat(agencyLinks.getSelf().getHref()).isEqualTo(String.format("/agencyadmin/agencies/%s", agency.getId()));
    assertThat(agencyLinks.getDelete()).isNotNull();
    assertThat(agencyLinks.getDelete().getMethod()).isEqualTo(MethodEnum.DELETE);
    assertThat(agencyLinks.getDelete().getHref()).isEqualTo(String.format("/agencyadmin/agencies/%s", agency.getId()));
    assertThat(agencyLinks.getUpdate()).isNotNull();
    assertThat(agencyLinks.getUpdate().getMethod()).isEqualTo(MethodEnum.PUT);
    assertThat(agencyLinks.getUpdate().getHref()).isEqualTo(String.format("/agencyadmin/agencies/%s", agency.getId()));
    assertThat(agencyLinks.getPostcodeRanges()).isNotNull();
    assertThat(agencyLinks.getPostcodeRanges().getMethod()).isEqualTo(MethodEnum.GET);
    assertThat(agencyLinks.getPostcodeRanges().getHref()).isEqualTo(String.format("/agencyadmin/postcoderanges/%s", this.agency.getId()));
  }

  @Test
  void fromAgency_Should_exposeAgencyWideLegalTexts_AsLanguageMaps() {
    // Shirloin's finding on ORISO-Admin#562: without this the admin editor could never read back
    // what it had just saved and silently fell through to the tenant text forever.
    this.agency.setContentDpp("{\"de\":\"<p>DSE</p>\",\"en\":\"<p>Privacy</p>\"}");
    this.agency.setContentImprint("{\"de\":\"<p>Impressum</p>\"}");

    var content = new AgencyAdminFullResponseDTOBuilder(agency).fromAgency().getEmbedded()
        .getContent();

    assertThat(content).isNotNull();
    assertThat(content.getPrivacy()).containsEntry("de", "<p>DSE</p>")
        .containsEntry("en", "<p>Privacy</p>");
    assertThat(content.getImpressum()).containsEntry("de", "<p>Impressum</p>");
  }

  @Test
  void fromAgency_Should_omitContent_When_theAgencyAuthoredNoLegalText() {
    this.agency.setContentDpp(null);
    this.agency.setContentImprint("   ");

    var result = new AgencyAdminFullResponseDTOBuilder(agency).fromAgency();

    assertNull(result.getEmbedded().getContent());
  }

  @Test
  void fromAgency_Should_reportUnreadableContentAsAbsent_RatherThanFailTheWholeRead() {
    // A legal text must never be able to take the admin's agency page down with it.
    this.agency.setContentDpp("not json at all");
    this.agency.setContentImprint("{\"de\":\"<p>Impressum</p>\"}");

    var content = new AgencyAdminFullResponseDTOBuilder(agency).fromAgency().getEmbedded()
        .getContent();

    assertNull(content.getPrivacy());
    assertThat(content.getImpressum()).containsEntry("de", "<p>Impressum</p>");
  }

}
