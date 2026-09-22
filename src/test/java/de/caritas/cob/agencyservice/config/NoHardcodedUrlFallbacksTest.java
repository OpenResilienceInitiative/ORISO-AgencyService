package de.caritas.cob.agencyservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Team rule: a deployed service never invents a URL. A missing origin must fail startup, and the
 * production host must not appear at all. Only the local and testing profiles may default.
 * Pattern taken from ORISO-ConsultingTypeService#154, extended by literal localhost values.
 */
class NoHardcodedUrlFallbacksTest {

  private static final Path RESOURCES = Path.of("src/main/resources");
  private static final Pattern URL_DEFAULT = Pattern.compile("\\$\\{[^}:]+:\\s*https?://");
  private static final Pattern LOCALHOST_VALUE =
      Pattern.compile("=\\s*https?://(localhost|127\\.0\\.0\\.1)\\b");
  private static final Pattern ORISO_HOST = Pattern.compile("oriso\\.org");
  // An unused Keycloak admin credential must not be mounted (least privilege, ORISO-Helm#367).
  private static final Pattern KEYCLOAK_ADMIN_CREDENTIAL =
      Pattern.compile("^\\s*keycloak\\.config\\.admin-(username|password)\\s*=");

  @Test
  void deployedProfiles_declareNoUrlDefaults_andNeverNameTheProductionHost() throws IOException {
    assertThat(violationsInDeployedProfiles()).isEmpty();
  }

  private static List<String> violationsInDeployedProfiles() throws IOException {
    try (Stream<Path> files = Files.list(RESOURCES)) {
      return files
          .filter(p -> p.getFileName().toString().matches("application(-[a-z]+)?\\.properties"))
          .filter(p -> !p.getFileName().toString().matches("application-(local|testing)\\..*"))
          .sorted()
          .flatMap(NoHardcodedUrlFallbacksTest::violationsIn)
          .toList();
    }
  }

  private static Stream<String> violationsIn(Path file) {
    try {
      List<String> lines = Files.readAllLines(file);
      return IntStream.range(0, lines.size())
          .filter(i -> !lines.get(i).trim().startsWith("#"))
          .filter(
              i ->
                  URL_DEFAULT.matcher(lines.get(i)).find()
                      || LOCALHOST_VALUE.matcher(lines.get(i)).find()
                      || ORISO_HOST.matcher(lines.get(i)).find()
                      || KEYCLOAK_ADMIN_CREDENTIAL.matcher(lines.get(i)).find())
          .mapToObj(i -> file.getFileName() + ":" + (i + 1) + " " + lines.get(i));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
