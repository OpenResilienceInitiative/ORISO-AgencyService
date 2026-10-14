package de.caritas.cob.agencyservice.config.security;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import de.caritas.cob.agencyservice.api.tenant.TechnicalUserTenantResolver;
import de.caritas.cob.agencyservice.config.AuthenticatedUserConfig;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class JwtAuthConverterTest {

  @Test
  void expiryDuringAuthenticatedRequestDoesNotChangeTechnicalPrincipalOrTenant() {
    var authenticatedAt = Instant.parse("2030-01-01T00:00:00Z");
    var afterExpiry = authenticatedAt.plusSeconds(2);
    var bindings = new MockEnvironment()
        .withProperty("IDENTITY_TECHNICAL_CLIENT_ID", "backend-technical")
        .withProperty("TECHNICAL_SERVICE_SUBJECT", "technical-subject");
    var identity = new TechnicalServiceIdentity(bindings);
    var converter = new JwtAuthConverter(new JwtAuthConverterProperties(),
        new AuthorisationService(), identity, new TaskServiceIdentity(bindings));
    var jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject("technical-subject")
        .claim("azp", "backend-technical").expiresAt(authenticatedAt.plusSeconds(1))
        .claim("realm_access", Map.of("roles", List.of("technical"))).build();
    var request = new MockHttpServletRequest();

    try (var time = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
      time.when(Instant::now).thenReturn(authenticatedAt);
      var authentication = converter.convert(jwt);
      request.setUserPrincipal(authentication);
      SecurityContextHolder.getContext().setAuthentication(authentication);
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
      time.when(Instant::now).thenReturn(afterExpiry);

      assertAll(
          () -> {
            var principal = new AuthenticatedUserConfig(identity, new TaskServiceIdentity(bindings)).getAuthenticatedUser();
            assertThat(principal.getUsername()).isEqualTo("backend-technical");
            assertThat(principal.getUserId()).isEqualTo("technical-subject");
            assertThat(principal.getTenantId()).isEqualTo(0L);
          },
          () -> assertThat(new TechnicalUserTenantResolver(identity, new TaskServiceIdentity(bindings)).resolve(request)).contains(0L),
          () -> assertThatExceptionOfType(InvalidBearerTokenException.class)
              .isThrownBy(() -> converter.convert(jwt)));
    } finally {
      SecurityContextHolder.clearContext();
      RequestContextHolder.resetRequestAttributes();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"IDENTITY_TECHNICAL_CLIENT_ID", "TECHNICAL_SERVICE_SUBJECT"})
  void missingBindingRejectsTechnicalBearerInsteadOfGrantingHumanAuthorities(String missingBinding) {
    var bindings = new MockEnvironment()
        .withProperty("IDENTITY_TECHNICAL_CLIENT_ID", "backend-technical")
        .withProperty("TECHNICAL_SERVICE_SUBJECT", "technical-subject")
        .withProperty(missingBinding, " ");
    var converter = new JwtAuthConverter(new JwtAuthConverterProperties(),
        new AuthorisationService(), new TechnicalServiceIdentity(bindings), new TaskServiceIdentity(bindings));
    var jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject("technical-subject")
        .claim("azp", "backend-technical").claim("username", "human-looking-profile")
        .expiresAt(Instant.now().plusSeconds(60))
        .claim("realm_access", Map.of("roles", List.of("technical"))).build();

    assertThatExceptionOfType(InvalidBearerTokenException.class)
        .isThrownBy(() -> converter.convert(jwt));
  }
}
