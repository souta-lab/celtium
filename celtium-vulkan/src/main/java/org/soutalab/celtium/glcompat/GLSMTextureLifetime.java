package org.soutalab.celtium.glcompat;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.profiling.Tracy;
import org.soutalab.celtium.glcompat.stacks.TextureBindingStack;
import org.soutalab.celtium.glcompat.states.ImageUnitBinding;
import org.soutalab.celtium.glcompat.texture.TextureTargets;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongMaps;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.function.IntConsumer;

import static org.soutalab.celtium.glcompat.GLStateManager.ctx;
import static org.soutalab.celtium.glcompat.GLStateManager.isSplashComplete;
import static org.soutalab.celtium.glcompat.GLStateManager.registeredContexts;
import static org.soutalab.celtium.glcompat.GLStateManager.restorePixelUnpackBuffer;
import static org.soutalab.celtium.glcompat.GLStateManager.suspendPixelUnpackBuffer;
import static org.soutalab.celtium.glcompat.backend.DirectGlExecutor.EXECUTOR;

/**
 * Tracks the lifetime of texture names between the delete that retires them and the driver call that frees them.
 *
 * <p>Compat-profile drivers silently recreate a texture when it is bound after deletion, while core-profile drivers
 * raise {@code GL_INVALID_OPERATION}. Vanilla and mods bind deleted names often enough that the error path is not
 * viable, so a deleted texture keeps its name valid until {@code glGenTextures} has a reason to recycle it. Until then
 * the name is either attached to a framebuffer, which keeps its storage, or released to a 1x1 placeholder after
 * {@link #RELEASE_DELAY_FRAMES} frames so the GPU memory is returned without invalidating the name.
 *
 * <p>Rebinding a deferred name rescues it: the name is dropped from the state machine and its storage and parameters
 * are reset to the defaults a freshly generated texture reports.
 */
public final class GLSMTextureLifetime {

    private GLSMTextureLifetime() {}

    private static final Int2LongOpenHashMap deferredDeleteTextures = new Int2LongOpenHashMap();
    static final long DEFERRED_ABSENT = Long.MIN_VALUE;
    static final long DEFERRED_ATTACHED = -1;
    static final long DEFERRED_RELEASED = -2;
    static final int RELEASE_DELAY_FRAMES = 3;
    private static long frameIndex;
    private static long nextReleaseFrame = Long.MAX_VALUE;
    private static final Int2IntOpenHashMap non2DTextureTargets = new Int2IntOpenHashMap();
    private static final Int2IntOpenHashMap textureFramebufferRefs = new Int2IntOpenHashMap();
    private static final FloatBuffer TRANSPARENT_BLACK = BufferUtils.createFloatBuffer(4);
    private static final Tracy.ZoneId Z_TEX_RELEASE_STORAGE = Tracy.zoneId("texReleaseStorage", Tracy.COLOR_FFP);
    private static final Tracy.ZoneId Z_TEX_RESCUE_RESET = Tracy.zoneId("texRescueReset", Tracy.COLOR_FFP);
    private static final Tracy.ZoneId Z_TEX_FLUSH_DELETES = Tracy.zoneId("texFlushDeletes", Tracy.COLOR_FFP);
    static {
        deferredDeleteTextures.defaultReturnValue(DEFERRED_ABSENT);
        non2DTextureTargets.defaultReturnValue(GL11.GL_TEXTURE_2D);
    }

