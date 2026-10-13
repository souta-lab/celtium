#!/usr/bin/env python3
"""Exercise the production full-upload methods with a fallible CPU transfer, not a Vulkan acceptance claim."""
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGlTexture.java"


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
    source = SOURCE.read_text()
    methods = "\n".join(method(source, name) for name in [
        "public void uploadFromGlTexture(", "public boolean uploadFromImage(",
        "private void invalidateUploadCache(", "private void commitFullUpload(",
    ]).replace("org.soutalab.celtium.vulkan.gl.CeltiumGlStub", "Capture")
    java = """import java.nio.*;import java.util.*;
public class VulkanGlTexture {
 static final int BYTES_PER_PIXEL=4;
 long lastPixelHash,lastUploadSerial=-1;
 boolean lastPixelHashValid,fullContentArrived;
 int width=1,height=1,mipLevels=1,imageUploadsSkipped,encodeLabel;
 long view=1,image=1,fullUploadBytes;
 int copies,reads,attempts; boolean failCopy,levelZeroCompared;
 ByteBuffer source=ByteBuffer.wrap(new byte[]{1,2,3,4});
 int sourceWidth=1,sourceHeight=1;
 Object poisonColour,flatPoisonColour;
 static Set<Integer> suppressedReadbackIds=new HashSet<>();
 static int suppressedReadbacks,fullUploads; static long fullUploadNanos;
 static Map<Integer,Integer> fullUploadsByGlId=new HashMap<>();
 static Map<Integer,Long> fullUploadBytesByGlId=new HashMap<>();
 static final boolean SAMPLER_DIAGNOSTICS=false;
 static class Capture {static boolean installed=true;static long serial=1;
  static boolean isInstalled(){return installed;}
  static long textureUploadSerial(int id,int level){return serial;}}
 static class GlLevel {int width,height;ByteBuffer pixels;boolean owned;
  GlLevel(int w,int h,ByteBuffer p){width=w;height=h;pixels=p;}}
 static class MemoryUtil {static void memFree(ByteBuffer ignored){}}
 void requireOwned(String ignored){} void requireAlive(){}
 Object poisonFor(int id){return null;} Object flatPoisonFor(int id){return null;}
 GlLevel readGlLevel0(int id,int target){reads++;return new GlLevel(sourceWidth,sourceHeight,source);}
 void dumpForDebugging(int id,GlLevel level){}void dumpSourcePixels(int id,GlLevel level){}
 void dumpMirrorPixels(int id){}void recordCapturedFill(int id,GlLevel level){}
 void compareLevelZeroWithGl(int id){}
 void recreateImageResources(int w,int h){width=w;height=h;lastPixelHash=0;invalidateUploadCache();}
 void copyToImage(ByteBuffer p,int w,int h,int level){attempts++;if(failCopy)throw new IllegalStateException("transfer fault");copies++;}
 static long requiredUploadBytes(String what,int w,int h,ByteBuffer p){return (long)w*h*4;}
 static long hashUploadSlice(ByteBuffer p,int n){long h=1469598103934665603L;for(int i=0;i<n;i++)h=(h^(p.get(p.position()+i)&255))*1099511628211L;return h;}
""" + methods + """
 static void expect(String name,long got,long want){if(got!=want)throw new AssertionError(name+": "+got+" != "+want);}
 static VulkanGlTexture reset(){Capture.installed=true;Capture.serial=1;return new VulkanGlTexture();}
 public static void main(String[] args){
  VulkanGlTexture t=reset();t.failCopy=true;
  try{t.uploadFromGlTexture(1,0);throw new AssertionError("fault ignored");}catch(IllegalStateException expected){}
  if(t.lastPixelHashValid)throw new AssertionError("failed transfer cached");
  t.failCopy=false;t.uploadFromGlTexture(1,0);expect("retry accepted",t.copies,1);expect("retry attempts",t.attempts,2);
  t.uploadFromGlTexture(1,0);expect("capture serial dedup",t.reads,2);
  t=reset();t.sourceWidth=2;t.source=ByteBuffer.wrap(new byte[]{1,2,3,4,5,6,7,8});
  t.uploadFromGlTexture(1,0);t.uploadFromGlTexture(1,0);expect("resize serial committed",t.copies,1);
  t=reset();t.uploadFromGlTexture(1,0);Capture.serial=2;t.uploadFromGlTexture(1,0);t.uploadFromGlTexture(1,0);
  expect("equal pixels newer serial hashed once",t.reads,2);expect("equal pixels no copy",t.copies,1);
  t=reset();Capture.installed=false;t.uploadFromGlTexture(1,0);t.source=ByteBuffer.wrap(new byte[]{9,8,7,6});t.uploadFromGlTexture(1,0);
  expect("missing serial must read",t.reads,2);expect("readback source changed",t.copies,2);
  t=reset();t.uploadFromGlTexture(1,0);t.uploadFromImage(1,1,ByteBuffer.wrap(new byte[]{9,8,7,6}));t.uploadFromGlTexture(1,0);
  expect("direct write invalidates prior captured serial",t.copies,3);
  t=reset();Capture.serial=Long.MIN_VALUE;t.uploadFromGlTexture(1,0);t.source=ByteBuffer.wrap(new byte[]{9,8,7,6});t.uploadFromGlTexture(1,0);
  expect("unknown capture serial cannot freeze content",t.copies,2);
  System.out.println("upload commit: failure retry, resize, serial dedup, readback source, producer switch, absent serial PASS");
 }
}
"""
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp)
        (path / "VulkanGlTexture.java").write_text(java)
        subprocess.run([shutil.which("javac"), "--release", "21", str(path / "VulkanGlTexture.java")], check=True)
        subprocess.run([shutil.which("java"), "-cp", str(path), "VulkanGlTexture"], check=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
