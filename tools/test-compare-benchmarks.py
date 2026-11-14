#!/usr/bin/env python3
"""Synthetic regression tests; these are not device performance results."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("comparison", Path(__file__).with_name("compare-benchmarks.py"))
comparison = importlib.util.module_from_spec(spec)
spec.loader.exec_module(comparison)


class ComparisonTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.reference = [self.capture(f"a{i}", "a", fps) for i, fps in enumerate((20, 21, 22))]
        self.candidate = [self.capture(f"b{i}", "b", fps) for i, fps in enumerate((24, 25, 26))]

    def capture(self, name, commit, fps):
        directory = self.root / name
        directory.mkdir()
        context = {key: "synthetic" for key in comparison.CONTEXT_FIELDS}
        context.update(commit=commit, apk_sha256=commit * 64,
                       config_sha256="c" * 64, game_executable_sha256="d" * 64,
                       frame_generation="off")
        summary = dict(package="test.package", measurement_elapsed_s=60,
                       presented_fps=fps, presented_frame_ms_median=1000 / fps,
                       presented_frame_ms_p95=60, presented_frame_ms_p99=80)
        (directory / "summary.json").write_text(json.dumps(summary))
        (directory / "benchmark-context.json").write_text(json.dumps(context))
        return comparison.read_capture(directory)

    def test_medians_ranges_and_percent_change(self):
        report = comparison.compare(self.reference, self.candidate)
        fps = report["metrics"]["presented_fps"]
        self.assertEqual(fps["reference_median"], 21)
        self.assertEqual(fps["candidate_range"], [24, 26])
        self.assertAlmostEqual(fps["change_percent"], 100 * (25 / 21 - 1))
        json.dumps(report, allow_nan=False)

    def test_reject_incomparable_context(self):
        for key in comparison.CONTEXT_FIELDS:
            with self.subTest(key=key):
                candidate = copy.deepcopy(self.candidate)
                candidate[0]["context"][key] = "different"
                with self.assertRaises(ValueError):
                    comparison.compare(self.reference, candidate)

    def test_reject_insufficient_or_duplicate_runs(self):
        for candidate in (self.candidate[:2], self.candidate[:2] + self.candidate[:1]):
            with self.assertRaises(ValueError):
                comparison.compare(self.reference, candidate)

    def test_reject_mixed_builds_and_durations(self):
        for key in ("commit", "apk_sha256"):
            candidate = copy.deepcopy(self.candidate)
            candidate[0]["context"][key] = "mixed"
            with self.assertRaises(ValueError):
                comparison.compare(self.reference, candidate)
        self.candidate[0]["summary"]["measurement_elapsed_s"] = 120
        with self.assertRaises(ValueError):
            comparison.compare(self.reference, self.candidate)

    def test_reject_nonfinite_missing_and_boolean_metrics(self):
        directory = Path(self.reference[0]["directory"])
        for value in (float("nan"), float("inf"), -1, 0, None, True):
            summary = dict(self.reference[0]["summary"], presented_fps=value)
            (directory / "summary.json").write_text(json.dumps(summary))
            with self.assertRaises(ValueError):
                comparison.read_capture(directory)

    def test_reject_frame_generation_and_bad_hash(self):
        directory = Path(self.reference[0]["directory"])
        for patch in ({"frame_generation": "on"}, {"apk_sha256": "unknown"}, {"scene": ""}):
            context = dict(self.reference[0]["context"], **patch)
            (directory / "benchmark-context.json").write_text(json.dumps(context))
            with self.assertRaises(ValueError):
                comparison.read_capture(directory)


if __name__ == "__main__":
    unittest.main()