    /**
     * Defers deleting a texture until it has been reclaimed: the name stays valid, so the driver never sees a bind
     * or attach of a name it already forgot.
     */
    static void deferDeleteTexture(int id) {
        final GLContextState glCtx = ctx();
        if (id == 0) return;

        final int savedUnit = glCtx.activeTextureUnit.getValue();
        int activeUnit = savedUnit;

        for (int i = 0; i <= glCtx.maxBoundTextureUnit; i++) {
            final TextureBindingStack unitBindings = glCtx.textures.getTextureUnitBindings(i);
            if (unitBindings.getBinding() == id) {
                if (i != activeUnit) {
                    EXECUTOR.activeTexture(GL13.GL_TEXTURE0 + i);
                    activeUnit = i;
                }
                final int unitTarget = unitBindings.getTarget();
                EXECUTOR.bindTexture(unitTarget == 0 ? GL11.GL_TEXTURE_2D : unitTarget, 0);
            }
        }

        for (int i = 0; i <= glCtx.maxBoundImageUnit; i++) {
            final ImageUnitBinding image = glCtx.imageUnits.get(i);
            if (image != null && image.getTexture() == id) {
                EXECUTOR.bindImageTexture(i, 0, 0, false, 0, GL15.GL_READ_ONLY, GL30.GL_R8);
            }
        }

        if (activeUnit != savedUnit) {
            EXECUTOR.activeTexture(GL13.GL_TEXTURE0 + savedUnit);
        }

        final TextureBindingStack savedBindings = glCtx.textures.getTextureUnitBindings(savedUnit);
        if (!is2D(savedBindings.getTarget()) && previous2D(savedBindings) == id) restoreUnitAfter2DBind(savedBindings, id, id);

        detachFromFramebuffer(glCtx, GL30.GL_DRAW_FRAMEBUFFER, glCtx.drawFramebuffer, id);
        if (glCtx.readFramebuffer != glCtx.drawFramebuffer) detachFromFramebuffer(glCtx, GL30.GL_READ_FRAMEBUFFER, glCtx.readFramebuffer, id);
        markDeferred(id);
    }

    /**
     * Enters {@code id} into the state machine. A name that is still attached somewhere keeps its storage, so the
     * release is only scheduled once the last framebuffer slot drops it.
     */
    static void markDeferred(int id) {
        if (textureFramebufferRefs.get(id) > 0) deferredDeleteTextures.put(id, DEFERRED_ATTACHED);
        else scheduleRelease(id);
    }

    private static void scheduleRelease(int id) {
        deferredDeleteTextures.put(id, frameIndex);
        nextReleaseFrame = Math.min(nextReleaseFrame, frameIndex + RELEASE_DELAY_FRAMES);
    }

    static final long BIND_REJECTED = Long.MIN_VALUE + 1;

    /**
     * Claims a deferred name for a bind, rescuing it. Returns {@link #BIND_REJECTED} when the bind target contradicts
     * the target the texture was created with, which must drop the bind instead of retargeting a live texture.
     */
    static long claimForBind(int texture, int target) {
        if (texture == 0) return DEFERRED_ABSENT;
        final long state = deferredDeleteTextures.isEmpty() ? DEFERRED_ABSENT : deferredDeleteTextures.get(texture);
        if (state == DEFERRED_ABSENT) {
            recordTarget(texture, target);
            return DEFERRED_ABSENT;
        }
        return non2DTextureTargets.get(texture) == target ? deferredDeleteTextures.remove(texture) : BIND_REJECTED;
    }

    static void finishRescue(int target, int texture, long claim) {
        if (claim != DEFERRED_ABSENT) resetRescuedTexture(target, texture, claim);
    }

    static int targetOf(int texture) {
        return non2DTextureTargets.get(texture);
    }

    static boolean isDeferredDelete(int texture) {
        if (texture == 0 || deferredDeleteTextures.isEmpty()) return false;
        return deferredDeleteTextures.containsKey(texture);
    }

    /**
     * Advances the frame counter and releases the storage of every deferred texture whose delay has elapsed. Deletion
     * stays deferred until {@link #flushDeferredTextureDeletes()}: releasing only empties the storage so the name keeps
     * working, while flushing hands the name back to the driver.
     */
    public static void onFrameEnd() {
        frameIndex++;
        if (!isSplashComplete()) return;
        releaseDueTextures(GLSMTextureLifetime::releaseStorage2D);
    }

