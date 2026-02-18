package de.caritas.cob.agencyservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
public class CustomSwaggerUIPathWebMvcConfigurer implements WebMvcConfigurer {

  @Value("${springdoc.swagger-ui.path:${springfox.docuPath:/swagger-ui.html}}")
  private String swaggerUiPath;

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler(swaggerUiPath + "/**")
        .addResourceLocations("classpath:/META-INF/resources/");
  }

}
