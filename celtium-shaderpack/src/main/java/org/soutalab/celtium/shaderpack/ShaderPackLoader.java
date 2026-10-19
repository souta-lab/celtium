package org.soutalab.celtium.shaderpack;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * S1 CPU-only OptiFine/Iris-style shaderpack frontend.
 *
 * <p>Reads a pack (zip or directory) into an immutable {@link PackManifest}:
 * dimension folders, program/stage inventory with base fallback, include
 * resolution and conditional evaluation, properties census, option/profile
 * census, comment-directive extraction and a sorted diagnostic list with
 * explicit supported/unsupported codes. No GL/Vulkan objects are created and
 * no rendering is attempted - the output is a plan, not a pipeline.</p>
 */
public final class ShaderPackLoader {

    private static final String SHADERS_ROOT = "/shaders/";
    private static final Pattern WORLD_DIR = Pattern.compile("^/shaders/(world-?\\d+)/.*$");
    private static final Pattern COMMENT_DIRECTIVE =
            Pattern.compile("/\\*\\s*([A-Z][A-Z0-9_]*)\\s*:\\s*([^*]+?)\\s*\\*/");
    private static final Pattern CONST_DECL =
            Pattern.compile("(?:^|[^\\w$])const\\s+(?:bool|int|uint|float|vec\\d|mat\\d)\\s+" +
                    "([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*([^;]+);");
    private static final Pattern RAW_PROPERTY_KEY =
            Pattern.compile("^\\s*([A-Za-z][A-Za-z0-9_.]*)\\s*=", Pattern.MULTILINE);
    private static final int MAX_DIRECTIVES_PER_PROGRAM = 64;

    public static PackManifest load(Path input) throws PackException {
        return load(input, LoadOptions.defaults());
    }

    /**
     * Expands one file of a pack and returns its GLSL text: includes resolved, macros from {@code seeds} applied.
     * <p>
     * The loader expands every program while building the manifest and keeps the directives it found, not the text. The
     * renderer needs the text itself - a program whose {@code #include}s are still in it is not GLSL, so nothing
     * downstream can lower or compile it. This is that door, using the same preprocessor and the same bounds.
     *
     * @param input pack zip or extracted directory
     * @param file  the file to expand, under {@code shaders/}
     * @param seeds object-like macros to define before expansion, e.g. the pack's own enabled options
     */
    public static String expand(Path input, PackPath file, Map<String, String> seeds) throws PackException {
        List<Diagnostic> diagnostics = new ArrayList<>();
        PackSource pack = PackSource.open(input, LoadOptions.defaults().limits(), diagnostics);
        SourcePreprocessor preprocessor = new SourcePreprocessor(pack, LoadOptions.defaults().limits(), diagnostics,
                SourcePreprocessor.Mode.GLSL);
        preprocessor.keepDefines();
        Map<String, String> environment = new TreeMap<>(seeds);
        SourcePreprocessor.ExpandedSource expanded = preprocessor.expand(file, environment);
        StringBuilder text = new StringBuilder();
        for (SourcePreprocessor.ExpandedLine line : expanded.lines) {
            text.append(line.text()).append('\n');
        }
        if (diagnostics.stream().anyMatch(d -> d.severity() == Severity.ERROR)) {
            Diagnostic first = diagnostics.stream().filter(d -> d.severity() == Severity.ERROR).findFirst().orElseThrow();
            throw new PackException(first.code(), first.message());
        }
        return text.toString();
    }

