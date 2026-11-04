package org.soutalab.celtium.vulkan.gl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lowers a mod's own legacy GLSL program (a vertex and a fragment source handed to {@code glShaderSource}) to Vulkan
 * GLSL 450, and describes the interface the native pass has to supply.
 * <p>
 * Why this exists: under GL-less a mod's {@code glCompileShader}/{@code glLinkProgram} reach the stub, which until now
 * recorded them as no-ops, so a batch drawn under the program could only be refused (goal.md, 2026-11-04). GTCEu's
 * bloom is the measured case: four programs bound from {@code Shaders.renderFullImageInFBO}, each a
 * {@code #version 120} full-screen pass. Unlike vanilla's post chain these programs are not a fixed set that can be
 * ported at build time, so they are translated when the mod links them.
 * <p>
 * What is lowered, and nothing more (a program outside this subset is reported, not guessed at):
 * <ul>
 * <li>{@code attribute} -> {@code layout(location=N) in}, N in declaration order; {@code varying} -> matched
 * {@code out}/{@code in} locations by name across the two stages.</li>
 * <li>Every non-sampler {@code uniform} -> one std140 block at set 0 binding 0, with the byte offset of each member, so
 * the values {@code glUniform*} recorded can be packed. Samplers -> combined image samplers at bindings 1..n.</li>
 * <li>{@code gl_FragColor}/{@code gl_FragData[0]} -> an output at location 0; {@code texture2D}, {@code texture2DLod}
 * -> {@code texture}, {@code textureLod}.</li>
 * <li>The fixed-function inputs mods still read: {@code gl_Vertex}, {@code gl_Color}, {@code gl_MultiTexCoord0},
 * {@code gl_ModelViewProjectionMatrix}, {@code gl_ModelViewMatrix}, {@code gl_ProjectionMatrix},
 * {@code ftransform()}, and the {@code gl_TexCoord[0]}/{@code gl_FrontColor} varyings. They become ordinary
 * attributes, uniforms and varyings with {@code celtium_} names, listed in the result like the mod's own.</li>
 * </ul>
 * The user {@code main} is renamed and called from a generated one, which is where the clip-space convention is fixed
 * up: Vulkan's y points down and its depth range is [0, 1].
 */
public final class ModGlslTranslator {

    /** A non-sampler uniform's place in the std140 block. */
    public record UniformMember(String name, String type, int arrayLength, int offset, int size) { }

    /** A sampler uniform and the binding it was given. */
    public record SamplerBinding(String name, String type, int binding) { }

    /** A vertex input and the location it was given. */
    public record Attribute(String name, String type, int location) { }

    /** The lowered program, or why it could not be lowered. */
    public record Result(String vertex, String fragment, List<Attribute> attributes, List<UniformMember> uniforms,
                         int uniformBlockSize, List<SamplerBinding> samplers, List<String> varyings,
                         String failure) {
        public boolean ok() {
            return this.failure == null;
        }
    }

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");
    private static final Pattern VERSION = Pattern.compile("^\\s*#\\s*version[^\\n]*\\n?", Pattern.MULTILINE);
    private static final Pattern EXTENSION = Pattern.compile("^\\s*#\\s*extension[^\\n]*\\n?", Pattern.MULTILINE);
    /** A global declaration with a storage qualifier: qualifier, optional precision, type, declarators. */
    private static final Pattern DECLARATION = Pattern.compile(
            "(?<![\\w.])(attribute|varying|uniform)\\s+(?:(?:lowp|mediump|highp)\\s+)?(\\w+)\\s+([^;{}()]+);");
    private static final Pattern DECLARATOR = Pattern.compile("^\\s*(\\w+)\\s*(?:\\[\\s*(\\d+)\\s*\\])?\\s*(?:=.*)?$");
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void)?\\s*\\)");

    private ModGlslTranslator() {
    }

    private record Declaration(String qualifier, String type, String name, int arrayLength) { }

    /**
     * Lowers one program; a null vertex source means the program has no vertex shader and gets the fixed-function
     * one. Never throws: a source outside the subset comes back with {@link Result#failure}.
     */
    public static Result translate(String vertexSource, String fragmentSource) {
        try {
            return translateOrThrow(vertexSource, fragmentSource);
        } catch (IllegalArgumentException outside) {
            return new Result(null, null, List.of(), List.of(), 0, List.of(), List.of(), outside.getMessage());
        }
    }

    /**
     * The vertex stage a program without one runs: GL 2 lets a program attach only a fragment shader and keep the
     * fixed-function vertex path (measured: five of GTCEu's fragment programs read {@code gl_TexCoord[0]} and are
     * linked that way). Written in the same legacy dialect so it goes through the same lowering.
     */
    static final String FIXED_FUNCTION_VERTEX = """
            void main() {
                gl_Position = ftransform();
                gl_TexCoord[0] = gl_MultiTexCoord0;
                gl_FrontColor = gl_Color;
            }
            """;

    private static Result translateOrThrow(String vertexSource, String fragmentSource) {
        if (vertexSource == null) {
            vertexSource = FIXED_FUNCTION_VERTEX;
        }
        String vertex = renameReserved(strip(vertexSource));
        String fragment = renameReserved(strip(fragmentSource));
        List<Declaration> vertexDecls = new ArrayList<>();
        List<Declaration> fragmentDecls = new ArrayList<>();
        vertex = removeDeclarations(vertex, vertexDecls);
        fragment = removeDeclarations(fragment, fragmentDecls);

        // Fixed-function inputs become ordinary declarations, so everything below treats them like the mod's own.
        vertex = lowerBuiltins(vertex, vertexDecls, true);
        fragment = lowerBuiltins(fragment, fragmentDecls, false);

        // Vulkan's fragment origin is the upper left, GL's the lower left: a stage that reads gl_FragCoord gets the GL
        // value back from the target height, which the pass supplies as a hidden uniform.
        boolean fragCoord = fragment.matches("(?s).*\\bgl_FragCoord\\b.*");
        if (fragCoord) {
            fragment = fragment.replaceAll("\\bgl_FragCoord\\b", "celtium_FragCoord");
            fragmentDecls.add(new Declaration("uniform", "float", TARGET_HEIGHT, 0));
        }
        Map<String, Declaration> uniforms = new LinkedHashMap<>();
        for (List<Declaration> stage : List.of(vertexDecls, fragmentDecls)) {
            for (Declaration d : stage) {
                if (!"uniform".equals(d.qualifier)) {
                    continue;
                }
                Declaration seen = uniforms.putIfAbsent(d.name, d);
                if (seen != null && (!seen.type.equals(d.type) || seen.arrayLength != d.arrayLength)) {
                    throw new IllegalArgumentException("uniform " + d.name + " is declared as " + seen.type + " and "
                            + d.type);
                }
            }
        }
        List<UniformMember> members = new ArrayList<>();
        List<SamplerBinding> samplers = new ArrayList<>();
        int offset = 0;
        for (Declaration d : uniforms.values()) {
            if (d.type.startsWith("sampler")) {
                if (d.arrayLength != 0) {
                    throw new IllegalArgumentException("sampler arrays are not lowered: " + d.name);
                }
                samplers.add(new SamplerBinding(glName(d.name), d.type, 1 + samplers.size()));
                continue;
            }
            int[] layout = std140(d.type, d.arrayLength);
            offset = align(offset, layout[0]);
            members.add(new UniformMember(glName(d.name), d.type, d.arrayLength, offset, layout[1]));
            offset += layout[1];
        }
        int blockSize = align(offset, 16);

        List<Attribute> attributes = new ArrayList<>();
        for (Declaration d : vertexDecls) {
            if ("attribute".equals(d.qualifier)) {
                attributes.add(new Attribute(d.name, d.type, attributes.size()));
            }
        }
        List<String> varyings = new ArrayList<>();
        Map<String, Declaration> vertexVaryings = new LinkedHashMap<>();
        for (Declaration d : vertexDecls) {
            if ("varying".equals(d.qualifier)) {
                vertexVaryings.put(d.name, d);
                varyings.add(d.name);
            }
        }
        for (Declaration d : fragmentDecls) {
            if ("varying".equals(d.qualifier) && !vertexVaryings.containsKey(d.name)) {
                throw new IllegalArgumentException("fragment varying " + d.name + " has no vertex output");
            }
        }

        StringBuilder common = new StringBuilder();
        if (!members.isEmpty()) {
            common.append("layout(std140, set = 0, binding = 0) uniform CeltiumModUniforms {\n");
            for (UniformMember m : members) {
                common.append("    ").append(m.type).append(' ').append(glslName(m.name))
                        .append(m.arrayLength > 0 ? "[" + m.arrayLength + "]" : "").append(";\n");
            }
            common.append("};\n");
        }
        for (SamplerBinding s : samplers) {
            common.append("layout(set = 0, binding = ").append(s.binding).append(") uniform ").append(s.type)
                    .append(' ').append(glslName(s.name)).append(";\n");
        }

        StringBuilder vs = new StringBuilder("#version 450\n").append(common);
        for (Attribute a : attributes) {
            vs.append("layout(location = ").append(a.location).append(") in ").append(a.type).append(' ')
                    .append(a.name).append(";\n");
        }
        StringBuilder fs = new StringBuilder("#version 450\n").append(common);
        int location = 0;
        for (Declaration d : vertexVaryings.values()) {
            String array = d.arrayLength > 0 ? "[" + d.arrayLength + "]" : "";
            vs.append("layout(location = ").append(location).append(") out ").append(d.type).append(' ')
                    .append(d.name).append(array).append(";\n");
            boolean read = false;
            for (Declaration f : fragmentDecls) {
                read |= "varying".equals(f.qualifier) && f.name.equals(d.name);
            }
            if (read) {
                fs.append("layout(location = ").append(location).append(") in ").append(d.type).append(' ')
                        .append(d.name).append(array).append(";\n");
            }
            location += locationsFor(d.type) * Math.max(1, d.arrayLength);
        }
        fs.append("layout(location = 0) out vec4 celtium_FragColor;\n");
        if (fragCoord) {
            fs.append("vec4 celtium_FragCoord;\n");
        }

        vertex = renameMain(vertex, "vertex");
        fragment = renameMain(fragment, "fragment");
        fragment = fragment.replaceAll("\\bgl_FragColor\\b", "celtium_FragColor")
                .replaceAll("\\bgl_FragData\\s*\\[\\s*0\\s*\\]", "celtium_FragColor");
        if (fragment.contains("gl_FragData")) {
            throw new IllegalArgumentException("only gl_FragData[0] is lowered");
        }
        vertex = lowerTextureCalls(vertex);
        fragment = lowerTextureCalls(fragment);

        vs.append(vertex).append("\nvoid main() {\n    celtium_user_main();\n")
                // GL clip space has y up and depth in [-1, 1]; Vulkan's has y down and depth in [0, 1].
                .append("    gl_Position.y = -gl_Position.y;\n")
                .append("    gl_Position.z = (gl_Position.z + gl_Position.w) * 0.5;\n}\n");
        fs.append(fragment).append("\nvoid main() {\n");
        if (fragCoord) {
            fs.append("    celtium_FragCoord = vec4(gl_FragCoord.x, ").append(TARGET_HEIGHT)
                    .append(" - gl_FragCoord.y, gl_FragCoord.z, gl_FragCoord.w);\n");
        }
        fs.append("    celtium_user_main();\n}\n");
        return new Result(vs.toString(), fs.toString(), Collections.unmodifiableList(attributes),
                Collections.unmodifiableList(members), blockSize, Collections.unmodifiableList(samplers),
                Collections.unmodifiableList(varyings), null);
    }

    /** The hidden uniform a stage reading {@code gl_FragCoord} needs: the target's height in pixels. */
    public static final String TARGET_HEIGHT = "celtium_TargetHeight";

    /**
     * Words a {@code #version 110/120} source may use as names that GLSL 450 reserves (measured: GTCEu's
     * {@code blackhole.frag} names its sampler {@code texture}). They are renamed in both stages; the reported
     * interface keeps the GL name, because that is what {@code glGetUniformLocation} is asked for.
     */
    private static final java.util.Set<String> RESERVED_IN_450 = java.util.Set.of("texture", "sample", "buffer",
            "shared", "input", "output", "filter", "image", "active", "common", "partition", "patch", "resource",
            "subroutine", "precise", "coherent", "restrict", "readonly", "writeonly", "smooth", "noperspective",
            "centroid", "flat", "layout", "superp", "in", "out");
    private static final String RENAMED = "celtium_r_";

    private static String renameReserved(String source) {
        String s = source;
        for (String word : RESERVED_IN_450) {
            if ("in".equals(word) || "out".equals(word) || "flat".equals(word) || "centroid".equals(word)
                    || "smooth".equals(word) || "noperspective".equals(word) || "layout".equals(word)) {
                continue; // reserved in 120 too: a 120 source cannot use them as names
            }
            s = s.replaceAll("\\b" + word + "\\b", RENAMED + word);
        }
        return s;
    }

    private static String glName(String glslName) {
        return glslName.startsWith(RENAMED) ? glslName.substring(RENAMED.length()) : glslName;
    }

    private static String glslName(String glName) {
        return RESERVED_IN_450.contains(glName) ? RENAMED + glName : glName;
    }

    private static String strip(String source) {
        if (source == null) {
            throw new IllegalArgumentException("a stage has no source");
        }
        String s = BLOCK_COMMENT.matcher(source.replace("\r\n", "\n")).replaceAll(" ");
        s = LINE_COMMENT.matcher(s).replaceAll("");
        s = VERSION.matcher(s).replaceAll("");
        return EXTENSION.matcher(s).replaceAll("");
    }

    private static String removeDeclarations(String source, List<Declaration> out) {
        Matcher m = DECLARATION.matcher(source);
        StringBuilder rest = new StringBuilder();
        while (m.find()) {
            String qualifier = m.group(1);
            String type = m.group(2);
            for (String declarator : m.group(3).split(",")) {
                Matcher d = DECLARATOR.matcher(declarator);
                if (!d.matches()) {
                    throw new IllegalArgumentException("unparsed declaration: " + m.group(0).strip());
                }
                int length = d.group(2) == null ? 0 : Integer.parseInt(d.group(2));
                out.add(new Declaration(qualifier, type, d.group(1), length));
            }
            m.appendReplacement(rest, "");
        }
        m.appendTail(rest);
        return rest.toString();
    }

    private static String lowerBuiltins(String source, List<Declaration> decls, boolean vertexStage) {
        String s = source;
        if (vertexStage) {
            s = s.replaceAll("\\bftransform\\s*\\(\\s*\\)",
                    // Expanded to the built-ins, not to their lowered names, so the two lowerings below declare them.
                    "(gl_ModelViewProjectionMatrix * gl_Vertex)");
            s = replaceBuiltin(s, "gl_Vertex", "celtium_Vertex", "attribute", "vec4", decls);
            s = replaceBuiltin(s, "gl_Color", "celtium_Color", "attribute", "vec4", decls);
            s = replaceBuiltin(s, "gl_MultiTexCoord0", "celtium_MultiTexCoord0", "attribute", "vec4", decls);
            s = replaceBuiltin(s, "gl_FrontColor", "celtium_FrontColor", "varying", "vec4", decls);
        } else {
            s = replaceBuiltin(s, "gl_Color", "celtium_FrontColor", "varying", "vec4", decls);
        }
        if (s.matches("(?s).*\\bgl_TexCoord\\s*\\[\\s*[1-9].*")) {
            throw new IllegalArgumentException("only gl_TexCoord[0] is lowered");
        }
        s = replaceBuiltinPattern(s, "\\bgl_TexCoord\\s*\\[\\s*0\\s*\\]", "celtium_TexCoord0", "varying", "vec4", decls);
        s = replaceBuiltin(s, "gl_ModelViewProjectionMatrix", "celtium_ModelViewProjectionMatrix", "uniform", "mat4",
                decls);
        s = replaceBuiltin(s, "gl_ModelViewMatrix", "celtium_ModelViewMatrix", "uniform", "mat4", decls);
        s = replaceBuiltin(s, "gl_ProjectionMatrix", "celtium_ProjectionMatrix", "uniform", "mat4", decls);
        Matcher leftover = Pattern.compile("\\bgl_(?!Position\\b|FragColor\\b|FragData\\b|FragCoord\\b|PointSize\\b)"
                + "(\\w+)").matcher(s);
        if (leftover.find()) {
            throw new IllegalArgumentException("built-in gl_" + leftover.group(1) + " is not lowered");
        }
        return s;
    }

    private static String replaceBuiltin(String s, String builtin, String name, String qualifier, String type,
                                         List<Declaration> decls) {
        return replaceBuiltinPattern(s, "\\b" + builtin + "\\b", name, qualifier, type, decls);
    }

    private static String replaceBuiltinPattern(String s, String pattern, String name, String qualifier, String type,
                                                List<Declaration> decls) {
        Matcher m = Pattern.compile(pattern).matcher(s);
        if (!m.find()) {
            return s;
        }
        boolean declared = false;
        for (Declaration d : decls) {
            declared |= d.name.equals(name);
        }
        if (!declared) {
            decls.add(new Declaration(qualifier, type, name, 0));
        }
        return m.replaceAll(name);
    }

    private static String renameMain(String source, String stage) {
        Matcher m = MAIN.matcher(source);
        if (!m.find()) {
            throw new IllegalArgumentException("the " + stage + " stage has no main()");
        }
        return m.replaceFirst("void celtium_user_main()");
    }

    private static String lowerTextureCalls(String s) {
        return s.replaceAll("\\btexture2DLod\\s*\\(", "textureLod(")
                .replaceAll("\\btexture2D\\s*\\(", "texture(")
                .replaceAll("\\btexture3D\\s*\\(", "texture(")
                .replaceAll("\\btextureCube\\s*\\(", "texture(");
    }

    private static int locationsFor(String type) {
        return switch (type) {
            case "mat2" -> 2;
            case "mat3" -> 3;
            case "mat4" -> 4;
            default -> 1;
        };
    }

    /** {@return {alignment, size}} of a std140 member. Arrays round every element up to 16 bytes. */
    static int[] std140(String type, int arrayLength) {
        int align;
        int size;
        switch (type) {
            case "float", "int", "uint", "bool" -> { align = 4; size = 4; }
            case "vec2", "ivec2", "uvec2", "bvec2" -> { align = 8; size = 8; }
            case "vec3", "ivec3", "uvec3", "bvec3" -> { align = 16; size = 12; }
            case "vec4", "ivec4", "uvec4", "bvec4" -> { align = 16; size = 16; }
            case "mat2" -> { align = 16; size = 32; }
            case "mat3" -> { align = 16; size = 48; }
            case "mat4" -> { align = 16; size = 64; }
            default -> throw new IllegalArgumentException("uniform type " + type + " is not lowered");
        }
        if (arrayLength > 0) {
            int stride = align(Math.max(size, 16), 16);
            return new int[] {16, stride * arrayLength};
        }
        return new int[] {align, size};
    }

    private static int align(int value, int alignment) {
        return (value + alignment - 1) / alignment * alignment;
    }
}
