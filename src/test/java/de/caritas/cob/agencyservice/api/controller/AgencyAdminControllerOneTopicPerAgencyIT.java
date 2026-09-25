package de.caritas.cob.agencyservice.api.controller;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.caritas.cob.agencyservice.api.manager.consultingtype.ConsultingTypeManager;
import de.caritas.cob.agencyservice.api.model.AgencyDTO;
import de.caritas.cob.agencyservice.api.model.UpdateAgencyDTO;
import de.caritas.cob.agencyservice.api.service.ApplicationSettingsService;
import de.caritas.cob.agencyservice.api.service.TenantService;
import de.caritas.cob.agencyservice.api.service.TopicService;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import de.caritas.cob.agencyservice.api.util.JsonConverter;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.model.ApplicationSettingsDTO;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.model.FeatureToggleDTO;
import de.caritas.cob.agencyservice.consultingtypeservice.generated.web.model.ExtendedConsultingTypeResponseDTO;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.Settings;
import de.caritas.cob.agencyservice.testHelper.PathConstants;
import de.caritas.cob.agencyservice.topicservice.generated.web.model.TopicDTO;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * ADR-014 amendment 2026-09-25 (ORISO-UserService#1264): the global switch
 * {@code oneTopicPerAgencyEnabled} limits every agency to one topic. Test data: agency 1 already
 * holds topics 0 and 1 (legacy multi-topic), agency 2 holds topic 2.
 */
@SpringBootTest
@ActiveProfiles("testing")
@TestPropertySource(properties = "feature.topics.enabled=true")
@AutoConfigureMockMvc(addFilters = false)
@Transactional
@Sql(scripts = "/database/AgencyDatabase.sql")
class AgencyAdminControllerOneTopicPerAgencyIT {

  private static final String CSRF_TOKEN = "test";
  private static final String MULTI_TOPIC_AGENCY_PATH = "/agencyadmin/agencies/1";
  private static final String SINGLE_TOPIC_AGENCY_PATH = "/agencyadmin/agencies/2";
  private static final String REASON = "ONE_TOPIC_PER_AGENCY";

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;
  @MockitoBean private ConsultingTypeManager consultingTypeManager;
  @MockitoBean private TopicService topicService;
  @MockitoBean private AuthenticatedUser authenticatedUser;
  @MockitoBean private TenantService tenantService;
  @MockitoBean private ApplicationSettingsService applicationSettingsService;

  @BeforeEach
  void setup() throws Exception {
    TenantContext.clear();
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    when(tenantService.getRestrictedTenantDataByTenantId(Mockito.any()))
        .thenReturn(new RestrictedTenantDTO()
            .settings(new Settings().featureCentralDataProtectionTemplateEnabled(false)));
    when(authenticatedUser.getTenantId()).thenReturn(1L);
    when(consultingTypeManager.getConsultingTypeSettings(anyInt()))
        .thenReturn(new ExtendedConsultingTypeResponseDTO());
    when(topicService.getAllTopics()).thenReturn(LongStream.rangeClosed(0, 3)
        .mapToObj(id -> new TopicDTO().id(id).name("Topic " + id).status("ACTIVE"))
        .toList());
  }

  @Test
  @WithMockUser(authorities = {"AUTHORIZATION_AGENCY_ADMIN"})
  void createAgency_Should_rejectTwoTopics_When_switchIsOn() throws Exception {
    switchOneTopicPerAgency(true);

    create(List.of(1L, 2L))
        .andExpect(status().isConflict())
        .andExpect(header().string("X-Reason", REASON));
  }

  @Test
  @WithMockUser(authorities = {"AUTHORIZATION_AGENCY_ADMIN"})
  void createAgency_Should_acceptOneTopic_When_switchIsOn() throws Exception {
    switchOneTopicPerAgency(true);

    create(List.of(1L)).andExpect(status().isCreated());
  }

  @Test
  @WithMockUser(authorities = {"AUTHORIZATION_AGENCY_ADMIN"})
  void createAgency_Should_acceptTwoTopics_When_switchIsOff() throws Exception {
    switchOneTopicPerAgency(false);

    create(List.of(1L, 2L))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("_embedded.topics.length()").value(2));
  }

  @Test
  @WithMockUser(authorities = {"AUTHORIZATION_AGENCY_ADMIN"})
  void updateAgency_Should_rejectSecondTopic_When_switchIsOn() throws Exception {
    switchOneTopicPerAgency(true);

    update(SINGLE_TOPIC_AGENCY_PATH, List.of(2L, 3L))
        .andExpect(status().isConflict())
        .andExpect(header().string("X-Reason", REASON));
  }

  @Test
  @WithMockUser(authorities = {"AUTHORIZATION_AGENCY_ADMIN"})
  void updateAgency_Should_acceptSecondTopic_When_switchIsOff() throws Exception {
    switchOneTopicPerAgency(false);

    update(SINGLE_TOPIC_AGENCY_PATH, List.of(2L, 3L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("_embedded.topics.length()").value(2));
  }

  @Test
  @WithMockUser(authorities = {"AUTHORIZATION_AGENCY_ADMIN"})
  void updateAgency_Should_acceptSwappingTheOnlyTopic_When_switchIsOn() throws Exception {
    switchOneTopicPerAgency(true);

    update(SINGLE_TOPIC_AGENCY_PATH, List.of(3L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("_embedded.topics.[0].id").value(3));
  }

  @Test
  @WithMockUser(authorities = {"AUTHORIZATION_AGENCY_ADMIN"})
  void updateAgency_Should_rejectAddingToLegacyMultiTopicAgency_When_switchIsOn()
      throws Exception {
    switchOneTopicPerAgency(true);

    update(MULTI_TOPIC_AGENCY_PATH, List.of(0L, 1L, 2L))
        .andExpect(status().isConflict())
        .andExpect(header().string("X-Reason", REASON));
  }

  @Test
  @WithMockUser(authorities = {"AUTHORIZATION_AGENCY_ADMIN"})
  void updateAgency_Should_allowRemovingFromLegacyMultiTopicAgency_When_switchIsOn()
      throws Exception {
    switchOneTopicPerAgency(true);

    update(MULTI_TOPIC_AGENCY_PATH, List.of(1L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("_embedded.topics.length()").value(1))
        .andExpect(jsonPath("_embedded.topics.[0].id").value(1));
  }

  @Test
  @WithMockUser(authorities = {"AUTHORIZATION_AGENCY_ADMIN"})
  void updateAgency_Should_keepLegacyMultiTopicAgencyUnchanged_When_switchIsOn()
      throws Exception {
    switchOneTopicPerAgency(true);

    update(MULTI_TOPIC_AGENCY_PATH, List.of(0L, 1L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("_embedded.topics.length()").value(2));
  }

  private void switchOneTopicPerAgency(boolean enabled) {
    when(applicationSettingsService.getApplicationSettings())
        .thenReturn(new ApplicationSettingsDTO()
            .oneTopicPerAgencyEnabled(new FeatureToggleDTO().value(enabled).readOnly(false)));
  }

  private ResultActions create(List<Long> topicIds) throws Exception {
    var agency = new AgencyDTO()
        .topicIds(topicIds)
        .name("Test name")
        .postcode("12345")
        .city("Test city")
        .teamAgency(true)
        .consultingType(0)
        .url("https://www.test.de")
        .external(true);
    return mockMvc.perform(withCsrf(post(PathConstants.CREATE_AGENCY_PATH))
        .contentType(APPLICATION_JSON)
        .content(JsonConverter.convertToJson(agency)));
  }

  private ResultActions update(String path, List<Long> topicIds) throws Exception {
    var agency = new UpdateAgencyDTO()
        .topicIds(topicIds)
        .name("Test update name")
        .offline(true)
        .external(false);
    return mockMvc.perform(withCsrf(put(path))
        .contentType(APPLICATION_JSON)
        .content(JsonConverter.convertToJson(agency)));
  }

  private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder) {
    return builder.header("X-CSRF-Token", CSRF_TOKEN).cookie(new Cookie("CSRF-TOKEN", CSRF_TOKEN));
  }
}
