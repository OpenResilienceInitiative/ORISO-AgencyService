package de.caritas.cob.agencyservice.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ConfigurationValidatorTest {

  @Test
  void validateConfiguration_Should_NotRequireMongoDbUri() {
    var validator = new ConfigurationValidator();
    setRequiredConfiguration(validator);

    assertDoesNotThrow(validator::validateConfiguration);
  }

  private void setRequiredConfiguration(ConfigurationValidator validator) {
    setField(validator, "datasourceUrl");
    setField(validator, "datasourceUsername");
    setField(validator, "datasourcePassword");
    setField(validator, "keycloakAuthServerUrl");
    setField(validator, "keycloakRealm");
    setField(validator, "jwtIssuerUri");
    setField(validator, "jwtJwkSetUri");
    setField(validator, "matrixApiUrl");
    setField(validator, "matrixRegistrationSharedSecret");
    setField(validator, "matrixServerName");
    setField(validator, "matrixAdminUsername");
    setField(validator, "matrixAdminPassword");
    setField(validator, "consultingTypeServiceApiUrl");
    setField(validator, "tenantServiceApiUrl");
    setField(validator, "userAdminServiceApiUrl");
  }

  private void setField(ConfigurationValidator validator, String fieldName) {
    ReflectionTestUtils.setField(validator, fieldName, "configured");
  }
}
