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
struct BoolVar : cvar::IConfigVar {
 bool current = false; int writes = 0;
 void LoadGameConfigValue(const toml::node* node) override {
   auto typed = dynamic_cast<const toml::value<bool>*>(node);
   assert(typed); current = typed->val; ++writes;
 }
};
int main() {
 BoolVar refresh;
 std::unordered_map<std::string, cvar::IConfigVar*> variables{{"clear_memory_page_state", &refresh}};
 cvar::ConfigVars = &variables;
 assert(xe::game_quirks::Apply(0x454107F8) == 1);
 assert(refresh.current && refresh.writes == 1);
 // Other Army titles, Spider-Man and Forza must not receive this override.
 for (uint32_t title : {0x454108D8u, 0x454109ABu, 0x41560815u, 0x4D5309C9u}) {
   xe::game_quirks::Apply(title); assert(refresh.writes == 1);
 }
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
print('Army quirk: real dispatcher applies only to 454107F8; user-file priority and unrelated titles preserved.')
