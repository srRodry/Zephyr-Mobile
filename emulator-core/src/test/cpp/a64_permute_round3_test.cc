#include "permute.h"
using V=std::array<uint8_t,16>;
using R=std::array<V,32>;
static uint64_t seed=0x143523988abcULL;
static uint64_t random64(){seed^=seed<<13;seed^=seed>>7;seed^=seed<<17;return seed;}
static V bytes(const vec128_t& v){V r;std::memcpy(r.data(),&v,16);return r;}
static void execute(const A64Emitter& e,R& regs){
 auto code=e.getCode<const uint32_t*>();size_t pc=0;
 while(pc<e.getSize()/4){uint32_t w=code[pc],d=w&31,n=(w>>5)&31,m=(w>>16)&31;
  auto a=regs[n],b=regs[m];auto op=w&0xffe0fc00u;
  if((w&0xff000000u)==0x9c000000u){int32_t off=int32_t(w<<8)>>13;size_t target=int64_t(pc)+off;
   assert(target*4+16<=e.getSize());std::memcpy(regs[d].data(),code+target,16);++pc;continue;}
  if((w&0xfc000000u)==0x14000000u){int32_t off=int32_t(w<<6)>>6;pc=int64_t(pc)+off;continue;}
  if(op==0x6e200800u){for(int k=0;k<16;k++)regs[d][k]=a[k^3];}
  else if(op==0x4ea00800u){for(int k=0;k<16;k++)regs[d][k]=a[k^4];}
  else if(op==0x4e201c00u){for(int k=0;k<16;k++)regs[d][k]=a[k]&b[k];}
  else if(op==0x4ea01c00u){for(int k=0;k<16;k++)regs[d][k]=a[k]|b[k];}
  else if(op==0x6e201c00u){for(int k=0;k<16;k++)regs[d][k]=a[k]^b[k];}
  else if((w&0xfff8fc00u)==0x4f00e400u){uint8_t imm=((w>>5)&31)|(((w>>16)&7)<<5);regs[d].fill(imm);}
  else if((w&0xff20bc00u)==0x4e003800u){
   int lane=1<<((w>>22)&3),half=(w>>14)&1,first=half*(8/lane);
   for(int k=0;k<8/lane;k++)for(int j=0;j<lane;j++){
    regs[d][(2*k)*lane+j]=a[(first+k)*lane+j];regs[d][(2*k+1)*lane+j]=b[(first+k)*lane+j];}
  }else if((w&0xffe0dc00u)==0x4e000000u){
   const bool two=(w&0x2000u)!=0;assert(!two || n<31);auto second=two?regs[n+1]:V{};for(int k=0;k<16;k++){int i=b[k];regs[d][k]=i<16?a[i]:two && i<32?second[i-16]:0;}
  }else{std::fprintf(stderr,"unknown encoding %08x\n",w);assert(false);}++pc;
 }
}
static void check(bool half,int mask,int dst,int ctrl,int a,int b,int pattern){
 R regs{};for(auto& v:regs)for(auto& x:v)x=uint8_t(random64());
 using Args=PERMUTE_V128::EmitArgType;Args args;
 args.dest.index=dst;args.src1.index=ctrl;args.src2.index=a;args.src3.index=b;
 for(auto pair:{std::pair{&args.src1,1},std::pair{&args.src2,2},std::pair{&args.src3,4}}){
  pair.first->is_constant=(mask&pair.second)!=0;pair.first->value.low=random64();pair.first->value.high=random64();}
 if(pattern){args.src1.is_constant=true;
  args.src1.value=half?(pattern==1?vec128s(0,8,1,9,2,10,3,11):vec128s(4,12,5,13,6,14,7,15)):
   (pattern==1?vec128b(0,16,1,17,2,18,3,19,4,20,5,21,6,22,7,23):vec128b(8,24,9,25,10,26,11,27,12,28,13,29,14,30,15,31));
 }
 auto c=args.src1.is_constant?bytes(args.src1.value):regs[ctrl];
 auto x=args.src2.is_constant?bytes(args.src2.value):regs[a];
 auto y=args.src3.is_constant?bytes(args.src3.value):regs[b];V expected{};
 if(!half){for(int k=0;k<16;k++){int i=(c[k]^3)&31;expected[k]=i<16?x[i]:y[i-16];}}
 else{for(int k=0;k<8;k++){int h=(c[2*k]|(c[2*k+1]<<8))&15,i=((h&7)^1)*2;auto& src=h<8?x:y;
  expected[2*k]=src[i];expected[2*k+1]=src[i+1];}}
 A64Emitter e;if(half)PERMUTE_V128::EmitByInt16(e,args);else PERMUTE_V128::EmitByInt8(e,args);e.ready();
 if(!half && !mask && !pattern)assert(e.getSize()==(a==b?16:20)); // 5 actual instructions, previously 7
 if(half && pattern && !(mask&6))assert(e.getSize()==8); // ZIP + REV64
 if(!half && pattern && !(mask&6) && a!=b)assert(e.getSize()==16); // 2 REV32 + ZIP + REV32
 auto before=regs;execute(e,regs);assert(regs[dst]==expected);
 for(int r=4;r<32;r++)if(r!=dst)assert(regs[r]==before[r]);
}
int main(){size_t count=0;
 for(int half=0;half<2;half++)for(int mask=0;mask<8;mask++){
  if(half && !(mask&1))continue;
  for(int d:{4,5,6,31})for(int alias=0;alias<4;alias++)for(int pattern=0;pattern<3;pattern++)for(int r=0;r<128;r++){
   check(half,mask,d,4,alias==1?4:5,alias==2 || alias==3?5:6,pattern);++count;}}
 std::printf("A64 permutes: %zu bit-exact encoded cases; constant/register operands, aliases, merge fast paths and non-destination preservation passed. Dynamic vperm: 5 instructions.\n",count);
}
