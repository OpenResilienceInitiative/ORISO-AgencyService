package de.caritas.cob.agencyservice.config.security;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class JwtAuthConverterTest {

  @ParameterizedTest
  @ValueSource(strings = {"IDENTITY_TECHNICAL_CLIENT_ID", "TECHNICAL_SERVICE_SUBJECT"})
  void missingBindingRejectsTechnicalBearerInsteadOfGrantingHumanAuthorities(String missingBinding) {
    var bindings = new MockEnvironment()
        .withProperty("IDENTITY_TECHNICAL_CLIENT_ID", "backend-technical")
        .withProperty("TECHNICAL_SERVICE_SUBJECT", "technical-subject")
        .withProperty(missingBinding, " ");
    var converter = new JwtAuthConverter(new JwtAuthConverterProperties(),
        new AuthorisationService(), new TechnicalServiceIdentity(bindings));
    var jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject("technical-subject")
        .claim("azp", "backend-technical").claim("username", "human-looking-profile")
        .expiresAt(Instant.now().plusSeconds(60))
        .claim("realm_access", Map.of("roles", List.of("technical"))).build();

    assertThatExceptionOfType(InvalidBearerTokenException.class)
        .isThrownBy(() -> converter.convert(jwt));
  }
}
