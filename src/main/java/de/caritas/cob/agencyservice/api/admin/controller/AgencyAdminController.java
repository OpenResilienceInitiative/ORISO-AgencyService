package de.caritas.cob.agencyservice.api.admin.controller;

import de.caritas.cob.agencyservice.api.admin.hallink.RootDTOBuilder;
import de.caritas.cob.agencyservice.api.admin.service.AgencyAdminService;
import de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol.AgencyAdminControlsFacade;
import de.caritas.cob.agencyservice.api.admin.service.agency.AgencyAdminFullResponseDTOBuilder;
import de.caritas.cob.agencyservice.api.admin.service.agency.AgencyAdminSearchService;
import de.caritas.cob.agencyservice.api.admin.service.agencypostcoderange.AgencyPostcodeRangeAdminService;
import de.caritas.cob.agencyservice.api.admin.service.legal.DepartmentDataProtectionService;
import de.caritas.cob.agencyservice.api.admin.service.legal.DepartmentImprintService;
import de.caritas.cob.agencyservice.api.admin.service.legal.LegalTextAdminService;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import de.caritas.cob.agencyservice.api.admin.validation.AgencyValidator;
import de.caritas.cob.agencyservice.api.model.AgencyAdminControls;
import de.caritas.cob.agencyservice.api.model.CreateLegalTextDTO;
import de.caritas.cob.agencyservice.api.model.LegalTextAdminDTO;
import de.caritas.cob.agencyservice.api.model.LegalTextAssignmentDTO;
import de.caritas.cob.agencyservice.api.model.UpdateLegalTextDTO;
import de.caritas.cob.agencyservice.api.model.AgencyAdminFullResponseDTO;
import de.caritas.cob.agencyservice.api.model.AgencyAdminSearchResultDTO;
import de.caritas.cob.agencyservice.api.model.AgencyDTO;
import de.caritas.cob.agencyservice.api.model.AgencyPostcodeRangeResponseDTO;
import de.caritas.cob.agencyservice.api.model.AgencyTypeRequestDTO;
import de.caritas.cob.agencyservice.api.model.DepartmentDataProtectionContentDTO;
import de.caritas.cob.agencyservice.api.model.DepartmentDataProtectionDTO;
import de.caritas.cob.agencyservice.api.model.DepartmentDataProtectionResponseDTO;
import de.caritas.cob.agencyservice.api.model.DepartmentImprintContentDTO;
import de.caritas.cob.agencyservice.api.model.DepartmentImprintDTO;
import de.caritas.cob.agencyservice.api.model.DepartmentImprintResponseDTO;
import de.caritas.cob.agencyservice.api.model.PostcodeRangeDTO;
import de.caritas.cob.agencyservice.api.model.RootDTO;
import de.caritas.cob.agencyservice.api.model.Sort;
import de.caritas.cob.agencyservice.api.model.UpdateAgencyDTO;
import de.caritas.cob.agencyservice.generated.api.admin.controller.AgencyadminApi;
import io.swagger.annotations.Api;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller to handle all agency admin requests.
 */
@RestController
@Validated
@Api(tags = "admin-agency-controller")
@RequiredArgsConstructor
public class AgencyAdminController implements AgencyadminApi {

  private final @NonNull AgencyAdminSearchService agencyAdminSearchService;
  private final @NonNull AgencyPostcodeRangeAdminService agencyPostcodeRangeAdminService;
  private final @NonNull AgencyAdminService agencyAdminService;
  private final @NonNull AgencyValidator agencyValidator;
  private final @NonNull AgencyAdminControlsFacade agencyAdminControlsFacade;
  private final @NonNull DepartmentDataProtectionService departmentDataProtectionService;
  private final @NonNull DepartmentImprintService departmentImprintService;
  private final @NonNull LegalTextAdminService legalTextAdminService;

  /**
   * Creates the root hal based navigation entity.
   *
   * @return a entity containing the available navigation hal links
   */
  @Override
  public ResponseEntity<RootDTO> getRoot() {
    var rootDTO = new RootDTOBuilder().buildRootDTO();
    return new ResponseEntity<>(rootDTO, HttpStatus.OK);
  }

  /**
   * Entry point to return one agency.
   *
   * @param agencyId Agency ID (required)
   * @return {@link AgencyAdminFullResponseDTO}
   */
  @Override
  public ResponseEntity<AgencyAdminFullResponseDTO> getAgency(@PathVariable Long agencyId) {
    return ResponseEntity.ok(this.agencyAdminService.findAgency(agencyId));
  }

