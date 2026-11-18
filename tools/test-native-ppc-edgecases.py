#!/usr/bin/env python3
"""Execute extracted production PowerPC emitters using a deterministic HIR model.

This verifies the instruction-level semantics, including XER.CA and CR6.
It does not execute emitted ARM64 machine code or claim game performance.
"""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PPC = ROOT / "emulator-core/src/main/cpp/xenia/src/xenia/cpu/ppc"


def extract_function(source, signature):
    pos = source.find(signature)
    if pos < 0 or source.find(signature, pos + len(signature)) != -1:
        raise AssertionError(f"Missing or ambiguous production definition: {signature}")
    brace = source.index("{", pos)
    depth, cursor = 1, brace + 1
    while depth:
        if cursor >= len(source):
            raise AssertionError(f"Unterminated production definition: {signature}")
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        cursor += 1
    return source[pos:cursor]


alu = (PPC / "ppc_emit_alu.cc").read_text(encoding="utf-8")
vmx = (PPC / "ppc_emit_altivec.cc").read_text(encoding="utf-8")
functions = {
    "SRAW": extract_function(alu, "int InstrEmit_srawx("),
    "SRAD": extract_function(alu, "int InstrEmit_sradx("),
    "VCMPBFP": extract_function(vmx, "int InstrEmit_vcmpbfp_("),
}
# Do not silently pass a test that stopped exercising the relevant HIR path.
assert "f.IsTrue(f.And(sh, f.LoadConstantInt8(0x20)))" in functions["SRAW"]
assert "f.IsTrue(f.And(sh, f.LoadConstantInt8(0x40)))" in functions["SRAD"]
assert "f.Not(f.VectorCompareSGE(vb_value, va_value, FLOAT32_TYPE))" in functions["VCMPBFP"]
assert "f.UpdateCR6(v);" in functions["VCMPBFP"]

