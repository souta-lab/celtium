#!/usr/bin/env python3
"""Execute named texture allocation, real captured-surface patches and query boundaries.

Production allocation/retirement, undefined-surface creation, conversion/patch,
metadata queries, backend size query and mixin handlers are extracted verbatim.
The registry's real fill/announcement methods and VulkanGlTexture's real captured
upload entry point also run. Only native memory/maps/GL and Vulkan transfer calls
are replaced. The transfer spy records calls; it does NOT assemble texture pixels
or establish GPU acceptance. Pixel placement is checked on the real stub surface.
"""
from pathlib import Path
import argparse
import re
import runpy
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan"
STUB = JAVA / "gl/CeltiumGlStub.java"
BACKEND = JAVA / "VulkanTerrainGpuBackend.java"
REGISTRY = JAVA / "VulkanTextureRegistry.java"
TEXTURE = JAVA / "VulkanGlTexture.java"
MIXIN = ROOT / "forge122/src/main/java/org/taumc/celeritas/mixin/core/TextureUtilMixin.java"
method = runpy.run_path(str(Path(__file__).with_name("draw_state_provenance_check.py")))["method"]


def field(source, name):
    pattern = (r"(?m)^[ \t]*private[ \t]+(?:(?:static|final|volatile)[ \t]+)*"
               r"[^\n=;]+?\b" + re.escape(name) + r"\s*(?=[=;,])")
    match = re.search(pattern, source)
    if not match:
        raise ValueError("missing production field: " + name)
    return source[match.start():source.index(";", match.end()) + 1].strip()


def annotated(source, signature):
    body = method(source, signature)
    start = source.index(signature)
    annotation = source.rfind("@Inject(", 0, start)
    if annotation < 0:
        raise ValueError("missing injection annotation: " + signature)
    return source[annotation:start] + body


STUB_FIELDS = ["installed", "lastBoundTexture", "textureBindings", "activeTexture", "texLevels", "texCaptures",
               "texUploadSerial", "undefinedExtents", "unconvertedUploads", "subCaptures",
               "identicalPatches", "unpackAlignment", "unpackRowLength", "unpackSkipPixels",
               "MAX_CAPTURE_BYTES", "GL_RGBA", "GL_RGB", "GL_BGRA", "GL_UNSIGNED_BYTE",
               "GL_UNSIGNED_INT_8_8_8_8_REV", "texParams", "TEX_WRAP_CLAMP_CONTROL",
               "GL_TEXTURE_WRAP_S", "GL_TEXTURE_WRAP_T", "GL_TEXTURE_MAG_FILTER",
               "GL_TEXTURE_MIN_FILTER", "GL_CLAMP_TO_EDGE", "GL_LINEAR", "GL_REPEAT",
               "GL_NEAREST_MIPMAP_LINEAR"]
STUB_METHODS = ["public static boolean isInstalled(", "public static void recordTextureAllocation(",
                "public static void forgetTextureAllocation(", "private void forgetTexture(",
                "private void undefinedLevel(", "private static long texLevelKey(",
                "public static int textureLevelParameter(", "private int texLevelParamForName(",
                "public static java.nio.ByteBuffer textureUploadRgba8(",
                "public static long textureUploadSerial(", "public static boolean textureLevelHasGamePixels(",
                "private static boolean isProxyTarget(", "private long texBindKey(",
                "private int uploadTargetName(", "private void texSubImage(",
                # The argument-taking overload the transformer's route calls; the long[] form delegates to it.
                "private void texSubImage(int target, ",
                "private ByteBuffer uploadToRgba8(", "private static int aligned(",
                "private static long texParamKey(", "private static int textureParameterValue(",
                "public static int recordedTextureParameter(", "public static int textureParameter(",
                "public static boolean textureParameterRecorded("]
REG_FIELDS = ["context", "entries", "externalImages", "fullyInitialized", "announcedGeneration",
              "announceCounter", "staleIds", "capturesRefusedWithoutGamePixels", "failureCount",
              "lastFailure", "mirrorCount", "imageUploadsSkipped", "imageUploads", "filledFromCapture",
              "MIRROR_REACH_OVERRIDE", "MIRROR_CAPTURED_LEVELS", "PATCH_MIP_LEVELS",
              "externalInvalidatesIgnored", "readbacksSkippedWithoutContent", "createdByAnnouncement",
              "patchesBeforeInitialization", "patchCount", "patchBytes", "patchNanos",
              "fullImageCount", "fullImageBytes", "fullImageNanos", "filledFromAnnouncement",
              "patchRejections", "wrapDefaultsUsed"]
