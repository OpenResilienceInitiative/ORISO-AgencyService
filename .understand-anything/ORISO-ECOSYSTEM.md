# ORISO AgencyService Ecosystem Connections

## Navigation

- [Role In ORISO](#role-in-oriso)
- [Inbound Consumers](#inbound-consumers)
- [Outbound Services](#outbound-services)
- [Infrastructure](#infrastructure)
- [Data Ownership](#data-ownership)
- [Operational Notes](#operational-notes)

## Role In ORISO

AgencyService owns agency master data and coverage rules. Other services should not duplicate agency postcode matching, agency topic relations, agency offline state, or agency Matrix service account credentials.

## Inbound Consumers

- ORISO Frontend calls public agency lookup endpoints during registration/search and admin endpoints in agency administration flows.
- ORISO UserService can consume agency data to connect sessions, consultants, agencies, and team-agency behavior.
- Internal ORISO services can call `/internal/agencies/{agencyId}/matrix-service-account` to read or provision Matrix credentials.
- Monitoring systems call actuator health endpoints.

## Outbound Services

- ConsultingTypeService: registration postcode rules, topic registration settings, and white-spot agency fallback settings.
- TenantService: restricted tenant data, tenant IDs, and subdomain mapping.
- TopicService: topic names and topic catalog enrichment.
- ApplicationSettingsService: global settings, especially single-domain multitenancy main tenant subdomain.
- UserAdminService: consultant adaptation when agency type changes and restricted agency admin scoping.
- AppointmentService: agency master-data sync/delete when appointment features are enabled.
- Matrix Synapse: agency service-account registration and password rotation.
- Keycloak/OIDC: JWT validation and realm role claims.

## Infrastructure

MariaDB stores agency, postcode range, and topic relation state. Liquibase changelogs describe the schema, but runtime properties disable Liquibase by default. Docker packages `AgencyService.jar`. Environment variables supply Matrix, Keycloak, datasource, tracing, and peer-service URLs.

## Data Ownership

Owned here: agency rows, postcode ranges, agency-topic relation rows, Matrix user ID/password, deletion marker, offline status, data-protection fields, demographics, logo, external URL flag, and counselling relations.

Referenced here but owned elsewhere: users and consultant relations in UserAdminService, consulting type settings in ConsultingTypeService, tenant settings in TenantService, topic catalog in TopicService, global settings in ApplicationSettingsService, appointments in AppointmentService, identity in Keycloak, and Matrix runtime accounts in Synapse.

## Operational Notes

Most peer service URLs default to empty values and must be injected by deployment configuration. `multitenancy.enabled=false` by default, but tenant-aware code is present and heavily tested. `feature.topics.enabled=true`; demographics and appointment features are disabled by default.
