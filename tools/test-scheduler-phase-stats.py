#!/usr/bin/env python3
"""Test actual collector; unwrap instrumentation and preserve original RunLoop."""
from pathlib import Path
import os
import re
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
base = root / 'emulator-core/src/main/cpp/xenia/src'
source = (base / 'xenia/kernel/guest_scheduler.cc').read_text()
body = source.split('void GuestScheduler::RunLoop(', 1)[1].split('void GuestScheduler::NoteForcedPreempt', 1)[0]
pattern = r'cpu0_phase_stats_\.Measure\(\s*cpu_index == 0 && cvars::guest_scheduler_stats,\s*SchedulerPhaseStats::Kind::(\w+),\s*\[&\]\(\) \{(.*?)\}\);'
matches = re.findall(pattern, body, re.S)
assert [kind for kind, _ in matches] == ['kRepoll', 'kFiber', 'kIdle', 'kIdle', 'kIdle']
unwrapped = 'void GuestScheduler::RunLoop(' + re.sub(pattern, lambda m: m[2].strip(), body, flags=re.S)
# The Android CPU preference is an opt-in pre-loop scheduling hint. Keep the
# original scheduler's predicates, queue operations and fiber transitions
# locked against the frozen baseline rather than updating that baseline.
fast_core_prelude = '''#if XE_PLATFORM_ANDROID || XE_PLATFORM_xendroid
  if (cpu_index == 0) {
    MaybePreferFastCoresForCpu0();
  }
#endif
'''
assert unwrapped.count(fast_core_prelude) == 1, 'Missing or duplicated CPU 0 affinity prelude'
unwrapped = unwrapped.replace(fast_core_prelude, '', 1)
assert 'sched_getaffinity(0, sizeof(original), &original)' in source
assert 'sched_setaffinity(0, sizeof(preferred), &preferred)' in source
baseline = (root / 'emulator-core/src/test/cpp/guest_scheduler_run_loop_baseline.inc').read_text()
baseline = baseline[baseline.index('void GuestScheduler::RunLoop('):]
normalize = lambda s: re.sub(r'\s+', '', s)
assert normalize(unwrapped) == normalize(baseline), 'Scheduler predicates/operations changed beyond instrumentation'
report = source.split('void GuestScheduler::ReportStatsIfDue()', 1)[1].split('void GuestScheduler::WatchdogLoop()', 1)[0]
assert report.index('if (!cvars::guest_scheduler_stats)') < report.index('cpu0_phase_stats_.Take()')
assert source.count('cpu0_phase_stats_.Take()') == 1
assert 'GuestSchedulerCPU0: window {} ms' in report

cxx = os.environ.get('CXX') or shutil.which('clang++') or 'c++'
with tempfile.TemporaryDirectory(prefix='zephyr-scheduler-phase-') as directory:
    binary = str(Path(directory) / 'test')
    subprocess.run([cxx, '-std=c++17', '-O2', '-pthread', '-I', str(base),
                    str(root / 'emulator-core/src/test/cpp/scheduler_phase_stats_test.cc'),
                    '-o', binary], check=True)
    subprocess.run([binary], check=True, timeout=30)
