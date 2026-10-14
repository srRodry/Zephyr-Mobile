#!/usr/bin/env python3
"""Compile actual wait collector and check its two production call sites."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
base = root / 'emulator-core/src/main/cpp/xenia/src'
source = (base / 'xenia/gpu/vulkan/vulkan_pipeline_cache.cc').read_text()
for begin, end, kind in (
    ('void VulkanPipelineCache::EndSubmission()', 'bool VulkanPipelineCache::IsCreatingPipelines()', 'kSubmission'),
    ('void VulkanPipelineCache::AwaitPipelineCompletion()', 'void VulkanPipelineCache::CreationThread()', 'kExplicit'),
):
    body = source.split(begin, 1)[1].split(end, 1)[0]
    assert body.count('pipeline_wait_stats_.Measure(') == 1
    assert body.count('xe::threading::Wait(creation_completion_event_.get(), false)') == 1
    assert 'PipelineWaitStats::Kind::' + kind in body
    assert 'cvars::log_gpu_frame_time_breakdown' in body
    assert body.index('if (await_creation_completion_event)') < body.index('pipeline_wait_stats_.Measure(')

cxx = os.environ.get('CXX') or shutil.which('clang++') or 'c++'
with tempfile.TemporaryDirectory(prefix='zephyr-pipeline-wait-') as directory:
    binary = str(Path(directory) / 'test')
    subprocess.run([cxx, '-std=c++17', '-O2', '-pthread', '-I', str(base),
                    str(root / 'emulator-core/src/test/cpp/pipeline_wait_stats_test.cc'),
                    '-o', binary], check=True)
    subprocess.run([binary], check=True)
