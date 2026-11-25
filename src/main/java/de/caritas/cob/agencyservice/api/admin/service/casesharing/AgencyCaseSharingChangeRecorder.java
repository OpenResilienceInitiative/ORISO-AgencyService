package de.caritas.cob.agencyservice.api.admin.service.casesharing;

import de.caritas.cob.agencyservice.api.converter.AgencyCaseSharingResolver;
import de.caritas.cob.agencyservice.api.model.AgencyCaseSharingMode;
import de.caritas.cob.agencyservice.api.model.Settings;
import de.caritas.cob.agencyservice.api.repository.casesharingchange.AgencyCaseSharingChange;
import de.caritas.cob.agencyservice.api.repository.casesharingchange.AgencyCaseSharingChangeRepository;
import de.caritas.cob.agencyservice.api.repository.casesharingchange.AgencyCaseSharingChangeSource;
import de.caritas.cob.agencyservice.api.service.TenantService;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Records every change of a counselling centre's <b>effective</b> case sharing model
 * (ORISO-Admin#2494). Each case keeps the model it was created under; this log shows which model
 * applied to new cases from when to when.
 *
 * <p>Rules:
 *
 * <ul>
 *   <li>Nothing is computed unless the centre's own {@code caseSharingMode} or its {@code
 *       teamAgency} flag actually changed, so every other update makes no extra tenant call.
 *   <li>The effective model before and after comes from {@link AgencyCaseSharingResolver#resolve}
 *       with the tenant's restricted data, exactly as the Admin read does. One row is written only
 *       if it differs. The caller's transaction carries the row.
 *   <li><b>Fallback without tenant data:</b> if TenantService cannot deliver the tenant, both
 *       values are resolved with {@code tenant = null}. The resolver then treats the Träger as
 *       "case handover, not enforced": the centre's own mode decides, and without one the model is
 *       case handover. Consequences: (a) a change of the own mode is recorded even if a Träger
 *       requirement, unknown at that moment, kept the effective model unchanged; (b) a change of
 *       only {@code teamAgency} resolves to the same model before and after, so it is not
 *       recorded. A warning is logged in both cases, and the save of the centre never fails
 *       because of this log.
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgencyCaseSharingChangeRecorder {

  static final int USER_ID_MAX_LENGTH = 36;
  static final int NAME_MAX_LENGTH = 255;

  private final @NonNull AgencyCaseSharingChangeRepository repository;
  private final @NonNull TenantService tenantService;
  private final @NonNull AuthenticatedUser authenticatedUser;

  /**
   * Writes one log row if the effective model of the centre changed.
   *
   * @param agencyId the centre
   * @param tenantId the centre's tenant (Träger), may be null in single-tenant mode
   * @param ownBefore the centre's own mode before the save, null = not set
   * @param teamAgencyBefore {@code teamAgency} before the save
   * @param ownAfter the centre's own mode after the save, null = not set
   * @param teamAgencyAfter {@code teamAgency} after the save
   * @param source what was changed
   */
  public void recordIfEffectiveModelChanged(
      Long agencyId,
      Long tenantId,
      AgencyCaseSharingMode ownBefore,
      boolean teamAgencyBefore,
      AgencyCaseSharingMode ownAfter,
      boolean teamAgencyAfter,
      AgencyCaseSharingChangeSource source) {
    if (ownBefore == ownAfter && teamAgencyBefore == teamAgencyAfter) {
      return;
    }
    var tenant = tenantOrNull(agencyId, tenantId);
    var previous = effective(ownBefore, teamAgencyBefore, tenant);
    var next = effective(ownAfter, teamAgencyAfter, tenant);
    if (Objects.equals(previous, next)) {
      return;
    }
    repository.save(
        AgencyCaseSharingChange.builder()
            .agencyId(agencyId)
            .tenantId(tenantId)
            .changedAt(LocalDateTime.now(ZoneOffset.UTC))
            .changedByUserId(truncate(actorUserId(), USER_ID_MAX_LENGTH))
            .changedByName(truncate(actorName(), NAME_MAX_LENGTH))
            .previousModel(previous)
            .newModel(next)
            .source(source)
            .build());
  }

  private static AgencyCaseSharingMode effective(
      AgencyCaseSharingMode own, boolean teamAgency, RestrictedTenantDTO tenant) {
    return AgencyCaseSharingResolver.resolve(new Settings().caseSharingMode(own), teamAgency, tenant)
        .getValue();
  }

  /** Null means "tenant data unavailable"; see the fallback in the class comment. */
  private RestrictedTenantDTO tenantOrNull(Long agencyId, Long tenantId) {
    try {
      var tenant = tenantService.getRestrictedTenantDataForPermissionPolicies(tenantId);
      if (tenant == null) {
        log.warn(
            "No tenant data for tenant {}; case sharing change of agency {} is recorded without "
                + "the Traeger policy",
            tenantId,
            agencyId);
      }
      return tenant;
    } catch (RuntimeException e) {
      log.warn(
          "Tenant data for tenant {} unavailable; case sharing change of agency {} is recorded "
              + "without the Traeger policy",
          tenantId,
          agencyId,
          e);
      return null;
    }
  }

  private String actorUserId() {
    try {
      return authenticatedUser.getUserId();
    } catch (RuntimeException e) {
      // No request scope (e.g. a background job): the actor is unknown, the row is still written.
      return null;
    }
  }

  private String actorName() {
    try {
      return authenticatedUser.getUsername();
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static String truncate(String value, int maxLength) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }
}
