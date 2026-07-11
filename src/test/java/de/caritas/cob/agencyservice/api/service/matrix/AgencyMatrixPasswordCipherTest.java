package de.caritas.cob.agencyservice.api.service.matrix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.caritas.cob.agencyservice.api.exception.httpresponses.InternalServerErrorException;
import org.junit.jupiter.api.Test;

class AgencyMatrixPasswordCipherTest {

  private final AgencyMatrixPasswordCipher cipher =
      new AgencyMatrixPasswordCipher("test-agency-matrix-encryption-key");

  @Test
  void encryptThenDecryptShouldRoundTrip() {
    var encrypted = cipher.encrypt("matrix-secret-password");
    assertThat(encrypted).startsWith("enc:");
    assertThat(cipher.decrypt(encrypted)).isEqualTo("matrix-secret-password");
  }

  @Test
  void decryptShouldAcceptLegacyPlaintextValues() {
    assertThat(cipher.decrypt("legacy-plain-password")).isEqualTo("legacy-plain-password");
  }

  @Test
  void encryptShouldBeIdempotentForAlreadyEncryptedValues() {
    var encrypted = cipher.encrypt("matrix-secret-password");
    assertThat(cipher.encrypt(encrypted)).isEqualTo(encrypted);
  }

  @Test
  void encryptShouldFailClearlyWhenApplicationKeyIsMissing() {
    var cipherWithoutKey = new AgencyMatrixPasswordCipher("");

    assertThatThrownBy(() -> cipherWithoutKey.encrypt("matrix-secret-password"))
        .isInstanceOf(InternalServerErrorException.class)
        .hasMessage("Agency Matrix password encryption key is not configured");
  }
}
