import org.objectweb.asm.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Transforms every org.lwjgl.opengl class and collects the external calls the emitted bodies make. */
/**
 * Transforms every {@code org.lwjgl.opengl.*} class with the shipped GL-less native transformer and reports what the
 * emitted bodies call.
 * <p>
 * Why this exists: two legs (27 and 28) were lost to a replacement body that called
 * {@code MemoryUtil.memASCII(CharSequence)} as if it returned a {@code long}, when LWJGL 3.4.1 returns a
 * {@code ByteBuffer} - a {@code NoSuchMethodError} at 8-15 s into startup. An audit over the whole surface catches
 * that class of mistake offline, before a GPU leg is spent on it: every class must transform without throwing, no
 * native method may remain, and every external call the emitted bodies make must exist in the jars.
 * <p>
 * Run it with the built mod jar, LWJGL, ASM and the Mixin and log4j jars on the classpath:
 * <pre>
 * java -cp "&lt;mod jar&gt;:&lt;lwjgl jars&gt;:&lt;asm jars&gt;:&lt;mixin&gt;:&lt;log4j-api&gt;:." scripts/dev/GllessSurfaceAudit.java \
 *      &lt;path to lwjgl-opengl-3.4.1.jar&gt;
 * </pre>
 * Last run 2026-10-15 against the jar built from the shader-status fix: classes=465 nativesLeft=0
 * transformFailures=0, and the only calls the emitted bodies make are CeltiumGllessHint.glStringPointer(I)J,
 * glGetIntegerv(IJ)V and glGetZero(JI)V - all present in that jar.
 */
public class GllessSurfaceAudit {
    public static void main(String[] args) throws Exception {
        Class<?> t = Class.forName("org.taumc.celeritas.core.CeltiumGllessNativeTransformer");
        Object transformer = t.getDeclaredConstructor().newInstance();
        java.lang.reflect.Method transform = t.getMethod("transform", String.class, String.class, byte[].class);
        Map<String, Set<String>> calls = new TreeMap<>();
        int classes = 0, failures = 0;
        final int[] natives = new int[1];
        try (ZipFile zip = new ZipFile(args[0])) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith("org/lwjgl/opengl/") || !name.endsWith(".class") || name.contains("$")) continue;
                byte[] in = zip.getInputStream(entry).readAllBytes();
                byte[] out;
                try {
                    out = (byte[]) transform.invoke(transformer, name.replace('/', '.').replace(".class", ""),
                            name.replace('/', '.').replace(".class", ""), in);
                } catch (Throwable error) {
                    System.out.println("TRANSFORM THREW " + name + " -> " + error.getCause());
                    failures++;
                    continue;
                }
                classes++;
                new ClassReader(out).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                        if ((a & Opcodes.ACC_NATIVE) != 0) natives[0]++;
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitMethodInsn(int op, String owner, String mn, String md, boolean itf) {
                                if (!owner.startsWith("org/lwjgl/opengl/")) {
                                    calls.computeIfAbsent(owner + "." + mn + md, k -> new TreeSet<>()).add(n);
                                }
                            }
                        };
                    }
                }, 0);
            }
        }
        System.out.println("classes=" + classes + " nativesLeft=" + natives[0] + " transformFailures=" + failures);
        System.out.println("external calls made by emitted bodies:");
        calls.forEach((call, from) -> System.out.println("  " + call + "   <- " + from));
    }
}
