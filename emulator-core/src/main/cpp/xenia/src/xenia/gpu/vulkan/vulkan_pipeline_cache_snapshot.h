/**
 * Zephyr: serialize a private Vulkan cache rather than the live compiler cache.
 * Released under the BSD license - see LICENSE in the root for more details.
 */
#ifndef XENIA_GPU_VULKAN_VULKAN_PIPELINE_CACHE_SNAPSHOT_H_
#define XENIA_GPU_VULKAN_VULKAN_PIPELINE_CACHE_SNAPSHOT_H_

#include <cstddef>
#include <cstdint>
#include <vector>
#include "third_party/Vulkan-Headers/include/vulkan/vulkan_core.h"

namespace xe::gpu::vulkan {

// The caller owns the live cache for this entire operation. Its default Vulkan
// synchronization permits concurrent pipeline creation and source-cache reads.
// Only this thread accesses the merge destination; no external lock is needed.
// A private copy shortens contention in drivers that lock their live cache while
// serializing. Merge cost and memory use are driver-dependent, not guaranteed wins.
// Failure does not fall back to a synchronous read of the live cache: the caller
// keeps the dirty bit and retries on the existing save schedule.
template <typename Functions>
bool ReadPipelineCacheForSave(const Functions& fn, VkDevice device,
                              VkPipelineCache live, bool use_snapshot,
                              size_t max_bytes, std::vector<uint8_t>& output) {
  output.clear();
  if (live == VK_NULL_HANDLE || !max_bytes) return false;
  VkPipelineCache copy = VK_NULL_HANDLE;
  if (use_snapshot) {
    VkPipelineCacheCreateInfo info{};
    info.sType = VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO;
    if (fn.vkCreatePipelineCache(device, &info, nullptr, &copy) != VK_SUCCESS) {
      return false;
    }
  }
  // All returns, including merge/read failures, release the private cache.
  struct Cleanup {
    const Functions& fn;
    VkDevice device;
    VkPipelineCache cache;
    ~Cleanup() {
      if (cache != VK_NULL_HANDLE) {
        fn.vkDestroyPipelineCache(device, cache, nullptr);
      }
    }
  } cleanup{fn, device, copy};
  if (use_snapshot &&
      fn.vkMergePipelineCaches(device, copy, 1, &live) != VK_SUCCESS) {
    return false;
  }
  const VkPipelineCache source = use_snapshot ? copy : live;
  size_t bytes = 0;
  if (fn.vkGetPipelineCacheData(device, source, &bytes, nullptr) != VK_SUCCESS ||
      !bytes || bytes > max_bytes) {
    return false;
  }
  std::vector<uint8_t> data(bytes);
  // On the baseline path concurrent compiles may grow the live cache between
  // calls. VK_INCOMPLETE is never persisted. Also validate the returned length
  // before exposing it to the file store, even for a faulty driver.
  if (fn.vkGetPipelineCacheData(device, source, &bytes, data.data()) != VK_SUCCESS ||
      !bytes || bytes > data.size()) {
    return false;
  }
  data.resize(bytes);
  output.swap(data);
  return true;
}

}  // namespace xe::gpu::vulkan
#endif  // XENIA_GPU_VULKAN_VULKAN_PIPELINE_CACHE_SNAPSHOT_H_
