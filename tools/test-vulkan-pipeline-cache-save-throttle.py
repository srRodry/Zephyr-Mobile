#!/usr/bin/env python3
"""Guard the Vulkan pipeline-cache save throttle against startup regressions."""

from pathlib import Path
import unittest

SOURCE = Path(__file__).resolve().parents[1] / (
    "emulator-core/src/main/cpp/xenia/src/xenia/gpu/vulkan/vulkan_pipeline_cache.cc"
)


class VulkanPipelineCacheSaveThrottleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = SOURCE.read_text()

    def test_new_title_resets_dirty_generation_and_starts_interval(self):
        start = self.text.index("void VulkanPipelineCache::InitializeShaderStorage(")
        end = self.text.index("void VulkanPipelineCache::SaveVkPipelineCache()")
        init = self.text[start:end]
        self.assertIn("ShutdownShaderStorage();", init)
        dirty = init.index(
            "vk_pipeline_cache_dirty_.store(false, std::memory_order_relaxed);"
        )
        timer = init.index(
            "vk_pipeline_cache_last_save_ms_ = xe::Clock::QueryHostUptimeMillis();"
        )
        storage = init.index("storage_writer_.InitializeShaderStorage(")
        self.assertLess(dirty, storage)
        self.assertLess(timer, storage)

    def test_periodic_save_waits_for_interval(self):
        start = self.text.index("void VulkanPipelineCache::MaybeSaveVkPipelineCache()")
        end = self.text.index("void VulkanPipelineCache::EndSubmission()")
        maybe = self.text[start:end]
        self.assertIn("kVkPipelineCacheSaveIntervalMs = 20000", maybe)
        self.assertIn(
            "now - vk_pipeline_cache_last_save_ms_ >= kVkPipelineCacheSaveIntervalMs",
            maybe,
        )
        self.assertIn("SaveVkPipelineCache();", maybe)

    def test_async_preload_defers_synchronous_cache_serialization(self):
        start = self.text.index("void VulkanPipelineCache::MaybeSaveVkPipelineCache()")
        end = self.text.index("void VulkanPipelineCache::EndSubmission()")
        maybe = self.text[start:end]
        guard = maybe.index("if (startup_loading_)")
        dirty = maybe.index("vk_pipeline_cache_dirty_.load")
        save = maybe.index("SaveVkPipelineCache();")
        self.assertLess(guard, dirty)
        self.assertLess(dirty, save)
        self.assertRegex(
            maybe[guard:dirty],
            r"if \\(startup_loading_\\) \\{\\s*return;\\s*\\}",
        )

    def test_shutdown_clears_generation_for_next_title(self):
        start = self.text.index("void VulkanPipelineCache::ShutdownShaderStorage()")
        shutdown = self.text[start:]
        save = shutdown.index("SaveVkPipelineCache();")
        clear_dirty = shutdown.index(
            "vk_pipeline_cache_dirty_.store(false, std::memory_order_relaxed);"
        )
        clear_timer = shutdown.index("vk_pipeline_cache_last_save_ms_ = 0;")
        self.assertLess(save, clear_dirty)
        self.assertLess(clear_dirty, clear_timer)


if __name__ == "__main__":
    unittest.main()
