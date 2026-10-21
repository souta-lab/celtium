package org.soutalab.celtium.vulkan;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The coalesced font run: the pending glyph vertices, the buffer that holds them, the run key that closes a run, and
 * the submission of a completed run through the owner's sink.
 * <p>
 * Ownership (docs/refactoring-plan.md R3b, first half): this class owns the font domain's pending state - the bytes a
 * glyph append adds, the matrix/colour/alpha/texture witnesses that decide whether a glyph may join the open run, the
 * counters that say how the runs went out, and the retention of a run the sink refused or abandoned so its glyphs are
 * not lost. It owns no GL or Vulkan state, holds no backend reference, and never reads a field of the backend: the two
 * interfaces below are the whole contract, and both are implemented by the owner.
 * <p>
 * The method names are the ones the backend calls today ({@code celtium$fontAppend},
 * {@code celtium$flushPendingFontBatch}, {@code celtium$fontMatrixBoundary}, {@code celtium$fontBatchSummary},
 * {@code celtium$setFontPreFlush}), so the wiring is one-to-one and the {@code CELTIUM_VERIFY} line can be compared
 * byte for byte before and after.
 * <p>
 * Deliberately narrow: {@link FontRunState} exposes the fifteen values a font run needs, not the backend's fields.
 * That is the permanent shape for this domain (unlike the report's provisional widening), and it is why the class can
 * move again without touching the renderer.
 * <p>
 * Two things this class changes on purpose, both to be read in the record rather than inferred:
 * <ul>
 * <li><b>Retention.</b> Today a refused or abandoned run loses its glyphs: the flush marks the run inactive and zeroes
 * its bytes before it submits, so a {@code false} return or a thrown submission leaves nothing to retry. The existing
 * re-queue is group-level (the immediate window's abort path) and does not cover the run's own pending bytes. The
 * retention here is <em>implemented and unexercised</em> until a leg actually refuses a run - the counters in
 * {@link #celtium$fontRetentionSummary()} say whether it fired, and nothing was added to
 * {@link #celtium$fontBatchSummary()} so the verification line is unchanged either way.</li>
 * <li><b>The target witness.</b> The run key today has no target, so a run can span a switch between the main
 * framebuffer and another one and be drawn into the wrong pass; this class includes the target identity in the key.</li>
 * <li><b>The draw-state witness.</b> The owner's batch path reads the live blend/depth/cull/colour-mask/scissor state
 * when it builds the group a run is submitted as, so a run appended under one draw state and submitted under another
 * is drawn with the state of the submission. The key covered colour, texture, alpha test, target and matrices; it did
 * not cover the rest of the state the group captures. This class now does, through the default methods on
 * {@link FontRunState}, and counts the runs that witness closes in {@link #celtium$fontStateSummary()}.</li>
 * </ul>
 */
public final class FontBatch {

    /**
     * Read-only view of the owner's state: the values that decide whether a glyph may join the open run and what the
     * run is drawn under. Implemented by the owner, pulled at append and flush time, never allocated per glyph.
     */
    public interface FontRunState {
        /** The device stalled or the capture is dirty: a run may not be started (the normal path owns the capture). */
        boolean stalled();

        /** Matrices are dirty in the mirror: the run's matrices would not be the ones it was appended under. */
        boolean matricesDirty();

        /** Whether the main framebuffer is the current render target. */
        boolean mainTarget();

        /** The identity of the current render target, for the per-target retention queue. */
        long targetIdentity();

        /** Whether {@code TEXTURE_2D} is enabled on the base unit. */
        boolean texture2dEnabled();

        /** The name bound on the base unit. */
        int boundTextureName();

        /** The white texture the run falls back to when no texture is bound. */
        int whiteTextureId();

        float colorRed();

        float colorGreen();

        float colorBlue();

        float colorAlpha();

        boolean alphaTestEnabled();

        float alphaFuncReference();

        /** The current projection matrix, 16 floats. */
        float[] projection();

        /** The current model-view matrix, 16 floats. */
        float[] modelView();

        /**
         * The draw state the run's quads will actually be drawn under, read at submit time by the owner's batch
         * path: {@code ImmediateGroup} captures the decoded GL state (blend, depth, cull) and the scissor box into
         * the group it builds, and a group is only built when the held run is finally offered.
         * <p>
         * These are part of the run key for the same reason the matrices are, and the reason is measured twice over
         * already: a run held across a matrix change came out as garbled menu buttons, a rotated splash and
         * block-like copyright text, and the scissor had to be captured per group because a group spanning a scissor
         * change was clipped by whatever rectangle was live at record time. The colour, texture, alpha test, target
         * and matrices were in the key; the rest of the state the group captures was not, so a run appended under
         * one draw state and submitted under another is drawn with the state of the *submission*, not of its first
         * quad. That is the hole this witness closes.
         * <p>
         * Defaults are "no change": a state view that does not answer them keeps the old behaviour exactly, which is
         * what makes the wiring one commit per side.
         */
        default boolean depthTestEnabled() {
            return true;
        }

        /** Whether depth writes are on, which the group's pipeline state carries with the test. */
        default boolean depthWriteEnabled() {
            return true;
        }

        /** Whether blending is on. */
        default boolean blendEnabled() {
            return true;
        }

        /** Whether face culling is on (the glyph strip's winding is the game's, not this class's, to change). */
        default boolean cullEnabled() {
            return false;
        }

        /** The colour write mask, {@code 0xF} when every channel is written. */
        default int colorMask() {
            return 0xF;
        }

        /**
         * The scissor rectangle the run will be clipped by, packed as {@code (x << 48) | (y << 32) | (w << 16) | h},
         * or {@code 0} when the scissor is off. Packed rather than four calls because this is read per glyph.
         */
        default long scissorKey() {
            return 0L;
        }
    }

    /**
     * The owner's submission of one completed run. Returning {@code false} means the run was refused; throwing means
     * it could not be submitted. Either way the batch retains the run and offers it again at the next flush, because a
     * refused run that is dropped is text drawn by nobody.
     */
    public interface RunSink {
        boolean submit(Run run);
    }

    /**
     * One completed run: the vertices and the state they were appended under. {@link #address} points into the
     * batch's pending buffer and is only valid for the duration of the {@link RunSink#submit} call - the batch copies
     * the bytes only if the run has to be retained.
     */
    public static final class Run {
        public final Object format;
        public final int stride;
        public final int formatId;
        public final long address;
        public final int vertexCount;
        public final int drawMode;
        public final int texture;
        public final boolean alphaTest;
        public final float alphaRef;
        public final float[] projection;
        public final float[] modelView;
        public final long targetIdentity;
        /** How this run was closed: {@code intervening}, {@code boundary} or {@code state-change}. */
        public final String how;

        Run(Object format, int stride, int formatId, long address, int vertexCount, int drawMode, int texture,
                boolean alphaTest, float alphaRef, float[] projection, float[] modelView, long targetIdentity,
                String how) {
            this.format = format;
            this.stride = stride;
            this.formatId = formatId;
            this.address = address;
            this.vertexCount = vertexCount;
            this.drawMode = drawMode;
            this.texture = texture;
            this.alphaTest = alphaTest;
            this.alphaRef = alphaRef;
            this.projection = projection;
            this.modelView = modelView;
            this.targetIdentity = targetIdentity;
            this.how = how;
        }
    }

    /** A run the sink refused or that threw, kept with its own copy of the bytes until it can be offered again. */
    private static final class RetainedRun {
        final Object format;
        final int stride;
        final int formatId;
        final int drawMode;
        final int texture;
        final boolean alphaTest;
        final float alphaRef;
        final float[] projection;
        final float[] modelView;
        final long targetIdentity;
        final ByteBuffer vertices;
        final int vertexCount;

        RetainedRun(Run run, ByteBuffer copy) {
            this.format = run.format;
            this.stride = run.stride;
            this.formatId = run.formatId;
            this.drawMode = run.drawMode;
            this.texture = run.texture;
            this.alphaTest = run.alphaTest;
            this.alphaRef = run.alphaRef;
            this.projection = run.projection;
            this.modelView = run.modelView;
            this.targetIdentity = run.targetIdentity;
            this.vertices = copy;
            this.vertexCount = run.vertexCount;
        }
    }

    /**
     * {@code -Dceltium.font.batching=false} is the one-flag control: with batching off every glyph goes back to its own
     * {@code submitImmediateBatch} call, so a crash or a picture change can be attributed to the coalescing rather than
     * to the tree.
     */
    private static final boolean BATCHING = !"false".equalsIgnoreCase(
            System.getProperty("celtium.font.batching", "true"));

    /**
     * Retention is the fix, not the flag: a refused or abandoned run is kept and offered again, because a refused run
     * that is dropped is text drawn by nobody. {@code -Dceltium.font.retaining=false} exists only as a diagnostic
     * control for a leg that wants the old behaviour (drop the run and count it), the same shape as
     * {@code -Dceltium.vulkan.nativeTextureCopy=false}: correctness does not depend on a lab flag.
     */
    private static final boolean RETAINING = !"false".equalsIgnoreCase(
            System.getProperty("celtium.font.retaining", "true"));

    /** Bounds on the retention queue: a refused run is kept, but a session that can never submit must not grow. */
    private static final int MAX_RETAINED_RUNS = 64;
    private static final int MAX_RETAINED_BYTES = 4 * 1024 * 1024;

    /** The run's vertices, owned by this batch and grown as needed; the font's own buffer is copied into it. */
    private ByteBuffer pendingVertices;
    private int pendingBytesUsed;
    private int pendingVertexCount;
    private Object pendingFormat;
    private int pendingStride;
    private int pendingFormatId;
    private int pendingDrawMode;
    /** The cheap state witnesses a run was started with: a change in any of them flushes it through the normal path. */
    private int pendingTexture = -1;
    private static final boolean APPEND_DEBUG = Boolean.getBoolean("celtium.fontAppend.debug");
    private int appendLogged;
    private long pendingColorBits;
    private boolean pendingAlphaTest;
    private float pendingAlphaRef;
    /**
     * The draw state the run will be submitted under, as read when the run was started. The owner's batch path reads
     * the live state when it builds the group, so a run held across a change of any of these is drawn with the wrong
     * one - see {@link FontRunState#depthTestEnabled()}.
     */
    private boolean pendingDepthTest = true;
    private boolean pendingDepthWrite = true;
    private boolean pendingBlend = true;
    private boolean pendingCull;
    private int pendingColorMask = 0xF;
    private long pendingScissorKey;
    private boolean pendingActive;
    /** The matrix pair the open run was started under; a change flushes it, as the normal path's group key does. */
    private final float[] pendingProjection = new float[16];
    private final float[] pendingModelView = new float[16];
    /** The target the open run was appended for; a run is only re-offered to the target it was written for. */
    private long pendingTargetIdentity;

    /** How each coalesced run was flushed: by an intervening submission, a boundary, or a state change. */
    /**
     * Flush invocations per caller-declared reason, including the ones that found no pending run.
     * <p>
     * The total alone said 3,962 calls a frame against 9 runs, but not which call site produced them - and the
     * state-change boundary turned out to be only 8% of it. Split by reason so the next reading names the site.
     */
    /** Which half of the work probe said yes, so a gate that does not reduce the calls can be read, not guessed. */
    private long workBecausePending;
    private long workBecauseCapture;

    private long flushCallsIntervening;
    private long flushCallsBoundary;
    private long flushCallsStateChange;

    private int flushedIntervening;
    private int flushedBoundary;
    private int flushedStateChange;
    /**
     * Runs that were closed because the draw state the group captures at submit time changed since the run's first
     * quad. Counted apart from {@link #flushedStateChange} so a leg can say whether the witness ever fired, and kept
     * out of {@code celtium$fontBatchSummary()} because that string is quoted by past acceptances byte for byte.
     */
    private int flushedDrawState;
    /** Glyph quads appended to a run and runs started, so "batched" is a quantity and not a claim. */
    private long quadsAppended;
    private long batchesStarted;
    private long appendNanos;
    /**
     * Degenerate separator vertices inserted between glyphs of a strip run (see {@link #celtium$fontAppend}): the
     * joining triangles a strip would otherwise draw between consecutive glyphs are the user-visible bars.
     */
    private long stripSeparators;

    /**
     * Runs the sink refused or that threw, oldest first, with their own copy of the bytes. Kept per target: a run is
     * only re-offered when its own target is current, so a run for a framebuffer that is never bound again waits
     * instead of being drawn into another target's pass.
     */
    private final Deque<RetainedRun> retained = new ArrayDeque<RetainedRun>();
    private long retainedBytes;
    private long retainedRuns;
    private long retainedQuads;
    private long retainedDroppedRuns;
    private long retainedDroppedQuads;
    /** Runs dropped because the retention control is off: the old behaviour, counted so a leg can say it happened. */
    private long retainedControlDrops;

    private FontRunState state;
    private RunSink sink;
    /**
     * The capture-side per-string font buffer's flush, run at the top of {@link #celtium$flushPendingFontBatch}: the
     * capture accumulates a string's glyphs itself and hands them over in one append, so every point that would flush
     * this run must first pull in whatever the capture is still holding.
     */
    private Runnable preFlush;

    /**
     * Whether the capture is holding glyphs this run has not seen yet.
     * <p>
     * The pre-flush hook above can only be asked by running it, and running it is what makes
     * {@code celtium$flushPendingFontBatch} cost something (~20 ns) at the ~4,000 calls a frame the GlStateManager
     * setters produce. This answers the same question as one boolean read, so a caller can skip the call entirely
     * when there is neither a pending run nor anything buffered.
     */
    private java.util.function.BooleanSupplier captureWork;

    /** The owner's state view; called once at wiring time, never per glyph. */
    public void celtium$setFontRunState(FontRunState state) {
        this.state = state;
    }

    /** The owner's submission; called once at wiring time. */
    public void celtium$setFontRunSink(RunSink sink) {
        this.sink = sink;
    }

    /** Registers the capture's pre-flush hook; invoked with {@code null} to clear it. */
    /** Wires the capture's "am I holding glyphs" probe, so a flush with nothing to do can be skipped. */
    public void celtium$setFontCaptureWork(java.util.function.BooleanSupplier probe) {
        this.captureWork = probe;
    }

    /**
     * {@return whether a flush would have anything to do} - a pending run, or glyphs the capture is still holding.
     */
    public boolean celtium$hasWork() {
        if (this.pendingActive) {
            this.workBecausePending++;
            return true;
        }
        java.util.function.BooleanSupplier probe = this.captureWork;
        if (probe != null && probe.getAsBoolean()) {
            this.workBecauseCapture++;
            return true;
        }
        return false;
    }

    /** {@return which half of the work probe said yes}. */
    public String fontWorkSummary() {
        return "pending=" + this.workBecausePending + " capture=" + this.workBecauseCapture;
    }

    public void celtium$setFontPreFlush(Runnable hook) {
        this.preFlush = hook;
    }

    /**
     * Adds one glyph's vertices to the open run, starting a run if there is none, and returns whether the run took
     * them. {@code false} means the caller must submit the batch through the normal path instead.
     * <p>
     * The vertices are copied, not aliased: the font's buffer is refilled for the next glyph. On a strip or fan run a
     * pair of degenerate vertices is inserted first - a copy of the previous glyph's last vertex and a copy of the new
     * glyph's first - because a strip of 4N vertices draws 4N-2 triangles, so the last vertices of one glyph connect
     * to the first of the next and the connecting triangles are the 1-px bars between characters.
     */
    public boolean celtium$fontAppend(Object vertexFormat, int vertexStride, int formatId, long vertexAddress,
                                      int vertexCount, int drawMode) {
        long started = System.nanoTime();
        try {
            if (!BATCHING) {
                return false;
            }
            if (vertexAddress == 0L || vertexCount <= 0 || vertexStride <= 0) {
                return false;
            }
            FontRunState view = this.state;
            if (view == null || this.sink == null) {
                return false;
            }
            if (view.stalled() || view.matricesDirty() || !view.mainTarget()) {
                // The normal path owns the capture (matrices, stall handling, target routing); a run is only started
                // from a state that has already been captured.
                return false;
            }
            int texture = view.texture2dEnabled() ? view.boundTextureName() : view.whiteTextureId();
            if (APPEND_DEBUG && appendLogged < 80) {
                appendLogged++;
                System.out.println("[Celtium] CELTIUM_FONTAPPEND tex=" + texture + " mode=" + drawMode
                        + " verts=" + vertexCount + " pendingTex=" + this.pendingTexture
                        + " pendingVerts=" + this.pendingVertexCount);
            }
            long colourBits = celtium$fontColourBits(view);
            boolean alphaTest = view.alphaTestEnabled();
            float alphaRef = view.alphaFuncReference();
            boolean depthTest = view.depthTestEnabled();
            boolean depthWrite = view.depthWriteEnabled();
            boolean blend = view.blendEnabled();
            boolean cull = view.cullEnabled();
            int colorMask = view.colorMask();
            long scissorKey = view.scissorKey();
            if (this.pendingActive
                    && !celtium$fontRunMatches(formatId, vertexStride, drawMode, texture, colourBits, alphaTest,
                            alphaRef, depthTest, depthWrite, blend, cull, colorMask, scissorKey, view)) {
                if (celtium$drawStateDiffers(depthTest, depthWrite, blend, cull, colorMask, scissorKey)) {
                    this.flushedDrawState++;
                }
                celtium$flushPendingFontBatch("state-change");
            }
            if (!this.pendingActive) {
                this.pendingFormat = vertexFormat;
                this.pendingStride = vertexStride;
                this.pendingFormatId = formatId;
                this.pendingDrawMode = drawMode;
                this.pendingTexture = texture;
                this.pendingColorBits = colourBits;
                this.pendingAlphaTest = alphaTest;
                this.pendingAlphaRef = alphaRef;
                this.pendingDepthTest = depthTest;
                this.pendingDepthWrite = depthWrite;
                this.pendingBlend = blend;
                this.pendingCull = cull;
                this.pendingColorMask = colorMask;
                this.pendingScissorKey = scissorKey;
                System.arraycopy(view.projection(), 0, this.pendingProjection, 0, 16);
                System.arraycopy(view.modelView(), 0, this.pendingModelView, 0, 16);
                this.pendingTargetIdentity = view.targetIdentity();
                this.pendingBytesUsed = 0;
                this.pendingVertexCount = 0;
                this.pendingActive = true;
                this.batchesStarted++;
            }
            int bytes = vertexCount * vertexStride;
            boolean needsSeparator = this.pendingVertexCount > 0
                    && (drawMode == org.lwjgl.opengl.GL11.GL_TRIANGLE_STRIP
                            || drawMode == org.lwjgl.opengl.GL11.GL_TRIANGLE_FAN);
            int separatorBytes = needsSeparator ? 2 * vertexStride : 0;
            celtium$ensurePendingFontCapacity(this.pendingBytesUsed + separatorBytes + bytes);
            long base = org.lwjgl.system.MemoryUtil.memAddress(this.pendingVertices, 0);
            if (needsSeparator) {
                long previousVertex = base + this.pendingBytesUsed - vertexStride;
                org.lwjgl.system.MemoryUtil.memCopy(previousVertex, base + this.pendingBytesUsed, vertexStride);
                org.lwjgl.system.MemoryUtil.memCopy(vertexAddress, base + this.pendingBytesUsed + vertexStride,
                        vertexStride);
                this.pendingBytesUsed += separatorBytes;
                this.pendingVertexCount += 2;
                this.stripSeparators++;
            }
            long destination = base + this.pendingBytesUsed;
            org.lwjgl.system.MemoryUtil.memCopy(vertexAddress, destination, bytes);
            this.pendingBytesUsed += bytes;
            this.pendingVertexCount += vertexCount;
            this.quadsAppended++;
            return true;
        } catch (RuntimeException | LinkageError error) {
            // The caller names the refusal; the run's state is left as it was so the bytes are not lost.
            return false;
        } finally {
            this.appendNanos += System.nanoTime() - started;
        }
    }

    /**
     * A matrix change is a boundary for the coalesced font run. The run's quads were appended while these matrices were
     * current, and the submission path captures the matrices at submit time - so a run held across a matrix change
     * would be drawn with the transform of whatever came last (the measured symptom with the batching on: garbled menu
     * button labels, a rotated splash and block-like copyright text). Flushing before the change is applied keeps
     * every quad with the matrices it was written for.
     */
    public void celtium$fontMatrixBoundary() {
        // Called on every matrix change in the game - thousands a frame - and 9 of those have a run to submit. With
        // neither a pending run nor glyphs held by the capture there is nothing to flush and nothing whose ordering a
        // skip could change, so the answer is known without asking. Measured before this gate: 3,280 of the 3,641
        // flushes in a frame came from here.
        if (!celtium$hasWork()) {
            return;
        }
        celtium$flushPendingFontBatch("state-change");
    }

    /** The current colour as four quantised bytes packed into one long: the run's colour witness. */
    private static long celtium$fontColourBits(FontRunState view) {
        // Packed as four bytes, not as float bit patterns: the first version shifted each 32-bit pattern by 48/32/16/0
        // and masked the low 16 bits, which for ordinary colours (1.0f = 0x3F800000, 0.25f, 0.0f) is zero - the
        // witness was 0 for almost every colour, so runs merged across colour changes, including the shadow pass and
        // its light pass. The quantised byte is what the vertex data carries anyway.
        return ((long) celtium$colourByte(view.colorRed()) << 24)
                | ((long) celtium$colourByte(view.colorGreen()) << 16)
                | ((long) celtium$colourByte(view.colorBlue()) << 8)
                | celtium$colourByte(view.colorAlpha());
    }

    private static int celtium$colourByte(float value) {
        int quantised = Math.round(value * 255.0f);
        return quantised < 0 ? 0 : Math.min(quantised, 255);
    }

    private boolean celtium$fontRunMatches(int formatId, int stride, int drawMode, int texture, long colourBits,
                                           boolean alphaTest, float alphaRef, boolean depthTest, boolean depthWrite,
                                           boolean blend, boolean cull, int colorMask, long scissorKey,
                                           FontRunState view) {
        // The matrix pair is part of the witness, exactly as it is for the normal path's group key: a run that spans a
        // matrix change is drawn with one matrix for all of its quads. Leaving it out was measured as the garbled
        // menu - the button labels, the rotated splash text and the copyright line came out as blocks while the
        // unscaled footer text stayed legible, because the scaled and rotated draws were merged into a run opened
        // under a different transform.
        // The draw state is the same contract one step further out: the owner's batch path reads the live state when
        // it builds the group, so a run held across a change of blend, depth, cull, colour mask or scissor is drawn
        // under the state of its submission rather than of its first quad - and the tooltip's two passes are the
        // measured case where that difference can be seen: the shadow pass is submitted inside the vanilla
        // disableDepth/enableDepth bracket and the main pass is submitted after it.
        return this.pendingFormatId == formatId && this.pendingStride == stride
                && this.pendingDrawMode == drawMode && this.pendingTexture == texture
                && this.pendingColorBits == colourBits && this.pendingAlphaTest == alphaTest
                && Float.compare(this.pendingAlphaRef, alphaRef) == 0
                && this.pendingDepthTest == depthTest && this.pendingDepthWrite == depthWrite
                && this.pendingBlend == blend && this.pendingCull == cull
                && this.pendingColorMask == colorMask && this.pendingScissorKey == scissorKey
                && this.pendingTargetIdentity == view.targetIdentity()
                && java.util.Arrays.equals(this.pendingProjection, view.projection())
                && java.util.Arrays.equals(this.pendingModelView, view.modelView());
    }

    /** Whether the difference that closed the run was in the draw state rather than in colour/texture/matrices. */
    private boolean celtium$drawStateDiffers(boolean depthTest, boolean depthWrite, boolean blend, boolean cull,
                                             int colorMask, long scissorKey) {
        return this.pendingDepthTest != depthTest || this.pendingDepthWrite != depthWrite
                || this.pendingBlend != blend || this.pendingCull != cull
                || this.pendingColorMask != colorMask || this.pendingScissorKey != scissorKey;
    }

    private void celtium$ensurePendingFontCapacity(int bytes) {
        ByteBuffer buffer = this.pendingVertices;
        if (buffer != null && buffer.capacity() >= bytes) {
            return;
        }
        int capacity = Math.max(4096, Integer.highestOneBit(Math.max(1, bytes)) * 2);
        ByteBuffer grown = org.lwjgl.system.MemoryUtil.memAlloc(capacity);
        if (buffer != null) {
            org.lwjgl.system.MemoryUtil.memCopy(org.lwjgl.system.MemoryUtil.memAddress(buffer, 0),
                    org.lwjgl.system.MemoryUtil.memAddress(grown, 0), this.pendingBytesUsed);
            org.lwjgl.system.MemoryUtil.memFree(buffer);
        }
        this.pendingVertices = grown;
    }

    /**
     * Closes the open run, offers it - and any run retained for the current target, oldest first - to the sink, and
     * counts how the run went out: an intervening submission, a group/pass/frame boundary, or a state change.
     * <p>
     * The run is marked inactive <em>before</em> the submission so the submission's own entry flush cannot see it
     * again. A run the sink refuses or that throws is copied into the retention queue instead of being dropped: it is
     * offered again at the next flush, and while a retained run waits for its target, later runs for that target queue
     * behind it so the glyphs cannot be drawn out of order.
     */
    /**
     * The projection or model-view array a run carries, reused when it is unchanged from the previous run.
     * <p>
     * Every run used to clone both matrices - 128 bytes and a GC candidate per text run, twice that when the shadow pass
     * runs, for a frame with dozens of runs. The arrays are read-only once a run holds them (the sink copies them into the
     * renderer's state and a retained run is retried unchanged), so a run can share the previous run's array when the
     * values are the same, which they are for every run inside one GUI matrix.
     */
    private float[] matrixSnapshot(boolean projection) {
        float[] pending = projection ? this.pendingProjection : this.pendingModelView;
        float[] last = projection ? this.lastRunProjection : this.lastRunModelView;
        if (last != null && java.util.Arrays.equals(last, pending)) {
            return last;
        }
        float[] snapshot = pending.clone();
        if (projection) {
            this.lastRunProjection = snapshot;
        } else {
            this.lastRunModelView = snapshot;
        }
        return snapshot;
    }

    /** The last matrices handed to a run, for {@link #matrixSnapshot}. */
    private float[] lastRunProjection;
    private float[] lastRunModelView;

    public void celtium$flushPendingFontBatch(String how) {
        // Every call is counted, before any early return: the classified counters below only move when a run was
        // actually open, so a stream of calls that find nothing to submit was invisible - which is the shape the
        // profile shows (4.5% of the warm window's self time against ~9.5 started runs a frame).
        if ("intervening".equals(how)) {
            this.flushCallsIntervening++;
        } else if ("boundary".equals(how)) {
            this.flushCallsBoundary++;
        } else {
            this.flushCallsStateChange++;
        }
        // The capture's own per-string buffer flushes through the same point as this pending run: whatever is about to
        // happen - an intervening submission, a boundary, a state change - every glyph the capture is holding belongs
        // before it, in the run this method is about to submit. Invoked unconditionally: the capture can hold a
        // segment while the pending run is empty.
        Runnable hook = this.preFlush;
        if (hook != null) {
            try {
                hook.run();
            } catch (Throwable ignored) {
                // A capture-side failure must not take the flush down with it.
            }
        }
        RunSink target = this.sink;
        FontRunState view = this.state;
        if (!this.pendingActive || target == null || view == null) {
            offerRetained(view, target);
            return;
        }
        if ("intervening".equals(how)) {
            this.flushedIntervening++;
        } else if ("boundary".equals(how)) {
            this.flushedBoundary++;
        } else {
            this.flushedStateChange++;
        }
        // The retained runs are older than the open one, so they are offered first: glyphs that were written before
        // this run must not be drawn after it. A run that is refused in this call is retained and left for the next
        // flush rather than offered again immediately - offering it twice in one flush asks the same question twice.
        offerRetained(view, target);
        Run run = new Run(this.pendingFormat, this.pendingStride, this.pendingFormatId,
                org.lwjgl.system.MemoryUtil.memAddress(this.pendingVertices, 0), this.pendingVertexCount,
                this.pendingDrawMode, this.pendingTexture, this.pendingAlphaTest, this.pendingAlphaRef,
                matrixSnapshot(true), matrixSnapshot(false), this.pendingTargetIdentity, how);
        this.pendingActive = false;
        this.pendingBytesUsed = 0;
        this.pendingVertexCount = 0;
        // The run's bytes stay valid until the pending buffer is reused, so a refusal can still copy them.
        if (!offer(run, target)) {
            retain(run);
        }
    }

    /**
     * Offers one run to the sink. {@code false} means it must be retained: the sink refused it, or it threw - and a
     * run whose submission threw is not a run that was drawn.
     */
    private boolean offer(Run run, RunSink target) {
        try {
            return target.submit(run);
        } catch (RuntimeException | LinkageError error) {
            return false;
        }
    }

    /** Copies a refused run out of the pending buffer, bounded, so the next glyph cannot overwrite it. */
    private void retain(Run run) {
        if (!RETAINING) {
            // The diagnostic control: the old behaviour, where a refused run's bytes are simply dropped. Counted, so
            // a leg run under the control can say how much it lost instead of leaving it to be inferred.
            this.retainedControlDrops++;
            return;
        }
        int bytes = run.vertexCount * run.stride;
        if (this.retained.size() >= MAX_RETAINED_RUNS || this.retainedBytes + bytes > MAX_RETAINED_BYTES) {
            this.retainedDroppedRuns++;
            this.retainedDroppedQuads += run.vertexCount / 4;
            return;
        }
        ByteBuffer copy = org.lwjgl.system.MemoryUtil.memAlloc(bytes);
        org.lwjgl.system.MemoryUtil.memCopy(run.address, org.lwjgl.system.MemoryUtil.memAddress(copy, 0), bytes);
        this.retained.addLast(new RetainedRun(run, copy));
        this.retainedBytes += bytes;
        this.retainedRuns++;
        this.retainedQuads += run.vertexCount / 4;
    }

    /** Re-offers the retained runs whose target is current, oldest first, stopping at the first that is refused. */
    private void offerRetained(FontRunState view, RunSink target) {
        if (this.retained.isEmpty() || view == null || target == null) {
            return;
        }
        long current = view.targetIdentity();
        while (!this.retained.isEmpty() && this.retained.peekFirst().targetIdentity == current) {
            RetainedRun run = this.retained.peekFirst();
            Run offer = new Run(run.format, run.stride, run.formatId,
                    org.lwjgl.system.MemoryUtil.memAddress(run.vertices, 0), run.vertexCount, run.drawMode,
                    run.texture, run.alphaTest, run.alphaRef, run.projection, run.modelView, run.targetIdentity,
                    "retained");
            if (!offer(offer, target)) {
                return;
            }
            this.retained.removeFirst();
            this.retainedBytes -= run.vertexCount * run.stride;
            org.lwjgl.system.MemoryUtil.memFree(run.vertices);
        }
    }

    /** {@return every flush asked for, including those that found no pending run}. */
    public long fontFlushCalls() {
        return this.flushCallsIntervening + this.flushCallsBoundary + this.flushCallsStateChange;
    }

    /** {@return flush calls by reason, for the report}. */
    public String fontFlushCallSummary() {
        return "intervening=" + this.flushCallsIntervening + " boundary=" + this.flushCallsBoundary
                + " stateChange=" + this.flushCallsStateChange;
    }

    /** The font batching's own census, for the report: how much was coalesced and how each run went out. */
    /** {@return how many degenerate separator vertices the runs carried}. */
    public long fontStripSeparators() {
        return this.stripSeparators;
    }

    /** {@return how many font runs were started}. */
    public long fontRunsStarted() {
        return this.batchesStarted;
    }

    /** {@return how many quads the runs appended}. */
    public long fontQuadsAppended() {
        return this.quadsAppended;
    }

    public String celtium$fontBatchSummary() {
        return "fontRunsStarted=" + this.batchesStarted
                + " fontQuadsAppended=" + this.quadsAppended
                // The strip separators, counted where the run is built and reported here: the counter existed and was
                // incremented with no reporting site, so a run could not say whether the degenerate-vertex insertion
                // had happened at all - the same "instrument that cannot be read" shape the plan records.
                + " fontStripSeparators=" + this.stripSeparators
                + " flushedIntervening=" + this.flushedIntervening
                + " flushedBoundary=" + this.flushedBoundary
                + " flushedStateChange=" + this.flushedStateChange
                + " appendMillis=" + (this.appendNanos / 1000000L);
    }

    /**
     * The retention queue, for a leg to read: how many runs were refused and kept, how many glyph quads they hold, and
     * how many were dropped by the bound. Deliberately not part of {@link #celtium$fontBatchSummary()}: that string is
     * quoted by past acceptances and compared byte for byte, so a new counter may not appear in it.
     */
    public String celtium$fontRetentionSummary() {
        return "retainedRuns=" + this.retainedRuns
                + " retainedQuads=" + this.retainedQuads
                + " retainedPendingRuns=" + this.retained.size()
                + " retainedPendingBytes=" + this.retainedBytes
                + " retainedDroppedRuns=" + this.retainedDroppedRuns
                + " retainedDroppedQuads=" + this.retainedDroppedQuads
                + " retainedControlDrops=" + this.retainedControlDrops;
    }

    /**
     * The draw-state witness, for a leg to read: how many runs were closed because the blend/depth/cull/colour-mask/
     * scissor state the owner captures at submit time changed since the run's first quad. Deliberately not part of
     * {@link #celtium$fontBatchSummary()}: that string is quoted by past acceptances and compared byte for byte, so a
     * new counter may not appear in it.
     */
    public String celtium$fontStateSummary() {
        return "fontDrawStateFlushes=" + this.flushedDrawState
                + " depthTest=" + this.pendingDepthTest
                + " depthWrite=" + this.pendingDepthWrite
                + " blend=" + this.pendingBlend
                + " cull=" + this.pendingCull
                + " colorMask=" + Integer.toHexString(this.pendingColorMask)
                + " scissor=" + Long.toHexString(this.pendingScissorKey);
    }

    /** Whether a run is open. Read by the owner's entry flush, which must close it before any other draw is recorded. */
    public boolean celtium$fontRunOpen() {
        return this.pendingActive;
    }

    /** {@return the runs still retained for a target that has not come back}, for a leg that needs the number alone. */
    public int celtium$retainedRunCount() {
        return this.retained.size();
    }

    /** Frees the pending buffer. Not called today - the backend never freed it - but the buffer is this class's. */
    public void close() {
        for (RetainedRun run : this.retained) {
            org.lwjgl.system.MemoryUtil.memFree(run.vertices);
        }
        this.retained.clear();
        this.retainedBytes = 0;
        if (this.pendingVertices != null) {
            org.lwjgl.system.MemoryUtil.memFree(this.pendingVertices);
            this.pendingVertices = null;
        }
    }
}
