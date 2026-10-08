package de.caritas.cob.agencyservice.api.service.matrix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.caritas.cob.agencyservice.api.exception.httpresponses.InternalServerErrorException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;

/** Exercises committed data and rollback on disposable MariaDB, without a test transaction. */
@Testcontainers
class AgencyMatrixPasswordBackfillIT {

  @Container
  static final MariaDBContainer database = new MariaDBContainer("mariadb:10.11")
      // Prove the migration refuses expansion even when MariaDB would silently truncate it.
      .withCommand("--sql-mode=");

  private final AgencyMatrixPasswordCipher cipher =
      new AgencyMatrixPasswordCipher("synthetic-backfill-test-key");
  private JdbcTemplate jdbc;
  private DataSourceTransactionManager transactionManager;

  @BeforeEach
  void createDatabase() {
    var dataSource = new DriverManagerDataSource(
        database.getJdbcUrl(), database.getUsername(), database.getPassword());
    jdbc = new JdbcTemplate(dataSource);
    transactionManager = new DataSourceTransactionManager(dataSource);
    jdbc.execute("DROP TABLE IF EXISTS agency");
    jdbc.execute("CREATE TABLE agency (id BIGINT PRIMARY KEY, tenant_id BIGINT,"
        + " matrix_user_id VARCHAR(255), matrix_password VARCHAR(255),"
        + " delete_date DATETIME) ENGINE=InnoDB");
  }

