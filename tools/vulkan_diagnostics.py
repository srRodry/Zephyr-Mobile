#!/usr/bin/env python3
"""Read VkFrameSync and VkPipelineWait logs with explicit timing units.

Offline only: no device connection, config writes or diagnostic activation.
Input timings are rounded native logs, not exact profiler measurements.
"""
import argparse
import io
import json
import math
from pathlib import Path
import re
import runpy
import zipfile

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
    "Append-only xe.log files are restricted to the latest marked session; unmarked logs cover all input.",
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
    splits = re.search(r"\bshared_upload_pass_splits=(\d+)\s*$", line)
    if splits:
        result["shared_upload_pass_splits"] = int(splits.group(1))
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
    session_boundaries_seen = 0
    for line in lines:
        # xe.log can contain multiple launches and games. Prevent GPU timing
        # from a previous session from contaminating the latest one.
        if "Storage root:" in line:
            session_boundaries_seen += 1
            windows.clear()
            rejected = 0
            pipeline_windows.clear()
            pipeline_rejected = 0
            continue
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
    return {"session_boundaries_seen": session_boundaries_seen,
            "analysis_scope": "latest_session" if session_boundaries_seen else "entire_log_unmarked",
            "valid_windows_selected": len(windows), "frames_selected": frames,
            "malformed_windows_rejected": rejected,
            "approximate_frame_weighted_cpu": cpu,
            "pipeline_waits": summarize_pipeline_windows(pipeline_windows, pipeline_rejected, last),
            "windows": windows, "limitations": LIMITATIONS}


NATIVE_FRAME = re.compile(r"^\w> f:(\d+) [\dA-Fa-f]+ ")
GAME_TITLE = re.compile(r"^\s*Title ID: ([\dA-Fa-f]{8})\s*$")
DRIVER = re.compile(r"\* driverID: ([^\s]+)")
PACE = re.compile(
    r"GpuFrame: (\d+) frames, interval avg=" + NUMBER + r"ms max=" + NUMBER +
    r"ms .*?intervals >37ms=(\d+) >50ms=(\d+) >70ms=(\d+)"
    r"(?: \| sampled=(\d+) >33\.3ms=(\d+))?\s*$")
SHARED_BARRIER_ORIGINS = re.compile(
    r"VkPassEndBarriers: per frame, passes ended by barriers pushed while: "
    r"textures=" + NUMBER + r" shared_memory=" + NUMBER)


def session_lines(lines, title_id=None):
    """Select the last launch containing the requested title, or the last launch."""
    selected = []
    current = []
    boundaries = 0
    current_matches = False
    for line in lines:
        if "Storage root:" in line:
            boundaries += 1
            if current_matches:
                selected = current
            current = []
            current_matches = False
            continue
        current.append(line)
        if title_id and (match := GAME_TITLE.match(line)) and match[1].upper() == title_id:
            current_matches = True
    if title_id is None or current_matches:
        selected = current
    return boundaries, selected


