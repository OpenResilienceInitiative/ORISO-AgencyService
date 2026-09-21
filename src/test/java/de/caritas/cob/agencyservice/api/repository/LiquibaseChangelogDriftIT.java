package de.caritas.cob.agencyservice.api.repository;

import static org.assertj.core.api.Assertions.assertThat;

import de.caritas.cob.agencyservice.AgencyServiceApplication;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import de.caritas.cob.agencyservice.api.workflow.AgencyPurgeTransaction;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;

/**
 * Permanent drift guard for the consolidated Liquibase changelog (Liquibase Re-Enablement
 * Plan 2026-07-04, package L1 "changelog truth").
 *
 * <p>Boots the full application context against a <em>fresh, empty</em> MariaDB, lets Liquibase
 * build the schema from {@code db/changelog/agencyservice-master.xml} using the {@code seed}
 * context, and then lets Hibernate run {@code ddl-auto=validate} against the JPA entities. If the
 * changelog and the entity model ever drift apart (a column added to an entity without a matching
 * changeset, a wrong type, etc.), the Hibernate {@code validate} step fails the context load and
 * this test goes red.
 *
 * <p>This is the exact failure mode that {@code spring.jpa.hibernate.ddl-auto=update} used to hide
 * on dev by silently mutating the schema. With ddl-auto set to {@code validate}, that class of gap
 * is now caught here.
 *
 * <p>The required CI contract owns this test and starts an isolated MariaDB 10.11 container. It
 * therefore cannot report green by skipping when a manually supplied database URL is absent.
 */
@SpringBootTest(classes = AgencyServiceApplication.class)
@ActiveProfiles("dev")
@Testcontainers
@TestPropertySource(
    properties = {
      "spring.datasource.driver-class-name=org.mariadb.jdbc.Driver",
      "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MariaDBDialect",
      // Liquibase owns the schema; run the consolidated master with the fresh-DB seed context.
      "spring.liquibase.enabled=true",
      "spring.liquibase.change-log=classpath:db/changelog/agencyservice-master.xml",
      // Defaults to the fresh-DB "seed" path; set LIQUIBASE_IT_CONTEXTS= (empty) to exercise
      // the staging/prod path (structural changesets only, against a pre-existing base schema).
      "spring.liquibase.contexts=${LIQUIBASE_IT_CONTEXTS:seed}",
      // The whole point: Hibernate must find the entity model already satisfied by the changelog.
      "spring.jpa.hibernate.ddl-auto=validate",
      "multitenancy.enabled=false",
      // The dev profile activates ConfigurationValidator (@Profile("!testing")), which fails
      // startup unless every required service URL/secret is non-empty. These are placeholders
      // only; this test never calls those services.
      "keycloak.auth-server-url=http://localhost:8080",
      "keycloak.realm=test",
      "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8080/realms/test",
      "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8080/realms/test/protocol/openid-connect/certs",
      "matrix.api-url=http://localhost:8008",
      "matrix.registration-shared-secret=test-secret",
      "matrix.server-name=localhost",
      "matrix.admin-username=admin",
      "matrix.admin-password=admin",
      "consulting.type.service.api.url=http://localhost:8083",
      "tenant.service.api.url=http://localhost:8089",
      "user.admin.service.api.url=http://localhost:8082"
    })
class LiquibaseChangelogDriftIT {

  @Container
  static final MariaDBContainer mariaDb =
      new MariaDBContainer("mariadb:10.11").withDatabaseName("agencyservice");

  @DynamicPropertySource
  static void mariaDbProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", mariaDb::getJdbcUrl);
    registry.add("spring.datasource.username", mariaDb::getUsername);
    registry.add("spring.datasource.password", mariaDb::getPassword);
  }

  @Autowired private DataSource dataSource;

  @Autowired private AgencyRepository agencyRepository;

  @Autowired private AgencyPurgeTransaction agencyPurgeTransaction;

  /**
   * If the Spring context loads, Liquibase built the schema and Hibernate validated every entity
   * against it. Reaching the assertion means no drift.
   */
  @Test
  void changelogMatchesJpaEntities_onFreshDatabase() {
    assertThat(dataSource).isNotNull();
  }

  /**
   * Drafts are agency-owned dependants. The database cascade must let the scheduled agency purge
   * remove them without teaching the purge workflow about this table, while drafts belonging to
   * another agency remain untouched.
   */
  @Test
  void agencyPurge_Should_cascadeOnlyThePurgedAgencysLegalDrafts() {
    var jdbc = new JdbcTemplate(dataSource);
    long purgedAgencyId = 9_900_001L;
    long retainedAgencyId = 9_900_002L;

    try {
      insertAgency(jdbc, purgedAgencyId, "Purged draft owner");
      insertAgency(jdbc, retainedAgencyId, "Retained draft owner");
      insertDraft(jdbc, "purged-draft", purgedAgencyId);
      insertDraft(jdbc, "retained-draft", retainedAgencyId);

      agencyPurgeTransaction.purge(agencyRepository.findById(purgedAgencyId).orElseThrow());

      assertThat(count(jdbc, "agency", purgedAgencyId)).isZero();
      assertThat(count(jdbc, "agency_legal_draft", purgedAgencyId)).isZero();
      assertThat(count(jdbc, "agency", retainedAgencyId)).isOne();
      assertThat(count(jdbc, "agency_legal_draft", retainedAgencyId)).isOne();
    } finally {
      jdbc.update("DELETE FROM agency WHERE id IN (?, ?)", purgedAgencyId, retainedAgencyId);
    }
  }

  private static void insertAgency(JdbcTemplate jdbc, long agencyId, String name) {
    jdbc.update(
        "INSERT INTO agency (id, name, id_old, consulting_type) VALUES (?, ?, ?, ?)",
        agencyId,
        name,
        agencyId,
        0);
  }

  private static void insertDraft(JdbcTemplate jdbc, String rowId, long agencyId) {
    jdbc.update(
        "INSERT INTO agency_legal_draft "
            + "(row_id, agency_id, kind, content, consent_text, version, saved_at) "
            + "VALUES (?, ?, 'DPP', '{}', '{}', 0, UTC_TIMESTAMP(6))",
        rowId,
        agencyId);
  }

  private static long count(JdbcTemplate jdbc, String table, long agencyId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE " + (table.equals("agency") ? "id" : "agency_id") + " = ?",
        Long.class,
        agencyId);
  }
}
