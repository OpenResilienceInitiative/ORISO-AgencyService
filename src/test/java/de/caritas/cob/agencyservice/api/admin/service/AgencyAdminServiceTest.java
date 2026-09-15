package de.caritas.cob.agencyservice.api.admin.service;

import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextLevel;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import static de.caritas.cob.agencyservice.api.exception.httpresponses.HttpStatusExceptionReason.AGENCY_ID_NOT_AVAILABLE;
import static de.caritas.cob.agencyservice.api.exception.httpresponses.HttpStatusExceptionReason.AGENCY_IS_ALREADY_DEFAULT_AGENCY;
import static de.caritas.cob.agencyservice.api.exception.httpresponses.HttpStatusExceptionReason.AGENCY_IS_ALREADY_TEAM_AGENCY;
import static de.caritas.cob.agencyservice.api.model.AgencyTypeRequestDTO.AgencyTypeEnum.DEFAULT_AGENCY;
import static de.caritas.cob.agencyservice.api.model.AgencyTypeRequestDTO.AgencyTypeEnum.TEAM_AGENCY;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.AGENCY_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import de.caritas.cob.agencyservice.api.admin.service.agency.AgencySettingsService;
import de.caritas.cob.agencyservice.api.admin.service.allocation.AgencyIdAllocationService;
import de.caritas.cob.agencyservice.api.admin.service.agency.AgencyTopicEnrichmentService;
import de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol.AgencyAdminControlsService;
import de.caritas.cob.agencyservice.api.admin.service.agency.DataProtectionConverter;
import de.caritas.cob.agencyservice.api.admin.service.agency.DemographicsConverter;
import de.caritas.cob.agencyservice.api.admin.validation.DeleteAgencyValidator;
import de.caritas.cob.agencyservice.api.exception.httpresponses.ConflictException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.admin.service.legal.ConsentTextService;
import de.caritas.cob.agencyservice.api.admin.service.legal.LegalContentSanitizer;
import de.caritas.cob.agencyservice.api.admin.service.legal.LegalTextVersionService;
import de.caritas.cob.agencyservice.api.model.AgencyAdminResponseDTO;
import de.caritas.cob.agencyservice.api.model.AgencyLegalContentDTO;
import de.caritas.cob.agencyservice.api.model.AgencyTypeRequestDTO;
import de.caritas.cob.agencyservice.api.model.DataProtectionContactDTO;
import de.caritas.cob.agencyservice.api.model.DataProtectionDTO;
import de.caritas.cob.agencyservice.api.model.DemographicsDTO;
import de.caritas.cob.agencyservice.api.model.Settings;
import de.caritas.cob.agencyservice.api.model.UpdateAgencyDTO;
import de.caritas.cob.agencyservice.api.model.AgencyDTO;
import de.caritas.cob.agencyservice.api.model.Settings;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyTenantUnawareRepository;
import de.caritas.cob.agencyservice.api.repository.agency.DataProtectionResponsibleEntity;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopicRepository;
import de.caritas.cob.agencyservice.api.service.AppointmentService;
import de.caritas.cob.agencyservice.api.service.AgencyService;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import de.caritas.cob.agencyservice.api.util.JsonConverter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

@ExtendWith(MockitoExtension.class)
class AgencyAdminServiceTest {

  @InjectMocks
  AgencyAdminService agencyAdminService;

  @Mock
  AgencyRepository agencyRepository;

  @Mock
  AgencyTenantUnawareRepository agencyTenantUnawareRepository;

  @Mock
  UserAdminService userAdminService;

  @Mock
  DeleteAgencyValidator deleteAgencyValidator;

  @Mock
  AgencyTopicMergeService mergeService;

  @Mock
  LegalContentSanitizer legalContentSanitizer;

  @Mock
  LegalTextVersionService legalTextVersionService;

  @Mock
  ConsentTextService consentTextService;

  @Mock
  AgencyTopicRepository agencyTopicRepository;

  @Mock
  AgencyTopicEnrichmentService agencyTopicEnrichmentService;

  @Mock
  DemographicsConverter demographicsConverter;

  @Mock
  DataProtectionConverter dataProtectionConverter;

  @Mock
  AgencyIdAllocationService agencyIdAllocationService;

  @Mock
  TransactionOperations agencyCreationTransaction;

  @Mock
  AppointmentService appointmentService;

  @Mock
  AgencyService agencyService;

  @Mock
  private Logger logger;

  @Mock
  AuthenticatedUser authenticatedUser;

  @Mock
  AgencyAdminControlsService agencyAdminControlsService;

  @Mock
  AgencySettingsService agencySettingsService;

