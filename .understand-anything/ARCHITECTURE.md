# Architecture Notes: ORISO-AgencyService

## Purpose

AgencyService provides agency core data, postcode ranges, agency settings, and agency admin APIs for the ORISO Online-Beratung platform.

## Architecture Layers

### Api And Routing

HTTP routes, controllers, OpenAPI contracts, authorization boundaries, and service API integration files.

Key files:
- `src/main/java/de/caritas/cob/agencyservice/api/admin/controller/AgencyAdminController.java` - src/main/java/de/caritas/cob/agencyservice/api/admin/controller/AgencyAdminController.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.admin.controller;".
- `src/main/java/de/caritas/cob/agencyservice/api/authorization/Authority.java` - src/main/java/de/caritas/cob/agencyservice/api/authorization/Authority.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.authorization;".
- `src/main/java/de/caritas/cob/agencyservice/api/authorization/RoleAuthorizationAuthorityMapper.java` - src/main/java/de/caritas/cob/agencyservice/api/authorization/RoleAuthorizationAuthorityMapper.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.authorization;".
- `src/main/java/de/caritas/cob/agencyservice/api/controller/AgencyController.java` - src/main/java/de/caritas/cob/agencyservice/api/controller/AgencyController.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.controller;".
- `src/main/java/de/caritas/cob/agencyservice/api/controller/CustomSwaggerUIController.java` - src/main/java/de/caritas/cob/agencyservice/api/controller/CustomSwaggerUIController.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.controller;".
- `src/main/java/de/caritas/cob/agencyservice/api/controller/VersionController.java` - src/main/java/de/caritas/cob/agencyservice/api/controller/VersionController.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.controller;".
- `src/test/java/de/caritas/cob/agencyservice/api/admin/controller/AgencyAdminControllerAuthorizationIT.java` - src/test/java/de/caritas/cob/agencyservice/api/admin/controller/AgencyAdminControllerAuthorizationIT.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.admin.controller;".
- `src/test/java/de/caritas/cob/agencyservice/api/admin/controller/AgencyAdminControllerTest.java` - src/test/java/de/caritas/cob/agencyservice/api/admin/controller/AgencyAdminControllerTest.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.admin.controller;".
- `src/test/java/de/caritas/cob/agencyservice/api/authorization/AuthorityTest.java` - src/test/java/de/caritas/cob/agencyservice/api/authorization/AuthorityTest.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.authorization;".
- `src/test/java/de/caritas/cob/agencyservice/api/authorization/RoleAuthorizationAuthorityMapperTest.java` - src/test/java/de/caritas/cob/agencyservice/api/authorization/RoleAuthorizationAuthorityMapperTest.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.authorization;".
- `src/test/java/de/caritas/cob/agencyservice/api/controller/ActuatorControllerIT.java` - src/test/java/de/caritas/cob/agencyservice/api/controller/ActuatorControllerIT.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.controller;".
- `src/test/java/de/caritas/cob/agencyservice/api/controller/AgencyAdminControllerIT.java` - src/test/java/de/caritas/cob/agencyservice/api/controller/AgencyAdminControllerIT.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.controller;".
- `src/test/java/de/caritas/cob/agencyservice/api/controller/AgencyAdminControllerWithDemographicsIT.java` - src/test/java/de/caritas/cob/agencyservice/api/controller/AgencyAdminControllerWithDemographicsIT.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.controller;".
- `src/test/java/de/caritas/cob/agencyservice/api/controller/AgencyAdminControllerWithTopicsIT.java` - src/test/java/de/caritas/cob/agencyservice/api/controller/AgencyAdminControllerWithTopicsIT.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.controller;".
- `src/test/java/de/caritas/cob/agencyservice/api/controller/AgencyControllerIT.java` - src/test/java/de/caritas/cob/agencyservice/api/controller/AgencyControllerIT.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.controller;".
- `src/test/java/de/caritas/cob/agencyservice/api/controller/AgencyControllerTest.java` - src/test/java/de/caritas/cob/agencyservice/api/controller/AgencyControllerTest.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.controller;".

### Domain Services

