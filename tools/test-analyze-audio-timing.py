#!/usr/bin/env python3
"""Tests for offline APU/AAudio diagnostics parsing."""
import importlib.util
from pathlib import Path
import unittest

PATH = Path(__file__).with_name("analyze-audio-timing.py")
spec = importlib.util.spec_from_file_location("audio_timing", PATH)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class AudioTimingTests(unittest.TestCase):
    def test_parses_both_formats_and_aggregates(self):
        lines = [
            "10-15 12:00:00.123 I xenia: AudioPump: 100 samples, 25 late "
            "(25.0%), max late 8000 us, guest dispatch avg 4200 us, "
            "max 11000 us, no-credit 3, no-submit 4, topups 8",
            "10-15 12:00:01.123 I xenia: AAudio: 200 cb, 10 gaps (5.0%), "
            "queue avg 1.50 max 4, rate 0.950, xruns 7, clipped 2 (0.1%), "
            "callback 100 us avg 200 max, queue lock 0.0 us avg 0 max",
            "10-15 12:00:05.123 I xenia: AudioPump: 100 samples, 5 late "
            "(5.0%), max late 1000 us, guest dispatch avg 1500 us, "
            "max 3000 us, no-credit 1, no-submit 0, topups 2",
        ]
        events = module.parse(lines)
        self.assertEqual(len(events), 3)
        self.assertEqual(events[0]["timestamp"], "10-15 12:00:00.123")
        summary = module.summarize(events)
        self.assertEqual(summary["pump_late_pct"], 15.0)
        self.assertEqual(summary["aaudio_gap_pct"], 5.0)
        self.assertEqual(summary["no_credit"], 4)
        self.assertEqual(summary["no_submit"], 4)
        self.assertEqual(summary["topups"], 10)
        self.assertEqual(summary["last_xruns"], 7)

    def test_xruns_are_cumulative_and_can_reset(self):
        sinks = [{"xruns": 4}, {"xruns": 6}, {"xruns": 1},
                 {"xruns": -1}, {"xruns": 3}]
        self.assertEqual(module.xrun_increase(sinks), 4)
        self.assertEqual(module.xrun_increase([{"xruns": 100}]), 0)

    def test_handles_missing_and_unstamped_lines(self):
        self.assertIsNone(module.summarize([])["pump_late_pct"])
        events = module.parse(["AAudio: 10 cb, 0 gaps (0.0%), queue avg 3.00 "
                               "max 5, rate 1.000, xruns -1, clipped 0 (0.0%)"])
        self.assertEqual(events[0]["timestamp"], None)
        self.assertEqual(module.summarize(events)["last_xruns"], None)


if __name__ == "__main__":
    unittest.main()
