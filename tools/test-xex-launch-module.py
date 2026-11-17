#!/usr/bin/env python3
"""Exercise the production extracted-XEX launcher with a stub VFS/kernel."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / 'emulator-core/src/main/cpp/xenia/src/xenia/emulator.cc').read_text()
launcher = source.split('X_STATUS Emulator::LaunchXexFile(', 1)[1].split('\nX_STATUS Emulator::LaunchDiscImage(', 1)[0]
code = r'''
#include <cassert>
#include <filesystem>
#include <memory>
#include <string>
using X_STATUS = int;
constexpr X_STATUS X_STATUS_SUCCESS = 0, X_STATUS_NOT_FOUND = 1;
#define XFAILED(x) ((x) != 0)
#define XELOGI(...) ((void)0)
namespace cvars { std::string launch_module; }
namespace xe { std::string path_to_utf8(const std::filesystem::path& p) { return p.string(); } }
namespace utf8 { std::string find_base_guest_path(const std::string&) { return "game:"; } }
namespace kernel { bool IsSystemTitle(int) { return false; } }
enum class XDeploymentType { kInstalledToHDD };
struct Module { std::string path() { return "game:\\AO4.Main.xex"; } };
struct Kernel {
 XDeploymentType deployment_type_;
 int title_id() { return 0x454109AB; }
 std::shared_ptr<Module> GetExecutableModule() { return std::make_shared<Module>(); }
 std::shared_ptr<Module> LoadUserModule(const char*) { return {}; }
 int FinishLoadingUserModule(std::shared_ptr<Module>, bool) { return 0; }
};
struct Vfs { void RegisterSymbolicLink(const char*, const std::string&) {} };
constexpr auto kDefaultGameSymbolicLink = "game:";
namespace fmt {
 std::string format(const char*, const char* p) { return std::string(p) + "\\"; }
}
struct Emulator {
 Kernel kernel; Kernel* kernel_state_ = &kernel;
 Vfs vfs; Vfs* file_system_ = &vfs;
 std::string loaded; int calls = 0;
 std::string FindLaunchModule() { return std::string("game:\\") + cvars::launch_module; }
 int CompleteLaunch(const std::filesystem::path&, const std::string& guest) {
   loaded = guest; ++calls;
   return guest == "game:\\missing.xex" ? X_STATUS_NOT_FOUND : X_STATUS_SUCCESS;
 }
 X_STATUS LaunchXexFile(const std::filesystem::path&);
};
'''
code += 'X_STATUS Emulator::LaunchXexFile(' + launcher
code += r'''
int main() {
 Emulator e;
 cvars::launch_module = "";
 assert(e.LaunchXexFile("/disc/Default.xex") == X_STATUS_SUCCESS);
 assert(e.loaded == "game:\\Default.xex");
 assert(e.LaunchXexFile("/disc/Other.xex") == X_STATUS_SUCCESS);
 assert(e.loaded == "game:\\Other.xex");
 cvars::launch_module = "AO4.Main.xex";
 assert(e.LaunchXexFile("/disc/Default.xex") == X_STATUS_SUCCESS);
 assert(e.loaded == "game:\\AO4.Main.xex");
 cvars::launch_module = "missing.xex";
 const int before = e.calls;
 assert(e.LaunchXexFile("/disc/Default.xex") == X_STATUS_NOT_FOUND);
 assert(e.loaded == "game:\\missing.xex" && e.calls == before + 1);
}
'''
with tempfile.TemporaryDirectory(prefix='zephyr-xex-') as directory:
    path = Path(directory)
    (path / 'test.cc').write_text(code)
    compiler = os.environ.get('CXX') or shutil.which('clang++') or 'g++'
    subprocess.run([compiler, '-std=c++20', '-Wall', '-Wextra', '-Werror',
                    '-fsanitize=undefined', str(path / 'test.cc'), '-o', str(path / 'test')], check=True)
    subprocess.run([str(path / 'test')], check=True)
print('XEX launch: explicit module honored, default/direct boots preserved, missing module fails without fallback.')