    public static PackManifest load(Path input, LoadOptions options) throws PackException {
        PackLimits limits = options.limits();
        List<Diagnostic> diagnostics = new ArrayList<>();
        PackSource pack = PackSource.open(input, limits, diagnostics);

        List<PackPath> files = pack.files();
        if (files.isEmpty()) {
            throw new PackException(DiagnosticCode.NO_SHADER_ROOT,
                    "no files under shaders/ in " + input);
        }

        // ---- seeds and option environment -------------------------------
        Map<String, String> seeds = new TreeMap<>();
        seeds.put("MC_VERSION", Integer.toString(options.minecraftVersion()));
        seeds.putAll(options.extraDefines());

        Map<String, OptionDiscovery.OptionDecl> optionDecls = OptionDiscovery.scan(pack, diagnostics);
        Map<String, String> lexicalDefaults = new TreeMap<>();
        for (OptionDiscovery.OptionDecl d : optionDecls.values()) {
            if (!d.conditional() && d.enabledByDefault() && d.value() != null) {
                lexicalDefaults.put(d.name(), d.value());
            }
        }

        // Profiles come from a seeds-only pass over shaders.properties: profile
        // lines are not realistically conditional, and seeding the option env
        // here would make profile selection circular.
        Map<String, String> propsSeeds = new TreeMap<>(seeds);
        Map<String, List<String>> profiles = new TreeMap<>();
        PackPath shaderPropsPath = PackPath.absolute(SHADERS_ROOT + "shaders.properties");
        if (pack.exists(shaderPropsPath)) {
            SourcePreprocessor first = new SourcePreprocessor(pack, limits, diagnostics,
                    SourcePreprocessor.Mode.PROPERTIES);
            SourcePreprocessor.ExpandedSource pass = first.expand(shaderPropsPath, propsSeeds);
            for (PackProperties.Entry e : PackProperties.parse(shaderPropsPath, pass.lines, diagnostics).entries()) {
                if (e.key().startsWith("profile.")) {
                    String name = e.key().substring("profile.".length());
                    profiles.put(name, List.of(e.value().strip().split("\\s+")));
                }
            }
        }

        Map<String, String> optionEnv = new TreeMap<>(lexicalDefaults);
        String selectedProfile = options.profile();
        if (selectedProfile != null) {
            applyProfile(profiles, selectedProfile, optionEnv, diagnostics, 8);
        }
        options.optionOverrides().forEach((name, value) -> {
            if (value == null) {
                optionEnv.remove(name);
            } else {
                optionEnv.put(name, value);
            }
        });
        Map<String, String> evalSeeds = new TreeMap<>(seeds);
        evalSeeds.putAll(optionEnv);

        // ---- properties census ------------------------------------------
        Map<String, PackManifest.PropertiesSummary> propertiesSummaries = new TreeMap<>();
        Map<String, Set<String>> potentialKeys = new TreeMap<>();
        Map<String, String> enabledConditions = new TreeMap<>(); // "worldKey/name" -> expr
        Map<String, List<String>> dimensionBindings = new TreeMap<>();
        Set<FeatureFlag> active = new TreeSet<>();
        Set<FeatureFlag> conditional = new TreeSet<>();
        Set<String> conditionSymbols = new TreeSet<>();
        Set<String> uiOptionTokens = new TreeSet<>(); // names on screen/sliders/profile lines

        for (PackPath file : files) {
            String name = file.name();
            if (!name.endsWith(".properties")) {
                continue;
            }
            SourcePreprocessor pp = new SourcePreprocessor(pack, limits, diagnostics,
                    SourcePreprocessor.Mode.PROPERTIES);
            SourcePreprocessor.ExpandedSource expanded = pp.expand(file, evalSeeds);
            conditionSymbols.addAll(expanded.conditionIdentifiers);
            PackProperties props = PackProperties.parse(file, expanded.lines, diagnostics);
            propertiesSummaries.put(file.toString(), summarize(props));

            // Keys hidden behind disabled options still say something about the
            // pack - collect them lexically for the conditional feature census.
            potentialKeys.put(file.toString(), rawPropertyKeys(pack, file));

            for (PackProperties.Entry e : props.entries()) {
                classifyProperty(file, e, enabledConditions, dimensionBindings, active, diagnostics);
                if (e.key().equals("screen") || e.key().startsWith("screen.")
                        || e.key().equals("sliders") || e.key().startsWith("profile.")) {
                    collectUiTokens(e.value(), uiOptionTokens);
                }
            }
        }
        // Potential features: keys that only exist behind dead conditionals.
        for (Map.Entry<String, Set<String>> e : potentialKeys.entrySet()) {
            Set<String> live = new TreeSet<>(propertiesSummaries.get(e.getKey()).keys());
            for (String key : e.getValue()) {
                if (!live.contains(key)) {
                    classifyPotential(key, conditional);
                }
            }
        }

        // ---- program enumeration ----------------------------------------
        Map<String, Map<String, EnumMap<Stage, PackPath>>> dirPrograms = new TreeMap<>();
        for (PackPath file : files) {
            String parent = file.parent().toString() + "/";
            boolean atBase = parent.equals("/shaders/");
            Matcher world = WORLD_DIR.matcher(file.toString());
            boolean atWorld = world.matches() && parent.equals("/shaders/" + world.group(1) + "/");
            if (!atBase && !atWorld) {
                continue; // lib/, program/, tex/, deeper folders: include material, not entry points
            }
            String dirKey = atBase ? "base" : world.group(1);
            String fileName = file.name();
            Stage.ofFileName(fileName).ifPresentOrElse(stage -> {
                String base = Stage.baseName(fileName);
                dirPrograms.computeIfAbsent(dirKey, k -> new TreeMap<>())
                        .computeIfAbsent(base, k -> new EnumMap<>(Stage.class))
                        .put(stage, file);
            }, () -> {
                if (fileName.endsWith(".json") || fileName.startsWith("voxy")) {
                    conditional.add(FeatureFlag.VOXY_PROGRAMS);
                }
            });
        }

        // ---- per-dimension plans ----------------------------------------
        List<String> worldKeys = dirPrograms.keySet().stream()
                .filter(k -> !k.equals("base"))
                .sorted(ShaderPackLoader::worldOrder)
                .toList();
        Map<String, EnumMap<Stage, PackPath>> basePrograms =
                dirPrograms.getOrDefault("base", Map.of());

        List<DimensionPlan> dimensions = new ArrayList<>();
        if (dirPrograms.containsKey("base")) {
            dimensions.add(buildPlan("base", basePrograms, Map.of(), enabledConditions,
                    dimensionBindings, evalSeeds, pack, limits, diagnostics, conditionSymbols, active));
        }
        for (String worldKey : worldKeys) {
            Map<String, EnumMap<Stage, PackPath>> own = dirPrograms.get(worldKey);
            dimensions.add(buildPlan(worldKey, own, basePrograms, enabledConditions,
                    dimensionBindings, evalSeeds, pack, limits, diagnostics, conditionSymbols, active));
        }

        boolean anyPrograms = dirPrograms.values().stream().anyMatch(m -> !m.isEmpty());
        if (!anyPrograms) {
            diagnostics.add(Diagnostic.error(DiagnosticCode.NO_SHADER_ROOT, SourceLocation.file("/shaders"),
                    "shaders/ contains no program sources"));
        }

        // ---- features from program inventory -----------------------------
        for (DimensionPlan dim : dimensions) {
            for (ProgramEntry p : dim.programs()) {
                switch (p.family()) {
                    case DH -> active.add(FeatureFlag.DH_PROGRAMS);
                    case VOXY -> active.add(FeatureFlag.VOXY_PROGRAMS);
                    case UNKNOWN -> {
                        active.add(FeatureFlag.UNKNOWN_PROGRAM_NAMES);
                        diagnostics.add(Diagnostic.warning(DiagnosticCode.UNKNOWN_PROGRAM_NAME,
                                SourceLocation.file(firstStagePath(p)), "program name outside known families: " + p.name()));
                    }
                    default -> {
                    }
                }
                for (Stage s : p.stages().keySet()) {
                    switch (s) {
                        case COMPUTE -> active.add(FeatureFlag.COMPUTE_STAGE);
                        case GEOMETRY -> active.add(FeatureFlag.GEOMETRY_STAGE);
                        case TESS_CONTROL, TESS_EVAL -> active.add(FeatureFlag.TESSELLATION_STAGE);
                        default -> {
                        }
                    }
                }
            }
        }
        if (!profiles.isEmpty()) {
            active.add(FeatureFlag.PROFILES);
        }
        if (!enabledConditions.isEmpty()) {
            active.add(FeatureFlag.PROGRAM_ENABLE_CONDITIONS);
        }
        if (!dimensionBindings.isEmpty()) {
            active.add(FeatureFlag.DIMENSION_MAP);
        }

        // Which of the declared options are actually user-facing: on a screen,
        // in sliders, in a profile, or gating a program through
        // program.*.enabled. The rest are internal defines by convention.
        Set<String> referencedOptionNames = new TreeSet<>(uiOptionTokens);
        for (String cond : enabledConditions.values()) {
            referencedOptionNames.addAll(ExpressionEvaluator.collectIdentifiers(cond));
        }
        Set<String> declaredNames = optionDecls.keySet();
        Set<String> userFacing = new TreeSet<>(referencedOptionNames);
        userFacing.retainAll(declaredNames);
        Set<String> undeclared = new TreeSet<>(referencedOptionNames);
        undeclared.removeAll(declaredNames);
        undeclared.removeAll(OptionDiscovery.INTERNAL_NAMES);

        // ---- manifest -----------------------------------------------------
        List<Diagnostic> sorted = new ArrayList<>(new TreeSet<>(diagnostics));

        return new PackManifest(
                pack.label(),
                contentHash(pack),
                files.size(),
                pack.skippedOutsideShaders(),
                seeds,
                selectedProfile,
                optionEnv,
                new ArrayList<>(optionDecls.values()),
                profiles,
                userFacing,
                undeclared,
                conditionSymbols,
                dimensions,
                propertiesSummaries,
                active,
                conditional,
                sorted);
    }

