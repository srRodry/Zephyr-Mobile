#!/usr/bin/env python3
"""Regression guards for the real TDC XAM export and low-overhead boot paths.

Static source contracts supplement (not replace) Android ARM64 TU compilation.
Do not confuse TDC title 454109AB with the 2008 Army of Two 454107F8.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
XENIA = ROOT / "emulator-core/src/main/cpp/xenia/src/xenia"


def source(path):
    return (XENIA / path).read_text(encoding="utf-8")


def cpp_body(text, marker):
    """Return a C++ function's brace-balanced body; reject absent/duplicates."""
    if text.count(marker) != 1:
        raise AssertionError(f"Expected one definition of {marker}")
    start = text.index(marker)
    opening = text.index("{", start + len(marker))
    depth = 1
    for i in range(opening + 1, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if not depth:
                return text[opening + 1:i]
    raise AssertionError("Unbalanced function body")


class ArmyTdcNativeTests(unittest.TestCase):
    def test_xam_background_download_ordinal_is_registered_as_safe_noop(self):
        table = source("kernel/xam/xam_table.inc")
        xam = source("kernel/xam/xam_content.cc")
        self.assertRegex(table, r"XE_EXPORT\(xam,\s*0x000009C4,\s*XamBackgroundDownloadSetMode,")
        self.assertEqual(xam.count(
            "DECLARE_XAM_EXPORT1(XamBackgroundDownloadSetMode, kContent, kStub);"), 1)
        body = cpp_body(xam, "XamBackgroundDownloadSetMode_entry(dword_t")
        self.assertRegex(body, r"return\s+X_ERROR_SUCCESS\s*;")
        self.assertNotRegex(body, r"\b(kernel_state|RelaunchTitle|thread|sleep|abort)\b")

    def test_xevent_trace_is_opt_in_and_saturates_after_budget(self):
        event = source("kernel/xevent.cc")
        self.assertRegex(event, r"DEFINE_bool\(zephyr_event_signal_trace,\s*false,")
        for method in ("int32_t XEvent::Set(", "int32_t XEvent::Pulse("):
            body = cpp_body(event, method)
            self.assertRegex(body, r"if \(cvars::zephyr_event_signal_trace\) \{\s*TraceSetter\(\);")
        trace = cpp_body(event, "void XEvent::TraceSetter()")
        self.assertIn("compare_exchange_weak", trace)
        self.assertIn("remaining > 0", trace)
        self.assertIn("remaining <= 0", trace)
        self.assertNotIn("fetch_sub(1)", trace)
        self.assertIn('XELOGI("XEvent signal:', trace)
        wake = cpp_body(source("kernel/xobject.cc"), "void XObject::WakeCooperativeWaiters()")
        self.assertLess(wake.index("fetch_add(1)"), wake.index("RecordCooperativeSignal(this)"))
        self.assertRegex(wake, r"if \(epoch <= 16 \|\| \(epoch & 4095u\) == 0\)")

    def test_accepted_poll_loops_are_still_optimized_without_logging(self):
        jit = source("cpu/compiler/passes/memory_poll_park_pass.cc")
        self.assertRegex(jit, r"DEFINE_bool\(log_memory_poll_park,\s*false,")
        body = cpp_body(jit, "bool MemoryPollParkPass::TryInstrumentLoop(")
        self.assertIn("builder->SpinBackoff(1)", body)
        self.assertRegex(
            body,
            r"if\s*\(cvars::log_memory_poll_park\)\s*\{\s*for\s*\(Instr\* instr = block->instr_head;")

        self.assertIn("backoff->MoveBefore(loop_branch)", body)
        self.assertRegex(
            body, r"if\s*\(cvars::log_memory_poll_park\)\s*\{\s*XELOGI\(\"MemoryPollPark: parked")

    def test_native_arm64_ci_covers_all_changed_translation_units(self):
        plan = (ROOT / "tools/compile-native-regressions.py").read_text()
        for item in ("kernel/xam/xam_content.cc", "kernel/xevent.cc",
                     "cpu/compiler/passes/memory_poll_park_pass.cc"):
            self.assertIn(item, plan)

    def test_retail_army_quirk_is_not_misapplied_to_tdc(self):
        quirks = source("game_quirks.cc")
        self.assertIn("{0x454107F8, \"clear_memory_page_state\"", quirks)
        self.assertNotIn('{0x454109AB, "clear_memory_page_state"', quirks)


if __name__ == "__main__":
    unittest.main()
