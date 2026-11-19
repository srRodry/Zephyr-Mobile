#!/usr/bin/env python3
"""Summarize Army of Two: The Devil's Cartel boot logs without guessing FPS.

Accepts a standalone xe.log or the app's diagnostic ZIP. Pure offline analysis:
no Android device, game image, network, changes to configs or unsafe extraction.
"""
import argparse
import json
from pathlib import Path
import re
from zipfile import ZipFile

MAX_LOG_BYTES = 32 * 1024 * 1024
LOADED_XEX = re.compile(r"\bModule [^\r\n]*?\\([A-Za-z0-9._-]+\.xex):", re.I)
LAUNCH = re.compile(r"LaunchXexFile: requested='([^']*)', resolved='([^']*)'")
RESCUE = re.compile(r"GuestScheduler: (\d+) gated waits were only re-readied by the (\d+)ms backstop")
RING_WRITEBACK = re.compile(r"Army TDC: pre-wait primary ring writeback rptr=(\d+) depth=(\d+)")


def read_log(path):
    if path.suffix.lower() != ".zip":
        if path.stat().st_size > MAX_LOG_BYTES:
            raise ValueError("Log is too large")
        return path.read_text(encoding="utf-8", errors="replace")
    with ZipFile(path) as archive:
        names = [n for n in archive.namelist()
                 if n.replace("\\", "/").endswith("/xe.log") or n == "xe.log"]
        if len(names) != 1:
            raise ValueError(f"Expected one xe.log in ZIP, found {len(names)}")
        if archive.getinfo(names[0]).file_size > MAX_LOG_BYTES:
            raise ValueError("Log in ZIP is too large")
        return archive.read(names[0]).decode("utf-8", errors="replace")


def analyze(content):
    launches = []
    modules = []
    rescues = []
    ring_writebacks = []
    for line in content.splitlines():
        if match := LAUNCH.search(line):
            launches.append({"requested": match.group(1), "resolved": match.group(2)})
        if match := LOADED_XEX.search(line):
            module = match.group(1)
            if module.lower() not in {x.lower() for x in modules}:
                modules.append(module)
        if match := RESCUE.search(line):
            rescues.append({"waits": int(match.group(1)), "backstop_ms": int(match.group(2))})
        if match := RING_WRITEBACK.search(line):
            ring_writebacks.append({"dword_index": int(match.group(1)),
                                    "indirect_buffer_depth": int(match.group(2))})
    missing_xam = len(re.findall(
        r"undefined extern call[^\r\n]*XamBackgroundDownloadSetMode", content, re.I))
    event_trace = content.count("XEvent signal:")
    poll_trace = content.count("MemoryPollPark: parked poll loop")
    has_vk = ("VkFrameSync:" in content or "VkPipelineWait:" in content)
    return {
        "title_id_454109AB_present": "454109AB" in content.upper(),
        "launches": launches,
        "wrong_main_as_launcher": "Cannot attach DLL AO4.Main" in content,
        "loaded_modules": modules,
        "missing_xam_background_download_calls": missing_xam,
        "gated_wait_backstop_windows": rescues,
        "rescued_wait_total_across_reported_windows": sum(x["waits"] for x in rescues),
        "watchdog_no_frame_reports": content.count("no guest frame presented"),
        "pre_wait_ring_writeback_markers": ring_writebacks,
        "android_vblank_resync_messages": content.count("Guest vblank resynced"),
        "xevent_trace_lines": event_trace,
        "memory_poll_trace_lines": poll_trace,
        "vulkan_frame_or_pipeline_timings_present": has_vk,
        "caveats": [
            "These are log occurrences, not distinct root causes or measured frame-time samples.",
            "XEX listed means loaded, not that the game reached gameplay.",
            "Backstop rescue totals are per report window, not a number of permanently lost signals.",
            "Diagnostic xe.log may be truncated and not represent the full execution.",
            "No GPU/FPS bottleneck can be attributed from absent timestamp lines.",
        ],
    }


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("log", type=Path, help="xe.log or unmodified xendroid diagnostics ZIP")
    args = ap.parse_args()
    try:
        print(json.dumps(analyze(read_log(args.log)), indent=2, ensure_ascii=False))
    except (OSError, ValueError, KeyError) as error:
        ap.error(str(error))


if __name__ == "__main__":
    main()
