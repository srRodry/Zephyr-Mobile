#!/usr/bin/env python3
"""Execute production permute encodings in a small independent NEON interpreter.

Host tests validate bits and emitted instruction counts, not ARM timing or games.
Constant loading is a harness literal load; the production materializer is unchanged.
"""
from pathlib import Path
import os, shutil, subprocess, tempfile
ROOT=Path(__file__).resolve().parents[1]
X=ROOT/'emulator-core/src/main/cpp/xenia'
source=(X/'src/xenia/cpu/backend/a64/a64_seq_vector.cc').read_text()
body=source[source.index('struct PERMUTE_V128'):source.index('EMITTER_OPCODE_TABLE(OPCODE_PERMUTE',source.index('struct PERMUTE_V128'))]
shim=r'''
#include <array>
#include <cassert>
#include <cstring>
#include <cstdio>
#include <memory>
#include <vector>
#include "xenia/base/vec128.h"
#include "xbyak_aarch64/xbyak_aarch64.h"
using namespace Xbyak_aarch64;
using namespace xe;
#define assert_true(x) assert(x)
#define assert_always() assert(false)
#define assert_unhandled_case(x) assert(false)
constexpr int OPCODE_PERMUTE=1, INT8_TYPE=0, INT16_TYPE=1, INT32_TYPE=2;
struct Instr { int flags=0; };
struct V128Op {
 bool is_constant=false; int index=4; vec128_t value{};
 VReg reg() const { return VReg(index); }
 const vec128_t& constant() const { return value; }
};
template<int O, class... T> struct I {
 V128Op dest,src1,src2,src3; Instr* instr=nullptr;
};
template<class S,class T> struct Sequence { using EmitArgType=T; };
struct A64Emitter : CodeGenerator {
 std::vector<std::unique_ptr<Label>> labels;
 Label& label() { labels.push_back(std::make_unique<Label>()); return *labels.back(); }
};
static void LoadV128Const(A64Emitter& e,int d,const vec128_t& v) {
 auto& literal=e.label();auto& done=e.label();
 e.ldr(QReg(d),literal);e.b(done);e.L(literal);e.dd(uint32_t(v.low));e.dd(uint32_t(v.low>>32));e.dd(uint32_t(v.high));e.dd(uint32_t(v.high>>32));e.L(done);
}
static int SrcVReg(A64Emitter& e,const V128Op& op,int scratch) {
 if(op.is_constant) { LoadV128Const(e,scratch,op.value);return scratch; }
 return op.index;
}
'''
with tempfile.TemporaryDirectory(prefix='zephyr-a64-round3-') as t:
 p=Path(t);(p/'permute.h').write_text(shim+body)
 cxx=os.environ.get('CXX') or shutil.which('clang++') or 'c++'
 cmd=[cxx,'-std=c++20','-O1','-msse4.1','-mfma','-DFMT_HEADER_ONLY','-Wno-attributes',
      '-I',str(p),'-I',str(X),'-I',str(X/'src'),'-I',str(X/'third_party/xbyak_aarch64'),
      '-I',str(X/'third_party/xbyak_aarch64/xbyak_aarch64'),
      str(ROOT/'emulator-core/src/test/cpp/a64_permute_round3_test.cc'),
      str(X/'third_party/xbyak_aarch64/src/xbyak_aarch64_impl.cpp'),'-o',str(p/'test')]
 if 'clang' not in Path(cxx).name:cmd.insert(1,'-fpermissive')
 subprocess.run(cmd,check=True);subprocess.run([str(p/'test')],check=True)
