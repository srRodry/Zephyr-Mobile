#!/usr/bin/env python3
"""Encode exact production AND/AND_NOT emitters with vendored Xbyak; no ARM execution."""
from pathlib import Path
import os, shutil, subprocess, tempfile
ROOT = Path(__file__).resolve().parents[1]
XENIA = ROOT / 'emulator-core/src/main/cpp/xenia'
SHIM = r'''
#pragma once
#include "xbyak_aarch64/xbyak_aarch64.h"
#include "xenia/cpu/backend/a64/a64_logical_immediate.h"
namespace test_a64 {
using xe::cpu::backend::a64::IsLogicalImmediate64;
using xe::cpu::backend::a64::IsLogicalImmediate32;
enum Opcode { OPCODE_AND, OPCODE_AND_NOT };
struct I64Op : Xbyak_aarch64::XReg {
  bool is_constant; uint64_t bits;
  I64Op(bool c, uint32_t index, uint64_t bits) : XReg(index), is_constant(c), bits(bits) {}
  uint64_t constant() const { return bits; }
};
struct I32Op : Xbyak_aarch64::WReg {
  bool is_constant; uint32_t bits;
  I32Op(bool c, uint32_t index, uint32_t bits) : WReg(index), is_constant(c), bits(bits) {}
  uint32_t constant() const { return bits; }
};
template<Opcode op, class D, class A, class B> struct I { D dest; A src1; B src2; };
template<class Self, class Arg> struct Sequence { using EmitArgType = Arg; };
struct A64Emitter : Xbyak_aarch64::CodeGenerator {};
'''
def main(sequence_source=None):
    source = sequence_source if sequence_source is not None else (XENIA / 'src/xenia/cpu/backend/a64/a64_sequences.cc').read_text()
    parts = []
    for begin, end in [('static void EmitAndImmediate64', 'struct AND_I8'),
                       ('struct AND_I64 :', 'struct AND_V128'),
                       ('struct AND_NOT_I32', 'struct AND_NOT_V128')]:
        start = source.index(begin); parts.append(source[start:source.index(end, start)])
    with tempfile.TemporaryDirectory(prefix='zephyr-logical-') as tmp:
        base = Path(tmp)
        (base / 'a64_logical_test.h').write_text(SHIM + ''.join(parts) + '\n}\n')
        cxx = os.environ.get('CXX') or shutil.which('clang++') or 'c++'
        subprocess.run([cxx, '-std=c++17', '-O1', '-I', str(base), '-I', str(XENIA / 'src'),
                        '-I', str(XENIA / 'third_party/xbyak_aarch64'),
                        '-I', str(XENIA / 'third_party/xbyak_aarch64/xbyak_aarch64'),
                        str(ROOT / 'emulator-core/src/test/cpp/a64_logical_immediate_test.cc'),
                        str(XENIA / 'third_party/xbyak_aarch64/src/xbyak_aarch64_impl.cpp'),
                        '-o', str(base / 'test')], check=True)
        subprocess.run([str(base / 'test')], check=True)
if __name__ == '__main__': main()
