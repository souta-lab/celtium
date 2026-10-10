package org.soutalab.celtium.vulkan;

import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.impl.CompactChunkVertex;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
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
 * because pass configuration (blending, culling) is baked into the pipeline object and differs between passes.
 * <p>
 * <b>Shader contract.</b> The terrain shaders declare five vertex inputs (locations 0..4, the {@code a_PosId} /
 * {@code a_Color} / {@code a_TexCoord} / {@code a_LightCoord} / {@code a_RdhFactor} attributes of the compact 24
 * byte terrain vertex), a push constant block of two {@code mat4} ({@code u_ProjectionMatrix} at offset 0 then
 * {@code u_ModelViewMatrix} at offset 64, 128 bytes, vertex stage only) and descriptor set 0 with binding 0 =
 * uniform buffer (80 bytes, vertex + fragment), <b>binding 1 = lightmap ({@code u_LightTex}, sampled by the vertex
 * stage)</b> and <b>binding 2 = block atlas ({@code u_BlockTex}, sampled by the fragment stage)</b>. This class
 * bakes exactly that contract; see {@link #LIGHTMAP_BINDING} for why the lightmap is not at binding 2.
 * <p>
 * The same {@code SpirvShaders.SOLID_*} pair is used for every pass. That is not a shortcut: the shaders select
 * alpha test, fog mode and fog shape at runtime from the uniform block, so one pipeline covers solid, cutout and
 * translucent terrain, and only blending and culling differ per pass.
 * <p>
 * <b>Y direction and winding.</b> The backend renders with a negative-height viewport so the OpenGL projection and
 * the OpenGL Y direction can be reused, and that viewport transform mirrors the framebuffer Y axis, which inverts
 * the sign of the triangle area Vulkan computes for the front-face test. The chunk mesher emits OpenGL
 * counter-clockwise front faces, so this pipeline declares {@link #FRONT_FACE} = {@code VK_FRONT_FACE_CLOCKWISE}.
 * Setting the OpenGL value here would mark every visible terrain face as back facing and cull it.
 * <p>
 * <b>API surface used from the sibling classes in this package.</b> Every call this class makes on them is listed
 * here so a signature change is easy to reconcile:
 * <ul>
 *   <li>{@code VulkanContext.device()} - the {@code VkDevice} for every object created here.</li>
 *   <li>{@code VulkanContext.physicalDevice()} - queried for {@code minUniformBufferOffsetAlignment},
 *       {@code maxPushConstantsSize}, {@code maxUniformBufferRange} and vertex format support before anything is
 *       created.</li>
 *   <li>{@code VulkanGlTexture.view()} and {@code VulkanGlTexture.sampler()} - the two {@code long} handles written
 *       into the descriptor set by {@link #bindTextures}.</li>
 *   <li>{@code VulkanBuffer.buffer()} and {@code VulkanBuffer.size()} - the uniform buffer handle and its size, used
 *       by the {@code VulkanBuffer} overload of {@link #bindTextures}. The backend's own uniform buffer is a
 *       {@code VulkanUniformBuffer}, which is a different type, so {@link #bindTextures(VulkanGlTexture,
 *       VulkanGlTexture, long, long)} takes its two handles directly.</li>
 *   <li>{@code VulkanFrameTarget.COLOR_RENDER_LAYOUT} / {@code DEPTH_RENDER_LAYOUT} - the layouts the frame target's
 *       acquire and release barriers use; the render pass reuses those constants so the two can never drift.</li>
 *   <li>{@code SpirvShaders.load(String)} with {@code SpirvShaders.SOLID_VERTEX} / {@code SpirvShaders.SOLID_FRAGMENT}
 *       - the SPIR-V for the two stages. {@code SpirvShaders} documents that the caller owns the returned buffer and
 *       must free it once the module exists, so {@code createPipeline} frees both in a {@code finally}.</li>
 * </ul>
 * {@code VulkanContext.queue()}, {@code queueFamilyIndex()}, {@code beginCommands()}, {@code endCommands(...)} and
 * {@code uploadToBuffer(...)} are deliberately unused: this class allocates no device memory and submits no work.
 * <p>
 * <b>Render pass ownership.</b> The pipeline creates its own {@code VkRenderPass} because pipeline creation needs
 * one. It is exposed through {@link #renderPass()} so the backend can record {@code vkCmdBeginRenderPass} with it,
 * and the backend builds its framebuffer from that same handle. The layout transition into the attachment layouts is
 * done by {@code VulkanFrameTarget.recordAcquireBarriers} before the render pass and back to
 * {@code VK_IMAGE_LAYOUT_GENERAL} by {@code recordReleaseBarriers} after it; this render pass therefore declares no
 * layout transitions of its own and needs no subpass dependencies, because those two barriers already publish and
 * claim the attachment accesses with {@code VK_PIPELINE_STAGE_ALL_COMMANDS_BIT} on both sides.
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
     * This is {@code VulkanFrameTarget.COLOR_RENDER_LAYOUT} by reference rather than by value: the frame target
     * transitions the shared color image from {@code VK_IMAGE_LAYOUT_GENERAL} (the layout OpenGL declares through
     * {@code GL_LAYOUT_GENERAL_EXT}) into this layout in {@code recordAcquireBarriers} and back in
     * {@code recordReleaseBarriers}. Reusing the constant makes a mismatch impossible; a literal here would be a
     * silent validation error the first time the frame target changes.
     */
    public static final int COLOR_ATTACHMENT_LAYOUT = VulkanFrameTarget.COLOR_RENDER_LAYOUT;

    /**
     * The layout the depth attachment is expected to be in when the render pass begins, and the layout it is left
     * in when the render pass ends. See {@link #COLOR_ATTACHMENT_LAYOUT} for why this references the frame target.
     */
    public static final int DEPTH_ATTACHMENT_LAYOUT = VulkanFrameTarget.DEPTH_RENDER_LAYOUT;

    /**
     * The front face orientation the terrain vertex data needs.
     * <p>
     * The chunk mesher emits OpenGL counter-clockwise front faces, and the backend renders through a negative
     * height viewport so the OpenGL projection can be reused. That viewport transform flips the framebuffer Y axis,
     * and the Vulkan front-face test uses the sign of the triangle area in framebuffer coordinates, so the flip
     * inverts the result: OpenGL counter-clockwise geometry is Vulkan {@code VK_FRONT_FACE_CLOCKWISE}. Using
     * {@code VK_FRONT_FACE_COUNTER_CLOCKWISE} here would classify every visible terrain face as back facing and,
     * with {@code blockFaceCulling}, cull it. If the backend ever stops flipping Y (for example by baking the flip
     * into the projection matrix instead), pass {@code VK_FRONT_FACE_COUNTER_CLOCKWISE} to
     * {@link #create(VulkanContext, TerrainRenderPass, boolean, int)}.
     */
    /**
     * Vulkan's framebuffer has its Y axis pointing down while OpenGL's points up, and the terrain matrices are
     * OpenGL's unchanged, so a counter-clockwise OpenGL face is clockwise in Vulkan framebuffer space. Declaring
     * counter-clockwise here culled exactly the visible faces and left the terrain's undersides on screen.
     */
    public static final int FRONT_FACE = VK_FRONT_FACE_CLOCKWISE;

    /**
     * The descriptor range used for the dynamic uniform buffer at binding 0, and therefore the size of one uniform
     * block the shader may read.
     * <p>
     * A dynamic uniform buffer binding cannot use {@code VK_WHOLE_SIZE}: the effective range is
     * {@code dynamicOffset .. dynamicOffset + range} and validation rejects anything that reaches past the end of
     * the buffer. Since {@link #bindTextures} does not receive a block size, the block size is fixed here: every
     * uniform block in the buffer passed to {@code bindTextures} must be at least this large (the terrain uniform
     * block itself is 80 bytes, and the backend's stride is 256 bytes aligned up to the device limit). 256 bytes is
     * a multiple of the {@code minUniformBufferOffsetAlignment} values desktop drivers report (1, 16, 64, 128,
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
    private static final int[] COMPACT_ATTRIBUTE_FORMATS = {
            VK_FORMAT_R16G16B16A16_UINT, // 0: a_PosId     4 x unsigned short, raw
            VK_FORMAT_R8G8B8A8_UNORM,    // 1: a_Color     4 x unsigned byte, normalized
            VK_FORMAT_R16G16_UINT,       // 2: a_TexCoord  2 x unsigned short, raw
            VK_FORMAT_R32_UINT,          // 3: a_LightCoord 1 x unsigned int, raw
            VK_FORMAT_R8G8B8A8_SNORM,    // 4: a_RdhFactor 4 x signed byte, normalized
    };

    /** VanillaLikeChunkVertex: positions and texture coordinates are floats, so no unpacking is needed. */
    private static final int[] VANILLA_ATTRIBUTE_FORMATS = {
            VK_FORMAT_R32G32B32_SFLOAT,  // 0: a_PosId     3 x float
            VK_FORMAT_R8G8B8A8_UNORM,    // 1: a_Color     4 x unsigned byte, normalized
            VK_FORMAT_R32G32_SFLOAT,     // 2: a_TexCoord  2 x float
            VK_FORMAT_R32_UINT,          // 3: a_LightCoord 1 x unsigned int, raw
            VK_FORMAT_R8G8B8A8_SNORM,    // 4: a_RdhFactor 4 x signed byte, normalized
    };

    private static final int[] COMPACT_ATTRIBUTE_OFFSETS = { 0, 8, 12, 16, 20 };
    private static final int[] VANILLA_ATTRIBUTE_OFFSETS = { 0, colourAttributeOffset(), 16, 24, 28 };

    /**
     * Where the vanilla-like layout's {@code a_Color} is read from, normally byte 12.
     * <p>
     * Overridable so one run can answer whether the offset is honoured at all: reading the position bytes instead must
     * change the frame, and if it does not, the attribute description is being ignored rather than reading data that
     * happens to be wrong.
     */
    private static int colourAttributeOffset() {
        return Integer.getInteger("celtium.vulkan.colourAttrOffset", 12).intValue();
    }

    /**
     * The two vertex layouts this build can draw. The game picks one through the "compact vertex format" option,
     * and the choice changes both the attribute layout and the position decoding in the vertex shader, so a
     * configuration using a format this backend does not know must be refused rather than drawn wrong.
     */
    private static final class VertexLayout {
        final int stride;
        final int[] formats;
        final int[] offsets;
        final String vertexModule;

        VertexLayout(int stride, int[] formats, int[] offsets, String vertexModule) {
            this.stride = stride;
            this.formats = formats;
            this.offsets = offsets;
            this.vertexModule = vertexModule;
        }

        static VertexLayout forPass(TerrainRenderPass pass) {
            int stride = pass.vertexType().getVertexFormat().getStride();
            if (stride == 24) {
                return new VertexLayout(24, COMPACT_ATTRIBUTE_FORMATS, COMPACT_ATTRIBUTE_OFFSETS,
                        SpirvShaders.SOLID_VERTEX);
            }
            if (stride == 32) {
                return new VertexLayout(32, VANILLA_ATTRIBUTE_FORMATS, VANILLA_ATTRIBUTE_OFFSETS,
                        SpirvShaders.SOLID_VERTEX_VANILLA);
            }
            throw new IllegalArgumentException("Terrain pass '" + pass.name() + "' uses a " + stride
                    + " byte vertex format that the Vulkan backend does not implement; OpenGL stays active");
        }
    }

    private static final int UNIFORM_BINDING = 0;

    /**
     * The block atlas sampler binding.
     * <p>
     * Note the numbering: the shader sources declare {@code u_LightTex} at binding 1 (vertex stage) and
     * {@code u_BlockTex} at binding 2 (fragment stage), so the atlas is binding 2 and the lightmap is binding 1.
     * The descriptor writes follow the shaders, because a descriptor that does not match the shader's declaration
     * silently samples the wrong texture. The public API still takes the atlas first and the lightmap second.
     */
    private static final int BLOCK_ATLAS_BINDING = 2;

    /** The lightmap sampler binding; see {@link #BLOCK_ATLAS_BINDING} for why it is 1 and not 2. */
    private static final int LIGHTMAP_BINDING = 1;

    private final VulkanContext context;
    private final String passName;
    private final boolean blended;
    private final boolean blockFaceCulling;
    private final int frontFace;
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

    /** The vertex layout of the pass this pipeline draws. */
    private final VertexLayout vertexLayout;

    /** What the device reported for the limits this pipeline depends on. Used for diagnostics. */
    private String limitsSummary = "unqueried";

    /** The vertex input state this pipeline was created with, for diagnostics. */
    private String vertexInputSummary = "unqueried";

    /** Non-null when a limit was not reported and a check had to be skipped. */
    private String limitsWarning;
    /** True once {@link #bindTextures} has written a complete descriptor set; until then binding it is invalid. */
    private boolean descriptorsWritten;
    private boolean deleted;

    /**
     * The atlas binding as the last {@link #bindTextures} wrote it into the descriptor set.
     * <p>
     * The descriptor stores handles, not objects: once {@code vkUpdateDescriptorSets} returns, the set names whatever
     * {@code atlas.view()} was at that instant and nothing re-reads it. If the mirror's image is recreated afterwards -
     * a resize rebuilds image, memory and view - the set keeps the retired view until it is written again. These four
     * fields are the only record of what was written, and {@link #auditAtlasDraw} compares them against the live
     * texture, which is what turns "the terrain samples a stale image" from a hypothesis into a number.
     */
    private VulkanGlTexture boundAtlas;
    private long boundAtlasView;
    private long boundAtlasKey;
    private int boundAtlasIdentity;

    /**
     * Whether the bounded atlas-binding audit prints; on by default and one property away from off
     * ({@code -Dceltium.vulkan.atlasAudit=false}).
     * <p>
     * The budget below is what keeps it cheap: at most {@link #ATLAS_AUDIT_LINES} lines for a whole run, a handful of
     * long comparisons per terrain pass, and nothing at all once the budget is spent. It is kept (rather than removed
     * after the question was answered) because the question - "does the descriptor name the image the mirror is
     * currently using" - is a property of every future change to texture ownership, and a run that answers it in ten
     * lines is cheaper than the next investigation.
     */
    private static final boolean ATLAS_AUDIT = !"false".equalsIgnoreCase(
            System.getProperty("celtium.vulkan.atlasAudit", "true"));
    /** Lines the audit may print in one process, so no run can flood the log. */
    private static final int ATLAS_AUDIT_LINES = 16;
    /** Draws whose binding is verified against the descriptor, and writes whose binding is printed. */
    private static final int ATLAS_AUDIT_DRAWS = 4;
    private static final int ATLAS_AUDIT_WRITES = 3;
    private static final int ATLAS_AUDIT_CHANGES = 3;
    private static int atlasAuditLines;
    private static int atlasAuditWrites;
    private static int atlasAuditDrawChecks;
    private static int atlasAuditChanges;
    private static int atlasAuditStaleAtDraw;
    private static int atlasAuditDeadAtDraw;
    private static int atlasAuditSummaries;
    private static long atlasAuditPrevView;
    private static int atlasAuditPrevIdentity;
    private static boolean atlasAuditHasPrev;

    private VulkanTerrainPipeline(VulkanContext context, TerrainRenderPass pass, boolean blockFaceCulling, int frontFace) {
        this.context = context;
        this.passName = pass.name();
        this.vertexLayout = VertexLayout.forPass(pass);
        this.blended = needsBlending(pass);
        this.blockFaceCulling = blockFaceCulling;
        this.frontFace = frontFace;
        this.hasNoLightmap = pass.hasNoLightmap();
        this.minUniformBufferOffsetAlignment = queryLimits(context);
    }

    /**
     * Creates the pipeline for one terrain render pass with the front face orientation the backend's negative
     * height viewport requires (see {@link #FRONT_FACE}).
     *
     * @param context          the live Vulkan device wrapper
     * @param pass             the render pass this pipeline is built for; its name selects the blending rule and its
     *                         {@code hasNoLightmap()} flag is remembered for {@link #bindTextures}
     * @param blockFaceCulling {@code true} to cull back faces, {@code false} to draw both faces
     * @return a fully created pipeline; delete it with {@link #delete()}
     * @throws IllegalArgumentException if the pass cannot be rendered by a triangle list pipeline
     * @throws IllegalStateException    if the device lacks a limit this pipeline depends on, or if Vulkan object
     *                                  creation fails
     */
    public static VulkanTerrainPipeline create(VulkanContext context, TerrainRenderPass pass,
                                               boolean blockFaceCulling) {
        return create(context, pass, blockFaceCulling, FRONT_FACE);
    }

    /**
     * Creates the pipeline for one terrain render pass with an explicit front face orientation.
     * <p>
     * Only use this overload when the Y flip convention changes: the default {@link #FRONT_FACE} assumes the backend
     * renders through a negative height viewport, and a backend that flips Y in the projection matrix instead needs
     * {@code VK_FRONT_FACE_COUNTER_CLOCKWISE} for the same visual result.
     *
     * @param context          the live Vulkan device wrapper
     * @param pass             the render pass this pipeline is built for
     * @param blockFaceCulling {@code true} to cull back faces (the vanilla behaviour for terrain), {@code false} to
     *                         draw both faces
     * @param frontFace        {@code VK_FRONT_FACE_CLOCKWISE} or {@code VK_FRONT_FACE_COUNTER_CLOCKWISE}
     * @return a fully created pipeline; delete it with {@link #delete()}
     * @throws IllegalArgumentException if the pass cannot be rendered by a triangle list pipeline, or if
     *                                  {@code frontFace} is neither Vulkan value
     * @throws IllegalStateException    if the device lacks a limit this pipeline depends on, or if Vulkan object
     *                                  creation fails
     */
    public static VulkanTerrainPipeline create(VulkanContext context, TerrainRenderPass pass,
                                               boolean blockFaceCulling, int frontFace) {
        if (context == null) {
            throw new NullPointerException("context");
        }
        if (pass == null) {
            throw new NullPointerException("pass");
        }
        if (frontFace != VK_FRONT_FACE_CLOCKWISE && frontFace != VK_FRONT_FACE_COUNTER_CLOCKWISE) {
            throw new IllegalArgumentException("frontFace must be VK_FRONT_FACE_CLOCKWISE or"
                    + " VK_FRONT_FACE_COUNTER_CLOCKWISE, but was " + frontFace);
        }
        // A quad list pass would need GL_QUADS, which Vulkan core does not have. Rejecting it here is much easier
        // to diagnose than the garbage geometry a triangle list draw of quad indices would produce.
        if (pass.primitiveType() == QuadPrimitiveType.DIRECT) {
            throw new IllegalArgumentException("Terrain pass '" + pass.name() + "' uses direct quads, which a"
                    + " triangle list Vulkan pipeline cannot draw; use a triangulated pass");
        }
        VulkanTerrainPipeline result = new VulkanTerrainPipeline(context, pass, blockFaceCulling, frontFace);
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
    /** {@return the vertex input state this pipeline was created with} */
    public String vertexInputSummary() {
        return this.vertexInputSummary;
    }

    /** {@return the device limits this pipeline read, for diagnostics} */
    public String limitsSummary() {
        return this.limitsSummary;
    }

    /** {@return a warning about skipped limit checks, or null when every check ran} */
    public String limitsWarning() {
        return this.limitsWarning;
    }

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
     *                 in which case the atlas takes its place so the descriptor set stays valid
     * @param uniforms the uniform buffer holding region offset and fog parameters, one block per section
     * @throws NullPointerException     if {@code atlas} or {@code uniforms} is {@code null}
     * @throws IllegalArgumentException if the uniform buffer has no storage
     * @throws IllegalStateException    if this pass samples the lightmap but no lightmap was supplied
     */
    public void bindTextures(VulkanGlTexture atlas, VulkanGlTexture lightmap, VulkanBuffer uniforms) {
        if (uniforms == null) {
            throw new NullPointerException("uniforms");
        }
        bindTextures(atlas, lightmap, uniforms.buffer(), uniforms.size());
    }

    /**
     * Fills the descriptor set from the two textures and the native handle and size of the uniform buffer.
     * <p>
     * This overload exists because the backend's uniform buffer ({@code VulkanUniformBuffer}) is not a
     * {@link VulkanBuffer}: both expose the native handle and the size, so the handles can be passed directly
     * without forcing either class to change.
     *
     * @param atlas             the block atlas image; required
     * @param lightmap          the lightmap image; may be {@code null} only for a pass whose
     *                          {@code hasNoLightmap()} is true
     * @param uniformBuffer     the native {@code VkBuffer} holding the terrain uniforms, one block per section
     * @param uniformBufferSize the size of that buffer in bytes
     * @throws NullPointerException     if {@code atlas} is {@code null}
     * @throws IllegalArgumentException if the uniform buffer handle is 0 or the buffer has no storage
     * @throws IllegalStateException    if this pass samples the lightmap but no lightmap was supplied
     */
    public void bindTextures(VulkanGlTexture atlas, VulkanGlTexture lightmap, long uniformBuffer,
                             long uniformBufferSize) {
        requireAlive();
        if (atlas == null) {
            throw new NullPointerException("atlas");
        }
        if (uniformBuffer == 0L) {
            throw new IllegalArgumentException("The uniform buffer has no VkBuffer; it was deleted or never written");
        }
        if (uniformBufferSize <= 0L) {
            throw new IllegalArgumentException("The uniform buffer has no storage");
        }
        if (lightmap == null) {
            if (!hasNoLightmap) {
                throw new IllegalStateException("Terrain pass '" + passName + "' samples the lightmap,"
                        + " so bindTextures must receive a non-null lightmap");
            }
            // The shader variant for a pass without a lightmap never samples the lightmap binding, but the
            // descriptor must still hold a valid image view and sampler, otherwise the set is invalid.
            lightmap = atlas;
        }

        this.uniformsSize = uniformBufferSize;
        this.uniformsRange = (int) Math.min(uniformBufferSize, (long) UNIFORM_BLOCK_SIZE);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack);
            bufferInfo.get(0).buffer(uniformBuffer).offset(0L).range(uniformsRange);

            VkDescriptorImageInfo.Buffer atlasInfo = VkDescriptorImageInfo.calloc(1, stack);
            atlasInfo.get(0).sampler(atlas.sampler()).imageView(atlas.view())
                    .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            VkDescriptorImageInfo.Buffer lightmapInfo = VkDescriptorImageInfo.calloc(1, stack);
            lightmapInfo.get(0).sampler(lightmap.sampler()).imageView(lightmap.view())
                    .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

            // The binding of each write comes from the constant, so the array order carries no meaning: the atlas
            // goes to BLOCK_ATLAS_BINDING (2) and the lightmap to LIGHTMAP_BINDING (1), matching the shader source.
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
        auditAtlasWrite(atlas);
    }

    /**
     * Records the atlas binding the descriptor set was just written with, and reports the identity transitions.
     * <p>
     * A transition is the interesting event: the terrain binds one image for the whole session in the healthy case, so
     * a second distinct (object, view) pair means the mirror was recreated and every earlier descriptor named a
     * retired view. The line carries the size and {@code hasFullContent()} as well, because a live descriptor bound to
     * a placeholder-sized or never-filled image draws black just as convincingly as a stale one.
     */
    private void auditAtlasWrite(VulkanGlTexture atlas) {
        this.boundAtlas = atlas;
        this.boundAtlasView = atlas.view();
        this.boundAtlasKey = atlas.key();
        this.boundAtlasIdentity = System.identityHashCode(atlas);
        if (!ATLAS_AUDIT) {
            return;
        }
        atlasAuditWrites++;
        boolean changed = atlasAuditHasPrev
                && (this.boundAtlasView != atlasAuditPrevView || this.boundAtlasIdentity != atlasAuditPrevIdentity);
        if (changed) {
            atlasAuditChanges++;
        }
        if (atlasAuditLines < ATLAS_AUDIT_LINES && (!atlasAuditHasPrev || atlasAuditWrites <= ATLAS_AUDIT_WRITES
                || (changed && atlasAuditChanges <= ATLAS_AUDIT_CHANGES))) {
            atlasAuditLines++;
            System.out.println("[Celtium] CELTIUM_ATLAS_DESCRIPTOR phase=write pass=" + passName + " write#"
                    + atlasAuditWrites + (atlasAuditHasPrev ? changed ? " CHANGED" : " unchanged" : " first")
                    + (atlasAuditHasPrev ? " prevView=0x" + Long.toHexString(atlasAuditPrevView) + " prevObj="
                            + Integer.toHexString(atlasAuditPrevIdentity) : "")
                    + " " + describeBinding(atlas));
        }
        atlasAuditPrevView = this.boundAtlasView;
        atlasAuditPrevIdentity = this.boundAtlasIdentity;
        atlasAuditHasPrev = true;
        // The summary lands a few hundred writes in - a few seconds of terrain at this pass count - so it is in the log
        // even for a run that is killed rather than shut down.
        if (atlasAuditWrites == 300) {
            auditSummary("write#300");
        }
    }

    /**
     * Verifies, at the first few draws, that the descriptor still names the image the texture is currently using.
     * <p>
     * This is the check the audit exists for: {@link #bindDescriptors} is the last point before the draw command is
     * recorded, so a mismatch here means the recorded draw samples a view the mirror has already replaced - the
     * "stale descriptor" failure - and a live view of 0 means the mirror was deleted while the descriptor still
     * referenced it.
     */
    private void auditAtlasDraw(long uniformBufferOffset) {
        if (!ATLAS_AUDIT || this.boundAtlas == null || atlasAuditDrawChecks >= ATLAS_AUDIT_DRAWS) {
            return;
        }
        atlasAuditDrawChecks++;
        long liveView = this.boundAtlas.view();
        long liveKey = this.boundAtlas.key();
        boolean stale = liveView != this.boundAtlasView || liveKey != this.boundAtlasKey;
        boolean dead = liveView == 0L;
        if (stale) {
            atlasAuditStaleAtDraw++;
        }
        if (dead) {
            atlasAuditDeadAtDraw++;
        }
        if (atlasAuditLines < ATLAS_AUDIT_LINES) {
            atlasAuditLines++;
            System.out.println("[Celtium] CELTIUM_ATLAS_DESCRIPTOR phase=draw pass=" + passName + " draw#"
                    + atlasAuditDrawChecks + " uniformOffset=" + uniformBufferOffset
                    + " writtenView=0x" + Long.toHexString(this.boundAtlasView)
                    + " writtenKey=0x" + Long.toHexString(this.boundAtlasKey)
                    + " liveView=0x" + Long.toHexString(liveView) + " liveKey=0x" + Long.toHexString(liveKey)
                    + " liveGen=" + generationOf(this.boundAtlas) + " liveSize=" + this.boundAtlas.width() + "x"
                    + this.boundAtlas.height() + " liveFull=" + this.boundAtlas.hasFullContent()
                    + " registryOfSameSize=" + VulkanImmediateTexturePipeline.registryImageOfSize(
                            this.boundAtlas.width(), this.boundAtlas.height())
                    + " verdict=" + (dead ? "DEAD_IMAGE" : stale ? "STALE_BINDING" : "fresh"));
        }
    }

    /** Prints the audit counters once, so a run's log ends with the totals rather than only the sampled lines. */
    private void auditSummary(String where) {
        if (!ATLAS_AUDIT || atlasAuditSummaries >= 2 || atlasAuditLines >= ATLAS_AUDIT_LINES) {
            return;
        }
        atlasAuditSummaries++;
        atlasAuditLines++;
        System.out.println("[Celtium] CELTIUM_ATLAS_DESCRIPTOR phase=summary at=" + where + " writes="
                + atlasAuditWrites + " bindingChanges=" + atlasAuditChanges + " drawChecks=" + atlasAuditDrawChecks
                + " staleAtDraw=" + atlasAuditStaleAtDraw + " deadAtDraw=" + atlasAuditDeadAtDraw
                + " descriptorFaultInjections=" + VulkanDescriptorFault.injections()
                + " retired=" + VulkanRetireQueue.describe());
    }

    /**
     * The identity a descriptor's image view and sampler form, as one line.
     * <p>
     * {@code VulkanGlTexture} exposes its view, sampler, size and content flag but not its image handle, and this class
     * may not add one; the view is what the descriptor actually stores, so the view plus the generation - read back out
     * of {@link VulkanGlTexture#key()} - is the complete identity of the binding.
     */
    private static String describeBinding(VulkanGlTexture texture) {
        return "obj=" + Integer.toHexString(System.identityHashCode(texture))
                + " view=0x" + Long.toHexString(texture.view())
                + " gen=" + generationOf(texture)
                + " key=0x" + Long.toHexString(texture.key())
                + " size=" + texture.width() + "x" + texture.height()
                + " full=" + texture.hasFullContent()
                + " sampler=0x" + Long.toHexString(texture.sampler());
    }

    /** {@return the generation folded into {@link VulkanGlTexture#key()}}, recovered by inverting the fold. */
    private static long generationOf(VulkanGlTexture texture) {
        return texture.key() ^ (texture.view() * 31L);
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
        auditAtlasDraw(uniformBufferOffset);
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
     * The shader modules are not listed because they no longer exist at this point: the driver copies the SPIR-V
     * into the pipeline, so both modules are destroyed (and their native buffers freed) as soon as
     * {@code vkCreateGraphicsPipelines} returns.
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
        auditSummary("delete(" + passName + ")");
        this.boundAtlas = null;
    }

    private void createResources() {
        VkDevice device = context.device();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(context.physicalDevice(), properties);
            // The Vulkan minimum for maxUniformBufferRange is 16384 and for maxPushConstantsSize 128, so a value
            // at or below zero can only mean the query did not answer. Report it and continue instead of refusing
            // to draw terrain on a device that is in fact capable of it.
            //
            // These limits are uint32 in Vulkan, so they must be read as unsigned: a value that prints as -1 in Java
            // is 4294967295, i.e. no limit at all, not a broken query. Reading them as signed is what made an
            // earlier revision reject a capable device. The real requirement is then checked against the unsigned
            // value, and a device that genuinely cannot serve the block is rejected with a clear message.
            long maxPushConstants = Integer.toUnsignedLong(properties.limits().maxPushConstantsSize());
            long maxUniformRange = Integer.toUnsignedLong(properties.limits().maxUniformBufferRange());
            // LWJGL types this one as a Java long even though Vulkan declares it uint32, so the unsigned value is
            // taken explicitly from the low 32 bits.
            long minUniformAlignment = properties.limits().minUniformBufferOffsetAlignment() & 0xFFFFFFFFL;
            limitsSummary = "maxPushConstantsSize=" + maxPushConstants
                    + " maxUniformBufferRange=" + maxUniformRange
                    + " minUniformBufferOffsetAlignment=" + minUniformAlignment;
            if (maxPushConstants < PUSH_CONSTANT_BYTES) {
                throw new IllegalStateException("The device allows only " + maxPushConstants
                        + " bytes of push constants but the terrain shaders need " + PUSH_CONSTANT_BYTES);
            }
            if (maxUniformRange < UNIFORM_BLOCK_SIZE) {
                throw new IllegalStateException("The device allows only " + maxUniformRange
                        + " bytes per uniform range but the terrain uniform block is " + UNIFORM_BLOCK_SIZE);
            }
            if (minUniformAlignment <= 0L || (minUniformAlignment & (minUniformAlignment - 1L)) != 0L) {
                // The backend aligns its dynamic offsets with this value; anything but a power of two would make
                // every offset wrong, so it is reported rather than silently assumed.
                limitsWarning = "minUniformBufferOffsetAlignment=" + minUniformAlignment
                        + " is not a power of two (" + limitsSummary + ")";
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
        for (int i = 0; i < this.vertexLayout.formats.length; i++) {
            int format = this.vertexLayout.formats[i];
            vkGetPhysicalDeviceFormatProperties(context.physicalDevice(), format, features);
            if ((features.bufferFeatures() & VK_FORMAT_FEATURE_VERTEX_BUFFER_BIT) == 0) {
                throw new IllegalStateException("Vertex attribute " + i + " of the terrain format uses Vulkan"
                        + " format " + format + ", which this device cannot use as a vertex buffer format");
            }
        }
    }

    private void createRenderPass(MemoryStack stack, VkDevice device) {
        renderPass = buildFrameRenderPass(stack, device);
    }

    /**
     * Builds a render pass compatible with the frame target's attachments without any terrain pipeline - the
     * standalone handle UI draws use when no terrain pass has run yet (the GL-less menu screen is the case: every
     * batch is immediate and no pass ever opens).
     */
    static long createFrameRenderPass(VulkanContext context) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            return buildFrameRenderPass(stack, context.device());
        }
    }

    private static long buildFrameRenderPass(MemoryStack stack, VkDevice device) {
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

        // No subpass dependencies are declared, for two reasons. First, this render pass performs no layout
        // transition of its own: initialLayout and finalLayout are the same attachment layouts, and
        // VulkanFrameTarget.recordAcquireBarriers / recordReleaseBarriers do the transitions outside the pass.
        // Second, those two barriers already carry every dependency this pass needs: both use
        // VK_PIPELINE_STAGE_ALL_COMMANDS_BIT on both sides with the attachment read and write access masks, so the
        // GL blit written into the shared images is available and visible to the attachment loads, and the terrain
        // writes are available for the release barrier that hands the images back to OpenGL. That barrier-driven
        // pattern is the one the interop probe was validated with on both target GPUs. The implicit external
        // dependencies Vulkan adds carry no access masks, so declaring narrower explicit dependencies on top would
        // only add ways to get the stage masks wrong.
        VkRenderPassCreateInfo create = VkRenderPassCreateInfo.calloc(stack).sType$Default()
                .pAttachments(attachments)
                .pSubpasses(subpasses);
        LongBuffer handle = stack.mallocLong(1);
        check(vkCreateRenderPass(device, create, null, handle), "vkCreateRenderPass");
        return handle.get(0);
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
        ByteBuffer vertexCode = null;
        ByteBuffer fragmentCode = null;
        try {
            // SpirvShaders.load hands over a native buffer that the caller owns, so both modules are loaded into
            // locals and released in the finally below, once the driver has copied the code into the modules.
            vertexCode = SpirvShaders.load(this.vertexLayout.vertexModule);
            fragmentCode = SpirvShaders.load(SpirvShaders.SOLID_FRAGMENT);
            vertexModule = createShaderModule(stack, device, vertexCode, "vertex");
            fragmentModule = createShaderModule(stack, device, fragmentCode, "fragment");
            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            stages.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertexModule)
                    .pName(stack.UTF8("main"));
            stages.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragmentModule)
                    .pName(stack.UTF8("main"));

            VkVertexInputBindingDescription.Buffer binding = VkVertexInputBindingDescription.calloc(1, stack);
            binding.get(0).binding(0).stride(this.vertexLayout.stride).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
            VkVertexInputAttributeDescription.Buffer attributes =
                    VkVertexInputAttributeDescription.calloc(this.vertexLayout.formats.length, stack);
            for (int i = 0; i < this.vertexLayout.formats.length; i++) {
                attributes.get(i).location(i).binding(0)
                        .format(this.vertexLayout.formats[i])
                        .offset(this.vertexLayout.offsets[i]);
            }
            // The chosen layout and shader module have to be visible: the pass's declared stride selects both, and a
            // mismatch between the buffer the mesher filled and the offsets the shader reads shows up as terrain whose
            // colour is unrelated to what the mesh builder packed.
            System.out.println("[Celtium] CELTIUM_TERRAIN_LAYOUT stride=" + this.vertexLayout.stride + " module=" + this.vertexLayout.vertexModule
                    + " colorOffset=" + this.vertexLayout.offsets[1] + " colorFormat=0x"
                    + Integer.toHexString(this.vertexLayout.formats[1]));
            StringBuilder input = new StringBuilder("vertex input stride=" + this.vertexLayout.stride
                    + " module=" + this.vertexLayout.vertexModule);
            for (int i = 0; i < this.vertexLayout.formats.length; i++) {
                input.append(" loc").append(i).append("=fmt").append(this.vertexLayout.formats[i])
                        .append("@").append(this.vertexLayout.offsets[i]);
            }
            vertexInputSummary = input.toString();
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
                    // The mesh winding is OpenGL counter-clockwise; see FRONT_FACE for why the negative height
                    // viewport turns that into VK_FRONT_FACE_CLOCKWISE for Vulkan's framebuffer space test.
                    .frontFace(frontFace)
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
                    .depthWriteEnable(!this.blended) // Minecraft draws its translucent layer with depth writes off
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
            // SpirvShaders.load transfers ownership of its native buffer to the caller, so both modules are
            // released here: after the module exists the driver has its own copy, and on the failure path the code
            // would otherwise leak.
            if (fragmentCode != null) {
                MemoryUtil.memFree(fragmentCode);
            }
            if (vertexCode != null) {
                MemoryUtil.memFree(vertexCode);
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
