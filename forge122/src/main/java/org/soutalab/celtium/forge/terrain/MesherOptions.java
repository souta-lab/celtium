package org.soutalab.celtium.forge.terrain;

import org.soutalab.celtium.core.shader.TerrainVertexRequirements;

import java.util.Objects;

/**
 * The per-build switches of the zero-based mesher: the leaf-culling mode (D-57, default SOLID),
 * whether a shader pack is in use (it disables greedy merging for sway/fluid faces and the leaf
 * material swap), the pack's vertex requirements (which decide the {@code mc_Entity}
 * auxiliary column), and the greedy-merging switch (S2-b-perf K2: the diagnostic arm of
 * Gate G2-31 - with merging off, every surviving face is emitted as a unit quad, so the
 * greedy dividend can be measured against the identical face set). Pack OFF is
 * {@code (DEFAULT, false, TerrainVertexRequirements.NONE, true)}.
 */
public record MesherOptions(
        NativeLeafCulling.Mode leafMode,
        boolean packInUse,
        TerrainVertexRequirements requirements,
        boolean greedyEnabled) {

    public MesherOptions {
        Objects.requireNonNull(leafMode, "leafMode");
        Objects.requireNonNull(requirements, "requirements");
    }

    /** The pre-K2 shape: greedy merging at its default (on). */
    public MesherOptions(NativeLeafCulling.Mode leafMode, boolean packInUse,
                         TerrainVertexRequirements requirements) {
        this(leafMode, packInUse, requirements, true);
    }

    /** The pack-OFF options: native leaf culling at its default, no auxiliary column. */
    public static final MesherOptions PACK_OFF =
            new MesherOptions(NativeLeafCulling.Mode.DEFAULT, false,
                    TerrainVertexRequirements.NONE, true);

    /**
     * The driver's pack-OFF options, with the K2 switch read from
     * {@code -Dceltium.terrain.greedy}: only the exact value {@code false} disables greedy
     * merging; an absent or unreadable property is {@link #PACK_OFF} itself.
     */
    public static MesherOptions packOffFromProperty() {
        if (!"false".equals(System.getProperty("celtium.terrain.greedy"))) {
            return PACK_OFF;
        }
        return new MesherOptions(PACK_OFF.leafMode(), PACK_OFF.packInUse(),
                PACK_OFF.requirements(), false);
    }
}
