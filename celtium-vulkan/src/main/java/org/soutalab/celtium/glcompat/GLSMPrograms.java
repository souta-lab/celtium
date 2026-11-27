package org.soutalab.celtium.glcompat;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.ffp.Instancing;
import org.soutalab.celtium.glcompat.stacks.BooleanStateStack;
import org.soutalab.celtium.glcompat.stacks.IntegerStateStack;
import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.soutalab.celtium.glcompat.DisplayListManager.RecordMode;
import org.soutalab.celtium.glcompat.ffp.ShaderManager;
import org.soutalab.celtium.glcompat.hooks.GLSMHooks;
import org.soutalab.celtium.glcompat.hooks.GpuCommandType;
import org.soutalab.celtium.glcompat.shader.ProgramBinaryCache;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.KHRDebug;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

import static org.soutalab.celtium.glcompat.GLStateManager.BYPASS_CACHE;
import static org.soutalab.celtium.glcompat.GLStateManager.TEXTURE;
import static org.soutalab.celtium.glcompat.GLStateManager.TEXTURE_RENAMED;
import static org.soutalab.celtium.glcompat.GLStateManager.ctx;
import static org.soutalab.celtium.glcompat.GLStateManager.rejectExitCapture;
import static org.soutalab.celtium.glcompat.GLStateManager.generateVertexShaderIfNeeded;
import static org.soutalab.celtium.glcompat.GLStateManager.initConfig;
import static org.soutalab.celtium.glcompat.GLStateManager.isCachingEnabled;
import static org.soutalab.celtium.glcompat.GLStateManager.isFragmentShader;
import static org.soutalab.celtium.glcompat.GLStateManager.isTextureBuffer;
import static org.soutalab.celtium.glcompat.GLStateManager.programLifetimeGeneration;
import static org.soutalab.celtium.glcompat.GLStateManager.recordGpuCommand;
import static org.soutalab.celtium.glcompat.backend.DirectGlExecutor.EXECUTOR;
import static org.soutalab.celtium.glcompat.GLStateManager.decodeShaderSourceString;
import static org.soutalab.celtium.glcompat.GLStateManager.glGetInteger;

public final class GLSMPrograms {

    private GLSMPrograms() {}

    /** Selects the instancing layout that the next FFP program selection compiles for. */
    public static void setFfpInstancing(Instancing mode) {
        ctx().ffpInstancing = mode;
    }

    public static void glUseProgram(int program) {
        rejectExitCapture();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordUseProgram(program);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }

        final ShaderManager ffp = ShaderManager.getInstance();
        if (program == 0 && ffp.isEnabled()) {
            final int prev = ctx().activeProgram;
            final boolean caching = isCachingEnabled();
            if (caching && prev == 0 && ffp.isActive()) {
                return;
            }
            if (caching) {
                if (prev != 0) ctx().programGeneration++;
                ctx().activeProgram = 0; // Track that FFP was requested
            }
            recordGpuCommand(GpuCommandType.PROGRAM_USE, program, 0);
            ffp.activate();
            return;
        }

        // Non-zero program or FFP emulation not enabled
        if (ffp.isActive()) {
            ffp.deactivate();
        }

