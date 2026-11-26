package org.soutalab.celtium.vulkan.batch;

import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;
import org.soutalab.celtium.vulkan.TopologyIndexEmitter;

import java.nio.ByteBuffer;

/**
 * Stages non-quad immediate geometry onto the frame staging ring and names the draw that
 * issues it (Stage 5 stage 6 slice S6-a, entry pack section 3-2).
 *
 * <p>The quad population goes through {@link ImmediateBatcher}: its draws share the quad
 * index pattern, so a capture is only vertex bytes plus a plan entry. Every other GL
 * primitive mode has no shared pattern - its index stream is a function of the mode and the
 * vertex count - so this emitter is the population's other wing. It owns no Vulkan objects
 * and records no commands: it copies one group's vertices into the ring the batcher stages
 * into (the same {@link StagingRing}, in production the same {@code FrameLinearRing} slot,
 * so the emitter adds no buffer object of its own), writes the mode's index expansion beside
 * them for the modes that need one, and hands back the offsets the caller's draw binds.
 *
 * <p>The issue forms are the slicing design's, fixed here and pinned by the unit tests:
 * <ul>
 *   <li>{@code GL_TRIANGLES} and {@code GL_LINES} draw <b>non-indexed</b>
 *   ({@code vkCmdDraw}): their expansion is the identity sequence, so generating it into the
 *   ring would pay an index stream to say nothing. This is the completion of the expand
 *   record's reservation (the non-indexed draw form belongs to the emitter, not to a group's
 *   single indexed draw).</li>
 *   <li>{@code GL_TRIANGLE_STRIP}, {@code GL_TRIANGLE_FAN}, {@code GL_POLYGON},
 *   {@code GL_LINE_STRIP}, {@code GL_LINE_LOOP} and {@code GL_QUAD_STRIP} draw indexed,
 *   with {@link TopologyIndexEmitter#writeIndices} writing the expansion as uint32 indices
 *   directly beside the staged vertices - the same sequences the accept path used to expand
 *   into each group's own mesh.</li>
 * </ul>
 *
 * <p>Input contract (fixed by this slice): the group's vertex bytes as one direct buffer
 * whose remaining bytes are exactly {@code vertexCount * bytesPerVertex}, the vertex count,
 * the stride, and the group's single GL draw mode. One group carries exactly one mode - the
 * backend's group identity includes the mode, as it already did for lines through the state
 * key's line bit - so the mode is a property of the group, never a per-batch question the
 * emitter has to re-ask. {@code GL_QUADS} is refused: quads are the batcher's population,
 * and an emitter call for one is a routing defect, not a fallback case.
 *
 * <p>Threading: render thread only, like the batcher it shares the ring with.
 */
public final class NonQuadImmediateEmitter {

    /**
     * What one staged group draws with: the slot-relative offsets of its staged bytes in the
     * ring, and the counts its draw call carries. The caller binds the ring slot's buffer as
     * the vertex buffer at {@link #vertexOffset} (and, for an indexed emission, as the index
     * buffer at {@link #indexOffset}) and issues {@code vkCmdDraw} or
     * {@code vkCmdDrawIndexed} with {@code firstVertex}/{@code firstIndex} zero - the staged
     * indices are relative to the group's own first vertex, exactly as
     * {@link TopologyIndexEmitter} wrote them into the group's mesh before.
     */
    public static final class Emission {
        /** Slot-relative byte offset of the staged vertex bytes. */
        public final long vertexOffset;
        /** Slot-relative byte offset of the staged indices, or -1 for a non-indexed emission. */
        public final long indexOffset;
        /** Indices the draw carries; 0 for a non-indexed emission. */
        public final int indexCount;
        /** Vertices the draw carries. */
        public final int vertexCount;

        Emission(long vertexOffset, long indexOffset, int indexCount, int vertexCount) {
            this.vertexOffset = vertexOffset;
            this.indexOffset = indexOffset;
            this.indexCount = indexCount;
            this.vertexCount = vertexCount;
        }

        /** {@return whether this emission draws indexed}. */
        public boolean indexed() {
            return this.indexOffset >= 0L;
        }
    }

    private final StagingRing ring;
    /** Scratch the index expansion is composed in before it is copied into the ring, grown on demand. */
    private ByteBuffer indexScratch;

