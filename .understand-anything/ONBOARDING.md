# Onboarding Guide: ORISO-AgencyService

1. Start with `pom.xml` and `src/main/java/de/caritas/cob/agencyservice/AgencyServiceApplication.java` to understand the Spring Boot runtime.
2. Open `.understand-anything/README.md` and launch the dashboard using the command shown there.
3. Follow the graph tour in this order:

- 1. Project Overview: `pom.xml`, `package.json`, `src/main/java/de/caritas/cob/agencyservice/AgencyServiceApplication.java`
- 2. Agency Admin API: `src/main/java/de/caritas/cob/agencyservice/api/admin/controller/AgencyAdminController.java`, `src/main/java/de/caritas/cob/agencyservice/api/admin/service/AgencyAdminService.java`, `src/main/java/de/caritas/cob/agencyservice/api/admin/service/agency/AgencySettingsService.java`, `src/main/java/de/caritas/cob/agencyservice/api/admin/service/agencyadmincontrol/AgencyAdminControlsFacade.java`
- 3. Persistence And Migrations: `src/main/java/de/caritas/cob/agencyservice/api/repository/agency/Agency.java`, `src/main/java/de/caritas/cob/agencyservice/api/repository/agencyadmincontrol/AgencyAdminControlEntity.java`, `src/main/resources/db/changelog/changeset/0019_agency_admin_control/0019_changeSet.xml`, `src/main/resources/db/changelog/changeset/0020_agency_settings/0020_changeSet.xml`
- 4. Security And Tenant Flow: `src/main/java/de/caritas/cob/agencyservice/api/authorization/Authority.java`, `src/main/java/de/caritas/cob/agencyservice/config/SecurityConfig.java`, `src/main/java/de/caritas/cob/agencyservice/api/tenant/TenantResolverService.java`, `src/main/java/de/caritas/cob/agencyservice/config/AuthenticatedUserConfig.java`
- 5. Deployment: `.github/workflows/ci-feature-branch.yml`, `.github/workflows/ci-main.yml`, `.github/workflows/ci-pull-request.yml`, `Dockerfile`, `pom.xml`

4. For admin API work, inspect `AgencyAdminController`, `AgencyAdminService`, `AgencySettingsService`, and `agencyadmincontrol` services together.
5. For database changes, inspect the repository/entity pair and matching Liquibase changeset before editing behavior.
6. For auth or tenant-sensitive changes, inspect `SecurityConfig`, `Authority`, `AuthenticatedUserConfig`, and tenant resolver classes.
