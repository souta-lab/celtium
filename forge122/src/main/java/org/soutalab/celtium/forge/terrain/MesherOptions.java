package org.soutalab.celtium.forge.terrain;

import org.soutalab.celtium.core.shader.TerrainVertexRequirements;

import java.util.Objects;

/**
 * The per-build switches of the zero-based mesher: the leaf-culling mode (D-57, default SOLID),
 * whether a shader pack is in use (it disables greedy merging for sway/fluid faces and the leaf
 * material swap), and the pack's vertex requirements (which decide the {@code mc_Entity}
 * auxiliary column). Pack OFF is {@code (SOLID, false, TerrainVertexRequirements.NONE)}.
 */
public record MesherOptions(
        NativeLeafCulling.Mode leafMode,
        boolean packInUse,
        TerrainVertexRequirements requirements) {

    public MesherOptions {
        Objects.requireNonNull(leafMode, "leafMode");
        Objects.requireNonNull(requirements, "requirements");
    }

    /** The pack-OFF options: native leaf culling at its default, no auxiliary column. */
    public static final MesherOptions PACK_OFF =
            new MesherOptions(NativeLeafCulling.Mode.DEFAULT, false, TerrainVertexRequirements.NONE);
}
