// Copyright 2026. Released under the BSD license; see LICENSE.
#ifndef XENIA_KERNEL_SCHEDULER_PHASE_STATS_H_
#define XENIA_KERNEL_SCHEDULER_PHASE_STATS_H_

#include <array>
#include <chrono>
#include <cstdint>

namespace xe {
namespace kernel {

// Read/write only on CPU 0's dispatch thread. Disabled Measure calls do not
// access counters or clocks, so other workers may use only that path.
// Completed API spans, including descheduling/locks, NOT useful CPU time.
class SchedulerPhaseStats {
 public:
  enum class Kind { kRepoll, kFiber, kIdle };
  struct Bucket {
    uint64_t calls = 0;
    uint64_t wall_ns = 0;
    uint64_t max_ns = 0;
  };
  using Snapshot = std::array<Bucket, 3>;

  template <typename Fn, typename Now>
  void Measure(bool enabled, Kind kind, Fn&& fn, Now&& now) {
    if (!enabled) {
      fn();
      return;
    }
    const uint64_t start = now();
    fn();
    const uint64_t end = now();
    const uint64_t elapsed = end >= start ? end - start : 0;
    auto& bucket = buckets_[static_cast<unsigned>(kind)];
    ++bucket.calls;
    bucket.wall_ns += elapsed;
    if (elapsed > bucket.max_ns) bucket.max_ns = elapsed;
  }

  template <typename Fn>
  void Measure(bool enabled, Kind kind, Fn&& fn) {
    Measure(enabled, kind, fn, []() {
      return static_cast<uint64_t>(
          std::chrono::duration_cast<std::chrono::nanoseconds>(
              std::chrono::steady_clock::now().time_since_epoch()).count());
    });
  }

  Snapshot Take() {
    const Snapshot snapshot = buckets_;
    buckets_ = {};
    return snapshot;
  }

 private:
  Snapshot buckets_{};
};

}  // namespace kernel
}  // namespace xe
#endif  // XENIA_KERNEL_SCHEDULER_PHASE_STATS_H_
