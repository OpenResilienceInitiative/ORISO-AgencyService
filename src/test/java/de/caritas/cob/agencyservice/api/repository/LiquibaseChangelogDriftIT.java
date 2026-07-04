package de.caritas.cob.agencyservice.api.repository;

import static org.assertj.core.api.Assertions.assertThat;

import de.caritas.cob.agencyservice.AgencyServiceApplication;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

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
 * <p>The project does not (yet) depend on Testcontainers, so the test is gated on the
 * {@code LIQUIBASE_IT_DB_URL} environment variable. Provide a fresh MariaDB and run it, e.g.:
 *
 * <pre>
 *   docker run -d --name l1-agency-mariadb -p 3313:3306 \
 *     -e MARIADB_ROOT_PASSWORD=root -e MARIADB_DATABASE=agencyservice mariadb:10.11
 *
 *   LIQUIBASE_IT_DB_URL="jdbc:mariadb://127.0.0.1:3313/agencyservice" \
 *   LIQUIBASE_IT_DB_USERNAME=root \
 *   LIQUIBASE_IT_DB_PASSWORD=root \
 *   ./mvnw -Dtest=LiquibaseChangelogDriftIT -DfailIfNoTests=false test
 * </pre>
 *
 * <p>When the variable is absent (normal CI/local unit runs) the test is skipped, so it never
 * breaks the H2-based {@code testing} profile build.
 */
@SpringBootTest(classes = AgencyServiceApplication.class)
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "LIQUIBASE_IT_DB_URL", matches = ".+")
@TestPropertySource(
    properties = {
      // Real MariaDB instead of any embedded replacement.
      "spring.datasource.url=${LIQUIBASE_IT_DB_URL}",
      "spring.datasource.username=${LIQUIBASE_IT_DB_USERNAME:root}",
      "spring.datasource.password=${LIQUIBASE_IT_DB_PASSWORD:root}",
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

  @Autowired private DataSource dataSource;

  /**
   * If the Spring context loads, Liquibase built the schema and Hibernate validated every entity
   * against it. Reaching the assertion means no drift.
   */
  @Test
  void changelogMatchesJpaEntities_onFreshDatabase() {
    assertThat(dataSource).isNotNull();
  }
}
