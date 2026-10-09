package org.embeddedt.embeddium.impl.render.chunk.backend;

/**
 * Holds the immediate-draw target the Mixins forward to.
 * <p>
 * The Vulkan backend installs itself when it is created and removes itself when it is deleted; the Mixins read this
 * holder on every state call, so it must stay a plain volatile field rather than anything that allocates. When no
 * target is installed (no Vulkan backend, or it failed and fell back) the Mixins do nothing at all and vanilla keeps
 * drawing through GL.
 */
public final class ImmediateDrawTargets {
    private static volatile ImmediateDrawTarget current;

    private ImmediateDrawTargets() {
    }

    /** {@return the installed target, or {@code null} when nothing wants immediate batches} */
    public static ImmediateDrawTarget current() {
        return current;
    }

    /** Installs {@code target}, or clears the holder when it is {@code null}. */
    public static void install(ImmediateDrawTarget target) {
        current = target;
    }
}
