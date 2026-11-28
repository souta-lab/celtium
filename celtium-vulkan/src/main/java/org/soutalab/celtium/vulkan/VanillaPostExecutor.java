package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.soutalab.celtium.vulkan.FrameCoordinator.VanillaPostOutcome;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetFormatClass;
import org.soutalab.celtium.vulkan.hal.FrameLinearRing;
import org.soutalab.celtium.vulkan.hal.TransientAttachmentSlab;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.*;

final class VanillaPostExecutor {
    private record Modules(String vertex, String fragment, int samplers, boolean uboVariant) {}
    private static final Map<String, Modules> PROGRAMS = Map.of(
            "invert", new Modules("post_blit.vert", "post_invert.frag", 1, false),
            "blit", new Modules("post_blit.vert", "post_blit.frag", 1, false),
            "blur", new Modules("post_sobel.vert", "post_blur.frag", 1, false),
            "bits", new Modules("post_sobel.vert", "post_bits.frag", 1, false),
            "entity_outline", new Modules("post_sobel.vert", "post_entity_sobel.frag", 1, false),
            "color_convolve", new Modules("post_sobel.vert", "post_color_convolve.frag", 1, true),
            "spider", new Modules("post_rotscale.vert", "post_spiderclip.frag", 2, true));

    private record PipelineKey(String program, int format, int pushBytes) {}
    private record LayoutKey(String program, boolean ubo) {}
    record Target(long image, long view, int width, int height, TargetFormatClass format, int initialLayout) {}
    private record Scratch(Target target, TransientAttachmentSlab slab, TransientAttachmentSlab.Allocation allocation) {}
    record PassTargets(VanillaPostPassSpec spec, Target[] inputs, Target output) {}
    private record Prepared(PassTargets targets, FullscreenPassPipeline pipeline, long descriptorSet, boolean ubo) {}

    static final class Unsupported extends RuntimeException {
        Unsupported(String message) { super(message); }
    }

    private final Map<PipelineKey, FullscreenPassPipeline> pipelines = new HashMap<>();
    private final Map<LayoutKey, SpirvBlock> blocks = new HashMap<>();
    private final Map<Integer, TransientAttachmentSlab> slabs = new HashMap<>();
    private final Set<Scratch> liveScratch = new HashSet<>();
    private long sampler;
    private int maxPushConstants;
    private long uniformAlignment;
    private long runs;
    private long passes;
    private long creationFailures;
    private long unsupportedRefusals;
    private String lastRefusalReason;

    static boolean isBundled(String program) { return program != null && PROGRAMS.containsKey(program); }

    long runs() { return runs; }
    long passes() { return passes; }
    long creationFailures() { return creationFailures; }
    long unsupportedRefusals() { return unsupportedRefusals; }
    String lastRefusalReason() { return lastRefusalReason; }

    VanillaPostOutcome refuseUnsupported(String reason) {
        unsupportedRefusals++;
        lastRefusalReason = reason;
        return VanillaPostOutcome.REFUSED_UNSUPPORTED;
    }

    private VanillaPostOutcome refuseCreation(Throwable failure) {
        creationFailures++;
        lastRefusalReason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        return VanillaPostOutcome.REFUSED_CREATION;
    }

    static boolean usesUniformBuffer(String program, int bytes, int maxPush) {
        if (bytes <= maxPush) return false;
        Modules modules = PROGRAMS.get(program);
        if (modules == null || !modules.uboVariant()) {
            throw new Unsupported("no uniform-buffer variant for " + program + " (" + bytes + " bytes)");
        }
        return true;
    }

    static String uboVariant(String module) {
        int dot = module.lastIndexOf('.');
        return module.substring(0, dot) + "_ubo" + module.substring(dot);
    }

