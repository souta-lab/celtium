/*
 * Derived from Iris / Angelica shader pack interpreter (LGPL-3.0).
 */
package org.soutalab.celtium.shader.model;

import org.jetbrains.annotations.Nullable;
import org.soutalab.celtium.core.shader.AttachmentBlendSpec;

public class BlendModeOverride {
    public static final BlendModeOverride OFF = new BlendModeOverride(null);

    @Nullable
    private final GlBlendState blendMode;

    public BlendModeOverride(@Nullable GlBlendState blendMode) {
        this.blendMode = blendMode;
    }

    @Nullable
    public GlBlendState getBlendMode() {
        return blendMode;
    }

    public boolean isOff() {
        return blendMode == null;
    }

    public AttachmentBlendSpec toAttachmentBlendSpec() {
        return blendMode == null ? AttachmentBlendSpec.opaque() : blendMode.toAttachmentBlendSpec();
    }
}
