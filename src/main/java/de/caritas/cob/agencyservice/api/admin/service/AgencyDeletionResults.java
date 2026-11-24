package de.caritas.cob.agencyservice.api.admin.service;

import de.caritas.cob.agencyservice.api.model.AgencyDeletionFailureDTO;
import de.caritas.cob.agencyservice.api.model.AgencyDeletionResultDTO;
import java.util.List;

/** Hands UserService's answer to the admin interface unchanged (ORISO-Admin#1524). */
final class AgencyDeletionResults {

  private AgencyDeletionResults() {}

  static AgencyDeletionResultDTO from(
      de.caritas.cob.agencyservice.useradminservice.generated.web.model.AgencyDeletionResultDTO
          cascade) {
    if (cascade == null) {
      throw new IllegalStateException("UserService sent no agency deletion result");
    }
    List<AgencyDeletionFailureDTO> failures =
        cascade.getFailures() == null
            ? List.of()
            : cascade.getFailures().stream()
                .map(
                    failure ->
                        new AgencyDeletionFailureDTO()
                            .kind(
                                AgencyDeletionFailureDTO.KindEnum.fromValue(
                                    failure.getKind().getValue()))
                            .id(failure.getId())
                            .reason(failure.getReason()))
                .toList();
    return new AgencyDeletionResultDTO()
        .complete(cascade.getComplete())
        .invites(cascade.getInvites())
        .adviceSeekers(cascade.getAdviceSeekers())
        .keptAdviceSeekerSessions(cascade.getKeptAdviceSeekerSessions())
        .openEnquiries(cascade.getOpenEnquiries())
        .consultantsDeleted(cascade.getConsultantsDeleted())
        .consultantsUnassigned(cascade.getConsultantsUnassigned())
        .agencyAdmins(cascade.getAgencyAdmins())
        .groupChats(cascade.getGroupChats())
        .failures(failures);
  }
}