Agency administration, agency settings, admin controls, topic enrichment, validation, and application services.

Key files:
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/AgencyAdminService.java` - src/main/java/de/caritas/cob/agencyservice/api/admin/service/AgencyAdminService.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.admin.service;".
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/AgencyTopicMergeService.java` - src/main/java/de/caritas/cob/agencyservice/api/admin/service/AgencyTopicMergeService.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.admin.service;".
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/UserAdminService.java` - src/main/java/de/caritas/cob/agencyservice/api/admin/service/UserAdminService.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.admin.service;".
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/agency/AgencyAdminFullResponseDTOBuilder.java` - src/main/java/de/caritas/cob/agencyservice/api/admin/service/agency/AgencyAdminFullResponseDTOBuilder.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.admin.service.agency;".
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/agency/AgencyAdminSearch.java` - src/main/java/de/caritas/cob/agencyservice/api/admin/service/agency/AgencyAdminSearch.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.admin.service.agency;".
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/agency/AgencyAdminSearchService.java` - src/main/java/de/caritas/cob/agencyservice/api/admin/service/agency/AgencyAdminSearchService.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.admin.service.agency;".
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/agency/AgencyAdminSearchTenantSupportService.java` - src/main/java/de/caritas/cob/agencyservice/api/admin/service/agency/AgencyAdminSearchTenantSupportService.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.admin.service.agency;".
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/agency/AgencySettingsService.java` - src/main/java/de/caritas/cob/agencyservice/api/admin/service/agency/AgencySettingsService.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.admin.service.agency;".
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/agency/AgencyTopicEnrichmentService.java` - src/main/java/de/caritas/cob/agencyservice/api/admin/service/agency/AgencyTopicEnrichmentService.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.admin.service.agency;".
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/agency/DataProtectionConverter.java` - src/main/java/de/caritas/cob/agencyservice/api/admin/service/agency/DataProtectionConverter.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.admin.service.agency;".
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/agency/DataProtectionDTOBuilder.java` - src/main/java/de/caritas/cob/agencyservice/api/admin/service/agency/DataProtectionDTOBuilder.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.admin.service.agency;".
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/agency/DemographicsConverter.java` - src/main/java/de/caritas/cob/agencyservice/api/admin/service/agency/DemographicsConverter.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.admin.service.agency;".
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/agency/SearchResult.java` - src/main/java/de/caritas/cob/agencyservice/api/admin/service/agency/SearchResult.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.admin.service.agency;".
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/agencyadmincontrol/AgencyAdminAllowedPermissionTogglesSettings.java` - src/main/java/de/caritas/cob/agencyservice/api/admin/service/agencyadmincontrol/AgencyAdminAllowedPermissionTogglesSettings.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol;".
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/agencyadmincontrol/AgencyAdminControlsConverter.java` - src/main/java/de/caritas/cob/agencyservice/api/admin/service/agencyadmincontrol/AgencyAdminControlsConverter.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol;".
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/agencyadmincontrol/AgencyAdminControlsFacade.java` - src/main/java/de/caritas/cob/agencyservice/api/admin/service/agencyadmincontrol/AgencyAdminControlsFacade.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.admin.service.agencyadmincontrol;".

### Data And Persistence

JPA repositories/entities, Liquibase changelogs, database schema files, and persistence tests.

