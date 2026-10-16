#!/usr/bin/env python3
"""Execute the real GL-less query methods without a GPU, native allocator or Gradle.

Only the stub's unrelated renderer shell, backend and byte-addressed allocator are
replaced. Query bodies, matrix selection, numeric writes and the complete hint
class come from the Java sources. The real native emitter is executed even on CI
without ASM; when cached ASM is available the complete transformer additionally
rewrites a native fixture which the JVM verifies and calls end-to-end.
"""
from pathlib import Path
import os
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
STUB = ROOT / "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java"
CORE = ROOT / "forge122/src/main/java/org/taumc/celeritas/core"


def method(source, signature):
    start = source.index(signature)
    begin = source.index("{", start)
    depth, end = 1, begin + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


MEMORY = r"""package org.lwjgl.system;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
public class MemoryUtil {
 public static final ByteBuffer bytes=ByteBuffer.allocate(224).order(ByteOrder.nativeOrder());
 public static int writes;
 public static void reset(){Arrays.fill(bytes.array(),(byte)0xa5);writes=0;}
 private static int address(long p,int size){
  if(p<=0 || p>bytes.capacity()-size)throw new AssertionError("invalid native write: "+p+"/"+size);
  writes++;return (int)p;
 }
 public static void memPutFloat(long p,float v){bytes.putFloat(address(p,4),v);}
 public static void memPutDouble(long p,double v){bytes.putDouble(address(p,8),v);}
 public static void memPutInt(long p,int v){bytes.putInt(address(p,4),v);}
 public static void memPutByte(long p,byte v){bytes.put(address(p,1),v);}
 // Unused string/window helpers merely allow the complete production hint class to compile.
 public static ByteBuffer memASCII(CharSequence s){return ByteBuffer.wrap((s+"\0").getBytes(StandardCharsets.US_ASCII));}
 public static long memAddress(ByteBuffer b){throw new AssertionError("query unexpectedly requested a string pointer");}
}
"""

STUB_SHELL = r"""package org.soutalab.celtium.vulkan.gl;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.system.MemoryUtil.memPutInt;
public class CeltiumGlStub {
 public static CeltiumGlStub installed;
 // Unrelated clear entry points are only needed to compile the complete hint class.
 public static void celtium$clear(int mask){}
 public static void celtium$clearColor(float r,float g,float b,float a){}
 public static void celtium$clearDepth(double depth){}
 public float currentRed=.125f,currentGreen=.375f,currentBlue=.625f,currentAlpha=.875f,lineWidth=3.5f;
 public int viewportX=-17,viewportY=23,viewportW=1920,viewportH=1080;
 public int[] scissorBox={11,31,199,60};
 public int activeTexture=3,drawFbo=41,readFbo=43,compilingList=47,compilingListMode=4864,currentProgram=53,boundVertexArray=59;
 // The hint publishes the open list here (CeltiumGllessHint.glNewList/glEndList), so the shell carries the
 // same two entry points the production stub does; GL_LIST_INDEX answers from the fields above.
 public static void celtium$noteListOpened(int list,int mode){installed.compilingList=list;installed.compilingListMode=mode;}
 public static void celtium$noteListClosed(){installed.compilingList=0;installed.compilingListMode=0;}
 public static int celtium$boundTexture(){return -1;}
 // The complete hint records texture parameters here (glTexParameteri is one of the calls the
 // transformer rewrites), so the shell carries the entry point as well.
 public static void celtium$texParameteri(int target,int pname,int value){}
 // The complete hint also serves texture copies (glCopyTexSubImage2D/glCopyTexImage2D are calls the
 // transformer rewrites); this contract is about the query paths, so no-ops are enough.
 public static void celtium$copyTexSubImage2D(int target,int level,int xo,int yo,int x,int y,int w,int h){}
 public static void celtium$copyTexImage2D(int target,int level,int internal,int x,int y,int w,int h){}
 // The complete hint routes the client-array/VBO draw family (VertexBuffer): these are the
 // stub entry points it calls, no-ops here because this contract is about other paths.
 public static void celtium$drawArrays(int mode,int first,int count){}
 public static void celtium$bindBuffer(int target,int name){}
 public static void celtium$clientState(int cap,boolean enabled){}
 public static void celtium$vertexPointer(int size,int type,int stride,long pointer){}
 public static void celtium$bufferData(int target,long size,long address,int usage){}
 public int lastBoundTexture=-1;
 static class Bindings {
  java.util.Map<Long,Integer> values=new java.util.HashMap<>();
  int get(long key){return values.getOrDefault(key,-1);}
 }
 Bindings textureBindings=new Bindings();
 public CeltiumGlStub(){textureBindings.values.put(texBindKey(0x0DE1),61);}
 static class Fn {final String name;Fn(String name){this.name=name;}}
 public static void legacyQuery(int pname,long out,boolean asDouble){
  installed.getFloats(pname,out,new Fn(asDouble?"glGetDoublev":"glGetFloatv"));
 }
"""

