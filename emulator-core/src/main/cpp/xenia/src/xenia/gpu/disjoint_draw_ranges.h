/**
 * Zephyr: conservative dependency tracking for guest memory exports.
 * Released under the BSD license - see LICENSE in the root for details.
 */
#ifndef XENIA_GPU_DISJOINT_DRAW_RANGES_H_
#define XENIA_GPU_DISJOINT_DRAW_RANGES_H_
#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <utility>
#include <vector>

namespace xe::gpu {
// No same-shader or in-place exemptions: RAW, WAR and WAW always require a barrier.
// The caller must also order every use outside this tracked group through Use().
class DisjointDrawRanges {
 public:
  using Range = std::pair<uint32_t, uint32_t>;
  static constexpr size_t kMaxRanges = 256;
  static constexpr uint64_t kAddressLimit = uint64_t(1) << 29;
  void Clear() { reads_.clear(); writes_.clear(); }
  bool NeedsBarrier(const Range* reads, size_t count, Range write) const {
    if (count > 97 || reads_.size() + writes_.size() + count + 1 > kMaxRanges ||
        !Valid(write)) return true;
    for (size_t i = 0; i < count; ++i) {
      if (!Valid(reads[i]) || Overlaps(writes_, reads[i])) return true;
    }
    return Overlaps(writes_, write) || Overlaps(reads_, write);
  }
  // Called only after a barrier if NeedsBarrier returned true.
  void Add(const Range* reads, size_t count, Range write) {
    for (size_t i = 0; i < count; ++i) Merge(reads_, reads[i]);
    Merge(writes_, write);
  }
 private:
  static bool Valid(Range r) { return uint64_t(r.first) + r.second <= kAddressLimit; }
  static bool Overlaps(const std::vector<Range>& ranges, Range r) {
    if (!r.second) return false;
    for (auto other : ranges) {
      if (uint64_t(r.first) < uint64_t(other.first) + other.second &&
          uint64_t(other.first) < uint64_t(r.first) + r.second) return true;
    }
    return false;
  }
  static void Merge(std::vector<Range>& ranges, Range r) {
    if (!r.second || !Valid(r)) return;
    uint64_t end = uint64_t(r.first) + r.second;
    auto first = std::lower_bound(ranges.begin(), ranges.end(), r.first,
        [](Range a, uint32_t start) { return uint64_t(a.first) + a.second < start; });
    auto last = first;
    while (last != ranges.end() && last->first <= end) {
      r.first = std::min(r.first, last->first);
      end = std::max(end, uint64_t(last->first) + last->second);
      ++last;
    }
    r.second = uint32_t(end - r.first);
    if (first != last) { *first = r; ranges.erase(first + 1, last); }
    else ranges.insert(first, r);
  }
  std::vector<Range> reads_, writes_;
};
}  // namespace xe::gpu
#endif
