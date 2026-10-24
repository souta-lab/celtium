import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;
import org.objectweb.asm.*;
import org.soutalab.celtium.vulkan.NativeRasterState;
import org.taumc.celeritas.core.CeltiumGllessNativeTransformer;

/** Executes transformed native declarations from the real LWJGL jar without initializing a GL class or context. */
public class NativeRasterAdapterCheck {
    public static void main(String[] args) throws Exception {
        String probe = "org.lwjgl.opengl.CeltiumRasterAbiProbe";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, probe.replace('.', '/'), null, "java/lang/Object", null);
        Map<String, String> signatures = new HashMap<>();
        try (JarFile jar = new JarFile(args[0])) {
            for (String cls : new String[] {"GL11C", "GL14C", "GL20C"}) {
                try (InputStream stream = jar.getInputStream(jar.getJarEntry("org/lwjgl/opengl/" + cls + ".class"))) {
                    new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                String signature, String[] exceptions) {
                            if ((access & Opcodes.ACC_NATIVE) != 0 && java.util.Set.of("glBlendFunc", "glBlendFuncSeparate",
                                    "glBlendEquation", "glBlendEquationSeparate", "glColorMask", "glCullFace", "glFrontFace",
                                    "glPolygonOffset", "glScissor").contains(name)) {
                                String old = signatures.putIfAbsent(name, descriptor);
                                if (old == null) writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
                                        name, descriptor, null, null).visitEnd();
                            }
                            return null;
                        }
                    }, ClassReader.SKIP_CODE);
                }
            }
        }
        writer.visitEnd();
        check(signatures.size() == 9, "real LWJGL declarations missing: " + signatures);
        byte[] rewritten = new CeltiumGllessNativeTransformer().transform(probe, probe, writer.toByteArray());
        Class<?> api = new ClassLoader(NativeRasterAdapterCheck.class.getClassLoader()) {
            Class<?> define() { return defineClass(probe, rewritten, 0, rewritten.length); }
        }.define();
        NativeRasterState state = NativeRasterState.current();
        api.getMethod("glBlendFunc", int.class, int.class).invoke(null, 1, 0);
        check(state.blendSrcRgb() == 1 && state.blendDstAlpha() == 0, "two factors not forwarded");
        api.getMethod("glBlendFuncSeparate", int.class, int.class, int.class, int.class).invoke(null, 0x302, 0x303, 1, 0);
        check(state.blendSrcRgb() == 0x302 && state.blendDstRgb() == 0x303 && state.blendSrcAlpha() == 1
                && state.blendDstAlpha() == 0, "four factor ABI/order");
        api.getMethod("glBlendEquation", int.class).invoke(null, 0x800A);
        check(state.blendEquationRgb() == 0x800A && state.blendEquationAlpha() == 0x800A, "equation");
        api.getMethod("glBlendEquationSeparate", int.class, int.class).invoke(null, 0x8006, 0x800B);
        check(state.blendEquationRgb() == 0x8006 && state.blendEquationAlpha() == 0x800B, "separate equations");
        api.getMethod("glColorMask", boolean.class, boolean.class, boolean.class, boolean.class)
                .invoke(null, true, false, true, false);
        check(state.colorMask() == 5, "boolean argument/channel ABI");
        api.getMethod("glCullFace", int.class).invoke(null, 0x0404);
        api.getMethod("glFrontFace", int.class).invoke(null, 0x0900);
        check(state.cullFace() == 0x0404 && state.frontFace() == 0x0900, "faces");
        api.getMethod("glPolygonOffset", float.class, float.class).invoke(null, -1.25f, -3.5f);
        check(state.polygonOffsetFactor() == -1.25f && state.polygonOffsetUnits() == -3.5f, "float slot/bit ABI");
        api.getMethod("glScissor", int.class, int.class, int.class, int.class).invoke(null, 3, 5, 111, 203);
        check(java.util.Arrays.equals(state.scissorBox(), new int[] {3, 5, 111, 203}), "rectangle argument order");
        System.out.println("PASS: 9 actual LWJGL native declarations execute against the Vulkan owner (CPU ABI only).");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
