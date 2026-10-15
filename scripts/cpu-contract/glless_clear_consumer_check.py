#!/usr/bin/env python3
"""Execute the real main-image clear consumer and its Vulkan dependency recording.

Only LWJGL struct allocation, the command recorder, context and live GL provider
are fixtures. Fields, construction, latching, acquire/release, clear selection,
clear values and both barriers are extracted unchanged from GllessFrameTarget.
No GPU, Vulkan loader, LWJGL, JOML, Gradle or cached dependencies are required.
--self-test also rejects in-memory regressions without editing project sources.
"""
from pathlib import Path
import argparse
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java"
PACKAGE = "org/soutalab/celtium/vulkan"


def method(source, signature):
    start = source.index(signature)
    begin = source.index("{", start)
    tokens = re.compile(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|[{}]', re.S)
    depth = 0
    for token in tokens.finditer(source, begin):
        if token.group() == "{":
            depth += 1
        elif token.group() == "}":
            depth -= 1
            if depth == 0:
                return source[start:token.end()]
    raise ValueError("unterminated Java method: " + signature)


def field(source, name):
    pattern = (r"(?m)^\s*private\s+(?:(?:static|final|volatile)\s+)*"
               r"[\w.<>]+(?:\[\])?\s+" + re.escape(name) + r"\s*(?:=|;)")
    match = re.search(pattern, source)
    if not match:
        raise ValueError("missing production field: " + name)
    end = match.end() if match.group().endswith(";") else source.index(";", match.end()) + 1
    return source[match.start():end].strip()


FIELDS = ["COLOR_RENDER_LAYOUT", "DEPTH_RENDER_LAYOUT", "context", "width", "height",
          "colorImage", "depthImage", "firstAcquire", "neverCleared", "gameClearLatched",
          "gameClearLatchedMask", "gameClearLatchedRed", "gameClearLatchedGreen",
          "gameClearLatchedBlue", "gameClearLatchedAlpha", "gameClearLatchedDepth",
          "latchedGameClears", "liveGameClears", "colorImageLayout", "depthImageLayout",
          "gameClearsDroppedForeignTarget", "lastDroppedClearOwner"]
METHODS = ["private GllessFrameTarget(", "public void noteGameClear(", "public boolean gameClearLatched(",
           "public int gameClearsDroppedForeignTargetCount(", "public int lastDroppedClearOwnerName(",
           "public void recordAcquireBarriers(", "private void applyGameClears(",
           "private void recordClearToAttachment(VkCommandBuffer command, boolean color, boolean depth)",
           "public void recordReleaseBarriers(", "private void recordLayoutBarriers("]

PLUMBING = {
    "org/lwjgl/system/MemoryStack.java": """package org.lwjgl.system;
public final class MemoryStack implements AutoCloseable {
 public static MemoryStack stackPush() {return new MemoryStack();}
 public void close() {}
}""",
    "org/lwjgl/vulkan/VkImageSubresourceRange.java": """package org.lwjgl.vulkan;
import org.lwjgl.system.MemoryStack;
public final class VkImageSubresourceRange {
 public int aspect, baseMip, levels, baseLayer, layers;
 public static VkImageSubresourceRange calloc(MemoryStack stack) {return new VkImageSubresourceRange();}
 public VkImageSubresourceRange aspectMask(int v) {aspect=v;return this;}
 public VkImageSubresourceRange levelCount(int v) {levels=v;return this;}
 public VkImageSubresourceRange layerCount(int v) {layers=v;return this;}
 public VkImageSubresourceRange set(int a,int b,int c,int d,int e) {
  aspect=a;baseMip=b;levels=c;baseLayer=d;layers=e;return this;
 }
 public VkImageSubresourceRange copy() {return new VkImageSubresourceRange().set(aspect,baseMip,levels,baseLayer,layers);}
}""",
    "org/lwjgl/vulkan/VkClearColorValue.java": """package org.lwjgl.vulkan;
import org.lwjgl.system.MemoryStack;
public final class VkClearColorValue {
 public final float[] rgba=new float[4];
 public static VkClearColorValue calloc(MemoryStack stack) {return new VkClearColorValue();}
 public VkClearColorValue float32(int i,float v) {rgba[i]=v;return this;}
}""",
    "org/lwjgl/vulkan/VkClearDepthStencilValue.java": """package org.lwjgl.vulkan;
import org.lwjgl.system.MemoryStack;
public final class VkClearDepthStencilValue {
 public float depth;public int stencil;
 public static VkClearDepthStencilValue calloc(MemoryStack stack) {return new VkClearDepthStencilValue();}
 public VkClearDepthStencilValue depth(float v) {depth=v;return this;}
 public VkClearDepthStencilValue stencil(int v) {stencil=v;return this;}
}""",
    "org/lwjgl/vulkan/VkImageMemoryBarrier.java": """package org.lwjgl.vulkan;
import org.lwjgl.system.MemoryStack;
public final class VkImageMemoryBarrier {
 public long image;public int oldLayout,newLayout,srcQueue,dstQueue,srcAccess,dstAccess;
 public boolean typed;private final VkImageSubresourceRange range=new VkImageSubresourceRange();
 public static Buffer calloc(int n,MemoryStack stack) {return new Buffer(n);}
 public VkImageMemoryBarrier sType$Default() {typed=true;return this;}
 public VkImageMemoryBarrier image(long v) {image=v;return this;}
 public VkImageMemoryBarrier oldLayout(int v) {oldLayout=v;return this;}
 public VkImageMemoryBarrier newLayout(int v) {newLayout=v;return this;}
 public VkImageMemoryBarrier srcQueueFamilyIndex(int v) {srcQueue=v;return this;}
 public VkImageMemoryBarrier dstQueueFamilyIndex(int v) {dstQueue=v;return this;}
 public VkImageMemoryBarrier srcAccessMask(int v) {srcAccess=v;return this;}
 public VkImageMemoryBarrier dstAccessMask(int v) {dstAccess=v;return this;}
 public VkImageSubresourceRange subresourceRange() {return range;}
 public VkImageMemoryBarrier copy() {
  VkImageMemoryBarrier b=new VkImageMemoryBarrier();
  b.image=image;b.oldLayout=oldLayout;b.newLayout=newLayout;b.srcQueue=srcQueue;b.dstQueue=dstQueue;
  b.srcAccess=srcAccess;b.dstAccess=dstAccess;b.typed=typed;
  b.range.set(range.aspect,range.baseMip,range.levels,range.baseLayer,range.layers);return b;
 }
 public static final class Buffer {
  private final VkImageMemoryBarrier[] values;
  Buffer(int n) {values=new VkImageMemoryBarrier[n];for(int i=0;i<n;i++)values[i]=new VkImageMemoryBarrier();}
  public VkImageMemoryBarrier get(int i) {return values[i];}
  public java.util.List<VkImageMemoryBarrier> copy() {
   java.util.List<VkImageMemoryBarrier> result=new java.util.ArrayList<>();
   for(VkImageMemoryBarrier b:values)result.add(b.copy());return result;
  }
 }
}""",
    "org/lwjgl/vulkan/VkCommandBuffer.java": """package org.lwjgl.vulkan;
public final class VkCommandBuffer {
 public final java.util.List<VK10.Event> events=new java.util.ArrayList<>();
}""",
    "org/lwjgl/vulkan/VK10.java": """package org.lwjgl.vulkan;
import java.util.*;
public final class VK10 {
 public static final int VK_ACCESS_COLOR_ATTACHMENT_READ_BIT=0x80,
  VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT=0x100,
  VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT=0x200,
  VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT=0x400,
  VK_ACCESS_TRANSFER_WRITE_BIT=0x1000,
  VK_IMAGE_ASPECT_COLOR_BIT=1,VK_IMAGE_ASPECT_DEPTH_BIT=2,
  VK_IMAGE_LAYOUT_UNDEFINED=0,VK_IMAGE_LAYOUT_GENERAL=1,
  VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT=1,VK_PIPELINE_STAGE_TRANSFER_BIT=0x1000,
  VK_PIPELINE_STAGE_ALL_COMMANDS_BIT=0x10000;
 public static final class Event {
  public String kind;public long image;public int layout,srcStage,dstStage,flags,stencil;
  public float[] rgba;public float depth;public VkImageSubresourceRange range;
  public List<VkImageMemoryBarrier> barriers;
  Event(String kind) {this.kind=kind;}
 }
 public static void vkCmdPipelineBarrier(VkCommandBuffer command,int src,int dst,int flags,
                                         Object memory,Object buffers,VkImageMemoryBarrier.Buffer images) {
  if(memory!=null || buffers!=null)throw new AssertionError("unexpected non-image barrier");
  Event event=new Event("BARRIER");event.srcStage=src;event.dstStage=dst;event.flags=flags;
  event.barriers=images.copy();command.events.add(event);
 }
 public static void vkCmdClearColorImage(VkCommandBuffer command,long image,int layout,
                                          VkClearColorValue value,VkImageSubresourceRange range) {
  Event event=new Event("COLOR");event.image=image;event.layout=layout;
  event.rgba=value.rgba.clone();event.range=range.copy();command.events.add(event);
 }
 public static void vkCmdClearDepthStencilImage(VkCommandBuffer command,long image,int layout,
                                                 VkClearDepthStencilValue value,VkImageSubresourceRange range) {
  Event event=new Event("DEPTH");event.image=image;event.layout=layout;
  event.depth=value.depth;event.stencil=value.stencil;event.range=range.copy();command.events.add(event);
 }
 public static void attach(VkCommandBuffer command) {command.events.add(new Event("ATTACH"));}
}
""",
    PACKAGE + "/VulkanContext.java": """package org.soutalab.celtium.vulkan;
public final class VulkanContext {public int queueFamilyIndex() {return 7;}}""",
    PACKAGE + "/gl/CeltiumGlStub.java": """package org.soutalab.celtium.vulkan.gl;
// Configurable live provider only: native producer behavior is tested separately.
public final class CeltiumGlStub {
 public static boolean installed=true,main=true;
 public static int pending,owner=17,consumes,colorReads,depthReads;
 public static float[] rgba={0.125f,0.25f,0.375f,0.5f};public static double depth=0.8125;
 public static void reset() {
  installed=true;main=true;pending=0;owner=17;consumes=colorReads=depthReads=0;
  rgba=new float[]{0.125f,0.25f,0.375f,0.5f};depth=0.8125;
 }
 public static int pendingClearDrawFbo() {return !installed || pending==0?-1:owner;}
 public static boolean pendingClearIsMain() {return installed && pending!=0 && main;}
 public static int consumePendingClears() {consumes++;if(!installed)return 0;int m=pending;pending=0;return m;}
 public static void clearColor(float[] out) {
  colorReads++;float[] c=installed?rgba:new float[]{0,0,0,1};System.arraycopy(c,0,out,0,4);
 }
 public static double clearDepth() {depthReads++;return installed?depth:1;}
}
""",
}

TESTS = r"""
 private static int assertions,scenarios;
 private static final long COLOR=0xc010L,DEPTH=0xd020L;
 private static final int COLOR_RW=VK_ACCESS_COLOR_ATTACHMENT_READ_BIT|VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
 private static final int DEPTH_RW=VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT|VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
 private static void check(boolean test,String message) {assertions++;if(!test)throw new AssertionError(message);}
 private static void bits(float actual,float expected,String label) {
  check(Float.floatToIntBits(actual)==Float.floatToIntBits(expected),label+": "+actual+" != "+expected);
 }
 private static GllessFrameTarget fresh() {
  CeltiumGlStub.reset();GllessFrameTarget target=new GllessFrameTarget(new VulkanContext(),1280,720);
  target.colorImage=COLOR;target.depthImage=DEPTH;return target;
 }
 private static GllessFrameTarget initialized() {
  GllessFrameTarget target=fresh();target.recordAcquireBarriers(new VkCommandBuffer());
  CeltiumGlStub.reset();return target;
 }
 private static void range(VkImageSubresourceRange r,int aspect,String label) {
  check(r.aspect==aspect && r.baseMip==0 && r.levels==1 && r.baseLayer==0 && r.layers==1,label+" full image range");
 }
 private static void barrier(VkImageMemoryBarrier b,long image,int aspect,int oldLayout,int src,int dst,String label) {
  check(b.typed,label+" type");check(b.image==image,label+" image");
  check(b.oldLayout==oldLayout && b.newLayout==VK_IMAGE_LAYOUT_GENERAL,label+" layouts");
  check(b.srcQueue==7 && b.dstQueue==7,label+" no external queue-family transfer");
  check(b.srcAccess==src && b.dstAccess==dst,label+" access masks "+b.srcAccess+"/"+b.dstAccess+" != "+src+"/"+dst);
  range(b.subresourceRange(),aspect,label);
 }
 private static void acquire(VK10.Event event,boolean first,int oldColor,int oldDepth,String label) {
  check(event.kind.equals("BARRIER"),label+" first command is acquire dependency");
  check(event.srcStage==(first?VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT:VK_PIPELINE_STAGE_ALL_COMMANDS_BIT),label+" source stage");
  check(event.dstStage==VK_PIPELINE_STAGE_ALL_COMMANDS_BIT && event.flags==0,label+" destination stage/flags");
  check(event.barriers.size()==2,label+" both target images acquired");
  barrier(event.barriers.get(0),COLOR,VK_IMAGE_ASPECT_COLOR_BIT,oldColor,0,COLOR_RW|VK_ACCESS_TRANSFER_WRITE_BIT,label+" color");
  barrier(event.barriers.get(1),DEPTH,VK_IMAGE_ASPECT_DEPTH_BIT,oldDepth,0,DEPTH_RW|VK_ACCESS_TRANSFER_WRITE_BIT,label+" depth");
 }
 private static void post(VK10.Event event,boolean color,boolean depth,String label) {
  check(event.kind.equals("BARRIER"),label+" clear-to-attachment dependency missing");
  check(event.srcStage==VK_PIPELINE_STAGE_TRANSFER_BIT && event.dstStage==VK_PIPELINE_STAGE_ALL_COMMANDS_BIT
        && event.flags==0,label+" transfer-to-attachment stages/flags");
  check(event.barriers.size()==(color?1:0)+(depth?1:0),label+" only actually cleared images synchronized");
  int i=0;
  if(color)barrier(event.barriers.get(i++),COLOR,VK_IMAGE_ASPECT_COLOR_BIT,VK_IMAGE_LAYOUT_GENERAL,
                   VK_ACCESS_TRANSFER_WRITE_BIT,COLOR_RW,label+" color");
  if(depth)barrier(event.barriers.get(i),DEPTH,VK_IMAGE_ASPECT_DEPTH_BIT,VK_IMAGE_LAYOUT_GENERAL,
                   VK_ACCESS_TRANSFER_WRITE_BIT,DEPTH_RW,label+" depth");
 }
 private static VkCommandBuffer record(GllessFrameTarget target,boolean first,boolean color,boolean depth,
                                        float[] rgba,double depthValue,String label) {
  int oldColor=target.colorImageLayout,oldDepth=target.depthImageLayout;
  VkCommandBuffer command=new VkCommandBuffer();target.recordAcquireBarriers(command);
  VK10.attach(command); // The next render pass may load the attachments immediately after this API returns.
  check(command.events.size()==2+(color?1:0)+(depth?1:0)+(color||depth?1:0),label+" command count/order: "+kinds(command));
  acquire(command.events.get(0),first,oldColor,oldDepth,label);
  int i=1;
  if(color) {
   VK10.Event e=command.events.get(i++);check(e.kind.equals("COLOR") && e.image==COLOR,label+" color clear order/target");
   check(e.layout==VK_IMAGE_LAYOUT_GENERAL,label+" color clear layout");range(e.range,VK_IMAGE_ASPECT_COLOR_BIT,label+" color clear");
   for(int channel=0;channel<3;channel++)bits(e.rgba[channel],rgba[channel],label+" RGB["+channel+"]");
   bits(e.rgba[3],1.0f,label+" default framebuffer alpha is opaque");
  }
  if(depth) {
   VK10.Event e=command.events.get(i++);check(e.kind.equals("DEPTH") && e.image==DEPTH,label+" depth clear order/target");
   check(e.layout==VK_IMAGE_LAYOUT_GENERAL && e.stencil==0,label+" D32 clear layout/stencil");
   range(e.range,VK_IMAGE_ASPECT_DEPTH_BIT,label+" depth clear");bits(e.depth,(float)depthValue,label+" depth conversion");
  }
  if(color||depth)post(command.events.get(i++),color,depth,label);
  check(command.events.get(i).kind.equals("ATTACH"),label+" post dependency must precede attachment use");
  check(!target.firstAcquire && target.colorImageLayout==VK_IMAGE_LAYOUT_GENERAL
        && target.depthImageLayout==VK_IMAGE_LAYOUT_GENERAL,label+" acquired state");
  return command;
 }
 private static String kinds(VkCommandBuffer command) {
  return command.events.stream().map(e->e.kind).toList().toString();
 }
 private static void resetReads() {CeltiumGlStub.consumes=CeltiumGlStub.colorReads=CeltiumGlStub.depthReads=0;}
 public static void main(String[] args) {
  GllessFrameTarget t=fresh();
  record(t,true,true,true,CeltiumGlStub.rgba,CeltiumGlStub.depth,"first without request");
  check(!t.neverCleared && t.liveGameClears==1 && CeltiumGlStub.consumes==1,"first initialized once");scenarios++;

  for(int mask:new int[]{0x4000,0x0100,0,0x1000,0x0400}) {
   t=fresh();t.noteGameClear(mask,.2f,.4f,.6f,.0f,.3125);
   record(t,true,true,true,new float[]{.2f,.4f,.6f,0},.3125,"first latched mask "+mask);
   check(!t.gameClearLatched() && !t.neverCleared && t.latchedGameClears==1 && CeltiumGlStub.consumes==0,
         "first latched request consumed locally");scenarios++;
  }

  // The native/live fallback must select only supported image bits after initialization.
  for(int mask:new int[]{0x4000,0x0100,0x4100,0,0x1000,0x0400,0x1400,0x5400,0x0500,0x0002}) {
   t=initialized();CeltiumGlStub.pending=mask;CeltiumGlStub.owner=37;CeltiumGlStub.main=true;
   CeltiumGlStub.rgba=new float[]{.1875f,.4375f,.8125f,.0f};CeltiumGlStub.depth=.123456789123;
   boolean color=(mask&0x4000)!=0,depth=(mask&0x0100)!=0;int live=t.liveGameClears;
   record(t,false,color,depth,CeltiumGlStub.rgba,CeltiumGlStub.depth,"later live mask "+mask);
   check(CeltiumGlStub.pending==0 && CeltiumGlStub.consumes==1,"live mask consumed exactly once");
   check(CeltiumGlStub.colorReads==(color?1:0) && CeltiumGlStub.depthReads==(depth?1:0),"no reads for unselected clear values");
   check(t.liveGameClears==live+(mask!=0?1:0),"live request census");scenarios++;
  }

  for(int mask:new int[]{0x4000,0x0100,0x4100,0,0x1000,0x0400,0x1400}) {
   t=initialized();float[] rgba={-.0f,.28125f,.59375f,.125f};double depthValue=.345678912345;
   t.noteGameClear(mask,rgba[0],rgba[1],rgba[2],rgba[3],depthValue);
   CeltiumGlStub.pending=0x4100;CeltiumGlStub.owner=63;CeltiumGlStub.main=false;
   boolean color=(mask&0x4000)!=0,depth=(mask&0x0100)!=0;
   record(t,false,color,depth,rgba,depthValue,"later latched mask "+mask);
   check(!t.gameClearLatched() && CeltiumGlStub.pending==0x4100 && CeltiumGlStub.consumes==0,
         "latched per-mask request consumed without stealing pending foreign mask");
   check(CeltiumGlStub.colorReads==0 && CeltiumGlStub.depthReads==0,"latched per-mask values are not live reads");
   check(t.latchedGameClears==(mask!=0?1:0),"latched request census");scenarios++;
  }

  // Latching must preserve the original values even if another framebuffer changes all live state.
  t=initialized();float[] latched={.15625f,.34375f,.71875f,.0f};double latchedDepth=.234567891234;
  t.noteGameClear(0x4100,latched[0],latched[1],latched[2],latched[3],latchedDepth);
  CeltiumGlStub.pending=0x4100;CeltiumGlStub.owner=99;CeltiumGlStub.main=false;
  CeltiumGlStub.rgba=new float[]{.9f,.8f,.7f,.6f};CeltiumGlStub.depth=.987654321;
  int live=t.liveGameClears;
  record(t,false,true,true,latched,latchedDepth,"latched values override foreign live state");
  check(!t.gameClearLatched() && t.latchedGameClears==1 && t.liveGameClears==live,"latched request census/state");
  check(CeltiumGlStub.pending==0x4100 && CeltiumGlStub.consumes==0
        && CeltiumGlStub.colorReads==0 && CeltiumGlStub.depthReads==0,"latch does not consume/read foreign live clear");scenarios++;
  resetReads();record(t,false,false,false,null,0,"later foreign pending after latch");
  check(t.gameClearsDroppedForeignTargetCount()==1 && t.lastDroppedClearOwnerName()==99
        && CeltiumGlStub.pending==0 && CeltiumGlStub.consumes==1,"later foreign clear is dropped and attributed");scenarios++;

  for(int owner:new int[]{0,41}) {
   t=initialized();CeltiumGlStub.pending=0x4100;CeltiumGlStub.owner=owner;CeltiumGlStub.main=false;
   record(t,false,false,false,null,0,"later foreign owner "+owner);
   check(CeltiumGlStub.pending==0 && CeltiumGlStub.consumes==1 && t.gameClearsDroppedForeignTargetCount()==1
         && t.lastDroppedClearOwnerName()==owner,"foreign identity drop");
   check(CeltiumGlStub.colorReads==0 && CeltiumGlStub.depthReads==0,"foreign values never applied to main");scenarios++;
  }

  // Even a raw default-FBO name may be non-main. First use must initialize our own images, not copy its clear.
  for(int owner:new int[]{0,71}) {
   t=fresh();CeltiumGlStub.pending=0x4100;CeltiumGlStub.owner=owner;CeltiumGlStub.main=false;
   CeltiumGlStub.rgba=new float[]{.9375f,.6875f,.4375f,0};CeltiumGlStub.depth=.0625;
   record(t,true,true,true,new float[]{0,0,0,1},1.0,"first foreign owner "+owner);
   check(!t.neverCleared && CeltiumGlStub.pending==0 && CeltiumGlStub.consumes==1,
         "first foreign clear still initializes own images");
   check(t.gameClearsDroppedForeignTargetCount()==1 && t.lastDroppedClearOwnerName()==owner,
         "first foreign identity attributed");
   check(CeltiumGlStub.colorReads==0 && CeltiumGlStub.depthReads==0,"first initialization ignores foreign clear values");scenarios++;
  }
  t=fresh();CeltiumGlStub.pending=0x4100;CeltiumGlStub.owner=0;CeltiumGlStub.main=true;
  record(t,true,true,true,CeltiumGlStub.rgba,CeltiumGlStub.depth,"main attribution beats raw default name");
  check(t.gameClearsDroppedForeignTargetCount()==0,"main identity must not be dropped by raw name");scenarios++;

  t=fresh();CeltiumGlStub.installed=false;
  record(t,true,true,true,new float[]{0,0,0,1},1.0,"no provider first initialization");
  resetReads();record(t,false,false,false,null,0,"no provider later acquire");scenarios++;

  // A present/copy may leave a different tracked layout; acquire must use it rather than assume GENERAL.
  t=initialized();t.colorImageLayout=1000001002;CeltiumGlStub.pending=0x4000;
  record(t,false,true,false,CeltiumGlStub.rgba,CeltiumGlStub.depth,"tracked present layout");scenarios++;
  VkCommandBuffer release=new VkCommandBuffer();t.recordReleaseBarriers(release);
  check(release.events.size()==1,"release only records ownership/layout dependency");
  VK10.Event e=release.events.get(0);
  check(e.srcStage==VK_PIPELINE_STAGE_ALL_COMMANDS_BIT && e.dstStage==VK_PIPELINE_STAGE_ALL_COMMANDS_BIT
        && e.flags==0 && e.barriers.size()==2,"release stages/size unchanged");
  barrier(e.barriers.get(0),COLOR,VK_IMAGE_ASPECT_COLOR_BIT,VK_IMAGE_LAYOUT_GENERAL,COLOR_RW,0,"release color");
  barrier(e.barriers.get(1),DEPTH,VK_IMAGE_ASPECT_DEPTH_BIT,VK_IMAGE_LAYOUT_GENERAL,DEPTH_RW,0,"release depth");scenarios++;

  // Direct helper call with no clear must not invent an image write or a barrier.
  VkCommandBuffer none=new VkCommandBuffer();t.recordClearToAttachment(none,false,false);
  check(none.events.isEmpty(),"no clear means no clear-to-attachment barrier");scenarios++;
  System.out.println("GL-less clear consumer: "+scenarios+" scenarios, "+assertions
    +" assertions PASS (actual acquire/clear/post-barrier/release, mask selection, values and ownership)");
 }
"""


def build_sources(source):
    java = """package org.soutalab.celtium.vulkan;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.soutalab.celtium.vulkan.gl.CeltiumGlStub;
public final class GllessFrameTarget {
"""
    java += "\n".join(field(source, name) for name in FIELDS) + "\n"
    java += "\n".join(method(source, signature) for signature in METHODS) + "\n"
    java += TESTS + "\n}\n"
    return dict(PLUMBING, **{PACKAGE + "/GllessFrameTarget.java": java})


def execute(source, expect_failure=False):
    javac, java = shutil.which("javac"), shutil.which("java")
    if not javac or not java:
        raise SystemExit("JDK 21+ javac/java are required")
    with tempfile.TemporaryDirectory(prefix="celtium-clear-consumer-") as tmp:
        directory = Path(tmp)
        paths = []
        for name, content in build_sources(source).items():
            path = directory / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content)
            paths.append(str(path))
        classes = directory / "classes"
        subprocess.run([javac, "--release", "21", "-d", str(classes), *paths], check=True)
        result = subprocess.run([java, "-cp", str(classes), "org.soutalab.celtium.vulkan.GllessFrameTarget"],
                                capture_output=True, text=True, cwd=directory)
        if expect_failure:
            if result.returncode == 0 or "AssertionError" not in result.stderr:
                raise AssertionError("mutation was not rejected by executable assertions:\n" + result.stdout + result.stderr)
            return
        if result.returncode:
            raise RuntimeError(result.stdout + result.stderr)
        print(result.stdout, end="")


