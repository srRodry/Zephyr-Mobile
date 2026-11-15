#!/usr/bin/env python3
"""Compare production packed-constant copies to independent per-register packing.
No Vulkan loader/GPU needed. Tests exact bytes, ordering, boundaries and untouched guards, not device FPS.
"""
import os
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
xenia = root / 'emulator-core/src/main/cpp/xenia'
with tempfile.TemporaryDirectory(prefix='zephyr-packed-constants-') as tmp:
    binary = str(Path(tmp) / 'test')
    subprocess.run([
        os.environ.get('CXX', 'c++'), '-std=c++20', '-O1', '-g',
        '-Wall', '-Wextra', '-Werror', '-fsanitize=address,undefined',
        '-fno-omit-frame-pointer', '-I', str(xenia / 'src'),
        '-I', str(xenia),
        '-I', str(xenia / 'third_party/Vulkan-Headers/include'),
        str(root / 'emulator-core/src/test/cpp/packed_float_constants_test.cc'),
        '-o', binary,
    ], check=True)
    subprocess.run([binary], check=True)
