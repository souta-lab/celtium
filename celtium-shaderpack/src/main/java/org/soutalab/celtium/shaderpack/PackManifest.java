package org.soutalab.celtium.shaderpack;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The immutable S1 output of {@link ShaderPackLoader}: everything the rest of
 * the pipeline (and reviewers) can learn about a pack without touching a GPU.
 *
 * <p>Determinism contract: two loads of byte-identical input produce identical
 * manifests, and {@link #toReport()} is byte-identical. All collections are
 * sorted or insertion-stable and unmodifiable.</p>
 *
 * @param label               archive/directory file name
 * @param contentSha256       SHA-256 over the sorted {@code (path, bytes)} of
 *                            the {@code shaders/} snapshot - pins the corpus
 * @param fileCount           files under {@code shaders/}
 * @param skippedOutsideShaders entries outside {@code shaders/} that were ignored
 * @param seeds               builtin defines used for evaluation (MC_VERSION...)
 * @param selectedProfile     profile applied to the option set, or null
 * @param optionEnvironment   effective option name to value used for property
 *                            preprocessing and {@code program.*.enabled}
 *                            evaluation (lexical defaults + profile + overrides)
 * @param options             discovered option declarations, sorted by name
 * @param profiles            raw {@code profile.NAME} token lists, sorted
 * @param userFacingOptions   option names actually reachable by a user
 *                            (screen/sliders/profile/enabled-condition tokens
 *                            that have a declaration)
 * @param undeclaredOptionNames names referenced by screens/profiles/conditions
 *                            with no declaration found - a pack bug indicator
 * @param conditionSymbols    every identifier consulted by any conditional
 * @param dimensions          per-world-folder program plans, canonical order
 * @param propertiesSummaries per-file property census (file to sorted keys)
 * @param activeFeatures      feature flags live under {@code optionEnvironment}
 * @param conditionalFeatures feature flags seen in the pack but not active
 *                            (e.g. behind a disabled option or dead branch)
 * @param diagnostics         all findings, sorted by location/severity/code
 */
public record PackManifest(
        String label,
        String contentSha256,
        int fileCount,
        int skippedOutsideShaders,
        Map<String, String> seeds,
        String selectedProfile,
        Map<String, String> optionEnvironment,
        List<OptionDiscovery.OptionDecl> options,
        Map<String, List<String>> profiles,
        Set<String> userFacingOptions,
        Set<String> undeclaredOptionNames,
        Set<String> conditionSymbols,
        List<DimensionPlan> dimensions,
        Map<String, PropertiesSummary> propertiesSummaries,
        Set<FeatureFlag> activeFeatures,
        Set<FeatureFlag> conditionalFeatures,
        List<Diagnostic> diagnostics) {

    /** Per-.properties-file census: surviving key count and sorted key list. */
    public record PropertiesSummary(int entryCount, List<String> keys) {
        public PropertiesSummary {
            keys = List.copyOf(keys);
        }
    }

    public PackManifest {
        // Deterministic iteration: every map is a sorted TreeMap copy.
        seeds = sortedCopy(seeds);
        optionEnvironment = sortedCopy(optionEnvironment);
        options = List.copyOf(options);
        profiles = copyProfiles(profiles);
        userFacingOptions = java.util.Collections.unmodifiableSet(new TreeSet<>(userFacingOptions));
        undeclaredOptionNames = java.util.Collections.unmodifiableSet(new TreeSet<>(undeclaredOptionNames));
        conditionSymbols = java.util.Collections.unmodifiableSet(new TreeSet<>(conditionSymbols));
        dimensions = List.copyOf(dimensions);
        propertiesSummaries = sortedCopy(propertiesSummaries);
        activeFeatures = java.util.Collections.unmodifiableSet(new TreeSet<>(activeFeatures));
        conditionalFeatures = java.util.Collections.unmodifiableSet(new TreeSet<>(conditionalFeatures));
        diagnostics = List.copyOf(diagnostics);
    }

    private static <V> Map<String, V> sortedCopy(Map<String, V> in) {
        return java.util.Collections.unmodifiableMap(new TreeMap<>(in));
    }

    private static Map<String, List<String>> copyProfiles(Map<String, List<String>> in) {
        TreeMap<String, List<String>> out = new TreeMap<>();
        in.forEach((k, v) -> out.put(k, List.copyOf(v)));
        return java.util.Collections.unmodifiableMap(out);
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == Severity.ERROR);
    }

    /** Programs gated by an option condition that are OFF under the default env. */
    public List<String> disabledPrograms() {
        List<String> out = new java.util.ArrayList<>();
        for (DimensionPlan dim : dimensions) {
            for (ProgramEntry p : dim.programs()) {
                if (Boolean.FALSE.equals(p.enabledByDefault())) {
                    out.add(dim.worldKey() + "/" + p.name());
                }
            }
        }
        return List.copyOf(out);
    }

    /**
     * Deterministic, human-readable serialization - the golden-file format used
     * by tests and by {@code PackInspect}. Not JSON on purpose: the report is
     * meant to be diffed in review.
     */
    public String toReport() {
        StringBuilder r = new StringBuilder(16 * 1024);
        r.append("pack=").append(label).append('\n');
        r.append("contentSha256=").append(contentSha256).append('\n');
        r.append("fileCount=").append(fileCount)
                .append(" skippedOutsideShaders=").append(skippedOutsideShaders).append('\n');
        r.append("seeds=").append(sortedKv(seeds)).append('\n');
        r.append("selectedProfile=").append(selectedProfile == null ? "<none>" : selectedProfile).append('\n');
        r.append("optionEnvironment[").append(optionEnvironment.size()).append("]=")
                .append(sortedKv(optionEnvironment)).append('\n');

        r.append("\n[profiles]\n");
        profiles.forEach((name, tokens) -> r.append(name).append(" = ").append(String.join(" ", tokens)).append('\n'));

        r.append("\n[options] ").append(options.size()).append(" discovered, ")
                .append(userFacingOptions.size()).append(" user-facing\n");
        if (!undeclaredOptionNames.isEmpty()) {
            r.append("  undeclaredNames=").append(undeclaredOptionNames).append('\n');
        }
        for (OptionDiscovery.OptionDecl o : options) {
            r.append("  ").append(o.name())
                    .append(o.enabledByDefault() ? " default=" : " disabledDefault")
                    .append(o.value() == null ? "<off>" : o.value());
            if (o.conditional()) {
                r.append(" (conditional)");
            }
            if (!o.allowedValues().isEmpty()) {
                r.append(" values=").append(o.allowedValues());
            }
            r.append(" @").append(o.at()).append('\n');
        }

        r.append("\n[conditionSymbols] ").append(conditionSymbols.size()).append('\n');
        for (String s : new TreeSet<>(conditionSymbols)) {
            r.append("  ").append(s).append('\n');
        }

        r.append("\n[dimensions]\n");
        for (DimensionPlan dim : dimensions) {
            r.append(dim.worldKey()).append(" ids=").append(dim.dimensionIds()).append('\n');
            for (ProgramEntry p : dim.programs()) {
                r.append("  ").append(p.name()).append(" family=").append(p.family())
                        .append(" stages=").append(stageList(p));
                if (p.enabledCondition() != null) {
                    r.append(" enabledIf='").append(p.enabledCondition()).append("'")
                            .append(" enabledByDefault=").append(p.enabledByDefault());
                }
                if (!p.directives().isEmpty()) {
                    r.append(" directives=").append(p.directives());
                }
                r.append(" files=").append(p.filesTouched()).append('\n');
            }
            if (!dim.inheritedFromBase().isEmpty()) {
                r.append("  inheritedFromBase=").append(dim.inheritedFromBase()).append('\n');
            }
        }

        r.append("\n[properties]\n");
        propertiesSummaries.forEach((file, summary) ->
                r.append(file).append(" entries=").append(summary.entryCount())
                        .append(" keys=").append(summary.keys()).append('\n'));

        r.append("\n[features]\n");
        r.append("active=").append(new TreeSet<>(activeFeatures)).append('\n');
        r.append("conditional=").append(new TreeSet<>(conditionalFeatures)).append('\n');

        r.append("\n[diagnostics] ").append(diagnostics.size()).append('\n');
        for (Diagnostic d : diagnostics) {
            r.append("  ").append(d).append('\n');
        }
        return r.toString();
    }

    private static String stageList(ProgramEntry p) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<Stage, PackPath> e : p.stages().entrySet()) {
            if (!first) {
                sb.append(' ');
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.append(']').toString();
    }

    private static String sortedKv(Map<String, String> map) {
        TreeMap<String, String> sorted = new TreeMap<>(map);
        StringBuilder sb = new StringBuilder("{");
        sorted.forEach((k, v) -> sb.append(k).append('=').append(v).append(", "));
        if (sb.length() > 1) {
            sb.setLength(sb.length() - 2);
        }
        return sb.append('}').toString();
    }
}
