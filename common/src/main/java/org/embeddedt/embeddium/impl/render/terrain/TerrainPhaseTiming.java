package org.embeddedt.embeddium.impl.render.terrain;

/**
 * A hook the renderer implementation installs to time the phases of {@code setupTerrain}.
 * <p>
 * {@link SimpleWorldRenderer} lives in the shared common module and cannot see the Vulkan backend's
 * {@code FramePassTimings}; the sink keeps the access rule and still lets a leg say which step of the largest
 * bracket in the world pass costs the time. Nothing is timed until a sink is installed, so a GL run pays a null
 * check per phase and no more.
 */
public final class TerrainPhaseTiming {
    /** The actual timing implementation (the Vulkan backend's pass timings). */
    public interface Sink {
        void begin(String name);

        void end(String name);
    }

    private static volatile Sink sink;

    private TerrainPhaseTiming() {
    }

    /** Installs the sink; {@code null} removes it. */
    public static void install(Sink timingSink) {
        sink = timingSink;
    }

    public static void begin(String name) {
        Sink current = sink;
        if (current != null) {
            current.begin(name);
        }
    }

    public static void end(String name) {
        Sink current = sink;
        if (current != null) {
            current.end(name);
        }
    }
}
