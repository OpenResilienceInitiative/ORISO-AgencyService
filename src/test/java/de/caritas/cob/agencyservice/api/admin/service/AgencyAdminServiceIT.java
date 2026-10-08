package de.caritas.cob.agencyservice.api.admin.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;

import de.caritas.cob.agencyservice.AgencyServiceApplication;
import de.caritas.cob.agencyservice.api.exception.httpresponses.ConflictException;
import de.caritas.cob.agencyservice.api.repository.agencyidreservation.AgencyIdReservation;
import de.caritas.cob.agencyservice.api.repository.agencyidreservation.AgencyIdReservationRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import de.caritas.cob.agencyservice.api.service.TopicService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = AgencyServiceApplication.class)
@TestPropertySource(properties = "spring.profiles.active=testing")
@AutoConfigureTestDatabase(replace = Replace.ANY)
@DirtiesContext(classMode = ClassMode.BEFORE_CLASS)
@Transactional
@TestPropertySource(properties = "multitenancy.enabled=false")
@Sql(scripts = "/database/AgencyDatabase.sql")
public class AgencyAdminServiceIT extends AgencyAdminServiceITBase {

  private static final Long RESERVED_AGENCY_ID = 4711L;

  /**
   * Without this the suite calls the real ConsultingTypeService on localhost:8083 during
   * createAgency and fails with 401 (#204). Mirrors AgencyAdminServiceTenantAwareIT.
   */
  @MockitoBean
  private TopicService topicService;

  @Autowired private AgencyIdReservationRepository agencyIdReservationRepository;

  @Autowired private EntityManager entityManager;

  @Test
  public void saveAgency_Should_PersistsAgency() {
    super.saveAgency_Should_PersistsAgency();
  }

  /**
   * The load-bearing half of ORISO-Admin#998: {@code Agency} generates its ID from
   * {@code sequence_agency}, so the reserved-ID path has to write the row itself and let the
   * ordinary save become an UPDATE. This test proves against a real database that the agency ends
   * up under the reserved ID with all its fields — not under a freshly generated one.
   */
  @Test
  public void saveAgency_Should_CreateAgencyUnderReservedId_When_ReservationIsOpen() {
    agencyIdReservationRepository.saveAndFlush(
        AgencyIdReservation.newReservation(RESERVED_AGENCY_ID, 1L));

    var agencyDTO = createAgencyDTO();
    agencyDTO.setTenantId(1L);
    agencyDTO.setReservedAgencyId(RESERVED_AGENCY_ID);

    var response = agencyAdminService.createAgency(agencyDTO);

    assertThat(response.getEmbedded().getId(), is(RESERVED_AGENCY_ID));
    entityManager.flush();
    entityManager.clear();
    var agency = agencyRepository.findById(RESERVED_AGENCY_ID).orElseThrow();
    assertThat(agency.getName(), is("Agency name"));
    assertThat(agency.getPostCode(), is("12345"));
    assertThat(agency.getDescription(), is("Agency description"));
    assertThat(agency.isOffline(), is(true));
    // the reservation is consumed by the very same transaction that created the agency
    assertThat(agencyIdReservationRepository.existsById(RESERVED_AGENCY_ID), is(false));
  }

  @Test
  public void saveAgency_Should_ThrowConflictAndKeepReservation_When_ReservationBelongsToAnotherTenant() {
    // A reservation is tenant-bound: creating an agency for tenant 2 must not be able to burn
    // tenant 1's reservation. The reservation and the ID space have to survive the attempt.
    // the caller is tenant 1 (AUTHENTICATED_TENANT_ID); the reservation belongs to tenant 2
    agencyIdReservationRepository.saveAndFlush(
        AgencyIdReservation.newReservation(RESERVED_AGENCY_ID, 2L));

    var agencyDTO = createAgencyDTO();
    agencyDTO.setTenantId(2L);
    agencyDTO.setReservedAgencyId(RESERVED_AGENCY_ID);

    assertThrows(ConflictException.class, () -> agencyAdminService.createAgency(agencyDTO));

    entityManager.clear();
    assertThat(agencyIdReservationRepository.existsById(RESERVED_AGENCY_ID), is(true));
    assertThat(agencyRepository.findById(RESERVED_AGENCY_ID).isPresent(), is(false));
  }

  @Test
  public void saveAgency_Should_ThrowConflict_When_ReservedIdHasNoOpenReservation() {
    var agencyDTO = createAgencyDTO();
    agencyDTO.setTenantId(1L);
    agencyDTO.setReservedAgencyId(RESERVED_AGENCY_ID);

    assertThrows(ConflictException.class, () -> agencyAdminService.createAgency(agencyDTO));
  }

  @Test
  public void saveAgency_Should_ThrowConflict_When_ReservedIdIsAlreadyAnAgency() {
    // an ID that is ASSIGNED is never RESERVED as well (allocation contract), so the missing
    // reservation is what rejects the claim — the primary key never gets the chance to.
    var agencyDTO = createAgencyDTO();
    agencyDTO.setTenantId(1L);
    agencyDTO.setReservedAgencyId(1L);

    assertThrows(ConflictException.class, () -> agencyAdminService.createAgency(agencyDTO));
  }

  @Test
  public void saveAgency_Should_SetOfflineToTrue_WhenPersistsAgency() {
    super.saveAgency_Should_SetOfflineToTrue_WhenPersistsAgency();
  }

  @Test
  public void saveAgency_Should_ProvideValidAgencyLinks() {
    super.saveAgency_Should_ProvideValidAgencyLinks();
  }

  @Test
  public void updateAgency_Should_PersistsAgencyChanges() {
    super.updateAgency_Should_PersistsAgencyChanges();
  }

  @Test
  public void updateAgency_Should_ProvideValidAgencyLinks() {
    super.updateAgency_Should_ProvideValidAgencyLinks();
  }

  @Test
  public void getAgency_Should_returnExpectedAgency_When_agencyWithIdExists() {
    super.getAgency_Should_returnExpectedAgency_When_agencyWithIdExists();
  }

}
