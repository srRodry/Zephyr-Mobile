#!/usr/bin/env python3
"""Exercise the Vulkan draw sampler loop with controlled bindings and failures."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (
    root / "emulator-core/src/main/cpp/xenia/src/xenia/gpu/vulkan"
    / "vulkan_command_processor.cc"
).read_text()
start = source.index("    uint32_t samplers_overflowed_count = 0;")
end = source.index("  // Set up the render targets", start)
sampler_loop = source[start:end]
visit = "for (size_t k = 0; k < shader_sampler_bindings.size(); ++k) {"
assert sampler_loop.count(visit) == 1
sampler_loop = sampler_loop.replace(visit, visit + "\n          ++visited;")

harness = r"""#include <cassert>
#include <cstddef>
#include <cstdint>
#include <utility>
#include <vector>
#define assert_zero(n) assert((n) == 0)
#define assert_true(n) assert(n)
using VkSampler = uint64_t;
constexpr VkSampler VK_NULL_HANDLE = 0;
namespace cvars { bool vulkan_cache_sampler_parameters = true; }

struct VulkanShader {
  struct SamplerBinding {
    uint32_t fetch_constant;
  };
  std::vector<SamplerBinding> bindings{{1}, {7}, {8}};
  bool ready = true;
  bool bindings_ready() const { return ready; }
  const std::vector<SamplerBinding>& GetSamplerBindingsAfterTranslation() const {
    return bindings;
  }
};

struct VulkanTextureCache {
  using SamplerParameters = uint32_t;
  int derives = 0;
  int uses = 0;
  bool fail = false;
  bool overflow = false;
  uint32_t version = 0;
  uint64_t generation = 0;

  auto sampler_destroy_generation() const { return generation; }
  auto GetSamplerParameters(VulkanShader::SamplerBinding binding) {
    ++derives;
    return version + binding.fetch_constant;
  }
  VkSampler UseSampler(SamplerParameters p, bool& overflowed) {
    ++uses;
    overflowed = overflow;
    return fail || overflowed ? VK_NULL_HANDLE : p;
  }
  uint64_t GetSubmissionToAwaitOnSamplerOverflow(uint32_t count) {
    assert(count == 3);
    return 1;
  }
};

struct Processor {
  VulkanTextureCache cache;
  VulkanTextureCache* texture_cache_ = &cache;
  uint64_t submission = 1;
  uint32_t current_samplers_fetch_mask_vertex_ = 0;
  uint32_t current_samplers_fetch_mask_pixel_ = 0;
  uint32_t current_samplers_fetch_up_to_date_vertex_ = 0;
  uint32_t current_samplers_fetch_up_to_date_pixel_ = 0;
  uint64_t current_samplers_submission_vertex_ = 0;
  uint64_t current_samplers_submission_pixel_ = 0;
  uint64_t current_samplers_destroy_generation_ = ~uint64_t(0);
  const VulkanShader* current_samplers_shader_vertex_ = nullptr;
  const VulkanShader* current_samplers_shader_pixel_ = nullptr;
  std::vector<std::pair<uint32_t, VkSampler>> current_samplers_vertex_;
  std::vector<std::pair<uint32_t, VkSampler>> current_samplers_pixel_;
  int visited = 0;

  uint64_t GetCurrentSubmission() const { return submission; }
  void CheckSubmissionCompletionAndDeviceLoss(uint64_t) {
    ++submission;
    cache.overflow = false;
  }
  bool Draw(const VulkanShader* vertex_shader,
            const VulkanShader* pixel_shader = nullptr) {
    bool stage_bindings_ready[2] = {};
    for (int i = 0; i < 2; ++i) {
#include "production.inc"
    return true;
  }
};

int main() {
  VulkanShader shader;
  Processor p;
  assert(p.Draw(&shader));
  assert(p.cache.derives == 3 && p.cache.uses == 3 && p.visited == 0);
  for (int draw = 0; draw < 1000; ++draw) {
    p.current_samplers_fetch_up_to_date_vertex_ &= ~(1u << 23);
    assert(p.Draw(&shader));
  }
  assert(p.cache.derives == 3 && p.cache.uses == 3 && p.visited == 0);
  p.current_samplers_fetch_up_to_date_vertex_ &= ~(1u << 7);
  ++p.cache.version;
  assert(p.Draw(&shader));
  assert(p.cache.derives == 4 && p.cache.uses == 4 && p.visited == 3);
  ++p.submission;
  assert(p.Draw(&shader));
  assert(p.cache.derives == 4 && p.cache.uses == 7 && p.visited == 6);
  ++p.cache.generation;
  assert(p.Draw(&shader));
  assert(p.cache.derives == 7 && p.cache.uses == 10 && p.visited == 6);
  p.current_samplers_fetch_up_to_date_vertex_ &= ~(1u << 1);
  ++p.cache.version;
  p.cache.fail = true;
  assert(!p.Draw(&shader));
  assert(p.current_samplers_submission_vertex_ == 0);
  assert(p.current_samplers_vertex_[0].second == VK_NULL_HANDLE);
  p.cache.fail = false;
  assert(p.Draw(&shader));
  assert(p.current_samplers_submission_vertex_ == p.submission);
  assert(p.current_samplers_vertex_[0].second != VK_NULL_HANDLE);
  Processor retry;
  retry.cache.overflow = true;
  assert(retry.Draw(&shader));
  assert(retry.submission == 2);
  assert(retry.current_samplers_submission_vertex_ == retry.submission);
  assert(retry.cache.uses == 6);
  VulkanShader pixel;
  pixel.bindings = {{6}, {10}};
  Processor stages;
  assert(stages.Draw(&shader, &pixel));
  assert(stages.cache.derives == 5 && stages.cache.uses == 5);
  stages.current_samplers_fetch_up_to_date_vertex_ &= ~(1u << 6);
  stages.current_samplers_fetch_up_to_date_pixel_ &= ~(1u << 6);
  ++stages.cache.version;
  assert(stages.Draw(&shader, &pixel));
  assert(stages.cache.derives == 6 && stages.cache.uses == 6);
  assert(stages.visited == 2);

  cvars::vulkan_cache_sampler_parameters = false;
  assert(p.Draw(&shader) && p.current_samplers_submission_vertex_ == 0);
  cvars::vulkan_cache_sampler_parameters = true;
  const int uses = p.cache.uses;
  assert(p.Draw(&shader) && p.cache.uses == uses + 3);
}
"""

with tempfile.TemporaryDirectory(prefix="zephyr-sampler-reuse-") as directory:
    path = Path(directory)
    (path / "production.inc").write_text(sampler_loop)
    (path / "test.cc").write_text(harness)
    compiler = os.environ.get("CXX") or shutil.which("clang++") or "g++"
    subprocess.run(
        [compiler, "-std=c++20", "-Wall", "-Wextra", "-Werror",
         "-fsanitize=address,undefined", str(path / "test.cc"),
         "-o", str(path / "test")],
        check=True,
    )
    subprocess.run([str(path / "test")], check=True)

print("Vulkan sampler reuse: clean stages skipped; dirty fetches, submission"
      " changes, evictions, failed updates, and overflow retries revalidated.")
