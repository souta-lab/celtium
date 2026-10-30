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
            throw new IllegalStateException("native VertexBuffer draw refused: " + backend.lastRefusal());
        }
    }
}
