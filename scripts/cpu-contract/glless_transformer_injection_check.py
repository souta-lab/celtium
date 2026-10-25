#!/usr/bin/env python3
"""Fail-closed contract for the GL-less window hint transformer, run against the real Display class.

The transformer edits `org.lwjgl.opengl.Display` so that `GLFW_CLIENT_API = GLFW_NO_API` is set immediately
before the window is created. Two ways for that to be silently absent were found by running this check by hand
before the first leg (2026-10-15):

* `Opcodes.ASM5` cannot visit the Java 25 class file (`NestMember requires ASM7`), and the transformer's own
  catch turns that into "return the original bytes" - no hint, no stub, and a leg that looks like a renderer
  finding.
* The class is stored as `org/lwjglx/opengl/Display` and relocated to `org.lwjgl.opengl.Display` at runtime, so a
  transformer that matched only one of the names would never fire.

This check compiles the transformer's own visitor logic against the game's `Display.class`, asserts the injected
call sits immediately before the `glfwCreateWindow` call, and runs ASM's `CheckClassAdapter` over the result. It
needs the game's lwjglxx jar and an ASM jar; when either is absent (a CI checkout) it says so and passes, because
the source-shape contract in `glless_early_install_check.py` still runs there.
"""
import glob
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TRANSFORMER = ROOT / 'forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessWindowTransformer.java'
PRISM = Path('/home/s0u7a/.local/share/PrismLauncher/libraries')
GRADLE_CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1/org.ow2.asm'

HARNESS = r'''
import org.objectweb.asm.*;
import org.objectweb.asm.util.CheckClassAdapter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

public class HintCheck {
    static final int ASM_API = 9 << 16;
    static final String GLFW = "org/lwjgl/glfw/GLFW";
    static final String CREATE = "glfwCreateWindow";
    static final String HINT_OWNER = "org/taumc/celeritas/core/CeltiumGllessHint";
    static final String HINT = "beforeWindowCreation";

    public static void main(String[] args) throws Exception {
        byte[] original = Files.readAllBytes(Paths.get(args[0]));
        byte[] transformed = transform(original);
        System.out.println("bytes " + original.length + " -> " + transformed.length);
        String order = order(transformed);
        System.out.println("order " + order);
        System.out.println("hints " + count(transformed, HINT));
        System.out.println("verify " + verify(transformed));
    }

    static byte[] transform(byte[] basicClass) {
        ClassReader reader = new ClassReader(basicClass);
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new ClassVisitor(ASM_API, writer) {
            @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                MethodVisitor next = super.visitMethod(a, n, d, s, e);
                return new MethodVisitor(ASM_API, next) {
                    @Override public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                        if (op == Opcodes.INVOKESTATIC && GLFW.equals(owner) && CREATE.equals(name)) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HINT_OWNER, HINT, "()V", false);
                        }
                        super.visitMethodInsn(op, owner, name, desc, itf);
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }

    static String order(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        new ClassReader(bytes).accept(new ClassVisitor(ASM_API) {
            @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                return new MethodVisitor(ASM_API) {
                    @Override public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                        if (CREATE.equals(name)) sb.append(sb.length() == 0 ? "CREATE" : "|CREATE");
                        else if (HINT.equals(name)) sb.append(sb.length() == 0 ? "HINT" : "|HINT");
                        else if ("glfwDefaultWindowHints".equals(name)) sb.append(sb.length() == 0 ? "DEFAULTS" : "|DEFAULTS");
                        else if ("glfwInit".equals(name)) sb.append(sb.length() == 0 ? "INIT" : "|INIT");
                    }
                };
            }
        }, 0);
        return sb.toString();
    }

    static int count(byte[] bytes, String method) {
        int[] found = new int[1];
        new ClassReader(bytes).accept(new ClassVisitor(ASM_API) {
            @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                return new MethodVisitor(ASM_API) {
                    @Override public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                        if (method.equals(name)) found[0]++;
                    }
                };
            }
        }, 0);
        return found[0];
    }

    static String verify(byte[] bytes) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        try {
            java.util.List<java.net.URL> urls = new java.util.ArrayList<>();
            for (String jar : new String[] {
                    System.getProperty("lwjgl.core"), System.getProperty("lwjgl.glfw"),
                    System.getProperty("lwjgl.opengl"), System.getProperty("lwjglxx")}) {
                if (jar != null && !jar.isEmpty()) urls.add(new java.io.File(jar).toURI().toURL());
            }
            ClassLoader loader = new java.net.URLClassLoader(urls.toArray(new java.net.URL[0]), HintCheck.class.getClassLoader());
            CheckClassAdapter.verify(new ClassReader(bytes), loader, false, pw);
        } catch (Throwable error) {
            return "threw:" + error;
        }
        pw.flush();
        String report = sw.toString().trim();
        return report.isEmpty() ? "clean" : "errors:" + report.replace('\n', ' ');
    }
}
'''


