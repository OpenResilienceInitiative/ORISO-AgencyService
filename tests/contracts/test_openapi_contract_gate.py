import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[2]


class OpenApiContractGateTest(unittest.TestCase):
    def test_consumer_gate_propagates_oasdiff_failure(self):
        gate = ROOT / "scripts/contracts/verify-consumer-contract.sh"
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            provider_root = temp / "provider"
            provider_root.mkdir()
            (provider_root / "provider.yaml").write_text("openapi: 3.0.3\n")
            consumer = temp / "consumer.yaml"
            consumer.write_text("openapi: 3.0.3\n")

            fake_redocly = temp / "redocly"
            fake_redocly.write_text("#!/usr/bin/env bash\n" 'cp "$2" "$4"\n')
            fake_redocly.chmod(0o755)

            fake_oasdiff = temp / "oasdiff"
            fake_oasdiff.write_text("#!/usr/bin/env bash\nexit 23\n")
            fake_oasdiff.chmod(0o755)

            env = os.environ.copy()
            env["REDOCLY_BIN"] = str(fake_redocly)
            env["REDOCLY_VERSION"] = "test"
            env["OASDIFF_BIN"] = str(fake_oasdiff)
            result = subprocess.run(
                [gate, consumer, provider_root, "provider.yaml"],
                cwd=ROOT,
                env=env,
                check=False,
            )

        self.assertEqual(23, result.returncode)

    def test_workflow_pins_tools_and_publishes_provider_artifact(self):
        workflow = (ROOT / ".github/workflows/openapi-contracts.yml").read_text()
        self.assertIn("REDOCLY_VERSION: 2.40.0", workflow)
        self.assertIn("OASDIFF_VERSION: v1.17.0", workflow)
        self.assertIn("name: openapi provider and consumer contracts", workflow)
        self.assertIn("actions/upload-artifact@v4", workflow)
        self.assertIn("provider-contracts-${{ github.sha }}", workflow)
        self.assertNotIn("continue-on-error:", workflow)

    def test_all_backend_consumer_contracts_are_checked(self):
        workflow = (ROOT / ".github/workflows/openapi-contracts.yml").read_text()
        expected = {
            "services/applicationsettingsservice.yml": ".providers/consulting",
            "services/consultingtypeservice.yaml": ".providers/consulting",
            "services/topicservice.yaml": ".providers/consulting",
            "services/tenantservice.yaml": ".providers/tenant",
            "services/useradminservice.yaml": ".providers/user",
        }
        for contract, checkout in expected.items():
            self.assertIn(contract, workflow)
            self.assertIn(checkout, workflow)

    def test_topic_string_contracts_do_not_claim_incompatible_formats(self):
        contract = yaml.safe_load((ROOT / "services/topicservice.yaml").read_text())
        schemas = contract["components"]["schemas"]

        for schema_name in ("WelcomeMessage", "FallBackUrl"):
            with self.subTest(schema=schema_name):
                self.assertEqual("string", schemas[schema_name]["type"])
                self.assertNotIn("format", schemas[schema_name])

    def test_pull_request_uses_coordinated_provider_commits(self):
        # What has to hold is the shape, not one particular commit: on a pull
        # request each coordinated provider is checked out at an immutable
        # 40-character SHA, and everywhere else it follows pre-dev.
        #
        # This used to repeat the two SHAs as literals here, so a coordinated
        # bump meant editing the workflow and this file in step. Branches that
        # updated one and not the other went red on a mismatch that said
        # nothing about the contracts — the failure this test exists to catch.
        workflow = (ROOT / ".github/workflows/openapi-contracts.yml").read_text()

        for provider in ("ORISO-TenantService", "ORISO-UserService"):
            with self.subTest(provider=provider):
                match = re.search(
                    r"repository: OpenResilienceInitiative/"
                    + re.escape(provider)
                    + r"\s*\n\s*ref: (?P<ref>.+)\n",
                    workflow,
                )
                self.assertIsNotNone(
                    match, f"{provider} is not checked out by the contract workflow"
                )
                ref = match.group("ref")
                self.assertRegex(
                    ref,
                    r"github\.event_name == 'pull_request'",
                    f"{provider} must pin a commit on the pull-request path",
                )
                self.assertRegex(
                    ref,
                    r"'[0-9a-f]{40}'",
                    f"{provider} must pin a full 40-character commit SHA, not a branch",
                )
                self.assertIn(
                    "|| 'pre-dev'",
                    ref,
                    f"{provider} must follow pre-dev outside pull requests",
                )

    def test_contract_gate_tests_are_executed_by_ci(self):
        # A gate assertion that never runs protects nothing. Without a job that
        # invokes pytest, this file can drift away from the workflow and the
        # scripts it describes while every check stays green.
        workflow = (ROOT / ".github/workflows/openapi-contracts.yml").read_text()

        self.assertIn("contract-gate-tests:", workflow)
        self.assertIn("python -m pytest -q tests/contracts", workflow)


if __name__ == "__main__":
    unittest.main()
