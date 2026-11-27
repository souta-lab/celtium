package org.soutalab.celtium.glcompat.ffp;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


/** Shared varying declarations and expressions for FFP and Iris fragment clipping. */
public final class ClipDistanceGlsl {

    public static final String VS_OUT_DIST0 = "out vec4 actinium_ClipDist0;";
    public static final String VS_OUT_DIST1 = "out vec4 actinium_ClipDist1;";
    public static final String FS_IN_DIST0 = "in vec4 actinium_ClipDist0;";
    public static final String FS_IN_DIST1 = "in vec4 actinium_ClipDist1;";
    public static final String FS_DISCARD = "if (any(lessThan(actinium_ClipDist0, vec4(0.0))) || any(lessThan(actinium_ClipDist1, vec4(0.0)))) discard;\n";

    private ClipDistanceGlsl() {}

    public static void appendVsAssign(StringBuilder sb, String[] distances) {
        sb.append("  actinium_ClipDist0 = vec4(").append(distances[0]).append(", ").append(distances[1]).append(", ").append(distances[2]).append(", ").append(distances[3]).append(");\n");
        sb.append("  actinium_ClipDist1 = vec4(").append(distances[4]).append(", ").append(distances[5]).append(", ").append(distances[6]).append(", ").append(distances[7]).append(");\n");
    }
}
