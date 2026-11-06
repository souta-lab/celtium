package org.soutalab.celtium.vulkan;

/**
 * The hovered-slot highlight probe (-Dceltium.vulkan.probeHighlight=true): poisons one small group's vertex colours
 * at accept, reads them back at the draw, and can borrow the neighbour's pipeline, so a missing GUI overlay can be
 * told apart as a lost fragment or a lost command. Moved out of {@link VulkanTerrainGpuBackend} unchanged
 * (2026-11-06).
 */
final class HighlightProbe {
    private final VulkanTerrainGpuBackend b;

    HighlightProbe(VulkanTerrainGpuBackend backend) {
        this.b = backend;
    }

    /**
     * {@code -Dceltium.vulkan.probeHighlight=true}: rewrites the hovered-slot highlight's own vertices to opaque magenta
     * just before its draw, so one screenshot answers "did this group's draw reach the image at all".
     * <p>
     * The question is not answerable from the counters. The group is accepted, closed, counted as drawn, carries the
     * right colour in its own vertex memory, projects onto the right slot, and blends with the same factors as the
     * groups around it - and none of its pixels appear (creative inventory, guiScale 3, leg r23a: every reference pixel
     * in the slot equals {@code (celtium + 255) / 2}, i.e. the {@code 0x80FFFFFF} half-white overlay is simply absent).
     * "Drawn" in the counters means a draw command was recorded, not that a fragment reached the colour image, and no
     * existing line separates those two. An opaque poison is the separation: a fragment that reaches the image at that
     * slot shows magenta whatever the destination colour is, whatever the blend factor, and whether or not the alpha
     * channel is writable - so "magenta there" means the loss is in what the fragment produced (its colour or its
     * alpha), and "nothing there" means the loss is in the group's place in the command stream (clip, viewport, target
     * image or order).
     * <p>
     * Off by default, bounded to three groups, and only from {@code probeHighlightMinFrame} on (default 8, the frame the
     * group census uses: the loading screen and the menu own the earlier ones).
     */
    static final boolean PROBE_HIGHLIGHT = Boolean.getBoolean("celtium.vulkan.probeHighlight");
    static final long PROBE_HIGHLIGHT_MIN_FRAME =
            Long.getLong("celtium.vulkan.probeHighlightMinFrame", 8L).longValue();
    /** {@code -Dceltium.vulkan.probeHighlightBorrow=true}: draw the poisoned group with its neighbour's pipeline. */
    static final boolean PROBE_HIGHLIGHT_BORROW =
            Boolean.getBoolean("celtium.vulkan.probeHighlightBorrow");
    /** The prepared material of the last group drawn before the poisoned one; the borrow probe's source. */
    PreparedImmediate probeLastPrepared;
    /** How many groups the highlight probe has rewritten, so an accidental match cannot repaint a whole frame. */
    int probeHighlightGroups;
    /** How many of the probe's read-back and borrow lines have been printed (the writes are not bounded). */
    int probeDrawPrints;
    int probeBorrowPrints;

    /**
     * The hovered-slot highlight probe, accept half: rewrites the group's own vertex colours to opaque magenta as the
     * group closes.
     * <p>
     * Selected by the group's own vertex memory rather than by a caller name, because the only thing known about the
     * group is what it carries: {@code POSITION_COLOR}, four vertices, first colour {@code 0x80FFFFFF}, from the frame
     * the group census reads. The positions are not touched, so if the draw reaches the image the poison lands exactly
     * in the slot the reference paints and the screenshot is a direct yes/no.
     * <p>
     * The poison is written here and read back at the draw ({@link #celtium$probeReadBack}) for one reason: the two
     * answers are different findings. Magenta in the picture means the draw landed; a read-back that still says magenta
     * with no magenta in the picture means the command reached the image and produced no fragment; a read-back that says
     * something else means the frame arena's slice did not hold this batch any more when the command read it - a defect
     * no counter could see, because the acceptance-time reads (bounds, first colour) happen at close, before anything
     * could have overwritten them.
     * <p>
     * The colour offset is the layout's own for this format (12: three floats of position and four bytes of colour), not
     * {@code mesh.layout().color}: an immediate mesh records itself as {@code Layout.positionOnly(stride)} because the
     * copy moves the caller's bytes verbatim, so its layout carries no colour offset (-1) even though the vertex does.
     * <p>
     * <b>Every</b> matching group is poisoned, not the first few: the screenshot is taken at a fixed tick and the frames
     * before it are not the frame whose picture is compared. A three-group bound poisoned frames 8-10 (leg r24c) and the
     * picture at tick 60 was a later frame, so the probe would have reported "no magenta" about a frame that had never
     * been poisoned. Only the printed lines are bounded now; the write is not.
     */
    void celtium$probeHighlightAccept(ImmediateGroup group, VulkanDynamicMesh mesh) {
        if (!PROBE_HIGHLIGHT || b.frameId < PROBE_HIGHLIGHT_MIN_FRAME) {
            return;
        }
        if (group.formatId != VulkanImmediateFormats.POSITION_COLOR || mesh == null || mesh.layout() == null) {
            return;
        }
        int stride = mesh.layout().stride;
        int vertices = mesh.vertexCount();
        long base = mesh.vertexAddress();
        if (stride < VulkanImmediatePipeline.VERTEX_BYTES || vertices != 4 || base == 0L
                || org.lwjgl.system.MemoryUtil.memGetInt(base + 12) != 0x80ffffff) {
            return;
        }
        this.probeHighlightGroups++;
        for (int vertex = 0; vertex < vertices; vertex++) {
            // Opaque magenta. Alpha 0xFF so the write lands whatever the blend is; magenta because nothing in the
            // creative inventory is magenta, so "did the poison reach the image" cannot be confused with the scene.
            org.lwjgl.system.MemoryUtil.memPutInt(base + ((long) vertex * stride) + 12, 0xFFFF00FF);
        }
        group.probePoisoned = true;
        if (this.probeHighlightGroups > 3) {
            return;
        }
        System.out.println("[Celtium] CELTIUM_PROBE_POISON frame=" + b.frameId + " group="
                + b.immediateStatistics.immediateGroupsClosed() + " vertices=" + vertices
                + " stride=" + stride + " was=0x80ffffff now=0xffff00ff target=" + group.target
                + " onMain=" + (group.target == b.targetRegistry.mainIdentity())
                + " indices=" + mesh.indexCount() + " firstIndices=" + celtium$firstIndices(mesh, 6)
                + " viewpoint=" + java.util.Arrays.toString(group.viewportValues));
    }

