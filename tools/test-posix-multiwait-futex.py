#!/usr/bin/env python3
"""Guard the Android/Linux multi-object wait against polling regressions."""

from pathlib import Path
import re
import unittest

SOURCE = Path(__file__).resolve().parents[1] / (
    "emulator-core/src/main/cpp/xenia/src/xenia/base/threading_posix.cc"
)


class PosixMultiWaitFutexTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = SOURCE.read_text()

    def test_linux_path_uses_generation_futex_without_global_wait_mutex(self):
        self.assertIn("std::atomic<uint32_t> g_multi_wait_gen", self.text)
        self.assertIn("FUTEX_WAIT_PRIVATE", self.text)
        self.assertIn("FUTEX_WAKE_PRIVATE", self.text)
        self.assertNotIn("g_multi_wait_mutex", self.text)
        self.assertNotIn("g_multi_wait_cv", self.text)

    def test_signal_path_stays_filtered_and_lock_free(self):
        notify = self.text[self.text.index("void NotifyAll()") :]
        notify = notify[: notify.index("class MultiWaitRegistration")]
        self.assertIn("multi_wait_refs_.load", notify)
        self.assertIn("PokeMultiWaiters()", notify)

        poke = self.text[self.text.index("static void PokeMultiWaiters()") :]
        poke = poke[: poke.index("static void WaitForMultiObjectSignal")]
        self.assertIn("g_multi_wait_gen.fetch_add", poke)
        self.assertIn("g_multi_waiters.load", poke)
        self.assertIn("FUTEX_WAKE_PRIVATE", poke)
        # A comment may mention mutexes; reject actual locking operations.
        self.assertNotRegex(poke, r"\b(?:pthread_mutex_lock|lock_guard|unique_lock|mutex_\.lock)\b")
        self.assertNotIn("condition_variable", poke)

    def test_generation_is_sampled_before_handle_check_and_wait_is_bounded(self):
        wait = self.text[self.text.index("static std::pair<WaitResult, size_t> WaitMultiple") :]
        wait = wait[: wait.index("[[nodiscard]] virtual void* native_handle")]
        generation = wait.index("wait_gen = g_multi_wait_gen.load")
        handle_check = wait.index("for (size_t i = 0; i < handles.size(); ++i)")
        park = wait.index("WaitForMultiObjectSignal(wait_gen, park)")
        self.assertLess(generation, handle_check)
        self.assertLess(handle_check, park)
        self.assertIn("std::chrono::milliseconds(50)", wait)
        self.assertRegex(
            wait,
            re.compile(
                r"g_multi_waiters\.fetch_add\([^;]+;\s*"
                r"WaitForMultiObjectSignal\(wait_gen, park\);\s*"
                r"g_multi_waiters\.fetch_sub",
                re.S,
            ),
        )

    def test_per_handle_registration_is_preserved(self):
        self.assertIn("std::atomic<uint32_t> multi_wait_refs_", self.text)
        self.assertIn("class MultiWaitRegistration", self.text)
        self.assertIn("MultiWaitRegistration registration(handles);", self.text)


if __name__ == "__main__":
    unittest.main()
