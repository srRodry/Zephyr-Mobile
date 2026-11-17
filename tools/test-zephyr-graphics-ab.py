#!/usr/bin/env python3
"""Validate A/B config sections against the actual production CVAR declarations."""
import configparser
import importlib.util
from pathlib import Path
import re
import unittest

path = Path(__file__).with_name("zephyr-graphics-ab.py")
spec = importlib.util.spec_from_file_location("zephyr_graphics_ab", path)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

ROOT = Path(__file__).resolve().parents[1]
CODE = ROOT / "emulator-core/src/main/cpp/xenia/src/xenia/gpu"
SOURCES = {
    "vulkan_async_skip_draws": CODE / "vulkan/vulkan_pipeline_cache.cc",
    "vulkan_skip_gpu_written_texture_loads": CODE / "vulkan/vulkan_texture_cache.cc",
    "log_gpu_frame_time_breakdown": CODE / "command_processor.cc",
}


class GraphicsABTests(unittest.TestCase):
    def parse(self, mode):
        parser = configparser.ConfigParser(interpolation=None)
        parser.optionxform = str
        parser.read_string(mod.render(mode))
        return parser

    def test_reference_disables_optional_shortcuts(self):
        config = self.parse("reference")
        self.assertEqual(set(config.sections()), {"GPU", "Vulkan"})
        for key in ("msaa_4x_as_2x", "alpha_to_coverage_as_alpha_test",
                    "vulkan_depth_4x_as_1x", "vulkan_samples_as_pixels_simple_ps",
                    "vulkan_samples_as_pixels_2x"):
            self.assertEqual(config["GPU"][key], "false")
        self.assertEqual(config["GPU"]["vulkan_shading_rate"], "0")
        self.assertEqual(config["GPU"]["log_gpu_frame_time_breakdown"], "false")
        self.assertEqual(config["Vulkan"]["vulkan_async_skip_draws"], "false")
        self.assertEqual(config["Vulkan"]["vulkan_skip_gpu_written_texture_loads"], "0")

    def test_unknown_mode_is_rejected(self):
        with self.assertRaises(ValueError):
            mod.render("unexpected")

    def test_light_timing_only_changes_instrumentation(self):
        reference, timing = self.parse("reference"), self.parse("light-timing")
        self.assertEqual(reference.sections(), timing.sections())
        for category in reference.sections():
            self.assertEqual(dict(reference[category]).keys(), dict(timing[category]).keys())
            for key, value in reference[category].items():
                expected = "true" if key == "log_gpu_frame_time_breakdown" else value
                self.assertEqual(timing[category][key], expected, f"{category}.{key}")
        self.assertEqual(timing["GPU"]["log_gpu_frame_time_breakdown_passes"], "false")

    def test_every_cvar_category_matches_production(self):
        # Categories are explicit in DEFINE_bool / DEFINE_int32; a leading
        # "vulkan_" does not imply the [Vulkan] section.
        for category, options in mod.REFERENCE.items():
            for key in options:
                source = SOURCES.get(key, CODE / "vulkan/vulkan_command_processor.cc")
                content = source.read_text(encoding="utf-8")
                pattern = rf'DEFINE_(?:bool|int32|uint32)\\(\\s*{re.escape(key)}\\s*,.*?,\\s*"([^"]+)"\\s*\\);'
                match = re.search(pattern, content, re.DOTALL)
                self.assertIsNotNone(match, f"CVAR {key} missing in {source}")
                self.assertEqual(category, match.group(1), f"{key} must use [{match.group(1)}]")


if __name__ == "__main__":
    unittest.main()
