#!/usr/bin/env python3
"""Execute shared native/callback clear producers, without GL, a GPU or Gradle.

Uses the real stub fields, typed producers, callback cases, live getters and
consume method, plus the complete production Hint. The real native emitter runs
even without ASM; cached ASM also enables full transformation/JVM verification.
This deliberately checks the existing pending-mask/live-value contract, not a
per-clear snapshot queue or the GPU consumer's scissor/write-mask semantics.
"""
from pathlib import Path
import argparse
import os
import re
import runpy
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
STUB = ROOT / "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java"
CORE = ROOT / "forge122/src/main/java/org/taumc/celeritas/core"
# Reuse existing extraction/recording helpers without importing bytecode caches
# or changing those contracts' runners.
QUERY = runpy.run_path(str(Path(__file__).with_name("glless_matrix_queries_check.py")))
EXTRACT = runpy.run_path(str(Path(__file__).with_name("draw_state_provenance_check.py")))
method, semantic_case = (EXTRACT[name] for name in ("method", "semantic_case"))


def field(source, name):
    # Clear state uses grouped declarations (drawFbo, readFbo / red, green, ...).
    pattern = (r"(?m)^\s*private\s+(?:(?:static|final|volatile)\s+)*"
               r"[\w.<>]+(?:\[\])?\s+" + re.escape(name) + r"(?=\s*[,=;])")
    match = re.search(pattern, source)
    if match:
        return source[match.start():source.index(";", match.end()) + 1].strip()
    # A field whose declaration starts with a different name (`private volatile int a, compilingList, b;`) is
    # not matched above. The whole declaration is returned instead: it declares the requested field together
    # with its neighbours, which in the reduced stub is exactly what the production file declares.
    grouped = (r"(?m)^\s*private\s+(?:(?:static|final|volatile)\s+)*[\w.<>]+(?:\[\])?\s+"
               r"[\w$]+(?:\s*,\s*[\w$]+)*(?:\s*=[^;]*)?;")
    for declaration in re.finditer(grouped, source):
        if name in re.findall(r"[,\s]([\w$]+)(?=\s*[,;=])", declaration.group()):
            return declaration.group().strip()
    raise ValueError("missing clear field: " + name)


FIELDS = ["installed", "drawFbo", "clearRed", "clearDepth", "pendingClearDrawFbo",
          "pendingClearIsMain", "drawFramebufferIsMain", "pendingClears",
          # The complete production hint publishes the open display list here, so the reduced stub has to
          # carry the same two fields and entry points or the hint will not compile against it.
          "compilingList", "compilingListMode", "lastBoundTexture"]
METHODS = ["public static boolean isInstalled(", "public static void noteDrawFramebufferIsMain(",
           "public static boolean pendingClearIsMain(", "public static int pendingClearDrawFbo(",
           "public static int consumePendingClears(", "public static void clearColor(float[] out)",
           "public static double clearDepth(", "public static void celtium$clear(",
           "public static void celtium$clearColor(", "public static void celtium$clearDepth(",
           "public static void celtium$noteListOpened(", "public static void celtium$noteListClosed(",
           "private void recordClear(", "private void recordClearColor(",
           "private void recordClearDepth(", "private void bindFramebuffer("]
SEMANTICS = ["SEM_CLEAR", "SEM_CLEAR_COLOR", "SEM_CLEAR_DEPTH", "SEM_BIND_FRAMEBUFFER"]

