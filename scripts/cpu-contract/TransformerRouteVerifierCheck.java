/*
 * CPU check: runs CeltiumGllessNativeTransformer over real LWJGL native declarations and lets the JVM verifier load the
 * result. A route whose emitted bytecode fails verification would fail a whole GL class at runtime, so this catches the
 * crash class the compiler and the source audits cannot see.
 *
 * Usage: transformer-route-verify.sh (assembly of the classpath and the invocation live there).
 */
import java.io.InputStream;
import java.lang.reflect.*;
import java.util.*;
import java.util.jar.JarFile;
import org.objectweb.asm.*;
import org.taumc.celeritas.core.CeltiumGllessNativeTransformer;

public class TransformerRouteVerifierCheck {
    static final String[] NAMES = {
        "nglGetIntegerv", "nglGetFloatv", "nglGetBooleanv", "nglGetDoublev",
        "nglGetInteger64v", "nglGetIntegeri_v", "nglGetInteger64i_v",
        "nglGetBooleani_v", "glGetBooleani_v", "nglGetFloati_v", "nglGetDoublei_v",
        "nglGetLightfv", "nglGetLightiv", "nglGetMaterialfv", "nglGetMaterialiv",
        "nglGetVertexAttribiv", "nglGetVertexAttribfv", "nglGetVertexAttribdv",
        "nglGetVertexAttribIiv", "nglGetVertexAttribIuiv", "nglGetVertexAttribPointerv",
        "nglGetBufferPointerv", "nglGetPointerv", "nglGetUniformfv", "nglGetUniformiv",
        "glGetObjectLabel", "glGetObjectPtrLabel", "nglGetSynciv",
        "nglGetQueryObjectiv", "nglGetQueryObjecti64v", "glIsEnabledi", "glIsEnabled",
        "glIsTexture", "glIsList", "glEnablei", "glDisablei",
        "nglMultiDrawArrays", "nglMultiDrawElements",
        "nglDrawElements", "nglDrawRangeElements", "nglDrawElementsBaseVertex",
        "glDrawArraysInstanced", "nglDrawElementsInstanced",
        "nglDrawElementsInstancedBaseVertex", "nglDrawElementsInstancedBaseInstance",
        "nglDrawElementsInstancedBaseVertexBaseInstance", "glDrawArraysInstancedBaseInstance",
        "nglVertexAttribPointer", "glVertexAttribIPointer",
        "glEnableVertexAttribArray", "glDisableVertexAttribArray",
        "glMapNamedBuffer", "glMapNamedBufferRange", "glUnmapNamedBuffer",
        "glClearBufferData", "nglClearBufferSubData", "nglClearNamedBufferData",
        "nglClearNamedBufferSubData", "nglGetNamedBufferParameteriv",
        "nglGetNamedBufferParameteri64v", "nglGetNamedFramebufferParameteriv",
        "nglGetNamedRenderbufferParameteriv", "nglGetTextureLevelParameteriv",
        "nglGetTextureParameteriv", "nglGetTextureParameterIiv",
        "nglGetTextureImage", "nglGetTextureSubImage", "glTextureStorage2D",
        "glTextureStorage3D", "nglTextureSubImage2D",
        "glGetTexParameteri", "nglGetTexParameteriv", "glTexParameterf", "nglTexParameteriv",
        "nglTexStorage2D", "nglTexStorage3D", "nglGetTexEnvfv", "nglGetTexEnviv",
        "glDepthMask", "glDepthFunc", "glBlendFuncSeparatei", "glBlendEquationi", "glColorMaski",
        "glLineWidth", "glPointSize", "glPixelStorei",
        "glPushAttrib", "glPopAttrib", "glPushClientAttrib", "glPopClientAttrib",
        "glFogf", "glFogi", "nglFogfv", "nglFogiv",
        "nglLightfv", "nglLightModelfv", "glListBase", "nglCallLists",
        "glDeleteLists", "glNewList", "glEndList", "glCallList",
        "nglGetProgramResourceiv", "nglGetProgramResourceName", "nglGetActiveUniform",
        "nglGetActiveAttrib", "nglGetShaderSource", "nglGetAttachedShaders",
        "nglGetProgramBinary", "nglGetShaderPrecisionFormat",
        "glGetProgramResourceIndex", "glGetUniformBlockIndex", "glGetFragDataLocation",
        "glGetProgramResourceLocation", "glGetProgramResourceLocationIndex",
        "glCheckNamedFramebufferStatus", "glCheckFramebufferStatus",
        "glNamedFramebufferTexture", "glNamedFramebufferTextureLayer",
        "nglGetBufferSubData", "glGetNamedBufferSubData", "glCopyBufferSubData",
        "glCopyNamedBufferSubData", "nglBufferData", "nglBufferSubData",
        "nglCopyTexSubImage2D", "glClearDepth", "glClearDepthf", "glClearColor", "glViewport", "glScissor",
        "nglGetProgramResourceLocation", "nglGetProgramResourceLocationIndex",
        "glTexSubImage2D", "nglTexSubImage2D", "nglTexImage2D", "glBindTexture",
        "nglGetTextureParameterfv", "nglGetTextureParameterIuiv",
        "nglGetSamplerParameteriv", "nglGetSamplerParameterfv", "glGetMultisamplefv",
        "nglTextureParameteri", "nglTextureParameterf", "nglTextureParameteriv", "nglTextureParameterfv",
        "nglTextureParameterIiv", "nglTextureParameterIuiv", "glTextureParameteri", "glTextureParameterf",
        "nglGetTextureParameteriv", "nglGetTextureParameterIiv", "nglGetTextureParameterIuiv",
        "nglGetTexParameterIiv", "nglGetTexParameterIuiv", "nglGetTexParameterfv",
        "nglGetTexLevelParameteriv", "nglGetTexLevelParameteri", "nglGetBufferParameteriv",
        "nglGetBufferParameteri64v", "nglGetVertexAttribPointerv", "nglGetVertexAttribIiv",
        "nglGetVertexAttribIuiv", "nglGetVertexAttribfv", "nglGetVertexAttribdv",
        "nglGetMaterialiv", "nglGetMaterialfv", "nglGetLightfv", "nglGetLightiv",
        "nglBlendEquationSeparatei", "nglBlendFuncSeparatei", "nglColorMaski",
        "glNamedRenderbufferStorage", "glNamedRenderbufferStorageMultisample",
        "glNamedFramebufferRenderbuffer", "nglGetNamedFramebufferAttachmentParameteriv",
        "nglGetFramebufferAttachmentParameteriv", "nglGetRenderbufferParameteriv",
        "nglClearTexImage", "nglClearTexSubImage",
    };

