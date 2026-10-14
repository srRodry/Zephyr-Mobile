#include "xenia/kernel/scheduler_phase_stats.h"
#include <atomic>
#include <cassert>
#include <condition_variable>
#include <iostream>
#include <mutex>
#include <thread>
#include <vector>

using Stats = xe::kernel::SchedulerPhaseStats;
int main() {
  Stats stats;
  uint64_t time = 0, reads = 0, calls = 0;
  auto now = [&]() { ++reads; return time; };
  for (unsigned i = 0; i < 100000; ++i) {
    stats.Measure(false, static_cast<Stats::Kind>(999), [&]() { ++calls; }, now);
  }
  assert(calls == 100000 && reads == 0);
  for (const auto& b : stats.Take()) assert(!b.calls && !b.wall_ns && !b.max_ns);
  stats.Measure(true, Stats::Kind::kRepoll, [&]() { time += 17; }, now);
  stats.Measure(true, Stats::Kind::kRepoll, [&]() { time += 23; }, now);
  stats.Measure(true, Stats::Kind::kFiber, [&]() { time += 101; }, now);
  stats.Measure(true, Stats::Kind::kIdle, [&]() { time += 999; }, now);
  auto s = stats.Take();
  assert(s[0].calls == 2 && s[0].wall_ns == 40 && s[0].max_ns == 23);
  assert(s[1].calls == 1 && s[1].wall_ns == 101 && s[1].max_ns == 101);
  assert(s[2].calls == 1 && s[2].wall_ns == 999 && s[2].max_ns == 999);
  for (const auto& b : stats.Take()) assert(!b.calls && !b.wall_ns && !b.max_ns);
  stats.Measure(true, Stats::Kind::kFiber, [&]() { time = 0; }, now);
  s = stats.Take();
  assert(s[1].calls == 1 && s[1].wall_ns == 0 && s[1].max_ns == 0);

  // Other workers call only the OFF path while CPU 0 owns active counters.
  std::atomic<unsigned> off_calls{0}, off_reads{0};
  std::vector<std::thread> workers;
  for (unsigned n = 0; n < 4; ++n) workers.emplace_back([&]() {
    for (unsigned i = 0; i < 25000; ++i) stats.Measure(false, Stats::Kind::kIdle,
        [&]() { ++off_calls; }, [&]() { ++off_reads; return uint64_t(0); });
  });
  for (unsigned i = 0; i < 10000; ++i) stats.Measure(true, Stats::Kind::kFiber,
      [&]() { ++time; }, now);
  for (auto& worker : workers) worker.join();
  s = stats.Take();
  assert(off_calls == 100000 && off_reads == 0);
  assert(s[1].calls == 10000 && s[1].wall_ns == 10000 && s[1].max_ns == 1);
  assert(s[0].calls == 0 && s[2].calls == 0);

  // A real waiting callback completes exactly once per handshake; producer
  // never touches the collector. Timing is the fake API span, not CPU usage.
  std::mutex mutex;
  std::condition_variable cv;
  unsigned requested = 0, completed = 0;
  std::thread producer([&]() {
    for (unsigned i = 1; i <= 200; ++i) {
      std::unique_lock<std::mutex> lock(mutex);
      cv.wait(lock, [&]() { return requested == i; });
      completed = i;
      cv.notify_all();
    }
  });
  for (unsigned i = 1; i <= 200; ++i) stats.Measure(true, Stats::Kind::kIdle, [&]() {
    std::unique_lock<std::mutex> lock(mutex);
    requested = i;
    cv.notify_all();
    cv.wait(lock, [&]() { return completed == i; });
    time += 7;
  }, now);
  producer.join();
  s = stats.Take();
  assert(s[2].calls == 200 && s[2].wall_ns == 1400 && s[2].max_ns == 7);
  unsigned real_calls = 0;
  stats.Measure(true, Stats::Kind::kRepoll, [&]() { ++real_calls; });
  assert(real_calls == 1 && stats.Take()[0].calls == 1);
  std::cout << "Scheduler phases: 100000 off calls and 100000 concurrent off calls with zero clocks; "
               "10000 owner records, categories/reset/rollback, 200 handshakes and real chrono passed.\n";
}
