package org.soutalab.celtium.glcompat;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.recording.AccumulatedDraw;
import org.soutalab.celtium.glcompat.recording.CommandBuffer;
import org.soutalab.celtium.glcompat.recording.CommandBufferProcessor;

import java.util.List;

/**
 * Builds final command buffers from raw command buffers and compiled VBO data.
 * Bridges the gap between VBO compilation (glsm types) and buffer processing (recording types).
 */
public final class CommandBufferBuilder {

    private CommandBufferBuilder() {}

    /**
     * Build commands with transform collapsing optimization.
     * Accumulates MODELVIEW transforms and emits MultMatrix at barriers (draws, CallList).
     * Uses mergedRanges where consecutive same-transform draws are combined.
     */
    public static void buildFromRawBuffer(
            CommandBuffer rawBuffer,
            List<AccumulatedDraw> accumulatedDraws,
            CommandBuffer finalBuffer) {

        rawBuffer.resetRead();
        int cmdIndex = 0;
        int rangeIndex = 0;

        AccumulatedDraw draw;
        while (rawBuffer.hasRemaining()) {
            CommandBufferProcessor.copyCommand(rawBuffer, finalBuffer);

            while (rangeIndex < accumulatedDraws.size() && (draw = accumulatedDraws.get(rangeIndex)).commandIndex == cmdIndex) {
                emitDrawRangeToBuffer(draw, finalBuffer, rangeIndex);
                rangeIndex++;
            }

            cmdIndex++;
        }

        while (rangeIndex < accumulatedDraws.size()) {
            emitDrawRangeToBuffer(accumulatedDraws.get(rangeIndex), finalBuffer, rangeIndex);
            rangeIndex++;
        }
    }

    /**
     * Emit a draw range to the output buffer, with transform if needed.
     */
    private static void emitDrawRangeToBuffer(
            AccumulatedDraw draw,
            CommandBuffer out,
            int vboIdx) {

        if (draw.restoreData != null) {
            out.writeDrawRangeRestore(vboIdx, draw.restoreData);
            return;
        }

        out.writeDrawRange(vboIdx);
    }
}
