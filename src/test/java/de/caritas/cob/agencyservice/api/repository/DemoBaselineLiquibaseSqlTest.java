package de.caritas.cob.agencyservice.api.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class DemoBaselineLiquibaseSqlTest {

  @Test
  void sequenceSetvalCallsUseIntegerLiteralsRequiredByMariaDb() throws IOException {
    var migration = new ClassPathResource(
        "db/changelog/changeset/0025_demo_baseline/syncDemoBaselineAgencyVisibility.sql");
    var sql = migration.getContentAsString(StandardCharsets.UTF_8);

    // next_value must be an integer literal (a user variable raises MariaDB ERROR 1064), and
    // is_used must be 1 so the next NEXTVAL skips past the reserved baseline ids instead of
    // returning them and colliding with the demo rows (ERROR 1062). See DemoBaselineChangesetIT
    // for the behavioural guard against a real MariaDB.
    assertThat(sql)
        .doesNotContain("SETVAL(`agencyservice`.`sequence_agency_postcode_range`, @")
        .doesNotContain("SETVAL(`agencyservice`.`sequence_agency_topic`, @")
        .contains("SETVAL(`agencyservice`.`sequence_agency_postcode_range`, 900000001, 1)")
        .contains("SETVAL(`agencyservice`.`sequence_agency_topic`, 900000010, 1)");
  }
}
