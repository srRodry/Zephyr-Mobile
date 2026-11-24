#!/usr/bin/env bash
# Pure-logic native tests (no Vulkan): presentation policy, frame-generation
# schedule, the pipeline cache file store, the guest vblank pacer, the native crash
# record (written from a real fault handler), the changed-settings lines, the guest
# frame stats, the adaptive audio buffer and the audio drivers' block processing.
# They need only a C++20 compiler.
# With XENDROID_NDK, also host threads: APCs alerted at once to several threads.
#
# Compiler: $CXX (default c++). On a host without one (no libstdc++/glibc headers,
# as in a bare WSL), set XENDROID_NDK to an NDK root: the tests are then built as
# static x86_64 Android executables, which a Linux kernel runs directly.
set -euo pipefail
root="$(realpath "$(dirname "${BASH_SOURCE[0]}")/..")"
build="${XENDROID_HOST_TEST_BUILD:-$root/build/native-logic-tests}"
mkdir -p "$build"
cpp="$root/emulator-core/src/main/cpp"
test="$root/emulator-core/src/test/cpp"
if [[ -n "${XENDROID_NDK:-}" ]]; then
    cxx=("$XENDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/x86_64-linux-android29-clang++" -static)
    export TMPDIR="${TMPDIR:-/tmp}"
    dl=()
else
    cxx=("${CXX:-c++}")
    dl=(-ldl)   # dladdr/dl_iterate_phdr before glibc 2.34
fi
flags=(-std=c++20 -O1 -Wall -I "$cpp" -I "$cpp/xenia" -I "$cpp/xenia/src" -I "$test/stubs")
for name in presentation_runtime_test pipeline_cache_file_test vblank_pacer_test changed_settings_test frame_stats_test audio_buffer_tuner_test audio_block_renderer_test structured_jumps_test; do
    "${cxx[@]}" "${flags[@]}" "$test/$name.cc" -o "$build/$name"
done
"${cxx[@]}" "${flags[@]}" "$test/bit_stream_test.cc" "$cpp/xenia/src/xenia/base/bit_stream.cc" -o "$build/bit_stream_test"
"$build/bit_stream_test"
# C01: the crash record is a source file of its own (signal-handler code).
"${cxx[@]}" "${flags[@]}" "$test/crash_record_test.cc" "$cpp/xe_crash_record.cpp" -o "$build/crash_record_test" "${dl[@]}"
"$build/presentation_runtime_test"
"$build/pipeline_cache_file_test" "$build/pipeline-cache-scratch"
"$build/vblank_pacer_test"
"$build/changed_settings_test"
"$build/frame_stats_test"
"$build/audio_buffer_tuner_test"
"$build/audio_block_renderer_test"
"$build/structured_jumps_test"
"$build/crash_record_test" "$build/crash-record-scratch"
# Host threads and APC delivery (Thread::QueueUserCallback) on the fork's Android
# path (XE_PLATFORM_xendroid), so only with the NDK; Xenia's headers need clang.
if [[ -n "${XENDROID_NDK:-}" ]]; then
    xbase="$cpp/xenia/src/xenia/base"
    "${cxx[@]}" "${flags[@]}" -DFMT_HEADER_ONLY "$test/threading_apc_test.cc" "$xbase/threading_posix.cc" \
        "$xbase/threading.cc" "$xbase/threading_timer_queue.cc" "$xbase/clock_posix.cc" -o "$build/threading_apc_test"
    "$build/threading_apc_test"
else
    echo "threading_apc_test: skipped (set XENDROID_NDK: it tests the Android build's code path)"
fi
