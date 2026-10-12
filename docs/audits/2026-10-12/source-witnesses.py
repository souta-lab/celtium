from pathlib import Path
import re, subprocess, json, hashlib
import argparse
parser=argparse.ArgumentParser(description="Historical source witnesses, not game/GPU acceptance tests")
parser.add_argument("--repo",type=Path,default=Path.cwd())
parser.add_argument("--snapshot",type=Path,default=Path(".celtium-work/audit-20261012-184412"))
args=parser.parse_args()
repo=args.repo.resolve()
root=args.snapshot if args.snapshot.is_absolute() else repo/args.snapshot
source=root/"source"
probe=root/"probes"
probe.mkdir(exist_ok=True)
def put(name, text):
    p=probe/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
def method(path, name):
    lines=path.read_text().splitlines()
    start=next(i for i,x in enumerate(lines) if re.match(r"^    (?:public|private|protected) .*\b"+re.escape(name)+r"\(",x))
    end=next(i for i in range(start+1,len(lines)) if lines[i]=="    }")
    return "\n".join(lines[start:end+1])
gather=next(source.rglob("EntityGatherer.java"))
put("org/taumc/celeritas/impl/render/entity/EntityGatherer.java",gather.read_text())
put("net/minecraft/entity/Entity.java","package net.minecraft.entity; public class Entity { public boolean visible=true; public boolean shouldRenderInPass(int pass){return visible && pass==0;} }")
put("net/minecraft/client/multiplayer/WorldClient.java","package net.minecraft.client.multiplayer; public class WorldClient { public long tick=42; public java.util.List<net.minecraft.entity.Entity> loadedEntityList=new java.util.ArrayList<>(); public long getTotalWorldTime(){return tick;} public Object getChunkProvider(){return new Object();} }")
put("net/minecraft/util/ClassInheritanceMultiMap.java","package net.minecraft.util; public class ClassInheritanceMultiMap<T> extends java.util.ArrayList<T> {}")
put("net/minecraft/world/chunk/Chunk.java","package net.minecraft.world.chunk; public class Chunk { public net.minecraft.util.ClassInheritanceMultiMap<net.minecraft.entity.Entity>[] getEntityLists(){return new net.minecraft.util.ClassInheritanceMultiMap[0];} }")
put("org/taumc/celeritas/mixin/core/terrain/ChunkAccessor.java","package org.taumc.celeritas.mixin.core.terrain; public interface ChunkAccessor {boolean celeritas$getHasEntities();}")
put("org/taumc/celeritas/mixin/core/terrain/ChunkProviderClientAccessor.java","package org.taumc.celeritas.mixin.core.terrain; public interface ChunkProviderClientAccessor { java.util.Map<Integer,net.minecraft.world.chunk.Chunk> celeritas$getLoadedChunks(); }")
put("EntityProbe.java",r"""import net.minecraft.entity.Entity; import net.minecraft.client.multiplayer.WorldClient; import org.taumc.celeritas.impl.render.entity.EntityGatherer;
public class EntityProbe {public static void main(String[] args){
 WorldClient w=new WorldClient(); Entity a=new Entity(),b=new Entity();w.loadedEntityList.add(a);
 EntityGatherer g=new EntityGatherer();int first=g.getLoadedEntityList(w)[0].size();w.loadedEntityList.add(b);
 int same=g.getLoadedEntityList(w)[0].size(); w.tick++;int next=g.getLoadedEntityList(w)[0].size();
 a.visible=false;int state=g.getLoadedEntityList(w)[0].size();w.tick++;int refreshed=g.getLoadedEntityList(w)[0].size();
 System.out.printf("entity: initial=%d added-same-tick=%d next-tick=%d changed-pass-same-tick=%d next-tick=%d%n",first,same,next,state,refreshed);
 if(first!=1||same!=1||next!=2||state!=2||refreshed!=1)throw new AssertionError("witness changed");
}}""")
copy=next(source.rglob("TextureCopyService.java"));t=copy.read_text()
expr=re.search(r"long copyKey = (.*?);",t,re.S)[1]
put("CopyKeyProbe.java","public class CopyKeyProbe {static long key(int destName,int level,int xoffset,int yoffset,int x,int y,int width,int height){return "+expr+";} public static void main(String[] args){long a=key(7,0,0,0,0,0,64,64), b=key(7,0,0,0,1,16,64,64), c=key(7,0,0,0,0,0,65,68);System.out.println(\"copy: rectA-key=\"+a+\" rectB-key=\"+b+\" different-size-key=\"+c+\" collide=\"+(a==b&&b==c));if(a!=b||a!=c)throw new AssertionError(\"witness changed\");}}")
font=next(source.rglob("CeltiumFontGlyphCapture.java"));m=method(font,"flushBufferedQuads")
put("org/embeddedt/embeddium/impl/render/chunk/backend/TerrainGpuBackend.java","package org.embeddedt.embeddium.impl.render.chunk.backend; public interface TerrainGpuBackend {boolean submitImmediateBatch(Object f,int s,int id,long addr,int n,int mode);}")
put("org/embeddedt/embeddium/impl/render/chunk/backend/TerrainGpuBackends.java","package org.embeddedt.embeddium.impl.render.chunk.backend; public class TerrainGpuBackends {public static TerrainGpuBackend current(){return null;}}")
put("org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java","package org.soutalab.celtium.vulkan; import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend; public class VulkanTerrainGpuBackend implements TerrainGpuBackend {public int offers,taken; public boolean celtium$fontAppend(Object f,int s,int id,long a,int n,int m){return false;} public boolean submitImmediateBatch(Object f,int s,int id,long a,int n,int m){offers++;if(offers==2){taken++;return true;}return false;}}")
put("DefaultVertexFormats.java","public class DefaultVertexFormats {public static final Object POSITION_TEX_COLOR=new Object();}")
put("FontProbe.java",r"""import org.embeddedt.embeddium.impl.render.chunk.backend.*; public class FontProbe {
 static boolean bufferedOpen=true;static int bufferedQuadCount=2,bufferedVertexCount=10,bufferedColour,STRIDE=24,FORMAT_ID=2,MAX_VERTICES=4,GL_TRIANGLE_STRIP=5;static long bufferedQuadsBase=1,accepted,bufferedFlushes,bufferedFlushQuads; static void refuse(String a,String b){throw new AssertionError(a+b);}
""" +m+r"""
 public static void main(String[] args){org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend b=new org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend();flushBufferedQuads(b);System.out.printf("font: offered=%d real-taken=%d accepted-counter=%d%n",b.offers,b.taken,accepted);if(b.taken!=1||accepted!=2)throw new AssertionError("witness changed");}}
""")

