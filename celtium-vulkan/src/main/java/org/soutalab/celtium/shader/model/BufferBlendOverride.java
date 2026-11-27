/*
 * Derived from Iris / Angelica shader pack interpreter (LGPL-3.0).
 */
package org.soutalab.celtium.shader.model;

import org.jetbrains.annotations.Nullable;
import org.soutalab.celtium.core.shader.AttachmentBlendSpec;

public class BufferBlendOverride {
    private final int index;
    @Nullable
    private final GlBlendState blendMode;

    public BufferBlendOverride(int index, @Nullable GlBlendState blendMode) {
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

    public AttachmentBlendSpec toAttachmentBlendSpec() {
        return blendMode == null ? AttachmentBlendSpec.opaque() : blendMode.toAttachmentBlendSpec();
    }
}
