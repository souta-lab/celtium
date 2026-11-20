package org.soutalab.celtium.vulkan;

/**
 * A mod's own shader programs drawn natively (default on, -Dceltium.vulkan.modPrograms=false to refuse them): pipeline cache, prepared draws, input readback and the per-program report. Moved out of {@link VulkanTerrainGpuBackend} unchanged (2026-11-06).
 */
final class ModPrograms {
    private final VulkanTerrainGpuBackend b;

    ModPrograms(VulkanTerrainGpuBackend backend) {
        this.b = backend;
    }

    /**
     * Whether a mod's own program draws the batches it can. <b>On by default</b>; {@code
     * -Dceltium.vulkan.modPrograms=false} turns it off for an A/B.
     * <p>
     * It was opt-in while the passes into the game's framebuffer blacked the world out (legs r17-r20-gt). That was the
     * stale-target read fixed in round 9 of docs/evidence/gregtech-bloom-2026-11-04.md: the window prefix asked
     * {@code activeImmediateTarget} before it was assigned for the window, so the read barrier never ran before the
     * copy. Keyed on {@code targetRegistry.currentIdentity()} the same passes draw with validation clean and the world
     * is pixel-normal (r42-gt), and a program that cannot be lowered or compiled is still refused and counted per
     * program - so the default no longer has to be "refuse everything a mod brought".
     * <p>
     * Measured on the GTCEu scene (legs {@code gt1-amd}/{@code gt2-amd}, 2026-11-07, renderer {@code 25d73c78}): the
     * chain's 16 passes draw, {@code CELTIUM_SHADER_PROGRAM_REFUSED} is 0, and the lamp row's mean RGB rises from
     * 170.8/173.4/168.3 to 210.1/212.7/207.6 against the GL reference's 243.0/245.5/240.6 - the emissive composite
     * arrives, the halo does not yet (the bloom layer's terrain geometry is usually not built: {@code
     * CELTIUM_LAYER_GEOMETRY layer=Bloom ... passes=[TerrainRenderPass[name=bloom]=false]}).
     */
    static final boolean MOD_PROGRAMS =
            !"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.modPrograms", "true"));
    /** Lab switch: {@code -Dceltium.vulkan.modProgramsOpaqueToMain=false} refuses blend-off passes into it. */
    static final boolean MOD_PROGRAMS_REFUSE_OPAQUE_MAIN =
            "false".equalsIgnoreCase(System.getProperty("celtium.vulkan.modProgramsOpaqueToMain", "true"));
    /** Lab switch: {@code false} refuses the mod-program passes whose target is the game's framebuffer. */
    static final boolean MOD_PROGRAMS_TO_MAIN =
            !"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.modProgramsToMain", "true"));
    /** {@code -Dceltium.debug.modProgramTrace=N}: log the record order of mod-program passes for N frames from 300. */
    static final int MOD_TRACE_FRAMES = Integer.getInteger("celtium.debug.modProgramTrace", 0).intValue();
    int modTraceLines;
    /** Lab switch: {@code false} refuses the mod-program passes that sample the game's own colour image. */
    static final boolean MOD_PROGRAMS_SAMPLE_MAIN =
            !"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.modProgramsSampleMain", "true"));
    final java.util.Map<ModProgramKey, VulkanModProgramPipeline> modProgramPipelines = new java.util.HashMap<>();

    /**
     * A mod program pipeline's identity: both SPIR-V arrays and the variant it is built like, by reference, plus
     * the render pass it is built against and its colour count. The pass belongs in the key because a pipeline is
     * only valid inside a compatible pass: the same program drawn into a one-colour target and into a
     * multi-target framebuffer needs two pipelines, and the {@code like} variant's own pass is the one-colour
     * case of the same fact.
     */
    static final class ModProgramKey {
        private final byte[] vertex;
        private final byte[] fragment;
        private final int formatId;
        private final Object like;
        private final long renderPass;
        private final int colorCount;

        ModProgramKey(byte[] vertex, byte[] fragment, int formatId, Object like, long renderPass, int colorCount) {
            this.vertex = vertex;
            this.fragment = fragment;
            this.formatId = formatId;
            this.like = like;
            this.renderPass = renderPass;
            this.colorCount = colorCount;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ModProgramKey k && k.vertex == this.vertex && k.fragment == this.fragment
                    && k.formatId == this.formatId && k.like == this.like
                    && k.renderPass == this.renderPass && k.colorCount == this.colorCount;
        }

        @Override
        public int hashCode() {
            return ((((System.identityHashCode(this.vertex) * 31 + System.identityHashCode(this.fragment)) * 31
                    + this.formatId) * 31 + System.identityHashCode(this.like)) * 31
                    + (int) (this.renderPass ^ (this.renderPass >>> 32))) * 31 + this.colorCount;
        }
    }
    private static final VulkanGlTexture[] EMPTY_TEXTURES = new VulkanGlTexture[0];
    final it.unimi.dsi.fastutil.ints.IntOpenHashSet modProgramReported =
            new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
    private org.soutalab.celtium.vulkan.gl.ModProgramRegistry.Program lastModProgram;
    private int lastModFormatId = -1;
    private Object lastModLike;
    private long lastModRenderPass;
    private int lastModColorCount = -1;
    private VulkanModProgramPipeline lastModPipeline;
    String lastModProgramFailure = "none";

    /** {@return the registry's program when this batch can be drawn with it}, or null to refuse as before. */
    org.soutalab.celtium.vulkan.gl.ModProgramRegistry.Program celtium$executableModProgram(int program,
                                                                                                    int formatId) {
        if (!MOD_PROGRAMS) {
            return null;
        }
        org.soutalab.celtium.vulkan.gl.ModProgramRegistry.Program p =
                org.soutalab.celtium.vulkan.gl.ModProgramRegistry.program(program);
        String reason = VulkanModProgramPipeline.unsupported(p, formatId);
        if (reason != null) {
            celtium$reportModProgram(program, false, reason);
            return null;
        }
        return p;
    }

    /**
     * {@return the prepared draw for a mod program}: a pipeline with the render state of {@code standard} (what the
     * batch would have been drawn with), the program's packed uniforms, its samplers resolved from their texture units,
     * and its fragment storage images resolved from the image-unit ledger. Null, with
     * {@link #lastModProgramFailure}, when any required image or descriptor is missing or cannot be transitioned.
     */
    PreparedImmediate celtium$modPrepared(org.soutalab.celtium.vulkan.gl.ModProgramRegistry.Program program,
                                                  int programId, int formatId, PreparedImmediate standard) {
        if (!(standard.pipeline instanceof VulkanImmediateTexturePipeline like)) {
            return celtium$modFailure(programId, "the batch's own variant is "
                    + standard.pipeline.getClass().getSimpleName() + ", not a textured one");
        }
        // The target's colour count decides both the refusal and the pass: a program writing more fragment
        // outputs than the target attaches has nowhere to write them (and a pipeline built for it against a
        // one-colour pass is invalid outright), while a multi-target target needs the pipeline built against
        // its own count's pass.
        int targetColors = celtium$modTargetColors();
        String mrtRefusal = celtium$mrtRefusal(program.lowered().fragOutputCount(), targetColors);
        if (mrtRefusal != null) {
            return celtium$modFailure(programId, mrtRefusal);
        }
        // The pass variant matches the target pair's format class (QUEUE 404, S4): for several targets the
        // (count, class) chain pass; for one target the standard pipeline's own pass, which the prepare path
        // already built against the class' frame-shaped pass when the class is not the default.
        long targetRenderPass = targetColors > 1
                ? b.chainPairRenderPass(targetColors, b.celtium$currentPairFormatClass())
                : like.celtium$renderPassHandle();
        VulkanModProgramPipeline pipeline;
        if (program == this.lastModProgram && formatId == this.lastModFormatId && like == this.lastModLike
                && targetRenderPass == this.lastModRenderPass && targetColors == this.lastModColorCount
                && this.lastModPipeline != null) {
            pipeline = this.lastModPipeline;
        } else {
            // Keyed by the objects themselves: identity hash codes are not unique, so a key built from them could hand
            // one program's pipeline to another (and, after a world teardown, to a pipeline built like a released one).
            ModProgramKey key = new ModProgramKey(program.vertexSpirv(), program.fragmentSpirv(), formatId, like,
                    targetRenderPass, targetColors);
            pipeline = this.modProgramPipelines.get(key);
            if (pipeline == null) {
                try {
                    pipeline = targetColors > 1
                            ? VulkanModProgramPipeline.create(b.context, program, formatId, like,
                                    targetRenderPass, targetColors)
                            : VulkanModProgramPipeline.create(b.context, program, formatId, like);
                } catch (RuntimeException | LinkageError failure) {
                    return celtium$modFailure(programId, "pipeline: " + failure.getMessage());
                }
                this.modProgramPipelines.put(key, pipeline);
            }
            this.lastModProgram = program;
            this.lastModFormatId = formatId;
            this.lastModLike = like;
            this.lastModRenderPass = targetRenderPass;
            this.lastModColorCount = targetColors;
            this.lastModPipeline = pipeline;
        }
        int samplerCount = pipeline.samplerCount();
        VulkanGlTexture[] textures = samplerCount == 0 ? EMPTY_TEXTURES : new VulkanGlTexture[samplerCount];
        boolean buildDetail = this.modProgramReported.size() < 64 || MOD_TRACE_FRAMES > 0 || MOD_READBACK;
        StringBuilder inputs = buildDetail ? new StringBuilder() : null;
        for (int i = 0; i < samplerCount; i++) {
            org.soutalab.celtium.vulkan.gl.ModGlslTranslator.SamplerBinding sampler = pipeline.samplers().get(i);
            int[] unitValue = program.ints(sampler.name());
            int unit = unitValue == null || unitValue.length == 0 ? 0 : unitValue[0];
            int name = b.immediateState.boundTextureName(unit);
            VulkanGlTexture texture = name > 0 ? celtium$modSamplerTexture(name) : null;
            if (texture == null || texture.view() == 0L) {
                return celtium$modFailure(programId, "sampler " + sampler.name() + " (unit " + unit + ", texture "
                        + name + ") has no native image");
            }
            if (b.celtium$samplesCurrentTargetColor(name)) {
                // The pass would read the image it is drawing into: inside one Vulkan render pass that is a feedback
                // loop whose result is undefined (measured: leg r17-gt drew the whole world black). Refused until the
                // target is snapshotted before the pass.
                return celtium$modFailure(programId, "sampler " + sampler.name() + " (unit " + unit + ", texture "
                        + name + ") is the colour image of the framebuffer being drawn into");
            }
            if (!MOD_PROGRAMS_SAMPLE_MAIN && b.mainFramebufferColorNames.contains(name)) {
                return celtium$modFailure(programId, "sampler " + sampler.name() + " reads the game's colour and "
                        + "-Dceltium.vulkan.modProgramsSampleMain=false refuses that");
            }
            textures[i] = texture;
            if (inputs != null) {
                inputs.append(' ').append(sampler.name()).append("=unit").append(unit).append(":tex").append(name)
                        .append(b.mainFramebufferColorNames.contains(name) ? "(main)" : "")
                        .append(b.publishedFrameImages.get(name) != 0L ? "(fb)" : "")
                        .append("{fbo=0x").append(Long.toHexString(b.celtium$framebufferIdentityForColorTexture(name)))
                        .append(" img=0x").append(Long.toHexString(b.publishedFrameImages.get(name)))
                        .append(" wimg=0x").append(Long.toHexString(texture.image()))
                        .append(" own=").append(texture.celtium$ownsImage())
                        .append(" layout=").append(texture.samplingLayout()).append('}');
            }
        }
        java.util.List<org.soutalab.celtium.vulkan.gl.ModGlslTranslator.ImageBinding> imageBindings =
                program.lowered().images();
        int imageCount = imageBindings.size();
        VulkanGlTexture[] imageTextures = imageCount == 0 ? EMPTY_TEXTURES : new VulkanGlTexture[imageCount];
        if (imageCount > 0) {
            org.soutalab.celtium.vulkan.NativeImageBindings imageLedger =
                    org.soutalab.celtium.vulkan.NativeImageBindings.current();
            for (int i = 0; i < imageCount; i++) {
                var image = imageBindings.get(i);
                int[] unitValue = program.ints(image.name());
                int unit = unitValue == null || unitValue.length == 0 ? 0 : unitValue[0];
                int name = imageLedger.texture(unit);
                if (name <= 0) return celtium$modFailure(programId, "image " + image.name() + " (unit " + unit + ") is not bound");
                VulkanGlTexture texture = celtium$modSamplerTexture(name);
                if (texture == null || texture.view() == 0L)
                    return celtium$modFailure(programId, "image " + image.name() + " (unit " + unit + ", texture "
                            + name + ") has no native image");
                if (b.celtium$samplesCurrentTargetColor(name))
                    return celtium$modFailure(programId, "image " + image.name() + " (unit " + unit + ", texture "
                            + name + ") is the colour image of the framebuffer being drawn into");
                if (texture.celtium$ownsImage())
                    return celtium$modFailure(programId, "image " + image.name() + " (unit " + unit + ", texture "
                            + name + ") is a mirrored texture whose layout the storage path cannot transition");
                imageTextures[i] = texture;
                if (inputs != null) {
                    inputs.append(" img ").append(image.name()).append('=').append(unit).append(":tex").append(name)
                            .append("{wimg=0x").append(Long.toHexString(texture.image())).append(" layout=")
                            .append(texture.foreignLayout()).append('}');
                }
            }
        }
        if (MOD_PROGRAMS_REFUSE_OPAQUE_MAIN && !like.celtium$colorBlend().blend()
                && b.currentFramebufferIdentity == b.targetRegistry.mainIdentity()) {
            return celtium$modFailure(programId, "a blend-off pass into the game's framebuffer and "
                    + "-Dceltium.vulkan.modProgramsOpaqueToMain=false refuses that");
        }
        if (!MOD_PROGRAMS_TO_MAIN && b.currentFramebufferIdentity == b.targetRegistry.mainIdentity()) {
            return celtium$modFailure(programId, "the pass draws into the game's framebuffer and "
                    + "-Dceltium.vulkan.modProgramsToMain=false refuses that");
        }
        long set = pipeline.samplersAndImages(textures, imageTextures, program, b.frameId);
        if ((samplerCount + imageCount > 0 || pipeline.uniformsViaBuffer()) && set == 0L) {
            return celtium$modFailure(programId, "no descriptor set could be allocated");
        }
        String label = null;
        if (inputs != null) {
            long[] targetColour = b.framebufferColorTexture(b.currentFramebufferIdentity);
            GllessFrameTarget pairOwner = b.offTargetTarget();
            inputs.append(" pairGen=").append(pairOwner == null ? -1L : pairOwner.chainPairGenerationTotal());
            String trimmedInputs = inputs.toString().trim();
            celtium$reportModProgram(programId, true, "inputs=[" + trimmedInputs + "] target=0x"
                    + Long.toHexString(b.currentFramebufferIdentity) + " targetColourTex="
                    + (targetColour == null ? -1 : targetColour[0]) + " targetIsMain="
                    + (b.currentFramebufferIdentity == b.targetRegistry.mainIdentity())
                    + " blend=" + like.celtium$colorBlend().blend() + "(" + like.celtium$colorBlend().srcColor() + ","
                    + like.celtium$colorBlend().dstColor() + "," + like.celtium$colorBlend().srcAlpha() + ","
                    + like.celtium$colorBlend().dstAlpha() + ") glBlend=" + b.immediateState.blendSrcRgb() + ","
                    + b.immediateState.blendDstRgb() + " depthTest=" + like.celtium$depthTest()
                    + " depthWrite=" + like.celtium$depthWrite());
            if (MOD_TRACE_FRAMES > 0 || MOD_READBACK) {
                label = "program=" + programId + " offeredFrame=" + b.frameId
                        + " target=0x" + Long.toHexString(b.currentFramebufferIdentity)
                        + " inputs=[" + trimmedInputs + "]";
            }
        }
        return new PreparedImmediate(new VulkanModProgramPipeline.Draw(pipeline,
                VulkanModProgramPipeline.packUniforms(program), set, label, textures, imageTextures), set, 0L);
    }

    /**
     * {@return how many colour targets the framebuffer the program is being prepared for attaches}: the main
     * framebuffer's one, and for a mod's own framebuffer the publication record's count - the same record the
     * window sizes its pass from, so the pipeline and the pass cannot disagree about the target.
     */
    private int celtium$modTargetColors() {
        long identity = b.currentFramebufferIdentity;
        if (identity == b.targetRegistry.mainIdentity()) {
            return 1;
        }
        return Math.max(1, b.publishedTextures.colorAttachmentCount(identity));
    }

    /**
     * {@return why a program with {@code fragOutputs} fragment outputs cannot draw into a target with
     * {@code targetColors} colour attachments}, or null when it can. A program writing fewer outputs than the
     * target attaches can draw - the unwritten attachments keep what the pass loaded - but one writing more has
     * outputs with no attachment, which no pipeline can honour. Static and pure so the rule is pinned without a
     * device.
     */
    static String celtium$mrtRefusal(int fragOutputs, int targetColors) {
        int targets = Math.max(1, targetColors);
        if (fragOutputs > targets) {
            return "program writes " + fragOutputs + " fragment outputs but the target attaches "
                    + targets + " colour target" + (targets == 1 ? "" : "s");
        }
        return null;
    }

    /**
     * The image a mod program samples for a GL texture name, resolved the way {@code prepareImmediateUncached} resolves
     * the unit-0 texture: a framebuffer colour texture is the framebuffer's own image, published under the name, not
     * the CPU mirror the registry may also hold for it (that mirror is empty - measured: leg r19-gt sampled GTCEu's
     * bloom framebuffer through it and composited black over the whole world).
     */
    VulkanGlTexture celtium$modSamplerTexture(int name) {
        VulkanTextureRegistry registry = b.celtium$registry();
        // Depth first: a name carrying a depth record is a framebuffer's depth attachment (a shadow map), and
        // the image it must resolve to is the shared depth store's, published by the depth route. The colour
        // resolution below cannot see that image - it would return the registry's mirror of the GL depth
        // texture, an image nothing ever writes, which is the sampling-publication gap SHADOW-NATIVE-DESIGN
        // section 3.4 names. Colour and depth attachments are different GL names, so a name carries at most
        // one of the two records and the depth record is a safe discriminator.
        if (b.publishedTextures.identityForDepthTexture(name) != Long.MIN_VALUE) {
            VulkanGlTexture publishedDepth = b.publishedTextures.published(name);
            if (publishedDepth != null && b.publishedFrameImages.get(name) != 0L) {
                return publishedDepth;
            }
            if (b.celtium$publishFramebufferDepthTexture(name)) {
                return registry.get(name);
            }
            // Publication refused (the store holds no image for the name yet): fall through to the ordinary
            // resolution rather than inventing an answer. The refusal is censused at the publish site, and the
            // next prepare retries.
        }
        VulkanGlTexture texture = registry.get(name);
        if (texture != null && b.publishedFrameImages.get(name) != 0L) {
            // Already the framebuffer's own image (the wrapper is the published one: r34-gt logged wimg == img for
            // every sampler), so there is nothing to publish again. Not a fix for the black world - r33-gt was black
            // with this in place - only a lookup that does not repeat the publication.
            return texture;
        }
        boolean unpublishedAttachment = texture != null && b.publishedFrameImages.get(name) == 0L
                && b.celtium$framebufferIdentityForColorTexture(name) != Long.MIN_VALUE;
        if (texture == null || !texture.hasFullContent() || unpublishedAttachment) {
            boolean published = b.celtium$publishFramebufferColorTexture(name);
            if (!published && b.celtium$adoptRawFramebufferColorTexture(name)) {
                published = b.celtium$publishFramebufferColorTexture(name);
            }
            if (published) {
                texture = registry.get(name);
            }
        }
        return texture;
    }

    /**
     * {@code -Dceltium.debug.modProgramReadback=true}: once, in the first window from frame 300 that carries a
     * mod-program pass, copy the centre pixel of every image those passes sample into a host buffer before the pass
     * begins, and print the values four frames later. This is what a pass actually reads, measured in the command
     * stream at the point it reads it - the question the GTCEu bloom copy left open (the image is right at present
     * and the pass reads black).
     */
    static final boolean MOD_READBACK = Boolean.getBoolean("celtium.debug.modProgramReadback");
    /** Read barrier before windows that carry a mod pass; default on, {@code -Dceltium.vulkan.modReadBarrier=false}. */
    static final boolean MOD_READ_BARRIER =
            !"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.modReadBarrier", "true"));

    /** {@return whether a queued group for {@code target} is a mod program's draw}. */
    boolean celtium$queuedForTargetHasModProgram(long target) {
        for (int i = 0, n = b.immediateGroups.size(); i < n; i++) {
            ImmediateGroup group = b.immediateGroups.get(i);
            if (group.target == target && group.prepared != null
                    && group.prepared.pipeline instanceof VulkanModProgramPipeline.Draw) {
                return true;
            }
        }
        return false;
    }

    VulkanHostBuffer modReadbackBuffer;
    int modReadbackProbeLines;
    /** Whether the chain-input patch line has been printed this run. */
    boolean chainInputPatchReported;

    long celtium$firstModGroupTarget() {
        for (int i = 0, n = b.immediateGroups.size(); i < n; i++) {
            ImmediateGroup g = b.immediateGroups.get(i);
            if (g.prepared != null && g.prepared.pipeline instanceof VulkanModProgramPipeline.Draw) {
                return g.target;
            }
        }
        return 0L;
    }
    java.util.List<String> modReadbackLabels;
    long modReadbackFrame;

    void celtium$recordModInputReadback(long target) {
        java.util.List<VulkanGlTexture> images = new java.util.ArrayList<>();
        java.util.List<String> labels = new java.util.ArrayList<>();
        for (int i = 0, n = b.immediateGroups.size(); i < n && images.size() < 16; i++) {
            ImmediateGroup group = b.immediateGroups.get(i);
            if (group.target != target || group.prepared == null
                    || !(group.prepared.pipeline instanceof VulkanModProgramPipeline.Draw draw)
                    || draw.pipeline.blends) {
                continue;
            }
            for (VulkanGlTexture input : draw.inputs) {
                if (input != null && input.image() != 0L && images.size() < 16) {
                    images.add(input);
                    labels.add((draw.label == null ? "pass" : draw.label.replaceAll(" inputs=.*", "")) + " input=0x"
                            + Long.toHexString(input.image()) + " " + input.width() + "x" + input.height());
                }
            }
        }
        if (this.modReadbackProbeLines < 12) {
            int modGroups = 0;
            for (int i = 0, n = b.immediateGroups.size(); i < n; i++) {
                ImmediateGroup g = b.immediateGroups.get(i);
                if (g.prepared != null && g.prepared.pipeline instanceof VulkanModProgramPipeline.Draw) {
                    modGroups++;
                }
            }
            if (modGroups > 0) {
                this.modReadbackProbeLines++;
                System.out.println("[Celtium] CELTIUM_MOD_READBACK_PROBE frame=" + b.frameId + " window=0x"
                        + Long.toHexString(target) + " queuedModGroups=" + modGroups + " opaqueInputsHere="
                        + images.size() + " firstModGroupTarget=0x" + Long.toHexString(celtium$firstModGroupTarget()));
            }
        }
        if (images.isEmpty()) {
            return;
        }
        if (this.modReadbackBuffer == null) {
            this.modReadbackBuffer = new VulkanHostBuffer(b.context, 64L,
                    org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT);
        }
        org.lwjgl.system.MemoryUtil.memSet(this.modReadbackBuffer.mappedAddress(), 0x5A, 64L);
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkMemoryBarrier.Buffer before = org.lwjgl.vulkan.VkMemoryBarrier.calloc(1, stack);
            before.get(0).sType$Default()
                    .srcAccessMask(org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                            | org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_READ_BIT);
            org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier(b.commandBuffer,
                    org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                    org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, 0, before, null, null);
            for (int i = 0; i < images.size(); i++) {
                VulkanGlTexture image = images.get(i);
                org.lwjgl.vulkan.VkBufferImageCopy.Buffer region = org.lwjgl.vulkan.VkBufferImageCopy.calloc(1, stack);
                region.get(0).bufferOffset(4L * i).bufferRowLength(0).bufferImageHeight(0);
                region.get(0).imageSubresource().set(org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1);
                region.get(0).imageOffset().set(Math.max(0, image.width() / 2), Math.max(0, image.height() / 2), 0);
                region.get(0).imageExtent().set(1, 1, 1);
                org.lwjgl.vulkan.VK10.vkCmdCopyImageToBuffer(b.commandBuffer, image.image(),
                        org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL, this.modReadbackBuffer.buffer(), region);
            }
            org.lwjgl.vulkan.VkMemoryBarrier.Buffer after = org.lwjgl.vulkan.VkMemoryBarrier.calloc(1, stack);
            after.get(0).sType$Default().srcAccessMask(org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_READ_BIT)
                    .dstAccessMask(org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT
                            | org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
            org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier(b.commandBuffer,
                    org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                    org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, after, null, null);
        }
        this.modReadbackLabels = labels;
        this.modReadbackFrame = b.frameId;
    }

    /**
     * One line per run, at the first window whose queued mod-program passes sample a chain pair image: the mean
     * of the pair over the bloom halo's band, read before the pass begins - the pair as the chain's first input
     * actually sees it.
     * <p>
     * The halo's recorded state is "the mask is empty", which has two gates the end-of-frame picture cannot
     * separate: the terrain pass never routed into the pair (or routed after the chain read it), or the mask was
     * in the pair and the chain still produced no halo. Reading the pair at the moment the first chain pass is
     * about to sample it separates them - empty here is routing/timing, non-empty here moves the question to the
     * chain itself (item 384, from item 343 N3). The band is the one the bloom evidence measures on the presented
     * image (x 300..560, y 230..265 of the 1280x720 world frame), scaled to the pair's own size; readback rows
     * count from the bottom (see {@code TextureCopyService.readImageRectangle}), so the band's y is flipped here.
     */
    void celtium$reportChainInputPatch(long target) {
        if (this.chainInputPatchReported) {
            return;
        }
        GllessFrameTarget pairs = b.offTargetTarget();
        if (pairs == null) {
            return;
        }
        for (int i = 0, n = b.immediateGroups.size(); i < n; i++) {
            ImmediateGroup group = b.immediateGroups.get(i);
            if (group.target != target || group.prepared == null
                    || !(group.prepared.pipeline instanceof VulkanModProgramPipeline.Draw draw)) {
                continue;
            }
            for (VulkanGlTexture input : draw.inputs) {
                if (input == null || input.image() == 0L) {
                    continue;
                }
                for (Long identity : pairs.chainPairIdentities()) {
                    if (pairs.chainPairColorImage(identity.longValue()) != input.image()) {
                        continue;
                    }
                    int width = input.width();
                    int height = input.height();
                    int size = Math.min((560 - 300) * width / 1280, (265 - 230) * height / 720);
                    if (size < 4 || width <= 0 || height <= 0) {
                        return;
                    }
                    int x = (300 + 560) / 2 * width / 1280 - size / 2;
                    int y = height - (230 + 265) / 2 * height / 720 - size / 2;
                    x = Math.max(0, Math.min(x, width - size));
                    y = Math.max(0, Math.min(y, height - size));
                    this.chainInputPatchReported = true;
                    System.out.println("[Celtium] CELTIUM_CHAIN_INPUT_PATCH frame=" + b.frameId
                            + " identity=0x" + Long.toHexString(identity.longValue())
                            + " pairImage=0x" + Long.toHexString(input.image())
                            + " size=" + width + "x" + height
                            + " patch=" + x + "," + y + " " + size + "x" + size
                            + " haloPatch=" + b.postChainBridge.celtium$patchMean(input.image(), x, y, size)
                            + " program=" + (draw.label == null ? "pass"
                                    : draw.label.replaceAll(" inputs=.*", "")));
                    return;
                }
            }
        }
    }

    /** Prints the readback once the frame that recorded it is certainly complete. */
    void celtium$reportModInputReadback() {
        if (this.modReadbackLabels == null || this.modReadbackFrame < 0 || b.frameId < this.modReadbackFrame + 4) {
            return;
        }
        long base = this.modReadbackBuffer.mappedAddress();
        for (int i = 0; i < this.modReadbackLabels.size(); i++) {
            int r = org.lwjgl.system.MemoryUtil.memGetByte(base + 4L * i) & 0xFF;
            int g = org.lwjgl.system.MemoryUtil.memGetByte(base + 4L * i + 1) & 0xFF;
            int b = org.lwjgl.system.MemoryUtil.memGetByte(base + 4L * i + 2) & 0xFF;
            int a = org.lwjgl.system.MemoryUtil.memGetByte(base + 4L * i + 3) & 0xFF;
            System.out.println("[Celtium] CELTIUM_MOD_READBACK recordedFrame=" + this.modReadbackFrame + " "
                    + this.modReadbackLabels.get(i) + " centre=(" + r + "," + g + "," + b + "," + a + ")");
        }
        this.modReadbackFrame = -1L;
    }

    PreparedImmediate celtium$modFailure(int programId, String reason) {
        this.lastModProgramFailure = reason;
        celtium$reportModProgram(programId, false, reason);
        return null;
    }

    /** One line per program and outcome: which programs draw natively, and why the others do not. */
    void celtium$reportModProgram(int program, boolean drawn, String detail) {
        // Keyed by the detail as well: one program is used for several passes (GTCEu's IMAGE_F copies three
        // different framebuffers), and which input each pass read is the question a wrong picture asks.
        if (this.modProgramReported.size() < 64
                && this.modProgramReported.add((drawn ? program : -program - 1) * 31 + detail.hashCode())) {
            System.out.println("[Celtium] CELTIUM_MOD_PROGRAM_" + (drawn ? "DRAWN" : "NOT_DRAWN") + " program=" + program
                    + " " + detail);
        }
    }
}
