package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.soutalab.celtium.vulkan.gl.ModGlslTranslator;
import org.soutalab.celtium.vulkan.gl.ModProgramRegistry;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.vulkan.VK10.*;

/**
 * A mod's own GL program (lowered and compiled by {@link ModProgramRegistry}) as a pipeline of the immediate path.
 * <p>
 * The batch a mod draws with its program bound used to be refused, because the immediate path's own shaders cannot
 * reproduce it (GTCEu's bloom passes are the measured case). This pipeline draws the same batch, in the same group
 * machinery, with the program's SPIR-V: the render state (render pass, blend, depth, cull) is copied from the variant
 * the path would have used for the batch, so the only difference is the shaders, their uniform block (the program's
 * std140 uniforms: push constants while the block fits the device's {@code maxPushConstantsSize}, which
 * {@link org.soutalab.celtium.vulkan.gl.ModPushConstantLimit} records at selection and the lowering checks, and a
 * uniform buffer at the set's last binding - fed from a slot of this pipeline's uniform ring - when it does not)
 * and their sampler set.
 * <p>
 * Vertex inputs follow GL's aliasing of generic attribute 0 with the conventional vertex position: a known position
 * name or an unnumbered first attribute takes the position, a texture coordinate attribute the format's UV element
 * and a colour attribute its colour element. Which attribute is which is decided by name, not by location, because
 * the location belongs to the program (a {@code #version 120} program's declaration order, GLSM's
 * {@code VertexFormatElement.Usage} numbers) while the batch's field order belongs to the uploader - the vocabularies
 * {@link #source} knows are the lowering's own {@code celtium_Vertex}/{@code celtium_MultiTexCoord0}/
 * {@code celtium_Color}, the GLSL 330 fixed-function generator's {@code a_Color}/{@code a_TexCoord0} and Iris's
 * {@code iris_*} renames of the same builtins. Any other attribute is refused with a reason, naming the attribute.
 * <p>
 * One instance per (program, layout, render state); {@link Draw} is the per-batch half (uniform bytes and sampler set)
 * that a group carries as its prepared pipeline.
 */
public final class VulkanModProgramPipeline {

    /**
     * A field of the batch's vertex stream a program attribute can read, with the format and offset the immediate
     * path's own pipelines declare for it: position at 0, the UV's two floats at 12, the colour's four bytes at 20
     * (the POSITION_TEX_COLOR layout, 24 bytes - {@code VulkanImmediateTexturePipeline}'s own attribute descriptions
     * say the same numbers). A wrong offset here reads a neighbour's bytes and draws the wrong picture without any
     * Vulkan error, so the numbers are asserted by {@code ModProgramAttributeSourceTest}.
     */
    enum Source {
        POSITION(VK_FORMAT_R32G32B32_SFLOAT, 0),
        TEXCOORD(VK_FORMAT_R32G32_SFLOAT, 12),
        COLOR(VK_FORMAT_R8G8B8A8_UNORM, 20),
        /**
         * GL's current colour, for a layout that carries none: GL feeds a colour attribute whose array is disabled
         * from the current generic attribute ({@code glColor4f}), the same value for every vertex. Here that is a
         * second vertex binding ({@link #CONSTANT_BINDING}) with stride 0 over four floats the draw writes from the
         * backend's colour mirror, so every vertex reads the one value - Vulkan's form of a constant attribute.
         */
        CONSTANT_COLOR(VK_FORMAT_R32G32B32A32_SFLOAT, 0);

        final int format;
        final int offset;

        Source(int format, int offset) {
            this.format = format;
            this.offset = offset;
        }

        /** {@return the vertex binding this field is read from}: the batch's own (0), or the constant ring (1). */
        int binding() {
            return this == CONSTANT_COLOR ? CONSTANT_BINDING : 0;
        }
    }

    /** The vertex binding of the constant-attribute ring (stride 0); the batch's own vertices are binding 0. */
    static final int CONSTANT_BINDING = 1;
    /** Bytes of one constant colour slot: four floats. */
    static final int CONSTANT_SLOT_BYTES = 16;
    /**
     * Slots in a pipeline's constant ring. A slot is rewritten only after the ring wraps, and the ring is sized far
     * beyond the draws one pipeline issues across the frames in flight (the measured user is the sky program's
     * horizon mesh: a handful of draws a frame); consecutive draws with an unchanged colour share one slot.
     */
    static final int CONSTANT_RING_SLOTS = 4096;

    /**
     * The texture-coordinate names: the lowering's own (a legacy {@code gl_MultiTexCoord0} comes back as
     * {@code celtium_MultiTexCoord0}), the GLSL 330 fixed-function generator's {@code a_TexCoord0} and Iris's
     * {@code iris_MultiTexCoord0} (its transformer renames {@code gl_MultiTexCoord0} and injects it at location 2),
     * each a {@code vec4} reading the batch's two UV floats - Vulkan fills the missing components with (0, 0, 0, 1),
     * the same values GL supplies, which is what the generator's {@code coord.st / q} division needs.
     * {@code iris_MultiTexCoord1} is not in this set: it is handled in {@link #source} because only one layout
     * may answer it (see there).
     */
    private static final java.util.Set<String> TEXCOORD_NAMES =
            java.util.Set.of("celtium_MultiTexCoord0", "a_TexCoord0", "iris_MultiTexCoord0");

    /**
     * The position names: the lowering's {@code celtium_Vertex}, which is {@code gl_Vertex} - and {@code ftransform()},
     * which expands to a use of it, so a legacy program that both declares its own attributes and calls
     * {@code ftransform()} has this one at a nonzero location. It was refused as "no source" for that reason and only
     * that reason. Iris's {@code iris_Vertex} is the same rename made by its transformer (injected at location 0, so
     * the location-0 fallback already answered it; naming it here drops that reliance). The three-float format fills
     * its {@code vec4}'s w with 1, as GL's vertex position had.
     */
    private static final java.util.Set<String> POSITION_NAMES = java.util.Set.of("celtium_Vertex", "iris_Vertex");

    /**
     * The colour names: the lowering's {@code celtium_Color}, the fixed-function generator's {@code a_Color} and
     * Iris's {@code iris_Color} (its transformer renames {@code gl_Color}, injected at location 1 in the patch shapes
     * that declare it). As with the other colour names it only resolves on the colour layout; on the colourless
     * layout it stays refused - a program that needs a colour value no batch field carries is the separate
     * constant-attribute question, not a name this gate can answer.
     */
    private static final java.util.Set<String> COLOR_NAMES = java.util.Set.of("celtium_Color", "a_Color", "iris_Color");

    /** The per-batch part a group draws: the pipeline, the packed uniforms and the sampler set. */
    public static final class Draw {
        final VulkanModProgramPipeline pipeline;
        final ByteBuffer uniforms;
        final long samplerSet;
        /** What this pass reads and writes, for the record-order trace; never read by the draw. */
        public final String label;
        /** The images the pass samples, for the mid-frame readback instrument only. */
        public final VulkanGlTexture[] inputs;
        /** Storage images whose layouts are transitioned around the fragment draw. */
        public final VulkanGlTexture[] images;

        /** The constant ring slot's byte offset holding the draw's current colour; -1 when nothing reads it. */
        final long constantOffset;

