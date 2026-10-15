package org.taumc.celeritas.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.taumc.celeritas.mixin.CeleritasVintageMixinPlugin;

/**
 * Replaces LWJGL3's native GL methods with Java bodies, so a mod that calls them cannot abort the JVM.
 * <p>
 * The GL-less window has no OpenGL context, and LWJGL3's generated GL classes are {@code public static native}
 * methods that dispatch through a per-thread function-address table. Without a context LWJGL either throws a
 * catchable {@code IllegalStateException} (when no capabilities are set at all) or, when a capability set exists but
 * a given address is still zero, aborts the whole JVM with
 * <pre>
 * FATAL ERROR in native method: No context is current or a function that is not available in the current context
 * was called. The JVM will abort execution.
 * </pre>
 * Measured on 2026-10-15: legs {@code gv-e2e-268-fix128g..j} aborted this way in Thaumcraft's
 * {@code ClientProxy.preInit} ({@code ARBShaderObjects.glCreateShaderObjectARB}), in Flare's
 * {@code GpuInfo.queryGpuModel} ({@code GL11C.nglGetString}) and in {@code GL11C.nglGenTextures}. Installing a
 * {@code GLCapabilities} built from the stub fixed the first and made the second worse - it turns the catchable
 * exception into an abort - so the surface itself is replaced here instead of being made reachable.
 * <p>
 * The replacement keeps the methods' signatures and their meaning where a caller can act on it: {@code glGetString}
 * returns this project's identity string, {@code glGetError} returns {@code GL_NO_ERROR}, and everything else returns
 * the type's zero value. Bodies are straight-line and frame-free, and the class is written without recomputing
 * frames so untouched methods keep theirs.
 */
