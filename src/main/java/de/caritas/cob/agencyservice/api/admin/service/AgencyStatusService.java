package de.caritas.cob.agencyservice.api.admin.service;

import de.caritas.cob.agencyservice.api.admin.validation.AgencyValidator;
import de.caritas.cob.agencyservice.api.exception.httpresponses.ConflictException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.HttpStatusExceptionReason;
import de.caritas.cob.agencyservice.api.exception.httpresponses.InternalServerErrorException;
import de.caritas.cob.agencyservice.api.exception.httpresponses.NotFoundException;
import de.caritas.cob.agencyservice.api.model.AgencyStatusResponseDTO;
import de.caritas.cob.agencyservice.api.model.UpdateAgencyDTO;
import de.caritas.cob.agencyservice.api.repository.agency.AgencyRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A selected-centre status command that cannot overwrite unrelated centre or legal data. */
@Service
@RequiredArgsConstructor
public class AgencyStatusService {

  private final @NonNull AgencyRepository agencyRepository;
  private final @NonNull AgencyValidator agencyValidator;
  private final @NonNull AgencyAvailabilityService availabilityService;

  @Transactional(readOnly = true)
  public AgencyStatusResponseDTO getStatus(Long agencyId) {
    var agency = agencyRepository.findById(agencyId).orElseThrow(NotFoundException::new);
    agencyValidator.validate(agencyId, new UpdateAgencyDTO());
    return availabilityService.getAvailability(agency);
  }

  @Transactional
  public AgencyStatusResponseDTO changeStatus(Long agencyId, boolean active) {
    var agency = agencyRepository.findLockedById(agencyId).orElseThrow(NotFoundException::new);
    boolean offline = !active;
    boolean changed = agency.isOffline() != offline;
    // Check the existing tenant/assignment permissions before consulting availability authorities.
    agencyValidator.validate(agencyId, new UpdateAgencyDTO());
    Boolean tenantActive = availabilityService.getAvailability(agency).getTenantActive();
    if (active) {
      if (agency.getDeleteDate() != null) {
        throw new ConflictException(HttpStatusExceptionReason.AGENCY_DELETED);
      }
      if (Boolean.FALSE.equals(tenantActive)) {
        throw new ConflictException(HttpStatusExceptionReason.TENANT_INACTIVE);
      }
      if (tenantActive == null) {
        throw new InternalServerErrorException("Tenant availability is unavailable for activation");
      }
    }
    if (changed && active) {
      agencyValidator.validate(agencyId, new UpdateAgencyDTO().offline(false));
    }
    if (changed) {
      if (agencyRepository.updateOfflineStatus(agencyId, offline,
          LocalDateTime.now(ZoneOffset.UTC)) != 1) {
        throw new NotFoundException();
      }
      // The column update clears the persistence context; re-read the saved own status.
      agency = agencyRepository.findById(agencyId).orElseThrow(NotFoundException::new);
    }
    return AgencyAvailabilityService.snapshot(agency, tenantActive);
  }

}
