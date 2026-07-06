package de.caritas.cob.agencyservice.config.resttemplate;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

/**
 * Contains the rest template configuration.
 */
@Configuration
public class RestTemplateConfig {

  /**
   * RestTemplate Bean.
   *
   * @return {@link RestTemplate}
   */
  @Bean
  @Primary
  public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
        .connectTimeout(Duration.ofSeconds(2))
        .readTimeout(Duration.ofSeconds(5))
        .errorHandler(new CustomResponseErrorHandler())
        .build();
  }

  @Bean
  @Qualifier("matrixRestTemplate")
  public RestTemplate matrixRestTemplate(RestTemplateBuilder builder) {
    return builder
        .connectTimeout(Duration.ofSeconds(2))
        .readTimeout(Duration.ofSeconds(10))
        .errorHandler(new CustomResponseErrorHandler())
        .build();
  }
}
