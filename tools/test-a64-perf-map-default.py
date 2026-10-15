#!/usr/bin/env python3
"""Guard the AArch64 perf-map profiler from re-entering the default JIT path."""

from pathlib import Path
import unittest

SOURCE = Path(__file__).resolve().parents[1] / (
    "emulator-core/src/main/cpp/xenia/src/xenia/cpu/backend/a64/a64_code_cache.cc"
)


class A64PerfMapDefaultTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = SOURCE.read_text()

    def test_profiler_is_opt_in(self):
        self.assertIn("DEFINE_bool(a64_perf_map, false,", self.text)

    def test_disabled_path_returns_before_mutex_and_file_io(self):
        start = self.text.index("void A64CodeCache::OnCodePlaced(")
        end = self.text.index("void A64CodeCache::FillCode(", start)
        body = self.text[start:end]
        guard = body.index("if (!cvars::a64_perf_map)")
        lock = body.index("std::lock_guard<std::mutex> lock(perf_map_mutex_)")
        write = body.index("std::fprintf(")
        flush = body.index("std::fflush(perf_map_file_)")
        self.assertLess(guard, lock)
        self.assertLess(lock, write)
        self.assertLess(write, flush)


if __name__ == "__main__":
    unittest.main()
