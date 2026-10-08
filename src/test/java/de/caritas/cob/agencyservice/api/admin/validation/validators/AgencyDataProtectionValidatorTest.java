package de.caritas.cob.agencyservice.api.admin.validation.validators;

import static org.junit.jupiter.api.Assertions.assertThrows;

import de.caritas.cob.agencyservice.api.admin.validation.validators.model.ValidateAgencyDTO;
import de.caritas.cob.agencyservice.api.exception.httpresponses.HttpStatusExceptionReason;
import de.caritas.cob.agencyservice.api.exception.httpresponses.InvalidOfflineStatusException;
import de.caritas.cob.agencyservice.api.model.DataProtectionDTO;
import de.caritas.cob.agencyservice.api.model.DataProtectionDTO.DataProtectionResponsibleEntityEnum;
import de.caritas.cob.agencyservice.api.service.ApplicationSettingsService;
import de.caritas.cob.agencyservice.api.service.TenantService;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.model.ApplicationSettingsDTO;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.model.SettingDTO;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.Settings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AgencyDataProtectionValidatorTest {

  @InjectMocks
  AgencyDataProtectionValidator agencyDataProtectionValidator;

  private @Mock AgencyDataProtectionValidationService agencyDataProtectionValidationService;

  private @Mock TenantService tenantService;

  private @Mock ApplicationSettingsService applicationSettingsService;

  private @Mock AuthenticatedUser authenticatedUser;

  @Test
  void validate_Should_ValidateForAgencyTenant_When_NonSingleDomainMultitenancy_And_CentralDataProtectionFeatureEnabled() {
    // given
    ValidateAgencyDTO agencyToValidate = ValidateAgencyDTO.builder()
        .tenantId(1L)
        .dataProtectionDTO(new DataProtectionDTO().dataProtectionResponsibleEntity(
            DataProtectionResponsibleEntityEnum.DATA_PROTECTION_OFFICER)).build();
    ReflectionTestUtils.setField(agencyDataProtectionValidator, "multitenancyWithSingleDomain",
        false);
    givenAgencyTenant(agencyToValidate, true);
    // when
    agencyDataProtectionValidator.validate(agencyToValidate);
    // then
    Mockito.verify(agencyDataProtectionValidationService, Mockito.times(1))
        .validate(agencyToValidate);
  }

  @Test
  void validate_Should_SkipValidationService_When_DataProtectionDtoIsNull() {
    ValidateAgencyDTO agencyToValidate = ValidateAgencyDTO.builder().tenantId(1L).build();
    ReflectionTestUtils.setField(agencyDataProtectionValidator, "multitenancyWithSingleDomain",
        false);

    agencyDataProtectionValidator.validate(agencyToValidate);

    Mockito.verify(tenantService, Mockito.never()).getRestrictedTenantDataByTenantId(Mockito.any());
    Mockito.verify(agencyDataProtectionValidationService, Mockito.never())
        .validate(agencyToValidate);
  }

  @Test
  void validate_Should_NotValidateForAgencyTenant_When_NonSingleDomainMultitenancy_And_CentralDataProtectionFeatureDisabled() {
    // given
    ValidateAgencyDTO agencyToValidate = ValidateAgencyDTO.builder()
        .tenantId(1L)
        .dataProtectionDTO(new DataProtectionDTO().dataProtectionResponsibleEntity(
            DataProtectionResponsibleEntityEnum.DATA_PROTECTION_OFFICER)).build();
    ReflectionTestUtils.setField(agencyDataProtectionValidator, "multitenancyWithSingleDomain",
        false);
    givenAgencyTenant(agencyToValidate, false);
    // when
    agencyDataProtectionValidator.validate(agencyToValidate);
    // then
    Mockito.verify(agencyDataProtectionValidationService, Mockito.never())
        .validate(agencyToValidate);
  }

  @ParameterizedTest
  @CsvSource({
      "true,true,2",
      "true,false,1",
      "false,true,1",
      "false,false,0",
  })
  void validate_Should_ValidateForAgencyTenantAndMainTenant_When_SingleDomainMultitenancy(boolean isAgencyTenantCentralDataProtectionEnabled, boolean isMainTenantCentralDataProtectionEnabled, int expectedValidationCalls) {
    // given
    ValidateAgencyDTO agencyToValidate = ValidateAgencyDTO.builder()
        .tenantId(1L)
        .dataProtectionDTO(new DataProtectionDTO().dataProtectionResponsibleEntity(
            DataProtectionResponsibleEntityEnum.DATA_PROTECTION_OFFICER)).build();
    ReflectionTestUtils.setField(agencyDataProtectionValidator, "multitenancyWithSingleDomain",
        true);
    givenAgencyTenant(agencyToValidate, isAgencyTenantCentralDataProtectionEnabled);
    givenSingleDomainWithValue("app");
    givenMainTenant("app", isMainTenantCentralDataProtectionEnabled);
    // when
    agencyDataProtectionValidator.validate(agencyToValidate);
    // then

    Mockito.verify(agencyDataProtectionValidationService, Mockito.times(expectedValidationCalls))
        .validate(agencyToValidate);
  }



  @Test
  void validate_Should_SkipValidationService_When_NoTenantCanBeResolved() {
    // Single tenancy: no tenant id in the request, none in the token, none in the context.
    TenantContext.clear();
    Mockito.when(authenticatedUser.getTenantId()).thenReturn(null);
    ValidateAgencyDTO agencyToValidate = ValidateAgencyDTO.builder()
        .dataProtectionDTO(new DataProtectionDTO().dataProtectionResponsibleEntity(
            DataProtectionResponsibleEntityEnum.DATA_PROTECTION_OFFICER)).build();
    ReflectionTestUtils.setField(agencyDataProtectionValidator, "multitenancyWithSingleDomain",
        false);

    agencyDataProtectionValidator.validate(agencyToValidate);

    Mockito.verify(tenantService, Mockito.never()).getRestrictedTenantDataByTenantId(Mockito.any());
    Mockito.verify(agencyDataProtectionValidationService, Mockito.never())
        .validate(agencyToValidate);
  }

  /**
   * The main-tenant branch shares its try/catch with the settings lookup it is there to tolerate.
   * A validation failure raised inside it must still reach the caller - swallowing it would report
   * a settings outage and create the invalid agency anyway.
   */
  @Test
  void validate_Should_PropagateValidationFailure_When_RaisedForTheMainTenantInSingleDomainMultitenancy() {
    ValidateAgencyDTO agencyToValidate = ValidateAgencyDTO.builder()
        .tenantId(1L)
        .dataProtectionDTO(new DataProtectionDTO().dataProtectionResponsibleEntity(
            DataProtectionResponsibleEntityEnum.AGENCY_RESPONSIBLE)).build();
    ReflectionTestUtils.setField(agencyDataProtectionValidator, "multitenancyWithSingleDomain",
        true);
    givenAgencyTenant(agencyToValidate, false);
    givenSingleDomainWithValue("app");
    givenMainTenant("app", true);
    Mockito.doThrow(new InvalidOfflineStatusException(
            HttpStatusExceptionReason.DATA_PROTECTION_RESPONSIBLE_IS_EMPTY))
        .when(agencyDataProtectionValidationService).validate(agencyToValidate);

    assertThrows(InvalidOfflineStatusException.class,
        () -> agencyDataProtectionValidator.validate(agencyToValidate));
  }

  /**
   * The other half of the same try: an unavailable settings lookup still must not block the
   * update (e.g. a visibility toggle). Narrowing the catch to the lookup must not narrow this.
   */
  @Test
  void validate_Should_NotBlockTheAgency_When_TheMainTenantSettingsLookupFails() {
    ValidateAgencyDTO agencyToValidate = ValidateAgencyDTO.builder()
        .tenantId(1L)
        .dataProtectionDTO(new DataProtectionDTO().dataProtectionResponsibleEntity(
            DataProtectionResponsibleEntityEnum.AGENCY_RESPONSIBLE)).build();
    ReflectionTestUtils.setField(agencyDataProtectionValidator, "multitenancyWithSingleDomain",
        true);
    givenAgencyTenant(agencyToValidate, false);
    Mockito.when(applicationSettingsService.getApplicationSettings())
        .thenThrow(new IllegalStateException("settings service unavailable"));

    agencyDataProtectionValidator.validate(agencyToValidate);

    Mockito.verify(agencyDataProtectionValidationService, Mockito.never())
        .validate(agencyToValidate);
  }

  private void givenAgencyTenant(ValidateAgencyDTO agency, boolean isCentralDataProtectionEnabled) {
    Mockito.when(tenantService.getRestrictedTenantDataByTenantId(agency.getTenantId()))
        .thenReturn(new RestrictedTenantDTO().settings(new Settings().featureCentralDataProtectionTemplateEnabled(isCentralDataProtectionEnabled)));
  }

  private void givenMainTenant(String domain, boolean isCentralDataProtectionEnabled) {
    Mockito.when(tenantService.getRestrictedTenantDataBySubdomain(domain))
        .thenReturn(new RestrictedTenantDTO().settings(new Settings().featureCentralDataProtectionTemplateEnabled(isCentralDataProtectionEnabled)));
  }

  private void givenSingleDomainWithValue(String domain) {
    Mockito.when(applicationSettingsService.getApplicationSettings()).thenReturn(
        new ApplicationSettingsDTO().mainTenantSubdomainForSingleDomainMultitenancy(
            new SettingDTO().value(
                domain)));
  }
}