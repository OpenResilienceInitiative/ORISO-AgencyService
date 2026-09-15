package de.caritas.cob.agencyservice.api.service.matrix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.caritas.cob.agencyservice.api.exception.httpresponses.InternalServerErrorException;
import org.junit.jupiter.api.Test;

class AgencyMatrixPasswordCipherTest {

  private final AgencyMatrixPasswordCipher cipher =
      new AgencyMatrixPasswordCipher("test-agency-matrix-encryption-key");

  @Test
  void encryptShouldProtectEveryNonNullValueIncludingBlanks() {
    for (String plaintext : new String[] {"", " ", "\t"}) {
      var encrypted = cipher.encrypt(plaintext);
      assertThat(encrypted).startsWith("enc:").isNotEqualTo(plaintext);
      assertThat(cipher.decrypt(encrypted)).isEqualTo(plaintext);
    }
    assertThat(cipher.encrypt(null)).isNull();
  }

  @Test
  void encryptShouldRejectMalformedOrUnknownReservedFormats() {
    for (String stored : new String[] {"enc:", "enc:not-base64!", "enc:v2:unknown"}) {
      assertThatThrownBy(() -> cipher.encrypt(stored))
          .isInstanceOf(InternalServerErrorException.class)
          .hasMessage("Unable to decrypt agency Matrix password");
    }
  }

  @Test
  void decryptShouldReadAnIndependentLegacyEcbFixture() {
    // Synthetic fixture produced with OpenSSL AES-128-ECB and the original SHA-1 key derivation.
    String legacy = "enc:/LKuZWDJI2HQ4f62KunPy8AjrVjQ+O9iIUqW1KLn8lE=";
    assertThat(cipher.decrypt(legacy)).isEqualTo("legacy-fixture-password");
    assertThat(cipher.encrypt(legacy)).isEqualTo(legacy);
  }

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
