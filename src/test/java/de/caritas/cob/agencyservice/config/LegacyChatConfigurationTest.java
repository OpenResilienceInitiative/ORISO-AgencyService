package de.caritas.cob.agencyservice.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
  void branchPublisherMustPublishImmutableMultiPlatformImagesWithEvidence() throws IOException {
    final var buildAction =
        Files.readString(Path.of(".github/actions/docker-build-push/action.yml"));
    final var mainWorkflow = Files.readString(Path.of(".github/workflows/ci-main.yml"));

    assertTrue(buildAction.contains("default: linux/amd64,linux/arm64"));
    assertTrue(buildAction.contains("platforms: ${{ inputs.platforms }}"));
    assertTrue(buildAction.contains("IMAGE_PLATFORMS: ${{ inputs.platforms }}"));
    assertTrue(buildAction.contains("provenance: mode=max"));
    assertTrue(buildAction.contains("sbom: ${{ inputs.image_tags == '' && 'true' || '' }}"));
    assertTrue(buildAction.contains("outputs: type=oci,"));
    assertEquals(
        List.of("push: false"),
        buildAction.lines().map(String::strip).filter(line -> line.startsWith("push:")).toList());
    assertTrue(buildAction.contains("value: ${{ steps.publish.outputs.digest }}"));
    assertTrue(
        buildAction.contains("EXPECTED_ARCHIVE_SHA256: ${{ steps.scan.outputs.archive_sha256 }}"));
    assertTrue(buildAction.contains("EXPECTED_DIGEST: ${{ steps.build.outputs.digest }}"));
    assertTrue(mainWorkflow.contains("id-token: write"));
    assertTrue(mainWorkflow.contains("attestations: write"));
    assertTrue(mainWorkflow.contains("packages: write"));
    assertTrue(mainWorkflow.contains("id: image"));
    assertTrue(mainWorkflow.contains("push_to_ghcr: true"));
    assertTrue(
        buildAction.contains("aquasecurity/setup-trivy@3fb12ec12f41e471780db15c232d5dd185dcb514"));
    assertTrue(buildAction.contains("version: v0.70.0"));
    assertTrue(mainWorkflow.contains("actions/attest@1e69f48acb82d1966a394da916b4c1698aa569d6"));
    assertTrue(
        mainWorkflow.contains(
            "subject-name: ${{ env.REGISTRY }}/${{ env.ORG }}/oriso-agencyservice"));
    assertTrue(mainWorkflow.contains("subject-digest: ${{ steps.image.outputs.digest }}"));

    var scanIndex = buildAction.indexOf("run: bash scripts/ci/scan-image-archive.sh");
    var loginIndex = buildAction.indexOf("uses: docker/login-action@");
    var publishIndex = buildAction.indexOf("run: bash scripts/ci/publish-image-archive.sh");
    assertTrue(scanIndex > -1, "the build action must run Trivy");
    assertTrue(publishIndex > -1, "the build action must publish the image");
    assertTrue(
        scanIndex < loginIndex && loginIndex < publishIndex,
        "the archive must pass its scan before registry login and publication");
  }

  @Test
  void pullRequestImageValidationMustNotWriteToTheRegistry() throws IOException {
    var buildAction = Files.readString(Path.of(".github/actions/docker-build-push/action.yml"));
    var prWorkflow = Files.readString(Path.of(".github/workflows/ci-pull-request.yml"));

    assertTrue(
        buildAction.contains(
            "- name: Login to GitHub Container Registry\n"
                + "    if: ${{ inputs.push_to_ghcr == 'true' }}"));
    assertTrue(
        buildAction.contains(
            "- name: Publish the scanned archive without rebuilding\n"
                + "    id: publish\n"
                + "    if: ${{ inputs.push_to_ghcr == 'true' }}"));
    assertTrue(prWorkflow.contains("contents: read"));
    assertTrue(prWorkflow.contains("persist-credentials: false"));
    assertTrue(prWorkflow.contains("push_to_ghcr: false"));
    assertFalse(prWorkflow.contains("push_to_ghcr: true"));
    assertFalse(prWorkflow.contains("github_token:"));
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
