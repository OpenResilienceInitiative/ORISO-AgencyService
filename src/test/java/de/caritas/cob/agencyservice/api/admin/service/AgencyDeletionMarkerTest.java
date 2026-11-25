package de.caritas.cob.agencyservice.api.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import de.caritas.cob.agencyservice.api.service.AppointmentService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgencyDeletionMarkerTest {

  private static final long AGENCY_ID = 7L;
  private static final long TENANT_ID = 2L;

  @Mock private EntityManager entityManager;
  @Mock private AgencyRepository agencyRepository;
  @Mock private DeletionAuditReporter deletionAuditReporter;
  @Mock private AppointmentService appointmentService;
  @InjectMocks private AgencyDeletionMarker marker;

  @Test
  void mark_Should_takeTheAgencyOfflineAndMarkIt_thenReport() {
    var agency = agency(null);
    when(entityManager.find(Agency.class, AGENCY_ID, LockModeType.PESSIMISTIC_WRITE))
        .thenReturn(agency);

    assertThat(marker.mark(AGENCY_ID)).isTrue();

    assertThat(agency.getDeleteDate()).isNotNull();
    assertThat(agency.isOffline()).isTrue();
    var order = inOrder(agencyRepository, deletionAuditReporter, appointmentService);
    order.verify(agencyRepository).save(agency);
    order
        .verify(deletionAuditReporter)
        .reportAgency(AGENCY_ID, TENANT_ID, DeletionAuditReporter.Outcome.SCHEDULED);
    order.verify(appointmentService).deleteAgency(agency);
  }

  @Test
  void mark_Should_keepTheFirstMarkAndReportNothing_When_theAgencyIsAlreadyInDeletion() {
    var firstMark = LocalDateTime.of(2026, 11, 20, 8, 0);
    var agency = agency(firstMark);
    when(entityManager.find(Agency.class, AGENCY_ID, LockModeType.PESSIMISTIC_WRITE))
        .thenReturn(agency);

    assertThat(marker.mark(AGENCY_ID)).isFalse();

    assertThat(agency.getDeleteDate()).isEqualTo(firstMark);
    verifyNoInteractions(agencyRepository, deletionAuditReporter, appointmentService);
  }

  @Test
  void mark_Should_stillReport_When_theAppointmentSyncFails() {
    var agency = agency(null);
    when(entityManager.find(Agency.class, AGENCY_ID, LockModeType.PESSIMISTIC_WRITE))
        .thenReturn(agency);
    doThrow(new IllegalStateException("appointment service down"))
        .when(appointmentService)
        .deleteAgency(agency);

    assertThatThrownBy(() -> marker.mark(AGENCY_ID)).isInstanceOf(IllegalStateException.class);

    verify(deletionAuditReporter)
        .reportAgency(AGENCY_ID, TENANT_ID, DeletionAuditReporter.Outcome.SCHEDULED);
  }

  @Test
  void mark_Should_throwNotFound_When_theAgencyIsGone() {
    assertThatThrownBy(() -> marker.mark(AGENCY_ID)).isInstanceOf(NotFoundException.class);
  }

  private static Agency agency(LocalDateTime deleteDate) {
    var agency = new Agency();
    agency.setId(AGENCY_ID);
    agency.setTenantId(TENANT_ID);
    agency.setDeleteDate(deleteDate);
    return agency;
  }
}