    public static void main(String[] args) throws Exception {
        Map<String, String> found = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(args[0])) {
            // Every core class in the jar, not a chosen subset: the same native can live in a C class or a version
            // class depending on how LWJGL generated it, and a missed declaration would silently shrink the check.
            for (String entry : Collections.list(jar.entries()).stream().map(e -> e.getName())
                    .filter(n -> n.startsWith("org/lwjgl/opengl/GL") && n.endsWith(".class"))
                    .sorted().collect(java.util.stream.Collectors.toList())) {
                String cls = entry.substring("org/lwjgl/opengl/".length(), entry.length() - ".class".length());
                if (jar.getJarEntry(entry) == null) continue;
                try (InputStream stream = jar.getInputStream(jar.getJarEntry(entry))) {
                    new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                String signature, String[] exceptions) {
                            if ((access & Opcodes.ACC_NATIVE) != 0) {
                                for (String wanted : NAMES) {
                                    if (wanted.equals(name)) found.putIfAbsent(name, descriptor);
                                }
                            }
                            return null;
                        }
                    }, ClassReader.SKIP_CODE);
                }
            }
        }
        System.out.println("declarations found: " + found.size() + "/" + NAMES.length);
        String internal = "org/lwjgl/opengl/CeltiumRouteProbe";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, internal, null, "java/lang/Object", null);
        for (Map.Entry<String, String> e : found.entrySet()) {
            writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE, e.getKey(), e.getValue(),
                    null, null).visitEnd();
        }
        writer.visitEnd();
        byte[] rewritten = new CeltiumGllessNativeTransformer().transform("org.lwjgl.opengl.CeltiumRouteProbe",
                "org.lwjgl.opengl.CeltiumRouteProbe", writer.toByteArray());
        Class<?> api;
        try {
            api = new ClassLoader(TransformerRouteVerifierCheck.class.getClassLoader()) {
                Class<?> define() { return defineClass("org.lwjgl.opengl.CeltiumRouteProbe", rewritten, 0, rewritten.length); }
            }.define();
            api.getDeclaredMethods();
        } catch (VerifyError | ClassFormatError error) {
            System.out.println("DEFINE-FAIL " + error);
            System.exit(1);
            return;
        }
        int called = 0, failed = 0;
        for (Method m : api.getDeclaredMethods()) {
            Class<?>[] types = m.getParameterTypes();
            Object[] zeros = new Object[types.length];
            for (int i = 0; i < zeros.length; i++) {
                if (types[i] == boolean.class) zeros[i] = false;
                else if (types[i] == long.class) zeros[i] = 0L;
                else if (types[i] == float.class) zeros[i] = 0f;
                else if (types[i] == double.class) zeros[i] = 0d;
                else if (types[i] == byte.class) zeros[i] = (byte) 0;
                else if (types[i] == short.class) zeros[i] = (short) 0;
                else zeros[i] = 0;
            }
            try { m.invoke(null, zeros); called++; }
            catch (InvocationTargetException e) {
                if (e.getCause() instanceof VerifyError || e.getCause() instanceof ClassFormatError) {
                    System.out.println("VERIFY-FAIL " + m.getName() + m.getParameterTypes().length + " -> " + e.getCause());
                    failed++;
                } else { called++; }
            }
        }
        System.out.println("called=" + called + " failed=" + failed);
        if (failed != 0) System.exit(1);
    }
}