Key files:
- `src/main/java/de/caritas/cob/agencyservice/api/repository/TenantAware.java` - src/main/java/de/caritas/cob/agencyservice/api/repository/TenantAware.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.repository;".
- `src/main/java/de/caritas/cob/agencyservice/api/repository/TenantUnaware.java` - src/main/java/de/caritas/cob/agencyservice/api/repository/TenantUnaware.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.repository;".
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agency/Agency.java` - src/main/java/de/caritas/cob/agencyservice/api/repository/agency/Agency.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.repository.agency;".
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agency/AgencyRepository.java` - src/main/java/de/caritas/cob/agencyservice/api/repository/agency/AgencyRepository.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.repository.agency;".
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agency/AgencyTenantAwareRepository.java` - src/main/java/de/caritas/cob/agencyservice/api/repository/agency/AgencyTenantAwareRepository.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.repository.agency;".
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agency/AgencyTenantUnawareRepository.java` - src/main/java/de/caritas/cob/agencyservice/api/repository/agency/AgencyTenantUnawareRepository.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.repository.agency;".
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agency/DataProtectionPlaceHolderType.java` - src/main/java/de/caritas/cob/agencyservice/api/repository/agency/DataProtectionPlaceHolderType.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.repository.agency;".
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agency/DataProtectionResponsibleContact.java` - src/main/java/de/caritas/cob/agencyservice/api/repository/agency/DataProtectionResponsibleContact.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.repository.agency;".
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agency/DataProtectionResponsibleEntity.java` - src/main/java/de/caritas/cob/agencyservice/api/repository/agency/DataProtectionResponsibleEntity.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.repository.agency;".
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agency/Gender.java` - src/main/java/de/caritas/cob/agencyservice/api/repository/agency/Gender.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.repository.agency;".
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agencyadmincontrol/AgencyAdminControlEntity.java` - src/main/java/de/caritas/cob/agencyservice/api/repository/agencyadmincontrol/AgencyAdminControlEntity.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.repository.agencyadmincontrol;".
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agencyadmincontrol/AgencyAdminControlRepository.java` - src/main/java/de/caritas/cob/agencyservice/api/repository/agencyadmincontrol/AgencyAdminControlRepository.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.repository.agencyadmincontrol;".
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agencypostcoderange/AgencyPostcodeRange.java` - src/main/java/de/caritas/cob/agencyservice/api/repository/agencypostcoderange/AgencyPostcodeRange.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.repository.agencypostcoderange;".
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agencypostcoderange/AgencyPostcodeRangeRepository.java` - src/main/java/de/caritas/cob/agencyservice/api/repository/agencypostcoderange/AgencyPostcodeRangeRepository.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.repository.agencypostcoderange;".
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agencytopic/AgencyTopic.java` - src/main/java/de/caritas/cob/agencyservice/api/repository/agencytopic/AgencyTopic.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.repository.agencytopic;".
- `src/test/java/de/caritas/cob/agencyservice/api/repository/agency/AgencyRepositoryIT.java` - src/test/java/de/caritas/cob/agencyservice/api/repository/agency/AgencyRepositoryIT.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.repository.agency;".

### Configuration

Runtime, build, package, framework, and environment configuration.

Key files:
- `.github/actions/docker-build-push/action.yml` - .github/actions/docker-build-push/action.yml is a config file in ORISO-AgencyService; starts with "name: Reusable Docker Build and Publish steps".
- `.github/actions/maven-build/action.yml` - .github/actions/maven-build/action.yml is a config file in ORISO-AgencyService; starts with "name: Reusable Maven Build steps".
- `.mvn/wrapper/maven-wrapper.properties` - .mvn/wrapper/maven-wrapper.properties is a config file in ORISO-AgencyService; starts with "wrapperVersion=3.3.4".
- `api/agencyadminservice.yaml` - api/agencyadminservice.yaml is a config file in ORISO-AgencyService; starts with "openapi: 3.0.1".
- `api/agencyservice.yaml` - api/agencyservice.yaml is a config file in ORISO-AgencyService; starts with "openapi: 3.0.1".
- `google_checks_light.xml` - google_checks_light.xml is a config file in ORISO-AgencyService; starts with "<?xml version='1.0'?>".
- `package-lock.json` - package-lock.json is a config file in ORISO-AgencyService; starts with "{".
- `package.json` - package.json is a config file in ORISO-AgencyService; starts with "{".
- `pom.xml` - pom.xml is a config file in ORISO-AgencyService; starts with "<?xml version='1.0' encoding='UTF-8'?>".
- `services/applicationsettingsservice.yml` - services/applicationsettingsservice.yml is a config file in ORISO-AgencyService; starts with "openapi: 3.0.1".
- `services/appointmentService.yaml` - services/appointmentService.yaml is a config file in ORISO-AgencyService; starts with "openapi: 3.0.1".
- `services/consultingtypeservice.yaml` - services/consultingtypeservice.yaml is a config file in ORISO-AgencyService; starts with "openapi: 3.0.1".
- `services/tenantservice.yaml` - services/tenantservice.yaml is a config file in ORISO-AgencyService; starts with "openapi: 3.0.1".
- `services/topicservice.yaml` - services/topicservice.yaml is a config file in ORISO-AgencyService; starts with "openapi: 3.0.1".
- `services/useradminservice.yaml` - services/useradminservice.yaml is a config file in ORISO-AgencyService; starts with "openapi: 3.0.1".
- `src/main/resources/application-dev.properties` - src/main/resources/application-dev.properties is a config file in ORISO-AgencyService; starts with "# ---------------- Logging ----------------".

### Deployment And Operations

Docker, CI/CD workflows, reusable GitHub actions, and operational scripts.

Key files:
- `.github/workflows/ci-feature-branch.yml` - .github/workflows/ci-feature-branch.yml is a pipeline file in ORISO-AgencyService; starts with "name: CI - Feature Branch".
- `.github/workflows/ci-main.yml` - .github/workflows/ci-main.yml is a pipeline file in ORISO-AgencyService; starts with "name: CI - Main".
- `.github/workflows/ci-pull-request.yml` - .github/workflows/ci-pull-request.yml is a pipeline file in ORISO-AgencyService; starts with "name: CI - Pull Request".
- `Dockerfile` - Dockerfile is a infra file in ORISO-AgencyService; starts with "FROM eclipse-temurin:21-jre".

### Documentation

Human-facing repository documentation and architecture notes.

Key files:
- `CHANGELOG.md` - CHANGELOG.md is a docs file in ORISO-AgencyService; starts with "# Changelog".
- `README.md` - README.md is a docs file in ORISO-AgencyService; starts with "# ORISO AgencyService".
- `readme.md` - readme.md is a docs file in ORISO-AgencyService; starts with "# Caritas Online-Beratung AgencyService".

### Application Core

Application entry point, shared utilities, exceptions, tenant resolution, and supporting code.

Key files:
- `.gitignore` - .gitignore is a code file in ORISO-AgencyService; starts with "/target/".
- `.swagger-codegen-ignore` - .swagger-codegen-ignore is a code file in ORISO-AgencyService; starts with "# Swagger Codegen Ignore".
- `LICENSE` - LICENSE is a code file in ORISO-AgencyService; starts with "GNU AFFERO GENERAL PUBLIC LICENSE".
- `check-version.sh` - check-version.sh is a script file in ORISO-AgencyService; starts with "#!/bin/bash".
- `commitlint.config.js` - commitlint.config.js is a code file in ORISO-AgencyService; starts with "module.exports = { extends: ['@commitlint/config-conventional'] };".
- `deploy-development.sh` - deploy-development.sh is a script file in ORISO-AgencyService; starts with "#!/bin/bash".
- `docker-build.cmd` - docker-build.cmd is a script file in ORISO-AgencyService; starts with "docker build --no-cache -t cob/agencyservice:development .".
- `mvnw` - mvnw is a code file in ORISO-AgencyService; starts with "#!/bin/sh".
- `mvnw.cmd` - mvnw.cmd is a script file in ORISO-AgencyService; starts with "<# : batch portion".
- `run-trivy.sh` - run-trivy.sh is a script file in ORISO-AgencyService; starts with "rm report*.sarif".
- `src/main/java/de/caritas/cob/agencyservice/AgencyServiceApplication.java` - src/main/java/de/caritas/cob/agencyservice/AgencyServiceApplication.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice;".
- `src/main/java/de/caritas/cob/agencyservice/api/ApiDefaultResponseEntityExceptionHandler.java` - src/main/java/de/caritas/cob/agencyservice/api/ApiDefaultResponseEntityExceptionHandler.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api;".
- `src/main/java/de/caritas/cob/agencyservice/api/ApiResponseEntityExceptionHandler.java` - src/main/java/de/caritas/cob/agencyservice/api/ApiResponseEntityExceptionHandler.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api;".
- `src/main/java/de/caritas/cob/agencyservice/api/admin/hallink/AgencyLinksBuilder.java` - src/main/java/de/caritas/cob/agencyservice/api/admin/hallink/AgencyLinksBuilder.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.admin.hallink;".
- `src/main/java/de/caritas/cob/agencyservice/api/admin/hallink/HalLinkBuilder.java` - src/main/java/de/caritas/cob/agencyservice/api/admin/hallink/HalLinkBuilder.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.admin.hallink;".
- `src/main/java/de/caritas/cob/agencyservice/api/admin/hallink/RootDTOBuilder.java` - src/main/java/de/caritas/cob/agencyservice/api/admin/hallink/RootDTOBuilder.java is a code file in ORISO-AgencyService; starts with "package de.caritas.cob.agencyservice.api.admin.hallink;".


## Major Flows

- Entry and boot flow: `src/main/java/de/caritas/cob/agencyservice/AgencyServiceApplication.java`, `pom.xml`, and Spring Boot configuration.
- Agency admin API flow: `api/agencyadminservice.yaml`, `AgencyAdminController`, `AgencyAdminService`, agency settings service, and agency admin controls facade/service/converter classes.
- Persistence flow: JPA entities and repositories under `api/repository` connect to Liquibase changesets under `src/main/resources/db/changelog`.
- Tenant and auth flow: Spring Security resource-server configuration, authority mapping, authenticated user configuration, and tenant resolver services guard API boundaries.
- Deployment flow: GitHub Actions workflows build/test the Maven service and Dockerfile packages the runnable service.

## API And Service Dependencies

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

## Authentication Relationship

- `services/tenantservice.yaml` - config, yaml
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/agency/AgencyAdminSearchTenantSupportService.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/admin/service/agencyadmincontrol/AgencyAdminAllowedPermissionTogglesSettings.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/admin/validation/validators/AgencyTenantValidator.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/admin/validation/validators/AgencyUpdatePermissionValidator.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/authorization/Authority.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/authorization/RoleAuthorizationAuthorityMapper.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/exception/KeycloakException.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/repository/TenantAware.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/repository/TenantUnaware.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agency/AgencyTenantAwareRepository.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agency/AgencyTenantUnawareRepository.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/service/TenantHeaderSupplier.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/service/TenantHibernateInterceptor.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/service/TenantService.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/service/securityheader/SecurityHeaderSupplier.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/tenant/AccessTokenTenantResolver.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/tenant/CustomHeaderTenantResolver.java` - code, java

