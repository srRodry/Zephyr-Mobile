// SPDX-License-Identifier: WTFPL

#ifndef APS3E_EMULATOR_H
#define APS3E_EMULATOR_H

#include <android/native_window_jni.h>
#include <mutex>
#include <string>
#include <cstdint>
#include <vector>

namespace ae{
    constexpr int BOOT_TYPE_WITH_PATH=1;
    constexpr int BOOT_TYPE_WITH_FD=2;
    extern int boot_type;

    extern std::string boot_game_path;
    extern int boot_game_fd;

    extern ANativeWindow* window;       // guarded by window_mutex
    extern int window_width;            // guarded by window_mutex
    extern int window_height;           // guarded by window_mutex
    extern std::mutex window_mutex;

    // Surface lifecycle entry points (called from the JNI/binder thread).
    // attach: take ownership of `w` (one ref from ANativeWindow_fromSurface),
    //         stash it, and -- once booted -- marshal a recreate to main_thr.
    // detach: SYNCHRONOUSLY marshal a teardown to main_thr (blocks until the
    //         GPU drain completes), then release the window.
    // resize: update the cached size (swapchain re-queries size on recreate).
    extern void surface_attach(ANativeWindow* w, int width, int height);
    extern void surface_detach();
    extern void surface_resize(int width, int height);

    extern void main_thr();
    extern void key_event(int key_code,bool pressed,int value);
    // Player slots P2-P4 (P1 = key_event): input, controller hotplug and guest rumble.
    extern void key_event_slot(int slot,int key_code,bool pressed,int value);
    extern void set_slot_connected(int slot,bool connected,const std::string& name);
    extern uint32_t slot_rumble(int slot);
    extern bool is_running();
    extern bool is_paused();
    extern uint32_t active_title_id();
    extern std::string active_executable();
    // L10: the active title's module hashes as its patches were matched (main executable first).
    extern std::vector<uint64_t> module_hashes();
    extern void pause();
    extern void resume();
    extern void quit();
    extern void flush_gpu_caches();
}
#endif //APS3E_EMULATOR_H
