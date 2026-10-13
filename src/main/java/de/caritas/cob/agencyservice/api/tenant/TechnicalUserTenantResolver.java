package de.caritas.cob.agencyservice.api.tenant;

import de.caritas.cob.agencyservice.config.security.TechnicalServiceIdentity;
import java.util.Optional;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class TechnicalUserTenantResolver implements TenantResolver {

  private final TechnicalServiceIdentity technicalServiceIdentity;

  public TechnicalUserTenantResolver(TechnicalServiceIdentity technicalServiceIdentity) {
    this.technicalServiceIdentity = technicalServiceIdentity;
  }

  @Override
  public Optional<Long> resolve(HttpServletRequest request) {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof Jwt jwt && technicalServiceIdentity.allows(jwt)
        ? Optional.of(0L) : Optional.empty();
  }
}
