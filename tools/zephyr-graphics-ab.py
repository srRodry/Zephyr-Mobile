#!/usr/bin/env python3
"""Generate conservative Forza Horizon graphics A/B config snippets.

The reference intentionally disables optional shortcuts rather than changing
rendering internals. Copy the snippet into the game's config and relaunch.
Use a fresh run per arm: live properties and caches can confound comparisons.
"""
import argparse

REFERENCE = {
    "vulkan_async_skip_draws": "false",
    "msaa_4x_as_2x": "false",
    "alpha_to_coverage_as_alpha_test": "false",
    "vulkan_depth_4x_as_1x": "false",
    "vulkan_samples_as_pixels_simple_ps": "false",
    "vulkan_samples_as_pixels_2x": "false",
    "vulkan_shading_rate": "0",
    "vulkan_skip_gpu_written_texture_loads": "0",
    "log_gpu_frame_time_breakdown": "false",
    "log_gpu_frame_time_breakdown_passes": "false",
}
LIGHT_TIMING = {
    **REFERENCE,
    "log_gpu_frame_time_breakdown": "true",
    "log_gpu_frame_time_breakdown_passes": "false",
}


def render(mode):
    settings = REFERENCE if mode == "reference" else LIGHT_TIMING
    lines = [
        "# Zephyr graphics A/B: " + mode,
        "# Restart the game after applying; preserve your existing game settings.",
        "# Remove any debug.xendroid.* Android system-property overrides first.",
        "[GPU]",
    ]
    lines.extend(f"{key} = {value}" for key, value in settings.items())
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["reference", "light-timing"])
    args = parser.parse_args()
    print(render(args.mode), end="")


if __name__ == "__main__":
    main()
