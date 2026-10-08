package org.embeddedt.embeddium.impl.render.chunk.backend;

/**
 * The terrain GPU backend chosen for the current GL device, together with the reason for that choice.
 * <p>
 * Selection is fail-closed. A configuration is only moved off the retained OpenGL backend once the capability
 * contract for that exact device (GL device/driver UUIDs, image and semaphore support, required usage and sample
 * counts, and required features) has been checked. Nothing here switches to another physical GPU implicitly, and
 * every fallback states why it happened so it can be reported to the user and asserted by a validation fixture.
 */
public final class TerrainBackendSelection {
    /** The retained backend: Celeritas' original OpenGL terrain renderer. */
    public static final String OPENGL = "opengl";

    /** The native Vulkan terrain backend. Not implemented yet; never selected by this build. */
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
     * <p>
     * Native Vulkan terrain does not exist in this build, so the OpenGL backend is always retained. When the Vulkan
     * upload path lands, this method gains the per-device capability verdict as its input and the OpenGL result
     * becomes conditional; until then the previous behaviour is preserved exactly.
     */
    public static TerrainBackendSelection select() {
        return new TerrainBackendSelection(OPENGL, false,
                "native Vulkan terrain is not implemented in this build");
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
