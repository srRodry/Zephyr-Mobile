#!/usr/bin/env python3
"""Run the real WAIT_REG_MEM packet method with a deterministic clock and memory.
Long waits must report while pending, retain the comparison, and finish only on
an actual guest write. This doesn't emulate Vulkan or ARM memory ordering.
"""
from pathlib import Path
import subprocess
import tempfile
root = Path(__file__).resolve().parents[1]
source = (root/'emulator-core/src/main/cpp/xenia/src/xenia/gpu/pm4_command_processor_implement.h').read_text()
code = source[source.index('static bool MatchValueAndRef('):source.index('XE_NOINLINE\nbool COMMAND_PROCESSOR::ExecutePacketType3_REG_RMW')]
harness = r'''
#include <algorithm>
#include <atomic>
#include <cassert>
#include <chrono>
#include <cstdint>
#include <string>
#include <vector>
#define COMMAND_PROCESSOR CP
#define XE_RESTRICT
#define XE_NOINLINE
#define SCOPE_profile_cpu_f(x)
#define XE_UNLIKELY(x) (x)
#define XE_PLATFORM_WIN32 0
#define assert_true(x) assert(x)
static int reports=0, polls=0, until_write=0;
static uint64_t now=1000000000ull;
static uint32_t guest_value=0;
template<class... T> void log_warning(const char*, T...){++reports;}
#define XELOGW(...) log_warning(__VA_ARGS__)
#define XELOGI(...)
namespace cvars {bool memexport_await_fences=false,guest_display_refresh_cap=true,wait_reg_mem_backoff=false; int wait_reg_mem_backoff_max_us=0;}
namespace xenos {enum class Endian{none};uint32_t GpuSwap(uint32_t x,Endian){return x;}}
namespace xe {namespace threading {
void step(){now+=1000000000ull; if(++polls==until_write)guest_value=0xABCD0007;}
void NanoSleep(uint64_t){step();}
void Sleep(std::chrono::milliseconds){step();}
}}
constexpr int XE_GPU_REG_COHER_STATUS_HOST=0,XE_GPU_REG_COHER_BASE_HOST=1,XE_GPU_REG_COHER_SIZE_HOST=2;
struct RegisterFile {static constexpr int kRegisterCount=32;uint32_t values[32]={};};
struct Memory {void* TranslatePhysical(uint32_t){return &guest_value;}};
struct Reader {std::vector<uint32_t> data;size_t i=0;template<class T>T ReadAndSwap(){return data.at(i++);}};
struct Trace {void WriteMemoryRead(uint32_t,uint32_t){}};
struct CP {
 Reader reader_; Memory memory;Memory*memory_=&memory;RegisterFile regs;RegisterFile*register_file_=&regs;Trace trace_writer_;
 uint32_t wrm_log_left_=0,read_ptr_index_=12;std::atomic<uint32_t>write_ptr_index_{13};std::atomic<bool>paused_{true};bool worker_running_=true;
 bool debug_markers_enabled(){return false;}template<class...T>void InsertDebugMarker(T...){}
 bool MemexportReadbacksPending(){return false;}void AwaitMemexportForCoherency(uint32_t,uint32_t){}void MakeCoherent(){}
 bool AwaitMemexportReadbacks(const char*){return false;}
 uint64_t FrameWaitBegin(){return 0;}static uint64_t FrameStatsNow(){return now;}
 void WrmWriterArm(uint32_t){}std::string WrmWriterTake(){return {};}
 void PrepareForWait(){}void ReturnFromWait(){}void FrameStatsEndWaitRegMem(uint64_t){}
 uint32_t CpuToGpu(uint32_t x){return x;}
 bool ExecutePacketType3_WAIT_REG_MEM(uint32_t,uint32_t);
};
'''+code+r'''
int main(){
 CP cp;
 // EQ masked low byte: the untouched high bytes must not prevent a match.
 cp.reader_.data={0x13,0x1000,7,0xff,0x100};
 until_write=16;
 assert(cp.ExecutePacketType3_WAIT_REG_MEM(0,5));
 assert(polls==16); assert(reports==3); assert(guest_value==0xABCD0007);
 // Already matched: no sleep and no spurious stall warning.
 polls=reports=0;cp.reader_.i=0;
 assert(cp.ExecutePacketType3_WAIT_REG_MEM(0,5));assert(polls==0);assert(reports==0);
}
'''
with tempfile.TemporaryDirectory() as d:
 p=Path(d);(p/'test.cc').write_text(harness)
 subprocess.run(['g++','-std=c++20','-O1','-fsanitize=undefined',str(p/'test.cc'),'-o',str(p/'test')],check=True)
 subprocess.run([str(p/'test')],check=True)
print('WAIT_REG_MEM: three throttled stall reports; no forced match; actual masked guest write releases wait.')