    // ------------------------------------------------------------------

    private static DimensionPlan buildPlan(String worldKey,
                                           Map<String, EnumMap<Stage, PackPath>> own,
                                           Map<String, EnumMap<Stage, PackPath>> base,
                                           Map<String, String> enabledConditions,
                                           Map<String, List<String>> dimensionBindings,
                                           Map<String, String> evalSeeds,
                                           PackSource pack, PackLimits limits,
                                           List<Diagnostic> allDiagnostics,
                                           Set<String> conditionSymbols,
                                           Set<FeatureFlag> active) {
        List<ProgramEntry> programs = new ArrayList<>();
        List<String> inherited = new ArrayList<>();
        Set<String> names = new TreeSet<>(own.keySet());
        names.addAll(base.keySet());
        for (String name : names) {
            EnumMap<Stage, PackPath> stageFiles = new EnumMap<>(Stage.class);
            EnumMap<Stage, PackPath> baseStages = base.getOrDefault(name, new EnumMap<>(Stage.class));
            EnumMap<Stage, PackPath> ownStages = own.getOrDefault(name, new EnumMap<>(Stage.class));
            stageFiles.putAll(baseStages);
            stageFiles.putAll(ownStages);
            if (ownStages.isEmpty() && !baseStages.isEmpty()) {
                inherited.add(name);
            }

            List<Diagnostic> programDiags = new ArrayList<>();
            Map<String, List<String>> directives = new TreeMap<>();
            Set<PackPath> touched = new java.util.LinkedHashSet<>();
            SourcePreprocessor.ExpandedSource combined = new SourcePreprocessor.ExpandedSource();
            // One preprocessor per program so expansion/depth bounds apply to the
            // program as a whole, not per stage file.
            SourcePreprocessor pp = new SourcePreprocessor(pack, limits, programDiags,
                    SourcePreprocessor.Mode.GLSL);

            for (Map.Entry<Stage, PackPath> s : stageFiles.entrySet()) {
                Stage stage = s.getKey();
                if (stage == Stage.COMPUTE) {
                    programDiags.add(Diagnostic.unsupported(DiagnosticCode.UNSUPPORTED_STAGE_COMPUTE,
                            SourceLocation.file(s.getValue().toString()),
                            "compute stage " + name + ".csh - not in the S1 native scope"));
                    active.add(FeatureFlag.COMPUTE_STAGE);
                } else if (stage == Stage.GEOMETRY) {
                    programDiags.add(Diagnostic.unsupported(DiagnosticCode.UNSUPPORTED_STAGE_GEOMETRY,
                            SourceLocation.file(s.getValue().toString()),
                            "geometry stage " + name + ".gsh - not in the S1 native scope"));
                    active.add(FeatureFlag.GEOMETRY_STAGE);
                } else if (stage == Stage.TESS_CONTROL || stage == Stage.TESS_EVAL) {
                    programDiags.add(Diagnostic.unsupported(DiagnosticCode.UNSUPPORTED_STAGE_TESSELLATION,
                            SourceLocation.file(s.getValue().toString()),
                            "tessellation stage " + name + " - not in the S1 native scope"));
                    active.add(FeatureFlag.TESSELLATION_STAGE);
                }
                try {
                    SourcePreprocessor.ExpandedSource src = pp.expand(s.getValue(), evalSeeds);
                    combined.lines.addAll(src.lines);
                    combined.filesTouched.addAll(src.filesTouched);
                    combined.conditionIdentifiers.addAll(src.conditionIdentifiers);
                } catch (PackException e) {
                    programDiags.add(Diagnostic.error(e.code(),
                            SourceLocation.file(s.getValue().toString()), e.getMessage()));
                }
            }
            touched.addAll(combined.filesTouched);
            conditionSymbols.addAll(combined.conditionIdentifiers);
            collectDirectives(combined, directives, programDiags);

            // Pairing check: graphics programs need vertex+fragment.
            if (!stageFiles.containsKey(Stage.COMPUTE) && stageFiles.containsKey(Stage.VERTEX)
                    != stageFiles.containsKey(Stage.FRAGMENT)) {
                programDiags.add(Diagnostic.warning(DiagnosticCode.STAGE_WITHOUT_PAIR,
                        SourceLocation.file(firstPath(stageFiles)),
                        name + ": vertex/fragment pair is incomplete"));
            }

            String cond = enabledConditions.get(worldKey + "/" + name);
            if (cond == null) {
                cond = enabledConditions.get(name);
            }
            Boolean enabled = null;
            if (cond != null) {
                enabled = ExpressionEvaluator.evalBoolean(cond, evalSeeds);
                if (enabled == null) {
                    programDiags.add(Diagnostic.error(DiagnosticCode.EXPRESSION_ERROR,
                            SourceLocation.file("/shaders/shaders.properties"),
                            "cannot evaluate enabled condition '" + cond + "' for " + name));
                }
            }

            programDiags.sort(null);
            allDiagnostics.addAll(programDiags);
            programs.add(new ProgramEntry(name, ProgramFamily.of(name), stageFiles,
                    cond, enabled, directives, touched.size(), programDiags));
        }
        programs.sort(null);
        List<String> dimIds = dimensionBindings.getOrDefault(worldKey, List.of());
        return new DimensionPlan(worldKey, dimIds, programs, inherited);
    }