arena=next(source.rglob("VulkanFrameArena.java"))
timing=next(source.rglob("FramePassTimings.java"))
put("org/soutalab/celtium/vulkan/VulkanFrameArena.java",arena.read_text())
put("org/soutalab/celtium/vulkan/FramePassTimings.java",timing.read_text())
put("org/soutalab/celtium/vulkan/VulkanContext.java","package org.soutalab.celtium.vulkan; public class VulkanContext {}")
put("org/soutalab/celtium/vulkan/VulkanHostBuffer.java","package org.soutalab.celtium.vulkan; public class VulkanHostBuffer {long size; public VulkanHostBuffer(VulkanContext c,long s,int u,boolean a){size=s;}public long size(){return size;}public long buffer(){return 1;}public long mappedAddress(){return 1;}public void ensureCapacity(long n){size=n;}public void delete(){}}")
put("org/soutalab/celtium/vulkan/VulkanDynamicMesh.java","package org.soutalab.celtium.vulkan; public class VulkanDynamicMesh {public final VulkanFrameArena.Region region;public VulkanDynamicMesh(VulkanFrameArena a,VulkanFrameArena.Region r){region=r;}}")
put("org/soutalab/celtium/vulkan/VulkanDiagnostics.java","package org.soutalab.celtium.vulkan;public class VulkanDiagnostics {public static boolean enabled(){return true;}}")
put("org/lwjgl/vulkan/VK10.java","package org.lwjgl.vulkan;public class VK10 {public static final int VK_BUFFER_USAGE_VERTEX_BUFFER_BIT=128,VK_BUFFER_USAGE_INDEX_BUFFER_BIT=64;}")
put("ArenaProbe.java",r"""import org.soutalab.celtium.vulkan.*;public class ArenaProbe {public static void main(String[]args){VulkanFrameArena a=new VulkanFrameArena(new VulkanContext());for(int f=1;f<=2;f++){a.beginFrame(f,f-1);VulkanDynamicMesh m=a.openMesh();m.region.vertexAddressAt(0,16);m.region.seal();m.region.abandon();a.submitted(f,f);a.completed(f,f);}a.beginFrame(3,3);boolean blocked=a.openMesh()==null;System.out.println("arena: sealed-region abandon then completion; third frame blocked="+blocked+" blocked-openings="+a.blockedOpenings());if(!blocked)throw new AssertionError("witness changed");}}""")
put("TimingProbe.java",r"""import java.lang.reflect.*;import org.soutalab.celtium.vulkan.FramePassTimings;public class TimingProbe {static volatile long sink; static Object field(String n)throws Exception{Field f=FramePassTimings.class.getDeclaredField(n);f.setAccessible(true);return f.get(null);}public static void main(String[]args)throws Exception{FramePassTimings.beginFrame();FramePassTimings.begin("world");FramePassTimings.begin("clouds");long n=0;for(int i=0;i<200000;i++)n=n*31+i;sink=n;FramePassTimings.end("clouds");FramePassTimings.end("world");FramePassTimings.endFrame();long frame=((long[])field("frameTotals"))[0],world=((long[][])field("topLevel"))[0][0],clouds=((long[][])field("topLevel"))[2][0],reported=((long[])field("residuals"))[0],expected=frame-world;System.out.printf("timing: frame=%d world=%d nested-clouds=%d true-outside-world=%d reported-residual=%d ns%n",frame,world,clouds,expected,reported);if(reported!=Math.max(0,expected-clouds)||reported>=expected)throw new AssertionError("witness changed");}}""")


