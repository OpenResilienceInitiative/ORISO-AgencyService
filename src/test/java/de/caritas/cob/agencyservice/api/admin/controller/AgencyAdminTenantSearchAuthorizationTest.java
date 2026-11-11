package de.caritas.cob.agencyservice.api.admin.controller;

import static de.caritas.cob.agencyservice.testHelper.TestConstants.VALID_AGENCY_DTO;
import static de.caritas.cob.agencyservice.testHelper.TestConstants.VALID_AGENCY_UPDATE_DTO;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.caritas.cob.agencyservice.api.admin.service.DeletionAuditReporter;
import de.caritas.cob.agencyservice.api.admin.service.UserAdminService;
import de.caritas.cob.agencyservice.api.service.AppointmentService;
import de.caritas.cob.agencyservice.api.service.TenantService;
import de.caritas.cob.agencyservice.api.service.TopicEnrichmentService;
import de.caritas.cob.agencyservice.testHelper.TenantPolicyFixture;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

/**
 * Real JWT authorities, tenant resolution and database search; only remote boundaries are mocked.
 * Named Test so the standard blocking Maven test execution checks this permission boundary.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = Replace.ANY)
@TestPropertySource(properties = {
    "spring.profiles.active=testing",
    "multitenancy.enabled=true",
    "feature.multitenancy.with.single.domain.enabled=true",
    "feature.topics.enabled=false"
})
@Sql(scripts = {"/database/AgencyDatabase.sql", "/database/AgencyPickerSearch.sql"})
class AgencyAdminTenantSearchAuthorizationTest {

  private static final String SUBJECT = "tenant-admin-subject";
  private static final String SEARCH_PATH = "/agencyadmin/agencies";
  private static final String POSTCODE_PATH = "/agencyadmin/postcoderanges/";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean private JwtDecoder jwtDecoder;
  @MockitoBean private TenantService tenantService;
  @MockitoBean private TopicEnrichmentService topicEnrichmentService;
  @MockitoBean private UserAdminService userAdminService;
  @MockitoBean private AppointmentService appointmentService;
  @MockitoBean private DeletionAuditReporter deletionAuditReporter;

  @BeforeEach
  void tenantMetadata() {
    when(tenantService.getRestrictedTenantDataByTenantId(1L))
        .thenReturn(new RestrictedTenantDTO().id(1L).name("Träger Köln"));
    when(tenantService.getRestrictedTenantDataForPermissionPolicies(1L))
        .thenReturn(TenantPolicyFixture.projection(1L, Map.of()));
  }

  @Test
  void search_Should_ReturnOnlyOwnCentres_When_TokenHasOnlyTenantAdminRole() throws Exception {
    tokenFor(1L, "tenant-admin");

    mvc.perform(get(SEARCH_PATH)
            .param("q", "zebrafink")
            .param("page", "1")
            .param("perPage", "50")
            .header("Authorization", "Bearer tenant-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("total").value(3))
        .andExpect(jsonPath("_embedded[*]._embedded.id")
            .value(containsInAnyOrder(9001, 9002, 9004)))
        .andExpect(jsonPath("_embedded[*]._embedded.tenantId").value(everyItem(is(1))));
    verifyNoInteractions(userAdminService);
  }

  @Test
  void search_Should_ReturnNoForeignCentres_When_TenantAdminSuppliesAnotherTenantId()
      throws Exception {
    tokenFor(1L, "tenant-admin");

    mvc.perform(get(SEARCH_PATH)
            .param("tenantId", "2")
            .param("page", "1")
            .param("perPage", "50")
            .header("Authorization", "Bearer tenant-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("total").value(0))
        .andExpect(jsonPath("_embedded").isEmpty());
  }

  @Test
  void search_Should_NotWidenTenantZero_When_TokenLacksPlatformAdminRole() throws Exception {
    tokenFor(0L, "tenant-admin");

    mvc.perform(get(SEARCH_PATH)
            .param("q", "zebrafink")
            .param("page", "1")
            .param("perPage", "50")
            .header("Authorization", "Bearer tenant-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("total").value(0))
        .andExpect(jsonPath("_embedded").isEmpty());
  }

  @Test
  void search_Should_KeepAssignedCentreScope_When_TenantAdminAlsoHasRestrictedRole()
      throws Exception {
    tokenFor(1L, "tenant-admin", "restricted-agency-admin");
    when(userAdminService.getAdminUserAgencyIds(SUBJECT)).thenReturn(List.of(9002L, 9003L));

    mvc.perform(get(SEARCH_PATH)
            .param("q", "zebrafink")
            .param("page", "1")
            .param("perPage", "50")
            .header("Authorization", "Bearer tenant-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("total").value(1))
        .andExpect(jsonPath("_embedded[0]._embedded.id").value(9002))
        .andExpect(jsonPath("_embedded[0]._embedded.tenantId").value(1));
    verify(userAdminService, atLeastOnce()).getAdminUserAgencyIds(SUBJECT);
  }

  @Test
  void detail_Should_RequireAgencyAssignmentForRestrictedAdmin() throws Exception {
    tokenFor(1L, "restricted-agency-admin");
    when(userAdminService.getAdminUserAgencyIds(SUBJECT)).thenReturn(List.of(9002L));

    mvc.perform(get(SEARCH_PATH + "/9002").header("Authorization", "Bearer tenant-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("_embedded.id").value(9002));

    mvc.perform(get(SEARCH_PATH + "/9001").header("Authorization", "Bearer tenant-token"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("_embedded").doesNotExist());
  }

  @Test
  void postcodeRanges_Should_RequireAgencyAssignmentForRestrictedAdmin() throws Exception {
    tokenFor(1L, "restricted-agency-admin");
    when(userAdminService.getAdminUserAgencyIds(SUBJECT)).thenReturn(List.of(9002L));
    seedPostcodeRanges();

    mvc.perform(get(POSTCODE_PATH + "9002").header("Authorization", "Bearer tenant-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("_embedded.postcodeRanges").value("51063;"));

    mvc.perform(get(POSTCODE_PATH + "9001").header("Authorization", "Bearer tenant-token"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("_embedded").doesNotExist());
  }

  @Test
  void detail_Should_PreserveBroaderRole_When_AdminHasBothAgencyRoles() throws Exception {
    tokenFor(1L, "agency-admin", "restricted-agency-admin");

    mvc.perform(get(SEARCH_PATH + "/9001").header("Authorization", "Bearer tenant-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("_embedded.id").value(9001));

    mvc.perform(get(POSTCODE_PATH + "9001").header("Authorization", "Bearer tenant-token"))
        .andExpect(status().isOk());
    verifyNoInteractions(userAdminService);
  }

  @ParameterizedTest
  @ValueSource(strings = {"POST", "PUT", "DELETE"})
  void postcodeMutations_Should_RequireAgencyAssignmentForRestrictedAdmin(String method)
      throws Exception {
    tokenFor(1L, "restricted-agency-admin");
    when(userAdminService.getAdminUserAgencyIds(SUBJECT)).thenReturn(List.of(9002L));
    seedPostcodeRanges();

    mvc.perform(postcodeMutation(method, 9002L))
        .andExpect("POST".equals(method) ? status().isCreated() : status().isOk());

    mvc.perform(postcodeMutation(method, 9001L))
        .andExpect(status().isForbidden());

    tokenFor(1L, "agency-admin");
    mvc.perform(get(POSTCODE_PATH + "9001").header("Authorization", "Bearer tenant-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("_embedded.postcodeRanges").value("51061;"));
  }

  @ParameterizedTest
  @ValueSource(longs = {9001L, 9002L})
  void deleteAgency_Should_RequireFullAgencyAdminAuthority(long agencyId) throws Exception {
    tokenFor(1L, "restricted-agency-admin");
    when(userAdminService.getAdminUserAgencyIds(SUBJECT)).thenReturn(List.of(9002L));
    if (agencyId == 9001L) {
      when(userAdminService.getConsultantsOfAgency(agencyId, 1, 1))
          .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
    }

    mvc.perform(delete(SEARCH_PATH + "/" + agencyId).header("Authorization", "Bearer tenant-token"))
        .andExpect(status().isForbidden());

    assertEquals(1, jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM AGENCY WHERE ID = ? AND DELETE_DATE IS NULL", Integer.class, agencyId));
    verifyNoInteractions(userAdminService, appointmentService, deletionAuditReporter);
  }

  private MockHttpServletRequestBuilder postcodeMutation(String method, long agencyId) {
    var mutation = request(HttpMethod.valueOf(method), POSTCODE_PATH + agencyId)
        .header("Authorization", "Bearer tenant-token");
    if (!"DELETE".equals(method)) {
      mutation.contentType(MediaType.APPLICATION_JSON)
          .content("{\"postcodeRanges\":\"60001-60003\"}");
    }
    return mutation;
  }

  private void seedPostcodeRanges() {
    jdbcTemplate.update("""
        INSERT INTO AGENCY_POSTCODE_RANGE (ID, TENANT_ID, AGENCY_ID, POSTCODE_FROM, POSTCODE_TO)
        VALUES (99001, 1, 9001, '51061', '51061'), (99002, 1, 9002, '51063', '51063')
        """);
  }

  @Test
  void create_Should_StayForbidden_When_TokenHasOnlyTenantAdminRole() throws Exception {
    tokenFor(1L, "tenant-admin");

    mvc.perform(post(SEARCH_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_AGENCY_DTO)
            .header("Authorization", "Bearer tenant-token"))
        .andExpect(status().isForbidden());
  }

  @Test
  void update_Should_StayForbidden_When_TokenHasOnlyTenantAdminRole() throws Exception {
    tokenFor(1L, "tenant-admin");

    mvc.perform(put(SEARCH_PATH + "/9001")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_AGENCY_UPDATE_DTO)
            .header("Authorization", "Bearer tenant-token"))
        .andExpect(status().isForbidden());
  }

  @Test
  void delete_Should_StayForbidden_When_TokenHasOnlyTenantAdminRole() throws Exception {
    tokenFor(1L, "tenant-admin");

    mvc.perform(delete(SEARCH_PATH + "/9001")
            .header("Authorization", "Bearer tenant-token"))
        .andExpect(status().isForbidden());
  }

  private void tokenFor(long tenantId, String... roles) {
    Jwt jwt = Jwt.withTokenValue("tenant-token")
        .header("alg", "none")
        .subject(SUBJECT)
        .claim("username", "tenant-admin")
        .claim("preferred_username", "tenant-admin")
        .claim("tenantId", tenantId)
        .claim("realm_access", Map.of("roles", List.of(roles)))
        .build();
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);
  }
}
