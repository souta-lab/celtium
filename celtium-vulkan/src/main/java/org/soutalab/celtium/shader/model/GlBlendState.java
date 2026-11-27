/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 */
package org.soutalab.celtium.shader.model;

import org.soutalab.celtium.core.shader.AttachmentBlendSpec;
import org.soutalab.celtium.core.shader.BlendOp;

/**
 * Pure value representation of a pack's GL blend state, convertible directly to Celtium's
 * {@link AttachmentBlendSpec}.
 */
public record GlBlendState(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {

    public int getSrcRgb() { return srcRgb; }
    public int getDstRgb() { return dstRgb; }
    public int getSrcAlpha() { return srcAlpha; }
    public int getDstAlpha() { return dstAlpha; }

    public AttachmentBlendSpec toAttachmentBlendSpec() {
        return new AttachmentBlendSpec(
                true,
                BlendModeFunction.toBlendFactor(srcRgb),
                BlendModeFunction.toBlendFactor(dstRgb),
                BlendOp.ADD,
                BlendModeFunction.toBlendFactor(srcAlpha),
                BlendModeFunction.toBlendFactor(dstAlpha),
                BlendOp.ADD,
                AttachmentBlendSpec.WRITE_RGBA
        );
    }
}