SHELL = r"""
 // The complete production hint calls this (glTexParameteri is one of the calls the transformer
 // rewrites). This contract is about the clear path, so the shell answers it with a no-op.
 public static void celtium$texParameteri(int target,int pname,int value){}
 // Same for the two copy entry points the complete production hint now calls.
 public static void celtium$copyTexSubImage2D(int target,int level,int xo,int yo,int x,int y,int w,int h){}
 public static void celtium$copyTexImage2D(int target,int level,int internal,int x,int y,int w,int h){}
 public static void fresh(){installed=new CeltiumGlStub();}
 public static void uninstall(){installed=null;}
 public static int peekMask(){return installed==null?0:installed.pendingClears.get();}
 public static void bind(int target,int name){installed.bindFramebuffer(target,name);}
 static class Fn {final String name;Fn(String name){this.name=name;}}
 public static void callbackClear(int mask){installed.dispatch(SEM_CLEAR,new Fn("glClear"),mask);}
 public static void callbackColor(float r,float g,float b,float a){
  installed.dispatch(SEM_CLEAR_COLOR,new Fn("glClearColor"),bits(r),bits(g),bits(b),bits(a));
 }
 public static void callbackDepth(double d){installed.dispatch(SEM_CLEAR_DEPTH,new Fn("glClearDepth"),Double.doubleToRawLongBits(d));}
 public static void callbackDepthf(float d){installed.dispatch(SEM_CLEAR_DEPTH,new Fn("glClearDepthf"),bits(d));}
 static long bits(float f){return Float.floatToRawIntBits(f)&0xffffffffL;}
 // Unrelated query/window entry points only allow the entire production Hint to compile.
 public static boolean celtium$getIntegers(int p,long out){throw new AssertionError("clear queried integer state");}
 public static void celtium$getFloats(int p,long out,boolean doubles){throw new AssertionError("clear queried float state");}
 public static void celtium$viewport(int x,int y,int w,int h){throw new AssertionError("clear changed viewport");}
 private long dispatch(int semantic,Fn fn,long... args){
  long[] c=java.util.Arrays.copyOf(args,4);long a0=c[0],a1=c[1];switch(semantic){
"""