BACKEND = r"""package org.soutalab.celtium.vulkan;
public class VulkanTerrainGpuBackend implements org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend {
 public float[] modelView,projection,texture;
 public int[] viewport;public int viewportCalls;
 public void viewport(int x,int y,int w,int h){viewport=new int[]{x,y,w,h};viewportCalls++;}
 public float[] celtium$mirroredModelView(){return modelView==null?null:modelView.clone();}
 public float[] celtium$mirroredProjection(){return projection==null?null:projection.clone();}
 public float[] celtium$mirroredTexture(){return texture==null?null:texture.clone();}
}
"""

CHECK = r"""
import java.util.*;
import org.lwjgl.system.MemoryUtil;
import org.soutalab.celtium.vulkan.gl.CeltiumGlStub;
import org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend;
import org.taumc.celeritas.core.CeltiumGllessHint;
public class MatrixQueryCheck {
 static final int MODELVIEW=0x0BA6,PROJECTION=0x0BA7,TEXTURE=0x0BA8,COLOR=0x0B00;
 static final int[] PNAMES={MODELVIEW,PROJECTION,TEXTURE,COLOR,0x0B70,0x0B21,0x0B22,0x0D3A,0xdead};
 static final int OUT=32;
 static int checks;
 @FunctionalInterface interface Query {void get(int pname,long out,boolean doubles)throws Exception;}
 @FunctionalInterface interface IntQuery {void get(int pname,long out)throws Exception;}
 @FunctionalInterface interface Viewport {void set(int x,int y,int w,int h)throws Exception;}
 static void require(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
 static void bounds(int count,int width){
  for(int i=0;i<MemoryUtil.bytes.capacity();i++)
   if(i<OUT || i>=OUT+count*width)
    require(MemoryUtil.bytes.get(i)==(byte)0xa5,"write outside "+count+" x "+width+" at byte "+i);
 }
 static void expect(Query query,int pname,boolean doubles,float... expected)throws Exception{
  MemoryUtil.reset();query.get(pname,OUT,doubles);int width=doubles?8:4;
  for(int i=0;i<expected.length;i++){
   long actual=doubles?MemoryUtil.bytes.getLong(OUT+i*width):MemoryUtil.bytes.getInt(OUT+i*width);
   long wanted=doubles?Double.doubleToRawLongBits((double)expected[i]):Float.floatToRawIntBits(expected[i]);
   require(actual==wanted,"pname "+Integer.toHexString(pname)+" "+(doubles?"double":"float")+"["+i+"]");
  }
  bounds(expected.length,width);
  require(MemoryUtil.writes==expected.length,"query did not write exactly its component count");
 }
 static void expectInt(IntQuery query,int pname,int... expected)throws Exception{
  MemoryUtil.reset();query.get(pname,OUT);
  for(int i=0;i<expected.length;i++)require(MemoryUtil.bytes.getInt(OUT+i*4)==expected[i],"integer pname "+pname+"["+i+"]");
  bounds(expected.length,4);
  require(MemoryUtil.writes==expected.length,"integer query component count");
 }
 static float[] columnMajor(float[][] rows){
  float[] result=new float[16];
  for(int row=0;row<4;row++)for(int col=0;col<4;col++)result[col*4+row]=rows[row][col];
  return result;
 }
 static VulkanTerrainGpuBackend backend(){
  VulkanTerrainGpuBackend b=new VulkanTerrainGpuBackend();
  // Non-symmetric matrices: off-diagonals, translations and perspective terms expose transposition.
  b.modelView=columnMajor(new float[][]{{.36f,.48f,-.8f,12.25f},{-.8f,.60f,0,-7.5f},{.48f,.64f,.60f,3.125f},{0,0,0,1}});
  b.projection=columnMajor(new float[][]{{1.25f,0,.15f,0},{0,1.75f,-.25f,0},{0,0,-1.002f,-.2002f},{0,0,-1,0}});
  b.texture=columnMajor(new float[][]{{2,.125f,0,.25f},{-.375f,3,0,.75f},{0,0,1,0},{0,0,0,1}});
  return b;
 }
 static void matrices(Query query,VulkanTerrainGpuBackend b,boolean doubles)throws Exception{
  expect(query,MODELVIEW,doubles,b.modelView);
  expect(query,PROJECTION,doubles,b.projection);
  expect(query,TEXTURE,doubles,b.texture);
 }
 static void floats(String label,Query query,boolean bridge)throws Exception{
  CeltiumGlStub stub=CeltiumGlStub.installed=new CeltiumGlStub();
  VulkanTerrainGpuBackend b=backend();TerrainGpuBackends.backend=b;
  for(boolean doubles:new boolean[]{false,true}){
   matrices(query,b,doubles);
   expect(query,COLOR,doubles,.125f,.375f,.625f,.875f);
   expect(query,0x0B70,doubles,0,1);expect(query,0x0B21,doubles,3.5f);
   expect(query,0x0B22,doubles,1);expect(query,0x0D3A,doubles,1,3.5f);expect(query,0xdead,doubles,0);
   // Mutate the same mirror and replace its arrays: queries must not retain snapshots.
   b.modelView[12]+=2.125f;b.projection[8]-=.0625f;b.texture[1]=-.0f;
   matrices(query,b,doubles);
   b.modelView=b.modelView.clone();b.modelView[2]=-19.75f;
   b.projection=b.projection.clone();b.projection[14]=-.4004f;
   b.texture=b.texture.clone();b.texture[13]=.8125f;
   matrices(query,b,doubles);
   // Replacing the current backend must be observed as well as mutating the old one.
   VulkanTerrainGpuBackend next=backend();next.modelView[13]=91.25f;
   TerrainGpuBackends.backend=next;matrices(query,next,doubles);TerrainGpuBackends.backend=b;
   stub.currentRed=.25f;stub.lineWidth=7.25f;
   expect(query,COLOR,doubles,.25f,.375f,.625f,.875f);expect(query,0x0B21,doubles,7.25f);
   stub.currentRed=.125f;stub.lineWidth=3.5f;
   for(int pname:PNAMES){MemoryUtil.reset();query.get(pname,0,doubles);bounds(0,1);require(MemoryUtil.writes==0,"null output write");}
  }
  float[] identity=columnMajor(new float[][]{{1,0,0,0},{0,1,0,0},{0,0,1,0},{0,0,0,1}});
  for(TerrainGpuBackend absent:new TerrainGpuBackend[]{null,new TerrainGpuBackend(){},new VulkanTerrainGpuBackend()}){
   TerrainGpuBackends.backend=absent;
   for(boolean doubles:new boolean[]{false,true})for(int pname:new int[]{MODELVIEW,PROJECTION,TEXTURE})expect(query,pname,doubles,identity);
  }
  if(bridge){
   CeltiumGlStub.installed=null;TerrainGpuBackends.backend=b;
   for(boolean doubles:new boolean[]{false,true})for(int pname:PNAMES){
    // Before installation only one numeric zero is safe, even for known vector/matrix names.
    expect(query,pname,doubles,0);MemoryUtil.reset();query.get(pname,0,doubles);bounds(0,1);
    require(MemoryUtil.writes==0,"uninstalled null output write");
   }
  }
  System.out.println(label+": matrix16 column-major, float/double widths, live state, vectors/scalars, byte bounds, null/fallback PASS");
 }
 static void integers(String label,IntQuery query,Viewport setter)throws Exception{
  CeltiumGlStub s=CeltiumGlStub.installed=new CeltiumGlStub();
  VulkanTerrainGpuBackend backend=backend();TerrainGpuBackends.backend=backend;
  expectInt(query,0x0BA2,-17,23,1920,1080);expectInt(query,0x0C10,11,31,199,60);
  setter.set(7,29,256,128);
  expectInt(query,0x0BA2,7,29,256,128);expectInt(query,0x0C10,11,31,199,60);
  require(backend.viewportCalls==1 && Arrays.equals(backend.viewport,new int[]{7,29,256,128}),"viewport not forwarded to backend");
  s.scissorBox=new int[]{-3,5,0,0};setter.set(-13,17,512,384);
  expectInt(query,0x0BA2,-13,17,512,384);expectInt(query,0x0C10,-3,5,0,0);
  TerrainGpuBackends.backend=null;setter.set(0,0,0,0);
  expectInt(query,0x0BA2,0,0,0,0);expectInt(query,0x0C10,-3,5,0,0);
  int[][] scalars={{0x8069,61},{0x84E0,0x84C3},{0x8CA6,41},{0x8CA5,43},{0x0B33,47},{0x8B8D,53},{0x85B5,59},{0x0D33,16384}};
  for(int[] pair:scalars)expectInt(query,pair[0],pair[1]);
  s.currentProgram=97;s.activeTexture=6;expectInt(query,0x8B8D,97);expectInt(query,0x84E0,0x84C6);expectInt(query,0x8069,0);
  expectInt(query,0x0D3B,16384,16384);expectInt(query,0xdead,0);
  for(int pname:new int[]{0x0BA2,0x0C10,0x8B8D,0x0D3B,0xdead}){
   MemoryUtil.reset();query.get(pname,0);bounds(0,1);require(MemoryUtil.writes==0,"null integer output");
  }
  // The bridge reports unhandled names without touching the caller's allocation.
  MemoryUtil.reset();require(!CeltiumGlStub.celtium$getIntegers(0xdead,OUT),"untracked integer handled");bounds(0,1);
  require(!CeltiumGlStub.celtium$getIntegers(0x0BA2,0),"null integer handled");
  CeltiumGlStub.installed=null;TerrainGpuBackends.backend=backend;
  setter.set(31,37,1920,1080);
  require(Arrays.equals(backend.viewport,new int[]{31,37,1920,1080}),"uninstalled viewport did not reach backend");
  TerrainGpuBackends.backend=null;setter.set(0,0,1,1);
  MemoryUtil.reset();require(!CeltiumGlStub.celtium$getIntegers(0x0BA2,OUT),"uninstalled integer handled");bounds(0,1);
  expectInt(query,0x0D33,16384);expectInt(query,0x0D3B,16384,16384);expectInt(query,0x0BA2,0);expectInt(query,0xdead,0);
  MemoryUtil.reset();query.get(0x0BA2,0);bounds(0,1);
  System.out.println(label+": live viewport/scissor4, tracked scalar bindings, bootstrap limits, byte bounds/null PASS");
 }
 public static void main(String[] args)throws Exception{
  floats("shared stub helper",CeltiumGlStub::celtium$getFloats,true);
  floats("callback Fn overload",CeltiumGlStub::legacyQuery,false);
  floats("native hints",(p,out,d)->{if(d)CeltiumGllessHint.glGetDoublev(p,out);else CeltiumGllessHint.glGetFloatv(p,out);},true);
  integers("native integer/viewport hints",CeltiumGllessHint::glGetIntegerv,CeltiumGllessHint::glViewport);
 }
}
"""

