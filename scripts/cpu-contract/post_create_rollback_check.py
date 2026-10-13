#!/usr/bin/env python3
"""Fault the production post-chain/target factories at every mocked API boundary; no driver or GPU required."""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import sys

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java"


def method(source, signature):
    start = source.index(signature)
    begin = source.index("{", start)
    depth = 1
    end = begin + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def main():
    source = (Path(sys.argv[1]) if len(sys.argv) > 1 else SOURCE).read_text()
    chain = method(source, "public static VulkanPostChain create(")
    target = method(source, "static Target create(")
    methods = chain + target
    constants = sorted(set(re.findall(r"\bVK_[A-Z0-9_]+\b", methods)))
    declarations = "\n".join(f"static final int {name}={i + 1};" for i, name in enumerate(constants))
    types = sorted(set(re.findall(r"\bVk[A-Za-z]+\b", methods)) - {"VkDevice"})
    fluent_names = sorted(set(re.findall(r"\.([A-Za-z]+)\(", methods)) - {
        "get", "limits", "colorImage", "device", "physicalDevice", "address", "deviceName",
        "delete", "writeUniformDescriptors", "fill", "println", "toHexString", "toUnsignedLong",
        "max", "extent", "subresourceRange", "minUniformBufferOffsetAlignment",
        "maxPushConstantsSize", "maxUniformBufferRange", "memoryTypeIndex", "mallocLong", "longs",
        "size", "memoryTypeBits", "calloc", "stackPush",
    })
    fluent = "\n".join(f"T {name}(Object... v){{return self();}}" for name in fluent_names)
    classes = "\n".join(
        f"""static class {name} extends Fluent<{name}> {{
 static {name} calloc(MemoryStack s){{return new {name}();}}
 static Buffer calloc(int n,MemoryStack s){{return new Buffer();}}
 static class Buffer extends {name} {{}}
}}""" for name in types
    )
    java = """import java.nio.*;import java.util.*;import java.io.*;import java.util.function.*;
public class VulkanPostChain {
""" + declarations + """
 static final int MAX_PUSH_CONSTANTS_OVERRIDE=0,UNIFORM_BLOCK_BINDING=2;
 long[] setRing=new long[32],setBoundEpoch=new long[32];
 FloatBuffer uniformView;long pass,layout,pool,sampler;VulkanUniformBuffer uniform;
 VulkanPostChain(VulkanContext c,FrameImageTarget f,long p,long l,long d,long s,int max,
  VulkanUniformBuffer u,long stride,int bytes,String refusal){pass=p;layout=l;pool=d;sampler=s;uniform=u;
  uniformView=u==null?null:FloatBuffer.allocate(2048);}
 static int declaredBlockBytes(){return 192;}
 void writeUniformDescriptors(int bytes){Fault.point("writeUniformDescriptors");}
 static void check(int code,String name){if(code!=0)throw new IllegalStateException(name);}
 static MemoryStack stackPush(){return new MemoryStack();}
 static class MemoryStack implements AutoCloseable{
  LongBuffer mallocLong(int n){return LongBuffer.allocate(n);}
  LongBuffer longs(long... v){return LongBuffer.wrap(v);}
  public void close(){}
 }
 static class Limits {int maxPushConstantsSize(){return 256;}int maxUniformBufferRange(){return 16384;}
  long minUniformBufferOffsetAlignment(){return 256;}}
 static class Shape {Shape width(int v){return this;}Shape height(int v){return this;}Shape depth(int v){return this;}
  Shape aspectMask(int v){return this;}Shape baseMipLevel(int v){return this;}Shape levelCount(int v){return this;}
  Shape baseArrayLayer(int v){return this;}Shape layerCount(int v){return this;}}
 static class Fluent<T> {
  @SuppressWarnings("unchecked") T self(){return (T)this;}
  T sType$Default(){return self();}T get(int i){return self();}T memoryTypeIndex(int i){return self();}
  T extent(Consumer<Shape> fn){fn.accept(new Shape());return self();}
  T subresourceRange(Consumer<Shape> fn){fn.accept(new Shape());return self();}
  Limits limits(){return new Limits();}long size(){return 4096;}int memoryTypeBits(){return 1;}
""" + fluent + "\n}\n" + classes + """
 static class VkDevice {long address(){return 1;}}
 static class VulkanContext {
  VkDevice device(){return new VkDevice();}Object physicalDevice(){return null;}String deviceName(){return "mock";}
  int memoryTypeIndex(int bits,int flags,String label){Fault.point("memoryTypeIndex");return 0;}
 }
 static class FrameImageTarget {long colorImage(){return 999;}}
 static class VulkanUniformBuffer {
  long buffer,memory;
  VulkanUniformBuffer(VulkanContext c,long size){Fault.point("uniform-constructor");buffer=Native.create("uniformBuffer");memory=Native.create("uniformMemory");}
  void delete(){Native.destroy(buffer);Native.destroy(memory);}
 }
 static class Fault {
  static String fail;static boolean error;static Set<String> reached=new LinkedHashSet<>();
  static void point(String p){reached.add(p);if(p.equals(fail)){if(error)throw new LinkageError(p);throw new IllegalStateException(p);}}
 }
 static class Native {
  static long id;static Map<Long,String> live=new LinkedHashMap<>();static Map<Long,Long> bound=new HashMap<>();
  static List<String> destroyed=new ArrayList<>();
  static long create(String type){long h=++id;live.put(h,type);return h;}
  static void destroy(long h){if(h==0)return;String type=live.remove(h);
   if(type==null)throw new AssertionError("double destruction "+h);destroyed.add(type);
   if(type.equals("descriptorPool"))live.entrySet().removeIf(e->e.getValue().equals("descriptorSet"));}
  static void reset(){id=0;live.clear();bound.clear();destroyed.clear();Fault.reached.clear();}
 }
 static int create(String op,String kind,LongBuffer out){Fault.point(op);out.put(0,Native.create(kind));return 0;}
 static int vkCreateRenderPass(VkDevice d,Object info,Object a,LongBuffer h){return create("renderPass","renderPass",h);}
 static int vkCreateDescriptorSetLayout(VkDevice d,Object info,Object a,LongBuffer h){return create("setLayout","setLayout",h);}
 static int vkCreateDescriptorPool(VkDevice d,Object info,Object a,LongBuffer h){return create("descriptorPool","descriptorPool",h);}
 static int vkCreateSampler(VkDevice d,Object info,Object a,LongBuffer h){return create("sampler","sampler",h);}
 static int descriptorCalls;
 static int vkAllocateDescriptorSets(VkDevice d,Object info,LongBuffer h){
  return create("descriptorSet-"+(++descriptorCalls),"descriptorSet",h);}
 static void vkGetPhysicalDeviceProperties(Object d,Object p){Fault.point("physicalProperties");}
 static void vkDestroyRenderPass(VkDevice d,long h,Object a){Native.destroy(h);}
 static void vkDestroyDescriptorSetLayout(VkDevice d,long h,Object a){Native.destroy(h);}
 static void vkDestroyDescriptorPool(VkDevice d,long h,Object a){Native.destroy(h);}
 static void vkDestroySampler(VkDevice d,long h,Object a){Native.destroy(h);}
 static int vkCreateImage(VkDevice d,Object info,Object a,LongBuffer h){return create("image","image",h);}
 static void vkGetImageMemoryRequirements(VkDevice d,long image,Object requirements){Fault.point("memoryRequirements");}
 static int vkAllocateMemory(VkDevice d,Object info,Object a,LongBuffer h){return create("memory","memory",h);}
 static int vkBindImageMemory(VkDevice d,long image,long memory,long off){Fault.point("bind");Native.bound.put(memory,image);return 0;}
 static int vkCreateImageView(VkDevice d,Object info,Object a,LongBuffer h){return create("view","view",h);}
 static int vkCreateFramebuffer(VkDevice d,Object info,Object a,LongBuffer h){return create("framebuffer","framebuffer",h);}
 static void vkDestroyFramebuffer(VkDevice d,long h,Object a){Native.destroy(h);}
 static void vkDestroyImageView(VkDevice d,long h,Object a){Native.destroy(h);}
 static void vkDestroyImage(VkDevice d,long h,Object a){Native.destroy(h);}
 static void vkFreeMemory(VkDevice d,long h,Object a){
  Long image=Native.bound.get(h);if(image!=null&&Native.live.containsKey(image))
   throw new AssertionError("bound memory freed before image");Native.destroy(h);}
""" + chain + """
 static class Target {
  long image,memory,view,fb;
  Target(int id,int w,int h,long i,long m,long v,long f,boolean owned){image=i;memory=m;view=v;fb=f;}
""" + target + """
 }
 static void run(boolean target) {
  if(target)Target.create(1,new VulkanContext(),5,256,256);
  else create(new VulkanContext(),new FrameImageTarget());
 }
 public static void main(String[] args){
  PrintStream out=System.out;System.setOut(new PrintStream(OutputStream.nullOutputStream()));
  int faults=0;
  for(boolean target:new boolean[]{false,true}) {
   Native.reset();descriptorCalls=0;Fault.fail=null;run(target);
   List<String> points=new ArrayList<>(Fault.reached);
   if(Native.live.isEmpty())throw new AssertionError("success freed published owner");
   for(String point:points)for(boolean error:new boolean[]{false,true}) {
    Native.reset();descriptorCalls=0;Fault.fail=point;Fault.error=error;
    boolean thrown=false;
    try{run(target);}catch(RuntimeException|LinkageError e){thrown=true;}
    // A uniform-route allocation failure is a documented fallback, not a failed post-chain factory.
    if(point.equals("uniform-constructor")&&!target) {
     if(thrown||Native.live.isEmpty())throw new AssertionError("uniform fallback broke push route");
    } else {
     if(!thrown)throw new AssertionError("fault was not exercised: "+point);
     if(!Native.live.isEmpty())throw new AssertionError("leak at "+point+": "+Native.live);
    }
    faults++;
    Native.reset();descriptorCalls=0;Fault.fail=null;run(target);
    if(Native.live.isEmpty())throw new AssertionError("next successful create lost ownership");
   }
  }
  System.setOut(out);System.out.println("post factories: "+faults+" failure cases, no leaked partial handles, retry and publication PASS");
 }
}
"""
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp)
        (path / "VulkanPostChain.java").write_text(java)
        subprocess.run([shutil.which("javac"), "--release", "21", str(path / "VulkanPostChain.java")], check=True)
        subprocess.run([shutil.which("java"), "-cp", str(path), "VulkanPostChain"], check=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