REG_METHODS = ["public VulkanTextureRegistry(", "private boolean fillFromCapture(",
               "public boolean announceFullPixels(", "private void recordApplication(",
               "private static int textureLevelParameter(", "private static int textureParameter(",
               "private static boolean nearestFilterOf(", "private static boolean nearestFilterFor(",
               "private static java.nio.ByteBuffer capturedRgbaOf(",
               "private static java.nio.ByteBuffer[] celtium$capturedLevels(",
               "private static boolean wrapRepeats(", "public static int toRgba(int[][] data, int level,",
               "private void rejectPatch("]

MEMORY = r"""package org.lwjgl.system;
import java.nio.*;import java.util.*;
public class MemoryUtil {
 static final NavigableMap<Long,ByteBuffer> regions=new TreeMap<>();static long next=1L<<32;
 public static boolean poison;public static long zeroedBytes;
 public static void reset(){regions.clear();poison=false;zeroedBytes=0;}
 public static long memAddress(ByteBuffer b){
  if(poison)for(int i=0;i<b.capacity();i++)b.put(i,(byte)0xa5);
  long p=next;next+=1L<<32;regions.put(p,b);return p;
 }
 public static ByteBuffer memByteBuffer(long p,int n){
  Map.Entry<Long,ByteBuffer> region=regions.floorEntry(p);
  if(region==null || n<0 || p-region.getKey()>region.getValue().capacity()-n)throw new AssertionError("native memory bounds: "+p+"/"+n);
  ByteBuffer view=region.getValue().duplicate();view.position((int)(p-region.getKey()));view.limit(view.position()+n);return view.slice();
 }
 public static void memSet(long p,int value,long n){
  if(n<0 || n>Integer.MAX_VALUE)throw new AssertionError("overflowed native fill: "+n);
  ByteBuffer b=memByteBuffer(p,(int)n);for(int i=0;i<n;i++)b.put(i,(byte)value);zeroedBytes+=n;
 }
 public static ByteBuffer memAlloc(int n){return ByteBuffer.allocateDirect(n);}
 public static void memFree(ByteBuffer ignored){}
}
"""

STUB_SHELL = r"""
 public static void fresh(){installed=new CeltiumGlStub();}
 public static void uninstall(){installed=null;}
 public static int count(){return installed==null?0:installed.texLevels.size();}
 public static void seedOldLevel(int name,int level){
  installed.texLevels.put(texLevelKey(name,level),new int[]{1,1,0x1908});
  installed.texCaptures.put(texLevelKey(name,level),new TexCapture(1,1,ByteBuffer.allocateDirect(4),true,999));
 }
 public static void patch(int name,int level,int x,int y,int w,int h,ByteBuffer bytes){
  installed.textureBindings.put(installed.texBindKey(0x0de1),name);
  installed.texSubImage(new long[]{0x0de1,level,x,y,w,h,0x1908,0x1401,MemoryUtil.memAddress(bytes)});
 }
}
"""

GL = r"""package org.lwjgl.opengl;
public class GL11 {
 public static final int GL_TEXTURE_WIDTH=0x1000,GL_TEXTURE_HEIGHT=0x1001,GL_TEXTURE_MAG_FILTER=0x2800,
  GL_TEXTURE_WRAP_S=0x2802,GL_TEXTURE_WRAP_T=0x2803,GL_NEAREST=0x2600,GL_REPEAT=0x2901,
  GL_TEXTURE_BINDING_2D=0x8069,GL_TEXTURE_2D=0x0de1;
 public static int calls,bound=99;public static boolean forbid=true,failQuery;
 static void called(){calls++;if(forbid)throw new AssertionError("installed named query crossed native GL");}
 public static int glGetInteger(int p){called();return bound;}
 public static void glBindTexture(int target,int name){called();bound=name;}
 public static int glGetTexLevelParameteri(int target,int level,int pname){called();if(failQuery)throw new IllegalStateException("query fault");return pname==GL_TEXTURE_WIDTH?777:333;}
}
"""

