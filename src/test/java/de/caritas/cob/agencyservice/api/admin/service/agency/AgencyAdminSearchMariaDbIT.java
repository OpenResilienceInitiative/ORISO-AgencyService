package de.caritas.cob.agencyservice.api.admin.service.agency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.AgencyServiceApplication;
import de.caritas.cob.agencyservice.api.service.TenantService;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import de.caritas.cob.agencyservice.api.model.Sort;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;

/** Actual migrated MariaDB, not H2's different text semantics. */
@SpringBootTest(classes = AgencyServiceApplication.class)
@ActiveProfiles("testing")
@Testcontainers
@TestPropertySource(properties = {
    "spring.datasource.driver-class-name=org.mariadb.jdbc.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MariaDBDialect",
    "spring.liquibase.enabled=true",
    "spring.liquibase.contexts=seed",
    "spring.jpa.hibernate.ddl-auto=validate",
    "multitenancy.enabled=false",
    "feature.topics.enabled=false"})
class AgencyAdminSearchMariaDbIT {
  @Container
  static final MariaDBContainer MARIA_DB = new MariaDBContainer("mariadb:10.11.18")
      .withDatabaseName("agencyservice");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MARIA_DB::getJdbcUrl);
    registry.add("spring.datasource.username", MARIA_DB::getUsername);
    registry.add("spring.datasource.password", MARIA_DB::getPassword);
  }

  @Autowired private AgencyAdminSearchService search;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private AuthenticatedUser user;
  @MockitoBean private TenantService tenantService;
  @MockitoBean private de.caritas.cob.agencyservice.api.service.TopicEnrichmentService enrichment;

  @BeforeEach
  void fixtures() {
    when(user.getTenantId()).thenReturn(71L);
    when(tenantService.getRestrictedTenantDataByTenantIds(anyCollection()))
        .thenReturn(List.of(new RestrictedTenantDTO().id(71L).name("Träger Éclair")));
    insert(980001L, "Müller", "Caféstraße", "Köln");
    insert(980002L, "Müller", "Caféstraße", "Köln");
    insert(980003L, "Other %_!", null, null);
  }

  void insert(long id, String name, String street, String city) {
    jdbc.update("insert into agency (id,id_old,tenant_id,name,street,city,postcode,consulting_type) "
        + "values (?,?,71,?,?,?,'12345',0)", id, id, name, street, city);
  }

  @AfterEach
  void cleanup() {
    jdbc.update("delete from agency where id between 980001 and 980003");
  }

  @Test
  void composedAndDecomposedAccentsMatchAcrossAllLocalFieldsBeforePaging() {
    var sort = new Sort().field(Sort.FieldEnum.ID).order(Sort.OrderEnum.ASC);
    for (String q : List.of("MULLER cafestraße KOLN", "müller caféstraße köln")) {
      var page = search.searchAgencies(q, 2, 1, sort);
      assertThat(page.getTotal()).isEqualTo(2);
      assertThat(page.getEmbedded()).extracting(row -> row.getEmbedded().getId())
          .containsExactly(980002L);
    }
    assertThat(search.searchAgencies("980001 12345 eclair", 1, 10, sort).getTotal()).isEqualTo(1);
  }

  @Test
  void wildcardCharactersRemainLiteralAndSchemaCollationsAreMeasured() {
    for (String q : List.of("%", "_", "!", "%_!")) {
      var page = search.searchAgencies(q, 1, 10, null);
      assertThat(page.getTotal()).isEqualTo(1);
      assertThat(page.getEmbedded()).extracting(row -> row.getEmbedded().getId())
          .containsExactly(980003L);
    }
    var collations = jdbc.queryForList("select column_name,collation_name "
        + "from information_schema.columns where table_schema=database() and table_name='agency' "
        + "and column_name in ('name','street','postcode','city')");
    assertThat(collations).hasSize(4);
    System.out.println("Agency search migrated column collations: " + collations);
  }
}
