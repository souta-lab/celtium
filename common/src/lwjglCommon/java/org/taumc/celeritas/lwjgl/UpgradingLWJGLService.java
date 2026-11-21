package org.taumc.celeritas.lwjgl;

import java.io.PrintStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.function.Supplier;

/**
 * An {@link LWJGLService} that starts out delegating to a degraded (all-NONE) instance and replaces it
 * with a fully capable one as soon as a caller runs on a thread where creating the real service succeeds.
 * <p>
 * The provider's singleton is created by whichever thread touches it first. On the GL-less Vulkan path
 * that can be a ChunkBuilder worker with no GL capabilities, whose only correct service is the degraded
 * fallback; freezing that fallback process-wide left the render thread's capability-gated operations
 * answering NONE for the rest of the session (BH10-01). This delegate keeps the fallback's contract -
 * nothing fails, memory operations work everywhere - and upgrades out of it.
 * <p>
 * Upgrade attempts are throttled and a failed attempt changes nothing: the degraded instance keeps
 * answering, exactly as it would have without the wrapper.
 */
final class UpgradingLWJGLService implements LWJGLService {
    private static final long UPGRADE_RETRY_NANOS = 1_000_000_000L;

    private final Supplier<LWJGLService> upgradeSource;
    private final long retryNanos;
    private volatile LWJGLService delegate;
    private volatile long lastAttemptNanos;

    UpgradingLWJGLService(LWJGLService initial, Supplier<LWJGLService> upgradeSource) {
        this(initial, upgradeSource, UPGRADE_RETRY_NANOS);
    }

    UpgradingLWJGLService(LWJGLService initial, Supplier<LWJGLService> upgradeSource, long retryNanos) {
        this.delegate = initial;
        this.upgradeSource = upgradeSource;
        this.retryNanos = retryNanos;
    }

    private LWJGLService delegate() {
        LWJGLService current = this.delegate;
        return current.isDegraded() ? tryUpgrade(current) : current;
    }

    private LWJGLService tryUpgrade(LWJGLService current) {
        if (System.nanoTime() - this.lastAttemptNanos < this.retryNanos) {
            return current;
        }
        synchronized (this) {
            if (this.delegate != current) {
                return this.delegate;
            }
            if (System.nanoTime() - this.lastAttemptNanos < this.retryNanos) {
                return this.delegate;
            }
            this.lastAttemptNanos = System.nanoTime();
            LWJGLService fresh;
            try {
                fresh = this.upgradeSource.get();
            } catch (Throwable ignored) {
                // An upgrade attempt must never break a call the degraded instance could have answered.
                return current;
            }
            if (fresh != null && !fresh.isDegraded()) {
                this.delegate = fresh;
                System.out.println("[Celeritas] LWJGL service upgraded from the degraded NONE fallback"
                        + " (the first thread to initialize it had no GL capabilities)");
                return fresh;
            }
            return current;
        }
    }

    @Override
    public boolean isDegraded() {
        return delegate.isDegraded();
    }

    @Override
    public String toString() {
        return "UpgradingLWJGLService[" + this.delegate + "]";
    }

    // ===================== CAPABILITIES =====================

    @Override
    public boolean isOpenGLVersionSupported(int major, int minor) {
        return delegate().isOpenGLVersionSupported(major, minor);
    }

    @Override
    public boolean isExtensionSupported(GLExtension extension) {
        return delegate().isExtensionSupported(extension);
    }

    @Override
    public int getPointerSize() {
        return delegate().getPointerSize();
    }

    // ===================== BUFFER OPERATIONS =====================

    @Override
    public int glGenBuffers() {
        return delegate().glGenBuffers();
    }

    @Override
    public void glDeleteBuffers(int buffer) {
        delegate().glDeleteBuffers(buffer);
    }

    @Override
    public void glBindBuffer(int target, int buffer) {
        delegate().glBindBuffer(target, buffer);
    }

    @Override
    public void glBufferData(int target, long size, int usage) {
        delegate().glBufferData(target, size, usage);
    }

    @Override
    public void glBufferData(int target, ByteBuffer data, int usage) {
        delegate().glBufferData(target, data, usage);
    }

