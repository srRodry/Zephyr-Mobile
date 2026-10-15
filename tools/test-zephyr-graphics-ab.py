#!/usr/bin/env python3
"""Validate non-destructive graphics comparison presets."""
import importlib.util
from pathlib import Path
import unittest

path = Path(__file__).with_name("zephyr-graphics-ab.py")
spec = importlib.util.spec_from_file_location("zephyr_graphics_ab", path)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


class GraphicsABTests(unittest.TestCase):
    def test_reference_disables_optional_shortcuts(self):
        text = mod.render("reference")
        self.assertIn("[GPU]", text)
        for key in ("vulkan_async_skip_draws",
                    "msaa_4x_as_2x",
                    "alpha_to_coverage_as_alpha_test",
                    "vulkan_depth_4x_as_1x",
                    "vulkan_samples_as_pixels_simple_ps",
                    "vulkan_samples_as_pixels_2x"):
            self.assertIn(f"{key} = false", text)
        self.assertIn("vulkan_skip_gpu_written_texture_loads = 0", text)
        self.assertIn("vulkan_shading_rate = 0", text)
        self.assertIn("log_gpu_frame_time_breakdown = false", text)

    def test_light_timing_does_not_enable_pass_timestamps(self):
        text = mod.render("light-timing")
        self.assertIn("log_gpu_frame_time_breakdown = true", text)
        self.assertIn("log_gpu_frame_time_breakdown_passes = false", text)
        self.assertEqual(
            {line for line in mod.render("reference").splitlines()
             if line and not line.startswith("#") and not line.startswith("log_")},
            {line for line in text.splitlines()
             if line and not line.startswith("#") and not line.startswith("log_")})


if __name__ == "__main__":
    unittest.main()
