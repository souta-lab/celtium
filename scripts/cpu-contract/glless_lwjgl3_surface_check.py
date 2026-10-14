#!/usr/bin/env python3
"""Fail-closed contract for the LWJGL3 GL surface, run against the game's own LWJGL jars.

A mod that calls LWJGL3's GL classes (Thaumcraft's ShaderHelper and Botania's are the two this project has seen)
used to abort the whole JVM under the GL-less window:

    FATAL ERROR in native method: No context is current or a function that is not available in the current
    context was called. The JVM will abort execution.
        at org.lwjgl.opengl.ARBShaderObjects.glCreateShaderObjectARB(Native Method)

`GL.create(provider)` replaces the function provider, but LWJGL3's generated GL classes are `public static native`
and take their addresses from the thread's `GLCapabilities`, which nothing sets without a context. The stub builds
one from itself and installs it with `GL.setCapabilities`; this check runs that path outside the game and then calls
the very method that used to abort.

It also caught a real bug before any leg: the guard `GL.getCapabilities() != null` throws
`IllegalStateException: No GLCapabilities instance set for the current thread` when none is set - which is exactly
the case the method exists for - and that message is what the leg reported, not `createCapabilities()`.

Where the game's LWJGL jars are absent (a CI checkout) it prints that it is skipping and passes; the source-shape
checks in `glless_early_install_check.py` still run there.
"""
import glob
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STUB = ROOT / 'celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java'
JAR = ROOT / 'forge122/build/libs/celtium-forge-mc1.12.2-2.5.0-dev.jar'
INTEROP_LIB = ROOT / 'celtium-interop/build/install/celtium-interop/lib'
PRISM = Path('/home/s0u7a/.local/share/PrismLauncher/libraries')

HARNESS = r'''
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.ARBShaderObjects;

/**
 * LWJGL3 keeps its capabilities in a ThreadLocal, so the install has to be repeated on every thread that calls
 * GL. This harness measures both halves: the installing thread works, and a second thread reports the exact
 * IllegalStateException the game hit until it installs its own.
 */
public class Lwjgl3SurfaceCheck {
    public static void main(String[] args) throws Exception {
        Class<?> stub = Class.forName("org.soutalab.celtium.vulkan.gl.CeltiumGlStub");
        System.out.println("install " + (stub.getMethod("install").invoke(null) != null));
        GLCapabilities caps = GL.getCapabilities();
        System.out.println("capabilities " + (caps == null ? "null" : "present"));
        if (caps != null) {
            System.out.println("arb_shader_objects " + caps.GL_ARB_shader_objects);
            System.out.println("addresses " + caps.getAddressBuffer().capacity());
        }
        // The call that used to abort the JVM: it must return the stub's answer instead.
        System.out.println("createShaderObjectARB " + ARBShaderObjects.glCreateShaderObjectARB(0x8B31));

        Thread other = new Thread(() -> {
            System.out.println("other_before " + state());
            try {
                stub.getMethod("ensureThreadCapabilities").invoke(null);
            } catch (Throwable error) {
                System.out.println("other_ensure_threw " + error);
            }
            System.out.println("other_after " + state());
            System.out.println("other_createShaderObjectARB " + ARBShaderObjects.glCreateShaderObjectARB(0x8B31));
        }, "ContractOtherThread");
        other.start();
        other.join();
    }

    static String state() {
        try {
            return GL.getCapabilities() == null ? "null" : "present";
        } catch (Throwable error) {
            return "throws(" + error.getClass().getSimpleName() + ")";
        }
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

    source = STUB.read_text(encoding='utf-8')
    require('the stub installs capabilities with setCapabilities, not createCapabilities alone',
            'setCapabilities' in source and 'getDeclaredConstructor' in source)
    require('there is a per-thread entry point for the threads the game calls GL on',
            'ensureThreadCapabilities' in source)
    mixin = (ROOT / 'forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java').read_text(encoding='utf-8')
    require('the game thread calls it from more than one entry point',
            mixin.count('ensureThreadCapabilities()') >= 3)
    require('one of those entry points is createDisplay, which runs on the Client thread',
            'installThreadCapabilitiesAtDisplay' in mixin and 'method = "createDisplay"' in mixin)
    # The probe is allowed now, but only guarded: GL.getCapabilities() throws when the thread has none, which is
    # the case it is asking about. The runtime half of this contract proves the behaviour on a second thread.
    require('the capabilities probe is guarded, because it throws when the thread has none',
            'catch (Throwable alreadyUnset)' in source)
    require('the extension set is taken from the capabilities class fields', 'startsWith("GL_")' in source)

    javac = shutil.which('javac')
    lwjgl = first(str(INTEROP_LIB / 'lwjgl-3.4.1.jar'))
    lwjgl_natives = first(str(INTEROP_LIB / 'lwjgl-3.4.1-natives-linux.jar'))
    opengl = first(str(INTEROP_LIB / 'lwjgl-opengl-3.4.1.jar'))
    opengl_natives = first(str(INTEROP_LIB / 'lwjgl-opengl-3.4.1-natives-linux.jar'))
    glfw = first(str(PRISM / 'org/lwjgl/lwjgl-glfw/3.4.1/lwjgl-glfw-3.4.1.jar'))
    glfw_natives = first(str(PRISM / 'org/lwjgl/lwjgl-glfw/3.4.1/lwjgl-glfw-3.4.1-natives-linux.jar'))
    fastutil = first(str(PRISM / 'it/unimi/dsi/fastutil/*/fastutil-*.jar'))
    if not all((javac, JAR.exists(), lwjgl, lwjgl_natives, opengl, opengl_natives, glfw, glfw_natives, fastutil)):
        print('skip: the built jar or the game\'s LWJGL jars are not on this machine '
              '(the source-shape checks above still ran)')
        return 0 if all(checks) else 1

    cp = os.pathsep.join([str(JAR), lwjgl, lwjgl_natives, opengl, opengl_natives, glfw, glfw_natives, fastutil])
    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        (work / 'Lwjgl3SurfaceCheck.java').write_text(HARNESS, encoding='utf-8')
        subprocess.run([javac, '-cp', cp, 'Lwjgl3SurfaceCheck.java'], cwd=work, check=True,
                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        result = subprocess.run([shutil.which('java'), '-cp', cp + os.pathsep + str(work), 'Lwjgl3SurfaceCheck'],
                                cwd=work, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        out = result.stdout
        print('\n'.join(line for line in out.splitlines() if not line.startswith('WARNING')))

    require('the stub install succeeded', 'install true' in out)
    require('capabilities are present for the thread', 'capabilities present' in out)
    require('the ARB shader-object extension is reported available', 'arb_shader_objects true' in out)
    require('the call that used to abort the JVM returns the stub answer',
            'createShaderObjectARB 0' in out)
    require('a second thread starts without capabilities, as the game did',
            'other_before throws(IllegalStateException)' in out)
    require('the per-thread entry point fixes that thread too', 'other_after present' in out)
    require('and the ARB call works on that thread as well',
            'other_createShaderObjectARB 0' in out)

    if not all(checks):
        print(f'{checks.count(False)} contract check(s) failed')
        return 1
    print(f'{len(checks)} contract checks passed')
    return 0


if __name__ == '__main__':
    sys.exit(main())
