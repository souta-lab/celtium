package org.embeddedt.embeddium.impl.render.chunk.backend;

import java.util.List;

/**
 * The Vulkan terrain backend chosen for the current device, together with the reason for that choice.
 * <p>
 * Celtium renders terrain with Vulkan only. Selection checks the capability contract for the exact device that
 * OpenGL is running on (device/driver UUIDs, image and semaphore support, required usage and sample counts, and
 * required features) and, when the contract cannot be met, states every reason and stops rather than rendering the
 * terrain with OpenGL. Nothing here switches to another physical GPU implicitly.
 */
public final class TerrainBackendSelection {
    /** The native Vulkan terrain backend. */
    public static final String VULKAN = "vulkan";

    private final String name;
    private final boolean nativeTerrain;
    private final String reason;

    private TerrainBackendSelection(String name, boolean nativeTerrain, String reason) {
        this.name = name;
        this.nativeTerrain = nativeTerrain;
        this.reason = reason;
    }

    /**
     * Chooses the terrain backend for the device Celtium is currently running on.
     *
     * @param vulkan the Vulkan backend, or {@code null} when OpenGL must be retained
     * @param reasons why Vulkan was rejected; empty when it was selected
     */
    public static TerrainBackendSelection select(TerrainGpuBackend vulkan, List<String> reasons) {
        if (vulkan != null) {
            return new TerrainBackendSelection(vulkan.getName(), true,
                    "Vulkan device matched the OpenGL device UUID and the interop contract was satisfied");
        }
        if (reasons.isEmpty()) {
            reasons.add("no native terrain backend was offered for this device");
        }
        throw new IllegalStateException("Celtium requires a Vulkan terrain backend, but none could be created: "
                + String.join("; ", reasons));
    }

    /** The state before a device or renderer exists, e.g. for the client command and the log. */
    public static TerrainBackendSelection pending(String reason) {
        return new TerrainBackendSelection(VULKAN, false, reason);
    }

    public String getName() {
        return this.name;
    }

    /** Whether the terrain geometry is uploaded and drawn by Celtium's own Vulkan code rather than by OpenGL. */
    public boolean isNativeTerrain() {
        return this.nativeTerrain;
    }

    public String getReason() {
        return this.reason;
    }

    /** Single-line description for logs, debug output and client commands. */
    public String describe() {
        return this.name + " (" + this.reason + ")";
    }

    @Override
    public String toString() {
        return describe();
    }
}
