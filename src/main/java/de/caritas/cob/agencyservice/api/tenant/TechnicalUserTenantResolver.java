package de.caritas.cob.agencyservice.api.tenant;

import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class TechnicalUserTenantResolver implements TenantResolver {
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private de.caritas.cob.agencyservice.config.security.TaskServiceIdentity taskIdentity;

  @Override
  public Optional<Long> resolve(HttpServletRequest request) {
    return isTechnicalUserRole() ? Optional.of(0L) : Optional.empty();
  }

  private boolean isTechnicalUserRole() {

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null) {
      if (!(authentication.getPrincipal() instanceof Jwt)) {
        return false;
      }
      Jwt jwt = (Jwt) authentication.getPrincipal();
      if (taskIdentity != null && taskIdentity.isTaskToken(jwt)) {
        return taskIdentity.allowsAnyTask(authentication);
      }
      if (de.caritas.cob.agencyservice.config.security.TaskServiceIdentity.hasTaskRole(jwt)) {
        return false;
      }
      return getRealmRoles(jwt).contains("technical");
    }
    return false;
  }

  private Collection<String> getRealmRoles(Jwt jwt) {

    if (jwt != null) {
      var claims = jwt.getClaims();
      if (claims.containsKey("realm_access")) {
        Map<String, Object> realmAccess = (Map<String, Object>) claims.get("realm_access");
        if (realmAccess.containsKey("roles")) {
          return (List<String>) realmAccess.get("roles");
        }
      }
    }
    return Lists.newArrayList();
  }
}
