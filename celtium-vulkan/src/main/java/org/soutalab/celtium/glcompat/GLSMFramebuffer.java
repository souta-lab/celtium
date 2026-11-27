package org.soutalab.celtium.glcompat;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.GpuCommandDiagnostics;
import org.soutalab.celtium.glcompat.stacks.IntegerStateStack;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import org.soutalab.celtium.glcompat.DisplayListManager.RecordMode;
import org.soutalab.celtium.glcompat.hooks.GpuCommandPhase;
import org.soutalab.celtium.glcompat.hooks.GpuCommandType;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.IntBuffer;

import static org.soutalab.celtium.glcompat.GLStateManager.beforeUncapturedStateChange;
import static org.soutalab.celtium.glcompat.GLStateManager.ctx;
import static org.soutalab.celtium.glcompat.GLStateManager.rejectExitCapture;
import static org.soutalab.celtium.glcompat.GLStateManager.gpuCheckpoint;
import static org.soutalab.celtium.glcompat.GLStateManager.isCachingEnabled;
import static org.soutalab.celtium.glcompat.GLStateManager.mod;
import static org.soutalab.celtium.glcompat.GLStateManager.recordGpuCommand;
import static org.soutalab.celtium.glcompat.GLStateManager.requireContext;
import static org.soutalab.celtium.glcompat.backend.DirectGlExecutor.EXECUTOR;
import static org.soutalab.celtium.glcompat.GLStateManager.initConfig;
import static org.soutalab.celtium.glcompat.GLStateManager.isGpuDiagnosticCacheTrusted;
import static org.soutalab.celtium.glcompat.GLStateManager.remapPixelTypeForGLES;
import static org.soutalab.celtium.glcompat.GLStateManager.restorePixelPackBuffer;
import static org.soutalab.celtium.glcompat.GLStateManager.suspendPixelPackBuffer;

public final class GLSMFramebuffer {

    private GLSMFramebuffer() {}

    private static long firstClearBlockNanos;
    private static boolean firstClearPending;

    /** Arms first-clear timing for the frame that follows. Called after each presentation. */
    static void armFirstClear() {
        firstClearPending = true;
    }

