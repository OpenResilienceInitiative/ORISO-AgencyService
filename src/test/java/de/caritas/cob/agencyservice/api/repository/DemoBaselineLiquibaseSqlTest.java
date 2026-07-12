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

    assertThat(sql)
        .doesNotContain("SETVAL(`agencyservice`.`sequence_agency_postcode_range`, @")
        .doesNotContain("SETVAL(`agencyservice`.`sequence_agency_topic`, @")
        .contains("SETVAL(`agencyservice`.`sequence_agency_postcode_range`, 900000001, 0)")
        .contains("SETVAL(`agencyservice`.`sequence_agency_topic`, 900000010, 0)");
  }
}
