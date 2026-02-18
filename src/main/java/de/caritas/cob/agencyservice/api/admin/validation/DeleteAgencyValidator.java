package de.caritas.cob.agencyservice.api.admin.validation;

import static de.caritas.cob.agencyservice.api.exception.httpresponses.HttpStatusExceptionReason.AGENCY_CONTAINS_CONSULTANTS;

import de.caritas.cob.agencyservice.api.admin.service.UserAdminService;
import de.caritas.cob.agencyservice.api.exception.httpresponses.ConflictException;
import de.caritas.cob.agencyservice.api.repository.agency.Agency;
import de.caritas.cob.agencyservice.useradminservice.generated.web.model.ConsultantAdminResponseDTO;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Validator for agencies before deletion.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeleteAgencyValidator {
  private final @NonNull UserAdminService userAdminService;
  private static final int FIRST_PAGE = 1;
  private static final int PER_PAGE = 1;

  /**
   * Checks if agency is existing, is no Kreuzbund agency and has no assigned consultants.
   *
   * @param agency {@link Agency}
   */
  public void validate(Agency agency) {
    checkIfAgencyHasAssignedConsultants(agency);
  }

  private void checkIfAgencyHasAssignedConsultants(Agency agency) {
    try {
      List<ConsultantAdminResponseDTO> consultantList =
          this.userAdminService.getConsultantsOfAgency(agency.getId(), FIRST_PAGE, PER_PAGE);

      if (!consultantList.isEmpty()) {
        throw new ConflictException(AGENCY_CONTAINS_CONSULTANTS);
      }
    } catch (ConflictException e) {
      // Re-throw ConflictException (agency has consultants)
      throw e;
    } catch (HttpClientErrorException e) {
      // UserService returned an HTTP error (400, 404, etc.)
      // Log the error but allow deletion to proceed (similar to AgencyOfflineStatusValidator)
      // This prevents blocking deletion when UserService is unavailable or has issues
      String responseBody = "N/A";
      try {
        responseBody = e.getResponseBodyAsString();
      } catch (Exception ex) {
        log.warn("Could not read response body from UserService error", ex);
      }
      log.warn(
          "Could not verify consultants for agency {}: UserService returned {} - Response body: {}. Allowing deletion to proceed.",
          agency.getId(),
          e.getStatusCode(),
          responseBody);
      // Allow deletion to proceed - we can't verify if there are consultants
    } catch (ResponseStatusException e) {
      // ResponseStatusException is thrown by CustomResponseErrorHandler
      // Log the error but allow deletion to proceed
      log.warn(
          "Could not verify consultants for agency {}: UserService returned {} - Reason: {}. Allowing deletion to proceed.",
          agency.getId(),
          e.getStatusCode(),
          e.getReason());
      // Allow deletion to proceed - we can't verify if there are consultants
    } catch (Exception e) {
      // Unexpected error when checking consultants
      // Log the error but allow deletion to proceed
      log.warn(
          "Unexpected error while checking consultants for agency {}: {}. Allowing deletion to proceed.",
          agency.getId(),
          e.getMessage(),
          e);
      // Allow deletion to proceed - we can't verify if there are consultants
    }
  }
}