TEXTURE_SHELL = r"""
 boolean fullContentArrived;int width,height,mipLevels,capturedCalls,patchCalls,namedCalls,reach;
 public int patchX,patchY,patchWidth,patchHeight,patchLevels;public int firstRgba;
 public static VulkanGlTexture create(VulkanContext context,int w,int h,boolean nearest,boolean s,boolean t){
  VulkanGlTexture texture=new VulkanGlTexture();texture.width=w;texture.height=h;texture.mipLevels=mipLevelsFor(w,h);return texture;
 }
 public void requireOwned(String label){}public void requireAlive(){}
 public boolean uploadFromImage(int w,int h,ByteBuffer pixels){
  if(pixels==null || pixels.remaining()<(long)w*h*4)throw new AssertionError("invalid whole transfer");
  width=w;height=h;fullContentArrived=true;capturedCalls++;return true;
 }
 public boolean uploadPatchedLevels(int w,int h,int x,int y,ByteBuffer[] levels,int count,int name){
  if(count<1 || levels[0]==null)throw new AssertionError("invalid patch transfer");
  patchCalls++;patchWidth=w;patchHeight=h;patchX=x;patchY=y;patchLevels=count;
  firstRgba=(levels[0].get(0)&255)<<24|(levels[0].get(1)&255)<<16|(levels[0].get(2)&255)<<8|(levels[0].get(3)&255);
  if(x==0 && y==0 && w==width && h==height)fullContentArrived=true;
  return true;
 }
 public void uploadFromNamedImage(int name,int w,int h,ByteBuffer pixels){namedCalls++;uploadFromImage(w,h,pixels);}
 void setSamplerReach(int value){reach=value;}public void forceSamplerReach(int value){reach=value;}
 public int width(){return width;}public int height(){return height;}
}
"""

