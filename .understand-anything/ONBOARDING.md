# ORISO AgencyService Onboarding

## Navigation

- [Project Overview](#project-overview)
- [Read This First](#read-this-first)
- [Local Mental Model](#local-mental-model)
- [Guided Tour](#guided-tour)
- [File Map](#file-map)
- [Common Change Paths](#common-change-paths)
- [Testing Map](#testing-map)
- [Known Hotspots](#known-hotspots)

## Project Overview

AgencyService owns agency master data for ORISO. It answers which agency should handle a request and how admins manage agencies and coverage. It stores agency data locally and calls peer services when it needs users, tenants, topics, consulting type settings, appointments, global settings, or Matrix state.

## Read This First

Recommended reading order:

1. [Architecture Summary](./ARCHITECTURE.md)
2. `api/agencyservice.yaml`
3. `api/agencyadminservice.yaml`
4. `src/main/java/de/caritas/cob/agencyservice/config/SecurityConfig.java`
5. `src/main/java/de/caritas/cob/agencyservice/api/controller/AgencyController.java`
6. `src/main/java/de/caritas/cob/agencyservice/api/admin/controller/AgencyAdminController.java`
7. `src/main/java/de/caritas/cob/agencyservice/api/service/AgencyService.java`
8. `src/main/java/de/caritas/cob/agencyservice/api/admin/service/AgencyAdminService.java`
9. `src/main/java/de/caritas/cob/agencyservice/api/repository/agency/AgencyRepository.java`
10. [Findings And Maintenance Risks](./FINDINGS.md)

## Local Mental Model

The repo has three main business surfaces:

- Public lookup: callers ask for agencies by postcode, consulting type, topic, demographics, or ID.
- Admin lifecycle: privileged callers search, create, update, soft-delete, change type, and maintain postcode ranges.
- Integration side effects: agency changes may update AppointmentService, UserAdminService consultant state, TopicService enrichment, tenant settings, ApplicationSettingsService, and Matrix Synapse agency accounts.

## Guided Tour

1. Start at `pom.xml` to understand generated contracts and clients.
2. Read `api/agencyservice.yaml` and `AgencyController.java`.
3. Read `api/agencyadminservice.yaml` and `AgencyAdminController.java`.
4. Follow public lookup into `AgencyService.java`, then `AgencyRepository.java`.
5. Follow admin create/update/delete into `AgencyAdminService.java`, `AgencyValidator.java`, `AgencyTopicMergeService.java`, and `AppointmentService.java`.
6. Read `SecurityConfig.java`, `JwtAuthConverter.java`, `Authority.java`, and `TenantResolverService.java`.
7. Read `Agency.java`, `AgencyPostcodeRange.java`, `AgencyTopic.java`, and the Liquibase changelogs.
8. Read `MatrixProvisioningService.java` only after the agency lifecycle is clear.

## File Map

- `AgencyServiceApplication.java`: bootstrap and scheduling enablement.
- `SecurityConfig.java`: endpoint authorization, stateless CSRF, JWT resource server, tenant filter insertion.
- `AgencyController.java`: public API implementation and Matrix credential endpoints.
- `AgencyAdminController.java`: admin API implementation.
- `AgencyService.java`: public lookup, DTO conversion, white-spot fallback, Matrix credential orchestration.
- `AgencyAdminService.java`: create/update/delete/type-change orchestration.
- `AgencyAdminSearchService.java`: Criteria API search and restricted-admin filtering.
- `AgencyPostcodeRangeAdminService.java`: postcode range validation and persistence.
- `AgencyRepository.java`: native search SQL and persistence methods.
- `TenantResolverService.java`: tenant resolution decision tree.
- `TenantAspect.java`: Hibernate tenant filter activation.
- `MatrixProvisioningService.java`: Matrix Synapse account creation/password rotation.

## Common Change Paths

For public lookup changes, update `api/agencyservice.yaml`, implement in `AgencyController.java` and `AgencyService.java`, touch `AgencyRepository.java` only if query shape changes, and add controller/service tests.

For admin behavior, update `api/agencyadminservice.yaml`, keep `AgencyAdminController.java` thin, put orchestration in `AgencyAdminService.java` or a focused admin service, and update validation under `api/admin/validation`.

For tenant behavior, start in `SecurityConfig.java`, `HttpTenantFilter.java`, and `TenantResolverService.java`; check `TenantAspect.java` before changing repositories.

## Testing Map

- Controller tests: `AgencyControllerTest.java`, `AgencyControllerIT.java`, `AgencyAdminControllerTest.java`, `AgencyAdminControllerIT.java`.
- Admin services: `AgencyAdminServiceTest.java`, `AgencyAdminServiceIT.java`, tenant-aware variants.
- Postcode ranges: `AgencyPostcodeRangeAdminServiceTest.java`, IT variants, `PostcodeRangeValidatorTest.java`.
- Public service: `AgencyServiceTest.java`, `AgencyServiceIT.java`, tenant-aware variants.
- Tenancy: resolver tests under `api/tenant`, plus `HttpTenantFilterTest.java`.

## Known Hotspots

- `AgencyAdminService.java` coordinates entity merge, tenant decisions, Matrix provisioning, AppointmentService sync, topic merge, and UserAdminService calls.
- `AgencyRepository.java` contains native SQL with postcode casts, topic joins, demographics, tenant parameters, and soft-delete checks.
- `TenantResolverService.java` has different paths for authenticated, unauthenticated, technical, header, subdomain, and single-domain cases.
- `SecurityConfig.java` has permissive internal Matrix credential paths and a dense authorization map.
- `MatrixProvisioningService.java` stores generated passwords and handles Synapse conflict/password reset paths.
