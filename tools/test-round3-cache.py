#!/usr/bin/env python3
"""Compile production cache/VFS snippets with host fakes, ASan and UBSan.

Does not execute a Vulkan driver or mount a real disc image. Tests the exact
lookup, memo, deduplication and invalidation paths; ARM64 CI compiles real files.
"""
from pathlib import Path
import os, re, shutil, subprocess, tempfile
R=Path(__file__).resolve().parents[1];S=R/'emulator-core/src/main/cpp/xenia/src/xenia'
def function(s, marker):
 a=s.index(marker);b=s.index('{',a);depth=1;i=b+1
 while depth:
  depth+=(s[i]=='{')-(s[i]=='}');i+=1
 return s[a:i]
s=(S/'gpu/texture_cache.cc').read_text();h=(S/'gpu/texture_cache.h').read_text()
helpers='\n'.join(function(s,m) for m in ['uint32_t TextureCache::GetTextureFindMemoIndex','void TextureCache::ForgetTextureFindMemo','void TextureCache::DestroyAllTextures','bool TextureCache::RecreateTextureForHostChange'])
f=function(s,'TextureCache::Texture* TextureCache::FindOrCreateTexture')
f='TextureCache::Texture* TextureCache::FindOrCreateTexture(TextureKey key) {\n'+f[f.index('  // Try to find an existing texture.'):]
a=h.index('  struct TextureFindMemoEntry');b=h.index('  uint64_t textures_total_host_memory_usage_',a)
fields=h[a:b]
a=s.index('  auto queue_texture_to_load');b=s.index('  while (xe::bit_scan_forward',a);dedup=s[a:b]
v=(S/'gpu/vulkan/vulkan_texture_cache.cc').read_text();v=function(v,'VkImageView VulkanTextureCache::VulkanTexture::GetView')
a=v.index('  // The arguments as passed');b=v.index('  xenos::DataDimension',a);view_begin=v[a:b]
a=v.index('    if (use_view_memo)',v.index('auto it = views_.find'));b=v.index('    return it->second;',a);view_hit=v[a:b]
a=v.index('  if (use_view_memo)',v.index('views_.emplace'));b=v.index('  return view;',a);view_store=v[a:b]
z=function((S/'vfs/devices/disc_zarchive_device.cc').read_text(),'Entry* DiscZarchiveDevice::ResolvePath')
# All actual erase routes must invalidate, even when the memo is disabled.
for begin in [s.index('textures_.erase(found_texture_it)'),s.index('textures_.erase(it)')]:
 assert 'ForgetTextureFindMemo(texture)' in s[max(0,begin-350):begin]
