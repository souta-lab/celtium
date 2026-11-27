package org.soutalab.celtium.glcompat.streaming;

/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.GLSMBuffers;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;

/**
 * Streaming buffer using the classic orphan pattern.
 */
public final class OrphanStreamingBuffer {

    private int bufferId;
    private int capacity;

    public OrphanStreamingBuffer() {
        this.bufferId = GLSMBuffers.glGenBuffers();
    }

    public void upload(ByteBuffer data) {
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId);
        capacity = StreamingUploader.upload(data, capacity);
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    void upload(StreamingUploader.UploadStrategy strategy, ByteBuffer data) {
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId);
        capacity = StreamingUploader.upload(strategy, data, capacity);
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    public int getCapacity() {
        return capacity;
    }

    public int getBufferId() {
        return bufferId;
    }

    public void destroy() {
        if (bufferId != 0) {
            GLSMBuffers.glDeleteBuffers(bufferId);
            bufferId = 0;
        }
    }
}
