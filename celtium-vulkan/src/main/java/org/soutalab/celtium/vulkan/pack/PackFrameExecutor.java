/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 */
package org.soutalab.celtium.vulkan.pack;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.soutalab.celtium.core.frame.ActiveRenderTargets;
import org.soutalab.celtium.core.frame.FrameContext;
import org.soutalab.celtium.core.shader.DescriptorBindingPlan;
import org.soutalab.celtium.core.shader.NonTerrainPackTarget;
import org.soutalab.celtium.core.shader.PackExecutionPlan;
import org.soutalab.celtium.core.shader.PackTerrainDriver;
import org.soutalab.celtium.core.shader.TerrainPassKind;
import org.soutalab.celtium.core.shader.TerrainStageProgram;
import org.soutalab.celtium.shader.compiler.CompiledShaderPack;
import org.soutalab.celtium.shader.compiler.NamedDescriptorBinding;
import org.soutalab.celtium.shader.compiler.NonTerrainProgramCatalog;
import org.soutalab.celtium.shader.shaderpack.FilledIndirectPointer;
import org.soutalab.celtium.shader.shaderpack.IndirectPointer;
import org.soutalab.celtium.vulkan.GpuPassTimingReport;
import org.soutalab.celtium.vulkan.VulkanContext;
import org.soutalab.celtium.vulkan.hal.VulkanDynamicRendering;
import org.soutalab.celtium.vulkan.terrain.lane1.Lane1CommandStream;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.lwjgl.vulkan.VK13.*;

/**
 * The shader pack Vulkan 1.3 executor (plan section 2.3, stage S5-b): walks a compiled pack's
 * {@link PackExecutionPlan} in the fixed P0-P5 template and records the whole frame natively -
 * pack uniforms and views (P0), begin compute (P1), the shadow stage with the non-terrain
 * lane's window between CUTOUT and TRANSLUCENT (P2), the gbuffer stage with its two windows
 * and the depth copies (P3), the deferred/composite fullscreen chain over ping-pong targets
 * (P4) and the final pass straight into the swapchain (P5). No stage ever passes through the
 * GL converter: terrain goes through {@link PackTerrainDriver}, non-terrain programs through
 * {@link NonTerrainPackTarget}, everything else is recorded here directly.
 *
 * <p>The executor is deliberately self-contained: it receives the frame's command buffer and
 * a {@link PackFrameEnvironment} and owns every pack-side GPU object (attachments in the
 * shared slab, the uniform ring, descriptor sets, pipelines, SSBO arena, custom textures).
 * Final frame integration - who calls {@link #executeFrame} and in which command buffer -
 * belongs to the frame coordinator.
 */
public final class PackFrameExecutor implements AutoCloseable {

    /** Per-program descriptor knowledge: layout bindings plus the name of each binding. */
    private static final class ProgramInfo {
        final List<PackDescriptorManager.LayoutBinding> layoutBindings = new ArrayList<>();
        final Map<Integer, String> nameByBinding = new HashMap<>();
        final Map<Integer, DescriptorBindingPlan.Kind> kindByBinding = new HashMap<>();

        void add(int binding, DescriptorBindingPlan.Kind kind, int stageMask, int count, String name) {
            layoutBindings.add(new PackDescriptorManager.LayoutBinding(binding, kind, stageMask, count));
            kindByBinding.put(binding, kind);
            if (name != null) {
                nameByBinding.put(binding, name);
            }
        }
    }

    private final VulkanContext context;
    private final CompiledShaderPack pack;
    private final PackTerrainDriver terrainDriver;
    private final int framesInFlight;

    private final PackUniformBuffer uniforms;
    private final PackAttachments attachments;
    private final PackDescriptorManager descriptors;
    private final PackPipelineFactory pipelines;
    private final PackSsboArena ssboArena;
    private final PackTextures textures;

    private final Map<String, ProgramInfo> programs = new LinkedHashMap<>();
    private final Map<String, Long> nonTerrainPipelines = new HashMap<>();
    private final Set<String> writtenSets = new java.util.HashSet<>();

    // GPU stage timings: one timestamp pool, 2 queries per stage boundary per slot.
    private static final int MAX_STAGE_QUERIES = 32;
    private long timestampPool;
    private double timestampPeriodNanos = 1.0;
    private final Map<String, Long> stageTimingsNanos = new LinkedHashMap<>();
    private int stageQueryCursor;
    private final List<String> stageQueryNames = new ArrayList<>();

    private int skippedComputePasses;
    private int skippedFinalPasses;
    private boolean sized;
    private int lastViewWidth;
    private int lastViewHeight;
    private final Map<Integer, List<String>> pendingSpansBySlot = new HashMap<>();

