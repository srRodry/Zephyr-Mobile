#!/usr/bin/env python3
"""Guard the Android/Linux multi-object wait against polling regressions."""

from pathlib import Path
import os
import re
import subprocess
import tempfile
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

    def test_repeated_conditions_and_contended_timeout(self):
        start = self.text.index("  static std::pair<WaitResult, size_t> WaitMultiple(")
        end = self.text.index("  [[nodiscard]] virtual void* native_handle", start)
        implementation = self.text[start:end]
        start = self.text.index("template <>\nclass PosixCondition<Event>")
        end = self.text.index("template <>\nclass PosixCondition<Semaphore>", start)
        event_implementation = self.text[start:end]
        # Run the production function with real POSIX mutexes. The event and
        # futex seams are minimal because these cases do not depend on wakeups.
        fixture = r"""
#include <algorithm>
#include <atomic>
#include <cassert>
#include <chrono>
#include <cerrno>
#include <condition_variable>
#include <cstdint>
#include <limits>
#include <mutex>
#include <thread>
#include <utility>
#include <vector>

#define XE_PLATFORM_MAC 0
#define XE_PLATFORM_ANDROID 0
#define XE_PLATFORM_xendroid 1
#define assert_true assert
using namespace std::chrono_literals;
enum class WaitResult { kSuccess, kTimeout, kFailed };
struct Event {};
struct EventInfo { uint32_t type, state; };
bool AutoResetEventHandoff() { return true; }
std::atomic<uint32_t> g_multi_wait_gen{0}, g_multi_waiters{0};
unsigned t_wait_any_calls = 0, t_wait_any_iters = 0;
uint64_t t_wait_any_park_ns = 0;
void WaitForMultiObjectSignal(uint32_t, std::chrono::milliseconds) {}

struct PosixConditionBase {
  std::mutex mutex_;
  std::condition_variable cond_;
  virtual ~PosixConditionBase() = default;
  bool ready = false;
  unsigned consumes = 0;
  virtual bool signaled() const { return ready; }
  virtual void post_execution() { ready = false; ++consumes; }
  virtual WaitResult Wait(std::chrono::milliseconds) { std::abort(); }
  virtual bool Signal() { return false; }
  virtual WaitResult SignalAndWaitOn(PosixConditionBase*, std::chrono::milliseconds) {
    std::abort();
  }
  void NotifyAll() { cond_.notify_all(); }
  struct ScopedParked {
    explicit ScopedParked(PosixConditionBase&) {}
  };
  struct MultiWaitRegistration {
    explicit MultiWaitRegistration(std::vector<PosixConditionBase*>&) {}
  };
@IMPLEMENTATION@
};

template <typename> class PosixCondition {};
@EVENT_IMPLEMENTATION@

int main() {
  PosixConditionBase first, second, third;
  auto wait = PosixConditionBase::WaitMultiple;
  assert(wait({&first, &second, &first}, false, 0ms).first ==
         WaitResult::kTimeout);
  second.ready = true;
  third.ready = true;
  auto result = wait({&first, &second, &second, &third}, false, 0ms);
  assert(result.first == WaitResult::kSuccess && result.second == 1);
  assert(second.consumes == 1 && third.consumes == 0);
  assert(wait({&third, &third}, true, 0ms).first == WaitResult::kFailed);
  assert(third.ready && third.consumes == 0);
  first.ready = true;
  assert(wait({&first, &third}, true, 0ms).first == WaitResult::kSuccess);
  assert(first.consumes == 1 && third.consumes == 1);

  std::atomic<bool> locked{false}, finished{false};
  std::thread holder([&] {
    std::lock_guard guard(first.mutex_);
    locked = true;
    while (!finished) std::this_thread::yield();
  });
  while (!locked) std::this_thread::yield();
  assert(wait({&first, &second}, false, 0ms).first == WaitResult::kTimeout);
  assert(wait({&first, &second}, true, 2ms).first == WaitResult::kTimeout);
  finished = true;
  holder.join();

  PosixCondition<Event> manual(true, false), automatic(false, false);
  assert(manual.Query().type == 0 && manual.Query().state == 0);
  assert(automatic.Query().type == 1 && automatic.Query().state == 0);
  manual.Signal();
  automatic.Signal();
  assert(manual.Query().state == 1 && automatic.Query().state == 1);
  assert(manual.Wait(0ms) == WaitResult::kSuccess);
  assert(automatic.Wait(0ms) == WaitResult::kSuccess);
  assert(manual.Query().state == 1 && automatic.Query().state == 0);
  manual.Reset();
  assert(manual.Query().state == 0);
}
"""
        with tempfile.TemporaryDirectory(prefix="zephyr-posix-wait-") as directory:
            program = Path(directory) / "wait.cc"
            binary = Path(directory) / "wait"
            program.write_text(
                fixture.replace("@IMPLEMENTATION@", implementation)
                .replace("@EVENT_IMPLEMENTATION@", event_implementation)
            )
            subprocess.run(
                [os.environ.get("CXX", "c++"), "-std=c++17", "-pthread",
                 str(program), "-o", str(binary)],
                check=True,
                timeout=30,
            )
            subprocess.run([str(binary)], check=True, timeout=5)


if __name__ == "__main__":
    unittest.main()