def first(pattern):
    matches = sorted(glob.glob(pattern))
    return matches[0] if matches else None


def main():
    checks = []

    def require(label, ok):
        checks.append(bool(ok))
        print(('ok  ' if ok else 'FAIL') + label)

    source = TRANSFORMER.read_text(encoding='utf-8')
    require('the transformer accepts both class names',
            'org.lwjglx.opengl.Display' in source and 'org.lwjgl.opengl.Display' in source)
    require('the visitor API is the ASM9 literal, not ASM5', '9 << 16' in source and 'Opcodes.ASM5' not in source)
    require('a failure returns the original bytes', 'catch (Throwable error)' in source)

    lwjglxx = first(str(PRISM / 'com/cleanroommc/lwjglxx/*/lwjglxx-*.jar'))
    asm = first(str(GRADLE_CACHE / 'asm/9.*/*/asm-9.*.jar'))
    asm_tree = first(str(GRADLE_CACHE / 'asm-tree/9.*/*/asm-tree-9.*.jar'))
    asm_analysis = first(str(GRADLE_CACHE / 'asm-analysis/9.*/*/asm-analysis-9.*.jar'))
    asm_util = first(str(GRADLE_CACHE / 'asm-util/9.*/*/asm-util-9.*.jar'))
    # The game's jars are built for the JVM that runs the game (class file 69 = Java 25 on this machine), so the
    # verifier has to run on at least that JVM: JAVA_HOME when set, the PATH otherwise.
    home = os.environ.get('JAVA_HOME')
    javac = os.path.join(home, 'bin', 'javac') if home and os.path.exists(os.path.join(home, 'bin', 'javac')) \
        else shutil.which('javac')
    java = os.path.join(home, 'bin', 'java') if home and os.path.exists(os.path.join(home, 'bin', 'java')) \
        else shutil.which('java')
    if not all((lwjglxx, asm, asm_tree, asm_analysis, asm_util, javac)):
        print('skip: the game jars or ASM are not on this machine (source-shape checks above still ran)')
        return 0 if all(checks) else 1

    core = first(str(PRISM / 'org/lwjgl/lwjgl/*unsafe/lwjgl-*unsafe.jar'))
    glfw = first(str(PRISM / 'org/lwjgl/lwjgl-glfw/*/lwjgl-glfw-*.jar'))
    opengl = first(str(PRISM / 'org/lwjgl/lwjgl-opengl/*/lwjgl-opengl-*.jar'))
    cp = os.pathsep.join([asm, asm_tree, asm_analysis, asm_util])
    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        (work / 'HintCheck.java').write_text(HARNESS, encoding='utf-8')
        subprocess.run([javac, '-cp', cp, 'HintCheck.java'], cwd=work, check=True,
                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        import zipfile
        with zipfile.ZipFile(lwjglxx) as jar:
            member = next((n for n in jar.namelist() if n.endswith('opengl/Display.class')), None)
            if member is None:
                print('skip: no Display.class in the lwjglxx jar')
                return 0 if all(checks) else 1
            target = work / 'Display.class'
            target.write_bytes(jar.read(member))
        result = subprocess.run(
            [java, '-cp', cp + os.pathsep + str(work),
             '-Dlwjgl.core=' + (core or ''), '-Dlwjgl.glfw=' + (glfw or ''),
             '-Dlwjgl.opengl=' + (opengl or ''), '-Dlwjglxx=' + lwjglxx,
             'HintCheck', str(target)],
            cwd=work, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        out = result.stdout
        print(out.strip())

    order = re.search(r'order (\S*)', out)
    hints = re.search(r'hints (\d+)', out)
    verify = re.search(r'verify (\S+)', out)
    require('the injected call lands immediately before glfwCreateWindow',
            bool(order) and order.group(1).endswith('HINT|CREATE'))
    require('the hint is injected exactly once', bool(hints) and hints.group(1) == '1')
    require('the transformed class passes ASM data-flow verification',
            bool(verify) and verify.group(1) == 'clean')

    if not all(checks):
        print(f'{checks.count(False)} contract check(s) failed')
        return 1
    print(f'{len(checks)} contract checks passed')
    return 0


if __name__ == '__main__':
    sys.exit(main())