item=repo/"forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java"
(root/"delta").mkdir(exist_ok=True)
if not (root/"delta/RenderItemMixin.java").exists():
    (root/"delta/RenderItemMixin.java").write_bytes(item.read_bytes())
item=root/"delta/RenderItemMixin.java"
original=repo/".celtium-work/mc-sources/net/minecraft/client/renderer/BufferBuilder.java"
builder_methods="\n".join(method(original,n) for n in ["reset","begin","addVertexData","finishDrawing","getByteBuffer","getBufferSize"])
put("BufferBuilder.java",r"""import java.nio.*; class VertexFormat {int getIntegerSize(){return 7;}int getSize(){return 28;}Object getElement(int i){return null;}} public class BufferBuilder {
 int vertexCount,vertexFormatIndex,drawMode;Object vertexFormatElement;boolean isDrawing,noColor;VertexFormat vertexFormat;final ByteBuffer byteBuffer=ByteBuffer.allocateDirect(1<<17).order(ByteOrder.nativeOrder());final IntBuffer rawIntBuffer=byteBuffer.asIntBuffer();void growBuffer(int n){if(n>byteBuffer.capacity())throw new IllegalStateException("test fixture too big");}
""" +builder_methods+r""" }""")
put("ItemBytesProbe.java","public class ItemBytesProbe {"+method(item,"celtium$bytes")+r""" public static void main(String[] args){BufferBuilder b=new BufferBuilder();b.begin(7,new VertexFormat());b.addVertexData(new int[24*7]);b.reset();int[] saved=celtium$bytes(b);System.out.printf("item: cube-used-ints=168 cached-ints=%d replay-vertices=%d (expected 24)%n",saved.length,saved.length/7);if(saved.length!=32768)throw new AssertionError("witness changed");}}""")


