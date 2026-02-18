package de.caritas.cob.agencyservice.api.service.matrix;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "matrix")
public class MatrixConfig {

  private String apiUrl = "http://matrix-synapse:8008";
  private String registrationSharedSecret = "caritas-registration-secret-2025";
  private String serverName = "caritas.local";
  private String adminUsername;
  private String adminPassword;

  public String getApiUrl(String endpoint) {
    return apiUrl + endpoint;
  }

  public boolean hasAdminCredentials() {
    return adminUsername != null && !adminUsername.isBlank()
        && adminPassword != null && !adminPassword.isBlank();
  }
}


