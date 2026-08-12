package de.caritas.cob.agencyservice.api.service;

import static de.caritas.cob.agencyservice.testHelper.TestConstants.AGENCY_ID;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.AGENCY_IDS_LIST;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.AGENCY_LIST;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.AGENCY_LIST_WITH_TOPICS;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.AGENCY_OFFLINE;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.AGENCY_ONLINE_U25;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.AGENCY_RESPONSE_DTO;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.AGENCY_SUCHT;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.CONSULTING_TYPE_EMIGRATION;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.CONSULTING_TYPE_SETTINGS_EMIGRATION;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.CONSULTING_TYPE_SETTINGS_WITHOUT_WHITESPOT_AGENCY;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.CONSULTING_TYPE_SETTINGS_WITH_WHITESPOT_AGENCY;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.CONSULTING_TYPE_SUCHT;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.EMPTY_AGENCY_LIST;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.FIELD_AGENCY_ID;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.POSTCODE;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.TOPIC_ID_LIST;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.VALID_POSTCODE;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.VALID_POSTCODE_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.everyItem;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.admin.service.agency.AgencySettingsService;
import de.caritas.cob.agencyservice.api.admin.service.agency.DemographicsConverter;
import de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol.AgencyAdminControlsService;
import de.caritas.cob.agencyservice.api.exception.MissingConsultingTypeException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.BadRequestException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.InternalServerErrorException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.manager.consultingtype.ConsultingTypeManager;
import de.caritas.cob.agencyservice.api.model.AgencyAdminAllowedPermissionToggles;
import de.caritas.cob.agencyservice.api.model.AgencyAdminControls;
import de.caritas.cob.agencyservice.api.model.AgencyResponseDTO;
import de.caritas.cob.agencyservice.api.model.FullAgencyResponseDTO;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic;
import de.caritas.cob.agencyservice.api.repository.agencytopic.PublicationStatus;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalText;
import de.caritas.cob.agencyservice.api.repository.legaltext.LegalTextKind;
import de.caritas.cob.agencyservice.api.service.matrix.AgencyMatrixPasswordCipher;
import de.caritas.cob.agencyservice.api.service.matrix.MatrixProvisioningService;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.consultingtypeservice.generated.web.model.RegistrationDTO;
import de.caritas.cob.agencyservice.consultingtypeservice.generated.web.model.ExtendedConsultingTypeResponseDTO;
import de.caritas.cob.agencyservice.consultingtypeservice.generated.web.model.RegistrationDTO;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.Settings;
import io.swagger.models.auth.In;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.swing.text.html.Option;
import org.hamcrest.collection.IsEmptyCollection;
import org.jeasy.random.EasyRandom;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class AgencyServiceTest {

  private static final Integer AGE = null;
  private static final String GENDER = null;

  private static final String COUNSELLING_RELATION = null;

  @InjectMocks
  private AgencyService agencyService;

  @Mock
  ConsultingTypeManager consultingTypeManager;

  @Mock
  TenantService tenantService;

  @Mock
  DemographicsConverter demographicsConverter;

  @Mock
  private AgencyRepository agencyRepository;

  @Mock
  CentralDataProtectionTemplateService centralDataProtectionTemplateService;

  @Mock
  AgencySettingsService agencySettingsService;

  @Mock
  AgencyAdminControlsService agencyAdminControlsService;

  @org.mockito.Spy
  private final de.caritas.cob.agencyservice.api.converter.AgencyEffectivePermissionSettingsApplier
      effectivePermissionSettingsApplier =
          new de.caritas.cob.agencyservice.api.converter.AgencyEffectivePermissionSettingsApplier();

  @Mock
  ApplicationSettingsService applicationSettingsService;

  @Mock
  private MatrixProvisioningService matrixProvisioningService;

  @Mock
  private AgencyMatrixPasswordCipher matrixPasswordCipher;

  private static final Long TENANT_ID = null;

  @org.junit.Before
  public void setUp() {
    ensureAgencyTopics(AGENCY_SUCHT);
    ensureAgencyTopics(AGENCY_ONLINE_U25);
    ensureAgencyTopics(AGENCY_OFFLINE);
    when(agencySettingsService.toSettings(any())).thenReturn(new de.caritas.cob.agencyservice.api.model.Settings());
  }

  private void ensureAgencyTopics(Agency agency) {
    if (agency.getAgencyTopics() == null) {
      ReflectionTestUtils.setField(agency, "agencyTopics", Collections.emptyList());
    }
  }

  @After
  public void tearDown() {
    TenantContext.clear();
    ReflectionTestUtils.setField(agencyService, "topicsFeatureEnabled", false);
    ReflectionTestUtils.setField(agencyService, "multitenancy", false);
  }

  @Test
  public void getListOfAgencies_Should_ReturnServiceExceptionAndLogDatabaseError_OnDatabaseErrorFindByPostCodeAndConsultingType()
      throws MissingConsultingTypeException {

    DataAccessException dbEx = new DataAccessException("db error") {
    };

    when(consultingTypeManager.getConsultingTypeSettings(Mockito.anyInt()))
        .thenReturn(CONSULTING_TYPE_SETTINGS_WITH_WHITESPOT_AGENCY);
    when(agencyRepository.searchWithoutTopic(VALID_POSTCODE, VALID_POSTCODE_LENGTH,
        CONSULTING_TYPE_SUCHT, AGE, GENDER, COUNSELLING_RELATION, TENANT_ID)).thenThrow(dbEx);

    callGetAgencies();
  }

  private void callGetAgencies() {
    Optional<Integer> emptyTopicIds = Optional.empty();
    try {
      agencyService.getAgencies(Optional.of(VALID_POSTCODE), CONSULTING_TYPE_SUCHT, emptyTopicIds);
      fail("Expected exception: ServiceException");
    } catch (InternalServerErrorException internalServerErrorException) {
      assertTrue("Excepted ServiceException thrown", true);
    }
  }

  @Test
  public void getListOfAgencies_Should_ReturnServiceException_OnInvalidWhiteSpotAgencyId()
      throws MissingConsultingTypeException {

    NumberFormatException nfEx = new NumberFormatException();

    when(agencyRepository.searchWithoutTopic(VALID_POSTCODE, VALID_POSTCODE_LENGTH,
        CONSULTING_TYPE_SUCHT, AGE, GENDER, COUNSELLING_RELATION, TENANT_ID)).thenReturn(EMPTY_AGENCY_LIST);
    when(consultingTypeManager.getConsultingTypeSettings(Mockito.anyInt()))
        .thenReturn(CONSULTING_TYPE_SETTINGS_WITH_WHITESPOT_AGENCY);
    when(agencyRepository.findByIdAndDeleteDateNull(Mockito.anyLong())).thenThrow(nfEx);

    callGetAgencies();
  }

  @Test
  public void getListOfAgencies_Should_ReturnServiceException_OnDatabaseErrorFindByIdAndDeleteDateNull()
      throws MissingConsultingTypeException {

    when(agencyRepository.searchWithoutTopic(VALID_POSTCODE, VALID_POSTCODE_LENGTH,
        CONSULTING_TYPE_SUCHT, AGE, GENDER, COUNSELLING_RELATION, TENANT_ID)).thenReturn(EMPTY_AGENCY_LIST);
    when(consultingTypeManager.getConsultingTypeSettings(Mockito.anyInt()))
        .thenReturn(CONSULTING_TYPE_SETTINGS_WITH_WHITESPOT_AGENCY);

    when(agencyRepository.findByIdAndDeleteDateNull(Mockito.anyLong()))
        .thenThrow(new NumberFormatException(""));

    callGetAgencies();
  }

  @Test
  public void getListOfAgencies_Should_ReturnListOfFullAgencyResponseDTO_WhenDBSelectIsSuccessfull()
      throws MissingConsultingTypeException {

    when(agencyRepository.searchWithoutTopic(VALID_POSTCODE, VALID_POSTCODE_LENGTH,
        CONSULTING_TYPE_SUCHT, AGE, GENDER, COUNSELLING_RELATION, TENANT_ID)).thenReturn(AGENCY_LIST);
    when(consultingTypeManager.getConsultingTypeSettings(Mockito.anyInt()))
        .thenReturn(CONSULTING_TYPE_SETTINGS_WITH_WHITESPOT_AGENCY);

    assertThat(agencyService.getAgencies(Optional.of(VALID_POSTCODE), CONSULTING_TYPE_SUCHT, Optional.empty()),
        everyItem(instanceOf(FullAgencyResponseDTO.class)));
    assertThat(agencyService.getAgencies(Optional.of(VALID_POSTCODE), CONSULTING_TYPE_SUCHT, Optional.empty()))
        .extracting(POSTCODE).contains(POSTCODE);
  }

  @Test
  public void getListOfAgencies_Should_MapDepartmentsWithLegalPublicationFlags()
      throws MissingConsultingTypeException {

    // one department with both legal texts published, one still fully in draft
    Agency agency = Agency.builder().id(100L).name("Zentrum").consultingTypeId(CONSULTING_TYPE_SUCHT)
        .build();
    AgencyTopic publishedDepartment = AgencyTopic.builder().agency(agency).topicId(1L)
        .contentDpp("{\"de\":\"<p>DSE</p>\"}").publicationStatus(PublicationStatus.PUBLISHED)
        .contentImprint("{\"de\":\"<p>Impressum</p>\"}")
        .publicationStatusImprint(PublicationStatus.PUBLISHED).build();
    AgencyTopic draftDepartment = AgencyTopic.builder().agency(agency).topicId(2L)
        .contentDpp("{\"de\":\"<p>Entwurf</p>\"}").publicationStatus(PublicationStatus.DRAFT)
        .build();
    ReflectionTestUtils.setField(agency, "agencyTopics",
        List.of(publishedDepartment, draftDepartment));

    when(agencyRepository.searchWithoutTopic(VALID_POSTCODE, VALID_POSTCODE_LENGTH,
        CONSULTING_TYPE_SUCHT, AGE, GENDER, COUNSELLING_RELATION, TENANT_ID))
        .thenReturn(List.of(agency));
    when(consultingTypeManager.getConsultingTypeSettings(Mockito.anyInt()))
        .thenReturn(CONSULTING_TYPE_SETTINGS_WITH_WHITESPOT_AGENCY);

    var result =
        agencyService.getAgencies(Optional.of(VALID_POSTCODE), CONSULTING_TYPE_SUCHT,
            Optional.empty());

    assertEquals(1, result.size());
    var departments = result.get(0).getDepartments();
    assertEquals(2, departments.size());
    var byTopicId = departments.stream()
        .collect(java.util.stream.Collectors.toMap(d -> d.getTopicId(), d -> d));
    assertTrue(byTopicId.get(1L).getHasPublishedDpp());
    assertTrue(byTopicId.get(1L).getHasPublishedImprint());
    assertEquals(Boolean.FALSE, byTopicId.get(2L).getHasPublishedDpp());
    assertEquals(Boolean.FALSE, byTopicId.get(2L).getHasPublishedImprint());
    // the existing topicIds contract is untouched
    assertEquals(List.of(1L, 2L), result.get(0).getTopicIds());
  }

  @Test
  public void getListOfAgencies_Should_MapAddressPhoneAndOpeningHours()
      throws MissingConsultingTypeException {

    Agency agency = Agency.builder().id(101L).name("Zentrum")
        .consultingTypeId(CONSULTING_TYPE_SUCHT)
        .street("Musterstraße").houseNumber("12a")
        .phone("+49301234567").openingHours("Mo-Fr 9-17 Uhr").build();
    ReflectionTestUtils.setField(agency, "agencyTopics", List.of());

    when(agencyRepository.searchWithoutTopic(VALID_POSTCODE, VALID_POSTCODE_LENGTH,
        CONSULTING_TYPE_SUCHT, AGE, GENDER, COUNSELLING_RELATION, TENANT_ID))
        .thenReturn(List.of(agency));
    when(consultingTypeManager.getConsultingTypeSettings(Mockito.anyInt()))
        .thenReturn(CONSULTING_TYPE_SETTINGS_WITH_WHITESPOT_AGENCY);

    var result = agencyService.getAgencies(Optional.of(VALID_POSTCODE),
        CONSULTING_TYPE_SUCHT, Optional.empty());

    assertEquals(1, result.size());
    assertEquals("Musterstraße", result.get(0).getStreet());
    assertEquals("12a", result.get(0).getHouseNumber());
    assertEquals("+49301234567", result.get(0).getPhone());
    assertEquals("Mo-Fr 9-17 Uhr", result.get(0).getOpeningHours());
  }

  @Test
  public void getListOfAgencies_Should_DeriveLegalPublicationFlagsFromReferencedLegalText_WhenDepartmentReferencesOne()
      throws MissingConsultingTypeException {

    // ADR-014 write-through only updates the referenced legal_text object; the inline columns are
    // intentionally kept stale. The search flags must therefore resolve reference-first, exactly
    // like DepartmentLegalService — otherwise a publish/draft-save would not show up here.
    Agency agency = Agency.builder().id(100L).name("Zentrum").consultingTypeId(CONSULTING_TYPE_SUCHT)
        .build();
    // referenced DPP is PUBLISHED while the stale inline copy says DRAFT → flag must be true
    AgencyTopic publishedViaReference = AgencyTopic.builder().agency(agency).topicId(1L)
        .contentDpp("{\"de\":\"<p>stale inline</p>\"}").publicationStatus(PublicationStatus.DRAFT)
        .dpp(LegalText.builder().id(500L).kind(LegalTextKind.DPP).label("Geteilte DSE")
            .content("{\"de\":\"<p>geteilt</p>\"}")
            .publicationStatus(PublicationStatus.PUBLISHED).build())
        .build();
    // referenced imprint is DRAFT while the stale inline copy says PUBLISHED → flag must be false
    AgencyTopic draftViaReference = AgencyTopic.builder().agency(agency).topicId(2L)
        .contentImprint("{\"de\":\"<p>stale inline</p>\"}")
        .publicationStatusImprint(PublicationStatus.PUBLISHED)
        .imprint(LegalText.builder().id(501L).kind(LegalTextKind.IMPRINT)
            .label("Geteiltes Impressum").content("{\"de\":\"<p>Entwurf</p>\"}")
            .publicationStatus(PublicationStatus.DRAFT).build())
        .build();
    ReflectionTestUtils.setField(agency, "agencyTopics",
        List.of(publishedViaReference, draftViaReference));

    when(agencyRepository.searchWithoutTopic(VALID_POSTCODE, VALID_POSTCODE_LENGTH,
        CONSULTING_TYPE_SUCHT, AGE, GENDER, COUNSELLING_RELATION, TENANT_ID))
        .thenReturn(List.of(agency));
    when(consultingTypeManager.getConsultingTypeSettings(Mockito.anyInt()))
        .thenReturn(CONSULTING_TYPE_SETTINGS_WITH_WHITESPOT_AGENCY);

    var result =
        agencyService.getAgencies(Optional.of(VALID_POSTCODE), CONSULTING_TYPE_SUCHT,
            Optional.empty());

    assertEquals(1, result.size());
    var byTopicId = result.get(0).getDepartments().stream()
        .collect(java.util.stream.Collectors.toMap(d -> d.getTopicId(), d -> d));
    assertTrue(byTopicId.get(1L).getHasPublishedDpp());
    assertEquals(Boolean.FALSE, byTopicId.get(1L).getHasPublishedImprint());
    assertEquals(Boolean.FALSE, byTopicId.get(2L).getHasPublishedDpp());
    assertEquals(Boolean.FALSE, byTopicId.get(2L).getHasPublishedImprint());
  }

  @Test
  public void getListOfAgencies_Should_ReturnWhiteSpotAgency_WhenNoAgencyFoundForGivenPostcodeAndAgencyHasSetWhiteSpotAgency()
      throws MissingConsultingTypeException {

    Optional<Agency> agency = Optional.of(AGENCY_SUCHT);

    when(agencyRepository.searchWithoutTopic(VALID_POSTCODE, VALID_POSTCODE_LENGTH,
        CONSULTING_TYPE_SUCHT, AGE, GENDER, COUNSELLING_RELATION, TENANT_ID)).thenReturn(EMPTY_AGENCY_LIST);
    when(agencyRepository.findByIdAndDeleteDateNull(Mockito.anyLong())).thenReturn(agency);
    when(consultingTypeManager.getConsultingTypeSettings(Mockito.anyInt()))
        .thenReturn(CONSULTING_TYPE_SETTINGS_WITH_WHITESPOT_AGENCY);

    assertThat(agencyService.getAgencies(Optional.of(VALID_POSTCODE), CONSULTING_TYPE_SUCHT, Optional.empty()))
        .extracting(FIELD_AGENCY_ID).contains(AGENCY_ID);
  }

  @Test
  public void getListOfAgencies_Should_ReturnEmptyList_WhenNoAgencyFoundForGivenPostcodeAndAgencyHasNotSetWhiteSpotAgency()
      throws MissingConsultingTypeException {

    when(agencyRepository.searchWithoutTopic(VALID_POSTCODE, VALID_POSTCODE_LENGTH,
        CONSULTING_TYPE_SUCHT, AGE, GENDER, COUNSELLING_RELATION, TENANT_ID)).thenReturn(new ArrayList<>());
    when(consultingTypeManager.getConsultingTypeSettings(Mockito.anyInt()))
        .thenReturn(CONSULTING_TYPE_SETTINGS_WITHOUT_WHITESPOT_AGENCY);

    assertThat(agencyService.getAgencies(Optional.of(VALID_POSTCODE), CONSULTING_TYPE_SUCHT, Optional.empty()),
        IsEmptyCollection.empty());
  }

  @Test
  public void getListOfAgencies_Should_ReturnEmptyList_When_PostcodeSizeIsSmallerThanMinSettingsValue()
      throws MissingConsultingTypeException {

    when(consultingTypeManager.getConsultingTypeSettings(Mockito.anyInt()))
        .thenReturn(CONSULTING_TYPE_SETTINGS_EMIGRATION);

    assertThat(agencyService.getAgencies(Optional.of(VALID_POSTCODE), CONSULTING_TYPE_EMIGRATION, Optional.empty()),
        IsEmptyCollection.empty());
  }

  @Test
  public void getAgencies_With_Ids_Should_ReturnListOfAgencyResponseDTO_When_DBSelectIsSuccessfull() {

    when(agencyRepository.findByIdIn(AGENCY_IDS_LIST))
        .thenReturn(AGENCY_LIST);

    AgencyResponseDTO result = agencyService.getAgencies(AGENCY_IDS_LIST).get(0);

    assertEquals(AGENCY_RESPONSE_DTO.getPostcode(), result.getPostcode());
    assertEquals(AGENCY_RESPONSE_DTO.getCity(), result.getCity());
    assertEquals(AGENCY_RESPONSE_DTO.getDescription(), result.getDescription());
    assertEquals(AGENCY_RESPONSE_DTO.getName(), result.getName());
    assertEquals(AGENCY_RESPONSE_DTO.getId(), result.getId());
    assertEquals(AGENCY_RESPONSE_DTO.getTeamAgency(), result.getTeamAgency());
    assertEquals(AGENCY_RESPONSE_DTO.getOffline(), result.getOffline());
    assertEquals(AGENCY_RESPONSE_DTO.getConsultingType(), result.getConsultingType());
    assertThat(agencyService.getAgencies(Collections.singletonList(AGENCY_ID)),
        everyItem(instanceOf(AgencyResponseDTO.class)));
  }

  @Test
  public void getAgencies_With_Ids_Should_ForceFeatureOff_When_PlatformDisallowsIt() {
    when(agencyRepository.findByIdIn(AGENCY_IDS_LIST)).thenReturn(AGENCY_LIST);
    when(agencySettingsService.toSettings(any()))
        .thenReturn(new de.caritas.cob.agencyservice.api.model.Settings().featureCallsEnabled(true));
    when(agencyAdminControlsService.getControls())
        .thenReturn(
            new AgencyAdminControls()
                .allowedPermissionToggles(
                    new AgencyAdminAllowedPermissionToggles().calls(false)));

    AgencyResponseDTO result = agencyService.getAgencies(AGENCY_IDS_LIST).get(0);

    assertEquals(false, result.getSettings().getFeatureCallsEnabled());
  }

  @Test
  public void getAgencies_With_Ids_Should_NotLeakAgencyAdminControls_OnThePublicResponse() {
    when(agencyRepository.findByIdIn(AGENCY_IDS_LIST)).thenReturn(AGENCY_LIST);
    when(agencyAdminControlsService.getControls())
        .thenReturn(
            new AgencyAdminControls()
                .allowedPermissionToggles(
                    new AgencyAdminAllowedPermissionToggles().calls(false)));

    AgencyResponseDTO result = agencyService.getAgencies(AGENCY_IDS_LIST).get(0);

    // The public response must never carry the raw controls object — only the effective
    // (merged) feature flags. Guards against regressing to enrichSettingsWithAgencyAdminControls,
    // which used to attach it here.
    assertEquals(null, result.getSettings().getAgencyAdminControls());
  }

  @Test
  public void getAgencies_With_Ids_Should_ReturnCorrectOfflineFlag_When_AgencyIsOnline() {

    when(agencyRepository.findByIdIn(AGENCY_IDS_LIST))
        .thenReturn(Collections.singletonList(AGENCY_ONLINE_U25));

    AgencyResponseDTO result = agencyService.getAgencies(Collections.singletonList(AGENCY_ID))
        .get(0);

    assertEquals(false, result.getOffline());
  }

  @Test
  public void getAgencies_With_Ids_Should_ReturnCorrectOfflineFlag_When_AgencyIsOffline() {

    when(agencyRepository.findByIdIn(AGENCY_IDS_LIST))
        .thenReturn(Collections.singletonList(AGENCY_OFFLINE));

    AgencyResponseDTO result = agencyService.getAgencies(AGENCY_IDS_LIST).get(0);

    assertEquals(true, result.getOffline());
  }

  @Test
  public void getAgencies_Should_ReturnEmptyList_When_NoAgencyFoundForGivenId() {

    when(agencyRepository.findByIdIn(AGENCY_IDS_LIST))
        .thenReturn(new ArrayList<>());

    assertThat(agencyService.getAgencies(AGENCY_IDS_LIST),
        IsEmptyCollection.empty());
  }

  @Test(expected = InternalServerErrorException.class)
  public void getAgencies_Should_ThrowInternalServerError_When_MissingConsultingTypeExceptionIsThrown()
      throws MissingConsultingTypeException {

    when(consultingTypeManager.getConsultingTypeSettings(anyInt()))
        .thenThrow(new MissingConsultingTypeException(""));
    agencyService.getAgencies(Optional.of(""), 0, Optional.empty());
  }

  @Test
  public void setAgencyOffline_Should_SaveAgency() {

    EasyRandom easyRandom = new EasyRandom();
    Agency agency = easyRandom.nextObject(Agency.class);
    when(agencyRepository.findById(agency.getId())).thenReturn(Optional.of(agency));
    agencyService.setAgencyOffline(agency.getId());
    verify(agencyRepository, times(1)).save(any());
  }

  @Test(expected = NotFoundException.class)
  public void setAgencyOffline_Should_ThrowNotFoundException_WhenAgencyIsNotFound() {

    when(agencyRepository.findById(AGENCY_ID)).thenReturn(Optional.empty());
    agencyService.setAgencyOffline(AGENCY_ID);

  }

  @Test(expected = BadRequestException.class)
  public void getAgenciesByConsultingType_Should_throwBadRequestException_When_ConsultingTypeIsInvalid()
      throws MissingConsultingTypeException {
    when(consultingTypeManager.getConsultingTypeSettings(-10))
        .thenThrow(new MissingConsultingTypeException(""));
    this.agencyService.getAgencies(-10);
  }

  @Test(expected = BadRequestException.class)
  public void getAgencies_Should_throwBadRequestException_When_TopicIdNotProvidedAndFeatureEnabled()
      throws MissingConsultingTypeException {
    // given
    ReflectionTestUtils.setField(agencyService, "topicsFeatureEnabled", true);
    ExtendedConsultingTypeResponseDTO dto = new ExtendedConsultingTypeResponseDTO().registration(
        new RegistrationDTO().minPostcodeSize(5));
    when(consultingTypeManager.getConsultingTypeSettings(1)).thenReturn(dto);
    RestrictedTenantDTO restrictedTenantDTO = new RestrictedTenantDTO().settings(
        new Settings().topicsInRegistrationEnabled(true));
    when(tenantService.getRestrictedTenantDataForSingleTenant()).thenReturn(restrictedTenantDTO);

    // when
    this.agencyService.getAgencies(Optional.of("12123"), 1, Optional.empty());
  }

  @Test
  public void getAgencies_Should_searchByTopicId_When_TopicIdProvidedAndFeatureEnabled()
      throws MissingConsultingTypeException {
    // given
    ReflectionTestUtils.setField(agencyService, "topicsFeatureEnabled", true);
    ExtendedConsultingTypeResponseDTO dto = new ExtendedConsultingTypeResponseDTO().registration(
        new RegistrationDTO().minPostcodeSize(5));
    when(consultingTypeManager.getConsultingTypeSettings(1)).thenReturn(dto);
    RestrictedTenantDTO restrictedTenantDTO = new RestrictedTenantDTO().settings(
        new Settings().topicsInRegistrationEnabled(true));
    when(tenantService.getRestrictedTenantDataForSingleTenant()).thenReturn(restrictedTenantDTO);

    // when
    this.agencyService.getAgencies(Optional.of("12123"), 1, Optional.of(2));

    // then
    verify(agencyRepository).searchWithTopic("12123", 5, 1, 2, AGE, GENDER, COUNSELLING_RELATION,
        TENANT_ID);
  }

  @Test
  public void getAgenciesTopics_Should_ReturnListOfTopicIds_When_topicsExist() {

    when(agencyRepository.findAllAgenciesTopics(TenantContext.getCurrentTenant()))
        .thenReturn(TOPIC_ID_LIST);

    Integer result = agencyService.getAgenciesTopics().get(0);

    assertEquals(TOPIC_ID_LIST.get(0), result);
  }

  @Test
  public void getAgenciesTopics_Should_ReturnEmptyListOfTopicIds_When_topicsDontExist() {

    when(agencyRepository.findAllAgenciesTopics(TenantContext.getCurrentTenant()))
        .thenReturn(new ArrayList<>());

    List<Integer> result = agencyService.getAgenciesTopics();

    assertThat(result).isEmpty();
  }

  @Test
  public void getAgencies_WithOnlyPostCodeAndTopicId_Should_ReturnEmptyList_When_NoAgencyFound() {

    assertThat(agencyService.getAgencies("99999", 1),
        IsEmptyCollection.empty());
  }

  @Test
  public void getAgencies_WithOnlyPostCodeAndTopicId_Should_ReturnResultList_When_AgencyFound() {

    when(agencyRepository.searchWithTopic("99999", 5, null,
        1, null, null, null, null))
        .thenReturn(AGENCY_LIST_WITH_TOPICS);

    var result = agencyService.getAgencies("99999", 1);

    assertThat(result).isNotEmpty();
  }
}
