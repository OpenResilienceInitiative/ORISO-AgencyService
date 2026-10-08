package de.caritas.cob.agencyservice.api.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import de.caritas.cob.agencyservice.config.security.TechnicalServiceIdentity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class TechnicalUserTenantResolverTest {

  private final TechnicalUserTenantResolver resolver = new TechnicalUserTenantResolver(
      new TechnicalServiceIdentity(new MockEnvironment()
          .withProperty("IDENTITY_TECHNICAL_CLIENT_ID", "backend-technical")
          .withProperty("TECHNICAL_SERVICE_SUBJECT", "technical-subject")));

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void resolvesTechnicalTenantOnlyForBoundServiceIdentity() {
    authenticate("technical-subject", "technical");
    assertThat(resolver.resolve(new MockHttpServletRequest())).contains(0L);
  }

  @Test
  void deniesTechnicalTenantForForeignSubjectEvenWithTechnicalRole() {
    authenticate("foreign", "technical");
    assertThat(resolver.resolve(new MockHttpServletRequest())).isEmpty();
  }

  @Test
  void deniesTechnicalTenantForHumanOrTaskRole() {
    authenticate("technical-subject", "agency-admin");
    assertThat(resolver.resolve(new MockHttpServletRequest())).isEmpty();
    authenticate("technical-subject", "technical", "config-wizard");
    assertThat(resolver.resolve(new MockHttpServletRequest())).isEmpty();
  }

  @Test
  void deniesTechnicalTenantWithoutAuthentication() {
    assertThat(resolver.resolve(new MockHttpServletRequest())).isEmpty();
  }

  private void authenticate(String subject, String... roles) {
    var jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject(subject)
        .claim("azp", "backend-technical").expiresAt(Instant.now().plusSeconds(60))
        .claim("realm_access", Map.of("roles", List.of(roles))).build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
  }
}
