package de.caritas.cob.agencyservice.config;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyChatConfigurationTest {

  private static final List<String> PROFILES = List.of("dev", "prod", "staging", "testing");

  @Test
  void applicationProfilesMustNotContainRocketChatConfiguration() throws IOException {
    for (String profile : PROFILES) {
      var resource = "/application-" + profile + ".properties";
      try (var stream = getClass().getResourceAsStream(resource)) {
        var content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

        assertFalse(
            content.toLowerCase().contains("rocket"),
            () -> resource + " still contains legacy Rocket.Chat configuration");
      }
    }
  }
}
