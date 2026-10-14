#!/usr/bin/env python3
"""Synthetic log regressions; no claimed measurements from an Android device."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from vulkan_diagnostics import parse_frame_sync, summarize


def line(frames=30, await_ms=2.5, replay=3.0, submissions=4.0, gpu=5.0):
    return (f"I> gpu VkFrameSync: {frames} frames | per frame: awaits=2.0 "
            f"await={await_ms}ms submissions={submissions} resolves=1.0 "
            "memexport_awaits=0.0 readback_awaits=0.0 | submit->fence "
            "avg=40.0ms max=60.0ms | blocking=1.0 delta avg=2.00 | "
            f"gpu exec avg={gpu}ms max=12.0ms gap avg=1.0ms | "
            "resolve_ms=0.00 avg=0.000 max=0.00 dropped=0 | draws=100 "
            f"rp_begins=5 splits=1.0 replay={replay}ms | "
            "resolve_clears=1.0 in_guest_pass=0.0 | uma readbacks=0.0")


class VulkanDiagnosticsTests(unittest.TestCase):
    def test_native_format_preserves_units_and_maxima(self):
        report = parse_frame_sync(line())
        self.assertEqual(report["cpu_await_ms_per_frame"], 2.5)
        self.assertEqual(report["gpu_ms_per_submission_avg"], 5.0)
        self.assertEqual(report["gpu_ms_per_submission_max"], 12.0)
        self.assertEqual(report["observed_submit_to_completion_max_ms"], 60.0)

    def test_different_frame_counts_weight_cpu_not_report_count(self):
        report = summarize([line(10, 100, 50), line(20, 10, 5)])
        self.assertEqual(report["approximate_frame_weighted_cpu"], {
            "cpu_await_ms_per_frame": 40.0,
            "cpu_replay_and_submit_ms_per_frame": 20.0})

    def test_does_not_infer_gpu_per_frame_from_submission_rate(self):
        report = summarize([line(submissions=100, gpu=1), line(submissions=1, gpu=100)])
        self.assertEqual([w["gpu_ms_per_submission_avg"] for w in report["windows"]], [1, 100])
        self.assertFalse(any("gpu" in k for k in report["approximate_frame_weighted_cpu"]))
        self.assertNotIn("gpu_ms_per_frame", json.dumps(report))

    def test_missing_logs_are_unavailable_not_zero(self):
        report = summarize(["GpuFrame: 30 frames", "VkFences: polls=2"])
        self.assertEqual(report["valid_windows_selected"], 0)
        self.assertIsNone(report["approximate_frame_weighted_cpu"]["cpu_await_ms_per_frame"])
        json.dumps(report, allow_nan=False)

    def test_rejects_invalid_truncated_and_ambiguous_values(self):
        for invalid in (line(0), line().replace("await=2.5ms", "await=-1ms"),
                        line().replace("avg=5.0ms", "avg=nanms"),
                        line().replace("avg=5.0ms", "avg=infms"),
                        line().split(" replay=")[0], line() + " replay=3.0ms"):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                parse_frame_sync(invalid)

    def test_last_selects_valid_windows_not_seconds_and_counts_rejections(self):
        report = summarize([line(10), "VkFrameSync: truncated", line(20), line(30)], last=2)
        self.assertEqual(report["frames_selected"], 50)
        self.assertEqual(report["malformed_windows_rejected"], 1)
        with self.assertRaises(ValueError): summarize([], last=0)

    def test_zero_gpu_keeps_ambiguity_warning(self):
        report = summarize([line(gpu=0)])
        self.assertEqual(report["windows"][0]["gpu_ms_per_submission_avg"], 0)
        self.assertTrue(any("Zero GPU" in note for note in report["limitations"]))

    def test_cli_json_and_invalid_window_argument(self):
        script = Path(__file__).with_name("vulkan_diagnostics.py")
        with tempfile.TemporaryDirectory(prefix="zephyr-vulkan-log-") as directory:
            log = Path(directory) / "xe.log"
            log.write_text(line() + "\n", encoding="utf-8")
            result = subprocess.run([sys.executable, str(script), str(log)],
                                    capture_output=True, text=True, check=True)
            self.assertEqual(json.loads(result.stdout)["frames_selected"], 30)
            result = subprocess.run([sys.executable, str(script), str(log), "--last", "0"],
                                    capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)


if __name__ == "__main__": unittest.main()
