import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


def job_block(workflow: str, job_name: str) -> str:
    marker = f"  {job_name}:\n"
    start = workflow.index(marker)
    remainder = workflow[start + len(marker) :]
    next_job = re.search(r"\n  [a-zA-Z0-9_-]+:\n", remainder)
    return remainder if next_job is None else remainder[: next_job.start()]


class RequiredCiContractTest(unittest.TestCase):
    def test_required_runner_propagates_maven_failure(self):
        runner = ROOT / "scripts/ci/run-required-integration-tests.sh"
        with tempfile.TemporaryDirectory() as temp_dir:
            fake_maven = Path(temp_dir) / "mvnw"
            fake_maven.write_text("#!/usr/bin/env bash\nexit 23\n")
            fake_maven.chmod(0o755)
            env = os.environ.copy()
            env["ORISO_MAVEN_WRAPPER"] = str(fake_maven)

            result = subprocess.run([runner], cwd=ROOT, env=env, check=False)

        self.assertEqual(23, result.returncode)

    def test_pull_request_has_one_truthful_required_conclusion(self):
        workflow = (ROOT / ".github/workflows/ci-pull-request.yml").read_text()
        integration = job_block(workflow, "required-integration-tests")
        aggregate = job_block(workflow, "required-ci")

        self.assertIn("name: required integration tests", integration)
        self.assertNotIn("continue-on-error:", integration)
        self.assertIn(
            "needs: [validate, required-integration-tests, contract-tests,"
            " full-integration-suite]",
            aggregate,
        )
        self.assertIn("if: always()", aggregate)
        self.assertIn("name: required PreDev CI", aggregate)
        self.assertIn("needs.required-integration-tests.result", aggregate)
        self.assertIn("needs.contract-tests.result", aggregate)
        self.assertIn("needs.full-integration-suite.result", aggregate)
        # Reading a result into the environment is not the same as acting on
        # it: the conclusion itself must consider every required job.
        self.assertIn('"${CONTRACT_RESULT}" != success', aggregate)
        # A failed full-suite job must also fail the aggregate conclusion.
        self.assertIn('"${FULL_SUITE_RESULT}" != success', aggregate)
        self.assertIn("full-suite=${FULL_SUITE_RESULT}", aggregate)

    def test_ci_contract_tests_are_executed_by_ci(self):
        # These assertions are worthless unless something runs them. Without a
        # job that invokes pytest, tests/ci is dead weight that can drift out
        # of sync with the workflows it claims to protect.
        workflow = (ROOT / ".github/workflows/ci-pull-request.yml").read_text()
        contract = job_block(workflow, "contract-tests")

        self.assertIn("pytest", contract)
        self.assertIn("tests/ci", contract)
        self.assertNotIn("continue-on-error:", contract)

    def test_publish_waits_for_required_integration_tests(self):
        workflow = (ROOT / ".github/workflows/ci-main.yml").read_text()
        publish = job_block(workflow, "publish")
        integration = job_block(workflow, "required-integration-tests")

        self.assertIn("needs: required-integration-tests", publish)
        self.assertIn("name: required integration tests", integration)
        self.assertNotIn("continue-on-error:", integration)

    def test_full_integration_suite_is_blocking_in_every_workflow(self):
        for relative_path in (
            ".github/workflows/ci-pull-request.yml",
            ".github/workflows/ci-feature-branch.yml",
            ".github/workflows/ci-main.yml",
        ):
            workflow = (ROOT / relative_path).read_text()
            self.assertNotIn("legacy-" "quarantine-expiry", workflow)
            self.assertNotIn("legacy-integration-quarantine", workflow)
            self.assertNotIn("QUARANTINE_" "EXPIRES", workflow)
            full_suite = job_block(workflow, "full-integration-suite")
            self.assertIn("name: full integration suite", full_suite)
            self.assertIn("uses: ./.github/actions/maven-verify-burnin", full_suite)
            self.assertNotIn("continue-on-error:", full_suite)

        action = (ROOT / ".github/actions/maven-verify-burnin/action.yml").read_text()
        self.assertIn("scripts/ci/verify-test-reports.py", action)

    def test_shared_action_does_not_swallow_the_maven_failure(self):
        # Moving continue-on-error from the job into the action would leave the
        # workflow looking blocking while the quarantine job stayed green no
        # matter what Maven did.
        action = (ROOT / ".github/actions/maven-verify-burnin/action.yml").read_text()

        self.assertIn("id: legacy_verify", action)
        self.assertIn("steps.legacy_verify.outcome", action)
        self.assertNotIn("continue-on-error:", action)
        self.assertNotIn("if ! ./mvnw", action)

    def test_blocking_suites_use_the_shared_zero_skip_guard(self):
        guard = "scripts/ci/verify-test-reports.py"
        unit_action = (ROOT / ".github/actions/maven-build/action.yml").read_text()
        required_runner = (ROOT / "scripts/ci/run-required-integration-tests.sh").read_text()

        self.assertIn(guard, unit_action)
        self.assertIn(guard, required_runner)

    def test_required_runner_owns_the_real_mariadb_contracts(self):
        required_runner = (ROOT / "scripts/ci/run-required-integration-tests.sh").read_text()

        self.assertIn("LiquibaseChangelogDriftIT", required_runner)
        self.assertIn("DemoBaselineChangesetIT", required_runner)

if __name__ == "__main__":
    unittest.main()