CHECK = r"""
import java.nio.*;import java.lang.reflect.*;import java.util.*;
import org.soutalab.celtium.vulkan.*;
import org.soutalab.celtium.vulkan.gl.CeltiumGlStub;
import org.taumc.celeritas.mixin.core.TextureUtilMixin;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.opengl.GL11;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends;
import org.spongepowered.asm.mixin.injection.Inject;
public class AllocationExtentCheck {
 static int assertions;
 static void require(boolean ok,String why){assertions++;if(!ok)throw new AssertionError(why);}
 static int query(int name,int level,int pname){return CeltiumGlStub.textureLevelParameter(name,level,pname);}
 static void extent(int name,int level,int w,int h){require(query(name,level,0x1000)==w,"level width "+level);require(query(name,level,0x1001)==h,"level height "+level);require(query(name,level,0x1003)==0x1908,"RGBA format "+level);}
 static ByteBuffer capture(int name,int level,int w,int h){
  int[] dims={-7,-9,123};ByteBuffer pixels=CeltiumGlStub.textureUploadRgba8(name,level,dims);
  require(pixels!=null,"missing actual capture "+name+":"+level);require(dims[0]==w && dims[1]==h && dims[2]==123,"capture bounds/extent");
  require(pixels.isReadOnly() && pixels.remaining()==(long)w*h*4,"capture read-only/full byte size");return pixels;
 }
 static void absent(int name,int level){
  int[] dims={-1,-1};require(CeltiumGlStub.textureUploadRgba8(name,level,dims)==null,"stale capture");require(Arrays.equals(dims,new int[]{0,0}),"missing capture left stale extent");
  require(!CeltiumGlStub.textureLevelHasGamePixels(name,level),"invented game content");require(CeltiumGlStub.textureUploadSerial(name,level)==Long.MIN_VALUE,"missing serial not unknown");
 }
 static ByteBuffer patchBytes(int w,int h){ByteBuffer bytes=ByteBuffer.allocateDirect(w*h*4);for(int i=0;i<w*h;i++)bytes.put((byte)0x12).put((byte)0x34).put((byte)0x56).put((byte)0x78);return bytes.flip();}
 static void allocator(){
  CeltiumGlStub.fresh();MemoryUtil.reset();MemoryUtil.poison=true;
  CeltiumGlStub.recordTextureAllocation(42,4,2048,2048);MemoryUtil.poison=false;
  long total=0;for(int level=0;level<=4;level++){
   int side=2048>>level;extent(42,level,side,side);ByteBuffer pixels=capture(42,level,side,side);total+=(long)side*side*4;
   require(pixels.get(0)==0 && pixels.get(pixels.capacity()/2)==0 && pixels.get(pixels.capacity()-1)==0,"undefined storage not zero-filled");
   require(!CeltiumGlStub.textureLevelHasGamePixels(42,level),"allocation invented game pixels");
  }
  require(MemoryUtil.zeroedBytes==total,"production allocation did not clear every level");require(CeltiumGlStub.count()==5,"maximum mip index must be inclusive");
  int calls=GL11.calls;GL11.bound=913;
  require(Arrays.equals(VulkanTerrainGpuBackend.size(42),new int[]{2048,2048}),"backend replaced named atlas size");
  require(Arrays.equals(VulkanTerrainGpuBackend.size(999),new int[]{0,0}),"missing texture guessed 256/1");
  require(Arrays.equals(VulkanTerrainGpuBackend.size(0),new int[]{0,0}),"zero name size");require(GL11.calls==calls && GL11.bound==913,"installed size query touched GL binding");
  long before=CeltiumGlStub.textureUploadSerial(42,0);CeltiumGlStub.patch(42,0,256,128,256,256,patchBytes(256,256));
  ByteBuffer pixels=capture(42,0,2048,2048);int start=(128*2048+256)*4,end=((128+255)*2048+256+255)*4;
  require(pixels.get(start)==0x12 && pixels.get(start+3)==0x78 && pixels.get(end+2)==0x56,"real surface patch lost named offset/rows");
  require(pixels.get(start-1)==0 && pixels.get((128*2048+512)*4)==0 && pixels.get(0)==0,"real patch wrote outside rectangle");
  require(CeltiumGlStub.textureLevelHasGamePixels(42,0) && CeltiumGlStub.textureUploadSerial(42,0)>before,"real write did not publish game flag/serial");
  before=CeltiumGlStub.textureUploadSerial(42,0);CeltiumGlStub.patch(42,0,2040,0,16,16,patchBytes(16,16));
  require(CeltiumGlStub.textureUploadSerial(42,0)==before,"out-of-bounds patch changed capture");
  CeltiumGlStub.seedOldLevel(42,255);CeltiumGlStub.recordTextureAllocation(42,1,32,16);extent(42,0,32,16);extent(42,1,16,8);
  for(int level:new int[]{2,3,4,255}){require(query(42,level,0x1003)==0,"id reuse retained old mip extent");absent(42,level);}
  require(!CeltiumGlStub.textureLevelHasGamePixels(42,0),"reallocation retained old pixels");
  CeltiumGlStub.forgetTextureAllocation(42);require(query(42,0,0x1003)==0,"delete kept extent");absent(42,0);
  CeltiumGlStub.recordTextureAllocation(7,0,16,16);extent(7,0,16,16);capture(7,0,16,16);require(!CeltiumGlStub.textureLevelHasGamePixels(7,0),"lightmap undefined marked full");
  CeltiumGlStub.recordTextureAllocation(8,6,65,33);for(int level=0;level<=6;level++)extent(8,level,65>>level,33>>level);absent(8,6);
  CeltiumGlStub.recordTextureAllocation(9,31,1,1);extent(9,31,0,0);require(query(9,32,0x1003)==0,"mip32 wrapped Java shift");
  for(int[] bad:new int[][]{{0,0,8,8},{-1,0,8,8},{7,-1,8,8},{7,32,8,8},{7,0,-1,8},{7,0,8,-1}})CeltiumGlStub.recordTextureAllocation(bad[0],bad[1],bad[2],bad[3]);
  extent(7,0,16,16);require(query(0,0,0x1003)==0 && query(-1,0,0x1003)==0,"invalid name allocation");
  CeltiumGlStub.recordTextureAllocation(10,0,0,16);extent(10,0,0,16);absent(10,0);
  CeltiumGlStub.recordTextureAllocation(11,0,Integer.MAX_VALUE,Integer.MAX_VALUE);extent(11,0,Integer.MAX_VALUE,Integer.MAX_VALUE);absent(11,0);
  CeltiumGlStub.recordTextureAllocation(12,0,4097,4096);extent(12,0,4097,4096);absent(12,0);
  CeltiumGlStub.recordTextureAllocation(13,0,4096,4096);ByteBuffer cap=capture(13,0,4096,4096);require(cap.get(cap.capacity()-1)==0,"capture budget boundary");
  CeltiumGlStub.uninstall();MemoryUtil.reset();CeltiumGlStub.recordTextureAllocation(99,4,2048,2048);CeltiumGlStub.forgetTextureAllocation(7);
  require(query(99,0,0x1000)==0 && MemoryUtil.zeroedBytes==0,"uninstalled allocator wrote state");absent(99,0);
  System.out.println("production allocations: atlas5 exact mips, lightmap/non-square/zero, real offset patch, retirement/reuse, overflow/budget/no-content PASS");
 }
 static void hooks()throws Exception{
  Method allocate=TextureUtilMixin.class.getDeclaredMethod("celtium$recordAllocatedTexture",int.class,int.class,int.class,int.class,org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);
  Inject injection=allocate.getAnnotation(Inject.class);require(Arrays.equals(injection.method(),new String[]{"func_180600_a(IIII)V"}) && injection.at().value().equals("TAIL") && injection.require()==1,"allocation injection must be exact descriptor TAIL require1");
  Method deleted=TextureUtilMixin.class.getDeclaredMethod("celtium$forgetDeletedTexture",int.class,org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);
  require(deleted.getAnnotation(Inject.class).at().value().equals("HEAD"),"delete injection moved after lifetime");
  CeltiumGlStub.fresh();TerrainGpuBackends.backend=null;TextureUtilMixin.allocate(71,1,64,32);extent(71,1,32,16);TextureUtilMixin.deleted(71);absent(71,0);require(query(71,0,0x1000)==0,"delete depended on backend");
  VulkanTerrainGpuBackend backend=new VulkanTerrainGpuBackend();TerrainGpuBackends.backend=backend;TextureUtilMixin.allocate(71,0,16,16);TextureUtilMixin.deleted(71);
  require(backend.deletions==1 && backend.sawForgotten,"backend notification preceded retirement");
  backend.failDelete=true;TextureUtilMixin.allocate(71,0,8,8);TextureUtilMixin.deleted(71);absent(71,0);TerrainGpuBackends.backend=null;
  System.out.println("real mixin handlers: allocation TAIL descriptor/required target, delete before optional backend/no backend PASS (not a runtime Mixin-application proof)");
 }
 static void interop(){
  CeltiumGlStub.uninstall();GL11.forbid=false;GL11.calls=0;GL11.bound=99;
  require(Arrays.equals(VulkanTerrainGpuBackend.size(42),new int[]{777,333}) && GL11.bound==99,"legacy query did not restore binding");
  GL11.failQuery=true;try{VulkanTerrainGpuBackend.size(42);throw new AssertionError("query fault suppressed");}catch(IllegalStateException expected){}finally{GL11.failQuery=false;}
  require(GL11.bound==99,"faulting legacy query leaked binding");GL11.forbid=true;
 }
 static void registry(){
  CeltiumGlStub.fresh();MemoryUtil.reset();CeltiumGlStub.recordTextureAllocation(90,4,2048,2048);
  VulkanTextureRegistry registry=new VulkanTextureRegistry(new VulkanContext());
  require(!registry.fill(90,false) && registry.mirror(90)==null,"undefined draw lookup claimed content");
  ByteBuffer[] captured=registry.captured(90,2048,2048);
  require(captured.length==1 && captured[0]!=null && captured[0].remaining()==2048*2048*4,"validated base lost or undefined higher mips invented");
  int[][] chain=new int[5][];for(int level=0;level<5;level++){int side=256>>level;chain[level]=new int[side*side];Arrays.fill(chain[level],0x78123456);}
  require(registry.announceFullPixels(90,chain,256,256,256,128),"first nonzero atlas announcement refused: "+registry.failure());
  require(registry.mirrorWidth(90)==2048 && registry.mirrorHeight(90)==2048,"registry established sprite-sized/256 atlas");
  VulkanGlTexture spy=registry.mirror(90);require(spy.patchX==256 && spy.patchY==128 && spy.patchWidth==256 && spy.patchHeight==256 && spy.patchLevels==5,"production announcement lost region/mip chain");
  require(spy.firstRgba==0x12345678,"actual registry int[] conversion byte order");require(!CeltiumGlStub.textureLevelHasGamePixels(90,0),"announcement falsely marked stub surface written before GL upload");
  require(registry.announceFullPixels(90,chain,256,256,512,256),"second atlas region refused");
  CeltiumGlStub.patch(90,0,256,128,256,256,patchBytes(256,256));
  CeltiumGlStub.patch(90,1,128,64,128,128,patchBytes(128,128));
  captured=registry.captured(90,2048,2048);
  require(captured.length==2 && captured[1].remaining()==1024*1024*4,"real higher-level game write not retained/undefined later mips exposed");
  require(!registry.announceFullPixels(90,chain,256,256,2040,0),"registry accepted out-of-bounds region");
  CeltiumGlStub.recordTextureAllocation(91,0,16,16);int[][] light={new int[256]};Arrays.fill(light[0],0xffabcdef);
  require(registry.announceFullPixels(91,light,16,16,0,0),"whole lightmap announcement refused");require(registry.mirrorWidth(91)==16 && registry.mirrorHeight(91)==16,"lightmap dimensions lost");
  require(!registry.announceFullPixels(777,chain,256,256,256,0),"missing texture inferred full size from offset patch");
  System.out.println("production registry boundary: undefined lookup refusal, first/subsequent offset atlas announcement and lightmap dimensions PASS (Vulkan transfers spied, not assembled)");
 }
 public static void main(String[] args)throws Exception{allocator();hooks();interop();registry();System.out.println("texture allocation extent: "+assertions+" semantic assertions PASS");}
}
"""


