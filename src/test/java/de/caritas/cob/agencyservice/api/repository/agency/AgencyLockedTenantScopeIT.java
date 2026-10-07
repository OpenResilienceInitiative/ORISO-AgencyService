package de.caritas.cob.agencyservice.api.repository.agency;

import static org.assertj.core.api.Assertions.assertThat;

import de.caritas.cob.agencyservice.api.tenant.TenantAspect;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@TestPropertySource(properties = {"spring.profiles.active=testing", "multitenancy.enabled=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(TenantAspect.class)
@Sql(scripts = "/database/AgencyDatabase.sql")
class AgencyLockedTenantScopeIT {

  @Autowired private AgencyTenantAwareRepository repository;

  @TestConfiguration
  @EnableAspectJAutoProxy
  static class EnableAspectsConfig {
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void lockedAgencyLookup_respectsCurrentTenant() {
    TenantContext.setCurrentTenant(1L);

    assertThat(repository.findLockedById(1735L)).isPresent();
    assertThat(repository.findLockedById(1738L)).isEmpty();
  }
}
