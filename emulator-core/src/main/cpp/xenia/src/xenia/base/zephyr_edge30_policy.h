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
constexpr bool DetailedGpuTiming(bool requested) {
#if ZEPHYR_EDGE30_ULTRA
  return false; // Avoid BOTTOM_OF_PIPE waits per pass/resolve on Turnip.
#else
  return requested;
#endif
}
constexpr bool SchedulerStats(bool requested) {
  // Runtime opt-in: dedicated Edge builds default this cvar to false.
  // A hard-coded false would prevent capturing CPU 0 scheduler phases at all.
  return requested;
}
}  // namespace xe::edge30
#endif
