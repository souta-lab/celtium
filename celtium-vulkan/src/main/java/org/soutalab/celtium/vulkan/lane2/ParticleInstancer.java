package org.soutalab.celtium.vulkan.lane2;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import org.soutalab.celtium.core.shader.NonTerrainPackTarget;

/**
 * The particle instancer (design V040-FINAL-PLAN section 4.2, stage S3-a item 4): particles
 * stop arriving as four fully expanded vertices each (112 bytes per particle in the vanilla
 * PARTICLE layout) and are written once as a {@value #INSTANCE_BYTES}-byte instance record;
 * the vertex shader expands the billboard from the record, the camera's right/up vectors and
 * the per-draw size.
 *
 * <p>Record layout (24 bytes, FFM {@link ValueLayout} offsets below):
 * <ul>
 *   <li>offset 0: position xyz (3 x f32) - the billboard centre in world space;</li>
 *   <li>offset 12: uv min corner (2 x f32) - the sprite's corner in the sheet; the shader
 *       adds the corner's share of the per-draw sprite-rect size;</li>
 *   <li>offset 20: colour RGBA8 (1 x u32) - the particle's colour with its light already
 *       folded in by the writer, which is what the lit particle path computes anyway; the
 *       instanced form has no per-instance lightmap element, by design.</li>
 * </ul>
 *
 * <p>Batching constraint, stated rather than hidden: one instanced draw covers particles
 * that share a texture sheet and a size, because the sprite-rect size and the particle size
 * are per-draw uniforms of the expansion. A particle system whose size animates per particle
 * quantises its size into the draw key, the same way the immediate path already groups by
 * texture.
 *
 * <p>The interface 2 receptacle is built in from the start ({@link #setPackTarget}): while
 * a pack stage is active the instancer's draws resolve through the target's
 * {@code gbuffers_particles} / {@code shadow} programs; with no target the resolution is
 * never consulted and the native instanced pipeline serves.
 *
 * <p>Threading: render thread only.
 */
public final class ParticleInstancer implements AutoCloseable {

    /** Bytes per particle instance record. */
    public static final int INSTANCE_BYTES = 24;
    /** Byte offset of the position (3 x f32). */
    public static final int OFFSET_POSITION = 0;
    /** Byte offset of the uv min corner (2 x f32). */
    public static final int OFFSET_UV = 12;
    /** Byte offset of the packed colour (RGBA8 in one u32, little-endian R in the low byte). */
    public static final int OFFSET_COLOR = 20;

    private final Arena arena;
    private final MemorySegment instances;
    private final int capacity;
    private int count;
    private NonTerrainPackTarget packTarget;

    public ParticleInstancer(int capacityParticles) {
        if (capacityParticles <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacityParticles;
        this.arena = Arena.ofConfined();
        this.instances = this.arena.allocate((long) capacityParticles * INSTANCE_BYTES, 4);
    }

    public int capacity() {
        return this.capacity;
    }

    public int count() {
        return this.count;
    }

    /** Starts a new frame's write: the count resets, the storage is reused. */
    public void beginFrame() {
        this.count = 0;
    }

    /**
     * Appends one particle. Returns false when the instancer is full - the caller keeps the
     * particle on the immediate path rather than losing it.
     */
    public boolean add(float x, float y, float z, float u, float v, int rgba) {
        if (this.count >= this.capacity) {
            return false;
        }
        write(this.instances, (long) this.count * INSTANCE_BYTES, x, y, z, u, v, rgba);
        this.count++;
        return true;
    }

    /** The instance storage, {@link #count()} records long, for the upload path. */
    public MemorySegment segment() {
        return this.instances;
    }

    /** Writes one record at a byte offset; the layout the shader reads, in one place. */
    public static void write(MemorySegment out, long offset,
                             float x, float y, float z, float u, float v, int rgba) {
        out.set(ValueLayout.JAVA_FLOAT, offset + OFFSET_POSITION, x);
        out.set(ValueLayout.JAVA_FLOAT, offset + OFFSET_POSITION + 4, y);
        out.set(ValueLayout.JAVA_FLOAT, offset + OFFSET_POSITION + 8, z);
        out.set(ValueLayout.JAVA_FLOAT, offset + OFFSET_UV, u);
        out.set(ValueLayout.JAVA_FLOAT, offset + OFFSET_UV + 4, v);
        out.set(ValueLayout.JAVA_INT, offset + OFFSET_COLOR, rgba);
    }

    /**
     * The CPU mirror of the shader's billboard expansion, kept so the expansion has exactly
     * one definition the tests can pin: corner {@code corner} (0..3, in the order
     * (0,0), (1,0), (1,1), (0,1)) of the quad around centre {@code (x, y, z)}, expanded
     * along the camera's right and up vectors by {@code size}.
     */
    public static float[] billboardCorner(float x, float y, float z,
                                          float rightX, float rightY, float rightZ,
                                          float upX, float upY, float upZ,
                                          float size, int corner) {
        float sx = (corner == 1 || corner == 2) ? 0.5f : -0.5f;
        float sy = (corner >= 2) ? 0.5f : -0.5f;
        return new float[]{
                x + (rightX * sx + upX * sy) * size,
                y + (rightY * sx + upY * sy) * size,
                z + (rightZ * sx + upZ * sy) * size};
    }

    // -- interface 2 receptacle -----------------------------------------------------

    /** Sets the active pack target, or {@code null} when no pack stage is active. */
    public void setPackTarget(NonTerrainPackTarget target) {
        this.packTarget = target;
    }

    public NonTerrainPackTarget packTarget() {
        return this.packTarget;
    }

    /**
     * The pipeline a pack program resolves to for the instancer's draws:
     * {@link ImmediateBatcher}'s contract - the target's handle,
     * {@link NonTerrainPackTarget#NO_PIPELINE} for a program the stage omits, or
     * {@link org.soutalab.celtium.vulkan.batch.ImmediateBatcher#PACK_NATIVE} with no pack.
     */
    public long packPipeline(String programName) {
        NonTerrainPackTarget target = this.packTarget;
        return target == null
                ? org.soutalab.celtium.vulkan.batch.ImmediateBatcher.PACK_NATIVE
                : target.pipelineHandle(programName);
    }

    @Override
    public void close() {
        this.arena.close();
    }
}
