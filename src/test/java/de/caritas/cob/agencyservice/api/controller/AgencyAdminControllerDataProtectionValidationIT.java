package de.caritas.cob.agencyservice.api.controller;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.caritas.cob.agencyservice.api.admin.service.UserAdminService;
import de.caritas.cob.agencyservice.api.manager.consultingtype.ConsultingTypeManager;
import de.caritas.cob.agencyservice.api.model.DataProtectionContactDTO;
import de.caritas.cob.agencyservice.api.model.DataProtectionDTO;
import de.caritas.cob.agencyservice.api.model.UpdateAgencyDTO;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import de.caritas.cob.agencyservice.api.service.TenantService;
import de.caritas.cob.agencyservice.api.service.TopicEnrichmentService;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import de.caritas.cob.agencyservice.api.util.JsonConverter;
import de.caritas.cob.agencyservice.consultingtypeservice.generated.web.model.ExtendedConsultingTypeResponseDTO;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.Settings;
import de.caritas.cob.agencyservice.testHelper.PathConstants;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Data-protection validation on the agency update path, with the ADR-003 requirement switched ON.
 *
 * <p>The {@code testing} profile sets {@code agency.department.require-dpo-contact=false} — the
 * deliberate "dev mode" from ADR-003 that lets the other suites create agencies without a complete
 * DPO contact. That switch made the rule unreachable, which is why this assertion sat red in the
 * #185 quarantine: it expected a 400 from a validator that returns early under the profile it runs
 * in. It was a test-configuration defect, not the production validation gap #209 suspected.
 *
 * <p>Pinning the property to {@code true} here is what production actually runs, so the rule is now
 * covered at IT level for the first time. Kept as its own class because the property is
 * context-level: flipping it inside {@link AgencyAdminControllerIT} would change every other test
 * in that suite.
 */
@SpringBootTest
@ActiveProfiles("testing")
@TestPropertySource(properties = {"feature.topics.enabled=false",
    "agency.department.require-dpo-contact=true"})
@AutoConfigureMockMvc(addFilters = false)
@Transactional
@Sql(scripts = "/database/AgencyDatabase.sql")
class AgencyAdminControllerDataProtectionValidationIT {

  private static final String CSRF_TOKEN = "test";

  private MockMvc mockMvc;

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private AgencyRepository agencyRepository;

  @MockitoBean
  private ConsultingTypeManager consultingTypeManager;

  @MockitoBean
  private TopicEnrichmentService topicEnrichmentService;

  @MockitoBean
  private AuthenticatedUser authenticatedUser;

  @MockitoBean
  private UserAdminService userAdminService;

  @MockitoBean
  private TenantService tenantService;

  @BeforeEach
  void setup() throws Exception {
    TenantContext.clear();
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    when(consultingTypeManager.getConsultingTypeSettings(anyInt()))
        .thenReturn(new ExtendedConsultingTypeResponseDTO());
    when(authenticatedUser.getTenantId()).thenReturn(1L);
    // Central data protection ON: the branch that delegates to the validation service.
    when(tenantService.getRestrictedTenantDataByTenantId(Mockito.any()))
        .thenReturn(new RestrictedTenantDTO()
            .settings(new Settings().featureCentralDataProtectionTemplateEnabled(true)));
  }

  private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder) {
    return builder.cookie(new Cookie("CSRF-TOKEN", CSRF_TOKEN)).header("X-CSRF-TOKEN", CSRF_TOKEN);
  }

  private UpdateAgencyDTO updateWithDataProtection(DataProtectionDTO dataProtection) {
    return new UpdateAgencyDTO()
        .name("Test update name")
        .description(null)
        .offline(true)
        .external(false)
        .dataProtection(dataProtection);
  }

  @Test
  @WithMockUser(authorities = "AUTHORIZATION_AGENCY_ADMIN")
  void updateAgency_Should_returnStatusBadRequest_When_CentralDataProtectionIsEnabled_And_PayloadContainsInvalidDataProtectionContent()
      throws Exception {
    var agencyDTO = updateWithDataProtection(new DataProtectionDTO()
        .dataProtectionResponsibleEntity(
            DataProtectionDTO.DataProtectionResponsibleEntityEnum.DATA_PROTECTION_OFFICER)
        .dataProtectionOfficerContact(new DataProtectionContactDTO()));

    mockMvc.perform(withCsrf(put(PathConstants.UPDATE_DELETE_AGENCY_PATH)
            .contentType(APPLICATION_JSON)
            .content(JsonConverter.convertToJson(agencyDTO))))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(authorities = "AUTHORIZATION_AGENCY_ADMIN")
  void updateAgency_Should_returnSuccess_When_DataProtectionOfficerContactIsComplete()
      throws Exception {
    var agencyDTO = updateWithDataProtection(new DataProtectionDTO()
        .dataProtectionResponsibleEntity(
            DataProtectionDTO.DataProtectionResponsibleEntityEnum.DATA_PROTECTION_OFFICER)
        .dataProtectionOfficerContact(new DataProtectionContactDTO()
            .nameAndLegalForm("Caritasverband e. V.")
            .city("Mainz")
            .postcode("55116")
            .email("datenschutz@example.org")));

    mockMvc.perform(withCsrf(put(PathConstants.UPDATE_DELETE_AGENCY_PATH)
            .contentType(APPLICATION_JSON)
            .content(JsonConverter.convertToJson(agencyDTO))))
        .andExpect(status().isOk());

    var savedAgency = agencyRepository.findById(1L).orElseThrow();
    org.junit.jupiter.api.Assertions.assertEquals("Test update name", savedAgency.getName());
  }
}
