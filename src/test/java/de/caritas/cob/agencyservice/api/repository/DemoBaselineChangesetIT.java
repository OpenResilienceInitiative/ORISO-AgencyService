package de.caritas.cob.agencyservice.api.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import de.caritas.cob.agencyservice.AgencyServiceApplication;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;

/**
 * Regression guard for the opt-in demo baseline changeset
 * ({@code db/changelog/changeset/0025_demo_baseline}, Liquibase context {@code demo-baseline}).
 *
 * <p>The consolidated {@link LiquibaseChangelogDriftIT} only runs the {@code seed} context, so the
 * {@code demo-baseline} changeset was never exercised by any test. Two real MariaDB-only defects
 * slipped through as a result:
 *
 * <ol>
 *   <li><b>Invalid MariaDB syntax</b> — the original changeset ended with
 *       {@code DO SETVAL(seq, @variable, 0)}. MariaDB's {@code SETVAL} rejects a user variable as
 *       its {@code next_value} argument with {@code ERROR 1064}, so AgencyService crashed on the
 *       first restart that actually applied the changeset. H2 (the {@code testing} profile) never
 *       runs Liquibase, so unit tests stayed green.</li>
 *   <li><b>Sequence collision</b> — after switching to a literal, {@code SETVAL(seq, id, 0)}
 *       (is_used = 0) makes the next {@code NEXTVAL} return the <em>reserved</em> baseline id, so
 *       the following application insert failed with {@code ERROR 1062 Duplicate entry}. The fix
 *       passes {@code is_used = 1} so {@code NEXTVAL} skips past the reserved ids.</li>
 * </ol>
 *
 * <p>This test boots the app against a <em>fresh, empty</em> MariaDB with the
 * {@code seed,demo-baseline} contexts (so the base schema and the demo baseline are both built by
 * Liquibase), then asserts the baseline landed and that a sequence-driven insert does not collide.
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
      // Liquibase owns the schema; build the base schema (seed) AND the opt-in demo baseline.
      "spring.liquibase.enabled=true",
      "spring.liquibase.change-log=classpath:db/changelog/agencyservice-master.xml",
      "spring.liquibase.contexts=seed,demo-baseline",
      // Hibernate schema validation is the drift IT's job; here we only care that Liquibase runs.
      "spring.jpa.hibernate.ddl-auto=none",
      "multitenancy.enabled=false",
      // The dev profile activates ConfigurationValidator (@Profile("!testing")); placeholders only.
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
class DemoBaselineChangesetIT {

  @Container
  static final MariaDBContainer mariaDb =
      new MariaDBContainer("mariadb:10.11").withDatabaseName("agencyservice");

  @DynamicPropertySource
  static void mariaDbProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", mariaDb::getJdbcUrl);
    registry.add("spring.datasource.username", mariaDb::getUsername);
    registry.add("spring.datasource.password", mariaDb::getPassword);
  }

  /** The demo agency id reserved by the baseline changeset (0025). */
  private static final long DEMO_AGENCY_ID = 246L;

  @Autowired private DataSource dataSource;

  /**
   * Reaching this test means the {@code demo-baseline} changeset applied without an
   * {@code ERROR 1064} (invalid SETVAL syntax). We then assert the reserved agency exists and that
   * an application-style insert driven by the AgencyService sequence does not collide with the
   * reserved baseline id ({@code ERROR 1062}), which would happen with {@code SETVAL(..., 0)}.
   */
  @Test
  void demoBaseline_applies_andSequenceDoesNotCollideWithReservedIds() throws Exception {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {

      long demoAgencyCount = queryCount(statement, "SELECT COUNT(*) FROM agency WHERE id = " + DEMO_AGENCY_ID);
      assertThat(demoAgencyCount)
          .as("demo baseline agency (0025) must be present after the demo-baseline context ran")
          .isEqualTo(1L);

      // The is_used=1 fix: the next value from the sequence must skip past the reserved baseline
      // id, so this insert must NOT raise "Duplicate entry" on the primary key.
      assertThatCode(() ->
              statement.executeUpdate(
                  "INSERT INTO agency_postcode_range (id, agency_id, postcode_from, postcode_to) "
                      + "VALUES (NEXTVAL(sequence_agency_postcode_range), "
                      + DEMO_AGENCY_ID
                      + ", '10000', '20000')"))
          .as("a sequence-driven insert must not collide with the reserved demo baseline id")
          .doesNotThrowAnyException();
    }
  }

  private static long queryCount(Statement statement, String sql) throws Exception {
    try (ResultSet resultSet = statement.executeQuery(sql)) {
      resultSet.next();
      return resultSet.getLong(1);
    }
  }
}
