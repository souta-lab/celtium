package org.soutalab.celtium.vulkan;

import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.impl.CompactChunkVertex;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.Locale;

import static org.lwjgl.vulkan.VK10.*;

/**
 * The Vulkan graphics pipeline for exactly one {@link TerrainRenderPass}.
 * <p>
 * One instance is created per terrain render pass and kept by the backend in a map; nothing is cached statically,
 * because pass configuration (blending, culling, shader defines) is baked into the pipeline object and differs
 * between passes.
 * <p>
 * <b>Shader contract.</b> The shaders written for this backend declare five vertex inputs (locations 0..4, the
 * {@code a_PosId} / {@code a_Color} / {@code a_TexCoord} / {@code a_LightCoord} / {@code a_RdhFactor} attributes of
 * the compact 24 byte terrain vertex), a push constant block of two {@code mat4} ({@code u_ProjectionMatrix} then
 * {@code u_ModelViewMatrix}, 128 bytes, vertex stage only) and a descriptor set 0 with binding 0 = uniform buffer
 * (vertex + fragment), binding 1 = block atlas, binding 2 = lightmap. This class bakes exactly that contract.
 * <p>
 * All passes currently use the {@code SpirvShaders.SOLID_*} pair, which is the only terrain shader named in the
 * published contract; the pass specific behaviour that the GL path gets from its shader defines (alpha test on the
 * cutout passes) has to come from that shader. If per pass shader variants are added later, the stage selection in
 * {@code createPipeline} is the single place to change.
 * <p>
 * <b>API surface used from the sibling classes in this package.</b> Every call this class makes on them is listed
 * here so a signature change is easy to reconcile:
 * <ul>
 *   <li>{@code VulkanContext.device()} - the {@code VkDevice} for every object created here.</li>
 *   <li>{@code VulkanContext.physicalDevice()} - queried for {@code minUniformBufferOffsetAlignment},
 *       {@code maxPushConstantsSize} and vertex format support before anything is created.</li>
 *   <li>{@code VulkanGlTexture.view()} and {@code VulkanGlTexture.sampler()} - the two {@code long} handles written
 *       into the descriptor set by {@link #bindTextures}.</li>
 *   <li>{@code VulkanBuffer.buffer()} and {@code VulkanBuffer.size()} - the uniform buffer handle and its size, used
 *       for the descriptor write and to validate the dynamic offset at bind time.</li>
 *   <li>{@code SpirvShaders.load(String)} with {@code SpirvShaders.SOLID_VERTEX} / {@code SpirvShaders.SOLID_FRAGMENT}
 *       - the SPIR-V for the two stages. The returned buffer is only read and is never freed here, because the
 *       ownership of that buffer is not part of the published contract.</li>
 * </ul>
 * {@code VulkanContext.queue()}, {@code queueFamilyIndex()}, {@code beginCommands()}, {@code endCommands(...)} and
 * {@code uploadToBuffer(...)} are deliberately unused: this class allocates no device memory and submits no work.
 * <p>
 * <b>Render pass ownership.</b> The pipeline creates its own {@code VkRenderPass} because pipeline creation needs
 * one. It is exposed through {@link #renderPass()} so the backend can record {@code vkCmdBeginRenderPass} with it.
 * A render pass the backend creates itself is compatible as long as it uses the same attachment formats, sample
 * counts, load/store operations and layouts - the exact description is documented on {@link #COLOR_ATTACHMENT_LAYOUT}
 * and the constants next to it. Both attachments are kept in {@code VK_IMAGE_LAYOUT_GENERAL}, which is what the
 * validated GL/Vulkan interop path in this project uses for images that alternate between the two APIs.
 * <p>
 * All methods must be called from the render thread. The class is not thread safe.
 */
public final class VulkanTerrainPipeline {
    /**
     * The terrain color attachment format. The frame target this pipeline draws into shares its image with
     * OpenGL, and R8G8B8A8_UNORM is the format both APIs agree on (it is also the format the interop bootstrap
     * verifies as importable).
     */
    public static final int COLOR_FORMAT = VK_FORMAT_R8G8B8A8_UNORM;

    /**
     * The terrain depth attachment format, matching the depth image of the interop frame target.
     */
    public static final int DEPTH_FORMAT = VK_FORMAT_D32_SFLOAT;

