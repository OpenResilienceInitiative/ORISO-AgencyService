package de.caritas.cob.agencyservice.api.admin.validation;

import static de.caritas.cob.agencyservice.testHelper.TestConstants.CONSULTING_TYPE_SETTINGS_SUCHT;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.CONSULTING_TYPE_SUCHT;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.INVALID_CONSULTING_TYPE_VALUE;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.INVALID_POSTCODE;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.VALID_POSTCODE;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.AgencyServiceApplication;

import de.caritas.cob.agencyservice.api.admin.service.UserAdminService;
import de.caritas.cob.agencyservice.api.exception.MissingConsultingTypeException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.InvalidConsultingTypeException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.InvalidOfflineStatusException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.HttpStatusExceptionReason;
import de.caritas.cob.agencyservice.api.exception.httpresponses.InvalidPostcodeException;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import de.caritas.cob.agencyservice.api.manager.consultingtype.ConsultingTypeManager;
import de.caritas.cob.agencyservice.api.model.AgencyDTO;
import de.caritas.cob.agencyservice.api.model.DataProtectionContactDTO;
import de.caritas.cob.agencyservice.api.model.DataProtectionDTO;
import de.caritas.cob.agencyservice.api.model.DataProtectionDTO.DataProtectionResponsibleEntityEnum;
import de.caritas.cob.agencyservice.api.model.UpdateAgencyDTO;
import de.caritas.cob.agencyservice.api.admin.validation.validators.AgencyDataProtectionValidator;
import de.caritas.cob.agencyservice.api.service.ApplicationSettingsService;
import de.caritas.cob.agencyservice.api.service.TenantService;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.model.ApplicationSettingsDTO;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.model.SettingDTO;
import de.caritas.cob.agencyservice.consultingtypeservice.generated.web.model.ExtendedConsultingTypeResponseDTO;
import de.caritas.cob.agencyservice.consultingtypeservice.generated.web.model.WhiteSpotDTO;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.Settings;
import de.caritas.cob.agencyservice.useradminservice.generated.web.model.ConsultantAdminResponseDTO;
import org.jeasy.random.EasyRandom;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = AgencyServiceApplication.class)
@TestPropertySource(properties = "spring.profiles.active=testing")
@AutoConfigureTestDatabase(replace = Replace.ANY)
@DirtiesContext(classMode = ClassMode.BEFORE_CLASS)
// The four update-path tests validate an EXISTING agency, so the validator has to be able to load
// it. Without the seed every one of them dies in AgencyValidator.fromUpdateAgencyDto with
// "Agency with id 1 not found!" and never reaches the rule it is meant to exercise (#208).
@Sql(scripts = "/database/AgencyDatabase.sql")
public class AgencyValidatorIT {

  @Autowired
  private AgencyValidator agencyValidator;

  @MockitoBean
  private UserAdminService userAdminService;

  @MockitoBean
  private ConsultingTypeManager consultingTypeManager;

  @MockitoBean
  private AuthenticatedUser authenticatedUser;

  /**
   * The update path runs AgencyDataProtectionValidator, which asks the TenantService whether the
   * central data-protection template is enabled. Unmocked that is a live call to localhost:8089
   * and every update test dies with a 400 before reaching its own rule (same cause as #205).
   */
  @MockitoBean
  private TenantService tenantService;

  @MockitoBean
  private ApplicationSettingsService applicationSettingsService;

  @Autowired
  private AgencyDataProtectionValidator agencyDataProtectionValidator;

  @Before
  public void setupTenantSettings() {
    when(tenantService.getRestrictedTenantDataByTenantId(any()))
        .thenReturn(new RestrictedTenantDTO()
            .settings(new Settings().featureCentralDataProtectionTemplateEnabled(false)));
  }

  @Test(expected = InvalidPostcodeException.class)
  public void validate_Should_ThrowInvalidPostcodeException_WhenCreateAndAgencyPostcodeIsInvalid() {
    AgencyDTO agencyDTO = getValidAgencyDTO();
    agencyDTO.setPostcode(INVALID_POSTCODE);
    agencyValidator.validate(agencyDTO);
  }

  @Test
  public void validate_Should_NotThrowInvalidPostcodeException_WhenCreateAndAgencyPostcodeIsValid() {
    AgencyDTO agencyDTO = getValidAgencyDTO();
    agencyDTO.setPostcode(VALID_POSTCODE);
    agencyValidator.validate(agencyDTO);
  }

  @Test(expected = InvalidConsultingTypeException.class)
  public void validate_Should_ThrowInvalidConsultingTypeException_WhenCreateAndAgencyConsultingTypeIsInvalid()
      throws MissingConsultingTypeException {
    when(consultingTypeManager.getConsultingTypeSettings(anyInt())).thenThrow(new MissingConsultingTypeException(""));
    AgencyDTO agencyDTO = getValidAgencyDTO();
    agencyDTO.setConsultingType(INVALID_CONSULTING_TYPE_VALUE);
    agencyValidator.validate(agencyDTO);
  }

