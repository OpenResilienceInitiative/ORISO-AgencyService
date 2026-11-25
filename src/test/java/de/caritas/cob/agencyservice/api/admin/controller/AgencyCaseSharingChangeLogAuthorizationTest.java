package de.caritas.cob.agencyservice.api.admin.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.caritas.cob.agencyservice.api.admin.service.DeletionAuditReporter;
import de.caritas.cob.agencyservice.api.admin.service.UserAdminService;
import de.caritas.cob.agencyservice.api.service.AppointmentService;
import de.caritas.cob.agencyservice.api.service.TenantService;
import de.caritas.cob.agencyservice.api.service.TopicEnrichmentService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

/**
 * ORISO-Frontend#2494: the case sharing change log. Real JWT authorities, tenant resolution and
 * database; only remote boundaries are mocked. Scope mirrors the agency search
 * ({@link AgencyAdminTenantSearchAuthorizationTest}).
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
class AgencyCaseSharingChangeLogAuthorizationTest {

  private static final String SUBJECT = "tenant-admin-subject";
  private static final String PATH = "/agencyadmin/agencies/case-sharing-changes";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean private JwtDecoder jwtDecoder;
  @MockitoBean private TenantService tenantService;
  @MockitoBean private TopicEnrichmentService topicEnrichmentService;
  @MockitoBean private UserAdminService userAdminService;
  @MockitoBean private AppointmentService appointmentService;
  @MockitoBean private DeletionAuditReporter deletionAuditReporter;

  @BeforeEach
  void seedChanges() {
    jdbcTemplate.update("DELETE FROM AGENCY_CASE_SHARING_CHANGE");
    insert(9001, 1, "2026-11-20 10:00:00", "admin-a", null, "CASE_HANDOVER", "AGENCY_SETTINGS");
    insert(9002, 1, "2026-11-20 11:00:00", "admin-b", "CASE_HANDOVER", "NONE", "AGENCY_SETTINGS");
    insert(9003, 2, "2026-11-20 12:00:00", "admin-c", "NONE", "TEAM_COUNSELLING", "AGENCY_TYPE");
    insert(9001, 1, "2026-11-20 13:00:00", null, "CASE_HANDOVER", "TEAM_COUNSELLING",
        "AGENCY_TYPE");
  }

  @Test
  void tenantAdmin_Should_SeeOwnTenantNewestFirst() throws Exception {
    tokenFor(1L, "tenant-admin");

    mvc.perform(get(PATH).header("Authorization", "Bearer token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("total").value(3))
        .andExpect(jsonPath("entries.length()").value(3))
        .andExpect(jsonPath("entries[0].agencyId").value(9001))
        .andExpect(jsonPath("entries[0].agencyName").value("Zebrafink Mitte"))
        .andExpect(jsonPath("entries[0].tenantId").value(1))
        .andExpect(jsonPath("entries[0].changedAt").value("2026-11-20T13:00:00Z"))
        .andExpect(jsonPath("entries[0].previousModel").value("CASE_HANDOVER"))
        .andExpect(jsonPath("entries[0].newModel").value("TEAM_COUNSELLING"))
        .andExpect(jsonPath("entries[0].source").value("AGENCY_TYPE"))
        .andExpect(jsonPath("entries[1].agencyId").value(9002))
        .andExpect(jsonPath("entries[1].changedBy").value("admin-b"))
        .andExpect(jsonPath("entries[2].agencyId").value(9001))
        .andExpect(jsonPath("entries[2].changedAt").value("2026-11-20T10:00:00Z"))
        // Contract: unknown values are explicit nulls, not missing keys.
        .andExpect(content().string(containsString("\"changedBy\":null")))
        .andExpect(content().string(containsString("\"previousModel\":null")));
    verifyNoInteractions(userAdminService);
  }

  @Test
  void historyOfAReusedAgencyId_Should_NotLeakIntoTheNewCentre() throws Exception {
    // Agency id 9001 belonged to tenant 2 before a purge and now belongs to tenant 1; a second
    // row predates the current 9001's creation (2026-09-01) within tenant 1.
    insert(9001, 2, "2026-11-21 09:00:00", "foreign-admin", "NONE", "TEAM_COUNSELLING",
        "AGENCY_SETTINGS");
    insert(9001, 1, "2026-08-01 09:00:00", "former-admin", "NONE", "CASE_HANDOVER",
        "AGENCY_SETTINGS");

    tokenFor(1L, "tenant-admin");
    mvc.perform(get(PATH).param("agencyId", "9001").header("Authorization", "Bearer token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("total").value(2))
        .andExpect(jsonPath("entries[0].changedAt").value("2026-11-20T13:00:00Z"))
        .andExpect(jsonPath("entries[1].changedAt").value("2026-11-20T10:00:00Z"))
        .andExpect(content().string(org.hamcrest.Matchers.not(containsString("foreign-admin"))))
        .andExpect(content().string(org.hamcrest.Matchers.not(containsString("former-admin"))));

    tokenFor(0L, "agency-admin", "tenant-admin");
    mvc.perform(get(PATH).header("Authorization", "Bearer token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("total").value(4))
        .andExpect(content().string(org.hamcrest.Matchers.not(containsString("foreign-admin"))))
        .andExpect(content().string(org.hamcrest.Matchers.not(containsString("former-admin"))));
  }

  @Test
  void creationRow_Should_BeServedWithSourceAgencyCreated() throws Exception {
    insert(9002, 1, "2026-11-22 08:00:00", "admin-b", null, "NONE", "AGENCY_CREATED");
    tokenFor(1L, "tenant-admin");

    mvc.perform(get(PATH).param("agencyId", "9002").header("Authorization", "Bearer token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("entries[0].source").value("AGENCY_CREATED"))
        .andExpect(jsonPath("entries[0].newModel").value("NONE"))
        .andExpect(content().string(containsString("\"previousModel\":null")));
  }

  @Test
  void agencyFilter_Should_NarrowToThatAgency() throws Exception {
    tokenFor(1L, "tenant-admin");

    mvc.perform(get(PATH).param("agencyId", "9001").header("Authorization", "Bearer token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("total").value(2))
        .andExpect(jsonPath("entries[0].agencyId").value(9001))
        .andExpect(jsonPath("entries[0].changedAt").value("2026-11-20T13:00:00Z"))
        .andExpect(jsonPath("entries[1].agencyId").value(9001))
        .andExpect(jsonPath("entries[1].changedAt").value("2026-11-20T10:00:00Z"));
  }

  @Test
  void agencyFilter_Should_NotWidenScope_When_AgencyBelongsToAnotherTenant() throws Exception {
    tokenFor(1L, "tenant-admin");

    mvc.perform(get(PATH).param("agencyId", "9003").header("Authorization", "Bearer token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("total").value(0))
        .andExpect(jsonPath("entries").isEmpty());
  }

  @Test
  void paging_Should_BeZeroBasedWithTotalOfAllPages() throws Exception {
    tokenFor(1L, "tenant-admin");

    mvc.perform(get(PATH).param("page", "1").param("perPage", "1")
            .header("Authorization", "Bearer token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("total").value(3))
        .andExpect(jsonPath("entries.length()").value(1))
        .andExpect(jsonPath("entries[0].agencyId").value(9002));
  }

  @Test
  void perPage_Should_BeRejected_When_AboveHundred() throws Exception {
    tokenFor(1L, "tenant-admin");

    mvc.perform(get(PATH).param("perPage", "101").header("Authorization", "Bearer token"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void restrictedAgencyAdmin_Should_SeeOnlyAdministeredAgencies() throws Exception {
    tokenFor(1L, "restricted-agency-admin");
    when(userAdminService.getAdminUserAgencyIds(SUBJECT)).thenReturn(List.of(9002L, 9003L));

    mvc.perform(get(PATH).header("Authorization", "Bearer token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("total").value(1))
        .andExpect(jsonPath("entries[0].agencyId").value(9002));
  }

  @Test
  void platformAdmin_Should_SeeAllTenants() throws Exception {
    tokenFor(0L, "agency-admin", "tenant-admin");

    mvc.perform(get(PATH).header("Authorization", "Bearer token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("total").value(4))
        .andExpect(jsonPath("entries[1].agencyId").value(9003));
  }

  @Test
  void tenantZeroWithoutPlatformRoles_Should_NotSeeAnyTenant() throws Exception {
    tokenFor(0L, "tenant-admin");

    mvc.perform(get(PATH).header("Authorization", "Bearer token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("total").value(0));
  }

  @Test
  void callerWithoutSearchAuthority_Should_BeForbidden() throws Exception {
    tokenFor(1L, "consultant");

    mvc.perform(get(PATH).header("Authorization", "Bearer token"))
        .andExpect(status().isForbidden());

    tokenFor(1L, "user");
    mvc.perform(get(PATH).header("Authorization", "Bearer token"))
        .andExpect(status().isForbidden());
  }

  private void insert(long agencyId, long tenantId, String changedAt, String by, String previous,
      String next, String source) {
    jdbcTemplate.update(
        "INSERT INTO AGENCY_CASE_SHARING_CHANGE (AGENCY_ID, TENANT_ID, CHANGED_AT, "
            + "CHANGED_BY_USER_ID, CHANGED_BY_NAME, PREVIOUS_MODEL, NEW_MODEL, SOURCE) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        agencyId, tenantId, java.sql.Timestamp.valueOf(changedAt), by == null ? null : "id-" + by,
        by, previous, next, source);
  }

  private void tokenFor(long tenantId, String... roles) {
    Jwt jwt = Jwt.withTokenValue("token")
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
