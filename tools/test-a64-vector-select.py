#!/usr/bin/env python3
"""Compile real HIR/DCE/allocator sources and encode the production A64 vector select.

Logging/profiling UI are excluded. This does not execute ARM64 machine code and
is not a game benchmark. Assertions stay enabled.
"""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
XENIA = ROOT / 'emulator-core/src/main/cpp/xenia'
SRC = XENIA / 'src'
TEST = ROOT / 'emulator-core/src/test/cpp/a64_vector_select_test.cc'
SOURCES = [
    'cpu/compiler/passes/register_allocation_pass.cc', 'cpu/compiler/compiler_pass.cc',
    'cpu/hir/hir_builder.cc', 'cpu/hir/block.cc', 'cpu/hir/instr.cc',
    'cpu/hir/value.cc', 'cpu/hir/opcodes.cc', 'base/arena.cc', 'base/string_buffer.cc',
    'cpu/compiler/passes/dead_code_elimination_pass.cc',
]
A64_SHIM = r'''
#pragma once
#include "xbyak_aarch64/xbyak_aarch64.h"
namespace test_a64 {
using xe::cpu::hir::OPCODE_SELECT;
using Xbyak_aarch64::VReg;
struct V128Op {
  bool is_constant; uint32_t index;
  struct Register { uint32_t index; uint32_t getIdx() const { return index; } };
  Register reg() const { return {index}; }
};
template<xe::cpu::hir::Opcode opcode, class D, class S, class S2, class S3>
struct I { D dest; S src1; S2 src2; S3 src3; };
template<class Self, class Arg> struct Sequence { using EmitArgType = Arg; };
struct A64Emitter : Xbyak_aarch64::CodeGenerator {};
static int SrcVReg(A64Emitter&, const V128Op& op, int) {
  assert(!op.is_constant); // Nonconstant production fast path; materialization is unchanged.
  return int(op.reg().getIdx());
}
'''



def main(sequence_source=None):
    cxx = os.environ.get('CXX') or shutil.which('clang++') or 'c++'
    with tempfile.TemporaryDirectory(prefix='zephyr-vector-select-') as tmp:
        root = Path(tmp)
        stub = root / 'xenia/base'
        stub.mkdir(parents=True)
        (stub / 'profiling.h').write_text('#pragma once\n')
        (stub / 'logging.h').write_text('#pragma once\n#define XELOGE(...) ((void)0)\n#define XELOGW(...) ((void)0)\n#define XELOGI(...) ((void)0)\n')
        # The exact production emitter body, with the actual vendored instruction encoder (no ARM CPU execution).
        sequences = sequence_source if sequence_source is not None else (SRC / 'xenia/cpu/backend/a64/a64_sequences.cc').read_text()
        select_begin = sequences.index('struct SELECT_V128_V128')
        select_end = sequences.index('EMITTER_OPCODE_TABLE(OPCODE_SELECT', select_begin)
        (root / 'a64_select_test.h').write_text(A64_SHIM + sequences[select_begin:select_end] + '\n}\n')
        flags = ['-std=c++20', '-O1', '-ffunction-sections', '-fdata-sections',
                 '-DFMT_HEADER_ONLY', '-msse4.1', '-mfma', '-Wno-attributes']
        if 'clang' not in Path(cxx).name:
            # GCC's unused _mm_stream_load_si128 declaration rejects const; Clang accepts it.
            # That MMIO intrinsic is not used in this test. No production header is modified.
            flags += ['-fpermissive']
        command = [cxx, *flags, '-I', str(root), '-I', str(XENIA), '-I', str(SRC), '-I', str(XENIA / 'third_party/xbyak_aarch64'),
                   str(TEST), *(str(SRC / 'xenia' / p) for p in SOURCES),
                   str(XENIA / 'third_party/xbyak_aarch64/src/xbyak_aarch64_impl.cpp'),
                   '-I', str(XENIA / 'third_party/xbyak_aarch64/xbyak_aarch64'), '-Wl,--gc-sections', '-o', str(root / 'test')]
        result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        if result.returncode:
            print(result.stdout)
            raise SystemExit(result.returncode)
        subprocess.run([str(root / 'test')], check=True)


if __name__ == '__main__':
    main()
