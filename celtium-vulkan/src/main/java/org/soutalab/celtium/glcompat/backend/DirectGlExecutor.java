package org.soutalab.celtium.glcompat.backend;

import java.util.function.Predicate;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

import org.soutalab.celtium.core.glcompat.DirectGlVulkanSink;
import org.soutalab.celtium.core.state.LightStateRegister;
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

    /** The state machine and sink translation behind this facade (celtium's own). */
    private final DirectGlRuntime runtime = new DirectGlRuntime();

    /** Installs the Vulkan sink the converter submits through (renderer bootstrap seat). */
    public void setSink(DirectGlVulkanSink sink) {
        this.runtime.setSink(sink);
    }

    /** The installed sink, or {@code null} before bootstrap. */
    public DirectGlVulkanSink sink() {
        return this.runtime.sink();
    }

    /** Register -> converter sync, called by {@code DirectGlDispatcher.enterLane3}. */
    public void syncFromRegister(LightStateRegister register) {
        this.runtime.syncFromRegister(register);
    }

    /** Converter -> register sync, called by {@code DirectGlDispatcher.exitLane3}. */
    public void syncToRegister(LightStateRegister register) {
        this.runtime.syncToRegister(register);
    }

    /** Draws the converter dropped before the sink (no program / no sink / bad state). */
    public int droppedDraws() {
        return this.runtime.droppedDraws();
    }

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

    /** Package-private so tests can hold an executor of their own; production uses {@link #EXECUTOR}. */
    DirectGlExecutor() {
    }

    public void init() {
        this.runtime.init();
    }

    public void shutdown() {
        this.runtime.shutdown();
    }

    public boolean isAvailable() {
        return this.runtime.isAvailable();
    }

    public String getName() {
        return this.runtime.getName();
    }

    public boolean hasContext() {
        return this.runtime.hasContext();
    }

    public boolean isCurrent() {
        return this.runtime.isCurrent();
    }

    public int getMinGLSLVersion() {
        return this.runtime.getMinGLSLVersion();
    }

    public void flush() {
        this.runtime.flush();
    }

    public void finish() {
        this.runtime.finish();
    }

    public void enable(int cap) {
        this.runtime.enable(cap);
    }

    public void enablei(int cap, int index) {
        this.runtime.enablei(cap, index);
    }

    public void disable(int cap) {
        this.runtime.disable(cap);
    }

    public void disablei(int cap, int index) {
        this.runtime.disablei(cap, index);
    }

    public void blendFuncSeparatei(int buf, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        this.runtime.blendFuncSeparatei(buf, srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    public void blendFunc(int sfactor, int dfactor) {
        this.runtime.blendFunc(sfactor, dfactor);
    }

    public void blendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        this.runtime.blendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    public void blendEquation(int mode) {
        this.runtime.blendEquation(mode);
    }

    public void blendEquationSeparate(int modeRGB, int modeAlpha) {
        this.runtime.blendEquationSeparate(modeRGB, modeAlpha);
    }

    public void blendColor(float red, float green, float blue, float alpha) {
        this.runtime.blendColor(red, green, blue, alpha);
    }

    public void depthFunc(int func) {
        this.runtime.depthFunc(func);
    }

    public void depthMask(boolean flag) {
        this.runtime.depthMask(flag);
    }

    public void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        this.runtime.colorMask(red, green, blue, alpha);
    }

    public void cullFace(int mode) {
        this.runtime.cullFace(mode);
    }

    public void frontFace(int mode) {
        this.runtime.frontFace(mode);
    }

    public void polygonMode(int face, int mode) {
        this.runtime.polygonMode(face, mode);
    }

    public void polygonOffset(float factor, float units) {
        this.runtime.polygonOffset(factor, units);
    }

    public void stencilFunc(int func, int ref, int mask) {
        this.runtime.stencilFunc(func, ref, mask);
    }

    public void stencilOp(int sfail, int dpfail, int dppass) {
        this.runtime.stencilOp(sfail, dpfail, dppass);
    }

    public void stencilMask(int mask) {
        this.runtime.stencilMask(mask);
    }

    public void stencilFuncSeparate(int face, int func, int ref, int mask) {
        this.runtime.stencilFuncSeparate(face, func, ref, mask);
    }

    public void stencilOpSeparate(int face, int sfail, int dpfail, int dppass) {
        this.runtime.stencilOpSeparate(face, sfail, dpfail, dppass);
    }

    public void stencilMaskSeparate(int face, int mask) {
        this.runtime.stencilMaskSeparate(face, mask);
    }

    public void viewport(int x, int y, int width, int height) {
        this.runtime.viewport(x, y, width, height);
    }

    public void depthRange(double nearVal, double farVal) {
        this.runtime.depthRange(nearVal, farVal);
    }

    public void scissor(int x, int y, int width, int height) {
        this.runtime.scissor(x, y, width, height);
    }

    public void clearColor(float red, float green, float blue, float alpha) {
        this.runtime.clearColor(red, green, blue, alpha);
    }

    public void clearDepth(double depth) {
        this.runtime.clearDepth(depth);
    }

    public void clearStencil(int s) {
        this.runtime.clearStencil(s);
    }

    public void clear(int mask) {
        this.runtime.clear(mask);
    }

    public void clearBufferfv(int buffer, int drawBuffer, FloatBuffer values) {
        this.runtime.clearBufferfv(buffer, drawBuffer, values);
    }

    public void clearBufferiv(int buffer, int drawBuffer, IntBuffer values) {
        this.runtime.clearBufferiv(buffer, drawBuffer, values);
    }

    public void clearBufferfi(int buffer, int drawBuffer, float depth, int stencil) {
        this.runtime.clearBufferfi(buffer, drawBuffer, depth, stencil);
    }

    public void lineWidth(float width) {
        this.runtime.lineWidth(width);
    }

    public void pointSize(float size) {
        this.runtime.pointSize(size);
    }

    public void logicOp(int opcode) {
        this.runtime.logicOp(opcode);
    }

    public void hint(int target, int mode) {
        this.runtime.hint(target, mode);
    }

    public void drawArrays(int mode, int first, int count) {
        this.runtime.drawArrays(mode, first, count);
    }

    public void drawElements(int mode, int count, int type, long indices) {
        this.runtime.drawElements(mode, count, type, indices);
    }

    public void multiDrawElementsIndirect(int mode, int type, long indirect, int drawcount, int stride) {
        this.runtime.multiDrawElementsIndirect(mode, type, indirect, drawcount, stride);
    }

    public void multiDrawArraysIndirect(int mode, long indirect, int drawcount, int stride) {
        this.runtime.multiDrawArraysIndirect(mode, indirect, drawcount, stride);
    }

    public void copyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        this.runtime.copyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
    }

    public void drawElementsInstanced(int mode, int count, int type, long indices, int primcount) {
        this.runtime.drawElementsInstanced(mode, count, type, indices, primcount);
    }

    public void drawElementsBaseVertex(int mode, int count, int type, long indices, int baseVertex) {
        this.runtime.drawElementsBaseVertex(mode, count, type, indices, baseVertex);
    }

    public void multiDrawElementsBaseVertex(int mode, long pCount, int type, long pIndices, int drawcount, long pBaseVertex) {
        this.runtime.multiDrawElementsBaseVertex(mode, pCount, type, pIndices, drawcount, pBaseVertex);
    }

    public void multiDrawArrays(int mode, IntBuffer firsts, IntBuffer counts) {
        this.runtime.multiDrawArrays(mode, firsts, counts);
    }

    public void primitiveRestartIndex(int index) {
        this.runtime.primitiveRestartIndex(index);
    }

    public void pointParameterf(int pname, float param) {
        this.runtime.pointParameterf(pname, param);
    }

    public void pointParameteri(int pname, int param) {
        this.runtime.pointParameteri(pname, param);
    }

    public void drawBuffer(int mode) {
        this.runtime.drawBuffer(mode);
    }

    public void dispatchCompute(int numGroupsX, int numGroupsY, int numGroupsZ) {
        this.runtime.dispatchCompute(numGroupsX, numGroupsY, numGroupsZ);
    }

    public void dispatchComputeIndirect(long offset) {
        this.runtime.dispatchComputeIndirect(offset);
    }

    public int genTextures() {
        return this.runtime.genTextures();
    }

    public void genTextures(IntBuffer textures) {
        this.runtime.genTextures(textures);
    }

    public void deleteTextures(int texture) {
        this.runtime.deleteTextures(texture);
    }

    public void bindTexture(int target, int texture) {
        this.runtime.bindTexture(target, texture);
    }

    public void activeTexture(int texture) {
        this.runtime.activeTexture(texture);
    }

    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        this.runtime.texImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }

    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, DoubleBuffer pixels) {
        this.runtime.texImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }

    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, FloatBuffer pixels) {
        this.runtime.texImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }

    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, IntBuffer pixels) {
        this.runtime.texImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }

    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, long pixelBufferOffset) {
        this.runtime.texImage2D(target, level, internalFormat, width, height, border, format, type, pixelBufferOffset);
    }

    public void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels) {
        this.runtime.texSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
    }

    public void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, IntBuffer pixels) {
        this.runtime.texSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
    }

    public void copyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        this.runtime.copyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
    }

    public void compressedTexImage2D(int target, int level, int internalFormat, int width, int height, int border, ByteBuffer pixels) {
        this.runtime.compressedTexImage2D(target, level, internalFormat, width, height, border, pixels);
    }

    public void compressedTexImage2D(int target, int level, int internalFormat, int width, int height, int border, int imageSize, long data) {
        this.runtime.compressedTexImage2D(target, level, internalFormat, width, height, border, imageSize, data);
    }

    public void compressedTexImage3D(int target, int level, int internalFormat, int width, int height, int depth, int border, ByteBuffer pixels) {
        this.runtime.compressedTexImage3D(target, level, internalFormat, width, height, depth, border, pixels);
    }

    public void compressedTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, ByteBuffer pixels) {
        this.runtime.compressedTexSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, pixels);
    }

    public void getCompressedTexImage(int target, int level, ByteBuffer pixels) {
        this.runtime.getCompressedTexImage(target, level, pixels);
    }

    public void texParameteri(int target, int pname, int param) {
        this.runtime.texParameteri(target, pname, param);
    }

    public void texParameterf(int target, int pname, float param) {
        this.runtime.texParameterf(target, pname, param);
    }

    public void texParameteriv(int target, int pname, IntBuffer params) {
        this.runtime.texParameteriv(target, pname, params);
    }

    public void texParameterfv(int target, int pname, FloatBuffer params) {
        this.runtime.texParameterfv(target, pname, params);
    }

    public int getTexParameteri(int target, int pname) {
        return this.runtime.getTexParameteri(target, pname);
    }

    public float getTexParameterf(int target, int pname) {
        return this.runtime.getTexParameterf(target, pname);
    }

    public int getTexLevelParameteri(int target, int level, int pname) {
        return this.runtime.getTexLevelParameteri(target, level, pname);
    }

    public void generateMipmap(int target) {
        this.runtime.generateMipmap(target);
    }

    public void pixelStorei(int pname, int param) {
        this.runtime.pixelStorei(pname, param);
    }

    public int genSamplers() {
        return this.runtime.genSamplers();
    }

    public void deleteSamplers(int sampler) {
        this.runtime.deleteSamplers(sampler);
    }

    public void bindSampler(int unit, int sampler) {
        this.runtime.bindSampler(unit, sampler);
    }

    public void samplerParameteri(int sampler, int pname, int param) {
        this.runtime.samplerParameteri(sampler, pname, param);
    }

    public void samplerParameterf(int sampler, int pname, float param) {
        this.runtime.samplerParameterf(sampler, pname, param);
    }

    public int genFramebuffers() {
        return this.runtime.genFramebuffers();
    }

    public void deleteFramebuffers(int framebuffer) {
        this.runtime.deleteFramebuffers(framebuffer);
    }

    public void bindFramebuffer(int target, int framebuffer) {
        this.runtime.bindFramebuffer(target, framebuffer);
    }

    public void framebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        this.runtime.framebufferTexture2D(target, attachment, textarget, texture, level);
    }

    public void framebufferTexture(int target, int attachment, int texture, int level) {
        this.runtime.framebufferTexture(target, attachment, texture, level);
    }

    public int genRenderbuffers() {
        return this.runtime.genRenderbuffers();
    }

    public void deleteRenderbuffers(int renderbuffer) {
        this.runtime.deleteRenderbuffers(renderbuffer);
    }

    public void bindRenderbuffer(int target, int renderbuffer) {
        this.runtime.bindRenderbuffer(target, renderbuffer);
    }

    public void renderbufferStorage(int target, int internalformat, int width, int height) {
        this.runtime.renderbufferStorage(target, internalformat, width, height);
    }

    public void renderbufferStorageMultisample(int target, int samples, int internalformat, int width, int height) {
        this.runtime.renderbufferStorageMultisample(target, samples, internalformat, width, height);
    }

    public void framebufferRenderbuffer(int target, int attachment, int renderbuffertarget, int renderbuffer) {
        this.runtime.framebufferRenderbuffer(target, attachment, renderbuffertarget, renderbuffer);
    }

    public int checkFramebufferStatus(int target) {
        return this.runtime.checkFramebufferStatus(target);
    }

    public void blitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        this.runtime.blitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    public void drawBuffers(int buffer) {
        this.runtime.drawBuffers(buffer);
    }

    public void drawBuffers(IntBuffer bufs) {
        this.runtime.drawBuffers(bufs);
    }

    public void readBuffer(int mode) {
        this.runtime.readBuffer(mode);
    }

    public void readPixels(int x, int y, int width, int height, int format, int type, ByteBuffer pixels) {
        this.runtime.readPixels(x, y, width, height, format, type, pixels);
    }

    public void readPixels(int x, int y, int width, int height, int format, int type, FloatBuffer pixels) {
        this.runtime.readPixels(x, y, width, height, format, type, pixels);
    }

    public void readPixels(int x, int y, int width, int height, int format, int type, IntBuffer pixels) {
        this.runtime.readPixels(x, y, width, height, format, type, pixels);
    }

    public void getTexImage(int target, int level, int format, int type, ByteBuffer pixels) {
        this.runtime.getTexImage(target, level, format, type, pixels);
    }

    public void getTexImage(int target, int level, int format, int type, IntBuffer pixels) {
        this.runtime.getTexImage(target, level, format, type, pixels);
    }

    public void getTexImage(int target, int level, int format, int type, long pixelBufferOffset) {
        this.runtime.getTexImage(target, level, format, type, pixelBufferOffset);
    }

    public int getFramebufferAttachmentParameteri(int target, int attachment, int pname) {
        return this.runtime.getFramebufferAttachmentParameteri(target, attachment, pname);
    }

    public int createShader(int type) {
        return this.runtime.createShader(type);
    }

    public void deleteShader(int shader) {
        this.runtime.deleteShader(shader);
    }

    public void shaderSource(int shader, CharSequence source) {
        this.runtime.shaderSource(shader, source);
    }

    public void compileShader(int shader) {
        this.runtime.compileShader(shader);
    }

    public int createProgram() {
        return this.runtime.createProgram();
    }

    public void deleteProgram(int program) {
        this.runtime.deleteProgram(program);
    }

    public void attachShader(int program, int shader) {
        this.runtime.attachShader(program, shader);
    }

    public void detachShader(int program, int shader) {
        this.runtime.detachShader(program, shader);
    }

    public void linkProgram(int program) {
        this.runtime.linkProgram(program);
    }

    public void useProgram(int program) {
        this.runtime.useProgram(program);
    }

    public String getShaderInfoLog(int shader, int maxLength) {
        return this.runtime.getShaderInfoLog(shader, maxLength);
    }

    public void getShaderInfoLog(int shader, IntBuffer length, ByteBuffer infoLog) {
        this.runtime.getShaderInfoLog(shader, length, infoLog);
    }

    public String getProgramInfoLog(int program, int maxLength) {
        return this.runtime.getProgramInfoLog(program, maxLength);
    }

    public void getProgramInfoLog(int program, IntBuffer length, ByteBuffer infoLog) {
        this.runtime.getProgramInfoLog(program, length, infoLog);
    }

    public int getShaderi(int shader, int pname) {
        return this.runtime.getShaderi(shader, pname);
    }

    public int getProgrami(int program, int pname) {
        return this.runtime.getProgrami(program, pname);
    }

    public void getProgramiv(int program, int pname, IntBuffer params) {
        this.runtime.getProgramiv(program, pname, params);
    }

    public String getActiveUniform(int program, int index, int maxLength, IntBuffer sizeType) {
        return this.runtime.getActiveUniform(program, index, maxLength, sizeType);
    }

    public void getActiveUniform(int program, int index, IntBuffer length, IntBuffer size, IntBuffer type, ByteBuffer name) {
        this.runtime.getActiveUniform(program, index, length, size, type, name);
    }

    public void getActiveAttrib(int program, int index, IntBuffer length, IntBuffer size, IntBuffer type, ByteBuffer name) {
        this.runtime.getActiveAttrib(program, index, length, size, type, name);
    }

    public String getActiveAttrib(int program, int index, int maxLength, IntBuffer sizeType) {
        return this.runtime.getActiveAttrib(program, index, maxLength, sizeType);
    }

    public void bindAttribLocation(int program, int index, CharSequence name) {
        this.runtime.bindAttribLocation(program, index, name);
    }

    public int getAttribLocation(int program, CharSequence name) {
        return this.runtime.getAttribLocation(program, name);
    }

    public int getAttribLocation(int program, ByteBuffer name) {
        return this.runtime.getAttribLocation(program, name);
    }

    public int getUniformLocation(int program, CharSequence name) {
        return this.runtime.getUniformLocation(program, name);
    }

    public int getUniformLocation(int program, ByteBuffer name) {
        return this.runtime.getUniformLocation(program, name);
    }

    public void getShaderSource(int shader, IntBuffer length, ByteBuffer source) {
        this.runtime.getShaderSource(shader, length, source);
    }

    public void uniform1i(int location, int v0) {
        this.runtime.uniform1i(location, v0);
    }

    public void uniform1ui(int location, int v0) {
        this.runtime.uniform1ui(location, v0);
    }

    public void uniform2ui(int location, int v0, int v1) {
        this.runtime.uniform2ui(location, v0, v1);
    }

    public void uniform3ui(int location, int v0, int v1, int v2) {
        this.runtime.uniform3ui(location, v0, v1, v2);
    }

    public void uniform4ui(int location, int v0, int v1, int v2, int v3) {
        this.runtime.uniform4ui(location, v0, v1, v2, v3);
    }

    public void uniform1d(int location, double v0) {
        this.runtime.uniform1d(location, v0);
    }

    public void uniform1f(int location, float v0) {
        this.runtime.uniform1f(location, v0);
    }

    public void uniform2f(int location, float v0, float v1) {
        this.runtime.uniform2f(location, v0, v1);
    }

    public void uniform2i(int location, int v0, int v1) {
        this.runtime.uniform2i(location, v0, v1);
    }

    public void uniform3f(int location, float v0, float v1, float v2) {
        this.runtime.uniform3f(location, v0, v1, v2);
    }

    public void uniform3i(int location, int v0, int v1, int v2) {
        this.runtime.uniform3i(location, v0, v1, v2);
    }

    public void uniform4f(int location, float v0, float v1, float v2, float v3) {
        this.runtime.uniform4f(location, v0, v1, v2, v3);
    }

    public void uniform4i(int location, int v0, int v1, int v2, int v3) {
        this.runtime.uniform4i(location, v0, v1, v2, v3);
    }

    public void uniform3(int location, FloatBuffer value) {
        this.runtime.uniform3(location, value);
    }

    public void uniform4(int location, FloatBuffer value) {
        this.runtime.uniform4(location, value);
    }

    public void uniformMatrix3(int location, boolean transpose, FloatBuffer value) {
        this.runtime.uniformMatrix3(location, transpose, value);
    }

    public void uniformMatrix4(int location, boolean transpose, FloatBuffer value) {
        this.runtime.uniformMatrix4(location, transpose, value);
    }

    public void vertexAttrib2f(int index, float v0, float v1) {
        this.runtime.vertexAttrib2f(index, v0, v1);
    }

    public void vertexAttrib3f(int index, float v0, float v1, float v2) {
        this.runtime.vertexAttrib3f(index, v0, v1, v2);
    }

    public void vertexAttrib4f(int index, float v0, float v1, float v2, float v3) {
        this.runtime.vertexAttrib4f(index, v0, v1, v2, v3);
    }

    public int genBuffers() {
        return this.runtime.genBuffers();
    }

    public void deleteBuffers(int buffer) {
        this.runtime.deleteBuffers(buffer);
    }

    public void deleteBuffers(IntBuffer buffers) {
        this.runtime.deleteBuffers(buffers);
    }

    public void bindBuffer(int target, int buffer) {
        this.runtime.bindBuffer(target, buffer);
    }

    public void bindBufferBase(int target, int index, int buffer) {
        this.runtime.bindBufferBase(target, index, buffer);
    }

    public void bindBufferRange(int target, int index, int buffer, long offset, long size) {
        this.runtime.bindBufferRange(target, index, buffer, offset, size);
    }

    public int getUniformBlockIndex(int program, CharSequence name) {
        return this.runtime.getUniformBlockIndex(program, name);
    }

    public void uniformBlockBinding(int program, int blockIndex, int binding) {
        this.runtime.uniformBlockBinding(program, blockIndex, binding);
    }

    public int getIndexedBufferBinding(int target, int index) {
        return this.runtime.getIndexedBufferBinding(target, index);
    }

    public void bufferData(int target, long size, int usage) {
        this.runtime.bufferData(target, size, usage);
    }

    public void bufferData(int target, ByteBuffer data, int usage) {
        this.runtime.bufferData(target, data, usage);
    }

    public void bufferData(int target, FloatBuffer data, int usage) {
        this.runtime.bufferData(target, data, usage);
    }

    public void bufferData(int target, ShortBuffer data, int usage) {
        this.runtime.bufferData(target, data, usage);
    }

    public void bufferData(int target, IntBuffer data, int usage) {
        this.runtime.bufferData(target, data, usage);
    }

    public void bufferData(int target, DoubleBuffer data, int usage) {
        this.runtime.bufferData(target, data, usage);
    }

    public void bufferData(int target, int[] data, int usage) {
        this.runtime.bufferData(target, data, usage);
    }

    public void bufferData(int target, float[] data, int usage) {
        this.runtime.bufferData(target, data, usage);
    }

    public void bufferSubData(int target, long offset, ByteBuffer data) {
        this.runtime.bufferSubData(target, offset, data);
    }

    public void bufferSubData(int target, long offset, ShortBuffer data) {
        this.runtime.bufferSubData(target, offset, data);
    }

    public void bufferSubData(int target, long offset, IntBuffer data) {
        this.runtime.bufferSubData(target, offset, data);
    }

    public void bufferSubData(int target, long offset, FloatBuffer data) {
        this.runtime.bufferSubData(target, offset, data);
    }

    public void bufferSubData(int target, long offset, DoubleBuffer data) {
        this.runtime.bufferSubData(target, offset, data);
    }

    public ByteBuffer mapBuffer(int target, int access) {
        return this.runtime.mapBuffer(target, access);
    }

    public ByteBuffer mapBuffer(int target, int access, long length, ByteBuffer old_buffer) {
        return this.runtime.mapBuffer(target, access, length, old_buffer);
    }

    public boolean unmapBuffer(int target) {
        return this.runtime.unmapBuffer(target);
    }

    public void bufferStorage(int target, ByteBuffer data, int flags) {
        this.runtime.bufferStorage(target, data, flags);
    }

    public void bufferStorage(int target, long size, int flags) {
        this.runtime.bufferStorage(target, size, flags);
    }

    public void getBufferSubData(int target, long offset, ByteBuffer data) {
        this.runtime.getBufferSubData(target, offset, data);
    }

    public void getBufferSubData(int target, long offset, ShortBuffer data) {
        this.runtime.getBufferSubData(target, offset, data);
    }

    public void getBufferSubData(int target, long offset, IntBuffer data) {
        this.runtime.getBufferSubData(target, offset, data);
    }

    public void getBufferSubData(int target, long offset, FloatBuffer data) {
        this.runtime.getBufferSubData(target, offset, data);
    }

    public void getBufferSubData(int target, long offset, DoubleBuffer data) {
        this.runtime.getBufferSubData(target, offset, data);
    }

    public int getBufferParameteri(int target, int pname) {
        return this.runtime.getBufferParameteri(target, pname);
    }

    public boolean isBuffer(int buffer) {
        return this.runtime.isBuffer(buffer);
    }

    public boolean isTexture(int texture) {
        return this.runtime.isTexture(texture);
    }

    public boolean isFramebuffer(int framebuffer) {
        return this.runtime.isFramebuffer(framebuffer);
    }

    public boolean isRenderbuffer(int renderbuffer) {
        return this.runtime.isRenderbuffer(renderbuffer);
    }

    public boolean isSampler(int sampler) {
        return this.runtime.isSampler(sampler);
    }

    public boolean isQuery(int query) {
        return false;
    }

    public ByteBuffer mapBufferRange(int target, long offset, long length, int access) {
        return this.runtime.mapBufferRange(target, offset, length, access);
    }

    public void flushMappedBufferRange(int target, long offset, long length) {
        this.runtime.flushMappedBufferRange(target, offset, length);
    }

    public int genVertexArrays() {
        return this.runtime.genVertexArrays();
    }

    public long getContextHandle() {
        return this.runtime.getContextHandle();
    }

    public void deleteVertexArrays(int array) {
        this.runtime.deleteVertexArrays(array);
    }

    public void bindVertexArray(int array) {
        this.runtime.bindVertexArray(array);
    }

    public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        this.runtime.vertexAttribPointer(index, size, type, normalized, stride, pointer);
    }

    public void vertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
        this.runtime.vertexAttribIPointer(index, size, type, stride, pointer);
    }

    public void enableVertexAttribArray(int index) {
        this.runtime.enableVertexAttribArray(index);
    }

    public void disableVertexAttribArray(int index) {
        this.runtime.disableVertexAttribArray(index);
    }

    public void getVertexAttribi(int index, int pname, IntBuffer params) {
        this.runtime.getVertexAttribi(index, pname, params);
    }

    public void vertexAttribDivisor(int index, int divisor) {
        this.runtime.vertexAttribDivisor(index, divisor);
    }

    public void bindVertexBuffer(int bindingindex, int buffer, long offset, int stride) {
        this.runtime.bindVertexBuffer(bindingindex, buffer, offset, stride);
    }

    public void vertexAttribFormat(int attribindex, int size, int type, boolean normalized, int relativeoffset) {
        this.runtime.vertexAttribFormat(attribindex, size, type, normalized, relativeoffset);
    }

    public void vertexAttribIFormat(int attribindex, int size, int type, int relativeoffset) {
        this.runtime.vertexAttribIFormat(attribindex, size, type, relativeoffset);
    }

    public void vertexAttribBinding(int attribindex, int bindingindex) {
        this.runtime.vertexAttribBinding(attribindex, bindingindex);
    }

    public int createTextures(int target) {
        return this.runtime.createTextures(target);
    }

    public void bindTextureUnit(int unit, int texture) {
        this.runtime.bindTextureUnit(unit, texture);
    }

    public void textureParameteri(int texture, int target, int pname, int param) {
        this.runtime.textureParameteri(texture, target, pname, param);
    }

    public void textureParameterf(int texture, int target, int pname, float param) {
        this.runtime.textureParameterf(texture, target, pname, param);
    }

    public void textureParameteriv(int texture, int target, int pname, IntBuffer params) {
        this.runtime.textureParameteriv(texture, target, pname, params);
    }

    public void texStorage1D(int target, int levels, int internalFormat, int width) {
        this.runtime.texStorage1D(target, levels, internalFormat, width);
    }

    public void texStorage2D(int target, int levels, int internalFormat, int width, int height) {
        this.runtime.texStorage2D(target, levels, internalFormat, width, height);
    }

    public void texStorage3D(int target, int levels, int internalFormat, int width, int height, int depth) {
        this.runtime.texStorage3D(target, levels, internalFormat, width, height, depth);
    }

    public void textureStorage1D(int texture, int levels, int internalFormat, int width) {
        this.runtime.textureStorage1D(texture, levels, internalFormat, width);
    }

    public void textureStorage2D(int texture, int levels, int internalFormat, int width, int height) {
        this.runtime.textureStorage2D(texture, levels, internalFormat, width, height);
    }

    public void textureStorage3D(int texture, int levels, int internalFormat, int width, int height, int depth) {
        this.runtime.textureStorage3D(texture, levels, internalFormat, width, height, depth);
    }

    public void generateTextureMipmap(int texture) {
        this.runtime.generateTextureMipmap(texture);
    }

    public void textureImage2DEXT(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        this.runtime.textureImage2DEXT(texture, target, level, internalformat, width, height, border, format, type, pixels);
    }

    public void textureImage2DEXT(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, IntBuffer pixels) {
        this.runtime.textureImage2DEXT(texture, target, level, internalformat, width, height, border, format, type, pixels);
    }

    public void textureSubImage2D(int texture, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels) {
        this.runtime.textureSubImage2D(texture, level, xoffset, yoffset, width, height, format, type, pixels);
    }

    public void textureSubImage2D(int texture, int level, int xoffset, int yoffset, int width, int height, int format, int type, IntBuffer pixels) {
        this.runtime.textureSubImage2D(texture, level, xoffset, yoffset, width, height, format, type, pixels);
    }

    public int createFramebuffers() {
        return this.runtime.createFramebuffers();
    }

    public void namedFramebufferTexture(int framebuffer, int attachment, int texture, int level) {
        this.runtime.namedFramebufferTexture(framebuffer, attachment, texture, level);
    }

    public void namedFramebufferReadBuffer(int framebuffer, int mode) {
        this.runtime.namedFramebufferReadBuffer(framebuffer, mode);
    }

    public void namedFramebufferDrawBuffers(int framebuffer, IntBuffer bufs) {
        this.runtime.namedFramebufferDrawBuffers(framebuffer, bufs);
    }

    public void blitNamedFramebuffer(int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        this.runtime.blitNamedFramebuffer(readFramebuffer, drawFramebuffer, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    public int createBuffers() {
        return this.runtime.createBuffers();
    }

    public void namedBufferData(int buffer, ByteBuffer data, int usage) {
        this.runtime.namedBufferData(buffer, data, usage);
    }

    public void namedBufferData(int buffer, FloatBuffer data, int usage) {
        this.runtime.namedBufferData(buffer, data, usage);
    }

    public void namedBufferData(int buffer, long size, int usage) {
        this.runtime.namedBufferData(buffer, size, usage);
    }

    public void namedBufferSubData(int buffer, long offset, ByteBuffer data) {
        this.runtime.namedBufferSubData(buffer, offset, data);
    }

    public void copyTextureSubImage2D(int texture, int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        this.runtime.copyTextureSubImage2D(texture, target, level, xoffset, yoffset, x, y, width, height);
    }

    public int getTextureParameteri(int texture, int target, int pname) {
        return this.runtime.getTextureParameteri(texture, target, pname);
    }

    public float getTextureParameterf(int texture, int target, int pname) {
        return this.runtime.getTextureParameterf(texture, target, pname);
    }

    public int getTextureLevelParameteri(int texture, int level, int pname) {
        return this.runtime.getTextureLevelParameteri(texture, level, pname);
    }

    public void textureSubImage3D(int texture, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type, ByteBuffer pixels) {
        this.runtime.textureSubImage3D(texture, level, xoffset, yoffset, zoffset, width, height, depth, format, type, pixels);
    }

    public void compressedTextureSubImage3D(int texture, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, ByteBuffer pixels) {
        this.runtime.compressedTextureSubImage3D(texture, level, xoffset, yoffset, zoffset, width, height, depth, format, pixels);
    }

    public void copyTextureSubImage3D(int texture, int level, int xoffset, int yoffset, int zoffset, int x, int y, int width, int height) {
        this.runtime.copyTextureSubImage3D(texture, level, xoffset, yoffset, zoffset, x, y, width, height);
    }

    public void clearTexSubImage(int texture, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type, ByteBuffer data) {
        this.runtime.clearTexSubImage(texture, level, xoffset, yoffset, zoffset, width, height, depth, format, type, data);
    }

    public void textureParameterfv(int texture, int target, int pname, FloatBuffer params) {
        this.runtime.textureParameterfv(texture, target, pname, params);
    }

    public void texParameterIiv(int target, int pname, IntBuffer params) {
        this.runtime.texParameterIiv(target, pname, params);
    }

    public void texParameterIuiv(int target, int pname, IntBuffer params) {
        this.runtime.texParameterIuiv(target, pname, params);
    }

    public void textureParameterIiv(int texture, int target, int pname, IntBuffer params) {
        this.runtime.textureParameterIiv(texture, target, pname, params);
    }

    public void textureParameterIuiv(int texture, int target, int pname, IntBuffer params) {
        this.runtime.textureParameterIuiv(texture, target, pname, params);
    }

    public void getTextureImage(int texture, int level, int format, int type, ByteBuffer pixels) {
        this.runtime.getTextureImage(texture, level, format, type, pixels);
    }

    public void getCompressedTextureImage(int texture, int level, ByteBuffer pixels) {
        this.runtime.getCompressedTextureImage(texture, level, pixels);
    }

    public int getInteger(int pname) {
        return this.runtime.getInteger(pname);
    }

    public void getInteger(int pname, IntBuffer params) {
        this.runtime.getInteger(pname, params);
    }

    public int getIntegerIndexed(int pname, int index) {
        return this.runtime.getIntegerIndexed(pname, index);
    }

    public float getFloat(int pname) {
        return this.runtime.getFloat(pname);
    }

    public void getFloat(int pname, FloatBuffer params) {
        this.runtime.getFloat(pname, params);
    }

    public boolean getBoolean(int pname) {
        return this.runtime.getBoolean(pname);
    }

    public void getBoolean(int pname, ByteBuffer params) {
        this.runtime.getBoolean(pname, params);
    }

    public String getString(int pname) {
        return this.runtime.getString(pname);
    }

    public String getStringi(int name, int index) {
        return this.runtime.getStringi(name, index);
    }

    public int getError() {
        return this.runtime.getError();
    }

    public long fenceSync(int condition, int flags) {
        return this.runtime.fenceSync(condition, flags);
    }

    public int clientWaitSync(long sync, int flags, long timeout) {
        return this.runtime.clientWaitSync(sync, flags, timeout);
    }

    public void deleteSync(long sync) {
        this.runtime.deleteSync(sync);
    }

    public void clearBufferSubData(int target, int internalFormat, long offset, long size, int format, int type, ByteBuffer data) {
        this.runtime.clearBufferSubData(target, internalFormat, offset, size, format, type, data);
    }

    public void clearBufferData(int target, int internalformat, int format, int type, ByteBuffer data) {
        this.runtime.clearBufferData(target, internalformat, format, type, data);
    }

    public void clearTexImage(int texture, int level, int format, int type) {
        this.runtime.clearTexImage(texture, level, format, type);
    }

    public void bindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        this.runtime.bindImageTexture(unit, texture, level, layered, layer, access, format);
    }

    public void memoryBarrier(int barriers) {
        this.runtime.memoryBarrier(barriers);
    }

    public void copyImageSubData(int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ, int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ, int srcWidth, int srcHeight, int srcDepth) {
        this.runtime.copyImageSubData(srcName, srcTarget, srcLevel, srcX, srcY, srcZ, dstName, dstTarget, dstLevel, dstX, dstY, dstZ, srcWidth, srcHeight, srcDepth);
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

    public void bindFragDataLocation(int program, int colorNumber, CharSequence name) {
        this.runtime.bindFragDataLocation(program, colorNumber, name);
    }

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

    public void copyTexImage1D(int target, int level, int internalFormat, int x, int y, int width, int border) {
        this.runtime.copyTexImage1D(target, level, internalFormat, x, y, width, border);
    }

    public void copyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height, int border) {
        this.runtime.copyTexImage2D(target, level, internalFormat, x, y, width, height, border);
    }

    public void copyTexSubImage1D(int target, int level, int xoffset, int x, int y, int width) {
        this.runtime.copyTexSubImage1D(target, level, xoffset, x, y, width);
    }

    public void copyTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int x, int y, int width, int height) {
        this.runtime.copyTexSubImage3D(target, level, xoffset, yoffset, zoffset, x, y, width, height);
    }

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

    public void getProgramBinary(int program, IntBuffer length, IntBuffer binaryFormat, ByteBuffer binary) {
        this.runtime.getProgramBinary(program, length, binaryFormat, binary);
    }

    public int getQueryObjecti(int id, int pname) { throw new UnsupportedOperationException("occlusion queries unsupported"); }

    public void getQueryObjecti(int id, int pname, IntBuffer params) { throw new UnsupportedOperationException("occlusion queries unsupported"); }

    public void getQueryObjectui(int id, int pname, IntBuffer params) { throw new UnsupportedOperationException("occlusion queries unsupported"); }

    public int getQueryObjectui(int id, int pname) { throw new UnsupportedOperationException("occlusion queries unsupported"); }

    public long getQueryObjectui64(int id, int pname) { throw new UnsupportedOperationException("timer queries unsupported"); }

    public int getSynci(long sync, int pname, IntBuffer length) { throw new UnsupportedOperationException("sync objects unsupported"); }

    public void getUniformfv(int program, int location, FloatBuffer params) {
        this.runtime.getUniformfv(program, location, params);
    }

    public void getUniformiv(int program, int location, IntBuffer params) {
        this.runtime.getUniformiv(program, location, params);
    }

    public boolean handleMakeCurrent(Object drawable) { return false; }

    public boolean handleReleaseContext(Object drawable) { return false; }

    public boolean handleSwapBuffers() { return false; }

    public boolean hasContextOnThread() { return hasContext(); }

    public boolean isAnisotropicSupported() { return true; }

    public boolean isBufferImmutable(int glId) { return false; }

    public boolean isEnabledIndexed(int cap, int index) {
            return getIntegerIndexed(cap, index) != 0;
        }

    public boolean isProgram(int obj) {
        return this.runtime.isProgram(obj);
    }

    public boolean isSDLGPU() { return false; }

    public boolean isShader(int obj) {
        return this.runtime.isShader(obj);
    }

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

    public void programBinary(int program, int binaryFormat, ByteBuffer binary) {
        this.runtime.programBinary(program, binaryFormat, binary);
    }

    public void programParameteri(int program, int pname, int value) {
        this.runtime.programParameteri(program, pname, value);
    }

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

    public void texImage1D(int target, int level, int internalFormat, int width, int border, int format, int type, ByteBuffer pixels) {
        this.runtime.texImage1D(target, level, internalFormat, width, border, format, type, pixels);
    }

    public void texImage3D(int target, int level, int internalFormat, int width, int height, int depth, int border, int format, int type, ByteBuffer pixels) {
        this.runtime.texImage3D(target, level, internalFormat, width, height, depth, border, format, type, pixels);
    }

    public void texImage3D(int target, int level, int internalFormat, int width, int height, int depth, int border, int format, int type, IntBuffer pixels) {
        this.runtime.texImage3D(target, level, internalFormat, width, height, depth, border, format, type, pixels);
    }

    public void texSubImage1D(int target, int level, int xoffset, int width, int format, int type, ByteBuffer pixels) {
        this.runtime.texSubImage1D(target, level, xoffset, width, format, type, pixels);
    }

    public void texSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type, ByteBuffer pixels) {
        this.runtime.texSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, type, pixels);
    }

    public void trimTextureStaging() {
            for (int i = 0; i < clientStagingCache.length; i++) {
                MemoryUtilities.memFree(clientStagingCache[i]);
                clientStagingCache[i] = null;
            }
        }

    public void uniform1fv(int location, FloatBuffer values) {
        this.runtime.uniform1fv(location, values);
    }

    public void uniform1fv(int location, float[] values) {
        this.runtime.uniform1fv(location, values);
    }

    public void uniform1iv(int location, IntBuffer value) {
        this.runtime.uniform1iv(location, value);
    }

    public void uniform2(int location, FloatBuffer value) {
        this.runtime.uniform2(location, value);
    }

    public void uniform2iv(int location, IntBuffer value) {
        this.runtime.uniform2iv(location, value);
    }

    public void uniform3fv(int location, float[] values) {
        this.runtime.uniform3fv(location, values);
    }

    public void uniform3iv(int location, IntBuffer value) {
        this.runtime.uniform3iv(location, value);
    }

    public void uniform4fv(int location, float[] values) {
        this.runtime.uniform4fv(location, values);
    }

    public void uniform4iv(int location, IntBuffer value) {
        this.runtime.uniform4iv(location, value);
    }

    public void uniformMatrix2(int location, boolean transpose, FloatBuffer matrices) {
        this.runtime.uniformMatrix2(location, transpose, matrices);
    }

    public void validateProgram(int program) {
        this.runtime.validateProgram(program);
    }

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

       public String getShaderSource(int shader, int maxLength) {
        return this.runtime.getShaderSource(shader, maxLength);
    }

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
