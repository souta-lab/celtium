package org.soutalab.celtium.vulkan;

/**
 * A mod's own shader programs drawn natively (opt-in, -Dceltium.vulkan.modPrograms=true): pipeline cache, prepared draws, input readback and the per-program report. Moved out of {@link VulkanTerrainGpuBackend} unchanged (2026-11-06).
 */
final class ModPrograms {
    private final VulkanTerrainGpuBackend b;

    ModPrograms(VulkanTerrainGpuBackend backend) {
        this.b = backend;
    }

    /**
     * Whether a mod's own program draws the batches it can. <b>Opt-in</b> ({@code -Dceltium.vulkan.modPrograms=true}):
     * on the GTCEu scene every bloom pass draws with validation clean, but the passes into the game's framebuffer turn
     * the whole world black (legs r17-r20-gt; r21-gt with {@code modProgramsToMain=false} is PASSED and pixel-normal),
     * so the default keeps refusing until that is understood. See docs/evidence/gregtech-bloom-2026-11-04.md.
     */
    static final boolean MOD_PROGRAMS = Boolean.getBoolean("celtium.vulkan.modPrograms");
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

    /** A mod program pipeline's identity: both SPIR-V arrays and the variant it is built like, by reference. */
    static final class ModProgramKey {
        private final byte[] vertex;
        private final byte[] fragment;
        private final int formatId;
        private final Object like;

        ModProgramKey(byte[] vertex, byte[] fragment, int formatId, Object like) {
            this.vertex = vertex;
            this.fragment = fragment;
            this.formatId = formatId;
            this.like = like;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ModProgramKey k && k.vertex == this.vertex && k.fragment == this.fragment
                    && k.formatId == this.formatId && k.like == this.like;
        }

        @Override
        public int hashCode() {
            return ((System.identityHashCode(this.vertex) * 31 + System.identityHashCode(this.fragment)) * 31
                    + this.formatId) * 31 + System.identityHashCode(this.like);
        }
    }
    final java.util.Set<Integer> modProgramReported = new java.util.HashSet<>();
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
     * batch would have been drawn with), the program's packed uniforms, and its samplers resolved from the texture
     * units their uniforms name. Null, with {@link #lastModProgramFailure}, when any of that is missing.
     */
    PreparedImmediate celtium$modPrepared(org.soutalab.celtium.vulkan.gl.ModProgramRegistry.Program program,
                                                  int programId, int formatId, PreparedImmediate standard) {
        if (!(standard.pipeline instanceof VulkanImmediateTexturePipeline like)) {
            return celtium$modFailure(programId, "the batch's own variant is "
                    + standard.pipeline.getClass().getSimpleName() + ", not a textured one");
        }
        // Keyed by the objects themselves: identity hash codes are not unique, so a key built from them could hand
        // one program's pipeline to another (and, after a world teardown, to a pipeline built like a released one).
        ModProgramKey key = new ModProgramKey(program.vertexSpirv(), program.fragmentSpirv(), formatId, like);
        VulkanModProgramPipeline pipeline = this.modProgramPipelines.get(key);
        if (pipeline == null) {
            try {
                pipeline = VulkanModProgramPipeline.create(b.context, program, formatId, like);
            } catch (RuntimeException | LinkageError failure) {
                return celtium$modFailure(programId, "pipeline: " + failure.getMessage());
            }
            this.modProgramPipelines.put(key, pipeline);
        }
        VulkanGlTexture[] textures = new VulkanGlTexture[pipeline.samplerCount()];
        StringBuilder inputs = new StringBuilder();
        for (int i = 0; i < textures.length; i++) {
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
            if (!MOD_PROGRAMS_SAMPLE_MAIN && b.mainFramebufferColorNames.contains(Integer.valueOf(name))) {
                return celtium$modFailure(programId, "sampler " + sampler.name() + " reads the game's colour and "
                        + "-Dceltium.vulkan.modProgramsSampleMain=false refuses that");
            }
            textures[i] = texture;
            inputs.append(' ').append(sampler.name()).append("=unit").append(unit).append(":tex").append(name)
                    .append(b.mainFramebufferColorNames.contains(Integer.valueOf(name)) ? "(main)" : "")
                    .append(b.publishedFrameImages.get(name) != 0L ? "(fb)" : "")
                    .append("{fbo=0x").append(Long.toHexString(b.celtium$framebufferIdentityForColorTexture(name)))
                    .append(" img=0x").append(Long.toHexString(b.publishedFrameImages.get(name)))
                    .append(" wimg=0x").append(Long.toHexString(texture.image()))
                    .append(" own=").append(texture.celtium$ownsImage())
                    .append(" layout=").append(texture.samplingLayout()).append('}');
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
        long set = pipeline.samplerSet(textures);
        if (textures.length > 0 && set == 0L) {
            return celtium$modFailure(programId, "no sampler set could be allocated");
        }
        long[] targetColour = b.framebufferColorTexture(b.currentFramebufferIdentity);
        GllessFrameTarget pairOwner = b.offTargetTarget();
        inputs.append(" pairGen=").append(pairOwner == null ? -1L : pairOwner.chainPairGenerationTotal());
        celtium$reportModProgram(programId, true, "inputs=[" + inputs.toString().trim() + "] target=0x"
                + Long.toHexString(b.currentFramebufferIdentity) + " targetColourTex="
                + (targetColour == null ? -1 : targetColour[0]) + " targetIsMain="
                + (b.currentFramebufferIdentity == b.targetRegistry.mainIdentity())
                + " blend=" + like.celtium$colorBlend().blend() + "(" + like.celtium$colorBlend().srcColor() + ","
                + like.celtium$colorBlend().dstColor() + "," + like.celtium$colorBlend().srcAlpha() + ","
                + like.celtium$colorBlend().dstAlpha() + ") glBlend=" + b.immediateState.blendSrcRgb() + ","
                + b.immediateState.blendDstRgb() + " depthTest=" + like.celtium$depthTest()
                + " depthWrite=" + like.celtium$depthWrite());
        String label = MOD_TRACE_FRAMES > 0 || MOD_READBACK ? "program=" + programId + " offeredFrame=" + b.frameId
                + " target=0x" + Long.toHexString(b.currentFramebufferIdentity) + " inputs=[" + inputs.toString().trim()
                + "]" : null;
        return new PreparedImmediate(new VulkanModProgramPipeline.Draw(pipeline,
                VulkanModProgramPipeline.packUniforms(program), set, label, textures), set, 0L);
    }

    /**
     * The image a mod program samples for a GL texture name, resolved the way {@code prepareImmediateUncached} resolves
     * the unit-0 texture: a framebuffer colour texture is the framebuffer's own image, published under the name, not
     * the CPU mirror the registry may also hold for it (that mirror is empty - measured: leg r19-gt sampled GTCEu's
     * bloom framebuffer through it and composited black over the whole world).
     */
    VulkanGlTexture celtium$modSamplerTexture(int name) {
        VulkanTextureRegistry registry = b.celtium$registry();
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
            b.context.celtium$beforeBarrier(b.commandBuffer.address());
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
                && this.modProgramReported.add(Integer.valueOf((drawn ? program : -program - 1) * 31 + detail.hashCode()))) {
            System.out.println("[Celtium] CELTIUM_MOD_PROGRAM_" + (drawn ? "DRAWN" : "NOT_DRAWN") + " program=" + program
                    + " " + detail);
        }
    }
}
