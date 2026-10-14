package org.soutalab.celtium.shaderpack;

import java.util.Locale;

/**
 * The OptiFine/Iris program family a basename belongs to. Determines the
 * canonical pass order used for manifest sorting: shadow first, then gbuffers,
 * then deferred/composite/final - the begin → shadow → gbuffers → deferred →
 * composite → final chain from docs/shader-plan.md §7.
 */
public enum ProgramFamily {
    /** {@code shadow} - the light-space depth pass. */
    SHADOW(0),
    /** {@code shadowcomp*} - post-shadow passes (usually compute). */
    SHADOWCOMP(1),
    /** {@code begin*}, {@code setup*}, {@code prepare*} - pre-frame and pre-translucent passes. */
    PREPARE(2),
    /** {@code gbuffers_*} - forward scene rendering. */
    GBUFFERS(3),
    /** {@code deferred*} - post-opaque, pre-translucent. */
    DEFERRED(4),
    /** {@code composite*} - post-translucent full-screen passes. */
    COMPOSITE(5),
    /** {@code final} - the last full-screen pass before present. */
    FINAL(6),
    /** {@code dh_*} - Distant Horizons LOD programs (mod-specific). */
    DH(7),
    /** {@code voxy_*} - Voxy LOD programs (mod-specific). */
    VOXY(8),
    /** Anything else - recorded verbatim, flagged UNKNOWN_PROGRAM_NAME. */
    UNKNOWN(9);

    /** Order used when building a deterministic pass list. */
    public final int passOrder;

    ProgramFamily(int passOrder) {
        this.passOrder = passOrder;
    }

    static ProgramFamily of(String baseName) {
        String n = baseName.toLowerCase(Locale.ROOT);
        if (n.equals("shadow")) {
            return SHADOW;
        }
        if (n.matches("shadowcomp[0-9]*")) {
            return SHADOWCOMP;
        }
        if (n.matches("prepare[0-9]*") || n.matches("begin[0-9]*") || n.matches("setup[0-9]*")) {
            return PREPARE;
        }
        if (n.startsWith("gbuffers_")) {
            return GBUFFERS;
        }
        if (n.matches("deferred[0-9]*")) {
            return DEFERRED;
        }
        if (n.matches("composite[0-9]*")) {
            return COMPOSITE;
        }
        if (n.equals("final")) {
            return FINAL;
        }
        if (n.startsWith("dh_")) {
            return DH;
        }
        if (n.startsWith("voxy_")) {
            return VOXY;
        }
        return UNKNOWN;
    }
}