public class CeltiumGllessNativeTransformer implements IClassTransformer {
    private static final String PACKAGE = "org.lwjgl.opengl.";
    /** The LWJGL2-compatible Display is Java and must not be touched; only native methods are rewritten anyway. */
    private static final String DONT_TOUCH = "org.lwjgl.opengl.Display";
    private static final int ASM_API = 9 << 16;
    private static final String GET_STRING_DESC = "(I)Ljava/lang/String;";
    private static final String GET_STRING_I_DESC = "(II)Ljava/lang/String;";
    private static final String GET_ERROR_DESC = "()I";
    private static final String IDENTITY = "Celtium (GL-less Vulkan)";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        String target = transformedName != null ? transformedName : name;
        if (!target.startsWith(PACKAGE) || DONT_TOUCH.equals(target)) {
            return basicClass;
        }
        try {
            ClassReader reader = new ClassReader(basicClass);
            ClassWriter writer = new ClassWriter(reader, 0);
            int[] rewritten = new int[1];
            reader.accept(new ClassVisitor(ASM_API, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature,
                                                 String[] exceptions) {
                    if ((access & Opcodes.ACC_NATIVE) == 0) {
                        return super.visitMethod(access, methodName, descriptor, signature, exceptions);
                    }
                    rewritten[0]++;
                    MethodVisitor method = super.visitMethod(access & ~Opcodes.ACC_NATIVE, methodName, descriptor,
                            signature, exceptions);
                    method.visitCode();
                    emitDefaultReturn(method, methodName, descriptor);
                    method.visitMaxs(2, argumentSlots(access, descriptor));
                    method.visitEnd();
                    return null;
                }
            }, 0);
            if (rewritten[0] == 0) {
                return basicClass;
            }
            CeleritasVintageMixinPlugin.LOGGER.info(
                    "CELTIUM_GLLESS replaced {} native GL method(s) in {}", rewritten[0], target);
            return writer.toByteArray();
        } catch (Throwable error) {
            CeleritasVintageMixinPlugin.LOGGER.error(
                    "GL-less native replacement failed for {}; its native methods keep LWJGL's behaviour", target, error);
            return basicClass;
        }
    }

    private static void emitDefaultReturn(MethodVisitor method, String name, String descriptor) {
        String returnType = descriptor.substring(descriptor.indexOf(')') + 1);
        // Both the public wrapper's native and the "ngl" form take the name differently ((int) versus a pointer to
        // the encoded name), and LWJGL3 generates the pointer form - so match on the name and the return type rather
        // than on one descriptor. Returning this project's identity keeps a caller like Flare's GpuInfo working
        // instead of handing it a null.
        if (returnType.equals("Ljava/lang/String;") && (name.endsWith("glGetString"))) {
            method.visitLdcInsn(IDENTITY);
            method.visitInsn(Opcodes.ARETURN);
            return;
        }
        // LWJGL3's own form is nglGetString(int) returning a pointer to NUL-terminated bytes; glGetString then
        // decodes it. Handing back a pointer to this project's identity keeps that path working - returning 0 would
        // leave a caller decoding a null pointer.
        if (returnType.equals("J") && name.endsWith("glGetString") && descriptor.startsWith("(I)")) {
            method.visitVarInsn(Opcodes.ILOAD, 0);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                    "glStringPointer", "(I)J", false);
            method.visitInsn(Opcodes.LRETURN);
            return;
        }
        if (returnType.equals("Ljava/lang/String;") && name.endsWith("glGetStringi")) {
            method.visitInsn(Opcodes.ACONST_NULL);
            method.visitInsn(Opcodes.ARETURN);
            return;
        }
        if (GET_ERROR_DESC.equals(descriptor) && "glGetError".equals(name)) {
            method.visitInsn(Opcodes.ICONST_0);
            method.visitInsn(Opcodes.IRETURN);
            return;
        }
        // The fence pair: a GL fence object cannot exist without a GL context, and the generic fallback answers the
        // type's zero, which RenderAheadManager.endFrame reads as failure and throws on (measured on leg
        // gv-cohort-fix129, the first run that reached the world). See CeltiumGllessHint.glFenceSyncId for why the
        // bound the fence provided is still enforced, by the renderer's own frame-slot fences.
        if (returnType.equals("J") && name.endsWith("glFenceSync") && descriptor.startsWith("(II)")) {
            method.visitVarInsn(Opcodes.ILOAD, 0);
            method.visitVarInsn(Opcodes.ILOAD, 1);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                    "glFenceSyncId", "(II)J", false);
            method.visitInsn(Opcodes.LRETURN);
            return;
        }
        if (returnType.equals("I") && name.endsWith("glClientWaitSync") && descriptor.startsWith("(JIJ)")) {
            method.visitVarInsn(Opcodes.LLOAD, 0);
            method.visitVarInsn(Opcodes.ILOAD, 2);
            method.visitVarInsn(Opcodes.LLOAD, 3);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                    "glClientWaitSyncStatus", "(JIJ)I", false);
            method.visitInsn(Opcodes.IRETURN);
            return;
        }
        // Functions whose return value a caller checks, and where the type's zero is not a usable answer.
        // Measured 2026-10-15 (legs gv-cohort-fix128s / gv-e2e-268-fix128s): returning zero for
        // glCheckFramebufferStatus made vanilla throw "glCheckFramebufferStatus returned unknown status:0" during
        // startup, in both legs - the same shape headlessmc's README warns about for Buffer-returning methods, where
        // a default value is not a valid one. These are the two families known to be checked here: the framebuffer
        // status, which has to say COMPLETE for the game to use its framebuffer, and the location lookups, where -1
        // is the documented "not found" and 0 would name a real location.
        if (returnType.equals("I") && name.endsWith("glCheckFramebufferStatus")) {
            method.visitLdcInsn(Integer.valueOf(0x8CD5)); // GL_FRAMEBUFFER_COMPLETE
            method.visitInsn(Opcodes.IRETURN);
            return;
        }
        if (returnType.equals("I") && (name.endsWith("glGetUniformLocation") || name.endsWith("glGetAttribLocation")
                || name.endsWith("glGetSubroutineUniformLocation") || name.endsWith("glGetSubroutineIndex")
                || name.endsWith("glGetFragDataLocation") || name.endsWith("glGetFragDataIndex"))) {
            method.visitInsn(Opcodes.ICONST_M1);
            method.visitInsn(Opcodes.IRETURN);
            return;
        }
        // The integer queries write into the caller's buffer, so they cannot be answered by a return value at all:
        // the body has to forward both arguments to a helper. glGetIntegerv(int, IntBuffer) is Java in LWJGL3 and
        // reaches this native with the buffer's address, and Minecraft reads the result in eleven places.
        if (returnType.equals("V") && name.endsWith("nglGetIntegerv") && descriptor.startsWith("(IJ)")) {
            method.visitVarInsn(Opcodes.ILOAD, 0);
            method.visitVarInsn(Opcodes.LLOAD, 1);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                    "glGetIntegerv", "(IJ)V", false);
            method.visitInsn(Opcodes.RETURN);
            return;
        }
        // Shader and program status queries: the caller reads the buffer (iv forms) or the return value (i forms), and
        // vanilla decides from them whether a shader is usable. Same family as glGetIntegerv, same reason.
        if (returnType.equals("V") && (name.endsWith("nglGetShaderiv") || name.endsWith("nglGetProgramiv"))
                && descriptor.startsWith("(IJ)")) {
            method.visitVarInsn(Opcodes.ILOAD, 0);
            method.visitVarInsn(Opcodes.LLOAD, 1);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                    "glGetShaderProgramiv", "(IJ)V", false);
            method.visitInsn(Opcodes.RETURN);
            return;
        }
        if (returnType.equals("I") && (name.endsWith("nglGetShaderi") || name.endsWith("nglGetProgrami"))
                && descriptor.startsWith("(II)")) {
            method.visitVarInsn(Opcodes.ILOAD, 0);
            method.visitVarInsn(Opcodes.ILOAD, 1);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                    "glGetShaderProgrami", "(II)I", false);
            method.visitInsn(Opcodes.IRETURN);
            return;
        }
        // The array forms of the generators: nglGenTextures(int n, long out) and friends write their names into the
        // caller's buffer, and the count is in the signature, so the whole buffer can be filled safely.
        if (returnType.equals("V") && descriptor.startsWith("(IJ)")
                && (name.startsWith("nglGen") || name.startsWith("glGen")
                        || name.startsWith("nglCreate") || name.startsWith("glCreate"))) {
            method.visitVarInsn(Opcodes.ILOAD, 0);
            method.visitVarInsn(Opcodes.LLOAD, 1);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                    "glGenNames", "(IJ)V", false);
            method.visitInsn(Opcodes.RETURN);
            return;
        }
        // glGetTexLevelParameteriv carries the property being asked for, and Forge's cloud renderer divides by two of
        // its answers (GL_TEXTURE_WIDTH/HEIGHT, filled at CloudRenderer.java 479-480 and used as a modulus at 326).
        // The generic zero-writer below answers zero, which is not a usable divisor, so this one query gets a real
        // answer. See CeltiumGllessHint.glGetTexLevelParameteriv for why 256 is the value Forge asks about.
        if (returnType.equals("V") && name.endsWith("nglGetTexLevelParameteriv") && descriptor.startsWith("(IIIJ)")) {
            method.visitVarInsn(Opcodes.ILOAD, 0);
            method.visitVarInsn(Opcodes.ILOAD, 1);
            method.visitVarInsn(Opcodes.ILOAD, 2);
            method.visitVarInsn(Opcodes.LLOAD, 3);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                    "glGetTexLevelParameteriv", "(IIIJ)V", false);
            method.visitInsn(Opcodes.RETURN);
            return;
        }
        // The remaining single-value get functions: the caller reads its own buffer, so a no-op would leave
        // uninitialised memory there. Only the ones whose element count is exactly one are handled, because the
        // signature does not say how many bytes the caller allocated and writing more would corrupt the heap.
        if (returnType.equals("V") && descriptor.endsWith("J)V")) {
            int size = CeltiumGllessHint.glGetZeroSize(name);
            if (size > 0) {
                // The pointer is the last argument. Every one of these natives is static, so the slot is the sum of
                // the preceding arguments' slots with no `this` to account for.
                int slot = lastArgumentSlot(descriptor);
                method.visitVarInsn(Opcodes.LLOAD, slot);
                method.visitLdcInsn(Integer.valueOf(size));
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                        "glGetZero", "(JI)V", false);
                method.visitInsn(Opcodes.RETURN);
                return;
            }
        }
        // The id-returning generators: glGenLists, glGenTextures, glCreateShader and the rest. 0 means "no object" to
        // every caller, and vanilla itself throws when it sees one (GLAllocation.generateDisplayLists, measured on the
        // 268-mod pack), so these get a fresh non-zero name instead of the type's zero.
        // The array forms (nglGenTextures(int, long)) return void and are handled by the zero-writer below; only the
        // single-id forms reach this, nglGenLists(int) among them.
        // Both spellings: LWJGL3's GL11 keeps some legacy natives under their bare name, and glGenLists is one of them
        // (public static native int glGenLists(int) in GL11, absent from GL11C) - the first version of this only
        // matched the ngl* form, and an offline check of the transformed class showed it still returning iconst_0.
        if (returnType.equals("I") && (name.startsWith("nglGen") || name.startsWith("glGen")
                || name.startsWith("nglCreate") || name.startsWith("glCreate"))) {
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                    "glGenName", "()I", false);
            method.visitInsn(Opcodes.IRETURN);
            return;
        }
        switch (returnType) {
            case "V":
                method.visitInsn(Opcodes.RETURN);
                break;
            case "Z":
            case "B":
            case "C":
            case "S":
            case "I":
                method.visitInsn(Opcodes.ICONST_0);
                method.visitInsn(Opcodes.IRETURN);
                break;
            case "J":
                method.visitInsn(Opcodes.LCONST_0);
                method.visitInsn(Opcodes.LRETURN);
                break;
            case "F":
                method.visitInsn(Opcodes.FCONST_0);
                method.visitInsn(Opcodes.FRETURN);
                break;
            case "D":
                method.visitInsn(Opcodes.DCONST_0);
                method.visitInsn(Opcodes.DRETURN);
                break;
            default:
                method.visitInsn(Opcodes.ACONST_NULL);
                method.visitInsn(Opcodes.ARETURN);
                break;
        }
    }

    /** The argument slot count, needed because the rewritten method is not static-analysis computed. */
    /** {@return the local-variable slot of the last argument of a static method's descriptor} */
    private static int lastArgumentSlot(String descriptor) {
        int slot = 0;
        int index = 1;
        int last = 0;
        while (descriptor.charAt(index) != ')') {
            last = slot;
            char type = descriptor.charAt(index);
            switch (type) {
                case 'J':
                case 'D':
                    slot += 2;
                    index += 1;
                    break;
                case 'L':
                    slot += 1;
                    index = descriptor.indexOf(';', index) + 1;
                    break;
                case '[':
                    slot += 1;
                    while (descriptor.charAt(index) == '[') {
                        index++;
                    }
                    if (descriptor.charAt(index) == 'L') {
                        index = descriptor.indexOf(';', index) + 1;
                    } else {
                        index++;
                    }
                    break;
                default:
                    slot += 1;
                    index += 1;
                    break;
            }
        }
        return last;
    }

    private static int argumentSlots(int access, String descriptor) {
        int slots = (access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
        int index = 1;
        while (descriptor.charAt(index) != ')') {
            char type = descriptor.charAt(index);
            switch (type) {
                case 'J':
                case 'D':
                    // Two slots, one character: advancing by two characters walked past the ')' on any descriptor
                    // whose last argument is a long or a double - (D)V, (IJ)V, (IIIJ)V - and threw
                    // StringIndexOutOfBoundsException, which the caller's catch turned into "the original bytes keep
                    // LWJGL's behaviour". Measured on the real GL11C: every such method failed.
                    slots += 2;
                    index += 1;
                    break;
                case 'L':
                    slots += 1;
                    index = descriptor.indexOf(';', index) + 1;
                    break;
                case '[':
                    slots += 1;
                    while (descriptor.charAt(index) == '[') {
                        index++;
                    }
                    if (descriptor.charAt(index) == 'L') {
                        index = descriptor.indexOf(';', index) + 1;
                    } else {
                        index++;
                    }
                    break;
                default:
                    slots += 1;
                    index += 1;
                    break;
            }
        }
        return Math.max(slots, 1);
    }
}
