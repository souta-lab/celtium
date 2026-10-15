#!/usr/bin/env python3
"""Execute draw-state provenance and the real pipeline capture without GL, a GPU or Gradle.

The native-replacement path can leave CeltiumGlStub installed but never called.
Its untouched disabled/default state must not erase GlStateManager's live mirror.
An actual disable is different: it must override even when the stub was already
false. The 2026-10-15 regression made the multiplicative vignette opaque.

Compile the actual VulkanGlState, ImmediateStatistics and VulkanColorBlendState,
extracted backend merge/capture/mapping methods, and the stub's actual provenance
getters and semantic dispatch cases. Only unrelated renderer/native plumbing and
Vulkan enum constants are replaced. --self-test also kills in-memory regressions;
it never rewrites repository sources.
"""
from pathlib import Path
import argparse
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "org/soutalab/celtium/vulkan"
JAVA_ROOT = ROOT / "celtium-vulkan/src/main/java"
BACKEND = JAVA_ROOT / PACKAGE / "VulkanTerrainGpuBackend.java"
STUB = JAVA_ROOT / PACKAGE / "gl/CeltiumGlStub.java"


def method(source, signature):
    start = source.index(signature)
    begin = source.index("{", start)
    # Ignore braces in comments and literals while preserving the original text.
    token = re.compile(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|[{}]', re.S)
    depth = 0
    for match in token.finditer(source, begin):
        if match.group() == "{":
            depth += 1
        elif match.group() == "}":
            depth -= 1
            if depth == 0:
                return source[start:match.end()]
    raise ValueError("unterminated Java method: " + signature)


def field(source, name):
    pattern = (r"(?m)^\s*private\s+(?:(?:static|final|volatile)\s+)*"
               r"[\w.<>]+(?:\[\])?\s+" + re.escape(name) + r"\s*(?:=|;)")
    match = re.search(pattern, source)
    if not match:
        raise ValueError("missing stub field: " + name)
    end = match.end() if match.group().endswith(";") else source.index(";", match.end()) + 1
    return source[match.start():end].strip()


def semantic_case(source, name):
    match = re.search(r"(?m)^            case " + re.escape(name) + r":", source)
    if not match:
        raise ValueError("missing semantic dispatch: " + name)
    following = re.search(r"(?m)^            case ", source[match.end():])
    if not following:
        raise ValueError("cannot delimit semantic dispatch: " + name)
    return source[match.start():match.end() + following.start()]


SEMANTICS = ["SEM_IS_ENABLED", "SEM_ENABLE", "SEM_DISABLE", "SEM_CULL_FACE",
             "SEM_BLEND_FUNC", "SEM_BLEND_FUNC_SEPARATE", "SEM_BLEND_EQUATION",
             "SEM_BLEND_EQUATION_SEPARATE", "SEM_COLOR_MASK"]
GETTERS = ["isInstalled", "blendEnabled", "blendEnableRecorded", "cullEnabled",
           "cullEnableRecorded", "cullFace", "cullFaceRecorded", "blendFactorsRecorded",
           "blendEquationRecorded", "colorMaskRecorded", "blendSrcRgb", "blendDstRgb",
           "blendSrcAlpha", "blendDstAlpha", "blendEquationRgb", "blendEquationAlpha",
           "colorMask"]
STUB_FIELDS = ["FLAT_MAP_SIZE", "enables", "capabilityObserved", "GL_BLEND", "GL_CULL_FACE",
               "GL_SCISSOR_TEST", "GL_DEPTH_TEST", "GL_BACK", "GL_FUNC_ADD", "GL_SRC_ALPHA",
               "GL_ONE_MINUS_SRC_ALPHA", "cullFaceValue", "cullFaceCalled", "blendSrcRgb",
               "blendDstRgb", "blendSrcAlpha", "blendDstAlpha", "blendEquationRgb",
               "blendEquationAlpha", "blendFactorsCalled", "blendEquationCalled",
               "colorMaskBits", "colorMaskCalled", "blendFuncCalls", "blendSeparateCalls",
               "blendEquationCalls", "colorMaskCalls", "scissorToggleCount"]

VK10 = """package org.lwjgl.vulkan;
public final class VK10 {
 public static final int VK_BLEND_FACTOR_ZERO=0, VK_BLEND_FACTOR_ONE=1,
  VK_BLEND_FACTOR_SRC_COLOR=2, VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR=3,
  VK_BLEND_FACTOR_DST_COLOR=4, VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR=5,
  VK_BLEND_FACTOR_SRC_ALPHA=6, VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA=7,
  VK_BLEND_FACTOR_DST_ALPHA=8, VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA=9,
  VK_BLEND_FACTOR_CONSTANT_COLOR=10, VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR=11,
  VK_BLEND_FACTOR_CONSTANT_ALPHA=12, VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA=13,
  VK_BLEND_FACTOR_SRC_ALPHA_SATURATE=14,
  VK_BLEND_OP_ADD=0, VK_BLEND_OP_SUBTRACT=1, VK_BLEND_OP_REVERSE_SUBTRACT=2,
  VK_BLEND_OP_MIN=3, VK_BLEND_OP_MAX=4,
  VK_COLOR_COMPONENT_R_BIT=1, VK_COLOR_COMPONENT_G_BIT=2,
  VK_COLOR_COMPONENT_B_BIT=4, VK_COLOR_COMPONENT_A_BIT=8;
}
"""

TESTS = r"""
 private static int assertions, scenarios;
 private static void check(boolean value,String message) {
  assertions++; if(!value) throw new AssertionError(message);
 }
 private static void equal(int[] actual,int[] expected,String message) {
  check(java.util.Arrays.equals(actual,expected),message+": "+java.util.Arrays.toString(actual)
   +" != "+java.util.Arrays.toString(expected));
 }
 private static DrawStateProvenanceCheck wrapper() {
  DrawStateProvenanceCheck b=new DrawStateProvenanceCheck(); VulkanGlState s=b.immediateState;
  s.blendEnabled(true); s.blendFunc(0,769,1,0); // vanilla vignette: ZERO, ONE_MINUS_SRC_COLOR
  s.blendEquation(0x800a,0x8008); s.colorMask(5); s.cullEnabled(true); s.cullFace(1028);
  s.depthTestEnabled(false); s.depthMask(false); s.depthFunc(519);
  s.alphaTestEnabled(true); s.alphaFunc(516,.125f); s.alphaFuncReference(.125f);
  s.lightingEnabled(true); return b;
 }
 private static int[] mirror(VulkanGlState s) {
  return new int[]{s.blendEnabled()?1:0,s.blendSrcRgb(),s.blendDstRgb(),s.blendSrcAlpha(),
   s.blendDstAlpha(),s.blendEquationRgb(),s.blendEquationAlpha(),s.colorMask(),
   s.cullEnabled()?1:0,s.cullFace(),s.depthTestEnabled()?1:0,s.depthMask()?1:0,
   s.depthFunc(),s.alphaTestEnabled()?1:0,Float.floatToIntBits(s.alphaFuncReference()),
   s.lightingEnabled()?1:0};
 }
 private static int[] pipeline() {
  return new int[]{1,0,3,0,0,1,Float.floatToIntBits(.125f),519,1,1,0,5,1,4,516};
 }
 private static void capture(DrawStateProvenanceCheck b,int[] state,int[] expected,String label) {
  // Execute the complete production capture: a correct merge with locals read from stale/default
  // stub fields, or locals computed before the merge, still fails this assertion.
  equal(b.celtium$captureState().clone(),expected,label+" pipeline");
  equal(mirror(b.immediateState),state,label+" mirror");
 }
 private static void fresh() { CeltiumGlStub.installFresh(); }
 private static void call(int semantic,long... args) { CeltiumGlStub.call(semantic,args); }
 public static void main(String[] args) {
  CeltiumGlStub.uninstall();
  check(!CeltiumGlStub.blendEnableRecorded() && !CeltiumGlStub.cullEnableRecorded()
   && !CeltiumGlStub.cullFaceRecorded(),"no provider has no observations");
  DrawStateProvenanceCheck b=wrapper(); int[] base=mirror(b.immediateState), expected=pipeline();
  b.celtium$mergeRecordedDrawState();
  for(int i=0;i<3;i++) capture(b,base,expected,"no provider stable"); scenarios++;

  fresh();
  check(!CeltiumGlStub.blendEnableRecorded() && !CeltiumGlStub.cullEnableRecorded()
   && !CeltiumGlStub.blendFactorsRecorded() && !CeltiumGlStub.blendEquationRecorded()
   && !CeltiumGlStub.colorMaskRecorded() && !CeltiumGlStub.cullFaceRecorded(),
   "installed is not an observation");
  check(CeltiumGlStub.call(CeltiumGlStub.SEM_IS_ENABLED,0x0be2)==0,"untouched blend is false");
  check(!CeltiumGlStub.blendEnableRecorded(),"reading false does not observe a disable");
  for(int i=0;i<3;i++) capture(b,base,expected,"installed untouched defaults"); scenarios++;

  // A disable at the default false value must still become known: change-only recording is wrong.
  fresh(); b=wrapper();
  call(CeltiumGlStub.SEM_DISABLE,0x0be2); call(CeltiumGlStub.SEM_DISABLE,0x0b44);
  check(CeltiumGlStub.blendEnableRecorded() && CeltiumGlStub.cullEnableRecorded(),
   "explicit false must be observed without an enable transition");
  check(!CeltiumGlStub.blendEnabled() && !CeltiumGlStub.cullEnabled(),"observed disable values");
  int[] disabled=base.clone(), disabledPipeline=expected.clone(); disabled[0]=disabled[8]=0;
  disabledPipeline[0]=disabledPipeline[1]=disabledPipeline[2]=disabledPipeline[5]=0;
  disabledPipeline[9]=disabledPipeline[10]=0;
  for(int i=0;i<3;i++) capture(b,disabled,disabledPipeline,"observed false"); scenarios++;

  fresh(); b=wrapper(); b.immediateState.blendEnabled(false); b.immediateState.cullEnabled(false);
  call(CeltiumGlStub.SEM_ENABLE,0x0be2); call(CeltiumGlStub.SEM_ENABLE,0x0b44);
  capture(b,base,expected,"observed true overrides disabled wrapper");
  call(CeltiumGlStub.SEM_DISABLE,0x0be2); call(CeltiumGlStub.SEM_DISABLE,0x0b44);
  capture(b,disabled,disabledPipeline,"true then false");
  call(CeltiumGlStub.SEM_ENABLE,0x0be2); call(CeltiumGlStub.SEM_ENABLE,0x0b44);
  capture(b,base,expected,"false then true"); scenarios++;

  // Observe each family independently. Untouched fields must keep wrapper state, not stub defaults.
  fresh(); b=wrapper(); call(CeltiumGlStub.SEM_BLEND_FUNC_SEPARATE,770,771,772,773);
  int[] factorState=base.clone(), factorPipeline=expected.clone();
  factorState[1]=770;factorState[2]=771;factorState[3]=772;factorState[4]=773;
  factorPipeline[1]=6;factorPipeline[2]=7;factorPipeline[9]=8;factorPipeline[10]=9;
  capture(b,factorState,factorPipeline,"only separate factors observed"); scenarios++;

  fresh(); b=wrapper(); call(CeltiumGlStub.SEM_BLEND_EQUATION_SEPARATE,0x8006,0x800b);
  int[] equationState=base.clone(), equationPipeline=expected.clone();
  equationState[5]=0x8006;equationState[6]=0x800b;equationPipeline[12]=0;equationPipeline[13]=2;
  capture(b,equationState,equationPipeline,"only equations observed including default ADD"); scenarios++;

  fresh(); b=wrapper(); call(CeltiumGlStub.SEM_COLOR_MASK,0,0,0,0);
  int[] maskState=base.clone(), maskPipeline=expected.clone();maskState[7]=0;maskPipeline[11]=0;
  capture(b,maskState,maskPipeline,"only zero write mask observed"); scenarios++;

  fresh(); b=wrapper(); call(CeltiumGlStub.SEM_DISABLE,0x0be2);
  int[] blendState=base.clone(), blendPipeline=expected.clone();blendState[0]=0;
  blendPipeline[0]=blendPipeline[1]=blendPipeline[2]=blendPipeline[9]=blendPipeline[10]=0;
  capture(b,blendState,blendPipeline,"only blend disable observed"); scenarios++;

  fresh(); b=wrapper(); call(CeltiumGlStub.SEM_DISABLE,0x0b44);
  int[] cullState=base.clone(), cullPipeline=expected.clone();cullState[8]=0;cullPipeline[5]=0;
  capture(b,cullState,cullPipeline,"only cull disable observed"); scenarios++;

  fresh(); b=wrapper(); call(CeltiumGlStub.SEM_CULL_FACE,1029); // observed BACK equals stub default
  int[] faceState=base.clone();faceState[9]=1029;
  capture(b,faceState,expected,"only default-valued cull face observed"); scenarios++;

  fresh(); b=wrapper(); call(CeltiumGlStub.SEM_ENABLE,0x0b71);
  call(CeltiumGlStub.SEM_ENABLE,-1);call(CeltiumGlStub.SEM_DISABLE,0x10000);
  check(!CeltiumGlStub.blendEnableRecorded() && !CeltiumGlStub.cullEnableRecorded(),
   "unrelated and out-of-range capabilities do not mark blend/cull");
  capture(b,base,expected,"unrelated capability does not corrupt wrapper"); scenarios++;

  // The non-separate entries set both halves, including alpha; zero/all masks are real observations.
  fresh(); b=wrapper(); call(CeltiumGlStub.SEM_BLEND_FUNC,1,0);
  call(CeltiumGlStub.SEM_BLEND_EQUATION,0x8006);call(CeltiumGlStub.SEM_COLOR_MASK,1,1,1,1);
  int[] pairState=base.clone(), pairPipeline=expected.clone();
  pairState[1]=pairState[3]=1;pairState[2]=pairState[4]=0;
  pairState[5]=pairState[6]=0x8006;pairState[7]=15;
  pairPipeline[1]=pairPipeline[9]=1;pairPipeline[2]=pairPipeline[10]=0;
  pairPipeline[11]=15;pairPipeline[12]=pairPipeline[13]=0;
  capture(b,pairState,pairPipeline,"two-factor and single-equation observations");
  CeltiumGlStub.uninstall(); b=wrapper();
  capture(b,base,expected,"uninstall does not replay old observations"); scenarios++;

  System.out.println("draw-state provenance: "+scenarios+" scenarios, "+assertions
   +" assertions PASS (real merge, dispatch, getters, full pipeline capture)");
 }
"""


def build_sources(backend, stub):
    getters = []
    for name in GETTERS:
        signature = re.search(r"public static (?:boolean|int) " + re.escape(name) + r"\(", stub)
        if not signature:
            raise ValueError("missing stub getter: " + name)
        getters.append(method(stub, signature.group()))
    stub_java = "package org.soutalab.celtium.vulkan.gl;\npublic class CeltiumGlStub {\n"
    stub_java += "static CeltiumGlStub installed;\n" + "\n".join(field(stub, n) for n in STUB_FIELDS)
    stub_java += "\n" + "\n".join(getters)
    stub_java += "\n" + method(stub, "private static boolean isDrawStateCapability(")
    stub_java += "\nvoid noteDrawStateBoundary() {}\n"
    stub_java += "\n".join("public static final int %s=%d;" % (name, i)
                           for i, name in enumerate(SEMANTICS))
    stub_java += """
 public static void installFresh(){installed=new CeltiumGlStub();}
 public static void uninstall(){installed=null;}
 public static long call(int semantic,long... args){
  long[] c=java.util.Arrays.copyOf(args,4);return installed.dispatch(semantic,c);
 }
 private long dispatch(int semantic,long[] c){long a0=c[0],a1=c[1];switch(semantic){
"""
    stub_java += "\n".join(semantic_case(stub, n) for n in SEMANTICS)
    stub_java += '\ndefault:throw new AssertionError("unexpected semantic "+semantic);}}\n}\n'
    java = """package org.soutalab.celtium.vulkan;
import org.soutalab.celtium.vulkan.gl.CeltiumGlStub;
public class DrawStateProvenanceCheck {
 private final VulkanGlState immediateState=new VulkanGlState();
 private final ImmediateStatistics immediateStatistics=new ImmediateStatistics();
 private final long[] colorMaskHistogram=new long[16];
 private final int[] celtium$stateScratch=new int[15];
 private static final boolean ALPHA_TEST_ENABLED=true, IMMEDIATE_BLEND_ALPHA=true,
  IMMEDIATE_BLEND_EQUATION=true, IMMEDIATE_COLOR_MASK=true;
 private static final int ALPHA_FUNC_OVERRIDE=0;
"""
    for signature in ["private void celtium$mergeRecordedDrawState(",
                      "private int[] celtium$captureState(",
                      "private static int glBlendEquationToVk(",
                      "static int glBlendFactorToVk("]:
        java += method(backend, signature) + "\n"
    java += TESTS + "\n}\n"
    files = {PACKAGE + "/DrawStateProvenanceCheck.java": java,
             PACKAGE + "/gl/CeltiumGlStub.java": stub_java,
             "org/lwjgl/vulkan/VK10.java": VK10}
    for name in ["VulkanGlState", "ImmediateStatistics", "VulkanColorBlendState", "VulkanDiagnostics"]:
        relative = PACKAGE + "/" + name + ".java"
        files[relative] = (JAVA_ROOT / relative).read_text(encoding="utf-8")
    return files


def run(files, directory, javac, java):
    paths = []
    for relative, source in files.items():
        path = directory / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding="utf-8")
        paths.append(str(path))
    result = subprocess.run([javac, "--release", "21", "-d", str(directory), *paths],
                            text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError("Java harness compilation failed:\n" + result.stdout + result.stderr)
    return subprocess.run([java, "-cp", str(directory),
                           "org.soutalab.celtium.vulkan.DrawStateProvenanceCheck"],
                          text=True, capture_output=True)


def mutants(backend, stub):
    for name in ["blendEnableRecorded", "cullEnableRecorded", "cullFaceRecorded",
                 "blendFactorsRecorded", "blendEquationRecorded", "colorMaskRecorded"]:
        old = "if (org.soutalab.celtium.vulkan.gl.CeltiumGlStub." + name + "())"
        if old not in backend:
            raise ValueError("cannot construct guard mutant: " + name)
        yield "unguarded " + name, backend.replace(old, "if (true)", 1), stub
    for semantic in ["SEM_ENABLE", "SEM_DISABLE"]:
        original = semantic_case(stub, semantic)
        changed = original.replace("this.capabilityObserved[(int) a0] = true;", "")
        if changed == original:
            raise ValueError("cannot construct observation mutant: " + semantic)
        yield "missing provenance " + semantic, backend, stub.replace(original, changed, 1)
    old = "glBlendFactorToVk(this.immediateState.blendDstRgb())"
    if old not in backend:
        raise ValueError("cannot construct stale factor-source mutant")
    yield "capture factors from untouched stub", backend.replace(
        old, "glBlendFactorToVk(org.soutalab.celtium.vulkan.gl.CeltiumGlStub.blendDstRgb())", 1), stub
    capture = method(backend, "private int[] celtium$captureState(")
    if "celtium$mergeRecordedDrawState();" not in capture:
        raise ValueError("capture no longer invokes the production merge")
    changed = capture.replace("celtium$mergeRecordedDrawState();", "", 1)
    changed = changed.replace("int writeMask = this.immediateState.colorMask();",
                              "int writeMask = this.immediateState.colorMask();\n"
                              "celtium$mergeRecordedDrawState();", 1)
    yield "locals captured before merge", backend.replace(capture, changed, 1), stub


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true", help="also reject in-memory regression mutants")
    args = parser.parse_args()
    javac, java = shutil.which("javac"), shutil.which("java")
    if not javac or not java:
        raise SystemExit("draw-state provenance: Java 21+ javac and java are required")
    backend, stub = BACKEND.read_text(encoding="utf-8"), STUB.read_text(encoding="utf-8")
    try:
        with tempfile.TemporaryDirectory(prefix="celtium-draw-state-") as temporary:
            directory = Path(temporary)
            result = run(build_sources(backend, stub), directory / "baseline", javac, java)
            if result.returncode:
                print(result.stdout + result.stderr, end="")
                return 1
            print(result.stdout, end="")
            if args.self_test:
                count = 0
                for label, changed_backend, changed_stub in mutants(backend, stub):
                    result = run(build_sources(changed_backend, changed_stub),
                                 directory / ("mutant-" + str(count)), javac, java)
                    if result.returncode == 0 or "AssertionError" not in result.stderr:
                        raise AssertionError("mutation not rejected by a semantic assertion: " + label
                                             + "\n" + result.stdout + result.stderr)
                    print("mutation rejected:", label)
                    count += 1
                print("draw-state provenance mutation self-test:", count, "regressions rejected PASS")
    except (ValueError, RuntimeError, AssertionError) as error:
        print("draw-state provenance: FAIL:", error)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
