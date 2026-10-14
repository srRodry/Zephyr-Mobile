// SPDX-License-Identifier: BSD-3-Clause
#ifndef XENIA_CPU_BACKEND_A64_LOGICAL_IMMEDIATE_H_
#define XENIA_CPU_BACKEND_A64_LOGICAL_IMMEDIATE_H_
#include <cstdint>
namespace xe::cpu::backend::a64 {
// A nonempty, nonfull contiguous run of ones, possibly rotated, repeated at a
// power-of-two element width. No shifts by 64; zero/all-ones use the fallback.
constexpr bool IsLogicalImmediate64(uint64_t value) {
  if (!value || value == ~uint64_t(0)) return false;
  for (unsigned width = 2; width <= 64; width *= 2) {
    const uint64_t element = value & (~uint64_t(0) >> (64 - width));
    uint64_t repeated = element;
    for (unsigned shift = width; shift < 64; shift += width)
      repeated |= element << shift;
    if (repeated != value) continue;
    const uint64_t rotated = (element >> 1) | ((element & 1) << (width - 1));
    uint64_t transitions = element ^ rotated;
    if (!transitions) return false;
    transitions &= transitions - 1;
    if (!transitions) return false;
    transitions &= transitions - 1;
    return transitions == 0; // exactly two edges of one run
  }
  return false;
}
constexpr bool IsLogicalImmediate32(uint32_t value) {
  return IsLogicalImmediate64(uint64_t(value) | (uint64_t(value) << 32));
}
}  // namespace xe::cpu::backend::a64
#endif
