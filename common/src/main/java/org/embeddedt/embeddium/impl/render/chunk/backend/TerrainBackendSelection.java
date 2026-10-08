package org.embeddedt.embeddium.impl.render.chunk.backend;

import java.util.List;

/**
 * The terrain GPU backend chosen for the current GL device, together with the reason for that choice.
 * <p>
 * Selection is fail-closed. A configuration is only moved off the retained OpenGL backend once the capability
 * contract for that exact device (GL device/driver UUIDs, image and semaphore support, required usage and sample
 * counts, and required features) has been checked and the alternative backend implements every render pass in use.
 * Nothing here switches to another physical GPU implicitly, and every fallback states why it happened so it can be
 * reported to the user and asserted by a validation fixture.
 */
public final class TerrainBackendSelection {
    /** The retained backend: Celeritas' original OpenGL terrain renderer. */
    public static final String OPENGL = "opengl";

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
        return new TerrainBackendSelection(OPENGL, false, String.join("; ", reasons));
    }

    /** The fail-closed answer used before a device or renderer exists, e.g. for the client command and the log. */
    public static TerrainBackendSelection opengl(String reason) {
        return new TerrainBackendSelection(OPENGL, false, reason);
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
