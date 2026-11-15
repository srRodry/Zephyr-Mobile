/**
 * Zephyr: pack used float4 constants in register order, copying dense runs.
 * Released under the BSD license - see LICENSE in the root for more details.
 */
#ifndef XENIA_GPU_PACKED_FLOAT_CONSTANTS_H_
#define XENIA_GPU_PACKED_FLOAT_CONSTANTS_H_
#include <bit>
#include <cstddef>
#include <cstdint>
#include <cstring>

namespace xe::gpu {
// Four bitmap words describe 256 float4 registers. The output has space for
// popcount(bitmap) * 16 bytes and does not overlap the register file. Values are
// copied bit-for-bit (including NaNs). The interpreter uses a different layout
// and must keep its full-register upload path.
inline size_t CopyPackedFloatConstants(uint8_t* destination,
                                       const uint32_t* registers,
                                       const uint64_t* bitmap,
                                       bool coalesce) {
  size_t written = 0;
  for (size_t word = 0; word < 4; ++word) {
    uint64_t remaining = bitmap[word];
    while (remaining) {
      const unsigned first = std::countr_zero(remaining);
      const unsigned count = coalesce
          ? std::countr_one(remaining >> first) : 1;
      const size_t bytes = size_t(count) * 4 * sizeof(uint32_t);
      std::memcpy(destination + written,
                  registers + (word * 64 + first) * 4, bytes);
      written += bytes;
      const unsigned end = first + count;
      // Avoid shifting by 64 for a run reaching the last register in a word.
      remaining = end == 64 ? 0 : remaining & (UINT64_MAX << end);
    }
  }
  return written;
}
}  // namespace xe::gpu
#endif  // XENIA_GPU_PACKED_FLOAT_CONSTANTS_H_
