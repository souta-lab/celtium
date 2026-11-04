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
import java.util.jar.JarEntry;
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
        // The program binding. It had no route at all until the GTCEu bloom investigation found the stub's tracked
        // program stuck at 0: the transformer's bare-return fallback answered the native, so the backend's
        // custom-program refusal could never fire and a mod's full-screen composite was drawn with the world
        // transform. Both spellings are listed because LWJGL declares the JNI and the C form separately.
        "glUseProgram", "nglUseProgram",
        "nglGetTextureParameterfv", "nglGetTextureParameterIuiv",
        "nglGetSamplerParameteriv", "nglGetSamplerParameterfv", "glGetMultisamplefv",
        "nglColor3fv", "nglColor4fv", "nglColor3bv", "nglColor4bv", "nglColor3ubv", "nglColor4ubv",
        "glColor4b", "glColor3b", "glAlphaFunc",
        // The legacy immediate-mode surface, in every vertex spelling the transformer routes. These are only routed
        // with -Dceltium.gless.legacyImmediate=true, so the script runs this check once per setting: a route that
        // emits bad bytecode must fail in the configuration that installs it, not only in the one that does not.
        // The integer and short spellings are listed with the float ones: they share one emitter, and the emitter's
        // arity is what decides the descriptor - a spelling left out here would be exactly the route whose bytecode
        // no run ever verifies.
        "glBegin", "glEnd", "glTexCoord2f", "glTexCoord2d",
        "glVertex2f", "glVertex2d", "glVertex3f", "glVertex3d", "glVertex4f", "glVertex4d",
        "glVertex2i", "glVertex3i", "glVertex4i", "glVertex2s", "glVertex3s",
        "glTexCoord2i", "glTexCoord2s",
        // The rectangle spellings and the multi-texture one. glRect* is a whole quad per call rather than a vertex
        // spelling, so its route is a composite one; glMultiTexCoord2f routes only when the capture is armed - its
        // default answer stays the bare return the light map's own path made it. Each is verified in the configuration
        // that installs it, which is why the script runs this list twice.
        "glRectf", "glRectd", "glRecti", "glRects", "glMultiTexCoord2f",
        // The rest of the texcoord component counts and widths, and their buffer forms. Like the vertex spellings
        // above, these are routed only with -Dceltium.gless.legacyImmediate=true; listing every one means the emitter's
        // descriptor decoding is exercised for each spelling in the configuration that installs it, and the texcoord
        // phase below reads the emitted hint call back out of the transformed probe.
        "glTexCoord1f", "glTexCoord1d", "glTexCoord1i", "glTexCoord1s",
        "glTexCoord3f", "glTexCoord3d", "glTexCoord3i", "glTexCoord3s",
        "glTexCoord4f", "glTexCoord4d", "glTexCoord4i", "glTexCoord4s",
        "nglTexCoord1fv", "nglTexCoord1dv", "nglTexCoord1iv", "nglTexCoord1sv",
        "nglTexCoord2fv", "nglTexCoord2dv", "nglTexCoord2iv", "nglTexCoord2sv",
        "nglTexCoord3fv", "nglTexCoord3dv", "nglTexCoord3iv", "nglTexCoord3sv",
        "nglTexCoord4fv", "nglTexCoord4dv", "nglTexCoord4iv", "nglTexCoord4sv",
        "glMultiTexCoord1f", "glMultiTexCoord1d", "glMultiTexCoord1i", "glMultiTexCoord1s",
        "glMultiTexCoord2d", "glMultiTexCoord2i", "glMultiTexCoord2s",
        "glMultiTexCoord3f", "glMultiTexCoord3d", "glMultiTexCoord3i", "glMultiTexCoord3s",
        "glMultiTexCoord4f", "glMultiTexCoord4d", "glMultiTexCoord4i", "glMultiTexCoord4s",
        "nglMultiTexCoord1fv", "nglMultiTexCoord1dv", "nglMultiTexCoord1iv", "nglMultiTexCoord1sv",
        "nglMultiTexCoord2fv", "nglMultiTexCoord2dv", "nglMultiTexCoord2iv", "nglMultiTexCoord2sv",
        "nglMultiTexCoord3fv", "nglMultiTexCoord3dv", "nglMultiTexCoord3iv", "nglMultiTexCoord3sv",
        "nglMultiTexCoord4fv", "nglMultiTexCoord4dv", "nglMultiTexCoord4iv", "nglMultiTexCoord4sv",
        "glBindFramebufferEXT", "glBindFramebufferARB", "glFramebufferTexture2DEXT", "glFramebufferTexture2DARB",
        "glCheckFramebufferStatusEXT", "glCheckFramebufferStatusARB",
        "nglDeleteTextures", "nglDeleteBuffers", "nglDeleteFramebuffers", "nglGenTextures", "nglGenBuffers",
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
                    .filter(n -> n.startsWith("org/lwjgl/opengl/") && n.endsWith(".class"))
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
        // Phase 2: every real GL class in the jar is transformed and loaded, so a route that emits invalid bytecode for a
        // descriptor the probe does not declare still fails here - per class, at load time. The GLDebugMessage callback
        // classes are skipped: they return their own nested class from a factory method, and defining the outer in a child
        // loader while the nested class resolves from the parent makes the verifier's stack map disagree (an artifact of
        // this check, not of the transform; the same class loads unchanged in its normal loader).
        int classFailures = 0, classes = 0;
        try (JarFile jar = new JarFile(args[0])) {
            for (String entry : Collections.list(jar.entries()).stream().map(e -> e.getName())
                    .filter(n -> n.startsWith("org/lwjgl/opengl/GL") && n.endsWith(".class") && !n.contains("$")
                            && !n.contains("GLDebugMessage") && !n.equals("org/lwjgl/opengl/GL.class"))
                    .sorted().collect(java.util.stream.Collectors.toList())) {
                String cn = entry.substring(0, entry.length() - ".class".length()).replace('/', '.');
                byte[] original;
                try (InputStream stream = jar.getInputStream(jar.getJarEntry(entry))) {
                    original = stream.readAllBytes();
                }
                byte[] transformed;
                try {
                    transformed = new CeltiumGllessNativeTransformer().transform(cn, cn, original);
                } catch (RuntimeException | LinkageError error) {
                    System.out.println("TRANSFORM-FAIL " + cn + " -> " + error);
                    classFailures++;
                    continue;
                }
                classes++;
                try {
                    Class<?> defined = new ClassLoader(TransformerRouteVerifierCheck.class.getClassLoader()) {
                        Class<?> define() { return defineClass(cn, transformed, 0, transformed.length); }
                    }.define();
                    defined.getDeclaredMethods();
                } catch (VerifyError | ClassFormatError error) {
                    System.out.println("CLASS-FAIL " + cn);
                    error.printStackTrace(System.out);
                    classFailures++;
                }
            }
        }
        System.out.println("classes checked=" + classes + " failed=" + classFailures);
        // Phase 3: every colour spelling answers. A colour native left on the generic no-op keeps the previous colour
        // in the mirror, and the immediate pipelines read that mirror for every layout with no per-vertex colour - so
        // one unrouted spelling is a wrong picture for whoever calls it, not a missing feature. Load success cannot see
        // this: a route that was never emitted still verifies. So the transformed GL11 body is read for the hint call.
        int colourMissing = 0;
        int colourRouted = 0;
        int colourResolved = 0;
        try (JarFile jar = new JarFile(args[0])) {
            byte[] original;
            try (InputStream stream = jar.getInputStream(jar.getJarEntry("org/lwjgl/opengl/GL11.class"))) {
                original = stream.readAllBytes();
            }
            byte[] transformed = new CeltiumGllessNativeTransformer().transform("org/lwjgl/opengl/GL11",
                    "org.lwjgl.opengl.GL11", original);
            final Set<String> wanted = new HashSet<>(Arrays.asList(COLOUR_NATIVES));
            final Map<String, Boolean> routed = new HashMap<>();
            final Set<String> hintCalls = new LinkedHashSet<>();
            new ClassReader(transformed).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                        String signature, String[] exceptions) {
                    if (!wanted.contains(name)) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String hintName,
                                String hintDescriptor, boolean isInterface) {
                            // A colour entry point, not any call on the hint: the generic no-op also calls the hint
                            // (noteUnrouted), so counting every invokestatic would report a dropped spelling as routed
                            // - the positive control that removed one route was answered with 32/32 before this.
                            if (opcode == Opcodes.INVOKESTATIC
                                    && "org/taumc/celeritas/core/CeltiumGllessHint".equals(owner)
                                    && hintName.startsWith("glColor")) {
                                routed.put(name, Boolean.TRUE);
                                hintCalls.add(hintName + hintDescriptor);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG);
            for (String name : COLOUR_NATIVES) {
                if (routed.containsKey(name)) {
                    colourRouted++;
                } else {
                    System.out.println("COLOUR-UNROUTED " + name + " (the previous colour stays in the mirror)");
                    colourMissing++;
                }
            }
            // And each emitted call names a method the hint actually declares. Resolved through the class that owns
            // the route, not by executing it: the hint's static initialisers need the Vulkan/LWJGL natives this check
            // deliberately does not load, so initialization is asked for as false and only the method lookup runs.
            // This is the failure mode that compiles fine and first appears in whichever mod calls that spelling.
            Class<?> hintClass = Class.forName("org.taumc.celeritas.core.CeltiumGllessHint", false,
                    TransformerRouteVerifierCheck.class.getClassLoader());
            for (String call : hintCalls) {
                int paren = call.indexOf('(');
                String methodName = call.substring(0, paren);
                String descriptor = call.substring(paren);
                Class<?> returnType = descriptorType(descriptor.substring(descriptor.indexOf(')') + 1));
                String arguments = descriptor.substring(1, descriptor.indexOf(')'));
                List<Class<?>> parameters = new ArrayList<>();
                for (int at = 0; at < arguments.length(); at++) {
                    char code = arguments.charAt(at);
                    if (code == '[') {
                        throw new IllegalStateException("the colour routes take no arrays: " + call);
                    }
                    parameters.add(descriptorType(String.valueOf(code)));
                }
                if (returnType != void.class) {
                    throw new IllegalStateException("a colour route must return void: " + call);
                }
                try {
                    hintClass.getDeclaredMethod(methodName, parameters.toArray(new Class<?>[0]));
                    colourResolved++;
                } catch (NoSuchMethodException missing) {
                    System.out.println("COLOUR-MISSING " + call + " is not declared by CeltiumGllessHint");
                    colourMissing++;
                }
            }
        }
        System.out.println("colour natives routed=" + colourRouted + "/" + COLOUR_NATIVES.length
                + " resolved=" + colourResolved);
        // Phase 4: the texcoord family, in the configuration that installs its routes. A spelling that falls through to
        // the generic no-op keeps the previous UV, which is a wrong picture rather than a missing call, and load success
        // cannot see it. The probe is scanned for the legacy entry points only - noteUnrouted is also a hint call - so the
        // positive control (one route deleted) reports the spelling by name instead of still reading full coverage.
        int texcoordMissing = 0;
        // The product default is on (2026-11-02), so the check runs unless a run explicitly turns it off; the script
        // still also runs the -D...=true configuration, which then reports the same coverage.
        if (Boolean.parseBoolean(System.getProperty("celtium.gless.legacyImmediate", "true"))) {
            final Set<String> wantedCoords = new HashSet<>(Arrays.asList(TEXCOORD_NATIVES));
            final Map<String, Boolean> routedCoords = new HashMap<>();
            new ClassReader(rewritten).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String methodName, String methodDescriptor,
                        String signature, String[] exceptions) {
                    if (!wantedCoords.contains(methodName)) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String hintName,
                                String hintDescriptor, boolean isInterface) {
                            if (opcode == Opcodes.INVOKESTATIC
                                    && "org/taumc/celeritas/core/CeltiumGllessHint".equals(owner)
                                    && (hintName.startsWith("glLegacyTexCoord")
                                            || hintName.startsWith("glLegacyMultiTexCoord"))) {
                                routedCoords.put(methodName, Boolean.TRUE);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG);
            int declared = 0;
            for (String name : TEXCOORD_NATIVES) {
                if (!found.containsKey(name)) {
                    continue;
                }
                declared++;
                if (!routedCoords.containsKey(name)) {
                    System.out.println("TEXCOORD-UNROUTED " + name + " (the previous UV stays current)");
                    texcoordMissing++;
                }
            }
            System.out.println("texcoord natives routed=" + (declared - texcoordMissing) + "/" + declared);
        } else {
            System.out.println("texcoord natives routed=skipped (legacy capture off)");
        }
        // Phase 5: the query natives must name their dedicated hint entry, not fall through to the zero-writer.
        // Load success cannot see this: the zero-writer's body is valid bytecode and answers "one zero component",
        // which is why two routes lived dead for weeks - their descriptor test compared the descriptor against a
        // string without the return type, so "(IJ)V" never equalled "(IJ)". The display-list leg r30b measured the
        // consequences (glGetBooleanv answered 0000 where glGetFloatv answered 1111; glGetTexParameteriv answered 0),
        // and only a check of the emitted call can catch the next one. The real classes are transformed here, one
        // per native, because the same name can live in GL11 or in a version class.
        int queryMissing = 0;
        Map<String, String> queryOwners = new HashMap<>();
        try (JarFile jar = new JarFile(args[0])) {
            for (String entry : Collections.list(jar.entries()).stream().map(JarEntry::getName)
                    .filter(n -> n.startsWith("org/lwjgl/opengl/") && n.endsWith(".class") && !n.contains("$"))
                    .sorted().collect(java.util.stream.Collectors.toList())) {
                String className = entry.substring(0, entry.length() - ".class".length()).replace('/', '.');
                byte[] original;
                try (InputStream stream = jar.getInputStream(jar.getJarEntry(entry))) {
                    original = stream.readAllBytes();
                }
                new ClassReader(original).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access, String methodName, String descriptor,
                            String signature, String[] exceptions) {
                        if ((access & Opcodes.ACC_NATIVE) != 0) {
                            for (String[] route : QUERY_ROUTES) {
                                if (route[0].equals(methodName) && route[2].equals(descriptor)) {
                                    queryOwners.putIfAbsent(methodName, className);
                                }
                            }
                        }
                        return null;
                    }
                }, ClassReader.SKIP_CODE);
            }
        }
        for (String[] route : QUERY_ROUTES) {
            String owner = queryOwners.get(route[0]);
            if (owner == null) {
                System.out.println("QUERY-ABSENT " + route[0] + route[2] + " is not declared native anywhere in the "
                        + "jar; the check cannot see its route");
                queryMissing++;
                continue;
            }
            byte[] original;
            try (JarFile jar = new JarFile(args[0])) {
                try (InputStream stream = jar.getInputStream(jar.getJarEntry(owner.replace('.', '/') + ".class"))) {
                    original = stream.readAllBytes();
                }
            }
            byte[] transformed = new CeltiumGllessNativeTransformer().transform(owner, owner, original);
            Set<String> calls = new LinkedHashSet<>();
            new ClassReader(transformed).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String methodName, String descriptor,
                        String signature, String[] exceptions) {
                    if (!route[0].equals(methodName) || !route[2].equals(descriptor)) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String callOwner, String hintName,
                                String hintDescriptor, boolean isInterface) {
                            if (opcode == Opcodes.INVOKESTATIC
                                    && "org/taumc/celeritas/core/CeltiumGllessHint".equals(callOwner)) {
                                calls.add(hintName + hintDescriptor);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG);
            String expected = route[1] + route[2];
            if (!calls.contains(expected)) {
                System.out.println("QUERY-UNROUTED " + route[0] + route[2] + " in " + owner + " emits " + calls
                        + ", not " + expected + " (the zero-writer answers one zero component)");
                queryMissing++;
            } else {
                System.out.println("QUERY-ROUTE " + route[0] + route[2] + " -> " + expected + " in " + owner);
            }
        }
        System.out.println("query natives routed=" + (QUERY_ROUTES.length - queryMissing) + "/" + QUERY_ROUTES.length);

        // Phase 6: the routes actually answer. A transformed body can name the right entry and still be handed the
        // wrong argument slot, and the stub can answer from the wrong record - neither shows in a bytecode read. The
        // probe class is the same transformation of the same jar declarations the run gets, so this is the in-run
        // route, executed: the stub is installed, the state is written through the stub's own dispatch entry, and the
        // query is asked through the transformed native. The float width is the positive control: it was never dead,
        // so a harness that fails it is failing its own marshalling rather than the route.
        int queryAnswerFailures = 0;
        try {
            Class<?> stubClass = Class.forName("org.soutalab.celtium.vulkan.gl.CeltiumGlStub");
            Class<?> rasterClass = Class.forName("org.soutalab.celtium.vulkan.NativeRasterState");
            Class<?> bindingsClass = Class.forName("org.soutalab.celtium.vulkan.NativeTextureBindings");
            Object raster = rasterClass.getMethod("current").invoke(null);
            Object bindings = bindingsClass.getMethod("current").invoke(null);
            var constructor = stubClass.getDeclaredConstructor(rasterClass, bindingsClass);
            constructor.setAccessible(true);
            Object stub = constructor.newInstance(raster, bindings);
            var installedField = stubClass.getDeclaredField("installed");
            installedField.setAccessible(true);
            installedField.set(null, stub);
            try {
                var dispatch = stubClass.getDeclaredMethod("dispatchArgs", int.class, long[].class);
                dispatch.setAccessible(true);
                var idsField = stubClass.getDeclaredField("IDS");
                idsField.setAccessible(true);
                Map<?, ?> ids = (Map<?, ?>) idsField.get(null);
                dispatch.invoke(stub, ids.get("glColorMask"), new long[] {1, 0, 1, 0});
                dispatch.invoke(stub, ids.get("glBindTexture"), new long[] {0x0DE1, 7});
                dispatch.invoke(stub, ids.get("glTexParameteri"), new long[] {0x0DE1, 0x2801, 0x2601});
                var buffer = org.lwjgl.system.MemoryUtil.memAlloc(64);
                try {
                    long address = org.lwjgl.system.MemoryUtil.memAddress(buffer);
                    java.lang.reflect.Method booleanv = api.getDeclaredMethod("nglGetBooleanv", int.class, long.class);
                    java.lang.reflect.Method floatv = api.getDeclaredMethod("nglGetFloatv", int.class, long.class);
                    java.lang.reflect.Method texParameteriv = api.getDeclaredMethod("nglGetTexParameteriv", int.class,
                            int.class, long.class);
                    for (int i = 0; i < 16; i++) org.lwjgl.system.MemoryUtil.memPutByte(address + i, (byte) 0x7F);
                    booleanv.invoke(null, 0x0C23, address);
                    StringBuilder mask = new StringBuilder();
                    for (int i = 0; i < 4; i++) mask.append((org.lwjgl.system.MemoryUtil.memGetByte(address + i) & 0xFF)
                            != 0 ? '1' : '0');
                    for (int i = 0; i < 16; i++) org.lwjgl.system.MemoryUtil.memPutFloat(address + i * 4L, -12345.0f);
                    floatv.invoke(null, 0x0C23, address);
                    StringBuilder maskFloat = new StringBuilder();
                    for (int i = 0; i < 4; i++) maskFloat.append(org.lwjgl.system.MemoryUtil.memGetFloat(address + i * 4L)
                            != 0.0f ? '1' : '0');
                    for (int i = 0; i < 16; i++) org.lwjgl.system.MemoryUtil.memPutInt(address + i * 4L, -12345);
                    texParameteriv.invoke(null, 0x0DE1, 0x2801, address);
                    int filter = org.lwjgl.system.MemoryUtil.memGetInt(address);
                    System.out.println("query answers boolean=" + mask + " float=" + maskFloat + " texFilter=0x"
                            + Integer.toHexString(filter));
                    if (!"1010".equals(mask.toString())) {
                        System.out.println("QUERY-ANSWER glGetBooleanv(GL_COLOR_WRITEMASK) answered " + mask
                                + ", not 1010; the byte the route wrote is not the recorded mask");
                        queryAnswerFailures++;
                    }
                    if (!"1010".equals(maskFloat.toString())) {
                        System.out.println("QUERY-ANSWER glGetFloatv(GL_COLOR_WRITEMASK) answered " + maskFloat
                                + ", not 1010; the harness itself is wrong (this width was never dead)");
                        queryAnswerFailures++;
                    }
                    if (filter != 0x2601) {
                        System.out.println("QUERY-ANSWER glGetTexParameteriv(GL_TEXTURE_MIN_FILTER) answered 0x"
                                + Integer.toHexString(filter) + ", not 0x2601 (the GL_LINEAR the stub recorded)");
                        queryAnswerFailures++;
                    }
                } finally {
                    org.lwjgl.system.MemoryUtil.memFree(buffer);
                }
            } finally {
                installedField.set(null, null);
            }
        } catch (ReflectiveOperationException | LinkageError error) {
            System.out.println("QUERY-ANSWER the stub could not be driven: " + error);
            queryAnswerFailures++;
        }
        if (queryAnswerFailures > 0) {
            System.out.println("query answers failed=" + queryAnswerFailures);
        }
        // Positive control for the live drawing census. The line reads "none" on a run whose unrouted calls are all
        // state, and that is also what a census that cannot fire looks like - so one drawing key is registered here and
        // the line is read back. Without this, "none" in a leg is not evidence of anything.
        int drawingCensusFailures = 0;
        try {
            org.taumc.celeritas.core.CeltiumGllessNativeTransformer
                    .celtium$noteUnroutedForCheck("glDrawElements(IIIJ)V");
            java.io.PrintStream original = System.out;
            java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
            try {
                System.setOut(new java.io.PrintStream(captured, true, "UTF-8"));
                org.taumc.celeritas.core.CeltiumGllessNativeTransformer.printSurfaceReport("positive-control");
            } finally {
                System.setOut(original);
            }
            String text = captured.toString("UTF-8");
            String expected = "CELTIUM_GL_SURFACE_DRAWING trigger=positive-control glDrawElements(IIIJ)V=1";
            if (!text.contains(expected)) {
                System.out.println("DRAWING-CENSUS-FAIL expected [" + expected + "] in [" + text.trim() + "]");
                drawingCensusFailures++;
            } else {
                System.out.println("DRAWING-CENSUS positive control named the drawing key");
            }
        } catch (Throwable error) {
            System.out.println("DRAWING-CENSUS-FAIL " + error);
            drawingCensusFailures++;
        }
        if (failed != 0 || classFailures != 0 || colourMissing != 0 || texcoordMissing != 0 || queryMissing != 0
                || queryAnswerFailures != 0 || drawingCensusFailures != 0) {
            System.exit(1);
        }
    }

    /** One JVM descriptor character as the class it names, for the emitted-call lookup. */
    static Class<?> descriptorType(String code) {
        switch (code) {
            case "V": return void.class;
            case "Z": return boolean.class;
            case "B": return byte.class;
            case "S": return short.class;
            case "C": return char.class;
            case "I": return int.class;
            case "J": return long.class;
            case "F": return float.class;
            case "D": return double.class;
            default: throw new IllegalStateException("unexpected descriptor character " + code);
        }
    }

    /** The colour natives GL11 declares, in javap order: 8 widths x 3/4 components, plus the 16 buffer spellings. */
    static final String[] COLOUR_NATIVES = {
        "glColor3b", "glColor3s", "glColor3i", "glColor3f", "glColor3d", "glColor3ub", "glColor3us", "glColor3ui",
        "nglColor3bv", "nglColor3sv", "nglColor3iv", "nglColor3fv", "nglColor3dv", "nglColor3ubv", "nglColor3usv",
        "nglColor3uiv",
        "glColor4b", "glColor4s", "glColor4i", "glColor4f", "glColor4d", "glColor4ub", "glColor4us", "glColor4ui",
        "nglColor4bv", "nglColor4sv", "nglColor4iv", "nglColor4fv", "nglColor4dv", "nglColor4ubv", "nglColor4usv",
        "nglColor4uiv",
    };

    /**
     * The query natives that must name a dedicated entry point: {native, hint entry, descriptor}.
     *
     * <p>These are the calls a mod's save/restore code makes, and the fallback for each is the zero-writer - a single
     * zero component, which reads as a real answer ("the mask is off", "the filter is 0"). The list is the phase-5
     * denominator; it deliberately includes the widths that were never dead, so the check's own positive control is in
     * it.
     */
    static final String[][] QUERY_ROUTES = {
        {"nglGetBooleanv", "glGetBooleanv", "(IJ)V"},
        {"nglGetFloatv", "glGetFloatv", "(IJ)V"},
        {"nglGetDoublev", "glGetDoublev", "(IJ)V"},
        {"nglGetIntegerv", "glGetIntegerv", "(IJ)V"},
        {"nglGetTexParameteriv", "glGetTexParameteriv", "(IIJ)V"},
        {"nglGetTexParameterfv", "glGetTexParameterfv", "(IIJ)V"},
        {"nglGetTexParameterIiv", "glGetTexParameterIiv", "(IIJ)V"},
        {"nglGetTexParameterIuiv", "glGetTexParameterIuiv", "(IIJ)V"},
    };

    /**
     * Every texcoord native the legacy capture answers: 1/2/3/4 components x f/d/i/s (GL11) and the same counts for
     * the multi-texture family (GL13), plus each one's buffer form. This is the phase-4 denominator; the list is kept
     * beside {@link #NAMES} because that array is also the probe's declaration list, and a spelling missing here would
     * still be transformed by phase 2 but never read back for its route.
     */
    static final String[] TEXCOORD_NATIVES = {
        "glTexCoord1f", "glTexCoord1d", "glTexCoord1i", "glTexCoord1s",
        "glTexCoord2f", "glTexCoord2d", "glTexCoord2i", "glTexCoord2s",
        "glTexCoord3f", "glTexCoord3d", "glTexCoord3i", "glTexCoord3s",
        "glTexCoord4f", "glTexCoord4d", "glTexCoord4i", "glTexCoord4s",
        "nglTexCoord1fv", "nglTexCoord1dv", "nglTexCoord1iv", "nglTexCoord1sv",
        "nglTexCoord2fv", "nglTexCoord2dv", "nglTexCoord2iv", "nglTexCoord2sv",
        "nglTexCoord3fv", "nglTexCoord3dv", "nglTexCoord3iv", "nglTexCoord3sv",
        "nglTexCoord4fv", "nglTexCoord4dv", "nglTexCoord4iv", "nglTexCoord4sv",
        "glMultiTexCoord1f", "glMultiTexCoord1d", "glMultiTexCoord1i", "glMultiTexCoord1s",
        "glMultiTexCoord2f", "glMultiTexCoord2d", "glMultiTexCoord2i", "glMultiTexCoord2s",
        "glMultiTexCoord3f", "glMultiTexCoord3d", "glMultiTexCoord3i", "glMultiTexCoord3s",
        "glMultiTexCoord4f", "glMultiTexCoord4d", "glMultiTexCoord4i", "glMultiTexCoord4s",
        "nglMultiTexCoord1fv", "nglMultiTexCoord1dv", "nglMultiTexCoord1iv", "nglMultiTexCoord1sv",
        "nglMultiTexCoord2fv", "nglMultiTexCoord2dv", "nglMultiTexCoord2iv", "nglMultiTexCoord2sv",
        "nglMultiTexCoord3fv", "nglMultiTexCoord3dv", "nglMultiTexCoord3iv", "nglMultiTexCoord3sv",
        "nglMultiTexCoord4fv", "nglMultiTexCoord4dv", "nglMultiTexCoord4iv", "nglMultiTexCoord4sv",
    };

    static Class<?> defineWith(ClassLoader loader, String name, byte[] bytes) {
        try {
            java.lang.reflect.Method m = loader.getClass().getDeclaredMethod("define", String.class, byte[].class);
            m.setAccessible(true);
            return (Class<?>) m.invoke(loader, name, bytes);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    static int countArgs(String descriptor) {
        int n = 0;
        for (int i = 1; i < descriptor.length(); i++) {
            char c = descriptor.charAt(i);
            if (c == ')') break;
            if (c == '[') { while (descriptor.charAt(i) == '[') i++; }
            if (c == 'L') { while (descriptor.charAt(i) != ';') i++; }
            n++;
        }
        return n;
    }
}