code=r'''
#include <array>
#include <cassert>
#include <cstdint>
#include <cstring>
#include <memory>
#include <unordered_map>
#include <string_view>
#include <cstdio>
#define assert_true(x) assert(x)
#define COUNT_profile_set(...) ((void)0)
#define XELOGFS(...) ((void)0)
namespace cvars { bool texture_lookup_memo=true; }
static size_t hash_calls=0;
struct TextureKey {
 uint64_t a=0,b=0;bool operator==(const TextureKey&)const=default;
 void LogAction(const char*)const{}
 struct Hasher { size_t operator()(const TextureKey& k)const{++hash_calls;return k.a^(k.b*17);} };
};
struct TextureCache {
 struct Texture { TextureKey k; uint64_t used=0; const TextureKey& key()const{return k;}
 uint64_t last_usage_submission_index()const{return used;} void LogAction(const char*)const{} };
 std::unordered_map<TextureKey,std::unique_ptr<Texture>,TextureKey::Hasher> textures_;
 FIELDS
 bool fail=false;
 std::unique_ptr<Texture> CreateTexture(TextureKey k){if(fail)return {};return std::make_unique<Texture>(Texture{k});}
 void ResetTextureBindings(bool=false){}
 Texture* FindOrCreateTexture(TextureKey);
 void DestroyAllTextures(bool);
 bool RecreateTextureForHostChange(const TextureKey&,uint64_t);
};
HELPERS
FIND
using VkImageView=uint64_t;
constexpr VkImageView VK_NULL_HANDLE=0;
struct Views {
 std::unordered_map<uint32_t,VkImageView> views_;VkImageView last_view_=0;uint32_t last_view_arguments_=0;
 bool fail=false;size_t lookups=0;
 VkImageView GetView(bool is_signed,uint32_t host_swizzle,bool is_array){
 VIEW_BEGIN
 // Host fake: keep each argument combination distinct, inject creation failures.
 ++lookups;uint32_t view_key=view_arguments;
 auto it=views_.find(view_key);if(it!=views_.end()){
 VIEW_HIT
 return it->second;}
 if(fail)return VK_NULL_HANDLE;
 VkImageView view=views_.size()+1;views_.emplace(view_key,view);
 VIEW_STORE
 return view;
 }
};
struct Entry { int requests=0;std::string_view last;Entry* ResolvePath(std::string_view p){++requests;last=p;return p=="missing"?nullptr:this;} };
struct Reader{};
struct DiscZarchiveDevice {Reader* reader_=nullptr;std::unique_ptr<Entry> root_entry_=std::make_unique<Entry>();Entry* ResolvePath(std::string_view);};
ZAR
static void duplicates(){
 using Texture=TextureCache::Texture;
 TextureCache::Texture objects[64];TextureCache::Texture* textures_to_load[64];uint32_t num_textures_to_load=0;
 DEDUP
 for(auto& object:objects){queue_texture_to_load(&object);queue_texture_to_load(&object);}
 assert(num_textures_to_load==64);for(int i=0;i<64;i++)assert(textures_to_load[i]==&objects[i]);
}
int main(){
 TextureCache cache;TextureKey a{12,45},b{13,45};
 auto first=cache.FindOrCreateTexture(a);auto before=hash_calls;
 for(int i=0;i<10000;i++)assert(cache.FindOrCreateTexture(a)==first);
 assert(hash_calls==before); // Hot memo must bypass the map, not merely preserve its result.
 while(TextureCache::GetTextureFindMemoIndex(a)!=TextureCache::GetTextureFindMemoIndex(b))++b.a;
 auto other=cache.FindOrCreateTexture(b);assert(other!=first);assert(cache.FindOrCreateTexture(a)==first);
 cache.ForgetTextureFindMemo(other); // A colliding eviction must not evict a different memo pointer.
 before=hash_calls;assert(cache.FindOrCreateTexture(a)==first);assert(hash_calls==before);
 first->used=20;assert(!cache.RecreateTextureForHostChange(a,19));assert(cache.FindOrCreateTexture(a)==first);
 cvars::texture_lookup_memo=false;assert(cache.RecreateTextureForHostChange(a,20));
 cvars::texture_lookup_memo=true;assert(cache.FindOrCreateTexture(a)); // ASan catches stale pointers if invalidation is missing.
 cache.DestroyAllTextures(false);assert(cache.textures_.empty());cache.fail=true;
 assert(cache.FindOrCreateTexture(a)==nullptr);assert(cache.FindOrCreateTexture(a)==nullptr);cache.fail=false;
 for(int turn=0;turn<20000;turn++){
  TextureKey k{uint64_t(turn%79),uint64_t(turn%17)};cvars::texture_lookup_memo=(turn%7)!=0;
  auto p=cache.FindOrCreateTexture(k);assert(p&&p->key()==k);
  if(turn%3==0){cache.ForgetTextureFindMemo(p);cache.textures_.erase(k);}
  if(turn%29==0)cache.DestroyAllTextures(false);
 }
 Views views;cvars::texture_lookup_memo=true;auto view=views.GetView(false,0x688,false);before=views.lookups;
 for(int i=0;i<10000;i++)assert(views.GetView(false,0x688,false)==view);assert(views.lookups==before);
 for(bool sign:{false,true})for(bool array:{false,true})for(uint32_t sw=0;sw<4096;sw++){
  auto one=views.GetView(sign,sw,array);cvars::texture_lookup_memo=false;assert(views.GetView(sign,sw,array)==one);cvars::texture_lookup_memo=true;
 }
 views.fail=true;assert(views.GetView(false,0x1000,false)==0);assert(views.GetView(false,0x1000,false)==0);
 duplicates();DiscZarchiveDevice disc;assert(!disc.ResolvePath("a"));assert(disc.root_entry_->requests==0);
 Reader reader;disc.reader_=&reader;assert(disc.ResolvePath("folder\\File.XEX")==disc.root_entry_.get());
 assert(disc.root_entry_->last=="folder\\File.XEX");assert(!disc.ResolvePath("missing"));assert(disc.root_entry_->requests==2);
 std::puts("Cache/VFS: 20,000 mixed lifecycles, collision invalidation, hot-map bypass, 16,384 view combinations, failures, 64-entry dedup and ZAR delegation passed.");
}
'''
for marker,value in [('FIELDS',fields),('HELPERS',helpers),('FIND',f),('VIEW_BEGIN',view_begin),('VIEW_HIT',view_hit),('VIEW_STORE',view_store),('DEDUP',dedup),('ZAR',z)]:code=re.sub(r'(?m)^ *'+marker+r' *$',lambda _:value,code)
with tempfile.TemporaryDirectory(prefix='zephyr-cache-round3-') as t:
 p=Path(t);(p/'test.cc').write_text(code)
 subprocess.run([os.environ.get('CXX') or shutil.which('clang++') or 'c++','-std=c++20','-O1','-g','-fsanitize=address,undefined','-fno-omit-frame-pointer',str(p/'test.cc'),'-o',str(p/'test')],check=True)
 env=os.environ.copy()
 # The local tool runner is ptraced. CI keeps the default leak check.
 if os.environ.get('ZEPHYR_PTRACE_HOST')=='1':env['ASAN_OPTIONS']='detect_leaks=0'
 subprocess.run([str(p/'test')],check=True,env=env)
