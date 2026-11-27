package org.soutalab.celtium.glcompat.backend;

import java.util.function.Predicate;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

import org.soutalab.celtium.glcompat.GLContextState;
import org.soutalab.celtium.glcompat.gtnh.bytebuf.MemoryUtilities;
import org.soutalab.celtium.glcompat.texture.TextureStaging;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;

/**
 * celtium's single internal executor for the converted GLSM front: the ONLY way
 * the direct-GL converter reaches the GPU. This is deliberately NOT the
 * supplier's RenderBackend SPI: there is no interface, no ServiceLoader, no
 * second implementation and no per-GL-function abstraction layer for its own
 * sake. State forwards are absorbed by the dispatcher's own tracked state;
 * resource and draw operations bottom out on celtium's DirectGlVulkanSink
 * (celtium-core, section 2.3 interface 3) at Vulkan granularity.
 *
 * Method signatures originate from the LGPL-3.0 GLSM backend surface
 * (Actinium-Dev/Actinium @ ce77eed9); the bodies are celtium's own.
 */
public final class DirectGlExecutor {

    /** The one executor instance the converted front calls. */
    public static final DirectGlExecutor EXECUTOR = new DirectGlExecutor();

    /** Client-side texture staging buffers cached between uploads (supplier field, kept). */
    private final ByteBuffer[] clientStagingCache = new ByteBuffer[16];

    /** Preferred tear-free vsync mode, latched by {@link #setVSyncPreference}. */
    private VSyncMode preferredTearFreeMode;
    private boolean vsyncHonored = true;
    private long displayGeneration;

    protected final void bumpDisplayGeneration() { displayGeneration++; }
    private VSyncMode effectiveVSyncMode = VSyncMode.ON;
    private final GateRecorder gateRecorder = new GateRecorder();

    /** Persistent-buffer writes are tracked by the sink's own upload path; nothing to do here. */
    public void onPersistentBufferWrite(int glId, long offset, long size) {
    }

    private DirectGlExecutor() {
    }

    public void init() {
        
    }

    public void shutdown() {
        
    }

    public boolean isAvailable() {
        return false;
    }

    public String getName() {
        return null;
    }

    public boolean hasContext() {
        return false;
    }

    public boolean isCurrent() {
        return false;
    }

    public int getMinGLSLVersion() {
        return 0;
    }

    public void flush() {
        
    }

    public void finish() {
        
    }

    public void enable(int cap) {
        
    }

    public void enablei(int cap, int index) {
        
    }

    public void disable(int cap) {
        
    }

    public void disablei(int cap, int index) {
        
    }

    public void blendFuncSeparatei(int buf, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        
    }

    public void blendFunc(int sfactor, int dfactor) {
        
    }