  @Captor
  private ArgumentCaptor<Agency> agencyArgumentCaptor;

  private EasyRandom easyRandom;

  @BeforeEach
  public void setup() {
    // Mockito's @InjectMocks cannot decide between the two mocks for the
    // AgencyRepository-typed constructor parameter (AgencyTenantUnawareRepository is a subtype),
    // so it can silently wire the tenant-unaware mock into the tenant-aware field and the stubs
    // then no-op. Pin both fields explicitly.
    ReflectionTestUtils.setField(agencyAdminService, "agencyRepository", agencyRepository);
    ReflectionTestUtils.setField(
        agencyAdminService, "agencyTenantUnawareRepository", agencyTenantUnawareRepository);
    ReflectionTestUtils.setField(agencyAdminService, "agencyTopicEnrichmentService", agencyTopicEnrichmentService);
    ReflectionTestUtils.setField(agencyAdminService, "demographicsConverter", demographicsConverter);

    // the creation transaction is a pass-through in unit tests: execute the callback directly
    Mockito.lenient().when(agencyCreationTransaction.execute(any()))
        .thenAnswer(invocation ->
            invocation.getArgument(0, TransactionCallback.class).doInTransaction(null));

    Mockito.lenient().when(agencySettingsService.toSettings(any())).thenReturn(new Settings());
    Mockito.lenient().when(agencySettingsService.toSettingsJson(any())).thenReturn("{}");

    // Default: a tenant-scoped admin. Tests covering the Platform Admin path
    // (see findAgencyById_Should_useTenantUnawareRepository_*) override this
    // to null or 0L explicitly (#265).
    Mockito.lenient().when(authenticatedUser.getTenantId()).thenReturn(1L);
    Mockito.lenient().when(agencyAdminControlsService.enrichSettingsWithAgencyAdminControls(any()))
        .thenAnswer(invocation -> invocation.getArgument(0) != null
            ? invocation.getArgument(0)
            : new Settings());

    this.easyRandom = new EasyRandom();
  }

  @Test
  void updateAgency_Should_ThrowNotFoundException_WhenAgencyIsNotFound() {
    when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.empty());

    var updateAgencyDTO = this.easyRandom.nextObject(UpdateAgencyDTO.class);

