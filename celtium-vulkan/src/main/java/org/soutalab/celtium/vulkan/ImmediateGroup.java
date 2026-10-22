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
    /** Retained geometry generation held until this group is discarded or its submission completes. */
    LeasedResource.Lease<?> geometryLease;
    /** Column-major projection matrix captured at accept time. */
    public float[] projection;
    /** Column-major model-view matrix captured at accept time. */
    public float[] modelView;
    /** The vertex format id the group was built with. */
    public int formatId;
    /** The GL texture id this group's draws sample; 0 when untextured. */
    public int texture;

    /**
     * The wrapper {@link #texture} resolved to when the batch was accepted, and the registry's mutation count at that
     * moment. The draw site checks whether the registry moved before re-resolving: steady-state frames resolve nothing,
     * which is 159 map lookups a frame measured in the window recording's JFR samples.
     */
    public VulkanGlTexture textureImage;
    public long textureMutations;
    /** The pipeline-state key the group was accepted under. */
    public long stateKey;
    /** The decoded GL state (blend, depth, cull and friends) used to build the push block. */
    /**
     * This group's own copy of the captured draw state.
     * <p>
     * Owned, not borrowed: the accept path captures into one reusable scratch array, and a group that kept the
     * reference was overwritten by every later capture - so every pending model group drew with whichever state the
     * last one left behind. The model path hands its capture straight in ({@code celtium$enqueueModelMesh}), which is
     * exactly the shape that aliased. Copied on assignment instead, so a pooled group allocates nothing after its
     * first use and no group can see another's state.
     */
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
     * {@return a hash of the group's viewport and scissor state}.
     * <p>
     * Two groups whose rectangles match can share one {@code vkCmdSetViewport}/{@code vkCmdSetScissor}, which is what
     * decides whether a run of groups is one command in the merge being measured.
     */
    public long viewportHash() {
        int[] viewport = this.viewportValues;
        long hash = 1469598103934665603L;
        if (viewport != null) {
            for (int i = 0; i < Math.min(4, viewport.length); i++) {
                hash = (hash ^ viewport[i]) * 1099511628211L;
            }
        }
        int[] clip = this.scissorBox;
        if (clip != null && this.scissorEnabled) {
            for (int i = 0; i < Math.min(4, clip.length); i++) {
                hash = (hash ^ clip[i]) * 1099511628211L;
            }
        }
        return hash;
    }

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
     * {@code glClear}s the game issued against this group's target after this group was queued.
     * <p>
     * A clear is an ordered command, not a state: the game's own ordering puts it after the draws already queued and
     * before the ones that follow, and the deferred model only holds that order if the clear rides the queue with the
     * draws. Attaching it to the last queued group of the same target places it at exactly that position when the
     * target's pass drains. A group that is retired rather than drawn still carries its clears into the pass - the
     * clear erases earlier content even when the group it followed is itself dropped.
     */
    public java.util.List<QueuedClear> postClears;
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
        this.state = copyState(state);
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
    static final boolean POOL = Boolean.getBoolean("celtium.vulkan.groupPool");
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
     * Releases a retired group's geometry lease, then optionally returns the group to the freelist.
     * Resource lifetime is independent of whether object pooling is enabled or the freelist is full.
     */
    public static void recycle(ImmediateGroup group) {
        if (group == null) {
            return;
        }
        if (group.geometryLease != null) {
            group.geometryLease.close();
            group.geometryLease = null;
        }
        if (!POOL || FREELIST.size() >= FREELIST_CAP) {
            return;
        }
        group.clearForReuse();
        // A group on the freelist can survive for the entire game session. These are not
        // constructor-time-only references: keeping them pins old-world mesh buffers, matrix
        // snapshots and GL state after their owners are gone, even though reset() will replace
        // every field at the next obtain(). Do not retain an old world via a pooled group.
        group.mesh = null;
        group.projection = null;
        group.modelView = null;
        group.state = null;
        group.formatId = 0;
        group.texture = 0;
        group.textureImage = null;
        group.textureMutations = 0L;
        group.stateKey = 0L;
        group.target = 0L;
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
        this.state = copyState(state);
        this.target = target;
        clearForReuse();
    }

    /** Clears the fields the accept path may set after construction; {@link #reset} and {@link #recycle} share it. */
    /** {@return this group's array holding {@code source}'s values}, reusing the existing one when it is long enough. */
    private int[] copyState(int[] source) {
        if (source == null) {
            return null;
        }
        int[] owned = this.state;
        if (owned == null || owned.length != source.length) {
            owned = new int[source.length];
            this.state = owned;
        }
        System.arraycopy(source, 0, owned, 0, source.length);
        return owned;
    }

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
        GroupStamp.recycle(this.stamp);
        this.stamp = null;
        this.postClears = null;
        this.retireSequence = 0L;
        this.textureImage = null;
        this.textureMutations = 0L;
    }
}

/**
 * A {@code glClear} a group carries, to be replayed inside the pass right after the group's draws.
 * <p>
 * The mask is GL's own buffer mask (0x4000 colour, 0x0100 depth); the colour and depth are the values the game had
 * latched when it issued the clear, not whatever the stub state says at record time.
 */
final class QueuedClear {
    final int mask;
    final float[] rgba;
    final double depth;

    QueuedClear(int mask, float[] rgba, double depth) {
        this.mask = mask;
        this.rgba = rgba;
        this.depth = depth;
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
    long frameId;
    int targetGeneration;
    /** {@code true} when the mesh belongs to someone else and must not be freed when the group retires. */
    boolean meshBorrowed;
    /** The world the group was accepted in; a group from an older world must never be drawn. */
    int worldEpoch;

    GroupStamp(long frameId, int targetGeneration, boolean meshBorrowed, int worldEpoch) {
        this.frameId = frameId;
        this.targetGeneration = targetGeneration;
        this.meshBorrowed = meshBorrowed;
        this.worldEpoch = worldEpoch;
    }

    /** One stamp per group, like the group itself; pooled under the same opt-in flag. */
    private static final java.util.ArrayDeque<GroupStamp> FREELIST = new java.util.ArrayDeque<GroupStamp>();
    private static final int FREELIST_CAP = 8192;
    static long poolCreated;
    static long poolReused;
    static long poolRecycled;

    static GroupStamp obtain(long frameId, int targetGeneration, boolean meshBorrowed, int worldEpoch) {
        if (!ImmediateGroup.POOL) {
            return new GroupStamp(frameId, targetGeneration, meshBorrowed, worldEpoch);
        }
        GroupStamp stamp = FREELIST.poll();
        if (stamp == null) {
            poolCreated++;
            return new GroupStamp(frameId, targetGeneration, meshBorrowed, worldEpoch);
        }
        poolReused++;
        stamp.frameId = frameId;
        stamp.targetGeneration = targetGeneration;
        stamp.meshBorrowed = meshBorrowed;
        stamp.worldEpoch = worldEpoch;
        return stamp;
    }

    static void recycle(GroupStamp stamp) {
        if (!ImmediateGroup.POOL || stamp == null || FREELIST.size() >= FREELIST_CAP) {
            return;
        }
        poolRecycled++;
        FREELIST.add(stamp);
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