        final boolean caching = isCachingEnabled();
        if (BYPASS_CACHE || !caching || program != ctx().activeProgram) {
            final int prev = ctx().activeProgram;
            if (caching) {
                ctx().programGeneration++;
                ctx().activeProgram = program;
            }
            if (initConfig != null && initConfig.isLwjglDebug()) {
                final String programName = GLDebug.getObjectLabel(KHRDebug.GL_PROGRAM, program);
                GLDebug.debugMessage("Activating Program - " + program + ":" + programName);
            }
            recordGpuCommand(GpuCommandType.PROGRAM_USE, program, 0);
            EXECUTOR.useProgram(program);
            CompatUniformManager.onUseProgram(program);
        }
    }

    public static void glShaderSource(int shader, CharSequence source) {
        rejectExitCapture();
        String src = source.toString();
        // Always rename reserved words for the target GLSL version (e.g. 'sampler' at 460)
        src = GlslTransformUtils.renameReservedWords(src, EXECUTOR.getMinGLSLVersion());
        if (ShaderManager.getInstance().isEnabled()) {
            src = CompatShaderTransformer.transform(src, isFragmentShader(shader));
        }
        EXECUTOR.shaderSource(shader, src);
    }

    public static void glShaderSource(int shader, ByteBuffer source) {
        final byte[] bytes = new byte[source.remaining()];
        final int pos = source.position();
        source.get(bytes);
        source.position(pos);
        glShaderSource(shader, new String(bytes, StandardCharsets.UTF_8));
    }

    public static void glShaderSource(int shader, CharSequence[] sources) {
        int totalLen = 0;
        for (CharSequence s : sources) totalLen += s.length();
        final StringBuilder sb = new StringBuilder(totalLen);
        for (CharSequence s : sources) sb.append(s);
        glShaderSource(shader, sb.toString());
    }

    public static void glLinkProgram(int program) {
        rejectExitCapture();
        if (ShaderManager.getInstance().isEnabled()) {
            generateVertexShaderIfNeeded(program);
        }
        if (ProgramBinaryCache.isEnabled()) {
            ProgramBinaryCache.markRetrievable(program);
        }
        long linkEpoch = CompatUniformManager.beforeLinkProgram(program);
        EXECUTOR.linkProgram(program);
        CompatUniformManager.onLinkProgram(program, linkEpoch);
    }

    public static void glDeleteProgram(int program) {
        rejectExitCapture();
        if (program == 0) return;
        CompatUniformManager.onDeleteProgram(program);

        // GL defers deletion of a bound program until unbind; the program cache entry stays set so the
        // cache matches GL_CURRENT_PROGRAM and the next glUseProgram is not skipped as redundant.
        EXECUTOR.deleteProgram(program);
        programLifetimeGeneration++;
    }

    public static int glCreateShader(int type) {
        return EXECUTOR.createShader(type);
    }

    public static void glCompileShader(int shader) {
        rejectExitCapture();
        EXECUTOR.compileShader(shader);
    }

    public static int glCreateProgram() {
        final int program = EXECUTOR.createProgram();
        programLifetimeGeneration++;
        return program;
    }

    public static void glAttachShader(int program, int shader) {
        rejectExitCapture();
        EXECUTOR.attachShader(program, shader);
    }

    public static void glDeleteShader(int shader) {
        rejectExitCapture();
        EXECUTOR.deleteShader(shader);
    }

    public static void glBindAttribLocation(int program, int index, CharSequence name) {
        rejectExitCapture();
        EXECUTOR.bindAttribLocation(program, index, name);
    }

    public static int glGetShaderi(int shader, int pname) {
        rejectExitCapture();
        return EXECUTOR.getShaderi(shader, pname);
    }

    public static String glGetShaderInfoLog(int shader, int maxLength) {
        rejectExitCapture();
        return EXECUTOR.getShaderInfoLog(shader, maxLength);
    }

    public static String glGetShaderInfoLog(int shader) {
        rejectExitCapture();
        return EXECUTOR.getShaderInfoLog(shader);
    }

    public static void glGetShaderInfoLog(int shader, IntBuffer length, ByteBuffer infoLog) {
        rejectExitCapture();
        EXECUTOR.getShaderInfoLog(shader, length, infoLog);
    }

    public static void glGetShaderInfoLog(int shader, int[] length, ByteBuffer infoLog) {
        rejectExitCapture();
        EXECUTOR.getShaderInfoLog(shader, length, infoLog);
    }

    public static int glGetProgrami(int program, int pname) {
        rejectExitCapture();
        return EXECUTOR.getProgrami(program, pname);
    }

    public static String glGetProgramInfoLog(int program, int maxLength) {
        rejectExitCapture();
        return EXECUTOR.getProgramInfoLog(program, maxLength);
    }

    public static String glGetProgramInfoLog(int program) {
        rejectExitCapture();
        return EXECUTOR.getProgramInfoLog(program);
    }

    public static void glGetProgramInfoLog(int program, IntBuffer length, ByteBuffer infoLog) {
        rejectExitCapture();
        EXECUTOR.getProgramInfoLog(program, length, infoLog);
    }

    public static void glGetProgramInfoLog(int program, int[] length, ByteBuffer infoLog) {
        rejectExitCapture();
        EXECUTOR.getProgramInfoLog(program, length, infoLog);
    }

    public static int glGetUniformLocation(int program, CharSequence name) {
        rejectExitCapture();
        int loc = EXECUTOR.getUniformLocation(program, name);
        if (loc == -1 && TEXTURE.contentEquals(name)) {
            loc = EXECUTOR.getUniformLocation(program, TEXTURE_RENAMED);
        }
        if (loc == -1) {
            // The program may carry the renamed form when the shader was emitted at a GLSL version
            // that reserves this identifier.
            final String renamed = GlslTransformUtils.renameReservedWords(name.toString(), EXECUTOR.getMinGLSLVersion());
            if (!renamed.contentEquals(name)) loc = EXECUTOR.getUniformLocation(program, renamed);
        }
        return loc;
    }

    public static int glGetUniformLocation(int program, ByteBuffer name) {
        rejectExitCapture();
        int loc = EXECUTOR.getUniformLocation(program, name);
        if (loc == -1 && isTextureBuffer(name)) {
            loc = EXECUTOR.getUniformLocation(program, TEXTURE_RENAMED);
        }
        if (loc == -1) {
            final String text = MemoryUtil.memUTF8(name);
            final String renamed = GlslTransformUtils.renameReservedWords(text, EXECUTOR.getMinGLSLVersion());
            if (!renamed.equals(text)) loc = EXECUTOR.getUniformLocation(program, renamed);
        }
        return loc;
    }

    public static int glGetAttribLocation(int program, CharSequence name) {
        rejectExitCapture();
        int loc = EXECUTOR.getAttribLocation(program, name);
        if (loc == -1 && TEXTURE.contentEquals(name)) {
            loc = EXECUTOR.getAttribLocation(program, TEXTURE_RENAMED);
        }
        return loc;
    }

    public static int glGetAttribLocation(int program, ByteBuffer name) {
        rejectExitCapture();
        int loc = EXECUTOR.getAttribLocation(program, name);
        if (loc == -1 && isTextureBuffer(name)) {
            loc = EXECUTOR.getAttribLocation(program, TEXTURE_RENAMED);
        }
        return loc;
    }

    public static void glUniform1f(int location, float v0) {
        rejectExitCapture();
        EXECUTOR.uniform1f(location, v0);
    }

    public static void glUniform2f(int location, float v0, float v1) {
        rejectExitCapture();
        EXECUTOR.uniform2f(location, v0, v1);
    }

    public static void glUniform3f(int location, float v0, float v1, float v2) {
        rejectExitCapture();
        EXECUTOR.uniform3f(location, v0, v1, v2);
    }

    public static void glUniform4f(int location, float v0, float v1, float v2, float v3) {
        rejectExitCapture();
        EXECUTOR.uniform4f(location, v0, v1, v2, v3);
    }

    public static void glUniform1i(int location, int v0) {
        rejectExitCapture();
        EXECUTOR.uniform1i(location, v0);
    }

    public static void glUniform2i(int location, int v0, int v1) {
        rejectExitCapture();
        EXECUTOR.uniform2i(location, v0, v1);
    }

    public static void glUniform3i(int location, int v0, int v1, int v2) {
        rejectExitCapture();
        EXECUTOR.uniform3i(location, v0, v1, v2);
    }

    public static void glUniformMatrix3(int location, boolean transpose, FloatBuffer matrices) {
        rejectExitCapture();
        EXECUTOR.uniformMatrix3(location, transpose, matrices);
    }

    public static void glUniformMatrix4(int location, boolean transpose, FloatBuffer matrices) {
        rejectExitCapture();
        EXECUTOR.uniformMatrix4(location, transpose, matrices);
    }

    public static void glUniformMatrix3(int location, boolean transpose, float[] matrices) {
        EXECUTOR.uniformMatrix3(location, transpose, matrices);
    }

    public static void glUniformMatrix4(int location, boolean transpose, float[] matrices) {
        EXECUTOR.uniformMatrix4(location, transpose, matrices);
    }

    public static void glGetActiveUniform(int program, int index, IntBuffer length, IntBuffer size, IntBuffer type, ByteBuffer name) {
        rejectExitCapture();
        EXECUTOR.getActiveUniform(program, index, length, size, type, name);
    }

    public static void glGetActiveUniform(int program, int index, int[] length, int[] size, int[] type, ByteBuffer name) {
        EXECUTOR.getActiveUniform(program, index, length, size, type, name);
    }

    public static String glGetActiveUniform(int program, int index, int maxLength) {
        // Legacy convenience form returning the active uniform name. Third-party mods
        // (e.g. HammerLib) call GL20.glGetActiveUniform(program, index, maxLength) ->
        // String, which the GL redirector rewrites to this class, so the same signature
        // must exist here or the call fails with NoSuchMethodError during mod init.
        IntBuffer sizeType = BufferUtils.createIntBuffer(2);
        return EXECUTOR.getActiveUniform(program, index, maxLength, sizeType);
    }

    public static void nglShaderSource(int shader, int count, long strings, long lengths) {
        rejectExitCapture();
            if (count <= 0) return;
            final CharSequence source;
            if (count == 1) {
                source = decodeShaderSourceString(strings, lengths, 0);
            } else {
                final StringBuilder sb = new StringBuilder();
                for (int i = 0; i < count; i++) sb.append(decodeShaderSourceString(strings, lengths, i));
                source = sb.toString();
            }
            glShaderSource(shader, source);
        }

    public static void glSpecializeShader(int shader, CharSequence entryPoint, IntBuffer constantIndex, IntBuffer constantValue) {
        rejectExitCapture();
            EXECUTOR.specializeShader(shader, entryPoint, constantIndex, constantValue);
        }

    public static void glDetachShader(int program, int shader) {
        rejectExitCapture();
            EXECUTOR.detachShader(program, shader);
        }

    public static void glValidateProgram(int program) {
        rejectExitCapture();
            EXECUTOR.validateProgram(program);
        }

    public static boolean glIsProgram(int obj) {
        rejectExitCapture();
            return EXECUTOR.isProgram(obj);
        }

    public static boolean glIsShader(int obj) {
        rejectExitCapture();
            return EXECUTOR.isShader(obj);
        }

    public static void glDeleteObjectARB(int obj) {
        rejectExitCapture();
            if (EXECUTOR.isShader(obj)) {
                EXECUTOR.deleteShader(obj);
            } else {
                glDeleteProgram(obj);
            }
        }

    public static int glGetHandleARB(int pname) {
        rejectExitCapture();
            return pname == ARBShaderObjects.GL_PROGRAM_OBJECT_ARB ? GLSMQueries.glGetInteger(GL20.GL_CURRENT_PROGRAM) : EXECUTOR.getInteger(pname);
        }

    public static void glGetObjectParameterARB(int obj, int pname, IntBuffer params) {
        rejectExitCapture();
            final var backend = EXECUTOR;
            if (backend.isShader(obj)) {
                params.put(params.position(), backend.getShaderi(obj, pname));
            } else {
                backend.getProgramiv(obj, pname, params);
            }
        }

    public static void glGetObjectParameterARB(int obj, int pname, FloatBuffer params) {
        rejectExitCapture();
            final var backend = EXECUTOR;
            params.put(params.position(), (float) (backend.isShader(obj) ? backend.getShaderi(obj, pname) : backend.getProgrami(obj, pname)));
        }

    public static int glGetObjectParameteriARB(int obj, int pname) {
        rejectExitCapture();
            final var backend = EXECUTOR;
            return backend.isShader(obj) ? backend.getShaderi(obj, pname) : backend.getProgrami(obj, pname);
        }

    public static String glGetInfoLogARB(int obj, int maxLength) {
        rejectExitCapture();
            final var backend = EXECUTOR;
            return backend.isShader(obj) ? backend.getShaderInfoLog(obj, maxLength) : backend.getProgramInfoLog(obj, maxLength);
        }

    public static void glGetInfoLogARB(int obj, IntBuffer length, ByteBuffer infoLog) {
        rejectExitCapture();
            final var backend = EXECUTOR;
            if (backend.isShader(obj)) {
                backend.getShaderInfoLog(obj, length, infoLog);
            } else {
                backend.getProgramInfoLog(obj, length, infoLog);
            }
        }

    public static void glUniform4i(int location, int v0, int v1, int v2, int v3) {
        rejectExitCapture();
            EXECUTOR.uniform4i(location, v0, v1, v2, v3);
        }

    public static void glUniform1(int location, FloatBuffer values) {
        rejectExitCapture();
            EXECUTOR.uniform1fv(location, values);
        }

    public static void glUniform1(int location, IntBuffer values) {
        rejectExitCapture();
            EXECUTOR.uniform1iv(location, values);
        }

    public static void glUniform1(int location, float[] values) {
            EXECUTOR.uniform1fv(location, values);
        }

    public static void glUniform2(int location, FloatBuffer values) {
        rejectExitCapture();
            EXECUTOR.uniform2(location, values);
        }

    public static void glUniform2(int location, IntBuffer values) {
        rejectExitCapture();
            EXECUTOR.uniform2iv(location, values);
        }

    public static void glUniform3(int location, FloatBuffer values) {
        rejectExitCapture();
            EXECUTOR.uniform3(location, values);
        }

    public static void glUniform3(int location, IntBuffer values) {
        rejectExitCapture();
            EXECUTOR.uniform3iv(location, values);
        }

    public static void glUniform3(int location, float[] values) {
        rejectExitCapture();
            EXECUTOR.uniform3fv(location, values);
        }

    public static void glUniform4(int location, FloatBuffer values) {
        rejectExitCapture();
            EXECUTOR.uniform4(location, values);
        }

    public static void glUniform4(int location, IntBuffer values) {
        rejectExitCapture();
            EXECUTOR.uniform4iv(location, values);
        }

    public static void glUniform4(int location, float[] values) {
        rejectExitCapture();
            EXECUTOR.uniform4fv(location, values);
        }

    public static void glUniformMatrix2(int location, boolean transpose, FloatBuffer matrices) {
        rejectExitCapture();
            EXECUTOR.uniformMatrix2(location, transpose, matrices);
        }

    public static void glGetActiveAttrib(int program, int index, IntBuffer length, IntBuffer size, IntBuffer type, ByteBuffer name) {
        rejectExitCapture();
            EXECUTOR.getActiveAttrib(program, index, length, size, type, name);
        }

    public static void glGetActiveAttrib(int program, int index, int[] length, int[] size, int[] type, ByteBuffer name) {
        rejectExitCapture();
            EXECUTOR.getActiveAttrib(program, index, length, size, type, name);
        }

    public static String glGetActiveAttrib(int program, int index, int maxLength, IntBuffer sizeType) {
        rejectExitCapture();
            return EXECUTOR.getActiveAttrib(program, index, maxLength, sizeType);
        }

    public static String glGetActiveAttrib(int program, int index, int maxLength, IntBuffer size, IntBuffer type) {
            final IntBuffer sizeType = BufferUtils.createIntBuffer(2);
            final String name = EXECUTOR.getActiveAttrib(program, index, maxLength, sizeType);
            if (size != null) size.put(size.position(), sizeType.get(0));
            if (type != null) type.put(type.position(), sizeType.get(1));
            return name;
        }

    public static String glGetActiveAttrib(int program, int index, IntBuffer size, IntBuffer type) {
            return glGetActiveAttrib(program, index, glGetProgrami(program, GL20.GL_ACTIVE_ATTRIBUTE_MAX_LENGTH), size, type);
        }

    public static void glGetAttachedShaders(int program, IntBuffer count, IntBuffer shaders) {
        rejectExitCapture();
            EXECUTOR.getAttachedShaders(program, count, shaders);
        }

    public static void glGetAttachedShaders(int program, int[] count, int[] shaders) {
            EXECUTOR.getAttachedShaders(program, count, shaders);
        }

    public static String glGetShaderSource(int shader, int maxLength) {
        rejectExitCapture();
            return EXECUTOR.getShaderSource(shader, maxLength);
        }

    public static String glGetShaderSource(int shader) {
            return glGetShaderSource(shader, glGetShaderi(shader, GL20.GL_SHADER_SOURCE_LENGTH));
        }

    public static void glGetShaderSource(int shader, IntBuffer length, ByteBuffer source) {
        rejectExitCapture();
            EXECUTOR.getShaderSource(shader, length, source);
        }

    public static void glGetShaderSource(int shader, int[] length, ByteBuffer source) {
        rejectExitCapture();
            EXECUTOR.getShaderSource(shader, length, source);
        }

    public static void glGetUniform(int program, int location, FloatBuffer params) {
        rejectExitCapture();
            EXECUTOR.getUniformfv(program, location, params);
        }

    public static void glGetUniform(int program, int location, IntBuffer params) {
        rejectExitCapture();
            EXECUTOR.getUniformiv(program, location, params);
        }

    public static int getProgramGeneration() {
            return ctx().programGeneration;
        }

    public static IntegerStateStack getProgramStack() {
            return ctx().programStack;
        }

    public static BooleanStateStack getVertexProgramTwoSideState() {
            return ctx().vertexProgramTwoSideState;
        }

    public static int getActiveProgram() {
            return ctx().activeProgram;
        }

    public static void glBindFragDataLocation(int program, int colorNumber, CharSequence name) { rejectExitCapture(); EXECUTOR.bindFragDataLocation(program, colorNumber, name); }

    public static int glGetUniformBlockIndex(int program, CharSequence name) { rejectExitCapture(); return EXECUTOR.getUniformBlockIndex(program, name); }

    public static void glUniformBlockBinding(int program, int blockIndex, int binding) { rejectExitCapture(); EXECUTOR.uniformBlockBinding(program, blockIndex, binding); }

    public static void glProgramParameteri(int program, int pname, int value) {
        rejectExitCapture();
        EXECUTOR.programParameteri(program, pname, value);
    }

    public static void glGetProgramBinary(int program, IntBuffer length, IntBuffer binaryFormat, ByteBuffer binary) {
        rejectExitCapture();
        EXECUTOR.getProgramBinary(program, length, binaryFormat, binary);
    }

    public static void glProgramBinary(int program, int binaryFormat, ByteBuffer binary) {
        rejectExitCapture();
        EXECUTOR.programBinary(program, binaryFormat, binary);
        if (EXECUTOR.getProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_TRUE) {
            CompatUniformManager.onLinkProgram(program, CompatUniformManager.beforeLinkProgram(program));
        }
    }

    public static void glDispatchCompute(int x, int y, int z) { rejectExitCapture(); EXECUTOR.dispatchCompute(x, y, z); }
}
