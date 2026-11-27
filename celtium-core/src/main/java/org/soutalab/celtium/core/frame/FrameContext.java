package org.soutalab.celtium.core.frame;

/**
 * The per-frame facts every native interface call is allowed to assume: a monotonically increasing
 * frame id (the same id {@code PackTerrainDriver.prepareMainView} is handed), the world tick the
 * frame belongs to and the partial-tick interpolation factor. Immutable by construction - a stage
 * that needs a different frame asks the frame coordinator, never mutates this.
 */
public record FrameContext(long frameId, int tickCount, float partialTicks) {

    public FrameContext {
        if (frameId < 0L) {
            throw new IllegalArgumentException("frameId must not be negative");
        }
        if (partialTicks < 0.0f || partialTicks >= 1.0f) {
            throw new IllegalArgumentException("partialTicks must be in [0, 1): " + partialTicks);
        }
    }
}
