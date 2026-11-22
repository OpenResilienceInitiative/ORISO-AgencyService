package de.caritas.cob.agencyservice.api.workflow;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class AgencyPurgeRunLock {

  // MariaDB lock names are shared by all databases on its server.
  private static final String LOCK_NAME = "CONCAT('oriso-agency-purge:', LEFT(SHA2(DATABASE(), 256), 40))";
  private static final String ACQUIRE = "SELECT GET_LOCK(" + LOCK_NAME + ", 0)";
  private static final String RELEASE = "SELECT RELEASE_LOCK(" + LOCK_NAME + ")";

  private final @NonNull DataSource dataSource;

  void runIfAcquired(Runnable workflow) {
    try {
      var pool = dataSource.unwrap(HikariDataSource.class);
      // Keep the MariaDB session checked out for the claim. Each purge still uses its own transaction.
      try (var connection = pool.getConnection()) {
        if (pool.getMaximumPoolSize() < 2) {
          throw new IllegalStateException("Agency purge requires a database pool of at least two connections");
        }
        if (executeLockCommand(pool, connection, ACQUIRE, true) == 0) {
          log.info("Agency deletion workflow skipped because another replica holds the database lock.");
          return;
        }
        try (Claim ignored = () -> executeLockCommand(pool, connection, RELEASE, false)) {
          workflow.run();
        }
      }
    } catch (SQLException exception) {
      throw new IllegalStateException("Could not coordinate the agency deletion workflow", exception);
    }
  }

  private int executeLockCommand(
      HikariDataSource pool, Connection connection, String sql, boolean allowContention)
      throws SQLException {
    try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
      if (result.next()) {
        int value = result.getInt(1);
        if (!result.wasNull() && (value == 1 || (allowContention && value == 0))) {
          return value;
        }
      }
      throw new SQLException("MariaDB could not confirm the agency purge lock operation");
    } catch (SQLException exception) {
      // Returning a pooled session with uncertain lock state could keep other replicas out.
      pool.evictConnection(connection);
      throw exception;
    }
  }

  @FunctionalInterface
  private interface Claim extends AutoCloseable {
    @Override
    void close() throws SQLException;
  }
}
