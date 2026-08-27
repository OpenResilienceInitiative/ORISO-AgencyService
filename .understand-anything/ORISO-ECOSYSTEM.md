# ORISO Ecosystem Notes: ORISO-AgencyService

These notes were written for `ORISO-AgencyService` only (refreshed 2026-08-27 at the current `pre-dev` tip). They do not analyze sibling repositories.

## Local Role Evidence

- Purpose: AgencyService is the backend microservice for agencies (Beratungsstellen), postcode ranges, agency settings and admin controls, agency-level legal texts (data protection, imprint, consent, versioned), agency ID reservation/allocation, and agency administration APIs for the ORISO platform.
- Repository size: ~511 tracked files (~325 Java: 186 main + 139 test, ~75 SQL changeset files, plus Python CI/contract tooling under `scripts/` and `tests/`).
- Languages: java, sql, xml, yaml, properties, shell, python, markdown, json, batch.
- Frameworks/tools: Spring Boot 4.0.7 parent (Java 21), Spring Security OAuth2 resource server, Spring Data JPA, Spring HATEOAS, Liquibase (changelogs tracked in-repo, disabled at runtime), FreeMarker, Ehcache, OWASP Java HTML Sanitizer, openapi-generator, Docker.
- Lineage: fork line of the Caritas Online-Beratung AgencyService (`de.caritas.cob` packages, AGPL license), now developed in the `OpenResilienceInitiative` GitHub org; integration branch is `pre-dev`.

## Integration Clues

Server contracts this service provides:

- `api/agencyservice.yaml` - public agency API (`/agencies`, `/agencies/by-tenant`, `/agencies/topics`, `/agencies/{agencyId}/topics/{topicId}/legal`, ...)
- `api/agencyadminservice.yaml` + `api/components/agency-settings.yaml` - admin API (`/agencyadmin/...`: agency CRUD/search, postcode ranges, controls, agency-ID reservations, legal texts and versions, imprint, topic details)

Client contracts this service consumes (generated from `services/`):

- `services/tenantservice.yaml` - tenant resolution for multitenancy
- `services/topicservice.yaml` - topic enrichment of agencies
- `services/consultingtypeservice.yaml` - consulting-type/registration settings and white-spot logic
- `services/applicationsettingsservice.yml` - platform application settings
- `services/useradminservice.yaml` - consultant/agency-admin lookups
- `services/appointmentService.yaml` - appointment-service integration

Other integration points:

- Matrix: `api/service/matrix/MatrixProvisioningService` provisions per-agency Matrix credentials (schema: `0018_agency_matrix_credentials`).
- Admin Panel and app-layer frontend consume the two served APIs; UserService references agency IDs.
- CI publishes/verifies OpenAPI provider contracts (`scripts/contracts/`, `.github/workflows/openapi-contracts.yml`).
- Release images ship as `ghcr.io/openresilienceinitiative/oriso-agencyservice` (`.github/workflows/release-image.yml`); deployment is managed via ORISO-Helm.

## Platform Relationships

- Serves and documents its own OpenAPI contracts under `api/`; consumes sibling-service contracts under `services/`.
- Persists agency, agency settings, postcode range, topic assignment (incl. department and details), agency admin control, legal text/version, and agency-ID reservation data with JPA; Liquibase changelogs (`0001`-`0032`) mirror the schema, which is executed via the ORISO-Database process.
- Uses Keycloak-issued JWTs via Spring Security resource server (`config/security/JwtAuthConverter`) plus a tenant resolver chain (access token, subdomain, custom header, single-domain, technical user) to scope all agency administration data per tenant.
- Owns the multi-level legal-text model (`LegalTextLevel`: SHARED, AGENCY, DEPARTMENT; kinds DPP and IMPRINT, plus consent texts) with inheritance resolution and sanitized rendering for public consumption.
