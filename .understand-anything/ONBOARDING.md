# Onboarding Guide: ORISO-AgencyService

1. Start with `pom.xml` (Java 21, Spring Boot parent 4.0.7) and `src/main/java/de/caritas/cob/agencyservice/AgencyServiceApplication.java` to understand the Spring Boot runtime.
2. Open `.understand-anything/README.md` and launch the dashboard using the command shown there.
3. Follow the graph tour in this order:

- 1. Project Overview: `pom.xml`, `README.md`, `AGENTS.md`, `src/main/java/de/caritas/cob/agencyservice/AgencyServiceApplication.java`
- 2. Public Agency API: `api/agencyservice.yaml`, `src/main/java/de/caritas/cob/agencyservice/api/controller/AgencyController.java`, `src/main/java/de/caritas/cob/agencyservice/api/service/AgencyService.java`, `src/main/java/de/caritas/cob/agencyservice/api/manager/consultingtype/ConsultingTypeManager.java`
- 3. Agency Admin API: `api/agencyadminservice.yaml`, `src/main/java/de/caritas/cob/agencyservice/api/admin/controller/AgencyAdminController.java`, `src/main/java/de/caritas/cob/agencyservice/api/admin/service/AgencyAdminService.java`, `src/main/java/de/caritas/cob/agencyservice/api/admin/service/agency/AgencySettingsService.java`, `src/main/java/de/caritas/cob/agencyservice/api/admin/service/agencyadmincontrol/AgencyAdminControlsFacade.java`, `src/main/java/de/caritas/cob/agencyservice/api/admin/service/allocation/AgencyIdAllocationService.java`
- 4. Legal Texts: `src/main/java/de/caritas/cob/agencyservice/api/repository/legaltext/LegalText.java`, `src/main/java/de/caritas/cob/agencyservice/api/admin/service/legal/LegalTextAdminService.java`, `src/main/java/de/caritas/cob/agencyservice/api/service/legal/LegalTextInheritanceResolver.java`, `src/main/resources/db/changelog/changeset/0026_legal_text/`, `.../0031_legal_text_version/`, `.../0032_legal_consent_text/`
- 5. Persistence And Migrations: `src/main/java/de/caritas/cob/agencyservice/api/repository/agency/Agency.java`, `src/main/resources/db/changelog/agencyservice-master.xml` (changesets `0001`-`0032`; note Liquibase is disabled at runtime, see `README.md`)
- 6. Security And Tenant Flow: `src/main/java/de/caritas/cob/agencyservice/config/SecurityConfig.java`, `src/main/java/de/caritas/cob/agencyservice/config/security/JwtAuthConverter.java`, `src/main/java/de/caritas/cob/agencyservice/api/tenant/TenantResolverService.java`, `src/main/java/de/caritas/cob/agencyservice/filter/HttpTenantFilter.java`
- 7. Deployment: `.github/workflows/ci-pull-request.yml`, `.github/workflows/openapi-contracts.yml`, `.github/workflows/release-image.yml`, `Dockerfile`, `scripts/ci/`, `scripts/contracts/`

4. For admin API work, inspect `AgencyAdminController`, `AgencyAdminService`, `AgencySettingsService`, and the `agencyadmincontrol`, `allocation`, and `legal` service packages together.
5. For database changes, inspect the repository/entity pair and the matching Liquibase changeset before editing behavior; remember schemas are executed via ORISO-Database, not by this service.
6. For auth or tenant-sensitive changes, inspect `SecurityConfig`, `config/security/`, `Authority`, `AuthenticatedUserConfig`, the `api/tenant` resolver chain, and the `filter/` package.
7. API changes must pass the OpenAPI contract gate (`.github/workflows/openapi-contracts.yml`, `scripts/contracts/`); PRs target `pre-dev` (see `AGENTS.md`).
