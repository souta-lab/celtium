package org.embeddedt.embeddium.impl.render.chunk.backend;

/**
 * Holds the live terrain backend so the Mixins that replace Minecraft's GL drawing can reach it.
 * <p>
 * The backend installs itself when it is created and clears the holder when it is deleted. When the holder is empty
 * (no Vulkan backend, or it failed to initialise) the Mixins do nothing and vanilla keeps drawing through GL, which
 * is the fail-safe the compatibility layer relies on.
 */
public final class TerrainGpuBackends {
    private static volatile TerrainGpuBackend current;

    private TerrainGpuBackends() {
    }

    /** {@return the live backend, or {@code null} when there is none} */
    public static TerrainGpuBackend current() {
        return current;
    }

    public static void install(TerrainGpuBackend backend) {
        current = backend;
    }
}
