// The Android audio drivers' per-block processing, free of AAudio and OpenSL ES so the host
// tests drive it with a real signal: a guest block becomes host stereo (the 5.1 fold of the
// game's frames, or the media player's own stereo), its gain is applied and bounded, a block
// that did not arrive in time is concealed without a step, and the output is resampled at the
// rate control's rate. Callback thread only: no allocation, no lock, no wait.
#pragma once
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <vector>

#include "xenia/apu/conversion.h"

namespace xe {
namespace apu {

class AudioBlockRenderer {
 public:
  // The first frames of a block after a concealed gap ramp in from silence (~1.3 ms at 48 kHz).
  static constexpr uint32_t kFadeInFrames = 64;

  // frames: per guest block. guest_channels: 6 for the game's frames (sequential big endian
  // 5.1), 2 for the media player's (interleaved host endian stereo).
  AudioBlockRenderer(uint32_t frames, uint32_t guest_channels)
      : frames_(frames),
        guest_channels_(guest_channels),
        block_(size_t(frames) * 2, 0.0f),
        conceal_fade_(frames),
        pos_(frames) {
    // Build the raised-cosine window before starting the audio callback.
    const float step = frames_ > 1 ? 3.14159265f / float(frames_ - 1) : 0.0f;
    for (uint32_t f = 0; f < frames_; ++f) {
      conceal_fade_[f] = 0.5f + 0.5f * std::cos(step * float(f));
    }
  }

  struct Result {
    uint32_t blocks = 0;   // guest blocks taken in
    uint32_t gaps = 0;     // blocks concealed because none was queued
    uint32_t clipped = 0;  // samples bounded to full scale
  };

  // `count` stereo frames into `out`, consuming `rate` source frames per output frame (1 = as
  // they are). next_block() returns the next guest block, or nullptr when none is queued;
  // done(block) takes a block back once it is converted. `gain` scales the samples, which are
  // then bounded to [-1, 1].
  template <typename Next, typename Done>
  Result Render(float* out, int32_t count, float rate, float gain, Next&& next_block,
                Done&& done) {
    Result result;
    for (int32_t i = 0; i < count; ++i) {
      while (frac_ >= 1.0f) {
        frac_ -= 1.0f;
        if (pos_ >= frames_) {
          Load(next_block, done, gain, result);
        }
        prev_l_ = cur_l_;
        prev_r_ = cur_r_;
        cur_l_ = block_[size_t(pos_) * 2];
        cur_r_ = block_[size_t(pos_) * 2 + 1];
        ++pos_;
      }
      out[size_t(i) * 2] = prev_l_ + frac_ * (cur_l_ - prev_l_);
      out[size_t(i) * 2 + 1] = prev_r_ + frac_ * (cur_r_ - prev_r_);
      frac_ += rate;
    }
    return result;
  }

 private:
  template <typename Next, typename Done>
  void Load(Next& next_block, Done& done, float gain, Result& result) {
    const float* block = next_block();
    pos_ = 0;
    if (!block) {
      ++result.gaps;
      Conceal();
      return;
    }
    if (guest_channels_ == 6) {
      conversion::sequential_6_BE_to_interleaved_2_LE(block_.data(), block, frames_);
    } else {
      std::memcpy(block_.data(), block, block_.size() * sizeof(float));
    }
    done(block);
    ++result.blocks;
    result.clipped += GainAndClamp(gain);
    if (fade_in_) {
      FadeIn();
      fade_in_ = false;
    }
    continues_ = true;
  }

  // The 5.1 fold peaks at ~2.9x a channel, so loud content can pass full scale. Bounded here
  // so the result is the same on every device, and counted: the fix for persistent clipping is
  // less gain, not a harder limit. A NaN from the guest passes both bounds: it is silenced (and
  // counted) instead of reaching the device and the next gap's concealment.
  uint32_t GainAndClamp(float gain) {
    uint32_t clipped = 0;
    for (float& sample : block_) {
      float s = sample * gain;
      if (s > 1.0f) {
        s = 1.0f;
        ++clipped;
      } else if (s < -1.0f) {
        s = -1.0f;
        ++clipped;
      } else if (std::isnan(s)) {
        s = 0.0f;
        ++clipped;
      }
      sample = s;
    }
    return clipped;
  }

  // A block that did not arrive. The first one after real audio continues the last block
  // mirrored in time - from its last frame, so there is no step where it starts - and fades
  // it to silence across the block (a raised cosine); the next missing ones are silence, and
  // the next real block ramps in from silence. Repeating the last block as it was put a step
  // where its end met its start again, at every repeat, and another where the repeat (decayed,
  // not silent) met the next block's ramp from zero: clicks on every underrun.
  void Conceal() {
    fade_in_ = true;
    if (!continues_) {
      std::fill(block_.begin(), block_.end(), 0.0f);
      return;
    }
    continues_ = false;
    for (uint32_t f = 0, g = frames_ - 1; f < g; ++f, --g) {
      std::swap(block_[size_t(f) * 2], block_[size_t(g) * 2]);
      std::swap(block_[size_t(f) * 2 + 1], block_[size_t(g) * 2 + 1]);
    }
    for (uint32_t f = 0; f < frames_; ++f) {
      const float fade = conceal_fade_[f];
      block_[size_t(f) * 2] *= fade;
      block_[size_t(f) * 2 + 1] *= fade;
    }
  }

  void FadeIn() {
    const uint32_t ramp = std::min(kFadeInFrames, frames_);
    for (uint32_t f = 0; f < ramp; ++f) {
      const float g = float(f) / float(ramp);
      block_[size_t(f) * 2] *= g;
      block_[size_t(f) * 2 + 1] *= g;
    }
  }

  const uint32_t frames_;
  const uint32_t guest_channels_;
  // The block being played, host stereo after gain; pos_ = its frames already consumed.
  std::vector<float> block_;
  std::vector<float> conceal_fade_;
  uint32_t pos_;
  // The last block was real audio, which a gap can continue (mirrored) from.
  bool continues_ = false;
  // A gap was concealed: the next real block ramps in.
  bool fade_in_ = false;
  // Linear resampler state.
  float frac_ = 0.0f;
  float prev_l_ = 0.0f, prev_r_ = 0.0f;
  float cur_l_ = 0.0f, cur_r_ = 0.0f;
};

}  // namespace apu
}  // namespace xe
