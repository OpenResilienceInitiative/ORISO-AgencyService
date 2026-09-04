package de.caritas.cob.agencyservice.api.admin.service.agency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import de.caritas.cob.agencyservice.api.admin.hallink.AgencyLinksBuilder;
import de.caritas.cob.agencyservice.api.model.AgencyAdminDepartmentDTO;
import de.caritas.cob.agencyservice.api.model.AgencyAdminFullResponseDTO;
import de.caritas.cob.agencyservice.api.model.AgencyAdminResponseDTO;
import de.caritas.cob.agencyservice.api.model.AgencyLegalContentDTO;
import de.caritas.cob.agencyservice.api.model.AgencyLinks;
import de.caritas.cob.agencyservice.api.model.DemographicsDTO;
import de.caritas.cob.agencyservice.api.model.TopicDTO;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.service.legal.DepartmentLegalPublicationState;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Builder to build an {@link AgencyAdminFullResponseDTO()} from an {@link Agency} instance.
 */
@RequiredArgsConstructor
@Slf4j
public class AgencyAdminFullResponseDTOBuilder {

  private static final ObjectReader CONTENT_READER =
      new ObjectMapper().readerFor(new TypeReference<Map<String, String>>() {});

  private final @NonNull Agency agency;


  /**
   * Creates an {@link AgencyAdminFullResponseDTO()} with HAL-Links from an {@link Agency}
   * instance.
   *
   * @return an {@link AgencyAdminFullResponseDTO()} instance
   */
  public AgencyAdminFullResponseDTO fromAgency() {
    return new AgencyAdminFullResponseDTO()
        .embedded(createAgency())
        .links(createAgencyLinks());
  }

  private AgencyAdminResponseDTO createAgency() {
    var responseDTO = new AgencyAdminResponseDTO()
        .id(this.agency.getId())
        .tenantId(agency.getTenantId())
        .name(this.agency.getName())
        .city(this.agency.getCity())
        .street(this.agency.getStreet())
        .houseNumber(this.agency.getHouseNumber())
        .floorBuilding(this.agency.getFloorBuilding())
        .country(this.agency.getCountry())
        .phone(this.agency.getPhone())
        .phoneSecondary(this.agency.getPhoneSecondary())
        .email(this.agency.getEmail())
        .openingHours(this.agency.getOpeningHours())
        .lat(this.agency.getLat())
        .lng(this.agency.getLng())
        .consultingType(this.agency.getConsultingTypeId())
        .description(this.agency.getDescription())
        .postcode(this.agency.getPostCode())
        .teamAgency(this.agency.isTeamAgency())
        .url(this.agency.getUrl())
        .external((this.agency.isExternal()))
        .offline(this.agency.isOffline())
        .topics(getTopics())
        .departments(getDepartments())
        .counsellingRelations(splitToList(agency.getCounsellingRelations()))
        .createDate(String.valueOf(this.agency.getCreateDate()))
        .updateDate(String.valueOf(this.agency.getUpdateDate()))
        .deleteDate(String.valueOf(this.agency.getDeleteDate()))
        .dataProtection(new DataProtectionDTOBuilder(this.agency).fromAgency())
        .content(getLegalContent())
        .agencyLogo(this.agency.getAgencyLogo());

    responseDTO.demographics(getDemographics(this.agency));
    return responseDTO;
  }

  private List<AgencyAdminResponseDTO.CounsellingRelationsEnum> splitToList(String counsellingRelationsAsCommaSeparatedString) {
    if (counsellingRelationsAsCommaSeparatedString == null) {
      return Lists.newArrayList();
    } else {
      return Splitter.on(",").trimResults()
          .splitToList(counsellingRelationsAsCommaSeparatedString).stream().map(AgencyAdminResponseDTO.CounsellingRelationsEnum::valueOf).collect(Collectors.toList());
    }
  }

  /**
   * The agency-wide legal texts (ADR-014 middle level, #222), read back as language→HTML maps —
   * the shape the admin UI and the tenant API both use. Stored content was already sanitised on
   * write; a column that never parsed is reported as absent rather than failing the whole agency
   * read, since a legal text must never be able to take the admin's agency page down with it.
   */
  private AgencyLegalContentDTO getLegalContent() {
    var privacy = toContentMap(this.agency.getContentDpp());
    var impressum = toContentMap(this.agency.getContentImprint());
    // ADR-021 decision 4: the consent sentence is a field of the privacy policy, so it is read back
    // through the same object rather than through a second endpoint.
    var consentText = toContentMap(this.agency.getConsentText());
    return privacy == null && impressum == null && consentText == null
        ? null
        : new AgencyLegalContentDTO()
            .privacy(privacy)
            .impressum(impressum)
            .consentText(consentText);
  }

  private Map<String, String> toContentMap(String storedJson) {
    if (storedJson == null || storedJson.isBlank()) {
      return null;
    }
    try {
      return CONTENT_READER.readValue(storedJson);
    } catch (JsonProcessingException e) {
      log.warn("Agency {} has unreadable stored legal content, reporting it as absent",
          this.agency.getId(), e);
      return null;
    }
  }

  private DemographicsDTO getDemographics(Agency agency) {
    return agency.hasAnyDemographicsAttributes() ? new DemographicsConverter().convertToDTO(agency)
        : null;
  }

  /**
   * The departments with the publication state of their own legal texts (ORISO-Admin#583).
   *
   * <p>An admin editing the agency-wide text needs to see who will <em>not</em> receive the
   * change — a Fachbereich that has published its own text no longer inherits. That is the
   * question {@link DepartmentLegalPublicationState} answers, and it is deliberately narrower than
   * the public {@code AgencyDepartmentDTO.hasPublishedDpp}, which reports whether any policy is in
   * force across the four ADR-021 levels. Using the wider predicate here would mark every
   * department of an agency that has any agency-wide text, i.e. exactly the departments that DO
   * still inherit — the marker would say the opposite of what it means.
   *
   * <p>Built from the already-loaded {@code agencyTopics} association ({@code topics} above uses
   * the same one), so this adds no query per agency or topic.
   */
  private List<AgencyAdminDepartmentDTO> getDepartments() {
    var agencyTopics = agency.getAgencyTopics();
    if (agencyTopics == null) {
      return Lists.newArrayList();
    }
    return agencyTopics.stream()
        .map(agencyTopic -> new AgencyAdminDepartmentDTO()
            .topicId(agencyTopic.getTopicId())
            .hasPublishedDpp(DepartmentLegalPublicationState.hasPublishedDpp(agencyTopic))
            .hasPublishedImprint(DepartmentLegalPublicationState.hasPublishedImprint(agencyTopic)))
        .toList();
  }

  private List<TopicDTO> getTopics() {
    // workaround to force loading of topics

    var agencyTopics = agency.getAgencyTopics();
    if (agencyTopics != null) {
      return getTopics(agencyTopics);
    } else {
      return Lists.newArrayList();
    }
  }

  private List<TopicDTO> getTopics(List<AgencyTopic> agencyTopics) {
    return agencyTopics.stream().map(AgencyTopic::getTopicData).collect(Collectors.toList());
  }

  private AgencyLinks createAgencyLinks() {
    return AgencyLinksBuilder.getInstance(agency).buildAgencyLinks();
  }
}
