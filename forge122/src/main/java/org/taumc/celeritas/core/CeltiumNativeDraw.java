package org.taumc.celeritas.core;

import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends;
import org.soutalab.celtium.vulkan.NativeGeometryBuffer;
import org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend;

/** Native Minecraft/Forge draw entry. Raw GL calls made by mods have a separate compatibility adapter. */
public final class CeltiumNativeDraw {
    private CeltiumNativeDraw() {
    }

    /** A native resource cannot fall back to GL: its opaque API identity is not a GL object or mirrored GL storage. */
    public static void vertexBuffer(NativeGeometryBuffer geometry, int mode) {
        if (!(TerrainGpuBackends.current() instanceof VulkanTerrainGpuBackend)) {
            throw new IllegalStateException("native VertexBuffer draw without an active Vulkan backend");
        }
        VulkanTerrainGpuBackend backend = (VulkanTerrainGpuBackend) TerrainGpuBackends.current();
        if (!backend.submitRetainedGeometry(geometry, mode)) {
            if (backend.celtium$isGpuStalled()) {
                // The stall latch is up (issue #425, item 416): the device is gone or stopped, no draw can be
                // recorded, and the renderer is rebuilt by the reload path, not by this call. The reporter's
                // second report was this entry throwing the latch's refusal into RenderGlobal.renderSky, which
                // killed the client three seconds after the loss it was surviving. A stalled backend's draws
                // are skipped - the batch is missing from the picture, which is the degraded state the latch
                // already promises - until the rebuild replaces the backend.
                return;
            }
            throw new IllegalStateException("native VertexBuffer draw refused: " + backend.lastRefusal());
        }
    }
}