  @Test
  public void validate_Should_NotThrowInvalidConsultingTypeException_WhenCreateAndAgencyConsultingTypeIsValid() {
    AgencyDTO agencyDTO = getValidAgencyDTO();
    agencyDTO.setConsultingType(CONSULTING_TYPE_SUCHT);
    agencyValidator.validate(agencyDTO);
  }

  @Test(expected = InvalidPostcodeException.class)
  public void validate_Should_ThrowInvalidPostcodeException_WhenUpdateAndAgencyPostcodeIsInvalid()
      throws MissingConsultingTypeException {
    when(consultingTypeManager.getConsultingTypeSettings(0)).thenReturn(CONSULTING_TYPE_SETTINGS_SUCHT);
    UpdateAgencyDTO updateAgencyDTO = getValidUpdateAgencyDTO();
    updateAgencyDTO.setPostcode(INVALID_POSTCODE);
    agencyValidator.validate(1L, updateAgencyDTO);
  }

  @Test
  public void validate_Should_NotThrowInvalidPostcodeException_WhenUpdateAndAgencyPostcodeIsValid()
      throws MissingConsultingTypeException {
    when(consultingTypeManager.getConsultingTypeSettings(0)).thenReturn(CONSULTING_TYPE_SETTINGS_SUCHT);
    UpdateAgencyDTO updateAgencyDTO = getValidUpdateAgencyDTO();
    updateAgencyDTO.setPostcode(VALID_POSTCODE);
    agencyValidator.validate(1L, updateAgencyDTO);
  }

  @Test(expected = InvalidOfflineStatusException.class)
  public void validate_Should_ThrowInvalidOfflineStatusException_WhenUpdateAndOfflineStatusIsInvalid()
      throws MissingConsultingTypeException {
    EasyRandom easyRandom = new EasyRandom();
    UpdateAgencyDTO updateAgencyDTO = getValidUpdateAgencyDTO();
    updateAgencyDTO.setOffline(false);
    var extendedConsultingTypeResponseDTO = new ExtendedConsultingTypeResponseDTO();
    extendedConsultingTypeResponseDTO.setWhiteSpot(easyRandom.nextObject(WhiteSpotDTO.class));
    when(consultingTypeManager.getConsultingTypeSettings(19)).thenReturn(extendedConsultingTypeResponseDTO);
    agencyValidator.validate(1734L, updateAgencyDTO);
  }

  @Test
  public void validate_Should_NotThrowInvalidOfflineStatusException_WhenUpdateAndOfflineStatusIsValid()
      throws MissingConsultingTypeException {
    when(this.userAdminService.getConsultantsOfAgency(anyLong(), anyInt(), anyInt()))
        .thenReturn(singletonList(mock(ConsultantAdminResponseDTO.class)));

    when(consultingTypeManager.getConsultingTypeSettings(0)).thenReturn(CONSULTING_TYPE_SETTINGS_SUCHT);

    UpdateAgencyDTO updateAgencyDTO = getValidUpdateAgencyDTO();
    updateAgencyDTO.setOffline(false);
    agencyValidator.validate(1L, updateAgencyDTO);
  }

  /**
   * The legal contact of the selected responsible entity is mandatory wherever an agency is
   * written, not only on the screen that happens to mark the fields as required. The update path
   * has enforced this since ADR-003; creation went through {@code AgencyValidator.validate(
   * AgencyDTO)}, which never carried the dataProtection block into the validator registry at all,
   * so the very same payload stored unvalidated when it came in as a create.
   */
  @Test
  public void validate_Should_ThrowInvalidOfflineStatusException_WhenCreateAndSelectedDataProtectionContactIsIncomplete() {
    givenCentralDataProtectionTemplateEnabled();
    AgencyDTO agencyDTO = getValidAgencyDTO();
    agencyDTO.setDataProtection(incompleteAgencyResponsibleContact());

    InvalidOfflineStatusException exception =
        assertThrows(InvalidOfflineStatusException.class, () -> agencyValidator.validate(agencyDTO));

    // The exception type alone would also be satisfied by an unrelated 400; the panel maps the
    // reason, not the type, to the field that is actually missing.
    assertEquals(HttpStatusExceptionReason.DATA_PROTECTION_RESPONSIBLE_IS_EMPTY,
        exception.getHttpStatusExceptionReason());
  }

