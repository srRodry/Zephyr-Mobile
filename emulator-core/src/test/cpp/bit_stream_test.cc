// Compare the XMA bit reader, writer and offset-preserving copy with a bitwise
// reference, including streams ending between bytes and outside word alignment.
#include <algorithm>
#include <array>
#include <cassert>
#include <cstdint>
#include <vector>
#include "xenia/base/bit_stream.h"

int main() {
  for (size_t bits = 0; bits <= 95; ++bits) {
    std::vector<uint8_t> source((bits + 7) / 8);
    for (size_t i = 0; i < source.size(); ++i) source[i] = uint8_t(i * 101 + 35);
    for (size_t start = 0; start <= bits; ++start) {
      for (size_t length = 0; length <= 58; ++length) {
        xe::BitStream stream(source.data(), bits);
        stream.SetOffset(start);
        const size_t available = std::min(length, bits - start);
        uint64_t value = 0;
        std::array<uint8_t, 10> expected, copied;
        expected.fill(0xa5);
        copied = expected;
        for (size_t i = 0; i < available; ++i) {
          size_t src = start + i, dst = start % 8 + i;
          bool bit = (source[src / 8] >> (7 - src % 8)) & 1;
          value = (value << 1) | bit;
          uint8_t mask = uint8_t(0x80 >> (dst % 8));
          if (bit) expected[dst / 8] |= mask;
          else expected[dst / 8] &= ~mask;
        }
        assert(stream.Peek(length) == (length <= 57 && length == available ? value : 0));
        assert(stream.Copy(copied.data(), length) == start % 8);
        assert(copied == expected && stream.offset_bits() == start + available);
        xe::BitStream output(copied.data(), start % 8 + available);
        output.SetOffset(start % 8);
        copied.fill(0xa5);
        bool valid = length <= 57 && length == available;
        assert(output.Write(value, length) == valid);
        if (valid) assert(copied == expected);
      }
    }
  }
}
