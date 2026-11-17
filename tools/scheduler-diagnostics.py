#!/usr/bin/env python3
"""Summarize GuestSchedulerCPU0 phase windows without claiming host CPU utilization.

The diagnostic log reports wall time (including descheduling), not CPU busy.
The first window has no calibrated elapsed duration and is excluded from rates.
Offline parser; never changes emulator, guest scheduler, or Android settings.
"""
import argparse
import json
import math
from pathlib import Path
import re

NUMBER = r"([0-9]+(?:\.[0-9]+)?)"
PHASE = r" calls ([0-9]+) wall " + NUMBER + r" ms max " + NUMBER + r" ms"
PATTERN = re.compile(
    r"GuestSchedulerCPU0: window ([0-9]+) ms \| repoll" + PHASE +
    r" \| fiber" + PHASE + r" \| idle" + PHASE + r"$")
KINDS = ("repoll", "fiber", "idle")
LIMITATIONS = [
    "Wall times include descheduling; none of these measurements is CPU utilization.",
    "Fiber includes guest work, native shims, waiting and descheduling; it is not useful PPC execution time.",
    "Repoll measures its wrapper, not individual blocked objects or all scheduler bookkeeping.",
    "Idle includes wait and host scheduling delay; it is not necessarily kernel sleep time.",
    "Categories exclude other scheduler work; their sum is not a complete CPU 0 breakdown.",
    "The first (bootstrap) window has zero elapsed duration and is excluded from rates.",
    "If a log appends multiple emulator sessions, only the last Storage root session is analyzed.",
    "Counters are rounded; a log interval is not guaranteed to last exactly one second.",
    "Measurement overhead needs a controlled same-APK telemetry-off/on comparison.",
]


def parse_window(line):
    if line.count("GuestSchedulerCPU0:") != 1:
        raise ValueError("Missing or duplicate GuestSchedulerCPU0 marker")
    match = PATTERN.fullmatch(line[line.index("GuestSchedulerCPU0:"):].strip())
    if not match:
        raise ValueError("Invalid or truncated GuestSchedulerCPU0 window")
    values = match.groups()
    elapsed_ms = int(values[0])
    phases = {}
    for index, name in enumerate(KINDS):
        calls = int(values[1 + index * 3])
        wall_ms = float(values[2 + index * 3])
        max_ms = float(values[3 + index * 3])
        if not math.isfinite(wall_ms) or not math.isfinite(max_ms):
            raise ValueError("Nonfinite timing")
        if calls == 0 and (wall_ms != 0 or max_ms != 0):
            raise ValueError("Timing without calls")
        if max_ms > wall_ms + 0.001:
            raise ValueError("Maximum call duration exceeds cumulative phase wall time")
        phases[name] = {"calls": calls, "wall_ms": wall_ms, "max_call_ms": max_ms}
    return {"elapsed_ms": elapsed_ms, "calibrated": elapsed_ms > 0, "phases": phases}


def summarize(lines, last=None):
    if last is not None and last < 1:
        raise ValueError("last must be positive")
    valid, rejected, bootstrap, session_boundaries = [], 0, 0, 0
    for line in lines:
        # An xe.log may append several launches. A valid sample must not
        # silently mix the current game with a previous emulator session.
        if "Storage root:" in line:
            valid.clear()
            rejected, bootstrap = 0, 0
            session_boundaries += 1
            continue
        if "GuestSchedulerCPU0:" not in line:
            continue
        try:
            record = parse_window(line)
        except ValueError:
            rejected += 1
            continue
        if not record["calibrated"]:
            bootstrap += 1
            continue
        valid.append(record)
    if last is not None:
        valid = valid[-last:]
    elapsed_ms = sum(window["elapsed_ms"] for window in valid)
    aggregate = None
    if elapsed_ms:
        aggregate = {}
        for name in KINDS:
            calls = sum(window["phases"][name]["calls"] for window in valid)
            wall_ms = sum(window["phases"][name]["wall_ms"] for window in valid)
            aggregate[name] = {
                "calls": calls,
                "calls_per_second": calls * 1000 / elapsed_ms,
                "wall_ms": wall_ms,
                "wall_ms_per_second_of_window": wall_ms * 1000 / elapsed_ms,
                "mean_wall_ms_per_call": wall_ms / calls if calls else None,
                "largest_observed_call_ms": max(
                    window["phases"][name]["max_call_ms"] for window in valid),
            }
    return {
        "valid_windows_selected": len(valid),
        "session_boundaries_seen": session_boundaries,
        "bootstrap_windows_excluded": bootstrap,
        "malformed_windows_rejected": rejected,
        "measured_elapsed_ms": elapsed_ms,
        "phases": aggregate,
        "windows": valid,
        "limitations": LIMITATIONS,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    parser.add_argument("--last", type=int,
                        help="Use last N valid, non-bootstrap CPU0 windows (not N seconds)")
    args = parser.parse_args()
    if args.last is not None and args.last < 1:
        parser.error("--last must be positive")
    with args.log.open(encoding="utf-8", errors="replace") as stream:
        result = summarize(stream, args.last)
    print(json.dumps(result, indent=2, allow_nan=False))


if __name__ == "__main__":
    main()