        Draw(VulkanModProgramPipeline pipeline, ByteBuffer uniforms, long samplerSet, String label,
             VulkanGlTexture[] inputs, VulkanGlTexture[] images) {
            this(pipeline, uniforms, samplerSet, label, inputs, images, -1L);
        }

        Draw(VulkanModProgramPipeline pipeline, ByteBuffer uniforms, long samplerSet, String label,
             VulkanGlTexture[] inputs, VulkanGlTexture[] images, long constantOffset) {
            this.pipeline = pipeline;
            this.uniforms = uniforms;
            this.samplerSet = samplerSet;
            this.label = label;
            this.inputs = inputs;
            this.images = images;
            this.constantOffset = constantOffset;
        }

        public void draw(VkCommandBuffer commandBuffer, VulkanDynamicMesh mesh, long[] bindCache) {
            if (this.constantOffset >= 0L) {
                this.pipeline.bindConstant(commandBuffer, this.constantOffset);
            }
            this.pipeline.draw(commandBuffer, mesh, this.uniforms, this.samplerSet, this.images, bindCache);
        }
    }

    private final VulkanContext context;
    private final List<ModGlslTranslator.Attribute> attributes;
    private final List<ModGlslTranslator.SamplerBinding> samplers;
    private final int samplerCount;
    private final List<ModGlslTranslator.ImageBinding> images;
    private final int imageCount;
    private final int pushBytes;
    /** Whether the program's uniform block travels through {@link #uniformBuffer} instead of push constants. */
    private final boolean uniformsViaBuffer;
    /** The uniform block's byte size on the buffer route; 0 on the push route. */
    private final int uniformBlockBytes;
    /** Slots of {@link #uniformBuffer} the descriptor sets can name; the post chain's ring depth. */
    private static final int UNIFORM_RING_SLOTS = 32;
    private VulkanUniformBuffer uniformBuffer;
    /** The constant-attribute ring (binding 1, stride 0); null for a pipeline whose layout feeds every attribute. */
    private VulkanUniformBuffer constantRing;
    private int constantCursor = -1;
    private float lastConstantRed = Float.NaN;
    private float lastConstantGreen;
    private float lastConstantBlue;
    private float lastConstantAlpha;
    private int uniformStride;
    private int uniformCursor;
    private long uniformEpoch = Long.MIN_VALUE;
    private int uniformClaimsThisEpoch;
    private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap uniformSlotsByVersion =
            new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
    {
        this.uniformSlotsByVersion.defaultReturnValue(-1);
    }
    /** Whether the pass blends (its render state as built); the readback instrument looks for the blend-off copy. */
    boolean blends;
    private long setLayout = VK_NULL_HANDLE;
    private long pipelineLayout = VK_NULL_HANDLE;
    private long pipeline = VK_NULL_HANDLE;
    private final java.util.List<Long> descriptorPools = new java.util.ArrayList<Long>();
    private static final VulkanGlTexture[] EMPTY_IMAGES = new VulkanGlTexture[0];
    private static final long[] EMPTY_LONG = new long[0];
    private static final int[] EMPTY_INT = new int[0];
    private final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap setsByKey =
            new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
    /**
     * The inputs each cached set was actually written from, packed by {@link #celtium$packSetInputs}, one per
     * {@link #setsByKey} key. The key is a 64-bit hash with no equality check behind it, so a hit is verified
     * against this snapshot before the set is handed out: a collision would otherwise bind another draw's
     * textures for the rest of the pipeline's life.
     */
    private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<long[]> setInputsByKey =
            new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    private long[] lastSamplerInputs;
    /** Verified hash collisions on the set cache: counted, so a collision is visible rather than silent. */
    private int descriptorSetKeyCollisions;
    private final long[] scratchViews;
    private final long[] scratchSamplers;
    private final int[] scratchLayouts;
    private final long[] scratchImageViews;
    private long lastSamplerKey;
    private long lastSamplerSet;
    /** Sets one pool holds, and max pools a pipeline may grow to (matches VulkanImmediateTexturePipeline). */
    private static final int MAX_SETS = 64;
    private static final int MAX_POOLS = 64;

    private VulkanModProgramPipeline(VulkanContext context, List<ModGlslTranslator.Attribute> attributes,
                                     List<ModGlslTranslator.SamplerBinding> samplers,
                                     List<ModGlslTranslator.ImageBinding> images, int pushBytes,
                                     boolean uniformsViaBuffer) {
        this.context = context;
        this.attributes = attributes;
        this.samplers = samplers;
        this.samplerCount = samplers.size();
        this.images = images;
        this.imageCount = images.size();
        this.uniformsViaBuffer = uniformsViaBuffer;
        this.uniformBlockBytes = uniformsViaBuffer ? pushBytes : 0;
        this.pushBytes = uniformsViaBuffer ? 0 : pushBytes;
        this.scratchViews = this.samplerCount > 0 ? new long[this.samplerCount] : EMPTY_LONG;
        this.scratchSamplers = this.samplerCount > 0 ? new long[this.samplerCount] : EMPTY_LONG;
        this.scratchLayouts = this.samplerCount > 0 ? new int[this.samplerCount] : EMPTY_INT;
        this.scratchImageViews = this.imageCount > 0 ? new long[this.imageCount] : EMPTY_LONG;
    }

    /**
     * {@return why this program and batch layout cannot be drawn}, or null when they can. Checked before any Vulkan
     * object is created, so a refusal costs nothing.
     */
    public static String unsupported(ModProgramRegistry.Program program, int formatId) {
        if (program == null || program.lowered() == null || !program.lowered().ok()) {
            return "the program is not lowered";
        }
        if (!program.compiled()) {
            return "the program has no SPIR-V (" + program.compileFailure() + ")";
        }
        return unsupportedAttributes(program.lowered().attributes(), formatId);
    }

    /**
     * {@return why this program's attributes cannot be read from this batch layout}, or null when they can - the
     * format half of {@link #unsupported}, split out so both halves are testable without a device: which formats map
     * onto program attributes at all, and which attribute reads which of the layout's fields.
     * <p>
     * {@code POSITION} (id 7) maps its one field: the layout is twelve bytes of position and nothing else, so a
     * program whose attributes all read the position draws with it, and any other attribute is refused by name in
     * {@link #source}. The measured arrival is Iris's horizon mesh (an octagonal prism and two gridded planes,
     * 1,384 position-only vertices submitted every frame from {@code HorizonRenderer}): the sky program it is
     * submitted under also reads a colour, which GL supplies from the current generic attribute - the pipeline
     * sets it to the fog colour immediately before the draw - so that program stays refused here until the
     * constant-attribute question is answered, now with the attribute named instead of the whole format.
     */
    static String unsupportedAttributes(List<ModGlslTranslator.Attribute> attributes, int formatId) {
        if (formatId != VulkanImmediateFormats.POSITION_TEX && formatId != VulkanImmediateFormats.POSITION_TEX_COLOR
                && formatId != VulkanImmediateFormats.POSITION) {
            return "batch format " + formatId + " is not mapped onto program attributes";
        }
        for (ModGlslTranslator.Attribute attribute : attributes) {
            if (source(attribute, formatId) == null) {
                return "attribute " + attribute.name() + " has no source in format " + formatId;
            }
        }
        return null;
    }

