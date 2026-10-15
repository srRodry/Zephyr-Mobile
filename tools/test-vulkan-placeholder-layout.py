#!/usr/bin/env python3
"""Guard Vulkan placeholder pipelines from inheriting unused guest PS layouts."""

from pathlib import Path
import unittest

SOURCE = Path(__file__).resolve().parents[1] / (
    "emulator-core/src/main/cpp/xenia/src/xenia/gpu/vulkan/vulkan_pipeline_cache.cc"
)


class VulkanPlaceholderLayoutTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = SOURCE.read_text()
        start = cls.text.index("bool VulkanPipelineCache::EnsurePipelineCreated(")
        end = cls.text.index("bool VulkanPipelineCache::Initialize", start)
        cls.ensure = cls.text[start:end]

    def test_real_pipeline_keeps_cached_real_layout(self):
        self.assertIn("if (!creating_placeholder)", self.ensure)
        self.assertIn(
            "creation_arguments.pipeline->second.pipeline_layout.load",
            self.ensure,
        )

    def test_interpreter_placeholder_is_texture_free(self):
        self.assertIn("vertex_shader_override != VK_NULL_HANDLE", self.ensure)
        self.assertIn(
            "command_processor_.GetPipelineLayout(0, 0, 0, 0)",
            self.ensure,
        )

    def test_real_vs_placeholder_ignores_pixel_shader_layout(self):
        self.assertIn(
            "GetGuestGraphicsPipelineLayout(\n"
            "        creation_arguments.vertex_shader, nullptr)",
            self.ensure,
        )
        self.assertNotIn(
            "GetGuestGraphicsPipelineLayout(\n"
            "        creation_arguments.vertex_shader, "
            "creation_arguments.pixel_shader)",
            self.ensure,
        )

    def test_selected_layout_is_validated_before_vulkan_create(self):
        self.assertIn("if (!creation_pipeline_layout)", self.ensure)
        self.assertIn(
            "pipeline_create_info.layout = "
            "creation_pipeline_layout->GetPipelineLayout()",
            self.ensure,
        )


if __name__ == "__main__":
    unittest.main()
