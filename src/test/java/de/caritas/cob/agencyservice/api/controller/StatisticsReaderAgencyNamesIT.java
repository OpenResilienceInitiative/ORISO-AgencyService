package de.caritas.cob.agencyservice.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.caritas.cob.agencyservice.AgencyServiceApplication;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The Statistik-Leser (realm role {@code statistics-reader}, platform tenant 0) reads the names of
 * every Beratungsstelle across all Träger for the statistics panel, and nothing else
 * (ORISO-UserService#1666). Multitenancy is on, so the tenant filter and the {@code TenantContext}
 * run exactly as in production; the bearer token goes through the production converter.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = AgencyServiceApplication.class)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@TestPropertySource(properties = {
    "spring.profiles.active=testing", "multitenancy.enabled=true",
    "csrf.header.property=csrfHeader", "csrf.cookie.property=csrfCookie"
})
@Sql(value = {"/database/AgencyDatabase.sql", "/setTenants.sql"})
public class StatisticsReaderAgencyNamesIT {

  private static final String NAMES = "/agencies/statistics/names";
  private static final Cookie CSRF_COOKIE = new Cookie("csrfCookie", "test");

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean private JwtDecoder jwtDecoder;

  @After
  public void clearTenant() {
    TenantContext.clear();
  }

  @Test
  public void statisticsReader_Should_readEveryNonDeletedAgencyOfEveryTenantSortedByName()
      throws Exception {
    authenticate(0L, "statistics-reader");
    jdbcTemplate.update("UPDATE agency SET delete_date = CURRENT_TIMESTAMP WHERE id = 1735");

    var names = readNames(bearer(get(NAMES)));

    var expected = jdbcTemplate.queryForList(
        "SELECT id, name, tenant_id FROM agency WHERE delete_date IS NULL ORDER BY name, id");
    assertThat(names).hasSize(expected.size());
    assertThat(names).extracting(row -> ((Number) row.get("id")).longValue())
        .containsExactlyElementsOf(
            expected.stream().map(row -> ((Number) row.get("ID")).longValue()).toList());
    assertThat(names).extracting(row -> row.get("tenantId")).contains(1, 2);
    assertThat(names).allSatisfy(
        row -> assertThat(row).containsOnlyKeys("id", "name", "tenantId"));
    var deleted = jdbcTemplate.queryForList(
        "SELECT id FROM agency WHERE delete_date IS NOT NULL", Long.class);
    assertThat(deleted).containsExactly(1735L);
    assertThat(names).extracting(row -> ((Number) row.get("id")).longValue())
        .doesNotContainAnyElementsOf(deleted);
  }

  @Test
  public void statisticsReader_Should_notBeNarrowed_When_aTenantHeaderOrStaleContextIsPresent()
      throws Exception {
    authenticate(0L, "statistics-reader");
    TenantContext.setCurrentTenant(1L);
    var expected = jdbcTemplate.queryForObject(
        "SELECT count(*) FROM agency WHERE delete_date IS NULL", Integer.class);

    var names = readNames(bearer(get(NAMES)).header("tenantId", "1"));

    assertThat(names).hasSize(expected);
  }

  @Test
  public void platformAdmin_Should_readAgencyNames() throws Exception {
    authenticate(0L, "agency-admin", "tenant-admin", "user-admin");
    var expected = jdbcTemplate.queryForObject(
        "SELECT count(*) FROM agency WHERE delete_date IS NULL", Integer.class);

    assertThat(readNames(bearer(get(NAMES)))).hasSize(expected);
  }

  @Test
  public void statisticsReader_Should_beRefused_When_tokenNamesAnotherTenant() throws Exception {
    authenticate(1L, "statistics-reader");
    mvc.perform(bearer(get(NAMES)).header("tenantId", "1")).andExpect(status().isForbidden());
  }

  @Test
  public void everyOtherRole_Should_beRefused() throws Exception {
    for (var caller : List.of(
        new Caller(1L, List.of("agency-admin", "tenant-admin", "user-admin")),
        new Caller(1L, List.of("agency-admin")),
        new Caller(1L, List.of("restricted-agency-admin")),
        new Caller(1L, List.of("restricted-consultant-admin")),
        new Caller(1L, List.of("consultant")),
        new Caller(1L, List.of("user")),
        new Caller(0L, List.of("agency-admin")),
        new Caller(0L, List.of("tenant-admin")))) {
      authenticate(caller.tenantId(), caller.roles().toArray(String[]::new));
      var status = mvc.perform(bearer(get(NAMES)).header("tenantId", caller.tenantId().toString()))
          .andReturn().getResponse().getStatus();
      assertThat(status).as("GET names as %s", caller).isEqualTo(403);
    }
  }

  @Test
  public void anonymous_Should_beUnauthorized() throws Exception {
    mvc.perform(get(NAMES).header("tenantId", "1")).andExpect(status().isUnauthorized());
  }

  @Test
  public void statisticsReader_Should_beRefusedOnEveryOtherAgencyAdminEndpoint() throws Exception {
    authenticate(0L, "statistics-reader");
    for (String path : List.of(
        "/agencyadmin/agencies?q=*",
        "/agencyadmin/agencies?q=*&page=1&perPage=10",
        "/agencyadmin/agencies/1735",
        "/agencyadmin/agencies/tenant/1",
        "/agencyadmin/controls",
        "/agencyadmin/legaltexts",
        "/agencies/by-tenant")) {
      var status = mvc.perform(bearer(get(path))).andReturn().getResponse().getStatus();
      assertThat(status).as("GET %s as statistics reader", path).isEqualTo(403);
    }
    mvc.perform(csrf(bearer(post("/agencyadmin/agencies")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(csrf(bearer(put("/agencyadmin/agencies/1735")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
        .andExpect(status().isForbidden());
  }

  private List<Map<String, Object>> readNames(MockHttpServletRequestBuilder request)
      throws Exception {
    var body = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse()
        .getContentAsString();
    return new ObjectMapper().readValue(body, new TypeReference<>() {});
  }

  private void authenticate(Long tenantId, String... roles) {
    var realmRoles = new ArrayList<>(List.of(roles));
    realmRoles.add("default-roles-online-beratung");
    var jwt = Jwt.withTokenValue("test-token").header("alg", "none")
        .claim("sub", "caller-subject").claim("username", "caller")
        .claim("tenantId", tenantId).claim("realm_access", Map.of("roles", realmRoles))
        .build();
    when(jwtDecoder.decode(anyString())).thenReturn(jwt);
  }

  private static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request) {
    return request.header("Authorization", "Bearer test-token");
  }

  private static MockHttpServletRequestBuilder csrf(MockHttpServletRequestBuilder request) {
    return request.cookie(CSRF_COOKIE).header("csrfHeader", "test");
  }

  private record Caller(Long tenantId, List<String> roles) {}
}
