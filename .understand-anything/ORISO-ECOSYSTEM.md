# ORISO Ecosystem Notes: ORISO-AgencyService

This graph was generated for `ORISO-AgencyService` only. It does not analyze sibling repositories.

## Local Role Evidence

- Purpose: AgencyService provides agency core data, postcode ranges, agency settings, and agency admin APIs for the ORISO Online-Beratung platform.
- Languages: batch, dockerfile, java, javascript, json, markdown, properties, shell, sql, unknown, xml, yaml
- Frameworks/tools: Docker, Spring Boot, Spring Security, Spring Data JPA, Liquibase
- API/service-related files: 22
- Auth/tenant/security-related files: 61
- Database-related files: 85
- Deployment/operation-related files: 9

## Integration Clues

- `api/agencyadminservice.yaml` - config, yaml
- `api/agencyservice.yaml` - config, yaml
- `services/applicationsettingsservice.yml` - config, yaml
- `services/appointmentService.yaml` - config, yaml
- `services/consultingtypeservice.yaml` - config, yaml
- `services/tenantservice.yaml` - config, yaml
- `services/topicservice.yaml` - config, yaml
- `services/useradminservice.yaml` - config, yaml
- `src/main/java/de/caritas/cob/agencyservice/api/admin/controller/AgencyAdminController.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/controller/AgencyController.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/controller/CustomSwaggerUIController.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/controller/VersionController.java` - code, java
- `src/test/java/de/caritas/cob/agencyservice/api/admin/controller/AgencyAdminControllerAuthorizationIT.java` - code, java
- `src/test/java/de/caritas/cob/agencyservice/api/admin/controller/AgencyAdminControllerTest.java` - code, java
- `src/test/java/de/caritas/cob/agencyservice/api/controller/ActuatorControllerIT.java` - code, java
- `src/test/java/de/caritas/cob/agencyservice/api/controller/AgencyAdminControllerIT.java` - code, java
- `src/test/java/de/caritas/cob/agencyservice/api/controller/AgencyAdminControllerWithDemographicsIT.java` - code, java
- `src/test/java/de/caritas/cob/agencyservice/api/controller/AgencyAdminControllerWithTopicsIT.java` - code, java
- `src/test/java/de/caritas/cob/agencyservice/api/controller/AgencyControllerIT.java` - code, java
- `src/test/java/de/caritas/cob/agencyservice/api/controller/AgencyControllerTest.java` - code, java
- `src/test/java/de/caritas/cob/agencyservice/api/controller/AgencyControllerWithDemographicsIT.java` - code, java
- `src/test/java/de/caritas/cob/agencyservice/api/controller/AgencyControllerWithSingleDomainMultitenancyIT.java` - code, java
- `services/tenantservice.yaml` - config, yaml
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/agency/AgencyAdminSearchTenantSupportService.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/agencyadmincontrol/AgencyAdminAllowedPermissionTogglesSettings.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/admin/validation/validators/AgencyTenantValidator.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/admin/validation/validators/AgencyUpdatePermissionValidator.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/authorization/Authority.java` - code, java

## Platform Relationships

- Consumes and documents OpenAPI/service contracts under `api/` and `services/`.
- Persists agency, agency settings, postcode range, topic, and agency admin control data with JPA and Liquibase.
- Uses Keycloak/Spring Security and tenant resolution to scope access to agency administration data.
