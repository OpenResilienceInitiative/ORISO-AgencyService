package de.caritas.cob.agencyservice.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.model.DataProtectionContactDTO;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agency.DataProtectionResponsibleEntity;
import de.caritas.cob.agencyservice.api.util.JsonConverter;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.model.ApplicationSettingsDTO;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.model.ApplicationSettingsDTOMultitenancyWithSingleDomainEnabled;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.AgencyContextDTO;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.Content;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.DataProtectionContactTemplateDTO;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.DataProtectionOfficerDTO;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import freemarker.cache.NullCacheStorage;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.freemarker.FreeMarkerConfigurationFactoryBean;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CentralDataProtectionTemplateServiceTest {

  public static final String DATA_PROTECTION_OFFICER_CONTACT_TEMPLATE = "Data protection officer contact name: <#if name?exists>${name},</#if><#if city?exists> city: ${city}, </#if><#if postCode?exists>postcode: ${postCode}, </#if><#if phoneNumber?exists>phoneNumber: ${phoneNumber}</#if>";
  public static final String RESPONSIBLE_CONTACT_TEMPLATE = "Data protection responsible contact name: <#if name?exists>${name},</#if><#if city?exists> city: ${city}, </#if><#if postCode?exists>postcode: ${postCode}, </#if><#if phoneNumber?exists>phoneNumber: ${phoneNumber}</#if>";

  private CentralDataProtectionTemplateService centralDataProtectionTemplateService;
  private TemplateRenderer templateRenderer;

  @Mock
  private TenantService tenantService;

  @Mock
  private ApplicationSettingsService applicationSettingsService;

  @BeforeEach
  void setup() throws Exception {
    templateRenderer = new TemplateRenderer(createFreemarkerConfiguration());
    centralDataProtectionTemplateService = new CentralDataProtectionTemplateService(
        tenantService, templateRenderer, applicationSettingsService);
    ReflectionTestUtils.setField(centralDataProtectionTemplateService,
        "multitenancyWithSingleDomain", false);
    when(applicationSettingsService.getApplicationSettings()).thenReturn(
        new ApplicationSettingsDTO().legalContentChangesBySingleTenantAdminsAllowed(
            new ApplicationSettingsDTOMultitenancyWithSingleDomainEnabled().value(true)));
  }

  private static Configuration createFreemarkerConfiguration()
      throws TemplateException, IOException {
    Configuration configuration = new FreeMarkerConfigurationFactoryBean().createConfiguration();
    configuration.setTemplateExceptionHandler(TemplateExceptionHandler.IGNORE_HANDLER);
    configuration.setCacheStorage(NullCacheStorage.INSTANCE);
    configuration.setTemplateLoader(new StringTemplateLoader());
    return configuration;
  }

  @Test
  void renderDataProtectionPrivacy_shouldProperlyRenderPrivacy_When_PlaceholdersAreRendered() {

    // given
    when(tenantService.getRestrictedTenantDataByTenantId(anyLong())).thenReturn(
        new RestrictedTenantDTO()
            .content(
                new Content().dataProtectionContactTemplate(getDataProtectionContactTemplate())
                    .privacy(
                        "Privacy template with placeholders: ${dataProtectionOfficer} ${responsible}")));
    DataProtectionContactDTO dataProtectionContactDTO = new DataProtectionContactDTO()
        .nameAndLegalForm("Max Mustermann");

    Agency agency = Agency.builder()
        .id(1000L)
        .tenantId(1L)
        .consultingTypeId(1)
        .name("agencyName")
        .dataProtectionResponsibleEntity(DataProtectionResponsibleEntity.DATA_PROTECTION_OFFICER)
        .dataProtectionOfficerContactData(JsonConverter.convertToJson(dataProtectionContactDTO))
        .dataProtectionAgencyResponsibleContactData(
            JsonConverter.convertToJson(dataProtectionContactDTO))
        .build();

    // when
    var renderedPrivacy = centralDataProtectionTemplateService.renderPrivacyTemplateWithRenderedPlaceholderValues(
        agency);

    // then
    assertThat(
        renderedPrivacy).isEqualTo(
        "Privacy template with placeholders: Data protection officer contact name: Max Mustermann, Data protection responsible contact name: Max Mustermann,");
  }

  @Test
  void renderDataProtectionPrivacy_shouldProperlyRenderPrivacyTakingTemplateFromMainTenant_When_MultitenancySingleDomainAndTenantLevelPrivacyOverrideNotAllowed() {
    // given
    ReflectionTestUtils.setField(centralDataProtectionTemplateService,
        "multitenancyWithSingleDomain", true);
    when(applicationSettingsService.getApplicationSettings()).thenReturn(
        new ApplicationSettingsDTO().legalContentChangesBySingleTenantAdminsAllowed(
            new ApplicationSettingsDTOMultitenancyWithSingleDomainEnabled().value(false)));

    when(tenantService.getMainTenant()).thenReturn(
        new RestrictedTenantDTO()
            .content(
                new Content().dataProtectionContactTemplate(getDataProtectionContactTemplate())
                    .privacy(
                        "Privacy template with placeholders from main tenant: ${dataProtectionOfficer} ${responsible}")));
    DataProtectionContactDTO dataProtectionContactDTO = new DataProtectionContactDTO()
        .nameAndLegalForm("Max Mustermann");

    Agency agency = Agency.builder()
        .id(1000L)
        .tenantId(1L)
        .consultingTypeId(1)
        .name("agencyName")
        .dataProtectionResponsibleEntity(DataProtectionResponsibleEntity.DATA_PROTECTION_OFFICER)
        .dataProtectionOfficerContactData(JsonConverter.convertToJson(dataProtectionContactDTO))
        .dataProtectionAgencyResponsibleContactData(
            JsonConverter.convertToJson(dataProtectionContactDTO))
        .build();

    // when
    var renderedPrivacy = centralDataProtectionTemplateService.renderPrivacyTemplateWithRenderedPlaceholderValues(
        agency);

    // then
    assertThat(
        renderedPrivacy).isEqualTo(
        "Privacy template with placeholders from main tenant: Data protection officer contact name: Max Mustermann, Data protection responsible contact name: Max Mustermann,");

  }


  @Test
  void renderDataProtectionPrivacy_shouldReturnPrivacyAsItIs_When_PlaceholdersAreNotIncludedInPrivacy() {

    // given
    when(tenantService.getRestrictedTenantDataByTenantId(anyLong())).thenReturn(
        new RestrictedTenantDTO()
            .content(
                new Content().dataProtectionContactTemplate(getDataProtectionContactTemplate())
                    .privacy(
                        "Privacy template without placeholders")));
    DataProtectionContactDTO dataProtectionContactDTO = new DataProtectionContactDTO()
        .nameAndLegalForm("Max Mustermann");

    Agency agency = Agency.builder()
        .id(1000L)
        .tenantId(1L)
        .consultingTypeId(1)
        .name("agencyName")
        .dataProtectionResponsibleEntity(DataProtectionResponsibleEntity.DATA_PROTECTION_OFFICER)
        .dataProtectionOfficerContactData(JsonConverter.convertToJson(dataProtectionContactDTO))
        .dataProtectionAgencyResponsibleContactData(
            JsonConverter.convertToJson(dataProtectionContactDTO))
        .build();

    // when
    var renderedPrivacy = centralDataProtectionTemplateService.renderPrivacyTemplateWithRenderedPlaceholderValues(
        agency);

    // then
    assertThat(
        renderedPrivacy).isEqualTo(
        "Privacy template without placeholders");
  }

  @Test
  void renderDataProtectionTemplatePlaceholders_shouldProperlyRenderPlaceholders_If_SomeVariableDataIsMissing() {

    // given
    RestrictedTenantDTO tenantDTO = new RestrictedTenantDTO()
        .content(
            new Content().dataProtectionContactTemplate(getDataProtectionContactTemplate()));
    when(tenantService.getRestrictedTenantDataByTenantId(anyLong())).thenReturn(
        tenantDTO);
    DataProtectionContactDTO dataProtectionContactDTO = new DataProtectionContactDTO()
        .nameAndLegalForm("Max Mustermann");

    Agency agency = Agency.builder()
        .id(1000L)
        .tenantId(1L)
        .consultingTypeId(1)
        .name("agencyName")
        .dataProtectionResponsibleEntity(DataProtectionResponsibleEntity.DATA_PROTECTION_OFFICER)
        .dataProtectionOfficerContactData(JsonConverter.convertToJson(dataProtectionContactDTO))
        .dataProtectionAgencyResponsibleContactData(
            JsonConverter.convertToJson(dataProtectionContactDTO))
        .build();

    // when
    var renderedPlaceholders = centralDataProtectionTemplateService.renderDataProtectionPlaceholdersFromTemplates(
        agency, tenantDTO);

    // then
    assertThat(
        renderedPlaceholders).containsEntry(DataProtectionPlaceHolderType.DATA_PROTECTION_OFFICER,
        "Data protection officer contact name: Max Mustermann,").containsEntry(
        DataProtectionPlaceHolderType.DATA_PROTECTION_RESPONSIBLE,
        "Data protection responsible contact name: Max Mustermann,");
  }

  @Test
  void renderDataProtectionTemplatePlaceholders_shouldProperlyRenderPlaceholders() {

    // given
    RestrictedTenantDTO tenantDTO = new RestrictedTenantDTO()
        .content(
            new Content().dataProtectionContactTemplate(getDataProtectionContactTemplate()));
    when(tenantService.getRestrictedTenantDataByTenantId(anyLong())).thenReturn(
        tenantDTO);
    DataProtectionContactDTO dataProtectionContactDTO = new DataProtectionContactDTO()
        .nameAndLegalForm("Max Mustermann")
        .street("Musterstraße 1")
        .postcode("12345")
        .city("Freiburg")
        .phoneNumber("0123456789");

    Agency agency = Agency.builder()
        .id(1000L)
        .tenantId(1L)
        .consultingTypeId(1)
        .name("agencyName")
        .dataProtectionResponsibleEntity(DataProtectionResponsibleEntity.DATA_PROTECTION_OFFICER)
        .dataProtectionOfficerContactData(JsonConverter.convertToJson(dataProtectionContactDTO))
        .dataProtectionAgencyResponsibleContactData(
            JsonConverter.convertToJson(dataProtectionContactDTO))
        .build();

    // when
    var renderedPlaceholders = centralDataProtectionTemplateService.renderDataProtectionPlaceholdersFromTemplates(
        agency, tenantDTO);

    // then
    assertThat(
        renderedPlaceholders).containsEntry(DataProtectionPlaceHolderType.DATA_PROTECTION_OFFICER,
            "Data protection officer contact name: Max Mustermann, city: Freiburg, postcode: 12345, phoneNumber: 0123456789")
        .containsEntry(
            DataProtectionPlaceHolderType.DATA_PROTECTION_RESPONSIBLE,
            "Data protection responsible contact name: Max Mustermann, city: Freiburg, postcode: 12345, phoneNumber: 0123456789");
  }

  @Test
  void renderDataProtectionTemplatePlaceholders_shouldReturnPlaceholderTemplate_IfDataProtectionAgencyContactDataIsNotSet() {

    // given
    RestrictedTenantDTO tenantDTO = new RestrictedTenantDTO()
        .content(
            new Content().dataProtectionContactTemplate(getDataProtectionContactTemplate()));
    when(tenantService.getRestrictedTenantDataByTenantId(anyLong())).thenReturn(
        tenantDTO);

    Agency agency = Agency.builder()
        .id(1000L)
        .tenantId(1L)
        .consultingTypeId(1)
        .name("agencyName")
        .dataProtectionResponsibleEntity(DataProtectionResponsibleEntity.DATA_PROTECTION_OFFICER)
        .build();

    // when
    var renderedPlaceholders = centralDataProtectionTemplateService.renderDataProtectionPlaceholdersFromTemplates(
        agency, tenantDTO);

    // then
    assertThat(
        renderedPlaceholders).containsEntry(DataProtectionPlaceHolderType.DATA_PROTECTION_OFFICER,
        "Data protection officer contact name: ").containsEntry(
        DataProtectionPlaceHolderType.DATA_PROTECTION_RESPONSIBLE,
        "Data protection responsible contact name: ").hasSize(2);
  }


  @Test
  void renderDataProtectionTemplatePlaceholders_shouldReturnPlaceholderTemplate_IfNoDataOnAgency() {

    // given
    RestrictedTenantDTO tenantDTO = new RestrictedTenantDTO()
        .content(
            new Content().dataProtectionContactTemplate(getDataProtectionContactTemplate()));
    when(tenantService.getRestrictedTenantDataByTenantId(anyLong())).thenReturn(
        tenantDTO);

    Agency agency = Agency.builder()
        .id(1000L)
        .tenantId(1L)
        .consultingTypeId(1)
        .name("agencyName")
        .dataProtectionResponsibleEntity(DataProtectionResponsibleEntity.DATA_PROTECTION_OFFICER)
        .build();

    // when
    var renderedPlaceholders = centralDataProtectionTemplateService.renderDataProtectionPlaceholdersFromTemplates(
        agency, tenantDTO);

    // then
    assertThat(
        renderedPlaceholders).containsEntry(DataProtectionPlaceHolderType.DATA_PROTECTION_OFFICER,
        "Data protection officer contact name: ").containsEntry(
        DataProtectionPlaceHolderType.DATA_PROTECTION_RESPONSIBLE,
        "Data protection responsible contact name: ").hasSize(2);
  }


  private DataProtectionContactTemplateDTO getDataProtectionContactTemplate() {
    return new DataProtectionContactTemplateDTO().agencyContext(
        getAgencyContext());
  }

  private AgencyContextDTO getAgencyContext() {
    return new AgencyContextDTO().dataProtectionOfficer(
            new DataProtectionOfficerDTO().dataProtectionOfficerContact(
                DATA_PROTECTION_OFFICER_CONTACT_TEMPLATE))
        .responsibleContact(
            RESPONSIBLE_CONTACT_TEMPLATE);
  }


}
