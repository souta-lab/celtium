/*
 * Derived from Iris / Angelica shader pack interpreter (LGPL-3.0).
 */
package org.soutalab.celtium.shader.model;

import org.jetbrains.annotations.Nullable;

public class BufferBlendInformation {
    private final int index;
    @Nullable
    private final GlBlendState blendMode;

    public BufferBlendInformation(int index, @Nullable GlBlendState blendMode) {
        this.index = index;
        this.blendMode = blendMode;
    }

    public int getIndex() {
        return index;
    }

    @Nullable
    public GlBlendState getBlendMode() {
        return blendMode;
    }
}
