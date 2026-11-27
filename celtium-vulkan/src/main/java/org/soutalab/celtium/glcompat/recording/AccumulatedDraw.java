package org.soutalab.celtium.glcompat.recording;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.gtnh.client.renderer.DirectTessellator;
import org.soutalab.celtium.glcompat.gtnh.client.renderer.vertex.VertexFormat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an accumulated draw call during display list compilation.
 * Matrix-as-Data Architecture: Stores untransformed vertices + captured matrix.
 * The matrix will be applied at runtime, not baked into vertices.
 */
public final class AccumulatedDraw {
    public final VertexFormat format;
    public final List<ByteBuffer> drawBuffers;
    public final int drawMode;
    public final int commandIndex; // Position in command list for state tracking
    public final int stateGeneration; // Position in command list for state tracking
    public RestoreData restoreData;

    public AccumulatedDraw(DirectTessellator tessellator, int commandIndex, int stateGeneration, boolean copyLast) {
        this.format = tessellator.getVertexFormat();
        this.drawMode = tessellator.getDrawMode();
        this.drawBuffers = new ArrayList<>();
        this.drawBuffers.add(tessellator.allocateBufferCopy());
        this.commandIndex = commandIndex;
        this.stateGeneration = stateGeneration;

        if (copyLast) {
            this.restoreData = new RestoreData(tessellator);
        }
    }


    public void mergeDraw(DirectTessellator tessellator, boolean copyLast) {
        this.drawBuffers.add(tessellator.allocateBufferCopy());

        if (copyLast) {
            this.restoreData = new RestoreData(tessellator);
        }
    }

    /**
     * Holds last vertex attribute values for GL state restoration after VBO draw.
     * Used to sync GLSM cache with actual GL state after VBO rendering.
     */
    public static final class RestoreData {
        public final int lastColor;
        public final int lastNormal;
        public final float lastTexCoordU;
        public final float lastTexCoordV;
        public final int flags;

        public RestoreData(DirectTessellator tessellator) {
            this.lastColor = tessellator.getPackedColor();
            this.lastNormal = tessellator.getPackedNormal();
            this.lastTexCoordU = (float) tessellator.getLastTextureU();
            this.lastTexCoordV = (float) tessellator.getLastTextureV();
            this.flags = tessellator.getVertexFormat().getVertexFlags();
        }
    }
}