    static String blockRefusal(VanillaPostPassSpec spec, SpirvBlock block) {
        int bytes = spec.pushBytes();
        if (bytes < block.bytes || (bytes % Float.BYTES) != 0 || bytes / Float.BYTES > spec.uniformWordCount()) {
            return "invalid block size for " + spec.program() + " (" + bytes + " bytes)";
        }
        String[] names = spec.blockNames();
        int[] offsets = spec.blockOffsets();
        if (names == null || offsets == null || names.length != offsets.length) return "missing block layout";
        Set<String> supplied = new HashSet<>();
        for (int i = 0; i < names.length; i++) {
            Integer expected = block.offsets.get(names[i]);
            if (expected == null || expected != offsets[i] || !supplied.add(names[i])) {
                return "block member " + names[i] + " at " + offsets[i] + " expected " + expected;
            }
        }
        for (String name : block.offsets.keySet()) {
            if (!supplied.contains(name)) return "missing block member " + name;
        }
        return null;
    }

    VanillaPostOutcome run(VulkanContext context, VkCommandBuffer cmd, int slot, FrameImageTarget frame,
                           RenderTargetRegistry registry, PublishedFrameTextures publications,
                           List<VanillaPostPassSpec> specs) {
        List<Scratch> scratch = new ArrayList<>();
        boolean recording = false;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            List<PassTargets> targets = resolve(context, frame, registry, publications, specs, scratch);
            initialize(context, stack);
            List<Prepared> prepared = new ArrayList<>(specs.size());
            for (PassTargets pass : targets) {
                try (MemoryStack prepareStack = MemoryStack.stackPush()) {
                    prepared.add(prepare(context, slot, pass, prepareStack));
                }
            }
            recording = true;
            Map<Target, Integer> layouts = new HashMap<>();
            try {
                for (Prepared pass : prepared) {
                    try (MemoryStack passStack = MemoryStack.stackPush()) {
                        record(cmd, passStack, pass, layouts);
                        passes++;
                    }
                }
            } finally {
                for (Target target : layouts.keySet()) {
                    int prior = target.initialLayout();
                    if (prior != VK_IMAGE_LAYOUT_UNDEFINED) {
                        try (MemoryStack barrierStack = MemoryStack.stackPush()) {
                            FullscreenPassPipeline.transition(cmd, barrierStack, target.image(), VK_IMAGE_ASPECT_COLOR_BIT,
                                    VK_IMAGE_LAYOUT_GENERAL, prior,
                                    VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT,
                                    VK_ACCESS_2_MEMORY_READ_BIT | VK_ACCESS_2_MEMORY_WRITE_BIT);
                        }
                    }
                }
            }
            runs++;
            lastRefusalReason = null;
            return VanillaPostOutcome.EXECUTED;
        } catch (Unsupported refusal) {
            return refuseUnsupported(refusal.getMessage());
        } catch (RuntimeException | LinkageError failure) {
            return refuseCreation(failure);
        } finally {
            for (Scratch image : scratch) {
                if (recording) context.celtium$retireCleanup("vanilla post scratch", () -> release(context.device(), image));
                else release(context.device(), image);
            }
        }
    }

    private List<PassTargets> resolve(VulkanContext context, FrameImageTarget frame, RenderTargetRegistry registry,
                                      PublishedFrameTextures publications, List<VanillaPostPassSpec> specs,
                                      List<Scratch> scratch) {
        Map<Integer, Target> supplied = new HashMap<>();
        VanillaPostPassSpec first = specs.getFirst();
        TargetFormatClass frameFormat = context.celtium$frameFormatClass();
        TargetFormatClass chainFormat = frameFormat;
        if (first.groupMainFbId() != 0 && first.groupMainFbId() != registry.mainIdentity()) {
            long id = first.groupMainFbId();
            if (!(frame instanceof GllessFrameTarget offTarget)
                    || !offTarget.chainPairMatches(id, first.groupMainW(), first.groupMainH())
                    || !offTarget.chainPairAcquired(id)) {
                throw new Unsupported("unsupplied group input " + id);
            }
            long image = offTarget.chainPairColorImage(id);
            if (publications != null) {
                long[] reported = publications.colorTexture(id);
                if (reported == null || reported[0] != first.groupMainTextureId()) {
                    throw new Unsupported("group input publication changed for " + id);
                }
            }
            chainFormat = offTarget.chainPairFormatClass(id);
            int layout = offTarget.chainPairColorLayout(id);
            if (image == 0 || offTarget.chainPairColorView(id) == 0 || layout == VK_IMAGE_LAYOUT_UNDEFINED) {
                throw new Unsupported("unsupplied group input " + id);
            }
            supplied.put(first.groupMainFbId(), new Target(image, offTarget.chainPairColorView(id),
                    first.groupMainW(), first.groupMainH(), chainFormat, layout));
        }
        if (chainFormat.isDepth() || (chainFormat != frameFormat
                && !VulkanColorFormat.usable(context.physicalDevice(), chainFormat))) {
            throw new Unsupported("incompatible chain format " + chainFormat);
        }
        TargetFormatClass chainFormatForScratch = chainFormat;
        return resolvePasses(specs, supplied, (width, height) -> frameTarget(frame, frameFormat, width, height),
                frame.borrowedPresentImage() != null, (width, height) -> {
                    Scratch allocated = allocateScratch(context, width, height, chainFormatForScratch);
                    scratch.add(allocated);
                    return allocated.target();
                });
    }

    @FunctionalInterface
    interface TargetAtExtent {
        Target resolve(int width, int height);
    }

    static List<PassTargets> resolvePasses(List<VanillaPostPassSpec> specs, Map<Integer, Target> supplied,
                                           TargetAtExtent main, boolean borrowedMain, TargetAtExtent scratch) {
        List<PassTargets> resolved = new ArrayList<>(specs.size());
        for (VanillaPostPassSpec spec : specs) {
            int[] auxIds = spec.auxFbIds();
            int[] auxW = spec.auxW();
            int[] auxH = spec.auxH();
            int samplers = PROGRAMS.get(spec.program()).samplers();
            if (auxIds.length != samplers - 1 || auxW.length != auxIds.length || auxH.length != auxIds.length) {
                throw new Unsupported("unsupported auxiliary inputs for " + spec.program());
            }
            Target[] inputs = new Target[samplers];
            inputs[0] = requireInput(main, borrowedMain, supplied, spec.inIsMain(), spec.inFbId(), spec.inW(), spec.inH());
            for (int i = 0; i < auxIds.length; i++) {
                inputs[i + 1] = requireInput(main, borrowedMain, supplied, auxIds[i] == 0, auxIds[i], auxW[i], auxH[i]);
            }
            Target output;
            if (spec.outIsMain()) {
                output = main.resolve(spec.outW(), spec.outH());
            } else {
                int id = spec.outFbId();
                output = supplied.get(id);
                if (output == null) {
                    output = scratch.resolve(spec.outW(), spec.outH());
                    supplied.put(id, output);
                }
                requireExtent(output, spec.outW(), spec.outH());
            }
            for (Target input : inputs) {
                if (input.image() == output.image()) throw new Unsupported("framebuffer feedback in " + spec.program());
            }
            resolved.add(new PassTargets(spec, inputs, output));
        }
        return resolved;
    }

    private static Target requireInput(TargetAtExtent mainTarget, boolean borrowedMain, Map<Integer, Target> supplied,
                                       boolean main, int id, int width, int height) {
        if (main && borrowedMain) {
            throw new Unsupported("the current present image cannot be sampled");
        }
        Target target = main ? mainTarget.resolve(width, height) : supplied.get(id);
        if (target == null) throw new Unsupported("unsupplied framebuffer input " + id);
        requireExtent(target, width, height);
        return target;
    }

    private static Target frameTarget(FrameImageTarget frame, TargetFormatClass format, int width, int height) {
        if (!frame.matches(width, height) || frame.colorImage() == 0 || frame.colorView() == 0
                || frame.colorImageLayout() == VK_IMAGE_LAYOUT_UNDEFINED) {
            throw new Unsupported("main framebuffer unavailable at " + width + "x" + height);
        }
        return new Target(frame.colorImage(), frame.colorView(), width, height, format, frame.colorImageLayout());
    }

    private static void requireExtent(Target target, int width, int height) {
        if (target.width() != width || target.height() != height) {
            throw new Unsupported("framebuffer extent mismatch at " + width + "x" + height);
        }
    }

    private void initialize(VulkanContext context, MemoryStack stack) {
        if (sampler != 0) return;
        var properties = VkPhysicalDeviceProperties.calloc(stack);
        vkGetPhysicalDeviceProperties(context.physicalDevice(), properties);
        maxPushConstants = properties.limits().maxPushConstantsSize();
        uniformAlignment = Math.max(1, properties.limits().minUniformBufferOffsetAlignment());
        var info = VkSamplerCreateInfo.calloc(stack).sType$Default()
                .magFilter(VK_FILTER_NEAREST).minFilter(VK_FILTER_NEAREST)
                .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE);
        LongBuffer handle = stack.mallocLong(1);
        check(vkCreateSampler(context.device(), info, null, handle), "create sampler");
        sampler = handle.get(0);
    }

    private Prepared prepare(VulkanContext context, int slot, PassTargets targets, MemoryStack stack) {
        VanillaPostPassSpec spec = targets.spec();
        Modules modules = PROGRAMS.get(spec.program());
        boolean ubo = usesUniformBuffer(spec.program(), spec.pushBytes(), maxPushConstants);
        SpirvBlock pushBlock = blocks.computeIfAbsent(new LayoutKey(spec.program(), false),
                key -> mergedBlock(modules.vertex(), modules.fragment()));
        SpirvBlock block = pushBlock;
        if (ubo) {
            block = blocks.computeIfAbsent(new LayoutKey(spec.program(), true),
                    key -> mergedBlock(uboVariant(modules.vertex()), uboVariant(modules.fragment())));
            if (!block.offsets.equals(pushBlock.offsets) || block.bytes != pushBlock.bytes) {
                throw new Unsupported("uniform-buffer variant block differs for " + spec.program());
            }
        }
        String refusal = blockRefusal(spec, block);
        if (refusal != null) throw new Unsupported(refusal);
        int pushBytes = ubo ? 0 : spec.pushBytes();
        int format = targets.output().format().vkFormat();
        PipelineKey key = new PipelineKey(spec.program(), format, pushBytes);
        FullscreenPassPipeline pipeline = pipelines.computeIfAbsent(key, ignored -> new FullscreenPassPipeline(context,
                moduleBytes(ubo ? uboVariant(modules.vertex()) : modules.vertex()),
                moduleBytes(ubo ? uboVariant(modules.fragment()) : modules.fragment()),
                modules.samplers(), pushBytes, ubo, false, format, VK_FORMAT_UNDEFINED,
                VulkanColorBlendState.of(false, VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ZERO, VK_BLEND_FACTOR_ONE,
                        VK_BLEND_FACTOR_ZERO, VK_BLEND_OP_ADD, VK_BLEND_OP_ADD, 0xF),
                false, false, VK_COMPARE_OP_ALWAYS, VulkanCullState.NONE));
        long buffer = 0;
        long offset = 0;
        if (ubo) {
            FrameLinearRing ring = context.celtium$frameLinearRing();
            offset = ring.alloc(slot, spec.pushBytes(), uniformAlignment);
            if (offset < 0) throw new Unsupported("vanilla post uniform ring exhausted");
            buffer = ring.buffer(slot);
            spec.writeUniforms(MemoryUtil.memFloatBuffer(ring.mappedAddress(slot, offset), spec.pushBytes() / Float.BYTES));
        }
        var images = new FullscreenPassPipeline.Image[targets.inputs().length];
        for (int i = 0; i < images.length; i++) {
            images[i] = new FullscreenPassPipeline.Image(targets.inputs()[i].view(), sampler, VK_IMAGE_LAYOUT_GENERAL);
        }
        return new Prepared(targets, pipeline, pipeline.descriptorSet(stack, images, buffer, offset, spec.pushBytes()), ubo);
    }

    private static void record(VkCommandBuffer cmd, MemoryStack stack, Prepared pass, Map<Target, Integer> layouts) {
        for (Target input : pass.targets().inputs()) {
            FullscreenPassPipeline.transition(cmd, stack, input.image(), VK_IMAGE_ASPECT_COLOR_BIT,
                    layouts.getOrDefault(input, input.initialLayout()), VK_IMAGE_LAYOUT_GENERAL,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT, VK_ACCESS_2_SHADER_SAMPLED_READ_BIT);
            layouts.put(input, VK_IMAGE_LAYOUT_GENERAL);
        }
        Target out = pass.targets().output();
        FullscreenPassPipeline.transition(cmd, stack, out.image(), VK_IMAGE_ASPECT_COLOR_BIT,
                layouts.getOrDefault(out, out.initialLayout()),
                VK_IMAGE_LAYOUT_GENERAL, VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT);
        layouts.put(out, VK_IMAGE_LAYOUT_GENERAL);
        var attachments = VkRenderingAttachmentInfo.calloc(1, stack);
        attachments.get(0).sType$Default().imageView(out.view()).imageLayout(VK_IMAGE_LAYOUT_GENERAL)
                .loadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
        var rendering = VkRenderingInfo.calloc(stack).sType$Default().layerCount(1).pColorAttachments(attachments);
        rendering.renderArea().extent().set(out.width(), out.height());
        BackendCallCounters.noteFboSwitch();
        vkCmdBeginRendering(cmd, rendering);
        try {
            FullscreenPassPipeline.viewport(cmd, stack, out.width(), out.height());
            ByteBuffer push = null;
            if (!pass.ubo()) {
                VanillaPostPassSpec spec = pass.targets().spec();
                push = stack.malloc(4, spec.pushBytes());
                spec.writeUniforms(push.asFloatBuffer());
            }
            pass.pipeline().draw(cmd, stack, null, push, pass.descriptorSet());
        } finally {
            vkCmdEndRendering(cmd);
        }
    }

    private Scratch allocateScratch(VulkanContext context, int width, int height, TargetFormatClass format) {
        if (width <= 0 || height <= 0) throw new Unsupported("invalid scratch extent");
        VkDevice device = context.device();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var create = VkImageCreateInfo.calloc(stack).sType$Default().imageType(VK_IMAGE_TYPE_2D)
                    .format(format.vkFormat()).mipLevels(1).arrayLayers(1).samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL).usage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE).initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            create.extent().set(width, height, 1);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateImage(device, create, null, handle), "create scratch image");
            long image = handle.get(0);
            long view = 0;
            TransientAttachmentSlab slab = null;
            TransientAttachmentSlab.Allocation allocation = null;
            try {
                var requirements = VkMemoryRequirements.calloc(stack);
                vkGetImageMemoryRequirements(device, image, requirements);
                int type = context.memoryTypeIndex(requirements.memoryTypeBits(),
                        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, "vanilla post scratch");
                slab = slabs.computeIfAbsent(type, index -> new TransientAttachmentSlab(device, index));
                allocation = slab.acquire(requirements.size(), requirements.alignment());
                check(vkBindImageMemory(device, image, slab.memoryHandle(), allocation.offset()), "bind scratch image");
                var viewInfo = VkImageViewCreateInfo.calloc(stack).sType$Default().image(image)
                        .viewType(VK_IMAGE_VIEW_TYPE_2D).format(format.vkFormat());
                viewInfo.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).levelCount(1).layerCount(1);
                check(vkCreateImageView(device, viewInfo, null, handle), "create scratch image view");
                view = handle.get(0);
                Scratch result = new Scratch(new Target(image, view, width, height, format, VK_IMAGE_LAYOUT_UNDEFINED),
                        slab, allocation);
                liveScratch.add(result);
                return result;
            } catch (RuntimeException | Error failure) {
                if (view != 0) vkDestroyImageView(device, view, null);
                vkDestroyImage(device, image, null);
                if (allocation != null) slab.release(allocation);
                throw failure;
            }
        }
    }

    private void release(VkDevice device, Scratch image) {
        if (!liveScratch.remove(image)) return;
        vkDestroyImageView(device, image.target().view(), null);
        vkDestroyImage(device, image.target().image(), null);
        image.slab().release(image.allocation());
    }

    void destroyIdle(VkDevice device) {
        pipelines.values().forEach(FullscreenPassPipeline::destroyIdle);
        pipelines.clear();
        for (Scratch image : List.copyOf(liveScratch)) release(device, image);
        slabs.values().forEach(TransientAttachmentSlab::close);
        slabs.clear();
        if (sampler != 0) {
            vkDestroySampler(device, sampler, null);
            sampler = 0;
        }
    }

    private static byte[] moduleBytes(String module) {
        ByteBuffer code = SpirvShaders.load(module);
        try {
            byte[] bytes = new byte[code.remaining()];
            code.get(bytes);
            return bytes;
        } finally {
            MemoryUtil.memFree(code);
        }
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) throw new IllegalStateException(operation + " failed with VkResult " + result);
    }
    static final class SpirvBlock {
        /** Opcodes this reader interprets; every other instruction is skipped by its own word count. */
        private static final int OP_MEMBER_NAME = 6;
        private static final int OP_TYPE_INT = 21;
        private static final int OP_TYPE_FLOAT = 22;
        private static final int OP_TYPE_VECTOR = 23;
        private static final int OP_TYPE_MATRIX = 24;
        private static final int OP_TYPE_STRUCT = 30;
        private static final int OP_TYPE_POINTER = 32;
        private static final int OP_VARIABLE = 59;
        private static final int OP_MEMBER_DECORATE = 72;
        private static final int DECORATION_MATRIX_STRIDE = 7;
        private static final int DECORATION_OFFSET = 35;
        private static final int STORAGE_UNIFORM = 2;
        private static final int STORAGE_PUSH_CONSTANT = 9;
        /** The SPIR-V header is five words; instructions start after it. */
        private static final int HEADER_WORDS = 5;

        /** Declared member name -> byte offset, in declaration order. */
        final Map<String, Integer> offsets = new LinkedHashMap<>();
        /** The end of the block: the furthest byte any member occupies. */
        final int bytes;

        private SpirvBlock(Map<String, Integer> offsets, int bytes) {
            this.offsets.putAll(offsets);
            this.bytes = bytes;
        }

        /**
         * {@return the block {@code module} declares}, read from the compiled module the driver is handed.
         */
        static SpirvBlock of(String module) {
            ByteBuffer code = SpirvShaders.load(module);
            try {
                return read(module, code);
            } finally {
                MemoryUtil.memFree(code);
            }
        }

        /**
         * {@return the block {@code code} declares}. Reads the module's words in the buffer's own byte order,
         * so the classpath loader's native-order buffer and a test's little-endian wrapper both work.
         */
        static SpirvBlock read(String module, ByteBuffer code) {
            int words = code.remaining() / 4;
            Map<Long, String> names = new HashMap<>();
            Map<Long, Integer> memberOffsets = new HashMap<>();
            Map<Long, Integer> memberStrides = new HashMap<>();
            Map<Integer, int[]> pointers = new HashMap<>();
            Map<Integer, int[]> structs = new HashMap<>();
            Map<Integer, Integer> scalars = new HashMap<>();
            Map<Integer, int[]> vectors = new HashMap<>();
            Map<Integer, int[]> matrices = new HashMap<>();
            Set<Integer> blockVariables = new LinkedHashSet<>();
            int index = HEADER_WORDS;
            while (index < words) {
                int instruction = code.getInt(index * 4);
                int wordCount = instruction >>> 16;
                int opcode = instruction & 0xFFFF;
                if (wordCount <= 0 || index + wordCount > words) {
                    throw new IllegalStateException("SPIR-V module " + module + " has a malformed instruction at word "
                            + index);
                }
                switch (opcode) {
                    case OP_MEMBER_NAME:
                        names.put(key(code.getInt((index + 1) * 4), code.getInt((index + 2) * 4)),
                                literal(code, index + 3, wordCount - 3));
                        break;
                    case OP_MEMBER_DECORATE: {
                        long member = key(code.getInt((index + 1) * 4), code.getInt((index + 2) * 4));
                        int decoration = code.getInt((index + 3) * 4);
                        if (decoration == DECORATION_OFFSET) {
                            memberOffsets.put(member, Integer.valueOf(code.getInt((index + 4) * 4)));
                        } else if (decoration == DECORATION_MATRIX_STRIDE) {
                            memberStrides.put(member, Integer.valueOf(code.getInt((index + 4) * 4)));
                        }
                        break;
                    }
                    case OP_TYPE_INT:
                    case OP_TYPE_FLOAT: {
                        int width = code.getInt((index + 2) * 4);
                        if (width != 32) {
                            throw new IllegalStateException("SPIR-V module " + module + " declares a " + width
                                    + "-bit scalar; the post chain's blocks are 32-bit");
                        }
                        scalars.put(Integer.valueOf(code.getInt((index + 1) * 4)), Integer.valueOf(width));
                        break;
                    }
                    case OP_TYPE_VECTOR:
                        vectors.put(Integer.valueOf(code.getInt((index + 1) * 4)), new int[] {
                                code.getInt((index + 2) * 4), code.getInt((index + 3) * 4) });
                        break;
                    case OP_TYPE_MATRIX:
                        matrices.put(Integer.valueOf(code.getInt((index + 1) * 4)), new int[] {
                                code.getInt((index + 2) * 4), code.getInt((index + 3) * 4) });
                        break;
                    case OP_TYPE_STRUCT: {
                        int[] members = new int[wordCount - 2];
                        for (int i = 0; i < members.length; ++i) {
                            members[i] = code.getInt((index + 2 + i) * 4);
                        }
                        structs.put(Integer.valueOf(code.getInt((index + 1) * 4)), members);
                        break;
                    }
                    case OP_TYPE_POINTER:
                        pointers.put(Integer.valueOf(code.getInt((index + 1) * 4)), new int[] {
                                code.getInt((index + 2) * 4), code.getInt((index + 3) * 4) });
                        break;
                    case OP_VARIABLE: {
                        int[] pointer = pointers.get(Integer.valueOf(code.getInt((index + 1) * 4)));
                        int storage = code.getInt((index + 3) * 4);
                        if (pointer != null && pointer[0] == storage
                                && (storage == STORAGE_PUSH_CONSTANT || storage == STORAGE_UNIFORM)
                                && structs.containsKey(Integer.valueOf(pointer[1]))) {
                            blockVariables.add(Integer.valueOf(pointer[1]));
                        }
                        break;
                    }
                    default:
                        break;
                }
                index += wordCount;
            }
            if (blockVariables.isEmpty()) {
                throw new IllegalStateException("SPIR-V module " + module
                        + " declares no push-constant or uniform block");
            }
            if (blockVariables.size() > 1) {
                throw new IllegalStateException("SPIR-V module " + module + " declares " + blockVariables.size()
                        + " blocks; the post chain's shaders declare one");
            }
            int block = blockVariables.iterator().next().intValue();
            int[] members = structs.get(Integer.valueOf(block));
            Map<String, Integer> offsets = new LinkedHashMap<>();
            int end = 0;
            for (int member = 0; member < members.length; ++member) {
                String name = names.get(key(block, member));
                Integer offset = memberOffsets.get(key(block, member));
                if (name == null || offset == null) {
                    throw new IllegalStateException("SPIR-V module " + module + " declares block member " + member
                            + " without a name or an offset decoration");
                }
                int size = sizeOf(module, members[member], memberStrides.get(key(block, member)), scalars, vectors,
                        matrices);
                offsets.put(name, offset);
                end = Math.max(end, offset.intValue() + size);
            }
            return new SpirvBlock(offsets, end);
        }

        private static int sizeOf(String module, int type, Integer matrixStride, Map<Integer, Integer> scalars,
                Map<Integer, int[]> vectors, Map<Integer, int[]> matrices) {
            Integer scalar = scalars.get(Integer.valueOf(type));
            if (scalar != null) {
                return scalar.intValue() / 8;
            }
            int[] vector = vectors.get(Integer.valueOf(type));
            if (vector != null) {
                Integer component = scalars.get(Integer.valueOf(vector[0]));
                if (component != null) {
                    return vector[1] * (component.intValue() / 8);
                }
            }
            int[] matrix = matrices.get(Integer.valueOf(type));
            if (matrix != null) {
                int[] column = vectors.get(Integer.valueOf(matrix[0]));
                if (column != null) {
                    Integer component = scalars.get(Integer.valueOf(column[0]));
                    if (component != null) {
                        // The stride between columns is decorated on the member; a matrix whose columns are not
                        // decorated is not a layout this reader will guess at.
                        if (matrixStride == null) {
                            throw new IllegalStateException("SPIR-V module " + module
                                    + " declares a matrix member without a MatrixStride decoration");
                        }
                        return matrix[1] * matrixStride.intValue();
                    }
                }
            }
            throw new IllegalStateException("SPIR-V module " + module + " declares block member type " + type
                    + ", which is neither a 32-bit scalar, a vector of them, nor a matrix of them");
        }

        /** The two-stage merge: the same member at two offsets is a disagreement, not a block. */
        static SpirvBlock merge(SpirvBlock first, SpirvBlock second) {
            Map<String, Integer> offsets = new LinkedHashMap<>(first.offsets);
            for (Map.Entry<String, Integer> member : second.offsets.entrySet()) {
                Integer previous = offsets.put(member.getKey(), member.getValue());
                if (previous != null && !previous.equals(member.getValue())) {
                    throw new IllegalStateException("the two stages declare " + member.getKey() + " at "
                            + previous + "B and " + member.getValue() + "B: one binding holds one block");
                }
            }
            return new SpirvBlock(offsets, Math.max(first.bytes, second.bytes));
        }

        private static long key(int type, int member) {
            return ((long) type << 32) | (member & 0xFFFFFFFFL);
        }

        /** The UTF-8 name a literal string's words carry. */
        private static String literal(ByteBuffer code, int firstWord, int wordCount) {
            byte[] bytes = new byte[wordCount * 4];
            for (int word = 0; word < wordCount; ++word) {
                int value = code.getInt((firstWord + word) * 4);
                bytes[word * 4] = (byte) value;
                bytes[word * 4 + 1] = (byte) (value >>> 8);
                bytes[word * 4 + 2] = (byte) (value >>> 16);
                bytes[word * 4 + 3] = (byte) (value >>> 24);
            }
            int end = 0;
            while (end < bytes.length && bytes[end] != 0) {
                ++end;
            }
            return new String(bytes, 0, end, StandardCharsets.UTF_8);
        }
    }

    /** {@return the block two shader modules declare together, or throws when they disagree}. */
    static SpirvBlock mergedBlock(String vertex, String fragment) {
        return SpirvBlock.merge(SpirvBlock.of(vertex), SpirvBlock.of(fragment));
    }
}
