// SPDX-License-Identifier: BSD-3-Clause
// Compile diagnostics only: no counters in executed guest instructions.
#ifndef XENIA_CPU_JIT_COMPILE_STATS_H_
#define XENIA_CPU_JIT_COMPILE_STATS_H_
#include <array>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <cstddef>
#include <mutex>
namespace xe::cpu {
struct JitCompileSnapshot {
  uint64_t completed = 0, duration_ns = 0, failed = 0;
  // <1ms, <4ms, <16.7ms, <33.3ms, <100ms, >=100ms per completed translation.
  std::array<uint64_t, 6> histogram{};
};
class JitCompileStats {
 public:
  std::atomic<bool> enabled{false};
  void Record(uint64_t duration_ns, bool success) {
    std::size_t bucket = 0;
    constexpr uint64_t bounds[] = {1000000, 4000000, 16700000, 33300000, 100000000};
    while (bucket < 5 && duration_ns >= bounds[bucket]) ++bucket;
    // Once per completed translation, not once per frame/instruction. Snapshot
    // is coherent across concurrent translator workers and the JNI reader.
    std::lock_guard<std::mutex> lock(mutex_);
    ++snapshot_.completed;
    snapshot_.duration_ns += duration_ns;
    snapshot_.failed += !success;
    ++snapshot_.histogram[bucket];
  }
  JitCompileSnapshot Snapshot() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return snapshot_;
  }
 private:
  mutable std::mutex mutex_;
  JitCompileSnapshot snapshot_;
};
inline JitCompileStats& TranslationCompileStats() {
  static JitCompileStats stats;
  return stats;
}
template <class Clock = std::chrono::steady_clock>
class ScopedJitCompile {
 public:
  explicit ScopedJitCompile(JitCompileStats& stats) : stats_(stats),
      enabled_(stats.enabled.load(std::memory_order_relaxed)) {
    if (enabled_) begin_ = Clock::now();
  }
  ~ScopedJitCompile() {
    if (!enabled_) return;
    const auto ns = std::chrono::duration_cast<std::chrono::nanoseconds>(Clock::now() - begin_).count();
    stats_.Record(ns > 0 ? uint64_t(ns) : 0, success_);
  }
  void Succeeded() { success_ = true; }
  ScopedJitCompile(const ScopedJitCompile&) = delete;
  ScopedJitCompile& operator=(const ScopedJitCompile&) = delete;
 private:
  JitCompileStats& stats_;
  bool enabled_, success_ = false;
  typename Clock::time_point begin_{};
};
}  // namespace xe::cpu
#endif
