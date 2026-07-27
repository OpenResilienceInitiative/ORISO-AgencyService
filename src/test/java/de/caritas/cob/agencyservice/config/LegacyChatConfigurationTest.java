package de.caritas.cob.agencyservice.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyChatConfigurationTest {

  private static final List<String> PROFILES = List.of("dev", "prod", "staging", "testing");

  @Test
  void releaseWorkflowMustPublishImmutableMultiPlatformImagesWithEvidence() throws IOException {
    final var buildAction =
        Files.readString(Path.of(".github/actions/docker-build-push/action.yml"));
    final var mainWorkflow = Files.readString(Path.of(".github/workflows/ci-main.yml"));

    assertTrue(buildAction.contains("linux/amd64,linux/arm64"));
    assertTrue(buildAction.contains("provenance: mode=max"));
    assertTrue(buildAction.contains("sbom: true"));
    assertTrue(buildAction.contains("value: ${{ steps.build.outputs.digest }}"));
    assertTrue(mainWorkflow.contains("id-token: write"));
    assertTrue(mainWorkflow.contains("attestations: write"));
    assertTrue(
        mainWorkflow.contains(
            "aquasecurity/trivy-action@ed142fd0673e97e23eac54620cfb913e5ce36c25"));
    assertTrue(
        mainWorkflow.contains("actions/attest@f7c74d28b9d84cb8768d0b8ca14a4bac6ef463e6"));
    assertTrue(
        mainWorkflow.contains(
            "image-ref: ${{ env.REGISTRY }}/${{ env.ORG }}/oriso-agencyservice@${{ steps.image.outputs.digest }}"));
    assertTrue(mainWorkflow.contains("subject-digest: ${{ steps.image.outputs.digest }}"));
  }

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

  @Test
  void configEnvExampleMustNotContainRocketChatConfiguration() throws IOException {
    var content = Files.readString(Path.of("config.env.example"));

    assertFalse(
        content.toLowerCase().contains("rocket"),
        "config.env.example still contains legacy Rocket.Chat configuration");
  }
}
