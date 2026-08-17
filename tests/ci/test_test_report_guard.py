from __future__ import annotations

from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
GUARD = ROOT / "scripts/ci/verify-test-reports.py"


class TestReportGuardTest(unittest.TestCase):
    def run_guard(self, reports: list[dict[str, int]]) -> subprocess.CompletedProcess[str]:
        with tempfile.TemporaryDirectory() as temp_dir:
            paths = []
            for index, attributes in enumerate(reports):
                path = Path(temp_dir) / f"TEST-Suite{index}.xml"
                rendered = " ".join(f'{name}="{value}"' for name, value in attributes.items())
                path.write_text(f"<testsuite name=\"Suite{index}\" {rendered}></testsuite>\n")
                paths.append(path)

            return subprocess.run(
                [sys.executable, GUARD, *paths],
                cwd=temp_dir,
                check=False,
                capture_output=True,
                text=True,
            )

    def test_rejects_zero_reports(self):
        result = self.run_guard([])

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Zero test reports", result.stdout)

    def test_rejects_zero_declared_tests(self):
        result = self.run_guard(
            [{"tests": 0, "failures": 0, "errors": 0, "skipped": 0}]
        )

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("zero executed tests", result.stdout)

    def test_rejects_an_all_skipped_report_and_counts_zero_executed(self):
        result = self.run_guard(
            [{"tests": 4, "failures": 0, "errors": 0, "skipped": 4}]
        )

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("executed=0", result.stdout)
        self.assertIn("skipped=4", result.stdout)

    def test_rejects_one_skip_beside_executed_tests_and_names_the_report(self):
        result = self.run_guard(
            [
                {"tests": 3, "failures": 0, "errors": 0, "skipped": 0},
                {"tests": 2, "failures": 0, "errors": 0, "skipped": 1},
            ]
        )

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("TEST-Suite1.xml", result.stdout)
        self.assertIn("skipped=1", result.stdout)
        self.assertIn("executed=4", result.stdout)

    def test_rejects_failures_and_errors(self):
        result = self.run_guard(
            [
                {"tests": 2, "failures": 1, "errors": 0, "skipped": 0},
                {"tests": 1, "failures": 0, "errors": 1, "skipped": 0},
            ]
        )

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("failures=1", result.stdout)
        self.assertIn("errors=1", result.stdout)

    def test_accepts_only_executed_passing_tests(self):
        result = self.run_guard(
            [{"tests": 3, "failures": 0, "errors": 0, "skipped": 0}]
        )

        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("tests=3 executed=3 skipped=0", result.stdout)


if __name__ == "__main__":
    unittest.main()
