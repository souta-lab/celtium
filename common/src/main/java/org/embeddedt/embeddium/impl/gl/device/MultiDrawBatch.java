package org.embeddedt.embeddium.impl.gl.device;

import org.embeddedt.embeddium.impl.gl.tessellation.GlPrimitiveType;
import org.embeddedt.embeddium.impl.gl.tessellation.GlTessellation;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;

/**
 * A fixed-size queue for building a draw-command list, usable with either direct or indirect multidraw.
 * Concrete subclasses own the storage format the underlying GL call expects, and how to execute it.
 */
public abstract class MultiDrawBatch {
    public static final int MAX_COMMAND_COUNT = (ModelQuadFacing.COUNT * RenderRegion.REGION_SIZE) + 1;

    private final int capacity;

    protected int size;

    protected int maxElementCount;

    protected MultiDrawBatch(int capacity) {
        // Always allow room for one extra command, to allow branchless tricks
        this.capacity = capacity + 1;
    }

    public final int size() {
        return this.size;
    }

    public final int capacity() {
        return this.capacity;
    }

    public void clear() {
        this.size = 0;
        this.maxElementCount = 0;
    }

    public abstract void delete();

    public final boolean isEmpty() {
        return this.size <= 0;
    }

    public final int getIndexBufferSize() {
        return this.maxElementCount;
    }

    /**
     * Marks the section the following draw commands belong to, called by the batch assembler once per
     * section before that section's commands are appended. The default retains nothing: most backends
     * never need to know which section a command came from. A backend that does (the Vulkan backend's
     * GPU-culling census, which culls per section rather than per region) overrides this and stamps
     * every appended command with the most recent value.
     * <p>
     * A command grown by {@link #mergeIntoLastCommand} keeps the section of the append that created it.
     * That is sound because the assembler only ever merges runs of one section into that section's own
     * last command; a merge never crosses a section boundary.
     *
     * @param localSectionIndex the section's index within its region
     *        ({@link org.embeddedt.embeddium.impl.render.chunk.LocalSectionIndex} space)
     */
    public void beginSection(int localSectionIndex) {
    }

    /**
     * Appends one draw command to the batch. If {@code elementCount} is 0, the command is discarded
     * and {@link #size()} does not advance.
     */
    public abstract void appendDrawCommand(int baseVertex, int elementCount, long elementPointer);

    /**
     * Adds {@code additionalElementCount} elements onto the most recently appended command, for
     * coalescing adjacent runs. Only valid when {@code size() > 0}.
     */
    public abstract void mergeIntoLastCommand(int additionalElementCount);

    /**
     * Must be called once assembly of the batch is complete, before the batch is {@link #execute}d for the
     * first time (possibly repeatedly, across frames, if cached).
     */
    public abstract void upload(CommandList commandList);

    /**
     * Issues the multidraw call for this batch against the currently bound {@code tessellation}.
     */
    public abstract void execute(CommandList commandList, GlTessellation tessellation, GlPrimitiveType primitiveType);
}
