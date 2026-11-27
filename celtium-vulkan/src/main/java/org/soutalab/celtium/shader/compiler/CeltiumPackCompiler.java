/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 * Central Stage S5-a deliverable: compiles a parsed Iris/OptiFine shader pack directly into
 * Celtium's native Vulkan 1.3 contracts (PackExecutionPlan, TerrainPackProgramSet,
 * NonTerrainProgramCatalog / NonTerrainPackTarget) with zero GLSM or legacy supplier-interface
 * residue (V040-FINAL-PLAN sections 2.2, 2.3 & 4.2 S5-a).
 */
package org.soutalab.celtium.shader.compiler;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK13;
import org.soutalab.celtium.core.shader.AttachmentBlendSpec;
import org.soutalab.celtium.core.shader.AttachmentFormatKey;
import org.soutalab.celtium.core.shader.BlendStateSpec;
import org.soutalab.celtium.core.shader.PackExecutionPlan;
import org.soutalab.celtium.core.shader.PackExecutionPlan.BarrierSpec;
import org.soutalab.celtium.core.shader.PackExecutionPlan.OrderedPass;
import org.soutalab.celtium.core.shader.PackExecutionPlan.RenderTargetSpec;
import org.soutalab.celtium.core.shader.TerrainPassKind;
import org.soutalab.celtium.core.shader.TerrainStageProgram;
import org.soutalab.celtium.core.shader.TerrainVertexRequirements;
import org.soutalab.celtium.shader.ShaderLogger;
import org.soutalab.celtium.shader.block_rendering.BlockRenderingSettings;
import org.soutalab.celtium.shader.features.PackCompilerCapabilities;
import org.soutalab.celtium.shader.model.AlphaTestOverride;
import org.soutalab.celtium.shader.model.BlendModeOverride;
import org.soutalab.celtium.shader.model.BufferBlendInformation;
import org.soutalab.celtium.shader.model.BufferBlendOverride;
import org.soutalab.celtium.shader.model.InputAvailability;
import org.soutalab.celtium.shader.model.InternalTextureFormat;
import org.soutalab.celtium.shader.shaderpack.ComputeSource;
import org.soutalab.celtium.shader.shaderpack.PackDirectives;
import org.soutalab.celtium.shader.shaderpack.PackRenderTargetDirectives;
import org.soutalab.celtium.shader.shaderpack.PackShadowDirectives;
import org.soutalab.celtium.shader.shaderpack.ProgramDirectives;
import org.soutalab.celtium.shader.shaderpack.ProgramFallbackResolver;
import org.soutalab.celtium.shader.shaderpack.ProgramSet;
import org.soutalab.celtium.shader.shaderpack.ProgramSource;
import org.soutalab.celtium.shader.shaderpack.ShaderPack;
import org.soutalab.celtium.shader.shaderpack.StringPair;
import org.soutalab.celtium.shader.shaderpack.loading.ProgramId;
import org.soutalab.celtium.shader.shaderpack.texture.TextureStage;
import org.soutalab.celtium.shader.transform.CeltiumTerrainTransformer;
import org.soutalab.celtium.shader.transform.PatchShaderType;
import org.soutalab.celtium.shader.transform.TransformPatcher;
import org.soutalab.celtium.shader.uniforms.ShaderFrameState;
import org.soutalab.celtium.shader.uniforms.Std140UniformPacker;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CeltiumPackCompiler {

    private static final int DEPTH_VK_FORMAT = VK10.VK_FORMAT_D32_SFLOAT;

    // Vulkan 1.3 synchronization2 barrier masks for stage edges
    private static final BarrierSpec BARRIER_COMPUTE_TO_GRAPHICS = new BarrierSpec(
            VK13.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
            VK13.VK_ACCESS_2_SHADER_WRITE_BIT,
            VK13.VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT | VK13.VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
            VK13.VK_ACCESS_2_SHADER_READ_BIT);

    private static final BarrierSpec BARRIER_SHADOW_TO_GBUFFER = new BarrierSpec(
            VK13.VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT
                    | VK13.VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT
                    | VK13.VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
            VK13.VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
                    | VK13.VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
            VK13.VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
            VK13.VK_ACCESS_2_SHADER_SAMPLED_READ_BIT);

    private static final BarrierSpec BARRIER_COLOR_TO_FRAGMENT_SAMPLE = new BarrierSpec(
            VK13.VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT
                    | VK13.VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
            VK13.VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT
                    | VK13.VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
            VK13.VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
            VK13.VK_ACCESS_2_SHADER_SAMPLED_READ_BIT);

    private static final ProgramId[] NON_TERRAIN_PROGRAM_IDS = {
            ProgramId.Basic,
            ProgramId.Line,
            ProgramId.Textured,
            ProgramId.TexturedLit,
            ProgramId.SkyBasic,
            ProgramId.SkyTextured,
            ProgramId.Clouds,
            ProgramId.Entities,
            ProgramId.EntitiesTrans,
            ProgramId.EntitiesGlowing,
            ProgramId.ArmorGlint,
            ProgramId.SpiderEyes,
            ProgramId.Block,
            ProgramId.BlockTrans,
            ProgramId.BeaconBeam,
            ProgramId.Hand,
            ProgramId.Water,
            ProgramId.Weather,
            ProgramId.HandWater
    };

    private final PackSpirvCompiler spirvCompiler;

    public CeltiumPackCompiler() {
        this(PackSpirvCompiler.SHADERC);
    }

    public CeltiumPackCompiler(PackSpirvCompiler spirvCompiler) {
        this.spirvCompiler = Objects.requireNonNull(spirvCompiler, "spirvCompiler");
    }

    /**
     * Loads a shader pack from {@code shadersRoot} (the pack's {@code shaders/} directory) and
     * compiles it directly into Celtium's native Vulkan 1.3 contracts.
     */
    public CompiledShaderPack compile(
            Path shadersRoot,
            Map<String, String> optionOverrides,
            PackCompilerCapabilities capabilities) throws IOException {
        return compile(shadersRoot, optionOverrides, defaultEnvironmentDefines(capabilities), "overworld", capabilities);
    }

    public CompiledShaderPack compile(
            Path shadersRoot,
            Map<String, String> optionOverrides,
            Iterable<StringPair> environmentDefines,
            String dimensionName,
            PackCompilerCapabilities capabilities) throws IOException {
        Objects.requireNonNull(shadersRoot, "shadersRoot");
        PackCompilerCapabilities caps = capabilities == null
                ? PackCompilerCapabilities.DEFAULT_VULKAN_13
                : capabilities;
        PackCompilerCapabilities.setActive(caps);

        ShaderPack pack = new ShaderPack(
                shadersRoot,
                optionOverrides == null ? Collections.emptyMap() : optionOverrides,
                environmentDefines == null ? defaultEnvironmentDefines(caps) : environmentDefines);
        return compile(pack, dimensionName, caps);
    }

    /**
     * Compiles an already-parsed {@link ShaderPack} for {@code dimensionName} into Celtium's
     * native Vulkan 1.3 contracts.
     */
    public CompiledShaderPack compile(
            ShaderPack pack,
            String dimensionName,
            PackCompilerCapabilities capabilities) {
        Objects.requireNonNull(pack, "pack");
        PackCompilerCapabilities caps = capabilities == null
                ? PackCompilerCapabilities.DEFAULT_VULKAN_13
                : capabilities;
        PackCompilerCapabilities.setActive(caps);

        ProgramSet programSet = pack.getProgramSet(dimensionName == null ? "overworld" : dimensionName);
        PackDirectives directives = programSet.getPackDirectives();
        ProgramFallbackResolver resolver = new ProgramFallbackResolver(programSet);

        ShaderFrameState frameState = new ShaderFrameState();
        Std140UniformPacker uniformPacker = Std140UniformPacker.buildStandard(pack.customUniforms, frameState);

        // Stained glass writes depth exactly when the translucent terrain program is the
        // water program with blending forced off (the original pipeline derivation).
        boolean writeStainedGlassDepth = resolver.resolve(ProgramId.Water)
                .filter(source -> ProgramId.Water.getSourceName().equals(source.getName()))
                .flatMap(source -> source.getDirectives().getBlendModeOverride())
                .orElse(null) == BlendModeOverride.OFF;

        BlockRenderingSettings blockSettings = BlockRenderingSettings.fromIdMap(
                pack.getIdMap(),
                directives.getAmbientOcclusionLevel(),
                !directives.isOldLighting(),
                directives.shouldUseSeparateAo(),
                writeStainedGlassDepth);

        // 1. Render targets
        boolean hasShadow = programSet.getShadow().isPresent()
                || programSet.getShadowWater().isPresent();
        List<RenderTargetSpec> renderTargets = buildRenderTargets(directives, hasShadow);

        // Execution-side tables the core contracts do not carry: descriptor binding names
        // (needed to resolve a binding to a render target), compute modules and the compute
        // indirect pointers from shaders.properties.
        Map<String, List<NamedDescriptorBinding>> namedBindings = new LinkedHashMap<>();
        Map<String, int[]> computeModules = new LinkedHashMap<>();
        Map<String, org.soutalab.celtium.shader.shaderpack.IndirectPointer> computeIndirectPointers =
                new LinkedHashMap<>();

        // 2. Terrain 6-stage program set (Interface 1)
        Map<TerrainPassKind, TerrainStageProgram> terrainPrograms = new EnumMap<>(TerrainPassKind.class);
        boolean reqMcEntity = false;
        boolean reqNormal = false;
        boolean reqTangent = false;
        boolean reqMidTex = false;

        for (TerrainPassKind kind : TerrainPassKind.values()) {
            Optional<ProgramSource> resolved = resolveTerrainSource(resolver, programSet, kind);
            if (resolved.isEmpty() || !resolved.get().isValid()) {
                continue;
            }
            ProgramSource src = resolved.get();
            String vsh = src.getVertexSource().orElse(null);
            String fsh = src.getFragmentSource().orElse(null);
            String gsh = caps.supportsGeometryShader() ? src.getGeometrySource().orElse(null) : null;
            if (vsh == null || fsh == null) {
                continue;
            }

            TerrainVertexRequirements stageReqs = CeltiumTerrainTransformer.detectVertexRequirements(vsh);
            reqMcEntity |= stageReqs.mcEntity();
            reqNormal |= stageReqs.normal();
            reqTangent |= stageReqs.tangent();
            reqMidTex |= stageReqs.midTexCoord();

            Map<PatchShaderType, String> transformed = TransformPatcher.patchCeltiumTerrain(
                    vsh, gsh, fsh, directives.getTextureMap());
            if (transformed == null) {
                continue;
            }
            String patchedVsh = transformed.get(PatchShaderType.VERTEX);
            String patchedFsh = transformed.get(PatchShaderType.FRAGMENT);
            String patchedGsh = transformed.get(PatchShaderType.GEOMETRY);
            if (patchedVsh == null || patchedFsh == null) {
                continue;
            }

            PackSpirvCompiler.CompiledProgram compiled = spirvCompiler.compileGraphics(
                    src.getName(), patchedVsh, patchedFsh, patchedGsh);
            namedBindings.put("terrain:" + kind.name(), compiled.namedBindings());

            boolean isShadowKind = isShadowPass(kind);
            AttachmentFormatKey formats = buildAttachmentFormats(directives, src.getDirectives(), isShadowKind);
            BlendStateSpec blendSpec = buildBlendSpec(src.getDirectives(), formats.colorAttachmentCount(), kind);
            float alphaThreshold = resolveAlphaThreshold(src.getDirectives(), kind);
            boolean depthWrite = resolveDepthWrite(writeStainedGlassDepth, kind);
            boolean faceCull = !isShadowKind;

            terrainPrograms.put(kind, new TerrainStageProgram(
                    kind,
                    compiled.spirv(),
                    formats,
                    blendSpec,
                    alphaThreshold,
                    depthWrite,
                    faceCull,
                    compiled.bindings()));
        }

        TerrainVertexRequirements combinedReqs = new TerrainVertexRequirements(
                reqMcEntity, reqNormal, reqTangent, reqMidTex);
        CompiledTerrainProgramSet terrainProgramSet = new CompiledTerrainProgramSet(
                terrainPrograms, combinedReqs, uniformPacker, frameState);

        // 3. Non-terrain program catalog (Interface 2 -> Lane 2)
        Map<String, NonTerrainProgramCatalog.ProgramSpec> nonTerrainSpecs = new LinkedHashMap<>();
        for (ProgramId id : NON_TERRAIN_PROGRAM_IDS) {
            Optional<ProgramSource> resolved = resolver.resolve(id);
            if (resolved.isEmpty() || !resolved.get().isValid()) {
                continue;
            }
            ProgramSource src = resolved.get();
            String vsh = src.getVertexSource().orElse(null);
            String fsh = src.getFragmentSource().orElse(null);
            String gsh = caps.supportsGeometryShader() ? src.getGeometrySource().orElse(null) : null;
            if (vsh == null || fsh == null) {
                continue;
            }
            Map<PatchShaderType, String> transformed = TransformPatcher.patchAttributes(
                    vsh, gsh, fsh, InputAvailability.ALL);
            if (transformed == null) {
                continue;
            }
            String patchedVsh = transformed.get(PatchShaderType.VERTEX);
            String patchedFsh = transformed.get(PatchShaderType.FRAGMENT);
            String patchedGsh = transformed.get(PatchShaderType.GEOMETRY);
            if (patchedVsh == null || patchedFsh == null) {
                continue;
            }
            PackSpirvCompiler.CompiledProgram compiled = spirvCompiler.compileGraphics(
                    id.getSourceName(), patchedVsh, patchedFsh, patchedGsh);
            namedBindings.put(id.getSourceName(), compiled.namedBindings());

            boolean isShadow = id.getSourceName().startsWith("shadow");
            AttachmentFormatKey formats = buildAttachmentFormats(directives, src.getDirectives(), isShadow);
            BlendStateSpec blendSpec = buildBlendSpec(src.getDirectives(), formats.colorAttachmentCount(), null);
            float alphaThreshold = src.getDirectives().getAlphaTestOverride()
                    .map(AlphaTestOverride::getReference)
                    .orElse(0.0f);
            nonTerrainSpecs.put(id.getSourceName(), new NonTerrainProgramCatalog.ProgramSpec(
                    id.getSourceName(), compiled.spirv(), formats, blendSpec, alphaThreshold, compiled.bindings()));
        }
        NonTerrainProgramCatalog nonTerrainCatalog = new NonTerrainProgramCatalog(nonTerrainSpecs);

        // 4. Ordered pass graph (PackExecutionPlan)
        List<OrderedPass> passes = buildOrderedPasses(programSet, directives, terrainProgramSet.hasShadowPass(), caps,
                namedBindings, computeModules, computeIndirectPointers);
        PackExecutionPlan executionPlan = new PackExecutionPlan(renderTargets, uniformPacker.layout(), passes);

        ShaderLogger.LOGGER.info(
                "Compiled shader pack: targets={}, uniforms={} ({} B), terrainPasses={}, nonTerrain={}, orderedPasses={}",
                renderTargets.size(),
                uniformPacker.layout().fields().size(),
                uniformPacker.layout().blockSizeBytes(),
                terrainPrograms.size(),
                nonTerrainSpecs.size(),
                passes.size());

        return new CompiledShaderPack(
                executionPlan,
                terrainProgramSet,
                nonTerrainCatalog,
                blockSettings,
                frameState,
                pack,
                namedBindings,
                computeModules,
                computeIndirectPointers);
    }

    public static List<StringPair> defaultEnvironmentDefines(PackCompilerCapabilities caps) {
        List<StringPair> defines = new ArrayList<>();
        defines.add(new StringPair("MC_VERSION", "11202"));
        defines.add(new StringPair("MC_GL_VERSION", String.valueOf(caps.maxGlslVersion())));
        defines.add(new StringPair("MC_GLSL_VERSION", String.valueOf(caps.maxGlslVersion())));
        defines.add(new StringPair("MC_OS_LINUX", ""));
        defines.add(new StringPair("MC_RENDER_QUALITY", "1.0"));
        defines.add(new StringPair("MC_SHADOW_QUALITY", "1.0"));
        defines.add(new StringPair("MC_HAND_DEPTH", "0.125"));
        defines.add(new StringPair("IS_IRIS", ""));
        defines.add(new StringPair("CELTIUM_VULKAN", "130"));
        // Iris's render-stage macros: the values are the WorldRenderingPhase
        // ordinals, which is what the host feeds into the pack's renderStage
        // uniform per draw (Complementary's sky programs compare against them).
        String[] renderStages = {
                "NONE", "SKY", "SUNSET", "CUSTOM_SKY", "SUN", "MOON", "STARS", "VOID",
                "TERRAIN_SOLID", "TERRAIN_CUTOUT_MIPPED", "TERRAIN_CUTOUT", "ENTITIES",
                "BLOCK_ENTITIES", "DESTROY", "OUTLINE", "DEBUG", "HAND_SOLID",
                "TERRAIN_TRANSLUCENT", "TRIPWIRE", "PARTICLES", "CLOUDS", "RAIN_SNOW",
                "WORLD_BORDER", "HAND_TRANSLUCENT"};
        for (int i = 0; i < renderStages.length; i++) {
            defines.add(new StringPair("MC_RENDER_STAGE_" + renderStages[i], String.valueOf(i)));
        }
        return defines;
    }

    private List<RenderTargetSpec> buildRenderTargets(PackDirectives directives, boolean hasShadow) {
        List<RenderTargetSpec> specs = new ArrayList<>();

        if (hasShadow) {
            specs.add(new RenderTargetSpec(
                    "shadowtex0", RenderTargetSpec.Kind.SHADOW_MAP, DEPTH_VK_FORMAT, 1.0f, false, new float[]{1, 1, 1, 1}));
            specs.add(new RenderTargetSpec(
                    "shadowtex1", RenderTargetSpec.Kind.SHADOW_MAP, DEPTH_VK_FORMAT, 1.0f, false, new float[]{1, 1, 1, 1}));
            PackShadowDirectives shadowDir = directives.getShadowDirectives();
            var buffers = shadowDir.getColorSamplingSettings();
            for (int i = 0; i < buffers.size(); i++) {
                var setting = buffers.get(i);
                int vkFormat = setting.getFormat().getColorRenderableFallback().getVkFormat();
                specs.add(new RenderTargetSpec(
                        "shadowcolor" + i,
                        RenderTargetSpec.Kind.SHADOW_COLOR,
                        vkFormat,
                        1.0f,
                        false,
                        new float[]{1, 1, 1, 1}));
            }
        }

        // Depth targets
        specs.add(new RenderTargetSpec(
                "depthtex0", RenderTargetSpec.Kind.DEPTH_TEXTURE, DEPTH_VK_FORMAT, 1.0f, false, new float[]{1, 1, 1, 1}));
        specs.add(new RenderTargetSpec(
                "depthtex1", RenderTargetSpec.Kind.DEPTH_TEXTURE, DEPTH_VK_FORMAT, 1.0f, false, new float[]{1, 1, 1, 1}));
        specs.add(new RenderTargetSpec(
                "depthtex2", RenderTargetSpec.Kind.DEPTH_TEXTURE, DEPTH_VK_FORMAT, 1.0f, false, new float[]{1, 1, 1, 1}));

        // Color targets (colortex0..N)
        PackRenderTargetDirectives rtDir = directives.getRenderTargetDirectives();
        Map<Integer, PackRenderTargetDirectives.RenderTargetSettings> rtSettings = rtDir.getRenderTargetSettings();
        int maxColorIndex = 7;
        for (int idx : rtSettings.keySet()) {
            if (idx > maxColorIndex) {
                maxColorIndex = idx;
            }
        }
        for (int i = 0; i <= maxColorIndex; i++) {
            PackRenderTargetDirectives.RenderTargetSettings setting = rtSettings.get(i);
            InternalTextureFormat fmt = setting != null ? setting.getInternalFormat() : InternalTextureFormat.RGBA;
            int vkFormat = fmt.getColorRenderableFallback().getVkFormat();
            float[] clear = new float[]{0, 0, 0, 0};
            if (setting != null) {
                var c = setting.getClearColor().orElse(null);
                if (c != null) {
                    clear = new float[]{c.x, c.y, c.z, c.w};
                }
            }
            specs.add(new RenderTargetSpec(
                    "colortex" + i,
                    RenderTargetSpec.Kind.COLOR_TEXTURE,
                    vkFormat,
                    1.0f,
                    true,
                    clear));
        }

        return specs;
    }

    private List<OrderedPass> buildOrderedPasses(
            ProgramSet programSet,
            PackDirectives directives,
            boolean hasShadowPass,
            PackCompilerCapabilities caps,
            Map<String, List<NamedDescriptorBinding>> namedBindings,
            Map<String, int[]> computeModules,
            Map<String, org.soutalab.celtium.shader.shaderpack.IndirectPointer> computeIndirectPointers) {
        List<OrderedPass> passes = new ArrayList<>();

        // Begin compute passes
        addComputePasses(passes, programSet.getBeginCompute(), true, directives,
                namedBindings, computeModules, computeIndirectPointers);

        // Shadow stage
        if (hasShadowPass) {
            addComputePasses(passes, new ComputeSource[][]{programSet.getShadowCompute()}, false, directives,
                    namedBindings, computeModules, computeIndirectPointers);
            List<TerrainPassKind> shadowKinds = List.of(
                    TerrainPassKind.SHADOW_SOLID,
                    TerrainPassKind.SHADOW_CUTOUT,
                    TerrainPassKind.SHADOW_TRANSLUCENT);
            passes.add(new OrderedPass.ShadowPass(
                    "shadow",
                    shadowKinds,
                    List.of(),
                    List.of("shadowtex0", "shadowtex1", "shadowcolor0"),
                    passes.isEmpty() ? BarrierSpec.NONE : BARRIER_COMPUTE_TO_GRAPHICS));
        }

        // Prepare passes
        for (ProgramSource src : programSet.getPrepare()) {
            compileFullscreenPass(src, directives, caps).ifPresent(compiled -> {
                namedBindings.put(src.getName(), compiled.namedBindings());
                passes.add(new OrderedPass.PreparePass(
                        src.getName(),
                        compiled.spirv(),
                        List.of("colortex0"),
                        drawBufferNames(src.getDirectives()),
                        BARRIER_COLOR_TO_FRAGMENT_SAMPLE));
            });
        }

        // GBuffer stage
        List<TerrainPassKind> mainKinds = List.of(
                TerrainPassKind.MAIN_SOLID,
                TerrainPassKind.MAIN_CUTOUT,
                TerrainPassKind.MAIN_TRANSLUCENT);
        passes.add(new OrderedPass.GBufferPass(
                "gbuffers",
                mainKinds,
                hasShadowPass ? List.of("shadowtex0", "shadowtex1", "shadowcolor0") : List.of(),
                List.of("colortex0", "depthtex0"),
                hasShadowPass ? BARRIER_SHADOW_TO_GBUFFER : BarrierSpec.NONE));

        // Deferred passes
        for (ProgramSource src : programSet.getDeferred()) {
            compileFullscreenPass(src, directives, caps).ifPresent(compiled -> {
                namedBindings.put(src.getName(), compiled.namedBindings());
                passes.add(new OrderedPass.DeferredPass(
                        src.getName(),
                        compiled.spirv(),
                        List.of("colortex0", "depthtex0"),
                        drawBufferNames(src.getDirectives()),
                        BARRIER_COLOR_TO_FRAGMENT_SAMPLE));
            });
        }

        // Composite passes
        for (ProgramSource src : programSet.getComposite()) {
            compileFullscreenPass(src, directives, caps).ifPresent(compiled -> {
                namedBindings.put(src.getName(), compiled.namedBindings());
                List<String> outputs = drawBufferNames(src.getDirectives());
                boolean flip = !outputs.isEmpty();
                passes.add(new OrderedPass.CompositePass(
                        src.getName(),
                        compiled.spirv(),
                        List.of("colortex0", "depthtex0", "depthtex1"),
                        outputs,
                        BARRIER_COLOR_TO_FRAGMENT_SAMPLE,
                        flip));
            });
        }

        // Final pass -> Swapchain directly (P5)
        programSet.getCompositeFinal().ifPresent(src ->
                compileFullscreenPass(src, directives, caps).ifPresent(compiled -> {
                    namedBindings.put(src.getName(), compiled.namedBindings());
                    passes.add(new OrderedPass.FinalPass(
                            src.getName(),
                            compiled.spirv(),
                            List.of("colortex0"),
                            BARRIER_COLOR_TO_FRAGMENT_SAMPLE));
                }));

        return passes;
    }

    private void addComputePasses(
            List<OrderedPass> passes,
            ComputeSource[][] groups,
            boolean isBegin,
            PackDirectives directives,
            Map<String, List<NamedDescriptorBinding>> namedBindings,
            Map<String, int[]> computeModules,
            Map<String, org.soutalab.celtium.shader.shaderpack.IndirectPointer> computeIndirectPointers) {
        if (groups == null) {
            return;
        }
        for (ComputeSource[] group : groups) {
            if (group == null) {
                continue;
            }
            for (ComputeSource cs : group) {
                if (cs == null || !cs.isValid()) {
                    continue;
                }
                String src = cs.getSource().orElse(null);
                if (src == null) {
                    continue;
                }
                String patched = TransformPatcher.patchCompute(
                        cs.getName(), src, TextureStage.COMPOSITE_AND_FINAL, directives.getTextureMap());
                PackSpirvCompiler.CompiledProgram compiled = patched != null
                        ? spirvCompiler.compileCompute(cs.getName(), patched)
                        : null;
                if (compiled != null) {
                    // The graphics-shaped pass program stays null; the executor takes the
                    // compute module and bindings from these side tables.
                    computeModules.put(cs.getName(), PackSpirvCompiler.computeWords(compiled));
                    namedBindings.put(cs.getName(), compiled.namedBindings());
                }
                if (cs.getIndirectPointer() != null) {
                    computeIndirectPointers.put(cs.getName(), cs.getIndirectPointer());
                }
                if (isBegin) {
                    passes.add(new OrderedPass.BeginComputePass(
                            cs.getName(),
                            null,
                            List.of(),
                            List.of(),
                            BarrierSpec.NONE));
                } else {
                    passes.add(new OrderedPass.ShadowComputePass(
                            cs.getName(),
                            null,
                            List.of("shadowtex0"),
                            List.of("shadowcolor0"),
                            BarrierSpec.NONE));
                }
            }
        }
    }

    private Optional<PackSpirvCompiler.CompiledProgram> compileFullscreenPass(
            ProgramSource src,
            PackDirectives directives,
            PackCompilerCapabilities caps) {
        if (src == null || !src.isValid()) {
            return Optional.empty();
        }
        String vsh = src.getVertexSource().orElse(null);
        String fsh = src.getFragmentSource().orElse(null);
        String gsh = caps.supportsGeometryShader() ? src.getGeometrySource().orElse(null) : null;
        if (vsh == null || fsh == null) {
            return Optional.empty();
        }
        Map<PatchShaderType, String> transformed = TransformPatcher.patchComposite(
                vsh, gsh, fsh, TextureStage.COMPOSITE_AND_FINAL, directives.getTextureMap());
        if (transformed == null) {
            return Optional.empty();
        }
        String patchedVsh = transformed.get(PatchShaderType.VERTEX);
        String patchedFsh = transformed.get(PatchShaderType.FRAGMENT);
        String patchedGsh = transformed.get(PatchShaderType.GEOMETRY);
        if (patchedVsh == null || patchedFsh == null) {
            return Optional.empty();
        }
        return Optional.of(spirvCompiler.compileGraphics(src.getName(), patchedVsh, patchedFsh, patchedGsh));
    }

    private static List<String> drawBufferNames(ProgramDirectives directives) {
        int[] buffers = directives.getDrawBuffers();
        if (buffers == null || buffers.length == 0) {
            return List.of("colortex0");
        }
        List<String> out = new ArrayList<>(buffers.length);
        for (int b : buffers) {
            out.add("colortex" + b);
        }
        return out;
    }

    private static Optional<ProgramSource> resolveTerrainSource(
            ProgramFallbackResolver resolver, ProgramSet programSet, TerrainPassKind kind) {
        return switch (kind) {
            case MAIN_SOLID -> resolver.resolve(ProgramId.TerrainSolid);
            case MAIN_CUTOUT -> resolver.resolve(ProgramId.TerrainCutout);
            case MAIN_TRANSLUCENT -> resolver.resolve(ProgramId.Water);
            case SHADOW_SOLID -> resolver.resolve(ProgramId.ShadowSolid);
            case SHADOW_CUTOUT -> resolver.resolve(ProgramId.ShadowCutout);
            case SHADOW_TRANSLUCENT -> resolver.resolve(ProgramId.ShadowWater);
        };
    }

    private static boolean isShadowPass(TerrainPassKind kind) {
        return kind == TerrainPassKind.SHADOW_SOLID
                || kind == TerrainPassKind.SHADOW_CUTOUT
                || kind == TerrainPassKind.SHADOW_TRANSLUCENT;
    }

    private static AttachmentFormatKey buildAttachmentFormats(
            PackDirectives packDirectives, ProgramDirectives programDirectives, boolean isShadow) {
        int[] drawBuffers = programDirectives.getDrawBuffers();
        if (drawBuffers == null || drawBuffers.length == 0) {
            drawBuffers = new int[]{0};
        }
        int[] vkFormats = new int[drawBuffers.length];
        var rtSettings = packDirectives.getRenderTargetDirectives().getRenderTargetSettings();
        for (int i = 0; i < drawBuffers.length; i++) {
            var s = rtSettings.get(drawBuffers[i]);
            InternalTextureFormat fmt = s != null ? s.getInternalFormat() : InternalTextureFormat.RGBA8;
            vkFormats[i] = fmt.getColorRenderableFallback().getVkFormat();
        }
        return AttachmentFormatKey.of(vkFormats, DEPTH_VK_FORMAT, AttachmentFormatKey.FORMAT_UNDEFINED);
    }

    private static BlendStateSpec buildBlendSpec(
            ProgramDirectives directives, int attachmentCount, @Nullable TerrainPassKind kind) {
        int count = Math.max(1, attachmentCount);
        AttachmentBlendSpec defaultSpec = (kind == TerrainPassKind.MAIN_TRANSLUCENT || kind == TerrainPassKind.SHADOW_TRANSLUCENT)
                ? AttachmentBlendSpec.alphaBlend()
                : AttachmentBlendSpec.opaque();

        BlendModeOverride globalOverride = directives.getBlendModeOverride().orElse(null);
        if (globalOverride != null) {
            defaultSpec = globalOverride.toAttachmentBlendSpec();
        }

        List<AttachmentBlendSpec> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(defaultSpec);
        }
        for (BufferBlendInformation info : directives.getBufferBlendOverrides()) {
            BufferBlendOverride bo = new BufferBlendOverride(info.getIndex(), info.getBlendMode());
            if (bo.getIndex() >= 0 && bo.getIndex() < count) {
                list.set(bo.getIndex(), bo.toAttachmentBlendSpec());
            }
        }
        return new BlendStateSpec(list);
    }

    private static float resolveAlphaThreshold(ProgramDirectives directives, TerrainPassKind kind) {
        Optional<AlphaTestOverride> override = directives.getAlphaTestOverride();
        if (override.isPresent()) {
            float ref = override.get().getReference();
            return ref < 0.0f ? 0.0f : ref;
        }
        return switch (kind) {
            case MAIN_SOLID, SHADOW_SOLID -> 0.0f;
            case MAIN_CUTOUT, SHADOW_CUTOUT -> 0.1f;
            case MAIN_TRANSLUCENT, SHADOW_TRANSLUCENT -> 0.0001f;
        };
    }

    private static boolean resolveDepthWrite(boolean writeStainedGlassDepth, TerrainPassKind kind) {
        if (kind == TerrainPassKind.MAIN_TRANSLUCENT || kind == TerrainPassKind.SHADOW_TRANSLUCENT) {
            return writeStainedGlassDepth;
        }
        return true;
    }
}