    public PackFrameExecutor(VulkanContext context, CompiledShaderPack pack,
                             PackTerrainDriver terrainDriver, PackSubmitter submitter,
                             int framesInFlight, int shadowExtent) {
        this.context = context;
        this.pack = pack;
        this.terrainDriver = terrainDriver;
        this.framesInFlight = Math.max(1, framesInFlight);

        PackExecutionPlan plan = pack.executionPlan();
        this.uniforms = new PackUniformBuffer(context.physicalDevice(), context.device(),
                plan, this.framesInFlight);

        Set<String> storageTargets = new java.util.HashSet<>();
        for (List<NamedDescriptorBinding> bindings : pack.namedBindings().values()) {
            for (NamedDescriptorBinding binding : bindings) {
                if (binding.kind() == DescriptorBindingPlan.Kind.STORAGE_IMAGE) {
                    storageTargets.add(binding.name());
                }
            }
        }
        this.attachments = new PackAttachments(context, plan, shadowExtent, storageTargets);
        this.descriptors = new PackDescriptorManager(context, this.framesInFlight);
        this.pipelines = new PackPipelineFactory(context);
        this.ssboArena = new PackSsboArena(context, pack.sourcePack().getBufferObjects());
        this.textures = new PackTextures(context, submitter);
        for (Map<String, org.soutalab.celtium.shader.shaderpack.texture.CustomTextureData> byStage
                : pack.sourcePack().getCustomTextureDataMap().values()) {
            textures.loadCustomTextures(byStage);
        }
        textures.loadCustomTextures(pack.sourcePack().getIrisCustomTextureDataMap());

        registerPrograms();
        createTimestampPool();
    }

    // ------------------------------------------------------------------ registration

    private void registerPrograms() {
        // Terrain programs: binding plans come from the stage programs themselves.
        for (TerrainPassKind kind : TerrainPassKind.values()) {
            pack.terrainPrograms().programFor(kind).ifPresent(program -> {
                String key = terrainKey(kind);
                ProgramInfo info = new ProgramInfo();
                Map<Integer, String> names = namesByBinding(pack.namedBindingsFor(key));
                for (DescriptorBindingPlan.Binding binding : program.bindings().bindings()) {
                    info.add(binding.binding(), binding.kind(),
                            PackDescriptorManager.stageMask(binding.stages()),
                            binding.descriptorCount(), names.get(binding.binding()));
                }
                programs.put(key, info);
                descriptors.registerProgram(key, info.layoutBindings);
            });
        }
        // Non-terrain programs (Lane 2's catalog).
        for (NonTerrainProgramCatalog.ProgramSpec spec : pack.nonTerrainPrograms().allPrograms().values()) {
            ProgramInfo info = new ProgramInfo();
            Map<Integer, String> names = namesByBinding(pack.namedBindingsFor(spec.programName()));
            for (DescriptorBindingPlan.Binding binding : spec.bindings().bindings()) {
                info.add(binding.binding(), binding.kind(),
                        PackDescriptorManager.stageMask(binding.stages()),
                        binding.descriptorCount(), names.get(binding.binding()));
            }
            programs.put(spec.programName(), info);
            descriptors.registerProgram(spec.programName(), info.layoutBindings);
        }
        // Fullscreen passes and compute passes: the plan carries no binding plan for them, so
        // the layout is reflected from the modules and named from the compiler's table.
        for (PackExecutionPlan.OrderedPass pass : pack.executionPlan().passes()) {
            if (pass instanceof PackExecutionPlan.OrderedPass.PreparePass
                    || pass instanceof PackExecutionPlan.OrderedPass.DeferredPass
                    || pass instanceof PackExecutionPlan.OrderedPass.CompositePass
                    || pass instanceof PackExecutionPlan.OrderedPass.FinalPass) {
                String key = passKey(pass.name());
                if (programs.containsKey(key)) {
                    continue;
                }
                ProgramInfo info = new ProgramInfo();
                Map<Integer, String> names = namesByBinding(pack.namedBindingsFor(pass.name()));
                Set<Integer> seen = new java.util.HashSet<>();
                pass.program().ifPresent(bundle -> {
                    for (SpirvReflection.ModuleInfo module : List.of(
                            SpirvReflection.reflect(bundle.vertexWords()),
                            SpirvReflection.reflect(bundle.fragmentWords()))) {
                        for (SpirvReflection.DescriptorVariable variable : module.descriptors()) {
                            if (seen.add(variable.binding())) {
                                info.add(variable.binding(), kindOf(variable.kind()),
                                        VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT,
                                        1, names.getOrDefault(variable.binding(), variable.name()));
                            }
                        }
                    }
                });
                programs.put(key, info);
                descriptors.registerProgram(key, info.layoutBindings);
            } else if (pass instanceof PackExecutionPlan.OrderedPass.BeginComputePass
                    || pass instanceof PackExecutionPlan.OrderedPass.ShadowComputePass) {
                String key = computeKey(pass.name());
                if (programs.containsKey(key)) {
                    continue;
                }
                ProgramInfo info = new ProgramInfo();
                Map<Integer, String> names = namesByBinding(pack.namedBindingsFor(pass.name()));
                int[] words = pack.computeModules().get(pass.name());
                if (words != null) {
                    for (SpirvReflection.DescriptorVariable variable
                            : SpirvReflection.reflect(words).descriptors()) {
                        info.add(variable.binding(), kindOf(variable.kind()),
                                VK_SHADER_STAGE_COMPUTE_BIT, 1,
                                names.getOrDefault(variable.binding(), variable.name()));
                    }
                }
                programs.put(key, info);
                descriptors.registerProgram(key, info.layoutBindings);
            }
        }
    }

