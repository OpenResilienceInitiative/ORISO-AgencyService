#!/usr/bin/env bash

set -euo pipefail

maven_wrapper="${ORISO_MAVEN_WRAPPER:-./mvnw}"
required_tests=(
  ActuatorControllerIT
  InternalMatrixServiceAccountAuthorizationIT
  AgencyControllerIT
  AgencyControllerAuthorizationIT
  TracingConfigVerificationIT
  LiquibaseChangelogDriftIT
  DemoBaselineChangesetIT
)
test_selector="$(IFS=,; echo "${required_tests[*]}")"

"${maven_wrapper}" -B -Dtest="${test_selector}" test

required_reports=()
for test_name in "${required_tests[@]}"; do
  matches=(target/surefire-reports/TEST-*."${test_name}".xml)
  if [[ ! -e "${matches[0]}" ]]; then
    echo "Required integration test produced no report: ${test_name}" >&2
    exit 1
  fi
  required_reports+=("${matches[@]}")
done

python3 scripts/ci/verify-test-reports.py "${required_reports[@]}"
echo "Required integration contract produced ${#required_reports[@]} complete report(s)."
