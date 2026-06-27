#!/usr/bin/env bash

set -euo pipefail
cd "$(dirname "$0")"

if [ -z "${JAVA_HOME:-}" ]; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home)"
fi
export PATH="$JAVA_HOME/bin:$PATH"

if [ -f config.env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./config.env
  set +a
fi

# Keep Maven on the default JVM truststore. The dev truststore is passed only
# to the Spring Boot app JVM below, otherwise Maven Central downloads can fail.
unset JAVA_TOOL_OPTIONS

JVM_ARGUMENTS=""
if [ -n "${LOCAL_JAVA_TRUSTSTORE:-}" ]; then
  JVM_ARGUMENTS="-Djavax.net.ssl.trustStore=${LOCAL_JAVA_TRUSTSTORE} -Djavax.net.ssl.trustStorePassword=${LOCAL_JAVA_TRUSTSTORE_PASSWORD:-changeit}"
fi

echo "Datasource: ${SPRING_DATASOURCE_URL}"
./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.jvmArguments="${JVM_ARGUMENTS}" \
  -Dspotless.check.skip=true