CHECK = r"""
import java.util.*;
import org.soutalab.celtium.vulkan.gl.CeltiumGlStub;
import org.taumc.celeritas.core.CeltiumGllessHint;
import org.embeddedt.embeddium.impl.render.chunk.backend.*;
public class ClearProducerCheck {
 static final int COLOR=0x4000,DEPTH=0x100,STENCIL=0x400;
 static int assertions;
 interface Producer {
  void clear(int mask)throws Exception;
  void color(float r,float g,float b,float a)throws Exception;
  void depth(double d)throws Exception;
  default void depthf(float d)throws Exception{depth((double)d);}
 }
 static final Producer DIRECT=new Producer(){
  public void clear(int m){CeltiumGlStub.celtium$clear(m);}
  public void color(float r,float g,float b,float a){CeltiumGlStub.celtium$clearColor(r,g,b,a);}
  public void depth(double d){CeltiumGlStub.celtium$clearDepth(d);}
 };
 static final Producer CALLBACK=new Producer(){
  public void clear(int m){CeltiumGlStub.callbackClear(m);}
  public void color(float r,float g,float b,float a){CeltiumGlStub.callbackColor(r,g,b,a);}
  public void depth(double d){CeltiumGlStub.callbackDepth(d);}
  public void depthf(float d){CeltiumGlStub.callbackDepthf(d);}
 };
 static final Producer HINT=new Producer(){
  public void clear(int m){CeltiumGllessHint.glClear(m);}
  public void color(float r,float g,float b,float a){CeltiumGllessHint.glClearColor(r,g,b,a);}
  public void depth(double d){CeltiumGllessHint.glClearDepth(d);}
 };
 static void require(boolean value,String why){assertions++;if(!value)throw new AssertionError(why);}
 static void rgba(float... expected){
  float sentinel=Float.intBitsToFloat(0x7f123456);float[] out={sentinel,sentinel,sentinel,sentinel,sentinel};
  CeltiumGlStub.clearColor(out);
  for(int i=0;i<4;i++)require(Float.floatToRawIntBits(out[i])==Float.floatToRawIntBits(expected[i]),"clear color bit pattern["+i+"]");
  require(Float.floatToRawIntBits(out[4])==Float.floatToRawIntBits(sentinel),"clear color wrote a fifth element");
 }
 static void depth(double expected){require(Double.doubleToRawLongBits(CeltiumGlStub.clearDepth())==Double.doubleToRawLongBits(expected),"clear depth exact double/clamp");}
 static void pending(int mask,int fbo,boolean main){
  require(CeltiumGlStub.peekMask()==mask,"pending mask expected 0x"+Integer.toHexString(mask));
  require(CeltiumGlStub.pendingClearDrawFbo()==fbo,"pending draw target");
  require(CeltiumGlStub.pendingClearIsMain()==main,"pending main attribution");
 }
 static void consume(int mask,int fbo,boolean main){
  pending(mask,fbo,main);require(CeltiumGlStub.consumePendingClears()==mask,"consumer lost produced mask");
  pending(0,-1,false);require(CeltiumGlStub.consumePendingClears()==0,"consumer replayed clear");
 }
 static void bind(int fbo,boolean main){CeltiumGlStub.bind(0x8d40,fbo);CeltiumGlStub.noteDrawFramebufferIsMain(main);}
 static class FlushSpy implements TerrainGpuBackend {
  List<Integer> masks=new ArrayList<>(),before=new ArrayList<>(),consumed=new ArrayList<>();
  boolean drain;
  public void flushBeforeClear(int mask){
   masks.add(mask);before.add(CeltiumGlStub.peekMask());
   if(drain)consumed.add(CeltiumGlStub.consumePendingClears());
  }
 }
 static void run(String label,Producer p,boolean hasFlush,boolean uninstalled)throws Exception{
  TerrainGpuBackends.backend=null;CeltiumGlStub.fresh();
  pending(0,-1,false);rgba(0,0,0,1);depth(1);
  float red=Float.intBitsToFloat(0x3eaaaaab),green=Float.intBitsToFloat(1),blue=-0.0f,alpha=.8125f;
  double precise=Double.longBitsToDouble(0x3fd5555555555555L);
  p.color(red,green,blue,alpha);rgba(red,green,blue,alpha);
  p.depth(precise);depth(precise);pending(0,-1,false);
  p.depth(-7.5);depth(0);p.depth(7.5);depth(1);
  p.depthf(.3125f);depth((double).3125f);p.depthf(-1);depth(0);p.depthf(2);depth(1);
  p.depth(precise);p.color(red,green,blue,alpha);pending(0,-1,false);
  require(CeltiumGlStub.consumePendingClears()==0,"value setters produced a clear");
  p.clear(0);pending(0,-1,false);
  for(int fbo:new int[]{0,37})for(boolean main:new boolean[]{false,true}){
   bind(fbo,main);p.clear(COLOR);pending(COLOR,fbo,main);
   p.clear(DEPTH);p.clear(STENCIL);p.clear(COLOR);pending(COLOR|DEPTH|STENCIL,fbo,main);
   // A later bind/classification must not retroactively change the pending clear's owner.
   bind(83,!main);pending(COLOR|DEPTH|STENCIL,fbo,main);
   rgba(red,green,blue,alpha);depth(precise);consume(COLOR|DEPTH|STENCIL,fbo,main);
  }
  bind(41,true);CeltiumGlStub.bind(0x8ca8,97);p.clear(DEPTH);consume(DEPTH,41,true);
  // Existing consumer reads live values; setters do not consume or enqueue a mask.
  bind(73,true);p.clear(COLOR);p.color(.0625f,.1875f,.4375f,.9375f);p.depth(.625);
  rgba(.0625f,.1875f,.4375f,.9375f);depth(.625);consume(COLOR,73,true);
  if(hasFlush){
   FlushSpy spy=new FlushSpy();TerrainGpuBackends.backend=spy;
   p.color(red,green,blue,alpha);p.depth(precise);p.depthf(.3125f);
   require(spy.masks.isEmpty(),"clear value setter flushed draws");
   CeltiumGlStub.celtium$clear(COLOR);p.clear(DEPTH);
   require(spy.masks.equals(List.of(DEPTH)) && spy.before.equals(List.of(COLOR)),"flush must precede NEW pending clear");
   pending(COLOR|DEPTH,73,true);
   spy.drain=true;p.clear(STENCIL);
   require(spy.consumed.equals(List.of(COLOR|DEPTH)),"flush consumed new clear instead of old work");
   consume(STENCIL,73,true);TerrainGpuBackends.backend=null;
   p.clear(COLOR);consume(COLOR,73,true); // absent backend remains a valid producer
  }
  if(uninstalled){
   CeltiumGlStub.uninstall();p.color(red,green,blue,alpha);p.depth(precise);p.depthf(.3125f);p.clear(COLOR|DEPTH);
   CeltiumGlStub.noteDrawFramebufferIsMain(false);rgba(0,0,0,1);depth(1);consume(0,-1,false);
   CeltiumGlStub.fresh();pending(0,-1,false);rgba(0,0,0,1);depth(1);
  }
  System.out.println(label+": exact float4/double, depth clamp, shared masks, target snapshots, live values, consume/null/order PASS");
 }
 public static void main(String[] args)throws Exception{
  run("direct producer",DIRECT,false,true);run("callback semantic cases",CALLBACK,false,false);run("complete native Hint",HINT,true,true);
  CeltiumGlStub.fresh();TerrainGpuBackends.backend=null;bind(101,true);
  CALLBACK.color(.125f,.375f,.625f,.875f);DIRECT.depth(.4375);HINT.clear(COLOR);CALLBACK.clear(DEPTH);
  rgba(.125f,.375f,.625f,.875f);depth(.4375);consume(COLOR|DEPTH,101,true);
  System.out.println("mixed callback/direct/native producer: same state and atomic pending mask PASS ("+assertions+" assertions)");
 }
}
"""