  @Test
  void migratesAllTenantsAndDeletedRowsAndPreservesCiphertextAndNullOnRepeatedRuns() {
    final String encrypted = cipher.encrypt("already-protected");
    seed(1, "legacy-password");
    seed(2, "");
    seed(3, " ");
    seed(4, encrypted);
    seed(5, null);
    jdbc.update("UPDATE agency SET delete_date = CURRENT_TIMESTAMP WHERE id = 3");

    runner(cipher).run(null);

    assertThat(cipher.decrypt(password(1))).isEqualTo("legacy-password");
    assertThat(cipher.decrypt(password(2))).isEmpty();
    assertThat(cipher.decrypt(password(3))).isEqualTo(" ");
    assertThat(password(4)).isEqualTo(encrypted);
    assertThat(password(5)).isNull();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agency WHERE matrix_password"
        + " IS NOT NULL AND matrix_password NOT LIKE 'enc:%'", Integer.class)).isZero();
    assertThat(jdbc.queryForList("SELECT matrix_user_id FROM agency", String.class))
        .containsOnly("@synthetic:example.invalid");
    List<String> firstRun = passwords();

    runner(cipher).run(null);

    assertThat(passwords()).isEqualTo(firstRun);
  }

  @Test
  void missingKeyFailsBeforeDatabaseAccessAndLeavesDataUntouched() {
    seed(1, "legacy-password");
    var missingKeyRunner = runner(new AgencyMatrixPasswordCipher(" "));

    assertThatThrownBy(() -> missingKeyRunner.run(null))
        .isInstanceOf(InternalServerErrorException.class)
        .hasMessage("Agency Matrix password encryption key is not configured");
    assertThat(password(1)).isEqualTo("legacy-password");

    // The missing-key diagnostic must precede even a SELECT against a broken schema.
    jdbc.execute("DROP TABLE agency");
    assertThatThrownBy(() -> missingKeyRunner.run(null))
        .hasMessage("Agency Matrix password encryption key is not configured");
  }

  @Test
  void databaseFailureOnSecondRowRollsBackFirstRowAndCanBeRetried() {
    seed(1, "first-password");
    seed(2, "second-password");
    jdbc.execute("CREATE TRIGGER reject_second BEFORE UPDATE ON agency FOR EACH ROW "
        + "BEGIN IF NEW.id = 2 THEN SIGNAL SQLSTATE '45000' "
        + "SET MESSAGE_TEXT = 'synthetic database failure'; END IF; END");

    assertThatThrownBy(() -> runner(cipher).run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Agency Matrix password backfill failed; verify committed state before retry")
        .hasNoCause();
    assertThat(passwords()).containsExactly("first-password", "second-password");

    jdbc.execute("DROP TRIGGER reject_second");
    runner(cipher).run(null);
    assertThat(cipher.decrypt(password(1))).isEqualTo("first-password");
    assertThat(cipher.decrypt(password(2))).isEqualTo("second-password");
    assertThat(password(1)).startsWith("enc:");
    assertThat(password(2)).startsWith("enc:");
  }

  @Test
  void onlyRegistersStartupRunnerWhenExplicitlyEnabled() {
    var contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(AgencyMatrixPasswordBackfill.class)
        .withBean(AgencyMatrixPasswordCipher.class, () -> cipher)
        .withBean(JdbcTemplate.class, () -> jdbc)
        .withBean(PlatformTransactionManager.class, () -> transactionManager);

    contextRunner.run(context -> assertThat(context)
        .doesNotHaveBean(AgencyMatrixPasswordBackfill.class));
    contextRunner.withPropertyValues("service.encryption.agency-matrix-backfill-enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(AgencyMatrixPasswordBackfill.class));
    seed(1, "legacy-password");
    contextRunner.withPropertyValues("service.encryption.agency-matrix-backfill-enabled=true")
        .run(context -> {
          assertThat(context).hasSingleBean(AgencyMatrixPasswordBackfill.class);
          context.getBean(AgencyMatrixPasswordBackfill.class).run(null);
        });
    assertThat(password(1)).startsWith("enc:");
  }

  @Test
  void refusesCiphertextExpansionBeyondColumnSizeAndRollsBackEarlierRows() {
    seed(1, "legacy-password");
    final String tooLong = "x".repeat(200);
    seed(2, tooLong);

    assertThatThrownBy(() -> runner(cipher).run(null))
        .hasMessage("Agency Matrix password backfill failed; verify committed state before retry")
        .hasNoCause();

    assertThat(passwords()).containsExactly("legacy-password", tooLong);
  }

  @Test
  void malformedExistingCiphertextRollsBackPlaintextUpdates() {
    seed(1, "legacy-password");
    seed(2, "enc:v2:unsupported");

    assertThatThrownBy(() -> runner(cipher).run(null))
        .hasMessage("Agency Matrix password backfill failed; verify committed state before retry")
        .hasNoCause();

    assertThat(passwords()).containsExactly("legacy-password", "enc:v2:unsupported");
  }

  @Test
  void doesNotClaimRollbackWhenCommitSucceededButItsAcknowledgementFailed() {
    seed(1, "legacy-password");
    var lostAcknowledgement = new DataSourceTransactionManager(transactionManager.getDataSource()) {
      @Override
      protected void doCommit(DefaultTransactionStatus status) {
        super.doCommit(status);
        // Fault injection after a real database commit, representing a lost commit response.
        throw new TransactionSystemException("synthetic driver detail must not escape");
      }
    };

    assertThatThrownBy(() -> new AgencyMatrixPasswordBackfill(cipher, jdbc, lostAcknowledgement)
        .run(null))
        .hasMessage("Agency Matrix password backfill failed; verify committed state before retry")
        .hasNoCause();
    assertThat(password(1)).startsWith("enc:");
    assertThat(cipher.decrypt(password(1))).isEqualTo("legacy-password");
    final String committedValue = password(1);
    runner(cipher).run(null);
    assertThat(password(1)).isEqualTo(committedValue);
  }

  @Test
  void aStaleEntitySaveAfterCommitCanRestorePlaintextSoOtherWritersMustBeStopped() {
    seed(1, "legacy-password");
    // This mirrors a JPA entity loaded before maintenance and saved afterwards without @Version.
    String staleEntityPassword = password(1);
    runner(cipher).run(null);
    assertThat(password(1)).startsWith("enc:");

    jdbc.update("UPDATE agency SET matrix_password = ? WHERE id = 1", staleEntityPassword);

    assertThat(password(1)).isEqualTo("legacy-password");
    // Row locks do not repair a stale save after commit. The operational guard is exclusive
    // maintenance; a subsequent isolated run can repair the value without double encryption.
    runner(cipher).run(null);
    assertThat(password(1)).startsWith("enc:");
    assertThat(cipher.decrypt(password(1))).isEqualTo("legacy-password");
  }

  @Test
  void locksRowsAndReadsTheCredentialCommittedByAConcurrentWriter() throws Exception {
    seed(1, "original-password");
    final String replacement = cipher.encrypt("concurrent-replacement");
    try (var connection = transactionManager.getDataSource().getConnection();
        var executor = Executors.newSingleThreadExecutor()) {
      connection.setAutoCommit(false);
      try (var lock = connection.prepareStatement("SELECT id FROM agency WHERE id = 1 FOR UPDATE")) {
        lock.executeQuery().close();
      }
      var migration = executor.submit(() -> runner(cipher).run(null));
      try {
        assertThatThrownBy(() -> migration.get(300, TimeUnit.MILLISECONDS))
            .isInstanceOf(TimeoutException.class);
        try (var update = connection.prepareStatement(
            "UPDATE agency SET matrix_password = ? WHERE id = 1")) {
          update.setString(1, replacement);
          update.executeUpdate();
        }
        connection.commit();
        migration.get(10, TimeUnit.SECONDS);
      } finally {
        connection.rollback();
      }
    }
    assertThat(password(1)).isEqualTo(replacement);
    assertThat(cipher.decrypt(password(1))).isEqualTo("concurrent-replacement");
  }

  private AgencyMatrixPasswordBackfill runner(AgencyMatrixPasswordCipher passwordCipher) {
    return new AgencyMatrixPasswordBackfill(passwordCipher, jdbc, transactionManager);
  }

  private void seed(long id, String password) {
    jdbc.update("INSERT INTO agency (id, tenant_id, matrix_user_id, matrix_password)"
        + " VALUES (?, ?, '@synthetic:example.invalid', ?)", id, id, password);
  }

  private String password(long id) {
    return jdbc.queryForObject("SELECT matrix_password FROM agency WHERE id = ?", String.class, id);
  }

  private List<String> passwords() {
    return jdbc.queryForList("SELECT matrix_password FROM agency ORDER BY id", String.class);
  }
}
