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
        end = self.text.index("bool VulkanPipelineCache::SaveVkPipelineCache()")
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
        self.assertIn("return;", maybe[guard:dirty])

    def test_cache_serialization_is_currently_on_submission_path(self):
        """Document the synchronous hot-path dependency before moving saves."""
        start = self.text.index("void VulkanPipelineCache::EndSubmission()")
        end = self.text.index("bool VulkanPipelineCache::IsCreatingPipelines()", start)
        submission = self.text[start:end]
        self.assertIn("MaybeSaveVkPipelineCache();", submission)
        save_start = self.text.index("void VulkanPipelineCache::SaveVkPipelineCache()")
        save_end = self.text.index("void VulkanPipelineCache::ShutdownShaderStorage()", save_start)
        save = self.text[save_start:save_end]
        self.assertIn("vkGetPipelineCacheData(", save)
        self.assertIn("vk_pipeline_cache_store_->Save(", save)

    def test_cache_save_dirty_bit_is_only_cleared_after_success(self):
        """A failed Vulkan cache serialization must remain eligible for retry."""
        start = self.text.index("void VulkanPipelineCache::MaybeSaveVkPipelineCache()")
        end = self.text.index("void VulkanPipelineCache::EndSubmission()", start)
        maybe = self.text[start:end]
        self.assertNotIn(
            "vk_pipeline_cache_dirty_.store(false, std::memory_order_relaxed);",
            maybe,
            "Clearing dirty before SaveVkPipelineCache can lose failed writes",
        )

    def test_failed_save_retries_and_concurrent_dirty_is_preserved(self):
        start = self.text.index("void VulkanPipelineCache::MaybeSaveVkPipelineCache()")
        end = self.text.index("void VulkanPipelineCache::EndSubmission()", start)
        maybe = self.text[start:end]
        self.assertIn(
            "vk_pipeline_cache_dirty_.exchange(false, std::memory_order_acq_rel)",
            maybe,
        )
        self.assertIn("if (!SaveVkPipelineCache())", maybe)
        self.assertIn(
            "vk_pipeline_cache_dirty_.store(true, std::memory_order_release)",
            maybe,
        )
        save_start = self.text.index("bool VulkanPipelineCache::SaveVkPipelineCache()")
        save_end = self.text.index("void VulkanPipelineCache::ShutdownShaderStorage()", save_start)
        save = self.text[save_start:save_end]
        self.assertIn("return true;", save)
        self.assertIn("return false;", save)

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