    private static void collectDirectives(SourcePreprocessor.ExpandedSource src,
                                          Map<String, List<String>> directives,
                                          List<Diagnostic> diags) {
        for (SourcePreprocessor.ExpandedLine line : src.lines) {
            String text = line.text();
            if (text.indexOf('/') < 0 && !text.contains("const")) {
                continue;
            }
            Matcher m = COMMENT_DIRECTIVE.matcher(text);
            while (m.find()) {
                addDirective(directives, m.group(1), m.group(2).strip(), diags);
            }
            Matcher c = CONST_DECL.matcher(text);
            while (c.find()) {
                addDirective(directives, "const " + c.group(1), c.group(2).strip(), diags);
            }
        }
    }

    private static void addDirective(Map<String, List<String>> map, String key, String value,
                                     List<Diagnostic> diags) {
        List<String> values = map.computeIfAbsent(key, k -> new ArrayList<>());
        if (values.size() >= MAX_DIRECTIVES_PER_PROGRAM) {
            return;
        }
        if (!values.contains(value)) {
            values.add(value);
        }
    }

    private static void classifyProperty(PackPath file, PackProperties.Entry e,
                                         Map<String, String> enabledConditions,
                                         Map<String, List<String>> dimensionBindings,
                                         Set<FeatureFlag> active, List<Diagnostic> diagnostics) {
        String key = e.key();
        SourceLocation at = e.location();
        if (key.startsWith("image.")) {
            active.add(FeatureFlag.CUSTOM_IMAGES);
            diagnostics.add(Diagnostic.unsupported(DiagnosticCode.UNSUPPORTED_FEATURE_CUSTOM_IMAGES, at,
                    "custom image declaration " + key + " (storage images are out of the S1 scope)"));
        } else if (key.startsWith("iris.features.")) {
            active.add(FeatureFlag.IRIS_FEATURE_FLAGS);
            diagnostics.add(Diagnostic.unsupported(DiagnosticCode.IRIS_FEATURE_DECLARATION, at,
                    key + "=" + e.value()));
        } else if (key.startsWith("uniform.") || key.startsWith("variable.")) {
            active.add(FeatureFlag.CUSTOM_UNIFORMS);
            diagnostics.add(Diagnostic.warning(DiagnosticCode.CUSTOM_UNIFORM_EXPRESSION, at,
                    key + " - recorded; expression evaluation is an S2+ concern"));
        } else if (key.startsWith("texture.")) {
            active.add(FeatureFlag.CUSTOM_TEXTURES);
        } else if (key.startsWith("alphaTest.")) {
            active.add(FeatureFlag.ALPHA_TEST_OVERRIDES);
        } else if (key.startsWith("blend.")) {
            active.add(FeatureFlag.BLEND_OVERRIDES);
        } else if (key.startsWith("size.") || key.startsWith("scale.")) {
            active.add(FeatureFlag.BUFFER_SIZE_OVERRIDES);
        } else if (key.startsWith("flip.")) {
            active.add(FeatureFlag.TARGET_FLIP_DIRECTIVES);
        } else if (key.startsWith("program.") && key.endsWith(".enabled")) {
            enabledConditions.put(key.substring("program.".length(), key.length() - ".enabled".length()),
                    e.value());
        } else if (key.startsWith("dimension.")) {
            dimensionBindings.put(key.substring("dimension.".length()),
                    List.of(e.value().strip().split("\\s+")));
        } else if (isKnownPropertyKey(key)) {
            // recognized, no feature flag needed
        } else {
            diagnostics.add(Diagnostic.warning(DiagnosticCode.UNKNOWN_PROPERTY_KEY, at,
                    "unrecognized key " + key));
        }
    }