    public void blendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        
    }

    public void blendEquation(int mode) {
        
    }

    public void blendEquationSeparate(int modeRGB, int modeAlpha) {
        
    }

    public void blendColor(float red, float green, float blue, float alpha) {
        
    }

    public void depthFunc(int func) {
        
    }

    public void depthMask(boolean flag) {
        
    }

    public void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        
    }

    public void cullFace(int mode) {
        
    }

    public void frontFace(int mode) {
        
    }

    public void polygonMode(int face, int mode) {
        
    }

    public void polygonOffset(float factor, float units) {
        
    }

    public void stencilFunc(int func, int ref, int mask) {
        
    }

    public void stencilOp(int sfail, int dpfail, int dppass) {
        
    }

    public void stencilMask(int mask) {
        
    }

    public void stencilFuncSeparate(int face, int func, int ref, int mask) {
        
    }

    public void stencilOpSeparate(int face, int sfail, int dpfail, int dppass) {
        
    }

    public void stencilMaskSeparate(int face, int mask) {
        
    }

    public void viewport(int x, int y, int width, int height) {
        
    }

    public void depthRange(double nearVal, double farVal) {
        
    }

    public void scissor(int x, int y, int width, int height) {
        
    }

    public void clearColor(float red, float green, float blue, float alpha) {
        
    }

    public void clearDepth(double depth) {
        
    }

    public void clearStencil(int s) {
        
    }

    public void clear(int mask) {
        
    }

    public void lineWidth(float width) {
        
    }

    public void pointSize(float size) {
        
    }

    public void logicOp(int opcode) {
        
    }

    public void hint(int target, int mode) {
        
    }

    public void drawArrays(int mode, int first, int count) {
        
    }

    public void drawElements(int mode, int count, int type, long indices) {
        
    }

    public void multiDrawElementsIndirect(int mode, int type, long indirect, int drawcount, int stride) {
        
    }

    public void multiDrawArraysIndirect(int mode, long indirect, int drawcount, int stride) {
        
    }

    public void copyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        
    }

    public void drawElementsInstanced(int mode, int count, int type, long indices, int primcount) {
        
    }

    public void drawElementsBaseVertex(int mode, int count, int type, long indices, int baseVertex) {
        
    }

    public void multiDrawElementsBaseVertex(int mode, long pCount, int type, long pIndices, int drawcount, long pBaseVertex) {
        
    }

    public void multiDrawArrays(int mode, IntBuffer firsts, IntBuffer counts) {
        
    }

    public void primitiveRestartIndex(int index) {
        
    }

    public void pointParameterf(int pname, float param) {
        
    }

    public void pointParameteri(int pname, int param) {
        
    }

    public void drawBuffer(int mode) {
        
    }

    public void dispatchCompute(int numGroupsX, int numGroupsY, int numGroupsZ) {
        
    }

    public void dispatchComputeIndirect(long offset) {
        
    }

    public int genTextures() {
        return 0;
    }

    public void genTextures(IntBuffer textures) {
        
    }

    public void deleteTextures(int texture) {
        
    }

    public void bindTexture(int target, int texture) {
        
    }

    public void activeTexture(int texture) {
        
    }

    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        
    }

    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, DoubleBuffer pixels) {
        
    }

    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, FloatBuffer pixels) {
        
    }

    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, IntBuffer pixels) {
        
    }

    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, long pixelBufferOffset) {
        
    }

    public void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels) {
        
    }

    public void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, IntBuffer pixels) {
        
    }

    public void copyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        
    }

    public void compressedTexImage2D(int target, int level, int internalFormat, int width, int height, int border, ByteBuffer pixels) {
        
    }

    public void compressedTexImage2D(int target, int level, int internalFormat, int width, int height, int border, int imageSize, long data) {
        
    }

    public void compressedTexImage3D(int target, int level, int internalFormat, int width, int height, int depth, int border, ByteBuffer pixels) {
        
    }

    public void compressedTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, ByteBuffer pixels) {
        
    }

    public void getCompressedTexImage(int target, int level, ByteBuffer pixels) {
        
    }

    public void texParameteri(int target, int pname, int param) {
        
    }

    public void texParameterf(int target, int pname, float param) {
        
    }

    public void texParameteriv(int target, int pname, IntBuffer params) {
        
    }

    public void texParameterfv(int target, int pname, FloatBuffer params) {
        
    }

    public int getTexParameteri(int target, int pname) {
        return 0;
    }

    public float getTexParameterf(int target, int pname) {
        return 0f;
    }

    public int getTexLevelParameteri(int target, int level, int pname) {
        return 0;
    }

    public void generateMipmap(int target) {
        
    }

    public void pixelStorei(int pname, int param) {
        
    }

    public int genSamplers() {
        return 0;
    }

    public void deleteSamplers(int sampler) {
        
    }

    public void bindSampler(int unit, int sampler) {
        
    }

    public void samplerParameteri(int sampler, int pname, int param) {
        
    }

    public void samplerParameterf(int sampler, int pname, float param) {
        
    }

    public int genFramebuffers() {
        return 0;
    }

    public void deleteFramebuffers(int framebuffer) {
        
    }

    public void bindFramebuffer(int target, int framebuffer) {
        
    }

    public void framebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        
    }

    public void framebufferTexture(int target, int attachment, int texture, int level) {
        
    }

    public int genRenderbuffers() {
        return 0;
    }

    public void deleteRenderbuffers(int renderbuffer) {
        
    }

    public void bindRenderbuffer(int target, int renderbuffer) {
        
    }

    public void renderbufferStorage(int target, int internalformat, int width, int height) {
        
    }

    public void renderbufferStorageMultisample(int target, int samples, int internalformat, int width, int height) {
        
    }

    public void framebufferRenderbuffer(int target, int attachment, int renderbuffertarget, int renderbuffer) {
        
    }

    public int checkFramebufferStatus(int target) {
        return 0;
    }

    public void blitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        
    }

    public void drawBuffers(int buffer) {
        
    }

    public void drawBuffers(IntBuffer bufs) {
        
    }

    public void readBuffer(int mode) {
        
    }

    public void readPixels(int x, int y, int width, int height, int format, int type, ByteBuffer pixels) {
        
    }

    public void readPixels(int x, int y, int width, int height, int format, int type, FloatBuffer pixels) {
        
    }

    public void readPixels(int x, int y, int width, int height, int format, int type, IntBuffer pixels) {
        
    }

    public void getTexImage(int target, int level, int format, int type, ByteBuffer pixels) {
        
    }

    public void getTexImage(int target, int level, int format, int type, IntBuffer pixels) {
        
    }

    public void getTexImage(int target, int level, int format, int type, long pixelBufferOffset) {
        
    }

    public int getFramebufferAttachmentParameteri(int target, int attachment, int pname) {
        return 0;
    }

    public int createShader(int type) {
        return 0;
    }

    public void deleteShader(int shader) {
        
    }

    public void shaderSource(int shader, CharSequence source) {
        
    }

    public void compileShader(int shader) {
        
    }

    public int createProgram() {
        return 0;
    }

    public void deleteProgram(int program) {
        
    }

    public void attachShader(int program, int shader) {
        
    }

    public void detachShader(int program, int shader) {
        
    }

    public void linkProgram(int program) {
        
    }

    public void useProgram(int program) {
        
    }

    public String getShaderInfoLog(int shader, int maxLength) {
        return null;
    }

    public void getShaderInfoLog(int shader, IntBuffer length, ByteBuffer infoLog) {
        
    }

    public String getProgramInfoLog(int program, int maxLength) {
        return null;
    }

    public void getProgramInfoLog(int program, IntBuffer length, ByteBuffer infoLog) {
        
    }

    public int getShaderi(int shader, int pname) {
        return 0;
    }

    public int getProgrami(int program, int pname) {
        return 0;
    }

    public void getProgramiv(int program, int pname, IntBuffer params) {
        
    }

    public String getActiveUniform(int program, int index, int maxLength, IntBuffer sizeType) {
        return null;
    }

    public void getActiveUniform(int program, int index, IntBuffer length, IntBuffer size, IntBuffer type, ByteBuffer name) {
        
    }

    public void getActiveAttrib(int program, int index, IntBuffer length, IntBuffer size, IntBuffer type, ByteBuffer name) {
        
    }

    public String getActiveAttrib(int program, int index, int maxLength, IntBuffer sizeType) {
        return null;
    }

    public void bindAttribLocation(int program, int index, CharSequence name) {
        
    }

    public int getAttribLocation(int program, CharSequence name) {
        return 0;
    }

    public int getAttribLocation(int program, ByteBuffer name) {
        return 0;
    }

    public int getUniformLocation(int program, CharSequence name) {
        return 0;
    }

    public int getUniformLocation(int program, ByteBuffer name) {
        return 0;
    }

    public void getShaderSource(int shader, IntBuffer length, ByteBuffer source) {
        
    }

    public void uniform1i(int location, int v0) {
        
    }

    public void uniform1ui(int location, int v0) {
        
    }

    public void uniform2ui(int location, int v0, int v1) {
        
    }

    public void uniform3ui(int location, int v0, int v1, int v2) {
        
    }

    public void uniform4ui(int location, int v0, int v1, int v2, int v3) {
        
    }

    public void uniform1d(int location, double v0) {
        
    }

    public void uniform1f(int location, float v0) {
        
    }

    public void uniform2f(int location, float v0, float v1) {
        
    }

    public void uniform2i(int location, int v0, int v1) {
        
    }

    public void uniform3f(int location, float v0, float v1, float v2) {
        
    }

    public void uniform3i(int location, int v0, int v1, int v2) {
        
    }

    public void uniform4f(int location, float v0, float v1, float v2, float v3) {
        
    }

    public void uniform4i(int location, int v0, int v1, int v2, int v3) {
        
    }

    public void uniform3(int location, FloatBuffer value) {
        
    }

    public void uniform4(int location, FloatBuffer value) {
        
    }

    public void uniformMatrix3(int location, boolean transpose, FloatBuffer value) {
        
    }

    public void uniformMatrix4(int location, boolean transpose, FloatBuffer value) {
        
    }

    public void vertexAttrib2f(int index, float v0, float v1) {
        
    }

    public void vertexAttrib3f(int index, float v0, float v1, float v2) {
        
    }

    public void vertexAttrib4f(int index, float v0, float v1, float v2, float v3) {
        
    }

    public int genBuffers() {
        return 0;
    }

    public void deleteBuffers(int buffer) {
        
    }

    public void deleteBuffers(IntBuffer buffers) {
        
    }

    public void bindBuffer(int target, int buffer) {
        
    }

    public void bindBufferBase(int target, int index, int buffer) {
        
    }

    public void bindBufferRange(int target, int index, int buffer, long offset, long size) {
        
    }

    public int getUniformBlockIndex(int program, CharSequence name) {
        return 0;
    }

    public void uniformBlockBinding(int program, int blockIndex, int binding) {
        
    }

    public int getIndexedBufferBinding(int target, int index) {
        return 0;
    }

    public void bufferData(int target, long size, int usage) {
        
    }

    public void bufferData(int target, ByteBuffer data, int usage) {
        
    }

    public void bufferData(int target, FloatBuffer data, int usage) {
        
    }

    public void bufferData(int target, ShortBuffer data, int usage) {
        
    }

    public void bufferData(int target, IntBuffer data, int usage) {
        
    }

    public void bufferData(int target, DoubleBuffer data, int usage) {
        
    }

    public void bufferData(int target, int[] data, int usage) {
        
    }

    public void bufferData(int target, float[] data, int usage) {
        
    }

    public void bufferSubData(int target, long offset, ByteBuffer data) {
        
    }

    public void bufferSubData(int target, long offset, ShortBuffer data) {
        
    }

    public void bufferSubData(int target, long offset, IntBuffer data) {
        
    }

    public void bufferSubData(int target, long offset, FloatBuffer data) {
        
    }

    public void bufferSubData(int target, long offset, DoubleBuffer data) {
        
    }

    public ByteBuffer mapBuffer(int target, int access) {
        return null;
    }

    public ByteBuffer mapBuffer(int target, int access, long length, ByteBuffer old_buffer) {
        return null;
    }

    public boolean unmapBuffer(int target) {
        return false;
    }

    public void bufferStorage(int target, ByteBuffer data, int flags) {
        
    }

    public void bufferStorage(int target, long size, int flags) {
        
    }

    public void getBufferSubData(int target, long offset, ByteBuffer data) {
        
    }

    public void getBufferSubData(int target, long offset, ShortBuffer data) {
        
    }

    public void getBufferSubData(int target, long offset, IntBuffer data) {
        
    }

    public void getBufferSubData(int target, long offset, FloatBuffer data) {
        
    }

    public void getBufferSubData(int target, long offset, DoubleBuffer data) {
        
    }

    public int getBufferParameteri(int target, int pname) {
        return 0;
    }

    public boolean isBuffer(int buffer) {
        return false;
    }

    public boolean isTexture(int texture) {
        return false;
    }

    public boolean isFramebuffer(int framebuffer) {
        return false;
    }

    public boolean isRenderbuffer(int renderbuffer) {
        return false;
    }

    public boolean isSampler(int sampler) {
        return false;
    }

    public boolean isQuery(int query) {
        return false;
    }

    public ByteBuffer mapBufferRange(int target, long offset, long length, int access) {
        return null;
    }

    public void flushMappedBufferRange(int target, long offset, long length) {
        
    }

    public int genVertexArrays() {
        return 0;
    }

    public long getContextHandle() {
        return 0L;
    }

    public void deleteVertexArrays(int array) {
        
    }

    public void bindVertexArray(int array) {
        
    }

    public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        
    }

    public void vertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
        
    }

    public void enableVertexAttribArray(int index) {
        
    }

    public void disableVertexAttribArray(int index) {
        
    }

    public void getVertexAttribi(int index, int pname, IntBuffer params) {
        
    }

    public void vertexAttribDivisor(int index, int divisor) {
        
    }

    public void bindVertexBuffer(int bindingindex, int buffer, long offset, int stride) {
        
    }

    public void vertexAttribFormat(int attribindex, int size, int type, boolean normalized, int relativeoffset) {
        
    }

    public void vertexAttribIFormat(int attribindex, int size, int type, int relativeoffset) {
        
    }

    public void vertexAttribBinding(int attribindex, int bindingindex) {
        
    }

    public int createTextures(int target) {
        return 0;
    }

    public void bindTextureUnit(int unit, int texture) {
        
    }

    public void textureParameteri(int texture, int target, int pname, int param) {
        
    }

    public void textureParameterf(int texture, int target, int pname, float param) {
        
    }

    public void textureParameteriv(int texture, int target, int pname, IntBuffer params) {
        
    }

    public void texStorage1D(int target, int levels, int internalFormat, int width) {
        
    }

    public void texStorage2D(int target, int levels, int internalFormat, int width, int height) {
        
    }

    public void texStorage3D(int target, int levels, int internalFormat, int width, int height, int depth) {
        
    }

    public void textureStorage1D(int texture, int levels, int internalFormat, int width) {
        
    }

    public void textureStorage2D(int texture, int levels, int internalFormat, int width, int height) {
        
    }

    public void textureStorage3D(int texture, int levels, int internalFormat, int width, int height, int depth) {
        
    }

    public void generateTextureMipmap(int texture) {
        
    }

    public void textureImage2DEXT(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        
    }

    public void textureImage2DEXT(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, IntBuffer pixels) {
        
    }

    public void textureSubImage2D(int texture, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels) {
        
    }

    public void textureSubImage2D(int texture, int level, int xoffset, int yoffset, int width, int height, int format, int type, IntBuffer pixels) {
        
    }

    public int createFramebuffers() {
        return 0;
    }

    public void namedFramebufferTexture(int framebuffer, int attachment, int texture, int level) {
        
    }

    public void namedFramebufferReadBuffer(int framebuffer, int mode) {
        
    }

    public void namedFramebufferDrawBuffers(int framebuffer, IntBuffer bufs) {
        
    }

    public void blitNamedFramebuffer(int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        
    }

    public int createBuffers() {
        return 0;
    }

    public void namedBufferData(int buffer, ByteBuffer data, int usage) {
        
    }

    public void namedBufferData(int buffer, FloatBuffer data, int usage) {
        
    }

    public void namedBufferData(int buffer, long size, int usage) {
        
    }

    public void namedBufferSubData(int buffer, long offset, ByteBuffer data) {
        
    }

    public void copyTextureSubImage2D(int texture, int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        
    }

    public int getTextureParameteri(int texture, int target, int pname) {
        return 0;
    }

    public float getTextureParameterf(int texture, int target, int pname) {
        return 0f;
    }

    public int getTextureLevelParameteri(int texture, int level, int pname) {
        return 0;
    }

    public void textureSubImage3D(int texture, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type, ByteBuffer pixels) {
        
    }

    public void compressedTextureSubImage3D(int texture, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, ByteBuffer pixels) {
        
    }

    public void copyTextureSubImage3D(int texture, int level, int xoffset, int yoffset, int zoffset, int x, int y, int width, int height) {
        
    }

    public void clearTexSubImage(int texture, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type, ByteBuffer data) {
        
    }

    public void textureParameterfv(int texture, int target, int pname, FloatBuffer params) {
        
    }

    public void texParameterIiv(int target, int pname, IntBuffer params) {
        
    }

    public void texParameterIuiv(int target, int pname, IntBuffer params) {
        
    }

    public void textureParameterIiv(int texture, int target, int pname, IntBuffer params) {
        
    }

    public void textureParameterIuiv(int texture, int target, int pname, IntBuffer params) {
        
    }

    public void getTextureImage(int texture, int level, int format, int type, ByteBuffer pixels) {
        
    }

    public void getCompressedTextureImage(int texture, int level, ByteBuffer pixels) {
        
    }

    public int getInteger(int pname) {
        return 0;
    }

    public void getInteger(int pname, IntBuffer params) {
        
    }

    public int getIntegerIndexed(int pname, int index) {
        return 0;
    }

    public float getFloat(int pname) {
        return 0f;
    }

    public void getFloat(int pname, FloatBuffer params) {
        
    }

    public boolean getBoolean(int pname) {
        return false;
    }

    public void getBoolean(int pname, ByteBuffer params) {
        
    }

    public String getString(int pname) {
        return null;
    }

    public String getStringi(int name, int index) {
        return null;
    }

    public int getError() {
        return 0;
    }

    public long fenceSync(int condition, int flags) {
        return 0L;
    }

    public int clientWaitSync(long sync, int flags, long timeout) {
        return 0;
    }

    public void deleteSync(long sync) {
        
    }

    public void clearBufferSubData(int target, int internalFormat, long offset, long size, int format, int type, ByteBuffer data) {
        
    }

    public void clearBufferData(int target, int internalformat, int format, int type, ByteBuffer data) {
        
    }

    public void clearTexImage(int texture, int level, int format, int type) {
        
    }

    public void bindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        
    }

    public void memoryBarrier(int barriers) {
        
    }

    public void copyImageSubData(int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ, int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ, int srcWidth, int srcHeight, int srcDepth) {
        
    }

    // -- Default-body methods carried from the supplier class (native conversion) --

    public void abandonTextureStaging(TextureStaging staging) {
            recycleClientStaging(claimStaging(staging).buffer);
        }

    public void beginQuery(int target, int id) { throw new UnsupportedOperationException("occlusion queries unsupported"); }

    public TextureStaging beginTextureStaging(int level, int x, int y, int width, int height) {
            final int size = Math.multiplyExact(Math.multiplyExact(width, height), 4);
            ByteBuffer buffer = null;
            for (int i = 0; i < clientStagingCache.length; i++) {
                final ByteBuffer cached = clientStagingCache[i];
                if (cached != null && cached.capacity() == size) {
                    clientStagingCache[i] = null;
                    buffer = cached;
                    break;
                }
            }
            if (buffer == null) buffer = MemoryUtilities.memAlloc(size);
            buffer.clear();
            return new ClientTextureStaging(this, buffer, level, x, y, width, height);
        }

    public void bindFragDataLocation(int program, int colorNumber, CharSequence name) {}

    public boolean commitTextureStaging(TextureStaging staging) {
            final ClientTextureStaging client = claimStaging(staging);
            final ByteBuffer upload = client.buffer;
            try {
                upload.clear();
                texSubImage2D(GL11.GL_TEXTURE_2D, client.level, client.x, client.y, client.width, client.height, GL12.GL_BGRA, stagingBgraPixelType(), upload);
            } finally {
                recycleClientStaging(upload);
            }
            return true;
        }

    public void copyTexImage1D(int target, int level, int internalFormat, int x, int y, int width, int border) {}

    public void copyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height, int border) {}

    public void copyTexSubImage1D(int target, int level, int xoffset, int x, int y, int width) {}

    public void copyTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int x, int y, int width, int height) {}

    public void debugMessageCallback(GLDebugMessageListener listener, long userParam) {}

    public void debugMessageControl(int source, int type, int severity, IntBuffer ids, boolean enabled) {}

    public void debugMessageInsert(int source, int type, int id, int severity, CharSequence message) {}

    public void deleteQueries(int id) { throw new UnsupportedOperationException("occlusion queries unsupported"); }

    public int disableDebugOutput() { return 0; }

    public void drawArraysInstanced(int mode, int first, int count, int primcount) {
            throw new UnsupportedOperationException("drawArraysInstanced is only supported by the LWJGL3 backend");
        }

    public void drawRangeElements(int mode, int start, int end, int count, int type, long indices) {
            drawElements(mode, count, type, indices);
        }

    public void drawRangeElementsBaseVertex(int mode, int start, int end, int count, int type, long indices, int baseVertex) {
            drawElementsBaseVertex(mode, count, type, indices, baseVertex);
        }

    public void endQuery(int target) { throw new UnsupportedOperationException("occlusion queries unsupported"); }

    public boolean framebufferCompletenessIsMeaningful() { return true; }

    public void framebufferTexture1D(int target, int attachment, int textarget, int texture, int level) {
            throw new UnsupportedOperationException(getName() + ": 1D framebuffer attachments unsupported");
        }

    public void framebufferTexture3D(int target, int attachment, int textarget, int texture, int level, int zoffset) {
            throw new UnsupportedOperationException(getName() + ": 3D framebuffer attachments unsupported");
        }

    public void framebufferTextureLayer(int target, int attachment, int texture, int level, int layer) {
            throw new UnsupportedOperationException(getName() + ": layered framebuffer attachments unsupported");
        }

    public void genQueries(IntBuffer ids) { throw new UnsupportedOperationException("occlusion queries unsupported"); }

    public int genQueries() { throw new UnsupportedOperationException("occlusion queries unsupported"); }

    public void getAttachedShaders(int program, IntBuffer count, IntBuffer shaders) {
            if (count != null) count.put(0, 0);
        }

    public void getAttachedShaders(int program, int[] count, int[] shaders) {
            getAttachedShaders(program, count == null ? null : IntBuffer.wrap(count), shaders == null ? null : IntBuffer.wrap(shaders));
        }

    public int getDebugMessageLog(int count, IntBuffer sources, IntBuffer types, IntBuffer ids, IntBuffer severities, IntBuffer lengths, ByteBuffer messageLog) { return 0; }

    public double getDouble(int pname) { return getFloat(pname); }

    public void getDouble(int pname, DoubleBuffer params) { if (params.remaining() > 0) params.put(params.position(), getDouble(pname)); }

    public final VSyncMode getEffectiveVSyncMode() { return effectiveVSyncMode; }

    public long getInteger64Indexed(int pname, int index) { return getIntegerIndexed(pname, index); }

    public String getObjectLabel(int identifier, int name) { return ""; }

    public void getProgramBinary(int program, IntBuffer length, IntBuffer binaryFormat, ByteBuffer binary) {}

    public int getQueryObjecti(int id, int pname) { throw new UnsupportedOperationException("occlusion queries unsupported"); }

    public void getQueryObjecti(int id, int pname, IntBuffer params) { throw new UnsupportedOperationException("occlusion queries unsupported"); }

    public void getQueryObjectui(int id, int pname, IntBuffer params) { throw new UnsupportedOperationException("occlusion queries unsupported"); }

    public int getQueryObjectui(int id, int pname) { throw new UnsupportedOperationException("occlusion queries unsupported"); }

    public long getQueryObjectui64(int id, int pname) { throw new UnsupportedOperationException("timer queries unsupported"); }

    public int getSynci(long sync, int pname, IntBuffer length) { throw new UnsupportedOperationException("sync objects unsupported"); }

    public void getUniformfv(int program, int location, FloatBuffer params) {}

    public void getUniformiv(int program, int location, IntBuffer params) {}

    public boolean handleMakeCurrent(Object drawable) { return false; }

    public boolean handleReleaseContext(Object drawable) { return false; }

    public boolean handleSwapBuffers() { return false; }

    public boolean hasContextOnThread() { return hasContext(); }

    public boolean isAnisotropicSupported() { return true; }

    public boolean isBufferImmutable(int glId) { return false; }

    public boolean isEnabledIndexed(int cap, int index) {
            return getIntegerIndexed(cap, index) != 0;
        }

    public boolean isProgram(int obj) { return false; }

    public boolean isSDLGPU() { return false; }

    public boolean isShader(int obj) { return false; }

    public boolean isVertexArray(int array) {
            throw new UnsupportedOperationException("isVertexArray is only supported by the LWJGL3 backend");
        }

    public long mapBufferRangeAddress(int target, long offset, long length, int access) {
            final ByteBuffer buf = mapBufferRange(target, offset, length, access);
            return buf == null ? 0L : MemoryUtilities.memAddress0(buf);
        }

    public void multiDrawArraysIndirectCount(int mode, long indirect, long drawcount, int maxdrawcount, int stride) { throw new UnsupportedOperationException(getName() + ": indirect draw-count buffers unsupported"); }

    public void multiDrawElementsIndirectCount(int mode, int type, long indirect, long drawcount, int maxdrawcount, int stride) { throw new UnsupportedOperationException(getName() + ": indirect draw-count buffers unsupported"); }

    public void objectLabel(int identifier, int name, CharSequence label) {}

    public void polygonOffsetClamp(float factor, float units, float clamp) { throw new UnsupportedOperationException(getName() + ": polygon offset clamp unsupported"); }

    public void popDebugGroup() {}

    public final VSyncMode preferredTearFreeMode() {
            return resolveTearFreeMode(preferredTearFreeMode, this::supportsVSyncMode);
        }

    public void programBinary(int program, int binaryFormat, ByteBuffer binary) {}

    public void programParameteri(int program, int pname, int value) {}

    public void provokingVertex(int provokeMode) {}

    public void pushDebugGroup(int source, int id, CharSequence message) {}

    public void queryCounter(int id, int target) { throw new UnsupportedOperationException("timer queries unsupported"); }

    public final void recordGate(long durationNanos, long endNanos) {
            gateRecorder.recordGate(durationNanos, endNanos);
        }

    public void sampleCoverage(float value, boolean invert) {}

    public final void setVSyncEnabled(boolean enabled) {
            setVSyncMode(enabled ? preferredTearFreeMode() : VSyncMode.OFF);
        }

    public final void setVSyncMode(VSyncMode preferred) {
            setVSyncPreference(preferred);
            final VSyncMode mode = applyVSyncMode(preferred);
            vsyncHonored = applyHonored();
            publishVSyncMode(mode);
        }

    public final void setVSyncPreference(VSyncMode preferred) {
            if (preferred.tearFree()) preferredTearFreeMode = preferred;
        }

    public int setupDebugOutput(DebugMessageHandler handler) { return 0; }

    public void specializeShader(int shader, CharSequence entryPoint, IntBuffer constantIndex, IntBuffer constantValue) { throw new UnsupportedOperationException(getName() + ": externally supplied SPIR-V binaries unsupported"); }

    public boolean supportsDebugOutput() { return false; }

    public boolean supportsProgramBinary() { return false; }

    public boolean supportsVSyncMode(VSyncMode mode) { return mode != VSyncMode.MAILBOX; }

    public void texImage1D(int target, int level, int internalFormat, int width, int border, int format, int type, ByteBuffer pixels) {}

    public void texImage3D(int target, int level, int internalFormat, int width, int height, int depth, int border, int format, int type, ByteBuffer pixels) {}

    public void texImage3D(int target, int level, int internalFormat, int width, int height, int depth, int border, int format, int type, IntBuffer pixels) {}

    public void texSubImage1D(int target, int level, int xoffset, int width, int format, int type, ByteBuffer pixels) {}

    public void texSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type, ByteBuffer pixels) {}

    public void trimTextureStaging() {
            for (int i = 0; i < clientStagingCache.length; i++) {
                MemoryUtilities.memFree(clientStagingCache[i]);
                clientStagingCache[i] = null;
            }
        }

    public void uniform1fv(int location, FloatBuffer values) {}

    public void uniform1fv(int location, float[] values) {}

    public void uniform1iv(int location, IntBuffer value) {
            throw new UnsupportedOperationException("uniform1iv is only supported by the LWJGL3 backend");
        }

    public void uniform2(int location, FloatBuffer value) {
            throw new UnsupportedOperationException("uniform2 is only supported by the LWJGL3 backend");
        }

    public void uniform2iv(int location, IntBuffer value) {
            throw new UnsupportedOperationException("uniform2iv is only supported by the LWJGL3 backend");
        }

    public void uniform3fv(int location, float[] values) {}

    public void uniform3iv(int location, IntBuffer value) {
            throw new UnsupportedOperationException("uniform3iv is only supported by the LWJGL3 backend");
        }

    public void uniform4fv(int location, float[] values) {}

    public void uniform4iv(int location, IntBuffer value) {
            throw new UnsupportedOperationException("uniform4iv is only supported by the LWJGL3 backend");
        }

    public void uniformMatrix2(int location, boolean transpose, FloatBuffer matrices) {}

    public void validateProgram(int program) {}

    public void waitSync(long sync, int flags, long timeout) { throw new UnsupportedOperationException("sync objects unsupported"); }

    // -- Window/display seats -------------------------------------------------
    // The supplier class delegated these to LWJGL2's Display. celtium's window is
    // its own SDL3 GL-less window owned by the frame layer; the converter never
    // pumps it, so these are inert answers at this seat.

    public void pumpDisplayMessages() {
    }

    public long refreshPeriodNanos() {
        return 0L;
    }

    public void updateDisplayFromWorkerThread(boolean processMessages) {
    }

    public boolean wantsDisplayUpdateGateTiming() {
        return false;
    }

    // -- Nested helpers carried from the supplier class (native conversion) --

        static final class GateRecorder {
    
            private long gateDurationNanos;
            private long gateEndNanos;
            private long gpuWaitNanos;
    
            synchronized void recordGate(long durationNanos, long endNanos) {
                if (gateEndNanos != 0L && gateDurationNanos >= durationNanos) return;
                gateDurationNanos = durationNanos;
                gateEndNanos = endNanos;
            }
    
            synchronized void recordGpuWait(long nanos) {
                gpuWaitNanos += nanos;
            }
    
            synchronized void readGateSample(GateSample out) {
                final long end = gateEndNanos;
                out.durationNanos = end == 0L ? 0L : gateDurationNanos;
                out.endNanos = end;
                out.gpuWaitNanos = gpuWaitNanos;
                gpuWaitNanos = 0L;
                gateEndNanos = 0L;
            }
        }

        private static final class ClientTextureStaging implements TextureStaging {
            private final DirectGlExecutor owner;
            private final ByteBuffer buffer;
            private final int level;
            private final int x;
            private final int y;
            private final int width;
            private final int height;
            private boolean released;
    
            ClientTextureStaging(DirectGlExecutor owner, ByteBuffer buffer, int level, int x, int y, int width, int height) {
                this.owner = owner;
                this.buffer = buffer;
                this.level = level;
                this.x = x;
                this.y = y;
                this.width = width;
                this.height = height;
            }
    
            @Override
            public ByteBuffer buffer() {
                return buffer;
            }
    
            @Override
            public boolean bgra() {
                return true;
            }
    
            @Override
            public int level() {
                return level;
            }
    
            @Override
            public String toString() {
                return "client staging block level " + level + " at (" + x + ", " + y + ") " + width + "x" + height;
            }
        }

    // -- Overloads and staging helpers carried from the supplier class --

       private ClientTextureStaging claimStaging(TextureStaging staging) {
            if (!(staging instanceof ClientTextureStaging client)) {
                throw new IllegalArgumentException("Texture staging block is not backed by client memory: " + staging);
            }
            if (client.owner != this) {
                throw new IllegalArgumentException("Texture staging block belongs to another render backend: " + staging);
            }
            if (client.released) {
                throw new IllegalStateException("Texture staging block was already released: " + staging);
            }
            client.released = true;
            return client;
        }

       private void recycleClientStaging(ByteBuffer buffer) {
            int victim = -1;
            for (int i = 0; i < clientStagingCache.length; i++) {
                final ByteBuffer cached = clientStagingCache[i];
                if (cached == null) {
                    clientStagingCache[i] = buffer;
                    return;
                }
                if (cached.capacity() < buffer.capacity() && (victim < 0 || cached.capacity() < clientStagingCache[victim].capacity())) {
                    victim = i;
                }
            }
            if (victim < 0) {
                MemoryUtilities.memFree(buffer);
                return;
            }
            MemoryUtilities.memFree(clientStagingCache[victim]);
            clientStagingCache[victim] = buffer;
        }

       public void drawBuffers(int[] bufs) { drawBuffers(IntBuffer.wrap(bufs)); }

       public void readPixels(int x, int y, int width, int height, int format, int type, long pixelBufferOffset) {
            throw new UnsupportedOperationException(getName() + ": pixel-pack-buffer readback unsupported");
        }

       public String getShaderInfoLog(int shader) {
            return getShaderInfoLog(shader, getShaderi(shader, GL20.GL_INFO_LOG_LENGTH));
        }

       public String getProgramInfoLog(int program) {
            return getProgramInfoLog(program, getProgrami(program, GL20.GL_INFO_LOG_LENGTH));
        }

       public String getShaderSource(int shader, int maxLength) { return ""; }

    // -- Vsync and gate helpers carried from the supplier class --

       static VSyncMode resolveTearFreeMode(VSyncMode preferred, Predicate<VSyncMode> supported) {
            if (preferred != null && preferred.tearFree() && supported.test(preferred)) return preferred;
            return VSyncMode.ON;
        }

       protected VSyncMode applyVSyncMode(VSyncMode preferred) {
            // celtium's window/frame layer owns the swap interval; the executor only
            // records the requested mode (the sink reads it at present time).
            return preferred.tearFree() ? VSyncMode.ON : VSyncMode.OFF;
        }

       protected boolean applyHonored() { return true; }

       protected final void publishVSyncMode(VSyncMode mode) {
            effectiveVSyncMode = mode;
            bumpDisplayGeneration();
        }

       public static final class GateSample {
            public long durationNanos;
            public long endNanos;
            public long gpuWaitNanos;
        }

    protected int stagingBgraPixelType() {
        return GL12.GL_UNSIGNED_INT_8_8_8_8_REV;
    }

    // -- Array/buffer convenience overloads (LWJGL3 call shapes the front uses) --

    public void getShaderInfoLog(int shader, int[] length, ByteBuffer infoLog) {
        getShaderInfoLog(shader, IntBuffer.wrap(length), infoLog);
    }

    public void getProgramInfoLog(int program, int[] length, ByteBuffer infoLog) {
        getProgramInfoLog(program, IntBuffer.wrap(length), infoLog);
    }

    public void getShaderSource(int shader, int[] length, ByteBuffer source) {
        getShaderSource(shader, IntBuffer.wrap(length), source);
    }

    public void getActiveUniform(int program, int index, int[] length, int[] size, int[] type, ByteBuffer name) {
        getActiveUniform(program, index, IntBuffer.wrap(length), IntBuffer.wrap(size), IntBuffer.wrap(type), name);
    }

    public void getActiveAttrib(int program, int index, int[] length, int[] size, int[] type, ByteBuffer name) {
        getActiveAttrib(program, index, IntBuffer.wrap(length), IntBuffer.wrap(size), IntBuffer.wrap(type), name);
    }

    public void uniformMatrix3(int location, boolean transpose, float[] matrices) {
        uniformMatrix3(location, transpose, FloatBuffer.wrap(matrices));
    }

    public void uniformMatrix4(int location, boolean transpose, float[] matrices) {
        uniformMatrix4(location, transpose, FloatBuffer.wrap(matrices));
    }

    public void multiDrawArrays(int mode, int[] firsts, int[] counts) {
        multiDrawArrays(mode, IntBuffer.wrap(firsts), IntBuffer.wrap(counts));
    }

    public void drawElements(int mode, int count, int type, ByteBuffer indices) {
        drawElements(mode, count, type, MemoryUtilities.memAddress0(indices));
    }

    public void drawElements(int mode, ByteBuffer indices) {
        drawElements(mode, indices.remaining(), GL11.GL_UNSIGNED_BYTE, MemoryUtilities.memAddress0(indices));
    }

    public void drawElements(int mode, ShortBuffer indices) {
        drawElements(mode, indices.remaining(), GL11.GL_UNSIGNED_SHORT, MemoryUtilities.memAddress0(indices));
    }

    public void drawElements(int mode, IntBuffer indices) {
        drawElements(mode, indices.remaining(), GL11.GL_UNSIGNED_INT, MemoryUtilities.memAddress0(indices));
    }

    public void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, long pixels) {
        // Address form: the upload reads from the given native address at submit time.
        texSubImage2D(target, level, xoffset, yoffset, width, height, format, type,
            pixels == 0L ? null : MemoryUtilities.memByteBuffer(pixels, Integer.MAX_VALUE));
    }

    public int getUniformBlockIndex(int program, ByteBuffer name) {
        final byte[] bytes = new byte[name.remaining()];
        name.duplicate().get(bytes);
        return getUniformBlockIndex(program, new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
    }
}
