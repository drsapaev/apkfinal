#!/usr/bin/env python3
"""
Coverage gate for the ClinicSystemMobile Android project.

Parses a JaCoCo XML report and enforces minimum coverage thresholds
on:
  - The overall project
  - Specific packages (e.g. `data/`, `domain/`)

Usage:
    coverage-gate.py <jacoco.xml> [--min-overall N] [--min-package <name>:<N> ...]

Exit code:
    0  All thresholds met
    1  One or more thresholds violated
    2  Invalid input (file missing, parse error)

The "coverage" metric used is LINE coverage (most intuitive for Android
projects where branch coverage can be noisy with Kotlin `when` and
 sealed classes).
"""

import argparse
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path


@dataclass
class CoverageResult:
    name: str
    missed: int
    covered: int
    total: int

    @property
    def percentage(self) -> float:
        if self.total == 0:
            return 100.0
        return (self.covered / self.total) * 100.0


def parse_jacoco(xml_path: Path) -> tuple[CoverageResult, dict[str, CoverageResult]]:
    """
    Parse the JaCoCo XML report.

    Returns:
        Tuple of (overall_result, per_package_dict).
        Package key is the last segment of the package path (e.g. `data`,
        `domain`, `ui`). Sub-packages are aggregated into their top-level
        parent.
    """
    try:
        tree = ET.parse(xml_path)
    except ET.ParseError as e:
        print(f"❌ Failed to parse JaCoCo XML: {e}", file=sys.stderr)
        sys.exit(2)
    except FileNotFoundError:
        print(f"❌ JaCoCo XML not found: {xml_path}", file=sys.stderr)
        sys.exit(2)

    root = tree.getroot()

    # Overall counters
    overall = _counters_to_result("overall", root)

    # Per top-level package
    per_pkg: dict[str, CoverageResult] = {}
    for pkg in root.findall(".//package"):
        pkg_name = pkg.get("name", "")
        # Normalize: `com/aistudio/clinicsystem/data/repository` → `data`
        # Take the 4th segment (0-indexed: 0=com, 1=aistudio, 2=clinicsystem, 3=top_pkg)
        parts = pkg_name.split("/")
        if len(parts) < 4:
            top = "root"
        else:
            top = parts[3]
        agg = per_pkg.get(top)
        pkg_result = _counters_to_result(top, pkg)
        if agg is None:
            per_pkg[top] = pkg_result
        else:
            per_pkg[top] = CoverageResult(
                name=top,
                missed=agg.missed + pkg_result.missed,
                covered=agg.covered + pkg_result.covered,
                total=agg.total + pkg_result.total,
            )

    return overall, per_pkg


def _counters_to_result(name: str, element: ET.Element) -> CoverageResult:
    """Extract LINE coverage counters from a JaCoCo XML element."""
    for counter in element.findall("counter"):
        if counter.get("type") == "LINE":
            missed = int(counter.get("missed", 0))
            covered = int(counter.get("covered", 0))
            return CoverageResult(
                name=name,
                missed=missed,
                covered=covered,
                total=missed + covered,
            )
    # No LINE counter → treat as fully covered (empty)
    return CoverageResult(name=name, missed=0, covered=0, total=0)


def print_report(
    overall: CoverageResult,
    per_pkg: dict[str, CoverageResult],
    min_overall: float,
    min_packages: dict[str, float],
) -> bool:
    """Pretty-print the coverage report. Returns True if all gates pass."""
    all_passed = True

    print("=" * 70)
    print("COVERAGE GATE REPORT")
    print("=" * 70)

    # Per-package breakdown
    print("\nPer-package line coverage (top-level):")
    print(f"  {'Package':<15} {'Coverage':>10} {'Threshold':>10} {'Status':>8}")
    print(f"  {'-' * 15} {'-' * 10} {'-' * 10} {'-' * 8}")
    for pkg_name in sorted(per_pkg.keys()):
        r = per_pkg[pkg_name]
        threshold = min_packages.get(pkg_name)
        pct = r.percentage
        if threshold is None:
            status = "—"
            status_str = "INFO"
        elif pct >= threshold:
            status = "✓"
            status_str = "PASS"
        else:
            status = "✗"
            status_str = "FAIL"
            all_passed = False
        print(f"  {pkg_name:<15} {pct:>9.2f}% {threshold or 0:>9.0f}% {status:>8}")

    # Overall
    print()
    print(f"Overall line coverage: {overall.percentage:.2f}% (threshold: {min_overall:.0f}%)")
    if overall.percentage < min_overall:
        print(f"  ❌ FAIL: overall coverage below {min_overall:.0f}%")
        all_passed = False
    else:
        print(f"  ✅ PASS")

    print("\n" + "=" * 70)
    if all_passed:
        print("✅ ALL COVERAGE GATES PASSED")
    else:
        print("❌ COVERAGE GATE FAILED — see above")
    print("=" * 70)
    return all_passed


def main() -> int:
    parser = argparse.ArgumentParser(description="JaCoCo coverage gate")
    parser.add_argument("xml", type=Path, help="Path to jacocoTestReport.xml")
    parser.add_argument(
        "--min-overall",
        type=float,
        default=50.0,
        help="Minimum overall line coverage (default: 50)",
    )
    parser.add_argument(
        "--min-package",
        action="append",
        default=[],
        metavar="NAME:PERCENT",
        help="Per-package minimum, e.g. --min-package data:70",
    )
    args = parser.parse_args()

    if not args.xml.exists():
        print(f"❌ JaCoCo XML not found: {args.xml}", file=sys.stderr)
        return 2

    min_packages: dict[str, float] = {}
    for spec in args.min_package:
        try:
            name, pct_str = spec.rsplit(":", 1)
            min_packages[name.strip()] = float(pct_str)
        except ValueError:
            print(f"❌ Invalid --min-package spec: '{spec}'. Expected NAME:PERCENT", file=sys.stderr)
            return 2

    overall, per_pkg = parse_jacoco(args.xml)
    passed = print_report(overall, per_pkg, args.min_overall, min_packages)
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