    /**
     * The layout the color attachment is expected to be in when the render pass begins, and the layout it is left
     * in when the render pass ends.
     * <p>
     * {@code VK_IMAGE_LAYOUT_GENERAL} is not the fastest color attachment layout, but the image is an external
     * image shared with OpenGL: GL has no equivalent of Vulkan layouts, so the image cannot be left in an
     * attachment-optimal layout across an ownership handoff. Keeping it in GENERAL makes the acquire barrier a
     * plain GENERAL to GENERAL transition and matches the barrier code already validated for this project.
     */
    public static final int COLOR_ATTACHMENT_LAYOUT = VK_IMAGE_LAYOUT_GENERAL;

    /**
     * The layout the depth attachment is expected to be in when the render pass begins, and the layout it is left
     * in when the render pass ends. See {@link #COLOR_ATTACHMENT_LAYOUT} for why this is GENERAL.
     */
    public static final int DEPTH_ATTACHMENT_LAYOUT = VK_IMAGE_LAYOUT_GENERAL;

    /**
     * The descriptor range used for the dynamic uniform buffer at binding 0, and therefore the size of one uniform
     * block the shader may read.
     * <p>
     * A dynamic uniform buffer binding cannot use {@code VK_WHOLE_SIZE}: the effective range is
     * {@code dynamicOffset .. dynamicOffset + range} and validation rejects anything that reaches past the end of
     * the buffer. Since {@link #bindTextures} does not receive a block size, the block size is fixed here: every
     * uniform block in the buffer passed to {@code bindTextures} must be exactly this many bytes (or the caller
     * must ensure that {@code offset + UNIFORM_BLOCK_SIZE <= buffer size} for every offset it passes to
     * {@link #bindDescriptors}). 256 bytes covers the region offset and fog parameters with room to spare, and it
     * is a multiple of the {@code minUniformBufferOffsetAlignment} values desktop drivers report (1, 16, 64, 128,
     * 256), so a 256 byte stride always produces legal dynamic offsets.
     */
    public static final int UNIFORM_BLOCK_SIZE = 256;

    /**
     * The bytes of push constant data this pipeline layout declares: two column-major {@code mat4}.
     */
    public static final int PUSH_CONSTANT_BYTES = 128;

    private static final int MATRIX_FLOATS = 16;

    /**
     * The compact terrain vertex format ({@link CompactChunkVertex}) transcribed to Vulkan input formats in
     * attribute location order. The offsets mirror the GL vertex format element offsets exactly.
     * <p>
     * The packed attributes ({@code a_PosId}, {@code a_TexCoord}, {@code a_LightCoord}) are integer formats and
     * deliberately <em>not</em> normalized: the shader decodes the bit fields itself with integer math, so a
     * normalized float conversion in the input assembler would destroy the packing. {@code a_Color} and
     * {@code a_RdhFactor} are normalized because the GL vertex format marks them normalized too.
     */
    private static final int[] VERTEX_ATTRIBUTE_FORMATS = {
            VK_FORMAT_R16G16B16A16_UINT, // 0: a_PosId     4 x unsigned short, raw
            VK_FORMAT_R8G8B8A8_UNORM,    // 1: a_Color     4 x unsigned byte, normalized
            VK_FORMAT_R16G16_UINT,       // 2: a_TexCoord  2 x unsigned short, raw
            VK_FORMAT_R32_UINT,          // 3: a_LightCoord 1 x unsigned int, raw
            VK_FORMAT_R8G8B8A8_SNORM,    // 4: a_RdhFactor 4 x signed byte, normalized
    };

    private static final int[] VERTEX_ATTRIBUTE_OFFSETS = { 0, 8, 12, 16, 20 };

    private static final int UNIFORM_BINDING = 0;
    private static final int BLOCK_ATLAS_BINDING = 1;
    private static final int LIGHTMAP_BINDING = 2;

    private final VulkanContext context;
    private final String passName;
    private final boolean blended;
    private final boolean blockFaceCulling;
    private final boolean hasNoLightmap;
    private final long minUniformBufferOffsetAlignment;

    private long renderPass;
    private long descriptorSetLayout;
    private long layout;
    private long descriptorPool;
    private long descriptorSet;
    private long pipeline;

    private long uniformsSize;
    private int uniformsRange;
    /** True once {@link #bindTextures} has written a complete descriptor set; until then binding it is invalid. */
    private boolean descriptorsWritten;
    private boolean deleted;

    private VulkanTerrainPipeline(VulkanContext context, TerrainRenderPass pass, boolean blockFaceCulling) {
        this.context = context;
        this.passName = pass.name();
        this.blended = needsBlending(pass);
        this.blockFaceCulling = blockFaceCulling;
        this.hasNoLightmap = pass.hasNoLightmap();
        this.minUniformBufferOffsetAlignment = queryLimits(context);
    }

