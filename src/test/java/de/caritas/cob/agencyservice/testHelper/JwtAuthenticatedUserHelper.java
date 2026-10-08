package de.caritas.cob.agencyservice.testHelper;

import de.caritas.cob.agencyservice.api.util.AuthenticatedUser;
import de.caritas.cob.agencyservice.config.AuthenticatedUserConfig;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Builds a real {@link AuthenticatedUser} the way production does: through {@link
 * AuthenticatedUserConfig} from a JWT. Tests that mock {@code AuthenticatedUser} cannot observe
 * how the user id is resolved from the token; tests that need that proof go through here.
 */
public final class JwtAuthenticatedUserHelper {

  public static final String RESTRICTED_AGENCY_ADMIN_ROLE = "restricted-agency-admin";
  public static final String AGENCY_ADMIN_ROLE = "agency-admin";

  private JwtAuthenticatedUserHelper() {}

  /** A token that carries {@code sub} and the given realm roles but no custom {@code userId}. */
  public static AuthenticatedUser userWithoutUserIdClaim(String subject, String... roles) {
    return fromJwt(builder -> builder.subject(subject).claim("username", "token-user")
        .claim("realm_access", Map.of("roles", List.of(roles))));
  }

  /** A token that carries both {@code sub} and the custom {@code userId} claim. */
  public static AuthenticatedUser userWithUserIdClaim(String subject, String userId,
      String... roles) {
    return fromJwt(builder -> builder.subject(subject).claim("userId", userId)
        .claim("username", "token-user")
        .claim("realm_access", Map.of("roles", List.of(roles))));
  }

  /** A token with neither {@code sub} nor {@code userId}. */
  public static AuthenticatedUser userWithoutAnyIdentity(String... roles) {
    return fromJwt(builder -> builder.claim("username", "token-user")
        .claim("realm_access", Map.of("roles", List.of(roles))));
  }

  public static AuthenticatedUser fromJwt(Consumer<Jwt.Builder> customizer) {
    var builder = Jwt.withTokenValue("test-token").header("alg", "none");
    customizer.accept(builder);
    var request = new MockHttpServletRequest();
    request.setUserPrincipal(new JwtAuthenticationToken(builder.build()));
    var previous = RequestContextHolder.getRequestAttributes();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    try {
      return new AuthenticatedUserConfig().getAuthenticatedUser();
    } finally {
      RequestContextHolder.setRequestAttributes(previous);
    }
  }
}
