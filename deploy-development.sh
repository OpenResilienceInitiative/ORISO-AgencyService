#!/bin/bash
set -e
cd /root/online-beratung/ORISO-Complete/caritas-workspace/ORISO-AgencyService
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean package -DskipTests -Dmaven.test.skip=true -Dspotless.check.skip=true -Dcheckstyle.skip=true
cp -f target/AgencyService.jar AgencyService.jar
TS=$(date +%s)
IMAGE_TAG="oriso-agencyservice:dev-${TS}"
docker build -t ${IMAGE_TAG} .
docker tag ${IMAGE_TAG} oriso-agencyservice:latest
docker save ${IMAGE_TAG} | sudo k3s ctr images import - > /dev/null 2>&1
docker save oriso-agencyservice:latest | sudo k3s ctr images import - > /dev/null 2>&1
kubectl rollout restart deployment/oriso-platform-agencyservice -n caritas
kubectl rollout status deployment/oriso-platform-agencyservice -n caritas --timeout=240s
