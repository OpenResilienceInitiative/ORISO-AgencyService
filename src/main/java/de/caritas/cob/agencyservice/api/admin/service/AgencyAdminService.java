package de.caritas.cob.agencyservice.api.admin.service;

import static de.caritas.cob.agencyservice.api.exception.httpresponses.HttpStatusExceptionReason.AGENCY_IS_ALREADY_DEFAULT_AGENCY;
import static de.caritas.cob.agencyservice.api.exception.httpresponses.HttpStatusExceptionReason.AGENCY_IS_ALREADY_TEAM_AGENCY;
import static de.caritas.cob.agencyservice.api.model.AgencyTypeRequestDTO.AgencyTypeEnum.TEAM_AGENCY;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.Validate.notNull;

import com.google.common.base.Joiner;
import de.caritas.cob.agencyservice.api.admin.service.agency.AgencyAdminFullResponseDTOBuilder;
import de.caritas.cob.agencyservice.api.admin.service.agency.AgencySettingsService;
import de.caritas.cob.agencyservice.api.admin.service.allocation.AgencyIdAllocationService;
import de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol.AgencyAdminControlsService;
import de.caritas.cob.agencyservice.api.admin.service.agency.AgencyTopicEnrichmentService;
import de.caritas.cob.agencyservice.api.admin.service.agency.DataProtectionConverter;
import de.caritas.cob.agencyservice.api.admin.service.agency.DemographicsConverter;
import de.caritas.cob.agencyservice.api.admin.validation.DeleteAgencyValidator;
import de.caritas.cob.agencyservice.api.exception.httpresponses.ConflictException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.model.AgencyAdminFullResponseDTO;
import de.caritas.cob.agencyservice.api.model.AgencyDTO;
import de.caritas.cob.agencyservice.api.model.AgencyTypeRequestDTO;
import de.caritas.cob.agencyservice.api.admin.service.legal.LegalContentSanitizer;
import de.caritas.cob.agencyservice.api.model.AgencyLegalContentDTO;
import de.caritas.cob.agencyservice.api.model.UpdateAgencyDTO;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyTenantUnawareRepository;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopicRepository;
import de.caritas.cob.agencyservice.api.service.AppointmentService;
import de.caritas.cob.agencyservice.api.service.AgencyService;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Service class to handle agency admin requests.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgencyAdminService {

  private final @NonNull AgencyRepository agencyRepository;

  private final @NonNull AgencyTenantUnawareRepository agencyTenantUnawareRepository;

  private final @NonNull UserAdminService userAdminService;
  private final @NonNull DeleteAgencyValidator deleteAgencyValidator;
  private final @NonNull AgencyTopicMergeService agencyTopicMergeService;
  private final @NonNull AgencyTopicRepository agencyTopicRepository;
  private final @NonNull AppointmentService appointmentService;
  private final @NonNull AgencyService agencyService;
  private final @NonNull AuthenticatedUser authenticatedUser;
  private final @NonNull DataProtectionConverter dataProtectionConverter;
  private final @NonNull AgencyAdminControlsService agencyAdminControlsService;
  private final @NonNull AgencySettingsService agencySettingsService;
  private final @NonNull AgencyIdAllocationService agencyIdAllocationService;
  private final @NonNull TransactionOperations agencyCreationTransaction;
  private final @NonNull LegalContentSanitizer legalContentSanitizer;

  @Autowired(required = false)
  private AgencyTopicEnrichmentService agencyTopicEnrichmentService;

  @Autowired(required = false)
  private DemographicsConverter demographicsConverter;


  @Value("${feature.topics.enabled}")
  private boolean featureTopicsEnabled;

  @Value("${feature.demographics.enabled}")
  private boolean featureDemographicsEnabled;

  /**
   * Returns the {@link AgencyAdminFullResponseDTO} for the provided agency ID.
   *
   * @param agencyId the agency id
   * @return the created {@link AgencyAdminFullResponseDTO}
   */
  public AgencyAdminFullResponseDTO findAgency(Long agencyId) {
    var agency = findAgencyById(agencyId);
    enrichWithAgencyTopicsIfTopicFeatureEnabled(agency);
    return buildAgencyAdminFullResponse(agency);
  }

  private AgencyAdminFullResponseDTO buildAgencyAdminFullResponse(Agency agency) {
    var response = new AgencyAdminFullResponseDTOBuilder(agency).fromAgency();
    enrichWithSettings(response, agency);
    return response;
  }

  private void enrichWithSettings(AgencyAdminFullResponseDTO response, Agency agency) {
    if (response.getEmbedded() != null) {
      var settings = agencySettingsService.toSettings(agency.getSettings());
      response.getEmbedded().setSettings(
          agencyAdminControlsService.enrichSettingsWithAgencyAdminControls(settings));
    }
  }

  private void enrichWithAgencyTopicsIfTopicFeatureEnabled(Agency agency) {
    if (featureTopicsEnabled) {
      agencyTopicEnrichmentService.enrichAgencyWithTopics(agency);
    }
  }

  /**
   * Returns the {@link Agency} for the provided agency ID.
   *
   * @param agencyId the agency ID
   * @return {@link Agency}
   */
  public Agency findAgencyById(Long agencyId) {
    return agencyRepository.findById(agencyId).orElseThrow(NotFoundException::new);
  }

  /**
   * Saves an agency to the database.
   *
   * @param agencyDTO (required)
   * @return an {@link AgencyAdminFullResponseDTO} instance
   */
  public AgencyAdminFullResponseDTO createAgency(AgencyDTO agencyDTO) {
    setDefaultCounsellingRelationsIfEmpty(agencyDTO);
    Agency agency = fromAgencyDTO(agencyDTO);
    setTenantIdOnCreate(agencyDTO, agency);

    var savedAgency = saveWithReservationGuard(agency);
    agencyService.provisionMatrixCredentials(savedAgency);
    enrichWithAgencyTopicsIfTopicFeatureEnabled(savedAgency);
    this.appointmentService.syncAgencyDataToAppointmentService(savedAgency);
    return new AgencyAdminFullResponseDTOBuilder(savedAgency)
        .fromAgency();
  }

  /**
   * TEN-INV-U2 allocation contract: an agency ID reserved by an open invite must never be handed
   * out to another agency. The ID sequence knows nothing about reservations, so the agency
   * insert and the reservation guard share one transaction: the guard inserts (and removes
   * again) a reservation row for the generated ID, letting the primary key of
   * {@code agency_id_reservation} arbitrate creation-vs-reservation races at the database level
   * instead of relying on a check-then-act sequence. A conflict rolls the agency row back —
   * no compensation logic involved. Invite consumption flows release/consume their reservation
   * before creating the agency, so they pass this guard.
   */
  private Agency saveWithReservationGuard(Agency agency) {
    return agencyCreationTransaction.execute(status -> {
      var savedAgency = agencyRepository.save(agency);
      agencyIdAllocationService.guardAssignmentAgainstOpenReservations(savedAgency.getId());
      return savedAgency;
    });
  }

  private void setTenantIdOnCreate(AgencyDTO agencyDTO, Agency agency) {
    Long effectiveTenantId = authenticatedUser.getTenantId();
    if (effectiveTenantId == null) {
      effectiveTenantId = TenantContext.getCurrentTenant();
    }

    if (effectiveTenantId != null && effectiveTenantId.equals(0L)) {
      notNull(agencyDTO.getTenantId());
      agency.setTenantId(agencyDTO.getTenantId());
    } else {
      notNull(effectiveTenantId);
      agency.setTenantId(effectiveTenantId);
    }
  }

  private void setDefaultCounsellingRelationsIfEmpty(AgencyDTO agencyDTO) {
    if (agencyDTO.getCounsellingRelations() == null || agencyDTO.getCounsellingRelations().isEmpty()) {
      agencyDTO.setCounsellingRelations(getAllPossibleCounsellingRelations());
    }
  }

  private List<AgencyDTO.CounsellingRelationsEnum> getAllPossibleCounsellingRelations() {
    return Arrays.stream(AgencyDTO.CounsellingRelationsEnum.values()).toList();

  }

  /**
   * Converts a {@link AgencyDTO} to an {@link Agency}. The attribute "offline" is always set to
   * true, because a newly created agency still has no assigned postcode ranges.
   *
   * @param agencyDTO (required)
   * @return an {@link Agency} instance
   */
  private Agency fromAgencyDTO(AgencyDTO agencyDTO) {

    var agencyBuilder = Agency.builder()
        .name(agencyDTO.getName())
        .description(agencyDTO.getDescription())
        .postCode(agencyDTO.getPostcode())
        .city(agencyDTO.getCity())
        .street(agencyDTO.getStreet())
        .houseNumber(agencyDTO.getHouseNumber())
        .floorBuilding(agencyDTO.getFloorBuilding())
        .country(agencyDTO.getCountry())
        .phone(agencyDTO.getPhone())
        .phoneSecondary(agencyDTO.getPhoneSecondary())
        .email(agencyDTO.getEmail())
        .openingHours(agencyDTO.getOpeningHours())
        .offline(true)
        .teamAgency(agencyDTO.getTeamAgency())
        .consultingTypeId(agencyDTO.getConsultingType())
        .url(agencyDTO.getUrl())
        .isExternal(agencyDTO.getExternal())
        .counsellingRelations(Joiner.on(",").join(agencyDTO.getCounsellingRelations()))
        .agencyLogo(agencyDTO.getAgencyLogo())
        .createDate(LocalDateTime.now(ZoneOffset.UTC))
        .updateDate(LocalDateTime.now(ZoneOffset.UTC));

    if (featureDemographicsEnabled && agencyDTO.getDemographics() != null) {
      demographicsConverter.convertToEntity(agencyDTO.getDemographics(), agencyBuilder);
    }
    dataProtectionConverter.convertToEntity(agencyDTO.getDataProtection(), agencyBuilder);
    var agencyToCreate = agencyBuilder.build();

    if (featureTopicsEnabled) {
      List<AgencyTopic> agencyTopics = agencyTopicMergeService.getMergedTopics(agencyToCreate,
          agencyDTO.getTopicIds());
      agencyToCreate.setAgencyTopics(agencyTopics);
    }

    convertCounsellingRelations(agencyDTO, agencyToCreate);
    return agencyToCreate;
  }

  private void convertCounsellingRelations(AgencyDTO agencyDTO, Agency agencyToCreate) {
    if (agencyDTO.getCounsellingRelations() != null && !agencyDTO.getCounsellingRelations().isEmpty()) {
      agencyToCreate.setCounsellingRelations(joinToCommaSeparatedValues(agencyDTO.getCounsellingRelations()));
    }
  }

  private void convertCounsellingRelations(UpdateAgencyDTO agencyDTO, Agency agencyToUpdate) {
    if (agencyDTO.getCounsellingRelations() != null && !agencyDTO.getCounsellingRelations().isEmpty()) {
      agencyToUpdate.setCounsellingRelations(joinToCommaSeparatedValues(agencyDTO.getCounsellingRelations()));
    }
  }

  private <T> String  joinToCommaSeparatedValues(List<T> counsellingRelationsEnums) {
    return Joiner.on(",").skipNulls().join(counsellingRelationsEnums);
  }

  /**
   * Updates an agency in the database.
   *
   * @param agencyId        the id of the agency to update
   * @param updateAgencyDTO {@link UpdateAgencyDTO}
   * @return an {@link AgencyAdminFullResponseDTO} instance
   */
  public AgencyAdminFullResponseDTO updateAgency(Long agencyId, UpdateAgencyDTO updateAgencyDTO) {
    var agency = agencyRepository.findById(agencyId).orElseThrow(NotFoundException::new);
    applySettingsUpdate(updateAgencyDTO);
    var updatedAgency = agencyRepository.save(mergeAgencies(agency, updateAgencyDTO));
    enrichWithAgencyTopicsIfTopicFeatureEnabled(updatedAgency);
    this.appointmentService.syncAgencyDataToAppointmentService(updatedAgency);
    agencyRepository.flush();
    return buildAgencyAdminFullResponse(updatedAgency);
  }

  private void applySettingsUpdate(UpdateAgencyDTO updateAgencyDTO) {
    if (updateAgencyDTO.getSettings() != null
        && updateAgencyDTO.getSettings().getAgencyAdminControls() != null) {
      agencyAdminControlsService.updateControls(
          updateAgencyDTO.getSettings().getAgencyAdminControls());
    }
  }

  private Map<String, String> legalContent(
      UpdateAgencyDTO updateAgencyDTO,
      Function<AgencyLegalContentDTO, Map<String, String>> kind) {
    var content = updateAgencyDTO.getContent();
    return content == null ? null : kind.apply(content);
  }

  /**
   * Absent means untouched — the same rule {@link AgencyTopicMergeService} applies to topics, and
   * for the same reason: an update about a phone number must not be able to wipe a legally
   * required document.
   *
   * <p>An <em>empty</em> map counts as absent, not as "clear it". The generated request model
   * initialises both maps to empty ones, so a client that sends only the imprint is
   * indistinguishable from one that sends an empty privacy policy — treating empty as a deletion
   * would hand exactly the silent-wipe defect this epic removes back to every partial update.
   * Emptying a text is therefore expressed by sending the language key with empty content
   * ({@code {"de": ""}}), which is what an emptied editor produces anyway.
   */
  private String resolveLegalTextForUpdate(String storedContent, Map<String, String> sentContent) {
    return sentContent == null || sentContent.isEmpty()
        ? storedContent
        : legalContentSanitizer.sanitizeToJson(sentContent);
  }

  private String resolveSettingsForUpdate(Agency agency, UpdateAgencyDTO updateAgencyDTO) {
    if (updateAgencyDTO.getSettings() != null) {
      return agencySettingsService.toSettingsJson(updateAgencyDTO.getSettings());
    }
    return agency.getSettings();
  }

  /**
   * Null {@code offline} on update means "leave the stored value", same as other optional
   * update fields. Treating it as required would reject partial payloads with a {@code @NotNull}
   * violation.
   */
  private boolean resolveOfflineForUpdate(Agency agency, UpdateAgencyDTO updateAgencyDTO) {
    return updateAgencyDTO.getOffline() != null
        ? updateAgencyDTO.getOffline()
        : agency.isOffline();
  }

  /**
   * Null {@code openingHours} on update means "leave the stored value", same as {@code offline}
   * and {@code dataProtection}: a partial payload that never mentions the field must not silently
   * wipe it. Clearing the opening hours is expressed by sending an empty string — the same
   * "absent keeps, empty deletes" split {@link #resolveLegalTextForUpdate} documents.
   */
  private String resolveOpeningHoursForUpdate(Agency agency, UpdateAgencyDTO updateAgencyDTO) {
    return updateAgencyDTO.getOpeningHours() != null
        ? updateAgencyDTO.getOpeningHours()
        : agency.getOpeningHours();
  }

  /**
   * Null {@code dataProtection} on update keeps the stored contacts. Calling
   * {@link DataProtectionConverter#convertToEntity} with null would nullify them.
   */
  private void applyDataProtectionUpdate(
      Agency agency, UpdateAgencyDTO updateAgencyDTO, Agency.AgencyBuilder agencyBuilder) {
    if (updateAgencyDTO.getDataProtection() != null) {
      dataProtectionConverter.convertToEntity(updateAgencyDTO.getDataProtection(), agencyBuilder);
      return;
    }
    agencyBuilder
        .dataProtectionResponsibleEntity(agency.getDataProtectionResponsibleEntity())
        .dataProtectionOfficerContactData(agency.getDataProtectionOfficerContactData())
        .dataProtectionAlternativeContactData(agency.getDataProtectionAlternativeContactData())
        .dataProtectionAgencyResponsibleContactData(
            agency.getDataProtectionAgencyResponsibleContactData());
  }

  private Agency mergeAgencies(Agency agency, UpdateAgencyDTO updateAgencyDTO) {

    var agencyBuilder = Agency.builder()
        .id(agency.getId())
        .name(updateAgencyDTO.getName())
        .description(updateAgencyDTO.getDescription())
        .postCode(updateAgencyDTO.getPostcode())
        .city(updateAgencyDTO.getCity())
        .street(updateAgencyDTO.getStreet())
        .houseNumber(updateAgencyDTO.getHouseNumber())
        .floorBuilding(updateAgencyDTO.getFloorBuilding())
        .country(updateAgencyDTO.getCountry())
        .phone(updateAgencyDTO.getPhone())
        .phoneSecondary(updateAgencyDTO.getPhoneSecondary())
        .email(updateAgencyDTO.getEmail())
        .openingHours(resolveOpeningHoursForUpdate(agency, updateAgencyDTO))
        .offline(resolveOfflineForUpdate(agency, updateAgencyDTO))
        .teamAgency(agency.isTeamAgency())
        .url(updateAgencyDTO.getUrl())
        .isExternal(updateAgencyDTO.getExternal())
        .createDate(agency.getCreateDate())
        .updateDate(LocalDateTime.now(ZoneOffset.UTC))
        .counsellingRelations(agency.getCounsellingRelations())
        .deleteDate(agency.getDeleteDate())
        .agencyLogo(updateAgencyDTO.getAgencyLogo())
        .matrixUserId(agency.getMatrixUserId())
        .matrixPassword(agency.getMatrixPassword())
        .settings(resolveSettingsForUpdate(agency, updateAgencyDTO))
        .contentDpp(
            resolveLegalTextForUpdate(
                agency.getContentDpp(), legalContent(updateAgencyDTO, AgencyLegalContentDTO::getPrivacy)))
        .contentImprint(
            resolveLegalTextForUpdate(
                agency.getContentImprint(),
                legalContent(updateAgencyDTO, AgencyLegalContentDTO::getImpressum)));

    applyDataProtectionUpdate(agency, updateAgencyDTO, agencyBuilder);

    if (nonNull(updateAgencyDTO.getConsultingType())) {
      agencyBuilder.consultingTypeId(updateAgencyDTO.getConsultingType());
    } else {
      agencyBuilder.consultingTypeId(agency.getConsultingTypeId());
    }

    if (featureDemographicsEnabled && updateAgencyDTO.getDemographics() != null) {
      demographicsConverter.convertToEntity(updateAgencyDTO.getDemographics(), agencyBuilder);
    }

    var agencyToUpdate = agencyBuilder.build();
    convertCounsellingRelations(updateAgencyDTO, agencyToUpdate);

    if (featureTopicsEnabled) {
      // Use getMergedTopicsForUpdate so that an update which does not carry topicIds (null) keeps
      // the existing links instead of wiping them; an explicitly empty list still clears them.
      var existingAgencyTopics = agencyTopicRepository.findAllByAgencyId(agency.getId());
      List<AgencyTopic> agencyTopics = agencyTopicMergeService.getMergedTopicsForUpdate(
          agencyToUpdate, existingAgencyTopics, updateAgencyDTO.getTopicIds());
      agencyToUpdate.setAgencyTopics(agencyTopics);
    } else {
      // If the Topic feature is not enabled, Hibernate use an empty PersistentBag,
      // and we have to consider this and pass it for merging.
      agencyToUpdate.setAgencyTopics(agency.getAgencyTopics());
    }

    agencyToUpdate.setTenantId(agency.getTenantId());
    return agencyToUpdate;
  }




  /**
   * Changes the type of the agency.
   *
   * @param agencyId      the agency id
   * @param agencyTypeDTO the request dto containing the agency type
   */
  public void changeAgencyType(Long agencyId, AgencyTypeRequestDTO agencyTypeDTO) {
    var agency = findAgencyById(agencyId);
    boolean isTeamAgency = TEAM_AGENCY.equals(agencyTypeDTO.getAgencyType());
    if (isTeamAgency == agency.isTeamAgency()) {
      throw new ConflictException(
          isTeamAgency ? AGENCY_IS_ALREADY_TEAM_AGENCY : AGENCY_IS_ALREADY_DEFAULT_AGENCY);
    }
    this.userAdminService
        .adaptRelatedConsultantsForChange(agencyId, agencyTypeDTO.getAgencyType().getValue());
    agency.setTeamAgency(isTeamAgency);
    this.agencyRepository.save(agency);
  }

  /**
   * Deletes the provided agency.
   *
   * @param agencyId agency ID
   */
  public void deleteAgency(Long agencyId) {
    var agency = this.findAgencyById(agencyId);
    this.deleteAgencyValidator.validate(agency);
    agency.setDeleteDate(LocalDateTime.now(ZoneOffset.UTC));
    this.agencyRepository.save(agency);
    this.appointmentService.deleteAgency(agency);
  }

  /**
   * Returns all agencies for the provided tenant ID.
   *
   * @param tenantId the provided tenantId
   */
  public List<Agency> getAgenciesByTenantId(Long tenantId) {
    return this.agencyTenantUnawareRepository.findByTenantId(tenantId);
  }
}
