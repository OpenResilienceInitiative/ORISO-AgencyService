# ORISO AgencyService

## Overview
Spring Boot service for managing counseling agencies in the Online Beratung platform.

## Quick Start

### Run in Kubernetes
The service automatically starts via Kubernetes deployment using Maven Spring Boot plugin.

```bash
# Check service status
kubectl get pods -n caritas | grep agencyservice
kubectl logs -n caritas -l app=agencyservice --tail=100
```

### Run Locally (Development)
```bash
cd <path-to-your-workspace>/ORISO-AgencyService
chmod +x mvnw
./mvnw spring-boot:run -Dspring-boot.run.profiles=local -DskipTests
```

## Configuration

### Database Connection
Point the service at the cluster's MariaDB service; the address is environment-specific and lives in the environment's secret store, not here.

```properties
# application-local.properties — supply real values from the environment
spring.datasource.url=jdbc:mariadb://<mariadb-host>:3306/agencyservice
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
```

### Liquibase
**STATUS:** ⚠️ **DISABLED**

```properties
spring.liquibase.enabled=false
```

Database schemas are managed separately in `ORISO-Database` repository.

### Keycloak
```properties
keycloak.auth-server-url=http://localhost:8080
keycloak.realm=online-beratung
keycloak.resource=agency-service
```

## Important Notes
- **Port:** `8084`
- **Profile:** `local`
- **Liquibase:** DISABLED - schemas managed in ORISO-Database
- **Database:** Uses the cluster's MariaDB service (NOT localhost)
- **Host Network:** Enabled in Kubernetes for direct localhost access
- **Caching:** Ehcache enabled for agency data

## Kubernetes Deployment Path
The deploy host keeps the working copy under the operator's home directory; the exact path is environment-specific and documented in the deployment runbook.

## Health Check
```bash
curl http://localhost:8084/actuator/health
```

## Dependencies
- Java 21
- Spring Boot 4.0.1
- MariaDB
- Keycloak
- Ehcache

