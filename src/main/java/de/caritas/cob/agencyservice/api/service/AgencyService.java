package de.caritas.cob.agencyservice.api.service;


import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.google.common.collect.Lists;
import de.caritas.cob.agencyservice.api.admin.service.agency.DemographicsConverter;
import de.caritas.cob.agencyservice.api.exception.MissingConsultingTypeException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.BadRequestException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.InternalServerErrorException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.manager.consultingtype.ConsultingTypeManager;
import de.caritas.cob.agencyservice.api.model.AgencyMatrixCredentialsDTO;
import de.caritas.cob.agencyservice.api.model.AgencyResponseDTO;
import de.caritas.cob.agencyservice.api.model.DemographicsDTO;
import de.caritas.cob.agencyservice.api.model.FullAgencyResponseDTO;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.consultingtypeservice.generated.web.model.ExtendedConsultingTypeResponseDTO;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.Settings;
import de.caritas.cob.agencyservice.api.service.matrix.MatrixProvisioningService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for agencies.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgencyService {

  @Qualifier("agencyTenantUnawareRepository")
  @Autowired
  AgencyRepository agencyTenantUnawareRepository;

  private final @NonNull ConsultingTypeManager consultingTypeManager;
  private final @NonNull AgencyRepository agencyRepository;
  private final @NonNull MatrixProvisioningService matrixProvisioningService;

  private final @NonNull TenantService tenantService;
  private final @NonNull DemographicsConverter demographicsConverter;

  private final @NonNull CentralDataProtectionTemplateService centralDataProtectionTemplateService;

  private final @NonNull ApplicationSettingsService applicationSettingsService;

  @Value("${feature.topics.enabled}")
  private boolean topicsFeatureEnabled;

  @Value("${feature.demographics.enabled}")
  private boolean demographicsFeatureEnabled;

  @Value("${multitenancy.enabled}")
  private boolean multitenancy;

  @Value("${feature.multitenancy.with.single.domain.enabled}")
  private boolean multitenancyWithSingleDomain;

  private static final String DB_POSTCODES_ERROR = "Database error while getting postcodes";

  /**
   * Returns a list of {@link AgencyResponseDTO} which match the provided agencyIds.
   *
   * @param agencyIds the ids of requested agencies
   * @return a list containing regarding agencies
   */
  public List<AgencyResponseDTO> getAgencies(List<Long> agencyIds) {
    return getAgencyRepositoryForSearch().findByIdIn(agencyIds).stream()
        .map(this::convertToAgencyResponseDTO)
        .toList();
  }

  /**
   * Returns a list of {@link AgencyResponseDTO} which match the provided consulting type.
   *
   * @param consultingTypeId the id of the request
   * @return a list containing regarding agencies
   */
  public List<AgencyResponseDTO> getAgencies(int consultingTypeId) {
    try {
      verifyConsultingTypeExists(consultingTypeId);

      return agencyRepository.findByConsultingTypeId(consultingTypeId).stream()
          .map(this::convertToAgencyResponseDTO)
          .toList();

    } catch (MissingConsultingTypeException ex) {
      throw new BadRequestException(
          String.format("Consulting type with id %s does not exist", consultingTypeId));
    }
  }

  public List<FullAgencyResponseDTO> getAgencies(Optional<String> postCode, int consultingTypeId,
      Optional<Integer> topicId) {
    return getAgencies(postCode, consultingTypeId, topicId, Optional.empty(), Optional.empty(), Optional.empty());
  }

  /**
   * Returns a randomly sorted list of {@link AgencyResponseDTO} which match to the provided
   * postCode. If no agency is found, returns the atm hard coded white spot agency id.
   *
   * @param postCode         the postcode for regarding agencies
   * @param consultingTypeId the consulting type used for filtering agencies
   * @return a list containing regarding agencies
   */
  public List<FullAgencyResponseDTO> getAgencies(Optional<String> postCode,
      Integer consultingTypeId,
      Optional<Integer> topicId,
      Optional<Integer> age, Optional<String> gender, Optional<String> counsellingRelation) {

    var consultingTypeSettings = retrieveConsultingTypeSettings(
        consultingTypeId);
    if (postCode.isPresent() && doesPostCodeNotMatchMinSize(postCode.get(),
        consultingTypeSettings)) {
      return Collections.emptyList();
    }

    var agencies = findAgencies(postCode, getConsultingTypeIdForSearch(consultingTypeId), topicId,
        age, gender, counsellingRelation);
    Collections.shuffle(agencies);
    var agencyResponseDTOs = agencies.stream()
        .map(this::convertToFullAgencyResponseDTO)
        .toList();

    var mutableResponseDTO = Lists.newArrayList(agencyResponseDTOs);

    if (agencyResponseDTOs.isEmpty()) {
      addWhiteSpotAgency(consultingTypeSettings, mutableResponseDTO);
    }

    return mutableResponseDTO;
  }

  public List<FullAgencyResponseDTO> getAgencies(String postCode, Integer topicId) {

    var agencies = findAgenciesForCurrentTenant(postCode, topicId);
    return agencies.stream()
        .map(this::convertToFullAgencyResponseDTO)
        .toList();
  }

  public List<Integer> getAgenciesTopics() {
    return agencyRepository.findAllAgenciesTopics(TenantContext.getCurrentTenant());
  }

  @Transactional
  public Optional<AgencyMatrixCredentialsDTO> provisionMatrixCredentials(Long agencyId) {
    var agency = agencyRepository.findById(agencyId).orElseThrow(NotFoundException::new);
    return provisionMatrixCredentials(agency);
  }

  @Transactional
  public Optional<AgencyMatrixCredentialsDTO> provisionMatrixCredentials(Agency agency) {
    return provisionMatrixCredentials(agency, true);
  }

  private Optional<AgencyMatrixCredentialsDTO> provisionMatrixCredentials(Agency agency, boolean saveEntity) {
    log.info("Attempting to provision Matrix credentials for agency {} (id={})", agency.getName(), agency.getId());
    
    if (nonNull(agency.getMatrixUserId()) && nonNull(agency.getMatrixPassword())) {
      log.info("Agency {} already has Matrix credentials: {}", agency.getName(), agency.getMatrixUserId());
      return Optional.of(new AgencyMatrixCredentialsDTO(agency.getMatrixUserId(), agency.getMatrixPassword()));
    }

    log.info("No existing Matrix credentials found. Provisioning new account for agency {}", agency.getName());
    
    try {
      var optionalCredentials =
          matrixProvisioningService.ensureAgencyAccount(
              "agency-" + agency.getId(), agency.getName());

      if (optionalCredentials.isEmpty()) {
        log.warn("Matrix provisioning returned empty result for agency {} (id={})", agency.getName(), agency.getId());
        return Optional.empty();
      }

      var creds = optionalCredentials.get();
      agency.setMatrixUserId(creds.getUserId());
      agency.setMatrixPassword(creds.getPassword());
      if (saveEntity) {
        agencyRepository.updateMatrixCredentials(
            agency.getId(), creds.getUserId(), creds.getPassword());
        log.info("Successfully provisioned and saved Matrix credentials for agency {} (id={}): {}", 
                 agency.getName(), agency.getId(), creds.getUserId());
      }

      return Optional.of(new AgencyMatrixCredentialsDTO(creds.getUserId(), creds.getPassword()));
    } catch (Exception ex) {
      log.warn(
          "Matrix provisioning failed for agency {} (id={}): {}",
          agency.getName(),
          agency.getId(),
          ex.getMessage(), ex);
      return Optional.empty();
    }
  }

  public Optional<AgencyMatrixCredentialsDTO> getMatrixCredentials(Long agencyId) {
    return agencyRepository
        .findById(agencyId)
        .map(
            agency ->
                new AgencyMatrixCredentialsDTO(
                    agency.getMatrixUserId(), agency.getMatrixPassword()));
  }

  private Optional<Integer> getConsultingTypeIdForSearch(int consultingTypeId) {
    return multitenancyWithSingleDomain ? Optional.empty() : Optional.of(consultingTypeId);
  }

  private List<Agency> findAgencies(Optional<String> postCode, Optional<Integer> consultingTypeId,
      Optional<Integer> optionalTopicId, Optional<Integer> age,
      Optional<String> gender, Optional<String> counsellingRelation) {

    AgencySearch agencySearch = buildAgencySearch(postCode,
        consultingTypeId, optionalTopicId, age, gender, counsellingRelation);

    if (demographicsFeatureEnabled) {
      assertAgeAndGenderAreProvided(age, gender);
    }

    if (isTopicFeatureEnabledAndActivatedInRegistration()) {
      assertTopicIdIsProvided(optionalTopicId);
      return findAgenciesWithTopic(agencySearch);
    } else {
      return findAgencies(agencySearch);
    }
  }

  private List<Agency> findAgencies(AgencySearch agencySearch) {
    try {
      return getAgencyRepositoryForSearch()
          .searchWithoutTopic(agencySearch.getPostCode().orElse(null),
              agencySearch.getPostCode().orElse("").length(), agencySearch.getConsultingTypeId().orElse(null),
              agencySearch.getAge().orElse(null),
              agencySearch.getGender().orElse(null),
              agencySearch.getCounsellingRelation().orElse(null),
              TenantContext.getCurrentTenant());
    } catch (DataAccessException ex) {
      throw new InternalServerErrorException(LogService::logDatabaseError,
          DB_POSTCODES_ERROR);
    }
  }

  private List<Agency> findAgenciesForCurrentTenant(String postCode, Integer topicId) {

    AgencySearch agencySearch = buildAgencySearch(Optional.of(postCode),
        Optional.empty(), Optional.of(topicId), Optional.empty(),
        Optional.empty(), Optional.empty());

    return findAgenciesWithTopicForCurrentTenant(agencySearch);
  }

  private static AgencySearch buildAgencySearch(Optional<String> postCode,
      Optional<Integer> consultingTypeId, Optional<Integer> optionalTopicId, Optional<Integer> age,
      Optional<String> gender, Optional<String> counsellingRelation) {
    return AgencySearch.builder()
        .postCode(postCode)
        .consultingTypeId(consultingTypeId)
        .topicId(optionalTopicId)
        .age(age)
        .gender(gender)
        .counsellingRelation(counsellingRelation)
        .build();
  }

  private void assertTopicIdIsProvided(Optional<Integer> topicId) {
    if (!topicId.isPresent()) {
      throw new BadRequestException("Topic id not provided in the search");
    }
  }

  private boolean isTopicFeatureEnabledAndActivatedInRegistration() {
    return topicsFeatureEnabled && isTopicFeatureActivatedInRegistration();
  }

  private boolean isTopicFeatureActivatedInRegistration() {
    RestrictedTenantDTO restrictedTenantDTO = getRestrictedTenantData();
    if (nonNull(restrictedTenantDTO) && nonNull(restrictedTenantDTO.getSettings())) {
      return Boolean.TRUE.equals(
          restrictedTenantDTO.getSettings().getTopicsInRegistrationEnabled());
    }
    return false;
  }

  private RestrictedTenantDTO getRestrictedTenantData() {
    if (multitenancy) {
      Long tenantId = TenantContext.getCurrentTenant();
      return tenantService.getRestrictedTenantDataByTenantId(tenantId);
    } else {
      return tenantService.getRestrictedTenantDataForSingleTenant();
    }
  }

  private void verifyConsultingTypeExists(int consultingTypeId)
      throws MissingConsultingTypeException {
    consultingTypeManager.getConsultingTypeSettings(consultingTypeId);
  }

  private boolean doesPostCodeNotMatchMinSize(String postCode,
      ExtendedConsultingTypeResponseDTO consultingTypeSettings) {
    var registration = consultingTypeSettings.getRegistration();
    return nonNull(registration) && nonNull(registration.getMinPostcodeSize())
        && postCode.length() < registration.getMinPostcodeSize();
  }

  public ExtendedConsultingTypeResponseDTO retrieveConsultingTypeSettings(int consultingTypeId) {
    try {
      return consultingTypeManager.getConsultingTypeSettings(consultingTypeId);
    } catch (MissingConsultingTypeException e) {
      throw new InternalServerErrorException(LogService::logInternalServerError, e.getMessage());
    }
  }

  private List<Agency> findAgenciesWithTopic(AgencySearch agencySearch) {
    try {
      return getAgencyRepositoryForSearch()
          .searchWithTopic(agencySearch.getPostCode().orElse(null), agencySearch.getPostCode().orElse("").length(),
              agencySearch.getConsultingTypeId().orElse(null),
              agencySearch.getTopicId().orElseThrow(),
              agencySearch.getAge().orElse(null), agencySearch.getGender().orElse(null),
              agencySearch.getCounsellingRelation().orElse(null),
              TenantContext.getCurrentTenant());

    } catch (DataAccessException ex) {
      throw new InternalServerErrorException(LogService::logDatabaseError,
          DB_POSTCODES_ERROR);
    }
  }

  private List<Agency> findAgenciesWithTopicForCurrentTenant(AgencySearch agencySearch) {
    try {
      return agencyRepository
          .searchWithTopic(agencySearch.getPostCode().orElse(null), agencySearch.getPostCode().orElse("").length(),
              agencySearch.getConsultingTypeId().orElse(null),
              agencySearch.getTopicId().orElseThrow(),
              agencySearch.getAge().orElse(null), agencySearch.getGender().orElse(null),
              agencySearch.getCounsellingRelation().orElse(null),
              TenantContext.getCurrentTenant());

    } catch (DataAccessException ex) {
      throw new InternalServerErrorException(LogService::logDatabaseError,
          DB_POSTCODES_ERROR);
    }
  }

  private AgencyRepository getAgencyRepositoryForSearch() {
    if (multitenancyWithSingleDomain) {
      return agencyTenantUnawareRepository;
    }
    return agencyRepository;
  }

  private void assertAgeAndGenderAreProvided(Optional<Integer> age, Optional<String> gender) {
    if (!age.isPresent()) {
      throw new BadRequestException("Age not provided in the search");
    }
    if (!gender.isPresent()) {
      throw new BadRequestException("Age not provided in the search");
    }
  }

  private void addWhiteSpotAgency(ExtendedConsultingTypeResponseDTO consultingTypeSettings,
      List<FullAgencyResponseDTO> agencyResponseDTOs) {

    var whiteSpot = consultingTypeSettings.getWhiteSpot();
    if (nonNull(whiteSpot) && nonNull(whiteSpot.getWhiteSpotAgencyId()) && isTrue(
        whiteSpot.getWhiteSpotAgencyAssigned())) {
      try {
        getAgencyRepositoryForSearch().findByIdAndDeleteDateNull(
                Long.valueOf(whiteSpot.getWhiteSpotAgencyId()))
            .ifPresent(agency -> agencyResponseDTOs.add(convertToFullAgencyResponseDTO(agency)));
      } catch (NumberFormatException nfEx) {
        throw new InternalServerErrorException(LogService::logNumberFormatException,
            "Invalid white spot agency id");
      }
    }
  }

  private RestrictedTenantDTO getTenantDataRelevantForFeatureToggles(Agency agency) {
    if (multitenancyWithSingleDomain) {
      String mainTenantSubdomain = applicationSettingsService.getApplicationSettings()
          .getMainTenantSubdomainForSingleDomainMultitenancy().getValue();
      return tenantService.getRestrictedTenantDataBySubdomain(mainTenantSubdomain);
    } else {

      return agency.getTenantId() != null ? tenantService.getRestrictedTenantDataByTenantId(
          agency.getTenantId()) : null;
    }
  }

  private AgencyResponseDTO convertToAgencyResponseDTO(Agency agency) {
    String renderedAgencySpecificPrivacy = getRenderedAgencySpecificPrivacy(agency);
    return new AgencyResponseDTO()
        .id(agency.getId())
        .name(agency.getName())
        .postcode(agency.getPostCode())
        .city(agency.getCity())
        .description(agency.getDescription())
        .teamAgency(agency.isTeamAgency())
        .offline(agency.isOffline())
        .tenantId(agency.getTenantId())
        .consultingType(agency.getConsultingTypeId())
        .agencySpecificPrivacy(renderedAgencySpecificPrivacy)
        .topicIds(agency.getAgencyTopics().stream().map(AgencyTopic::getTopicId).toList())
        .agencyLogo(agency.getAgencyLogo());
  }

  protected String getRenderedAgencySpecificPrivacy(Agency agency) {
    RestrictedTenantDTO tenantDataHoldingFeatureToggles = getTenantDataRelevantForFeatureToggles(
        agency);
    Settings settings = tenantDataHoldingFeatureToggles != null ? tenantDataHoldingFeatureToggles.getSettings() : null;
    if (settings != null && settings.getFeatureCentralDataProtectionTemplateEnabled() != null && Boolean.TRUE.equals(settings.getFeatureCentralDataProtectionTemplateEnabled())) {
      return centralDataProtectionTemplateService.renderPrivacyTemplateWithRenderedPlaceholderValues(
          agency);
    } else {
      return null;
    }
  }


  private FullAgencyResponseDTO convertToFullAgencyResponseDTO(Agency agency) {
    return new FullAgencyResponseDTO()
        .id(agency.getId())
        .name(agency.getName())
        .postcode(agency.getPostCode())
        .city(agency.getCity())
        .description(agency.getDescription())
        .teamAgency(agency.isTeamAgency())
        .offline(agency.isOffline())
        .consultingType(agency.getConsultingTypeId())
        .url(agency.getUrl())
        .external(agency.isExternal())
        .demographics(getDemographics(agency))
        .tenantId(agency.getTenantId())
        .topicIds(agency.getAgencyTopics().stream().map(AgencyTopic::getTopicId).toList())
        .agencyLogo(agency.getAgencyLogo());

  }

  private DemographicsDTO getDemographics(Agency agency) {
    return agency.hasAnyDemographicsAttributes() ? demographicsConverter.convertToDTO(agency)
        : null;
  }

  /**
   * Sets the {@link Agency} with given id to offline.
   *
   * @param agencyId the id for the agency to set offline
   */
  public void setAgencyOffline(Long agencyId) {
    var agency = this.agencyRepository.findById(agencyId)
        .orElseThrow(NotFoundException::new);
    agency.setOffline(true);
    agency.setUpdateDate(LocalDateTime.now(ZoneOffset.UTC));
    this.agencyRepository.save(agency);
  }

}
