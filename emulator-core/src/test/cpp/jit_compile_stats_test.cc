#include <cassert>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <thread>
#include <vector>
#include "xenia/cpu/jit_compile_stats.h"
using namespace xe::cpu;
struct FakeClock {
  using time_point = std::chrono::time_point<FakeClock, std::chrono::nanoseconds>;
  static inline int calls = 0;
  static inline int64_t ns = 0;
  static time_point now() { ++calls; return time_point(std::chrono::nanoseconds(ns)); }
};
static void ScopeLifecycle() {
  JitCompileStats stats;
  { ScopedJitCompile<FakeClock> guard(stats); guard.Succeeded(); }
  assert(FakeClock::calls == 0 && stats.Snapshot().completed == 0);
  stats.enabled = true;
  FakeClock::ns = 100;
  { ScopedJitCompile<FakeClock> guard(stats); FakeClock::ns += 500000; guard.Succeeded(); }
  { ScopedJitCompile<FakeClock> guard(stats); FakeClock::ns += 33300000; }
  { ScopedJitCompile<FakeClock> guard(stats); FakeClock::ns -= 1; guard.Succeeded(); }
  { ScopedJitCompile<FakeClock> guard(stats); stats.enabled = false; FakeClock::ns += 10; guard.Succeeded(); }
  auto result = stats.Snapshot();
  assert(result.completed == 4 && result.failed == 1 && result.duration_ns == 33800010);
  assert(result.histogram[0] == 3 && result.histogram[4] == 1);
  assert(FakeClock::calls == 8);
  { ScopedJitCompile<FakeClock> guard(stats); }
  assert(FakeClock::calls == 8 && stats.Snapshot().completed == 4);
}
static void BucketBoundaries() {
  JitCompileStats stats;
  const uint64_t durations[] = {0,999999,1000000,3999999,4000000,16699999,
                               16700000,33299999,33300000,99999999,100000000,100000001};
  uint64_t sum = 0;
  for (auto ns : durations) { stats.Record(ns, true); sum += ns; }
  auto result = stats.Snapshot();
  assert(result.completed == 12 && result.failed == 0 && result.duration_ns == sum);
  for (auto count : result.histogram) assert(count == 2);
}
static void ConcurrentRecording() {
  JitCompileStats stats;
  std::vector<std::thread> workers;
  for (int t = 0; t < 4; ++t) workers.emplace_back([&] {
    for (int i = 0; i < 30000; ++i) stats.Record(1000000, (i % 3) != 0);
  });
  for (int i = 0; i < 1000; ++i) {
    auto sample = stats.Snapshot();
    uint64_t total = 0; for (auto n : sample.histogram) total += n;
    assert(total == sample.completed && sample.failed <= sample.completed);
    assert(sample.duration_ns == sample.completed * 1000000);
  }
  for (auto& worker : workers) worker.join();
  auto result = stats.Snapshot();
  assert(result.completed == 120000 && result.failed == 40000);
  assert(result.duration_ns == 120000000000ULL && result.histogram[1] == 120000);
}
int main() {
  ScopeLifecycle(); BucketBoundaries(); ConcurrentRecording();
  std::puts("JIT compile diagnostics: disabled path has zero clock reads; scope/failure/bucket boundaries and 120000 concurrent records with coherent snapshots passed.");
}
