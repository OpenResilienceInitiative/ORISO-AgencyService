package de.caritas.cob.agencyservice.api.admin.service.allocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.caritas.cob.agencyservice.api.exception.httpresponses.ConflictException;
import de.caritas.cob.agencyservice.api.repository.agencyidreservation.AgencyIdReservationRepository;
import de.caritas.cob.agencyservice.api.service.TenantService;
import de.caritas.cob.agencyservice.api.tenant.TenantAspect;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant-scope integration test for the shared agency ID allocation contract (TEN-INV-U2).
 *
 * <p>The agency ID space is global, but on multi-tenant deployments ({@code
 * multitenancy.enabled=true}) the {@link TenantAspect} enables the Hibernate {@code tenantFilter}
 * before every non-{@code @TenantUnaware} repository call. This test acts as a tenant-scoped
 * agency admin (tenant 2) and proves that agencies ASSIGNED to another tenant (tenant 1) are
 * still authoritative for every allocation decision — they must never be reported FREE or become
 * manually reservable.
 */
@TestPropertySource(properties = {
    "spring.profiles.active=testing",
    "multitenancy.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ExtendWith(SpringExtension.class)
@DataJpaTest
@Import({AgencyIdAllocationService.class, TenantAspect.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AgencyIdAllocationServiceTenantScopeIT {

  private static final long OTHER_TENANT_ID = 1L;
  private static final long CURRENT_TENANT_ID = 2L;

  @Autowired private AgencyIdAllocationService allocationService;

  @Autowired private AgencyIdReservationRepository reservationRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private TenantService tenantService;

  @TestConfiguration
  @EnableAspectJAutoProxy
  static class EnableAspectsConfig {
  }

  @BeforeEach
  void actAsTenantScopedAdmin() {
    TenantContext.setCurrentTenant(CURRENT_TENANT_ID);
  }

  @AfterEach
  void cleanUp() {
    TenantContext.clear();
    jdbcTemplate.update("DELETE FROM agency_id_reservation");
    jdbcTemplate.update("DELETE FROM agency");
  }

  private void seedAgencyForTenant(long id, long tenantId) {
    jdbcTemplate.update(
        "INSERT INTO agency (id, tenant_id, name, is_team_agency, consulting_type, is_offline,"
            + " is_external, create_date, update_date, data_protection_responsible_entity)"
            + " VALUES (?, ?, 'Seeded agency', 0, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,"
            + " 'AGENCY_RESPONSIBLE')",
        id, tenantId);
  }

  @Test
  void checkAvailability_Should_reportAssigned_When_agencyBelongsToAnotherTenant() {
    seedAgencyForTenant(5L, OTHER_TENANT_ID);

    assertThat(allocationService.checkAvailability(5L)).isEqualTo(AgencyIdStatus.ASSIGNED);
  }

  @Test
  void reserve_Should_rejectManualRequest_When_idIsAssignedToAnotherTenantsAgency() {
    seedAgencyForTenant(5L, OTHER_TENANT_ID);

    assertThatThrownBy(() -> allocationService.reserve(5L, null))
        .isInstanceOf(ConflictException.class);
    assertThat(reservationRepository.existsById(5L)).isFalse();
  }

  @Test
  void reserve_Should_skipAnotherTenantsAgencies_When_autoModeIsRequested() {
    seedAgencyForTenant(1L, OTHER_TENANT_ID);
    seedAgencyForTenant(2L, OTHER_TENANT_ID);
    seedAgencyForTenant(3L, CURRENT_TENANT_ID);

    var reservedId = allocationService.reserve(null, null);

    assertThat(reservedId).isEqualTo(4L);
  }
}
