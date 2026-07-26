package de.caritas.cob.agencyservice.config;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyChatConfigurationTest {

  private static final List<String> PROFILES = List.of("dev", "prod", "staging", "testing");

  @Test
  void releaseContainerBaseMustBePinnedByDigest() throws IOException {
    var fromLines =
        Files.readAllLines(Path.of("Dockerfile")).stream()
            .filter(line -> line.startsWith("FROM "))
            .toList();

    assertFalse(fromLines.isEmpty(), "Dockerfile must contain a base image");
    assertFalse(
        fromLines.stream().anyMatch(line -> !line.matches(".*@sha256:[a-f0-9]{64}.*")),
        "Every Dockerfile base must be pinned by digest");
  }

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
