#!/usr/bin/env python3
"""Compile the actual opt-in JIT measurement header; no Android APK or guest benchmark."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
root = Path(__file__).resolve().parents[1]
cxx = os.environ.get('CXX') or shutil.which('clang++') or 'c++'
with tempfile.TemporaryDirectory(prefix='zephyr-jit-stats-') as tmp:
    binary = str(Path(tmp) / 'test')
    subprocess.run([cxx, '-std=c++17', '-O2', '-pthread', '-I',
                    str(root / 'emulator-core/src/main/cpp/xenia/src'),
                    str(root / 'emulator-core/src/test/cpp/jit_compile_stats_test.cc'),
                    '-o', binary], check=True)
    subprocess.run([binary], check=True)
