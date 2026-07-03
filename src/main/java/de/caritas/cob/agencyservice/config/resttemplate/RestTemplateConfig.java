package de.caritas.cob.agencyservice.config.resttemplate;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
  public RestTemplate restTemplate() {
    RestTemplate restTemplate = new RestTemplate();
    restTemplate.setErrorHandler(new CustomResponseErrorHandler());
    return restTemplate;
  }
}
