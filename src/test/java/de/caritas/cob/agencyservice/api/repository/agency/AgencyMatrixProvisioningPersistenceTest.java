package de.caritas.cob.agencyservice.api.repository.agency;

import static org.assertj.core.api.Assertions.assertThat;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = "spring.profiles.active=testing")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AgencyMatrixProvisioningPersistenceTest {

  private static final long AGENCY_ID = 93751L;
  private static final String MATRIX_ID = "@synthetic-agency:matrix.test";
  private static final String ENCRYPTED_PASSWORD = "enc:synthetic-password";

  @Autowired
  @Qualifier("agencyRepository")
  private AgencyRepository repository;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void insertUsingLegacyColumnList() {
    jdbc.update("INSERT INTO agency (id, name, is_team_agency, consulting_type, is_offline,"
        + " is_external, create_date, update_date, data_protection_responsible_entity)"
        + " VALUES (?, 'Synthetic agency', 0, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,"
        + " 'AGENCY_RESPONSIBLE')", AGENCY_ID);
  }

  @AfterEach
  void cleanUp() {
    jdbc.update("DELETE FROM agency WHERE id = ?", AGENCY_ID);
  }

  @Test
  void commitsIdentityPasswordAndCompletionTogetherForMixedVersionReaders() {
    assertThat(storedAccount(jdbc)).isEqualTo(new StoredAccount(null, null, false));

    var updatedRows = new TransactionTemplate(transactionManager).execute(
        status -> repository.updateMatrixCredentials(AGENCY_ID, MATRIX_ID, ENCRYPTED_PASSWORD));

    assertThat(updatedRows).isEqualTo(1);
    assertThat(storedAccount(jdbc)).isEqualTo(new StoredAccount(MATRIX_ID, ENCRYPTED_PASSWORD, true));
    assertThat(jdbc.queryForObject("SELECT matrix_password FROM agency WHERE id = ?",
        String.class, AGENCY_ID)).isEqualTo(ENCRYPTED_PASSWORD);
  }

  @Test
  void missingAgencyUpdatesZeroRows() {
    var updatedRows = new TransactionTemplate(transactionManager).execute(
        status -> repository.updateMatrixCredentials(AGENCY_ID + 1, MATRIX_ID, ENCRYPTED_PASSWORD));

    assertThat(updatedRows).isZero();
    assertThat(storedAccount(jdbc)).isEqualTo(new StoredAccount(null, null, false));
  }

  @Test
  void unchangedLegacyWriterCanStillPersistAnIdentityAndPassword() {
    jdbc.update("UPDATE agency SET matrix_user_id = ?, matrix_password = ? WHERE id = ?",
        MATRIX_ID, ENCRYPTED_PASSWORD, AGENCY_ID);

    assertThat(storedAccount(jdbc)).isEqualTo(new StoredAccount(MATRIX_ID, ENCRYPTED_PASSWORD, false));
  }

  @Test
  void rollingBackAnInterruptedLocalWriteLeavesAllProvisioningFieldsUnchanged() {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      repository.updateMatrixCredentials(AGENCY_ID, MATRIX_ID, ENCRYPTED_PASSWORD);
      status.setRollbackOnly();
    });

    assertThat(storedAccount(jdbc)).isEqualTo(new StoredAccount(null, null, false));
  }

  @Test
  void additiveChangesetPreservesAnExistingLegacyPairAndAllowsTheOldInsert() throws Exception {
    var dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:agency_375_schema;DB_CLOSE_DELAY=-1", "sa", "");
    var legacy = new JdbcTemplate(dataSource);
    legacy.execute("CREATE TABLE agency (id BIGINT PRIMARY KEY, matrix_user_id VARCHAR(255),"
        + " matrix_password VARCHAR(255))");
    legacy.update("INSERT INTO agency (id, matrix_user_id, matrix_password) VALUES (?, ?, ?)",
        AGENCY_ID, MATRIX_ID, ENCRYPTED_PASSWORD);

    var liquibase = new SpringLiquibase();
    liquibase.setDataSource(dataSource);
    liquibase.setChangeLog(
        "classpath:db/changelog/changeset/0042_agency_matrix_provisioning/0042_changeSet.xml");
    liquibase.afterPropertiesSet();

    assertThat(storedAccount(legacy)).isEqualTo(new StoredAccount(MATRIX_ID, ENCRYPTED_PASSWORD, false));
    assertThat(legacy.update("INSERT INTO agency (id, matrix_user_id, matrix_password) VALUES (?, ?, ?)",
        AGENCY_ID + 1, MATRIX_ID, ENCRYPTED_PASSWORD)).isEqualTo(1);
  }

  private StoredAccount storedAccount(JdbcTemplate jdbc) {
    return jdbc.queryForObject(
        "SELECT matrix_user_id, matrix_password, matrix_account_provisioned FROM agency WHERE id = ?",
        (row, number) -> new StoredAccount(row.getString(1), row.getString(2), row.getBoolean(3)),
        AGENCY_ID);
  }

  private record StoredAccount(String matrixUserId, String password, boolean provisioned) {
  }
}
