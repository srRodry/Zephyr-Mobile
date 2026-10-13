#!/usr/bin/env python3
"""Exercise the production audio ADPF owner with a mock NDK on a Linux host.

This verifies cleanup and failure paths, not Android scheduling or audio quality.
No native emulation libraries or device are needed.
"""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "emulator-core/src/main/cpp/xenia/src/xenia/apu/audio_system.cc"
PREFIX = r'''
#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <dirent.h>
#include <dlfcn.h>
#include <map>
#include <string>
#include <vector>
#include <unistd.h>
#include <sys/prctl.h>
#include <cassert>
#define XELOGI(...) ((void)0)
#define XELOGW(...) ((void)0)
namespace cvars { bool apu_performance_hint=true; }
struct AudioSystem { static constexpr int kAudioPumpInterval=5333; };
int opens=0,unloads=0,created=0,closed=0,reports=0;
int64_t duration=0;
bool missing_close=false,missing_report=false,manager_ok=true,create_ok=true;
void* manager() { return manager_ok ? reinterpret_cast<void*>(1) : nullptr; }
void* create(void*,const int32_t*,size_t n,int64_t target) {
 assert(n>=1 && target==5333000); ++created;
 return create_ok ? reinterpret_cast<void*>(2) : nullptr;
}
int report(void* s,int64_t n) { assert(s==reinterpret_cast<void*>(2));++reports;duration=n;return 0; }
void close_session(void* s) { assert(s==reinterpret_cast<void*>(2));++closed; }
void* fake_open(const char*,int) { ++opens;return reinterpret_cast<void*>(3); }
int fake_unload(void* h) { assert(h==reinterpret_cast<void*>(3));++unloads;return 0; }
void* fake_symbol(void*,const char* name) {
 if(!strcmp(name,"APerformanceHint_getManager"))return reinterpret_cast<void*>(&manager);
 if(!strcmp(name,"APerformanceHint_createSession"))return reinterpret_cast<void*>(&create);
 if(!strcmp(name,"APerformanceHint_reportActualWorkDuration"))return missing_report?nullptr:reinterpret_cast<void*>(&report);
 if(!strcmp(name,"APerformanceHint_closeSession"))return missing_close?nullptr:reinterpret_cast<void*>(&close_session);
 return nullptr;
}
#define dlopen fake_open
#define dlclose fake_unload
#define dlsym fake_symbol
'''
SUFFIX = r'''
int main() {
 prctl(PR_SET_NAME,"Audio Worker");
 {AudioPerformanceHint hint;hint.ReportPump(8);assert(duration==8000);hint.ReportPump(0);assert(duration==1000);}
 assert(opens==1 && unloads==1 && created==1 && closed==1 && reports==2);
 cvars::apu_performance_hint=false;
 {AudioPerformanceHint hint;hint.ReportPump(8);hint.ReportPump(8);}
 assert(opens==1 && unloads==1 && reports==2);
 cvars::apu_performance_hint=true;missing_close=true;
 {AudioPerformanceHint hint;hint.ReportPump(8);hint.ReportPump(8);}
 assert(opens==2 && unloads==2 && created==1 && closed==1);
 missing_close=false;missing_report=true;
 {AudioPerformanceHint hint;hint.ReportPump(8);}
 assert(opens==3 && unloads==3 && created==1 && closed==1);
 missing_report=false;manager_ok=false;
 {AudioPerformanceHint hint;hint.ReportPump(8);}
 assert(opens==4 && unloads==4 && created==1 && closed==1);
 manager_ok=true;create_ok=false;
 {AudioPerformanceHint hint;hint.ReportPump(8);}
 assert(opens==5 && unloads==5 && created==2 && closed==1);
 create_ok=true;
 {AudioPerformanceHint hint;hint.ReportPump(9);}
 assert(opens==6 && unloads==6 && created==3 && closed==2 && duration==9000);
 puts("AudioPerformanceHint lifecycle: passed (mock NDK, actual production class)");
}
'''


def main():
    source = SOURCE.read_text()
    begin = source.index("class AudioPerformanceHint {")
    end = source.index("// Reports the first failure", begin)
    # Compile the exact production class, not a second implementation.
    with tempfile.TemporaryDirectory(prefix="zephyr-audio-hint-") as tmp:
        cpp = Path(tmp) / "test.cc"
        binary = Path(tmp) / "test"
        cpp.write_text(PREFIX + source[begin:end] + SUFFIX)
        subprocess.run([os.environ.get("CXX", "c++"), "-std=c++20", "-O1", "-Wall",
                        str(cpp), "-o", str(binary)], check=True)
        subprocess.run([str(binary)], check=True)


if __name__ == "__main__":
    main()