EMITTER_TEST = r"""
 static void check(String name,String descriptor,String helper,String helperDescriptor,String... loads){
  MethodVisitor visitor=new MethodVisitor();emitDefaultReturn(visitor,name,descriptor);
  List<String> expected=new ArrayList<>(Arrays.asList(loads));
  expected.add("call:184:org/taumc/celeritas/core/CeltiumGllessHint:"+helper+":"+helperDescriptor+":false");expected.add("insn:177");
  if(!visitor.events.equals(expected))throw new AssertionError(name+descriptor+" dispatch: "+visitor.events);
 }
 public static void main(String[] args){
  check("glClear","(I)V","glClear","(I)V","var:21:0");
  check("glClearColor","(FFFF)V","glClearColor","(FFFF)V","var:23:0","var:23:1","var:23:2","var:23:3");
  check("glClearDepth","(D)V","glClearDepth","(D)V","var:24:0");
  check("glClearDepthf","(F)V","glClearDepth","(D)V","var:23:0","insn:141");
  for(String[] pair:new String[][]{{"glClear","(J)V"},{"glClearColor","(DDDD)V"},{"glClearDepth","(F)V"},{"glClearDepthf","(D)V"},
    {"xglClear","(I)V"},{"glClearColorExtra","(FFFF)V"},{"nglClearDepth","(D)V"},{"glClearDepthfExtra","(F)V"}}){
   MethodVisitor visitor=new MethodVisitor();emitDefaultReturn(visitor,pair[0],pair[1]);
   for(String event:visitor.events)if(event.startsWith("call:") && event.contains(":glClear"))throw new AssertionError("clear accepted lookalike/descriptor "+Arrays.toString(pair));
  }
  System.out.println("real clear emitter: exact names/descriptors, FLOAD4, DLOAD, F2D and helper dispatch PASS");
 }
}
"""

NATIVE = r"""
import org.objectweb.asm.*;
import java.lang.reflect.Method;
import org.taumc.celeritas.core.CeltiumGllessNativeTransformer;
public class NativeClearProducerCheck {
 static final String NAME="org/lwjgl/opengl/ClearProducerFixture";
 static class Loader extends ClassLoader{Class<?> load(byte[] bytes){return defineClass(NAME.replace('/','.'),bytes,0,bytes.length);}}
 public static void main(String[] args)throws Exception{
  ClassWriter writer=new ClassWriter(0);writer.visit(Opcodes.V21,Opcodes.ACC_PUBLIC,NAME,null,"java/lang/Object",null);
  String[][] methods={{"glClear","(I)V"},{"glClearColor","(FFFF)V"},{"glClearDepth","(D)V"},{"glClearDepthf","(F)V"}};
  for(String[] m:methods)writer.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC|Opcodes.ACC_NATIVE,m[0],m[1],null,null).visitEnd();
  writer.visitEnd();byte[] original=writer.toByteArray();CeltiumGllessNativeTransformer transformer=new CeltiumGllessNativeTransformer();
  ClearProducerCheck.require(transformer.transform("other.Type",null,original)==original,"unrelated package transformed");
  byte[] transformed=transformer.transform(NAME.replace('/','.'),NAME.replace('/','.'),original);int[] visited={0};
  new ClassReader(transformed).accept(new ClassVisitor(Opcodes.ASM9){
   public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions){
    ClearProducerCheck.require((access&Opcodes.ACC_NATIVE)==0,"clear kept native flag");visited[0]++;
    int stack=name.equals("glClearColor")?4:name.equals("glClear")?1:2;
    int locals=name.equals("glClearColor")?4:name.equals("glClearDepth")?2:1;
    return new MethodVisitor(Opcodes.ASM9){public void visitMaxs(int s,int l){
     ClearProducerCheck.require(s>=stack,"typed clear stack: "+name+"="+s);
     ClearProducerCheck.require(l>=locals,"typed clear locals: "+name+"="+l);
    }};
   }
  },0);
  ClearProducerCheck.require(visited[0]==4,"clear native fixture count");
  Class<?> type=new Loader().load(transformed);
  Method clear=type.getMethod("glClear",int.class),color=type.getMethod("glClearColor",float.class,float.class,float.class,float.class),
   depth=type.getMethod("glClearDepth",double.class),depthf=type.getMethod("glClearDepthf",float.class);
  ClearProducerCheck.run("JVM-verified transformed natives",new ClearProducerCheck.Producer(){
   public void clear(int mask)throws Exception{clear.invoke(null,mask);}
   public void color(float r,float g,float b,float a)throws Exception{color.invoke(null,r,g,b,a);}
   public void depth(double d)throws Exception{depth.invoke(null,d);}
   public void depthf(float d)throws Exception{depthf.invoke(null,d);}
  },true,true);
  System.out.println("complete clear transformer: JVM verification, float4/double2/F2D2 stack and end-to-end producer PASS");
 }
}
"""


