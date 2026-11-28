package org.soutalab.celtium.core;

import org.soutalab.celtium.vulkan.CeltiumBackends;
import org.soutalab.celtium.vulkan.NativeDrawSkipCensus;
import org.soutalab.celtium.vulkan.NativeGeometryBuffer;
import org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend;

/** Native Minecraft/Forge draw entry. Raw GL calls made by mods have a separate compatibility adapter. */
public final class CeltiumNativeDraw {
    /** Bounded prints for refused skips: the census counts every one, the log names the first few reasons. */
    private static int refusalPrints;

    private CeltiumNativeDraw() {
    }

    /**
     * A native resource cannot fall back to GL: its opaque API identity is not a GL object or mirrored GL storage.
     * <p>
     * A refusal from a live Vulkan backend is not that invariant case. The interface contract's {@code false}
     * ("the caller offers the same vertices through {@code submitImmediateBatch} instead") is written for
     * backends that do not accept retained geometry at all; the Vulkan backend does, so a runtime refusal here
     * has no second route - re-offering the retained bytes enters the same submission and is refused for the
     * same reason. What a refusal may not do is throw: the one caller ({@code VertexBufferMixin}) sits inside
     * {@code VertexBuffer.drawArrays}, whose vanilla and Forge callers hold no catch, and the stall case's
     * history (issue #425) is exactly that propagation killing the client. So a refused draw is skipped -
     * the batch is missing from the picture, the degraded state the stall latch already promises - and
     * counted in {@link NativeDrawSkipCensus}, split by cause (DP-02/DP-03). Only the no-Vulkan-backend
     * invariant still throws: there a loud failure beats a silently black screen, because no draw of any
     * kind is possible.
     */
    public static void vertexBuffer(NativeGeometryBuffer geometry, int mode) {
        if (!(CeltiumBackends.current() instanceof VulkanTerrainGpuBackend)) {
            throw new IllegalStateException("native VertexBuffer draw without an active Vulkan backend");
        }
        VulkanTerrainGpuBackend backend = (VulkanTerrainGpuBackend) CeltiumBackends.current();
        if (!backend.submitRetainedGeometry(geometry, mode)) {
            if (backend.celtium$isGpuStalled()) {
                // The stall latch is up (issue #425, item 416): the device is gone or stopped, no draw can be
                // recorded, and the renderer is rebuilt by the reload path, not by this call. The reporter's
                // second report was this entry throwing the latch's refusal into RenderGlobal.renderSky, which
                // killed the client three seconds after the loss it was surviving. A stalled backend's draws
                // are skipped - the batch is missing from the picture, which is the degraded state the latch
                // already promises - until the rebuild replaces the backend. DP-03: the skip is counted now,
                // in its own population - stalledGeometryDrops counts accepted batches dropped at flush,
                // which a submission-side refusal never reaches.
                NativeDrawSkipCensus.noteStalledSkip();
                return;
            }
            // DP-02: a live backend's refusal degrades to the same counted skip instead of throwing into
            // an uncaught caller.
            NativeDrawSkipCensus.noteRefusedSkip();
            if (refusalPrints < 3) {
                refusalPrints++;
                System.out.println("[Celtium] CELTIUM_NATIVE_DRAW_REFUSED reason=" + backend.lastRefusal()
                        + " (the draw is skipped and counted in nativeDrawSkips; a refusal no longer throws)");
            }
        }
    }
}