    /**
     * {@return the batch field this attribute reads}, or null when this layout has no such field.
     * <p>
     * Names first, then the location-0 alias: a vertex, a texture coordinate or a colour is identified by what the
     * source called it, whichever location the program gave it, while GL's generic attribute 0 with no known name is
     * the position - a rule that has to stay, because a legacy {@code attribute vec3 position;} is lowered to location
     * 0 under its own name and a name whitelist alone would refuse every such program. An unknown attribute is null,
     * never a guess: the same bytes read as the wrong field draw a wrong picture with no Vulkan error to show for it.
     */
    static Source source(ModGlslTranslator.Attribute attribute, int formatId) {
        if (POSITION_NAMES.contains(attribute.name())) {
            return Source.POSITION;
        }
        if ("iris_MultiTexCoord1".equals(attribute.name())) {
            // Iris's second texture coordinate (injected at location 3). The fullscreen-quad path folds its two
            // attributes at offset 12 onto the one UV the batch carries, and POSITION_TEX is that path's layout, so
            // the name reads TEXCOORD there. POSITION_TEX_COLOR has no second UV field at all - the same ground on
            // which the fixed-function generator's a_TexCoord1 stays refused - so it is refused there rather than
            // answered with the first UV. Terrain programs, whose second coordinate is a light map, do not come
            // through this gate (they travel the Celeritas channel), so the fold cannot leak into them.
            return formatId == VulkanImmediateFormats.POSITION_TEX ? Source.TEXCOORD : null;
        }
        if (TEXCOORD_NAMES.contains(attribute.name())) {
            // POSITION carries no UV element: offset 12 in a 12-byte vertex is the next vertex's position, so the
            // name is answered only by the layouts that carry the field. A program that reads a texture coordinate
            // the batch does not carry is refused here, as a colour read on a colourless layout already is.
            return formatId == VulkanImmediateFormats.POSITION_TEX
                    || formatId == VulkanImmediateFormats.POSITION_TEX_COLOR ? Source.TEXCOORD : null;
        }
        if (COLOR_NAMES.contains(attribute.name())) {
            // POSITION_TEX has no colour element; a program that reads one needs the colour layout, not this batch.
            // POSITION is answered with GL's current colour (the constant attribute): its measured arrival is Iris's
            // horizon mesh, drawn by the sky program right after glColor4f(fog colour), which is exactly the value GL
            // would hand gl_Color there. POSITION_TEX stays refused until a measured case says what feeds it.
            if (formatId == VulkanImmediateFormats.POSITION_TEX_COLOR) {
                return Source.COLOR;
            }
            return formatId == VulkanImmediateFormats.POSITION ? Source.CONSTANT_COLOR : null;
        }
        return attribute.location() == 0 ? Source.POSITION : null;
    }

    /** {@return whether any of these attributes reads the constant ring on this layout} (static and pure, for tests). */
    static boolean readsConstant(List<ModGlslTranslator.Attribute> attributes, int formatId) {
        for (ModGlslTranslator.Attribute attribute : attributes) {
            if (source(attribute, formatId) == Source.CONSTANT_COLOR) {
                return true;
            }
        }
        return false;
    }

