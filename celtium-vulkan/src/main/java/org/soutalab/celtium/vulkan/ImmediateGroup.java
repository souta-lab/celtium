package org.soutalab.celtium.vulkan;

/**
 * One accepted batch of immediate geometry, held from queueing through recording, submission and completion.
 * <p>
 * This class is the named form of the {@code Object[]} a group used to be: fifteen positional slots whose meaning lived
 * in comments at three construction sites and in the memory of every reader. The constructor names the eight fields a
 * group cannot exist without; the rest are assigned by name where they are produced, so the only positional contract
 * left is the one in this file.
 * <p>
 * The lifecycle is unchanged: {@code immediateGroups} holds the queue, {@code recordedImmediateGroups} the groups whose
 * draws have been recorded but not yet submitted, and {@code retiredImmediateGroups} the submitted ones whose meshes are
 * freed only after the fence says the GPU is done with them.
 */
public final class ImmediateGroup {

    /** The geometry: borrowed meshes (captured model groups) are owned by their lender and are not freed on retire. */
    public VulkanDynamicMesh mesh;
    /** Column-major projection matrix captured at accept time. */
    public float[] projection;
    /** Column-major model-view matrix captured at accept time. */
    public float[] modelView;
    /** The vertex format id the group was built with. */
    public int formatId;
    /** The GL texture id this group's draws sample; 0 when untextured. */
    public int texture;
    /** The pipeline-state key the group was accepted under. */
    public long stateKey;
    /** The decoded GL state (blend, depth, cull and friends) used to build the push block. */
    public int[] state;
    /** The render target this group was accepted for: a pass retires only its own target's groups. */
    public long target;

    /** Group colour for the push block; {@code null} means 1.0 - model-mesh groups carry none. */
    public float[] color;
    /**
     * The texture-environment constant when this group must draw through the GL_COMBINE interpolate the fixed
     * pipeline would have used - the hurt flash and the team outline are the callers. {@code RGBA}, {@code null}
     * when no combine was in effect. The push block encodes it as a negative alpha reference plus the RGB slots.
     */
    public float[] combineColor;
    /**
     * The GL texture matrix's 2D affine at accept time - {@code [a,b,c,d,tx,ty]} for {@code u'=a*u+c*v+tx,
     * v'=b*u+d*v+ty} - or {@code null} when the texture matrix was identity. The item glint and the renderer's
     * own texture-matrix effects ride this; it is captured per group because the matrix changes per draw while the
     * shared mesh does not.
     */
    public float[] texMatrix;
    /**
     * The fixed-function lighting payload for a lit group: twenty floats laid out as five vec4 - the two light
     * directions already rotated into model space, the scene ambient, and each light's diffuse. {@code null} for an
     * unlit group, which is also how the draw loop knows whether to write the extended push range.
     */
    public float[] lightParams;
    /**
     * The world light level this batch was offered under, as the light-map coordinate pair the game set for the draw
     * ({@code OpenGlHelper.setLightmapTextureCoords}), or {@code null} when none had been captured yet. It travels in
     * the lit ITEM push block at 208 and is sampled against the light map, which is the term that makes an entity dark
     * at night; captured per group for the same reason as the fog, because it changes for every entity.
     */
    public float[] lightmapCoords;
    /**
     * The fixed-function fog this batch was offered under: eight floats {@code {selector, density, start, end, r, g, b,
     * weight}} in the push block's own layout, or {@code null} when fog was off. Captured per group rather than read at
     * flush time because {@code RenderGlobal.renderSky} enables fog for the sky dome and disables it again for the sun
     * and the moon inside one pass.
     */
    public float[] fog;
    /** Blend factors when the group needs them for the pipeline selection. */
    public int blendSrc;
    public int blendDst;
    /** The pipeline + descriptor chosen when the group was closed. */
    public PreparedImmediate prepared;
    /** How many OpenGL draw calls this group stands for: every counter that reports work counts batches. */
    public int batches;
    /** Viewport values captured at accept time for off-target passes. */
    public int[] viewportValues;
    /**
     * The game's clip rectangle and its enable bit, captured when this group was opened.
     * <p>
     * The scissor is command state and it is read where the group's rectangle becomes the Vulkan scissor - but it was
     * read from the *live* stub record at that point, so a group that spans a scissor change (the F3 overlay's list,
     * a scrolling panel) had its last rectangle applied to every batch in it. Captured here for the same reason the
     * fog and the state are: at group open the live state is the batch's own.
     */
    public int[] scissorBox;
    /** Whether the clip was enabled when this group was opened. */
    public boolean scissorEnabled;

