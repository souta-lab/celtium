package org.soutalab.celtium.shaderpack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The first S2 lowering pass: OptiFine-era GLSL to the Vulkan-GLSL interface a pipeline can be built from.
 * <p>
 * The preprocessor has already expanded includes and applied the pack's options by the time this runs, so what is left
 * is the part that made the source compile against a compatibility profile: {@code attribute}/{@code varying},
 * {@code gl_FragColor}, the fixed-function matrices and attributes, {@code texture2D}, and {@code ftransform()}. Each
 * becomes something a Vulkan pipeline understands - a location-qualified interface variable, an output, a member of a
 * transform block, or a plain function.
 * <p>
 * Deliberately line- and comment-aware rather than a full parser: the frontend's {@link GlslComments} keeps comments out
 * of the rewritten text, and a rewrite that would need real parsing (expressions, scoping) is refused with a diagnostic
 * instead of guessed. What this pass does not do is equally deliberate and is reported by {@link Result#unsupported()}:
 * {@code gl_TexCoord}, {@code gl_FrontColor} and the other fixed-function interpolants, arrayed attributes, and
 * {@code #extension}/{@code #pragma} handling all need decisions that belong to later stages.
 *
 * <p>Not yet wired to the loader: the interface variables this pass assigns locations to are what a pipeline has to be
 * created with, so the wiring happens together with the program-plan-to-pipeline translation.</p>
 */
public final class LegacyGlslLowering {
    /** The transform block the fixed-function matrices are lowered into. */
    public static final String TRANSFORM_BLOCK = "CeltiumTransforms";
    /** The instance name the block's members are reached through. */
    public static final String TRANSFORM_INSTANCE = "celtium_transforms";
    /** The fragment output {@code gl_FragColor} becomes. */
    public static final String FRAGMENT_COLOUR = "celtium_FragColour";
    /** The fragment output array {@code gl_FragData[i]} becomes. */
    public static final String FRAGMENT_DATA = "celtium_FragData";
    /** The vertex attribute {@code gl_Vertex} becomes, and the location it takes unless the caller maps it. */
    public static final String ATTRIBUTE_POSITION = "a_Position";

    private static final Map<String, String> BUILTIN_ATTRIBUTES = builtinAttributes();
    private static final Map<String, String> BUILTIN_MATRICES = builtinMatrices();
    private static final Map<String, String> TEXTURE_FUNCTIONS = textureFunctions();
    /** Names a sampler uniform cannot keep once the legacy calls are rewritten to the Vulkan GLSL built-ins. */
    private static final java.util.Set<String> RESERVED_FUNCTION_NAMES =
            java.util.Set.of("texture", "textureLod", "textureGrad", "textureProj");

    private LegacyGlslLowering() {
    }

    /** The lowered source and what it declares, so a pipeline can be built against it without re-parsing the text. */
    public record Result(String source, Map<String, Integer> attributes, List<String> varyings, List<String> uniforms,
                         Map<String, Integer> samplers, List<String> unsupported, int originalVersion,
                         String usedTransformBlock) {
    }

    private static Map<String, String> builtinAttributes() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("gl_Vertex", "vec4 a_Position");
        map.put("gl_Normal", "vec3 a_Normal");
        map.put("gl_Color", "vec4 a_Colour");
        for (int unit = 0; unit < 8; unit++) {
            map.put("gl_MultiTexCoord" + unit, "vec4 a_TexCoord" + unit);
        }
        return map;
    }

    private static Map<String, String> builtinMatrices() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("gl_ModelViewProjectionMatrix", "mat4 u_ModelViewProjection");
        map.put("gl_ModelViewMatrix", "mat4 u_ModelView");
        map.put("gl_ProjectionMatrix", "mat4 u_Projection");
        map.put("gl_NormalMatrix", "mat3 u_NormalMatrix");
        map.put("gl_TextureMatrix", "mat4 u_TextureMatrix[8]");
        return map;
    }

    private static Map<String, String> textureFunctions() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("texture2D", "texture");
        map.put("texture3D", "texture");
        map.put("textureCube", "texture");
        map.put("shadow2D", "texture");
        map.put("texture2DLod", "textureLod");
        map.put("texture3DLod", "textureLod");
        map.put("textureCubeLod", "textureLod");
        map.put("shadow2DLod", "textureLod");
        return map;
    }

    /**
     * Lowers one stage.
     *
     * @param stage     the stage whose interface rules apply ({@code varying} is {@code out} in vertex, {@code in} in
     *                  fragment)
     * @param source    preprocessed GLSL: includes expanded, option macros applied
     * @param locations attribute locations to use, by name without the {@code gl_} form; missing entries get the next
     *                  free location after the highest one given, and the built-in names default to the same order
     *                  every pack uses (position, normal, colour, texcoords)
     */
    public static Result lower(Stage stage, String source, Map<String, Integer> locations) {
        return lower(stage, source, locations, Map.of());
    }

    /**
     * Lowers one stage with explicit sampler bindings too.
     *
     * @param bindings sampler bindings by uniform name; missing entries take the next free binding, because Vulkan GLSL
     *                 requires an explicit {@code layout(binding = N)} on every opaque uniform - the compile-side test
     *                 is what made that a requirement here rather than a later stage's problem
     */
    public static Result lower(Stage stage, String source, Map<String, Integer> locations,
                               Map<String, Integer> bindings) {
        Map<String, Integer> assigned = new LinkedHashMap<>(locations);
        List<String> unsupported = new ArrayList<>();
        List<String> uniforms = new ArrayList<>();
        List<String> varyings = new ArrayList<>();
        List<String> attributeDeclarations = new ArrayList<>();
        Map<String, String> attributeByBuiltin = new LinkedHashMap<>();
        Map<String, Integer> samplerBindings = new LinkedHashMap<>(bindings);
        int nextBinding = 0;
        for (Integer used : samplerBindings.values()) {
            nextBinding = Math.max(nextBinding, used.intValue() + 1);
        }
        boolean usesTransforms = false;
        boolean usesFragmentColour = false;
        boolean usesFragmentData = false;
        int originalVersion = 0;

        List<String> lines = List.of(source.split("\n", -1));

        // A pre-scan for opaque uniforms, because a sampler's name has to be settled before any line is emitted: the
        // first version renamed the declaration as it passed and left the call sites saying `texture(texture, uv)`,
        // which the compiler resolves to the variable and rejects. Bindings are assigned here for the same reason -
        // every opaque uniform needs one, and a later line may sample a sampler declared earlier.
        Map<String, String> samplerRenames = new LinkedHashMap<>();
        GlslComments prescan = new GlslComments();
        for (String line : lines) {
            String declaration = samplerDeclaration(prescan.split(line).code());
            if (declaration == null) {
                continue;
            }
            String[] parts = declaration.split(" ");
            String declared = parts[2];
            String effective = RESERVED_FUNCTION_NAMES.contains(declared) ? "celtium_sampler_" + declared : declared;
            samplerRenames.put(declared, effective);
            if (!bindings.containsKey(effective) && !samplerBindings.containsKey(effective)) {
                samplerBindings.put(effective, Integer.valueOf(nextBinding++));
            }
        }
        List<String> out = new ArrayList<>(lines.size() + 16);
        GlslComments comments = new GlslComments();
        int nextLocation = 0;
        for (Integer used : assigned.values()) {
            nextLocation = Math.max(nextLocation, used.intValue() + 1);
        }

        for (String rawLine : lines) {
            GlslComments.Split split = comments.split(rawLine);
            String code = split.code();
            String comment = split.comment();
            String trimmed = code.trim();

            if (trimmed.startsWith("#version")) {
                String[] parts = trimmed.split("\\s+");
                if (parts.length > 1) {
                    try {
                        originalVersion = Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException unreadable) {
                        unsupported.add("#version field is not a number: " + parts[1]);
                    }
                }
                // The pass emits its own #version, and the block declaration has to follow it.
                out.add("#version 450");
                continue;
            }
            if (trimmed.startsWith("#extension") || trimmed.startsWith("#pragma")) {
                // OptiFine's GL_* compatibility extensions have no Vulkan equivalent; keeping them out is a decision,
                // not a silent drop, so the line is reported.
                if (trimmed.startsWith("#extension")) {
                    unsupported.add(trimmed);
                }
                out.add(rawLine);
                continue;
            }

            String lowered = code;
            boolean attributeLine = false;
            boolean varyingLine = false;

            // attribute / varying declarations.
            if (trimmed.startsWith("attribute ")) {
                String rest = trimmed.substring("attribute ".length()).trim();
                int nameStart = lastIdentifierStart(rest);
                if (nameStart <= 0) {
                    unsupported.add("attribute declaration could not be read: " + trimmed);
                    out.add(rawLine);
                    continue;
                }
                String type = rest.substring(0, nameStart).trim();
                String name = rest.substring(nameStart).replace(";", "").trim();
                int location = assigned.computeIfAbsent(name, key -> Integer.valueOf(-1)).intValue();
                if (location < 0) {
                    location = nextLocation++;
                    assigned.put(name, Integer.valueOf(location));
                }
                attributeDeclarations.add(name);
                out.add("layout(location = " + location + ") in " + type + " " + name + ";"
                        + (comment.isEmpty() ? "" : " //" + comment));
                attributeLine = true;
            } else if (trimmed.startsWith("varying ")) {
                String rest = trimmed.substring("varying ".length()).trim();
                int nameStart = lastIdentifierStart(rest);
                if (nameStart <= 0) {
                    unsupported.add("varying declaration could not be read: " + trimmed);
                    out.add(rawLine);
                    continue;
                }
                String type = rest.substring(0, nameStart).trim();
                String name = rest.substring(nameStart).replace(";", "").trim();
                int location = varyings.size();
                String qualifier = stage == Stage.VERTEX ? "out" : "in";
                varyings.add(qualifier + " " + type + " " + name);
                out.add("layout(location = " + location + ") " + qualifier + " " + type + " " + name + ";"
                        + (comment.isEmpty() ? "" : " //" + comment));
                varyingLine = true;
            }

            if (!attributeLine && !varyingLine) {
                if (containsIdentifier(lowered, "ftransform")) {
                    usesTransforms = true;
                    if (!uniforms.contains("mat4 u_ModelViewProjection")) {
                        uniforms.add("mat4 u_ModelViewProjection");
                    }
                    // Whole-word first, then the argument: ftransform() takes none in compatibility GLSL because it reads
                    // gl_Vertex itself, and the inserted call has to name that attribute *before* the attribute rewrite
                    // runs so the declaration is emitted. Doing it the other way round produced
                    // "celtium$celtium_ftransform" (the second replace saw the $ as a word boundary) and an argument-less
                    // call; the test caught both.
                    lowered = replaceIdentifier(lowered, "ftransform", "celtium_ftransform");
                    lowered = lowered.replace("celtium_ftransform()", "celtium_ftransform(gl_Vertex)");
                }
                for (Map.Entry<String, String> builtin : BUILTIN_ATTRIBUTES.entrySet()) {
                    if (containsIdentifier(lowered, builtin.getKey())) {
                        String declaration = builtin.getValue();
                        String type = declaration.substring(0, declaration.indexOf(' '));
                        String name = declaration.substring(declaration.indexOf(' ') + 1);
                        attributeByBuiltin.put(builtin.getKey(), type + " " + name);
                        lowered = replaceIdentifier(lowered, builtin.getKey(), name);
                    }
                }
                for (Map.Entry<String, String> matrix : BUILTIN_MATRICES.entrySet()) {
                    if (lowered.contains(matrix.getKey())) {
                        usesTransforms = true;
                        lowered = lowered.replace(matrix.getKey(), TRANSFORM_INSTANCE + "." + matrix.getValue()
                                .substring(matrix.getValue().indexOf(' ') + 1));
                        if (!uniforms.contains(matrix.getValue())) {
                            uniforms.add(matrix.getValue());
                        }
                    }
                }
                if (lowered.contains("gl_FragColor")) {
                    usesFragmentColour = true;
                    lowered = lowered.replace("gl_FragColor", FRAGMENT_COLOUR);
                }
                if (lowered.contains("gl_FragData")) {
                    usesFragmentData = true;
                    lowered = lowered.replace("gl_FragData", FRAGMENT_DATA);
                }
                for (Map.Entry<String, String> rename : samplerRenames.entrySet()) {
                    if (containsIdentifier(lowered, rename.getKey())) {
                        lowered = replaceIdentifier(lowered, rename.getKey(), rename.getValue());
                    }
                }
                String opaque = samplerDeclaration(lowered);
                if (opaque != null) {
                    String[] parts = opaque.split(" ");
                    Integer binding = samplerBindings.get(parts[2]);
                    if (binding != null) {
                        String declaration = parts[0] + " " + parts[1] + " " + parts[2];
                        lowered = lowered.replace(declaration,
                                "layout(binding = " + binding.intValue() + ") " + declaration);
                    }
                }
                for (Map.Entry<String, String> function : TEXTURE_FUNCTIONS.entrySet()) {
                    if (lowered.contains(function.getKey() + "(")) {
                        lowered = lowered.replace(function.getKey() + "(", function.getValue() + "(");
                    }
                }
                if (lowered.contains("gl_TexCoord") || lowered.contains("gl_FrontColor")
                        || lowered.contains("gl_BackColor") || lowered.contains("gl_FogFragCoord")) {
                    unsupported.add("fixed-function interpolant: " + trimmed);
                }
                out.add(lowered + (comment.isEmpty() ? "" : " //" + comment));
            }
        }

        StringBuilder text = new StringBuilder();
        int inserted = 0;
        for (int i = 0; i < out.size(); i++) {
            text.append(out.get(i)).append('\n');
            if (inserted == 0 && out.get(i).startsWith("#version")) {
                text.append('\n');
                if (usesTransforms || !uniforms.isEmpty()) {
                    text.append("layout(set = 0, binding = 0, std140) uniform ").append(TRANSFORM_BLOCK).append(" {\n");
                    for (String member : uniforms) {
                        text.append("    ").append(member).append(";\n");
                    }
                    text.append("} ").append(TRANSFORM_INSTANCE).append(";\n\n");
                }
                if (usesTransforms) {
                    text.append("vec4 celtium_ftransform(vec4 position) {\n")
                            .append("    return ").append(TRANSFORM_INSTANCE).append(".u_ModelViewProjection * position;\n")
                            .append("}\n\n");
                }
                // Declared `attribute` lines stay where the source had them: emitting them in the preamble as well
                // declared every named attribute twice, which the compiler rejects ("redefinition") - the compile-side
                // test is what caught it, after the text-side tests passed on the duplicates.
                for (Map.Entry<String, String> builtin : attributeByBuiltin.entrySet()) {
                    String[] parts = builtin.getValue().split(" ");
                    Integer known = assigned.get(parts[1]);
                    int location = known == null ? nextLocation++ : known.intValue();
                    text.append("layout(location = ").append(location).append(") in ").append(parts[0]).append(' ')
                            .append(parts[1]).append(";\n");
                    assigned.put(parts[1], Integer.valueOf(location));
                }
                if (!attributeByBuiltin.isEmpty()) {
                    text.append('\n');
                }
                if (usesFragmentColour) {
                    text.append("layout(location = 0) out vec4 ").append(FRAGMENT_COLOUR).append(";\n\n");
                }
                if (usesFragmentData) {
                    text.append("layout(location = 0) out vec4 ").append(FRAGMENT_DATA).append("[8];\n\n");
                }
                inserted = 1;
            }
        }

        Map<String, Integer> attributes = new TreeMap<>(assigned);
        return new Result(text.toString(), attributes, List.copyOf(varyings), List.copyOf(uniforms),
                new TreeMap<>(samplerBindings), List.copyOf(unsupported), originalVersion,
                usesTransforms ? TRANSFORM_BLOCK : "");
    }

    /**
     * {@return {@code <type> uniform <name>} for an opaque uniform declaration}, or null.
     * <p>
     * Samplers are the one uniform kind Vulkan GLSL insists on binding explicitly, so they are handled here rather than
     * left to a later pass.
     */
    private static String samplerDeclaration(String code) {
        String trimmed = code.trim();
        if (!trimmed.startsWith("uniform ")) {
            return null;
        }
        String rest = trimmed.substring("uniform ".length()).trim();
        if (!rest.endsWith(";")) {
            return null;
        }
        int nameStart = lastIdentifierStart(rest);
        if (nameStart <= 0) {
            return null;
        }
        String type = rest.substring(0, nameStart).trim();
        String name = rest.substring(nameStart).replace(";", "").trim();
        if (!type.startsWith("sampler") && !type.startsWith("usampler") && !type.startsWith("isampler")) {
            return null;
        }
        return "uniform " + type + " " + name;
    }

    /** {@return the index where the trailing identifier of a declaration starts}, or -1. */
    private static int lastIdentifierStart(String declaration) {
        String text = declaration.endsWith(";")
                ? declaration.substring(0, declaration.length() - 1) : declaration;
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        int start = end;
        while (start > 0 && (Character.isLetterOrDigit(text.charAt(start - 1)) || text.charAt(start - 1) == '_')) {
            start--;
        }
        // An array suffix is not an identifier: "vec4 a[4]" would otherwise report "4".
        if (start == 0 && end == 0) {
            return -1;
        }
        return start >= end ? -1 : start;
    }

    /** {@return whether the identifier occurs as a word}, not as part of a longer name. */
    static boolean containsIdentifier(String text, String identifier) {
        int from = 0;
        while (true) {
            int at = text.indexOf(identifier, from);
            if (at < 0) {
                return false;
            }
            boolean leftOk = at == 0 || !isIdentifierChar(text.charAt(at - 1));
            int after = at + identifier.length();
            boolean rightOk = after >= text.length() || !isIdentifierChar(text.charAt(after));
            if (leftOk && rightOk) {
                return true;
            }
            from = at + 1;
        }
    }

    /** Replaces whole-word occurrences, leaving longer names that contain the same text alone. */
    static String replaceIdentifier(String text, String identifier, String replacement) {
        StringBuilder result = new StringBuilder(text.length());
        int from = 0;
        while (true) {
            int at = text.indexOf(identifier, from);
            if (at < 0) {
                result.append(text, from, text.length());
                return result.toString();
            }
            boolean leftOk = at == 0 || !isIdentifierChar(text.charAt(at - 1));
            int after = at + identifier.length();
            boolean rightOk = after >= text.length() || !isIdentifierChar(text.charAt(after));
            if (leftOk && rightOk) {
                result.append(text, from, at).append(replacement);
                from = after;
            } else {
                result.append(text, from, at + 1);
                from = at + 1;
            }
        }
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /** {@return the stage a pack path names}, for callers that only have the file name. */
    public static Stage stageOf(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".vsh")) {
            return Stage.VERTEX;
        }
        if (lower.endsWith(".fsh")) {
            return Stage.FRAGMENT;
        }
        if (lower.endsWith(".gsh")) {
            return Stage.GEOMETRY;
        }
        if (lower.endsWith(".csh")) {
            return Stage.COMPUTE;
        }
        return Stage.VERTEX;
    }
}
