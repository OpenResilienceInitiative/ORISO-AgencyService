package de.caritas.cob.agencyservice.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.caritas.cob.agencyservice.api.admin.service.UserAdminService;
import de.caritas.cob.agencyservice.api.manager.consultingtype.ConsultingTypeManager;
import de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopicRepository;
import de.caritas.cob.agencyservice.api.service.TenantService;
import de.caritas.cob.agencyservice.api.service.TopicEnrichmentService;
import de.caritas.cob.agencyservice.api.service.legal.LegalTextInheritanceResolver;
import de.caritas.cob.agencyservice.api.service.legal.LegalTextSourceLevel;
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
 * HTTP-level round trip for "revert a department's own legal text to the inherited text"
 * (ORISO-AgencyService#334, ORISO-Admin#916): publish an own text, revert it, read it back, and ask
 * the public resolver whose text now applies. Security filters are off and {@link
 * AuthenticatedUser} is a mock, as in the sibling department ITs.
 */
@SpringBootTest
@ActiveProfiles("testing")
@AutoConfigureMockMvc(addFilters = false)
@Transactional
@Sql(scripts = "/database/AgencyDatabase.sql")
class AgencyAdminControllerDepartmentLegalRevertIT {

  private static final String DPP = "/agencyadmin/agencies/1/topics/1/dpp";
  private static final String IMPRINT = "/agencyadmin/agencies/1/topics/1/imprint";
  private static final String PUBLISH_BODY = "{\"content\":{\"de\":\"<p>eigener Text</p>\"},\"publish\":true}";

  @Autowired private MockMvc mockMvc;
  @Autowired private AgencyTopicRepository agencyTopicRepository;
  @Autowired private LegalTextInheritanceResolver resolver;

  @MockitoBean private ConsultingTypeManager consultingTypeManager;
  @MockitoBean private TenantService tenantService;
  @MockitoBean private TopicEnrichmentService topicEnrichmentService;
  @MockitoBean private UserAdminService userAdminService;
  @MockitoBean private AuthenticatedUser authenticatedUser;

  @Test
  void revertDataProtection_Should_clearOwnText_And_stopTheDepartmentLevelFromAnswering()
      throws Exception {
    publish(DPP);
    assertThat(resolveDppLevel()).isEqualTo(LegalTextSourceLevel.DEPARTMENT);

    mockMvc
        .perform(post(DPP + "/revert"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reverted").value(true));

    mockMvc
        .perform(get(DPP))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.publicationStatus").value("DRAFT"))
        .andExpect(jsonPath("$.content").doesNotExist());
    assertThat(resolveDppLevel()).isNotEqualTo(LegalTextSourceLevel.DEPARTMENT);
  }

  @Test
  void revertDataProtection_Should_reportNoChange_When_calledTwice() throws Exception {
    publish(DPP);
    mockMvc.perform(post(DPP + "/revert")).andExpect(status().isOk());

    mockMvc
        .perform(post(DPP + "/revert"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reverted").value(false));
  }

  @Test
  void revertImprint_Should_clearOwnText_And_stopTheDepartmentLevelFromAnswering()
      throws Exception {
    publish(IMPRINT);
    assertThat(resolver.resolveImprint(department()).sourceLevel())
        .isEqualTo(LegalTextSourceLevel.DEPARTMENT);

    mockMvc
        .perform(post(IMPRINT + "/revert"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reverted").value(true));

    mockMvc
        .perform(get(IMPRINT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.publicationStatus").value("DRAFT"))
        .andExpect(jsonPath("$.content").doesNotExist());
    assertThat(resolver.resolveImprint(department()).sourceLevel())
        .isNotEqualTo(LegalTextSourceLevel.DEPARTMENT);
  }

  @Test
  void revert_Should_return403_When_restrictedAdminDoesNotOwnTheAgency() throws Exception {
    publish(DPP);
    when(authenticatedUser.hasRestrictedAgencyPriviliges()).thenReturn(true);
    when(authenticatedUser.requireUserId()).thenReturn("admin-1");
    when(userAdminService.getAdminUserAgencyIds("admin-1")).thenReturn(List.of(9L));

    mockMvc.perform(post(DPP + "/revert")).andExpect(status().isForbidden());
    mockMvc.perform(post(IMPRINT + "/revert")).andExpect(status().isForbidden());
  }

  private void publish(String path) throws Exception {
    mockMvc
        .perform(put(path).contentType(MediaType.APPLICATION_JSON).content(PUBLISH_BODY))
        .andExpect(status().isOk());
  }

  private de.caritas.cob.agencyservice.api.repository.agencytopic.AgencyTopic department() {
    return agencyTopicRepository.findByAgency_IdAndTopicId(1L, 1L).orElseThrow();
  }

  private LegalTextSourceLevel resolveDppLevel() {
    return resolver.resolveDpp(department()).sourceLevel();
  }
}
