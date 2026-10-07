package de.caritas.cob.agencyservice.config.security;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/** Recognizes the pinned, role-only backend caller without requiring a human profile. */
@Component
public class TechnicalServiceIdentity {

  private final String clientId;
  private final String subject;

  public TechnicalServiceIdentity(Environment environment) {
    clientId = environment.getProperty("IDENTITY_TECHNICAL_CLIENT_ID", "").trim();
    subject = environment.getProperty("TECHNICAL_SERVICE_SUBJECT", "").trim();
  }

  public boolean allows(Jwt jwt) {
    if (clientId.isBlank() || subject.isBlank()
        || !clientId.equals(jwt.getClaims().get("azp"))
        || !subject.equals(jwt.getClaims().get("sub"))
        || jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(Instant.now())) {
      return false;
    }
    Object realm = jwt.getClaims().get("realm_access");
    if (!(realm instanceof Map<?, ?> access)
        || !(access.get("roles") instanceof Collection<?> roles)
        || roles.size() != 1 || !roles.contains("technical")) {
      return false;
    }
    Object resources = jwt.getClaims().get("resource_access");
    return !jwt.getClaims().containsKey("resource_access")
        || resources instanceof Map<?, ?> resourceAccess && resourceAccess.isEmpty();
  }

  /** A claimed technical identity must not fall through to human role or profile handling. */
  public void requireValidIfTechnical(Jwt jwt) {
    Object realm = jwt.getClaims().get("realm_access");
    boolean technicalRole = realm instanceof Map<?, ?> access
        && access.get("roles") instanceof Collection<?> roles && roles.contains("technical");
    boolean boundClient = !clientId.isBlank() && clientId.equals(jwt.getClaims().get("azp"));
    boolean boundSubject = !subject.isBlank() && subject.equals(jwt.getClaims().get("sub"));
    if ((technicalRole || boundClient || boundSubject) && !allows(jwt)) {
      throw new InvalidBearerTokenException("Technical service identity does not match its binding");
    }
  }
}
