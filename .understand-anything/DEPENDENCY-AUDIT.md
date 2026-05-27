# ORISO AgencyService Dependency Audit Notes

## Navigation

- [Build System](#build-system)
- [Runtime Dependencies](#runtime-dependencies)
- [Generated Code](#generated-code)
- [Test Dependencies](#test-dependencies)
- [Configuration Risks](#configuration-risks)
- [Review Checklist](#review-checklist)

## Build System

`pom.xml` is the source of truth for Java build behavior. It declares Spring Boot parent `4.0.1`, Maven artifact `de.caritas.cob:agencyservice`, Java properties at 21, and compiler plugin source/target 17 with `--enable-preview`. Treat that mismatch as a policy issue.

## Runtime Dependencies

Core dependencies include Spring Boot starters for web/security/data-jpa/cache/HATEOAS/validation/OAuth2/FreeMarker/actuator/Liquibase, Micrometer/OTel tracing, OpenAPI Generator, springdoc, Keycloak Admin Client, Lombok, Commons Lang, Handlebars, Liquibase, MariaDB, HttpClient 5, Ehcache 2, Log4j libraries, SnakeYAML, H2, JUnit 4, Spring Security test, and c4-soft OAuth2 test addons.

## Generated Code

OpenAPI Generator produces server interfaces from `api/agencyservice.yaml` and `api/agencyadminservice.yaml`, markdown docs for the admin contract, and RestTemplate clients from six `services/*` contracts. Handwritten adapters under `api/service`, `api/admin/service`, and `config/apiclient` depend directly on those generated packages.

## Test Dependencies

Tests use Spring Boot test, Spring Security test, JUnit 4, H2 1.4.200, c4-soft OAuth2 test webmvc addons, and Easy Random. `powermock-module-junit4.version` is declared as a property, but no inspected dependency entry uses it.

## Configuration Risks

- Base `application.properties` has `cache.appsettings.configuration.*`; profile files and code expect `cache.applicationsettings.configuration.*`.
- `CacheManagerConfig.buildApplicationSettingsCacheConfiguration` currently copies topic cache values.
- `spring.liquibase.enabled=false` in every profile means schema migration must be handled externally.
- Peer service URLs often default to blank environment substitutions.
- Matrix config defaults to blank values, and provisioning paths can fail softly.

## Review Checklist

- Confirm Spring Boot major version compatibility with springdoc/Springfox, Keycloak client, Ehcache 2, Hibernate/JPA APIs, and OpenAPI Generator templates.
- Align Java source/target/compiler properties.
- Remove duplicate `swagger-annotations`.
- Verify generated clients compile after OpenAPI Generator upgrades.
- Run controller, service, tenant, and repository integration tests after OpenAPI, security, or Hibernate changes.
