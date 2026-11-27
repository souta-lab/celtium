package org.soutalab.celtium.glcompat.streaming;

/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.backend.DirectGlExecutor;
import org.soutalab.celtium.glcompat.debug.GLSMPerfDebug.FenceSource;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;

import static org.soutalab.celtium.glcompat.gtnh.bytebuf.MemoryUtilities.memAddress0;
import static org.soutalab.celtium.glcompat.gtnh.bytebuf.MemoryUtilities.memCopy;

public final class PersistentStreamingBuffer {

    public static final int DEFAULT_CAPACITY = 16 * 1024 * 1024;

    private final GlStreamingRing ring;

    private PersistentStreamingBuffer(GlStreamingRing ring) {
        this.ring = ring;
    }

    public static PersistentStreamingBuffer createOrNull(int capacity) {
        return createOrNull(capacity, Boolean.getBoolean("celtium.glcompat.forceOrphanStreaming"));
    }

    public static PersistentStreamingBuffer createOrNull(int capacity, boolean forceOrphan) {
        return createOrNull(capacity, forceOrphan, FenceSource.UNATTRIBUTED);
    }

    /** Creates a streaming buffer whose fence polls are attributed to the supplied owner. */
    public static PersistentStreamingBuffer createOrNull(int capacity, FenceSource fenceSource) {
        return createOrNull(capacity, Boolean.getBoolean("celtium.glcompat.forceOrphanStreaming"), fenceSource);
    }

    private static PersistentStreamingBuffer createOrNull(int capacity, boolean forceOrphan, FenceSource fenceSource) {
        final GlStreamingRing ring = GlStreamingRing.create(GL15.GL_ARRAY_BUFFER, capacity, forceOrphan, fenceSource);
        return ring == null ? null : new PersistentStreamingBuffer(ring);
    }

    public int upload(ByteBuffer data, int vertexStride) {
        return upload(data, vertexStride, 1);
    }

    public int upload(ByteBuffer data, int vertexStride, int vertexAlignment) {
        final int size = data.remaining();
        final long offset = ring.reserve(size, vertexStride * vertexAlignment);
        if (offset < 0) return -1;
        memCopy(memAddress0(data) + data.position(), ring.address() + offset, size);
        DirectGlExecutor.EXECUTOR.onPersistentBufferWrite(ring.bufferId(), offset, size);
        return (int) (offset / vertexStride);
    }

    public int getBufferId() { return ring.bufferId(); }
    public int getCapacity() { return ring.capacity(); }
    public int getRemaining() { return ring.remaining(); }
    public int getFencesIssued() { return ring.fencesIssued(); }
    public int getForcedReclaims() { return ring.forcedReclaims(); }
    public int getWraps() { return ring.wraps(); }
    public static long getFencesIssuedByAllRings() { return GlStreamingRing.totalFencesIssued(); }

    ByteBuffer getMappedBuffer() { return ring.mapped(); }

    public void endFrame() {
        ring.endFrame();
    }

    public void destroy() {
        ring.destroy();
    }
}
