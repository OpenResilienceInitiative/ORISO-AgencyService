package de.caritas.cob.agencyservice.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.caritas.cob.agencyservice.api.admin.service.UserAdminService;
import de.caritas.cob.agencyservice.api.manager.consultingtype.ConsultingTypeManager;
import de.caritas.cob.agencyservice.api.service.TenantService;
import de.caritas.cob.agencyservice.api.service.TopicEnrichmentService;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * HTTP-level coverage for the department (Fachbereich = agency × topic) contact detail overrides
 * endpoint (ORISO-Admin#197 / AGY-PUB-02). The sibling controller/service tests mock their
 * collaborators; this one drives the real routing, JSON mapping and persistence, so the
 * AGENCY_TOPIC override columns are actually exercised against the shared test schema.
 */
@SpringBootTest
@ActiveProfiles("testing")
@AutoConfigureMockMvc(addFilters = false)
@Transactional
@Sql(scripts = "/database/AgencyDatabase.sql")
class AgencyAdminControllerDepartmentDetailsIT {

  private static final String PATH = "/agencyadmin/agencies/1/topics/1/details";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ConsultingTypeManager consultingTypeManager;
  @MockitoBean private TenantService tenantService;
  @MockitoBean private TopicEnrichmentService topicEnrichmentService;
  @MockitoBean private UserAdminService userAdminService;
  @MockitoBean private AuthenticatedUser authenticatedUser;

  @Test
  @WithMockUser(authorities = {"AUTHORIZATION_AGENCY_ADMIN"})
  void getDepartmentDetails_Should_returnAllNull_When_noOverrideStored() throws Exception {
    mockMvc
        .perform(get(PATH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openingHours").doesNotExist())
        .andExpect(jsonPath("$.phoneExtension").doesNotExist())
        .andExpect(jsonPath("$.floorLocation").doesNotExist());
  }

  @Test
  @WithMockUser(authorities = {"AUTHORIZATION_AGENCY_ADMIN"})
  void updateDepartmentDetails_Should_persistOverrides_And_beReadBack() throws Exception {
    var body =
        "{\"openingHours\":\"Di+Do 14-18 Uhr\",\"phoneExtension\":\"-23\","
            + "\"floorLocation\":\"3. OG, Raum 312\"}";

    mockMvc
        .perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openingHours").value("Di+Do 14-18 Uhr"))
        .andExpect(jsonPath("$.phoneExtension").value("-23"))
        .andExpect(jsonPath("$.floorLocation").value("3. OG, Raum 312"));

    mockMvc
        .perform(get(PATH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openingHours").value("Di+Do 14-18 Uhr"))
        .andExpect(jsonPath("$.phoneExtension").value("-23"))
        .andExpect(jsonPath("$.floorLocation").value("3. OG, Raum 312"));
  }

  @Test
  @WithMockUser(authorities = {"AUTHORIZATION_AGENCY_ADMIN"})
  void updateDepartmentDetails_Should_clearOverride_When_memberIsNull() throws Exception {
    mockMvc
        .perform(
            put(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"openingHours\":\"Di+Do 14-18 Uhr\",\"phoneExtension\":\"-23\","
                        + "\"floorLocation\":\"3. OG, Raum 312\"}"))
        .andExpect(status().isOk());

    // A null member clears the override, so the department inherits the Beratungsstelle value.
    mockMvc
        .perform(
            put(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"openingHours\":null,\"phoneExtension\":null,\"floorLocation\":\"EG\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openingHours").doesNotExist())
        .andExpect(jsonPath("$.phoneExtension").doesNotExist())
        .andExpect(jsonPath("$.floorLocation").value("EG"));

    mockMvc
        .perform(get(PATH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openingHours").doesNotExist())
        .andExpect(jsonPath("$.phoneExtension").doesNotExist())
        .andExpect(jsonPath("$.floorLocation").value("EG"));
  }
}
