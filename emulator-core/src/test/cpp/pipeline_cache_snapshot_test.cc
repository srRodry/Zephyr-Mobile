#include <cassert>
#include <cstring>
#include <iostream>
#include "xenia/gpu/vulkan/vulkan_pipeline_cache_snapshot.h"

using xe::gpu::vulkan::ReadPipelineCacheForSave;

// Compile the production helper against the actual dispatch member inventory.
// This catches API calls absent from the runtime's loader table.
struct RuntimeDispatch {
#define XE_UI_VULKAN_FUNCTION(name) PFN_##name name = nullptr;
#include "xenia/ui/vulkan/functions/device_1_0.inc"
#undef XE_UI_VULKAN_FUNCTION
};
const auto runtime_extractor = &ReadPipelineCacheForSave<RuntimeDispatch>;

struct Driver {
  VkDevice device = reinterpret_cast<VkDevice>(1);
  VkPipelineCache live = reinterpret_cast<VkPipelineCache>(2);
  VkPipelineCache copy = reinterpret_cast<VkPipelineCache>(3);
  mutable unsigned creates = 0, merges = 0, destroys = 0, reads = 0;
  mutable unsigned live_reads = 0, copy_reads = 0;
  mutable size_t live_bytes = 64, copy_bytes = 0;
  int failure = 0;
  bool grow_on_read = false;
  bool shrink_on_read = false;
  VkResult vkCreatePipelineCache(VkDevice d, const VkPipelineCacheCreateInfo* info,
                                 const VkAllocationCallbacks*, VkPipelineCache* out) const {
    assert(d == device && info->sType == VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO);
    assert(!info->flags && !info->initialDataSize && !info->pInitialData);
    ++creates;
    if (failure == 1) return VK_ERROR_OUT_OF_HOST_MEMORY;
    *out = copy;
    return VK_SUCCESS;
  }
  VkResult vkMergePipelineCaches(VkDevice d, VkPipelineCache dst, uint32_t n,
                                 const VkPipelineCache* src) const {
    assert(d == device && dst == copy && n == 1 && *src == live);
    ++merges;
    if (failure == 2) return VK_ERROR_OUT_OF_DEVICE_MEMORY;
    copy_bytes = live_bytes;
    return VK_SUCCESS;
  }
  void vkDestroyPipelineCache(VkDevice d, VkPipelineCache c,
                              const VkAllocationCallbacks*) const {
    assert(d == device && c == copy); // never destroy the live cache
    ++destroys;
  }
  VkResult vkGetPipelineCacheData(VkDevice d, VkPipelineCache c, size_t* n, void* out) const {
    assert(d == device && (c == live || c == copy));
    ++reads;
    c == live ? ++live_reads : ++copy_reads;
    if (!out) {
      if (failure == 3) return VK_ERROR_UNKNOWN;
      *n = failure == 4 ? 0 : failure == 5 ? 4096 : c == copy ? copy_bytes : live_bytes;
      // Simulate compilation racing between the size query and extraction.
      if (grow_on_read) live_bytes += 32;
      return VK_SUCCESS;
    }
    if (failure == 6) return VK_INCOMPLETE;
    if (failure == 7) return VK_ERROR_UNKNOWN;
    if (failure == 8) { ++*n; return VK_SUCCESS; } // broken driver length
    if (failure == 9) { *n = 0; return VK_SUCCESS; }
    const size_t available = c == copy ? copy_bytes : live_bytes;
    if (*n < available) return VK_INCOMPLETE;
    *n = shrink_on_read ? available / 2 : available;
    std::memset(out, 0x5a, *n);
    return VK_SUCCESS;
  }
};

int main() {
  for (bool snapshot : {false, true}) {
    Driver d;
    std::vector<uint8_t> out{0xff};
    assert(ReadPipelineCacheForSave(d, d.device, d.live, snapshot, 128, out));
    assert(out == std::vector<uint8_t>(64, 0x5a));
    assert(d.creates == unsigned(snapshot) && d.merges == unsigned(snapshot));
    assert(d.destroys == unsigned(snapshot));
    assert(d.live_reads == (snapshot ? 0u : 2u));
    assert(d.copy_reads == (snapshot ? 2u : 0u));
    for (int failure = 1; failure <= 9; ++failure) {
      if (!snapshot && failure <= 2) continue;
      Driver f;
      f.failure = failure;
      out = {0xff};
      assert(!ReadPipelineCacheForSave(f, f.device, f.live, snapshot, 128, out));
      assert(out.empty());
      assert(f.destroys == unsigned(snapshot && failure != 1));
      if (failure <= 2) assert(!f.reads);
      if (snapshot) assert(!f.live_reads); // no hidden fallback stall on failure
      if (failure == 5) assert(f.reads == 1); // bound before blob allocation
    }
    Driver race;
    race.grow_on_read = true;
    assert(ReadPipelineCacheForSave(race, race.device, race.live, snapshot, 128, out) == snapshot);
    assert(snapshot ? out.size() == 64 : out.empty());
    Driver shrink;
    shrink.shrink_on_read = true;
    assert(ReadPipelineCacheForSave(shrink, shrink.device, shrink.live, snapshot, 128, out));
    assert(out.size() == 32);
    Driver boundary;
    assert(ReadPipelineCacheForSave(boundary, boundary.device, boundary.live, snapshot, 64, out));
    assert(!ReadPipelineCacheForSave(boundary, boundary.device, boundary.live, snapshot, 63, out));
    assert(out.empty());
  }
  Driver invalid;
  std::vector<uint8_t> out{0xff};
  assert(!ReadPipelineCacheForSave(invalid, invalid.device, VK_NULL_HANDLE, true, 128, out));
  assert(out.empty() && !invalid.creates);
  assert(!ReadPipelineCacheForSave(invalid, invalid.device, invalid.live, true, 0, out));
  assert(!invalid.creates);
  std::cout << "pipeline-cache snapshot: success, races, bounds and all failure paths passed\n";
}
