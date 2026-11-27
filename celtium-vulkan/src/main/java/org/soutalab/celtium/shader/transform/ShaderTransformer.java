/*
 * Derived from Iris / Angelica shader pack interpreter (LGPL-3.0).
 * Adapted natively for Celtium Vulkan 1.3: GLSM-free, Celeritas-free, DH-free,
 * directly targeting CeltiumTerrainTransformer (16 B PackedAxisQuad / 64 B GeneralQuad + mc_Entity).
 */
package org.soutalab.celtium.shader.transform;

import com.google.common.base.Stopwatch;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.soutalab.celtium.shader.ShaderLogger;
import org.soutalab.celtium.shader.features.PackCompilerCapabilities;
import org.soutalab.celtium.shader.model.ShaderType;
import org.soutalab.celtium.shader.transform.parameter.AttributeParameters;
import org.soutalab.celtium.shader.transform.parameter.Parameters;
import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.Transformer;
import org.taumc.glsl.grammar.GLSLLexer;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShaderTransformer {
    private static final Pattern VERSION_PATTERN = Pattern.compile("#version\\s+(\\d+)(?:\\s+(\\w+))?");
    private static final float MID_TEX_SCALE = 1.0f;

    private static final Set<String> loggedNegotiations = new HashSet<>();

    private record VersionRequirement(String keyword, int minVersion, BooleanSupplier supported) {}

    private static final VersionRequirement[] VERSION_REQUIREMENTS = {
            new VersionRequirement("std430", 430, () -> PackCompilerCapabilities.active().supportsSSBO()),
            new VersionRequirement("iimage", 420, () -> PackCompilerCapabilities.active().supportsImageLoadStore()),
            new VersionRequirement("uimage", 420, () -> PackCompilerCapabilities.active().supportsImageLoadStore()),
            new VersionRequirement("imageLoad", 420, () -> PackCompilerCapabilities.active().supportsImageLoadStore()),
            new VersionRequirement("imageStore", 420, () -> PackCompilerCapabilities.active().supportsImageLoadStore()),
            new VersionRequirement("uint", 130, () -> PackCompilerCapabilities.active().maxGlslVersion() >= 130),
            new VersionRequirement("uvec2", 130, () -> PackCompilerCapabilities.active().maxGlslVersion() >= 130),
            new VersionRequirement("uvec3", 130, () -> PackCompilerCapabilities.active().maxGlslVersion() >= 130),
            new VersionRequirement("uvec4", 130, () -> PackCompilerCapabilities.active().maxGlslVersion() >= 130),
            new VersionRequirement("flat", 130, () -> PackCompilerCapabilities.active().maxGlslVersion() >= 130),
    };

    record NegotiationResult(int targetVersion, String profile, String error) {
        static NegotiationResult error(String message) {
            return new NegotiationResult(-1, "", message);
        }

        static NegotiationResult noop(int version, String profile) {
            return new NegotiationResult(version, profile, null);
        }

        boolean isError() {
            return error != null;
        }
    }

    private static int getStageMinimumVersion(PatchShaderType stage) {
        return switch (stage) {
            case COMPUTE -> 430;
            case TESS_CONTROL, TESS_EVAL -> 400;
            case GEOMETRY -> 330;
            default -> 330;
        };
    }

    static NegotiationResult negotiateVersion(int effectiveVersion, PatchShaderType stage) {
        int maxGlsl = PackCompilerCapabilities.active().maxGlslVersion();
        if (effectiveVersion <= maxGlsl) {
            return NegotiationResult.noop(effectiveVersion, effectiveVersion >= 150 ? "core" : "");
        }
        int stageMin = getStageMinimumVersion(stage);
        if (maxGlsl < stageMin) {
            return NegotiationResult.error(
                    "Hardware GLSL " + maxGlsl + " below stage minimum " + stageMin + " for " + stage.name());
        }
        return NegotiationResult.error(
                "Shader requires GLSL " + effectiveVersion + " but hardware max is " + maxGlsl);
    }

    private static volatile Pattern hoistPattern;
    private static volatile Object2IntMap<String> keywordToVersion;
    private static volatile int maxSupportedHoistVersion;

    static {
        init();
    }

    public static void init() {
        StringBuilder patternBuilder = new StringBuilder();
        Object2IntOpenHashMap<String> versionMap = new Object2IntOpenHashMap<>();
        int maxVersion = 0;

        for (VersionRequirement req : VERSION_REQUIREMENTS) {
            if (req.supported.getAsBoolean()) {
                if (!patternBuilder.isEmpty()) {
                    patternBuilder.append('|');
                }
                patternBuilder.append("\\b").append(Pattern.quote(req.keyword))
                        .append(req.keyword.endsWith("image") ? "\\w*\\b" : "\\b");
                versionMap.put(req.keyword, req.minVersion);
                maxVersion = Math.max(maxVersion, req.minVersion);
            }
        }

        if (!patternBuilder.isEmpty()) {
            hoistPattern = Pattern.compile(patternBuilder.toString());
            keywordToVersion = versionMap;
        }
        maxSupportedHoistVersion = maxVersion;
    }

    static void clearSessionState() {
        synchronized (loggedNegotiations) {
            loggedNegotiations.clear();
        }
    }

    private static int getRequiredVersion(String shaderSource, int declaredVersion) {
        Pattern pat = hoistPattern;
        Object2IntMap<String> map = keywordToVersion;
        if (pat == null || map == null || declaredVersion >= maxSupportedHoistVersion) {
            return declaredVersion;
        }

        Matcher m = pat.matcher(shaderSource);
        int required = declaredVersion;
        while (m.find()) {
            String token = m.group();
            String key = token.startsWith("iimage") ? "iimage" : (token.startsWith("uimage") ? "uimage" : token);
            int ver = map.getInt(key);
            if (ver > required) {
                required = ver;
                if (required >= maxSupportedHoistVersion) {
                    break;
                }
            }
        }
        return required;
    }

    public static <P extends Parameters> Map<PatchShaderType, String> transform(
            String vertex, String geometry, String tessControl, String tessEval, String fragment, P parameters) {
        if (vertex == null && geometry == null && tessControl == null && tessEval == null && fragment == null) {
            return null;
        }

        EnumMap<PatchShaderType, String> inputs = new EnumMap<>(PatchShaderType.class);
        inputs.put(PatchShaderType.VERTEX, vertex);
        inputs.put(PatchShaderType.GEOMETRY, geometry);
        inputs.put(PatchShaderType.TESS_CONTROL, tessControl);
        inputs.put(PatchShaderType.TESS_EVAL, tessEval);
        inputs.put(PatchShaderType.FRAGMENT, fragment);
        try {
            return transformInternal(inputs, parameters.patch, parameters);
        } finally {
            parameters.type = null;
        }
    }

    public static <P extends Parameters> Map<PatchShaderType, String> transformCompute(String compute, P parameters) {
        if (compute == null) {
            return null;
        }
        try {
            return transformComputeInternal(compute, parameters.patch, parameters);
        } finally {
            parameters.type = null;
        }
    }

    private static <P extends Parameters> Map<PatchShaderType, String> transformComputeInternal(
            String compute, Patch patchType, P parameters) {
        EnumMap<PatchShaderType, String> result = new EnumMap<>(PatchShaderType.class);
        Stopwatch watch = Stopwatch.createStarted();
        parameters.type = ShaderType.COMPUTE;

        Matcher matcher = VERSION_PATTERN.matcher(compute);
        if (!matcher.find()) {
            throw new IllegalArgumentException("No #version directive found in compute shader source code!");
        }

        int versionInt = Integer.parseInt(matcher.group(1));
        int requiredVersion = getRequiredVersion(compute, versionInt);
        if (requiredVersion > versionInt) {
            versionInt = requiredVersion;
        }
        if (versionInt < 430) {
            versionInt = 430;
        }

        NegotiationResult negotiation = negotiateVersion(versionInt, PatchShaderType.COMPUTE);
        if (negotiation.isError()) {
            throw new RuntimeException("Compute shader version negotiation failed: " + negotiation.error());
        }
        versionInt = negotiation.targetVersion();
        String profileString = "#version " + versionInt + " core\n";

        String input = GlslTransformHelpers.replaceTexture(compute);
        input = GlslTransformHelpers.renameReservedWords(input, versionInt);

        var parsedShader = ShaderParser.parseShader(input);
        var transformer = new Transformer(parsedShader.full());

        doTransform(transformer, patchType, parameters, versionInt);

        String extensions = VERSION_PATTERN
                .matcher(GlslTransformHelpers.getFormattedShader(parsedShader.pre(), ""))
                .replaceFirst("").trim();
        String finalHeader = profileString + (extensions.isEmpty() ? "" : "\n" + extensions);
        StringBuilder formattedShaderBuilder = new StringBuilder();

        transformer.mutateTree(tree -> {
            GlslTransformHelpers.restoreReservedWordsInTree(tree);
            formattedShaderBuilder.append(GlslTransformHelpers.getFormattedShader(tree, finalHeader));
        });

        String formattedShader = hoistTransformedVersion(formattedShaderBuilder.toString(), parameters.type);
        result.put(PatchShaderType.COMPUTE, formattedShader);

        watch.stop();
        ShaderLogger.LOGGER.debug("Transformed compute shader for {} in {}", patchType.name(), watch);
        return result;
    }

    private static <P extends Parameters> Map<PatchShaderType, String> transformInternal(
            EnumMap<PatchShaderType, String> inputs, Patch patchType, P parameters) {
        EnumMap<PatchShaderType, String> result = new EnumMap<>(PatchShaderType.class);
        EnumMap<PatchShaderType, Transformer> types = new EnumMap<>(PatchShaderType.class);
        EnumMap<PatchShaderType, String> prepatched = new EnumMap<>(PatchShaderType.class);

        Stopwatch watch = Stopwatch.createStarted();

        for (PatchShaderType type : PatchShaderType.VALUES) {
            parameters.type = type.glShaderType;
            if (inputs.get(type) == null) {
                continue;
            }

            String input = inputs.get(type);
            Matcher matcher = VERSION_PATTERN.matcher(input);
            if (!matcher.find()) {
                throw new IllegalArgumentException("No #version directive found in source code!");
            }

            String versionString = matcher.group(1);
            if (versionString == null) {
                continue;
            }

            int versionInt = Integer.parseInt(versionString);
            String scanSource = (patchType == Patch.CELTIUM_TERRAIN && type == PatchShaderType.VERTEX)
                    ? input + CeltiumTerrainTransformer.terrainVertexHeader()
                    : input;
            int requiredVersion = getRequiredVersion(scanSource, versionInt);
            if (requiredVersion > versionInt) {
                versionInt = requiredVersion;
            }

            int stageMin = getStageMinimumVersion(type);
            if (versionInt < stageMin) {
                versionInt = stageMin;
            }

            NegotiationResult negotiation = negotiateVersion(versionInt, type);
            if (negotiation.isError()) {
                throw new RuntimeException("Shader version negotiation failed for " + type.name() + ": " + negotiation.error());
            }

            String profileString = "#version " + versionInt + " core\n";

            input = GlslTransformHelpers.replaceTexture(input);
            input = GlslTransformHelpers.renameReservedWords(input, versionInt);
            input = GlslTransformHelpers.fixupQualifiers(input, parameters.type == ShaderType.FRAGMENT);
            if (patchType == Patch.COMPOSITE && parameters.type == ShaderType.FRAGMENT) {
                input = CompatibilityTransformer.patchCaveSkyholeClouds(input);
                input = CompatibilityTransformer.patchVolumetricCloudReferenceDistance(input);
            }
            if (parameters.type == ShaderType.FRAGMENT) {
                input = CompatibilityTransformer.patchCloudMovementTime(input);
            }

            var parsedShader = ShaderParser.parseShader(input);
            var transformer = new Transformer(parsedShader.full());

            doTransform(transformer, patchType, parameters, versionInt);

            String extensions = VERSION_PATTERN
                    .matcher(GlslTransformHelpers.getFormattedShader(parsedShader.pre(), ""))
                    .replaceFirst("").trim();

            types.put(type, transformer);
            prepatched.put(type, profileString + (extensions.isEmpty() ? "" : "\n" + extensions));
        }

        CompatibilityTransformer.transformGrouped(types, parameters);

        for (var entry : types.entrySet()) {
            PatchShaderType shaderType = entry.getKey();
            Transformer transformer = entry.getValue();
            String header = prepatched.get(shaderType);

            if (patchType == Patch.CELTIUM_TERRAIN && shaderType == PatchShaderType.VERTEX) {
                header += "\n" + CeltiumTerrainTransformer.terrainVertexHeader();
            }

            String finalHeader = header;
            StringBuilder formattedShaderBuilder = new StringBuilder();

            transformer.mutateTree(tree -> {
                GlslTransformHelpers.restoreReservedWordsInTree(tree);
                formattedShaderBuilder.append(GlslTransformHelpers.getFormattedShader(tree, finalHeader));
            });

            String formattedShader = hoistTransformedVersion(formattedShaderBuilder.toString(), shaderType.glShaderType);
            result.put(shaderType, formattedShader);
        }

        watch.stop();
        ShaderLogger.LOGGER.debug("Transformed shader for {} in {}", patchType.name(), watch);
        return result;
    }

    private static String hoistTransformedVersion(String source, ShaderType stage) {
        Matcher version = VERSION_PATTERN.matcher(source);
        if (!version.find()) {
            throw new IllegalArgumentException("Transformed shader has no version directive");
        }
        int declared = Integer.parseInt(version.group(1));
        int required = getRequiredVersion(source, declared);
        if (required <= declared) {
            return source;
        }
        NegotiationResult negotiation = negotiateVersion(required, PatchShaderType.fromGlShaderType(stage)[0]);
        if (negotiation.isError()) {
            throw new IllegalStateException(negotiation.error());
        }
        return version.replaceFirst("#version " + required + " core");
    }

    private static void doTransform(Transformer transformer, Patch patchType, Parameters parameters, int versionInt) {
        switch (patchType) {
            case CELTIUM_TERRAIN -> {
                CeltiumTerrainTransformer.transform(transformer, parameters, versionInt);
                patchMultiTexCoord3(transformer, parameters);
                replaceMidTexCoord(transformer, MID_TEX_SCALE);
                replaceMCEntity(transformer, parameters);
                applyIntelHd4000Workaround(transformer);
            }
            case COMPOSITE -> CompositeDepthTransformer.transform(transformer, parameters, versionInt);
            case ATTRIBUTES -> AttributeTransformer.transform(transformer, (AttributeParameters) parameters, versionInt);
            case COMPUTE -> ComputeTransformer.transform(transformer, parameters, versionInt);
        }
        TextureTransformer.transform(transformer, parameters);
        CompatibilityTransformer.transformEach(transformer, parameters);
    }

    public static void applyIntelHd4000Workaround(Transformer transformer) {
        transformer.renameFunctionCall("ftransform", "iris_ftransform");
    }

    public static void patchMultiTexCoord3(Transformer transformer, Parameters parameters) {
        if (parameters.type == ShaderType.VERTEX
                && transformer.hasVariable("gl_MultiTexCoord3")
                && !transformer.hasVariable("mc_midTexCoord")) {
            transformer.rename("gl_MultiTexCoord3", "mc_midTexCoord");
            transformer.injectVariable("attribute vec4 mc_midTexCoord;");
        }
    }

    public static void replaceMidTexCoord(Transformer transformer, float textureScale) {
        int type = transformer.findType("mc_midTexCoord");
        if (type != 0) {
            transformer.removeVariable("mc_midTexCoord");
        }
        transformer.replaceExpression("mc_midTexCoord", "iris_MidTex");
        switch (type) {
            case 0, GLSLLexer.BOOL -> {
                return;
            }
            case GLSLLexer.FLOAT -> transformer.injectFunction(
                    "float iris_MidTex = (mc_midTexCoord.x * " + textureScale + ").x;");
            case GLSLLexer.VEC2 -> transformer.injectFunction(
                    "vec2 iris_MidTex = (mc_midTexCoord.xy * " + textureScale + ").xy;");
            case GLSLLexer.VEC3 -> transformer.injectFunction(
                    "vec3 iris_MidTex = vec3(mc_midTexCoord.xy * " + textureScale + ", 0.0);");
            case GLSLLexer.VEC4 -> transformer.injectFunction(
                    "vec4 iris_MidTex = vec4(mc_midTexCoord.xy * " + textureScale + ", 0.0, 1.0);");
            default -> {
            }
        }
        transformer.injectVariable("layout(location = 5) in vec2 mc_midTexCoord;");
    }

    /**
     * Replaces shader-declared mc_Entity with unpacking from Celtium's +2 B auxiliary column
     * (passed as a uint vertex attribute: ((blockId + 1) << 1) | (renderType & 1)).
     */
    public static void replaceMCEntity(Transformer transformer, Parameters parameters) {
        if (parameters.type != ShaderType.VERTEX) {
            return;
        }
        int type = transformer.findType("mc_Entity");
        if (type != 0) {
            transformer.removeVariable("mc_Entity");
        }
        transformer.replaceExpression("mc_Entity", "iris_Entity");
        switch (type) {
            case 0, GLSLLexer.BOOL -> {
                return;
            }
            case GLSLLexer.FLOAT -> transformer.injectFunction(
                    "float iris_Entity = float(int(mc_Entity >> 1u) - 1);");
            case GLSLLexer.VEC2 -> transformer.injectFunction(
                    "vec2 iris_Entity = vec2(int(mc_Entity >> 1u) - 1, mc_Entity & 1u);");
            case GLSLLexer.VEC3 -> transformer.injectFunction(
                    "vec3 iris_Entity = vec3(int(mc_Entity >> 1u) - 1, mc_Entity & 1u, 0.0);");
            case GLSLLexer.VEC4 -> transformer.injectFunction(
                    "vec4 iris_Entity = vec4(int(mc_Entity >> 1u) - 1, mc_Entity & 1u, 0.0, 1.0);");
            case GLSLLexer.UINT -> transformer.injectFunction(
                    "uint iris_Entity = uint(int(mc_Entity >> 1u) - 1);");
            case GLSLLexer.INT -> transformer.injectFunction(
                    "int iris_Entity = int(mc_Entity >> 1u) - 1;");
            case GLSLLexer.IVEC2 -> transformer.injectFunction(
                    "ivec2 iris_Entity = ivec2(int(mc_Entity >> 1u) - 1, mc_Entity & 1u);");
            case GLSLLexer.IVEC3 -> transformer.injectFunction(
                    "ivec3 iris_Entity = ivec3(int(mc_Entity >> 1u) - 1, mc_Entity & 1u, 0);");
            case GLSLLexer.IVEC4 -> transformer.injectFunction(
                    "ivec4 iris_Entity = ivec4(int(mc_Entity >> 1u) - 1, mc_Entity & 1u, 0, 1);");
            default -> throw new IllegalStateException(
                    "Invalid format for mc_Entity (type token " + type + ").");
        }
        transformer.injectVariable("layout(location = 6) in uint mc_Entity;");
    }

    public static void addIfNotExists(Transformer transformer, String name, String code) {
        if (!transformer.hasVariable(name)) {
            transformer.injectVariable(code);
        }
    }
}