    /**
     * Runs one release pass over the deferred names at the current {@code frameIndex}, handing each due 2D name to
     * {@code releaseStorage}. Split out of {@link #onFrameEnd()} so the transition logic can be driven without a GL
     * context; the caller owns the frame counter, and {@code nextReleaseFrame} skips the scan on frames where no name
     * can become due.
     */
    static void releaseDueTextures(IntConsumer releaseStorage) {
        if (frameIndex < nextReleaseFrame) return;
        nextReleaseFrame = Long.MAX_VALUE;
        for (Int2LongMap.Entry entry : deferredDeleteTextures.int2LongEntrySet()) {
            final long since = entry.getLongValue();
            if (since < 0) continue;
            if (frameIndex - since < RELEASE_DELAY_FRAMES) {
                nextReleaseFrame = Math.min(nextReleaseFrame, since + RELEASE_DELAY_FRAMES);
                continue;
            }
            final int id = entry.getIntKey();
            if (non2DTextureTargets.get(id) == GL11.GL_TEXTURE_2D) releaseStorage.accept(id);
            entry.setValue(DEFERRED_RELEASED);
        }
    }

    /** Counts deferred names whose storage has not been released yet, for tests and diagnostics. */
    public static int deferredTexturesAwaitingRelease() {
        int count = 0;
        final LongIterator it = deferredDeleteTextures.values().iterator();
        while (it.hasNext()) {
            if (it.nextLong() != DEFERRED_RELEASED) count++;
        }
        return count;
    }

    /**
     * Advances the frame counter without running a release pass. Production reaches the same counter through
     * {@link #onFrameEnd()}; tests drive it separately so a pass can run with an injected release action.
     */
    static void advanceFrameForTesting() {
        frameIndex++;
    }

    /** Clears the shared tables so one test's names never leak into the next. */
    static void resetForTesting() {
        deferredDeleteTextures.clear();
        non2DTextureTargets.clear();
        textureFramebufferRefs.clear();
        frameIndex = 0;
        nextReleaseFrame = Long.MAX_VALUE;
    }

    /** Drops every released name from the table the way {@link #flushDeferredTextureDeletes()} does, minus the driver. */
    static int flushReleasedForTesting() {
        int flushed = 0;
        final var it = deferredDeleteTextures.int2LongEntrySet().iterator();
        while (it.hasNext()) {
            final Int2LongMap.Entry entry = it.next();
            if (entry.getLongValue() == DEFERRED_ATTACHED) continue;
            it.remove();
            non2DTextureTargets.remove(entry.getIntKey());
            flushed++;
        }
        return flushed;
    }

    private static void releaseStorage2D(int id) {
        if (Tracy.ENABLED) Tracy.beginZone(Z_TEX_RELEASE_STORAGE);
        try {
            final TextureBindingStack unit = ctx().textures.getTextureUnitBindings(GLSMTextures.getActiveTextureUnit());
            final int previous2D = previous2D(unit);
            EXECUTOR.bindTexture(GL11.GL_TEXTURE_2D, id);
            if (bindTook(GL11.GL_TEXTURE_2D, id)) releaseBoundStorage2D();
            restoreUnitAfter2DBind(unit, id, previous2D);
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
    }

    private static void restoreUnitAfter2DBind(TextureBindingStack unit, int deleted, int previous2D) {
        EXECUTOR.bindTexture(GL11.GL_TEXTURE_2D, previous2D == deleted ? 0 : previous2D);
        final int target = unit.getTarget();
        if (is2D(target)) return;
        EXECUTOR.bindTexture(target, unit.getBinding() == deleted ? 0 : unit.getBinding());
    }

    static boolean is2D(int target) {
        return target == 0 || target == GL11.GL_TEXTURE_2D;
    }

    private static int previous2D(TextureBindingStack unit) {
        return is2D(unit.getTarget()) ? unit.getBinding() : EXECUTOR.getInteger(GL11.GL_TEXTURE_BINDING_2D);
    }

    static void recordTarget(int texture, int target) {
        if (target != GL11.GL_TEXTURE_2D) non2DTextureTargets.put(texture, target);
    }

    private static void releaseBoundStorage2D() {
        // Immutable storage cannot be reallocated, so a texture that owns it keeps its levels until the name is freed.
        if (RenderSystem.supportsTextureStorage() && EXECUTOR.getTexParameteri(GL11.GL_TEXTURE_2D, GL42.GL_TEXTURE_IMMUTABLE_FORMAT) != 0) return;
        final int size = Math.max(EXECUTOR.getTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH), EXECUTOR.getTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT));
        final int levels = Math.max(1, 32 - Integer.numberOfLeadingZeros(size));
        suspendPixelUnpackBuffer();
        for (int level = 0; level < levels; level++) {
            EXECUTOR.texImage2D(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA8, 0, 0, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        }
        restorePixelUnpackBuffer();
    }

