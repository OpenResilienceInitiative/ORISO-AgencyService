package de.caritas.cob.agencyservice.api.admin.validation.validators;

import static com.fasterxml.jackson.databind.util.ClassUtil.nonNull;

import de.caritas.cob.agencyservice.api.admin.validation.validators.annotation.CreateAgencyValidator;
import de.caritas.cob.agencyservice.api.admin.validation.validators.annotation.UpdateAgencyValidator;
import de.caritas.cob.agencyservice.api.admin.validation.validators.model.ValidateAgencyDTO;
import de.caritas.cob.agencyservice.api.tenant.TenantContext;
import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@UpdateAgencyValidator
@CreateAgencyValidator
@Slf4j
public class AgencyTenantValidator implements ConcreteAgencyValidator {

  private final @NonNull AuthenticatedUser authenticatedUser;

  @Override
  public void validate(ValidateAgencyDTO validateAgencyDto) {
    Long effectiveTenantId = resolveEffectiveTenantId();
    if (effectiveTenantId != null && effectiveTenantId.equals(0L)) {
      nonNull(validateAgencyDto.getTenantId(), "Tenant id must not be null.");
    } else {
      if (validateAgencyDto.getTenantId() != null && effectiveTenantId != null && !effectiveTenantId
          .equals(validateAgencyDto.getTenantId())) {
        throw new AccessDeniedException(
            "Access denied. Tenant id in the request does not match current tenant.");
      }
    }
  }

  private Long resolveEffectiveTenantId() {
    Long tenantIdFromAuth = authenticatedUser.getTenantId();
    if (tenantIdFromAuth != null) {
      return tenantIdFromAuth;
    }
    return TenantContext.getCurrentTenant();
  }
}
