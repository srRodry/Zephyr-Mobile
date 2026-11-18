#!/usr/bin/env python3
"""Generate conservative Forza Horizon graphics A/B config snippets.

The reference intentionally disables optional shortcuts rather than changing
rendering internals. Copy the snippet into the game's config and relaunch.
Use a fresh run per arm: live properties and caches can confound comparisons.

Cvar categories come from their DEFINE_* registrations, NOT their names.
A [GPU] entry for a [Vulkan] cvar is silently ignored by ReadGameConfig.
"""
import argparse

REFERENCE = {
    "GPU": {
        "msaa_4x_as_2x": "false",
        "alpha_to_coverage_as_alpha_test": "false",
        "vulkan_depth_4x_as_1x": "false",
        "vulkan_samples_as_pixels_simple_ps": "false",
        "vulkan_samples_as_pixels_2x": "false",
        "vulkan_shading_rate": "0",
        "log_gpu_frame_time_breakdown": "false",
        "log_gpu_frame_time_breakdown_passes": "false",
    },
    "Vulkan": {
        "vulkan_async_skip_draws": "false",
        "vulkan_skip_gpu_written_texture_loads": "0",
    },
}


def render(mode):
    if mode not in ("reference", "light-timing"):
        raise ValueError(f"Unknown graphics A/B mode: {mode}")
    lines = [
        "# Zephyr graphics A/B: " + mode,
        "# Restart the game after applying; preserve your existing game settings.",
        "# Remove any debug.xendroid.* Android system-property overrides first.",
    ]
    for category, settings in REFERENCE.items():
        lines.append(f"[{category}]")
        for key, value in settings.items():
            if mode == "light-timing" and key == "log_gpu_frame_time_breakdown":
                value = "true"
            lines.append(f"{key} = {value}")
        lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["reference", "light-timing"])
    args = parser.parse_args()
    print(render(args.mode), end="")


if __name__ == "__main__":
    main()
