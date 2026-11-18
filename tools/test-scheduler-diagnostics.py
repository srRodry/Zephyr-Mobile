#!/usr/bin/env python3
"""Synthetic host regressions for the Edge30 CPU0 diagnostic log parser."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("scheduler-diagnostics.py")
spec = importlib.util.spec_from_file_location("scheduler_diagnostics", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def line(window=1000, repoll=(10, 20.0, 4.0), fiber=(100, 300.0, 12.0),
         idle=(2, 50.0, 30.0)):
    def part(name, values):
        count, wall, maximum = values
        return f"{name} calls {count} wall {wall:.3f} ms max {maximum:.3f} ms"
    return (f"I> kernel GuestSchedulerCPU0: window {window} ms | "
            + " | ".join(part(name, values) for name, values in
                         (("repoll", repoll), ("fiber", fiber), ("idle", idle))))


class Cpu0DiagnosticsTests(unittest.TestCase):
    def test_parse_matches_production_log_format(self):
        production = (Path(__file__).resolve().parents[1] /
                      "emulator-core/src/main/cpp/xenia/src/xenia/kernel/guest_scheduler.cc"
                      ).read_text()
        for token in ("GuestSchedulerCPU0: window {} ms",
                      "repoll calls {} wall {:.3f} ms max {:.3f} ms",
                      "fiber calls {} wall {:.3f} ms max {:.3f} ms",
                      "idle calls {} wall {:.3f} ms max {:.3f} ms"):
            self.assertIn(token, production)
        parsed = module.parse_window(line())
        self.assertEqual(parsed["elapsed_ms"], 1000)
        self.assertEqual(parsed["phases"]["repoll"]["calls"], 10)
        self.assertEqual(parsed["phases"]["fiber"]["max_call_ms"], 12)
        self.assertTrue(parsed["calibrated"])

    def test_real_elapsed_window_weighting_and_last(self):
        a = line(1000, repoll=(10, 100.0, 10.0))
        b = line(3000, repoll=(30, 300.0, 15.0))
        report = module.summarize([a, b])
        self.assertEqual(report["measured_elapsed_ms"], 4000)
        self.assertEqual(report["phases"]["repoll"]["calls_per_second"], 10)
        self.assertEqual(report["phases"]["repoll"]["wall_ms_per_second_of_window"], 100)
        self.assertEqual(report["phases"]["repoll"]["largest_observed_call_ms"], 15)
        last = module.summarize([a, b], last=1)
        self.assertEqual(last["valid_windows_selected"], 1)
        self.assertEqual(last["measured_elapsed_ms"], 3000)

    def test_bootstrap_and_missing_samples_are_not_idle_zero(self):
        report = module.summarize([line(window=0)])
        self.assertEqual(report["bootstrap_windows_excluded"], 1)
        self.assertEqual(report["valid_windows_selected"], 0)
        self.assertIsNone(report["phases"])
        self.assertEqual(module.summarize(["other logs"])["phases"], None)
        json.dumps(report, allow_nan=False)

    def test_rejects_ambiguous_broken_or_impossible_records(self):
        normal = line()
        invalid = [
            normal + " GuestSchedulerCPU0: extra",
            normal.split(" | idle")[0],
            normal.replace("300.000 ms", "-300.000 ms"),
            normal.replace("300.000 ms", "nan ms"),
            normal.replace("300.000 ms", "inf ms"),
            line(repoll=(0, 2.0, 1.0)),
            line(repoll=(1, 1.0, 2.0)),
        ]
        for bad in invalid:
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                module.parse_window(bad)
        report = module.summarize(invalid + [normal])
        self.assertEqual(report["malformed_windows_rejected"], len(invalid))
        self.assertEqual(report["valid_windows_selected"], 1)

    def test_new_launch_boundary_resets_old_session_samples(self):
        # Existing xe.log files may contain an earlier title launch.
        report = module.summarize([
            line(window=1000, repoll=(200, 200.0, 12.0)),
            "I> xenia Storage root: /sdcard/old",
            line(window=1000, repoll=(100, 100.0, 10.0)),
            "I> xenia Storage root: /sdcard/current",
            line(window=0),
            line(window=1000, repoll=(5, 20.0, 4.0)),
        ], last=30)
        self.assertEqual(report["session_boundaries_seen"], 2)
        self.assertEqual(report["bootstrap_windows_excluded"], 1)
        self.assertEqual(report["valid_windows_selected"], 1)
        self.assertEqual(report["phases"]["repoll"]["calls"], 5)

    def test_no_false_cpu_busy_claim_and_cli(self):
        report = module.summarize([line()])
        self.assertNotIn("utilization", json.dumps(report).lower().split('"phases"')[1].split('"limitations"')[0])
        self.assertTrue(any("CPU utilization" in item for item in report["limitations"]))
        with self.assertRaises(ValueError):
            module.summarize([], last=0)
        with tempfile.TemporaryDirectory(prefix="zephyr-cpu0-") as temp:
            log = Path(temp) / "xe.log"
            log.write_text(line() + "\n", encoding="utf-8")
            command = [sys.executable, str(SCRIPT), str(log)]
            result = subprocess.run(command, capture_output=True, text=True, check=True)
            self.assertEqual(json.loads(result.stdout)["valid_windows_selected"], 1)
            invalid = subprocess.run(command + ["--last", "0"],
                                     capture_output=True, text=True)
            self.assertNotEqual(invalid.returncode, 0)


if __name__ == "__main__":
    unittest.main()