    public NonQuadImmediateEmitter(StagingRing ring) {
        if (ring == null) {
            throw new IllegalArgumentException("ring must not be null");
        }
        this.ring = ring;
    }

    /** {@return whether {@code drawMode} is one of this emitter's modes (anything but quads)}. */
    public static boolean isNonQuadMode(int drawMode) {
        return drawMode != GL11.GL_QUADS
                && TopologyIndexEmitter.indexCount(drawMode, 4) >= 0;
    }

    /**
     * {@return whether {@code drawMode} issues non-indexed}: the two modes whose expansion is
     * the identity sequence.
     */
    public static boolean isNonIndexedMode(int drawMode) {
        return drawMode == GL11.GL_TRIANGLES || drawMode == GL11.GL_LINES;
    }

    /**
     * {@return how many indices {@code drawMode}'s expansion of {@code vertexCount} vertices
     * produces}, or -1 when the renderer has no equivalent for the pair - the accept path's
     * own contract, delegated so the emitter and the acceptance can never disagree.
     */
    public static int indexCount(int drawMode, int vertexCount) {
        return TopologyIndexEmitter.indexCount(drawMode, vertexCount);
    }

    /**
     * Stages one non-quad group: copies its vertices into the ring and, for an indexed mode,
     * writes the mode's index expansion beside them.
     *
     * @param vertexData the group's vertex bytes; remaining bytes must be exactly
     *                   {@code vertexCount * bytesPerVertex}
     * @param drawMode the group's GL primitive mode; never {@code GL_QUADS}
     * @return the emission to draw with, or {@code null} when the ring had no room - nothing
     *         is drawn from a partial staging, and the caller falls back to a direct draw of
     *         the group's own mesh, so a full ring costs the staging, never the picture
     * @throws IllegalArgumentException when the mode is quads, has no expansion, or the
     *         vertex data does not match the declared shape
     */
    public Emission stage(ByteBuffer vertexData, int vertexCount, int bytesPerVertex, int drawMode) {
        if (drawMode == GL11.GL_QUADS) {
            throw new IllegalArgumentException("quads are the batcher's population, not the emitter's");
        }
        if (vertexCount <= 0 || bytesPerVertex <= 0) {
            throw new IllegalArgumentException("vertexCount " + vertexCount
                    + " bytesPerVertex " + bytesPerVertex);
        }
        int indices = isNonIndexedMode(drawMode) ? 0 : TopologyIndexEmitter.indexCount(drawMode, vertexCount);
        if (indices < 0) {
            throw new IllegalArgumentException("no index expansion for draw mode " + drawMode
                    + " at " + vertexCount + " vertices");
        }
        int vertexBytes = vertexCount * bytesPerVertex;
        if (vertexData.remaining() != vertexBytes) {
            throw new IllegalArgumentException("vertex data holds " + vertexData.remaining()
                    + " bytes, " + vertexCount + " vertices at " + bytesPerVertex
                    + " bytes/vertex need " + vertexBytes);
        }
        long vertexOffset = this.ring.allocate(vertexBytes, bytesPerVertex);
        if (vertexOffset < 0L) {
            return null;
        }
        this.ring.write(vertexOffset, vertexData);
        if (indices == 0) {
            return new Emission(vertexOffset, -1L, 0, vertexCount);
        }
        long indexOffset = this.ring.allocate(indices * 4, 4);
        if (indexOffset < 0L) {
            return null;
        }
        ByteBuffer scratch = indexScratchFor(indices);
        TopologyIndexEmitter.writeIndices(drawMode, vertexCount, 0, MemoryUtil.memAddress(scratch));
        ByteBuffer view = scratch.duplicate();
        view.position(0);
        view.limit(indices * 4);
        this.ring.write(indexOffset, view);
        return new Emission(vertexOffset, indexOffset, indices, vertexCount);
    }

    /** {@return a direct scratch buffer holding at least {@code indices} uint32 slots}. */
    private ByteBuffer indexScratchFor(int indices) {
        int bytes = indices * 4;
        if (this.indexScratch == null || this.indexScratch.capacity() < bytes) {
            int capacity = this.indexScratch == null ? Math.max(bytes, 4096)
                    : Math.max(bytes, this.indexScratch.capacity() * 2);
            this.indexScratch = ByteBuffer.allocateDirect(capacity);
        }
        return this.indexScratch;
    }
}
