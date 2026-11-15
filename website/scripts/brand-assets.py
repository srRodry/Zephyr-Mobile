#!/usr/bin/env python3
"""Compatibility entry point: generate shared Zephyr Anel assets, never upstream art."""
from pathlib import Path
import runpy
runpy.run_path(str(Path(__file__).resolve().parents[2]/'branding/generate.py'),run_name='__main__')
