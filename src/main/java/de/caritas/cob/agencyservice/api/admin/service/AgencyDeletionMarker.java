package de.caritas.cob.agencyservice.api.admin.service;

import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import de.caritas.cob.agencyservice.api.service.AppointmentService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marks an agency for deletion and takes it offline at once (ORISO-Admin#1524, rule 2 of the
 * deletion epic ORISO-Admin#1520). Its own bean so the mark is committed before UserService is
 * asked to delete what belongs to the agency: UserService takes the last counsellor off an agency
 * only once it is offline.
 */
@Component
@RequiredArgsConstructor
public class AgencyDeletionMarker {

  private final @NonNull EntityManager entityManager;
  private final @NonNull AgencyRepository agencyRepository;
  private final @NonNull DeletionAuditReporter deletionAuditReporter;
  private final @NonNull AppointmentService appointmentService;

  /**
   * The caller has already checked that the agency exists and is within reach.
   *
   * @return true when this call marked the agency, false when it was already in deletion
   */
  @Transactional
  public boolean mark(Long agencyId) {
    var agency = entityManager.find(Agency.class, agencyId, LockModeType.PESSIMISTIC_WRITE);
    if (agency == null) {
      throw new NotFoundException();
    }
    if (agency.getDeleteDate() != null) {
      return false;
    }
    agency.setDeleteDate(LocalDateTime.now(ZoneOffset.UTC));
    agency.setOffline(true);
    agencyRepository.save(agency);
    // Reported as soon as the marking is saved: a failing appointment sync afterwards must not
    // leave a marked agency without its log line.
    deletionAuditReporter.reportAgency(
        agency.getId(), agency.getTenantId(), DeletionAuditReporter.Outcome.SCHEDULED);
    appointmentService.deleteAgency(agency);
    return true;
  }
}