    public static void glDrawBuffer(int mode) {
        rejectExitCapture();
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            DisplayListManager.recordDrawBuffer(mode);
            if (recordMode == RecordMode.COMPILE) {
                return;
            }
        }
        if (DepthEcho.replaying()) return;
        if (!isCachingEnabled() || ctx().drawBuffer.getValue() != mode) beforeUncapturedStateChange();
        if (isCachingEnabled()) mod(ctx().drawBuffer).setValue(mode);
        EXECUTOR.drawBuffer(mode);
    }

    public static void glClear(int mask) {
        rejectExitCapture();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordClear(mask);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if ((mask & GL11.GL_DEPTH_BUFFER_BIT) != 0 && DepthEcho.protectsDepth(getDrawFramebuffer())) {
            mask &= ~GL11.GL_DEPTH_BUFFER_BIT;
            if (mask == 0) return;
        }
        recordGpuCommand(GpuCommandType.CLEAR, GpuCommandPhase.BEGIN, mask, 0);
        if (firstClearPending && GLStateManager.isMainThread() && getDrawFramebuffer() == 0) {
            firstClearPending = false;
            final long start = System.nanoTime();
            EXECUTOR.clear(mask);
            final long end = System.nanoTime();
            if (end > start) {
                firstClearBlockNanos += end - start;
                EXECUTOR.recordGate(end - start, end);
            }
        } else {
            EXECUTOR.clear(mask);
        }
        recordGpuCommand(GpuCommandType.CLEAR, GpuCommandPhase.END, mask, 0);
        gpuCheckpoint(GpuCommandType.CLEAR);
    }

    public static long takeFirstClearBlockNanos() {
        final long value = firstClearBlockNanos;
        firstClearBlockNanos = 0L;
        return value;
    }

    public static void glReadBuffer(int mode) {
        rejectExitCapture();
        if (DisplayListManager.isRecording()) {
            throw new UnsupportedOperationException("glReadBuffer in display lists not yet implemented - if you see this, please report!");
        }
        EXECUTOR.readBuffer(mode);
    }

    public static void glDrawBuffers(int buffer) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDrawBuffers(1, buffer);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        beforeUncapturedStateChange();
        EXECUTOR.drawBuffers(buffer);
    }

    public static void glDrawBuffers(IntBuffer bufs) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDrawBuffers(bufs.remaining(), bufs);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        beforeUncapturedStateChange();
        EXECUTOR.drawBuffers(bufs);
    }

    public static void glDrawBuffers(int[] bufs) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDrawBuffers(bufs.length, IntBuffer.wrap(bufs));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        beforeUncapturedStateChange();
        EXECUTOR.drawBuffers(bufs);
    }

    public static void glBindFramebuffer(int target, int framebuffer) {
        rejectExitCapture();
        if (target == GL30.GL_FRAMEBUFFER) {
            if (ctx().drawFramebuffer == framebuffer && ctx().readFramebuffer == framebuffer) return;
            if (ctx().drawFramebuffer != framebuffer) ctx().drawFramebufferGeneration++;
            ctx().drawFramebuffer = framebuffer;
            ctx().readFramebuffer = framebuffer;
        } else if (target == GL30.GL_DRAW_FRAMEBUFFER) {
            if (ctx().drawFramebuffer == framebuffer) return;
            ctx().drawFramebufferGeneration++;
            ctx().drawFramebuffer = framebuffer;
        } else if (target == GL30.GL_READ_FRAMEBUFFER) {
            if (ctx().readFramebuffer == framebuffer) return;
            ctx().readFramebuffer = framebuffer;
        }
        recordGpuCommand(GpuCommandType.FRAMEBUFFER_BIND, target, framebuffer);
        EXECUTOR.bindFramebuffer(target, framebuffer);
    }

    public static void glDeleteFramebuffers(int framebuffer) {
        rejectExitCapture();
        if (ctx().drawFramebuffer == framebuffer) ctx().drawFramebuffer = 0;
        if (ctx().readFramebuffer == framebuffer) ctx().readFramebuffer = 0;
        // Slots of a deleted framebuffer no longer keep their textures alive.
        final int[] slots = ctx().framebufferTextures.remove(framebuffer);
        if (slots != null) {
            for (int slot = 0; slot < slots.length; slot++) GLSMTextureLifetime.setFramebufferSlot(slots, slot, 0);
        }
        EXECUTOR.deleteFramebuffers(framebuffer);
    }

    public static void glDeleteFramebuffers(IntBuffer framebuffers) {
        for (int i = framebuffers.position(); i < framebuffers.limit(); i++) glDeleteFramebuffers(framebuffers.get(i));
    }

    public static void glDeleteFramebuffers(int[] framebuffers) {
        for (int framebuffer : framebuffers) glDeleteFramebuffers(framebuffer);
    }

    public static int glGenFramebuffers() {
        return EXECUTOR.genFramebuffers();
    }

    public static void glGenFramebuffers(IntBuffer framebuffers) {
        requireContext("glGenFramebuffers");
        for (int i = framebuffers.position(); i < framebuffers.limit(); i++) framebuffers.put(i, EXECUTOR.genFramebuffers());
    }

    public static void glGenFramebuffers(int[] framebuffers) {
        requireContext("glGenFramebuffers");
        for (int i = 0; i < framebuffers.length; i++) framebuffers[i] = EXECUTOR.genFramebuffers();
    }

    public static int glCheckFramebufferStatus(int target) {
        rejectExitCapture();
        return EXECUTOR.checkFramebufferStatus(target);
    }

    public static int glGetFramebufferAttachmentParameteri(int target, int attachment, int pname) {
        rejectExitCapture();
        return EXECUTOR.getFramebufferAttachmentParameteri(target, attachment, pname);
    }

    public static void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        rejectExitCapture();
        if (!GLSMTextureLifetime.beginAttach(target, attachment, texture)) return;
        EXECUTOR.framebufferTexture2D(target, attachment, textarget, texture, level);
    }

    public static void glFramebufferTexture1D(int target, int attachment, int textarget, int texture, int level) {
        rejectExitCapture();
        if (!GLSMTextureLifetime.beginAttach(target, attachment, texture)) return;
        EXECUTOR.framebufferTexture1D(target, attachment, textarget, texture, level);
    }

    public static void glFramebufferTexture3D(int target, int attachment, int textarget, int texture, int level, int zoffset) {
        rejectExitCapture();
        if (!GLSMTextureLifetime.beginAttach(target, attachment, texture)) return;
        EXECUTOR.framebufferTexture3D(target, attachment, textarget, texture, level, zoffset);
    }

    public static void glFramebufferTextureLayer(int target, int attachment, int texture, int level, int layer) {
        rejectExitCapture();
        if (!GLSMTextureLifetime.beginAttach(target, attachment, texture)) return;
        EXECUTOR.framebufferTextureLayer(target, attachment, texture, level, layer);
    }

    public static void glNamedFramebufferTexture(int framebuffer, int attachment, int texture, int level) {
        rejectExitCapture();
        if (!GLSMTextureLifetime.beginAttach(ctx(), framebuffer, attachment, texture)) return;
        EXECUTOR.namedFramebufferTexture(framebuffer, attachment, texture, level);
    }

    public static void glNamedFramebufferTextureLayer(int framebuffer, int attachment, int texture, int level, int layer) {
        final int previous = swapDrawFramebuffer(framebuffer);
        glFramebufferTextureLayer(GL30.GL_DRAW_FRAMEBUFFER, attachment, texture, level, layer);
        swapDrawFramebuffer(previous);
    }

    public static void glFramebufferTextureFace(int target, int attachment, int texture, int level, int face) {
        glFramebufferTexture2D(target, attachment, face, texture, level);
    }

    public static void glNamedFramebufferTextureEXT(int framebuffer, int attachment, int texture, int level) {
        final int previous = swapDrawFramebuffer(framebuffer);
        glFramebufferTexture(GL30.GL_DRAW_FRAMEBUFFER, attachment, texture, level);
        swapDrawFramebuffer(previous);
    }

    public static void glNamedFramebufferTexture1DEXT(int framebuffer, int attachment, int textarget, int texture, int level) {
        final int previous = swapDrawFramebuffer(framebuffer);
        glFramebufferTexture1D(GL30.GL_DRAW_FRAMEBUFFER, attachment, textarget, texture, level);
        swapDrawFramebuffer(previous);
    }

    public static void glNamedFramebufferTexture2DEXT(int framebuffer, int attachment, int textarget, int texture, int level) {
        final int previous = swapDrawFramebuffer(framebuffer);
        glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, attachment, textarget, texture, level);
        swapDrawFramebuffer(previous);
    }

    public static void glNamedFramebufferTexture3DEXT(int framebuffer, int attachment, int textarget, int texture, int level, int zoffset) {
        final int previous = swapDrawFramebuffer(framebuffer);
        glFramebufferTexture3D(GL30.GL_DRAW_FRAMEBUFFER, attachment, textarget, texture, level, zoffset);
        swapDrawFramebuffer(previous);
    }

    public static void glNamedFramebufferTextureFaceEXT(int framebuffer, int attachment, int texture, int level, int face) {
        glNamedFramebufferTexture2DEXT(framebuffer, attachment, face, texture, level);
    }

    private static int swapDrawFramebuffer(int framebuffer) {
        final GLContextState glCtx = ctx();
        final int previous = glCtx.drawFramebuffer;
        glCtx.drawFramebuffer = framebuffer;
        EXECUTOR.bindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        return previous;
    }

    public static void attachTextureForReading(int attachment, int texture) {
        if (GLSMTextureLifetime.isDeferredDelete(texture)) return;
        final GLContextState glCtx = ctx();
        GLSMTextureLifetime.trackFramebufferAttachment(glCtx, glCtx.readFramebuffer, attachment, texture);
        EXECUTOR.framebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, attachment, GL11.GL_TEXTURE_2D, texture, 0);
    }

    public static boolean glIsFramebuffer(int framebuffer) { rejectExitCapture(); return EXECUTOR.isFramebuffer(framebuffer); }

    public static boolean glIsRenderbuffer(int renderbuffer) { rejectExitCapture(); return EXECUTOR.isRenderbuffer(renderbuffer); }

    public static void glBindRenderbuffer(int target, int renderbuffer) { rejectExitCapture(); EXECUTOR.bindRenderbuffer(target, renderbuffer); }

    public static void glDeleteRenderbuffers(int renderbuffer) { rejectExitCapture(); EXECUTOR.deleteRenderbuffers(renderbuffer); }

    public static void glRenderbufferStorage(int target, int internalformat, int width, int height) { rejectExitCapture(); EXECUTOR.renderbufferStorage(target, internalformat, width, height); }

    public static void glRenderbufferStorageMultisample(int target, int samples, int internalformat, int width, int height) { rejectExitCapture(); EXECUTOR.renderbufferStorageMultisample(target, samples, internalformat, width, height); }

    public static void glNamedFramebufferRenderbuffer(int framebuffer, int attachment, int renderbuffertarget, int renderbuffer) {
        final int previous = swapDrawFramebuffer(framebuffer);
        glFramebufferRenderbuffer(GL30.GL_DRAW_FRAMEBUFFER, attachment, renderbuffertarget, renderbuffer);
        swapDrawFramebuffer(previous);
    }

    public static int glGenRenderbuffers() { return EXECUTOR.genRenderbuffers(); }

    public static void glGenRenderbuffers(IntBuffer renderbuffers) {
        requireContext("glGenRenderbuffers");
        for (int i = renderbuffers.position(); i < renderbuffers.limit(); i++) renderbuffers.put(i, EXECUTOR.genRenderbuffers());
    }

    public static void glGenRenderbuffers(int[] renderbuffers) {
        requireContext("glGenRenderbuffers");
        for (int i = 0; i < renderbuffers.length; i++) renderbuffers[i] = EXECUTOR.genRenderbuffers();
    }

    public static void glFramebufferRenderbuffer(int target, int attachment, int renderbuffertarget, int renderbuffer) {
        rejectExitCapture();
        if (DepthEcho.replaying()) return;
        // A renderbuffer replaces the texture in the slot, so the slot no longer keeps a texture alive.
        GLSMTextureLifetime.beginAttach(target, attachment, 0);
        EXECUTOR.framebufferRenderbuffer(target, attachment, renderbuffertarget, renderbuffer);
    }

    public static boolean framebufferCompletenessIsMeaningful() { return EXECUTOR.framebufferCompletenessIsMeaningful(); }

    public static void glReadPixels(int x, int y, int width, int height, int format, int type, ByteBuffer pixels) {
        rejectExitCapture();
            type = remapPixelTypeForGLES(format, type);
            suspendPixelPackBuffer();
            EXECUTOR.readPixels(x, y, width, height, format, type, pixels);
            restorePixelPackBuffer();
        }

    public static void glReadPixels(int x, int y, int width, int height, int format, int type, FloatBuffer pixels) {
        rejectExitCapture();
            type = remapPixelTypeForGLES(format, type);
            suspendPixelPackBuffer();
            EXECUTOR.readPixels(x, y, width, height, format, type, pixels);
            restorePixelPackBuffer();
        }

    public static void glReadPixels(int x, int y, int width, int height, int format, int type, IntBuffer pixels) {
        rejectExitCapture();
            type = remapPixelTypeForGLES(format, type);
            suspendPixelPackBuffer();
            EXECUTOR.readPixels(x, y, width, height, format, type, pixels);
            restorePixelPackBuffer();
        }

    public static void glReadPixels(int x, int y, int width, int height, int format, int type, long pixelBufferOffset) {
        rejectExitCapture();
            type = remapPixelTypeForGLES(format, type);
            EXECUTOR.readPixels(x, y, width, height, format, type, pixelBufferOffset);
        }

    public static void glFramebufferTexture(int target, int attachment, int texture, int level) {
        rejectExitCapture();
        if (!GLSMTextureLifetime.beginAttach(target, attachment, texture)) return;
        EXECUTOR.framebufferTexture(target, attachment, texture, level);
    }

    public static void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        rejectExitCapture();
        if ((mask & GL11.GL_DEPTH_BUFFER_BIT) != 0 && DepthEcho.protectsDepth(getDrawFramebuffer())) {
            mask &= ~GL11.GL_DEPTH_BUFFER_BIT;
            if (mask == 0) return;
        }
            final boolean cacheTrusted = isGpuDiagnosticCacheTrusted();
            final int diagnosticReadFramebuffer = GpuCommandDiagnostics.trustedValue(cacheTrusted, ctx().readFramebuffer);
            final int diagnosticDrawFramebuffer = GpuCommandDiagnostics.trustedValue(cacheTrusted, ctx().drawFramebuffer);
            recordGpuCommand(
                GpuCommandType.BLIT_FRAMEBUFFER,
                GpuCommandPhase.BEGIN,
                diagnosticReadFramebuffer,
                diagnosticDrawFramebuffer
            );
            EXECUTOR.blitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
            recordGpuCommand(
                GpuCommandType.BLIT_FRAMEBUFFER,
                GpuCommandPhase.END,
                diagnosticReadFramebuffer,
                diagnosticDrawFramebuffer
            );
            gpuCheckpoint(GpuCommandType.BLIT_FRAMEBUFFER);
        }

    public static boolean isFramebufferEnabled() {
            return initConfig != null && initConfig.isFramebufferSupported() && initConfig.isFboEnabled();
        }

    public static int getDrawFramebufferGeneration() {
            return ctx().drawFramebufferGeneration;
        }

    public static IntegerStateStack getDrawBuffer() {
            return ctx().drawBuffer;
        }

    public static int getDrawFramebuffer() {
            return ctx().drawFramebuffer;
        }

    public static int getReadFramebuffer() {
            return ctx().readFramebuffer;
        }
}
