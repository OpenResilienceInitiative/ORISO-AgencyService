package de.caritas.cob.agencyservice.api.service.matrix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Verifies that the application's JPA transaction manager enlists the runner's native JDBC writes. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({AgencyMatrixPasswordBackfill.class, AgencyMatrixPasswordCipher.class})
@TestPropertySource(properties = {
    "spring.profiles.active=testing",
    "service.encryption.agency-matrix-backfill-enabled=true"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AgencyMatrixPasswordBackfillJpaTest {

  @Autowired private AgencyMatrixPasswordBackfill backfill;
  @Autowired private AgencyMatrixPasswordCipher cipher;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;

  @AfterEach
  void cleanUp() {
    jdbc.update("DELETE FROM agency WHERE id IN (901, 902)");
  }

  @Test
  void commitsUsingAutoConfiguredJpaAndJdbcWiring() {
    assertThat(transactionManager).isInstanceOf(JpaTransactionManager.class);
    seed(901, "synthetic-password");

    backfill.run(null);

    assertThat(password(901)).startsWith("enc:");
    assertThat(cipher.decrypt(password(901))).isEqualTo("synthetic-password");
  }

  @Test
  void rollsBackNativeJdbcWritesWhenJpaTransactionFails() {
    seed(901, "synthetic-password");
    seed(902, "x".repeat(200));

    assertThatThrownBy(() -> backfill.run(null))
        .hasMessage("Agency Matrix password backfill failed; verify committed state before retry")
        .hasNoCause();

    assertThat(password(901)).isEqualTo("synthetic-password");
    assertThat(password(902)).isEqualTo("x".repeat(200));
  }

  private void seed(long id, String password) {
    jdbc.update("INSERT INTO agency (id, name, is_team_agency, consulting_type, is_offline,"
        + " is_external, create_date, update_date, data_protection_responsible_entity, matrix_password)"
        + " VALUES (?, 'Synthetic agency', 0, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,"
        + " 'AGENCY_RESPONSIBLE', ?)", id, password);
  }

  private String password(long id) {
    return jdbc.queryForObject("SELECT matrix_password FROM agency WHERE id = ?", String.class, id);
  }
}
