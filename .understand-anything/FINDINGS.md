# ORISO AgencyService Findings And Maintenance Risks

## Navigation

- [Documentation Gaps](#documentation-gaps)
- [Dead Or Suspicious Code](#dead-or-suspicious-code)
- [Risky Dependencies And Version Drift](#risky-dependencies-and-version-drift)
- [Unclear Architecture Boundaries](#unclear-architecture-boundaries)
- [Duplicated Logic](#duplicated-logic)
- [Recommended Next Fixes](#recommended-next-fixes)

## Documentation Gaps

- The root docs are minimal and do not explain security, tenancy, OpenAPI generation, Matrix provisioning, profile configuration, Liquibase-disabled runtime, or peer service dependencies.
- The repository has architecture graph/image artifacts, but no canonical markdown architecture page before this generated folder.
- Git tracks both `README.md` and `readme.md`, which is a case-collision risk on macOS and Windows filesystems.
- Current service naming and links still use Caritas/Online-Beratung naming in several places; decide whether ORISO docs should keep historical naming or normalize it.

## Dead Or Suspicious Code

- `AgencyAdminService.updateAgency` contains direct `System.out.println` debug output. Replace with structured logging or remove it.
- `AgencyPostcodeRangeAdminService.markAgencyOffline` is private and the apparent call is commented out in `deleteAgencyPostcodeRange`. Remove it or restore the intended rule with tests.
- `AgencyAdminSearchService` declares a private `AgencyRepository agencyRepository` field that is not used in the inspected implementation.
- `ConsultingTypeService` injects `SecurityHeaderSupplier` but the current code intentionally does not use it. Remove the field or document why.

## Risky Dependencies And Version Drift

- `pom.xml` declares Spring Boot parent `4.0.1`, Java properties at 21, and compiler plugin source/target 17 with `--enable-preview`. This is a build/runtime policy mismatch.
- `pom.xml` includes springdoc while code/properties still use Springfox naming and `SpringFoxConfig.java`. Review compatibility.
- `swagger-annotations` appears twice in `pom.xml`.
- `h2.version` is `1.4.200`, an old test database line.
- Ehcache 2 is a maintenance risk in modern Spring Boot stacks.
- Base `application.properties` uses `cache.appsettings.*`; code/profile files expect `cache.applicationsettings.*`. `buildApplicationSettingsCacheConfiguration` also applies topic cache values.
- `spring.liquibase.enabled=false` is set even though the repo contains Liquibase changelogs.

## Unclear Architecture Boundaries

- `AgencyAdminService.java` owns entity merge, tenant assignment, topic merge, Matrix provisioning, AppointmentService sync, UserAdminService consultant adaptation, and data-protection conversion.
- `api/service` mixes domain services, external service adapters, header suppliers, Matrix integration, and template rendering.
- Tenant behavior crosses filters, resolvers, aspects, repositories, header suppliers, and generated clients.
- Internal Matrix credential endpoints are permit-all in `SecurityConfig.WHITE_LIST`; document the network/security assumption if intentional.
- Matrix passwords are persisted on `agency.matrix_password`; document storage, rotation, and access expectations.

## Duplicated Logic

- Outbound service adapters repeat `addDefaultHeaders` logic with small variations.
- Profile files repeat database, Keycloak, service URL, feature flag, Matrix, cron, and cache settings.
- Agency-to-DTO conversion appears in public service conversion, admin response builders, demographics converters, and data-protection converters.

## Recommended Next Fixes

1. Resolve the `README.md` / `readme.md` case collision.
2. Remove `System.out.println` debug output from `AgencyAdminService.updateAgency`.
3. Fix or document the application-settings cache property mismatch.
4. Decide the Java/Spring Boot target and align compiler settings.
5. Document the security expectation for `/internal/agencies/**`.
