package org.soutalab.celtium.core.shader;

import java.lang.foreign.MemorySegment;
import java.util.Optional;

import org.soutalab.celtium.core.frame.FrameContext;

/**
 * Interface 1, the interpreter's side (design V040-FINAL-PLAN section 2.3): everything the terrain
 * lane may know about a loaded shader pack. The pack compiler produces one program set per loaded
 * pack; the terrain driver consumes it through {@link PackTerrainDriver} without ever seeing a GL
 * program name, a GLSM framebuffer or a supplier-side callback shape.
 */
public interface TerrainPackProgramSet extends AutoCloseable {

    /** Whether the pack defines any shadow programs (and so a shadow stage exists at all). */
    boolean hasShadowPass();

    /** The program for a pass kind, or empty when the pack does not define that pass. */
    Optional<TerrainStageProgram> programFor(TerrainPassKind kind);

    /** What the pack's terrain programs read per vertex; drives auxiliary-column generation. */
    TerrainVertexRequirements vertexRequirements();

    /**
     * Fills this frame's pack uniform block: the built-in uniforms and the pack's custom
     * ({@code stareval}) uniforms, packed once into the single std140 block the plan laid out.
     * Called once per frame by the frame executor - the per-draw {@code glUniform*} stream of the
     * GL-shaped design does not exist here.
     *
     * @param packUniformUbo the writable block, sized by the plan's {@code PackUniformLayout}
     * @param ctx            the frame being prepared
     */
    void updateFrameUniforms(MemorySegment packUniformUbo, FrameContext ctx);

    @Override
    void close();
}
