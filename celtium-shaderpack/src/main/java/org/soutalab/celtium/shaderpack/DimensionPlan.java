package org.soutalab.celtium.shaderpack;

import java.util.List;

/**
 * The program plan for one dimension folder.
 *
 * @param worldKey      folder name ({@code "world0"}, {@code "world-1"}), or
 *                      {@code "base"} for the unqualified {@code shaders/} level
 * @param dimensionIds  ids this folder is bound to via {@code dimension.properties}
 *                      ({@code minecraft:overworld}, legacy numeric ids, or
 *                      {@code *} catch-all); empty when undeclared
 * @param programs      resolved programs in canonical pass order
 * @param inheritedFromBase programs served by the base {@code shaders/} files
 *                      because the world folder does not override them
 */
public record DimensionPlan(
        String worldKey,
        List<String> dimensionIds,
        List<ProgramEntry> programs,
        List<String> inheritedFromBase) {

    public DimensionPlan {
        dimensionIds = List.copyOf(dimensionIds);
        programs = List.copyOf(programs);
        inheritedFromBase = List.copyOf(inheritedFromBase);
    }
}