put("Tessellator.java","public class Tessellator {static final Tessellator I=new Tessellator();final BufferBuilder b=new BufferBuilder();public static Tessellator getInstance(){return I;}public BufferBuilder getBuffer(){return b;}public void draw(){b.finishDrawing();b.reset();}}")
put("OpenGlHelper.java","public class OpenGlHelper {static int lightmapTexUnit=1;static float lastBrightnessX,lastBrightnessY;static void setLightmapTextureCoords(int u,float a,float b){lastBrightnessX=a;lastBrightnessY=b;}}")
put("DefaultVertexFormats.java","public class DefaultVertexFormats {public static final Object POSITION_TEX_COLOR=new Object();public static final VertexFormat ITEM=new VertexFormat();}")
put("org/lwjgl/opengl/GL11.java","package org.lwjgl.opengl;public class GL11 {public static final int GL_QUADS=7;}")
put("net/minecraft/client/renderer/GlStateManager.java","package net.minecraft.client.renderer;public class GlStateManager {public static boolean lighting=true;public static void enableLighting(){lighting=true;}public static void disableLighting(){lighting=false;}}")
put("ItemReplayProbe.java",r"""class ItemStack{} public class ItemReplayProbe {static class Segment {int[]verts=new int[24*7];boolean updateShading=true,shade=false,updateLighting;int blockLight,skyLight;}""" +method(item,"celtium$replay")+r"""public static void main(String[]args){new ItemReplayProbe().celtium$replay(new Segment[]{new Segment()},new ItemStack(),-1);System.out.println("item replay: entered with lighting=true; returned lighting="+net.minecraft.client.renderer.GlStateManager.lighting+" (Forge cleanup expects true)");if(net.minecraft.client.renderer.GlStateManager.lighting)throw new AssertionError("witness changed");}}""")

java=list(probe.rglob("*.java"))
classes=probe/"classes";classes.mkdir(exist_ok=True)
j=re.findall(r"java.home\s*=\s*(.*)",subprocess.run(["java","-XshowSettings:properties","-version"],capture_output=True,text=True).stderr)
java_home=Path(j[0].strip()) if j else Path("/usr/lib/jvm/default")
cmd=[str(java_home/"bin/javac"),"--release","21","-d",str(classes)]+[str(p) for p in java]
r=subprocess.run(cmd,text=True,capture_output=True);print("compile exit",r.returncode,r.stderr[:1200])
if r.returncode: raise SystemExit(r.returncode)
runs=[]
for name in ["EntityProbe","CopyKeyProbe","FontProbe","ArenaProbe","TimingProbe","ItemBytesProbe","ItemReplayProbe"]:
 r=subprocess.run([str(java_home/"bin/java"),"-cp",str(classes),name],text=True,capture_output=True)
 print(r.stdout.strip(), "exit", r.returncode)
 runs.append({"name":name,"exit":r.returncode,"stdout":r.stdout})
result={"scope":"isolated CPU source witnesses; dependency stubs; no Vulkan device/game/image acceptance","sourceManifestSha256":hashlib.sha256((root/"manifest.json").read_bytes()).hexdigest(),"sources":{str(p.relative_to(root)) if p.is_relative_to(root) else str(p.relative_to(repo)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [gather,copy,font,arena,timing,item,original]},"runs":runs}
(root/"cpu-witnesses.json").write_text(json.dumps(result,ensure_ascii=False,indent=2))
print("resultSha256",hashlib.sha256((root/"cpu-witnesses.json").read_bytes()).hexdigest())