  /**
   * Entry point to search for agencies.
   *
   * @param page    Number of page where to start in the query (1 = first page) (required)
   * @param perPage Number of items which are being returned per page (required)
   * @param q       The query parameter to search for (optional)
   * @return an entity containing the search result
   */
  @Override
  public ResponseEntity<AgencyAdminSearchResultDTO> searchAgencies(
      Integer page, Integer perPage, String q, Sort sort) {

    var agencyAdminSearchResultDTO =
        this.agencyAdminSearchService.searchAgencies(q, page, perPage, sort);

    return new ResponseEntity<>(agencyAdminSearchResultDTO, HttpStatus.OK);
  }

  /**
   * Entry point for creating an agency.
   *
   * @param agencyDTO (required)
   * @return {@link AgencyAdminSearchService}
   */
  @Override
  @PreAuthorize("hasAuthority('AUTHORIZATION_AGENCY_ADMIN')")
  public ResponseEntity<AgencyAdminFullResponseDTO> createAgency(AgencyDTO agencyDTO) {


    agencyValidator.validate(agencyDTO);
    var agencyAdminFullResponseDTO = agencyAdminService
        .createAgency(agencyDTO);

    return new ResponseEntity<>(agencyAdminFullResponseDTO, HttpStatus.CREATED);
  }

  /**
   * Entry point to update a specific agency.
   *
   * @param agencyId        Agency Id (required)
   * @param updateAgencyDTO (required)
   * @return a {@link AgencyAdminFullResponseDTO} entity
   */
  @Override
  public ResponseEntity<AgencyAdminFullResponseDTO> updateAgency(
      @PathVariable Long agencyId, UpdateAgencyDTO updateAgencyDTO) {

    agencyValidator.validate(agencyId, updateAgencyDTO);
    var agencyAdminFullResponseDTO = agencyAdminService
        .updateAgency(agencyId, updateAgencyDTO);

    return ResponseEntity.ok(agencyAdminFullResponseDTO);
  }

  /**
   * Entry point to mark an agency as deleted.
   *
   * @param agencyId Agency Id (required)
   * @return a {@link ResponseEntity} with the status code.
   */
  @Override
  public ResponseEntity<Void> deleteAgency(@PathVariable Long agencyId) {
    this.agencyAdminService.deleteAgency(agencyId);
    return new ResponseEntity<>(HttpStatus.OK);
  }

  /**
   * Entry point to get the postcode ranges for a specific agency.
   *
   * @param agencyId Agency Id (required)
   * @return an entity containing the search result
   */
  @Override
  public ResponseEntity<AgencyPostcodeRangeResponseDTO> getAgencyPostcodeRanges(
      @PathVariable Long agencyId) {
    var postcodeRangesForAgency = this.agencyPostcodeRangeAdminService
        .findPostcodeRangesForAgency(agencyId);
    return ResponseEntity.ok(postcodeRangesForAgency);
  }

  /**
   * Entry point to create a new postcode range for the given agency.
   *
   * @param agencyId         Agency Id (required)
   * @param postcodeRangeDTO {@link PostcodeRangeDTO} (required)
   * @return an entity containing the created postcode range
   */
  @Override
  public ResponseEntity<AgencyPostcodeRangeResponseDTO> createAgencyPostcodeRange(
      @PathVariable Long agencyId, PostcodeRangeDTO postcodeRangeDTO) {

    return new ResponseEntity<>(
        agencyPostcodeRangeAdminService.createPostcodeRanges(agencyId, postcodeRangeDTO),
        HttpStatus.CREATED);
  }

  /**
   * Entry point to update a postcode range for the given postcode range Id.
   *
   * @param agencyId         Postcode range Id (required)
   * @param postcodeRangeDTO {@link PostcodeRangeDTO} (required)
   * @return an entity containing the updated postcode range
   */
  @Override
  public ResponseEntity<AgencyPostcodeRangeResponseDTO> updateAgencyPostcodeRange(
      @PathVariable Long agencyId, PostcodeRangeDTO postcodeRangeDTO) {
    var rangeResponseDTO = agencyPostcodeRangeAdminService
        .updatePostcodeRange(agencyId, postcodeRangeDTO);

    return ResponseEntity.ok(rangeResponseDTO);
  }

  /**
   * Entry point to delete an agency postcode range.
   *
   * @param agencyId Postcode range id (required)
   * @return a {@link ResponseEntity} with the status code.
   */
  @Override
  public ResponseEntity<Void> deleteAgencyPostcodeRange(Long agencyId) {
    this.agencyPostcodeRangeAdminService.deleteAgencyPostcodeRange(agencyId);
    return new ResponseEntity<>(HttpStatus.OK);
  }

