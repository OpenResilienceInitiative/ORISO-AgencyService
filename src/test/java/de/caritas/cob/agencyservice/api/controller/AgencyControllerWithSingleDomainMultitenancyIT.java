package de.caritas.cob.agencyservice.api.controller;

import static de.caritas.cob.agencyservice.testHelper.PathConstants.PATH_GET_AGENCIES_WITH_IDS;
import static de.caritas.cob.agencyservice.testHelper.PathConstants.PATH_GET_LIST_OF_AGENCIES;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.caritas.cob.agencyservice.api.exception.MissingConsultingTypeException;
import de.caritas.cob.agencyservice.api.manager.consultingtype.ConsultingTypeManager;
import de.caritas.cob.agencyservice.api.service.TopicEnrichmentService;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.ApiClient;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.model.ApplicationSettingsDTO;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.model.SettingDTO;
import de.caritas.cob.agencyservice.config.apiclient.ApplicationSettingsApiControllerFactory;
import de.caritas.cob.agencyservice.config.apiclient.TenantServiceApiControllerFactory;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.ApplicationsettingsControllerApi;
import de.caritas.cob.agencyservice.tenantservice.generated.web.TenantControllerApi;

@SpringBootTest
@TestPropertySource(properties = {"feature.multitenancy.with.single.domain.enabled=true",
    "multitenancy.enabled=true", "feature.topics.enabled=false"})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("testing")
@Transactional
@Sql(scripts = "/database/AgencyDatabase.sql")
class AgencyControllerWithSingleDomainMultitenancyIT {

  private static final String VALID_COUNSELLING_RELATION_QUERY = "counsellingRelation=PARENTAL_COUNSELLING";
  private MockMvc mvc;

  @BeforeEach
  public void setup() {
    TenantContext.clear();
    mvc = MockMvcBuilders
        .webAppContextSetup(context)
        .apply(springSecurity())
        .build();
  }

  @MockitoBean
  private ConsultingTypeManager consultingTypeManager;

  @MockitoBean
  private TopicEnrichmentService topicEnrichmentService;

  @MockitoBean
  private ApplicationSettingsApiControllerFactory applicationSettingsApiControllerFactory;
  @MockitoBean
  private ApplicationsettingsControllerApi applicationsettingsControllerApi;

  @MockitoBean
  private TenantControllerApi tenantControllerApi;


  @MockitoBean
  private TenantServiceApiControllerFactory tenantServiceApiControllerFactory;

  @Autowired
  private WebApplicationContext context;


  @BeforeEach
  public void setUp() throws MissingConsultingTypeException {
    when(applicationSettingsApiControllerFactory.createControllerApi()).thenReturn(applicationsettingsControllerApi);
    when(applicationsettingsControllerApi.getApiClient()).thenReturn(new ApiClient());

    when(consultingTypeManager.getConsultingTypeSettings(anyInt()))
        .thenReturn(
            new de.caritas.cob.agencyservice.consultingtypeservice.generated.web.model.ExtendedConsultingTypeResponseDTO());
    when(applicationsettingsControllerApi.getApplicationSettings()).thenReturn(new ApplicationSettingsDTO()
        .mainTenantSubdomainForSingleDomainMultitenancy(new SettingDTO().value("app")));
    when(tenantControllerApi.getRestrictedTenantDataBySubdomain("app", null)).thenReturn(new RestrictedTenantDTO().id(0L));
    when(tenantServiceApiControllerFactory.createControllerApi()).thenReturn(tenantControllerApi);
    when(tenantControllerApi.getRestrictedTenantDataByTenantId(Mockito.anyLong())).thenReturn(new RestrictedTenantDTO().id(0L));
  }

  @Test
  void getAgencies_Should_ReturnOk_AndSkipConsultingTypeParamInAgencySearch_When_MatchingSearchParametersAreProvided() throws Exception {
    mvc.perform(

            get(PATH_GET_LIST_OF_AGENCIES + "?" + "postcode=99999" + "&"
                + "consultingType=19")
                .accept(MediaType.APPLICATION_JSON))

        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(6)));
  }

  @Test
  void getAgencies_Should_ReturnOk_When_MatchingSearchParametersAreProvided() throws Exception {
    ResultActions resultActions = mvc.perform(
            get(PATH_GET_LIST_OF_AGENCIES + "?" + "postcode=53001" + "&"
                + "consultingType=20" + "&" + VALID_COUNSELLING_RELATION_QUERY)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").value(0));
  }

  @Test
  void getAgencies_Should_ReturnNoContent_When_NonMatchingSearchParametersAreProvided() throws Exception {
    ResultActions resultActions = mvc.perform(
            get(PATH_GET_LIST_OF_AGENCIES + "?" + "postcode=53001" + "&"
                + "consultingType=20" + "&" + "counsellingRelation=RELATIVE_COUNSELLING")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void getAgencies_Should_ReturnOk_GetAgencyById_When_DifferentTenantAgencyIsRequested() throws Exception {
    when(tenantControllerApi.getRestrictedTenantDataByTenantId(Mockito.anyLong())).thenReturn(new RestrictedTenantDTO().id(10L));
    when(tenantServiceApiControllerFactory.createControllerApi()).thenReturn(tenantControllerApi);
    mvc.perform(

            get(PATH_GET_AGENCIES_WITH_IDS + "1738")
                .header("tenantId", 10)
                .accept(MediaType.APPLICATION_JSON))

        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
  }

}
