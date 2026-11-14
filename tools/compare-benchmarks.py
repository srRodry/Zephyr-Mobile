#!/usr/bin/env python3
"""Compare benchmark_forza.py captures without confusing presentation with guest FPS.

Each capture needs summary.json and a manually verified benchmark-context.json.
No Android device, external packages or changes to emulator settings are required.
"""
import argparse
import json
import math
from pathlib import Path
import statistics

CONTEXT_FIELDS = (
    "device", "soc", "android_build", "driver", "game_title_id",
    "game_executable_sha256", "scene", "internal_resolution", "config_sha256",
    "cache_state", "frame_generation", "instrumentation",
)
METRICS = ("presented_fps", "presented_frame_ms_median",
           "presented_frame_ms_p95", "presented_frame_ms_p99")


def positive(value, name):
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"{name}: expected a finite positive number")
    if not math.isfinite(value) or value <= 0:
        raise ValueError(f"{name}: expected a finite positive number")
    return value


def read_capture(directory):
    directory = Path(directory)
    summary = json.loads((directory / "summary.json").read_text(encoding="utf-8"))
    context = json.loads((directory / "benchmark-context.json").read_text(encoding="utf-8"))
    if not isinstance(summary, dict) or not isinstance(context, dict):
        raise ValueError(f"{directory}: summary and context must be objects")
    for key in (*CONTEXT_FIELDS, "commit", "apk_sha256"):
        if not isinstance(context.get(key), str) or not context[key].strip():
            raise ValueError(f"{directory}: missing context field {key}")
    for key in ("game_executable_sha256", "config_sha256", "apk_sha256"):
        value = context[key]
        if len(value) != 64 or any(c not in "0123456789abcdef" for c in value.lower()):
            raise ValueError(f"{directory}: {key} must be a SHA-256 hex digest")
    if context["frame_generation"] != "off":
        raise ValueError(f"{directory}: disable frame generation before comparing")
    for key in METRICS:
        positive(summary.get(key), f"{directory}: {key}")
    if not (summary[METRICS[1]] <= summary[METRICS[2]] <= summary[METRICS[3]]):
        raise ValueError(f"{directory}: inconsistent frame-time percentiles")
    positive(summary.get("measurement_elapsed_s"), f"{directory}: measurement_elapsed_s")
    package = summary.get("package")
    if not isinstance(package, str) or not package.strip():
        raise ValueError(f"{directory}: missing package")
    return {"directory": str(directory), "context": context, "summary": summary}


def compare(reference, candidate, *, allow_config_change=False):
    if len(reference) < 3 or len(candidate) < 3:
        raise ValueError("At least three independent captures per arm are required")
    paths = [Path(r["directory"]).resolve() for r in reference + candidate]
    if len(paths) != len(set(paths)):
        raise ValueError("A capture cannot be reused as an independent run")
    first = reference[0]
    for run in reference + candidate:
        for key in CONTEXT_FIELDS:
            if allow_config_change and key == "config_sha256":
                continue
            if run["context"][key] != first["context"][key]:
                raise ValueError(f"Incomparable context: {key} differs in {run['directory']}")
        if run["summary"]["package"] != first["summary"]["package"]:
            raise ValueError("Incomparable captures: package differs")
    for arm in (reference, candidate):
        for key in ("commit", "apk_sha256", "config_sha256"):
            if len({r["context"][key] for r in arm}) != 1:
                raise ValueError(f"Mixed {key} values within one arm")
    durations = [r["summary"]["measurement_elapsed_s"] for r in reference + candidate]
    if max(durations) / min(durations) > 1.1:
        raise ValueError("Capture durations differ by more than 10%")
    rows = {}
    for metric in METRICS:
        a = [r["summary"][metric] for r in reference]
        b = [r["summary"][metric] for r in candidate]
        am, bm = statistics.median(a), statistics.median(b)
        rows[metric] = {
            "reference_median": am, "candidate_median": bm,
            "change_percent": 100 * (bm / am - 1),
            "reference_range": [min(a), max(a)],
            "candidate_range": [min(b), max(b)],
        }
    return {
        "schema_version": 1,
        "config_change_allowed": allow_config_change,
        "config_changed": reference[0]["context"]["config_sha256"] != candidate[0]["context"]["config_sha256"],
        "reference_runs": len(reference),
        "candidate_runs": len(candidate), "metrics": rows,
        "reference_commit": reference[0]["context"]["commit"],
        "candidate_commit": candidate[0]["context"]["commit"],
        "captures": {"reference": reference, "candidate": candidate},
        "caveats": [
            "Presentation FPS is not proof of guest simulation speed.",
            "Medians of run percentiles are not pooled frame percentiles.",
            "Context is operator supplied; inspect thermal logs and screenshots manually.",
            "This descriptive comparison does not establish statistical significance or visual correctness.",
        ],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", nargs="+", type=Path, required=True)
    parser.add_argument("--candidate", nargs="+", type=Path, required=True)
    parser.add_argument("--output", type=Path, help="New JSON report; existing files are preserved")
    parser.add_argument("--allow-config-change", action="store_true",
                        help="Explicit configuration experiment; other context must still match")
    args = parser.parse_args()
    try:
        result = compare([read_capture(p) for p in args.reference],
                         [read_capture(p) for p in args.candidate],
                         allow_config_change=args.allow_config_change)
        rendered = json.dumps(result, indent=2, allow_nan=False) + "\n"
        if args.output:
            with args.output.open("x", encoding="utf-8") as stream:
                stream.write(rendered)
        print(rendered, end="")
    except (ValueError, OSError, KeyError, TypeError) as error:
        parser.error(str(error))


if __name__ == "__main__":
    main()
