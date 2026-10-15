#!/usr/bin/env python3
"""Run the actual rectangle dispatch/query methods with byte storage instead of LWJGL's native allocator."""
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java"


def method(source, signature):
    start = source.index(signature)
    begin = source.index("{", start)
    depth = 1
    end = begin + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def case(source, name):
    start = source.index("            case " + name + ":")
    end = source.index("            case ", start + 1)
    return source[start:end]


def main():
    source = SOURCE.read_text()
    init = method(source, "public static void initializeWindowRectangles(")
    query = method(source, "private void getInts(")
    viewport = case(source, "SEM_VIEWPORT")
    scissor = case(source, "SEM_SCISSOR")
    java = """import java.nio.*; import java.util.*;
public class CeltiumGlStub {
 static CeltiumGlStub installed;
 int viewportX, viewportY, viewportW=1280, viewportH=720;
 int[] scissorBox={0,0,1280,720}; boolean scissorCalled; long scissorCallCount;
 static final int SEM_VIEWPORT=27, SEM_SCISSOR=45;
 static ByteBuffer memory=ByteBuffer.allocate(24).order(ByteOrder.nativeOrder());
 static class Fn {}
 int drawStateBoundaries;
 // GL_LIST_INDEX is answered from these two by the extracted getInts, the same fields the production stub
 // sets when the transformer publishes glNewList/glEndList to it.
 int compilingList, compilingListMode;
 void noteDrawStateBoundary() {drawStateBoundaries++;}
 static void memPutInt(long p,int v) {memory.putInt((int)p,v);}
 int getIntegerValue(int pname) {return Integer.MIN_VALUE;}
""" + init + "\n" + query + """
 long dispatch(int id,long... c) {switch(id) {
""" + viewport + scissor + """
 default:return 0;}}
 int[] query(int pname) {
  for(int i=0;i<24;i+=4) memory.putInt(i,0x55aa55aa);
  getInts(pname,4,null);
  if(memory.getInt(0)!=0x55aa55aa || memory.getInt(20)!=0x55aa55aa)
    throw new AssertionError("query wrote outside its four words");
  return new int[]{memory.getInt(4),memory.getInt(8),memory.getInt(12),memory.getInt(16)};
 }
 static void expect(int[] a,int... b){if(!Arrays.equals(a,b))throw new AssertionError(Arrays.toString(a)+" != "+Arrays.toString(b));}
 public static void main(String[] args) {
  installed=new CeltiumGlStub();
  initializeWindowRectangles(1920,1080);
  expect(installed.query(0x0BA2),0,0,1920,1080);
  expect(installed.query(0x0C10),0,0,1920,1080);
  installed.dispatch(SEM_VIEWPORT,-17,23,256,128);
  expect(installed.query(0x0BA2),-17,23,256,128);
  expect(installed.query(0x0C10),0,0,1920,1080);
  installed.dispatch(SEM_SCISSOR,11,31,199,60);
  expect(installed.query(0x0C10),11,31,199,60);
  installed.dispatch(SEM_VIEWPORT,0,0,1920,1080);
  expect(installed.query(0x0C10),11,31,199,60);
  installed.dispatch(SEM_SCISSOR,0,0,0,0);
  expect(installed.query(0x0C10),0,0,0,0);
  if(!installed.scissorCalled || installed.scissorCallCount!=2)
    throw new AssertionError("query altered the call census");
  if(installed.drawStateBoundaries!=2)
    throw new AssertionError("scissor state did not create two draw-state boundaries");
  System.out.println("rectangle queries: drawable defaults, origin, independent scissor, zero box, bounds PASS");
 }
}
"""
    javac = shutil.which("javac")
    java_bin = shutil.which("java")
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp)
        (path / "CeltiumGlStub.java").write_text(java)
        subprocess.run([javac, "--release", "21", str(path / "CeltiumGlStub.java")], check=True)
        subprocess.run([java_bin, "-cp", str(path), "CeltiumGlStub"], check=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
