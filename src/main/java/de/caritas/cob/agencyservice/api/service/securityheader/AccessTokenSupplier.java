package de.caritas.cob.agencyservice.api.service.securityheader;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class AccessTokenSupplier {

  public String getAccessToken() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
      return jwtAuthenticationToken.getToken().getTokenValue();
    }
    throw new AccessDeniedException("Authenticated JWT access token is required.");
  }
}
