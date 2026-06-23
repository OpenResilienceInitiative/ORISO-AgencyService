#!/bin/bash
set -e

NAMESPACE=${NAMESPACE:-caritas}
DEPLOYMENT=${DEPLOYMENT:-oriso-platform-agencyservice}
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}
export PATH="$JAVA_HOME/bin:$PATH"

cd "$(dirname "$(realpath "$0")")"

mvn clean package -DskipTests -Dmaven.test.skip=true -Dspotless.check.skip=true -Dcheckstyle.skip=true
cp -f target/AgencyService.jar AgencyService.jar
TS=$(date +%s)
IMAGE_TAG="oriso-agencyservice:dev-${TS}"
docker build -t ${IMAGE_TAG} .
docker tag ${IMAGE_TAG} oriso-agencyservice:latest
docker save ${IMAGE_TAG} | sudo k3s ctr images import - > /dev/null 2>&1
docker save oriso-agencyservice:latest | sudo k3s ctr images import - > /dev/null 2>&1
kubectl rollout restart deployment/${DEPLOYMENT} -n ${NAMESPACE}
kubectl rollout status deployment/${DEPLOYMENT} -n ${NAMESPACE} --timeout=240s