    private static Map<Integer, String> namesByBinding(List<NamedDescriptorBinding> named) {
        Map<Integer, String> names = new HashMap<>();
        for (NamedDescriptorBinding binding : named) {
            names.put(binding.binding(), binding.name());
        }
        return names;
    }

    private static DescriptorBindingPlan.Kind kindOf(SpirvReflection.DescriptorKind kind) {
        return switch (kind) {
            case UNIFORM_BUFFER -> DescriptorBindingPlan.Kind.UNIFORM_BUFFER;
            case STORAGE_BUFFER -> DescriptorBindingPlan.Kind.STORAGE_BUFFER;
            case COMBINED_IMAGE_SAMPLER -> DescriptorBindingPlan.Kind.COMBINED_IMAGE_SAMPLER;
            case SAMPLED_IMAGE -> DescriptorBindingPlan.Kind.SAMPLED_IMAGE;
            case STORAGE_IMAGE -> DescriptorBindingPlan.Kind.STORAGE_IMAGE;
            case SAMPLER -> DescriptorBindingPlan.Kind.SAMPLER;
        };
    }

    static String terrainKey(TerrainPassKind kind) {
        return "terrain:" + kind.name();
    }

    static String passKey(String passName) {
        return "pass:" + passName;
    }

    static String computeKey(String passName) {
        return "compute:" + passName;
    }

    // ------------------------------------------------------------------ frame entry