    private static void classifyPotential(String key, Set<FeatureFlag> conditional) {
        if (key.startsWith("image.")) {
            conditional.add(FeatureFlag.CUSTOM_IMAGES);
        } else if (key.startsWith("uniform.") || key.startsWith("variable.")) {
            conditional.add(FeatureFlag.CUSTOM_UNIFORMS);
        } else if (key.startsWith("texture.")) {
            conditional.add(FeatureFlag.CUSTOM_TEXTURES);
        } else if (key.startsWith("iris.features.")) {
            conditional.add(FeatureFlag.IRIS_FEATURE_FLAGS);
        } else if (key.startsWith("program.") && key.endsWith(".enabled")) {
            conditional.add(FeatureFlag.PROGRAM_ENABLE_CONDITIONS);
        }
    }

    private static boolean isKnownPropertyKey(String key) {
        if (key.startsWith("screen") || key.equals("sliders") || key.startsWith("profile.")) {
            return true;
        }
        if (key.startsWith("entity.") || key.startsWith("item.") || key.startsWith("block.")) {
            return true;
        }
        return switch (key) {
            case "clouds", "moon", "sun", "stars", "rain", "snow", "vignette",
                 "oldLighting", "oldHandLight", "dynamicHandLight", "separateAo",
                 "underwaterOverlay", "particles.ordering", "breaksAnisotropy",
                 "frustum.culling", "beacon.beam.depth", "shadowEntities",
                 "shadowBlockEntities", "shadow.culling", "dhShadow.enabled", "dhClouds",
                 "wetnessHalflife", "drynessHalflife", "centerDepthHalflife",
                 "eyeBrightnessHalflife", "rainStrength", "screenBrightnessGamma",
                 "handHeld", "sunPathRotation" -> true;
            default -> false;
        };
    }