## Database Relationship

- `src/main/java/de/caritas/cob/agencyservice/api/ApiDefaultResponseEntityExceptionHandler.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/ApiResponseEntityExceptionHandler.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/repository/TenantAware.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/repository/TenantUnaware.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agency/Agency.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agency/AgencyRepository.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agency/AgencyTenantAwareRepository.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agency/AgencyTenantUnawareRepository.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agency/DataProtectionPlaceHolderType.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agency/DataProtectionResponsibleContact.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agency/DataProtectionResponsibleEntity.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agency/Gender.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agencyadmincontrol/AgencyAdminControlEntity.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agencyadmincontrol/AgencyAdminControlRepository.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agencypostcoderange/AgencyPostcodeRange.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agencypostcoderange/AgencyPostcodeRangeRepository.java` - code, java
- `src/main/java/de/caritas/cob/agencyservice/api/repository/agencytopic/AgencyTopic.java` - code, java
- `src/main/resources/db/changelog/agencyservice-dev-master.xml` - data, xml

## Deployment Relationship

- `.github/workflows/ci-feature-branch.yml` - pipeline, yaml
- `.github/workflows/ci-main.yml` - pipeline, yaml
- `.github/workflows/ci-pull-request.yml` - pipeline, yaml
- `Dockerfile` - infra, dockerfile
- `check-version.sh` - script, shell
- `deploy-development.sh` - script, shell
- `docker-build.cmd` - script, batch
- `mvnw.cmd` - script, batch
- `run-trivy.sh` - script, shell

## ORISO Ecosystem Fit

`ORISO-AgencyService` is one service in the ORISO platform. Locally visible integration contracts show relationships to application settings, appointment, consulting type, tenant, topic, and user admin services.