# Execute the production emitter without any external dependency. This is not a
# substring assertion: incorrect descriptors, argument slots or helper choice fail.
EMITTER = r"""
import java.util.*;
import org.taumc.celeritas.core.CeltiumGllessHint;
public class NativeEmitterCheck {
 interface Opcodes {
  int ACONST_NULL=1,ICONST_M1=2,ICONST_0=3,LCONST_0=9,FCONST_0=11,DCONST_0=14;
  int ILOAD=21,LLOAD=22,FLOAD=23,DLOAD=24,F2D=141,IRETURN=172,LRETURN=173,FRETURN=174,DRETURN=175,ARETURN=176,RETURN=177,INVOKESTATIC=184;
 }
 static class MethodVisitor {
  List<String> events=new ArrayList<>();
  void visitInsn(int op){events.add("insn:"+op);}
  void visitVarInsn(int op,int slot){events.add("var:"+op+":"+slot);}
  void visitLdcInsn(Object value){events.add("const:"+value);}
  void visitMethodInsn(int op,String owner,String name,String desc,boolean itf){events.add("call:"+op+":"+owner+":"+name+":"+desc+":"+itf);}
 }
 __METHODS__
 static void check(String nativeName,String helper){
  MethodVisitor visitor=new MethodVisitor();emitDefaultReturn(visitor,nativeName,"(IJ)V");
  List<String> expected=List.of("var:21:0","var:22:1","call:184:org/taumc/celeritas/core/CeltiumGllessHint:"+helper+":(IJ)V:false","insn:177");
  if(!visitor.events.equals(expected))throw new AssertionError(nativeName+" dispatch: "+visitor.events);
 }
 public static void main(String[] args){
  check("nglGetFloatv","glGetFloatv");check("nglGetDoublev","glGetDoublev");check("nglGetIntegerv","glGetIntegerv");
  MethodVisitor viewport=new MethodVisitor();emitDefaultReturn(viewport,"glViewport","(IIII)V");
  if(!viewport.events.equals(List.of("var:21:0","var:21:1","var:21:2","var:21:3",
    "call:184:org/taumc/celeritas/core/CeltiumGllessHint:glViewport:(IIII)V:false","insn:177")))
   throw new AssertionError("viewport dispatch: "+viewport.events);
  for(String name:new String[]{"nglGetFloatv","nglGetDoublev","nglGetFloatvExtra","xnglGetDoublev"})
   for(String desc:new String[]{"(IIJ)V","(IJJ)V","(IJ)I"}){
    MethodVisitor visitor=new MethodVisitor();emitDefaultReturn(visitor,name,desc);
    for(String event:visitor.events)
     if(event.contains(":glGetFloatv:") || event.contains(":glGetDoublev:"))throw new AssertionError("matrix helper accepted "+name+desc);
   }
  for(String name:new String[]{"nglGetFloatvExtra","xnglGetDoublev"}){
   MethodVisitor visitor=new MethodVisitor();emitDefaultReturn(visitor,name,"(IJ)V");
   for(String event:visitor.events)
    if(event.contains(":glGetFloatv:") || event.contains(":glGetDoublev:"))throw new AssertionError("matrix helper accepted "+name);
  }
  System.out.println("real native emitter: exact names/descriptors, ILOAD/LLOAD slots, float/double/integer helper choice PASS");
 }
}
"""