  /**
   * Single-domain multitenancy, where the Beratungsstelle's own Traeger has the central template
   * off and the main tenant has it on. The main-tenant branch runs its validation inside the same
   * try that tolerates an unavailable settings lookup, so a genuine validation failure used to be
   * caught there, logged as a lookup error and the invalid agency created anyway - the hardening
   * did nothing in exactly the deployment it is meant to protect.
   */
  @Test
  public void validate_Should_ThrowInvalidOfflineStatusException_WhenCreateAndOnlyTheMainTenantEnablesTheCentralTemplate() {
    givenCentralDataProtectionTemplateEnabled(false);
    givenMainTenantWithCentralDataProtectionTemplate("app");
    AgencyDTO agencyDTO = getValidAgencyDTO();
    agencyDTO.setDataProtection(incompleteAgencyResponsibleContact());

    ReflectionTestUtils.setField(agencyDataProtectionValidator, "multitenancyWithSingleDomain", true);
    try {
      InvalidOfflineStatusException exception =
          assertThrows(InvalidOfflineStatusException.class,
              () -> agencyValidator.validate(agencyDTO));

      assertEquals(HttpStatusExceptionReason.DATA_PROTECTION_RESPONSIBLE_IS_EMPTY,
          exception.getHttpStatusExceptionReason());
    } finally {
      ReflectionTestUtils.setField(agencyDataProtectionValidator, "multitenancyWithSingleDomain",
          false);
    }
  }

  private DataProtectionDTO incompleteAgencyResponsibleContact() {
    return new DataProtectionDTO()
        .dataProtectionResponsibleEntity(DataProtectionResponsibleEntityEnum.AGENCY_RESPONSIBLE)
        .agencyDataProtectionResponsibleContact(
            new DataProtectionContactDTO()
                .nameAndLegalForm("  ")
                .postcode("79106")
                .city("Freiburg")
                .email("datenschutz@traeger.de"));
  }

  private void givenMainTenantWithCentralDataProtectionTemplate(String subdomain) {
    when(applicationSettingsService.getApplicationSettings())
        .thenReturn(new ApplicationSettingsDTO()
            .mainTenantSubdomainForSingleDomainMultitenancy(new SettingDTO().value(subdomain)));
    when(tenantService.getRestrictedTenantDataBySubdomain(subdomain))
        .thenReturn(new RestrictedTenantDTO()
            .settings(new Settings().featureCentralDataProtectionTemplateEnabled(true)));
  }

  @Test
  public void validate_Should_NotThrow_WhenCreateAndSelectedDataProtectionContactIsComplete() {
    givenCentralDataProtectionTemplateEnabled();
    AgencyDTO agencyDTO = getValidAgencyDTO();
    agencyDTO.setDataProtection(
        new DataProtectionDTO()
            .dataProtectionResponsibleEntity(DataProtectionResponsibleEntityEnum.AGENCY_RESPONSIBLE)
            .agencyDataProtectionResponsibleContact(
                new DataProtectionContactDTO()
                    .nameAndLegalForm("Traeger Nord gGmbH")
                    .postcode("79106")
                    .city("Freiburg")
                    .email("datenschutz@traeger.de")));

    agencyValidator.validate(agencyDTO);
  }

  /** A tenant without the central data-protection template keeps the old, unvalidated creation. */
  @Test
  public void validate_Should_NotValidateDataProtectionOnCreate_WhenCentralTemplateIsDisabled() {
    AgencyDTO agencyDTO = getValidAgencyDTO();
    agencyDTO.setDataProtection(
        new DataProtectionDTO()
            .dataProtectionResponsibleEntity(DataProtectionResponsibleEntityEnum.AGENCY_RESPONSIBLE)
            .agencyDataProtectionResponsibleContact(new DataProtectionContactDTO()));

    agencyValidator.validate(agencyDTO);
  }

  private void givenCentralDataProtectionTemplateEnabled() {
    givenCentralDataProtectionTemplateEnabled(true);
  }

  private void givenCentralDataProtectionTemplateEnabled(boolean enabled) {
    when(tenantService.getRestrictedTenantDataByTenantId(any()))
        .thenReturn(new RestrictedTenantDTO()
            .settings(new Settings().featureCentralDataProtectionTemplateEnabled(enabled)));
  }

  private AgencyDTO getValidAgencyDTO() {

    EasyRandom easyRandom = new EasyRandom();
    AgencyDTO agencyDTO = easyRandom.nextObject(AgencyDTO.class);
    agencyDTO.setConsultingType(CONSULTING_TYPE_SUCHT);
    agencyDTO.setPostcode(VALID_POSTCODE);
    return agencyDTO;

  }

  private UpdateAgencyDTO getValidUpdateAgencyDTO() {
    EasyRandom easyRandom = new EasyRandom();
    UpdateAgencyDTO updateAgencyDTO = easyRandom.nextObject(UpdateAgencyDTO.class);
    updateAgencyDTO.setPostcode(VALID_POSTCODE);
    return updateAgencyDTO;
  }


}