def mutate(source, old, new):
    if source.count(old) != 1:
        raise ValueError("mutation anchor must occur exactly once: " + old)
    return source.replace(old, new, 1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true", help="also prove executable assertions reject regressions")
    args = parser.parse_args()
    source = SOURCE.read_text()
    execute(source)
    if args.self_test:
        mutations = [
            ("missing post-clear dependency", "            recordClearToAttachment(command, color, depth);", ""),
            ("accumulation clears color", "boolean color = first || (clears & 0x4000) != 0;",
             "boolean color = first || (clears & 0x4000) != 0 || (clears & 0x1000) != 0;"),
            ("acquire omits transfer destination", ".dstAccessMask(release ? 0 : (colorAccess | VK_ACCESS_TRANSFER_WRITE_BIT));",
             ".dstAccessMask(release ? 0 : colorAccess);"),
            ("first initialization leaks foreign values", "initializeOnly = true;", "initializeOnly = false;"),
            ("post-clear forgets color attachment reads",
             ".dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);",
             ".dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);"),
        ]
        # The last anchor also exists in Pair; mutate only the extracted main helper, not unrelated pair code.
        helper_signature = "private void recordClearToAttachment(VkCommandBuffer command, boolean color, boolean depth)"
        for label, old, new in mutations:
            if label.startswith("post-clear"):
                original = method(source, helper_signature)
                changed = mutate(original, old, new)
                candidate = source.replace(original, changed, 1)
            else:
                candidate = mutate(source, old, new)
            execute(candidate, expect_failure=True)
            print("clear consumer self-test rejected: " + label)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
