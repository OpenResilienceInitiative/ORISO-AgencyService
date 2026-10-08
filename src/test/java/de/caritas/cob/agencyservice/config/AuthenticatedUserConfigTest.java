package de.caritas.cob.agencyservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.Map;
import java.time.Instant;
import de.caritas.cob.agencyservice.config.security.TechnicalServiceIdentity;
import de.caritas.cob.agencyservice.config.security.TaskServiceIdentity;
import org.springframework.mock.env.MockEnvironment;
import org.junit.After;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import de.caritas.cob.agencyservice.api.exception.KeycloakException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class AuthenticatedUserConfigTest {

  private final AuthenticatedUserConfig authenticatedUserConfig =
      new AuthenticatedUserConfig(new TechnicalServiceIdentity(new MockEnvironment()),
          new TaskServiceIdentity(new MockEnvironment()));

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

  @Test
  public void getAuthenticatedUser_Should_FallBackToSubject_When_UserIdClaimIsBlank() {
    // A blank claim is what a wiped-then-re-added empty Keycloak attribute produces; it must
    // count as "missing", otherwise requireUserId() still answers 403.
    var jwt = Jwt.withTokenValue("test-token")
        .header("alg", "none")
        .subject("8ed43c2c-1f51-4169-a7d9-c75de7eaf830")
        .claim("userId", "   ")
        .claim("username", "agency-admin")
        .claim("realm_access", Map.of("roles", List.of("restricted-agency-admin")))
        .build();
    givenRequestWith(jwt);

    var authenticatedUser = authenticatedUserConfig.getAuthenticatedUser();

    assertThat(authenticatedUser.getUserId()).isEqualTo("8ed43c2c-1f51-4169-a7d9-c75de7eaf830");
    assertThat(authenticatedUser.requireUserId()).isEqualTo("8ed43c2c-1f51-4169-a7d9-c75de7eaf830");
  }

  @Test
  public void getAuthenticatedUser_Should_KeepRequireUserIdDenying_When_NeitherClaimNorSubjectExists() {
    // The fallback must not invent an id: without userId AND sub the restricted-admin scoping
    // still has to refuse (403 semantics of requireUserId are preserved).
    var jwt = Jwt.withTokenValue("test-token")
        .header("alg", "none")
        .claim("username", "agency-admin")
        .claim("realm_access", Map.of("roles", List.of("restricted-agency-admin")))
        .build();
    givenRequestWith(jwt);

    var authenticatedUser = authenticatedUserConfig.getAuthenticatedUser();

    assertThat(authenticatedUser.getUserId()).isNull();
    assertThatExceptionOfType(AccessDeniedException.class)
        .isThrownBy(authenticatedUser::requireUserId);
  }

  @Test
  public void getAuthenticatedUser_Should_StringifyNumericUserIdClaim() {
    // Keycloak mappers can emit the attribute as a number; the domain id is compared as a string.
    var jwt = Jwt.withTokenValue("test-token")
        .header("alg", "none")
        .subject("subject-id")
        .claim("userId", 4711L)
        .claim("username", "agency-admin")
        .claim("realm_access", Map.of("roles", List.of("restricted-agency-admin")))
        .build();
    givenRequestWith(jwt);

    var authenticatedUser = authenticatedUserConfig.getAuthenticatedUser();

    assertThat(authenticatedUser.getUserId()).isEqualTo("4711");
  }

  @Test
  public void getAuthenticatedUser_Should_FallBackToSubject_When_UserIdClaimIsMissingAndSubjectIsNumeric() {
    var jwt = Jwt.withTokenValue("test-token")
        .header("alg", "none")
        .claim("sub", 12345)
        .claim("username", "agency-admin")
        .claim("realm_access", Map.of("roles", List.of("restricted-agency-admin")))
        .build();
    givenRequestWith(jwt);

    var authenticatedUser = authenticatedUserConfig.getAuthenticatedUser();

    assertThat(authenticatedUser.getUserId()).isEqualTo("12345");
  }

  private void givenRequestWith(Jwt jwt) {
    var request = new MockHttpServletRequest();
    request.setUserPrincipal(new JwtAuthenticationToken(jwt));
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  @Test
  public void humanUsernameStillRequiresTheCustomClaim() {
    var jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject("human-subject")
        .claim("preferred_username", "human-profile")
        .claim("realm_access", Map.of("roles", List.of("agency-admin"))).build();
    givenRequestWith(jwt);

    assertThatExceptionOfType(KeycloakException.class)
        .isThrownBy(authenticatedUserConfig::getAuthenticatedUser);
  }

  @Test
  public void encodedHumanUsernameAndTenantRemainUnchanged() {
    var jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject("human-subject")
        .claim("username", "enc.JBSWY3DP").claim("tenantId", 45L)
        .claim("realm_access", Map.of("roles", List.of("agency-admin"))).build();
    givenRequestWith(jwt);

    var principal = authenticatedUserConfig.getAuthenticatedUser();
    assertThat(principal.getUsername()).isEqualTo("Hello");
    assertThat(principal.getTenantId()).isEqualTo(45L);
  }

  @Test
  public void boundTechnicalPrincipalUsesServiceIdentityInsteadOfHumanProfileClaims() {
    var config = new AuthenticatedUserConfig(new TechnicalServiceIdentity(new MockEnvironment()
        .withProperty("IDENTITY_TECHNICAL_CLIENT_ID", "backend-technical")
        .withProperty("TECHNICAL_SERVICE_SUBJECT", "technical-subject")),
        new TaskServiceIdentity(new MockEnvironment()));
    var jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject("technical-subject")
        .claim("azp", "backend-technical").expiresAt(Instant.now().plusSeconds(60))
        .claim("userId", "human-domain-id").claim("tenantId", 45L)
        .claim("resource_access", Map.of())
        .claim("realm_access", Map.of("roles", List.of("technical"))).build();
    givenRequestWith(jwt);

    var principal = config.getAuthenticatedUser();
    assertThat(principal.getUsername()).isEqualTo("backend-technical");
    assertThat(principal.getUserId()).isEqualTo("technical-subject");
    assertThat(principal.getTenantId()).isEqualTo(0L);
    assertThat(principal.isTechnicalUser()).isTrue();
  }

  @Test
  public void dedicatedTaskPrincipalCannotAdoptHumanProfileOrRights() {
    var environment = new MockEnvironment().withProperty("TASK_IDENTITY_AUDIENCE", "agencyservice")
        .withProperty("IDENTITY_CONFIG_WIZARD_CLIENT_ID", "backend-config-wizard")
        .withProperty("IDENTITY_CONFIG_WIZARD_SERVICE_SUBJECT", "wizard-subject");
    var config = new AuthenticatedUserConfig(new TechnicalServiceIdentity(environment),
        new TaskServiceIdentity(environment));
    var token = Jwt.withTokenValue("task-token").header("alg", "none").subject("wizard-subject")
        .claim("azp", "backend-config-wizard").audience(List.of("agencyservice"))
        .expiresAt(Instant.now().plusSeconds(60)).claim("userId", "human-id")
        .claim("username", "human-profile").claim("tenantId", 45L)
        .claim("realm_access", Map.of("roles", List.of("config-wizard"))).build();
    var request = new MockHttpServletRequest();
    request.setUserPrincipal(new JwtAuthenticationToken(token, List.of()));
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    var principal = config.getAuthenticatedUser();
    assertThat(principal.getUserId()).isEqualTo("wizard-subject");
    assertThat(principal.getUsername()).isEqualTo("service:backend-config-wizard");
    assertThat(principal.getTenantId()).isEqualTo(0L);
    assertThat(principal.isTechnicalUser()).isFalse();
    assertThat(principal.isAgencyAdmin()).isFalse();
    assertThat(principal.getRoles()).isEmpty();
  }

}
