#include "xenia/gpu/disjoint_draw_ranges.h"
#include <cassert>
#include <cstdio>
#include <random>
using T = xe::gpu::DisjointDrawRanges;
using R = T::Range;
int main() {
  T t;
  R read{100, 10};
  t.Add(&read, 1, {200, 10});
  assert(!t.NeedsBarrier(nullptr, 0, {300, 10}));
  assert(t.NeedsBarrier(nullptr, 0, {209, 1})); // WAW
  assert(t.NeedsBarrier(nullptr, 0, {109, 1})); // WAR
  R overlap{209, 1};
  assert(t.NeedsBarrier(&overlap, 1, {})); // RAW
  R edge{210, 10};
  assert(!t.NeedsBarrier(&edge, 1, {110, 10}));
  assert(!t.NeedsBarrier(nullptr, 0, {200, 0}));
  assert(t.NeedsBarrier(nullptr, 0, {0xFFFFFFF0, 100}));
  R invalid{uint32_t(T::kAddressLimit), 1};
  assert(t.NeedsBarrier(&invalid, 1, {}));
  t.Clear();
  assert(!t.NeedsBarrier(&overlap, 1, {}));
  // In-place updates retain ordering even within the same conceptual shader batch.
  t.Add(&read, 1, read);
  assert(t.NeedsBarrier(&read, 1, read));
  t.Clear();
  for (size_t i = 0; i < T::kMaxRanges; ++i) t.Add(nullptr, 0, {uint32_t(i * 4), 1});
  assert(t.NeedsBarrier(nullptr, 0, {100000, 1}));
  // Compare range merging against an independent byte-level dependency oracle.
  std::mt19937 random(730);
  for (int round = 0; round < 500; ++round) {
    t.Clear(); bool reads[512]{}, writes[512]{};
    for (int n = 0; n < 100; ++n) {
      R r{uint32_t(random() % 480), uint32_t(random() % 32)};
      R w{uint32_t(random() % 480), uint32_t(random() % 32)};
      bool hazard = false;
      for (uint32_t i = r.first; i < r.first + r.second; ++i) hazard |= writes[i];
      for (uint32_t i = w.first; i < w.first + w.second; ++i) hazard |= writes[i] || reads[i];
      bool barrier = t.NeedsBarrier(&r, 1, w);
      assert(!hazard || barrier);
      if (barrier) { t.Clear(); for (int i = 0; i < 512; ++i) reads[i] = writes[i] = false; }
      t.Add(&r, 1, w);
      for (uint32_t i = r.first; i < r.first + r.second; ++i) reads[i] = true;
      for (uint32_t i = w.first; i < w.first + w.second; ++i) writes[i] = true;
    }
  }
  std::puts("Disjoint draw ranges: hazards, bounds, capacity and 50,000 oracle comparisons passed");
}
