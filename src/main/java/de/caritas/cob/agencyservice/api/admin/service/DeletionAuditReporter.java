package de.caritas.cob.agencyservice.api.admin.service;

import de.caritas.cob.agencyservice.api.service.TenantHeaderSupplier;
import de.caritas.cob.agencyservice.api.service.securityheader.SecurityHeaderSupplier;
import de.caritas.cob.agencyservice.config.apiclient.UserAdminServiceApiControllerFactory;
import de.caritas.cob.agencyservice.useradminservice.generated.web.model.DeletionAuditEntryRequestDTO;
import de.caritas.cob.agencyservice.useradminservice.generated.web.model.DeletionAuditEntryRequestDTO.OutcomeEnum;
import de.caritas.cob.agencyservice.useradminservice.generated.web.model.DeletionAuditEntryRequestDTO.TargetTypeEnum;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Tells the UserService deletion log (Admin &gt; Logs) that a Beratungsstelle was deleted.
 *
 * <p>Best effort by contract: the deletion is already done when this runs, so a failing log call is
 * logged and swallowed and can never fail or undo it. The caller's own token is forwarded, so the
 * UserService records who acted; only the opaque id, the tenant id and the outcome are sent - no
 * name, no address.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeletionAuditReporter {

  /** What happened to the Beratungsstelle. */
  public enum Outcome {
    SCHEDULED,
    EXECUTED
  }

  private final @NonNull UserAdminServiceApiControllerFactory userAdminServiceApiControllerFactory;
  private final @NonNull SecurityHeaderSupplier securityHeaderSupplier;
  private final @NonNull TenantHeaderSupplier tenantHeaderSupplier;

  public void reportAgency(Long agencyId, Long tenantId, Outcome outcome) {
    try {
      var api = userAdminServiceApiControllerFactory.createDeletionAuditApi();
      var headers = securityHeaderSupplier.getKeycloakAndCsrfHttpHeaders();
      tenantHeaderSupplier.addTenantHeader(headers);
      headers.forEach(
          (key, value) -> api.getApiClient().addDefaultHeader(key, value.iterator().next()));
      api.recordDeletion(
          new DeletionAuditEntryRequestDTO()
              .targetType(TargetTypeEnum.AGENCY)
              .targetId(String.valueOf(agencyId))
              .tenantId(tenantId)
              .outcome(OutcomeEnum.valueOf(outcome.name())));
    } catch (RuntimeException e) {
      log.warn(
          "Could not write deletion log entry for agency {} ({}): {}",
          agencyId,
          outcome,
          e.toString());
    }
  }
}