    @Override
    public void glBufferData(int target, long size, long data, int usage) {
        delegate().glBufferData(target, size, data, usage);
    }

    @Override
    public void glBufferStorage(int target, long size, int flags) {
        delegate().glBufferStorage(target, size, flags);
    }

    @Override
    public ByteBuffer glMapBufferRange(int target, long offset, long length, int flags) {
        return delegate().glMapBufferRange(target, offset, length, flags);
    }

    @Override
    public long nglMapBuffer(int target, int access) {
        return delegate().nglMapBuffer(target, access);
    }

    @Override
    public ByteBuffer glMapBuffer(int target, int access) {
        return delegate().glMapBuffer(target, access);
    }

    @Override
    public void glUnmapBuffer(int target) {
        delegate().glUnmapBuffer(target);
    }

    @Override
    public void glFlushMappedBufferRange(int target, long offset, long length) {
        delegate().glFlushMappedBufferRange(target, offset, length);
    }

    @Override
    public void glCopyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        delegate().glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
    }

    @Override
    public void glBindBufferBase(int target, int index, int buffer) {
        delegate().glBindBufferBase(target, index, buffer);
    }

    // ===================== VAO OPERATIONS =====================

    @Override
    public int glGenVertexArrays() {
        return delegate().glGenVertexArrays();
    }

    @Override
    public void glDeleteVertexArrays(int array) {
        delegate().glDeleteVertexArrays(array);
    }

    @Override
    public void glBindVertexArray(int array) {
        delegate().glBindVertexArray(array);
    }

    @Override
    public void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        delegate().glVertexAttribPointer(index, size, type, normalized, stride, pointer);
    }

    @Override
    public void glVertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
        delegate().glVertexAttribIPointer(index, size, type, stride, pointer);
    }

    @Override
    public void glEnableVertexAttribArray(int index) {
        delegate().glEnableVertexAttribArray(index);
    }

    @Override
    public void glVertexAttribDivisor(int index, int divisor) {
        delegate().glVertexAttribDivisor(index, divisor);
    }

    // ===================== SHADER OPERATIONS =====================

    @Override
    public int glCreateShader(int type) {
        return delegate().glCreateShader(type);
    }

    @Override
    public void glShaderSource(int shader, CharSequence source) {
        delegate().glShaderSource(shader, source);
    }

    @Override
    public void glShaderSourceSafe(int shader, CharSequence source) {
        delegate().glShaderSourceSafe(shader, source);
    }

    @Override
    public void glCompileShader(int shader) {
        delegate().glCompileShader(shader);
    }

    @Override
    public String glGetShaderInfoLog(int shader, int maxLength) {
        return delegate().glGetShaderInfoLog(shader, maxLength);
    }

    @Override
    public int glGetShaderi(int shader, int pname) {
        return delegate().glGetShaderi(shader, pname);
    }

    @Override
    public void glDeleteShader(int shader) {
        delegate().glDeleteShader(shader);
    }

    @Override
    public int glCreateProgram() {
        return delegate().glCreateProgram();
    }

    @Override
    public void glAttachShader(int program, int shader) {
        delegate().glAttachShader(program, shader);
    }

    @Override
    public void glLinkProgram(int program) {
        delegate().glLinkProgram(program);
    }

    @Override
    public String glGetProgramInfoLog(int program, int maxLength) {
        return delegate().glGetProgramInfoLog(program, maxLength);
    }

    @Override
    public int glGetProgrami(int program, int pname) {
        return delegate().glGetProgrami(program, pname);
    }

    @Override
    public void glUseProgram(int program) {
        delegate().glUseProgram(program);
    }

    @Override
    public void glDeleteProgram(int program) {
        delegate().glDeleteProgram(program);
    }

    @Override
    public void glBindAttribLocation(int program, int index, CharSequence name) {
        delegate().glBindAttribLocation(program, index, name);
    }

    @Override
    public void glBindFragDataLocation(int program, int colorNumber, CharSequence name) {
        delegate().glBindFragDataLocation(program, colorNumber, name);
    }

    // ===================== UNIFORM OPERATIONS =====================

    @Override
    public int glGetUniformLocation(int program, CharSequence name) {
        return delegate().glGetUniformLocation(program, name);
    }

    @Override
    public int glGetUniformBlockIndex(int program, CharSequence name) {
        return delegate().glGetUniformBlockIndex(program, name);
    }

    @Override
    public void glUniformBlockBinding(int program, int blockIndex, int blockBinding) {
        delegate().glUniformBlockBinding(program, blockIndex, blockBinding);
    }

    @Override
    public void glUniform1f(int location, float v0) {
        delegate().glUniform1f(location, v0);
    }

    @Override
    public void glUniform1i(int location, int v0) {
        delegate().glUniform1i(location, v0);
    }

    @Override
    public void glUniform1fv(int location, FloatBuffer value) {
        delegate().glUniform1fv(location, value);
    }

    @Override
    public void glUniform2i(int location, int v0, int v1) {
        delegate().glUniform2i(location, v0, v1);
    }

    @Override
    public void glUniform3f(int location, float v0, float v1, float v2) {
        delegate().glUniform3f(location, v0, v1, v2);
    }

    @Override
    public void glUniform3fv(int location, FloatBuffer value) {
        delegate().glUniform3fv(location, value);
    }

    @Override
    public void glUniform3fv(int location, float[] value) {
        delegate().glUniform3fv(location, value);
    }

    @Override
    public void glUniform4fv(int location, FloatBuffer value) {
        delegate().glUniform4fv(location, value);
    }

    @Override
    public void glUniform4fv(int location, float[] value) {
        delegate().glUniform4fv(location, value);
    }

    @Override
    public void glUniformMatrix3fv(int location, boolean transpose, FloatBuffer value) {
        delegate().glUniformMatrix3fv(location, transpose, value);
    }

    @Override
    public void glUniformMatrix4fv(int location, boolean transpose, FloatBuffer value) {
        delegate().glUniformMatrix4fv(location, transpose, value);
    }

    // ===================== DRAW OPERATIONS =====================

    @Override
    public void glDrawArrays(int mode, int first, int count) {
        delegate().glDrawArrays(mode, first, count);
    }

    @Override
    public void glDrawElementsBaseVertex(int mode, int count, int type, long indices, int basevertex) {
        delegate().glDrawElementsBaseVertex(mode, count, type, indices, basevertex);
    }

    @Override
    public void glMultiDrawElementsBaseVertex(int mode, long pCount, int type, long pIndices, int drawcount, long pBaseVertex) {
        delegate().glMultiDrawElementsBaseVertex(mode, pCount, type, pIndices, drawcount, pBaseVertex);
    }

    @Override
    public void glMultiDrawElementsIndirect(int mode, int type, long indirect, int drawcount, int stride) {
        delegate().glMultiDrawElementsIndirect(mode, type, indirect, drawcount, stride);
    }

    // ===================== SYNC OPERATIONS =====================

    @Override
    public long glFenceSync(int condition, int flags) {
        return delegate().glFenceSync(condition, flags);
    }

    @Override
    public int glClientWaitSync(long sync, int flags, long timeout) {
        return delegate().glClientWaitSync(sync, flags, timeout);
    }

    @Override
    public int glGetSynci(long sync, int pname, IntBuffer length) {
        return delegate().glGetSynci(sync, pname, length);
    }

    @Override
    public void glWaitSync(long sync, int flags, long timeout) {
        delegate().glWaitSync(sync, flags, timeout);
    }

    @Override
    public void glDeleteSync(long sync) {
        delegate().glDeleteSync(sync);
    }

    // ===================== QUERY OPERATIONS =====================

    @Override
    public int glGenQueries() {
        return delegate().glGenQueries();
    }

    @Override
    public void glDeleteQueries(int query) {
        delegate().glDeleteQueries(query);
    }

    @Override
    public void glBeginQuery(int target, int id) {
        delegate().glBeginQuery(target, id);
    }

    @Override
    public void glEndQuery(int target) {
        delegate().glEndQuery(target);
    }

    @Override
    public long glGetQueryObjectui64(int id, int pname) {
        return delegate().glGetQueryObjectui64(id, pname);
    }

    // ===================== DEBUG OPERATIONS =====================

    @Override
    public PrintStream getDebugStream() {
        return delegate().getDebugStream();
    }

    @Override
    public int setupDebugCallback(DebugMessageHandler handler) {
        return delegate().setupDebugCallback(handler);
    }

    @Override
    public void disableDebugCallback() {
        delegate().disableDebugCallback();
    }

    @Override
    public void glObjectLabel(int identifier, int name, CharSequence label) {
        delegate().glObjectLabel(identifier, name, label);
    }

    @Override
    public void glPushDebugGroup(int source, int id, CharSequence message) {
        delegate().glPushDebugGroup(source, id, message);
    }

    @Override
    public void glPopDebugGroup() {
        delegate().glPopDebugGroup();
    }

    // ===================== TEXTURE OPERATIONS =====================

    @Override
    public int glGenTextures() {
        return delegate().glGenTextures();
    }

    @Override
    public void glGenTextures(int[] textures) {
        delegate().glGenTextures(textures);
    }

    @Override
    public void glDeleteTextures(int texture) {
        delegate().glDeleteTextures(texture);
    }

    @Override
    public void glDeleteTextures(int[] textures) {
        delegate().glDeleteTextures(textures);
    }

    @Override
    public void glBindTexture(int target, int texture) {
        delegate().glBindTexture(target, texture);
    }

    @Override
    public void glActiveTexture(int texture) {
        delegate().glActiveTexture(texture);
    }

    @Override
    public int glGetTexLevelParameteri(int target, int level, int pname) {
        return delegate().glGetTexLevelParameteri(target, level, pname);
    }

    @Override
    public void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        delegate().glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
    }

    @Override
    public void glPixelStorei(int pname, int param) {
        delegate().glPixelStorei(pname, param);
    }

    @Override
    public void glTexImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        delegate().glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }

    @Override
    public void glTexParameteri(int target, int pname, int param) {
        delegate().glTexParameteri(target, pname, param);
    }

    // ===================== FRAMEBUFFER OPERATIONS =====================

    @Override
    public int glGenFramebuffers() {
        return delegate().glGenFramebuffers();
    }

    @Override
    public void glDeleteFramebuffers(int framebuffer) {
        delegate().glDeleteFramebuffers(framebuffer);
    }

    @Override
    public void glBindFramebuffer(int target, int framebuffer) {
        delegate().glBindFramebuffer(target, framebuffer);
    }

    @Override
    public int glCheckFramebufferStatus(int target) {
        return delegate().glCheckFramebufferStatus(target);
    }

    @Override
    public void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        delegate().glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }

    @Override
    public void glDrawBuffers(int[] buffers) {
        delegate().glDrawBuffers(buffers);
    }

    @Override
    public void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        delegate().glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    // ===================== STATE OPERATIONS =====================

    @Override
    public void glEnable(int cap) {
        delegate().glEnable(cap);
    }

    @Override
    public void glDisable(int cap) {
        delegate().glDisable(cap);
    }

    @Override
    public void glBlendFunc(int sfactor, int dfactor) {
        delegate().glBlendFunc(sfactor, dfactor);
    }

    @Override
    public void glBlendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        delegate().glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    @Override
    public void glDepthFunc(int func) {
        delegate().glDepthFunc(func);
    }

    @Override
    public void glDepthMask(boolean flag) {
        delegate().glDepthMask(flag);
    }

    @Override
    public void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        delegate().glColorMask(red, green, blue, alpha);
    }

    @Override
    public void glViewport(int x, int y, int width, int height) {
        delegate().glViewport(x, y, width, height);
    }

    @Override
    public void glClear(int mask) {
        delegate().glClear(mask);
    }

    @Override
    public void glClearColor(float red, float green, float blue, float alpha) {
        delegate().glClearColor(red, green, blue, alpha);
    }

    @Override
    public int glGetError() {
        return delegate().glGetError();
    }

    // ===================== COMPATIBILITY PROFILE =====================

    @Override
    public void glMatrixMode(int mode) {
        delegate().glMatrixMode(mode);
    }

    @Override
    public void glLoadMatrixf(FloatBuffer m) {
        delegate().glLoadMatrixf(m);
    }

    // ===================== MISC GL =====================

    @Override
    public int glGetInteger(int pname) {
        return delegate().glGetInteger(pname);
    }

    @Override
    public void glGetIntegerv(int pname, int[] params) {
        delegate().glGetIntegerv(pname, params);
    }

    @Override
    public boolean glGetBoolean(int pname) {
        return delegate().glGetBoolean(pname);
    }

    @Override
    public String glGetString(int pname) {
        return delegate().glGetString(pname);
    }

    @Override
    public int glGetAttribLocation(int program, CharSequence name) {
        return delegate().glGetAttribLocation(program, name);
    }

    // ===================== MEMORY STACK =====================

    @Override
    public MemoryStack stackPush() {
        return delegate().stackPush();
    }

    // ===================== NATIVE MEMORY =====================

    @Override
    public long nmemAlloc(long size) {
        return delegate().nmemAlloc(size);
    }

    @Override
    public long nmemCalloc(long count, long size) {
        return delegate().nmemCalloc(count, size);
    }

    @Override
    public long nmemAlignedAlloc(long alignment, long size) {
        return delegate().nmemAlignedAlloc(alignment, size);
    }

    @Override
    public long nmemRealloc(long ptr, long size) {
        return delegate().nmemRealloc(ptr, size);
    }

    @Override
    public void nmemFree(long ptr) {
        delegate().nmemFree(ptr);
    }

    @Override
    public void nmemAlignedFree(long ptr) {
        delegate().nmemAlignedFree(ptr);
    }

    @Override
    public ByteBuffer memAlloc(int size) {
        return delegate().memAlloc(size);
    }

    @Override
    public ByteBuffer memCalloc(int size) {
        return delegate().memCalloc(size);
    }

    @Override
    public ByteBuffer memRealloc(ByteBuffer buffer, int size) {
        return delegate().memRealloc(buffer, size);
    }

    @Override
    public void memFree(Buffer buffer) {
        delegate().memFree(buffer);
    }

    @Override
    public ByteBuffer memByteBuffer(long address, int capacity) {
        return delegate().memByteBuffer(address, capacity);
    }

    @Override
    public long memAddress(Buffer buffer) {
        return delegate().memAddress(buffer);
    }

    @Override
    public long memAddress(Buffer buffer, int position) {
        return delegate().memAddress(buffer, position);
    }

    @Override
    public void memSet(long address, int value, long bytes) {
        delegate().memSet(address, value, bytes);
    }

    @Override
    public void memCopy(long src, long dst, long bytes) {
        delegate().memCopy(src, dst, bytes);
    }

    @Override
    public void memCopy(ByteBuffer src, ByteBuffer dst) {
        delegate().memCopy(src, dst);
    }

    @Override
    public void memPutByte(long address, byte value) {
        delegate().memPutByte(address, value);
    }

    @Override
    public void memPutShort(long address, short value) {
        delegate().memPutShort(address, value);
    }

    @Override
    public void memPutInt(long address, int value) {
        delegate().memPutInt(address, value);
    }

    @Override
    public void memPutFloat(long address, float value) {
        delegate().memPutFloat(address, value);
    }

    @Override
    public void memPutLong(long address, long value) {
        delegate().memPutLong(address, value);
    }

    @Override
    public void memPutAddress(long address, long value) {
        delegate().memPutAddress(address, value);
    }

    @Override
    public byte memGetByte(long address) {
        return delegate().memGetByte(address);
    }

    @Override
    public short memGetShort(long address) {
        return delegate().memGetShort(address);
    }

    @Override
    public int memGetInt(long address) {
        return delegate().memGetInt(address);
    }

    @Override
    public float memGetFloat(long address) {
        return delegate().memGetFloat(address);
    }

    @Override
    public long memGetLong(long address) {
        return delegate().memGetLong(address);
    }

    @Override
    public long memGetAddress(long address) {
        return delegate().memGetAddress(address);
    }

    @Override
    public ByteBuffer memSlice(ByteBuffer buffer, int offset, int capacity) {
        return delegate().memSlice(buffer, offset, capacity);
    }
}
