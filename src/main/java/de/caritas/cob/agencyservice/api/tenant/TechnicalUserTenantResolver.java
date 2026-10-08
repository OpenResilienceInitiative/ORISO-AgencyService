package de.caritas.cob.agencyservice.api.tenant;

import de.caritas.cob.agencyservice.config.security.TaskServiceIdentity;
import de.caritas.cob.agencyservice.config.security.TechnicalServiceIdentity;
import java.util.Optional;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class TechnicalUserTenantResolver implements TenantResolver {
  private final TechnicalServiceIdentity technicalServiceIdentity;
  private final TaskServiceIdentity taskIdentity;

  public TechnicalUserTenantResolver(TechnicalServiceIdentity technicalServiceIdentity,
      TaskServiceIdentity taskIdentity) {
    this.technicalServiceIdentity = technicalServiceIdentity;
    this.taskIdentity = taskIdentity;
  }

  @Override
  public Optional<Long> resolve(HttpServletRequest request) {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()
        || !(authentication.getPrincipal() instanceof Jwt jwt)) {
      return Optional.empty();
    }
    if (taskIdentity.isTaskToken(jwt)) {
      return taskIdentity.allowsAnyTask(authentication) ? Optional.of(0L) : Optional.empty();
    }
    return technicalServiceIdentity.allows(jwt) ? Optional.of(0L) : Optional.empty();
  }
}
