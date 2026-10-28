# Architecture Notes: ORISO-AgencyService

_Refreshed 2026-08-27 against the current `pre-dev` tip. Supersedes the June 2026 notes (~328 files); the repository now tracks ~511 files (~325 Java sources: 186 main, 139 test)._

## Purpose

AgencyService is the ORISO backend microservice that owns agencies (Beratungsstellen): agency core data, postcode ranges, agency settings and admin controls, agency-level legal texts (data protection, imprint, consent) with versioning, agency ID reservation/allocation, and agency administration APIs for the Admin Panel.

## Stack

- Java 21, Spring Boot parent `4.0.7` (`pom.xml`), Maven Wrapper.
- Spring Web, Spring Data JPA, Spring Security OAuth2 resource server, Spring HATEOAS, Ehcache, FreeMarker.
- MariaDB in deployment; H2 for the `testing` profile. Liquibase changelogs are tracked in-repo (`src/main/resources/db/changelog/`) but Liquibase is disabled at runtime (`spring.liquibase.enabled=false`, see `README.md`); schemas are managed via the ORISO-Database repository.
- OpenAPI-first: server stubs are generated from `api/agencyservice.yaml` and `api/agencyadminservice.yaml` (openapi-generator-maven-plugin 7.17.0); client stubs from the specs under `services/`.
- OWASP Java HTML Sanitizer for legal-text content sanitization.
- The old Keycloak Spring adapters are gone from `pom.xml`; auth is plain Spring Security resource-server JWT (only `jboss-logging`, formerly a Keycloak-adapter transitive, is still declared explicitly).

## Architecture Layers

### API and Routing

HTTP controllers implement the generated OpenAPI interfaces; authorization boundaries live beside them.

Key files:
- `api/agencyservice.yaml` - public/app-facing contract: `/agencies`, `/agencies/by-tenant`, `/agencies/topics`, `/agencies/{agencyIds}`, `/agencies/{agencyId}/topics/{topicId}/legal`, `/agencies/consultingtype/{consultingTypeId}`.
- `api/agencyadminservice.yaml` - admin contract: agency CRUD and search, `changetype`, postcode ranges, admin `controls`, agency-ID availability/next-free/reservations, legal texts and legal-text versions, per-topic data-protection (`dpp`), imprint, and topic `details` endpoints. Shared schema parts in `api/components/agency-settings.yaml`.
- `src/main/java/de/caritas/cob/agencyservice/api/controller/AgencyController.java` - public agency read APIs.
- `src/main/java/de/caritas/cob/agencyservice/api/admin/controller/AgencyAdminController.java` - all `/agencyadmin` endpoints.
- `src/main/java/de/caritas/cob/agencyservice/api/controller/CustomSwaggerUIController.java`, `VersionController.java` - Swagger UI and version info.
- `src/main/java/de/caritas/cob/agencyservice/api/authorization/Authority.java`, `RoleAuthorizationAuthorityMapper.java` - Keycloak role to Spring authority mapping.
- `src/main/java/de/caritas/cob/agencyservice/filter/` - `HttpTenantFilter`, `SubdomainExtractor`, `StatelessCsrfFilter`, `CorrelationIdFilter`.

### Domain Services

Agency administration, settings, admin controls, legal texts, ID allocation, topic enrichment, and validation.

