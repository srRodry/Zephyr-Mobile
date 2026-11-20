#!/usr/bin/env python3
"""Run production quirk dispatch with a fake cvar backend, preserving user priority."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
base = root / 'emulator-core/src/main/cpp/xenia/src'
config = (base / 'xenia/config.cc').read_text()
load = config.split('uint32_t LoadGameConfigForFile(', 1)[1].split('// Writes a temporary', 1)[0]
assert load.index('xe::game_quirks::Apply(title_id)') < load.index('const auto config = ParseConfig(game_config_path)')
assert load.index('const auto config = ParseConfig(game_config_path)') < load.index('config_var->LoadGameConfigValue(config_key_node.node())')

with tempfile.TemporaryDirectory(prefix='zephyr-army-quirk-') as directory:
    path = Path(directory)
    (path / 'xenia/base').mkdir(parents=True)
    (path / 'xenia/base/cvar.h').write_text(r'''
#pragma once
#include <string>
#include <unordered_map>
#include <type_traits>
namespace toml {
struct node {virtual ~node() = default;};
template<class T> struct value : node {T val; explicit value(T v): val(v) {}};
}
namespace cvar {
struct IConfigVar {virtual ~IConfigVar() = default; virtual void LoadGameConfigValue(const toml::node*) = 0;};
extern std::unordered_map<std::string, IConfigVar*>* ConfigVars;
}
''')
    (path / 'xenia/base/logging.h').write_text('#pragma once\n#define XELOGI(...) ((void)0)\n#define XELOGW(...) ((void)0)\n')
    (path / 'test.cc').write_text(r'''
#include <cassert>
#include "xenia/game_quirks.h"
#include "xenia/base/cvar.h"
namespace cvar {std::unordered_map<std::string, IConfigVar*>* ConfigVars = nullptr;}
template<class T> struct Var : cvar::IConfigVar {
 T current{}; int writes = 0;
 void LoadGameConfigValue(const toml::node* node) override {
   auto typed = dynamic_cast<const toml::value<T>*>(node);
   assert(typed); current = typed->val; ++writes;
 }
};
int main() {
 Var<bool> refresh, exports, fences, skip_shader, skip_pipeline;
 Var<bool> accurate_resolve, destination_packing, integer_fetch, frame_timing, pass_timing;
 Var<bool> selective_readback, neutralize_ssao, unorm8_precision, sanitize_hdr, range_coherency;
 Var<std::string> resolve;
 std::unordered_map<std::string, cvar::IConfigVar*> variables{{"clear_memory_page_state", &refresh}};
 cvar::ConfigVars = &variables;
 assert(xe::game_quirks::Apply(0x454107F8) == 1);
 assert(refresh.current && refresh.writes == 1);
 // Other Army titles, Spider-Man and Forza must not receive this override.
 for (uint32_t title : {0x454108D8u, 0x454109ABu, 0x41560815u, 0x4D5309C9u}) {
   xe::game_quirks::Apply(title); assert(refresh.writes == 1);
 }
 variables.insert({{"memexport_enable", &exports}, {"memexport_await_fences", &fences},
                   {"vulkan_memexport_range_coherency", &range_coherency},
                   {"readback_resolve", &resolve}, {"async_shader_skip_draws", &skip_shader},
                   {"vulkan_async_skip_draws", &skip_pipeline},
                   {"accurate_resolve_number_formats", &accurate_resolve},
                   {"resolve_copy_dest_number_packing", &destination_packing},
                   {"texture_integer_num_format", &integer_fetch},
                   {"vulkan_army_tdc_selective_resolve_readback", &selective_readback},
                   {"vulkan_army_tdc_neutralize_ssao", &neutralize_ssao},
                   {"vulkan_sanitize_host_7e3_exports", &sanitize_hdr},
                   {"spirv_texture_unorm8_precision", &unorm8_precision},
                   {"log_gpu_frame_time_breakdown", &frame_timing},
                   {"log_gpu_frame_time_breakdown_passes", &pass_timing}});
 for (uint32_t title : {0x454107F8u, 0x454108D8u, 0x41560815u, 0x4D5309C9u}) {
   xe::game_quirks::Apply(title);
   assert(exports.writes == 0 && fences.writes == 0 && skip_shader.writes == 0 &&
          skip_pipeline.writes == 0 && resolve.writes == 0);
   assert(selective_readback.writes == 0 && neutralize_ssao.writes == 0 &&
          unorm8_precision.writes == 0 && sanitize_hdr.writes == 0 &&
          range_coherency.writes == 0);
   assert(accurate_resolve.writes == 0 && destination_packing.writes == 0 &&
          integer_fetch.writes == 0 && frame_timing.writes == 0 && pass_timing.writes == 0);
 }
 assert(xe::game_quirks::Apply(0x454109AB) == 15);
 assert(sanitize_hdr.writes == 1 && sanitize_hdr.current);
 assert(range_coherency.writes == 1 && range_coherency.current);
 assert(selective_readback.current && neutralize_ssao.current && unorm8_precision.current);
 assert(accurate_resolve.current && destination_packing.current && integer_fetch.current);
 assert(frame_timing.current && pass_timing.writes == 1 && !pass_timing.current);
 assert(exports.current && fences.current && resolve.current == "uma");
 assert(skip_shader.writes == 1 && !skip_shader.current);
 assert(skip_pipeline.writes == 1 && !skip_pipeline.current);
 // Config.cc applies the user's game file after dispatch; it must still win.
 toml::value<bool> explicit_user_override(false);
 refresh.LoadGameConfigValue(&explicit_user_override);
 assert(!refresh.current);
 cvar::ConfigVars = nullptr;
 assert(xe::game_quirks::Apply(0x454107F8) == 0);
}
''')
    compiler = os.environ.get('CXX') or shutil.which('clang++') or 'g++'
    binary = path / 'test'
    subprocess.run([compiler, '-std=c++20', '-O2', '-fsanitize=undefined',
                    '-fno-sanitize-recover=undefined', '-I', str(path), '-I', str(base),
                    str(base / 'xenia/game_quirks.cc'), str(path / 'test.cc'),
                    '-o', str(binary)], check=True)
    subprocess.run([str(binary)], check=True)
print('Army quirks: original page refresh and TDC GPU compatibility are scoped separately.')
