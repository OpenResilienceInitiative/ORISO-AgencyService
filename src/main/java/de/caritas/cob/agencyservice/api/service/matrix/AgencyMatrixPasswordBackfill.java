package de.caritas.cob.agencyservice.api.service.matrix;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Opt-in, application-side migration of legacy passwords across every tenant, including deleted rows. */
@Component
@ConditionalOnProperty(
    name = "service.encryption.agency-matrix-backfill-enabled", havingValue = "true")
@Slf4j
public class AgencyMatrixPasswordBackfill implements ApplicationRunner {

  private final AgencyMatrixPasswordCipher cipher;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transaction;

  public AgencyMatrixPasswordBackfill(AgencyMatrixPasswordCipher cipher, JdbcTemplate jdbc,
      PlatformTransactionManager transactionManager) {
    this.cipher = cipher;
    this.jdbc = jdbc;
    this.transaction = new TransactionTemplate(transactionManager);
    this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public void run(ApplicationArguments args) {
    // Fail before even opening a transaction. No weak/default key or partial migration is allowed.
    try {
      cipher.requireApplicationKey();
    } catch (RuntimeException exception) {
      log.error("Agency Matrix password backfill refused: encryption key is not configured");
      throw exception;
    }
    final Integer migrated;
    try {
      migrated = transaction.execute(status -> {
        // Native SQL intentionally bypasses request-scoped tenant and soft-delete filters.
        // Lock in ID order to serialize current database writes. Stale entity saves still require
        // all other AgencyService writers to be drained/stopped; see the maintenance runbook.
        var passwords = jdbc.query("SELECT id, matrix_password FROM agency"
            + " WHERE matrix_password IS NOT NULL ORDER BY id FOR UPDATE",
            (row, number) -> new StoredPassword(row.getLong("id"), row.getString("matrix_password")));
        int changed = 0;
        for (var password : passwords) {
          // Validate existing enc: values as well: rejected format/padding must abort the
          // transaction. Legacy ECB cannot authenticate ciphertext or reliably detect a wrong key.
          String encrypted = cipher.encrypt(password.value());
          if (encrypted.length() > 255) {
            // MariaDB can silently truncate in non-strict mode; fail before sending any value.
            throw new IllegalStateException("Encrypted password exceeds the storage column");
          }
          if (!encrypted.equals(password.value())) {
            jdbc.update("UPDATE agency SET matrix_password = ? WHERE id = ?",
                encrypted, password.agencyId());
            changed++;
          }
        }
        return changed;
      });
    } catch (RuntimeException exception) {
      // Driver errors can contain bound values. Never propagate/log those causes or credentials.
      log.error("Agency Matrix password backfill failed; verify committed state before retry");
      throw new IllegalStateException(
          "Agency Matrix password backfill failed; verify committed state before retry");
    }
    // TransactionTemplate returns only after commit, so this count describes committed changes.
    log.info("Agency Matrix password backfill committed: {} row(s) encrypted", migrated);
  }

  private record StoredPassword(long agencyId, String value) {
  }
}
