package de.caritas.cob.agencyservice.api.admin.validation.validators;

import de.caritas.cob.agencyservice.api.admin.validation.validators.annotation.CreateAgencyValidator;
import de.caritas.cob.agencyservice.api.admin.validation.validators.annotation.UpdateAgencyValidator;
import de.caritas.cob.agencyservice.api.admin.validation.validators.model.ValidateAgencyDTO;
import de.caritas.cob.agencyservice.api.service.ApplicationSettingsService;
import de.caritas.cob.agencyservice.api.service.TenantService;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@UpdateAgencyValidator
@CreateAgencyValidator
@Slf4j
public class AgencyDataProtectionValidator implements ConcreteAgencyValidator {

  private final @NonNull TenantService tenantService;

  private final @NonNull AuthenticatedUser authenticatedUser;

  private final @NonNull ApplicationSettingsService applicationSettingsService;

  private final @NonNull AgencyDataProtectionValidationService agencyDataProtectionValidationService;

  @Value("${feature.multitenancy.with.single.domain.enabled}")
  private boolean multitenancyWithSingleDomain;

  @Override
  public void validate(ValidateAgencyDTO validateAgencyDto) {
    if (validateAgencyDto.getDataProtectionDTO() == null) {
      return;
    }

    Long tenantId = resolveTenantId(validateAgencyDto);
    if (tenantId == null) {
      // No tenant to ask about featureCentralDataProtectionTemplateEnabled - a single-tenancy
      // deployment, where the central data protection template does not exist. Asking the tenant
      // service for tenant "null" would answer the admin with a 500 instead.
      log.info("No tenant resolvable for agency {}; skipping data protection validation.",
          validateAgencyDto.getId());
      return;
    }

    var tenant = tenantService.getRestrictedTenantDataByTenantId(tenantId);

    if (Boolean.TRUE.equals(tenant.getSettings().getFeatureCentralDataProtectionTemplateEnabled())) {
      log.info("Validating agency data protection for agency with id {}.", validateAgencyDto.getId());
      agencyDataProtectionValidationService.validate(validateAgencyDto);
    }

    if (multitenancyWithSingleDomain) {
      // Only the LOOKUP is tolerated, never the validation. Both used to share this try, so a
      // genuine InvalidOfflineStatusException raised for the main tenant was caught here, logged
      // as a settings outage and the invalid agency written anyway - the rule did nothing in the
      // one deployment shape (agency tenant off, main tenant on) where this branch is what
      // enforces it.
      Boolean mainTenantCentralTemplateEnabled = null;
      try {
        var mainTenantSubdomainForSingleDomainMultitenancy =
            applicationSettingsService
                .getApplicationSettings()
                .getMainTenantSubdomainForSingleDomainMultitenancy();
        de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO mainTenant =
            tenantService.getRestrictedTenantDataBySubdomain(
                mainTenantSubdomainForSingleDomainMultitenancy.getValue());
        mainTenantCentralTemplateEnabled =
            mainTenant.getSettings().getFeatureCentralDataProtectionTemplateEnabled();
      } catch (Exception exception) {
        // Do not block agency updates (e.g. visibility toggle) if optional main-tenant
        // settings lookup is temporarily unavailable.
        log.warn(
            "Skipping optional main tenant data-protection validation for agency {} due to settings lookup error: {}",
            validateAgencyDto.getId(),
            exception.getMessage());
      }

      if (Boolean.TRUE.equals(mainTenantCentralTemplateEnabled)) {
        agencyDataProtectionValidationService.validate(validateAgencyDto);
      }
    }
  }

  /**
   * On update the tenant comes from the stored agency row. On create there is no row yet and the
   * request may legitimately omit the tenant id - a Traeger admin's tenant lives in the token, not
   * in the body - so the same fallback chain {@code AgencyTenantValidator} uses is applied here.
   */
  private Long resolveTenantId(ValidateAgencyDTO validateAgencyDto) {
    if (validateAgencyDto.getTenantId() != null) {
      return validateAgencyDto.getTenantId();
    }
    Long tenantIdFromAuth = authenticatedUser.getTenantId();
    return tenantIdFromAuth != null ? tenantIdFromAuth : TenantContext.getCurrentTenant();
  }
}