    /**
     * Extracts option-name tokens from screen/sliders/profile values:
     * {@code <empty>}, {@code <profile>} and {@code [SECTION]} are structural,
     * {@code !NAME} is an off-toggle, {@code NAME=value} is an assignment,
     * {@code profile.X} is a nested profile reference (not an option).
     */
    private static void collectUiTokens(String value, Set<String> sink) {
        for (String token : value.strip().split("\\s+")) {
            if (token.isEmpty() || token.startsWith("<") || token.startsWith("[")) {
                continue;
            }
            if (token.startsWith("!")) {
                token = token.substring(1);
            }
            int eq = token.indexOf('=');
            if (eq >= 0) {
                token = token.substring(0, eq);
            }
            if (token.startsWith("profile.") || token.isEmpty()) {
                continue;
            }
            sink.add(token);
        }
    }

    private static PackManifest.PropertiesSummary summarize(PackProperties props) {
        List<String> keys = props.entries().stream().map(PackProperties.Entry::key).distinct().sorted().toList();
        return new PackManifest.PropertiesSummary(props.entries().size(), keys);
    }

    private static Set<String> rawPropertyKeys(PackSource pack, PackPath file) {
        Set<String> keys = new TreeSet<>();
        try {
            String text = new String(pack.read(file), java.nio.charset.StandardCharsets.UTF_8);
            Matcher m = RAW_PROPERTY_KEY.matcher(text);
            while (m.find()) {
                keys.add(m.group(1));
            }
        } catch (PackException ignored) {
        }
        return keys;
    }

