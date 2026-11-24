#!/usr/bin/env python3
"""Capture a stationary XenDroid scene via ADB (Python 3, standard library).

Does not change settings, restart the game, clear caches or inject input.
SurfaceFlinger numbers measure game-surface presentation, not Android UI FPS.
Optional native GpuFrame logging independently measures guest frame intervals.
"""

import argparse
import json
import math
from pathlib import Path
import re
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--package", default="xendroid.compose.fork")
    parser.add_argument("--duration", type=int, default=60)
    parser.add_argument("--label", required=True)
    args = parser.parse_args()
    if args.duration < 5 or not re.fullmatch(r"[A-Za-z0-9_-]+", args.label):
        parser.error("duration must be >=5 and label may contain letters, digits, _ and -")
    out = Path(__file__).resolve().parents[1] / "performance-tests" / args.label
    out.mkdir(parents=True, exist_ok=False)
    prefix = [args.adb, "-s", args.serial]
    log_path = f"/sdcard/Android/data/{args.package}/files/compose/xe.log"

    def adb(*command, binary=False):
        result = subprocess.run(prefix + list(command), capture_output=True, timeout=30)
        if result.returncode:
            raise RuntimeError(result.stderr.decode(errors="replace"))
        return result.stdout if binary else result.stdout.decode(errors="replace")

    def snapshot(stage):
        for service in ("battery", "thermalservice"):
            (out / f"{stage}-{service}.txt").write_text(
                adb("shell", "dumpsys", service), encoding="utf-8")
        (out / f"{stage}.png").write_bytes(adb("exec-out", "screencap", "-p", binary=True))

    layer_lines = adb("shell", "dumpsys", "SurfaceFlinger", "--list").splitlines()
    layers = [line for line in layer_lines
              if f"SurfaceView[{args.package}/" in line and "(BLAST)" in line]
    if len(layers) != 1:
        raise RuntimeError(f"Expected one active game surface, got {layers}")
    layer = layers[0]
    if layer.startswith("RequestedLayerState{"):
        layer = layer.split("{", 1)[1].split(" parentId=", 1)[0]

    def timestamps():
        raw = adb("shell", f"dumpsys SurfaceFlinger --latency '{layer}'")
        stamps = set()
        for line in raw.splitlines()[1:]:
            fields = line.split()
            if len(fields) == 3 and all(x.isdigit() for x in fields):
                actual = int(fields[1])
                if 0 < actual < 2**63 - 1:
                    stamps.add(actual)
        return stamps

    snapshot("start")
    before = adb("shell", f"tail -c 65536 '{log_path}'")
    counters = re.findall(r"f:(\d+)", before)
    start_frame = max(map(int, counters), default=0)
    cutoff = max(timestamps(), default=0)
    all_stamps = set()
    polls = []
    start = time.monotonic()
    print(f"Measuring {args.label} for {args.duration}s", flush=True)
    while time.monotonic() - start < args.duration:
        stamps = timestamps()
        fresh = {stamp for stamp in stamps if stamp > cutoff}
        all_stamps.update(fresh)
        polls.append({"elapsed_s": time.monotonic() - start, "timestamps_ns": sorted(fresh)})
        time.sleep(min(1.0, max(0.0, args.duration - (time.monotonic() - start))))
    final_stamps = {stamp for stamp in timestamps() if stamp > cutoff}
    all_stamps.update(final_stamps)
    elapsed = time.monotonic() - start
    polls.append({"elapsed_s": elapsed, "timestamps_ns": sorted(final_stamps)})
    # Save the complete native log before taking the end snapshot, then restrict
    # native statistics to frame numbers greater than the initial log position.
    native_log = adb("exec-out", "cat", log_path)
    session_starts = list(re.finditer(r"^.*Storage root:.*$", native_log, re.MULTILINE))
    if session_starts:
        native_log = native_log[session_starts[-1].start():]
    (out / "xe.log").write_text(native_log, encoding="utf-8")
    snapshot("end")
    ordered = sorted(all_stamps)
    intervals = [(b - a) / 1e6 for a, b in zip(ordered, ordered[1:])]
    if len(intervals) < 2:
        raise RuntimeError("No advancing frame timestamps; inspect the saved screenshots/log")

    sorted_intervals = sorted(intervals)

    def percentile(p):
        return sorted_intervals[max(0, math.ceil(len(sorted_intervals) * p) - 1)]

    native = []
    for line in native_log.splitlines():
        match = re.search(r"f:(\d+).*GpuFrame: (\d+) frames, interval avg=([\d.]+)ms max=([\d.]+)ms", line)
        if match and int(match[1]) > start_frame:
            native.append({"frame": int(match[1]), "count": int(match[2]),
                           "avg_ms": float(match[3]), "max_ms": float(match[4])})
    result = {
        "label": args.label, "package": args.package, "surface": layer,
        "measurement_elapsed_s": elapsed, "presented_frames": len(ordered),
        "presented_fps": 1000.0 * len(intervals) / sum(intervals),
        "presented_frame_ms_median": percentile(.5),
        "presented_frame_ms_p95": percentile(.95),
        "presented_frame_ms_p99": percentile(.99),
        "presented_frame_ms_max": max(intervals),
        "native_samples": len(native),
        "caveat": "Compare the same scene and thermal conditions; native logging adds overhead.",
    }
    # Count each timestamp once at first observation. Denominators include time
    # with no presents, including a freeze at the end of a run. Window boundaries
    # have the precision of an ADB poll; keep the raw polls for inspection.
    result["presented_fps_over_elapsed"] = len(ordered) / elapsed
    if elapsed >= 180:
        seen = set()
        early = late = 0
        for poll in polls:
            fresh = set(poll["timestamps_ns"]) - seen
            seen.update(fresh)
            if poll["elapsed_s"] <= 60:
                early += len(fresh)
            elif poll["elapsed_s"] > elapsed - 60:
                late += len(fresh)
        result["presented_fps_first_minute"] = early / 60
        result["presented_fps_last_minute"] = late / 60
    if native:
        native_time = sum(x["count"] * x["avg_ms"] for x in native)
        result["guest_fps_from_logged_intervals"] = (
            1000.0 * sum(x["count"] for x in native) / native_time if native_time else None)
        result["guest_worst_logged_frame_ms"] = max(x["max_ms"] for x in native)
    (out / "frame-data.json").write_text(json.dumps({"polls": polls, "native": native}, indent=2))
    (out / "summary.json").write_text(json.dumps(result, indent=2))
    print(json.dumps(result, indent=2), flush=True)


if __name__ == "__main__":
    main()
