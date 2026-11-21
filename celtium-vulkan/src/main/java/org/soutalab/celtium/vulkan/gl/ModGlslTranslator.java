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
 * {@code out}/{@code in} locations by name across the two stages. A varying the two stages declare with a
 * different type, array length or interpolation qualifier is a refusal: GL would fail the link, and lowering
 * both declarations as written would leave the fragment stage reading undefined values.</li>
 * <li>The modern spelling of the same interface (GLSL 330 / ES 3.00 sources, measured: GLSM's fixed-function
 * generator): a vertex {@code in} is an attribute, a vertex {@code out} / fragment {@code in} pair is a varying
 * matched by name, and a fragment {@code out} is a draw-buffer output. A {@code layout(location = N)} the source
 * already carries is kept - the mod's own vertex setup binds by it - and one stage's number binds the other; a
 * declaration with no number is given the next free location, so no user input/output reaches shaderc without
 * one (SPIR-V refuses it otherwise). Interpolation qualifiers ({@code flat} and peers) are carried through.</li>
 * <li>Every non-sampler {@code uniform} -> one std140 block, with the byte offset of each member, so the values
 * {@code glUniform*} recorded can be packed. A uniform declared with a constant initializer keeps that value
 * until first written (it is in {@link Result#uniformDefaults}); an initializer that is not a constant this
 * lowering evaluates is a refusal, not a silent drop of the declaration. The block travels as push constants while it fits the selected
 * device's {@code maxPushConstantsSize} ({@link ModPushConstantLimit}), and as a uniform buffer at set 0's
 * binding after the samplers and images when it does not - the limit is a property of the push route, not of
 * the block, so a program above it is lowered, not refused. Samplers -> combined image samplers at set 0,
 * bindings 0..n-1.</li>
 * <li>{@code gl_FragColor}/{@code gl_FragData[0]} -> an output at location 0; {@code gl_FragData[n]} -> an output
 * at location n, up to {@link #MAX_FRAG_OUTPUTS} draw buffers; {@code texture2D}, {@code texture2DLod}
 * -> {@code texture}, {@code textureLod}.</li>
 * <li>The fixed-function inputs mods still read: {@code gl_Vertex}, {@code gl_Color}, {@code gl_MultiTexCoord0},
 * {@code gl_ModelViewProjectionMatrix}, {@code gl_ModelViewMatrix}, {@code gl_ProjectionMatrix},
 * {@code ftransform()}, and the {@code gl_TexCoord[0]}/{@code gl_FrontColor} varyings. They become ordinary
 * attributes, uniforms and varyings with {@code celtium_} names, listed in the result like the mod's own.</li>
 * </ul>
 * The user {@code main} is renamed and called from a generated one, which is where the clip depth is remapped to
 * Vulkan's [0, 1]; y is left as GL has it, because the renderer keeps images in GL's row order.
 */
public final class ModGlslTranslator {

    /** A non-sampler uniform's place in the std140 block. */
    public record UniformMember(String name, String type, int arrayLength, int offset, int size) { }

    /** A sampler uniform and the binding it was given. */
    public record SamplerBinding(String name, String type, int binding) { }

    /** A fragment storage image and the descriptor binding assigned after all combined samplers. */
    public record ImageBinding(String name, String type, int binding) { }

    /** A vertex input and the location it was given. */
    public record Attribute(String name, String type, int location) { }

    /** The lowered program, or why it could not be lowered. */
    public record Result(String vertex, String fragment, List<Attribute> attributes, List<UniformMember> uniforms,
                         int uniformBlockSize, List<SamplerBinding> samplers, List<ImageBinding> images, List<String> varyings,
                         int fragOutputCount, boolean uniformsViaBuffer, Map<String, float[]> uniformDefaults,
                         String failure) {
        public boolean ok() {
            return this.failure == null;
        }
    }

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");
    private static final Pattern VERSION = Pattern.compile("^\\s*#\\s*version[^\\n]*\\n?", Pattern.MULTILINE);
    private static final Pattern EXTENSION = Pattern.compile("^\\s*#\\s*extension[^\\n]*\\n?", Pattern.MULTILINE);
    /**
     * A global declaration with a storage qualifier: optional layout/interpolation, qualifier, optional precision,
     * type, declarators. A {@code location = N} inside the layout is captured (group 1): the modern spelling
     * ({@link #IO_DECLARATION}) already honours it, and dropping it here silently renumbered an explicitly laid
     * out attribute or varying by declaration order instead. The declarator part allows parentheses because a
     * uniform's initializer may be a constructor call ({@code uniform vec4 c = vec4(1.0);}): excluding them made
     * the whole declaration unparseable, so the uniform never reached the interface and its {@code glUniform*}
     * writes were dropped without a word. A storage qualifier cannot open a function definition, so the wider
     * span is safe.
     */
    private static final Pattern DECLARATION = Pattern.compile(
            "(?<![\\w.])(?:layout\\s*\\((?:[^)]*?location\\s*=\\s*(\\d+))?[^)]*\\)\\s*)?"
                    + "(?:(flat|smooth|noperspective|centroid)\\s+)?"
                    + "(attribute|varying|uniform)\\s+(?:(?:lowp|mediump|highp)\\s+)?(\\w+)\\s+([^;{}]+);");
    private static final Pattern DECLARATOR = Pattern.compile(
            "^\\s*(\\w+)\\s*(?:\\[\\s*(\\d+)\\s*\\])?\\s*(?:=\\s*(.*))?$", Pattern.DOTALL);
    /** A constant constructor initializer: a type name, an optional array size, and the argument text. */
    private static final Pattern CONSTRUCTOR_INITIALIZER = Pattern.compile(
            "(\\w+)\\s*(?:\\[\\s*(\\d+)\\s*\\])?\\s*\\((.*)\\)", Pattern.DOTALL);
    /** One numeric literal of an initializer (a trailing {@code f} is GLSL's float suffix). */
    private static final Pattern NUMBER_LITERAL = Pattern.compile(
            "[+-]?(?:0[xX][0-9a-fA-F]+|(?:\\d+\\.\\d*|\\.\\d+|\\d+)(?:[eE][+-]?\\d+)?)[fF]?");
    /**
     * A modern global input/output declaration: an optional {@code layout(... location = N ...)}, an optional
     * interpolation qualifier, {@code in}/{@code out}, optional precision, type, declarators. The declarator part
     * excludes parentheses, so a function parameter list ({@code const in float f}) can never match.
     */
    private static final Pattern IO_DECLARATION = Pattern.compile(
            "(?<![\\w.])(?:layout\\s*\\([^)]*?location\\s*=\\s*(\\d+)[^)]*?\\)\\s*)?"
                    + "(?:(flat|smooth|noperspective|centroid)\\s+)?(in|out)\\s+"
                    + "(?:(?:lowp|mediump|highp)\\s+)?(\\w+)\\s+([^;{}()]+);");
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void)?\\s*\\)");
    private static final Pattern LAID_OUT_IMAGE = Pattern.compile(
            "layout\\s*\\(([^)]*)\\)\\s*uniform\\s+(?:[iu]?image\\w+)\\s+(\\w+)");
    private static final Pattern RESERVED_WORDS_PATTERN = Pattern.compile(
            "\\b(?:texture|sample|buffer|shared|input|output|filter|image|active|common|partition|patch|"
                    + "resource|subroutine|precise|coherent|restrict|readonly|writeonly|superp)\\b");
    /** The same list for a source of the modern dialect ({@code #version} 300+): there {@code texture} is the
     * built-in function the source is calling, not a name it chose, so it is not renamed. */
    private static final Pattern RESERVED_WORDS_MODERN_PATTERN = Pattern.compile(
            "\\b(?:sample|buffer|shared|input|output|filter|image|active|common|partition|patch|"
                    + "resource|subroutine|precise|coherent|restrict|readonly|writeonly|superp)\\b");
    private static final Pattern VERSION_NUMBER = Pattern.compile("^\\s*#\\s*version\\s+(\\d+)", Pattern.MULTILINE);
    private static final Pattern FRAG_COLOR_PATTERN = Pattern.compile("\\bgl_FragColor\\b");
    private static final Pattern FRAG_DATA0_PATTERN = Pattern.compile("\\bgl_FragData\\s*\\[\\s*0\\s*\\]");
    private static final Pattern FRAG_DATA_PATTERN = Pattern.compile("\\bgl_FragData\\s*\\[\\s*(\\d+)\\s*\\]");
    /** The draw buffers lowered to fragment outputs; Iris's own ceiling, and the mod pipeline's attachment ledger. */
    static final int MAX_FRAG_OUTPUTS = 8;
    private static final Pattern FTRANSFORM_PATTERN = Pattern.compile("\\bftransform\\s*\\(\\s*\\)");
    private static final Pattern TEXCOORD_BAD_PATTERN = Pattern.compile("(?s).*\\bgl_TexCoord\\s*\\[\\s*[1-9].*");
    private static final Pattern TEXCOORD0_PATTERN = Pattern.compile("\\bgl_TexCoord\\s*\\[\\s*0\\s*\\]");
    private static final Pattern GL_LEFTOVER_PATTERN = Pattern.compile(
            "\\bgl_(?!Position\\b|FragColor\\b|FragData\\b|FragCoord\\b|FragDepth\\b|FrontFacing\\b|PointSize\\b|PointCoord\\b|ClipDistance\\b|PrimitiveID\\b|Layer\\b|VertexIndex\\b|InstanceIndex\\b)(\\w+)");
    private static final Pattern GL_VERTEX_ID_PATTERN = Pattern.compile("\\bgl_VertexID\\b");
    private static final Pattern GL_INSTANCE_ID_PATTERN = Pattern.compile("\\bgl_InstanceID\\b");
    private static final Pattern GL_VERTEX_PATTERN = Pattern.compile("\\bgl_Vertex\\b");
    private static final Pattern GL_COLOR_PATTERN = Pattern.compile("\\bgl_Color\\b");
    private static final Pattern GL_MULTI_TEXCOORD0_PATTERN = Pattern.compile("\\bgl_MultiTexCoord0\\b");
    private static final Pattern GL_FRONT_COLOR_PATTERN = Pattern.compile("\\bgl_FrontColor\\b");
    private static final Pattern GL_MVP_PATTERN = Pattern.compile("\\bgl_ModelViewProjectionMatrix\\b");
    private static final Pattern GL_MV_PATTERN = Pattern.compile("\\bgl_ModelViewMatrix\\b");
    private static final Pattern GL_PROJ_PATTERN = Pattern.compile("\\bgl_ProjectionMatrix\\b");
    private static final Pattern TEX1D_LOD_PATTERN = Pattern.compile("\\btexture1DLod\\s*\\(");
    private static final Pattern TEX1D_PATTERN = Pattern.compile("\\btexture1D\\s*\\(");
    private static final Pattern TEX2D_PROJ_LOD_PATTERN = Pattern.compile("\\btexture2DProjLod\\s*\\(");
    private static final Pattern TEX2D_PROJ_PATTERN = Pattern.compile("\\btexture2DProj\\s*\\(");
    private static final Pattern TEX2D_LOD_PATTERN = Pattern.compile("\\btexture2DLod\\s*\\(");
    private static final Pattern TEX2D_GRAD_ARB_PATTERN = Pattern.compile("\\btexture2DGradARB\\s*\\(");
    private static final Pattern TEX2D_RECT_PROJ_PATTERN = Pattern.compile("\\btexture2DRectProj\\s*\\(");
    private static final Pattern TEX2D_RECT_PATTERN = Pattern.compile("\\btexture2DRect\\s*\\(");
    private static final Pattern TEX2D_PATTERN = Pattern.compile("\\btexture2D\\s*\\(");
    private static final Pattern TEX3D_LOD_PATTERN = Pattern.compile("\\btexture3DLod\\s*\\(");
    private static final Pattern TEX3D_PATTERN = Pattern.compile("\\btexture3D\\s*\\(");
    private static final Pattern TEXCUBE_LOD_PATTERN = Pattern.compile("\\btextureCubeLod\\s*\\(");
    private static final Pattern TEXCUBE_GRAD_ARB_PATTERN = Pattern.compile("\\btextureCubeGradARB\\s*\\(");
    private static final Pattern TEXCUBE_PATTERN = Pattern.compile("\\btextureCube\\s*\\(");

    private ModGlslTranslator() {
    }

    /**
     * One parsed global declaration. {@code explicitLocation} is the source's own {@code layout(location = N)},
     * or -1 when it declared none; {@code interpolation} is its interpolation qualifier, or "".
     * The qualifier vocabulary is the legacy one plus {@code fragout} for a fragment stage's own {@code out}:
     * modern spellings are mapped onto it at parse time (vertex {@code in} -> {@code attribute}, vertex
     * {@code out} / fragment {@code in} -> {@code varying}, fragment {@code out} -> {@code fragout}).
     */
    private record Declaration(String qualifier, String type, String name, int arrayLength, int explicitLocation,
                               String interpolation, String initializer) {
        Declaration(String qualifier, String type, String name, int arrayLength) {
            this(qualifier, type, name, arrayLength, -1, "", null);
        }
    }

    /**
     * Lowers one program; a null vertex source means the program has no vertex shader and gets the fixed-function
     * one. Never throws: a source outside the subset comes back with {@link Result#failure}.
     */
    public static Result translate(String vertexSource, String fragmentSource) {
        try {
            return translateOrThrow(vertexSource, fragmentSource);
        } catch (IllegalArgumentException outside) {
            return new Result(null, null, List.of(), List.of(), 0, List.of(), List.of(), List.of(), 0, false,
                    Map.of(), outside.getMessage());
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

    /**
     * A source may be NUL-terminated: GL's {@code length == NULL} form makes the driver stop at the terminator, and a
     * measured caller sends it (Iris's {@code GlShader}: {@code glShaderSource(handle, src + '\0')}). This lowering
     * appends its own entry point after the mod's body, which would leave that terminator mid-file, where glslang
     * refuses it as {@code '' : unexpected token} (item 371, Demonica's centerDepthSmooth program). Trailing
     * terminators are dropped; an interior one is a refusal naming the stage and the offset, not a silent splice.
     */
    private static String withoutNulTerminator(String source, String stage) {
        if (source == null) {
            // A missing stage is strip()'s refusal ("a stage has no source"), not this normalisation's.
            return null;
        }
        int end = source.length();
        while (end > 0 && source.charAt(end - 1) == '\u0000') {
            end--;
        }
        int interior = source.indexOf('\u0000');
        if (interior >= 0 && interior < end) {
            throw new IllegalArgumentException("the " + stage + " stage's source holds a NUL at offset " + interior
                    + " (a NUL is a terminator, not part of GLSL text)");
        }
        return source.substring(0, end);
    }

    private static Result translateOrThrow(String vertexSource, String fragmentSource) {
        if (vertexSource == null) {
            vertexSource = FIXED_FUNCTION_VERTEX;
        }
        vertexSource = withoutNulTerminator(vertexSource, "vertex");
        fragmentSource = withoutNulTerminator(fragmentSource, "fragment");
        Matcher laidOutImage = LAID_OUT_IMAGE.matcher(strip(fragmentSource));
        if (laidOutImage.find()) {
            String qualifier = laidOutImage.group(1).trim();
            String reason = qualifier.matches("(?:rgba|rg|r)[a-zA-Z0-9_]*")
                    ? "has a format qualifier, which the storage-image descriptor path does not preserve"
                    : "has a layout qualifier, which the storage-image descriptor path does not preserve";
            throw new IllegalArgumentException("image uniform " + laidOutImage.group(2) + " " + reason);
        }
        String vertex = renameReserved(strip(vertexSource), modernDialect(vertexSource));
        String fragment = renameReserved(strip(fragmentSource), modernDialect(fragmentSource));
        List<Declaration> vertexDecls = new ArrayList<>();
        List<Declaration> fragmentDecls = new ArrayList<>();
        vertex = removeDeclarations(vertex, vertexDecls, true);
        fragment = removeDeclarations(fragment, fragmentDecls, false);

        // Fixed-function inputs become ordinary declarations, so everything below treats them like the mod's own.
        vertex = lowerBuiltins(vertex, vertexDecls, true);
        fragment = lowerBuiltins(fragment, fragmentDecls, false);

        // gl_FragCoord needs no fix-up: this renderer keeps images in GL's row order (row 0 at the bottom: the
        // projection only has its depth remapped and viewports keep GL's y), so Vulkan's upper-left origin counts the
        // same rows GL's lower-left origin does.
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
        Map<String, float[]> uniformDefaults = new LinkedHashMap<>();
        List<SamplerBinding> samplers = new ArrayList<>();
        int offset = 0;
        for (Declaration d : uniforms.values()) {
            if (isSamplerType(d.type)) {
                if (d.arrayLength != 0) {
                    throw new IllegalArgumentException("sampler arrays are not lowered: " + d.name);
                }
                samplers.add(new SamplerBinding(glName(d.name), d.type, samplers.size()));
                continue;
            }
            if (d.type.toLowerCase(java.util.Locale.ROOT).contains("image")) {
                if (!List.of("image2D", "iimage2D", "uimage2D").contains(d.type)) {
                    throw new IllegalArgumentException("image uniform " + d.name + " has unsupported image type " + d.type);
                }
                if (vertexDecls.stream().anyMatch(v -> v.name.equals(d.name) && "uniform".equals(v.qualifier))) {
                    throw new IllegalArgumentException("image uniform " + d.name + " in the vertex stage is not lowered");
                }
                if (d.arrayLength != 0) {
                    throw new IllegalArgumentException("image uniform arrays are not lowered: " + d.name);
                }
                continue;
            }
            int[] layout = std140(d.type, d.arrayLength);
            offset = align(offset, layout[0]);
            members.add(new UniformMember(glName(d.name), d.type, d.arrayLength, offset, layout[1]));
            if (d.initializer != null) {
                uniformDefaults.put(glName(d.name), constantInitializer(d));
            }
            offset += layout[1];
        }
        int blockSize = align(offset, 16);
        // The block travels as push constants while it fits, and as a uniform buffer when it does not: the limit
        // is a property of the push route, not of the block, so a program above it (Demonica's deferred passes
        // carry 480/496 bytes, its gbuffers programs 1568) is lowered, not refused - the pipeline gives the
        // buffer route a descriptor binding after the samplers and images and a slot of its uniform ring, the
        // shape VulkanPostChain's own oversized programs already take. The threshold is the number
        // ModPushConstantLimit records (the device's limit, or the guaranteed minimum before selection), so a
        // program linked before selection takes the buffer route above 128 bytes: always valid, because the
        // buffer route does not depend on the push limit at all.
        boolean uniformsViaBuffer = blockSize > ModPushConstantLimit.limit();
        List<ImageBinding> images = new ArrayList<>();
        for (Declaration d : uniforms.values()) {
            if (List.of("image2D", "iimage2D", "uimage2D").contains(d.type)) {
                images.add(new ImageBinding(glName(d.name), d.type, samplers.size() + images.size()));
            }
        }

        List<Attribute> attributes = new ArrayList<>();
        java.util.BitSet attributeLocations = new java.util.BitSet();
        for (Declaration d : vertexDecls) {
            if ("attribute".equals(d.qualifier)) {
                int width = locationsFor(d.type) * Math.max(1, d.arrayLength);
                attributes.add(new Attribute(d.name, d.type,
                        claimLocation(attributeLocations, d.explicitLocation, width, "attribute " + d.name)));
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
        Map<String, Declaration> fragmentVaryings = new LinkedHashMap<>();
        for (Declaration d : fragmentDecls) {
            if ("varying".equals(d.qualifier)) {
                if (!vertexVaryings.containsKey(d.name)) {
                    throw new IllegalArgumentException("fragment varying " + d.name + " has no vertex output");
                }
                fragmentVaryings.put(d.name, d);
            }
        }

        StringBuilder common = new StringBuilder();
        if (!members.isEmpty()) {
            if (uniformsViaBuffer) {
                // The binding after every sampler and storage image: the pipeline declares the same index.
                common.append("layout(set = 0, binding = ").append(samplers.size() + images.size())
                        .append(", std140) uniform CeltiumModUniforms {\n");
            } else {
                common.append("layout(push_constant, std140) uniform CeltiumModUniforms {\n");
            }
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
        for (ImageBinding image : images) {
            common.append("layout(set = 0, binding = ").append(image.binding).append(") uniform ")
                    .append(image.type).append(' ').append(glslName(image.name)).append(";\n");
        }

        StringBuilder vs = new StringBuilder("#version 450\n").append(common);
        for (Attribute a : attributes) {
            vs.append("layout(location = ").append(a.location).append(") in ").append(a.type).append(' ')
                    .append(a.name).append(";\n");
        }
        StringBuilder fs = new StringBuilder("#version 450\n").append(common);
        java.util.BitSet varyingLocations = new java.util.BitSet();
        for (Declaration d : vertexVaryings.values()) {
            Declaration f = fragmentVaryings.get(d.name);
            if (f != null) {
                // The interface the two stages share: GL fails the link when any of these disagree, and the
                // lowering compiles the stages separately (there is no link step to catch it), so a mismatch
                // checked nowhere would hand the fragment stage undefined values at the shared locations.
                if (!f.type.equals(d.type)) {
                    throw new IllegalArgumentException("varying " + d.name + " is declared as " + d.type
                            + " in the vertex stage and " + f.type + " in the fragment stage");
                }
                if (f.arrayLength != d.arrayLength) {
                    throw new IllegalArgumentException("varying " + d.name + " is declared with array length "
                            + d.arrayLength + " in the vertex stage and " + f.arrayLength
                            + " in the fragment stage");
                }
                if (!effectiveInterpolation(f).equals(effectiveInterpolation(d))) {
                    throw new IllegalArgumentException("varying " + d.name + " is " + effectiveInterpolation(d)
                            + " in the vertex stage and " + effectiveInterpolation(f) + " in the fragment stage");
                }
            }
            int explicit = d.explicitLocation;
            if (f != null && f.explicitLocation >= 0) {
                if (explicit >= 0 && explicit != f.explicitLocation) {
                    throw new IllegalArgumentException("varying " + d.name + " declares location " + explicit
                            + " in the vertex stage and " + f.explicitLocation + " in the fragment stage");
                }
                explicit = f.explicitLocation;
            }
            int width = locationsFor(d.type) * Math.max(1, d.arrayLength);
            int location = claimLocation(varyingLocations, explicit, width, "varying " + d.name);
            String array = d.arrayLength > 0 ? "[" + d.arrayLength + "]" : "";
            vs.append("layout(location = ").append(location).append(") ").append(interpolationPrefix(d))
                    .append("out ").append(d.type).append(' ').append(d.name).append(array).append(";\n");
            if (f != null) {
                String fragmentArray = f.arrayLength > 0 ? "[" + f.arrayLength + "]" : "";
                fs.append("layout(location = ").append(location).append(") ").append(interpolationPrefix(f))
                        .append("in ").append(f.type).append(' ').append(f.name).append(fragmentArray).append(";\n");
            }
        }
        // Draw buffers beyond the first lower to their own outputs (MRT): gl_FragData[n] becomes the output at
        // location n. The scan runs on the unlowered fragment so the indices are the source's own.
        java.util.TreeSet<Integer> fragDataIndices = new java.util.TreeSet<>();
        Matcher fragDataScan = FRAG_DATA_PATTERN.matcher(fragment);
        while (fragDataScan.find()) {
            int index = Integer.parseInt(fragDataScan.group(1));
            if (index >= MAX_FRAG_OUTPUTS) {
                throw new IllegalArgumentException(
                        "gl_FragData[" + index + "] exceeds the " + MAX_FRAG_OUTPUTS + " lowered draw buffers");
            }
            fragDataIndices.add(index);
        }
        int legacyOutputCount = Math.max(1, fragDataIndices.isEmpty() ? 1 : fragDataIndices.last() + 1);
        int fragOutputCount = legacyOutputCount;
        List<Declaration> fragOutputs = new ArrayList<>();
        for (Declaration d : fragmentDecls) {
            if ("fragout".equals(d.qualifier)) {
                fragOutputs.add(d);
            }
        }
        if (fragOutputs.isEmpty()) {
            fs.append("layout(location = 0) out vec4 celtium_FragColor;\n");
            for (int index : fragDataIndices) {
                if (index > 0) {
                    fs.append("layout(location = ").append(index).append(") out vec4 celtium_FragData")
                            .append(index).append(";\n");
                }
            }
        } else {
            // The stage declares its own outputs (the GLSL 330 spelling). The lowered gl_FragColor output is
            // emitted only when the body still writes it, and the declared outputs take the locations after it.
            java.util.BitSet outputLocations = new java.util.BitSet();
            boolean legacyUsed = FRAG_COLOR_PATTERN.matcher(fragment).find() || !fragDataIndices.isEmpty();
            if (legacyUsed) {
                outputLocations.set(0, legacyOutputCount);
                fs.append("layout(location = 0) out vec4 celtium_FragColor;\n");
                for (int index : fragDataIndices) {
                    if (index > 0) {
                        fs.append("layout(location = ").append(index).append(") out vec4 celtium_FragData")
                                .append(index).append(";\n");
                    }
                }
            } else {
                fragOutputCount = 0;
            }
            for (Declaration d : fragOutputs) {
                int width = Math.max(1, d.arrayLength);
                int location = claimLocation(outputLocations, d.explicitLocation, width,
                        "fragment output " + d.name);
                String array = d.arrayLength > 0 ? "[" + d.arrayLength + "]" : "";
                fs.append("layout(location = ").append(location).append(") ").append(interpolationPrefix(d))
                        .append("out ").append(d.type).append(' ').append(d.name).append(array).append(";\n");
                fragOutputCount = Math.max(fragOutputCount, location + width);
            }
        }

        vertex = renameMain(vertex, "vertex");
        fragment = renameMain(fragment, "fragment");
        fragment = FRAG_DATA0_PATTERN.matcher(
                FRAG_COLOR_PATTERN.matcher(fragment).replaceAll("celtium_FragColor"))
                .replaceAll("celtium_FragColor");
        Matcher fragData = FRAG_DATA_PATTERN.matcher(fragment);
        StringBuffer lowered = new StringBuffer(fragment.length());
        while (fragData.find()) {
            fragData.appendReplacement(lowered,
                    Matcher.quoteReplacement("celtium_FragData" + fragData.group(1)));
        }
        fragData.appendTail(lowered);
        fragment = lowered.toString();
        if (fragment.contains("gl_FragData")) {
            throw new IllegalArgumentException("gl_FragData with a non-constant index is not lowered");
        }
        vertex = lowerTextureCalls(vertex);
        fragment = lowerTextureCalls(fragment);

        vs.append(vertex).append("\nvoid main() {\n    celtium_user_main();\n")
                // GL's clip depth is [-1, 1], Vulkan's [0, 1]. y is not flipped: like every other pass here the image
                // keeps GL's row order (VulkanTerrainGpuBackend.remapClipDepth remaps depth only).
                .append("    gl_Position.z = (gl_Position.z + gl_Position.w) * 0.5;\n}\n");
        fs.append(fragment).append("\nvoid main() {\n    celtium_user_main();\n}\n");
        return new Result(vs.toString(), fs.toString(), Collections.unmodifiableList(attributes),
                Collections.unmodifiableList(members), blockSize, Collections.unmodifiableList(samplers),
                Collections.unmodifiableList(images),
                Collections.unmodifiableList(varyings), fragOutputCount, uniformsViaBuffer,
                Collections.unmodifiableMap(uniformDefaults), null);
    }

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

    /** {@return whether the source declares a {@code #version} of the modern dialect (300 or above)}. */
    private static boolean modernDialect(String source) {
        if (source == null) {
            return false;
        }
        Matcher m = VERSION_NUMBER.matcher(source);
        return m.find() && Integer.parseInt(m.group(1)) >= 300;
    }

    private static String renameReserved(String source, boolean modernDialect) {
        Matcher m = (modernDialect ? RESERVED_WORDS_MODERN_PATTERN : RESERVED_WORDS_PATTERN).matcher(source);
        if (!m.find()) {
            return source;
        }
        StringBuilder sb = new StringBuilder(source.length() + 32);
        do {
            m.appendReplacement(sb, RENAMED + m.group());
        } while (m.find());
        m.appendTail(sb);
        return sb.toString();
    }

    private static String glName(String glslName) {
        return glslName.startsWith(RENAMED) ? glslName.substring(RENAMED.length()) : glslName;
    }

    private static String glslName(String glName) {
        return RESERVED_IN_450.contains(glName) ? RENAMED + glName : glName;
    }

    /** Package-private for {@link ModProgramRegistry}'s fallback stage heuristic, which reads the same cleaned text. */
    static String strip(String source) {
        if (source == null) {
            throw new IllegalArgumentException("a stage has no source");
        }
        String s = BLOCK_COMMENT.matcher(source.replace("\r\n", "\n")).replaceAll(" ");
        s = LINE_COMMENT.matcher(s).replaceAll("");
        s = VERSION.matcher(s).replaceAll("");
        return EXTENSION.matcher(s).replaceAll("");
    }

    private static String removeDeclarations(String source, List<Declaration> out, boolean vertexStage) {
        Matcher m = DECLARATION.matcher(source);
        StringBuilder rest = new StringBuilder();
        while (m.find()) {
            int explicit = m.group(1) == null ? -1 : Integer.parseInt(m.group(1));
            String interpolation = m.group(2) == null ? "" : m.group(2);
            String qualifier = m.group(3);
            String type = m.group(4);
            for (String declarator : splitDeclarators(m.group(5))) {
                Matcher d = DECLARATOR.matcher(declarator);
                if (!d.matches()) {
                    throw new IllegalArgumentException("unparsed declaration: " + m.group(0).strip());
                }
                int length = d.group(2) == null ? 0 : Integer.parseInt(d.group(2));
                String initializer = d.group(3) == null ? null : d.group(3).strip();
                out.add(new Declaration(qualifier, type, d.group(1), length, explicit, interpolation, initializer));
            }
            m.appendReplacement(rest, "");
        }
        m.appendTail(rest);
        // The modern spelling of the same declarations. A built-in name (a gl_FragCoord redeclaration is the
        // sane case) is not user input/output: its text stays, and the leftover check below judges it as before.
        Matcher io = IO_DECLARATION.matcher(rest.toString());
        StringBuilder modern = new StringBuilder();
        while (io.find()) {
            String type = io.group(4);
            List<Declaration> parsed = new ArrayList<>();
            boolean builtin = false;
            for (String declarator : io.group(5).split(",")) {
                Matcher d = DECLARATOR.matcher(declarator);
                if (!d.matches()) {
                    throw new IllegalArgumentException("unparsed declaration: " + io.group(0).strip());
                }
                builtin |= d.group(1).startsWith("gl_");
                int length = d.group(2) == null ? 0 : Integer.parseInt(d.group(2));
                String qualifier = vertexStage
                        ? ("in".equals(io.group(3)) ? "attribute" : "varying")
                        : ("in".equals(io.group(3)) ? "varying" : "fragout");
                int explicit = io.group(1) == null ? -1 : Integer.parseInt(io.group(1));
                String interpolation = io.group(2) == null ? "" : io.group(2);
                parsed.add(new Declaration(qualifier, type, d.group(1), length, explicit, interpolation, null));
            }
            if (builtin) {
                io.appendReplacement(modern, Matcher.quoteReplacement(io.group(0)));
            } else {
                out.addAll(parsed);
                io.appendReplacement(modern, "");
            }
        }
        io.appendTail(modern);
        return modern.toString();
    }

    private static String lowerBuiltins(String source, List<Declaration> decls, boolean vertexStage) {
        String s = source;
        if (vertexStage) {
            s = GL_VERTEX_ID_PATTERN.matcher(s).replaceAll("gl_VertexIndex");
            s = GL_INSTANCE_ID_PATTERN.matcher(s).replaceAll("gl_InstanceIndex");
            s = FTRANSFORM_PATTERN.matcher(s).replaceAll(
                    // Expanded to the built-ins, not to their lowered names, so the two lowerings below declare them.
                    "(gl_ModelViewProjectionMatrix * gl_Vertex)");
            s = replaceBuiltinPattern(s, GL_VERTEX_PATTERN, "celtium_Vertex", "attribute", "vec4", decls);
            s = replaceBuiltinPattern(s, GL_COLOR_PATTERN, "celtium_Color", "attribute", "vec4", decls);
            s = replaceBuiltinPattern(s, GL_MULTI_TEXCOORD0_PATTERN, "celtium_MultiTexCoord0", "attribute", "vec4",
                    decls);
            s = replaceBuiltinPattern(s, GL_FRONT_COLOR_PATTERN, "celtium_FrontColor", "varying", "vec4", decls);
        } else {
            s = replaceBuiltinPattern(s, GL_COLOR_PATTERN, "celtium_FrontColor", "varying", "vec4", decls);
        }
        if (TEXCOORD_BAD_PATTERN.matcher(s).matches()) {
            throw new IllegalArgumentException("only gl_TexCoord[0] is lowered");
        }
        s = replaceBuiltinPattern(s, TEXCOORD0_PATTERN, "celtium_TexCoord0", "varying", "vec4", decls);
        s = replaceBuiltinPattern(s, GL_MVP_PATTERN, "celtium_ModelViewProjectionMatrix", "uniform", "mat4", decls);
        s = replaceBuiltinPattern(s, GL_MV_PATTERN, "celtium_ModelViewMatrix", "uniform", "mat4", decls);
        s = replaceBuiltinPattern(s, GL_PROJ_PATTERN, "celtium_ProjectionMatrix", "uniform", "mat4", decls);
        Matcher leftover = GL_LEFTOVER_PATTERN.matcher(s);
        if (leftover.find()) {
            throw new IllegalArgumentException("built-in gl_" + leftover.group(1) + " is not lowered");
        }
        return s;
    }

    private static String replaceBuiltinPattern(String s, Pattern pattern, String name, String qualifier, String type,
                                                List<Declaration> decls) {
        Matcher m = pattern.matcher(s);
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

    private static boolean isSamplerType(String type) {
        return type.startsWith("sampler") || type.startsWith("isampler") || type.startsWith("usampler");
    }

    private static String lowerTextureCalls(String s) {
        s = TEX1D_LOD_PATTERN.matcher(s).replaceAll("textureLod(");
        s = TEX1D_PATTERN.matcher(s).replaceAll("texture(");
        s = TEX2D_PROJ_LOD_PATTERN.matcher(s).replaceAll("textureProjLod(");
        s = TEX2D_PROJ_PATTERN.matcher(s).replaceAll("textureProj(");
        s = TEX2D_LOD_PATTERN.matcher(s).replaceAll("textureLod(");
        s = TEX2D_GRAD_ARB_PATTERN.matcher(s).replaceAll("textureGrad(");
        s = TEX2D_RECT_PROJ_PATTERN.matcher(s).replaceAll("textureProj(");
        s = TEX2D_RECT_PATTERN.matcher(s).replaceAll("texture(");
        s = TEX2D_PATTERN.matcher(s).replaceAll("texture(");
        s = TEX3D_LOD_PATTERN.matcher(s).replaceAll("textureLod(");
        s = TEX3D_PATTERN.matcher(s).replaceAll("texture(");
        s = TEXCUBE_LOD_PATTERN.matcher(s).replaceAll("textureLod(");
        s = TEXCUBE_GRAD_ARB_PATTERN.matcher(s).replaceAll("textureGrad(");
        return TEXCUBE_PATTERN.matcher(s).replaceAll("texture(");
    }

    private static String interpolationPrefix(Declaration d) {
        return d.interpolation.isEmpty() ? "" : d.interpolation + " ";
    }

    /** {@return the interpolation a declaration effectively carries}: an unqualified varying is {@code smooth}. */
    private static String effectiveInterpolation(Declaration d) {
        return d.interpolation.isEmpty() ? "smooth" : d.interpolation;
    }

    /**
     * Splits a declarator list on its top-level commas. A constructor initializer's argument commas sit inside
     * parentheses and belong to one declarator, so a plain {@code split(",")} would shred
     * {@code vec4 a = vec4(1, 2, 3, 4), b} into pieces no declarator pattern can read.
     */
    private static List<String> splitDeclarators(String text) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(' || c == '[') {
                depth++;
            } else if (c == ')' || c == ']') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(text.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(text.substring(start));
        return parts;
    }

    /**
     * {@return the value of a uniform's constant initializer}, one float per scalar component in the order
     * {@code packUniforms} consumes them (element, then matrix column, then component). The forms evaluated are
     * the ones a generated shader writes: a bare literal, a constructor of literals (a single argument splats,
     * a matrix's single argument is its diagonal, as GLSL defines), and an array constructor of literals over a
     * scalar element type. Anything else - an expression, a reference, a nested constructor - is a refusal
     * naming the uniform: the declaration is part of the interface now, so guessing a value (or zero) would be
     * a silent wrong answer where the pre-fix behaviour at least kept the initializer in the emitted shader.
     */
    private static float[] constantInitializer(Declaration d) {
        String init = d.initializer.strip();
        int components = scalarComponents(d.type);
        Matcher ctor = CONSTRUCTOR_INITIALIZER.matcher(init);
        if (ctor.matches()) {
            String ctorType = ctor.group(1);
            List<String> args = new ArrayList<>();
            for (String arg : splitDeclarators(ctor.group(3))) {
                if (!arg.isBlank()) {
                    args.add(arg.strip());
                }
            }
            if (d.arrayLength > 0) {
                // An array constructor: float[2](1.0, 2.0). Only a scalar element type is evaluated - a
                // vector element's own constructor would nest, which the refusal below names.
                if (!ctorType.equals(d.type) || ctor.group(2) == null
                        || Integer.parseInt(ctor.group(2)) != d.arrayLength || components != 1
                        || args.size() != d.arrayLength) {
                    throw notConstant(d, init);
                }
                float[] values = new float[d.arrayLength];
                for (int i = 0; i < values.length; i++) {
                    values[i] = literal(d, args.get(i));
                }
                return values;
            }
            if (!ctorType.equals(d.type)) {
                throw notConstant(d, init);
            }
            if (d.type.startsWith("mat")) {
                // matN's constructor arguments fill column by column; one scalar is the diagonal.
                if (args.size() == 1) {
                    float[] values = new float[components * components];
                    float v = literal(d, args.get(0));
                    for (int c = 0; c < components; c++) {
                        values[c * components + c] = v;
                    }
                    return values;
                }
                if (args.size() != components * components) {
                    throw notConstant(d, init);
                }
                float[] values = new float[args.size()];
                for (int i = 0; i < values.length; i++) {
                    values[i] = literal(d, args.get(i));
                }
                return values;
            }
            if (args.size() == 1 && components > 1) {
                float[] values = new float[components];
                java.util.Arrays.fill(values, literal(d, args.get(0)));
                return values;
            }
            if (args.size() != components) {
                throw notConstant(d, init);
            }
            float[] values = new float[components];
            for (int i = 0; i < components; i++) {
                values[i] = literal(d, args.get(i));
            }
            return values;
        }
        if (d.arrayLength == 0 && components == 1) {
            return new float[] {literal(d, init)};
        }
        throw notConstant(d, init);
    }

    private static IllegalArgumentException notConstant(Declaration d, String init) {
        return new IllegalArgumentException("uniform " + d.name + " has an initializer this lowering does not "
                + "evaluate (" + init + "): only a constant literal or a constructor of literals is carried");
    }

    /** {@return one initializer literal as a float} ({@code true}/{@code false} are 1/0), or refuses. */
    private static float literal(Declaration d, String text) {
        if ("true".equals(text)) {
            return 1.0f;
        }
        if ("false".equals(text)) {
            return 0.0f;
        }
        if (!NUMBER_LITERAL.matcher(text).matches()) {
            throw notConstant(d, text);
        }
        String digits = text.endsWith("f") || text.endsWith("F") ? text.substring(0, text.length() - 1) : text;
        try {
            if (digits.startsWith("0x") || digits.startsWith("0X") || digits.startsWith("+0x")
                    || digits.startsWith("-0x")) {
                boolean negative = digits.startsWith("-");
                String hex = digits.substring(digits.indexOf('x') + 1);
                long v = Long.parseLong(hex, 16);
                return (float) (negative ? -v : v);
            }
            return (float) Double.parseDouble(digits);
        } catch (NumberFormatException bad) {
            throw notConstant(d, text);
        }
    }

    /** {@return the scalar components of one element of a uniform type} (a matrix counts its columns' scalars). */
    private static int scalarComponents(String type) {
        return switch (type) {
            case "vec2", "ivec2", "uvec2", "bvec2" -> 2;
            case "vec3", "ivec3", "uvec3", "bvec3" -> 3;
            case "vec4", "ivec4", "uvec4", "bvec4" -> 4;
            case "mat2" -> 2;
            case "mat3" -> 3;
            case "mat4" -> 4;
            default -> 1;
        };
    }

    /**
     * Claims {@code width} consecutive locations in {@code used}: the source's own number when it declared one
     * (a second claim on any of those slots is a refusal naming the declaration), otherwise the first free run.
     */
    private static int claimLocation(java.util.BitSet used, int explicit, int width, String what) {
        if (explicit >= 0) {
            if (!used.get(explicit, explicit + width).isEmpty()) {
                throw new IllegalArgumentException(what + " declares location " + explicit
                        + ", which another declaration already uses");
            }
            used.set(explicit, explicit + width);
            return explicit;
        }
        int location = 0;
        while (!used.get(location, location + width).isEmpty()) {
            location++;
        }
        used.set(location, location + width);
        return location;
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
