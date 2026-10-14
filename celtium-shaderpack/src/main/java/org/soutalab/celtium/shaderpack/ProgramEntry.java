package org.soutalab.celtium.shaderpack;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One resolved program in one dimension: which stage sources exist after
 * world-folder fallback, whether the pack gates it behind an option
 * expression, and the comment/const directives observed on the live
 * preprocessor branches.
 *
 * @param name             program basename ({@code gbuffers_terrain})
 * @param family           classification used for pass ordering
 * @param stages           stage to resolved in-pack source path, sorted by stage
 * @param enabledCondition raw {@code program.<world>/<name>.enabled} expression, or null
 * @param enabledByDefault evaluation of that condition under the default option
 *                         set; null when unevaluatable or absent
 * @param directives       comment directives ({@code DRAWBUFFERS} etc.) and
 *                         {@code const colortex*} tunables found on live lines;
 *                         key to sorted distinct values
 * @param filesTouched     number of distinct files in the include closure
 * @param diagnostics      diagnostics attributable to this program's expansion
 */
public record ProgramEntry(
        String name,
        ProgramFamily family,
        Map<Stage, PackPath> stages,
        String enabledCondition,
        Boolean enabledByDefault,
        Map<String, List<String>> directives,
        int filesTouched,
        List<Diagnostic> diagnostics) implements Comparable<ProgramEntry> {

    public ProgramEntry {
        // EnumMap/TreeMap copies keep deterministic iteration order; Map.copyOf does not.
        stages = java.util.Collections.unmodifiableMap(new EnumMap<>(stages));
        directives = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(directives));
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean isComputeOnly() {
        return stages.size() == 1 && stages.containsKey(Stage.COMPUTE);
    }

    @Override
    public int compareTo(ProgramEntry other) {
        int byFamily = Integer.compare(family.passOrder, other.family.passOrder);
        return byFamily != 0 ? byFamily : name.compareTo(other.name);
    }
}
