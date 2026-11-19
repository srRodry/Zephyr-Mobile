#!/usr/bin/env python3
"""Compare the production memexport bitmap with page-by-page tracking."""

import os
from pathlib import Path
import subprocess
import tempfile


GPU = (
    Path(__file__).resolve().parents[1]
    / "emulator-core/src/main/cpp/xenia/src/xenia/gpu"
)

SOURCE = r"""
#include <algorithm>
#include <array>
#include <bit>
#include <cassert>
#include <cstdint>
#include <iterator>
#include <random>

struct SharedMemory {
  static constexpr uint32_t kBufferSize = 512u * 1024 * 1024;
};

// Unused renderer types required by the production class-body include.
struct Shader {
  struct ConstantRegisterMap {
    uint32_t vertex_fetch_bitmap[3]{};
  };
  ConstantRegisterMap constant_register_map() const { return {}; }
};
namespace xenos {
enum class FetchConstantType { kVertex };
struct xe_gpu_vertex_fetch_t {
  FetchConstantType type{};
  uint32_t address = 0;
  uint32_t size = 0;
};
}
struct RegisterFile {
  xenos::xe_gpu_vertex_fetch_t GetVertexFetch(uint32_t) const { return {}; }
};
namespace xe {
using std::size;
template <class T, size_t N>
constexpr size_t countof(const T (&array)[N]) { return size(array); }
bool bit_scan_forward(uint32_t value, uint32_t* bit) {
  *bit = std::countr_zero(value);
  return value != 0;
}
uint32_t clear_lowest_bit(uint32_t value) { return value & (value - 1); }
}

struct Tracker {
  bool AwaitAllQueueOperationsCompletion() { return true; }
#include "command_processor_memexport.inc"
};

constexpr uint32_t kBufferSize = SharedMemory::kBufferSize;
constexpr uint32_t kPageSize = 4096;
constexpr uint32_t kWordBytes = 64 * kPageSize;

struct Reference {
  std::array<uint64_t, Tracker::kMemexportPageCount / 64> words{};
  bool marked = false;

  void Update(uint32_t base, uint32_t size, bool written) {
    if (!size || base >= kBufferSize) {
      return;
    }
    const uint32_t end = base + std::min(size, kBufferSize - base);
    const uint32_t first_page = written ? base / kPageSize
                                       : (base + kPageSize - 1) / kPageSize;
    const uint32_t end_page = written ? (end + kPageSize - 1) / kPageSize
                                     : end / kPageSize;
    for (uint32_t page = first_page; page < end_page; ++page) {
      const uint64_t mask = uint64_t{1} << (page % 64);
      if (written) {
        words[page / 64] |= mask;
      } else {
        words[page / 64] &= ~mask;
      }
    }
    marked |= written;
  }

  bool Contains(uint32_t base, uint32_t size) const {
    if (!size || base >= kBufferSize || !marked) {
      return false;
    }
    const uint32_t end = base + std::min(size, kBufferSize - base);
    for (uint32_t page = base / kPageSize; page <= (end - 1) / kPageSize; ++page) {
      if (words[page / 64] & (uint64_t{1} << (page % 64))) {
        return true;
      }
    }
    return false;
  }
};

void CheckState(const Tracker& tracker, const Reference& reference) {
  assert(tracker.memexport_written_pages_ == reference.words);
  assert(tracker.any_memexport_pages_written_ == reference.marked);
  assert(tracker.memexport_await_pending_ == reference.marked);
  assert(!tracker.memexport_readbacks_pending_);
}

int main() {
  constexpr std::array<uint32_t, 16> boundaries = {
      0, 1, kPageSize - 1, kPageSize, kPageSize + 1,
      kWordBytes - kPageSize, kWordBytes - 1, kWordBytes,
      kWordBytes + 1, kWordBytes + kPageSize, 2 * kWordBytes,
      kBufferSize - kPageSize, kBufferSize - 1, kBufferSize,
      kBufferSize + 1, UINT32_MAX,
  };

  Tracker tracker;
  Reference reference;
  for (auto base : boundaries) {
    for (auto size : boundaries) {
      tracker.ResetMemexportPages();
      reference = {};
      CheckState(tracker, reference);
      assert(!tracker.IsMemexportRange(base, size));
      tracker.MarkMemexportPagesWritten(base, size);
      reference.Update(base, size, true);
      CheckState(tracker, reference);

      for (auto query_base : boundaries) {
        for (auto query_size : boundaries) {
          assert(tracker.IsMemexportRange(query_base, query_size) ==
                 reference.Contains(query_base, query_size));
        }
      }
      for (auto clear_base : boundaries) {
        for (auto clear_size : boundaries) {
          auto cleared = tracker;
          auto expected = reference;
          cleared.ClearMemexportPages(clear_base, clear_size);
          expected.Update(clear_base, clear_size, false);
          CheckState(cleared, expected);
          assert(cleared.IsMemexportRange(base, size) == expected.Contains(base, size));
        }
      }
    }
  }

  std::mt19937 random(8475);
  for (uint32_t i = 0; i < 120000; ++i) {
    if (i % 16384 == 0) {
      tracker.ResetMemexportPages();
      reference = {};
    }
    const uint32_t base = uint32_t(random()) % kBufferSize;
    const uint32_t size = uint32_t(random()) % (i % 2 ? kWordBytes : 1024);
    switch (i % 5) {
      case 0:
      case 1:
        tracker.MarkMemexportPagesWritten(base, size);
        reference.Update(base, size, true);
        break;
      case 2:
      case 3:
        tracker.ClearMemexportPages(base, size);
        reference.Update(base, size, false);
        break;
      default:
        assert(tracker.IsMemexportRange(base, size) == reference.Contains(base, size));
        break;
    }
    CheckState(tracker, reference);
  }
}
"""


def main():
    with tempfile.TemporaryDirectory() as temporary_directory:
        directory = Path(temporary_directory)
        source = directory / "memexport_page_bitmap.cc"
        executable = directory / "memexport_page_bitmap"
        source.write_text(SOURCE)
        subprocess.run(
            [
                os.environ.get("CXX", "c++"),
                "-std=c++20",
                "-O2",
                "-Wall",
                "-Wextra",
                "-Werror",
                "-fsanitize=undefined",
                "-fno-sanitize-recover=undefined",
                "-I",
                GPU,
                source,
                "-o",
                executable,
            ],
            check=True,
        )
        subprocess.run([executable], check=True)
    print("Memexport bitmap: boundary cases and 120,000 mixed operations passed.")


if __name__ == "__main__":
    main()