def build_sources(stub, hint, transformer, asm):
    java = "package org.soutalab.celtium.vulkan.gl;\npublic class CeltiumGlStub {\n"
    java += "\n".join(field(stub, name) for name in FIELDS + SEMANTICS)
    java += "\n" + "\n".join(method(stub, signature) for signature in METHODS) + SHELL
    java += "\n".join(semantic_case(stub, name) for name in SEMANTICS)
    java += '\ndefault:throw new AssertionError("unexpected callback semantic");}}\n}\n'
    emitter = QUERY["EMITTER"].split("__METHODS__", 1)[0].replace("NativeEmitterCheck", "ClearEmitterCheck")
    emitter += "\n".join(method(transformer, signature) for signature in (
        "private static void emitDefaultReturn(", "private static int lastArgumentSlot("))
    for name in ("GET_ERROR_DESC", "IDENTITY"):
        declaration = re.search(r"private static final String " + name + r"\s*=\s*[^;]+;", transformer)
        if declaration is None:
            raise ValueError("missing emitter constant: " + name)
        emitter += "\n" + declaration.group()
    emitter += EMITTER_TEST
    files = {
        "org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java": java,
        "org/lwjgl/system/MemoryUtil.java": QUERY["MEMORY"],
        "org/taumc/celeritas/core/CeltiumGllessHint.java": hint,
        "org/embeddedt/embeddium/impl/render/chunk/backend/TerrainGpuBackend.java": """package org.embeddedt.embeddium.impl.render.chunk.backend;
public interface TerrainGpuBackend {default void viewport(int x,int y,int w,int h){} default void flushBeforeClear(int mask){}}""",
        "org/embeddedt/embeddium/impl/render/chunk/backend/TerrainGpuBackends.java": """package org.embeddedt.embeddium.impl.render.chunk.backend;
public class TerrainGpuBackends {public static TerrainGpuBackend backend;public static TerrainGpuBackend current(){return backend;}}""",
        "org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java": """package org.taumc.celeritas.mixin;
public class CeleritasVintageMixinPlugin {public static final Log LOGGER=new Log(); public static class Log {
 public void info(String m,Object... args){} public void warn(String m,Object... args){}
 public void error(String m,Object... args){throw new AssertionError(m,(Throwable)args[args.length-1]);}
}}""",
        "ClearProducerCheck.java": CHECK,
        "ClearEmitterCheck.java": emitter,
    }
    if asm:
        files.update({"org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java": transformer,
                      "NativeClearProducerCheck.java": NATIVE,
                      "net/minecraft/launchwrapper/IClassTransformer.java": """package net.minecraft.launchwrapper;
public interface IClassTransformer {byte[] transform(String name,String transformedName,byte[] basicClass);}"""})
    return files