    /**
     * Creates the pipeline for one terrain render pass.
     *
     * @param context          the live Vulkan device wrapper
     * @param pass             the render pass this pipeline is built for; its name selects the blending rule and its
     *                         {@code hasNoLightmap()} flag is remembered for {@link #bindTextures}
     * @param blockFaceCulling {@code true} to cull back faces (the vanilla behaviour for terrain), {@code false} to
     *                         draw both faces
     * @return a fully created pipeline; delete it with {@link #delete()}
     * @throws IllegalArgumentException if the pass cannot be rendered by a triangle list pipeline
     * @throws IllegalStateException    if the device lacks a limit this pipeline depends on, or if Vulkan object
     *                                  creation fails
     */
    public static VulkanTerrainPipeline create(VulkanContext context, TerrainRenderPass pass,
                                               boolean blockFaceCulling) {
        if (context == null) {
            throw new NullPointerException("context");
        }
        if (pass == null) {
            throw new NullPointerException("pass");
        }
        // A quad list pass would need GL_QUADS, which Vulkan core does not have. Rejecting it here is much easier
        // to diagnose than the garbage geometry a triangle list draw of quad indices would produce.
        if (pass.primitiveType() == QuadPrimitiveType.DIRECT) {
            throw new IllegalArgumentException("Terrain pass '" + pass.name() + "' uses direct quads, which a"
                    + " triangle list Vulkan pipeline cannot draw; use a triangulated pass");
        }
        VulkanTerrainPipeline result = new VulkanTerrainPipeline(context, pass, blockFaceCulling);
        try {
            result.createResources();
            return result;
        } catch (RuntimeException | LinkageError error) {
            // Release whatever was already created so a failed create() cannot leak device objects.
            result.delete();
            throw error;
        }
    }

    /**
     * Whether a pass needs alpha blending.
     * <p>
     * {@link TerrainRenderPass} carries no blend flag, and the GL pipeline state callback it does carry is opaque
     * ({@code PipelineState.setup()} touches the GL texture manager, not blending). Inspecting the vanilla pass
     * configuration leaves exactly one signal: the pass name. The vanilla terrain layers are "solid", "cutout",
     * "cutout_mipped" and "translucent", and only the translucent layer blends - the cutout layers use an alpha
     * test (a fragment discard in the shader) while writing depth, and solid does neither. A custom pass is
     * therefore blended if its name contains "translucent" (case insensitive), and the backend can reuse this
     * predicate to decide whether it has to sort the pass.
     *
     * @param pass the render pass to classify
     * @return {@code true} if the pipeline for this pass must enable alpha blending
     */
    public static boolean needsBlending(TerrainRenderPass pass) {
        return pass.name().toLowerCase(Locale.ROOT).contains("translucent");
    }

    /**
     * The {@code VkRenderPass} this pipeline was created against.
     * <p>
     * Record {@code vkCmdBeginRenderPass} with this handle, or with a render pass whose attachments have the same
     * formats, sample counts, load/store operations and layouts ({@link #COLOR_ATTACHMENT_LAYOUT} and
     * {@link #DEPTH_ATTACHMENT_LAYOUT}). Subpass 0 is the only subpass.
     *
     * @return the native render pass handle, valid until {@link #delete()}
     */
    public long renderPass() {
        return renderPass;
    }

    /**
     * The {@code VkPipelineLayout}, e.g. for the backend's own {@code vkCmdPushConstants} or descriptor binds.
     *
     * @return the native pipeline layout handle, valid until {@link #delete()}
     */
    public long layout() {
        return layout;
    }

    /**
     * The minimum alignment the dynamic uniform buffer offset passed to {@link #bindDescriptors} must satisfy.
     * Exposed so the backend can size and align its uniform blocks correctly without querying the device again.
     *
     * @return {@code VkPhysicalDeviceLimits::minUniformBufferOffsetAlignment}
     */
    public long minUniformBufferAlignment() {
        return minUniformBufferOffsetAlignment;
    }

    /**
     * Binds this pipeline to the graphics bind point of a command buffer.
     *
     * @param commandBuffer the command buffer being recorded inside the render pass
     */
    public void bindPipeline(VkCommandBuffer commandBuffer) {
        requireAlive();
        if (commandBuffer == null) {
            throw new NullPointerException("commandBuffer");
        }
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
    }

