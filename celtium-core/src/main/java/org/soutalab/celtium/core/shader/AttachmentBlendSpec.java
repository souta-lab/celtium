package org.soutalab.celtium.core.shader;

import java.util.Objects;

/**
 * The blend state of one colour attachment. {@code colorWriteMask} is the RGBA bit set
 * (bit 0 = R, 1 = G, 2 = B, 3 = A) the attachment accepts writes to.
 */
public record AttachmentBlendSpec(
        boolean blendEnabled,
        BlendFactor srcColorFactor,
        BlendFactor dstColorFactor,
        BlendOp colorOp,
        BlendFactor srcAlphaFactor,
        BlendFactor dstAlphaFactor,
        BlendOp alphaOp,
        int colorWriteMask) {

    public static final int WRITE_RGBA = 0xF;

    public AttachmentBlendSpec {
        Objects.requireNonNull(srcColorFactor, "srcColorFactor");
        Objects.requireNonNull(dstColorFactor, "dstColorFactor");
        Objects.requireNonNull(colorOp, "colorOp");
        Objects.requireNonNull(srcAlphaFactor, "srcAlphaFactor");
        Objects.requireNonNull(dstAlphaFactor, "dstAlphaFactor");
        Objects.requireNonNull(alphaOp, "alphaOp");
        if (colorWriteMask < 0 || colorWriteMask > WRITE_RGBA) {
            throw new IllegalArgumentException("colorWriteMask must be an RGBA bit set (0..15)");
        }
    }

    /** Blending off, all channels writable: the opaque terrain default. */
    public static AttachmentBlendSpec opaque() {
        return new AttachmentBlendSpec(false,
                BlendFactor.ONE, BlendFactor.ZERO, BlendOp.ADD,
                BlendFactor.ONE, BlendFactor.ZERO, BlendOp.ADD,
                WRITE_RGBA);
    }

    /** Classic src-alpha / one-minus-src-alpha blending, all channels writable. */
    public static AttachmentBlendSpec alphaBlend() {
        return new AttachmentBlendSpec(true,
                BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA, BlendOp.ADD,
                BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA, BlendOp.ADD,
                WRITE_RGBA);
    }
}
