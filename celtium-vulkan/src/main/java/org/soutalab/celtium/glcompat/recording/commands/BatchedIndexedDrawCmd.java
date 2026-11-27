package org.soutalab.celtium.glcompat.recording.commands;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.GLSMDraws;
import org.soutalab.celtium.glcompat.GLSMVertexInput;

/**
 * Placeholder command for one baked indexed draw. Recorded into the command stream as a
 * complex object at capture time and {@link #fill}ed once the {@link IndexedDrawBatch}
 * for its layout group has been built at the end of list compilation.
 */
public final class BatchedIndexedDrawCmd implements DisplayListCommand {
    private IndexedDrawBatch batch;
    private int drawMode;
    private int indexCount;
    private int indexType;
    private long indexByteOffset;

    public void fill(IndexedDrawBatch batch, int drawMode, int indexCount, int indexType, long indexByteOffset) {
        this.batch = batch;
        this.drawMode = drawMode;
        this.indexCount = indexCount;
        this.indexType = indexType;
        this.indexByteOffset = indexByteOffset;
    }

    public int getDrawMode() { return drawMode; }
    public int getIndexCount() { return indexCount; }
    public int getIndexType() { return indexType; }
    public long getIndexByteOffset() { return indexByteOffset; }

    @Override
    public void execute() {
        if (batch == null) return;
        final int callerVao = GLSMVertexInput.getBoundVAO();
        try {
            batch.bind();
            GLSMDraws.glDrawElements(drawMode, indexCount, indexType, indexByteOffset);
        } finally {
            GLSMVertexInput.glBindVertexArray(callerVao);
        }
    }

    @Override
    public String toString() {
        return "BatchedIndexedDrawCmd(batch=" + batch + ", mode=" + drawMode + ", count=" + indexCount + ", type=" + indexType + ", offset=" + indexByteOffset + ")";
    }
}