    private static void applyProfile(Map<String, List<String>> profiles, String name,
                                     Map<String, String> env, List<Diagnostic> diagnostics, int depth) {
        if (depth <= 0) {
            diagnostics.add(Diagnostic.error(DiagnosticCode.EXPANSION_LIMIT_EXCEEDED,
                    SourceLocation.file("/shaders/shaders.properties"), "profile nesting too deep"));
            return;
        }
        List<String> tokens = profiles.get(name);
        if (tokens == null) {
            diagnostics.add(Diagnostic.error(DiagnosticCode.MALFORMED_PROPERTY_LINE,
                    SourceLocation.file("/shaders/shaders.properties"),
                    "selected profile '" + name + "' is not declared"));
            return;
        }
        for (String token : tokens) {
            if (token.startsWith("profile.")) {
                applyProfile(profiles, token.substring("profile.".length()), env, diagnostics, depth - 1);
            } else if (token.startsWith("!")) {
                env.remove(token.substring(1));
            } else {
                int eq = token.indexOf('=');
                if (eq >= 0) {
                    env.put(token.substring(0, eq), token.substring(eq + 1));
                } else {
                    env.put(token, "1");
                }
            }
        }
    }

    private static int worldOrder(String a, String b) {
        return Integer.compare(worldNumber(a), worldNumber(b));
    }

    private static int worldNumber(String key) {
        try {
            return Integer.parseInt(key.substring("world".length()));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static String firstStagePath(ProgramEntry p) {
        return p.stages().values().iterator().next().toString();
    }

    private static String firstPath(Map<Stage, PackPath> stages) {
        return stages.values().iterator().next().toString();
    }

    private static String contentHash(PackSource pack) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            for (PackPath p : pack.files()) {
                sha.update(p.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                sha.update((byte) 0);
                try {
                    sha.update(pack.read(p));
                } catch (PackException e) {
                    // listed at open; unreachable
                }
                sha.update((byte) 0);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : sha.digest()) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return "<sha-256 unavailable>";
        }
    }

    private ShaderPackLoader() {
    }
}