NATIVE_CHECK = r"""
import org.objectweb.asm.*;
import java.lang.reflect.Method;
import java.util.*;
import org.taumc.celeritas.core.CeltiumGllessNativeTransformer;
public class NativeMatrixQueryCheck {
 static final String NAME="org/lwjgl/opengl/MatrixQueryFixture";
 static class Loader extends ClassLoader {Class<?> load(byte[] bytes){return defineClass(NAME.replace('/','.'),bytes,0,bytes.length);}}
 static byte[] fixture(){
  ClassWriter writer=new ClassWriter(0);
  writer.visit(Opcodes.V21,Opcodes.ACC_PUBLIC,NAME,null,"java/lang/Object",null);
  String[][] methods={{"nglGetFloatv","(IJ)V"},{"nglGetDoublev","(IJ)V"},{"nglGetIntegerv","(IJ)V"},{"glViewport","(IIII)V"},
   {"glFenceSync","(II)J"},{"glClientWaitSync","(JIJ)I"},
   {"defaultVoid","()V"},{"defaultBoolean","()Z"},{"defaultByte","()B"},{"defaultChar","()C"},{"defaultShort","()S"},
   {"defaultInt","()I"},{"defaultLong","()J"},{"defaultFloat","()F"},{"defaultDouble","()D"},
   {"defaultObject","()Ljava/lang/Object;"},{"defaultArray","()[F"}};
  for(String[] m:methods)writer.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC|Opcodes.ACC_NATIVE,m[0],m[1],null,null).visitEnd();
  MethodVisitor javaMethod=writer.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"untouched","()I",null,null);
  javaMethod.visitCode();javaMethod.visitIntInsn(Opcodes.BIPUSH,73);javaMethod.visitInsn(Opcodes.IRETURN);javaMethod.visitMaxs(1,0);javaMethod.visitEnd();
  writer.visitEnd();return writer.toByteArray();
 }
 public static void main(String[] args)throws Exception{
  byte[] original=fixture();CeltiumGllessNativeTransformer transformer=new CeltiumGllessNativeTransformer();
  MatrixQueryCheck.require(transformer.transform("other.Type","other.Type",original)==original,"transformed unrelated package");
  MatrixQueryCheck.require(transformer.transform("org.lwjgl.opengl.Display",null,original)==original,"transformed Display");
  MatrixQueryCheck.require(transformer.transform(NAME.replace('/','.'),null,null)==null,"null input");
  byte[] bytes=transformer.transform(NAME.replace('/','.'),NAME.replace('/','.'),original);
  int[] queries={0};
  new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9){
   public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
    MatrixQueryCheck.require((access&Opcodes.ACC_NATIVE)==0,"transformer kept a native method: "+name);
    if(!name.startsWith("nglGet"))return null;queries[0]++;
    return new MethodVisitor(Opcodes.ASM9){
     public void visitMaxs(int stack,int locals){
      MatrixQueryCheck.require(stack>=3,"int + pointer needs three stack slots: "+name+"="+stack);
      MatrixQueryCheck.require(locals>=3,"int + pointer needs three local slots: "+name+"="+locals);
     }
    };
   }
  },0);
  MatrixQueryCheck.require(queries[0]==3,"fixture query count");
  Class<?> type=new Loader().load(bytes);
  Method floats=type.getMethod("nglGetFloatv",int.class,long.class),doubles=type.getMethod("nglGetDoublev",int.class,long.class);
  Method integers=type.getMethod("nglGetIntegerv",int.class,long.class);
  Method viewport=type.getMethod("glViewport",int.class,int.class,int.class,int.class);
  MatrixQueryCheck.floats("transformed native methods",(p,out,d)->(d?doubles:floats).invoke(null,p,out),true);
  MatrixQueryCheck.integers("transformed native integer/viewport methods",(p,out)->integers.invoke(null,p,out),(x,y,w,h)->viewport.invoke(null,x,y,w,h));
  MatrixQueryCheck.require((int)type.getMethod("untouched").invoke(null)==73,"changed a non-native method");
  MatrixQueryCheck.require((long)type.getMethod("glFenceSync",int.class,int.class).invoke(null,0,0)>0,"fence token");
  MatrixQueryCheck.require((int)type.getMethod("glClientWaitSync",long.class,int.class,long.class).invoke(null,1L,0,1L)==0x911A,"five-slot fence wait");
  String[] defaults={"defaultVoid","defaultBoolean","defaultByte","defaultChar","defaultShort","defaultInt","defaultLong","defaultFloat","defaultDouble","defaultObject","defaultArray"};
  Object[] expected={null,false,(byte)0,'\0',(short)0,0,0L,0f,0d,null,null};
  for(int i=0;i<defaults.length;i++)MatrixQueryCheck.require(Objects.equals(type.getMethod(defaults[i]).invoke(null),expected[i]),"default return: "+defaults[i]);
  System.out.println("complete transformer: JVM verification, three-slot queries, five-slot fence, all return types/non-native preservation PASS");
 }
}
"""


