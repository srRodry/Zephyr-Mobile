#!/usr/bin/env python3
"""Compile the production POSIX clock with controlled clock values on Linux.

Check time units at multiple reported resolutions and count eliminated queries.
This is a correctness/operation-count test, not an Android FPS benchmark.
"""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'emulator-core/src/main/cpp/xenia/src/xenia/base/clock_posix.cc'
CLOCK_HEADER = r'''
#pragma once
#include <cstdint>
#include <ctime>
namespace xe {
class Clock {
 public:
  static uint64_t host_tick_frequency_platform();
  static uint64_t host_tick_count_platform();
  static uint64_t QueryHostSystemTime();
  static uint64_t QueryHostUptimeMillis();
  static uint64_t QueryHostInterruptTime();
};
}
'''
TEST = r'''
#include <cassert>
#include <cstdio>
#include <ctime>
#include "xenia/base/clock.h"
static long resolution = 1;
static int resolution_queries = 0;
extern "C" int clock_getres(clockid_t id, timespec* out) noexcept {
  assert(id == CLOCK_MONOTONIC_RAW);
  ++resolution_queries;
  out->tv_sec = 0;
  out->tv_nsec = resolution;
  return 0;
}
extern "C" int clock_gettime(clockid_t id, timespec* out) noexcept {
  assert(id == CLOCK_MONOTONIC_RAW);
  out->tv_sec = 123;
  out->tv_nsec = 456789000;
  return 0;
}
int main() {
  for (long granularity : {1L, 10L, 1000L}) {
    resolution = granularity;
    assert(xe::Clock::host_tick_frequency_platform() == 1000000000ULL);
    assert(xe::Clock::host_tick_count_platform() == 123456789000ULL);
    assert(xe::Clock::QueryHostUptimeMillis() == 123456ULL);
    assert(xe::Clock::QueryHostInterruptTime() == 123456789000ULL);
  }
  for (int i = 0; i < 10000; ++i) {
    assert(xe::Clock::QueryHostUptimeMillis() == 123456ULL);
  }
  assert(resolution_queries == 0);
  puts("Native POSIX clock: nanosecond units, 3 resolutions, 10000 uptime reads, 0 resolution queries");
}
'''


def main():
    with tempfile.TemporaryDirectory(prefix='zephyr-clock-') as tmp:
        root = Path(tmp)
        base = root / 'xenia/base'
        base.mkdir(parents=True)
        (base / 'clock.h').write_text(CLOCK_HEADER)
        (base / 'assert.h').write_text('#pragma once\n#include <cassert>\n#define assert_zero(x) assert((x) == 0)\n')
        test = root / 'test.cc'
        test.write_text('#include <initializer_list>\n' + TEST)
        binary = root / 'test'
        subprocess.run([os.environ.get('CXX', 'c++'), '-std=c++20', '-O2', '-Wall',
                        '-I', str(root), str(SOURCE), str(test), '-o', str(binary)], check=True)
        subprocess.run([str(binary)], check=True)


if __name__ == '__main__':
    main()
