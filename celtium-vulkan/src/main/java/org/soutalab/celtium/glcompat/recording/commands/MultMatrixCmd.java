package org.soutalab.celtium.glcompat.recording.commands;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.GLStateManager;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import org.soutalab.celtium.glcompat.GLSMMatrices;

/**
 * Command: glMultMatrix(matrix)
 * Multiplies the current matrix by the specified matrix.
 */
public record MultMatrixCmd(Matrix4f matrix, FloatBuffer buffer) implements DisplayListCommand {

    /**
     * Create a MultMatrixCmd with pre-allocated buffer.
     */
    public static MultMatrixCmd create(Matrix4f matrix) {
        final FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        matrix.get(buffer);
        buffer.rewind();
        return new MultMatrixCmd(new Matrix4f(matrix), buffer);
    }

    @Override
    public void execute() {
        buffer.rewind();
        GLSMMatrices.glMultMatrix(buffer);
    }

    @Override
    public @NotNull String toString() {
        return String.format("MultMatrix(%s)", matrix);
    }
}
