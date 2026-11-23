package org.soutalab.celtium.vulkan.gl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * -> {@code texture}, {@code textureLod}. The legacy shadow calls ({@code shadow1D}/{@code shadow2D} and
 * their Proj forms) -> the modern comparison fetch wrapped back into the {@code vec4} the legacy call
 * answers with, and a shadow sampler type is carried as declared: whether the texture a binding resolves
 * to has a comparing sampler is a property of that texture, checked by the prepare path, not here.</li>
 * <li>On by default ({@code -Dceltium.vulkan.fractionalPowGuard=false} opts out): a {@code pow} whose
 * exponent is, as written, a positive fractional constant is routed through {@code celtium_pow_guard},
 * which never calls {@code pow} on a non-positive base. Measured cause: BSL's light shafts reach
 * {@code pow(vl / 32, 0.25)} at exactly 0 - the value its own falloff clamp produces - and RADV's native
 * execution answers NaN over the anti-sun cone (wedge-16); the verification leg (item 684) measured the
 * cone NaN gone with the guard on and reproduced with it off, which is what flipped the default. With
 * the switch off the lowering is byte-for-byte what it was before the guard existed.</li>
 * <li>The fixed-function inputs mods still read: {@code gl_Vertex}, {@code gl_Color}, {@code gl_MultiTexCoord0},
 * {@code gl_ModelViewProjectionMatrix}, {@code gl_ModelViewMatrix}, {@code gl_ProjectionMatrix},
 * {@code gl_NormalScale}, {@code ftransform()}, and the {@code gl_TexCoord[0]}/{@code gl_FrontColor} varyings. They become ordinary
 * attributes, uniforms and varyings with {@code celtium_} names, listed in the result like the mod's own.</li>
 * <li>Declarations inside preprocessor conditionals are extracted from the branch the source's own
 * {@code #define}s select: a declaration in a branch those defines certainly reject is not extracted (its text
 * stays in the body inside its conditional, where shaderc's preprocessor drops it), so the two spellings of one
 * name in exclusive branches - Complementary's {@code shadowtex0}, a plain sampler in the composite stage and a
 * shadow sampler elsewhere - no longer read as one name declared with two types. A condition the source's own
 * defines cannot decide keeps the extract-everything behaviour, collision refusal included.</li>
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
                         String failure, String regionOffsetMember, List<String> sharedPerFrameMembers,
                         List<UniformMember> extractedUniforms) {
        public boolean ok() {
            return this.failure == null;
        }
    }

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
    /**
     * A constant constructor initializer: a type name, an optional array size, and the argument text. The
     * bracket group also matches the unsized form {@code float[](...)} (group 2 is then empty, not null):
     * GLSL lets an array constructor omit the size and take it from the context, and a uniform declaration
     * supplies that context - Twilight Forest's aurora shader writes both of its blur tables that way, and
     * the sized-only pattern made the initializer unrecognisable before evaluation even saw it (item 726).
     */
    private static final Pattern CONSTRUCTOR_INITIALIZER = Pattern.compile(
            "(\\w+)\\s*(?:\\[\\s*(\\d*)\\s*\\])?\\s*\\((.*)\\)", Pattern.DOTALL);
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
    private static final Pattern GL_NORMAL_SCALE_PATTERN = Pattern.compile("\\bgl_NormalScale\\b");
    private static final Pattern TEX1D_LOD_PATTERN = Pattern.compile("\\btexture1DLod\\s*\\(");
    private static final Pattern TEX1D_PROJ_PATTERN = Pattern.compile("\\btexture1DProj\\s*\\(");
    private static final Pattern TEX1D_PATTERN = Pattern.compile("\\btexture1D\\s*\\(");
    private static final Pattern TEX2D_PROJ_LOD_PATTERN = Pattern.compile("\\btexture2DProjLod\\s*\\(");
    private static final Pattern TEX2D_PROJ_PATTERN = Pattern.compile("\\btexture2DProj\\s*\\(");
    private static final Pattern TEX2D_LOD_PATTERN = Pattern.compile("\\btexture2DLod\\s*\\(");
    private static final Pattern TEX2D_GRAD_ARB_PATTERN = Pattern.compile("\\btexture2DGradARB\\s*\\(");
    private static final Pattern TEX2D_RECT_PROJ_PATTERN = Pattern.compile("\\btexture2DRectProj\\s*\\(");
    private static final Pattern TEX2D_RECT_PATTERN = Pattern.compile("\\btexture2DRect\\s*\\(");
    private static final Pattern TEX2D_PATTERN = Pattern.compile("\\btexture2D\\s*\\(");
    private static final Pattern TEX3D_LOD_PATTERN = Pattern.compile("\\btexture3DLod\\s*\\(");
    private static final Pattern TEX3D_PROJ_PATTERN = Pattern.compile("\\btexture3DProj\\s*\\(");
    private static final Pattern TEX3D_PATTERN = Pattern.compile("\\btexture3D\\s*\\(");
    private static final Pattern TEXCUBE_LOD_PATTERN = Pattern.compile("\\btextureCubeLod\\s*\\(");
    private static final Pattern TEXCUBE_GRAD_ARB_PATTERN = Pattern.compile("\\btextureCubeGradARB\\s*\\(");
    private static final Pattern TEXCUBE_PATTERN = Pattern.compile("\\btextureCube\\s*\\(");
    /**
     * The legacy shadow-sampling calls. GLSL 1.20 answers them with a {@code vec4} - the comparison result
     * in the first three components, 1.0 in the fourth - where the modern {@code texture} on a shadow
     * sampler returns the comparison alone, so the lowering wraps the fetch: {@code shadow2D(s, c)} becomes
     * {@code vec4(vec3(texture(s, c)), 1.0)}, and the Proj forms wrap {@code textureProj} the same way. The
     * coordinate text carries over for the 2D forms (a {@code vec3} whose third component is the reference
     * value; a {@code vec4} divided through for Proj) and for {@code shadow1DProj}; {@code shadow1D}'s
     * {@code vec3} narrows to the {@code vec2} the modern signature takes, keeping the coordinate and the
     * reference value and dropping the ignored middle component. A call whose argument list this lowering
     * cannot parse - it does not close, or it does not split into a sampler, a coordinate and an optional
     * bias - is refused by name, not passed through to die at shader compilation as an unknown function.
     */
    private static final Pattern SHADOW_CALL_PATTERN = Pattern.compile(
            "\\b(shadow1DProj|shadow2DProj|shadow1D|shadow2D)\\s*\\(");
    /** A call to the built-in {@code pow}: the name, then the opening parenthesis of its argument list. */
    private static final Pattern POW_CALL_PATTERN = Pattern.compile("(?<![\\w.])pow\\s*\\(");
    /** A vector constructor heading an exponent written as one call: the type name and the argument text. */
    private static final Pattern EXPONENT_CONSTRUCTOR = Pattern.compile("(vec[234])\\s*\\((.*)\\)",
            Pattern.DOTALL);

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
                    Map.of(), outside.getMessage(), null, List.of(), List.of());
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
        vertex = renameBuiltinCollidingFunctions(vertex, dialectVersion(vertexSource));
        fragment = renameBuiltinCollidingFunctions(fragment, dialectVersion(fragmentSource));
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
        // The common block below is emitted without an instance name, so its member names land in the
        // global scope of BOTH stages. A uniform declared in one stage whose name another stage uses for a
        // varying (or either stage for an attribute / fragment output) therefore redefines that name in the
        // other stage's copy of the block - Twilight Forest's starburst and shield both declare
        // `uniform vec3 position` in the fragment stage while their shared vertex stage writes a
        // `varying vec3 position`, and glslang refuses the pair (item 726). Such a member is emitted under
        // an internal name instead; the reported interface (Result.uniforms, the defaults, the packing)
        // keeps the GL name, exactly as the reserved-word rename above does. References are rewritten only
        // in a stage that declares the uniform - in the other stage the name means the varying, and its
        // text is not touched.
        java.util.Set<String> ioNames = new java.util.HashSet<>();
        for (List<Declaration> stage : List.of(vertexDecls, fragmentDecls)) {
            for (Declaration d : stage) {
                if (!"uniform".equals(d.qualifier)) {
                    ioNames.add(d.name);
                }
            }
        }
        // Keyed by the member's GL name; the value is the name the block and the declaring stages use.
        Map<String, String> uniformBlockRenames = new LinkedHashMap<>();
        for (Declaration d : uniforms.values()) {
            if (isSamplerType(d.type) || d.type.toLowerCase(java.util.Locale.ROOT).contains("image")
                    || !ioNames.contains(d.name)) {
                continue;
            }
            uniformBlockRenames.put(glName(d.name), "celtium_u_" + d.name);
        }
        if (!uniformBlockRenames.isEmpty()) {
            vertex = renameUniformReferences(vertex, vertexDecls, uniformBlockRenames);
            fragment = renameUniformReferences(fragment, fragmentDecls, uniformBlockRenames);
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
        // A lowered gl_NormalScale carries a default of 1.0, the value GL presents when neither
        // GL_RESCALE_NORMAL nor GL_NORMALIZE is enabled: nothing in the stub records either enable, so
        // that is the only value a draw here can have. It travels in the defaults map, so a value a
        // program is actually written under the name replaces it, as a first glUniform write replaces
        // a declared initializer's default.
        for (UniformMember member : members) {
            if (member.name().equals("celtium_NormalScale")) {
                uniformDefaults.putIfAbsent(member.name(), new float[] {1.0f});
            }
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

        // GLSM stage 1 (glsm-stage1-design.md §2.2/§2.3), behind -Dceltium.vulkan.glsmPackState and
        // only on the buffer route, whose programs are the pack terrain programs this stage targets:
        // the REGION member leaves the block for the indexed SSBO table the built-in route already
        // proves (CeltiumRegionOffsets, binding after the block's own), and PER_FRAME standard
        // members leave for the one shared block every program declares identically (binding after
        // the table). The pipeline declares the same binding arithmetic. Extracted members stay in
        // the reported interface as Result.extractedUniforms, so the registry still maps their
        // locations and records their values - the values are what the harvester packs and what a
        // region write records - they simply own no block bytes any more. A member the block-rename
        // pass above claimed (its name collides with an input/output) is not extracted: its body
        // references no longer spell the plain name this pass would rewrite.
        String glsmRegionMember = null;
        List<String> glsmSharedPerFrame = new ArrayList<>();
        List<UniformMember> glsmExtracted = new ArrayList<>();
        boolean glsmFragmentRegion = false;
        if (GlsmPackState.ENABLED && uniformsViaBuffer) {
            for (UniformMember m : members) {
                GlsmPackState.Bucket bucket = GlsmPackState.classify(m.name(), m.type(), m.arrayLength());
                if (bucket == GlsmPackState.Bucket.REGION && glsmRegionMember == null
                        && !uniformBlockRenames.containsKey(m.name())) {
                    glsmRegionMember = m.name();
                } else if (bucket == GlsmPackState.Bucket.PER_FRAME
                        && !uniformBlockRenames.containsKey(m.name())) {
                    glsmSharedPerFrame.add(m.name());
                } else if (GlsmPackState.isRegionName(m.name())) {
                    // A region-named member whose shape or rename state refused extraction: counted,
                    // never silently left as a permanent fallback - the stage's exit condition reads this.
                    GlsmPackState.noteRegionUnextractable();
                }
            }
            if (glsmRegionMember != null || !glsmSharedPerFrame.isEmpty()) {
                if (glsmRegionMember != null) {
                    vertex = renameUniformReferences(vertex, vertexDecls,
                            Map.of(glsmRegionMember, "celtium_regionOffset()"));
                    boolean fragmentDeclares = fragmentDecls.stream().anyMatch(
                            d -> "uniform".equals(d.qualifier) && glName(d.name).equals(glsmRegionMember));
                    if (fragmentDeclares) {
                        fragment = renameUniformReferences(fragment, fragmentDecls,
                                Map.of(glsmRegionMember, "celtium_v_regionOffset"));
                        glsmFragmentRegion = true;
                    }
                    GlsmPackState.noteRegionExtracted();
                }
                List<UniformMember> kept = new ArrayList<>();
                int keptOffset = 0;
                for (UniformMember m : members) {
                    if (m.name().equals(glsmRegionMember) || glsmSharedPerFrame.contains(m.name())) {
                        glsmExtracted.add(m);
                        continue;
                    }
                    int[] layout = std140(m.type(), m.arrayLength());
                    keptOffset = align(keptOffset, layout[0]);
                    kept.add(new UniformMember(m.name(), m.type(), m.arrayLength(), keptOffset, layout[1]));
                    keptOffset += layout[1];
                }
                members = kept;
                blockSize = align(keptOffset, 16);
            }
        }

        List<Attribute> attributes = new ArrayList<>();
        Map<String, Declaration> vertexAttributes = new LinkedHashMap<>();
        java.util.BitSet attributeLocations = new java.util.BitSet();
        for (Declaration d : vertexDecls) {
            if ("attribute".equals(d.qualifier)) {
                Declaration prev = vertexAttributes.get(d.name);
                if (prev != null) {
                    if (!prev.type.equals(d.type)) {
                        throw new IllegalArgumentException("attribute " + d.name + " is declared as " + prev.type
                                + " and " + d.type);
                    }
                    continue;
                }
                vertexAttributes.put(d.name, d);
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
                common.append("    ").append(m.type).append(' ')
                        .append(uniformBlockRenames.getOrDefault(m.name(), glslName(m.name())))
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

        // GLSM stage 1 headers: the shared per-frame block rides in the common header (both stages
        // declare it identically, at the binding after the region table's), and the region table
        // itself is vertex-only, exactly as the built-in route declares its binding-5 array. The
        // shared block declares the full standard member set in the classification's fixed order -
        // the layout GlsmPackState packs - whether or not this program reads every member.
        if (!glsmSharedPerFrame.isEmpty()) {
            common.append("layout(set = 0, binding = ").append(samplers.size() + images.size() + 2)
                    .append(", std140) uniform CeltiumPerFrame {\n");
            for (Map.Entry<String, String> member : GlsmPackState.PER_FRAME_MEMBERS.entrySet()) {
                common.append("    ").append(member.getValue()).append(' ').append(member.getKey()).append(";\n");
            }
            common.append("};\n");
        }
        StringBuilder glsmVertexHeader = new StringBuilder();
        if (glsmRegionMember != null) {
            glsmVertexHeader.append("layout(std430, set = 0, binding = ")
                    .append(samplers.size() + images.size() + 1)
                    .append(") readonly buffer CeltiumRegionOffsets {\n    vec4 celtium_regionOffsets[];\n};\n")
                    .append("vec3 celtium_regionOffset() { return celtium_regionOffsets[gl_InstanceIndex].xyz; }\n");
        }

        StringBuilder vs = new StringBuilder("#version 450\n").append(common).append(glsmVertexHeader);
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

        // GLSM stage 1: a fragment stage that reads the region offset gets it through a flat varying
        // the vertex stage fills from the table - the interface contract's own shape (the offset is
        // vertex-supplied; fragment consumption travels by varying). Its location is claimed after
        // every program varying, so it can never renumber one.
        if (glsmFragmentRegion) {
            int regionVaryingLocation = claimLocation(varyingLocations, -1, 1, "celtium region varying");
            vs.append("layout(location = ").append(regionVaryingLocation)
                    .append(") flat out vec3 celtium_v_regionOffset;\n");
            fs.append("layout(location = ").append(regionVaryingLocation)
                    .append(") flat in vec3 celtium_v_regionOffset;\n");
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
        vertex = lowerTextureCalls(lowerShadowCalls(guardFractionalPow(vertex)));
        fragment = lowerTextureCalls(lowerShadowCalls(guardFractionalPow(fragment)));

        vs.append(vertex).append("\nvoid main() {\n")
                .append(glsmFragmentRegion ? "    celtium_v_regionOffset = celtium_regionOffset();\n" : "")
                .append("    celtium_user_main();\n")
                // GL's clip depth is [-1, 1], Vulkan's [0, 1]. y is not flipped: like every other pass here the image
                // keeps GL's row order (VulkanTerrainGpuBackend.remapClipDepth remaps depth only).
                .append("    gl_Position.z = (gl_Position.z + gl_Position.w) * 0.5;\n}\n");
        fs.append(fragment).append("\nvoid main() {\n    celtium_user_main();\n}\n");
        return new Result(vs.toString(), fs.toString(), Collections.unmodifiableList(attributes),
                Collections.unmodifiableList(members), blockSize, Collections.unmodifiableList(samplers),
                Collections.unmodifiableList(images),
                Collections.unmodifiableList(varyings), fragOutputCount, uniformsViaBuffer,
                Collections.unmodifiableMap(uniformDefaults), null, glsmRegionMember,
                Collections.unmodifiableList(glsmSharedPerFrame),
                Collections.unmodifiableList(glsmExtracted));
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

    /**
     * Function names GLSL 1.30 added as built-ins. A {@code #version} 120 (or unversioned) source may define
     * its own function under one of these names - Twilight Forest's pulsing_yellow defines
     * {@code float round(float)} - and in 120 that definition is the only {@code round} there is. Lowered to
     * {@code #version 450} it lands in the same overload set as glslang's built-in declaration, whose
     * parameter carries a {@code highp} precision qualifier the source's definition does not, and glslang
     * refuses the pair ("overloaded functions must have the same parameter precision qualifiers", item 726).
     */
    private static final Pattern BUILTIN_COLLIDING_DEFINITION = Pattern.compile(
            "(?<![\\w.])(?:\\w+)\\s+(round|roundEven|trunc)\\s*\\([^;{}]*\\)\\s*[{;]");

    /** The {@code #version} a source declares, or 110 - the dialect an unversioned legacy source is written in. */
    private static int dialectVersion(String source) {
        if (source == null) {
            return 110;
        }
        Matcher m = VERSION_NUMBER.matcher(source);
        return m.find() ? Integer.parseInt(m.group(1)) : 110;
    }

    /**
     * Renames a stage's own definitions of {@link #BUILTIN_COLLIDING_DEFINITION} names - and every call to
     * them in the same stage - to {@code celtium_r_<name>}, the {@link #renameReserved} prefix. Only a
     * pre-1.30 dialect is touched: there the name cannot be the built-in (it did not exist yet), so a
     * definition under it is unambiguously the source's own function. At 1.30 and above a definition and a
     * built-in call cannot be told apart mechanically, and renaming could change what a call means, so
     * those sources keep the compiler's own verdict. A function name is not part of the uniform interface,
     * so nothing in the reported {@code Result} changes.
     */
    private static String renameBuiltinCollidingFunctions(String source, int version) {
        if (version >= 130) {
            return source;
        }
        java.util.Set<String> defined = new java.util.LinkedHashSet<>();
        Matcher m = BUILTIN_COLLIDING_DEFINITION.matcher(source);
        while (m.find()) {
            defined.add(m.group(1));
        }
        for (String name : defined) {
            source = Pattern.compile("\\b" + name + "\\s*\\(").matcher(source)
                    .replaceAll(RENAMED + name + "(");
        }
        return source;
    }

    /**
     * Rewrites one stage's references to the uniforms {@code renames} covers (keyed by GL name), for the
     * stages that declare them. A stage that also declares the same name as an input/output, or whose body
     * declares a local variable or parameter under it, makes the name's meaning in that stage ambiguous to
     * a word-level rewrite, so those shapes are refused by name instead of renamed into a wrong program.
     */
    private static String renameUniformReferences(String body, List<Declaration> stageDecls,
                                                  Map<String, String> renames) {
        for (Declaration d : stageDecls) {
            if (!"uniform".equals(d.qualifier)) {
                continue;
            }
            String blockName = renames.get(glName(d.name));
            if (blockName == null) {
                continue;
            }
            for (Declaration other : stageDecls) {
                if (!"uniform".equals(other.qualifier) && other.name.equals(d.name)) {
                    throw new IllegalArgumentException("uniform " + d.name + " is also declared as a "
                            + other.qualifier + " in the same stage: the name collision this lowering "
                            + "renames around spans both meanings in one stage and is not lowered");
                }
            }
            Pattern shadowed = Pattern.compile(
                    "(?<![\\w.])(?!(?:return|if|while|for|switch|else|do|case)\\s)\\w+\\s+"
                            + Pattern.quote(d.name) + "(?=\\s*[,;=\\)\\[])");
            if (shadowed.matcher(body).find()) {
                throw new IllegalArgumentException("uniform " + d.name + " collides with an input/output "
                        + "name and is shadowed by a local declaration in a stage that declares it: "
                        + "not lowered");
            }
            body = Pattern.compile("\\b" + Pattern.quote(d.name) + "\\b").matcher(body)
                    .replaceAll(blockName);
        }
        return body;
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
        String s = stripComments(source.replace("\r\n", "\n"));
        s = VERSION.matcher(s).replaceAll("");
        return EXTENSION.matcher(s).replaceAll("");
    }

    /**
     * Blanks the comments out of a source, one pass, the way GLSL itself lexes them: whichever comment form
     * opens first owns the text until its own end, so a {@code /*} inside a {@code //} comment opens nothing
     * and a {@code //} inside a block comment ends nothing. The two-pattern version this replaces ran the
     * block pattern over the whole text first, so a {@code /*} mentioned in a line comment (a note about a
     * block comment, a commented-out line carrying one) paired with the next real {@code *}{@code /} anywhere
     * below and ate the live code in between - declarations included, which then never reached the interface.
     * Comment characters become spaces and newlines stay, so the cleaned text keeps the source's line layout
     * for the line-anchored patterns that run on it. An unterminated block comment blanks to the end of the
     * source: the text after it is comment by the same lexing, and what it held is not code this lowering
     * may extract declarations from.
     */
    private static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                out.append("  ");
                i += 2;
                while (i < n && source.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                out.append("  ");
                i += 2;
                while (i < n && !(source.charAt(i) == '*' && i + 1 < n && source.charAt(i + 1) == '/')) {
                    out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < n) {
                    out.append("  ");
                    i += 2;
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /**
     * {@return whether {@code offset} sits on a preprocessor line} - a line whose first non-whitespace
     * character is {@code #}. Declaration patterns run on raw text, and a preprocessor line's text is not
     * code: a {@code #define} body is stamped out by the compiler's expansion, at sites this lowering does
     * not model, so a declaration shape found there names the macro's own text, not an interface member.
     */
    private static boolean onPreprocessorLine(String text, int offset) {
        int lineStart = offset == 0 ? 0 : text.lastIndexOf('\n', offset - 1) + 1;
        int i = lineStart;
        while (i < offset && (text.charAt(i) == ' ' || text.charAt(i) == '\t' || text.charAt(i) == '\r')) {
            i++;
        }
        return i < offset && text.charAt(i) == '#';
    }

    // ----- Extraction-side conditional evaluation ----------------------------------------------------
    //
    // Declaration extraction used to read every branch of every preprocessor conditional, so one name spelled
    // with two types in exclusive branches (Complementary's shadowtex0: sampler2D under #ifdef COMPOSITE1,
    // sampler2DShadow in the #else) merged into a single interface entry and refused as a type collision, even
    // though the driver only ever compiles one of the spellings. The classification below follows the branch
    // the source's own #defines select - the same text the driver's preprocessor sees, and glShaderSource has
    // no other define route into it. Only extraction follows it: the body, conditional directives and rejected
    // branches included, is emitted exactly as before, and shaderc's preprocessor makes the same selection
    // there. A condition the source's own defines cannot decide (an implementation-reserved name, a
    // function-like macro call, a non-integer token) classifies as REGION_UNKNOWN and keeps the
    // extract-everything behaviour, collision refusal included.

    /** A position is in a branch the enclosing conditionals certainly select. */
    private static final byte REGION_ACTIVE = 0;
    /** A position is in a branch the enclosing conditionals certainly reject. */
    private static final byte REGION_INACTIVE = 1;
    /** The enclosing conditionals cannot be decided from the source's own {@code #define}s. */
    private static final byte REGION_UNKNOWN = 2;

    private static final Pattern DIRECTIVE = Pattern.compile("^\\s*#\\s*([A-Za-z]+)\\s*(.*)$");
    private static final Pattern DEFINE_HEAD = Pattern.compile("^([A-Za-z_]\\w*)(\\([^)]*\\))?\\s*(.*)$");
    private static final Pattern IDENTIFIER_HEAD = Pattern.compile("^\\s*([A-Za-z_]\\w*)");

    /** Control flow for "this condition is not decidable here"; never escapes {@link #evalCondition}. */
    private static final class Undecidable extends RuntimeException {
        private Undecidable() {
            super(null, null, false, false);
        }
    }

    private static final Undecidable UNDECIDABLE = new Undecidable();

    private static byte and3(byte a, byte b) {
        return a == REGION_INACTIVE || b == REGION_INACTIVE ? REGION_INACTIVE
                : a == REGION_UNKNOWN || b == REGION_UNKNOWN ? REGION_UNKNOWN : REGION_ACTIVE;
    }

    private static byte or3(byte a, byte b) {
        return a == REGION_ACTIVE || b == REGION_ACTIVE ? REGION_ACTIVE
                : a == REGION_UNKNOWN || b == REGION_UNKNOWN ? REGION_UNKNOWN : REGION_INACTIVE;
    }

    private static byte not3(byte a) {
        return a == REGION_ACTIVE ? REGION_INACTIVE : a == REGION_INACTIVE ? REGION_ACTIVE : REGION_UNKNOWN;
    }

    /** The {@code #define} state at one point of a stage source, applied in source order. */
    private static final class DefineTable {
        /** Object-like macros: name -> replacement text. */
        private final Map<String, String> objectLike = new LinkedHashMap<>();
        /** Function-like macros: name -> parameter list and replacement text (kept for redefinition checks). */
        private final Map<String, String> functionLike = new LinkedHashMap<>();
        /** Names whose defined-ness an undecidable region left open: defined on one possible path only. */
        private final Set<String> poisoned = new HashSet<>();

        /** {@return 1 when the name is certainly defined here, 0 when certainly not, -1 when undecidable}. */
        private int definedness(String name) {
            if (this.poisoned.contains(name)) {
                return -1;
            }
            if (this.objectLike.containsKey(name) || this.functionLike.containsKey(name)) {
                return 1;
            }
            // A name in the implementation's reserved space (GL_/__/gl_) may be predefined by the compiler
            // itself, so the source's own text cannot settle it either way.
            return name.startsWith("GL_") || name.startsWith("__") || name.startsWith("gl_") ? -1 : 0;
        }

        private void poison(String name) {
            this.objectLike.remove(name);
            this.functionLike.remove(name);
            this.poisoned.add(name);
        }

        private void define(String name, String replacement, boolean function, byte region) {
            if (region == REGION_INACTIVE) {
                return;
            }
            if (region == REGION_UNKNOWN) {
                poison(name);
                return;
            }
            this.poisoned.remove(name);
            boolean wasFunction = this.functionLike.containsKey(name);
            boolean wasObject = this.objectLike.containsKey(name);
            if (wasFunction || wasObject) {
                String previous = wasFunction ? this.functionLike.get(name) : this.objectLike.get(name);
                if (function != wasFunction || !replacement.equals(previous)) {
                    // A redefinition with a different body: which value a later condition sees is not this
                    // classification's to guess (GLSL calls it an error; drivers differ).
                    poison(name);
                }
                return;
            }
            if (function) {
                this.functionLike.put(name, replacement);
            } else {
                this.objectLike.put(name, replacement);
            }
        }

        private void undef(String name, byte region) {
            if (region == REGION_ACTIVE) {
                this.objectLike.remove(name);
                this.functionLike.remove(name);
                this.poisoned.remove(name);
            } else if (region == REGION_UNKNOWN) {
                poison(name);
            }
        }
    }

    /** One open {@code #if} chain: the current branch's activity, and whether an earlier branch was taken. */
    private static final class ConditionalFrame {
        private byte branch;
        private byte taken;
        private boolean seenElse;

        private ConditionalFrame(byte branch) {
            this.branch = branch;
            this.taken = branch;
        }
    }

    private static byte regionOf(List<ConditionalFrame> frames) {
        byte region = REGION_ACTIVE;
        for (ConditionalFrame frame : frames) {
            region = and3(region, frame.branch);
        }
        return region;
    }

    /**
     * {@return the activity of every position of a stripped stage source} under the preprocessor conditionals
     * enclosing it. Directive lines themselves classify {@link #REGION_UNKNOWN} (a declaration spelled inside a
     * {@code #define} body is extracted exactly as before this classification existed), and a structurally
     * broken conditional (an {@code #else} with no open chain, a second {@code #else}) makes everything after
     * it unknown rather than risk following a chain that has lost its shape.
     */
    private static byte[] conditionalActivity(String source) {
        byte[] activity = new byte[source.length()];
        DefineTable defines = new DefineTable();
        List<ConditionalFrame> frames = new ArrayList<>();
        boolean broken = false;
        int pos = 0;
        while (pos < source.length()) {
            int lineEnd = source.indexOf('\n', pos);
            if (lineEnd < 0) {
                lineEnd = source.length();
            }
            // A logical line: a trailing backslash splices the next physical line, as the preprocessor does.
            StringBuilder logical = new StringBuilder(source.substring(pos, lineEnd));
            int logicalEnd = lineEnd;
            while (logical.length() > 0 && logical.charAt(logical.length() - 1) == '\\'
                    && logicalEnd < source.length()) {
                logical.setLength(logical.length() - 1);
                int nextEnd = source.indexOf('\n', logicalEnd + 1);
                if (nextEnd < 0) {
                    nextEnd = source.length();
                }
                logical.append(' ').append(source, logicalEnd + 1, nextEnd);
                logicalEnd = nextEnd;
            }
            int spanEnd = Math.min(logicalEnd + 1, source.length());
            Matcher directive = DIRECTIVE.matcher(logical);
            if (broken || !directive.find()) {
                Arrays.fill(activity, pos, spanEnd, broken ? REGION_UNKNOWN : regionOf(frames));
                pos = spanEnd;
                continue;
            }
            Arrays.fill(activity, pos, spanEnd, REGION_UNKNOWN);
            pos = spanEnd;
            String rest = directive.group(2);
            switch (directive.group(1)) {
                case "if" -> frames.add(new ConditionalFrame(evalCondition(rest, defines)));
                case "ifdef", "ifndef" -> {
                    byte branch = REGION_UNKNOWN;
                    Matcher id = IDENTIFIER_HEAD.matcher(rest);
                    if (id.find()) {
                        int defined = defines.definedness(id.group(1));
                        branch = defined > 0 ? REGION_ACTIVE
                                : defined == 0 ? REGION_INACTIVE : REGION_UNKNOWN;
                        if ("ifndef".equals(directive.group(1))) {
                            branch = not3(branch);
                        }
                    }
                    frames.add(new ConditionalFrame(branch));
                }
                case "elif" -> {
                    ConditionalFrame top = frames.isEmpty() ? null : frames.get(frames.size() - 1);
                    if (top == null || top.seenElse) {
                        broken = true;
                    } else {
                        top.branch = and3(not3(top.taken), evalCondition(rest, defines));
                        top.taken = or3(top.taken, top.branch);
                    }
                }
                case "else" -> {
                    ConditionalFrame top = frames.isEmpty() ? null : frames.get(frames.size() - 1);
                    if (top == null || top.seenElse) {
                        broken = true;
                    } else {
                        top.branch = not3(top.taken);
                        top.taken = or3(top.taken, top.branch);
                        top.seenElse = true;
                    }
                }
                case "endif" -> {
                    if (frames.isEmpty()) {
                        broken = true;
                    } else {
                        frames.remove(frames.size() - 1);
                    }
                }
                case "define" -> {
                    Matcher head = DEFINE_HEAD.matcher(rest);
                    if (head.find()) {
                        boolean function = head.group(2) != null;
                        String replacement = function
                                ? head.group(2) + " " + head.group(3).strip()
                                : head.group(3).strip();
                        defines.define(head.group(1), replacement, function, regionOf(frames));
                    }
                }
                case "undef" -> {
                    Matcher id = IDENTIFIER_HEAD.matcher(rest);
                    if (id.find()) {
                        defines.undef(id.group(1), regionOf(frames));
                    }
                }
                default -> {
                    // #pragma, #error, #line and friends carry no conditional state.
                }
            }
        }
        return activity;
    }

    /** {@return the branch activity a {@code #if}/{@code #elif} condition selects}, or unknown. */
    private static byte evalCondition(String text, DefineTable defines) {
        try {
            List<CondToken> tokens = expandCondition(tokenizeCondition(text), defines, Set.of(), 0);
            long value = new ConditionParser(tokens, defines).parse();
            return value != 0 ? REGION_ACTIVE : REGION_INACTIVE;
        } catch (Undecidable undecidable) {
            return REGION_UNKNOWN;
        }
    }

    private enum CondKind { NUMBER, IDENT, DEFINED, OP, LPAREN, RPAREN, QUESTION, COLON, END }

    private record CondToken(CondKind kind, long value, String text) { }

    private static List<CondToken> tokenizeCondition(String text) {
        List<CondToken> tokens = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (Character.isDigit(c)) {
                int j = i + 1;
                while (j < text.length()) {
                    char d = text.charAt(j);
                    if (!Character.isLetterOrDigit(d) && d != '.' && d != '\'') {
                        break;
                    }
                    j++;
                }
                tokens.add(new CondToken(CondKind.NUMBER, conditionNumber(text.substring(i, j)), null));
                i = j;
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int j = i + 1;
                while (j < text.length()
                        && (Character.isLetterOrDigit(text.charAt(j)) || text.charAt(j) == '_')) {
                    j++;
                }
                String word = text.substring(i, j);
                tokens.add(new CondToken("defined".equals(word) ? CondKind.DEFINED : CondKind.IDENT, 0, word));
                i = j;
                continue;
            }
            if (i + 1 < text.length()) {
                String pair = text.substring(i, i + 2);
                if (List.of("<<", ">>", "<=", ">=", "==", "!=", "&&", "||").contains(pair)) {
                    tokens.add(new CondToken(CondKind.OP, 0, pair));
                    i += 2;
                    continue;
                }
            }
            switch (c) {
                case '+', '-', '*', '/', '%', '<', '>', '&', '|', '^', '~', '!' ->
                        tokens.add(new CondToken(CondKind.OP, 0, String.valueOf(c)));
                case '(' -> tokens.add(new CondToken(CondKind.LPAREN, 0, null));
                case ')' -> tokens.add(new CondToken(CondKind.RPAREN, 0, null));
                case '?' -> tokens.add(new CondToken(CondKind.QUESTION, 0, null));
                case ':' -> tokens.add(new CondToken(CondKind.COLON, 0, null));
                default -> throw UNDECIDABLE;
            }
            i++;
        }
        tokens.add(new CondToken(CondKind.END, 0, null));
        return tokens;
    }

    /** {@return an integer literal of a condition} (decimal, hex or octal, suffixes dropped), or undecidable. */
    private static long conditionNumber(String raw) {
        String core = raw.replaceAll("[uUlL]+$", "");
        try {
            if (core.startsWith("0x") || core.startsWith("0X")) {
                String hex = core.substring(2);
                if (hex.isEmpty() || !hex.matches("[0-9a-fA-F]+")) {
                    throw UNDECIDABLE;
                }
                return Long.parseUnsignedLong(hex, 16);
            }
            if (core.length() > 1 && core.startsWith("0")) {
                if (!core.matches("[0-7]+")) {
                    throw UNDECIDABLE;
                }
                return Long.parseUnsignedLong(core, 8);
            }
            if (!core.matches("[0-9]+")) {
                throw UNDECIDABLE;
            }
            return Long.parseUnsignedLong(core, 10);
        } catch (NumberFormatException overflow) {
            throw UNDECIDABLE;
        }
    }

    /**
     * Expands object-like macros in a condition's tokens. The operand of {@code defined} is copied verbatim
     * (it is the one place expansion must not reach), a function-like macro invocation is undecidable, and a
     * name already being expanded stays an identifier, which the parser then refuses as a cycle.
     */
    private static List<CondToken> expandCondition(List<CondToken> tokens, DefineTable defines,
                                                   Set<String> expanding, int depth) {
        if (depth > 32) {
            throw UNDECIDABLE;
        }
        List<CondToken> out = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            CondToken token = tokens.get(i);
            if (token.kind() == CondKind.DEFINED) {
                out.add(token);
                int j = i + 1;
                if (j < tokens.size() && tokens.get(j).kind() == CondKind.LPAREN) {
                    out.add(tokens.get(j++));
                }
                if (j < tokens.size() && tokens.get(j).kind() == CondKind.IDENT) {
                    out.add(tokens.get(j++));
                }
                if (j < tokens.size() && tokens.get(j).kind() == CondKind.RPAREN) {
                    out.add(tokens.get(j++));
                }
                i = j - 1;
                continue;
            }
            if (token.kind() == CondKind.IDENT) {
                String name = token.text();
                if (defines.poisoned.contains(name)) {
                    throw UNDECIDABLE;
                }
                String replacement = defines.objectLike.get(name);
                if (replacement != null && !expanding.contains(name)) {
                    Set<String> nested = new HashSet<>(expanding);
                    nested.add(name);
                    for (CondToken expanded : expandCondition(tokenizeCondition(replacement), defines, nested,
                            depth + 1)) {
                        if (expanded.kind() != CondKind.END) {
                            out.add(expanded);
                        }
                    }
                    continue;
                }
                if (defines.functionLike.containsKey(name) && i + 1 < tokens.size()
                        && tokens.get(i + 1).kind() == CondKind.LPAREN) {
                    throw UNDECIDABLE;
                }
            }
            out.add(token);
        }
        return out;
    }

    /** A recursive-descent evaluator for a condition's integer expression, after macro expansion. */
    private static final class ConditionParser {
        private final List<CondToken> tokens;
        private final DefineTable defines;
        private int pos;

        private ConditionParser(List<CondToken> tokens, DefineTable defines) {
            this.tokens = tokens;
            this.defines = defines;
        }

        private long parse() {
            long value = parseTernary();
            if (peek().kind() != CondKind.END) {
                throw UNDECIDABLE;
            }
            return value;
        }

        private CondToken peek() {
            return this.tokens.get(Math.min(this.pos, this.tokens.size() - 1));
        }

        private CondToken next() {
            CondToken token = peek();
            this.pos++;
            return token;
        }

        private boolean atOp(String op) {
            CondToken token = peek();
            return token.kind() == CondKind.OP && op.equals(token.text());
        }

        private long parseTernary() {
            long condition = parseOr();
            if (peek().kind() != CondKind.QUESTION) {
                return condition;
            }
            next();
            long thenValue = 0;
            boolean thenUnknown = false;
            try {
                thenValue = parseTernary();
            } catch (Undecidable undecidable) {
                thenUnknown = true;
            }
            if (peek().kind() != CondKind.COLON) {
                throw UNDECIDABLE;
            }
            next();
            long elseValue = 0;
            boolean elseUnknown = false;
            try {
                elseValue = parseTernary();
            } catch (Undecidable undecidable) {
                elseUnknown = true;
            }
            if (condition != 0) {
                if (thenUnknown) {
                    throw UNDECIDABLE;
                }
                return thenValue;
            }
            if (elseUnknown) {
                throw UNDECIDABLE;
            }
            return elseValue;
        }

        private long parseOr() {
            long left = parseAnd();
            while (atOp("||")) {
                next();
                long right = 0;
                boolean unknown = false;
                try {
                    right = parseAnd();
                } catch (Undecidable undecidable) {
                    unknown = true;
                }
                if (left != 0) {
                    left = 1;
                } else if (unknown) {
                    throw UNDECIDABLE;
                } else {
                    left = right != 0 ? 1 : 0;
                }
            }
            return left;
        }

        private long parseAnd() {
            long left = parseBitOr();
            while (atOp("&&")) {
                next();
                long right = 0;
                boolean unknown = false;
                try {
                    right = parseBitOr();
                } catch (Undecidable undecidable) {
                    unknown = true;
                }
                if (left == 0) {
                    left = 0;
                } else if (unknown) {
                    throw UNDECIDABLE;
                } else {
                    left = right != 0 ? 1 : 0;
                }
            }
            return left;
        }

        private long parseBitOr() {
            long left = parseBitXor();
            while (atOp("|")) {
                next();
                left |= parseBitXor();
            }
            return left;
        }

        private long parseBitXor() {
            long left = parseBitAnd();
            while (atOp("^")) {
                next();
                left ^= parseBitAnd();
            }
            return left;
        }

        private long parseBitAnd() {
            long left = parseEquality();
            while (atOp("&")) {
                next();
                left &= parseEquality();
            }
            return left;
        }

        private long parseEquality() {
            long left = parseRelational();
            while (true) {
                if (atOp("==")) {
                    next();
                    left = left == parseRelational() ? 1 : 0;
                } else if (atOp("!=")) {
                    next();
                    left = left != parseRelational() ? 1 : 0;
                } else {
                    return left;
                }
            }
        }

        private long parseRelational() {
            long left = parseShift();
            while (true) {
                if (atOp("<")) {
                    next();
                    left = left < parseShift() ? 1 : 0;
                } else if (atOp("<=")) {
                    next();
                    left = left <= parseShift() ? 1 : 0;
                } else if (atOp(">")) {
                    next();
                    left = left > parseShift() ? 1 : 0;
                } else if (atOp(">=")) {
                    next();
                    left = left >= parseShift() ? 1 : 0;
                } else {
                    return left;
                }
            }
        }

        private long parseShift() {
            long left = parseAdditive();
            while (true) {
                if (atOp("<<")) {
                    next();
                    long count = parseAdditive();
                    if (count < 0 || count > 63) {
                        throw UNDECIDABLE;
                    }
                    left <<= count;
                } else if (atOp(">>")) {
                    next();
                    long count = parseAdditive();
                    if (count < 0 || count > 63) {
                        throw UNDECIDABLE;
                    }
                    left >>= count;
                } else {
                    return left;
                }
            }
        }

        private long parseAdditive() {
            long left = parseMultiplicative();
            while (true) {
                if (atOp("+")) {
                    next();
                    left += parseMultiplicative();
                } else if (atOp("-")) {
                    next();
                    left -= parseMultiplicative();
                } else {
                    return left;
                }
            }
        }

        private long parseMultiplicative() {
            long left = parseUnary();
            while (true) {
                if (atOp("*")) {
                    next();
                    left *= parseUnary();
                } else if (atOp("/")) {
                    next();
                    long right = parseUnary();
                    if (right == 0) {
                        throw UNDECIDABLE;
                    }
                    left /= right;
                } else if (atOp("%")) {
                    next();
                    long right = parseUnary();
                    if (right == 0) {
                        throw UNDECIDABLE;
                    }
                    left %= right;
                } else {
                    return left;
                }
            }
        }

        private long parseUnary() {
            if (atOp("!")) {
                next();
                return parseUnary() == 0 ? 1 : 0;
            }
            if (atOp("~")) {
                next();
                return ~parseUnary();
            }
            if (atOp("-")) {
                next();
                return -parseUnary();
            }
            if (atOp("+")) {
                next();
                return parseUnary();
            }
            return parsePrimary();
        }

        private long parsePrimary() {
            CondToken token = next();
            switch (token.kind()) {
                case NUMBER -> {
                    return token.value();
                }
                case LPAREN -> {
                    long value = parseTernary();
                    if (next().kind() != CondKind.RPAREN) {
                        throw UNDECIDABLE;
                    }
                    return value;
                }
                case DEFINED -> {
                    String name;
                    if (peek().kind() == CondKind.LPAREN) {
                        next();
                        CondToken id = next();
                        if (id.kind() != CondKind.IDENT) {
                            throw UNDECIDABLE;
                        }
                        name = id.text();
                        if (next().kind() != CondKind.RPAREN) {
                            throw UNDECIDABLE;
                        }
                    } else {
                        CondToken id = next();
                        if (id.kind() != CondKind.IDENT) {
                            throw UNDECIDABLE;
                        }
                        name = id.text();
                    }
                    int defined = this.defines.definedness(name);
                    if (defined < 0) {
                        throw UNDECIDABLE;
                    }
                    return defined;
                }
                case IDENT -> {
                    // An identifier that survived expansion: a macro cycle, or a name whose defined-ness the
                    // source cannot settle, is undecidable; any other name was never defined, and the
                    // preprocessor reads it as 0 (a bare function-like macro name included).
                    if (this.defines.objectLike.containsKey(token.text())
                            || this.defines.definedness(token.text()) < 0) {
                        throw UNDECIDABLE;
                    }
                    return 0;
                }
                default -> throw UNDECIDABLE;
            }
        }    }

    private static String removeDeclarations(String source, List<Declaration> out, boolean vertexStage) {
        byte[] activity = conditionalActivity(source);
        Matcher m = DECLARATION.matcher(source);
        StringBuilder rest = new StringBuilder();
        while (m.find()) {
            if (onPreprocessorLine(source, m.start())) {
                // A declaration inside a #define body is macro text, not a declaration: extracting it would
                // file the macro's parameter name (or whatever name the macro would have stamped out) as a
                // real member of the interface and leave the define itself gutted. Preprocessor lines are
                // left exactly as they are; an invocation's expansion is the compiler's, not this lowering's.
                m.appendReplacement(rest, Matcher.quoteReplacement(m.group()));
                continue;
            }
            if (activity[m.start()] == REGION_INACTIVE) {
                // Certainly not compiled: the declaration stays in the body inside its conditional, where
                // shaderc's preprocessor drops it; only the selected branch's declaration is hoisted.
                m.appendReplacement(rest, Matcher.quoteReplacement(m.group(0)));                continue;
            }
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
        String restText = rest.toString();
        // The conditional structure survives the first pass (directive lines are never removed), so the second
        // pass classifies its own text the same way.
        byte[] modernActivity = conditionalActivity(restText);
        Matcher io = IO_DECLARATION.matcher(restText);
        StringBuilder modern = new StringBuilder();
        while (io.find()) {
            if (onPreprocessorLine(restText, io.start())) {
                io.appendReplacement(modern, Matcher.quoteReplacement(io.group(0)));
                continue;
            }
            if (modernActivity[io.start()] == REGION_INACTIVE) {                io.appendReplacement(modern, Matcher.quoteReplacement(io.group(0)));
                continue;
            }
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
        s = replaceBuiltinPattern(s, GL_NORMAL_SCALE_PATTERN, "celtium_NormalScale", "uniform", "float", decls);
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

    /**
     * Lowers one stage's legacy shadow-sampling calls (see {@link #SHADOW_CALL_PATTERN} for the shapes and
     * the wrap). The scan is positional rather than a pattern replacement because the wrap has to span the
     * whole call: a name-only rewrite would leave the fetch returning a scalar where the source's {@code .z}
     * swizzle or {@code vec4} assignment expects the legacy four components. The scan resumes after each
     * call it wraps, so a legacy call nested inside another's arguments is not wrapped twice. This runs
     * before {@link #lowerTextureCalls}, so a legacy fetch inside a shadow call's own arguments is still
     * lowered by that pass, and the {@code texture}/{@code textureProj} this pass inserts are not its
     * patterns' targets.
     */
    private static String lowerShadowCalls(String s) {
        Matcher m = SHADOW_CALL_PATTERN.matcher(s);
        if (!m.find()) {
            return s;
        }
        StringBuilder out = new StringBuilder(s.length() + 64);
        int copied = 0;
        while (true) {
            String call = m.group(1);
            int open = m.end() - 1;
            int close = matchingParen(s, open);
            if (close < 0) {
                throw new IllegalArgumentException(
                        "shadow sampling call " + call + " is not lowered: its argument list does not close");
            }
            List<String> args = new ArrayList<>();
            for (String arg : splitDeclarators(s.substring(open + 1, close))) {
                args.add(arg.strip());
            }
            if (args.size() < 2 || args.size() > 3 || args.get(0).isEmpty() || args.get(1).isEmpty()) {
                throw new IllegalArgumentException("shadow sampling call " + call + " is not lowered: it has "
                        + args.size() + " argument(s), not the sampler, coordinate and optional bias it takes");
            }
            out.append(s, copied, m.start());
            out.append("vec4(vec3(").append(call.endsWith("Proj") ? "textureProj(" : "texture(");
            out.append(args.get(0)).append(", ");
            if ("shadow1D".equals(call)) {
                // GLSL 1.20's shadow1D takes a vec3 whose middle component is ignored (the coordinate is
                // .x, the reference value .z); the modern fetch on a sampler1DShadow takes a vec2 of
                // exactly those two. The parentheses keep the swizzles on the whole coordinate
                // expression, whatever it is.
                out.append("vec2((").append(args.get(1)).append(").x, (").append(args.get(1)).append(").z)");
            } else {
                out.append(args.get(1));
            }
            if (args.size() == 3) {
                out.append(", ").append(args.get(2));
            }
            out.append(")), 1.0)");
            copied = close + 1;
            if (!m.find(copied)) {
                break;
            }
        }
        out.append(s, copied, s.length());
        return out.toString();
    }

    /**
     * The overload set a stage with at least one guarded call is prefixed with. The guard never calls
     * {@code pow} on a non-positive base: a positive base takes the call unchanged, and zero, a negative
     * or a NaN base answers 0 - the value {@code pow(0, e)} has for the positive fractional exponents
     * this pass rewrites, and the value the shader's own clamps intend where a negative leaked past
     * one (GLSL leaves {@code pow} of a negative base undefined). The vector forms select per
     * component, so a NaN one component computed is discarded by the select, not propagated by
     * arithmetic.
     */
    private static final String POW_GUARD_HELPERS = """
            float celtium_pow_guard(float celtium_base, float celtium_exp) {
                return celtium_base > 0.0 ? pow(celtium_base, celtium_exp) : 0.0;
            }
            vec2 celtium_pow_guard(vec2 celtium_base, vec2 celtium_exp) {
                return mix(vec2(0.0), pow(celtium_base, celtium_exp), greaterThan(celtium_base, vec2(0.0)));
            }
            vec3 celtium_pow_guard(vec3 celtium_base, vec3 celtium_exp) {
                return mix(vec3(0.0), pow(celtium_base, celtium_exp), greaterThan(celtium_base, vec3(0.0)));
            }
            vec4 celtium_pow_guard(vec4 celtium_base, vec4 celtium_exp) {
                return mix(vec4(0.0), pow(celtium_base, celtium_exp), greaterThan(celtium_base, vec4(0.0)));
            }
            vec2 celtium_pow_guard(vec2 celtium_base, float celtium_exp) {
                return celtium_pow_guard(celtium_base, vec2(celtium_exp));
            }
            vec3 celtium_pow_guard(vec3 celtium_base, float celtium_exp) {
                return celtium_pow_guard(celtium_base, vec3(celtium_exp));
            }
            vec4 celtium_pow_guard(vec4 celtium_base, float celtium_exp) {
                return celtium_pow_guard(celtium_base, vec4(celtium_exp));
            }
            vec2 celtium_pow_guard(float celtium_base, vec2 celtium_exp) {
                return celtium_pow_guard(vec2(celtium_base), celtium_exp);
            }
            vec3 celtium_pow_guard(float celtium_base, vec3 celtium_exp) {
                return celtium_pow_guard(vec3(celtium_base), celtium_exp);
            }
            vec4 celtium_pow_guard(float celtium_base, vec4 celtium_exp) {
                return celtium_pow_guard(vec4(celtium_base), celtium_exp);
            }
            """;

    /**
     * {@return whether the fractional-pow guard is switched on for this translation}. Read at every
     * translation rather than latched into a static: a program is translated when a mod links it, long
     * after the launch properties are set, and a per-call read leaves no initialization-order trap of
     * the kind a static latch on a property has.
     */
    private static boolean fractionalPowGuardEnabled() {
        return !"false".equalsIgnoreCase(
                System.getProperty("celtium.vulkan.fractionalPowGuard", "true"));
    }

    /**
     * Rewrites {@code pow(base, exp)} to {@code celtium_pow_guard(base, exp)} wherever {@code exp} is,
     * as written, a constant with a positive fractional value (see
     * {@link #isPositiveFractionalConstant}), and prefixes the overload set when the stage gained a
     * rewrite. Behind {@code -Dceltium.vulkan.fractionalPowGuard} (default on since the item-684
     * verification leg; {@code =false} opts out): with the switch off the source is returned
     * untouched, and a stage with no rewritten call is untouched either way.
     * <p>
     * Why: measured on BSL's composite (wedge-16), a light-shafts value reaches
     * {@code pow(vl / 32.0, vec3(0.25))} as exactly 0 - its falloff clamp produces 0 - and the
     * reference result is 0, but the native execution on RADV/ACO wrote NaN over the whole anti-sun
     * cone, which the downstream chain turns into a black wedge. Whether the clamp is what the native
     * code drops or its {@code pow(0, 0.25)} itself returns NaN is not separable from the capture; the
     * guard covers both, because it never calls {@code pow} on a non-positive base at all. A
     * {@code max(base, 0.0)} clamp would cover only the first: the clamped 0 would still reach the
     * suspect {@code pow}.
     * <p>
     * The pass is conservative where the shadow lowering is strict: a hardening must not refuse a
     * program the translator otherwise accepts, so a call whose argument list does not close, does
     * not split, or whose exponent is not a constant this pass proves fractional is passed through
     * verbatim. Scanning and parsing run on the comment-blanked text ({@link #stripComments} keeps
     * every offset), so a {@code pow(} inside a comment - balanced or not - is neither rewritten nor
     * able to corrupt the parse of a real call; the argument texts are taken from that same blanked
     * text, in which a comment inside an argument list has already become spaces.
     */
    private static String guardFractionalPow(String s) {
        if (!fractionalPowGuardEnabled()) {
            return s;
        }
        String rewritten = rewritePowCalls(s);
        return rewritten.equals(s) ? s : POW_GUARD_HELPERS + rewritten;
    }

    /**
     * {@return {@code s} with every provably-fractional-constant {@code pow} call guarded},
     * recursing into argument lists so a nested call is rewritten inside its outer call whether or
     * not the outer one qualifies. A call that does not qualify - and whose arguments no rewrite
     * touched - is copied out verbatim, comments and spacing included.
     */
    private static String rewritePowCalls(String s) {
        String scan = stripComments(s);
        Matcher m = POW_CALL_PATTERN.matcher(scan);
        StringBuilder out = null;
        int copied = 0;
        while (m.find()) {
            int open = m.end() - 1;
            int close = matchingParen(scan, open);
            if (close < 0) {
                continue;
            }
            List<String> rawArgs = new ArrayList<>();
            for (String arg : splitDeclarators(scan.substring(open + 1, close))) {
                rawArgs.add(arg.strip());
            }
            List<String> args = new ArrayList<>(rawArgs.size());
            boolean argsChanged = false;
            for (String arg : rawArgs) {
                String rewrittenArg = rewritePowCalls(arg);
                argsChanged |= !rewrittenArg.equals(arg);
                args.add(rewrittenArg);
            }
            boolean guard = rawArgs.size() == 2 && isPositiveFractionalConstant(rawArgs.get(1));
            if (!guard && !argsChanged) {
                continue;
            }
            if (out == null) {
                out = new StringBuilder(s.length() + 64);
            }
            out.append(s, copied, m.start());
            out.append(guard ? "celtium_pow_guard(" : "pow(");
            out.append(String.join(", ", args));
            out.append(')');
            copied = close + 1;
            m.region(copied, scan.length());
        }
        if (out == null) {
            return s;
        }
        out.append(s, copied, s.length());
        return out.toString();
    }

    /**
     * {@return whether an exponent as written is a constant with a positive fractional value}: a bare
     * numeric literal, a {@code vecN} splat of one, or a {@code vecN} constructor whose components
     * are all such literals. Anything else - a name, an expression (even a constant one like
     * {@code 1.0 / 2.2}), an integer-valued, zero or negative literal - is not rewritten: the pass
     * rewrites only what it can prove, and integer exponents are left alone because a negative base
     * raised to them is the behaviour programs in the wild already get from this pipeline.
     */
    private static boolean isPositiveFractionalConstant(String exponent) {
        String t = exponent.strip();
        Matcher ctor = EXPONENT_CONSTRUCTOR.matcher(t);
        if (ctor.matches()) {
            for (String part : splitDeclarators(ctor.group(2))) {
                if (!isPositiveFractionalLiteral(part.strip())) {
                    return false;
                }
            }
            return true;
        }
        return isPositiveFractionalLiteral(t);
    }

    /** {@return whether {@code text} is one numeric literal whose value is positive and not an integer}. */
    private static boolean isPositiveFractionalLiteral(String text) {
        if (!NUMBER_LITERAL.matcher(text).matches()) {
            return false;
        }
        double value;
        try {
            value = Double.parseDouble(text.replaceAll("[fF]$", ""));
        } catch (NumberFormatException notDecimal) {
            // A hex literal: an integer by construction, never a fractional exponent this pass rewrites.
            return false;
        }
        return value > 0.0 && Double.isFinite(value) && value != Math.floor(value);
    }

    /** {@return the index of the close parenthesis matching the one at {@code open}, or -1 when it never closes}. */
    private static int matchingParen(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static String lowerTextureCalls(String s) {
        s = TEX1D_LOD_PATTERN.matcher(s).replaceAll("textureLod(");
        s = TEX1D_PROJ_PATTERN.matcher(s).replaceAll("textureProj(");
        s = TEX1D_PATTERN.matcher(s).replaceAll("texture(");
        s = TEX2D_PROJ_LOD_PATTERN.matcher(s).replaceAll("textureProjLod(");
        s = TEX2D_PROJ_PATTERN.matcher(s).replaceAll("textureProj(");
        s = TEX2D_LOD_PATTERN.matcher(s).replaceAll("textureLod(");
        s = TEX2D_GRAD_ARB_PATTERN.matcher(s).replaceAll("textureGrad(");
        s = TEX2D_RECT_PROJ_PATTERN.matcher(s).replaceAll("textureProj(");
        s = TEX2D_RECT_PATTERN.matcher(s).replaceAll("texture(");
        s = TEX2D_PATTERN.matcher(s).replaceAll("texture(");
        s = TEX3D_LOD_PATTERN.matcher(s).replaceAll("textureLod(");
        s = TEX3D_PROJ_PATTERN.matcher(s).replaceAll("textureProj(");
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
                // An array constructor: float[2](1.0, 2.0), or the unsized float[](...) whose length the
                // declaration itself fixes - the argument count is then the size check, and it is already
                // required to equal the declared length below. Only a scalar element type is evaluated - a
                // vector element's own constructor would nest, which the refusal below names.
                String ctorSize = ctor.group(2);
                if (!ctorType.equals(d.type)
                        || ctorSize == null
                        || (!ctorSize.isEmpty() && Integer.parseInt(ctorSize) != d.arrayLength)
                        || components != 1
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
