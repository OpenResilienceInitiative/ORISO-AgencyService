#!/usr/bin/env python3

from __future__ import annotations

import argparse
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Verify Surefire/Failsafe XML reports")
    parser.add_argument("reports", nargs="*", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    reports = args.reports or sorted(Path("target/surefire-reports").glob("TEST-*.xml"))
    reports = [report for report in reports if report.is_file()]

    if not reports:
        print("::error::Zero test reports found — tests were silently skipped or never ran.")
        return 1

    total_tests = total_skipped = 0
    failing_reports: list[tuple[Path, int, int]] = []
    skipped_reports: list[tuple[Path, int]] = []
    for report in reports:
        root = ET.parse(report).getroot()
        total_tests += int(root.attrib.get("tests", 0))
        failures = int(root.attrib.get("failures", 0))
        errors = int(root.attrib.get("errors", 0))
        skipped = int(root.attrib.get("skipped", 0))
        total_skipped += skipped
        if failures or errors:
            failing_reports.append((report, failures, errors))
        if skipped:
            skipped_reports.append((report, skipped))

    executed_tests = total_tests - total_skipped
    print(
        f"Parsed {len(reports)} test report(s): "
        f"tests={total_tests} executed={executed_tests} skipped={total_skipped}."
    )
    failed = False
    if executed_tests <= 0:
        print("::error::Test reports contain zero executed tests.")
        failed = True

    if skipped_reports:
        print("::error::Skipped tests are not allowed in a blocking test gate:")
        for report, skipped in skipped_reports:
            print(f"- {report}: skipped={skipped}")
        failed = True

    if failing_reports:
        print("Test reports contain failures/errors:")
        for report, failures, errors in failing_reports:
            print(f"- {report}: failures={failures}, errors={errors}")
        failed = True

    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