    /**
     * GL's polygon offset, captured when this group was opened.
     * <p>
     * The draw site used to read the live stub record, but vanilla brackets the break overlay with
     * {@code preRenderDamagedBlocks}/{@code postRenderDamagedBlocks} (RenderGlobal:1914-1925): the offset is enabled
     * for the overlay's own draw calls and disabled again immediately after, so a group holding those batches but
     * flushed later read the disabled state and got no bias - the accept-vs-flush class BH-04/BH-05 fixed for the
     * colour and the lighting.
     */
    public float polygonOffsetFactor;
    public float polygonOffsetUnits;
    public boolean polygonOffsetEnabled;
    /** Frame and target-generation stamp: groups of a destroyed frame target must be retired, not drawn. */
    public GroupStamp stamp;
    /**
     * The submission sequence that must be confirmed before this group's mesh may be freed: tagged when the group is
     * retired with the highest submission that could still name its arena bytes. 0 means nothing in flight reads
     * them (the group never reached a submission) and the mesh may go immediately.
     */
    public long retireSequence;

    public ImmediateGroup(VulkanDynamicMesh mesh, float[] projection, float[] modelView, int formatId, int texture,
            long stateKey, int[] state, long target) {
        this.mesh = mesh;
        this.projection = projection;
        this.modelView = modelView;
        this.formatId = formatId;
        this.texture = texture;
        this.stateKey = stateKey;
        this.state = state;
        this.target = target;
    }

    /**
     * Whether a retired group is recycled for the next accept instead of garbage-collected.
     * <p>
     * Off by default: the group's lifetime spans three lists (recorded, immediate, retired) and a recycled instance
     * whose mutable fields were not fully cleared draws the stale batch it last carried. Opt-in with
     * {@code -Dceltium.vulkan.groupPool=true}; the constructor path is untouched, so off is byte-for-byte the old
     * behaviour.
     */
    private static final boolean POOL = Boolean.getBoolean("celtium.vulkan.groupPool");
    private static final java.util.ArrayDeque<ImmediateGroup> FREELIST =
            new java.util.ArrayDeque<ImmediateGroup>();
    /** Pool cap: beyond it the garbage collector takes the extra instances, so a frame spike cannot grow it forever. */
    private static final int FREELIST_CAP = 8192;
    static long poolRecycled;
    static long poolCreated;
    static long poolReused;

    /**
     * {@return a group initialised as if freshly constructed}
     * <p>
     * With the pool on, a retired instance is reinitialised through {@link #reset} rather than allocated. Every field
     * the constructor or the accept path writes is written again - the whole point of the explicit list - so a reused
     * group carries no trace of the batch it last held.
     */
    public static ImmediateGroup obtain(VulkanDynamicMesh mesh, float[] projection, float[] modelView, int formatId,
            int texture, long stateKey, int[] state, long target) {
        if (!POOL) {
            return new ImmediateGroup(mesh, projection, modelView, formatId, texture, stateKey, state, target);
        }
        ImmediateGroup group = FREELIST.poll();
        if (group == null) {
            poolCreated++;
            return new ImmediateGroup(mesh, projection, modelView, formatId, texture, stateKey, state, target);
        }
        poolReused++;
        group.reset(mesh, projection, modelView, formatId, texture, stateKey, state, target);
        return group;
    }