Key files:
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/AgencyAdminService.java` - agency create/update/changetype/delete-marking core.
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/agency/` - `AgencyAdminSearchService` (+ `AgencyAdminSearchTenantSupportService`), `AgencySettingsService`, `AgencyTopicEnrichmentService`, `DataProtectionConverter`/`DataProtectionDTOBuilder`, `DemographicsConverter`, `AgencyAdminFullResponseDTOBuilder`.
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/agencyadmincontrol/` - `AgencyAdminControlsFacade`, `AgencyAdminControlsService`, converter, and `AgencyAdminAllowedPermissionTogglesSettings` (permission toggles for restricted agency admins).
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/legal/` - legal-text administration: `LegalTextAdminService`, `LegalTextVersionAdminService`/`LegalTextVersionService`, `ConsentTextService`, `DepartmentDataProtectionService`, `DepartmentImprintService`, `LegalAdminAccessGuard`, `LegalContentSanitizer` (OWASP), `LegalTextTokens`.
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/allocation/` - `AgencyIdAllocationService` (next-free ID, availability, reservations).
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/department/` - `DepartmentDetailsService` (per-topic department details).
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/agencypostcoderange/` - postcode range admin service, transformer, validator.
- `src/main/java/de/caritas/cob/agencyservice/api/service/` - `AgencyService`/`AgencySearch` (public lookups), `DepartmentLegalService`, `CentralDataProtectionTemplateService`, `TemplateRenderer` (FreeMarker in-memory template rendering), `TopicEnrichmentService`, and HTTP clients `TenantService`, `TopicService`, `ConsultingTypeService`, `ApplicationSettingsService`, `AppointmentService`, `UserAdminService` (under `admin/service/`).
- `src/main/java/de/caritas/cob/agencyservice/api/service/legal/` - `LegalTextInheritanceResolver` and `PublicLegalTextRenderer`: resolve the effective legal text across levels (`LegalTextLevel`: SHARED, AGENCY, DEPARTMENT) for public delivery.
- `src/main/java/de/caritas/cob/agencyservice/api/service/matrix/` - `MatrixProvisioningService`, `AgencyMatrixPasswordCipher`, `MatrixConfig`: per-agency Matrix credentials provisioning.
- `src/main/java/de/caritas/cob/agencyservice/api/workflow/` - `DeleteAgenciesMarkedForDeletionScheduler`, `DeleteAgencyService`, `AgencyPurgeTransaction`: scheduled purge of agencies marked for deletion.
- `src/main/java/de/caritas/cob/agencyservice/api/admin/validation/` - `AgencyValidator` plus validators (offline, consulting type, postcode-range delete, tenant, update-permission) with the `annotation/` marker package.
- `src/main/java/de/caritas/cob/agencyservice/api/manager/consultingtype/` - `ConsultingTypeManager`, registration and white-spot settings.

### Data and Persistence

JPA entities/repositories with tenant-aware and tenant-unaware variants; Liquibase changelogs mirror the schema.

Key files:
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agency/` - `Agency` (incl. settings, data-protection fields, logo, address/contact, opening hours, Matrix credentials), `AgencyRepository`, `AgencyTenantAwareRepository`, `AgencyTenantUnawareRepository`, `DataProtectionResponsibleEntity`/`Contact`, `DataProtectionPlaceHolderType`, `Gender`.
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agencytopic/AgencyTopic.java` - per-topic assignment incl. legal-text linkage, department, and details.
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agencypostcoderange/` - `AgencyPostcodeRange` + repository.
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agencyadmincontrol/` - `AgencyAdminControlEntity` + repository.
- `src/main/java/de/caritas/cob/agencyservice/api/repository/legaltext/` - `LegalText`, `LegalTextVersion`, `LegalTextKind`, `LegalTextLevel` + repositories.
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agencyidreservation/` - `AgencyIdReservation` + repository.
- `src/main/java/de/caritas/cob/agencyservice/api/repository/TenantAware.java`, `TenantUnaware.java` - repository marker interfaces for tenant filtering.
- `src/main/resources/db/changelog/agencyservice-master.xml` - master changelog including changesets `0001`-`0032` (the former `agencyservice-dev-master.xml` no longer exists).
- Changesets added since June 2026 include: `0021_agency_topic_legal`, `0022_agency_address_contact`, `0023_agency_topic_department`, `0024_agency_dpo_contact_nullable`, `0025_demo_baseline`, `0026_legal_text`, `0027_agency_legal_text`, `0028_agency_id_reservation`, `0029_agency_opening_hours`, `0030_agency_topic_details`, `0031_legal_text_version`, `0032_legal_consent_text`.

