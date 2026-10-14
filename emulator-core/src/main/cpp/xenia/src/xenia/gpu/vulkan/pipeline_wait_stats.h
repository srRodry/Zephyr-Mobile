/**
 ******************************************************************************
 * Xenia : Xbox 360 Emulator Research Project                                 *
 ******************************************************************************
 * Released under the BSD license - see LICENSE in the root for more details. *
 ******************************************************************************
 */
// Optional host wait diagnostics. Not GPU execution or summed worker time.
#ifndef XENIA_GPU_VULKAN_PIPELINE_WAIT_STATS_H_
#define XENIA_GPU_VULKAN_PIPELINE_WAIT_STATS_H_

#include <algorithm>
#include <array>
#include <chrono>
#include <cstdint>

namespace xe::gpu::vulkan {

// Confined to the command-processor thread. Creation workers never access it.
class PipelineWaitStats {
 public:
  enum class Kind : unsigned { kSubmission, kExplicit };
  struct Bucket {
    uint64_t calls = 0;
    uint64_t ns = 0;
    uint64_t max_ns = 0;
  };
  using Snapshot = std::array<Bucket, 2>;

  template <typename Wait, typename Clock>
  void Measure(Kind kind, bool enabled, Wait&& wait, Clock&& clock) {
    if (!enabled) {
      wait();
      return;
    }
    const uint64_t begin = clock();
    wait();
    const uint64_t end = clock();
    const uint64_t elapsed = end >= begin ? end - begin : 0;
    auto& bucket = stats_[static_cast<unsigned>(kind)];
    ++bucket.calls;
    bucket.ns += elapsed;
    bucket.max_ns = std::max(bucket.max_ns, elapsed);
  }

  template <typename Wait>
  void Measure(Kind kind, bool enabled, Wait&& wait) {
    Measure(kind, enabled, wait, []() {
      return uint64_t(std::chrono::duration_cast<std::chrono::nanoseconds>(
                          std::chrono::steady_clock::now().time_since_epoch())
                          .count());
    });
  }

  Snapshot Take() {
    const Snapshot result = stats_;
    stats_ = {};
    return result;
  }

 private:
  Snapshot stats_{};
};

}  // namespace xe::gpu::vulkan
#endif  // XENIA_GPU_VULKAN_PIPELINE_WAIT_STATS_H_
