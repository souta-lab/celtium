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
 * returns this project's identity string, and tracked state queries read the renderer's semantic state, with the
 * component count required by the query. Unsupported operations still return the type's zero value. Bodies are
 * straight-line and frame-free; maximum stack sizes are computed without replacing untouched methods' frames.
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
            // Query helpers consume an int plus a pointer (three stack slots), and some existing helpers
            // consume more. Preserve existing frames, but calculate each replacement's actual stack bound.
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
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
                    if (REWRITE_CENSUS) {
                        // Opt-in (celtium.glless.census=true): a per-call census of the natives this transformer
                        // rewrites, so the calls that are dropped can be named instead of inferred. The stub's own
                        // no-op census cannot answer it - measured on leg gv-cohort-cloud1, whose log has no
                        // CELTIUM_NOOP_CENSUS line at all, because under GL-less these calls never reach the stub.
                        // Off by default: one map increment per GL call is not free in a renderer's steady state.
                        method.visitLdcInsn(methodName);
                        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                                "noteRewritten", "(Ljava/lang/String;)V", false);
                    }
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

    /**
     * The raw GL11 matrix natives, forwarded to CeltiumGllessHint's mirror routes (which skip calls made inside a vanilla
     * GlStateManager method). Floats are widened to the double routes so each operation has one entry point.
     * glMultMatrix is deliberately not routed: vanilla's camera calls it raw through Project.gluPerspective, which
     * GluProjectionMixin already mirrors - routing it too drew the world with the projection applied twice (blank).
     */
    private static boolean emitRawMatrixRoute(MethodVisitor method, String name, String descriptor) {
        String hint = "org/taumc/celeritas/core/CeltiumGllessHint";
        switch (name + descriptor) {
            case "glPushMatrix()V":
            case "glPopMatrix()V":
            case "glLoadIdentity()V":
                method.visitMethodInsn(Opcodes.INVOKESTATIC, hint, name, "()V", false);
                break;
            case "glMatrixMode(I)V":
                method.visitVarInsn(Opcodes.ILOAD, 0);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, hint, name, "(I)V", false);
                break;
            case "glOrtho(DDDDDD)V":
                for (int slot = 0; slot < 12; slot += 2) method.visitVarInsn(Opcodes.DLOAD, slot);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, hint, "glOrtho", "(DDDDDD)V", false);
                break;
            case "glTranslated(DDD)V":
            case "glScaled(DDD)V":
                for (int slot = 0; slot < 6; slot += 2) method.visitVarInsn(Opcodes.DLOAD, slot);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, hint, name, "(DDD)V", false);
                break;
            case "glTranslatef(FFF)V":
            case "glScalef(FFF)V":
                for (int slot = 0; slot < 3; slot++) {
                    method.visitVarInsn(Opcodes.FLOAD, slot);
                    method.visitInsn(Opcodes.F2D);
                }
                method.visitMethodInsn(Opcodes.INVOKESTATIC, hint,
                        name.startsWith("glTranslate") ? "glTranslated" : "glScaled", "(DDD)V", false);
                break;
            case "glRotated(DDDD)V":
                for (int slot = 0; slot < 8; slot += 2) method.visitVarInsn(Opcodes.DLOAD, slot);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, hint, "glRotated", "(DDDD)V", false);
                break;
            case "glRotatef(FFFF)V":
                for (int slot = 0; slot < 4; slot++) {
                    method.visitVarInsn(Opcodes.FLOAD, slot);
                    method.visitInsn(Opcodes.F2D);
                }
                method.visitMethodInsn(Opcodes.INVOKESTATIC, hint, "glRotated", "(DDDD)V", false);
                break;
            default:
                return false;
        }
        method.visitInsn(Opcodes.RETURN);
        return true;
    }

    /**
     * The raw GL11 colour and depth-mask natives, forwarded to CeltiumGllessHint, which feeds the same mirror
     * GlStateManager's hooks do. Every form is widened to one entry point: floats as given, a missing alpha as 1,
     * doubles narrowed, unsigned bytes through glColor4ub.
     */
    private static boolean emitRawColourRoute(MethodVisitor method, String name, String descriptor) {
        String hint = "org/taumc/celeritas/core/CeltiumGllessHint";
        switch (name + descriptor) {
            case "glColor4f(FFFF)V":
                for (int slot = 0; slot < 4; slot++) method.visitVarInsn(Opcodes.FLOAD, slot);
                break;
            case "glColor3f(FFF)V":
                for (int slot = 0; slot < 3; slot++) method.visitVarInsn(Opcodes.FLOAD, slot);
                method.visitInsn(Opcodes.FCONST_1);
                break;
            case "glColor4d(DDDD)V":
                for (int slot = 0; slot < 8; slot += 2) {
                    method.visitVarInsn(Opcodes.DLOAD, slot);
                    method.visitInsn(Opcodes.D2F);
                }
                break;
            case "glColor3d(DDD)V":
                for (int slot = 0; slot < 6; slot += 2) {
                    method.visitVarInsn(Opcodes.DLOAD, slot);
                    method.visitInsn(Opcodes.D2F);
                }
                method.visitInsn(Opcodes.FCONST_1);
                break;
            case "glColor4ub(BBBB)V":
                for (int slot = 0; slot < 4; slot++) method.visitVarInsn(Opcodes.ILOAD, slot);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, hint, "glColor4ub", "(BBBB)V", false);
                method.visitInsn(Opcodes.RETURN);
                return true;
            case "glColor3ub(BBB)V":
                for (int slot = 0; slot < 3; slot++) method.visitVarInsn(Opcodes.ILOAD, slot);
                method.visitInsn(Opcodes.ICONST_M1);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, hint, "glColor4ub", "(BBBB)V", false);
                method.visitInsn(Opcodes.RETURN);
                return true;
            case "glDepthMask(Z)V":
                method.visitVarInsn(Opcodes.ILOAD, 0);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, hint, "glDepthMask", "(Z)V", false);
                method.visitInsn(Opcodes.RETURN);
                return true;
            default:
                return false;
        }
        method.visitMethodInsn(Opcodes.INVOKESTATIC, hint, "glColor4f", "(FFFF)V", false);
        method.visitInsn(Opcodes.RETURN);
        return true;
    }

    /** {@code -Dceltium.glless.census=true} makes every rewritten native count its own calls; see the emitter. */
    private static final boolean REWRITE_CENSUS = Boolean.getBoolean("celtium.glless.census");

    /** The raw GL raster ABI is forwarded as typed arguments, with no per-call array or libffi trampoline. */
    private static boolean emitNativeRasterAdapter(MethodVisitor method, String name, String descriptor) {
        String entry = name.startsWith("ngl") ? name.substring(1) : name;
        String expected;
        switch (entry) {
            case "glBlendFunc": expected = "(II)V"; break;
            case "glBlendFuncSeparate": expected = "(IIII)V"; break;
            case "glBlendEquation": expected = "(I)V"; break;
            case "glBlendEquationSeparate": expected = "(II)V"; break;
            case "glColorMask": expected = "(ZZZZ)V"; break;
            case "glCullFace":
            case "glFrontFace": expected = "(I)V"; break;
            case "glPolygonOffset": expected = "(FF)V"; break;
            case "glScissor": expected = "(IIII)V"; break;
            default: return false;
        }
        if (!expected.equals(descriptor)) return false;
        int slot = 0;
        for (org.objectweb.asm.Type argument : org.objectweb.asm.Type.getArgumentTypes(descriptor)) {
            method.visitVarInsn(argument.getOpcode(Opcodes.ILOAD), slot);
            slot += argument.getSize();
        }
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/soutalab/celtium/vulkan/gl/NativeRasterAdapter",
                entry, descriptor, false);
        method.visitInsn(Opcodes.RETURN);
        return true;
    }

    private static void emitDefaultReturn(MethodVisitor method, String name, String descriptor) {
        if (emitNativeRasterAdapter(method, name, descriptor)) return;
        if (emitRawColourRoute(method, name, descriptor)) return;
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
        // Uniform locations are the exception: vanilla's ShaderManager drops every uniform and sampler whose location
        // is -1, and the native post chain (ShaderGroupMixin) reads exactly those ShaderUniform objects, so -1 left
        // every post shader without its uniforms ("Uniform 'BlurDir' does not exist", entity_outline.json; leg
        // postshader-creeper-amd-1). The answer is a stable non-negative location derived from the name; the glUniform
        // calls that receive it are no-ops here.
        if ("(IJ)I".equals(descriptor) && (name.endsWith("nglGetUniformLocation")
                || name.endsWith("nglGetUniformLocationARB"))) {
            method.visitVarInsn(Opcodes.LLOAD, 1);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                    "glUniformLocation", "(J)I", false);
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
        // These typed natives otherwise return without ever reaching the existing pending-clear producer.
        if ("glClear".equals(name) && "(I)V".equals(descriptor)) {
            method.visitVarInsn(Opcodes.ILOAD, 0);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                    "glClear", "(I)V", false);
            method.visitInsn(Opcodes.RETURN);
            return;
        }
        if ("glClearColor".equals(name) && "(FFFF)V".equals(descriptor)) {
            method.visitVarInsn(Opcodes.FLOAD, 0);
            method.visitVarInsn(Opcodes.FLOAD, 1);
            method.visitVarInsn(Opcodes.FLOAD, 2);
            method.visitVarInsn(Opcodes.FLOAD, 3);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                    "glClearColor", "(FFFF)V", false);
            method.visitInsn(Opcodes.RETURN);
            return;
        }
        if ("glClearDepth".equals(name) && "(D)V".equals(descriptor)) {
            method.visitVarInsn(Opcodes.DLOAD, 0);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                    "glClearDepth", "(D)V", false);
            method.visitInsn(Opcodes.RETURN);
            return;
        }
        if ("glClearDepthf".equals(name) && "(F)V".equals(descriptor)) {
            method.visitVarInsn(Opcodes.FLOAD, 0);
            method.visitInsn(Opcodes.F2D);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                    "glClearDepth", "(D)V", false);
            method.visitInsn(Opcodes.RETURN);
            return;
        }
        if ("glViewport".equals(name) && "(IIII)V".equals(descriptor)) {
            method.visitVarInsn(Opcodes.ILOAD, 0);
            method.visitVarInsn(Opcodes.ILOAD, 1);
            method.visitVarInsn(Opcodes.ILOAD, 2);
            method.visitVarInsn(Opcodes.ILOAD, 3);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                    "glViewport", "(IIII)V", false);
            method.visitInsn(Opcodes.RETURN);
            return;
        }
        // Matrix queries are arrays, not scalar zero-returning functions. ActiveRenderInfo reads both
        // matrices before unprojecting the camera; ClippingHelper reads them before terrain visibility.
        // Dispatch to the existing state mirror for all 16 components in the caller's numeric type.
        if ("(IJ)V".equals(descriptor)
                && ("nglGetFloatv".equals(name) || "nglGetDoublev".equals(name))) {
            method.visitVarInsn(Opcodes.ILOAD, 0);
            method.visitVarInsn(Opcodes.LLOAD, 1);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                    "nglGetDoublev".equals(name) ? "glGetDoublev" : "glGetFloatv", "(IJ)V", false);
            method.visitInsn(Opcodes.RETURN);
            return;
        }
        // Shader and program status queries: the caller reads the buffer (iv forms) or the return value (i forms), and
        // vanilla decides from them whether a shader is usable. Same family as glGetIntegerv, same reason. LWJGL 3's
        // natives carry the object first - nglGetShaderiv(int shader, int pname, long params), (IIJ)V - so the answer
        // is chosen by the second argument. Matching "(IJ)" here, as this did before, never matched a real native:
        // the generic fallback wrote 0 for GL_COMPILE_STATUS and every vanilla post shader failed to load
        // ("Invalid shaders/program/sobel.vsh: Couldn't compile vertex program", leg split4-creeper-amd-1).
        if ("(IIJ)V".equals(descriptor) && (name.endsWith("nglGetShaderiv") || name.endsWith("nglGetProgramiv")
                || name.endsWith("nglGetObjectParameterivARB"))) {
            method.visitVarInsn(Opcodes.ILOAD, 1);
            method.visitVarInsn(Opcodes.LLOAD, 2);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                    "glGetShaderProgramiv", "(IJ)V", false);
            method.visitInsn(Opcodes.RETURN);
            return;
        }
        if ("(II)I".equals(descriptor) && (name.endsWith("nglGetShaderi") || name.endsWith("nglGetProgrami"))) {
            method.visitVarInsn(Opcodes.ILOAD, 1);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                    "glGetShaderProgrami", "(I)I", false);
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
        // The int-returning twin: LWJGL's glGetTexLevelParameteri has its own native and returns the value rather
        // than writing a buffer, so the rule below never covered it and textureSize() read zero for every texture.
        // See CeltiumGllessHint.glGetTexLevelParameteri for the measurement.
        if (returnType.equals("I") && name.endsWith("nglGetTexLevelParameteri") && descriptor.startsWith("(III)")) {
            method.visitVarInsn(Opcodes.ILOAD, 0);
            method.visitVarInsn(Opcodes.ILOAD, 1);
            method.visitVarInsn(Opcodes.ILOAD, 2);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                    "glGetTexLevelParameteri", "(III)I", false);
            method.visitInsn(Opcodes.IRETURN);
            return;
        }
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
        // The texture-env family, both directions. RenderLivingBase.setBrightness writes the hurt overlay through
        // GlStateManager.glTexEnv*, lwjglx delegates those to these natives, and the generic rewrite below would drop
        // them - measured on legs plain-hurt3/plain-hurt4: a hurt zombie rendered untinted while the stub recorded
        // zero COMBINE writes. Routed to the stub's per-unit state instead.
        if ("glTexEnvi".equals(name) && "(III)V".equals(descriptor)) {
            method.visitVarInsn(Opcodes.ILOAD, 0);
            method.visitVarInsn(Opcodes.ILOAD, 1);
            method.visitVarInsn(Opcodes.ILOAD, 2);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                    "glTexEnvi", "(III)V", false);
            method.visitInsn(Opcodes.RETURN);
            return;
        }
        if ("glTexEnvf".equals(name) && "(IIF)V".equals(descriptor)) {
            method.visitVarInsn(Opcodes.ILOAD, 0);
            method.visitVarInsn(Opcodes.ILOAD, 1);
            method.visitVarInsn(Opcodes.FLOAD, 2);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                    "glTexEnvf", "(IIF)V", false);
            method.visitInsn(Opcodes.RETURN);
            return;
        }
        if (("nglTexEnviv".equals(name) || "nglTexEnvfv".equals(name)) && "(IIJ)V".equals(descriptor)) {
            method.visitVarInsn(Opcodes.ILOAD, 0);
            method.visitVarInsn(Opcodes.ILOAD, 1);
            method.visitVarInsn(Opcodes.LLOAD, 2);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                    "glTexEnvPtr", "(IIJ)V", false);
            method.visitInsn(Opcodes.RETURN);
            return;
        }
        if ("nglGetTexEnviv".equals(name) && "(IIJ)V".equals(descriptor)) {
            method.visitVarInsn(Opcodes.ILOAD, 0);
            method.visitVarInsn(Opcodes.ILOAD, 1);
            method.visitVarInsn(Opcodes.LLOAD, 2);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                    "glGetTexEnvInt", "(IIJ)V", false);
            method.visitInsn(Opcodes.RETURN);
            return;
        }
        if ("nglGetTexEnvfv".equals(name) && "(IIJ)V".equals(descriptor)) {
            method.visitVarInsn(Opcodes.ILOAD, 0);
            method.visitVarInsn(Opcodes.ILOAD, 1);
            method.visitVarInsn(Opcodes.LLOAD, 2);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                    "glGetTexEnvFloats", "(IIJ)V", false);
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
        if ("(II)J".equals(descriptor) && (name.endsWith("nglMapBuffer") || name.endsWith("nglMapBufferARB"))) {
            // target, access -> address. The default zero became a null ByteBuffer in LWJGL's wrapper, which is
            // where Xaero's Minimap crashed (its PBO tile upload); the stub's buffer storage answers instead.
            method.visitVarInsn(Opcodes.ILOAD, 0);
            method.visitInsn(Opcodes.ICONST_0);
            method.visitInsn(Opcodes.LCONST_0);
            method.visitInsn(Opcodes.LCONST_0);
            method.visitVarInsn(Opcodes.ILOAD, 1);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                    "glMapBuffer", "(IZJJI)J", false);
            method.visitInsn(Opcodes.LRETURN);
            return;
        }
        if ("(IJJI)J".equals(descriptor) && name.endsWith("nglMapBufferRange")) {
            // target, offset, length, access -> address, range-checked by the stub.
            method.visitVarInsn(Opcodes.ILOAD, 0);
            method.visitInsn(Opcodes.ICONST_1);
            method.visitVarInsn(Opcodes.LLOAD, 1);
            method.visitVarInsn(Opcodes.LLOAD, 3);
            method.visitVarInsn(Opcodes.ILOAD, 5);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                    "glMapBuffer", "(IZJJI)J", false);
            method.visitInsn(Opcodes.LRETURN);
            return;
        }
        if ("(I)Z".equals(descriptor) && (name.endsWith("glUnmapBuffer") || name.endsWith("glUnmapBufferARB"))) {
            // GL returns false only when the store was corrupted; the default false told callers it was.
            method.visitVarInsn(Opcodes.ILOAD, 0);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                    "glUnmapBuffer", "(I)Z", false);
            method.visitInsn(Opcodes.IRETURN);
            return;
        }
        // Display lists. glNewList(list, mode), glEndList() and glCallList(list) all return void, so without this
        // they land in the zero-writer below and do nothing at all - which is why the model capture read list=0 from
        // GL_LIST_INDEX and refused every quad with `reason=unknown-list`, and why captured stayed at 0. A model
        // compiles its display list exactly once (ModelRenderer.render only calls compileDisplayList while compiled
        // is false, and the quads only run inside it), so that one refusal costs the model its only chance.
        // The hint tracks the open list and answers GL_LIST_INDEX from it; these three calls are what set and use it.
        // Both spellings again: GL11 keeps some legacy natives under their bare name, as glGenLists does.
        if (emitRawMatrixRoute(method, name, descriptor)) {
            return;
        }
        if (returnType.equals("V")) {
            if ("glNewList".equals(name) || "nglNewList".equals(name)) {
                // (II)V - the natives are static, so the arguments are at slots 0 and 1.
                method.visitVarInsn(Opcodes.ILOAD, 0);
                method.visitVarInsn(Opcodes.ILOAD, 1);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                        "glNewList", "(II)V", false);
                method.visitInsn(Opcodes.RETURN);
                return;
            }
            if ("glEndList".equals(name) || "nglEndList".equals(name)) {
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                        "glEndList", "()V", false);
                method.visitInsn(Opcodes.RETURN);
                return;
            }
            if ("glCallList".equals(name) || "nglCallList".equals(name)) {
                // (I)V
                method.visitVarInsn(Opcodes.ILOAD, 0);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                        "glCallList", "(I)V", false);
                method.visitInsn(Opcodes.RETURN);
                return;
            }
            // Texture parameters. Vanilla sets them through GlStateManager
            // (TextureUtil.glTexParameteri(3553, MIN/MAG_FILTER/MAX_LEVEL/WRAP, ...) - the sources' own lines 215-264),
            // so without this case they land in the zero-writer below and the stub records none of them. That is
            // measured in the picture: the registry asks textureParameterRecorded(texture, MIN/MAG_FILTER) and falls
            // back to inferring the filter from the upload's shape, which answers LINEAR for the whole-image upload an
            // item texture is - so a magnified GUI item is blurred, the user report's "item resolution is a little
            // low". Both spellings, as for glGenLists above: LWJGL3 keeps glTexParameteri as a wrapper over
            // nglTexParameteri, and whichever the transformer sees is the one rewritten.
            // Texture copies. The main menu's panorama is built by copying the framebuffer into its own 256x256
            // texture and blurring it (GuiMainMenu.renderSkybox -> rotateAndBlurSkybox -> glCopyTexSubImage2D), and the
            // stub's SEM_COPY_TEX_* case is what serves that copy through the frame target. Without this route the call
            // lands in the zero-writer below and the copy never happens: measured on leg gv-cohort-gui-menu
            // (-Dceltium.validation.menuHoldTicks=200), no CELTIUM_TEX_COPY_SERVED line, textureCopiesServed=0, and the
            // panorama's own 256x256 texture rejected as "no game pixels, nothing announced a write into it".
            if ("glCopyTexSubImage2D".equals(name) || "nglCopyTexSubImage2D".equals(name)) {
                // (IIIIIIII)V - the natives are static, so the eight arguments are at slots 0..7.
                for (int slot = 0; slot < 8; slot++) {
                    method.visitVarInsn(Opcodes.ILOAD, slot);
                }
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                        "glCopyTexSubImage2D", "(IIIIIIII)V", false);
                method.visitInsn(Opcodes.RETURN);
                return;
            }
            if ("glCopyTexImage2D".equals(name) || "nglCopyTexImage2D".equals(name)) {
                for (int slot = 0; slot < 8; slot++) {
                    method.visitVarInsn(Opcodes.ILOAD, slot);
                }
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                        "glCopyTexImage2D", "(IIIIIIII)V", false);
                method.visitInsn(Opcodes.RETURN);
                return;
            }
            // glReadPixels is the readback half of the same framebuffer contract the copies serve: a mod reads the
            // bound read framebuffer's pixels into a client buffer (ScreenShotHelper's screenshots, the validation
            // fixture's format-2 readback). Without the route it lands in the zero-writer below and every caller's
            // buffer stays zeroed - measured as the fixture's all-zero pixel checks in leg fbo-read-amd.
            if (("glReadPixels".equals(name) || "nglReadPixels".equals(name))
                    && descriptor.startsWith("(IIIIIIJ)")) {
                // (IIIIIIJ)V - x, y, width, height, format, type, pixels address. The descriptor guard keeps the
                // ByteBuffer wrapper (IIIIIILjava/nio/ByteBuffer)V out: it calls this form, so the address route
                // serves both spellings and a buffer argument never reaches an LLOAD slot.
                for (int slot = 0; slot < 6; slot++) {
                    method.visitVarInsn(Opcodes.ILOAD, slot);
                }
                method.visitVarInsn(Opcodes.LLOAD, 6);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                        "glReadPixels", "(IIIIIIJ)V", false);
                method.visitInsn(Opcodes.RETURN);
                return;
            }
            if ("nglGetTexImage".equals(name) && descriptor.equals("(IIIIJ)V")) {
                // The screenshot's read: target, level, format, type, pixels address. Served from the mirror of the
                // bound texture. This is the native LWJGL method, so the buffer form never appears here - the game's
                // own IntBuffer call is inside GL11, which forwards to this native, and this is the native the
                // transformer replaces.
                for (int slot = 0; slot < 4; slot++) {
                    method.visitVarInsn(Opcodes.ILOAD, slot);
                }
                method.visitVarInsn(Opcodes.LLOAD, 4);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                        "glGetTexImage", "(IIIIJ)V", false);
                method.visitInsn(Opcodes.RETURN);
                return;
            }
            // The client-array/VBO draw family. VertexBuffer.bindBuffer/glVertexPointer/glDrawArrays is how the sky
            // dome, the stars, the horizon plane and Forge's CloudRenderer draw; the stub decodes that shape, and
            // without this route the calls were no-ops (leg gv-cohort-cloud2's census: glDrawArrays=6220,
            // nglVertexPointer=5332, glEnableClientState=7117, glBindBuffer=12474 - and no cloud pixel in the frame).
            if ("glDrawArrays".equals(name) || "nglDrawArrays".equals(name)) {
                // (III)V
                for (int slot = 0; slot < 3; slot++) {
                    method.visitVarInsn(Opcodes.ILOAD, slot);
                }
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                        "glDrawArrays", "(III)V", false);
                method.visitInsn(Opcodes.RETURN);
                return;
            }
            if ("glBindBuffer".equals(name) || "nglBindBuffer".equals(name)) {
                // (II)V
                method.visitVarInsn(Opcodes.ILOAD, 0);
                method.visitVarInsn(Opcodes.ILOAD, 1);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                        "glBindBuffer", "(II)V", false);
                method.visitInsn(Opcodes.RETURN);
                return;
            }
            if ("glEnable".equals(name) || "nglEnable".equals(name)
                    || "glDisable".equals(name) || "nglDisable".equals(name)
                    || "glEnableClientState".equals(name) || "nglEnableClientState".equals(name)
                    || "glDisableClientState".equals(name) || "nglDisableClientState".equals(name)) {
                // (I)V
                boolean enable = name.startsWith("glEnable") || name.startsWith("nglEnable");
                method.visitVarInsn(Opcodes.ILOAD, 0);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                        enable ? "glEnableClientState" : "glDisableClientState", "(I)V", false);
                method.visitInsn(Opcodes.RETURN);
                return;
            }
            if ("nglVertexPointer".equals(name) || "glVertexPointer".equals(name)) {
                // (IIIJ)V - the last argument is the pointer or the VBO offset, a 64-bit value.
                method.visitVarInsn(Opcodes.ILOAD, 0);
                method.visitVarInsn(Opcodes.ILOAD, 1);
                method.visitVarInsn(Opcodes.ILOAD, 2);
                method.visitVarInsn(Opcodes.LLOAD, 3);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                        "glVertexPointer", "(IIIJ)V", false);
                method.visitInsn(Opcodes.RETURN);
                return;
            }
            if ("glFramebufferTexture2D".equals(name) || "nglFramebufferTexture2D".equals(name)) {
                // (IIIII)V - target, attachment, textarget, texture, level.
                for (int slot = 0; slot < 5; slot++) {
                    method.visitVarInsn(Opcodes.ILOAD, slot);
                }
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                        "glFramebufferTexture2D", "(IIIII)V", false);
                method.visitInsn(Opcodes.RETURN);
                return;
            }
            if ("glBindFramebuffer".equals(name) || "nglBindFramebuffer".equals(name)) {
                // (II)V - target, framebuffer.
                method.visitVarInsn(Opcodes.ILOAD, 0);
                method.visitVarInsn(Opcodes.ILOAD, 1);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                        "glBindFramebuffer", "(II)V", false);
                method.visitInsn(Opcodes.RETURN);
                return;
            }
            if ("nglTexImage2D".equals(name) || "glTexImage2D".equals(name)) {
                // (IIIIIIIIJ)V - target, level, internal, width, height, border, format, type, pixels.
                for (int slot = 0; slot < 8; slot++) {
                    method.visitVarInsn(Opcodes.ILOAD, slot);
                }
                method.visitVarInsn(Opcodes.LLOAD, 8);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                        "glTexImage2D", "(IIIIIIIIJ)V", false);
                method.visitInsn(Opcodes.RETURN);
                return;
            }
            if ("nglTexSubImage2D".equals(name) || "glTexSubImage2D".equals(name)) {
                // (IIIIIIIIJ)V - target, level, xoffset, yoffset, width, height, format, type, pixels.
                for (int slot = 0; slot < 8; slot++) {
                    method.visitVarInsn(Opcodes.ILOAD, slot);
                }
                method.visitVarInsn(Opcodes.LLOAD, 8);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                        "glTexSubImage2D", "(IIIIIIIIJ)V", false);
                method.visitInsn(Opcodes.RETURN);
                return;
            }
            if ("nglBufferData".equals(name) || "glBufferData".equals(name)) {
                // (IJJI)V - target, size, data address, usage.
                method.visitVarInsn(Opcodes.ILOAD, 0);
                method.visitVarInsn(Opcodes.LLOAD, 1);
                method.visitVarInsn(Opcodes.LLOAD, 3);
                method.visitVarInsn(Opcodes.ILOAD, 5);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                        "glBufferData", "(IJJI)V", false);
                method.visitInsn(Opcodes.RETURN);
                return;
            }
            if ("glTexParameteri".equals(name) || "nglTexParameteri".equals(name)) {
                // (III)V - the natives are static, so the arguments are at slots 0, 1 and 2.
                method.visitVarInsn(Opcodes.ILOAD, 0);
                method.visitVarInsn(Opcodes.ILOAD, 1);
                method.visitVarInsn(Opcodes.ILOAD, 2);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                        "glTexParameteri", "(III)V", false);
                method.visitInsn(Opcodes.RETURN);
                return;
            }
            if ("glBindTexture".equals(name) || "nglBindTexture".equals(name)) {
                // (II)V - target, name. Dropped entirely before the route existed: a mod's raw GL11.glBindTexture
                // left no record anywhere, so its upload was filed under the game's last binding and the name it
                // meant was refused "no mirror" - the E2E pack's texture=506 case (the renderer's own white
                // texture is created with the same direct call).
                method.visitVarInsn(Opcodes.ILOAD, 0);
                method.visitVarInsn(Opcodes.ILOAD, 1);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                        "glBindTexture", "(II)V", false);
                method.visitInsn(Opcodes.RETURN);
                return;
            }
            if ("glActiveTexture".equals(name) || "nglActiveTexture".equals(name)) {
                // (I)V - the unit enum; the stub records it so the unit-keyed binding map answers the right slot.
                method.visitVarInsn(Opcodes.ILOAD, 0);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                        "glActiveTexture", "(I)V", false);
                method.visitInsn(Opcodes.RETURN);
                return;
            }
            if ("nglDeleteTextures".equals(name) || "glDeleteTextures".equals(name)) {
                // (IJ)V - count, names pointer; deleting without the route left stale captures a reallocated name
                // would read as its own pixels.
                method.visitVarInsn(Opcodes.ILOAD, 0);
                method.visitVarInsn(Opcodes.LLOAD, 1);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/taumc/celeritas/core/CeltiumGllessHint",
                        "glDeleteTextures", "(IJ)V", false);
                method.visitInsn(Opcodes.RETURN);
                return;
            }
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