### Configuration

Key files:
- `src/main/java/de/caritas/cob/agencyservice/config/SecurityConfig.java` - resource-server security filter chain.
- `src/main/java/de/caritas/cob/agencyservice/config/AuthenticatedUserConfig.java`, `AppConfig.java`, `CacheManagerConfig.java`, `CorsConfig.java`, `FreeMarkerConfig.java`, `TracingConfig.java`, `ConfigurationValidator.java`, `SortParameterBindingConfig.java`, `SpringFoxConfig.java`.
- `src/main/java/de/caritas/cob/agencyservice/config/apiclient/` and `config/resttemplate/` - generated-client and RestTemplate wiring for downstream services.
- `src/main/resources/application.properties` plus `-dev`, `-staging`, `-prod`, `-testing` profiles; `hibernate.properties`, `liquibase.properties`, `logback-spring.xml`, `version.properties`.
- `api/` and `services/` OpenAPI specs (see below), `pom.xml`, `package.json` (commitlint/standard-version tooling only).

### Deployment and Operations

Key files:
- `.github/workflows/ci-pull-request.yml`, `ci-feature-branch.yml`, `ci-main.yml` - Maven build/test pipelines (Java 21).
- `.github/workflows/openapi-contracts.yml` - OpenAPI contract gate (Redocly lint + oasdiff breaking-change check) on PRs and `pre-dev` pushes.
- `.github/workflows/release-image.yml` - builds/publishes `ghcr.io/openresilienceinitiative/oriso-agencyservice` from `release/agencyservice-*` branches.
- `.github/actions/maven-build/`, `.github/actions/docker-build-push/` - reusable steps.
- `Dockerfile` - digest-pinned `eclipse-temurin:21-jre`, exposes port 8084.
- `scripts/ci/` (coverage summary, required-IT runner, test-report guard) and `scripts/contracts/` (provider/consumer contract publish/verify), with matching Python tests under `tests/ci/` and `tests/contracts/`.
- `run-trivy.sh`, `check-version.sh`, `deploy-development.sh`, `run-local-remote-db.sh`.

### Documentation

- `README.md` - run/config notes (port 8084, Liquibase disabled at runtime, MariaDB, Keycloak realm settings).
- `AGENTS.md` - agent working rules (build commands, `pre-dev` integration branch, no self-merge).
- `CHANGELOG.md`, `documentation/AgencyService-Architektur.graphml|png`.

## Major Flows

- Boot flow: `src/main/java/de/caritas/cob/agencyservice/AgencyServiceApplication.java` + Spring Boot auto-config; `filter/` servlet filters run before the security chain.
- Public agency flow: `api/agencyservice.yaml` -> `AgencyController` -> `AgencyService`/`AgencySearch` -> tenant-aware repositories; postcode-range matching and white-spot fallback via `ConsultingTypeManager`.
- Admin flow: `api/agencyadminservice.yaml` -> `AgencyAdminController` -> `AgencyAdminService`, `AgencySettingsService`, `AgencyAdminControlsFacade`, `AgencyIdAllocationService`, legal admin services -> repositories; HAL links via `api/admin/hallink/`.
- Legal-text flow: admin services persist `LegalText`/`LegalTextVersion` per level and kind; `LegalTextInheritanceResolver` + `PublicLegalTextRenderer` resolve and render the effective text (with token substitution) for public endpoints such as `/agencies/{agencyId}/topics/{topicId}/legal`.
- Tenant and auth flow: JWT resource server (`SecurityConfig`) -> `RoleAuthorizationAuthorityMapper`; tenant resolved by `TenantResolverService` chaining `AccessTokenTenantResolver`, `SubdomainTenantResolver`, `CustomHeaderTenantResolver`, `MultitenancyWithSingleDomainTenantResolver`, `TechnicalUserTenantResolver`; enforced in persistence via `TenantContext`, `TenantAspect`, and `TenantHibernateInterceptor`.
- Deletion flow: agencies marked for deletion are purged by `DeleteAgenciesMarkedForDeletionScheduler` -> `DeleteAgencyService` -> `AgencyPurgeTransaction`.
- Deployment flow: GitHub Actions build and contract-gate the service; release branches publish the GHCR image; ORISO-Helm deploys it.