cpp = r"""
#include <algorithm>
#include <array>
#include <bit>
#include <cassert>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <deque>
#include <random>
#include <vector>
constexpr int INT8_TYPE = 8;
constexpr int INT32_TYPE = 32;
constexpr int INT64_TYPE = 64;
constexpr int FLOAT32_TYPE = 100;
struct vec128i {
  std::array<uint32_t, 4> lane;
  vec128i(uint32_t a, uint32_t b, uint32_t c, uint32_t d) : lane{a,b,c,d} {}
};
struct Value {
  uint64_t bits = 0;
  int width = 64;
  bool vector = false;
  std::array<uint32_t, 4> lane{};
};
struct InstrData {
  struct { unsigned RA=3, RB=2, RT=1, Rc=0; } X;
};
static uint64_t Mask(int bits) {
  return bits == 64 ? UINT64_MAX : (uint64_t{1} << bits) - 1;
}
struct PPCHIRBuilder {
  std::array<uint64_t, 32> gpr{};
  std::array<std::array<uint32_t,4>, 128> vr{};
  std::array<uint32_t,4> vr_result{};
  bool ca = false, cr6_all = false, cr6_none = false, cr6_middle = false;
  std::deque<Value> arena;

  Value* Scalar(uint64_t raw, int w) {
    arena.emplace_back();
    auto* p = &arena.back();
    p->bits = raw & Mask(w); p->width = w;
    return p;
  }
  Value* Vector(std::array<uint32_t,4> v) {
    arena.emplace_back();
    auto* p = &arena.back(); p->vector = true; p->lane = v;
    return p;
  }
  Value* LoadGPR(unsigned r) {return Scalar(gpr.at(r), 64);}
  void StoreGPR(unsigned r, Value* v) {gpr.at(r) = v->bits;}
  Value* LoadVR(unsigned r) {return Vector(vr.at(r));}
  void StoreVR(unsigned, Value* v) {assert(v->vector); vr_result = v->lane;}
  void StoreCA(Value* v) {assert(!v->vector); ca = v->bits != 0;}
  void UpdateCR(unsigned, Value*) {}
  void UpdateCR6(Value* v) {
    assert(v->vector);
    cr6_all = true; cr6_none = true; cr6_middle = false;
    for (auto value : v->lane) {
      cr6_all &= value == UINT32_MAX;
      cr6_none &= value == 0;
    }
  }
  Value* LoadConstantInt8(uint8_t v) {return Scalar(v,8);}
  Value* LoadConstantVec128(const vec128i& v) {return Vector(v.lane);}
  Value* Truncate(Value* v, int w) {assert(!v->vector); return Scalar(v->bits,w);}
  Value* SignExtend(Value* v, int w) {
    assert(!v->vector && w>=v->width);
    uint64_t value = v->bits;
    if ((value >> (v->width-1)) & 1) value |= ~Mask(v->width);
    return Scalar(value,w);
  }
  Value* Min(Value* a, Value* b) {
    assert(!a->vector && !b->vector && a->width == b->width);
    return Scalar(std::min(a->bits,b->bits),a->width);
  }
  Value* And(Value* a, Value* b) {
    assert(a->vector==b->vector);
    if (!a->vector) {assert(a->width==b->width);return Scalar(a->bits&b->bits,a->width);}
    std::array<uint32_t,4> r{};
    for(int i=0;i<4;++i) r[i]=a->lane[i]&b->lane[i];
    return Vector(r);
  }
  Value* Or(Value* a, Value* b) {
    assert(a->vector==b->vector);
    if (!a->vector) {assert(a->width==b->width);return Scalar(a->bits|b->bits,a->width);}
    std::array<uint32_t,4> r{};
    for(int i=0;i<4;++i) r[i]=a->lane[i]|b->lane[i];
    return Vector(r);
  }
  Value* Not(Value* a) {
    if (!a->vector) return Scalar(~a->bits,a->width);
    std::array<uint32_t,4> r{};
    for(int i=0;i<4;++i) r[i]=~a->lane[i];
    return Vector(r);
  }
  Value* IsTrue(Value* v) {assert(!v->vector); return Scalar(v->bits!=0,8);}
  Value* CompareNE(Value* a, Value* b) {
    assert(!a->vector&&!b->vector&&a->width==b->width);
    return Scalar(a->bits!=b->bits,8);
  }
  Value* Shr(Value* a, int sh) {return Scalar(a->bits>>sh,a->width);}
  Value* Shl(Value* a, Value* b) {
    unsigned sh=unsigned(b->bits);
    assert(sh<unsigned(a->width));
    return Scalar(a->bits<<sh,a->width);
  }
  Value* Sha(Value* a, Value* b) {
    assert(!a->vector && !b->vector);
    unsigned sh=unsigned(b->bits);
    assert(sh<unsigned(a->width));
    uint64_t result = a->bits >> sh;
    if (sh && ((a->bits >> (a->width-1)) & 1)) {
      result |= Mask(a->width) << (a->width-sh);
    }
    return Scalar(result,a->width);
  }
  Value* Neg(Value* a) {
    assert(a->vector);
    std::array<uint32_t,4> r{};
    for(int i=0;i<4;++i) r[i]=a->lane[i]^0x80000000u;
    return Vector(r);
  }
  Value* VectorCompareSGE(Value* a, Value* b, int type) {
    assert(a->vector&&b->vector&&type==FLOAT32_TYPE);
    std::array<uint32_t,4> r{};
    for(int i=0;i<4;++i) {
      float av=std::bit_cast<float>(a->lane[i]);
      float bv=std::bit_cast<float>(b->lane[i]);
      r[i]=(av>=bv)?UINT32_MAX:0;
    }
    return Vector(r);
  }
};
SRAW_FUNCTION
SRAD_FUNCTION
VCMPBFP_FUNCTION

struct ShiftResult {uint64_t value; bool ca;};
ShiftResult OracleShift(uint64_t input, uint64_t raw_shift, unsigned w) {
  const uint64_t mask=Mask(w);
  const uint64_t src=input & mask;
  const unsigned shift=unsigned(raw_shift & (w==32?63:127));
  const bool sign=((src>>(w-1))&1)!=0;
  const unsigned amount=std::min(shift,w-1);
  uint64_t result=src >> amount;
  if (sign && amount) result |= mask << (w-amount);
  result &= mask;
  if (w==32 && sign) result |= ~mask;
  const bool lost = shift>=w || (shift && (src & ((uint64_t{1}<<shift)-1))!=0);
  return {result, sign && lost};
}
void CheckShift(unsigned w,uint64_t input,uint64_t amount,bool record) {
  PPCHIRBuilder b;
  b.gpr[1]=input; b.gpr[2]=amount;
  InstrData i; i.X.Rc=record?1:0;
  int rc = w==32 ? InstrEmit_srawx(b,i):InstrEmit_sradx(b,i);
  auto expected=OracleShift(input,amount,w);
  if(rc || b.ca!=expected.ca || b.gpr[3]!=expected.value) {
    std::printf("FAIL %u-bit shift=%llu src=%016llx got=%016llx CA=%u wanted=%016llx CA=%u\n",
      w,(unsigned long long)amount,(unsigned long long)input,
      (unsigned long long)b.gpr[3],unsigned(b.ca),
      (unsigned long long)expected.value,unsigned(expected.ca));
    std::abort();
  }
}
void CheckVcmp(const std::array<uint32_t,4>& aa,
               const std::array<uint32_t,4>& bb,bool record) {
  PPCHIRBuilder b; b.vr[4]=aa;b.vr[5]=bb;
  InstrData i;
  if(InstrEmit_vcmpbfp_(b,i,3,4,5,record?1:0))std::abort();
  std::array<uint32_t,4> expected{};
  bool all_inside=true;
  for(int index=0;index<4;++index) {
    float a=std::bit_cast<float>(aa[index]);
    float bv=std::bit_cast<float>(bb[index]);
    uint32_t res=!(a<=bv)?0x80000000u:0u;
    res|=!(a>=-bv)?0x40000000u:0u;
    expected[index]=res;
    all_inside &= res==0;
  }
  if(b.vr_result != expected || (record && (b.cr6_all || b.cr6_none!=all_inside || b.cr6_middle))) {
    std::printf("FAIL vcmpbfp record=%d\n",record);
    std::abort();
  }
}
int main() {
  const std::array<uint64_t,12> operands={
    0ull,1ull,0x7fffffffull,0x80000000ull,
    0xffffffffull,0xffffffffffffffffull,
    0x8000000000000000ull,0x8000000000000001ull,
    0xc000000000000000ull,0x7fffffffffffffffull,
    0x0000000100000000ull,0xffffffff80000000ull
  };
  std::mt19937_64 random(0x5A475048ULL);
  uint64_t shifted=0, compared=0;
  for(unsigned w:{32u,64u}) {
    for(uint64_t operand:operands) {
      for(unsigned count=0;count<256;++count) {
        CheckShift(w,operand,count,false);
        CheckShift(w,operand,count,true);
        shifted+=2;
      }
    }
    for(unsigned i=0;i<25000;++i) {
      CheckShift(w,random(),random()&255,true);
      ++shifted;
    }
  }
  const std::array<uint32_t,16> floatbits={
    0u,0x80000000u,0x3f800000u,0xbf800000u,
    0x40a00000u,0xc0a00000u,0x40e00000u,0xc0e00000u,
    0x7f800000u,0xff800000u,0x7fc00000u,0xffc00001u,
    0x7f800001u,0xffa00000u,0x00000001u,0x80800000u
  };
  for(uint32_t va:floatbits)for(uint32_t vb:floatbits) {
    std::array<uint32_t,4> aa{va,va,va,va},bb{vb,vb,vb,vb};
    CheckVcmp(aa,bb,true);++compared;
  }
  for(unsigned k=0;k<15000;++k) {
    std::array<uint32_t,4> aa{},bb{};
    for(int lane=0;lane<4;++lane) {
      aa[lane]=floatbits[random()%floatbits.size()];
      bb[lane]=floatbits[random()%floatbits.size()];
    }
    CheckVcmp(aa,bb,k%2==0);
    ++compared;
  }
  std::printf("PASS: %llu production shift emitter cases and %llu vector bounds cases\n",
    (unsigned long long)shifted,(unsigned long long)compared);
}
"""
for tag, name in (("SRAW_FUNCTION", "SRAW"), ("SRAD_FUNCTION", "SRAD"),
                  ("VCMPBFP_FUNCTION", "VCMPBFP")):
    cpp = cpp.replace(tag, functions[name])

compiler = os.environ.get("CXX") or shutil.which("clang++") or shutil.which("g++")
if not compiler:
    raise RuntimeError("clang++ or g++ required")
with tempfile.TemporaryDirectory(prefix="zephyr-ppc-recomp-") as temp:
    src = Path(temp) / "test.cpp"
    exe = Path(temp) / "test"
    src.write_text(cpp, encoding="utf-8")
    subprocess.run(
        [compiler, "-std=c++20", "-O2", "-fno-fast-math",
         "-fsanitize=undefined", "-fno-sanitize-recover=undefined",
         str(src), "-o", str(exe)],
        check=True)
    subprocess.run([str(exe)], check=True)