    /**
     * Returns a retired group to the freelist, clearing the references that would retain its mesh and stamp.
     * A no-op with the pool off, so the death sites do not need their own flag check.
     */
    public static void recycle(ImmediateGroup group) {
        if (!POOL || group == null || FREELIST.size() >= FREELIST_CAP) {
            return;
        }
        group.clearForReuse();
        poolRecycled++;
        FREELIST.add(group);
    }

    /**
     * Reinitialises the eight constructor fields and clears every field the accept path writes, so a recycled group
     * is indistinguishable from a fresh one. If a field is added to the class it must be reset here - the list is the
     * whole safety argument, and it is reviewed beside the field it protects.
     */
    private void reset(VulkanDynamicMesh mesh, float[] projection, float[] modelView, int formatId, int texture,
            long stateKey, int[] state, long target) {
        this.mesh = mesh;
        this.projection = projection;
        this.modelView = modelView;
        this.formatId = formatId;
        this.texture = texture;
        this.stateKey = stateKey;
        this.state = state;
        this.target = target;
        clearForReuse();
    }

    /** Clears the fields the accept path may set after construction; {@link #reset} and {@link #recycle} share it. */
    private void clearForReuse() {
        this.color = null;
        this.combineColor = null;
        this.texMatrix = null;
        this.lightParams = null;
        this.lightmapCoords = null;
        this.fog = null;
        this.blendSrc = 0;
        this.blendDst = 0;
        this.prepared = null;
        this.batches = 0;
        this.viewportValues = null;
        this.scissorBox = null;
        this.scissorEnabled = false;
        this.polygonOffsetFactor = 0.0f;
        this.polygonOffsetUnits = 0.0f;
        this.polygonOffsetEnabled = false;
        this.stamp = null;
        this.retireSequence = 0L;
    }
}

/**
 * The frame, target generation and world a group was accepted under.
 * <p>
 * A frame target replacement destroys the render pass and pipeline a stale group was built for, so drawing it would
 * bind the wrong thing; {@code targetGeneration} lets the draw side retire such a group instead. {@code meshBorrowed}
 * keeps the retire path from freeing a mesh that belongs to a lender (captured model meshes are drawn every frame).
 * <p>
 * {@code worldEpoch} is the same idea one lifetime up: a queued group can outlive its world, and the world teardown
 * releases the model meshes that a captured model group's mesh is borrowed from. {@code targetGeneration} says nothing
 * about that, and waiting for GPU idle ends GPU-side use rather than CPU-side ownership, so a group whose world is
 * gone is retired without being drawn.
 */
final class GroupStamp {
    final long frameId;
    final int targetGeneration;
    /** {@code true} when the mesh belongs to someone else and must not be freed when the group retires. */
    final boolean meshBorrowed;
    /** The world the group was accepted in; a group from an older world must never be drawn. */
    final int worldEpoch;

    GroupStamp(long frameId, int targetGeneration, boolean meshBorrowed, int worldEpoch) {
        this.frameId = frameId;
        this.targetGeneration = targetGeneration;
        this.meshBorrowed = meshBorrowed;
        this.worldEpoch = worldEpoch;
    }
}

/** The pipeline and descriptor a closed group was prepared to draw with. */
final class PreparedImmediate {
    final Object pipeline;
    final long descriptor;
    /**
     * The sampled wrapper's {@code key()} (view + generation) at prepare time, or 0 for an untextured group.
     * <p>
     * A group can outlive the texture it was prepared with: the wrapper is retired when the renderer replaces the image
     * (the measured case is {@code glTexture=22} unpublished at frame 0, moments after the group was enqueued), and the
     * descriptor then names a view that has been destroyed. Drawing it is not a missing batch but a device-lost class
     * failure, so the draw site compares this key against the registry's current wrapper and re-prepares the descriptor
     * when it has moved.
     */
    final long textureKey;

    PreparedImmediate(Object pipeline, long descriptor) {
        this(pipeline, descriptor, 0L);
    }

    PreparedImmediate(Object pipeline, long descriptor, long textureKey) {
        this.pipeline = pipeline;
        this.descriptor = descriptor;
        this.textureKey = textureKey;
    }
}