def sources(stub, backend, registry, texture, mixin):
    stub_java = "package org.soutalab.celtium.vulkan.gl;\nimport java.nio.*;import org.lwjgl.system.MemoryUtil;import static org.lwjgl.system.MemoryUtil.memAddress;\npublic class CeltiumGlStub {\n"
    stub_java += "\n".join(field(stub, name) for name in STUB_FIELDS)
    stub_java += "\n" + method(stub, "private static final class TexCapture")
    stub_java += "\n" + "\n".join(method(stub, signature) for signature in STUB_METHODS) + STUB_SHELL
    reg_java = "package org.soutalab.celtium.vulkan;\nimport java.util.*;import org.soutalab.celtium.vulkan.gl.CeltiumGlStub;\npublic class VulkanTextureRegistry {\n"
    reg_java += method(registry, "private static final class Entry") + "\n"
    reg_java += "\n".join(field(registry, name) for name in REG_FIELDS) + "\n"
    reg_java += "\n".join(method(registry, signature) for signature in REG_METHODS)
    reg_java += """
 public boolean fill(int id,boolean writing){return fillFromCapture(id,false,writing);}
 public java.nio.ByteBuffer[] captured(int id,int w,int h){return celtium$capturedLevels(id,w,h,CeltiumGlStub.textureUploadRgba8(id,0,null));}
 public VulkanGlTexture mirror(int id){Entry e=entries.get(id);return e==null?null:e.texture;}
 public int mirrorWidth(int id){return entries.get(id).width;}public int mirrorHeight(int id){return entries.get(id).height;}
 public String failure(){return lastFailure+" "+patchRejections;}
 private void celtium$noteMirrorSource(Object... ignored){}private void logPatchLevelContract(Object... ignored){}
}\n"""
    texture_java = "package org.soutalab.celtium.vulkan;\nimport java.nio.*;\npublic class VulkanGlTexture {\n"
    texture_java += "\n".join(method(texture, signature) for signature in (
        "public int uploadCapturedLevels(", "public boolean hasFullContent(",
        "public int mipLevels(", "private static int mipLevelsFor(")) + TEXTURE_SHELL
    backend_java = "package org.soutalab.celtium.vulkan;\npublic class VulkanTerrainGpuBackend implements org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend {\n"
    backend_java += method(backend, "private static int[] textureSize(") + """
 public static int[] size(int id){return textureSize(id);}public int deletions;public boolean sawForgotten,failDelete;
 public void onGlTextureReallocated(int id){deletions++;sawForgotten=org.soutalab.celtium.vulkan.gl.CeltiumGlStub.textureLevelParameter(id,0,0x1000)==0;if(failDelete)throw new IllegalStateException("backend teardown");}
}\n"""
    mixin_java = "package org.taumc.celeritas.mixin.core;\nimport org.spongepowered.asm.mixin.injection.*;\npublic class TextureUtilMixin {\n"
    mixin_java += annotated(mixin, "private static void celtium$recordAllocatedTexture(") + "\n" + annotated(mixin, "private static void celtium$forgetDeletedTexture(")
    mixin_java += """
 public static void allocate(int id,int levels,int w,int h){celtium$recordAllocatedTexture(id,levels,w,h,null);}
 public static void deleted(int id){celtium$forgetDeletedTexture(id,null);}
}\n"""
    files = {"org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java": stub_java,
             "org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java": backend_java,
             "org/soutalab/celtium/vulkan/VulkanTextureRegistry.java": reg_java,
             "org/soutalab/celtium/vulkan/VulkanGlTexture.java": texture_java,
             "org/soutalab/celtium/vulkan/VulkanContext.java": "package org.soutalab.celtium.vulkan;public class VulkanContext{}",
             "org/taumc/celeritas/mixin/core/TextureUtilMixin.java": mixin_java,
             "org/lwjgl/system/MemoryUtil.java": MEMORY,
             "org/lwjgl/opengl/GL11.java": GL,
             "org/lwjgl/opengl/GL11C.java": "package org.lwjgl.opengl;public class GL11C extends GL11{}",
             "org/lwjgl/opengl/GL45C.java": """package org.lwjgl.opengl;public class GL45C extends GL11 {
public static int glGetTextureLevelParameteri(int id,int level,int pname){called();return 777;}
public static int glGetTextureParameteri(int id,int pname){called();return 0x2601;}}""",
             "org/embeddedt/embeddium/impl/render/chunk/backend/TerrainGpuBackend.java": "package org.embeddedt.embeddium.impl.render.chunk.backend;public interface TerrainGpuBackend{}",
             "org/embeddedt/embeddium/impl/render/chunk/backend/TerrainGpuBackends.java": "package org.embeddedt.embeddium.impl.render.chunk.backend;public class TerrainGpuBackends {public static TerrainGpuBackend backend;public static TerrainGpuBackend current(){return backend;}}",
             "org/spongepowered/asm/mixin/injection/At.java": "package org.spongepowered.asm.mixin.injection;import java.lang.annotation.*;@Retention(RetentionPolicy.RUNTIME)public @interface At{String value();}",
             "org/spongepowered/asm/mixin/injection/Inject.java": "package org.spongepowered.asm.mixin.injection;import java.lang.annotation.*;@Retention(RetentionPolicy.RUNTIME)public @interface Inject{String[] method();At at();int require() default -1;}",
             "org/spongepowered/asm/mixin/injection/callback/CallbackInfo.java": "package org.spongepowered.asm.mixin.injection.callback;public class CallbackInfo{}",
             "AllocationExtentCheck.java": CHECK}
    fast = "it/unimi/dsi/fastutil/longs/"
    files[fast + "Long2ObjectMap.java"] = "package it.unimi.dsi.fastutil.longs;public class Long2ObjectMap<V> extends java.util.HashMap<Long,V>{}"
    files[fast + "Long2ObjectOpenHashMap.java"] = "package it.unimi.dsi.fastutil.longs;public class Long2ObjectOpenHashMap<V> extends Long2ObjectMap<V>{}"
    files[fast + "Long2IntMap.java"] = "package it.unimi.dsi.fastutil.longs;public class Long2IntMap extends java.util.HashMap<Long,Integer>{public int get(long k){return getOrDefault(k,-1);}public int put(long k,int v){Integer old=super.put(k,v);return old==null?-1:old;}}"
    files[fast + "Long2IntOpenHashMap.java"] = "package it.unimi.dsi.fastutil.longs;public class Long2IntOpenHashMap extends Long2IntMap{}"
    return files