## API and Service Dependencies

Server contracts (this service provides):
- `api/agencyservice.yaml` - public agency API.
- `api/agencyadminservice.yaml` (+ `api/components/agency-settings.yaml`) - admin API.

Client contracts (this service consumes, generated from `services/`):
- `services/tenantservice.yaml` - tenant lookup for multitenancy.
- `services/topicservice.yaml` - topic data for `TopicEnrichmentService`/`AgencyTopicEnrichmentService`.
- `services/consultingtypeservice.yaml` - consulting-type settings for registration/white-spot logic.
- `services/applicationsettingsservice.yml` - platform application settings.
- `services/useradminservice.yaml` - consultant/agency-admin data via `UserAdminService`.
- `services/appointmentService.yaml` - appointment-service integration.

## Authentication Relationship

- `src/main/java/de/caritas/cob/agencyservice/config/SecurityConfig.java`, `AuthenticatedUserConfig.java`
- `src/main/java/de/caritas/cob/agencyservice/config/security/` - `JwtAuthConverter` (+ properties), `AuthorisationService`, `KeycloakLogoutHandler`
- `src/main/java/de/caritas/cob/agencyservice/api/authorization/Authority.java`, `RoleAuthorizationAuthorityMapper.java`
- `src/main/java/de/caritas/cob/agencyservice/api/tenant/` - resolver chain, `TenantContext`, `TenantAspect`
- `src/main/java/de/caritas/cob/agencyservice/filter/HttpTenantFilter.java`, `SubdomainExtractor.java`, `StatelessCsrfFilter.java`
- `src/main/java/de/caritas/cob/agencyservice/api/service/TenantHeaderSupplier.java`, `TenantHibernateInterceptor.java`, `TenantService.java`, `securityheader/SecurityHeaderSupplier.java`
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/legal/LegalAdminAccessGuard.java` - restricts legal administration per role/tenant
- `src/main/java/de/caritas/cob/agencyservice/api/admin/validation/validators/AgencyTenantValidator.java`, `AgencyUpdatePermissionValidator.java`

## Database Relationship

- Entities/repositories under `src/main/java/de/caritas/cob/agencyservice/api/repository/` (agency, agencytopic, agencypostcoderange, agencyadmincontrol, legaltext, agencyidreservation).
- `src/main/resources/db/changelog/agencyservice-master.xml` with changesets `0001_initsql` through `0032_legal_consent_text` (~75 tracked SQL files).
- Runtime note: Liquibase is disabled in deployed environments; the changelogs document/develop the schema, execution happens via the ORISO-Database process.

## Deployment Relationship

- `.github/workflows/ci-pull-request.yml`, `ci-feature-branch.yml`, `ci-main.yml`, `openapi-contracts.yml`, `release-image.yml`
- `.github/actions/maven-build/action.yml`, `.github/actions/docker-build-push/action.yml`
- `Dockerfile` (eclipse-temurin:21-jre, port 8084)
- `scripts/ci/`, `scripts/contracts/`, `run-trivy.sh`, `check-version.sh`, `deploy-development.sh`

## ORISO Ecosystem Fit

`ORISO-AgencyService` is one backend service of the ORISO platform (GitHub org `OpenResilienceInitiative`; lineage: Caritas Online-Beratung AgencyService, AGPL). It is the source of truth for agencies and their legal texts, consumed by the Admin Panel (agency administration screens), the app-layer frontend (agency selection during registration), and UserService (consultant-agency relations). It consumes TenantService, TopicService, ConsultingTypeService, ApplicationSettingsService, UserAdminService, and AppointmentService via generated clients.
