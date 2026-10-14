#!/usr/bin/env python3
"""Test production timeline semantics and bounded selector with fake fences."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
base = root / 'emulator-core/src/main/cpp/xenia/src'
source = (base / 'xenia/ui/vulkan/vulkan_gpu_completion_timeline.cc').read_text()
begin = 'void VulkanGPUCompletionTimeline::UpdateCompletedSubmissionUpTo('
end = 'void VulkanGPUCompletionTimeline::PollPendingFences('
assert source.count(begin) == 1 and source.count(end) == 1
policy = begin + source.split(begin, 1)[1].split(end, 1)[0]
fixture = (root / 'emulator-core/src/test/cpp/gpu_completion_timeline_test.cc').read_text()
cxx = os.environ.get('CXX') or shutil.which('clang++') or 'c++'
with tempfile.TemporaryDirectory(prefix='zephyr-gpu-timeline-') as directory:
    temp = Path(directory)
    # Replace only Xenia assertion plumbing; compile the actual base header.
    header = temp / 'xenia/base/assert.h'
    header.parent.mkdir(parents=True)
    header.write_text('#pragma once\n#include <cassert>\n'
                      '#define assert_true(x) assert(x)\n'
                      '#define assert_not_zero(x) assert((x) != 0)\n')
    test = temp / 'test.cc'
    test.write_text(fixture + '\nnamespace xe::ui::vulkan {\n' + policy + '\n}\n')
    binary = str(temp / 'test')
    subprocess.run([cxx, '-std=c++17', '-O2', '-I', str(temp), '-I', str(base),
                    str(test), '-o', binary], check=True)
    subprocess.run([binary], check=True)
