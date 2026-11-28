package org.soutalab.celtium.core.state;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * The single source of truth for the lightweight fixed-function state the non-terrain lane and the
 * direct-GL converter share (design V040-FINAL-PLAN section 2.3, interface 3 contract): the
 * {@link RenderStateKey}, the bound texture id, the display list currently being compiled, and the
 * matrix stack. The stack lives off-heap in an
 * FFM segment because the lanes read it while recording - the same 16 floats the pipeline's push
 * constants carry, with no per-draw copy out of a Java array.
 * <p>
 * Ownership rules (the contract's four clauses, in operational form):
 * <ol>
 *   <li>The non-terrain lane reads only this register; it never calls the GL converter.</li>
 *   <li>When a mod's raw GL call enters the converter lane, the dispatcher syncs the register's
 *       current values in ({@link #markLane3Entered()}), and on the way back marks the register
 *       dirty ({@link #markLane3Returned()}), because the mod may have changed GL state behind
 *       the register's back.</li>
 *   <li>Texture and framebuffer ids come from the HAL's single registry; this class only holds
 *       the currently bound texture id, it never allocates ids.</li>
 * </ol>
 * Not thread-safe: state is a render-thread concept.
 */
public final class LightStateRegister implements AutoCloseable {

    /** Matrix stack depth: the fixed-function stacks the converter emulates go this deep at most. */
    public static final int MAX_MATRIX_DEPTH = 32;

    /** Floats per matrix: a 4x4, column-major, the order the push constants carry. */
    public static final int MATRIX_FLOATS = 16;

    private final Arena arena;
    private final MemorySegment matrixStack;
    private int depth;
    private RenderStateKey key = RenderStateKey.DEFAULT;
    private int boundTextureId;
    private int compilingListId;
    private int activeProgramId;
    private boolean lane3Dirty;

    public LightStateRegister() {
        this.arena = Arena.ofConfined();
        this.matrixStack = arena.allocate(
                (long) MAX_MATRIX_DEPTH * MATRIX_FLOATS * Float.BYTES, Float.BYTES);
        writeIdentity(0);
        this.depth = 1;
    }

    // -- render state key -------------------------------------------------

    public RenderStateKey key() {
        return key;
    }

    public void setKey(RenderStateKey key) {
        this.key = Objects.requireNonNull(key, "key");
    }

    // -- bound texture ------------------------------------------------------

    /** The HAL texture-table id currently bound, or 0 for none. */
    public int boundTextureId() {
        return boundTextureId;
    }

    public void setBoundTextureId(int textureId) {
        if (textureId < 0) {
            throw new IllegalArgumentException("textureId must not be negative");
        }
        this.boundTextureId = textureId;
    }

    // -- display list compilation --------------------------------------------

    /**
     * The display list currently being compiled, or 0 when none is. Written only by the
     * display-list open/close owners (the vanilla/Forge capture path and the converter's
     * display-list manager), which publish the new value synchronously at glNewList /
     * glEndList; the non-terrain lane reads it to keep its hands off batches that belong
     * to a list being compiled. It is deliberately not part of {@link RenderStateKey}:
     * a list id is an arbitrary positive integer, not pipeline state, and the lane-3
     * register sync does not carry it - a compilation scope is owned by glNewList /
     * glEndList alone, including across converter excursions inside the list.
     */
    public int compilingListId() {
        return compilingListId;
    }

    public void setCompilingListId(int listId) {
        if (listId < 0) {
            throw new IllegalArgumentException("listId must not be negative");
        }
        this.compilingListId = listId;
    }

    /**
     * The guest program currently bound by {@code glUseProgram} on the Lane 3 side:
     * {@code 0} is the fixed-function pipeline, {@code > 0} a custom shader program.
     * Lane 2 reads this only as the Lane 2 / Lane 3 boundary gate (a batch offered with
     * a program bound belongs to Lane 3); Lane 3 publishes it (ruling item 21). It is
     * deliberately not part of {@code RenderStateKey}: a program id is not pipeline
     * state of the fixed-function key space.
     */
    public int activeProgramId() {
        return activeProgramId;
    }

    public void setActiveProgramId(int programId) {
        if (programId < 0) {
            throw new IllegalArgumentException("programId must not be negative");
        }
        this.activeProgramId = programId;
    }

    // -- matrix stack ---------------------------------------------------------

    public int matrixDepth() {
        return depth;
    }

    /** Pushes a copy of the current top matrix. */
    public void pushMatrix() {
        if (depth >= MAX_MATRIX_DEPTH) {
            throw new IllegalStateException("matrix stack overflow at depth " + MAX_MATRIX_DEPTH);
        }
        MemorySegment.copy(matrixStack, slotOffset(depth - 1), matrixStack, slotOffset(depth),
                (long) MATRIX_FLOATS * Float.BYTES);
        depth++;
    }

    /** Pops the top matrix; the bottom (identity-born) matrix cannot be popped. */
    public void popMatrix() {
        if (depth <= 1) {
            throw new IllegalStateException("matrix stack underflow: the base matrix cannot be popped");
        }
        depth--;
    }

    /** Replaces the top matrix with the identity. */
    public void loadIdentity() {
        writeIdentity(depth - 1);
    }

    /** Replaces the top matrix with the given 16 floats (column-major). */
    public void setCurrentMatrix(float[] matrix) {
        if (matrix == null || matrix.length != MATRIX_FLOATS) {
            throw new IllegalArgumentException("a matrix is exactly " + MATRIX_FLOATS + " floats");
        }
        MemorySegment.copy(matrix, 0, matrixStack, ValueLayout.JAVA_FLOAT, slotOffset(depth - 1),
                MATRIX_FLOATS);
    }

    /** Copies the top matrix into {@code out} (16 floats, column-major). */
    public void currentMatrix(float[] out) {
        if (out == null || out.length != MATRIX_FLOATS) {
            throw new IllegalArgumentException("out must hold exactly " + MATRIX_FLOATS + " floats");
        }
        MemorySegment.copy(matrixStack, ValueLayout.JAVA_FLOAT, slotOffset(depth - 1), out, 0,
                MATRIX_FLOATS);
    }

    /** The off-heap segment holding the whole stack (the lanes' zero-copy read path). */
    public MemorySegment matrixStackSegment() {
        return matrixStack;
    }

    /** Byte offset of the current top matrix inside {@link #matrixStackSegment()}. */
    public long currentMatrixOffset() {
        return slotOffset(depth - 1);
    }

    private long slotOffset(int slot) {
        return (long) slot * MATRIX_FLOATS * Float.BYTES;
    }

    private void writeIdentity(int slot) {
        long base = slotOffset(slot);
        for (int i = 0; i < MATRIX_FLOATS; i++) {
            matrixStack.set(ValueLayout.JAVA_FLOAT, base + (long) i * Float.BYTES,
                    (i % 5 == 0) ? 1.0f : 0.0f);
        }
    }

    // -- lane 3 synchronisation -------------------------------------------------

    /**
     * Marks the start of a converter-lane excursion: the dispatcher has synced this register's
     * values into the converter, so until {@link #markLane3Returned()} the converter's view is
     * authoritative and this register is not stale.
     */
    public void markLane3Entered() {
        this.lane3Dirty = false;
    }

    /**
     * Marks the return from a converter-lane excursion: GL state may have changed, so readers must
     * treat the register as dirty (re-read / re-sync) until the next {@link #markLane3Entered()}.
     */
    public void markLane3Returned() {
        this.lane3Dirty = true;
    }

    public boolean isLane3Dirty() {
        return lane3Dirty;
    }

    @Override
    public void close() {
        arena.close();
    }
}
