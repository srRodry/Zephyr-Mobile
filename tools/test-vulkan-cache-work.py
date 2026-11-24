#!/usr/bin/env python3
"""Compile production cache paths against instrumented shader/driver stubs."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / 'emulator-core/src/main/cpp/xenia/src/xenia/gpu/vulkan/vulkan_pipeline_cache.cc').read_text()
loaders = source[source.index('VulkanShader* VulkanPipelineCache::LoadShader('):source.index('SpirvShaderTranslator::Modification\nVulkanPipelineCache::GetCurrentVertexShaderModification')]
save = source[source.index('bool VulkanPipelineCache::SaveVkPipelineCache()'):source.index('void VulkanPipelineCache::ShutdownShaderStorage()')]
reader = (root / 'emulator-core/src/main/cpp/xenia/src/xenia/gpu/shader_storage.h').read_text()
assert reader.index('shader_header.ucode_data_hash != ucode_data_hash') < reader.index('if (!callback(shader_header.type')
assert 'LoadShaderWithHash(type, ucode_dwords, ucode_dword_count,' in source
code = r'''
#include <cassert>
#include <cstdint>
#include <cstring>
#include <filesystem>
#include <memory>
#include <string>
#include <unordered_map>
#define XELOGI(...) ((void)0)
#define XELOGE(...) ((void)0)
#define XELOGW(...) ((void)0)
using VkDevice = int;
using VkResult = int;
constexpr int VK_NULL_HANDLE=0, VK_SUCCESS=0, VK_INCOMPLETE=1;
namespace xenos { enum class ShaderType { vertex, pixel }; }
int hashes = 0, allocations = 0;
uint64_t XXH3_64bits(const void* data, size_t size) {
 ++hashes; uint64_t hash=0;
 for (size_t i=0; i<size; ++i) hash=hash*31+static_cast<const uint8_t*>(data)[i];
 return hash;
}
namespace ui::vulkan {
struct VulkanDevice {
 struct Functions {
  mutable int calls=0;
  int mode=0;
  VkResult vkGetPipelineCacheData(int, int, size_t* size, void* data) const {
   ++calls;
   if (!data) { *size=mode==2 ? 0 : mode==3 ? 9 : 8; return VK_SUCCESS; }
   std::memset(data, 0xAB, 4); *size=4;
   return mode==1 ? VK_INCOMPLETE : VK_SUCCESS;
  }
 } fn;
 const Functions& functions() const { return fn; }
 VkDevice device() const { return 1; }
};
}
struct Command {
 ui::vulkan::VulkanDevice device;
 const ui::vulkan::VulkanDevice* GetVulkanDevice() const { return &device; }
};
struct VulkanShader {
 uint64_t hash;
 VulkanShader(const ui::vulkan::VulkanDevice*, xenos::ShaderType, uint64_t h, const uint32_t*, uint32_t):hash(h) { ++allocations; }
};
struct Store {
 int saves=0; size_t saved_size=0;
 bool CanStorePayload(size_t size) const { return size <= 8; }
 bool Save(const uint8_t* data, size_t size, std::string&) {
  ++saves; saved_size=size;
  assert(size==4); for(size_t i=0;i<size;++i) assert(data[i]==0xAB);
  return true;
 }
};
class VulkanPipelineCache {
public:
 Command command_processor_;
 std::unordered_map<uint64_t,VulkanShader*> shaders_;
 int vk_pipeline_cache_=1;
 std::filesystem::path vk_pipeline_cache_path_="test.cache";
 std::unique_ptr<Store> vk_pipeline_cache_store_=std::make_unique<Store>();
 ~VulkanPipelineCache() { for(auto [hash,shader]:shaders_) delete shader; }
 VulkanShader* LoadShader(xenos::ShaderType, const uint32_t*, uint32_t);
 VulkanShader* LoadShaderWithHash(xenos::ShaderType, const uint32_t*, uint32_t,uint64_t);
 bool SaveVkPipelineCache();
};
'''
code += loaders + save
code += r'''
int main() {
 VulkanPipelineCache cache;
 const uint32_t ucode[]={1,2,3};
 const uint64_t validated=XXH3_64bits(ucode,sizeof(ucode));
 hashes=0;
 auto* stored=cache.LoadShaderWithHash(xenos::ShaderType::vertex,ucode,3,validated);
 assert(hashes==0 && allocations==1 && stored->hash==validated);
 assert(cache.LoadShaderWithHash(xenos::ShaderType::vertex,ucode,3,validated)==stored);
 assert(hashes==0 && allocations==1);
 assert(cache.LoadShader(xenos::ShaderType::vertex,ucode,3)==stored);
 assert(hashes==1 && allocations==1); // Live guest memory still validated.
 const uint32_t changed[]={4,5,6};
 assert(cache.LoadShader(xenos::ShaderType::vertex,changed,3)!=stored);
 assert(hashes==2 && allocations==2);
 assert(cache.SaveVkPipelineCache());
 assert(cache.vk_pipeline_cache_store_->saves==1 && cache.vk_pipeline_cache_store_->saved_size==4);
 cache.command_processor_.device.fn.mode=1;
 assert(!cache.SaveVkPipelineCache()); // Incomplete driver payload is never persisted.
 assert(cache.vk_pipeline_cache_store_->saves==1);
 cache.command_processor_.device.fn.mode=2;
 assert(!cache.SaveVkPipelineCache());
 assert(cache.vk_pipeline_cache_store_->saves==1);
 cache.command_processor_.device.fn.mode=3;
 const int before_budget=cache.command_processor_.device.fn.calls;
 assert(!cache.SaveVkPipelineCache());
 assert(cache.command_processor_.device.fn.calls==before_budget+1); // Size query only.
 assert(cache.vk_pipeline_cache_store_->saves==1);
 cache.vk_pipeline_cache_=VK_NULL_HANDLE;
 const int calls=cache.command_processor_.device.fn.calls;
 assert(!cache.SaveVkPipelineCache());
 assert(cache.command_processor_.device.fn.calls==calls);
}
'''
with tempfile.TemporaryDirectory(prefix='zephyr-cache-work-') as directory:
    path = Path(directory)
    (path / 'test.cc').write_text(code)
    compiler = os.environ.get('CXX') or shutil.which('clang++') or 'g++'
    subprocess.run([compiler, '-std=c++20', '-Wall', '-Wextra', '-Werror',
                    '-fsanitize=address,undefined', str(path / 'test.cc'), '-o', str(path / 'test')], check=True)
    subprocess.run([str(path / 'test')], check=True)
print('Production cache paths: validated storage hash reused; live ucode still hashed; exact driver size saved; incomplete/empty payload rejected.')
