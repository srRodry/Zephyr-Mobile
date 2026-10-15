#!/usr/bin/env python3
"""Guard Android AAudio hot-path choices that protect realtime playback."""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
CPP = (ROOT / "emulator-core/src/main/cpp/xe_aaudio_audio_driver.cpp").read_text()
HEADER = (ROOT / "emulator-core/src/main/cpp/xe_aaudio_audio_driver.h").read_text()


def function(text: str, signature: str, next_signature: str) -> str:
    start = text.index(signature)
    end = text.index(next_signature, start)
    return text[start:end]


class AAudioHotPathTests(unittest.TestCase):
    def test_stream_declares_game_usage_and_low_latency(self):
        self.assertIn(
            "AAudioStreamBuilder_setPerformanceMode(builder_,\n"
            "                                           AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);",
            CPP,
        )
        self.assertIn(
            "AAudioStreamBuilder_setUsage(builder_, AAUDIO_USAGE_GAME);", CPP
        )

    def test_queue_depth_is_lock_free_for_pacing(self):
        queued = function(
            CPP,
            "size_t AAudioAudioDriver::GetQueuedFrameCount()",
            "void AAudioAudioDriver::SetVolume",
        )
        self.assertIn("queued_frame_count_.load(std::memory_order_relaxed)", queued)
        self.assertNotIn("unique_lock", queued)

        callback = function(
            CPP,
            "aaudio_data_callback_result_t AAudioAudioDriver::AudioCallback(",
            "const float* AAudioAudioDriver::NextGuestBlock()",
        )
        self.assertIn("queued_frame_count_.load(std::memory_order_relaxed)", callback)
        self.assertNotIn("frames_queued_.size()", callback)

    def test_ready_and_recycle_queues_have_independent_locks(self):
        self.assertIn("frames_queued_mutex_", HEADER)
        self.assertIn("frames_unused_mutex_", HEADER)
        self.assertNotIn("frames_mutex_", HEADER)

        next_block = function(
            CPP,
            "const float* AAudioAudioDriver::NextGuestBlock()",
            "void AAudioAudioDriver::ReturnGuestBlock",
        )
        recycle = function(
            CPP,
            "void AAudioAudioDriver::ReturnGuestBlock",
            "void AAudioAudioDriver::RecordQueueLockWait",
        )
        self.assertIn("frames_queued_mutex_", next_block)
        self.assertNotIn("frames_unused_mutex_", next_block)
        self.assertIn("frames_unused_mutex_", recycle)
        self.assertNotIn("frames_queued_mutex_", recycle)

    def test_detailed_callback_timing_is_opt_in(self):
        self.assertIn("DEFINE_bool(apu_aaudio_log_stats, false,", CPP)
        callback = function(
            CPP,
            "aaudio_data_callback_result_t AAudioAudioDriver::AudioCallback(",
            "const float* AAudioAudioDriver::NextGuestBlock()",
        )
        self.assertIn("const bool detailed_stats = cvars::apu_aaudio_log_stats;", callback)
        self.assertIn("if (detailed_stats) {", callback)


if __name__ == "__main__":
    unittest.main()
