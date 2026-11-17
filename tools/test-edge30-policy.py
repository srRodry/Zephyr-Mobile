#!/usr/bin/env python3
"""Execute production Edge30 worker policy and I/O completion with stats on/off.

Host harnesses validate policy and preserved completion, not ARM throughput.
"""
from pathlib import Path
import os
import argparse
import json
import shlex
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
base = root / 'emulator-core/src/main/cpp/xenia/src'
source = (base / 'xenia/kernel/guest_scheduler.cc').read_text()

def extract(marker):
    begin = source.index(marker)
    brace = source.index('{', begin)
    depth, end = 1, brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[begin:end]

collector = extract('static bool CollectSchedulerStats()')
worker = extract('void GuestScheduler::IoWorkerLoop()')
code = r'''
#include <atomic>
#include <cassert>
#include <cstdint>
#include <functional>
#include <mutex>
#include <queue>
#include "xenia/base/zephyr_edge30_policy.h"
namespace cvars { bool guest_scheduler_stats = true; }
COLLECTOR
struct Clock {
  static inline uint64_t reads = 0;
  static uint64_t host_tick_count_raw() { return 100 + 10 * reads++; }
};
struct Event {};
namespace xe::threading { void Wait(Event*, bool) { assert(false); } }
struct GuestScheduler {
  struct BlockingCall {
    const std::function<void()>* fn = nullptr;
    std::atomic<bool> done{false};
    uint64_t queued_ns = 20;
  };
  struct Stats {std::atomic<uint64_t> io_calls{0}, io_queue_ns{0}, io_run_ns{0}, io_queue_max_ns{0};} stats_;
  std::atomic<bool> shutting_down_{false};
  std::mutex io_lock_;
  std::queue<BlockingCall*> io_queue_;
  struct Holder { Event* get() {return nullptr;} } io_event_;
  int wakes = 0;
  void WakeAll() { ++wakes; }
  void IoWorkerLoop();
};
WORKER
int main() {
  for (int requested = -8; requested <= 1024; ++requested) {
    for (uint32_t logical = 0; logical <= 128; ++logical) {
      const uint32_t cpus = std::max(logical, uint32_t(1));
      uint32_t expected = requested == 0 ? 0 : requested < 0 ? std::max(cpus * 3 / 4, uint32_t(1)) : std::min(uint32_t(requested), cpus);
      if (ZEPHYR_EDGE30_ULTRA) expected = std::min(expected, uint32_t(2));
      assert(xe::edge30::PipelineWorkers(requested, logical) == expected);
    }
  }
  assert(xe::edge30::PipelineWorkers(-1, 8) == (ZEPHYR_EDGE30_ULTRA ? 2 : 6));
  assert(xe::edge30::PipelineWorkers(4, 8) == (ZEPHYR_EDGE30_ULTRA ? 2 : 4));
  assert(!xe::edge30::DetailedGpuTiming(false));
  assert(xe::edge30::DetailedGpuTiming(true) == !ZEPHYR_EDGE30_ULTRA);
  assert(!xe::edge30::SchedulerStats(false));
  assert(xe::edge30::SchedulerStats(true));
  for (bool requested : {false, true}) {
    cvars::guest_scheduler_stats = requested;
    for (bool recorded : {false, true}) {
      GuestScheduler scheduler;
      GuestScheduler::BlockingCall call;
      call.queued_ns = recorded ? 20 : 0;
      Clock::reads = 0;
      int ran = 0;
      std::function<void()> fn = [&] {++ran; scheduler.shutting_down_.store(true);};
      call.fn = &fn;
      scheduler.io_queue_.push(&call);
      scheduler.IoWorkerLoop();
      assert(ran == 1 && call.done.load(std::memory_order_acquire));
      assert(scheduler.wakes == 1 && scheduler.io_queue_.empty());
      const bool measured = requested && recorded;
      assert(Clock::reads == (measured ? 2 : 0));
      assert(scheduler.stats_.io_calls.load() == (measured ? 1 : 0));
      assert(scheduler.stats_.io_queue_ns.load() == (measured ? 80 : 0));
      assert(scheduler.stats_.io_run_ns.load() == (measured ? 10 : 0));
    }
  }
}
'''.replace('COLLECTOR', collector).replace('WORKER', worker)
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--verify-android-target', action='store_true')
args = parser.parse_args()
if args.verify_android_target:
    databases = list(root.glob('emulator-core/.cxx/Release/*/arm64-v8a/compile_commands.json'))
    assert len(databases) == 1, 'Expected one dedicated Release compilation database'
    entries = json.loads(databases[0].read_text())
    for name in ('guest_scheduler.cc', 'vulkan_pipeline_cache.cc', 'vulkan_command_processor.cc', 'xendroid_emu.cpp'):
        selected = [entry for entry in entries if Path(entry['file']).name == name]
        assert len(selected) == 1, name
        flags = selected[0].get('arguments') or shlex.split(selected[0]['command'])
        for flag in ('-DZEPHYR_EDGE30_ULTRA=1', '-mtune=cortex-x2', '-mno-outline-atomics'):
            assert flag in flags, (name, flag)
        assert [flag for flag in flags if flag.startswith('-march=')][-1] == '-march=armv8.2-a+fp16+dotprod+lse', name
        assert any('aarch64' in flag and 'android' in flag for flag in flags), name
    print('NDK compile commands confirm SM8475 target flags for CPU, GPU and boot path.')
compiler = os.environ.get('CXX') or shutil.which('clang++') or 'g++'
with tempfile.TemporaryDirectory(prefix='zephyr-edge30-') as directory:
    path = Path(directory)
    (path / 'test.cc').write_text(code)
    for specialized in (0, 1):
        executable = path / f'test-{specialized}'
        subprocess.run([compiler, '-std=c++20', '-O2', '-fsanitize=undefined',
                        '-fno-sanitize-recover=undefined', '-pthread',
                        f'-DZEPHYR_EDGE30_ULTRA={specialized}', '-I', str(base),
                        str(path / 'test.cc'), '-o', str(executable)], check=True)
        subprocess.run([str(executable)], check=True)
print('Edge30 policy: 266,514 worker cases; real I/O completion/wake/timing paths, generic and dedicated modes passed.')
