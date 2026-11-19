#!/usr/bin/env python3
"""Synthetic and safe archived-log tests for the Army TDC boot classifier."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile

MODULE = Path(__file__).with_name("analyze-army-tdc-boot.py")
spec = importlib.util.spec_from_file_location("army_tdc_boot", MODULE)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

EXAMPLE = r"""
i> title_id = 454109AB
i> LaunchXexFile: requested='AO4.Main.xex', resolved='GAME:\AO4.Main.xex'
!> Cannot attach DLL AO4.Main on a host thread.
i> LaunchXexFile: requested='', resolved='GAME:\Default.xex'
i> Module \Device\Package_0\Default.xex:
i> Module \Device\Package_0\AO4.Splash.xex:
i> Module \Device\Package_0\AO4.Main.xex:
!> undefined extern call to 83EA349C XamBackgroundDownloadSetMode
i> Module \Device\Package_0\Engine.BuildInfo.xex:
w> GuestScheduler: 59 gated waits were only re-readied by the 64ms backstop
w> GuestScheduler: 3 gated waits were only re-readied by the 64ms backstop
w> GuestScheduler: no guest frame presented in 2000 watchdog ticks
i> Army TDC: pre-wait primary ring writeback rptr=28 depth=2
i> XEvent signal: handle=F8000000
i> XEvent signal: handle=F8000001
i> MemoryPollPark: parked poll loop at guest 82014F8C
"""


class ArmyBootTests(unittest.TestCase):
    def test_classifies_independent_boot_phases_and_missing_export(self):
        report = mod.analyze(EXAMPLE)
        self.assertTrue(report["title_id_454109AB_present"])
        self.assertTrue(report["wrong_main_as_launcher"])
        self.assertEqual([x["requested"] for x in report["launches"]],
                         ["AO4.Main.xex", ""])
        self.assertEqual(report["loaded_modules"], [
            "Default.xex", "AO4.Splash.xex", "AO4.Main.xex", "Engine.BuildInfo.xex"])
        self.assertEqual(report["missing_xam_background_download_calls"], 1)
        self.assertEqual(report["rescued_wait_total_across_reported_windows"], 62)
        self.assertEqual(report["watchdog_no_frame_reports"], 1)
        self.assertEqual(report["pre_wait_ring_writeback_markers"],
                         [{"dword_index": 28, "indirect_buffer_depth": 2}])
        self.assertEqual(report["xevent_trace_lines"], 2)
        self.assertEqual(report["memory_poll_trace_lines"], 1)
        self.assertFalse(report["vulkan_frame_or_pipeline_timings_present"])

    def test_no_log_lines_does_not_imply_success_or_fps(self):
        report = mod.analyze("hello world")
        self.assertEqual(report["launches"], [])
        self.assertEqual(report["loaded_modules"], [])
        self.assertEqual(report["pre_wait_ring_writeback_markers"], [])
        self.assertFalse(report["vulkan_frame_or_pipeline_timings_present"])
        self.assertGreater(len(report["caveats"]), 2)

    def test_read_zip_and_plain_log_without_extracting_files(self):
        with tempfile.TemporaryDirectory(prefix="zephyr-army-") as directory:
            root = Path(directory)
            plain = root / "xe.log"
            plain.write_text(EXAMPLE, encoding="utf-8")
            self.assertEqual(mod.read_log(plain), EXAMPLE)
            path = root / "diagnostics.zip"
            with ZipFile(path, "w") as z:
                z.writestr("sessions/session_20261118/xe.log", EXAMPLE)
            self.assertEqual(mod.read_log(path), EXAMPLE)
            with ZipFile(path, "w") as z:
                z.writestr("another-file", EXAMPLE)
            with self.assertRaises(ValueError):
                mod.read_log(path)


if __name__ == "__main__":
    unittest.main()