  /**
   * Entry point to change the tpe of an agency.
   *
   * @param agencyId             Agency Id (required)
   * @param agencyTypeRequestDTO the dto containing the flag for type change
   * @return a {@link ResponseEntity} with the status code.
   */
  @Override
  public ResponseEntity<Void> changeAgencyType(
      Long agencyId, AgencyTypeRequestDTO agencyTypeRequestDTO) {
    this.agencyAdminService.changeAgencyType(agencyId, agencyTypeRequestDTO);
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @Override
  public ResponseEntity<List<AgencyAdminFullResponseDTO>> getAgenciesByTenantId(Long tenantId) {

    var agencies = this.agencyAdminService.getAgenciesByTenantId(tenantId);
    var agenciesResponse = agencies.stream()
        .map(agency -> new AgencyAdminFullResponseDTOBuilder(agency)
            .fromAgency()).toList();

    return new ResponseEntity<>(agenciesResponse, HttpStatus.OK);
  }

  /**
   * Entry point to publish (or draft-save) a department's (Fachbereich = agency × topic) own data
   * privacy policy. Authorisation is scoped to the caller's agencies inside the service (IDOR
   * guard) so a restricted agency admin can only edit its own Fachbereiche.
   *
   * @param agencyId Agency Id (Beratungszentrum)
   * @param topicId  Topic Id (Fachbereich)
   * @param departmentDataProtectionDTO multilingual content + publish flag
   * @return the resulting {@link DepartmentDataProtectionResponseDTO} publication status
   */
  /**
   * Entry point to read a department's (Fachbereich = agency × topic) own data privacy policy, used
   * to prefill the admin editor. Same agency/tenant scoping as the publish endpoint.
   *
   * @param agencyId Agency Id (Beratungszentrum)
   * @param topicId  Topic Id (Fachbereich)
   * @return the stored {@link DepartmentDataProtectionContentDTO} content + publication status
   */
  @Override
  public ResponseEntity<DepartmentDataProtectionContentDTO> getDepartmentDataProtection(
      Long agencyId, Long topicId) {
    var view = departmentDataProtectionService.getDepartmentDataPrivacy(agencyId, topicId);
    var response =
        new DepartmentDataProtectionContentDTO()
            .content(view.content())
            .publicationStatus(
                DepartmentDataProtectionContentDTO.PublicationStatusEnum.fromValue(
                    view.publicationStatus().name()));
    return ResponseEntity.ok(response);
  }

  @Override
  public ResponseEntity<DepartmentDataProtectionResponseDTO> publishDepartmentDataProtection(
      Long agencyId, Long topicId, DepartmentDataProtectionDTO departmentDataProtectionDTO) {
    var status =
        departmentDataProtectionService.publishDepartmentDataPrivacy(
            agencyId,
            topicId,
            departmentDataProtectionDTO.getContent(),
            Boolean.TRUE.equals(departmentDataProtectionDTO.getPublish()));
    var response =
        new DepartmentDataProtectionResponseDTO()
            .publicationStatus(
                DepartmentDataProtectionResponseDTO.PublicationStatusEnum.fromValue(status.name()));
    return ResponseEntity.ok(response);
  }

  /**
   * Entry point to read a department's (Fachbereich = agency × topic) own imprint, used to
   * prefill the admin editor. Same agency/tenant scoping as the DPP endpoints (IDOR guard inside
   * the service).
   *
   * @param agencyId Agency Id (Beratungszentrum)
   * @param topicId  Topic Id (Fachbereich)
   * @return the stored {@link DepartmentImprintContentDTO} content + publication status
   */
  @Override
  public ResponseEntity<DepartmentImprintContentDTO> getDepartmentImprint(
      Long agencyId, Long topicId) {
    var view = departmentImprintService.getDepartmentImprint(agencyId, topicId);
    var response =
        new DepartmentImprintContentDTO()
            .content(view.content())
            .publicationStatus(
                DepartmentImprintContentDTO.PublicationStatusEnum.fromValue(
                    view.publicationStatus().name()));
    return ResponseEntity.ok(response);
  }

  /**
   * Entry point to publish (or draft-save) a department's (Fachbereich = agency × topic) own
   * imprint. Authorisation is scoped to the caller's agencies inside the service (IDOR guard) so a
   * restricted agency admin can only edit its own Fachbereiche.
   *
   * @param agencyId Agency Id (Beratungszentrum)
   * @param topicId  Topic Id (Fachbereich)
   * @param departmentImprintDTO multilingual content + publish flag
   * @return the resulting {@link DepartmentImprintResponseDTO} publication status
   */
  @Override
  public ResponseEntity<DepartmentImprintResponseDTO> publishDepartmentImprint(
      Long agencyId, Long topicId, DepartmentImprintDTO departmentImprintDTO) {
    var status =
        departmentImprintService.publishDepartmentImprint(
            agencyId,
            topicId,
            departmentImprintDTO.getContent(),
            Boolean.TRUE.equals(departmentImprintDTO.getPublish()));
    var response =
        new DepartmentImprintResponseDTO()
            .publicationStatus(
                DepartmentImprintResponseDTO.PublicationStatusEnum.fromValue(status.name()));
    return ResponseEntity.ok(response);
  }

  /**
   * Lists the caller tenant's shared legal texts of one kind, each with its "used by N
   * departments" count (ADR-014 legal-text library).
   */
  @Override
  public ResponseEntity<List<LegalTextAdminDTO>> getLegalTexts(String kind) {
    var views = legalTextAdminService.listLegalTexts(LegalTextKind.valueOf(kind));
    return ResponseEntity.ok(views.stream().map(this::toLegalTextAdminDto).toList());
  }

  /** Creates a shared legal text owned by the caller's tenant (ADR-014). */
  @Override
  public ResponseEntity<LegalTextAdminDTO> createLegalText(
      CreateLegalTextDTO createLegalTextDTO) {
    var view =
        legalTextAdminService.createLegalText(
            LegalTextKind.valueOf(createLegalTextDTO.getKind().getValue()),
            createLegalTextDTO.getLabel(),
            createLegalTextDTO.getContent(),
            Boolean.TRUE.equals(createLegalTextDTO.getPublish()));
    return ResponseEntity.ok(toLegalTextAdminDto(view));
  }

  /** Updates a shared legal text; the change applies to every department referencing it. */
  @Override
  public ResponseEntity<LegalTextAdminDTO> updateLegalText(
      Long legalTextId, UpdateLegalTextDTO updateLegalTextDTO) {
    var view =
        legalTextAdminService.updateLegalText(
            legalTextId,
            updateLegalTextDTO.getLabel(),
            updateLegalTextDTO.getContent(),
            // null = preserve the current publication status (see service javadoc)
            updateLegalTextDTO.getPublish());
    return ResponseEntity.ok(toLegalTextAdminDto(view));
  }

  /**
   * Assigns a shared legal text to a department's DPP or imprint slot, or clears the slot
   * (tenant-level fallback applies again). IDOR/tenant guards live in the service.
   */
  @Override
  public ResponseEntity<Void> assignDepartmentLegalText(
      Long agencyId, Long topicId, LegalTextAssignmentDTO legalTextAssignmentDTO) {
    legalTextAdminService.assignDepartmentLegalText(
        agencyId,
        topicId,
        LegalTextKind.valueOf(legalTextAssignmentDTO.getKind().getValue()),
        legalTextAssignmentDTO.getLegalTextId());
    return ResponseEntity.noContent().build();
  }

  private LegalTextAdminDTO toLegalTextAdminDto(
      de.caritas.cob.agencyservice.api.admin.service.legal.LegalTextAdminView view) {
    return new LegalTextAdminDTO()
        .id(view.id())
        .kind(LegalTextAdminDTO.KindEnum.fromValue(view.kind().name()))
        .label(view.label())
        .content(view.content())
        .publicationStatus(
            LegalTextAdminDTO.PublicationStatusEnum.fromValue(view.publicationStatus().name()))
        .usageCount(view.usageCount());
  }

  @Override
  @PreAuthorize("hasAuthority('AUTHORIZATION_GET_ALL_AGENCIES')")
  public ResponseEntity<AgencyAdminControls> getAgencyAdminControls() {
    return new ResponseEntity<>(agencyAdminControlsFacade.getAgencyAdminControls(), HttpStatus.OK);
  }

  @Override
  @PreAuthorize("hasAuthority('AUTHORIZATION_GET_ALL_AGENCIES')")
  public ResponseEntity<AgencyAdminControls> updateAgencyAdminControls(
      AgencyAdminControls agencyAdminControls) {
    return new ResponseEntity<>(
        agencyAdminControlsFacade.updateAgencyAdminControls(agencyAdminControls), HttpStatus.OK);
  }

}