def run(inputs, directory, javac, java):
    files = sources(*inputs)
    for relative, content in files.items():
        target = directory / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")
    compile_result = subprocess.run([javac, "--release", "21", "-d", str(directory),
                                     *[str(directory / name) for name in files]], capture_output=True, text=True)
    if compile_result.returncode:
        raise RuntimeError("allocation contract compilation failed:\n" + compile_result.stdout + compile_result.stderr)
    return subprocess.run([java, "-Xverify:all", "-XX:MaxDirectMemorySize=256m", "-cp", str(directory),
                           "AllocationExtentCheck"], capture_output=True, text=True)


def mutants(inputs):
    stub, backend, registry, texture, mixin = inputs

    def changed(label, index, before, after):
        if before == after or before not in inputs[index]:
            raise ValueError("cannot construct mutation: " + label)
        result = list(inputs)
        result[index] = result[index].replace(before, after, 1)
        return label, result

    allocator = method(stub, "public static void recordTextureAllocation(")
    yield changed("maximum mip level treated as exclusive", 0, allocator,
                  allocator.replace("level <= mipmapLevels", "level < mipmapLevels"))
    yield changed("reused name keeps retired levels", 0, allocator,
                  allocator.replace("stub.forgetTexture(name);", ""))
    undefined = method(stub, "private void undefinedLevel(")
    yield changed("undefined storage claims game pixels", 0, undefined,
                  undefined.replace("zeros, false,", "zeros, true,"))
    overflow = undefined.replace("long pixels = (long) width * (long) height;",
                                 "long bytes = (long) width * (long) height * 4L;")
    overflow = overflow.replace("pixels > MAX_CAPTURE_BYTES / 4L", "bytes > MAX_CAPTURE_BYTES")
    overflow = overflow.replace("long bytes = pixels * 4L;", "")
    yield changed("capture byte multiplication overflows before guard", 0, undefined, overflow)
    size = method(backend, "private static int[] textureSize(")
    guessed = re.sub(r"org\.soutalab\.celtium\.vulkan\.gl\.CeltiumGlStub\.textureLevelParameter\(texture, 0, 0x100[01]\)", "256", size)
    yield changed("backend invents global 256 extent", 1, size, guessed)
    deleted = method(mixin, "private static void celtium$forgetDeletedTexture(")
    forget = "org.soutalab.celtium.vulkan.gl.CeltiumGlStub.forgetTextureAllocation(glTextureId);"
    optional = deleted.replace(forget, "").replace(
        "if (backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {",
        "if (backend instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan) {\n" + forget)
    yield changed("delete record depends on optional backend", 4, deleted, optional)
    captured = method(registry, "private static java.nio.ByteBuffer[] celtium$capturedLevels(")
    unseeded = captured.replace("found[0] = base;", "").replace("int count = 1;", "int count = 0;")
    unseeded = unseeded.replace("for (int level = 1;", "for (int level = 0;")
    yield changed("validated undefined base is tested a second time", 2, captured, unseeded)
    yield changed("draw lookup accepts undefined game content", 2,
                  "if (!gameWritesThisLevel && !CeltiumGlStub.textureLevelHasGamePixels(glTextureId, 0))",
                  "if (false)")
    yield changed("undefined higher mip storage becomes sampler content", 2, captured,
                  captured.replace("|| !CeltiumGlStub.textureLevelHasGamePixels(glTextureId, level)", ""))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true", help="also reject in-memory regressions; never edits source files")
    args = parser.parse_args()
    javac, java = shutil.which("javac"), shutil.which("java")
    if not javac or not java:
        raise SystemExit("allocation extent contract requires Java 21+ javac and java")
    inputs = [path.read_text(encoding="utf-8") for path in (STUB, BACKEND, REGISTRY, TEXTURE, MIXIN)]
    try:
        with tempfile.TemporaryDirectory(prefix="celtium-allocation-contract-") as temporary:
            directory = Path(temporary)
            result = run(inputs, directory / "baseline", javac, java)
            print(result.stdout, end="")
            print(result.stderr, end="")
            if result.returncode:
                return result.returncode
            if args.self_test:
                count = 0
                for label, mutated in mutants(inputs):
                    result = run(mutated, directory / ("mutant-" + str(count)), javac, java)
                    if result.returncode == 0 or "AssertionError" not in result.stderr:
                        raise RuntimeError("mutation not rejected semantically: " + label + "\n" + result.stdout + result.stderr)
                    print("mutation rejected:", label)
                    count += 1
                print("texture allocation mutation self-test:", count, "regressions rejected PASS")
            return 0
    except (ValueError, RuntimeError) as error:
        print("texture allocation extent: FAIL:", error)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
