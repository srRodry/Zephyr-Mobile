#include "xenia/gpu/vulkan/pipeline_wait_stats.h"

#include <cassert>
#include <condition_variable>
#include <iostream>
#include <mutex>
#include <thread>

using xe::gpu::vulkan::PipelineWaitStats;
using Kind = PipelineWaitStats::Kind;

int main() {
  PipelineWaitStats stats;
  unsigned waits = 0, clocks = 0;
  auto wait = [&]() { ++waits; };
  auto clock = [&]() { return uint64_t(++clocks) * 100; };
  for (unsigned i = 0; i < 100000; ++i) {
    stats.Measure(Kind::kSubmission, false, wait, clock);
  }
  assert(waits == 100000 && clocks == 0);
  auto snapshot = stats.Take();
  assert(snapshot[0].calls == 0 && snapshot[1].calls == 0);

  stats.Measure(Kind::kSubmission, true, wait, clock);
  stats.Measure(Kind::kSubmission, true, wait, clock);
  stats.Measure(Kind::kExplicit, true, wait, clock);
  snapshot = stats.Take();
  assert(waits == 100003 && clocks == 6);
  assert(snapshot[0].calls == 2 && snapshot[0].ns == 200);
  assert(snapshot[0].max_ns == 100);
  assert(snapshot[1].calls == 1 && snapshot[1].ns == 100);
  snapshot = stats.Take();
  assert(snapshot[0].calls == 0 && snapshot[1].ns == 0);

  unsigned reads = 0;
  stats.Measure(Kind::kExplicit, true, wait,
                [&]() { return ++reads == 1 ? uint64_t(100) : uint64_t(90); });
  snapshot = stats.Take();
  assert(snapshot[1].calls == 1 && snapshot[1].ns == 0);

  // A real producer signals completion concurrently; only the waiter owns
  // statistics. The clock is deterministic, not an Android measurement.
  for (unsigned i = 0; i < 200; ++i) {
    std::mutex mutex;
    std::condition_variable event;
    bool entered = false, complete = false;
    std::thread worker([&]() {
      std::unique_lock<std::mutex> lock(mutex);
      event.wait(lock, [&]() { return entered; });
      complete = true;
      event.notify_all();
    });
    stats.Measure(Kind::kExplicit, i % 2 == 0, [&]() {
      std::unique_lock<std::mutex> lock(mutex);
      entered = true;
      event.notify_all();
      event.wait(lock, [&]() { return complete; });
    }, clock);
    worker.join();
  }
  snapshot = stats.Take();
  assert(snapshot[1].calls == 100 && snapshot[1].ns == 10000);
  assert(clocks == 206);

  // Production chrono overload, both branches, and independent instances.
  PipelineWaitStats other;
  other.Measure(Kind::kSubmission, true, wait);
  other.Measure(Kind::kSubmission, false, wait);
  assert(other.Take()[0].calls == 1);
  assert(stats.Take()[0].calls == 0);
  std::cout << "Pipeline waits: 100000 disabled calls with zero clocks; "
               "categories/reset/clock rollback and 200 concurrent "
               "completion handshakes passed.\n";
}
