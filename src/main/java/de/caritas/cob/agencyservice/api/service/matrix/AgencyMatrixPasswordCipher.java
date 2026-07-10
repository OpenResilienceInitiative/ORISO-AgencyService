package de.caritas.cob.agencyservice.api.service.matrix;

import de.caritas.cob.agencyservice.api.exception.httpresponses.InternalServerErrorException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Encrypts agency Matrix service-account passwords at rest (AS-C01). Uses the same {@code enc:}
 * prefix convention as ORISO UserService so values can be migrated incrementally.
 */
@Service
public class AgencyMatrixPasswordCipher {

  private static final String CIPHER_TRANSFORMATION = "AES/ECB/PKCS5Padding";
  private static final String SECRET_KEY_ALGORITHM = "AES";
  private static final String MESSAGE_DIGEST_ALGORITHM = "SHA-1";
  private static final String ENCRYPTED_PREFIX = "enc:";
  private static final String KEY_DERIVATION_SALT = "agency-matrix-password";

  private final String applicationKey;

  public AgencyMatrixPasswordCipher(
      @Value("${service.encryption.appkey}") String applicationKey) {
    this.applicationKey = applicationKey;
  }

  public String encrypt(String plaintext) {
    if (StringUtils.isBlank(plaintext) || plaintext.startsWith(ENCRYPTED_PREFIX)) {
      return plaintext;
    }
    try {
      Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec());
      byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(encrypted);
    } catch (Exception ex) {
      throw new InternalServerErrorException("Unable to encrypt agency Matrix password", ex);
    }
  }

  public String decrypt(String storedValue) {
    if (StringUtils.isBlank(storedValue) || !storedValue.startsWith(ENCRYPTED_PREFIX)) {
      return storedValue;
    }
    String ciphertext = storedValue.substring(ENCRYPTED_PREFIX.length());
    try {
      Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, secretKeySpec());
      byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
      return new String(decrypted, StandardCharsets.UTF_8);
    } catch (Exception ex) {
      throw new InternalServerErrorException("Unable to decrypt agency Matrix password", ex);
    }
  }

  private SecretKeySpec secretKeySpec() throws NoSuchAlgorithmException {
    byte[] keyMaterial =
        (KEY_DERIVATION_SALT + applicationKey).getBytes(StandardCharsets.UTF_8);
    MessageDigest digest = MessageDigest.getInstance(MESSAGE_DIGEST_ALGORITHM);
    keyMaterial = digest.digest(keyMaterial);
    keyMaterial = Arrays.copyOf(keyMaterial, 16);
    return new SecretKeySpec(keyMaterial, SECRET_KEY_ALGORITHM);
  }
}
