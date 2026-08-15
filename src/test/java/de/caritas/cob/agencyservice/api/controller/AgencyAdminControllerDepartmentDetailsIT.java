package de.caritas.cob.agencyservice.api.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.caritas.cob.agencyservice.api.admin.service.UserAdminService;
import de.caritas.cob.agencyservice.api.manager.consultingtype.ConsultingTypeManager;
import de.caritas.cob.agencyservice.api.service.TenantService;
import de.caritas.cob.agencyservice.api.service.TopicEnrichmentService;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * HTTP-level coverage for the department (Fachbereich = agency × topic) contact detail overrides
 * endpoint (ORISO-Admin#197 / AGY-PUB-02). The sibling controller/service tests mock their
 * collaborators; this one drives the real routing, JSON mapping, bean validation and persistence,
 * so the AGENCY_TOPIC override columns are actually exercised against the shared test schema.
 *
 * <p>The security filter chain is disabled ({@code addFilters = false}) and {@link
 * AuthenticatedUser} is a mock, so role routing is NOT covered here — the service-level guards
 * are instead exercised directly by stubbing the mock (see the restricted-admin denial case) and
 * unit-tested exhaustively in {@code DepartmentDetailsServiceTest}.
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
  void getDepartmentDetails_Should_returnAllNull_When_noOverrideStored() throws Exception {
    mockMvc
        .perform(get(PATH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openingHours").doesNotExist())
        .andExpect(jsonPath("$.phoneExtension").doesNotExist())
        .andExpect(jsonPath("$.floorLocation").doesNotExist());
  }

  @Test
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
  void updateDepartmentDetails_Should_return403_When_restrictedAdminDoesNotOwnTheAgency()
      throws Exception {
    // The IDOR guard rejects before any load or write; ApiDefaultResponseEntityExceptionHandler
    // maps AgencyAccessDeniedException to 403 FORBIDDEN.
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(true);
    when(authenticatedUser.requireUserId()).thenReturn("admin-1");
    when(userAdminService.getAdminUserAgencyIds("admin-1")).thenReturn(List.of(9L));

    mockMvc
        .perform(
            put(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openingHours\":\"Mo 9-12\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateDepartmentDetails_Should_return400_When_valueExceedsMaxLength() throws Exception {
    // The DTO @Size limits (1000/50/100) must reject oversized input at the HTTP boundary,
    // not surface as a DB truncation error.
    var oversized = "x".repeat(1001);
    mockMvc
        .perform(
            put(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openingHours\":\"" + oversized + "\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
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
