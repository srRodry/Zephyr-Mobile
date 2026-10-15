#!/usr/bin/env python3
"""Regression checks for opt-in APU pump timing diagnostics."""
from pathlib import Path
import unittest

SOURCE = (Path(__file__).resolve().parents[1] /
          "emulator-core/src/main/cpp/xenia/src/xenia/apu/audio_system.cc").read_text()


class AudioPumpDiagnosticsTests(unittest.TestCase):
    def test_diagnostics_are_opt_in(self):
        self.assertIn("if (cvars::apu_aaudio_log_stats) {", SOURCE)
        self.assertIn("pump_lateness_us = wake_actual_us - earliest_pump_us;", SOURCE)
        self.assertIn("pump_exec_end - exec_begin", SOURCE)
        self.assertIn('XELOGI("AudioPump: {} samples, {} late', SOURCE)

    def test_diagnostics_are_worker_local(self):
        start = SOURCE.index("void AudioSystem::WorkerThreadMain()")
        end = SOURCE.index("int AudioSystem::FindFreeClient()", start)
        worker = SOURCE[start:end]
        for counter in ("pump_samples", "pump_late", "pump_late_max_us",
                        "pump_exec_sum_us", "pump_exec_max_us"):
            self.assertIn(counter, worker)
        self.assertIn("std::chrono::seconds(5)", worker)
        self.assertIn("audio_perf_hint.ReportPump(pump_exec_us);", worker)

    def test_existing_pacing_is_unchanged(self):
        self.assertIn("xe::threading::NanoSleepPrecise", SOURCE)
        self.assertIn("std::chrono::milliseconds(0)) !=", SOURCE)
        self.assertIn("cvars::apu_pump_topup", SOURCE)


if __name__ == "__main__":
    unittest.main()
