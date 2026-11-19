import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

class AgencyRollbackProof {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(args[0]);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Use the disposable MariaDB service port");
        }
        String url = "jdbc:mariadb://127.0.0.1:" + port
                + "/agency_rollback_proof?connectTimeout=5000&socketTimeout=10000";
        try (var connection = DriverManager.getConnection(url, "agency_proof", "synthetic-agency-proof")) {
            var metadata = connection.getMetaData();
            String version = metadata.getDatabaseProductVersion();
            if (!"MariaDB".equals(metadata.getDatabaseProductName()) || !version.matches("11\\.0\\.6(?:[-+].*)?")) {
                throw new AssertionError("Expected MariaDB 11.0.6, got " + version);
            }
            System.out.println("Native MariaDB " + version + " with JDBC " + metadata.getDriverVersion());
            exec(connection, """
                    CREATE TABLE agency (
                        id BIGINT PRIMARY KEY,
                        matrix_user_id VARCHAR(255),
                        matrix_password VARCHAR(255)
                    ) ENGINE=InnoDB
                    """);
            exec(connection, """
                    INSERT INTO agency VALUES
                        (1, '@legacy:example.invalid', 'synthetic-retained'),
                        (2, '@partial:example.invalid', NULL),
                        (3, NULL, NULL)
                    """);
            var database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            var migration = new Liquibase(
                    "db/changelog/changeset/0042_agency_matrix_provisioning/0042_changeSet.xml",
                    new ClassLoaderResourceAccessor(), database);
            migration.update(new Contexts(), new LabelExpression());
            expectMarkers(connection, List.of("1:false", "2:false", "3:false"));

            connection.setAutoCommit(false);
            exec(connection, """
                    UPDATE agency SET matrix_user_id = '@new:example.invalid',
                        matrix_password = 'synthetic-new', matrix_account_provisioned = true
                    WHERE id = 3
                    """);
            exec(connection, """
                    INSERT INTO agency (id, matrix_user_id, matrix_password)
                    VALUES (4, '@mixed:example.invalid', 'synthetic-mixed')
                    """);
            connection.commit();
            connection.setAutoCommit(true);
            expectMarkers(connection, List.of("1:false", "2:false", "3:true", "4:false"));
            var legacyView = legacyRows(connection);
            if (!legacyView.equals(List.of(
                    "1|@legacy:example.invalid|synthetic-retained",
                    "2|@partial:example.invalid|null",
                    "3|@new:example.invalid|synthetic-new",
                    "4|@mixed:example.invalid|synthetic-mixed"))) {
                throw new AssertionError("Unexpected compatibility view");
            }

            migration.rollback(1, new Contexts(), new LabelExpression());
            try (var statement = connection.createStatement(); var result = statement.executeQuery("""
                    SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
                        AND table_name = 'agency' AND column_name = 'matrix_account_provisioned'
                    """)) {
                if (!result.next() || result.getInt(1) != 0) {
                    throw new AssertionError("Rollback left the completion column behind");
                }
            }
            if (!legacyView.equals(legacyRows(connection))) {
                throw new AssertionError("Rollback changed a legacy provisioning value");
            }
            migration.update(new Contexts(), new LabelExpression());
            expectMarkers(connection, List.of("1:false", "2:false", "3:false", "4:false"));
            if (!legacyView.equals(legacyRows(connection))) {
                throw new AssertionError("Replay changed a legacy provisioning value");
            }
            System.out.println("Applied, rolled back and reapplied the committed changeset. "
                    + "All four synthetic IDs and retained values survived.");
        }
    }

    static void exec(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    static List<String> legacyRows(Connection connection) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(
                "SELECT id, matrix_user_id, matrix_password FROM agency ORDER BY id")) {
            var rows = new ArrayList<String>();
            while (result.next()) {
                rows.add(result.getLong(1) + "|" + result.getString(2) + "|" + result.getString(3));
            }
            return rows;
        }
    }

    static void expectMarkers(Connection connection, List<String> expected) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(
                "SELECT id, matrix_account_provisioned FROM agency ORDER BY id")) {
            var actual = new ArrayList<String>();
            while (result.next()) {
                actual.add(result.getLong(1) + ":" + result.getBoolean(2));
            }
            if (!expected.equals(actual)) {
                throw new AssertionError(actual.toString());
            }
        }
    }
}
