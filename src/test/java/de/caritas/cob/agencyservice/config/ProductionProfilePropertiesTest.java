package de.caritas.cob.agencyservice.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * Guards the security-relevant settings of the internet-facing profiles (AS-H05).
 *
 * <p>The audit finding was that dev, staging and prod were byte-identical, which is how {@code
 * keycloak.ssl-required=none} ended up accepting plaintext HTTP tokens in production. These
 * assertions fail as soon as a profile drifts back.
 */
class ProductionProfilePropertiesTest {

  private static final String SSL_REQUIRED = "keycloak.ssl-required";

  @Test
  void prodProfile_Should_RequireSslForKeycloakTokens() throws IOException {
    assertEquals("external", loadProfile("prod").getProperty(SSL_REQUIRED));
  }

  @Test
  void stagingProfile_Should_RequireSslForKeycloakTokens() throws IOException {
    assertEquals("external", loadProfile("staging").getProperty(SSL_REQUIRED));
  }

  @Test
  void prodProfile_Should_NotEnableSqlLogging() throws IOException {
    var prod = loadProfile("prod");

    assertEquals("false", prod.getProperty("spring.jpa.show-sql"));
    assertNotEquals("DEBUG", prod.getProperty("logging.level.root"));
  }

  @Test
  void prodProfile_Should_DifferFromDevProfile() throws IOException {
    var prod = loadProfile("prod");
    var dev = loadProfile("dev");

    assertNotEquals(
        dev.getProperty(SSL_REQUIRED),
        prod.getProperty(SSL_REQUIRED),
        "prod must not reuse the dev Keycloak SSL setting");
  }

  private Properties loadProfile(String profile) throws IOException {
    var properties = new Properties();
    try (InputStream stream =
        getClass().getClassLoader().getResourceAsStream("application-" + profile + ".properties")) {
      assertNotNull(stream, "application-" + profile + ".properties must exist");
      properties.load(stream);
    }
    return properties;
  }
}
