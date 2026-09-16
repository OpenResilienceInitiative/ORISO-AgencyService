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

  @Test
  public void getAuthenticatedUser_Should_FallBackToSubject_When_UserIdClaimIsMissing() {
    var jwt = Jwt.withTokenValue("test-token")
        .header("alg", "none")
        .subject("8ed43c2c-1f51-4169-a7d9-c75de7eaf830")
        .claim("username", "agency-admin")
        .claim("realm_access", Map.of("roles", List.of("restricted-agency-admin")))
        .build();
    var request = new MockHttpServletRequest();
    request.setUserPrincipal(new JwtAuthenticationToken(jwt));
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    var authenticatedUser = authenticatedUserConfig.getAuthenticatedUser();

    assertThat(authenticatedUser.getUserId()).isEqualTo("8ed43c2c-1f51-4169-a7d9-c75de7eaf830");
    assertThat(authenticatedUser.requireUserId()).isEqualTo("8ed43c2c-1f51-4169-a7d9-c75de7eaf830");
    assertThat(authenticatedUser.hasRestrictedAgencyPriviliges()).isTrue();
  }

  @Test
  public void getAuthenticatedUser_Should_PreferUserIdClaim_When_Present() {
    var jwt = Jwt.withTokenValue("test-token")
        .header("alg", "none")
        .subject("subject-id")
        .claim("userId", "domain-id")
        .claim("username", "agency-admin")
        .claim("realm_access", Map.of("roles", List.of("restricted-agency-admin")))
        .build();
    var request = new MockHttpServletRequest();
    request.setUserPrincipal(new JwtAuthenticationToken(jwt));
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    var authenticatedUser = authenticatedUserConfig.getAuthenticatedUser();

    assertThat(authenticatedUser.getUserId()).isEqualTo("domain-id");
  }
}
