package de.caritas.cob.agencyservice.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyChatConfigurationTest {

  private static final List<String> PROFILES = List.of("dev", "prod", "staging", "testing");
  private static final List<String> LEGACY_CHAT_KEYS =
      List.of("rocket.chat", "rocket_chat", "rocketchat", "rocket.technical", "rocket_technical");

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
        buildAction.contains("aquasecurity/trivy-action@ed142fd0673e97e23eac54620cfb913e5ce36c25"));
    assertTrue(
        mainWorkflow.contains("actions/attest@f7c74d28b9d84cb8768d0b8ca14a4bac6ef463e6"));
    assertTrue(mainWorkflow.contains("subject-digest: ${{ steps.image.outputs.digest }}"));

    // The vulnerability scan has to sit ahead of the publish. Scanning after
    // `push: true` can only redden the run; the image is already in GHCR and the
    // deploy scripts resolve a tag to a digest without reading workflow results
    // (OpenResilienceInitiative/ORISO-Docs#88).
    var scanIndex = buildAction.indexOf("aquasecurity/trivy-action@");
    var publishIndex = buildAction.indexOf("push: ${{ inputs.push_to_ghcr }}");
    assertTrue(scanIndex > -1, "the build action must run Trivy");
    assertTrue(publishIndex > -1, "the build action must publish the image");
    assertTrue(
        scanIndex < publishIndex, "Trivy must run before the image is pushed to the registry");
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
        assertNotNull(stream, () -> "Missing required application profile " + resource);
        var content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

        assertFalse(
            containsLegacyChatKey(content),
            () -> resource + " still contains legacy Rocket.Chat configuration");
      }
    }
  }

  @Test
  void exampleEnvironmentMustNotProvisionRocketChatSecrets() throws IOException {
    var content = Files.readString(Path.of("config.env.example"));

    assertFalse(
        containsLegacyChatKey(content),
        "config.env.example still provisions legacy Rocket.Chat configuration");
  }

  private static boolean containsLegacyChatKey(String content) {
    var lowerCaseContent = content.toLowerCase();
    return LEGACY_CHAT_KEYS.stream().anyMatch(lowerCaseContent::contains);
  }
}