def run(stub, hint, transformer, asm, work, javac, java):
    files = build_sources(stub, hint, transformer, asm)
    for relative, content in files.items():
        path = work / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
    classpath = str(work) + (os.pathsep + str(asm) if asm else "")
    compilation = subprocess.run([javac, "--release", "21", "-cp", classpath, "-d", str(work),
                                  *[str(work / relative) for relative in files]], capture_output=True, text=True)
    if compilation.returncode:
        raise RuntimeError("clear harness compilation failed:\n" + compilation.stdout + compilation.stderr)
    output = ""
    for test in ("ClearEmitterCheck", "ClearProducerCheck") + (("NativeClearProducerCheck",) if asm else ()):
        result = subprocess.run([java, "-Xverify:all", "-cp", classpath, test], capture_output=True, text=True)
        output += result.stdout + result.stderr
        if result.returncode:
            return result.returncode, output
    return 0, output


def mutants(stub, hint, transformer, asm):
    body = method(stub, "private void recordClear(")
    changed = body.replace("previous | mask", "mask")
    if changed == body:
        raise ValueError("cannot construct mask OR mutant")
    yield "mask replacement loses earlier clear", stub.replace(body, changed), hint, transformer
    changed = body.replace("this.pendingClearIsMain = this.drawFramebufferIsMain;", "this.pendingClearIsMain = this.drawFbo == 0;")
    yield "raw FBO name replaces routing attribution", stub.replace(body, changed), hint, transformer
    depth = method(stub, "private void recordClearDepth(")
    yield "double narrowed to float", stub.replace(depth, depth.replace("Math.max(0.0D, Math.min(1.0D, depth))", "(double)(float)depth")), hint, transformer
    callback = semantic_case(stub, "SEM_CLEAR")
    yield "callback bypasses shared producer", stub.replace(callback, callback.replace("this.recordClear((int) a0);", "")), hint, transformer
    clear = method(hint, "public static void glClear(")
    changed = clear.replace("org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$clear(mask);", "")
    changed = changed.replace("if (backend != null)", "org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$clear(mask);\n        if (backend != null)")
    yield "new clear produced before old draw flush", stub, hint.replace(clear, changed), transformer
    yield "float depth conversion missing", stub, hint, transformer.replace("method.visitInsn(Opcodes.F2D);", "")
    if asm:
        yield "fixed two-slot maximum stack", stub, hint, transformer.replace("new ClassWriter(reader, ClassWriter.COMPUTE_MAXS)", "new ClassWriter(reader, 0)")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--without-asm", action="store_true", help="exercise dependency-free real-emitter fallback")
    parser.add_argument("--self-test", action="store_true", help="also reject in-memory regressions; never edits sources")
    args = parser.parse_args()
    javac, java = shutil.which("javac"), shutil.which("java")
    if not javac or not java:
        raise SystemExit("clear producer contract requires Java 21+ javac and java")
    stub = STUB.read_text(encoding="utf-8")
    hint = (CORE / "CeltiumGllessHint.java").read_text(encoding="utf-8")
    transformer = (CORE / "CeltiumGllessNativeTransformer.java").read_text(encoding="utf-8")
    asm = None if args.without_asm else QUERY["asm_jar"]()
    try:
        with tempfile.TemporaryDirectory(prefix="celtium-clear-contract-") as temporary:
            directory = Path(temporary)
            code, output = run(stub, hint, transformer, asm, directory / "baseline", javac, java)
            print(output, end="")
            if code:
                return code
            if not asm:
                print("skip: ASM transformation unavailable/disabled; real emitter and producer contracts above still ran")
            if args.self_test:
                count = 0
                for label, changed_stub, changed_hint, changed_transformer in mutants(stub, hint, transformer, asm):
                    code, output = run(changed_stub, changed_hint, changed_transformer, asm,
                                       directory / ("mutant-" + str(count)), javac, java)
                    if not code or "AssertionError" not in output:
                        raise AssertionError("regression was not rejected semantically: " + label + "\n" + output)
                    print("mutation rejected:", label)
                    count += 1
                print("clear producer mutation self-test:", count, "regressions rejected PASS")
    except (ValueError, RuntimeError, AssertionError) as error:
        print("clear producer contract: FAIL:", error)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