def combined_report(lines, title_id=None):
    audio = runpy.run_path(str(Path(__file__).with_name("analyze-audio-timing.py")))
    boundaries, selected = session_lines(lines, title_id)
    title_ids = sorted({match[1].upper() for match in map(GAME_TITLE.match, selected) if match})
    driver_ids = sorted({match[1] for match in map(DRIVER.search, selected) if match})
    timeline = []
    audio_events = []
    pacing_windows = []
    shared_origins = []
    invalid_gpu_windows = 0
    last_audio_frame = {}

    for line in selected:
        prefix = NATIVE_FRAME.match(line)
        end_frame = int(prefix[1]) if prefix else None
        if (match := PACE.search(line)):
            frames, avg, maximum, over_37, over_50, over_70, count, over_budget = match.groups()
            data = {"frames": int(frames), "average_ms": float(avg), "maximum_ms": float(maximum),
                    "over_37ms": int(over_37), "over_50ms": int(over_50), "over_70ms": int(over_70),
                    "sampled_intervals": int(count) if count is not None else None,
                    "over_30fps_budget": int(over_budget) if over_budget is not None else None}
            pacing_windows.append(data)
            kind = "guest_pacing"
        elif "VkFrameSync:" in line:
            try:
                data = parse_frame_sync(line)
            except ValueError:
                invalid_gpu_windows += 1
                continue
            kind = "vulkan"
        elif (match := SHARED_BARRIER_ORIGINS.search(line)):
            data = {"shared_memory_pass_end_origins_per_frame": float(match[2])}
            shared_origins.append(data)
            kind = "vulkan_barriers"
        elif "AudioPump:" in line or "AAudio:" in line:
            events = audio["parse"]([line])
            if not events:
                continue
            event = events[0]
            audio_events.append(event)
            kind = event["kind"]
            data = {key: val for key, val in event.items() if key != "kind"}
            data["previous_report_frame"] = last_audio_frame.get(kind)
            last_audio_frame[kind] = end_frame
        else:
            continue
        timeline.append({"kind": kind, "end_frame": end_frame, **data})

    vulkan_windows = [event for event in timeline if event["kind"] == "vulkan"]
    split_windows = [event for event in vulkan_windows if "shared_upload_pass_splits" in event]
    budget_windows = [window for window in pacing_windows if window["sampled_intervals"] is not None]
    measured = sum(window["sampled_intervals"] for window in budget_windows)
    summary = audio["summarize"](audio_events)
    # Early builds counted a zero deadline as if it were a real clock reading.
    # Keep the source sample in the timeline for audit; mark that maximum suspect.
    summary["contains_pump_lateness_over_60s"] = any(
        event.get("max_late_us", 0) > 60_000_000 for event in audio_events
    )
    return {
        "session_boundaries_seen": boundaries,
        "title_filter": title_id,
        "title_ids": title_ids,
        "vulkan_driver_ids": driver_ids,
        "vulkan_driver_id": driver_ids[0] if len(driver_ids) == 1 else None,
        "guest_pacing": {
            "windows": len(pacing_windows),
            "sampled_intervals_for_30fps_budget": measured if budget_windows else None,
            "over_30fps_budget": sum(w["over_30fps_budget"] for w in budget_windows) if budget_windows else None,
            "over_50ms": sum(w["over_50ms"] for w in pacing_windows) if pacing_windows else None,
        },
        "vulkan": {
            "windows": len(vulkan_windows), "malformed_windows": invalid_gpu_windows,
            "shared_memory_barrier_windows": len(shared_origins),
            "shared_upload_split_windows": len(split_windows),
            "frames_with_split_counter": sum(w["frames"] for w in split_windows) if split_windows else None,
            "shared_upload_pass_splits": sum(w["shared_upload_pass_splits"] for w in split_windows) if split_windows else None,
        },
        "audio": summary,
        "timeline": timeline,
        "limitations": [
            "Native frame numbers mark report emission, not synchronized GPU/audio sample boundaries.",
            "Audio reports cover separate intervals; proximity cannot establish causation.",
            "Frame pacing can include pauses, loading and throttling.",
            "Vulkan driver identity is taken from the native device report, not the saved driver setting.",
            "Old logs lack the exact upload split and 30 FPS budget counters.",
        ],
    }


def read_combined(path, title_id):
    if not zipfile.is_zipfile(path):
        with path.open(encoding="utf-8", errors="replace") as stream:
            return combined_report(stream, title_id)
    with zipfile.ZipFile(path) as archive:
        for name in ("current/xe.log", "xe.log"):
            if name in archive.namelist():
                with archive.open(name) as raw, io.TextIOWrapper(raw, encoding="utf-8", errors="replace") as stream:
                    return combined_report(stream, title_id)
    raise ValueError("Diagnostics archive does not contain current/xe.log or xe.log")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    parser.add_argument("--last", type=int, help="Keep last N valid windows of each log type independently, not N seconds")
    parser.add_argument("--combined", action="store_true",
                        help="Report guest pacing, Vulkan, audio and driver from xe.log or a diagnostics ZIP")
    parser.add_argument("--title", type=str.upper, help="Select the latest session containing this title ID")
    args = parser.parse_args()
    if args.title and (not args.combined or not re.fullmatch(r"[0-9A-F]{8}", args.title)):
        parser.error("--title requires --combined and an 8-digit hexadecimal title ID")
    if args.combined:
        if args.last is not None:
            parser.error("--last is only available for the Vulkan window report")
        try:
            result = read_combined(args.log, args.title)
        except (OSError, ValueError, zipfile.BadZipFile) as error:
            parser.error(str(error))
        print(json.dumps(result, indent=2, allow_nan=False))
        return
    if args.last is not None and args.last < 1:
        parser.error("--last must be positive")
    with args.log.open(encoding="utf-8", errors="replace") as stream:
        result = summarize(stream, args.last)
    print(json.dumps(result, indent=2, allow_nan=False))


if __name__ == "__main__":
    main()
