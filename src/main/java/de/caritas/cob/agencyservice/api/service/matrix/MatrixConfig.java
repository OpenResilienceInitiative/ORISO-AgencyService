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

  private String apiUrl;
  private String registrationSharedSecret;
  private String serverName;
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