    /**
     * The probe's draw half: what the group's vertex memory holds when the command is recorded.
     * <p>
     * Only for the poisoned group, so a default run pays one boolean per group and prints nothing.
     */
    void celtium$probeReadBack(ImmediateGroup group, VulkanDynamicMesh mesh) {
        if (!PROBE_HIGHLIGHT || !group.probePoisoned || mesh == null) {
            return;
        }
        long base = mesh.vertexAddress();
        int now = base == 0L ? 0 : org.lwjgl.system.MemoryUtil.memGetInt(base + 12);
        // Printed for the first three draws, and for any draw whose slice no longer holds the poison: the write happens
        // on every frame, but only an anomaly (or the sampling line) has to reach the log.
        boolean anomaly = now != 0xffff00ff;
        if (!anomaly && this.probeDrawPrints >= 3) {
            return;
        }
        if (!anomaly) {
            this.probeDrawPrints++;
        }
        Object pipeline = group.prepared == null ? null : group.prepared.pipeline;
        System.out.println("[Celtium] CELTIUM_PROBE_DRAW frame=" + b.frameId + " group="
                + b.immediateStatistics.immediateGroupsClosed() + " anomaly=" + anomaly
                + " colourNow=0x" + Integer.toHexString(now)
                + " strideNow=" + (mesh.layout() == null ? -1 : mesh.layout().stride)
                + " verticesNow=" + mesh.vertexCount() + " indicesNow=" + mesh.indexCount()
                + " firstIndices=" + celtium$firstIndices(mesh, 6)
                + " indexBytes=" + (mesh.indexAddress() == 0L ? -1L : Long.valueOf(mesh.indexCount() * 4L))
                // The handle the draw will bind. draw() returns silently for a zero handle, and a group that leaves no
                // pixels while being counted as drawn is exactly what that silence looks like from outside.
                + " pipeline=0x" + Integer.toHexString(System.identityHashCode(pipeline))
                + " handle=0x" + (pipeline instanceof VulkanImmediatePipeline colour
                        ? Long.toHexString(colour.celtium$handle()) : "other")
                + " vertexBuffer=0x" + Long.toHexString(mesh.vertexBuffer())
                + " indexBuffer=0x" + Long.toHexString(mesh.indexBuffer())
                + " vertexBind=" + mesh.vertexBindOffset() + " indexBind=" + mesh.indexBindOffset()
                + " postClears=" + (group.postClears == null ? 0 : group.postClears.size())
                + " passOpen=" + (b.commandState != null && b.commandState.inRenderPass())
                + " passImage=0x" + Long.toHexString(b.passAttachedColorImage));
    }

    /**
     * The probe's third question: is the state-keyed pipeline the reason the group leaves no pixels?
     * <p>
     * The poisoned group is drawn with the prepared pipeline of the last group that reached the draw before it - a
     * visible neighbour of the same family and the same layout, whose only differences are the colour write mask and the
     * depth test. Magenta in the picture then names the pipeline as the difference; nothing in the picture moves the
     * question to the mesh or the command stream. Off by default, and only ever for the poisoned group.
     */
    PreparedImmediate celtium$probeBorrowPipeline(ImmediateGroup group, PreparedImmediate prepared) {
        if (!PROBE_HIGHLIGHT_BORROW || !group.probePoisoned || this.probeLastPrepared == null || prepared == null) {
            return prepared;
        }
        if (!(prepared.pipeline instanceof VulkanImmediatePipeline)
                || !(this.probeLastPrepared.pipeline instanceof VulkanImmediatePipeline borrowed)) {
            return prepared;
        }
        if (this.probeBorrowPrints < 3) {
            this.probeBorrowPrints++;
            System.out.println("[Celtium] CELTIUM_PROBE_BORROW frame=" + b.frameId
                    + " from=0x" + Integer.toHexString(System.identityHashCode(prepared.pipeline))
                    + " to=0x" + Integer.toHexString(System.identityHashCode(borrowed))
                    + " borrowedHandle=0x" + Long.toHexString(borrowed.celtium$handle())
                    + " blends=" + borrowed.celtium$blends() + " depthTests=" + borrowed.celtium$depthTests()
                    + " blend=" + borrowed.celtium$colorBlend().describe());
        }
        return this.probeLastPrepared;
    }

    /** {@return the first {@code count} indices of the mesh as a comma-separated list}, for the probe's lines. */
    static String celtium$firstIndices(VulkanDynamicMesh mesh, int count) {
        long address = mesh == null ? 0L : mesh.indexAddress();
        if (address == 0L) {
            return "none";
        }
        int available = Math.min(count, mesh.indexCount());
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < available; index++) {
            if (index > 0) {
                text.append(',');
            }
            text.append(org.lwjgl.system.MemoryUtil.memGetInt(address + (long) index * 4));
        }
        return text.length() == 0 ? "none" : text.toString();
    }
}
