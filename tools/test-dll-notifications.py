#!/usr/bin/env python3
"""Exercise the production title-thread DLL notification predicate."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / 'emulator-core/src/main/cpp/xenia/src/xenia/kernel/kernel_state.cc').read_text()
start = source.index('static bool NotifiesDlls(')
end = source.index('\nvoid KernelState::OnThreadExecute', start)
code = r'''
#include <cassert>
constexpr int X_PROCTYPE_TITLE=1;
struct X_KTHREAD { int process_type; };
struct XThread {
 bool guest; X_KTHREAD object; int reads=0;
 bool is_guest_thread(){return guest;}
 template<class T> T* guest_object(){++reads;return &object;}
};
'''+source[start:end]+r'''
int main(){
 XThread host{false,{1}}; assert(!NotifiesDlls(&host)); assert(host.reads==0);
 XThread system{true,{0}}; assert(!NotifiesDlls(&system));
 XThread title{true,{1}}; assert(NotifiesDlls(&title));
}
'''
# All four lifecycle notifications must pass the guest HMODULE, not a host object handle.
for marker in ('// Call DllMain(DLL_PROCESS_ATTACH)', '// Call DllMain(DLL_PROCESS_DETACH)',
               '// Call DllMain(DLL_THREAD_ATTACH)', '// Call DllMain(DLL_THREAD_DETACH)'):
    section = source.split(marker,1)[1].split('};',1)[0]
    assert '->hmodule_ptr()' in section, marker
    assert '->handle()' not in section, marker
with tempfile.TemporaryDirectory() as directory:
    path=Path(directory)
    (path/'test.cc').write_text(code)
    subprocess.run(['g++','-std=c++20','-fsanitize=undefined',str(path/'test.cc'),'-o',str(path/'test')],check=True)
    subprocess.run([str(path/'test')],check=True)
print('DLL notifications: title guests accepted; system and host threads excluded; four HMODULE arguments verified.')
