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
cd /home/caritas/Desktop/online-beratung/caritas-workspace/ORISO-AgencyService
chmod +x mvnw
./mvnw spring-boot:run -Dspring-boot.run.profiles=local -DskipTests
```

## Configuration

### Database Connection
**MariaDB ClusterIP:** `10.43.123.72:3306`

```properties
# application-local.properties
spring.datasource.url=jdbc:mariadb://10.43.123.72:3306/agencyservice
spring.datasource.username=agencyservice
spring.datasource.password=agencyservice
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
- **Database:** Uses MariaDB ClusterIP (NOT localhost)
- **Host Network:** Enabled in Kubernetes for direct localhost access
- **Caching:** Ehcache enabled for agency data

## Kubernetes Deployment Path
```
/home/caritas/Desktop/online-beratung/caritas-workspace/ORISO-AgencyService
```

## Health Check
```bash
curl http://localhost:8084/actuator/health
```

## Dependencies
- Java 17
- Spring Boot 2.7.14
- MariaDB
- Keycloak
- Ehcache

