package org.soutalab.celtium.core.glcompat;

import java.util.Objects;

import org.soutalab.celtium.core.state.RenderStateKey;

/**
 * The complete state one direct-GL draw is submitted under (design section 2.3, interface 3): the
 * fixed-function key from the shared {@link RenderStateKey}, the converted program's handle, the
 * primitive topology, and the render target the draw lands on (a HAL target id; the swapchain is
 * id 0 of the registry). The sink hashes this state to its pipeline cache entry - two draws with
 * equal state share a pipeline.
 */
public record DirectGlDrawState(
        RenderStateKey renderState,
        long programHandle,
        PrimitiveTopology topology,
        int renderTargetId) {

    public DirectGlDrawState {
        Objects.requireNonNull(renderState, "renderState");
        Objects.requireNonNull(topology, "topology");
        if (renderTargetId < 0) {
            throw new IllegalArgumentException("renderTargetId must not be negative");
        }
    }
}
