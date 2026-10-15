#!/usr/bin/env python3
"""Correlate APU pump and AAudio diagnostics from Android emulator logs.

Usage: python3 tools/analyze-audio-timing.py xendroid.log
Optional: --json for machine-readable interval records.

Each AudioPump window covers 5 seconds, each AAudio window about 1 second.
Input order and original logcat timestamps are preserved. The report provides
aggregate metrics only; it does not infer causality from overlapping windows.
"""
import argparse
import json
import re
from pathlib import Path

PUMP = re.compile(
    r"AudioPump: (?P<samples>\d+) samples, (?P<late>\d+) late "
    r"\((?P<late_pct>[\d.]+)%\), max late (?P<max_late_us>\d+) us, "
    r"guest dispatch avg (?P<avg_dispatch_us>\d+) us, max "
    r"(?P<max_dispatch_us>\d+) us, no-credit (?P<no_credit>\d+), "
    r"no-submit (?P<no_submit>\d+), topups (?P<topups>\d+)"
)
AAUDIO = re.compile(
    r"AAudio: (?P<callbacks>\d+) cb, (?P<gaps>\d+) gaps "
    r"\((?P<gap_pct>[\d.]+)%\), queue avg (?P<queue_avg>[\d.]+) "
    r"max (?P<queue_max>\d+), rate (?P<rate>[\d.]+), "
    r"xruns (?P<xruns>-?\d+), clipped (?P<clipped>\d+) "
    r"\([\d.]+%\), callback (?P<callback_avg_us>[\d.]+) us avg "
    r"(?P<callback_max_us>\d+) max, queue lock "
    r"(?P<queue_lock_avg_us>[\d.]+) us avg "
    r"(?P<queue_lock_max_us>\d+) max"
)
LOGCAT = re.compile(r"(?P<date>\d\d-\d\d)\s+(?P<time>\d\d:\d\d:\d\d\.\d+)")


def timestamp(line):
    match = LOGCAT.search(line)
    if not match:
        return None
    # Keep the source-local logcat timestamp without assuming a year or zone.
    return match.group("date") + " " + match.group("time")


def parse(lines):
    events = []
    for line in lines:
        for kind, pattern in (("pump", PUMP), ("aaudio", AAUDIO)):
            match = pattern.search(line)
            if match:
                values = {key: (float(value) if "." in value else int(value))
                          for key, value in match.groupdict().items()}
                events.append({"kind": kind, "timestamp": timestamp(line), **values})
                break
    return events


def xrun_increase(sinks):
    """XRunCount is cumulative per stream, not a per-second measurement.

    Resets can occur after a stream rebuild. Do not count the first observed
    value as a delta; its starting point is unknown.
    """
    previous = None
    increase = 0
    for sink in sinks:
        current = sink["xruns"]
        if current < 0:
            continue
        if previous is not None and current >= previous:
            increase += current - previous
        previous = current
    return increase


def summarize(events):
    pumps = [e for e in events if e["kind"] == "pump"]
    sinks = [e for e in events if e["kind"] == "aaudio"]
    callbacks = sum(e["callbacks"] for e in sinks)
    samples = sum(e["samples"] for e in pumps)
    return {
        "pump_windows": len(pumps),
        "aaudio_windows": len(sinks),
        "pump_late_pct": round(100 * sum(e["late"] for e in pumps) / samples, 2)
        if samples else None,
        "aaudio_gap_pct": round(100 * sum(e["gaps"] for e in sinks) / callbacks, 2)
        if callbacks else None,
        "no_credit": sum(e["no_credit"] for e in pumps),
        "no_submit": sum(e["no_submit"] for e in pumps),
        "topups": sum(e["topups"] for e in pumps),
        "max_pump_late_us": max((e["max_late_us"] for e in pumps), default=None),
        "max_dispatch_us": max((e["max_dispatch_us"] for e in pumps), default=None),
        "max_callback_us": max((e["callback_max_us"] for e in sinks),
                               default=None),
        "max_queue_lock_us": max((e["queue_lock_max_us"] for e in sinks),
                                default=None),
        "min_queue_avg": min((e["queue_avg"] for e in sinks), default=None),
        "min_playback_rate": min((e["rate"] for e in sinks), default=None),
        "last_xruns": next((e["xruns"] for e in reversed(sinks)
                            if e["xruns"] >= 0), None),
        "observed_xrun_increase": xrun_increase(sinks),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    events = parse(args.log.read_text(errors="replace").splitlines())
    summary = summarize(events)
    if args.json:
        print(json.dumps({"summary": summary, "events": events}, indent=2))
    else:
        print("Audio timing diagnostics")
        for key, value in summary.items():
            print(f"  {key}: {value}")
        if not events:
            print("No matching AudioPump/AAudio diagnostics found. "
                  "Enable apu_aaudio_log_stats for the next test.")


if __name__ == "__main__":
    main()
