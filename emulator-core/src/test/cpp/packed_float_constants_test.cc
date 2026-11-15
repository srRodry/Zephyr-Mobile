#include <array>
#include <cassert>
#include <cstring>
#include <iostream>
#include <vector>
#include "xenia/gpu/packed_float_constants.h"

int main() {
  std::array<uint32_t, 1024> registers;
  uint64_t rng = 0x192837465abcdefULL;
  auto random = [&]() { rng ^= rng << 13; rng ^= rng >> 7; rng ^= rng << 17; return rng; };
  for (auto& value : registers) value = uint32_t(random());
  registers[0] = 0x7fc00001; // NaN payloads and signed zero stay bit-exact
  registers[1] = 0xff800000;
  registers[2] = 0x80000000;
  size_t cases = 0;
  auto check = [&](const std::array<uint64_t, 4>& bitmap) {
    std::vector<uint8_t> expected;
    for (unsigned i = 0; i < 256; ++i) {
      if (!(bitmap[i / 64] & (uint64_t(1) << (i % 64)))) continue;
      const uint8_t* src = reinterpret_cast<const uint8_t*>(registers.data() + 4 * i);
      expected.insert(expected.end(), src, src + 16);
    }
    for (bool coalesce : {false, true}) {
      // Unaligned output and guard bytes catch overruns and unwanted padding.
      std::array<uint8_t, 4096 + 14> output;
      output.fill(0xa7);
      const auto n = xe::gpu::CopyPackedFloatConstants(output.data() + 7,
          registers.data(), bitmap.data(), coalesce);
      assert(n == expected.size());
      assert(!n || std::memcmp(output.data() + 7, expected.data(), n) == 0);
      for (size_t i = 0; i < 7; ++i) assert(output[i] == 0xa7);
      for (size_t i = n + 7; i < output.size(); ++i) assert(output[i] == 0xa7);
    }
    ++cases;
  };
  check({0, 0, 0, 0});
  check({UINT64_MAX, UINT64_MAX, UINT64_MAX, UINT64_MAX});
  check({0xaaaaaaaaaaaaaaaa, 0x5555555555555555, 0xaaaaaaaaaaaaaaaa, 0x5555555555555555});
  for (unsigned first = 0; first < 64; ++first) {
    for (unsigned end = first + 1; end <= 64; ++end) {
      const uint64_t run = (UINT64_MAX << first) & (UINT64_MAX >> (64 - end));
      check({run, run, run, run});
    }
  }
  for (unsigned pattern = 0; pattern < 65536; ++pattern) {
    const uint64_t x = pattern;
    check({x, x << 16, x << 32, x << 48});
  }
  for (unsigned i = 0; i < 20000; ++i) check({random(), random(), random(), random()});
  std::cout << "packed float constants: " << cases << " bit-exact maps passed in both modes\n";
}
