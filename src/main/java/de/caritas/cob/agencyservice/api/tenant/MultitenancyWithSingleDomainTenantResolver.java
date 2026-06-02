package de.caritas.cob.agencyservice.api.tenant;

import de.caritas.cob.agencyservice.api.repository.agency.AgencyTenantUnawareRepository;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.model.ApplicationSettingsDTO;
import de.caritas.cob.agencyservice.applicationsettingsservice.generated.web.model.ApplicationSettingsDTOMainTenantSubdomainForSingleDomainMultitenancy;
import de.caritas.cob.agencyservice.config.apiclient.ApplicationSettingsApiControllerFactory;
import de.caritas.cob.agencyservice.config.apiclient.TenantServiceApiControllerFactory;
import de.caritas.cob.agencyservice.tenantservice.generated.web.model.RestrictedTenantDTO;
import java.util.Optional;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.plexus.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
@Slf4j
@RequiredArgsConstructor
public class MultitenancyWithSingleDomainTenantResolver implements TenantResolver {

  @Value("${feature.multitenancy.with.single.domain.enabled}")
  private boolean multitenancyWithSingleDomain;

  @Autowired
  private ApplicationSettingsApiControllerFactory applicationSettingsApiControllerFactory;

  @Autowired
  private TenantServiceApiControllerFactory tenantServiceApiControllerFactory;

  @Autowired
  private AgencyTenantUnawareRepository agencyTenantUnawareRepository;

  @Override
  public Optional<Long> resolve(HttpServletRequest request) {
    if (multitenancyWithSingleDomain) {
      return resolveForMultitenancyWithSingleDomain(request);
    } else {
      return Optional.empty();
    }
  }

  private Optional<Long> resolveForMultitenancyWithSingleDomain(HttpServletRequest request) {
    log.debug("MultitenancyWithSingleDomainTenantResolver resolves tenantId");
    // Prefer resolving from agencyId header for internal/non-auth calls.
    // This is required for endpoints like /internal/agencies/{id}/matrix-service-account.
    Optional<Long> tenantFromAgencyHeader = resolveTenantFromAgencyHeader(request);
    if (tenantFromAgencyHeader.isPresent()) {
      return tenantFromAgencyHeader;
    }

    Optional<String> mainTenantSubdomain = getMainTenantSubdomainFromApplicationSettings();
    if (mainTenantSubdomain.isPresent() && StringUtils.isNotBlank(mainTenantSubdomain.get())) {
      return resolveFromTenantServiceBasedOnMainTenantSubdomain(mainTenantSubdomain.get());
    } else {
      log.warn(
          "Main tenant subdomain not available in application settings. Resolving tenant to 0.");
      return Optional.of(0L);
    }
  }

  private Optional<Long> resolveTenantFromAgencyHeader(HttpServletRequest request) {
    String agencyIdHeader = request.getHeader("agencyId");
    if (StringUtils.isBlank(agencyIdHeader)) {
      agencyIdHeader = request.getHeader("agencyid");
    }
    if (StringUtils.isBlank(agencyIdHeader)) {
      return Optional.empty();
    }
    try {
      Long agencyId = Long.parseLong(agencyIdHeader);
      return agencyTenantUnawareRepository
          .findByIdAndDeleteDateNull(agencyId)
          .map(agency -> agency.getTenantId());
    } catch (NumberFormatException ex) {
      log.warn("Invalid agencyId header value for tenant resolution: {}", agencyIdHeader);
      return Optional.empty();
    }
  }

  private Optional<Long> resolveFromTenantServiceBasedOnMainTenantSubdomain(
      String rootTenantSubdomain) {
    try {
      var tenantControllerApi = tenantServiceApiControllerFactory.createControllerApi();
      RestrictedTenantDTO rootTenantData =
          tenantControllerApi.getRestrictedTenantDataBySubdomain(rootTenantSubdomain, null);
      if (rootTenantData != null && rootTenantData.getId() != null) {
        return Optional.of(rootTenantData.getId());
      }
    } catch (Exception exception) {
      log.warn(
          "Could not resolve tenant by main subdomain '{}'. Falling back to global tenant context.",
          rootTenantSubdomain);
    }
    return Optional.of(0L);
  }

  private Optional<String> getMainTenantSubdomainFromApplicationSettings() {
    ApplicationSettingsDTO applicationSettings = applicationSettingsApiControllerFactory.createControllerApi()
        .getApplicationSettings();
    ApplicationSettingsDTOMainTenantSubdomainForSingleDomainMultitenancy mainTenantSubdomainForSingleDomainMultitenancy = applicationSettings.getMainTenantSubdomainForSingleDomainMultitenancy();
    if (mainTenantSubdomainForSingleDomainMultitenancy == null) {
      return Optional.empty();
    }
    String subdomain = mainTenantSubdomainForSingleDomainMultitenancy.getValue();
    return Optional.ofNullable(subdomain);
  }

  @Override
  public boolean canResolve(HttpServletRequest request) {
    return resolve(request).isPresent();
  }
}