    /**
     * Records one full pack frame into {@code cmd}. The command buffer must be in recording
     * state and outside any rendering scope; submission and presentation stay with the caller.
     */
    public void executeFrame(VkCommandBuffer cmd, FrameContext ctx, PackFrameEnvironment env) {
        ActiveRenderTargets swapchain = env.swapchainTargets();
        if (swapchain != null) {
            attachments.resize(swapchain.width(), swapchain.height());
            lastViewWidth = swapchain.width();
            lastViewHeight = swapchain.height();
            if (!sized) {
                ssboArena.allocate(swapchain.width(), swapchain.height());
                sized = true;
            }
        } else if (!sized) {
            return; // no extent known yet: nothing can be recorded honestly
        }
        int slot = (int) Math.floorMod(ctx.frameId(), framesInFlight);
        writtenSets.clear();
        readBackTimings(slot);

        // P0: pack uniforms for this frame (built-ins + custom), then the weather feed.
        java.lang.foreign.MemorySegment uboSegment = uniforms.slotSegment(slot);
        pack.terrainPrograms().updateFrameUniforms(uboSegment, ctx);
        PackWeatherFeed.writeInto(pack.executionPlan().uniformLayout(), uboSegment, env.weather());

        terrainDriver.prepareMainView(env.mainCamera(), env.mainFrustum(), ctx.frameId());
        boolean hasShadow = pack.terrainPrograms().hasShadowPass();
        if (hasShadow) {
            terrainDriver.prepareShadowView(env.shadowCamera(), env.shadowFrustum(),
                    env.shadowDistance(), ctx.frameId());
        }

        if (timestampPool != 0L) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                vkCmdResetQueryPool(cmd, timestampPool, slot * MAX_STAGE_QUERIES, MAX_STAGE_QUERIES);
            }
            stageQueryCursor = 0;
            stageQueryNames.clear();
        }

        for (PackExecutionPlan.OrderedPass pass : pack.executionPlan().passes()) {
            String stage = stageNameOf(pass);
            int stageStartQuery = writeTimestamp(cmd, slot);
            if (pass instanceof PackExecutionPlan.OrderedPass.BeginComputePass begin) {
                runComputePass(cmd, begin.name(), begin.barrier(), begin.outputTargets(), slot, env);
            } else if (pass instanceof PackExecutionPlan.OrderedPass.ShadowComputePass shadowCompute) {
                runComputePass(cmd, shadowCompute.name(), shadowCompute.barrier(),
                        shadowCompute.outputTargets(), slot, env);
            } else if (pass instanceof PackExecutionPlan.OrderedPass.ShadowPass shadow) {
                runShadowStage(cmd, shadow, slot, env);
            } else if (pass instanceof PackExecutionPlan.OrderedPass.PreparePass prepare) {
                runFullscreenPass(cmd, passKey(prepare.name()), prepare.program().orElse(null),
                        prepare.inputTargets(), prepare.outputTargets(), prepare.barrier(),
                        slot, env, null, false);
            } else if (pass instanceof PackExecutionPlan.OrderedPass.GBufferPass gbuffer) {
                runGBufferStage(cmd, gbuffer, slot, env);
            } else if (pass instanceof PackExecutionPlan.OrderedPass.DeferredPass deferred) {
                runFullscreenPass(cmd, passKey(deferred.name()), deferred.program().orElse(null),
                        deferred.inputTargets(), deferred.outputTargets(), deferred.barrier(),
                        slot, env, null, false);
            } else if (pass instanceof PackExecutionPlan.OrderedPass.CompositePass composite) {
                runFullscreenPass(cmd, passKey(composite.name()), composite.program().orElse(null),
                        composite.inputTargets(), composite.outputTargets(), composite.barrier(),
                        slot, env, null, composite.flip());
            } else if (pass instanceof PackExecutionPlan.OrderedPass.FinalPass finalPass) {
                if (swapchain == null) {
                    skippedFinalPasses++;
                } else {
                    runFullscreenPass(cmd, passKey(finalPass.name()), finalPass.program().orElse(null),
                            finalPass.inputTargets(), List.of(), finalPass.barrier(),
                            slot, env, swapchain, false);
                }
            }
            int stageEndQuery = writeTimestamp(cmd, slot);
            recordStageSpan(stage, stageStartQuery, stageEndQuery);
        }
        if (timestampPool != 0L) {
            pendingSpansBySlot.put(slot, List.copyOf(stageQueryNames));
        }
    }

    private static String stageNameOf(PackExecutionPlan.OrderedPass pass) {
        if (pass instanceof PackExecutionPlan.OrderedPass.BeginComputePass) {
            return "begin";
        }
        if (pass instanceof PackExecutionPlan.OrderedPass.ShadowComputePass
                || pass instanceof PackExecutionPlan.OrderedPass.ShadowPass) {
            return "shadow";
        }
        if (pass instanceof PackExecutionPlan.OrderedPass.GBufferPass) {
            return "gbuffers";
        }
        if (pass instanceof PackExecutionPlan.OrderedPass.PreparePass
                || pass instanceof PackExecutionPlan.OrderedPass.DeferredPass) {
            return "deferred";
        }
        if (pass instanceof PackExecutionPlan.OrderedPass.CompositePass) {
            return "composite";
        }
        return "final";
    }

    // ------------------------------------------------------------------ terrain stages

    private void runShadowStage(VkCommandBuffer cmd, PackExecutionPlan.OrderedPass.ShadowPass pass,
                                  int slot, PackFrameEnvironment env) {
        applyBarrier(cmd, pass.barrier());
        for (String input : pass.inputTargets()) {
            attachments.transitionReadToSampled(cmd, input);
        }
        for (String output : pass.outputTargets()) {
            attachments.transitionWriteToAttachment(cmd, output);
        }
        ActiveRenderTargets clear = attachments.activeTargets(pass.outputTargets(), true);
        ActiveRenderTargets load = attachments.activeTargets(pass.outputTargets(), false);
        Lane1CommandStream stream = newLane1Stream(cmd, pass.outputTargets(), slot, env);
        boolean first = true;
        boolean windowDone = false;
        for (TerrainPassKind kind : pass.terrainKinds()) {
            if (kind == TerrainPassKind.SHADOW_TRANSLUCENT && !windowDone) {
                runShadowWindow(cmd, load, slot, env);
                windowDone = true;
            }
            var program = pack.terrainPrograms().programFor(kind);
            if (program.isEmpty()) {
                continue;
            }
            terrainDriver.recordTerrainPass(stream, kind, program.get(), first ? clear : load);
            first = false;
        }
        if (!windowDone) {
            runShadowWindow(cmd, load, slot, env);
        }
    }

    private void runShadowWindow(VkCommandBuffer cmd, ActiveRenderTargets targets,
                                 int slot, PackFrameEnvironment env) {
        List<PackFrameEnvironment.ShadowBlockEntity> entities = new ArrayList<>();
        terrainDriver.gatherShadowBlockEntities(env.shadowFrustum(),
                (x, y, z, typeId) -> entities.add(
                        new PackFrameEnvironment.ShadowBlockEntity(x, y, z, typeId)));
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VulkanDynamicRendering.beginRendering(cmd, stack, targets, null, 1.0f, 0);
            try {
                env.lane2().drawShadow(cmd,
                        stageTarget("shadow", targets, slot), entities);
            } finally {
                VulkanDynamicRendering.endRendering(cmd);
            }
        }
    }

    private void runGBufferStage(VkCommandBuffer cmd, PackExecutionPlan.OrderedPass.GBufferPass pass,
                                   int slot, PackFrameEnvironment env) {
        applyBarrier(cmd, pass.barrier());
        for (String input : pass.inputTargets()) {
            attachments.transitionReadToSampled(cmd, input);
        }
        for (String output : pass.outputTargets()) {
            attachments.transitionWriteToAttachment(cmd, output);
        }
        ActiveRenderTargets clear = attachments.activeTargets(pass.outputTargets(), true);
        ActiveRenderTargets load = attachments.activeTargets(pass.outputTargets(), false);
        Lane1CommandStream stream = newLane1Stream(cmd, pass.outputTargets(), slot, env);
        boolean first = true;
        boolean entitiesDone = false;
        for (TerrainPassKind kind : pass.terrainKinds()) {
            if (kind == TerrainPassKind.MAIN_TRANSLUCENT && !entitiesDone) {
                runLane2Window(cmd, load, slot, env, true);
                entitiesDone = true;
            }
            var program = pack.terrainPrograms().programFor(kind);
            if (program.isEmpty()) {
                continue;
            }
            terrainDriver.recordTerrainPass(stream, kind, program.get(), first ? clear : load);
            first = false;
        }
        if (!entitiesDone) {
            runLane2Window(cmd, load, slot, env, true);
        }
        runLane2Window(cmd, load, slot, env, false);

        // P3 tail: the depth copies later stages sample (depthtex1 / depthtex2).
        if (attachments.has("depthtex1")) {
            attachments.copyReadToWrite(cmd, "depthtex0", "depthtex1");
        }
        if (attachments.has("depthtex2")) {
            attachments.copyReadToWrite(cmd, "depthtex0", "depthtex2");
        }
    }

    private void runLane2Window(VkCommandBuffer cmd, ActiveRenderTargets targets,
                                int slot, PackFrameEnvironment env, boolean entities) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VulkanDynamicRendering.beginRendering(cmd, stack, targets, null, 1.0f, 0);
            try {
                NonTerrainPackTarget target = stageTarget("gbuffers", targets, slot);
                if (entities) {
                    env.lane2().drawMainEntities(cmd, target);
                } else {
                    env.lane2().drawMainTranslucentTail(cmd, target);
                }
            } finally {
                VulkanDynamicRendering.endRendering(cmd);
            }
        }
    }

    /** The interface-2 target of a stage: stage pipelines + this frame's uniform block. */
    private NonTerrainPackTarget stageTarget(String stageName, ActiveRenderTargets targets, int slot) {
        Map<String, Long> handles = new LinkedHashMap<>();
        for (String programName : pack.nonTerrainPrograms().programNames()) {
            boolean shadowProgram = programName.startsWith("shadow");
            if ("shadow".equals(stageName) != shadowProgram) {
                continue;
            }
            handles.put(programName, nonTerrainPipeline(programName, targets));
        }
        return pack.nonTerrainPrograms().bindStageTarget(
                stageName, targets, uniforms.slotSegment(slot), handles);
    }

    private long nonTerrainPipeline(String programName, ActiveRenderTargets targets) {
        String cacheKey = programName + "@" + targets.width() + "x" + targets.height()
                + "/" + targets.colorAttachments().size()
                + "/" + (targets.depthAttachment() != null ? targets.depthAttachment().format() : 0);
        Long existing = nonTerrainPipelines.get(cacheKey);
        if (existing != null) {
            return existing;
        }
        var spec = pack.nonTerrainPrograms().program(programName).orElseThrow();
        List<Integer> colorFormats = new ArrayList<>();
        for (ActiveRenderTargets.RenderTargetView view : targets.colorAttachments()) {
            colorFormats.add(view.format());
        }
        int depthFormat = targets.depthAttachment() != null ? targets.depthAttachment().format() : 0;
        PackPipelineFactory.GraphicsRequest request = new PackPipelineFactory.GraphicsRequest(
                cacheKey,
                spec.spirv().vertexWords(), spec.spirv().fragmentWords(), spec.spirv().geometryWords(),
                descriptors.pipelineLayout(programName),
                colorFormats, depthFormat, spec.blendSpec(),
                depthFormat != 0, false, false, VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
        long pipeline = pipelines.graphicsPipeline(request);
        nonTerrainPipelines.put(cacheKey, pipeline);
        return pipeline;
    }

    private Lane1CommandStream newLane1Stream(VkCommandBuffer cmd, List<String> outputs,
                                              int slot, PackFrameEnvironment env) {
        float[][] clearColors = new float[outputs.size()][];
        float clearDepth = 1.0f;
        int colorIndex = 0;
        for (String output : outputs) {
            if (!attachments.has(output)) {
                continue;
            }
            var spec = attachments.spec(output);
            if (PackAttachments.isDepth(spec)) {
                clearDepth = spec.clearColor()[0];
            } else {
                clearColors[colorIndex++] = spec.clearColor();
            }
        }
        Consumer<TerrainStageProgram> binder =
                program -> bindTerrainProgram(cmd, program, slot, env);
        return new Lane1CommandStream(context, cmd.address(), binder, clearColors, clearDepth, 0);
    }

    private void bindTerrainProgram(VkCommandBuffer cmd, TerrainStageProgram program,
                                    int slot, PackFrameEnvironment env) {
        // The binder runs inside recordTerrainPass, after beginRendering: the attachment set
        // is the one the driver was handed for this kind, recovered from the program's stage.
        String key = terrainKey(program.passKind());
        List<Integer> colorFormats = new ArrayList<>();
        for (int format : program.targetFormats().colorFormats()) {
            colorFormats.add(format);
        }
        int depthFormat = program.targetFormats().hasDepth()
                ? program.targetFormats().depthFormat() : 0;
        String cacheKey = key + "@" + colorFormats + "/" + depthFormat
                + "/" + program.depthWrite() + "/" + program.faceCullEnabled();
        PackPipelineFactory.GraphicsRequest request = new PackPipelineFactory.GraphicsRequest(
                cacheKey,
                program.spirv().vertexWords(), program.spirv().fragmentWords(),
                program.spirv().geometryWords(),
                descriptors.pipelineLayout(key),
                colorFormats, depthFormat, program.blendSpec(),
                depthFormat != 0, program.depthWrite(), program.faceCullEnabled(),
                VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
        long pipeline = pipelines.graphicsPipeline(request);
        vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        bindSet(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, key, slot, env);
    }

    // ------------------------------------------------------------------ fullscreen + compute

    private void runFullscreenPass(VkCommandBuffer cmd, String key,
                                   org.soutalab.celtium.core.shader.SpirvModuleBundle program,
                                   List<String> inputs, List<String> outputs,
                                   PackExecutionPlan.BarrierSpec barrier,
                                   int slot, PackFrameEnvironment env,
                                   ActiveRenderTargets swapchain, boolean flip) {
        if (program == null) {
            return;
        }
        applyBarrier(cmd, barrier);
        ProgramInfo info = programs.get(key);
        for (String input : inputs) {
            if (info != null && bindsAsStorage(info, input)) {
                attachments.transitionReadToGeneral(cmd, input);
            } else {
                attachments.transitionReadToSampled(cmd, input);
            }
        }
        ActiveRenderTargets targets;
        if (swapchain != null) {
            targets = swapchain;
        } else {
            for (String output : outputs) {
                attachments.transitionWriteToAttachment(cmd, output);
            }
            targets = attachments.activeTargets(outputs, true);
        }
        List<Integer> colorFormats = new ArrayList<>();
        for (ActiveRenderTargets.RenderTargetView view : targets.colorAttachments()) {
            colorFormats.add(view.format());
        }
        int depthFormat = targets.depthAttachment() != null ? targets.depthAttachment().format() : 0;
        String cacheKey = key + "@" + colorFormats + "/" + depthFormat;
        PackPipelineFactory.GraphicsRequest request = new PackPipelineFactory.GraphicsRequest(
                cacheKey,
                program.vertexWords(), program.fragmentWords(), null,
                descriptors.pipelineLayout(key),
                colorFormats, depthFormat, null,
                false, false, false, VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP);
        long pipeline = pipelines.graphicsPipeline(request);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VulkanDynamicRendering.beginRendering(cmd, stack, targets,
                    swapchain == null ? clearColorsOf(outputs) : null, 1.0f, 0);
            try {
                PackPipelineFactory.bindGraphics(cmd, pipeline, targets.width(), targets.height());
                vkCmdBindVertexBuffers(cmd, 0,
                        stack.longs(pipelines.quadBuffer()), stack.longs(0L));
                bindSet(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, key, slot, env);
                vkCmdDraw(cmd, 4, 1, 0, 0);
            } finally {
                VulkanDynamicRendering.endRendering(cmd);
            }
        }
        if (flip) {
            for (String output : outputs) {
                attachments.flip(output);
            }
        }
    }

    private float[][] clearColorsOf(List<String> outputs) {
        float[][] clearColors = new float[outputs.size()][];
        int colorIndex = 0;
        for (String output : outputs) {
            if (attachments.has(output) && !PackAttachments.isDepth(attachments.spec(output))) {
                clearColors[colorIndex++] = attachments.spec(output).clearColor();
            }
        }
        return clearColors;
    }

    private void runComputePass(VkCommandBuffer cmd, String passName,
                                PackExecutionPlan.BarrierSpec barrier,
                                List<String> outputs, int slot, PackFrameEnvironment env) {
        applyBarrier(cmd, barrier);
        int[] words = pack.computeModules().get(passName);
        if (words == null) {
            skippedComputePasses++;
            return;
        }
        String key = computeKey(passName);
        long pipeline = pipelines.computePipeline(key, words, descriptors.pipelineLayout(key));
        vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
        bindSet(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, key, slot, env);

        IndirectPointer pointer = pack.computeIndirectPointers().get(passName);
        if (pointer != null) {
            FilledIndirectPointer filled = ssboArena.fill(pointer, lastViewWidth, lastViewHeight);
            long buffer = ssboArena.bufferHandle(filled.buffer());
            if (buffer != 0L) {
                vkCmdDispatchIndirect(cmd, buffer, filled.offset());
                return;
            }
        }
        int[] localSize = SpirvReflection.reflect(words).computeLocalSize();
        int localX = localSize != null ? Math.max(localSize[0], 1) : 1;
        int localY = localSize != null ? Math.max(localSize[1], 1) : 1;
        int extentX = lastViewWidth;
        int extentY = lastViewHeight;
        if (!outputs.isEmpty() && attachments.has(outputs.get(0))) {
            extentX = attachments.width(outputs.get(0));
            extentY = attachments.height(outputs.get(0));
        }
        vkCmdDispatch(cmd,
                Math.max(1, (extentX + localX - 1) / localX),
                Math.max(1, (extentY + localY - 1) / localY),
                1);
    }

    private boolean bindsAsStorage(ProgramInfo info, String targetName) {
        for (Map.Entry<Integer, String> entry : info.nameByBinding.entrySet()) {
            if (entry.getValue().equals(targetName)
                    && info.kindByBinding.get(entry.getKey())
                            == DescriptorBindingPlan.Kind.STORAGE_IMAGE) {
                return true;
            }
        }
        return false;
    }

    private void applyBarrier(VkCommandBuffer cmd, PackExecutionPlan.BarrierSpec barrier) {
        if (barrier == null || barrier.equals(PackExecutionPlan.BarrierSpec.NONE)) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VulkanDynamicRendering.pipelineBarrier2Memory(cmd, stack, barrier);
        }
    }

    // ------------------------------------------------------------------ descriptors

    private void bindSet(VkCommandBuffer cmd, int bindPoint, String programKey,
                         int slot, PackFrameEnvironment env) {
        String writeKey = programKey + "@" + slot;
        long set = descriptors.set(programKey, slot);
        if (writtenSets.add(writeKey)) {
            descriptors.writeSet(set, resolveWrites(programKey, slot, env, cmd));
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkCmdBindDescriptorSets(cmd, bindPoint, descriptors.pipelineLayout(programKey),
                    0, stack.longs(set), null);
        }
    }

    /**
     * Resolves every descriptor of one program against this frame's state: the pack uniform
     * block, the plan's targets by name, pack custom textures, environment textures (atlas,
     * lightmap), and the SSBO arena in declared-index order for storage buffers.
     */
    private List<PackDescriptorManager.Write> resolveWrites(
            String programKey, int slot, PackFrameEnvironment env, VkCommandBuffer cmd) {
        ProgramInfo info = programs.get(programKey);
        List<PackDescriptorManager.Write> writes = new ArrayList<>();
        if (info == null) {
            return writes;
        }
        List<Integer> ssboIndices = ssboArena.declaredIndices();
        int ssboOrder = 0;
        for (PackDescriptorManager.LayoutBinding binding : info.layoutBindings) {
            String name = info.nameByBinding.get(binding.binding());
            switch (binding.kind()) {
                case UNIFORM_BUFFER -> writes.add(PackDescriptorManager.Write.uniformBuffer(
                        binding.binding(), uniforms.bufferHandle(),
                        uniforms.slotOffset(slot), uniforms.blockSizeBytes()));
                case STORAGE_BUFFER -> {
                    if (!ssboIndices.isEmpty()) {
                        int index = ssboIndices.get(Math.min(ssboOrder, ssboIndices.size() - 1));
                        long buffer = ssboArena.bufferHandle(index);
                        if (buffer != 0L) {
                            writes.add(PackDescriptorManager.Write.storageBuffer(
                                    binding.binding(), buffer, 0L, VK_WHOLE_SIZE));
                        }
                    }
                    ssboOrder++;
                }
                case SAMPLER -> writes.add(PackDescriptorManager.Write.samplerOnly(
                        binding.binding(),
                        textures.sampler(new org.soutalab.celtium.shader.shaderpack.texture.TextureFilteringData(true, false), false)));
                case COMBINED_IMAGE_SAMPLER, SAMPLED_IMAGE, STORAGE_IMAGE ->
                        writes.add(imageWrite(binding, name, env, cmd));
            }
        }
        return writes;
    }

    private PackDescriptorManager.Write imageWrite(
            PackDescriptorManager.LayoutBinding binding, String name,
            PackFrameEnvironment env, VkCommandBuffer cmd) {
        if (name != null && attachments.has(name)) {
            if (binding.kind() == DescriptorBindingPlan.Kind.STORAGE_IMAGE) {
                attachments.transitionReadToGeneral(cmd, name);
                return PackDescriptorManager.Write.storageImage(binding.binding(),
                        attachments.readView(name));
            }
            boolean shadowSampled = name.startsWith("shadow");
            long sampler = textures.sampler(
                    new org.soutalab.celtium.shader.shaderpack.texture.TextureFilteringData(true, false),
                    shadowSampled);
            return binding.kind() == DescriptorBindingPlan.Kind.SAMPLED_IMAGE
                    ? PackDescriptorManager.Write.sampledImage(binding.binding(),
                            attachments.readView(name))
                    : PackDescriptorManager.Write.combinedImage(binding.binding(),
                            attachments.readView(name), sampler);
        }
        if (name != null) {
            PackTextures.TextureImage custom = textures.custom(name);
            if (custom != null) {
                return PackDescriptorManager.Write.combinedImage(binding.binding(),
                        custom.imageView(), custom.sampler());
            }
            var resolved = env.texture(name);
            if (resolved.isPresent()) {
                return PackDescriptorManager.Write.combinedImage(binding.binding(),
                        resolved.get().imageView(), resolved.get().sampler());
            }
        }
        PackTextures.TextureImage fallback = textures.fallback();
        return PackDescriptorManager.Write.combinedImage(binding.binding(),
                fallback.imageView(), fallback.sampler());
    }

    /** The descriptor set of a non-terrain program for a frame slot (Lane 2 integration seat). */
    public long descriptorSetFor(String programName, int slot) {
        return descriptors.set(programName, slot);
    }

    /** The pipeline layout of a program (Lane 2 integration seat). */
    public long pipelineLayoutFor(String programName) {
        return descriptors.pipelineLayout(programName);
    }

    // ------------------------------------------------------------------ timings

    private void createTimestampPool() {
        if (!GpuPassTimingReport.enabled()) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkQueryPoolCreateInfo poolInfo =
                    org.lwjgl.vulkan.VkQueryPoolCreateInfo.calloc(stack)
                            .sType(VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO)
                            .queryType(VK_QUERY_TYPE_TIMESTAMP)
                            .queryCount(MAX_STAGE_QUERIES * framesInFlight);
            java.nio.LongBuffer handle = stack.mallocLong(1);
            if (vkCreateQueryPool(context.device(), poolInfo, null, handle) == VK_SUCCESS) {
                timestampPool = handle.get(0);
            }
            org.lwjgl.vulkan.VkPhysicalDeviceProperties properties =
                    org.lwjgl.vulkan.VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(context.physicalDevice(), properties);
            timestampPeriodNanos = properties.limits().timestampPeriod();
        }
    }

    private int writeTimestamp(VkCommandBuffer cmd, int slot) {
        if (timestampPool == 0L || stageQueryCursor >= MAX_STAGE_QUERIES - 1) {
            return -1;
        }
        int index = slot * MAX_STAGE_QUERIES + stageQueryCursor++;
        vkCmdWriteTimestamp(cmd, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, timestampPool, index);
        return index;
    }

    private void recordStageSpan(String stage, int startQuery, int endQuery) {
        if (startQuery >= 0 && endQuery >= 0) {
            stageQueryNames.add(stage + "\n" + startQuery + "\n" + endQuery);
        }
    }

    private void readBackTimings(int slot) {
        List<String> pendingSpans = pendingSpansBySlot.getOrDefault(slot, List.of());
        if (timestampPool == 0L || pendingSpans.isEmpty()) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.LongBuffer results = stack.mallocLong(MAX_STAGE_QUERIES);
            int result = vkGetQueryPoolResults(context.device(), timestampPool,
                    slot * MAX_STAGE_QUERIES, MAX_STAGE_QUERIES,
                    results, 8L, VK_QUERY_RESULT_64_BIT);
            if (result != VK_SUCCESS) {
                return;
            }
            Map<String, Long> sums = new LinkedHashMap<>();
            for (String span : pendingSpans) {
                String[] parts = span.split("\n");
                int start = Integer.parseInt(parts[1]) - slot * MAX_STAGE_QUERIES;
                int end = Integer.parseInt(parts[2]) - slot * MAX_STAGE_QUERIES;
                long delta = (long) ((results.get(end) - results.get(start)) * timestampPeriodNanos);
                if (delta > 0L) {
                    sums.merge(parts[0], delta, Long::sum);
                }
            }
            stageTimingsNanos.putAll(sums);
            pendingSpansBySlot.remove(slot);
        }
    }

    /** Stage timings in nanoseconds, keyed by stage (shadow/gbuffers/deferred/composite/final/begin). */
    public Map<String, Long> stageTimingsNanos() {
        return Map.copyOf(stageTimingsNanos);
    }

    /** Feeds the executor's stage timings into the CELTIUM_PASS_GPU report seat. */
    public void reportTimings(long frameId) {
        GpuPassTimingReport.printIfDue(frameId, Map.of(), stageTimingsNanos,
                0L, 0L, 0L, 0L, 0, 0L, 0L, 0L, "");
    }

    public int skippedComputePasses() {
        return skippedComputePasses;
    }

    public int skippedFinalPasses() {
        return skippedFinalPasses;
    }

    public String diagnosticsSummary() {
        return "pack executor: programs=" + programs.size()
                + " skippedCompute=" + skippedComputePasses
                + " skippedFinal=" + skippedFinalPasses
                + " stageTimings=" + stageTimingsNanos.keySet();
    }

    @Override
    public void close() {
        if (timestampPool != 0L) {
            vkDestroyQueryPool(context.device(), timestampPool, null);
            timestampPool = 0L;
        }
        descriptors.close();
        pipelines.close();
        attachments.close();
        uniforms.close();
        ssboArena.close();
        textures.close();
    }
}
