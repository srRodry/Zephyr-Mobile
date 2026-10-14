#!/usr/bin/env python3
"""Read VkFrameSync and VkPipelineWait logs with explicit timing units.

Offline only: no device connection, config writes or diagnostic activation.
Input timings are rounded native logs, not exact profiler measurements.
"""
import argparse
import json
import math
from pathlib import Path
import re

NUMBER = r"([0-9]+(?:\.[0-9]+)?)"
FIELDS = {
    "cpu_await_ms_per_frame": r"\bawait=" + NUMBER + r"ms\b",
    "submissions_per_frame": r"\bsubmissions=" + NUMBER + r"\b",
    "observed_submit_to_completion_avg_ms": r"submit->fence avg=" + NUMBER + r"ms\b",
    "observed_submit_to_completion_max_ms": r"submit->fence avg=" + NUMBER + r"ms max=" + NUMBER + r"ms\b",
    "gpu_ms_per_submission_avg": r"gpu exec avg=" + NUMBER + r"ms\b",
    "gpu_ms_per_submission_max": r"gpu exec avg=" + NUMBER + r"ms max=" + NUMBER + r"ms\b",
    "gpu_gap_ms_per_sample": r"gap avg=" + NUMBER + r"ms\b",
    "cpu_replay_and_submit_ms_per_frame": r"\breplay=" + NUMBER + r"ms\b",
}
PATTERNS = {key: re.compile(value) for key, value in FIELDS.items()}
PIPELINE_PATTERN = re.compile(
    r"VkPipelineWait: ([0-9]+) frames \| submission calls=" + NUMBER +
    r"/fr time=" + NUMBER + r"ms/fr max=" + NUMBER +
    r"ms \| explicit calls=" + NUMBER + r"/fr time=" + NUMBER +
    r"ms/fr max=" + NUMBER + r"ms\s*$")
PIPELINE_FIELDS = (
    "submission_calls_per_frame", "submission_wait_ms_per_frame",
    "submission_wait_max_call_ms", "explicit_calls_per_frame",
    "explicit_wait_ms_per_frame", "explicit_wait_max_call_ms",
)
LIMITATIONS = [
    "Rounded log values; weighted CPU means are approximate.",
    "GPU means are per completed timestamp sample/submission, not per frame.",
    "New submissions and completed GPU samples can belong to different windows; do not multiply their means.",
    "Zero GPU timing cannot distinguish missing timestamps from a value rounded to zero.",
    "submit->fence includes delayed CPU observation; it is not pure GPU execution.",
    "replay includes driver submission/fence acquisition, but excludes pipeline EndSubmission wait and setup replay.",
    "CPU await and GPU execution can overlap; do not add them to infer frame time.",
    "Pipeline waits are host API wall time, not GPU busy or summed worker compilation.",
    "Pipeline windows are independent; adjacency/frame counts do not establish a shared frame ID.",
    "Pipeline maxima are per wait call, not per frame; missing pipeline logs are unavailable, not zero.",
    "These logs alone do not identify a bottleneck, FPS gain or game compatibility.",
]


def parse_frame_sync(line):
    match = re.search(r"VkFrameSync: ([0-9]+) frames\b", line)
    if not match or int(match.group(1)) == 0:
        raise ValueError("Missing positive frame count")
    result = {"frames": int(match.group(1))}
    for key, pattern in PATTERNS.items():
        matches = list(pattern.finditer(line))
        if len(matches) != 1:
            raise ValueError(f"Missing or ambiguous {key}")
        # Max patterns capture the preceding average as well; take the last.
        value = float(matches[0].groups()[-1])
        if not math.isfinite(value):
            raise ValueError(f"Non-finite {key}")
        result[key] = value
    return result


def parse_pipeline_wait(line):
    if line.count("VkPipelineWait:") != 1:
        raise ValueError("Missing or ambiguous pipeline log marker")
    match = PIPELINE_PATTERN.fullmatch(line[line.index("VkPipelineWait:"):])
    if not match or int(match.group(1)) == 0:
        raise ValueError("Invalid pipeline wait window")
    result = {"frames": int(match.group(1))}
    for key, raw in zip(PIPELINE_FIELDS, match.groups()[1:]):
        value = float(raw)
        if not math.isfinite(value):
            raise ValueError(f"Non-finite {key}")
        result[key] = value
    return result


def summarize_pipeline_windows(windows, rejected, last):
    if last is not None:
        windows = windows[-last:]
    frames = sum(w["frames"] for w in windows)
    means = {}
    maxima = {}
    for key in PIPELINE_FIELDS:
        if key.endswith("_per_frame"):
            means[key] = (sum(w[key] * w["frames"] for w in windows) / frames
                          if frames else None)
        else:
            maxima[key] = max((w[key] for w in windows), default=None)
    return {"valid_windows_selected": len(windows), "frames_selected": frames,
            "malformed_windows_rejected": rejected,
            "approximate_frame_weighted": means,
            "largest_observed_wait_call_ms": maxima, "windows": windows}


def summarize(lines, last=None):
    if last is not None and last < 1:
        raise ValueError("last must be positive")
    windows = []
    rejected = 0
    pipeline_windows = []
    pipeline_rejected = 0
    for line in lines:
        if "VkPipelineWait:" in line:
            try:
                pipeline_windows.append(parse_pipeline_wait(line))
            except ValueError:
                pipeline_rejected += 1
        if "VkFrameSync:" not in line:
            continue
        try:
            windows.append(parse_frame_sync(line))
        except ValueError:
            rejected += 1
    if last is not None:
        windows = windows[-last:]
    frames = sum(window["frames"] for window in windows)
    cpu = {}
    for key in ("cpu_await_ms_per_frame", "cpu_replay_and_submit_ms_per_frame"):
        cpu[key] = (sum(w[key] * w["frames"] for w in windows) / frames
                    if frames else None)
    return {"valid_windows_selected": len(windows), "frames_selected": frames,
            "malformed_windows_rejected": rejected,
            "approximate_frame_weighted_cpu": cpu,
            "pipeline_waits": summarize_pipeline_windows(pipeline_windows, pipeline_rejected, last),
            "windows": windows, "limitations": LIMITATIONS}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    parser.add_argument("--last", type=int, help="Keep last N valid windows of each log type independently, not N seconds")
    args = parser.parse_args()
    if args.last is not None and args.last < 1:
        parser.error("--last must be positive")
    with args.log.open(encoding="utf-8", errors="replace") as stream:
        result = summarize(stream, args.last)
    print(json.dumps(result, indent=2, allow_nan=False))


if __name__ == "__main__":
    main()