    /**
     * Fills the descriptor set of this pipeline with the two terrain textures and the uniform buffer.
     * <p>
     * This is not a per-draw call: it writes set 0 and therefore only has to run when a texture or the uniform
     * buffer object changes (resource reload, first use, arena growth). The images are expected to be in
     * {@code VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL}, which is the resting layout {@link VulkanGlTexture}
     * maintains. The uniform buffer must stay alive until {@link #delete()}, because the native handle is stored in
     * the descriptor set.
     * <p>
     * The uniform buffer is bound as {@code VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC}: the descriptor stores the
     * buffer and a {@link #UNIFORM_BLOCK_SIZE} byte range, and every draw supplies its block offset through
     * {@link #bindDescriptors}.
     *
     * @param atlas    the block atlas image; required
     * @param lightmap the lightmap image; may be {@code null} only for a pass whose {@code hasNoLightmap()} is true,
     *                 in which case the atlas is bound to binding 2 as a placeholder so the set stays valid
     * @param uniformBuffer the uniform buffer handle holding region offset and fog parameters, one block per
     *                      section; the caller owns it and must keep it alive while the pipeline draws
     * @param uniformBufferSize the size of that buffer in bytes
     * @throws NullPointerException     if {@code atlas} or {@code uniforms} is {@code null}
     * @throws IllegalArgumentException if the uniform buffer has no storage
     * @throws IllegalStateException    if this pass samples the lightmap but no lightmap was supplied
     */
    public void bindTextures(VulkanGlTexture atlas, VulkanGlTexture lightmap, long uniformBuffer, long uniformBufferSize) {
        requireAlive();
        if (atlas == null) {
            throw new NullPointerException("atlas");
        }
        if (uniformBuffer == 0L) {
            throw new IllegalArgumentException("The uniform buffer handle is 0");
        }
        if (lightmap == null) {
            if (!hasNoLightmap) {
                throw new IllegalStateException("Terrain pass '" + passName + "' samples the lightmap,"
                        + " so bindTextures must receive a non-null lightmap");
            }
            // The shader variant for a pass without a lightmap never samples binding 2, but the descriptor must
            // still hold a valid image view and sampler, otherwise the set is invalid.
            lightmap = atlas;
        }
        long buffer = uniformBuffer;
        long size = uniformBufferSize;
        if (buffer == 0L) {
            throw new IllegalArgumentException("The uniform buffer has no VkBuffer; it was deleted or never written");
        }
        if (size <= 0L) {
            throw new IllegalArgumentException("The uniform buffer has no storage");
        }

        this.uniformsSize = size;
        this.uniformsRange = (int) Math.min(size, (long) UNIFORM_BLOCK_SIZE);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack);
            bufferInfo.get(0).buffer(buffer).offset(0L).range(uniformsRange);

