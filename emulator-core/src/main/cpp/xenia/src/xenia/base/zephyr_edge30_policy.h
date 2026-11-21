#ifndef XENIA_BASE_ZEPHYR_EDGE30_POLICY_H_
#define XENIA_BASE_ZEPHYR_EDGE30_POLICY_H_
#include <algorithm>
#include <cstdint>
#ifndef ZEPHYR_EDGE30_ULTRA
#define ZEPHYR_EDGE30_ULTRA 0
#endif
namespace xe::edge30 {
// Pure policy shared by startup translation and live Vulkan compilation.
inline uint32_t PipelineWorkers(int32_t requested, uint32_t logical) {
  logical = std::max(logical, uint32_t(1));
  if (!requested) return 0;
  uint32_t count = requested < 0 ? std::max(logical * 3 / 4, uint32_t(1))
                                : std::min(uint32_t(requested), logical);
#if ZEPHYR_EDGE30_ULTRA
  // Six guest dispatchers and the GPU submitter share four performance cores.
  // Keep live pipeline compilation from consuming four/six more workers.
  count = std::min(count, uint32_t(2));
#endif
  return count;
}
// The reduced limit gets work back onto an idle queue. Keep the normal
// batching once the requested post-drain window has elapsed.
constexpr uint32_t SubmissionDrawLimit(int32_t normal, int32_t reduced,
                                       int32_t window, uint64_t submission,
                                       uint64_t drained_at) {
  const uint32_t regular = uint32_t(std::max(normal, 0));
  if (reduced <= 0 || window <= 0 || submission < drained_at ||
      submission - drained_at >= uint64_t(window)) {
    return regular;
  }
  const uint32_t after_drain = uint32_t(reduced);
  return regular ? std::min(regular, after_drain) : after_drain;
}
constexpr bool DetailedGpuTiming(bool requested) {
#if ZEPHYR_EDGE30_ULTRA
  return false; // Avoid BOTTOM_OF_PIPE waits per pass/resolve on Turnip.
#else
  return requested;
#endif
}
constexpr bool SchedulerStats(bool requested, bool dedicated_diagnostics_opt_in) {
#if ZEPHYR_EDGE30_ULTRA
  // A saved legacy config can already contain guest_scheduler_stats=true.
  // Require a separate new explicit opt-in to preserve performance defaults.
  return requested && dedicated_diagnostics_opt_in;
#else
  (void)dedicated_diagnostics_opt_in;
  return requested;
#endif
}
}  // namespace xe::edge30
#endif
