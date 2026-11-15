#!/usr/bin/env python3
"""Execute production cache extraction against a fake driver, under sanitizers.
No Vulkan loader/GPU needed. This tests lifetime, failures and isolation, not FPS.
"""
import os
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
xenia = root / 'emulator-core/src/main/cpp/xenia'
with tempfile.TemporaryDirectory(prefix='zephyr-cache-snapshot-') as tmp:
    binary = str(Path(tmp) / 'test')
    subprocess.run([
        os.environ.get('CXX', 'c++'), '-std=c++20', '-O1', '-g',
        '-Wall', '-Wextra', '-Werror', '-fsanitize=address,undefined',
        '-fno-omit-frame-pointer', '-I', str(xenia / 'src'),
        '-I', str(xenia),
        '-I', str(xenia / 'third_party/Vulkan-Headers/include'),
        str(root / 'emulator-core/src/test/cpp/pipeline_cache_snapshot_test.cc'),
        '-o', binary,
    ], check=True)
    subprocess.run([binary], check=True)
