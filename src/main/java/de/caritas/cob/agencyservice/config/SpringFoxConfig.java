package de.caritas.cob.agencyservice.config;

import java.util.ArrayList;
import java.util.List;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.client.LinkDiscoverer;
import org.springframework.hateoas.client.LinkDiscoverers;
import org.springframework.hateoas.mediatype.collectionjson.CollectionJsonLinkDiscoverer;
import org.springframework.plugin.core.SimplePluginRegistry;

/**
 * OpenAPI / Swagger UI configuration (springdoc-openapi).
 */
@Configuration
public class SpringFoxConfig {

  @Value("${springfox.docuTitle}")
  private String docuTitle;
  @Value("${springfox.docuDescription}")
  private String docuDescription;
  @Value("${springfox.docuVersion}")
  private String docuVersion;
  @Value("${springfox.docuTermsUrl}")
  private String docuTermsUrl;
  @Value("${springfox.docuContactName}")
  private String docuContactName;
  @Value("${springfox.docuContactUrl}")
  private String docuContactUrl;
  @Value("${springfox.docuContactEmail}")
  private String docuContactEmail;
  @Value("${springfox.docuLicense}")
  private String docuLicense;
  @Value("${springfox.docuLicenseUrl}")
  private String docuLicenseUrl;

  @Bean
  public GroupedOpenApi agencyApi() {
    return GroupedOpenApi.builder()
        .group("agencyservice")
        .packagesToScan("de.caritas.cob.agencyservice.api")
        .build();
  }

  @Bean
  public LinkDiscoverers discoverers() {
    List<LinkDiscoverer> plugins = new ArrayList<>();
    plugins.add(new CollectionJsonLinkDiscoverer());
    return new LinkDiscoverers(SimplePluginRegistry.create(plugins));
  }
}
