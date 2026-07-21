package de.caritas.cob.agencyservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class AuthenticatedUserConfigTest {

  private final AuthenticatedUserConfig authenticatedUserConfig = new AuthenticatedUserConfig();

  @After
  public void resetRequestContext() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  public void getAuthenticatedUser_Should_AllowAgencyAdminWithoutDomainUserId() {
    var jwt = Jwt.withTokenValue("test-token")
        .header("alg", "none")
        .claim("username", "platform-admin")
        .claim("realm_access", Map.of("roles", List.of("agency-admin")))
        .build();
    var request = new MockHttpServletRequest();
    request.setUserPrincipal(new JwtAuthenticationToken(jwt));
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    var authenticatedUser = authenticatedUserConfig.getAuthenticatedUser();

    assertThat(authenticatedUser.getUserId()).isNull();
    assertThat(authenticatedUser.getUsername()).isEqualTo("platform-admin");
    assertThat(authenticatedUser.isAgencyAdmin()).isTrue();
  }
}