    assertThrows(NotFoundException.class,
        () -> agencyAdminService.updateAgency(AGENCY_ID, updateAgencyDTO));
  }

  @Test
  void createAgency_Should_CreateAgencyAndAddDefaultCounsellingRelations() {
    // given
    var agency = this.easyRandom.nextObject(Agency.class);
    agency.setCounsellingRelations(null);
    agency.setDataProtectionOfficerContactData(null);
    clearDataProtection(agency);
    var agencyDTO = this.easyRandom.nextObject(AgencyDTO.class);
    agencyDTO.setCounsellingRelations(null);
    agencyDTO.setConsultingType(1);
    agencyDTO.setDataProtection(new DataProtectionDTO());

    when(agencyRepository.save(any())).thenReturn(agency);
    // when
    agencyAdminService.createAgency(agencyDTO);
    // then
    verify(agencyRepository).save(agencyArgumentCaptor.capture());
    assertThat(agencyArgumentCaptor.getValue().getCounsellingRelations(),
        is("RELATIVE_COUNSELLING,SELF_COUNSELLING,PARENTAL_COUNSELLING"));
    verify(dataProtectionConverter).convertToEntity(Mockito.any(DataProtectionDTO.class),
        Mockito.any(Agency.AgencyBuilder.class));
  }

  @Test
  void createAgency_Should_ThrowConflictBeforeProvisioning_When_GeneratedIdIsReservedByOpenInvite() {
    // given: the sequence hands out an ID that an open invite has reserved (TEN-INV-U2 —
    // assigned or reserved IDs must never be re-issued). The DB-level guard runs inside the
    // creation transaction and surfaces the collision as a conflict, rolling the insert back.
    var agency = this.easyRandom.nextObject(Agency.class);
    agency.setCounsellingRelations(null);
    agency.setDataProtectionOfficerContactData(null);
    clearDataProtection(agency);
    var agencyDTO = this.easyRandom.nextObject(AgencyDTO.class);
    agencyDTO.setConsultingType(1);
    agencyDTO.setDataProtection(new DataProtectionDTO());

    when(agencyRepository.save(any())).thenReturn(agency);
    Mockito.doThrow(new ConflictException(AGENCY_ID_NOT_AVAILABLE))
        .when(agencyIdAllocationService).guardAssignmentAgainstOpenReservations(agency.getId());

    // when, then
    assertThrows(ConflictException.class, () -> agencyAdminService.createAgency(agencyDTO));
    verify(agencyService, Mockito.never()).provisionMatrixCredentials(any(Agency.class));
    verify(appointmentService, Mockito.never()).syncAgencyDataToAppointmentService(any());
  }

  @Test
  void createAgency_Should_GuardGeneratedIdInsideCreationTransaction() {
    // given
    var agency = this.easyRandom.nextObject(Agency.class);
    agency.setCounsellingRelations(null);
    agency.setDataProtectionOfficerContactData(null);
    clearDataProtection(agency);
    var agencyDTO = this.easyRandom.nextObject(AgencyDTO.class);
    agencyDTO.setConsultingType(1);
    agencyDTO.setDataProtection(new DataProtectionDTO());

    when(agencyRepository.save(any())).thenReturn(agency);

    // when
    agencyAdminService.createAgency(agencyDTO);

    // then: save and guard both ran through the shared creation transaction
    verify(agencyCreationTransaction).execute(any());
    verify(agencyIdAllocationService).guardAssignmentAgainstOpenReservations(agency.getId());
  }

  @Test
  void updateAgency_Should_SaveAgencyMandatoryChanges_WhenAgencyIsFound() {
    var agency = this.easyRandom.nextObject(Agency.class);
    clearDataProtection(agency);
    DataProtectionContactDTO dataProtectionContactDTO = this.easyRandom.nextObject(DataProtectionContactDTO.class);
    agency.setDataProtectionOfficerContactData(JsonConverter.convertToJson(dataProtectionContactDTO));
    agency.setDataProtectionAlternativeContactData(null);
    agency.setDataProtectionResponsibleEntity(DataProtectionResponsibleEntity.DATA_PROTECTION_OFFICER);

    agency.setCounsellingRelations(null);
    when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.of(agency));
    when(agencyRepository.save(any())).thenReturn(agency);

    var updateAgencyDTO = this.easyRandom.nextObject(UpdateAgencyDTO.class);
    updateAgencyDTO.setConsultingType(null);
    agencyAdminService.updateAgency(AGENCY_ID, updateAgencyDTO);

    verify(agencyRepository).save(agencyArgumentCaptor.capture());
    var passedConsultingTypeId = agencyArgumentCaptor.getValue().getConsultingTypeId();
    assertEquals(agency.getConsultingTypeId(), passedConsultingTypeId);
  }

  @Test
  void updateAgency_Should_KeepStoredDataProtectionAndOffline_WhenPayloadOmitsThem() {
    var agency = this.easyRandom.nextObject(Agency.class);
    agency.setCounsellingRelations(null);
    agency.setOffline(true);
    agency.setDataProtectionResponsibleEntity(DataProtectionResponsibleEntity.DATA_PROTECTION_OFFICER);
    DataProtectionContactDTO dataProtectionContactDTO =
        this.easyRandom.nextObject(DataProtectionContactDTO.class);
    agency.setDataProtectionOfficerContactData(JsonConverter.convertToJson(dataProtectionContactDTO));
    agency.setDataProtectionAlternativeContactData(null);
    agency.setDataProtectionAgencyResponsibleContactData(null);
    when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.of(agency));
    when(agencyRepository.save(any())).thenReturn(agency);

    var updateAgencyDTO = this.easyRandom.nextObject(UpdateAgencyDTO.class);
    updateAgencyDTO.setConsultingType(null);
    updateAgencyDTO.setDataProtection(null);
    updateAgencyDTO.setOffline(null);

    agencyAdminService.updateAgency(AGENCY_ID, updateAgencyDTO);

    verify(dataProtectionConverter, never()).convertToEntity(any(), any());
    verify(agencyRepository).save(agencyArgumentCaptor.capture());
    var saved = agencyArgumentCaptor.getValue();
    assertEquals(Boolean.TRUE, saved.isOffline());
    assertEquals(
        DataProtectionResponsibleEntity.DATA_PROTECTION_OFFICER,
        saved.getDataProtectionResponsibleEntity());
    assertEquals(
        agency.getDataProtectionOfficerContactData(), saved.getDataProtectionOfficerContactData());
  }

  @Test
  void updateAgency_Should_KeepStoredOpeningHours_WhenPayloadOmitsThem() {
    var agency = this.easyRandom.nextObject(Agency.class);
    agency.setCounsellingRelations(null);
    agency.setOpeningHours("Mo-Fr 9-17 Uhr");
    clearDataProtection(agency);
    when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.of(agency));
    when(agencyRepository.save(any())).thenReturn(agency);

    var updateAgencyDTO = this.easyRandom.nextObject(UpdateAgencyDTO.class);
    updateAgencyDTO.setConsultingType(null);
    updateAgencyDTO.setDataProtection(null);
    updateAgencyDTO.setOpeningHours(null);

    agencyAdminService.updateAgency(AGENCY_ID, updateAgencyDTO);

    verify(agencyRepository).save(agencyArgumentCaptor.capture());
    assertEquals("Mo-Fr 9-17 Uhr", agencyArgumentCaptor.getValue().getOpeningHours());
  }

  @Test
  void createAgency_Should_StoreCoordinates_WhenProvided() {
    var agency = this.easyRandom.nextObject(Agency.class);
    agency.setCounsellingRelations(null);
    clearDataProtection(agency);
    var agencyDTO = this.easyRandom.nextObject(AgencyDTO.class);
    agencyDTO.setCounsellingRelations(null);
    agencyDTO.setConsultingType(1);
    agencyDTO.setDataProtection(new DataProtectionDTO());
    agencyDTO.setLat(52.520008);
    agencyDTO.setLng(13.404954);
    when(agencyRepository.save(any())).thenReturn(agency);

    agencyAdminService.createAgency(agencyDTO);

    verify(agencyRepository).save(agencyArgumentCaptor.capture());
    assertEquals(52.520008, agencyArgumentCaptor.getValue().getLat());
    assertEquals(13.404954, agencyArgumentCaptor.getValue().getLng());
  }

  @Test
  void updateAgency_Should_KeepStoredCoordinates_WhenPayloadOmitsThem() {
    // Same "absent keeps" contract as openingHours: a partial payload must not
    // silently drop an agency off the map (#278).
    var agency = this.easyRandom.nextObject(Agency.class);
    agency.setCounsellingRelations(null);
    agency.setLat(52.520008);
    agency.setLng(13.404954);
    clearDataProtection(agency);
    when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.of(agency));
    when(agencyRepository.save(any())).thenReturn(agency);

    var updateAgencyDTO = this.easyRandom.nextObject(UpdateAgencyDTO.class);
    updateAgencyDTO.setConsultingType(null);
    updateAgencyDTO.setDataProtection(null);
    updateAgencyDTO.setLat(null);
    updateAgencyDTO.setLng(null);

    agencyAdminService.updateAgency(AGENCY_ID, updateAgencyDTO);

    verify(agencyRepository).save(agencyArgumentCaptor.capture());
    assertEquals(52.520008, agencyArgumentCaptor.getValue().getLat());
    assertEquals(13.404954, agencyArgumentCaptor.getValue().getLng());
  }

  @Test
  void updateAgency_Should_UpdateCoordinates_WhenPayloadSendsThem() {
    var agency = this.easyRandom.nextObject(Agency.class);
    agency.setCounsellingRelations(null);
    agency.setLat(52.520008);
    agency.setLng(13.404954);
    clearDataProtection(agency);
    when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.of(agency));
    when(agencyRepository.save(any())).thenReturn(agency);

    var updateAgencyDTO = this.easyRandom.nextObject(UpdateAgencyDTO.class);
    updateAgencyDTO.setConsultingType(null);
    updateAgencyDTO.setDataProtection(null);
    updateAgencyDTO.setLat(48.135125);
    updateAgencyDTO.setLng(11.581981);

    agencyAdminService.updateAgency(AGENCY_ID, updateAgencyDTO);

    verify(agencyRepository).save(agencyArgumentCaptor.capture());
    assertEquals(48.135125, agencyArgumentCaptor.getValue().getLat());
    assertEquals(11.581981, agencyArgumentCaptor.getValue().getLng());
  }

  @Test
  void updateAgency_Should_ClearOpeningHours_WhenPayloadSendsEmptyString() {
    // Absent keeps, empty deletes — without this the field could never be cleared.
    var agency = this.easyRandom.nextObject(Agency.class);
    agency.setCounsellingRelations(null);
    agency.setOpeningHours("Mo-Fr 9-17 Uhr");
    clearDataProtection(agency);
    when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.of(agency));
    when(agencyRepository.save(any())).thenReturn(agency);

    var updateAgencyDTO = this.easyRandom.nextObject(UpdateAgencyDTO.class);
    updateAgencyDTO.setConsultingType(null);
    updateAgencyDTO.setDataProtection(null);
    updateAgencyDTO.setOpeningHours("");

    agencyAdminService.updateAgency(AGENCY_ID, updateAgencyDTO);

    verify(agencyRepository).save(agencyArgumentCaptor.capture());
    assertEquals("", agencyArgumentCaptor.getValue().getOpeningHours());
  }

  private void clearDataProtection(Agency agency) {
    agency.setDataProtectionResponsibleEntity(null);
    agency.setDataProtectionAgencyResponsibleContactData(null);
    agency.setDataProtectionAlternativeContactData(null);
    agency.setDataProtectionOfficerContactData(null);
  }

  @Test
  void updateAgency_Should_SaveOptionalAgencyChanges_WhenAgencyIsFound() {
    var agency = easyRandom.nextObject(Agency.class);
    agency.setCounsellingRelations(AgencyAdminResponseDTO.CounsellingRelationsEnum.PARENTAL_COUNSELLING.getValue());
    agency.setDataProtectionResponsibleEntity(DataProtectionResponsibleEntity.ALTERNATIVE_REPRESENTATIVE);
    agency.setDataProtectionAlternativeContactData(JsonConverter.convertToJson(new DataProtectionContactDTO()));
    agency.setDataProtectionOfficerContactData(null);
    agency.setDataProtectionAgencyResponsibleContactData(null);
    when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.of(agency));
    when(agencyRepository.save(any())).thenReturn(agency);

    var updateAgencyDTO = easyRandom.nextObject(UpdateAgencyDTO.class);
    updateAgencyDTO
        .setCounsellingRelations(Lists.newArrayList(UpdateAgencyDTO.CounsellingRelationsEnum.PARENTAL_COUNSELLING));

    agencyAdminService.updateAgency(AGENCY_ID, updateAgencyDTO);

    verify(agencyRepository).save(agencyArgumentCaptor.capture());
    var passedConsultingTypeId = agencyArgumentCaptor.getValue().getConsultingTypeId();
    assertEquals(updateAgencyDTO.getConsultingType(), passedConsultingTypeId);
    assertEquals("PARENTAL_COUNSELLING", agencyArgumentCaptor.getValue().getCounsellingRelations());
  }

  @Test
  void updateAgency_Should_SaveAgencyChanges_WhenAgencyIsFoundAndTopicFeatureEnabled() {
    // given
    ReflectionTestUtils.setField(agencyAdminService, "featureTopicsEnabled", true);
    var agency = this.easyRandom.nextObject(Agency.class);
    clearDataProtection(agency);
    agency.setCounsellingRelations(null);
    when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.of(agency));
    when(agencyRepository.save(any())).thenReturn(agency);
    when(agencyTopicRepository.findAllByAgencyId(anyLong()))
        .thenReturn(Lists.newArrayList(AgencyTopic.builder().topicId(1L).build()));
    var updateAgencyDTO = this.easyRandom.nextObject(UpdateAgencyDTO.class);
    updateAgencyDTO.setTopicIds(Lists.newArrayList(2L));

    // when
    agencyAdminService.updateAgency(AGENCY_ID, updateAgencyDTO);

    // then
    verify(this.agencyRepository).save(any());
    verify(this.mergeService).getMergedTopicsForUpdate(Mockito.any(Agency.class), any(List.class),
        any(List.class));
    verify(this.agencyTopicEnrichmentService).enrichAgencyWithTopics(agency);
    ReflectionTestUtils.setField(agencyAdminService, "featureTopicsEnabled", false);
  }

  @Test
  void updateAgency_Should_SaveAgencyChanges_WhenAgencyIsFoundAndDemographicsFeatureIsEnabled() {
    // given
    ReflectionTestUtils.setField(agencyAdminService, "featureDemographicsEnabled", true);
    var agency = this.easyRandom.nextObject(Agency.class);
    clearDataProtection(agency);
    agency.setDataProtectionAgencyResponsibleContactData(null);
    agency.setDataProtectionResponsibleEntity(DataProtectionResponsibleEntity.AGENCY_RESPONSIBLE);
    agency.setCounsellingRelations(AgencyAdminResponseDTO.CounsellingRelationsEnum.PARENTAL_COUNSELLING.getValue());
    when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.of(agency));
    when(agencyRepository.save(any())).thenReturn(agency);
    var updateAgencyDTO = this.easyRandom.nextObject(UpdateAgencyDTO.class);

    // when
    agencyAdminService.updateAgency(AGENCY_ID, updateAgencyDTO);

    // then
    verify(this.agencyRepository).save(any());
    verify(this.demographicsConverter).convertToEntity(Mockito.any(DemographicsDTO.class),
        Mockito.any(Agency.AgencyBuilder.class));
    ReflectionTestUtils.setField(agencyAdminService, "featureDemographicsEnabled", false);
  }

  @Test
  void findAgencyById_Should_ThrowNotFoundException_WhenAgencyIsNotFound() {
    when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> agencyAdminService.findAgencyById(AGENCY_ID));
  }

  @Test
  void
      findAgencyById_Should_useTenantUnawareRepository_When_authenticatedUserHasNoBoundTenant() {
    // A Platform Admin's JWT carries no tenantId claim (#265). The tenant-aware search widens
    // for exactly this signal, so the detail lookup must match — otherwise the row is visible
    // in the overview but returns 404 on click.
    when(authenticatedUser.getTenantId()).thenReturn(null);
    Agency agency = new Agency();
    when(agencyTenantUnawareRepository.findById(AGENCY_ID)).thenReturn(Optional.of(agency));

    Agency result = agencyAdminService.findAgencyById(AGENCY_ID);

    assertThat(result, is(agency));
    verifyNoInteractions(agencyRepository);
  }

  @Test
  void
      findAgencyById_Should_useTenantUnawareRepository_When_technicalTenantSentinelIsBound() {
    // Technical/super context is represented by tenant 0 in both TenantContext and the
    // JWT claim path. The tenant-aware repository would apply the Hibernate tenantFilter
    // even for tenant 0 through the JPQL findById, so route through the unaware repo.
    when(authenticatedUser.getTenantId()).thenReturn(0L);
    Agency agency = new Agency();
    when(agencyTenantUnawareRepository.findById(AGENCY_ID)).thenReturn(Optional.of(agency));

    Agency result = agencyAdminService.findAgencyById(AGENCY_ID);

    assertThat(result, is(agency));
    verifyNoInteractions(agencyRepository);
  }

  @Test
  void
      findAgencyById_Should_useTenantAwareRepository_When_tenantScopedAdminIsBoundToTenant() {
    // Tenant-scoped and agency-scoped administrators must remain restricted to their
    // authorised agencies (#265 acceptance). Only Platform Admin widens.
    when(authenticatedUser.getTenantId()).thenReturn(42L);
    Agency agency = new Agency();
    when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.of(agency));

    Agency result = agencyAdminService.findAgencyById(AGENCY_ID);

    assertThat(result, is(agency));
    verifyNoInteractions(agencyTenantUnawareRepository);
  }

  @Test
  void
      findAgencyById_Should_ThrowNotFoundException_When_platformAdminAndAgencyMissing() {
    when(authenticatedUser.getTenantId()).thenReturn(null);
    when(agencyTenantUnawareRepository.findById(AGENCY_ID)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> agencyAdminService.findAgencyById(AGENCY_ID));
  }

  @Test
  void changeAgencyType_Should_throwNotFoundException_When_agencyWasNotFound() {
    when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class,
        () -> agencyAdminService.changeAgencyType(AGENCY_ID, mock(AgencyTypeRequestDTO.class)));
  }

  @Test
  void changeAgencyType_Should_throwConflictExceptionWithCorrectReason_When_agencyHasAlreadyTypeTeamAgency() {
    var agency = this.easyRandom.nextObject(Agency.class);
    agency.setTeamAgency(true);
    when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.of(agency));
    var requestDTO = new AgencyTypeRequestDTO().agencyType(TEAM_AGENCY);

    try {
      agencyAdminService.changeAgencyType(AGENCY_ID, requestDTO);
      fail("ConflictException not thrown");
    } catch (ConflictException exception) {
      assertThat(AGENCY_IS_ALREADY_TEAM_AGENCY, is(exception.getHttpStatusExceptionReason()));
    }
  }

  @Test
  void changeAgencyType_Should_throwConflictExceptionWithCorrectReason_When_agencyHasAlreadyTypeDefault() {
    var agency = this.easyRandom.nextObject(Agency.class);
    agency.setTeamAgency(false);
    when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.of(agency));
    var requestDTO = new AgencyTypeRequestDTO().agencyType(DEFAULT_AGENCY);

    try {
      agencyAdminService.changeAgencyType(AGENCY_ID, requestDTO);
      fail("ConflictException not thrown");
    } catch (ConflictException exception) {
      assertThat(AGENCY_IS_ALREADY_DEFAULT_AGENCY, is(exception.getHttpStatusExceptionReason()));
    }
  }

  @Test
  void changeAgencyType_Should_callUserAdminServiceAndSaveChangedAgency_When_agencyCanBeChanged() {
    var agency = this.easyRandom.nextObject(Agency.class);
    when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.of(agency));
    var requestDTO = new AgencyTypeRequestDTO().agencyType(DEFAULT_AGENCY);

    agencyAdminService.changeAgencyType(AGENCY_ID, requestDTO);

    verify(this.userAdminService).adaptRelatedConsultantsForChange(AGENCY_ID,
        requestDTO.getAgencyType().getValue());
    verify(this.agencyRepository).save(any());
  }

  @Test
  void updateAgency_Should_storeAgencyWideLegalTexts_SanitizedNotVerbatim() {
    var agency = this.easyRandom.nextObject(Agency.class);
    clearDataProtection(agency);
    agency.setCounsellingRelations(null);
    when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.of(agency));
    when(agencyRepository.save(any())).thenReturn(agency);
    when(legalContentSanitizer.sanitizeToJson(Map.of("de", "<p>DSE</p><script>x</script>")))
        .thenReturn("{\"de\":\"<p>DSE</p>\"}");
    when(legalContentSanitizer.sanitizeToJson(Map.of("de", "<p>Impressum</p>")))
        .thenReturn("{\"de\":\"<p>Impressum</p>\"}");

    var updateAgencyDTO = this.easyRandom.nextObject(UpdateAgencyDTO.class);
    updateAgencyDTO.setContent(
        new AgencyLegalContentDTO()
            .privacy(Map.of("de", "<p>DSE</p><script>x</script>"))
            .impressum(Map.of("de", "<p>Impressum</p>")));

    agencyAdminService.updateAgency(AGENCY_ID, updateAgencyDTO);

    verify(agencyRepository).save(agencyArgumentCaptor.capture());
    // Admin-authored HTML must take the same sanitisation path as the department texts.
    assertEquals("{\"de\":\"<p>DSE</p>\"}", agencyArgumentCaptor.getValue().getContentDpp());
    assertEquals(
        "{\"de\":\"<p>Impressum</p>\"}", agencyArgumentCaptor.getValue().getContentImprint());
  }

  @Test
  void updateAgency_Should_recordNoVersion_When_theLegalWordingDidNotChange() {
    var agency = this.easyRandom.nextObject(Agency.class);
    clearDataProtection(agency);
    agency.setCounsellingRelations(null);
    when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.of(agency));
    when(agencyRepository.save(any())).thenReturn(agency);

    var updateAgencyDTO = this.easyRandom.nextObject(UpdateAgencyDTO.class);
    updateAgencyDTO.setContent(null);

    agencyAdminService.updateAgency(AGENCY_ID, updateAgencyDTO);

    // Changeset 0031 deliberately backfills no history, so an agency that already had legal texts
    // has NO open version to deduplicate against. Without the change check, the first unrelated
    // update - a phone number, the opening hours - would snapshot the untouched old wording stamped
    // with the current time and assert the policy came into force at that moment.
    verifyNoInteractions(legalTextVersionService);
  }

  @Test
  void updateAgency_Should_recordAVersion_When_theLegalWordingChanged() {
    var agency = this.easyRandom.nextObject(Agency.class);
    clearDataProtection(agency);
    agency.setCounsellingRelations(null);
    agency.setId(AGENCY_ID);
    agency.setContentDpp("{\"de\":\"<p>alt</p>\"}");
    when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.of(agency));
    when(agencyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(legalContentSanitizer.sanitizeToJson(Map.of("de", "<p>neu</p>")))
        .thenReturn("{\"de\":\"<p>neu</p>\"}");

    var updateAgencyDTO = this.easyRandom.nextObject(UpdateAgencyDTO.class);
    updateAgencyDTO.setContent(new AgencyLegalContentDTO().privacy(Map.of("de", "<p>neu</p>")));

    agencyAdminService.updateAgency(AGENCY_ID, updateAgencyDTO);

    // The agency level has no publication status, so a save IS the publish - but only when the
    // wording actually moved.
    verify(legalTextVersionService)
        .recordPublication(
            eq(LegalTextLevel.AGENCY),
            eq(AGENCY_ID),
            eq(LegalTextKind.DPP),
            any(),
            eq("{\"de\":\"<p>neu</p>\"}"),
            any());
  }

  @Test
  void updateAgency_Should_keepStoredLegalTexts_When_updateCarriesNoContent() {
    // The defect class this epic exists to remove: an update about something else — a phone
    // number, an opening hour — must never wipe a legally required document.
    var agency = this.easyRandom.nextObject(Agency.class);
    clearDataProtection(agency);
    agency.setCounsellingRelations(null);
    agency.setContentDpp("{\"de\":\"<p>bestehende DSE</p>\"}");
    agency.setContentImprint("{\"de\":\"<p>bestehendes Impressum</p>\"}");
    when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.of(agency));
    when(agencyRepository.save(any())).thenReturn(agency);

    var updateAgencyDTO = this.easyRandom.nextObject(UpdateAgencyDTO.class);
    updateAgencyDTO.setContent(null);

    agencyAdminService.updateAgency(AGENCY_ID, updateAgencyDTO);

    verify(agencyRepository).save(agencyArgumentCaptor.capture());
    assertEquals(
        "{\"de\":\"<p>bestehende DSE</p>\"}", agencyArgumentCaptor.getValue().getContentDpp());
    assertEquals(
        "{\"de\":\"<p>bestehendes Impressum</p>\"}",
        agencyArgumentCaptor.getValue().getContentImprint());
  }

  @Test
  void updateAgency_Should_keepTheOtherText_When_onlyOneKindIsSent() {
    var agency = this.easyRandom.nextObject(Agency.class);
    clearDataProtection(agency);
    agency.setCounsellingRelations(null);
    agency.setContentImprint("{\"de\":\"<p>bestehendes Impressum</p>\"}");
    when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.of(agency));
    when(agencyRepository.save(any())).thenReturn(agency);
    when(legalContentSanitizer.sanitizeToJson(Map.of("de", "<p>neue DSE</p>")))
        .thenReturn("{\"de\":\"<p>neue DSE</p>\"}");

    var updateAgencyDTO = this.easyRandom.nextObject(UpdateAgencyDTO.class);
    updateAgencyDTO.setContent(
        new AgencyLegalContentDTO().privacy(Map.of("de", "<p>neue DSE</p>")));

    agencyAdminService.updateAgency(AGENCY_ID, updateAgencyDTO);

    verify(agencyRepository).save(agencyArgumentCaptor.capture());
    assertEquals("{\"de\":\"<p>neue DSE</p>\"}", agencyArgumentCaptor.getValue().getContentDpp());
    assertEquals(
        "{\"de\":\"<p>bestehendes Impressum</p>\"}",
        agencyArgumentCaptor.getValue().getContentImprint());
  }

  @Test
  void updateAgency_Should_keepStoredLegalText_When_anEmptyMapIsSent() {
    // The generated request model initialises both maps to empty ones, so "I sent no privacy
    // policy" and "I sent an empty privacy policy" arrive identically. Treating that as a deletion
    // would hand the silent-wipe defect back to every partial update.
    var agency = this.easyRandom.nextObject(Agency.class);
    clearDataProtection(agency);
    agency.setCounsellingRelations(null);
    agency.setContentDpp("{\"de\":\"<p>bestehende DSE</p>\"}");
    when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.of(agency));
    when(agencyRepository.save(any())).thenReturn(agency);

    var updateAgencyDTO = this.easyRandom.nextObject(UpdateAgencyDTO.class);
    updateAgencyDTO.setContent(new AgencyLegalContentDTO().privacy(Map.of()));

    agencyAdminService.updateAgency(AGENCY_ID, updateAgencyDTO);

    verify(agencyRepository).save(agencyArgumentCaptor.capture());
    assertEquals(
        "{\"de\":\"<p>bestehende DSE</p>\"}", agencyArgumentCaptor.getValue().getContentDpp());
  }

  @Test
  void updateAgency_Should_emptyLegalText_When_theLanguageKeyCarriesEmptyContent() {
    // Deliberate removal stays possible — it just has to name the language, which is exactly what
    // an emptied editor sends.
    var agency = this.easyRandom.nextObject(Agency.class);
    clearDataProtection(agency);
    agency.setCounsellingRelations(null);
    agency.setContentDpp("{\"de\":\"<p>bestehende DSE</p>\"}");
    when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.of(agency));
    when(agencyRepository.save(any())).thenReturn(agency);
    when(legalContentSanitizer.sanitizeToJson(Map.of("de", ""))).thenReturn("{\"de\":\"\"}");

    var updateAgencyDTO = this.easyRandom.nextObject(UpdateAgencyDTO.class);
    updateAgencyDTO.setContent(new AgencyLegalContentDTO().privacy(Map.of("de", "")));

    agencyAdminService.updateAgency(AGENCY_ID, updateAgencyDTO);

    verify(agencyRepository).save(agencyArgumentCaptor.capture());
    assertEquals("{\"de\":\"\"}", agencyArgumentCaptor.getValue().getContentDpp());
  }

  @Test
  void deleteAgency_Should_ThrowNotFoundException_WhenAgencyIsNotFound() {
    when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> agencyAdminService.deleteAgency(AGENCY_ID));
  }

  @Test
  void deleteAgency_Should_callDeleteAgencyValidatorAndSaveChangedAgency_When_AgencyIsFound() {
    var agency = this.easyRandom.nextObject(Agency.class);
    when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.of(agency));

    agencyAdminService.deleteAgency(AGENCY_ID);

    verify(this.deleteAgencyValidator).validate(agency);
    verify(this.agencyRepository).save(any());
  }

}