def asm_jar():
    cache = Path.home() / ".gradle/caches/modules-2/files-2.1/org.ow2.asm/asm"
    jars = sorted(cache.glob("9.*/*/asm-9.*.jar"), reverse=True)
    return jars[0] if jars else None


def main():
    stub = STUB.read_text(encoding="utf-8")
    hint = (CORE / "CeltiumGllessHint.java").read_text(encoding="utf-8")
    transformer = (CORE / "CeltiumGllessNativeTransformer.java").read_text(encoding="utf-8")
    signatures = [
        "private void getFloats(int pname, long out, Fn fn)",
        "public static void celtium$getFloats(",
        "private void getFloats(int pname, long out, boolean asDouble)",
        "private static float[] mirroredMatrix(",
        "private static void putFloatOrDouble(",
        "public static boolean celtium$getIntegers(",
        "public static void celtium$viewport(",
        "private void getInts(",
        "private int getIntegerValue(",
        "private long texBindKey(",
    ]
    emitter_methods = "\n".join(method(transformer, signature) for signature in [
        "private static void emitDefaultReturn(", "private static int lastArgumentSlot(",
    ])
    for name in ("GET_ERROR_DESC", "IDENTITY"):
        declaration = re.search(r"private static final String " + name + r"\s*=\s*[^;]+;", transformer)
        if declaration is None:
            raise AssertionError("missing native emitter constant: " + name)
        emitter_methods += "\n" + declaration.group()
    files = {
        "org/lwjgl/system/MemoryUtil.java": MEMORY,
        "org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java": STUB_SHELL + "\n".join(method(stub, s) for s in signatures) + "\n}",
        "org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java": BACKEND,
        "org/embeddedt/embeddium/impl/render/chunk/backend/TerrainGpuBackend.java": """package org.embeddedt.embeddium.impl.render.chunk.backend;
public interface TerrainGpuBackend {default void viewport(int x,int y,int w,int h){} default void flushBeforeClear(int mask){}}""",
        "org/embeddedt/embeddium/impl/render/chunk/backend/TerrainGpuBackends.java": """package org.embeddedt.embeddium.impl.render.chunk.backend;
public class TerrainGpuBackends {public static TerrainGpuBackend backend;public static TerrainGpuBackend current(){return backend;}}""",
        "org/taumc/celeritas/core/CeltiumGllessHint.java": hint,
        "org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java": """package org.taumc.celeritas.mixin;
public class CeleritasVintageMixinPlugin {
 public static final Log LOGGER=new Log();
 public static class Log {
  public void info(String message,Object... args){}
  public void warn(String message,Object... args){}
  public void error(String message,Object... args){throw new AssertionError(message, (Throwable)args[args.length-1]);}
 }
}""",
        "MatrixQueryCheck.java": CHECK,
        "NativeEmitterCheck.java": EMITTER.replace("__METHODS__", emitter_methods),
    }
    javac, java = shutil.which("javac"), shutil.which("java")
    if not javac or not java:
        raise RuntimeError("Java 21+ compiler and runtime are required")
    asm = asm_jar()
    if asm:
        files.update({
            "org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java": transformer,
            "net/minecraft/launchwrapper/IClassTransformer.java": """package net.minecraft.launchwrapper;
public interface IClassTransformer {byte[] transform(String name,String transformedName,byte[] basicClass);}""",
            "NativeMatrixQueryCheck.java": NATIVE_CHECK,
        })
    with tempfile.TemporaryDirectory(prefix="celtium-matrix-contract-") as tmp:
        work = Path(tmp)
        for name, content in files.items():
            target = work / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content, encoding="utf-8")
        classpath = str(work) + (os.pathsep + str(asm) if asm else "")
        subprocess.run([javac, "--release", "21", "-cp", classpath, "-d", str(work),
                        *[str(work / name) for name in files]], check=True)
        for test in ("NativeEmitterCheck", "MatrixQueryCheck") + (("NativeMatrixQueryCheck",) if asm else ()):
            subprocess.run([java, "-Xverify:all", "-cp", classpath, test], check=True)
    if not asm:
        print("skip: cached ASM9 unavailable; real emitter and executable query contracts above still ran")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
