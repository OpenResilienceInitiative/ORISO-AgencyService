package de.caritas.cob.agencyservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * SpringFox configuration - disabled for Spring Boot 4.0 compatibility.
 * Springfox is not compatible with Spring Boot 4.0/Spring Framework 7.
 * API documentation is generated via OpenAPI Generator from YAML specs.
 */
@Configuration
@ConditionalOnProperty(name = "springfox.enabled", havingValue = "true", matchIfMissing = false)
public class SpringFoxConfig {
  // Intentionally empty - Springfox is incompatible with Spring Boot 4.0
  // API documentation is handled by OpenAPI Generator
}
