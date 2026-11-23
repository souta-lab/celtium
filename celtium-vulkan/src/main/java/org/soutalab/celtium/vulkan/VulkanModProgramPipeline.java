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
    /** Ring depth of a shader-pack terrain pipeline: one claim per region per pass, and two frames in flight. */
    private static final int TERRAIN_UNIFORM_RING_SLOTS = 2048;
    /**
     * Item 678 Layer B, B1: pack a program's uniform block incrementally - rewrite only the members whose
     * values changed since the last pack (the registry tracks them per member) into a block the program
     * retains, instead of walking every member on every version change. The census behind it (M5) is that
     * a region step changes one member (the region offset) while the frame's members change once per frame,
     * so the per-region walk is almost all repetition. Off by default: the off route is the original full
     * walk, byte for byte, and the on route is built to produce the identical block.
     */
    private static final boolean PACK_UNIFORM_INCREMENTAL =
            Boolean.getBoolean("celtium.vulkan.packUniformIncremental");
    /** This pipeline's ring depth: {@link #UNIFORM_RING_SLOTS}, or {@link #TERRAIN_UNIFORM_RING_SLOTS} for terrain. */
    private int uniformRingSlots = UNIFORM_RING_SLOTS;
    /**
     * Whether the uniform binding is a dynamic uniform buffer (item 678 Layer B, B3): the descriptor set
     * names the ring's base and the staged slot travels as the bind's dynamic offset, so one set serves
     * every slot of a sampler group instead of one set per (sampler group, slot). Terrain pipelines on
     * the buffer route only - the post chain's ring-32 pipelines keep the plain binding, whose sets the
     * prepared-draw path binds without dynamic offsets.
     */
    private boolean dynamicUniform;
    /**
     * The dynamic offset the next {@link #bindTerrainUniformsAndSet} call binds, remembered by
     * {@link #samplersAndImages} on the dynamic route: the terrain backend calls the two back to back for
     * one region, so the slot staged for that region is still the value here when its set is bound.
     */
    private int pendingDynamicOffset;
    private VulkanUniformBuffer uniformBuffer;
    /** The constant-attribute ring (binding 1, stride 0); null for a pipeline whose layout feeds every attribute. */
    private VulkanUniformBuffer constantRing;
    /** Dedicated zero-filled vertex buffer (binding 1, stride 0) for optional pack terrain attributes absent from the vertex format (e.g. {@code at_midBlock}). */
    private VulkanUniformBuffer terrainZeroAttributeBuffer;
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
    /**
     * The item-719 census route tag for this pipeline's factory: {@code "mod"} for the prepared-draw
     * factories, {@code "native"} for {@link #createForNativePass}, {@code "terrain"} for
     * {@link #createForTerrain}. Stamped by the factory so the per-pipeline set census can split its
     * lines by route without the census knowing the call sites.
     */
    String celtium$censusRoute = "mod";
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
    // E11 (B0, shadow-layerb-678 §5.4): the pass-scoped sampler token. Within one pass execution
    // the sampler/image inputs are invariant, yet every region's claim rehashed them and
    // re-verified the set snapshot. A token pays that hash and snapshot once at the pass' head;
    // region claims then present the token, and only the uniform slot - the one input that does
    // change per region - is resolved per call. The token's arrays are its own, never the shared
    // scratch: other claim entry points reuse the scratch and would rewrite a token pointing in.
    private long samplerTokenSerial;
    private long liveSamplerToken;
    private long samplerTokenHash;
    private long[] samplerTokenViews;
    private long[] samplerTokenSamplers;
    private int[] samplerTokenLayouts;
    private long[] samplerTokenImageViews;
    /** The set a token resolved on routes whose set names the textures alone (push, dynamic). */
    private long samplerTokenSet;
    private boolean samplerTokenSetResolved;
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
        if (program == null || program.lowered() == null) {
            return "the program is not lowered";
        }
        if (!program.lowered().ok()) {
            // The lowering's own reason, not just the fact: the LINKED line printed it once at link time,
            // but the per-batch refusal line is the one a leg reads, and it repeated only the general text.
            String failure = program.lowered().failure();
            return failure == null ? "the program is not lowered"
                    : "the program is not lowered (" + failure + ")";
        }
        if (!program.compiled()) {
            return "the program has no SPIR-V (" + program.compileFailure() + ")";
        }
        return unsupportedAttributes(program.lowered().attributes(), formatId);
    }

    /**
     * {@return whether a batch this program cannot draw may be drawn by the built-in pipeline instead of
     * refused}: the batch's texture state is disabled, so the bound program is not the program a
     * fixed-function stack would draw it with - {@code Gui.drawRect} and the gradient-rect family run
     * with texturing off and are rendered by the untextured program variant, whatever textured program
     * happens to be bound - and the only thing wrong with the bound program for this batch is the
     * attribute mapping. The measured arrival is Demonica's GLSM: its textured FFP program stays bound
     * across the menu's rectangle draws, and a POSITION batch under it was refused for reading a texture
     * coordinate the batch does not carry, so the rectangles vanished (demonica-gui-1, mechanism (a)).
     * <p>
     * Every other refusal stands: the program must be one the stub itself lowered and compiled (an
     * unlowered binding - the ShaderGroup post-process shape - is refused exactly as before), and a
     * batch with its texture state enabled is refused exactly as before.
     */
    public static boolean untexturedBuiltInFallback(ModProgramRegistry.Program program, int formatId,
                                                    boolean textureDisabled) {
        if (!textureDisabled || program == null || program.lowered() == null || !program.lowered().ok()
                || !program.compiled()) {
            return false;
        }
        return unsupportedAttributes(program.lowered().attributes(), formatId) != null;
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
        built.celtium$censusRoute = "native";
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

    /**
     * Builds a pipeline for a shader-pack terrain program ({@code gbuffers_terrain} / {@code gbuffers_water} /
     * {@code shadow}) whose vertex attributes come directly from a Celeritas/Iris {@link org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat}
     * (such as Demonica's 44-byte {@code ExtendedChunkVertexType}).
     */
    public static VulkanModProgramPipeline createForTerrain(
            VulkanContext context,
            ModProgramRegistry.Program program,
            org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat vertexFormat,
            long renderPass,
            int cull,
            boolean depthTest,
            boolean depthWrite,
            int depthCompareOp,
            VulkanColorBlendState colorBlend,
            int colorAttachments) {
        if (program == null || !program.compiled() || program.lowered() == null) {
            throw new IllegalArgumentException("terrain mod program is not compiled");
        }
        if (vertexFormat == null) {
            throw new IllegalArgumentException("terrain vertexFormat must be non-null");
        }
        ModGlslTranslator.Result lowered = program.lowered();
        boolean viaBuffer = lowered.uniformsViaBuffer();
        VulkanModProgramPipeline built = new VulkanModProgramPipeline(context, lowered.attributes(),
                lowered.samplers(), lowered.images(), lowered.uniformBlockSize(), viaBuffer);
        built.uniformRingSlots = TERRAIN_UNIFORM_RING_SLOTS;
        built.dynamicUniform = viaBuffer;
        built.celtium$censusRoute = "terrain";
        try {
            built.createLayouts();
            built.createTerrainPipeline(program.vertexSpirv(), program.fragmentSpirv(), vertexFormat, renderPass,
                    cull, depthTest, depthWrite, depthCompareOp,
                    colorBlend != null ? colorBlend : VulkanColorBlendState.legacy(), colorAttachments);
        } catch (RuntimeException | LinkageError failure) {
            built.delete();
            throw failure;
        }
        return built;
    }

    private void createTerrainPipeline(
            byte[] vertexSpirv,
            byte[] fragmentSpirv,
            org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat vertexFormat,
            long renderPass,
            int cull,
            boolean depthTest,
            boolean depthWrite,
            int depthCompareOp,
            VulkanColorBlendState colorBlend,
            int colorAttachments) {
        ByteBuffer vertexCode = MemoryUtil.memAlloc(vertexSpirv.length).put(vertexSpirv).flip();
        ByteBuffer fragmentCode = MemoryUtil.memAlloc(fragmentSpirv.length).put(fragmentSpirv).flip();
        long vertexModule = VK_NULL_HANDLE;
        long fragmentModule = VK_NULL_HANDLE;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer handle = stack.mallocLong(1);
            VkShaderModuleCreateInfo moduleInfo = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(vertexCode);
            check(vkCreateShaderModule(this.context.device(), moduleInfo, null, handle), "vkCreateShaderModule(terrain mod vertex)");
            vertexModule = handle.get(0);
            moduleInfo.pCode(fragmentCode);
            check(vkCreateShaderModule(this.context.device(), moduleInfo, null, handle),
                    "vkCreateShaderModule(terrain mod fragment)");
            fragmentModule = handle.get(0);

            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            stages.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertexModule).pName(stack.UTF8("main"));
            stages.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragmentModule)
                    .pName(stack.UTF8("main"));

            int stride = vertexFormat.getStride();
            List<ModGlslTranslator.Attribute> declared = this.attributes;
            boolean needsZeroFallback = false;
            for (int i = 0; i < declared.size(); i++) {
                if (matchTerrainAttribute(vertexFormat, declared.get(i).name()) == null) {
                    needsZeroFallback = true;
                    break;
                }
            }
            VkVertexInputBindingDescription.Buffer binding =
                    VkVertexInputBindingDescription.calloc(needsZeroFallback ? 2 : 1, stack);
            binding.get(0).binding(0).stride(stride).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
            if (needsZeroFallback) {
                binding.get(1).binding(1).stride(0).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
                this.terrainZeroAttributeBuffer =
                        new VulkanUniformBuffer(this.context, 16L, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
                MemoryUtil.memSet(this.terrainZeroAttributeBuffer.mappedAddress(), 0, 16L);
            }

            VkVertexInputAttributeDescription.Buffer attrs =
                    VkVertexInputAttributeDescription.calloc(declared.size(), stack);
            for (int i = 0; i < declared.size(); i++) {
                ModGlslTranslator.Attribute attr = declared.get(i);
                org.embeddedt.embeddium.impl.gl.attribute.GlVertexAttribute matched =
                        matchTerrainAttribute(vertexFormat, attr.name());
                if (matched == null) {
                    attrs.get(i).location(attr.location()).binding(1)
                            .format(zeroAttributeVkFormat(attr.type())).offset(0);
                } else {
                    int vkFormat = vkFormatForTerrainAttribute(matched, attr.type());
                    attrs.get(i).location(attr.location()).binding(0).format(vkFormat).offset(matched.getPointer());
                }
            }

            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType$Default().pVertexBindingDescriptions(binding).pVertexAttributeDescriptions(attrs);
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
            VkPipelineDepthStencilStateCreateInfo depth = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType$Default().depthTestEnable(depthTest).depthWriteEnable(depthWrite)
                    .depthCompareOp(depthCompareOp);

            this.blends = colorBlend.blend();
            int blendCount = Math.max(1, colorAttachments);
            VkPipelineColorBlendAttachmentState.Buffer blendAttachment =
                    VkPipelineColorBlendAttachmentState.calloc(blendCount, stack);
            for (int i = 0; i < blendCount; i++) {
                blendAttachment.get(i)
                        .colorWriteMask(colorBlend.writeMask())
                        .blendEnable(colorBlend.blend())
                        .srcColorBlendFactor(colorBlend.srcColor())
                        .dstColorBlendFactor(colorBlend.dstColor())
                        .colorBlendOp(colorBlend.colorOp())
                        .srcAlphaBlendFactor(colorBlend.srcAlpha())
                        .dstAlphaBlendFactor(colorBlend.dstAlpha())
                        .alphaBlendOp(colorBlend.alphaOp());
            }
            VkPipelineColorBlendStateCreateInfo blend = VkPipelineColorBlendStateCreateInfo.calloc(stack).sType$Default()
                    .pAttachments(blendAttachment);
            VkPipelineDynamicStateCreateInfo dynamic = VkPipelineDynamicStateCreateInfo.calloc(stack).sType$Default()
                    .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR,
                            VK_DYNAMIC_STATE_DEPTH_BIAS));

            VkGraphicsPipelineCreateInfo.Buffer info = VkGraphicsPipelineCreateInfo.calloc(1, stack);
            info.get(0).sType$Default().pStages(stages).pVertexInputState(vertexInput).pInputAssemblyState(assembly)
                    .pViewportState(viewport).pRasterizationState(raster).pMultisampleState(multisample)
                    .pDepthStencilState(depth).pColorBlendState(blend).pDynamicState(dynamic)
                    .layout(this.pipelineLayout).renderPass(renderPass).subpass(0);
            check(vkCreateGraphicsPipelines(this.context.device(), VK_NULL_HANDLE, info, null, handle),
                    "vkCreateGraphicsPipelines(terrain mod program)");
            this.pipeline = handle.get(0);
        } finally {
            if (vertexModule != VK_NULL_HANDLE) vkDestroyShaderModule(this.context.device(), vertexModule, null);
            if (fragmentModule != VK_NULL_HANDLE) vkDestroyShaderModule(this.context.device(), fragmentModule, null);
            MemoryUtil.memFree(vertexCode);
            MemoryUtil.memFree(fragmentCode);
        }
    }

    private static org.embeddedt.embeddium.impl.gl.attribute.GlVertexAttribute matchTerrainAttribute(
            org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat format, String shaderName) {
        if (shaderName == null) {
            return null;
        }
        for (var attr : format.getAttributes()) {
            if (shaderName.equals(attr.getName())) {
                return attr;
            }
        }
        String alias = switch (shaderName) {
            case "a_Pos", "iris_Pos", "vaPosition" -> "a_PosId";
            case "iris_Color", "vaColor" -> "a_Color";
            case "iris_UV0", "vaUV0" -> "a_TexCoord";
            case "iris_UV2", "vaUV2" -> "a_LightCoord";
            case "vaNormal" -> "iris_Normal";
            default -> null;
        };
        if (alias != null) {
            for (var attr : format.getAttributes()) {
                if (alias.equals(attr.getName())) {
                    return attr;
                }
            }
        }
        return null;
    }

    private static int vkFormatForTerrainAttribute(
            org.embeddedt.embeddium.impl.gl.attribute.GlVertexAttribute attr, String glslType) {
        int glType = attr.getFormat().typeId();
        int count = attr.getCount();
        boolean norm = attr.isNormalized();
        boolean intAttr = attr.isIntType() || (glslType != null && (glslType.startsWith("i") || glslType.startsWith("u")));
        // GL constants: BYTE=0x1400, UNSIGNED_BYTE=0x1401, SHORT=0x1402, UNSIGNED_SHORT=0x1403,
        // INT=0x1404, UNSIGNED_INT=0x1405, FLOAT=0x1406.
        return switch (glType) {
            case 0x1406 -> switch (count) {
                case 1 -> VK_FORMAT_R32_SFLOAT;
                case 2 -> VK_FORMAT_R32G32_SFLOAT;
                case 3 -> VK_FORMAT_R32G32B32_SFLOAT;
                default -> VK_FORMAT_R32G32B32A32_SFLOAT;
            };
            case 0x1401 -> intAttr ? VK_FORMAT_R8G8B8A8_UINT
                    : (norm ? VK_FORMAT_R8G8B8A8_UNORM : VK_FORMAT_R8G8B8A8_USCALED);
            case 0x1400 -> intAttr ? VK_FORMAT_R8G8B8A8_SINT
                    : (norm ? VK_FORMAT_R8G8B8A8_SNORM : VK_FORMAT_R8G8B8A8_SSCALED);
            case 0x1403 -> count <= 2
                    ? (intAttr ? VK_FORMAT_R16G16_UINT : (norm ? VK_FORMAT_R16G16_UNORM : VK_FORMAT_R16G16_USCALED))
                    : (intAttr ? VK_FORMAT_R16G16B16A16_UINT : (norm ? VK_FORMAT_R16G16B16A16_UNORM : VK_FORMAT_R16G16B16A16_USCALED));
            case 0x1402 -> count <= 2
                    ? (intAttr ? VK_FORMAT_R16G16_SINT : (norm ? VK_FORMAT_R16G16_SNORM : VK_FORMAT_R16G16_SSCALED))
                    : (intAttr ? VK_FORMAT_R16G16B16A16_SINT : (norm ? VK_FORMAT_R16G16B16A16_SNORM : VK_FORMAT_R16G16B16A16_SSCALED));
            case 0x1405 -> switch (count) {
                case 1 -> VK_FORMAT_R32_UINT;
                case 2 -> VK_FORMAT_R32G32_UINT;
                case 3 -> VK_FORMAT_R32G32B32_UINT;
                default -> VK_FORMAT_R32G32B32A32_UINT;
            };
            case 0x1404 -> switch (count) {
                case 1 -> VK_FORMAT_R32_SINT;
                case 2 -> VK_FORMAT_R32G32_SINT;
                case 3 -> VK_FORMAT_R32G32B32_SINT;
                default -> VK_FORMAT_R32G32B32A32_SINT;
            };
            default -> throw new IllegalStateException("Unsupported terrain vertex GL type 0x"
                    + Integer.toHexString(glType) + " count=" + count);
        };
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
                        .descriptorType(celtium$uniformBindingType(this.dynamicUniform)).descriptorCount(1)
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
                        (long) this.uniformStride * this.uniformRingSlots);
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
        if (this.uniformsViaBuffer) sizes.get(sizeIndex).type(celtium$uniformBindingType(this.dynamicUniform))
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

    /**
     * {@return the uniform binding's descriptor type}: dynamic on the B3 route, where the staged slot is
     * supplied as the bind's dynamic offset, plain where the set itself names the slot's offset. Static and
     * pure so the choice is pinned without a device.
     */
    static int celtium$uniformBindingType(boolean dynamicUniform) {
        return dynamicUniform ? VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC : VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
    }

    /**
     * {@return the descriptor-set key for the uniform route}: on the plain route the staged slot is part
     * of the set's identity, because the set names the slot's offset; on the dynamic route the set names
     * the ring's base and the slot is no part of its identity, so every slot of a sampler group shares one
     * set. Static and pure so the identity rule is pinned without a device.
     */
    static long celtium$uniformRouteSetKey(long samplerHash, long uniformBuffer, int slot,
                                           boolean dynamicUniform) {
        long hash = dynamicUniform
                ? mix64(samplerHash) ^ mix64(uniformBuffer)
                : mix64(samplerHash ^ (slot + 1L) * 0x9E3779B97F4A7C15L) ^ mix64(uniformBuffer);
        return hash == 0L ? 1L : hash;
    }

    /**
     * {@return the slot value a set's input snapshot records}: the slot itself on the plain route, where
     * the snapshot check then refuses a set written for another slot; the constant -1 on the dynamic
     * route, whose sets are written from the ring's base and serve every slot.
     */
    static int celtium$routeSnapshotSlot(int slot, boolean dynamicUniform) {
        return dynamicUniform ? -1 : slot;
    }

    /**
     * {@return the offset written into the set's uniform descriptor}: the slot's offset on the plain
     * route; 0 on the dynamic route, where the bind's dynamic offset supplies it instead. The offset the
     * shader effectively reads is this value plus {@link #celtium$uniformDynamicOffset} on the dynamic
     * route, and this value alone on the plain route - the equivalence the B3 test pins.
     */
    static long celtium$uniformWriteOffset(boolean dynamicUniform, int slot, int stride) {
        return dynamicUniform ? 0L : (long) slot * stride;
    }

    /** {@return the dynamic offset a bind supplies for {@code slot} on the dynamic route}. */
    static int celtium$uniformDynamicOffset(int slot, int stride) {
        return slot * stride;
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
     * values were staged into for {@code epoch} (the caller's frame counter). On the dynamic route the set
     * names the ring's base instead and the staged slot is remembered as the bind's dynamic offset
     * ({@link #pendingDynamicOffset}), so the set is shared by every slot of the sampler group. 0 when no
     * set could be allocated or the epoch claimed every ring slot, which the caller refuses as a missing draw.
     */
    public long samplersAndImages(VulkanGlTexture[] textures, VulkanGlTexture[] imageTextures,
                                  ModProgramRegistry.Program program, long epoch) {
        // An array-form claim supersedes any live sampler token: the token promised the inputs
        // would only be presented through it for its pass' duration.
        this.liveSamplerToken = 0L;
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
        if (this.dynamicUniform) {
            // B3: the set names the ring's base, so its identity is the sampler group alone; the staged
            // slot reaches the draw as the bind's dynamic offset, remembered here for
            // bindTerrainUniformsAndSet, which the terrain backend calls immediately after this returns.
            this.pendingDynamicOffset = celtium$uniformDynamicOffset(slot, this.uniformStride);
            hash = celtium$uniformRouteSetKey(hash, this.uniformBuffer.buffer(), slot, true);
            return allocateOrReuseDescriptorSet(hash, views, samplers, layouts, imageViews,
                    celtium$routeSnapshotSlot(slot, true));
        }
        hash = celtium$uniformRouteSetKey(hash, this.uniformBuffer.buffer(), slot, false);
        return allocateOrReuseDescriptorSet(hash, views, samplers, layouts, imageViews,
                celtium$routeSnapshotSlot(slot, false));
    }

    /**
     * Begins a sampler token for one pass execution over {@code textures} / {@code imageTextures}
     * (E11): hashes the sampler/image inputs once and snapshots them into the token's own arrays,
     * and returns the token the pass' region claims present to
     * {@link #samplersAndImages(long, ModProgramRegistry.Program, long)} instead of the arrays.
     * Beginning a token supersedes any live one, and an array-form claim on this pipeline
     * invalidates it - the token is a pass-scoped promise, made by the one caller that owns the
     * arrays for the pass' duration, that the inputs are presented only through it.
     */
    public long beginSamplerToken(VulkanGlTexture[] textures, VulkanGlTexture[] imageTextures) {
        if (this.samplerTokenViews == null) {
            this.samplerTokenViews = this.samplerCount > 0 ? new long[this.samplerCount] : EMPTY_LONG;
            this.samplerTokenSamplers = this.samplerCount > 0 ? new long[this.samplerCount] : EMPTY_LONG;
            this.samplerTokenLayouts = this.samplerCount > 0 ? new int[this.samplerCount] : EMPTY_INT;
            this.samplerTokenImageViews = this.imageCount > 0 ? new long[this.imageCount] : EMPTY_LONG;
        }
        long hash = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < this.samplerCount; i++) {
            VulkanGlTexture tex = textures[i];
            long view = tex.view();
            long sampler = tex.sampler();
            int layout = tex.samplingLayout();
            this.samplerTokenViews[i] = view;
            this.samplerTokenSamplers[i] = sampler;
            this.samplerTokenLayouts[i] = layout;
            hash = mix64(hash ^ tex.key()) ^ mix64(view ^ (sampler * 31L) ^ layout);
        }
        for (int i = 0; i < this.imageCount; i++) {
            VulkanGlTexture img = imageTextures[i];
            long view = img.view();
            this.samplerTokenImageViews[i] = view;
            hash = mix64(hash ^ img.key() ^ 0x517CC1B727220A95L) ^ mix64(view);
        }
        if (hash == 0L) {
            hash = 1L;
        }
        this.samplerTokenHash = hash;
        this.samplerTokenSet = 0L;
        this.samplerTokenSetResolved = false;
        this.liveSamplerToken = ++this.samplerTokenSerial;
        return this.liveSamplerToken;
    }

    /**
     * The token form of {@link #samplersAndImages(VulkanGlTexture[], VulkanGlTexture[], ModProgramRegistry.Program, long)}:
     * the sampler/image half comes from the token, so per region only the uniform slot claim
     * remains. On the routes whose set names the textures alone - the push route and B3's dynamic
     * route, whose set is shared by every slot of the sampler group - the resolved set is the
     * token's own, remembered after the first region. A token that is no longer live (superseded,
     * or invalidated by an array-form claim) resolves to 0, which the caller refuses as a missing
     * draw, exactly like an exhausted claim.
     */
    public long samplersAndImages(long token, ModProgramRegistry.Program program, long epoch) {
        if (token == 0L || token != this.liveSamplerToken) {
            return 0L;
        }
        if (this.samplerCount + this.imageCount == 0 && !this.uniformsViaBuffer) {
            return 0L;
        }
        if (!this.uniformsViaBuffer) {
            if (!this.samplerTokenSetResolved) {
                this.samplerTokenSet = allocateOrReuseDescriptorSet(this.samplerTokenHash,
                        this.samplerTokenViews, this.samplerTokenSamplers, this.samplerTokenLayouts,
                        this.samplerTokenImageViews, -1);
                this.samplerTokenSetResolved = this.samplerTokenSet != 0L;
            } else {
                noteTokenSetHit();
            }
            return this.samplerTokenSet;
        }
        if (program == null) {
            return 0L;
        }
        int slot = uniformSlot(program, epoch);
        if (slot < 0) {
            return 0L;
        }
        if (this.dynamicUniform) {
            // B3, as in the array form: the staged slot reaches the draw as the bind's dynamic
            // offset, remembered here for the bindTerrainUniformsAndSet call that follows.
            this.pendingDynamicOffset = celtium$uniformDynamicOffset(slot, this.uniformStride);
            if (!this.samplerTokenSetResolved) {
                long hash = celtium$uniformRouteSetKey(this.samplerTokenHash, this.uniformBuffer.buffer(),
                        slot, true);
                this.samplerTokenSet = allocateOrReuseDescriptorSet(hash,
                        this.samplerTokenViews, this.samplerTokenSamplers, this.samplerTokenLayouts,
                        this.samplerTokenImageViews, celtium$routeSnapshotSlot(slot, true));
                this.samplerTokenSetResolved = this.samplerTokenSet != 0L;
            } else {
                noteTokenSetHit();
            }
            return this.samplerTokenSet;
        }
        long hash = celtium$uniformRouteSetKey(this.samplerTokenHash, this.uniformBuffer.buffer(),
                slot, false);
        return allocateOrReuseDescriptorSet(hash, this.samplerTokenViews, this.samplerTokenSamplers,
                this.samplerTokenLayouts, this.samplerTokenImageViews,
                celtium$routeSnapshotSlot(slot, false));
    }

    /** Files a token-resolved set with the censuses exactly as an allocateOrReuse fast-path hit. */
    private void noteTokenSetHit() {
        if (this.uniformRingSlots == TERRAIN_UNIFORM_RING_SLOTS) {
            PackUniformCensus.noteSetHit();
        }
        if (PackUniformCensus.counting()) {
            PackUniformCensus.notePipelineSetHit(this, this.celtium$censusRoute);
        }
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
        if (this.uniformClaimsThisEpoch >= (this.uniformRingSlots == TERRAIN_UNIFORM_RING_SLOTS
                ? this.uniformRingSlots / 2 : this.uniformRingSlots)) {
            return -1;
        }
        int slot = this.uniformCursor;
        this.uniformCursor = (this.uniformCursor + 1) % this.uniformRingSlots;
        ByteBuffer packed;
        if (this.uniformRingSlots == TERRAIN_UNIFORM_RING_SLOTS) {
            // Item 678 pack site (a): the terrain pipeline's ring-slot staging pack, bracketed and
            // counted as PackUniformCensus' packUniformPack / packWalks. The post chain's pipelines
            // keep the unbracketed call - the instrument prices the terrain span only.
            FramePassTimings.begin("packUniformPack");
            try {
                packed = packUniforms(program);
            } finally {
                FramePassTimings.end("packUniformPack");
            }
            PackUniformCensus.notePack(program, packed, this.samplerCount, this.imageCount);
        } else {
            packed = packUniforms(program);
        }
        MemoryUtil.memCopy(MemoryUtil.memAddress0(packed),
                this.uniformBuffer.mappedAddress() + (long) slot * this.uniformStride,
                this.uniformBlockBytes);
        this.uniformSlotsByVersion.put(version, slot);
        this.uniformClaimsThisEpoch++;
        if (this.uniformRingSlots == TERRAIN_UNIFORM_RING_SLOTS) {
            PackUniformCensus.noteSlotClaim();
        }
        return slot;
    }

    /**
     * Files the backend's push-route pack (item 678 pack site (b)) with the census, with this
     * pipeline's sampler / image counts - the fields the backend cannot read. The pack itself stays
     * the caller's: it brackets the call, this only counts it.
     */
    public void celtium$notePushPack(ModProgramRegistry.Program program, ByteBuffer packed) {
        PackUniformCensus.notePack(program, packed, this.samplerCount, this.imageCount);
    }

    /** Allocates or reuses a descriptor set binding explicit image views, samplers and layouts. */
    public long samplerSetForHandles(long[] views, long[] samplers, int[] layouts, long[] generations) {
        return samplerSetForHandles(views, samplers, layouts, generations, this.samplerCount);
    }

    public long samplerSetForHandles(long[] views, long[] samplers, int[] layouts, long[] generations, int count) {
        this.liveSamplerToken = 0L;
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
        // Item 678: the census counts this lookup's outcome for terrain pipelines only - the set
        // population the pack record span pays for. Other pipelines keep the bare returns.
        boolean censusTerrain = this.uniformRingSlots == TERRAIN_UNIFORM_RING_SLOTS;
        // Item 719: the per-pipeline census counts every route; one counting() read gates it here
        // and the facade re-checks, so the off path stays a boolean read like the other censuses.
        boolean censusPipeline = PackUniformCensus.counting();
        if (key == this.lastSamplerKey && this.lastSamplerSet != 0L
                && celtium$setInputsMatch(this.lastSamplerInputs, views, samplers, layouts, imageViews,
                        this.samplerCount, this.imageCount, uniformSlot)) {
            if (censusTerrain) {
                PackUniformCensus.noteSetHit();
            }
            if (censusPipeline) {
                PackUniformCensus.notePipelineSetHit(this, this.celtium$censusRoute);
            }
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
                if (censusTerrain) {
                    PackUniformCensus.noteSetHit();
                }
                if (censusPipeline) {
                    PackUniformCensus.notePipelineSetHit(this, this.celtium$censusRoute);
                }
                return cached;
            }
            // A hash collision: the cached set was written from different inputs and would sample another
            // draw's textures for the rest of this pipeline's life. Count it and fall through to allocate a
            // set for these inputs; the old set stays valid for whoever still holds it, it is just no longer
            // this key's answer.
            this.descriptorSetKeyCollisions++;
        }
        if (cached == 0L && this.setsByKey.size() >= MAX_SETS * MAX_POOLS) {
            if (censusTerrain) {
                PackUniformCensus.noteSetMiss();
            }
            if (censusPipeline) {
                PackUniformCensus.notePipelineSetMiss(this, this.celtium$censusRoute);
                PackUniformCensus.notePipelineAllocFailure(this, this.celtium$censusRoute, true);
            }
            return 0L;
        }
        // Item 678: everything below is the miss path - allocate, write, update, register - and the
        // packUniformSetAlloc bracket covers exactly it, so the lookup's hash and snapshot-check cost
        // stays in packRecordUniform's residue instead of inflating the allocate share.
        if (censusTerrain) {
            PackUniformCensus.noteSetMiss();
            FramePassTimings.begin("packUniformSetAlloc");
        }
        if (censusPipeline) {
            PackUniformCensus.notePipelineSetMiss(this, this.celtium$censusRoute);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long set = allocateDescriptorSet(stack);
            if (set == 0L) {
                // Item 719: pool exhaustion was previously returned uncounted; file it separately
                // from the cap guard above so the cliff's firing mode is distinguishable.
                if (censusPipeline) {
                    PackUniformCensus.notePipelineAllocFailure(this, this.celtium$censusRoute, false);
                }
                return 0L;
            }
            // The uniform binding is written when the caller staged a slot (uniformSlot >= 0) and, on
            // the dynamic route, also for its -1 snapshot slot: the set names the ring's base there.
            // Non-dynamic callers passing -1 (the explicit-handle sets) never write one, as before.
            boolean writeUniform = uniformSlot >= 0 || this.dynamicUniform;
            VkDescriptorImageInfo.Buffer imageInfos = VkDescriptorImageInfo.calloc(this.samplerCount + this.imageCount, stack);
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(
                    this.samplerCount + this.imageCount + (writeUniform ? 1 : 0), stack);
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
            if (writeUniform) {
                VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
                        .buffer(this.uniformBuffer.buffer())
                        .offset(celtium$uniformWriteOffset(this.dynamicUniform, uniformSlot, this.uniformStride))
                        .range(this.uniformBlockBytes);
                writes.get(this.samplerCount + this.imageCount).sType$Default().dstSet(set)
                        .dstBinding(this.samplerCount + this.imageCount)
                        .descriptorType(celtium$uniformBindingType(this.dynamicUniform)).descriptorCount(1)
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
            if (censusTerrain) {
                PackUniformCensus.noteSetAlloc();
            }
            if (censusPipeline) {
                PackUniformCensus.notePipelineSetAlloc(this, this.celtium$censusRoute, this.setsByKey.size());
            }
            return set;
        } finally {
            if (censusTerrain) {
                FramePassTimings.end("packUniformSetAlloc");
            }
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
        if (PACK_UNIFORM_INCREMENTAL) {
            ByteBuffer retained = packUniformsIncremental(program);
            if (retained != null) {
                return retained;
            }
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
     * One lowered block's packing arithmetic, computed once per lowering: the full walk resolves each
     * member's component count, column count and stride from its type string and looks its initializer up
     * on every pack; a plan does that a single time and keeps the results in flat arrays indexed by the
     * member's position in the lowered member order (the same order the registry's dirty bitset uses).
     */
    private static final class PackPlan {
        final String[] names;
        final int[] offsets;
        final int[] sizes;
        final int[] components;
        final int[] columns;
        final int[] elements;
        final int[] columnStrides;
        final boolean[] integerTypes;
        final float[][] defaults;

        PackPlan(ModGlslTranslator.Result lowered) {
            List<ModGlslTranslator.UniformMember> members = lowered.uniforms();
            int n = members.size();
            this.names = new String[n];
            this.offsets = new int[n];
            this.sizes = new int[n];
            this.components = new int[n];
            this.columns = new int[n];
            this.elements = new int[n];
            this.columnStrides = new int[n];
            this.integerTypes = new boolean[n];
            this.defaults = new float[n][];
            Map<String, float[]> defaultsByName = lowered.uniformDefaults();
            for (int i = 0; i < n; i++) {
                ModGlslTranslator.UniformMember member = members.get(i);
                this.names[i] = member.name();
                this.offsets[i] = member.offset();
                this.sizes[i] = member.size();
                String type = member.type();
                this.components[i] = componentsOf(type);
                this.columns[i] = columnsOf(type);
                this.elements[i] = Math.max(1, member.arrayLength());
                this.columnStrides[i] = (member.arrayLength() > 0 || this.columns[i] > 1)
                        ? 16 : this.components[i] * 4;
                this.integerTypes[i] = isIntegerType(type);
                this.defaults[i] = defaultsByName.get(member.name());
            }
        }
    }

    /** Plans by lowering; a lowering is immutable, and a re-link's new lowering simply misses once. */
    private static final java.util.WeakHashMap<ModGlslTranslator.Result, PackPlan> PACK_PLANS =
            new java.util.WeakHashMap<>();

    private static PackPlan packPlan(ModGlslTranslator.Result lowered) {
        PackPlan plan = PACK_PLANS.get(lowered);
        if (plan == null) {
            plan = new PackPlan(lowered);
            PACK_PLANS.put(lowered, plan);
        }
        return plan;
    }

    /**
     * Writes one member of the plan into {@code block} from the program's current values, with the full
     * walk's source precedence (recorded floats, recorded ints, declaration defaults) and bounds guard.
     * A member with no source is zero, as in GL: a full pack gets that from the cleared buffer, so a
     * partial pack clears the member's own range instead.
     */
    private static void packPlanMember(PackPlan plan, int i, ModProgramRegistry.Program program, ByteBuffer block) {
        float[] floats = program.floats(plan.names[i]);
        int[] ints = floats == null ? program.ints(plan.names[i]) : null;
        float[] defaults = floats == null && ints == null ? plan.defaults[i] : null;
        if (floats == null && ints == null && defaults == null) {
            int from = plan.offsets[i];
            int to = Math.min(block.capacity(), from + plan.sizes[i]);
            if (to > from) {
                MemoryUtil.memSet(MemoryUtil.memAddress(block) + from, 0, to - from);
            }
            return;
        }
        int components = plan.components[i];
        int columns = plan.columns[i];
        int elements = plan.elements[i];
        int columnStride = plan.columnStrides[i];
        boolean integerType = plan.integerTypes[i];
        int value = 0;
        for (int e = 0; e < elements; e++) {
            for (int c = 0; c < columns; c++) {
                int base = plan.offsets[i] + (e * columns + c) * columnStride;
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

    /**
     * {@return the program's uniform block packed incrementally} (item 678 Layer B, B1): the block the
     * program retains across versions is rewritten only where values changed - in full after a link or an
     * unclassified change, member by member otherwise, per the registry's dirty bitset - and produces the
     * same bytes the full walk in {@link #packUniforms} would. Returns null when the program has no
     * lowering, leaving the caller on the original route (and its original behavior there).
     */
    private static ByteBuffer packUniformsIncremental(ModProgramRegistry.Program program) {
        ModGlslTranslator.Result lowered = program.lowered();
        if (lowered == null) {
            return null;
        }
        long version = program.uniformVersion();
        int bytes = Math.max(16, lowered.uniformBlockSize());
        ByteBuffer block = program.retainedPackedUniforms();
        PackPlan plan = packPlan(lowered);
        boolean full = block == null || block.capacity() < bytes
                || program.retainedPackResult() != lowered || program.uniformPackAllDirty();
        if (full) {
            if (block == null || block.capacity() < bytes) {
                block = ByteBuffer.allocateDirect(bytes).order(java.nio.ByteOrder.nativeOrder());
            }
            MemoryUtil.memSet(MemoryUtil.memAddress(block), 0, block.capacity());
            for (int i = 0; i < plan.names.length; i++) {
                packPlanMember(plan, i, program, block);
            }
            program.retainPackedUniforms(block, lowered);
        } else {
            long[] dirty = program.uniformPackDirtyWords();
            if (dirty != null) {
                for (int w = 0; w < dirty.length; w++) {
                    long word = dirty[w];
                    while (word != 0L) {
                        int member = (w << 6) + Long.numberOfTrailingZeros(word);
                        if (member < plan.names.length) {
                            packPlanMember(plan, member, program, block);
                        }
                        word &= word - 1;
                    }
                }
            }
        }
        program.consumeUniformPackDirty();
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

    public List<ModGlslTranslator.ImageBinding> images() {
        return this.images;
    }

    /** {@return the Vulkan 4-component format for a zero-fallback terrain attribute matching {@code glslType}} */
    static int zeroAttributeVkFormat(String glslType) {
        if (glslType != null && glslType.startsWith("u")) {
            return VK_FORMAT_R32G32B32A32_UINT;
        }
        if (glslType != null && glslType.startsWith("i")) {
            return VK_FORMAT_R32G32B32A32_SINT;
        }
        return VK_FORMAT_R32G32B32A32_SFLOAT;
    }

    /** Binds this pipeline for indexed terrain draws inside an open render pass. */
    void bindTerrainPipeline(VkCommandBuffer commandBuffer) {
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipeline);
        vkCmdSetDepthBias(commandBuffer, 0.0f, 0.0f, 0.0f);
        if (this.terrainZeroAttributeBuffer != null) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                vkCmdBindVertexBuffers(commandBuffer, 1,
                        stack.longs(this.terrainZeroAttributeBuffer.buffer()), stack.longs(0L));
            }
        }
    }

    /** Binds descriptor set and/or push constants for one region's terrain draws. */
    void bindTerrainUniformsAndSet(VkCommandBuffer commandBuffer, ByteBuffer pushUniforms, long descriptorSet,
                                   LongBuffer setScratch) {
        if (descriptorSet != 0L) {
            setScratch.put(0, descriptorSet);
            if (this.dynamicUniform) {
                // B3: the set names the ring's base; the region's staged slot is this bind's dynamic
                // offset, remembered by samplersAndImages for exactly this call.
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipelineLayout,
                            0, setScratch, stack.ints(this.pendingDynamicOffset));
                }
            } else {
                vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipelineLayout,
                        0, setScratch, null);
            }
        }
        if (this.pushBytes > 0 && pushUniforms != null) {
            pushUniforms.position(0).limit(this.pushBytes);
            vkCmdPushConstants(commandBuffer, this.pipelineLayout,
                    VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, pushUniforms);
            VulkanPushCache.SHARED.invalidate();
        }
    }

    /** Destroys the Vulkan objects; defers cleanup if a command buffer is currently recording. */
    public void delete() {
        if (this.constantRing != null) {
            // The ring's own delete defers its free while a recording is open (VulkanUniformBuffer.delete).
            this.constantRing.delete();
            this.constantRing = null;
        }
        if (this.terrainZeroAttributeBuffer != null) {
            this.terrainZeroAttributeBuffer.delete();
            this.terrainZeroAttributeBuffer = null;
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
