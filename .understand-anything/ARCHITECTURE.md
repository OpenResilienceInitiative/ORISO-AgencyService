# ORISO AgencyService Architecture

## Navigation

- [Purpose](#purpose)
- [Stack Snapshot](#stack-snapshot)
- [Architecture Layers](#architecture-layers)
- [Major Modules](#major-modules)
- [Data Flow](#data-flow)
- [Authentication Flow](#authentication-flow)
- [API Structure](#api-structure)
- [Dependencies](#dependencies)
- [Deployment And Config](#deployment-and-config)
- [Top Files](#top-files)

## Purpose

ORISO AgencyService is the agency master-data backend for Online-Beratung. It answers public agency lookup requests, manages agencies and postcode ranges through an admin API, stores agency metadata in MariaDB, coordinates tenant-aware access, and integrates with ORISO peer services for consulting type settings, tenants, topics, users, appointments, application settings, and Matrix agency service accounts.

The repository is a Spring Boot service rooted at `src/main/java/de/caritas/cob/agencyservice/AgencyServiceApplication.java`. Maven drives compilation, OpenAPI code generation, generated peer clients, tests, profiles, and packaging through `pom.xml`.

## Stack Snapshot

- Spring Boot service with Java source under `src/main/java/de/caritas/cob/agencyservice`
- Spring Security OAuth2 Resource Server with JWT role-to-authority mapping
- Spring Data JPA and MariaDB persistence
- Hibernate tenant filtering for optional multitenancy
- OpenAPI Generator for inbound controller contracts and outbound RestTemplate clients
- Liquibase changelog files under `src/main/resources/db/changelog`
- Ehcache-backed caches for consulting type, tenant, topic, and application settings lookups
- Matrix Synapse admin API provisioning through `MatrixProvisioningService`

Important repo anchors:

- app bootstrap: `src/main/java/de/caritas/cob/agencyservice/AgencyServiceApplication.java`
- public API controller: `src/main/java/de/caritas/cob/agencyservice/api/controller/AgencyController.java`
- admin API controller: `src/main/java/de/caritas/cob/agencyservice/api/admin/controller/AgencyAdminController.java`
- security config: `src/main/java/de/caritas/cob/agencyservice/config/SecurityConfig.java`
- tenant resolver: `src/main/java/de/caritas/cob/agencyservice/api/tenant/TenantResolverService.java`
- public search service: `src/main/java/de/caritas/cob/agencyservice/api/service/AgencyService.java`
- admin lifecycle service: `src/main/java/de/caritas/cob/agencyservice/api/admin/service/AgencyAdminService.java`
- agency repository: `src/main/java/de/caritas/cob/agencyservice/api/repository/agency/AgencyRepository.java`
- entity root: `src/main/java/de/caritas/cob/agencyservice/api/repository/agency/Agency.java`
- base config: `src/main/resources/application.properties`

## Architecture Layers

- Documentation and API contracts: `api/*.yaml`, `services/*.yaml`, architecture images, root README files, and release metadata.
- Application bootstrap and configuration: `AgencyServiceApplication.java`, `pom.xml`, `Dockerfile`, `application*.properties`, `config/*`, RestTemplate, cache, Swagger/OpenAPI, and runtime validators.
- HTTP API and error handling: `AgencyController.java`, `AgencyAdminController.java`, HAL link builders, exception classes, and controller advice.
- Security, authorization, and tenancy: `SecurityConfig.java`, `StatelessCsrfFilter.java`, `HttpTenantFilter.java`, `JwtAuthConverter.java`, `Authority.java`, tenant resolvers, `TenantContext.java`, and `TenantAspect.java`.
- Agency domain services: `AgencyService.java`, `AgencyAdminService.java`, postcode range services, validators, topic/data-protection/demographic converters, Matrix provisioning, and outbound ORISO service adapters.
- Persistence and database: JPA entities and repositories under `api/repository`, Liquibase changelogs, SQL migrations, and database test fixtures.
- Scheduled workflows: `DeleteAgenciesMarkedForDeletionScheduler.java` and `DeleteAgencyService.java`.

## Major Modules

- Public lookup: `AgencyController.java`, `AgencyService.java`, `AgencyRepository.java`, `ConsultingTypeManager.java`, `TenantService.java`, `TopicEnrichmentService.java`.
- Admin lifecycle: `AgencyAdminController.java`, `AgencyAdminService.java`, `AgencyValidator.java`, `DeleteAgencyValidator.java`, `AgencyTopicMergeService.java`.
- Postcode ranges: `AgencyPostcodeRangeAdminService.java`, `PostcodeRangeTransformer.java`, `PostcodeRangeValidator.java`, `AgencyPostcodeRangeRepository.java`.
- Tenant infrastructure: `HttpTenantFilter.java`, `TenantResolverService.java`, resolvers, `TenantAspect.java`.
- Peer clients: wrappers in `UserAdminService.java`, `ConsultingTypeService.java`, `TenantService.java`, `TopicService.java`, `ApplicationSettingsService.java`, and `AppointmentService.java`.
- Matrix: `MatrixConfig.java`, `MatrixProvisioningService.java`, and the internal Matrix credential endpoints.

## Data Flow

Public lookup starts at `GET /agencies` in `api/agencyservice.yaml` and `AgencyController.getAgencies`. `AgencyService.getAgencies` loads consulting type settings through `ConsultingTypeManager`, validates postcode length, applies topic and demographics feature rules, queries `AgencyRepository.searchWithoutTopic` or `searchWithTopic`, shuffles matches, maps entities to DTOs, and optionally falls back to the consulting type white-spot agency.

Admin create starts at `POST /agencyadmin/agencies`. `AgencyAdminController.createAgency` validates through `AgencyValidator`, `AgencyAdminService.createAgency` builds the `Agency` entity, sets tenant ownership, merges topic IDs, saves through `AgencyRepository`, provisions Matrix credentials through `AgencyService.provisionMatrixCredentials`, and optionally syncs agency master data to AppointmentService.

Admin delete is soft-first. `AgencyAdminService.deleteAgency` validates with `DeleteAgencyValidator`, sets `deleteDate`, saves the agency, and optionally deletes it from AppointmentService. The scheduled deletion workflow later deletes postcode ranges and the agency row.

## Authentication Flow

`SecurityConfig.java` builds a stateless Spring Security chain. It disables default CSRF, adds `StatelessCsrfFilter`, optionally adds `HttpTenantFilter` after `BearerTokenAuthenticationFilter`, configures path authorization, and ends unmatched requests with `denyAll()`.

JWTs are handled by the OAuth2 resource server. `JwtAuthConverter.java` combines Spring's default JWT authorities with realm-role authorities from `AuthorisationService.java`. `RoleAuthorizationAuthorityMapper.java` maps realm roles to internal constants in `Authority.java`.

Tenant resolution is request scoped. `HttpTenantFilter.java` asks `TenantResolverService.java`, which tries technical role, access-token claims, custom header, single-domain rules, and subdomain resolution depending on authentication state and feature flags.

## API Structure

Inbound contracts:

- `api/agencyservice.yaml`: public `/agencies`, `/agencies/by-tenant`, `/agencies/topics`, `/agencies/{agencyIds}`, and `/agencies/consultingtype/{consultingTypeId}`.
- `api/agencyadminservice.yaml`: `/agencyadmin`, agency search/create/get/update/delete, tenant lookup, type change, and postcode range CRUD.

Custom controller endpoints not declared in those specs include `GET/POST /internal/agencies/{agencyId}/matrix-service-account`, `GET /version`, `GET /version/info`, and a custom Swagger UI redirect.

Outbound contracts live under `services/` for UserAdminService, ConsultingTypeService, TenantService, TopicService, ApplicationSettingsService, and AppointmentService.

## Dependencies

The service depends on Spring Boot, Spring Security, Spring Data JPA, Spring HATEOAS, validation, OAuth2 resource server, FreeMarker, actuator, Micrometer/OTel tracing, OpenAPI Generator, springdoc, Keycloak Admin Client, Lombok, Commons Lang, Handlebars, Liquibase, MariaDB, HttpClient 5, Ehcache 2, Log4j libraries, SnakeYAML, H2, JUnit 4, Spring Security test, and c4-soft OAuth2 test addons.

## Deployment And Config

`application.properties` is the base defaults file. Runtime values are expected from environment variables for Matrix, Keycloak/OAuth2 issuer, MariaDB, service URLs, tracing, and UserAdminService. Liquibase changelogs exist under `src/main/resources/db/changelog`, but `spring.liquibase.enabled=false` is set in base and profile properties.

## Top Files

1. `pom.xml`
2. `src/main/java/de/caritas/cob/agencyservice/AgencyServiceApplication.java`
3. `src/main/java/de/caritas/cob/agencyservice/config/SecurityConfig.java`
4. `src/main/java/de/caritas/cob/agencyservice/api/controller/AgencyController.java`
5. `src/main/java/de/caritas/cob/agencyservice/api/admin/controller/AgencyAdminController.java`
6. `src/main/java/de/caritas/cob/agencyservice/api/service/AgencyService.java`
7. `src/main/java/de/caritas/cob/agencyservice/api/admin/service/AgencyAdminService.java`
8. `src/main/java/de/caritas/cob/agencyservice/api/repository/agency/AgencyRepository.java`
9. `src/main/java/de/caritas/cob/agencyservice/api/tenant/TenantResolverService.java`
10. `src/main/java/de/caritas/cob/agencyservice/api/service/matrix/MatrixProvisioningService.java`
