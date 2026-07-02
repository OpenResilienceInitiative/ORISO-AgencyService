#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

K8S_NAMESPACE="${K8S_NAMESPACE:-${NAMESPACE:-caritas}}"
DEPLOYMENT_NAME="${DEPLOYMENT_NAME:-${DEPLOYMENT:-oriso-platform-agencyservice}}"
IMAGE_NAME="${IMAGE_NAME:-oriso-agencyservice}"

./mvnw clean package -DskipTests -Dmaven.test.skip=true -Dspotless.check.skip=true -Dcheckstyle.skip=true
TS=$(date +%s)
IMAGE_TAG="${IMAGE_NAME}:dev-${TS}"

docker build -t "${IMAGE_TAG}" .
docker tag "${IMAGE_TAG}" "${IMAGE_NAME}:latest"
docker save "${IMAGE_TAG}" | sudo k3s ctr images import - > /dev/null 2>&1
docker save "${IMAGE_NAME}:latest" | sudo k3s ctr images import - > /dev/null 2>&1
kubectl rollout restart "deployment/${DEPLOYMENT_NAME}" -n "${K8S_NAMESPACE}"
kubectl rollout status "deployment/${DEPLOYMENT_NAME}" -n "${K8S_NAMESPACE}" --timeout=240s
