#!/usr/bin/env python3
"""Run the real WAIT_REG_MEM packet method with a deterministic clock and memory.
Long waits must report while pending, retain the comparison, and finish only on
an actual guest write. TDC also publishes consumed primary-ring bytes before
blocking, including when the wait is nested in one or more indirect buffers.
"""
from pathlib import Path
import subprocess
import tempfile
root = Path(__file__).resolve().parents[1]
source = (root/'emulator-core/src/main/cpp/xenia/src/xenia/gpu/pm4_command_processor_implement.h').read_text()
code = source[source.index('bool COMMAND_PROCESSOR::ExecutePacketType3_INDIRECT_BUFFER('):source.index('XE_NOINLINE\nbool COMMAND_PROCESSOR::ExecutePacketType3_REG_RMW')]
processor = (root/'emulator-core/src/main/cpp/xenia/src/xenia/gpu/command_processor.cc').read_text()
writeback = processor[processor.index('void CommandProcessor::WritePrimaryReadPointer('):processor.index('void CommandProcessor::Pause()')].replace('CommandProcessor::', 'CP::')
harness = r'''
#include <algorithm>
#include <atomic>
#include <cassert>
#include <chrono>
#include <cstdint>
#include <optional>
#include <string>
#include <vector>
#define COMMAND_PROCESSOR CP
#define XE_RESTRICT
#define XE_NOINLINE
#define SCOPE_profile_cpu_f(x)
#define XE_UNLIKELY(x) (x)
#define XE_PLATFORM_WIN32 0
#define assert_true(x) assert(x)
#define assert_zero(x) assert(!(x))
static int reports=0, polls=0, until_write=0;
static uint64_t now=1000000000ull;
static uint32_t guest_value=0, guest_rptr=0;
static bool release_on_progress=false;
static std::vector<uint32_t> writebacks;
void NotifyGuestGpuProgress(){writebacks.push_back(guest_rptr);}
template<class... T> void log_warning(const char*, T...){++reports;}
#define XELOGW(...) log_warning(__VA_ARGS__)
#define XELOGI(...)
namespace cvars {bool memexport_await_fences=false,guest_display_refresh_cap=true,wait_reg_mem_backoff=false; int wait_reg_mem_backoff_max_us=0;}
namespace xenos {enum class Endian{none};uint32_t GpuSwap(uint32_t x,Endian){return x;}}
namespace xe {template<class T> void store_and_swap(void* p,T x){*static_cast<T*>(p)=x;}}
namespace xe {namespace threading {
void step(){
 now+=1000000000ull;
 ++polls;
 if (release_on_progress) assert(!writebacks.empty());
 if (polls==until_write) guest_value=0xABCD0007;
}
void NanoSleep(uint64_t){step();}
void Sleep(std::chrono::milliseconds){step();}
}}
constexpr int XE_GPU_REG_COHER_STATUS_HOST=0,XE_GPU_REG_COHER_BASE_HOST=1,XE_GPU_REG_COHER_SIZE_HOST=2;
struct RegisterFile {static constexpr int kRegisterCount=32;uint32_t values[32]={};};
struct Memory {void* TranslatePhysical(uint32_t addr){return addr==0x1004?&guest_rptr:&guest_value;}};
struct Reader {
 std::vector<uint32_t> data;
 size_t i=0;
 size_t start=0, capacity=256;
 uint32_t read_offset() const {return ((start+i)*sizeof(uint32_t))%capacity;}
 template<class T>T ReadAndSwap(){return data.at(i++);}
};
struct Trace {void WriteMemoryRead(uint32_t,uint32_t){}};
struct Kernel {uint32_t title=0;uint32_t title_id(){return title;}};
struct CP {
 Reader reader_; Memory memory;Memory*memory_=&memory;RegisterFile regs;RegisterFile*register_file_=&regs;Trace trace_writer_;
 Kernel kernel;Kernel*kernel_state_=&kernel;
 uint32_t read_ptr_writeback_ptr_=0x1004,ib_call_depth_=0;
 bool primary_buffer_active_=false,primary_wait_writeback_logged_=false;
 std::optional<uint32_t> primary_indirect_read_index_;
 struct {bool frames_left=false;} bin_trace_;
 uint32_t wrm_log_left_=0,read_ptr_index_=12;std::atomic<uint32_t>write_ptr_index_{13};std::atomic<bool>paused_{true};bool worker_running_=true;
 bool debug_markers_enabled(){return false;}template<class...T>void InsertDebugMarker(T...){}
 bool MemexportReadbacksPending(){return false;}void AwaitMemexportForCoherency(uint32_t,uint32_t){}void MakeCoherent(){}
 bool AwaitMemexportReadbacks(const char*){return false;}
 uint64_t FrameWaitBegin(){return 0;}static uint64_t FrameStatsNow(){return now;}
 void WrmWriterArm(uint32_t){}std::string WrmWriterTake(){return {};}
 void PrepareForWait(){}void ReturnFromWait(){}void FrameStatsEndWaitRegMem(uint64_t){}
 uint32_t CpuToGpu(uint32_t x){return x;}
 uint32_t GpuToCpu(uint32_t x){return x;}
 void BinTraceIndirectBuffer(uint32_t,uint32_t,bool,bool){}
 bool ExecuteIndirectBuffer(uint32_t,uint32_t);
 bool ExecutePacketType3_INDIRECT_BUFFER(uint32_t,uint32_t);
 bool ExecutePacketType3_WAIT_REG_MEM(uint32_t,uint32_t);
 void WritePrimaryReadPointer(uint32_t);
 void PublishPrimaryProgressBeforeWait();
};
'''+writeback+code+r'''
static const std::vector<uint32_t> wait_data={0x13,0x1000,7,0xff,0x100};

// Zephyr replaces reader_ in-place for an indirect list and restores it on
// return. Exercise the real dispatch wrapper around the same reader swap.
bool CP::ExecuteIndirectBuffer(uint32_t ptr,uint32_t){
 ++ib_call_depth_;
 Reader parent=reader_;
 bool completed;
 if (ptr==0x2000) {
   reader_={{0x3000,5}};
   completed=ExecutePacketType3_INDIRECT_BUFFER(0,2);
 } else {
   assert(ptr==0x3000);
   reader_={wait_data};
   completed=ExecutePacketType3_WAIT_REG_MEM(0,5);
 }
 reader_=parent;
 --ib_call_depth_;
 return completed;
}

int main(){
 CP cp;
 // EQ masked low byte: the untouched high bytes must not prevent a match.
 cp.reader_.data=wait_data;
 until_write=16;
 assert(cp.ExecutePacketType3_WAIT_REG_MEM(0,5));
 assert(polls==16); assert(reports==3); assert(guest_value==0xABCD0007);
 // Already matched: no sleep and no spurious stall warning.
 polls=reports=0;cp.reader_.i=0;
 assert(cp.ExecutePacketType3_WAIT_REG_MEM(0,5));assert(polls==0);assert(reports==0);
 assert(writebacks.empty());

 cp.kernel.title=0x454109AB;
 cp.primary_buffer_active_=true;
 // An already satisfied packet needs no early writeback even in TDC.
 cp.reader_.i=0;
 assert(cp.ExecutePacketType3_WAIT_REG_MEM(0,5));
 assert(writebacks.empty());
 auto reset=[&] {
   polls=reports=0; until_write=3; guest_value=0; writebacks.clear();
 };
 reset();
 release_on_progress=true;
 cp.reader_={wait_data,0,22};
 assert(cp.ExecutePacketType3_WAIT_REG_MEM(0,5));
 assert((writebacks==std::vector<uint32_t>{27}));
 assert(polls==3 && cp.read_ptr_index_==12);

 // The outer packet ends exactly on primary-ring wrap. A nested indirect
 // wait must publish offset 0, not the inner wait's dword count.
 reset();
 cp.reader_={{0x2000,2},0,62};
 assert(cp.ExecutePacketType3_INDIRECT_BUFFER(0,2));
 assert((writebacks==std::vector<uint32_t>{0}));
 assert(cp.reader_.read_offset()==0 && !cp.primary_indirect_read_index_);
 assert(cp.ib_call_depth_==0 && cp.read_ptr_index_==12);

 release_on_progress=false;
 for (unsigned which=0;which!=3;++which){
   reset();
   cp.kernel.title=which==0?0:0x454109AB;
   cp.primary_buffer_active_=which!=1;
   cp.read_ptr_writeback_ptr_=which==2?0:0x1004;
   cp.reader_={wait_data};
   assert(cp.ExecutePacketType3_WAIT_REG_MEM(0,5));
   assert(writebacks.empty() && polls==3);
 }

 // Shutdown must stop short spin waits too, without claiming ring space.
 cp.primary_buffer_active_=true;
 cp.read_ptr_writeback_ptr_=0x1004;
 cp.worker_running_=false;
 for (uint32_t interval : {0u, 0x100u}) {
   reset();
   cp.reader_={wait_data};
   cp.reader_.data.back()=interval;
   assert(!cp.ExecutePacketType3_WAIT_REG_MEM(0,5));
   assert(writebacks.empty() && polls==0);
 }
 cp.reader_={{0x2000,2}};
 assert(!cp.ExecutePacketType3_INDIRECT_BUFFER(0,2));
 assert(!cp.primary_indirect_read_index_ && cp.ib_call_depth_==0);
}
'''
with tempfile.TemporaryDirectory() as d:
 p=Path(d);(p/'test.cc').write_text(harness)
 subprocess.run(['g++','-std=c++20','-O1','-fsanitize=undefined',str(p/'test.cc'),'-o',str(p/'test')],check=True)
 subprocess.run([str(p/'test')],check=True)
print('WAIT_REG_MEM: stall diagnostics, matching, primary-ring progress and shutdown cancellation passed.')