    /**
     * Binds the constant ring at {@code offset} on {@link #CONSTANT_BINDING}. Binding 1 is not part of the shared
     * bind cache (which names binding 0 only), so binding it here cannot make a later batch skip its own bind.
     */
    void bindConstant(VkCommandBuffer commandBuffer, long offset) {
        VulkanUniformBuffer ring = this.constantRing;
        if (ring == null || offset < 0L) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkCmdBindVertexBuffers(commandBuffer, CONSTANT_BINDING, stack.longs(ring.buffer()), stack.longs(offset));
        }
    }

    /** {@return whether this pipeline reads GL's current colour from the constant ring} */
    public boolean readsConstant() {
        return this.constantRing != null;
    }

    /**
     * {@return the byte offset of a ring slot holding this colour}, or -1 when the pipeline has no ring. Written at
     * prepare time, when the backend's colour mirror holds the value GL would give the draw; the recorded draw binds
     * the slot later. An unchanged colour reuses the previous slot, so a run of same-coloured draws costs one write.
     */
    public long constantColorOffset(float red, float green, float blue, float alpha) {
        VulkanUniformBuffer ring = this.constantRing;
        if (ring == null) {
            return -1L;
        }
        if (this.constantCursor >= 0 && red == this.lastConstantRed && green == this.lastConstantGreen
                && blue == this.lastConstantBlue && alpha == this.lastConstantAlpha) {
            return (long) this.constantCursor * CONSTANT_SLOT_BYTES;
        }
        this.constantCursor = (this.constantCursor + 1) % CONSTANT_RING_SLOTS;
        long address = ring.mappedAddress() + (long) this.constantCursor * CONSTANT_SLOT_BYTES;
        MemoryUtil.memPutFloat(address, red);
        MemoryUtil.memPutFloat(address + 4L, green);
        MemoryUtil.memPutFloat(address + 8L, blue);
        MemoryUtil.memPutFloat(address + 12L, alpha);
        this.lastConstantRed = red;
        this.lastConstantGreen = green;
        this.lastConstantBlue = blue;
        this.lastConstantAlpha = alpha;
        return (long) this.constantCursor * CONSTANT_SLOT_BYTES;
    }

    /** Builds the pipeline with the render state of {@code like}; throws on a Vulkan failure. */
    public static VulkanModProgramPipeline create(VulkanContext context, ModProgramRegistry.Program program,
                                                  int formatId, VulkanImmediateTexturePipeline like) {
        ModGlslTranslator.Result lowered = program.lowered();
        VulkanModProgramPipeline built = new VulkanModProgramPipeline(context, lowered.attributes(), lowered.samplers(),
                lowered.images(), lowered.uniformBlockSize(), lowered.uniformsViaBuffer());
        if (!lowered.images().isEmpty() && !context.celtium$storageImagesWithoutFormat()) {
            throw new IllegalStateException("storage images without format qualifiers are not supported by this device "
                    + "(shaderStorageImageRead/WriteWithoutFormat=false)");
        }
        try {
            built.createLayouts();
            built.createPipeline(program.vertexSpirv(), program.fragmentSpirv(), formatId,
                    like.celtium$renderPassHandle(), like.celtium$cull(), like.celtium$depthTest(),
                    like.celtium$depthWrite(), like.celtium$depthCompareOp(), like.celtium$colorBlend(), true, 1);
        } catch (RuntimeException | LinkageError failure) {
            built.delete();
            throw failure;
        }
        return built;
    }

    /**
     * Builds the pipeline against an explicit render pass with {@code colorAttachments} colour attachments -
     * the multi-target form, for a program whose fragment shader writes several outputs into a framebuffer
     * that attaches that many targets. The blend state is the {@code like} variant's, applied to every
     * attachment: GL has one blend state for all draw buffers unless a mod sets the per-buffer form, which the
     * translator does not lower.
     */
    public static VulkanModProgramPipeline create(VulkanContext context, ModProgramRegistry.Program program,
                                                  int formatId, VulkanImmediateTexturePipeline like,
                                                  long renderPass, int colorAttachments) {
        ModGlslTranslator.Result lowered = program.lowered();
        VulkanModProgramPipeline built = new VulkanModProgramPipeline(context, lowered.attributes(), lowered.samplers(),
                lowered.images(), lowered.uniformBlockSize(), lowered.uniformsViaBuffer());
        if (!lowered.images().isEmpty() && !context.celtium$storageImagesWithoutFormat()) {
            throw new IllegalStateException("storage images without format qualifiers are not supported by this device "
                    + "(shaderStorageImageRead/WriteWithoutFormat=false)");
        }
        try {
            built.createLayouts();
            built.createPipeline(program.vertexSpirv(), program.fragmentSpirv(), formatId,
                    renderPass, like.celtium$cull(), like.celtium$depthTest(),
                    like.celtium$depthWrite(), like.celtium$depthCompareOp(), like.celtium$colorBlend(), true,
                    colorAttachments);
        } catch (RuntimeException | LinkageError failure) {
            built.delete();
            throw failure;
        }
        return built;
    }

    /**
     * Builds a pipeline for a native fullscreen pass from pre-compiled SPIR-V and explicit pipeline state.
     * Reuses the same layout, descriptor pool, shader module and prepared-draw path as mod programs.
     */
    public static VulkanModProgramPipeline createForNativePass(VulkanContext context,
                                                               byte[] vertexSpirv,
                                                               byte[] fragmentSpirv,
                                                               int samplerCount,
                                                               int pushBytes,
                                                               VulkanColorBlendState colorBlend,
                                                               boolean depthTest,
                                                               boolean depthWrite,
                                                               int depthCompareOp,
                                                               int cull,
                                                               long renderPass) {
        if (vertexSpirv == null || fragmentSpirv == null) {
            throw new IllegalArgumentException("vertex and fragment SPIR-V must be non-null");
        }
        List<ModGlslTranslator.Attribute> attrs = List.of(
                new ModGlslTranslator.Attribute("position", "vec3", 0),
                new ModGlslTranslator.Attribute("celtium_MultiTexCoord0", "vec2", 1));
        java.util.ArrayList<ModGlslTranslator.SamplerBinding> bindings = new java.util.ArrayList<>(samplerCount);
        for (int i = 0; i < samplerCount; i++) {
            bindings.add(new ModGlslTranslator.SamplerBinding("input" + i, "sampler2D", i));
        }
        VulkanModProgramPipeline built = new VulkanModProgramPipeline(context, attrs,
                java.util.Collections.unmodifiableList(bindings), List.of(), Math.max(0, pushBytes), false);
        try {
            built.createLayouts();
            built.createPipeline(vertexSpirv, fragmentSpirv, VulkanImmediateFormats.POSITION_TEX, renderPass, cull,
                    depthTest, depthWrite, depthCompareOp,
                    colorBlend != null ? colorBlend : VulkanColorBlendState.legacy(), false, 1);
        } catch (RuntimeException | LinkageError failure) {
            built.delete();
            throw failure;
        }
        return built;
    }

    /** Creates a prepared draw bound to this pipeline. */
    public Draw createDraw(ByteBuffer uniforms, long samplerSet, String label, VulkanGlTexture[] inputs) {
        return new Draw(this, uniforms, samplerSet, label, inputs, EMPTY_IMAGES);
    }

    public Draw createDraw(ByteBuffer uniforms, long descriptorSet, String label, VulkanGlTexture[] inputs,
                           VulkanGlTexture[] images) {
        return new Draw(this, uniforms, descriptorSet, label, inputs, images);
    }

    private void createLayouts() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer handle = stack.mallocLong(1);
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(
                    this.samplerCount + this.imageCount + (this.uniformsViaBuffer ? 1 : 0), stack);
            for (int i = 0; i < this.samplerCount; i++) {
                bindings.get(i).binding(i).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                        .descriptorCount(1).stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT | VK_SHADER_STAGE_VERTEX_BIT);
            }
            for (int i = 0; i < this.imageCount; i++) {
                bindings.get(this.samplerCount + i).binding(this.samplerCount + i)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).descriptorCount(1)
                        .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            }
            if (this.uniformsViaBuffer) {
                // The binding the lowering named for the block: after every sampler and storage image. Both
                // stages read the block (the fixed-function matrices live in the vertex stage), as the push
                // range it replaces did.
                bindings.get(this.samplerCount + this.imageCount)
                        .binding(this.samplerCount + this.imageCount)
                        .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(1)
                        .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT | VK_SHADER_STAGE_VERTEX_BIT);
            }
            VkDescriptorSetLayoutCreateInfo setInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default()
                    .pBindings(bindings);
            check(vkCreateDescriptorSetLayout(this.context.device(), setInfo, null, handle),
                    "vkCreateDescriptorSetLayout(mod program)");
            this.setLayout = handle.get(0);

            VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
                    .pSetLayouts(stack.longs(this.setLayout));
            if (this.pushBytes > 0) {
                VkPushConstantRange.Buffer range = VkPushConstantRange.calloc(1, stack);
                range.get(0).stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT).offset(0)
                        .size(this.pushBytes);
                layoutInfo.pPushConstantRanges(range);
            }
            check(vkCreatePipelineLayout(this.context.device(), layoutInfo, null, handle),
                    "vkCreatePipelineLayout(mod program)");
            this.pipelineLayout = handle.get(0);

            if (this.samplerCount + this.imageCount > 0 || this.uniformsViaBuffer) {
                if (createDescriptorPool(stack) == VK_NULL_HANDLE) {
                    throw new IllegalStateException("vkCreateDescriptorPool(mod program) failed");
                }
            }
            if (this.uniformsViaBuffer) {
                // Fixed at the ring's full size: the buffer handle is baked into descriptor sets, so it is
                // never grown or replaced the way the terrain's own uniform buffer is.
                this.uniformStride = uniformStride(this.context, this.uniformBlockBytes);
                this.uniformBuffer = new VulkanUniformBuffer(this.context,
                        (long) this.uniformStride * UNIFORM_RING_SLOTS);
            }
        }
    }

    private long createDescriptorPool(MemoryStack stack) {
        if (this.descriptorPools.size() >= MAX_POOLS) {
            return VK_NULL_HANDLE;
        }
        VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc((this.samplerCount > 0 ? 1 : 0)
                + (this.imageCount > 0 ? 1 : 0) + (this.uniformsViaBuffer ? 1 : 0), stack);
        int sizeIndex = 0;
        if (this.samplerCount > 0) sizes.get(sizeIndex++).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(MAX_SETS * this.samplerCount);
        if (this.imageCount > 0) sizes.get(sizeIndex++).type(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                .descriptorCount(MAX_SETS * this.imageCount);
        if (this.uniformsViaBuffer) sizes.get(sizeIndex).type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .descriptorCount(MAX_SETS);
        VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack).sType$Default()
                .maxSets(MAX_SETS).pPoolSizes(sizes);
        LongBuffer handle = stack.mallocLong(1);
        if (vkCreateDescriptorPool(this.context.device(), poolInfo, null, handle) != VK_SUCCESS) {
            return VK_NULL_HANDLE;
        }
        long pool = handle.get(0);
        this.descriptorPools.add(Long.valueOf(pool));
        return pool;
    }

    private long allocateDescriptorSet(MemoryStack stack) {
        for (int attempt = 0; attempt < 2; attempt++) {
            if (this.descriptorPools.isEmpty()) {
                return 0L;
            }
            long pool = this.descriptorPools.get(this.descriptorPools.size() - 1).longValue();
            VkDescriptorSetAllocateInfo allocate = VkDescriptorSetAllocateInfo.calloc(stack).sType$Default()
                    .descriptorPool(pool).pSetLayouts(stack.longs(this.setLayout));
            LongBuffer handle = stack.mallocLong(1);
            if (vkAllocateDescriptorSets(this.context.device(), allocate, handle) == VK_SUCCESS) {
                return handle.get(0);
            }
            if (createDescriptorPool(stack) == VK_NULL_HANDLE) {
                break;
            }
        }
        return 0L;
    }

    /**
     * {@return the ring stride for a block of {@code blockBytes}}: the block size rounded up to the device's
     * uniform offset alignment, floored at 256 - the same floor {@link VulkanUniformBuffer} aligns its own
     * sizes to.
     */
    private static int uniformStride(VulkanContext context, int blockBytes) {
        long alignment = 256;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(context.physicalDevice(), properties);
            alignment = Math.max(256, properties.limits().minUniformBufferOffsetAlignment());
        }
        long mask = alignment - 1;
        return (int) ((blockBytes + mask) & ~mask);
    }

    private void createPipeline(byte[] vertexSpirv, byte[] fragmentSpirv, int formatId, long renderPass, int cull,
                                boolean depthTest, boolean depthWrite, int depthCompareOp,
                                VulkanColorBlendState colorBlend, boolean honourModProgramFlags,
                                int colorAttachments) {
        ByteBuffer vertexCode = MemoryUtil.memAlloc(vertexSpirv.length).put(vertexSpirv).flip();
        ByteBuffer fragmentCode = MemoryUtil.memAlloc(fragmentSpirv.length).put(fragmentSpirv).flip();
        long vertexModule = VK_NULL_HANDLE;
        long fragmentModule = VK_NULL_HANDLE;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer handle = stack.mallocLong(1);
            VkShaderModuleCreateInfo moduleInfo = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(vertexCode);
            check(vkCreateShaderModule(this.context.device(), moduleInfo, null, handle), "vkCreateShaderModule(mod vertex)");
            vertexModule = handle.get(0);
            moduleInfo.pCode(fragmentCode);
            check(vkCreateShaderModule(this.context.device(), moduleInfo, null, handle),
                    "vkCreateShaderModule(mod fragment)");
            fragmentModule = handle.get(0);

            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            stages.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertexModule).pName(stack.UTF8("main"));
            stages.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragmentModule)
                    .pName(stack.UTF8("main"));

            int stride = formatId == VulkanImmediateFormats.POSITION_TEX_COLOR
                    ? VulkanImmediateTexturePipeline.VERTEX_BYTES
                    : formatId == VulkanImmediateFormats.POSITION
                            ? VulkanImmediateTexturePipeline.POSITION_BYTES
                            : VulkanImmediateTexturePipeline.POSITION_TEX_BYTES;
            List<ModGlslTranslator.Attribute> declared = this.attributes;
            boolean constant = readsConstant(declared, formatId);
            VkVertexInputBindingDescription.Buffer binding =
                    VkVertexInputBindingDescription.calloc(constant ? 2 : 1, stack);
            binding.get(0).binding(0).stride(stride).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
            if (constant) {
                // Stride 0: every vertex reads the same four floats, the draw's current colour.
                binding.get(1).binding(CONSTANT_BINDING).stride(0).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
                if (this.constantRing == null) {
                    this.constantRing = new VulkanUniformBuffer(this.context,
                            (long) CONSTANT_SLOT_BYTES * CONSTANT_RING_SLOTS, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
                }
            }
            VkVertexInputAttributeDescription.Buffer attributes =
                    VkVertexInputAttributeDescription.calloc(declared.size(), stack);
            for (int i = 0; i < declared.size(); i++) {
                ModGlslTranslator.Attribute attribute = declared.get(i);
                Source field = source(attribute, formatId);
                if (field == null) {
                    // unsupported() admitted no attribute without a source, so this is a caller's mistake, not a
                    // layout fact: refuse loudly instead of falling back to the position and reading its bytes.
                    throw new IllegalStateException("attribute " + attribute.name() + " has no source in format "
                            + formatId);
                }
                attributes.get(i).location(attribute.location()).binding(field.binding())
                        .format(field.format).offset(field.offset);
            }
            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType$Default().pVertexBindingDescriptions(binding).pVertexAttributeDescriptions(attributes);
            VkPipelineInputAssemblyStateCreateInfo assembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType$Default().topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
            VkPipelineViewportStateCreateInfo viewport = VkPipelineViewportStateCreateInfo.calloc(stack).sType$Default()
                    .viewportCount(1).scissorCount(1);
            VkPipelineRasterizationStateCreateInfo raster = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType$Default().polygonMode(VK_POLYGON_MODE_FILL).depthBiasEnable(true)
                    .cullMode(VulkanCullState.vkCullMode(cull)).frontFace(VulkanCullState.vkFrontFace(cull))
                    .lineWidth(1.0f);
            VkPipelineMultisampleStateCreateInfo multisample = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType$Default().rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
            // Lab switch -Dceltium.vulkan.modProgramDepth=false: no depth test or write for mod passes, to separate a
            // full-screen quad's depth from its colour when a pass misbehaves (round 8 of the GTCEu bloom evidence).
            boolean depthOn = !honourModProgramFlags
                    || !"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.modProgramDepth", "true"));
            VkPipelineDepthStencilStateCreateInfo depth = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType$Default().depthTestEnable(depthOn && depthTest)
                    .depthWriteEnable(depthOn && depthWrite).depthCompareOp(depthCompareOp);
            VulkanColorBlendState effectiveBlend = colorBlend;
            if (honourModProgramFlags && FORCE_ADDITIVE_OPAQUE && !effectiveBlend.blend()) {
                // Lab: a blend-off pass drawn additively instead, so a black result says "it read black" and a
                // saturated one says "it read the picture and the output was lost" (round 8f of the bloom evidence).
                effectiveBlend = VulkanColorBlendState.of(true, VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ONE,
                        VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ONE, VK_BLEND_OP_ADD, VK_BLEND_OP_ADD, 0xF);
            }
            this.blends = effectiveBlend.blend();
            // One blend attachment state per colour attachment the render pass has: the count must match the
            // pass's subpass, and the state is the same for each - GL applies one blend state to every draw
            // buffer, so a multi-target program blends all its outputs the way it blends its first.
            int blendCount = Math.max(1, colorAttachments);
            VkPipelineColorBlendAttachmentState.Buffer blendAttachment =
                    VkPipelineColorBlendAttachmentState.calloc(blendCount, stack);
            for (int i = 0; i < blendCount; i++) {
                blendAttachment.get(i).blendEnable(effectiveBlend.blend())
                        .srcColorBlendFactor(effectiveBlend.srcColor()).dstColorBlendFactor(effectiveBlend.dstColor())
                        .colorBlendOp(effectiveBlend.colorOp())
                        .srcAlphaBlendFactor(effectiveBlend.srcAlpha()).dstAlphaBlendFactor(effectiveBlend.dstAlpha())
                        .alphaBlendOp(effectiveBlend.alphaOp()).colorWriteMask(effectiveBlend.writeMask());
            }
            VkPipelineColorBlendStateCreateInfo blend = VkPipelineColorBlendStateCreateInfo.calloc(stack).sType$Default()
                    .logicOpEnable(false).pAttachments(blendAttachment);
            // The same dynamic states as the immediate variants, so the group loop's per-group commands stay valid.
            VkPipelineDynamicStateCreateInfo dynamic = VkPipelineDynamicStateCreateInfo.calloc(stack).sType$Default()
                    .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR,
                            VK_DYNAMIC_STATE_DEPTH_BIAS));
            VkGraphicsPipelineCreateInfo.Buffer create = VkGraphicsPipelineCreateInfo.calloc(1, stack);
            create.get(0).sType$Default().pStages(stages).pVertexInputState(vertexInput)
                    .pInputAssemblyState(assembly).pViewportState(viewport).pRasterizationState(raster)
                    .pMultisampleState(multisample).pDepthStencilState(depth).pColorBlendState(blend)
                    .pDynamicState(dynamic).layout(this.pipelineLayout).renderPass(renderPass)
                    .subpass(0);
            check(VulkanContext.createGraphicsPipelines(this.context.device(), create, handle, "mod-program"),
                    "vkCreateGraphicsPipelines(mod program)");
            this.pipeline = handle.get(0);
        } finally {
            if (vertexModule != VK_NULL_HANDLE) {
                vkDestroyShaderModule(this.context.device(), vertexModule, null);
            }
            if (fragmentModule != VK_NULL_HANDLE) {
                vkDestroyShaderModule(this.context.device(), fragmentModule, null);
            }
            MemoryUtil.memFree(vertexCode);
            MemoryUtil.memFree(fragmentCode);
        }
    }

    /**
     * {@return the sampler set for these textures (binding i = textures[i])}, cached by their keys; 0 when the pool is
     * exhausted, which the caller refuses as a missing draw.
     */
    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public long samplerSet(VulkanGlTexture[] textures) {
        return samplersAndImages(textures, EMPTY_IMAGES, null, 0L);
    }

    /**
     * Builds the one descriptor set from sampler bindings followed by storage-image bindings - and, on the
     * uniform-buffer route, the block's binding, which names the ring slot {@code program}'s current uniform
     * values were staged into for {@code epoch} (the caller's frame counter). 0 when no set could be allocated
     * or the epoch claimed every ring slot, which the caller refuses as a missing draw.
     */
    public long samplersAndImages(VulkanGlTexture[] textures, VulkanGlTexture[] imageTextures,
                                  ModProgramRegistry.Program program, long epoch) {
        if (this.samplerCount + this.imageCount == 0 && !this.uniformsViaBuffer) {
            return 0L;
        }
        long hash = 0x9E3779B97F4A7C15L;
        long[] views = this.scratchViews;
        long[] samplers = this.scratchSamplers;
        int[] layouts = this.scratchLayouts;
        for (int i = 0; i < this.samplerCount; i++) {
            VulkanGlTexture tex = textures[i];
            long view = tex.view();
            long sampler = tex.sampler();
            int layout = tex.samplingLayout();
            views[i] = view;
            samplers[i] = sampler;
            layouts[i] = layout;
            hash = mix64(hash ^ tex.key()) ^ mix64(view ^ (sampler * 31L) ^ layout);
        }
        long[] imageViews = this.scratchImageViews;
        for (int i = 0; i < this.imageCount; i++) {
            VulkanGlTexture img = imageTextures[i];
            long view = img.view();
            imageViews[i] = view;
            hash = mix64(hash ^ img.key() ^ 0x517CC1B727220A95L) ^ mix64(view);
        }
        if (hash == 0L) {
            hash = 1L;
        }
        if (!this.uniformsViaBuffer) {
            return allocateOrReuseDescriptorSet(hash, views, samplers, layouts, imageViews, -1);
        }
        if (program == null) {
            return 0L;
        }
        int slot = uniformSlot(program, epoch);
        if (slot < 0) {
            return 0L;
        }
        hash = mix64(hash ^ (slot + 1L) * 0x9E3779B97F4A7C15L) ^ mix64(this.uniformBuffer.buffer());
        if (hash == 0L) {
            hash = 1L;
        }
        return allocateOrReuseDescriptorSet(hash, views, samplers, layouts, imageViews, slot);
    }

    /**
     * {@return the uniform ring slot holding this program's current uniform values}, staging them on their
     * first use in this epoch, or -1 when this epoch has already claimed every slot.
     * <p>
     * The epoch is the caller's frame counter: a new epoch forgets the version-to-slot map, because the values
     * a version named last frame are not the values it names now - the map exists so a program drawn by several
     * passes in one frame is written once, not because its bytes survive the frame. A claimed slot is written
     * before any set naming it is bound, and the ring does not hand it out again until it comes back around -
     * {@link #UNIFORM_RING_SLOTS} claims later, far beyond the frames in flight - which is the same lifetime
     * argument as the post chain's descriptor ring. Claims past the ring depth inside one epoch are refused
     * rather than overwriting a block a pending draw still reads.
     */
    private int uniformSlot(ModProgramRegistry.Program program, long epoch) {
        if (epoch != this.uniformEpoch) {
            this.uniformEpoch = epoch;
            this.uniformSlotsByVersion.clear();
            this.uniformClaimsThisEpoch = 0;
        }
        long version = program.uniformVersion();
        int staged = this.uniformSlotsByVersion.get(version);
        if (staged >= 0) {
            return staged;
        }
        if (this.uniformClaimsThisEpoch >= UNIFORM_RING_SLOTS) {
            return -1;
        }
        int slot = this.uniformCursor;
        this.uniformCursor = (this.uniformCursor + 1) % UNIFORM_RING_SLOTS;
        ByteBuffer packed = packUniforms(program);
        MemoryUtil.memCopy(MemoryUtil.memAddress0(packed),
                this.uniformBuffer.mappedAddress() + (long) slot * this.uniformStride,
                this.uniformBlockBytes);
        this.uniformSlotsByVersion.put(version, slot);
        this.uniformClaimsThisEpoch++;
        return slot;
    }

    /** Allocates or reuses a descriptor set binding explicit image views, samplers and layouts. */
    public long samplerSetForHandles(long[] views, long[] samplers, int[] layouts, long[] generations) {
        return samplerSetForHandles(views, samplers, layouts, generations, this.samplerCount);
    }

    public long samplerSetForHandles(long[] views, long[] samplers, int[] layouts, long[] generations, int count) {
        if (this.samplerCount == 0 || count <= 0) {
            return 0L;
        }
        // The set is written from all samplerCount entries, so the key covers all of them. Hashing only the
        // caller's first count entries cached sets whose tail bindings - whatever the caller's scratch arrays
        // still held there - the key never saw, and a later call with the same head but a different tail was
        // handed that set as if it were its own.
        return allocateOrReuseDescriptorSet(
                samplerSetHash(views, samplers, layouts, generations, this.samplerCount),
                views, samplers, layouts, EMPTY_LONG, -1);
    }

    /** The sampler-set key: a hash of exactly the entries the descriptor set is written from. */
    static long samplerSetHash(long[] views, long[] samplers, int[] layouts, long[] generations, int samplerCount) {
        long hash = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < samplerCount; i++) {
            hash = mix64(hash ^ views[i]) ^ mix64(samplers[i]) ^ mix64(generations[i] ^ ((long) layouts[i] << 32));
        }
        return hash == 0L ? 1L : hash;
    }

    /** Packs the inputs a descriptor set is written from, for the collision check behind the hash key. */
    static long[] celtium$packSetInputs(long[] views, long[] samplers, int[] layouts, long[] imageViews,
                                        int samplerCount, int imageCount, int uniformSlot) {
        long[] snapshot = new long[1 + 3 * samplerCount + imageCount];
        snapshot[0] = uniformSlot;
        for (int i = 0; i < samplerCount; i++) {
            snapshot[1 + i] = views[i];
            snapshot[1 + samplerCount + i] = samplers[i];
            snapshot[1 + 2 * samplerCount + i] = layouts[i];
        }
        for (int i = 0; i < imageCount; i++) {
            snapshot[1 + 3 * samplerCount + i] = imageViews[i];
        }
        return snapshot;
    }

    /** Whether a packed snapshot names exactly the inputs a set would be written from now. */
    static boolean celtium$setInputsMatch(long[] snapshot, long[] views, long[] samplers, int[] layouts,
                                          long[] imageViews, int samplerCount, int imageCount, int uniformSlot) {
        if (snapshot == null || snapshot.length != 1 + 3 * samplerCount + imageCount || snapshot[0] != uniformSlot) {
            return false;
        }
        for (int i = 0; i < samplerCount; i++) {
            if (snapshot[1 + i] != views[i] || snapshot[1 + samplerCount + i] != samplers[i]
                    || snapshot[1 + 2 * samplerCount + i] != layouts[i]) {
                return false;
            }
        }
        for (int i = 0; i < imageCount; i++) {
            if (snapshot[1 + 3 * samplerCount + i] != imageViews[i]) {
                return false;
            }
        }
        return true;
    }

    int descriptorSetKeyCollisions() {
        return this.descriptorSetKeyCollisions;
    }

    private long allocateOrReuseDescriptorSet(long key, long[] views, long[] samplers, int[] layouts,
                                              long[] imageViews, int uniformSlot) {
        if (key == this.lastSamplerKey && this.lastSamplerSet != 0L
                && celtium$setInputsMatch(this.lastSamplerInputs, views, samplers, layouts, imageViews,
                        this.samplerCount, this.imageCount, uniformSlot)) {
            return this.lastSamplerSet;
        }
        long cached = this.setsByKey.get(key);
        if (cached != 0L) {
            long[] snapshot = this.setInputsByKey.get(key);
            if (celtium$setInputsMatch(snapshot, views, samplers, layouts, imageViews,
                    this.samplerCount, this.imageCount, uniformSlot)) {
                this.lastSamplerKey = key;
                this.lastSamplerSet = cached;
                this.lastSamplerInputs = snapshot;
                return cached;
            }
            // A hash collision: the cached set was written from different inputs and would sample another
            // draw's textures for the rest of this pipeline's life. Count it and fall through to allocate a
            // set for these inputs; the old set stays valid for whoever still holds it, it is just no longer
            // this key's answer.
            this.descriptorSetKeyCollisions++;
        }
        if (cached == 0L && this.setsByKey.size() >= MAX_SETS * MAX_POOLS) {
            return 0L;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long set = allocateDescriptorSet(stack);
            if (set == 0L) {
                return 0L;
            }
            VkDescriptorImageInfo.Buffer imageInfos = VkDescriptorImageInfo.calloc(this.samplerCount + this.imageCount, stack);
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(
                    this.samplerCount + this.imageCount + (uniformSlot >= 0 ? 1 : 0), stack);
            for (int i = 0; i < this.samplerCount; i++) {
                imageInfos.get(i).imageLayout(layouts[i]).imageView(views[i]).sampler(samplers[i]);
                writes.get(i).sType$Default().dstSet(set).dstBinding(i)
                        .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(1)
                        .pImageInfo(VkDescriptorImageInfo.create(imageInfos.address(i), 1));
            }
            for (int i = 0; i < this.imageCount; i++) {
                int index = this.samplerCount + i;
                imageInfos.get(index).imageLayout(VK_IMAGE_LAYOUT_GENERAL).imageView(imageViews[i]).sampler(VK_NULL_HANDLE);
                writes.get(index).sType$Default().dstSet(set).dstBinding(index)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).descriptorCount(1)
                        .pImageInfo(VkDescriptorImageInfo.create(imageInfos.address(index), 1));
            }
            if (uniformSlot >= 0) {
                VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
                        .buffer(this.uniformBuffer.buffer())
                        .offset((long) uniformSlot * this.uniformStride)
                        .range(this.uniformBlockBytes);
                writes.get(this.samplerCount + this.imageCount).sType$Default().dstSet(set)
                        .dstBinding(this.samplerCount + this.imageCount)
                        .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(1)
                        .pBufferInfo(bufferInfo);
            }
            vkUpdateDescriptorSets(this.context.device(), writes, null);
            long[] snapshot = celtium$packSetInputs(views, samplers, layouts, imageViews,
                    this.samplerCount, this.imageCount, uniformSlot);
            this.setsByKey.put(key, set);
            this.setInputsByKey.put(key, snapshot);
            this.lastSamplerKey = key;
            this.lastSamplerSet = set;
            this.lastSamplerInputs = snapshot;
            return set;
        }
    }

    /**
     * {@return the program's uniform block packed at its std140 offsets} from the values {@code glUniform*} recorded;
     * a uniform never set is zero, as in GL.
     */
    public static ByteBuffer packUniforms(ModProgramRegistry.Program program) {
        ByteBuffer cached = program.cachedPackedUniforms();
        if (cached != null) {
            return cached;
        }
        long version = program.uniformVersion();
        ModGlslTranslator.Result lowered = program.lowered();
        ByteBuffer block = program.reusablePackedUniformBuffer(Math.max(16, lowered.uniformBlockSize()));
        for (ModGlslTranslator.UniformMember member : lowered.uniforms()) {
            float[] floats = program.floats(member.name());
            int[] ints = floats == null ? program.ints(member.name()) : null;
            // A member nobody wrote still has a value when its declaration carried an initializer: the lowering
            // evaluated it into the defaults, and that is the value GL hands the shader until the first write.
            float[] defaults = floats == null && ints == null
                    ? lowered.uniformDefaults().get(member.name()) : null;
            if (floats == null && ints == null && defaults == null) {
                continue;
            }
            String type = member.type();
            int components = componentsOf(type);
            int columns = columnsOf(type);
            int elements = Math.max(1, member.arrayLength());
            boolean integerType = isIntegerType(type);
            // std140: an array element or a matrix column starts on a 16-byte boundary; a plain vector packs tightly.
            int columnStride = (member.arrayLength() > 0 || columns > 1) ? 16 : components * 4;
            int value = 0;
            for (int e = 0; e < elements; e++) {
                for (int c = 0; c < columns; c++) {
                    int base = member.offset() + (e * columns + c) * columnStride;
                    for (int k = 0; k < components; k++, value++) {
                        int at = base + k * 4;
                        if (at + 4 > block.capacity()) {
                            continue;
                        }
                        if (floats != null && value < floats.length) {
                            block.putFloat(at, floats[value]);
                        } else if (ints != null && value < ints.length) {
                            if (integerType) {
                                block.putInt(at, ints[value]);
                            } else {
                                block.putFloat(at, ints[value]);
                            }
                        } else if (defaults != null && value < defaults.length) {
                            if (integerType) {
                                block.putInt(at, (int) defaults[value]);
                            } else {
                                block.putFloat(at, defaults[value]);
                            }
                        }
                    }
                }
            }
        }
        program.cachePackedUniforms(version, block);
        return block;
    }

    /**
     * {@return a private copy of the program's packed uniform block, frozen at this call}, for a prepared draw
     * on the push route. {@link #packUniforms} returns the program's one reusable buffer, refilled in place
     * whenever its uniforms change - and a prepared {@link Draw} is recorded later, at the window's flush, from
     * whatever that buffer then holds. Preparing the same program twice in one window with different values
     * (a two-pass blur whose direction uniform flips between passes) therefore pushed the second pass's values
     * for both. The buffer route never had the defect: {@link #uniformSlot} copies into a ring slot at prepare
     * time. This copy is the push route's counterpart, taken at the same moment.
     */
    public static ByteBuffer packUniformsSnapshot(ModProgramRegistry.Program program) {
        ByteBuffer packed = packUniforms(program);
        ByteBuffer copy = ByteBuffer.allocateDirect(packed.capacity())
                .order(java.nio.ByteOrder.nativeOrder());
        ByteBuffer view = packed.duplicate();
        view.clear();
        copy.put(view);
        copy.clear();
        return copy;
    }

    private static int componentsOf(String type) {
        return switch (type) {
            case "vec2", "ivec2", "uvec2", "bvec2", "mat2" -> 2;
            case "vec3", "ivec3", "uvec3", "bvec3", "mat3" -> 3;
            case "vec4", "ivec4", "uvec4", "bvec4", "mat4" -> 4;
            default -> 1;
        };
    }

    private static int columnsOf(String type) {
        return switch (type) {
            case "mat2" -> 2;
            case "mat3" -> 3;
            case "mat4" -> 4;
            default -> 1;
        };
    }

    private static boolean isIntegerType(String type) {
        if (type == null || type.isEmpty()) {
            return false;
        }
        char c = type.charAt(0);
        return c == 'i' || c == 'u' || c == 'b';
    }

    void draw(VkCommandBuffer commandBuffer, VulkanDynamicMesh mesh, ByteBuffer uniforms, long samplerSet,
              long[] bindCache) {
        draw(commandBuffer, mesh, uniforms, samplerSet, EMPTY_IMAGES, bindCache);
    }

    void draw(VkCommandBuffer commandBuffer, VulkanDynamicMesh mesh, ByteBuffer uniforms, long samplerSet,
              VulkanGlTexture[] images, long[] bindCache) {
        if (this.pipeline == VK_NULL_HANDLE || mesh == null || mesh.isEmpty()) {
            return;
        }
        // A descriptor may only name GENERAL once it has been transitioned there in this command buffer. Keep this
        // before pipeline/set binding so the bound descriptor and its declared layout agree from first use.
        if (!SKIP_DRAW_CALL) {
            for (VulkanGlTexture image : images) image.ensureGeneralLayout(commandBuffer);
        }
        if (bindCache[4] != this.pipeline) {
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipeline);
            bindCache[4] = this.pipeline;
            bindCache[5] = -1L;
        }
        if (samplerSet != 0L && bindCache[5] != samplerSet) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipelineLayout, 0,
                        stack.longs(samplerSet), null);
            }
            bindCache[5] = samplerSet;
        }
        if (this.pushBytes > 0 && uniforms != null) {
            uniforms.position(0).limit(this.pushBytes);
            if (VulkanPushCache.SHARED.shouldPush(commandBuffer.address(), this.pipelineLayout, uniforms)) {
                vkCmdPushConstants(commandBuffer, this.pipelineLayout,
                        VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, uniforms);
                VulkanPushCache.SHARED.remember(commandBuffer.address(), this.pipelineLayout, uniforms);
            }
        } else {
            // Layouts without a push range (e.g. UBO route) can disturb push constants for incompatible layouts.
            VulkanPushCache.SHARED.invalidate();
        }
        if (SKIP_DRAW_CALL) {
            // Lab: every bind of the pass without its pixels, to tell "the pass's pixels are wrong" from "the pass
            // leaves state behind that breaks what is drawn after it".
            return;
        }
        mesh.bindAndDraw(commandBuffer, bindCache);
        for (VulkanGlTexture image : images) image.ensureSampledLayout(commandBuffer);
    }

    private static final boolean FORCE_ADDITIVE_OPAQUE = Boolean.getBoolean("celtium.vulkan.modProgramAdditive");
    private static final boolean SKIP_DRAW_CALL = Boolean.getBoolean("celtium.vulkan.modProgramSkipDraw");

    public int samplerCount() {
        return this.samplerCount;
    }

    /** {@return whether this pipeline's uniform block travels through the uniform ring} */
    public boolean uniformsViaBuffer() {
        return this.uniformsViaBuffer;
    }

    public List<ModGlslTranslator.SamplerBinding> samplers() {
        return this.samplers;
    }

    /** Destroys the Vulkan objects; defers cleanup if a command buffer is currently recording. */
    public void delete() {
        if (this.constantRing != null) {
            // The ring's own delete defers its free while a recording is open (VulkanUniformBuffer.delete).
            this.constantRing.delete();
            this.constantRing = null;
        }
        VulkanUniformBuffer uniformBufferToDestroy = this.uniformBuffer;
        long pipelineToDestroy = this.pipeline;
        java.util.List<Long> poolsToDestroy = new java.util.ArrayList<Long>(this.descriptorPools);
        long pipelineLayoutToDestroy = this.pipelineLayout;
        long setLayoutToDestroy = this.setLayout;
        this.uniformBuffer = null;
        this.pipeline = VK_NULL_HANDLE;
        this.descriptorPools.clear();
        this.pipelineLayout = VK_NULL_HANDLE;
        this.setLayout = VK_NULL_HANDLE;
        this.setsByKey.clear();
        this.setInputsByKey.clear();
        this.lastSamplerKey = 0L;
        this.lastSamplerSet = 0L;
        this.lastSamplerInputs = null;
        if (this.context.celtium$recordingOpen()) {
            this.context.celtium$retireCleanup("modprogram.delete", () -> destroyHandles(
                    uniformBufferToDestroy, pipelineToDestroy, poolsToDestroy,
                    pipelineLayoutToDestroy, setLayoutToDestroy));
            return;
        }
        destroyHandles(uniformBufferToDestroy, pipelineToDestroy, poolsToDestroy,
                pipelineLayoutToDestroy, setLayoutToDestroy);
    }

    private void destroyHandles(VulkanUniformBuffer uniformBufferToDestroy, long pipelineToDestroy,
                                java.util.List<Long> poolsToDestroy, long pipelineLayoutToDestroy,
                                long setLayoutToDestroy) {
        VkDevice device = this.context.device();
        if (uniformBufferToDestroy != null) {
            uniformBufferToDestroy.delete();
        }
        if (pipelineToDestroy != VK_NULL_HANDLE) {
            vkDestroyPipeline(device, pipelineToDestroy, null);
        }
        for (int i = 0; i < poolsToDestroy.size(); i++) {
            vkDestroyDescriptorPool(device, poolsToDestroy.get(i).longValue(), null);
        }
        if (pipelineLayoutToDestroy != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(device, pipelineLayoutToDestroy, null);
        }
        if (setLayoutToDestroy != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(device, setLayoutToDestroy, null);
        }
    }

    private static void check(int result, String what) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(what + " failed with VkResult " + result);
        }
    }
}