            VkDescriptorImageInfo.Buffer atlasInfo = VkDescriptorImageInfo.calloc(1, stack);
            atlasInfo.get(0).sampler(atlas.sampler()).imageView(atlas.view())
                    .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            VkDescriptorImageInfo.Buffer lightmapInfo = VkDescriptorImageInfo.calloc(1, stack);
            lightmapInfo.get(0).sampler(lightmap.sampler()).imageView(lightmap.view())
                    .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(3, stack);
            writes.get(0).sType$Default().dstSet(descriptorSet).dstBinding(UNIFORM_BINDING).dstArrayElement(0)
                    .descriptorCount(1).descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC)
                    .pBufferInfo(bufferInfo);
            writes.get(1).sType$Default().dstSet(descriptorSet).dstBinding(BLOCK_ATLAS_BINDING).dstArrayElement(0)
                    .descriptorCount(1).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .pImageInfo(atlasInfo);
            writes.get(2).sType$Default().dstSet(descriptorSet).dstBinding(LIGHTMAP_BINDING).dstArrayElement(0)
                    .descriptorCount(1).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .pImageInfo(lightmapInfo);
            vkUpdateDescriptorSets(context.device(), writes, null);
        }
        descriptorsWritten = true;
    }

    /**
     * Binds the descriptor set of this pipeline with the dynamic uniform buffer offset of the current draw.
     *
     * @param commandBuffer      the command buffer being recorded inside the render pass
     * @param uniformBufferOffset byte offset of this draw's uniform block inside the buffer given to
     *                           {@link #bindTextures}; must be a multiple of {@link #minUniformBufferAlignment()}
     *                           and must leave {@link #UNIFORM_BLOCK_SIZE} bytes inside the buffer
     * @throws IllegalStateException if {@link #bindTextures} has not been called yet, or if the offset violates the
     *                               alignment or range rules (fail here instead of letting the validation layers or
     *                               the GPU produce undefined fog and region offsets)
     */
    public void bindDescriptors(VkCommandBuffer commandBuffer, long uniformBufferOffset) {
        requireAlive();
        if (commandBuffer == null) {
            throw new NullPointerException("commandBuffer");
        }
        if (!descriptorsWritten) {
            throw new IllegalStateException("bindDescriptors was called before bindTextures for terrain pass '"
                    + passName + "'; the descriptor set holds no valid descriptors yet");
        }
        if (uniformBufferOffset < 0L) {
            throw new IllegalArgumentException("Negative uniform buffer offset " + uniformBufferOffset);
        }
        if (uniformBufferOffset > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("Uniform buffer offset " + uniformBufferOffset
                    + " does not fit in the 32 bit dynamic offset");
        }
        if (minUniformBufferOffsetAlignment > 0L
                && uniformBufferOffset % minUniformBufferOffsetAlignment != 0L) {
            throw new IllegalStateException("Uniform buffer offset " + uniformBufferOffset + " for terrain pass '"
                    + passName + "' is not a multiple of minUniformBufferOffsetAlignment "
                    + minUniformBufferOffsetAlignment);
        }
        if (uniformBufferOffset + uniformsRange > uniformsSize) {
            throw new IllegalStateException("Uniform buffer offset " + uniformBufferOffset + " with range "
                    + uniformsRange + " reaches past the " + uniformsSize + " byte buffer of terrain pass '"
                    + passName + "'; the uniform blocks of this buffer must be " + UNIFORM_BLOCK_SIZE + " bytes");
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, layout, 0,
                    stack.longs(descriptorSet), stack.ints((int) uniformBufferOffset));
        }
    }

    /**
     * Uploads the 128 byte push constant block of the terrain shaders: the projection matrix followed by the
     * model view matrix, both column-major, which is the memory layout {@code mat4} has in GLSL.
     *
     * @param commandBuffer the command buffer being recorded inside the render pass
     * @param projection    the projection matrix, written first
     * @param modelView     the model view matrix, written second
     */
    public void pushMatrices(VkCommandBuffer commandBuffer, Matrix4fc projection, Matrix4fc modelView) {
        requireAlive();
        if (commandBuffer == null) {
            throw new NullPointerException("commandBuffer");
        }
        if (projection == null) {
            throw new NullPointerException("projection");
        }
        if (modelView == null) {
            throw new NullPointerException("modelView");
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Float indices are used on purpose: JOML's ByteBuffer overloads take byte indices, and mixing the two
            // is a silent off-by-4 error. 32 floats are 128 bytes.
            FloatBuffer block = stack.mallocFloat(2 * MATRIX_FLOATS);
            projection.get(0, block);
            modelView.get(MATRIX_FLOATS, block);
            vkCmdPushConstants(commandBuffer, layout, VK_SHADER_STAGE_VERTEX_BIT, 0, block);
        }
    }

    /**
     * Destroys every device object this pipeline owns: the pipeline, the descriptor pool (which frees the
     * descriptor set allocated from it), the descriptor set layout, the pipeline layout and the render pass.
     * <p>
     * Idempotent: calling it twice, or after a partially failed {@link #create}, is safe. The caller must ensure
     * the device is idle with respect to this pipeline before deleting it.
     */
    public void delete() {
        if (deleted) {
            return;
        }
        deleted = true;
        VkDevice device = context.device();
        if (pipeline != 0L) {
            vkDestroyPipeline(device, pipeline, null);
            pipeline = 0L;
        }
        if (descriptorPool != 0L) {
            // Destroying the pool also frees the descriptor set allocated from it.
            vkDestroyDescriptorPool(device, descriptorPool, null);
            descriptorPool = 0L;
            descriptorSet = 0L;
        }
        if (descriptorSetLayout != 0L) {
            vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
            descriptorSetLayout = 0L;
        }
        if (layout != 0L) {
            vkDestroyPipelineLayout(device, layout, null);
            layout = 0L;
        }
        if (renderPass != 0L) {
            vkDestroyRenderPass(device, renderPass, null);
            renderPass = 0L;
        }
        uniformsSize = 0L;
        uniformsRange = 0;
        descriptorsWritten = false;
    }

    private void createResources() {
        VkDevice device = context.device();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(context.physicalDevice(), properties);
            int maxPushConstants = properties.limits().maxPushConstantsSize();
            if (maxPushConstants < PUSH_CONSTANT_BYTES) {
                throw new IllegalStateException("The device allows only " + maxPushConstants
                        + " bytes of push constants but the terrain shaders need " + PUSH_CONSTANT_BYTES);
            }
            if (properties.limits().maxUniformBufferRange() < UNIFORM_BLOCK_SIZE) {
                throw new IllegalStateException("The device allows only "
                        + properties.limits().maxUniformBufferRange()
                        + " bytes per uniform range but the terrain uniform block is " + UNIFORM_BLOCK_SIZE);
            }
            requireVertexFormatSupport(stack);

            createRenderPass(stack, device);
            createDescriptorSetLayout(stack, device);
            createPipelineLayout(stack, device);
            createPipeline(stack, device);
            createDescriptorPool(stack, device);
        }
    }

    /**
     * Verifies that the input assembler can read the compact vertex format before a pipeline is created from it; a
     * clear message here is worth more than an unexplained pipeline creation failure.
     */
    private void requireVertexFormatSupport(MemoryStack stack) {
        VkFormatProperties features = VkFormatProperties.calloc(stack);
        for (int i = 0; i < VERTEX_ATTRIBUTE_FORMATS.length; i++) {
            int format = VERTEX_ATTRIBUTE_FORMATS[i];
            vkGetPhysicalDeviceFormatProperties(context.physicalDevice(), format, features);
            if ((features.bufferFeatures() & VK_FORMAT_FEATURE_VERTEX_BUFFER_BIT) == 0) {
                throw new IllegalStateException("Vertex attribute " + i + " of the terrain format uses Vulkan"
                        + " format " + format + ", which this device cannot use as a vertex buffer format");
            }
        }
    }

    private void createRenderPass(MemoryStack stack, VkDevice device) {
        VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(2, stack);
        VkAttachmentDescription color = attachments.get(0);
        color.format(COLOR_FORMAT)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                // LOAD, not CLEAR: the frame target is shared with OpenGL, which has already cleared it and may
                // already have drawn into it, so clearing here would erase that work.
                .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(COLOR_ATTACHMENT_LAYOUT)
                .finalLayout(COLOR_ATTACHMENT_LAYOUT);
        VkAttachmentDescription depth = attachments.get(1);
        depth.format(DEPTH_FORMAT)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                // D32_SFLOAT has no stencil aspect, so the stencil operations are irrelevant.
                .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(DEPTH_ATTACHMENT_LAYOUT)
                .finalLayout(DEPTH_ATTACHMENT_LAYOUT);

        VkAttachmentReference.Buffer colorReferences = VkAttachmentReference.calloc(1, stack);
        colorReferences.get(0).attachment(0).layout(COLOR_ATTACHMENT_LAYOUT);
        VkAttachmentReference depthReference = VkAttachmentReference.calloc(stack)
                .attachment(1).layout(DEPTH_ATTACHMENT_LAYOUT);
        VkSubpassDescription.Buffer subpasses = VkSubpassDescription.calloc(1, stack);
        subpasses.get(0).pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                .colorAttachmentCount(1)
                .pColorAttachments(colorReferences)
                .pDepthStencilAttachment(depthReference);

        // The implicit external dependencies of a render pass carry no access masks, so the load operations would
        // not see earlier writes to the shared images and the release barrier would have nothing to source from.
        // Declaring them explicitly makes the render pass self contained: everything written before it (by Vulkan
        // or by the GL side of the handoff) is available to the loads, and the terrain writes are made available
        // to whatever runs afterwards.
        VkSubpassDependency.Buffer dependencies = VkSubpassDependency.calloc(2, stack);
        dependencies.get(0)
                .srcSubpass(VK_SUBPASS_EXTERNAL)
                .dstSubpass(0)
                .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                        | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT)
                .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                        | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
                .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                        | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
                .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                        | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                        | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
                .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);
        dependencies.get(1)
                .srcSubpass(0)
                .dstSubpass(VK_SUBPASS_EXTERNAL)
                .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                        | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT)
                // BOTTOM_OF_PIPE with no destination access only makes the writes available; visibility is the
                // job of the release barrier that hands the images back to OpenGL.
                .dstStageMask(VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
                .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                        | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
                .dstAccessMask(0)
                .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);

        VkRenderPassCreateInfo create = VkRenderPassCreateInfo.calloc(stack).sType$Default()
                .pAttachments(attachments)
                .pSubpasses(subpasses)
                .pDependencies(dependencies);
        LongBuffer handle = stack.mallocLong(1);
        check(vkCreateRenderPass(device, create, null, handle), "vkCreateRenderPass");
        renderPass = handle.get(0);
    }

    private void createDescriptorSetLayout(MemoryStack stack, VkDevice device) {
        // The stage flags are deliberately permissive: a descriptor that is statically used by a stage which is not
        // listed in its stageFlags makes the set invalid, while listing a stage that does not use it is harmless.
        // The shader sources are authored in parallel with this class, so permissiveness is the safe choice.
        int stages = VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT;
        VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(3, stack);
        bindings.get(0).binding(UNIFORM_BINDING)
                .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC)
                .descriptorCount(1)
                .stageFlags(stages);
        bindings.get(1).binding(BLOCK_ATLAS_BINDING)
                .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .stageFlags(stages);
        bindings.get(2).binding(LIGHTMAP_BINDING)
                .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .stageFlags(stages);
        VkDescriptorSetLayoutCreateInfo create = VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default()
                .pBindings(bindings);
        LongBuffer handle = stack.mallocLong(1);
        check(vkCreateDescriptorSetLayout(device, create, null, handle), "vkCreateDescriptorSetLayout");
        descriptorSetLayout = handle.get(0);
    }

    private void createPipelineLayout(MemoryStack stack, VkDevice device) {
        VkPushConstantRange.Buffer range = VkPushConstantRange.calloc(1, stack);
        range.get(0).stageFlags(VK_SHADER_STAGE_VERTEX_BIT).offset(0).size(PUSH_CONSTANT_BYTES);
        VkPipelineLayoutCreateInfo create = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
                .pSetLayouts(stack.longs(descriptorSetLayout))
                .pPushConstantRanges(range);
        LongBuffer handle = stack.mallocLong(1);
        check(vkCreatePipelineLayout(device, create, null, handle), "vkCreatePipelineLayout");
        layout = handle.get(0);
    }

    private void createPipeline(MemoryStack stack, VkDevice device) {
        long vertexModule = 0L;
        long fragmentModule = 0L;
        try {
            vertexModule = createShaderModule(stack, device, SpirvShaders.load(SpirvShaders.SOLID_VERTEX), "vertex");
            fragmentModule = createShaderModule(stack, device, SpirvShaders.load(SpirvShaders.SOLID_FRAGMENT),
                    "fragment");
            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            stages.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertexModule)
                    .pName(stack.UTF8("main"));
            stages.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragmentModule)
                    .pName(stack.UTF8("main"));

            VkVertexInputBindingDescription.Buffer binding = VkVertexInputBindingDescription.calloc(1, stack);
            binding.get(0).binding(0).stride(CompactChunkVertex.STRIDE).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
            VkVertexInputAttributeDescription.Buffer attributes =
                    VkVertexInputAttributeDescription.calloc(VERTEX_ATTRIBUTE_FORMATS.length, stack);
            for (int i = 0; i < VERTEX_ATTRIBUTE_FORMATS.length; i++) {
                attributes.get(i).location(i).binding(0)
                        .format(VERTEX_ATTRIBUTE_FORMATS[i])
                        .offset(VERTEX_ATTRIBUTE_OFFSETS[i]);
            }
            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .pVertexBindingDescriptions(binding)
                    .pVertexAttributeDescriptions(attributes);
            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                    .primitiveRestartEnable(false);
            // Viewport and scissor are dynamic, so the counts are declared but no static values are needed.
            VkPipelineViewportStateCreateInfo viewport = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType$Default().viewportCount(1).scissorCount(1);
            VkPipelineRasterizationStateCreateInfo rasterization = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .depthClampEnable(false)
                    .rasterizerDiscardEnable(false)
                    .polygonMode(VK_POLYGON_MODE_FILL)
                    // The compact vertex format and the chunk mesher emit counter-clockwise front faces, the same
                    // winding the OpenGL terrain path uses.
                    .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                    .cullMode(blockFaceCulling ? VK_CULL_MODE_BACK_BIT : VK_CULL_MODE_NONE)
                    .depthBiasEnable(false)
                    .lineWidth(1.0f);
            VkPipelineMultisampleStateCreateInfo multisample = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)
                    .sampleShadingEnable(false)
                    .alphaToCoverageEnable(false)
                    .alphaToOneEnable(false);
            VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .depthTestEnable(true)
                    // Depth writes stay enabled for every pass, including translucent, matching the pass contract
                    // used by this backend; the vanilla GL renderer disables them for its translucent layer.
                    .depthWriteEnable(true)
                    .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL)
                    .depthBoundsTestEnable(false)
                    .stencilTestEnable(false)
                    .minDepthBounds(0.0f)
                    .maxDepthBounds(1.0f);
            VkPipelineColorBlendAttachmentState.Buffer blendAttachment =
                    VkPipelineColorBlendAttachmentState.calloc(1, stack);
            blendAttachment.get(0)
                    .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT
                            | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);
            if (blended) {
                // Standard alpha blending with the separate alpha function Minecraft uses for translucency:
                // src alpha, one minus src alpha for colour, and one, one minus src alpha for alpha.
                blendAttachment.get(0)
                        .blendEnable(true)
                        .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                        .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                        .colorBlendOp(VK_BLEND_OP_ADD)
                        .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                        .dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                        .alphaBlendOp(VK_BLEND_OP_ADD);
            } else {
                // Explicitly replace, so a disabled blend attachment does not depend on the zero-initialized
                // (ZERO, ZERO) factors that calloc leaves behind.
                blendAttachment.get(0)
                        .blendEnable(false)
                        .srcColorBlendFactor(VK_BLEND_FACTOR_ONE)
                        .dstColorBlendFactor(VK_BLEND_FACTOR_ZERO)
                        .colorBlendOp(VK_BLEND_OP_ADD)
                        .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                        .dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO)
                        .alphaBlendOp(VK_BLEND_OP_ADD);
            }
            VkPipelineColorBlendStateCreateInfo blend = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .logicOpEnable(false)
                    .pAttachments(blendAttachment);
            VkPipelineDynamicStateCreateInfo dynamic = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));

            VkGraphicsPipelineCreateInfo.Buffer create = VkGraphicsPipelineCreateInfo.calloc(1, stack);
            create.get(0).sType$Default()
                    .pStages(stages)
                    .pVertexInputState(vertexInput)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewport)
                    .pRasterizationState(rasterization)
                    .pMultisampleState(multisample)
                    .pDepthStencilState(depthStencil)
                    .pColorBlendState(blend)
                    .pDynamicState(dynamic)
                    .layout(layout)
                    .renderPass(renderPass)
                    .subpass(0)
                    .basePipelineHandle(VK_NULL_HANDLE)
                    .basePipelineIndex(-1);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, create, null, handle),
                    "vkCreateGraphicsPipelines(" + passName + ")");
            pipeline = handle.get(0);
        } finally {
            // A shader module is only needed while the pipeline is being created; the pipeline captures the code.
            if (fragmentModule != 0L) {
                vkDestroyShaderModule(device, fragmentModule, null);
            }
            if (vertexModule != 0L) {
                vkDestroyShaderModule(device, vertexModule, null);
            }
        }
    }

    private void createDescriptorPool(MemoryStack stack, VkDevice device) {
        VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc(2, stack);
        sizes.get(0).type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC).descriptorCount(1);
        sizes.get(1).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(2);
        VkDescriptorPoolCreateInfo create = VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                .maxSets(1)
                .pPoolSizes(sizes);
        LongBuffer handle = stack.mallocLong(1);
        check(vkCreateDescriptorPool(device, create, null, handle), "vkCreateDescriptorPool");

        descriptorPool = handle.get(0);
        VkDescriptorSetAllocateInfo allocate = VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                .descriptorPool(descriptorPool)
                .pSetLayouts(stack.longs(descriptorSetLayout));
        check(vkAllocateDescriptorSets(device, allocate, handle), "vkAllocateDescriptorSets");
        descriptorSet = handle.get(0);
    }

    private static long createShaderModule(MemoryStack stack, VkDevice device, ByteBuffer code, String stage) {
        if (code == null || !code.isDirect() || code.remaining() < 4) {
            throw new IllegalStateException("SpirvShaders.load returned no direct SPIR-V byte code for the "
                    + stage + " stage of the terrain shader");
        }
        VkShaderModuleCreateInfo create = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(code);
        LongBuffer handle = stack.mallocLong(1);
        check(vkCreateShaderModule(device, create, null, handle), "vkCreateShaderModule(" + stage + ")");
        return handle.get(0);
    }

    private static long queryLimits(VulkanContext context) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(context.physicalDevice(), properties);
            return properties.limits().minUniformBufferOffsetAlignment();
        }
    }

    private void requireAlive() {
        if (deleted) {
            throw new IllegalStateException("Terrain pipeline '" + passName + "' was deleted");
        }
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " returned " + result);
        }
    }
}