    private static boolean bindTook(int target, int texture) {
        return EXECUTOR.getInteger(TextureTargets.bindingQuery(target)) == texture;
    }

    private static void resetRescuedTexture(int target, int texture, long state) {
        if (Tracy.ENABLED) Tracy.beginZone(Z_TEX_RESCUE_RESET);
        try {
            if (!bindTook(target, texture)) return;
            if (target == GL11.GL_TEXTURE_2D && state >= 0) releaseBoundStorage2D();
            if (target == GL31.GL_TEXTURE_BUFFER || target == GL32.GL_TEXTURE_2D_MULTISAMPLE || target == GL32.GL_TEXTURE_2D_MULTISAMPLE_ARRAY) return;
            final boolean rectangle = target == GL31.GL_TEXTURE_RECTANGLE;
            final int wrap = rectangle ? GL12.GL_CLAMP_TO_EDGE : GL11.GL_REPEAT;
            EXECUTOR.texParameteri(target, GL11.GL_TEXTURE_MIN_FILTER, rectangle ? GL11.GL_LINEAR : GL11.GL_NEAREST_MIPMAP_LINEAR);
            EXECUTOR.texParameteri(target, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            EXECUTOR.texParameteri(target, GL11.GL_TEXTURE_WRAP_S, wrap);
            EXECUTOR.texParameteri(target, GL11.GL_TEXTURE_WRAP_T, wrap);
            EXECUTOR.texParameteri(target, GL12.GL_TEXTURE_WRAP_R, wrap);
            EXECUTOR.texParameteri(target, GL12.GL_TEXTURE_BASE_LEVEL, 0);
            if (!rectangle) EXECUTOR.texParameteri(target, GL12.GL_TEXTURE_MAX_LEVEL, 1000);
            EXECUTOR.texParameterf(target, GL12.GL_TEXTURE_MIN_LOD, -1000.0f);
            EXECUTOR.texParameterf(target, GL12.GL_TEXTURE_MAX_LOD, 1000.0f);
            EXECUTOR.texParameteri(target, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
            EXECUTOR.texParameteri(target, GL14.GL_TEXTURE_COMPARE_FUNC, GL11.GL_LEQUAL);
            EXECUTOR.texParameteri(target, GL33.GL_TEXTURE_SWIZZLE_R, GL11.GL_RED);
            EXECUTOR.texParameteri(target, GL33.GL_TEXTURE_SWIZZLE_G, GL11.GL_GREEN);
            EXECUTOR.texParameteri(target, GL33.GL_TEXTURE_SWIZZLE_B, GL11.GL_BLUE);
            EXECUTOR.texParameteri(target, GL33.GL_TEXTURE_SWIZZLE_A, GL11.GL_ALPHA);
            if (!RenderSystem.isGLES()) {
                EXECUTOR.texParameterf(target, GL14.GL_TEXTURE_LOD_BIAS, 0.0f);
                EXECUTOR.texParameterfv(target, GL11.GL_TEXTURE_BORDER_COLOR, TRANSPARENT_BLACK);
            }
            if (RenderSystem.supportsStencilTexturing()) EXECUTOR.texParameteri(target, GL43.GL_DEPTH_STENCIL_TEXTURE_MODE, GL11.GL_DEPTH_COMPONENT);
            if (EXECUTOR.isAnisotropicSupported()) EXECUTOR.texParameterf(target, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, 1.0f);
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
    }

    /** Flush deferred deletes so the driver can recycle names. */
    static void flushDeferredTextureDeletes() {
        if (!isSplashComplete() || deferredDeleteTextures.isEmpty()) return;
        if (Tracy.ENABLED) Tracy.beginZone(Z_TEX_FLUSH_DELETES);
        try {
            final var it = deferredDeleteTextures.int2LongEntrySet().iterator();
            while (it.hasNext()) {
                final Int2LongMap.Entry entry = it.next();
                if (entry.getLongValue() == DEFERRED_ATTACHED) continue;
                final int id = entry.getIntKey();
                it.remove();
                EXECUTOR.deleteTextures(id);
                non2DTextureTargets.remove(id);
                // Every live context keeps its own cached bindings, so each has to forget the name.
                for (GLContextState glCtx : registeredContexts) {
                    GLSMTextures.clearTextureBindings(glCtx, id);
                }
            }
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
    }

    private static final int DEPTH_SLOT = 16;
    private static final int STENCIL_SLOT = 17;
    static final int FRAMEBUFFER_SLOTS = 18;

    private static int attachmentSlot(int attachment) {
        return switch (attachment) {
            case GL30.GL_DEPTH_ATTACHMENT -> DEPTH_SLOT;
            case GL30.GL_STENCIL_ATTACHMENT -> STENCIL_SLOT;
            default -> attachment >= GL30.GL_COLOR_ATTACHMENT0 && attachment <= GL30.GL_COLOR_ATTACHMENT15 ? attachment - GL30.GL_COLOR_ATTACHMENT0 : -1;
        };
    }

    private static int slotAttachment(int slot) {
        return switch (slot) {
            case DEPTH_SLOT -> GL30.GL_DEPTH_ATTACHMENT;
            case STENCIL_SLOT -> GL30.GL_STENCIL_ATTACHMENT;
            default -> GL30.GL_COLOR_ATTACHMENT0 + slot;
        };
    }

    static boolean beginAttach(int target, int attachment, int texture) {
        final GLContextState glCtx = ctx();
        return beginAttach(glCtx, target == GL30.GL_READ_FRAMEBUFFER ? glCtx.readFramebuffer : glCtx.drawFramebuffer, attachment, texture);
    }

    static boolean beginAttach(GLContextState glCtx, int framebuffer, int attachment, int texture) {
        if (isDeferredDelete(texture)) return false;
        // Upstream also reports the attach to TextureInfoCache.onWrittenOutsideUploads, which marks the texture as
        // written by something other than an upload. Local TextureInfoCache has no such flag, and the only consumer
        // of it upstream is EntityTextureAtlas, which does not exist here either.
        trackFramebufferAttachment(glCtx, framebuffer, attachment, texture);
        return true;
    }

    static void trackFramebufferAttachment(GLContextState glCtx, int framebuffer, int attachment, int texture) {
        if (framebuffer == 0) return;
        if (attachment == GL30.GL_DEPTH_STENCIL_ATTACHMENT) {
            trackFramebufferAttachment(glCtx, framebuffer, GL30.GL_DEPTH_ATTACHMENT, texture);
            trackFramebufferAttachment(glCtx, framebuffer, GL30.GL_STENCIL_ATTACHMENT, texture);
            return;
        }
        final int slot = attachmentSlot(attachment);
        if (slot < 0) return;
        int[] slots = glCtx.framebufferTextures.get(framebuffer);
        if (slots == null) {
            if (texture == 0) return;
            slots = new int[FRAMEBUFFER_SLOTS];
            glCtx.framebufferTextures.put(framebuffer, slots);
        }
        setFramebufferSlot(slots, slot, texture);
    }

    static void setFramebufferSlot(int[] slots, int slot, int texture) {
        final int previous = slots[slot];
        if (previous == texture) return;
        slots[slot] = texture;
        if (texture != 0) textureFramebufferRefs.addTo(texture, 1);
        if (previous != 0 && textureFramebufferRefs.addTo(previous, -1) == 1) {
            textureFramebufferRefs.remove(previous);
            if (deferredDeleteTextures.get(previous) == DEFERRED_ATTACHED) scheduleRelease(previous);
        }
    }

    private static void detachFromFramebuffer(GLContextState glCtx, int target, int framebuffer, int texture) {
        final int[] slots = glCtx.framebufferTextures.get(framebuffer);
        if (slots == null) return;
        for (int slot = 0; slot < slots.length; slot++) {
            if (slots[slot] != texture) continue;
            EXECUTOR.framebufferTexture2D(target, slotAttachment(slot), GL11.GL_TEXTURE_2D, 0, 0);
            setFramebufferSlot(slots, slot, 0);
        }
    }
}
