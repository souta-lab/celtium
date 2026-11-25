package org.soutalab.celtium.vulkan.compat;

/**
 * What a {@link FrameExtension} sees at frame start: the frame's camera and index, and the one sink
 * an extension may contribute through - this frame's dynamic lights, written into the Global Frame
 * UBO's reserved region by the pipeline that owns the buffer. The sink is the whole surface on
 * purpose: an extension hands over plain data before any pass records, and the pipeline decides where
 * it lives. There is no handle here to a command buffer, a descriptor, or a draw, because an
 * extension that could record or intercept draws would be a second, unledgered connection surface -
 * see {@link RenderExtensionRegistry}.
 */
public interface FrameExtensionContext {

    /** {@return the frame's camera world x}. */
    double cameraX();

    /** {@return the frame's camera world y}. */
    double cameraY();

    /** {@return the frame's camera world z}. */
    double cameraZ();

    /** {@return the index of the frame being started}, for extensions that pace themselves by it. */
    long frameIndex();

    /**
     * Contributes this frame's dynamic light sources: {@code count} sources of four floats each
     * (xyz camera-relative, w the 0..15 level), gathered around the camera this context reports. The
     * pipeline writes them into the frame block's reserved region ({@link FrameLights}); a later
     * contribution in the same frame replaces an earlier one.
     */
    void writeLights(float[] sources, int count);
}
