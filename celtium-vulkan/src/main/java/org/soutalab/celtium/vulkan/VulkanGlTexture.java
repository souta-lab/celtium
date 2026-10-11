package org.soutalab.celtium.vulkan;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL13C.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;

/**
 * One Vulkan sampled image that mirrors a Minecraft OpenGL texture (block atlas, lightmap).
 * <p>
 * <b>Why a copy.</b> The GL textures are created and owned by Minecraft, and neither OpenGL nor its
 * {@code EXT_memory_object} extensions offer a way to export an existing texture's storage: the extension can only
 * import memory that was created elsewhere, not hand out the storage of a texture GL already allocated. The only
 * route is therefore to read the pixels back from GL and upload them into a Vulkan image of our own, exactly as
 * this class does. This is a per-refresh copy, not a zero-copy import: every upload costs a GPU to CPU readback of
 * the whole level and a CPU to GPU transfer, so it must only run when the texture content actually changed
 * (resource reload / texture atlas refresh), never per frame.
 * <p>
 * <b>Where the pixels come from when there is no GL context.</b> A GL-less boot has no texture storage to read:
 * {@code glGetTexImage} is answered by the stub and writes nothing, so the readback above would deliver uninitialized
 * memory and the mirror would sample noise. The pixels do exist on the CPU side, because the game hands them to GL
 * itself: the stub keeps the last upload per texture level, and {@link #uploadFromGlTexture} takes them from there.
 * They travel through the same copy, the same layout changes and the same flags as a readback, and a level the stub
 * never saw is not uploaded at all - the mirror keeps no content and says so through {@link #hasFullContent}
 * (see {@link #readCapturedLevel}). {@link #capturedUploadSummary} counts both outcomes.
 * <p>
 * <b>Which mip levels are updated, and what a sampler above level 0 must expect (audit N07).</b> The image holds a
 * full chain ({@link #mipLevelsFor}), and the sampler reads it with {@code mipmapMode LINEAR} and {@code maxLod 1.0},
 * so level 1 is reachable and is blended with level 0 at grazing angles. Two write paths exist and they give
 * different guarantees:
 * <ul>
 *   <li><b>Whole-image writes</b> ({@link #uploadFromImage}, {@link #uploadFromGlTexture}) write level 0 and then
 *       regenerate levels 1..N from it by blitting ({@code recordMipChain}), so every level holds content derived
 *       from the pixels that were just uploaded. No level is left behind.</li>
 *   <li><b>Region patches</b> ({@link #uploadPatchedLevels}) write exactly the levels the producer supplied, each at
 *       its own scaled region ({@code x >> level}, {@code width >> level}) - the same arithmetic
 *       {@code TextureUtil.uploadTextureMipmap} uses for OpenGL. A level the producer did not supply keeps the
 *       content it already had, which is exactly what the GL texture holds for the same level after the same call:
 *       OpenGL's {@code glTexSubImage2D} does not regenerate the chain either. That is a contract, not an accident,
 *       and {@link #mirrorLevelSummary()} plus the {@code CELTIUM_MIRROR_PATCH} line report, per patch and in the
 *       run's census, how many levels were written, how deep the producer's own chain went, and whether any level
 *       the sampler can reach was left unwritten - so a reachable level is never stale silently.</li>
 * </ul>
 * A patch therefore does <em>not</em> regenerate the whole chain: the cheap correct path is the producer's own
 * per-level data, which is what Minecraft hands over for animated sprites.
 * <p>
 * <b>Layout invariant.</b> The image is in {@code VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL} at rest: it is put there
 * when it is created and again at the end of every upload, so a descriptor written by
 * {@code VulkanTerrainPipeline.bindTextures} is valid as soon as {@link #create} or {@link #uploadFromGlTexture}
 * returns.
 * <p>
 * <b>A shared image's layout is a submission fact, not a recording fact (audit N05).</b> The transitions
 * {@link #ensureSampledLayout} and {@link #ensureGeneralLayout} record are bound to the command buffer they are
 * recorded into, through the {@link #beginLayoutRecording(VkCommandBuffer)} /
 * {@link #commitLayoutRecording(VkCommandBuffer)} pair: the image's recorded layout changes only once the submission
 * that carries the barrier has been accepted by the queue. A recording that is abandoned - a refused submit, a
 * reset, a new recording started before the previous one was confirmed - rolls its transitions back, so the next
 * {@code ensure*} call re-records the barrier the dropped buffer never executed. Every wrapper that stands for the
 * same {@code VkImage} shares one layout fact, so a published image and any other wrapper of it cannot disagree.
 * <p>
 * <b>Threading.</b> All methods run on the render thread. {@link #uploadFromGlTexture} additionally requires the
 * current GL context (it issues GL calls). Because {@link #create} and a size-changing upload destroy device
 * objects, the caller must ensure no submitted frame is still reading the previous image when calling them; the
 * backend does this at texture refresh time, where the surrounding frame boundary already guarantees it.
 * <p>
 * <b>API surface used from the sibling classes in this package.</b>
 * <ul>
 *   <li>{@code VulkanContext.device()} - the {@code VkDevice} for every object created and for the memory mapping.</li>
 *   <li>{@code VulkanContext.physicalDevice()} - queried for format support, the maximum image dimension and memory
 *       types (the context does not expose memory type selection).</li>
 *   <li>{@code VulkanContext.beginCommands()} - a one-shot command buffer for the layout transitions and the copy.</li>
 *   <li>{@code VulkanContext.endCommands(VkCommandBuffer)} - submits that command buffer and waits for it. This
 *       class assumes the helper does not return before the work has completed; that is what makes the layout
 *       invariant and the staging buffer reuse safe.</li>
 * </ul>
 * {@code VulkanContext.queue()}, {@code queueFamilyIndex()} and {@code uploadToBuffer(...)} are deliberately unused:
 * the staging buffer is mapped here so the pixels can be copied without an intermediate Java array, and the image is
 * only ever used by the same queue family, so barriers use {@code VK_QUEUE_FAMILY_IGNORED} instead of an ownership
 * transfer.
 */
public final class VulkanGlTexture {
    /**
     * The mirror image format. R8G8B8A8_UNORM is what the GL readback is converted to (see
     * {@link #readGlLevel0}) and what the interop bootstrap already proved importable on both APIs.
     */
    private static final int IMAGE_FORMAT = VK_FORMAT_R8G8B8A8_UNORM;

    /** Number of mip levels of the current image; the OpenGL atlas is sampled with a mip chain. */
    private int mipLevels = 1;
    private int imageUploadsSkipped;
    /** Levels the GL texture reported on the last upload, or 0 to compute them from the size. */
    /**
     * Bumped whenever this mirror's image and view are recreated (audit S12).
     * <p>
     * A driver may hand back the same view handle for a new view, so caches keyed on the handle alone can serve a
     * descriptor set built on a view that has been retired. The generation makes the key change even when the handle
     * does not.
     */
    private long generation;
    private boolean levelZeroCompared;
    /** GL's row order is bottom-up and Vulkan's is top-down, so the mirror has to flip. */
    private static final boolean FLIP_ROWS = false;
    private static final boolean SAMPLER_DIAGNOSTICS =
            Boolean.getBoolean("celtium.vulkan.textureDiagnostics");
    /**
     * Where the mirror-content dump writes, or {@code null} when it is off.
     * <p>
     * The question this answers is "what does the shader actually sample", which the source-side dump cannot: the pixels a
     * readback handed over and the pixels that ended up in the device image are two different facts, and a black frame can
     * come from either. One PNG per upload per level, plus one line of numbers per upload, so a run's log alone says
     * whether the readback was empty and whether the image that received it was empty too.
     */
    private static final String MIRROR_DUMP_DIR = System.getProperty("celtium.vulkan.mirrorDump");
    /** GL names to dump; empty means every texture, which a per-frame light map would flood. */
    private static final String MIRROR_DUMP_IDS = System.getProperty("celtium.vulkan.mirrorDumpIds", "");
    /** Dumps written so far, so a long run cannot fill the disk with the same 16x16 light map. */
    private static long mirrorDumpsWritten;
    /** Distinct images dumped; the dump runs inside the frame, so its cost has to stay out of the fixture's timing. */
    private static final long MIRROR_DUMP_LIMIT = 40;
    /** Dumps per image: the first upload and the one that follows a refresh, which is the pair worth comparing. */
    private static final int MIRROR_DUMPS_PER_OBJECT = 2;
    /**
     * The largest image the dump writes as a PNG. The frame-sized textures the immediate path mirrors are megabytes
     * each and encode slowly enough to change the frame being measured, so their numbers are logged and their pixels
     * are not written; the atlas is a quarter of a megapixel and stays inside this.
     */
    private static final int MIRROR_DUMP_MAX_PIXELS = 300000;

    /**
     * GL names whose mirrored pixels are replaced by a flat colour, as {@code id=AARRGGBB,id=AARRGGBB}.
     * <p>
     * The one question the frame cannot otherwise answer: whether a draw samples <em>this</em> image. The content of the
     * mirror is proven by the dump above, so if the terrain is still black while this image holds the atlas, either the
     * descriptor names another image or the black comes from what multiplies the sample. Painting the mirror magenta and
     * looking at the frame separates those two, which no amount of reading the code can.
     */
    private static final String MIRROR_POISON = System.getProperty("celtium.vulkan.mirrorPoison");

    /**
     * GL names whose mirror is filled with ONE flat colour, as {@code id=AARRGGBB,id=AARRGGBB}.
     * <p>
     * Not the same instrument as {@link #MIRROR_POISON}: that one is a per-texel checkerboard of the colour and its
     * inverse, which is what detects a degenerate texture mapping but also lets the sample land on the inverse -
     * and the inverse of white is black, so a light map poisoned that way can zero the whole shading term and hide the
     * answer. This one writes the same colour over every texel of level 0 (the mip chain is blitted from it, so every
     * level is flat too) and is sticky across every write path, which is what makes "does this draw sample that image"
     * a question with only two possible answers.
     */
    private static final String MIRROR_POISON_FLAT = System.getProperty("celtium.vulkan.mirrorPoisonFlat");

    /** {@return the one colour to fill this texture's mirror with, or 0 to write the real pixels}. */
    private static int flatPoisonFor(int glTextureId) {
        return parseColourFor(MIRROR_POISON_FLAT, glTextureId);
    }

    /** {@return the flat colour to write instead of this texture's pixels, or 0 to write the real ones}. */
    private static int poisonFor(int glTextureId) {
        return parseColourFor(MIRROR_POISON, glTextureId);
    }

    private static int parseColourFor(String spec, int glTextureId) {
        if (spec == null || spec.isEmpty()) {
            return 0;
        }
        for (String part : spec.split(",")) {
            int equals = part.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            try {
                if (Integer.parseInt(part.substring(0, equals).trim()) == glTextureId) {
                    return (int) Long.parseLong(part.substring(equals + 1).trim(), 16);
                }
            } catch (NumberFormatException ignored) {
                // A malformed entry names no texture; the real pixels are written.
            }
        }
        return 0;
    }

    /** The flat colour the last upload of this object was told to write, or 0 for the real pixels. */
    private int poisonColour;

    /** The one colour this object's mirror is filled with, or 0 for the real pixels; sticky like {@link #poisonColour}. */
    private int flatPoisonColour;

    /** GL names whose mirror is filled with an x/y coordinate ramp instead of their pixels; see copyToImage. */
    private static final java.util.Set<Integer> encodeIds = parseIds(System.getProperty("celtium.vulkan.mirrorEncode"));
    /** The GL name of this object's last upload, so the encoding rule can be applied to it. */
    private int encodeLabel = -1;

    /**
     * GL names whose readback supply is suppressed, as {@code id,id}; for measurement only.
     * <p>
     * A directly held texture is filled by both the announcement and the readback, so "is the announcement path alone
     * enough" cannot be asked while the readback covers for it. Naming a texture here makes the announcement its only
     * source, which is the configuration the light map's black terrain was found in: with it set, the light map's mirror
     * held only the announced pixels, the terrain sampled black before the producer's shape fix and the correct band
     * after it. Nothing but a diagnostic run sets it, and the pixels it withholds are exactly the ones the announcement
     * already carries. It is not a way to run the renderer - while the directly held textures are supplied through the
     * readback the same condition is reached by re-pointing that supply, and once it is gone this switch has no work left.
     */
    private static final java.util.Set<Integer> suppressedReadbackIds =
            parseIds(System.getProperty("celtium.vulkan.suppressReadbackIds"));
    /** Uploads whose readback was suppressed, so a run says whether the property took effect. */
    private static long suppressedReadbacks;

    private static java.util.Set<Integer> parseIds(String spec) {
        java.util.Set<Integer> ids = new java.util.HashSet<Integer>();
        if (spec == null || spec.isEmpty()) {
            return ids;
        }
        for (String part : spec.split(",")) {
            try {
                ids.add(Integer.valueOf(Integer.parseInt(part.trim())));
            } catch (NumberFormatException ignored) {
                // A malformed entry names no texture.
            }
        }
        return ids;
    }

    /** Whether the sampler magnifies with NEAREST (block atlas) or LINEAR (light map). */
    /** The layout the image is actually in; barriers used to declare one the image was not in. */
    /**
     * The recorded layout of each mip level.
     * <p>
     * One field cannot describe a mipmapped atlas: the upload path leaves level 0 in a different layout from the levels a blit
     * chain produced, and a partial update that believes the whole image is being sampled cannot move level 0 at all. The
     * earlier attempts changed the tail barrier's range and broke exactly that agreement.
     */
    private int[] levelLayouts = new int[0];
    /**
     * Whether this image has ever been given its whole content.
     * <p>
     * A mirror created because a partial write arrived has only the patch that created it: the rest of the atlas is undefined,
     * and the registry used to clear its stale mark anyway, which made an image of mostly-nothing drawable. Drawing from it is
     * worse than sending the batch back to GL, so the acceptance path asks this before it builds a descriptor (audit A04).
     */
    private boolean fullContentArrived;
    private int currentLayout = org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
    private boolean nearestFilter;
    /** The source GL texture's wrap on each axis: {@code true} for GL_REPEAT, {@code false} for a clamp mode. */
    private boolean wrapRepeatU;
    private boolean wrapRepeatV;

    /** Hash of the last uploaded level 0, so an unchanged texture skips the copy (and its queue drain). */
    private long lastPixelHash;

    /**
     * Per-level write provenance, which is what makes "which levels did this write touch" a number rather than a
     * reading of the code (audit N07).
     * <p>
     * {@link #levelWrites} counts the writes each level received and {@link #levelLastWrite} is the value of
     * {@link #writeSerial} at the last of them. {@link #levelZeroWriteSerial} is the serial of the newest write that
     * touched level 0 - a patch or a whole-image upload - so a level whose last write is older than that does not
     * contain the newest level-0 content and the mirror dump prints it as behind. Levels above a patch's supplied
     * depth are expected to be behind, because OpenGL leaves them exactly the same way; this is the field that says so
     * instead of leaving it implicit.
     */
    private long[] levelWrites = new long[0];
    private long[] levelLastWrite = new long[0];
    private long levelZeroWriteSerial;
    private long writeSerial;

    /**
     * Levels a patch left unwritten because the producer supplied no data for them, counted only where the producer's
     * own chain ended - see {@link #mirrorLevelSummary()}.
     */
    private static long patchLevelsBeyondProducerChain;
    private static long patchLevelsWritten;
    private static final long[] PATCH_LEVEL_HISTOGRAM = new long[12];

    /**
     * The highest mip level this class's samplers can read, which is the {@code maxLod} they are created with.
     * <p>
     * One number, named once: {@link #createSampler} passes it to {@code vkCreateSampler} and
     * {@link #mirrorLevelSummary()} uses it to say whether a patch left a level the sampler can actually reach
     * unwritten. If the clamp is ever raised or removed, both move together and the census cannot keep claiming a
     * reach of one level while the sampler reads four.
     */
    private static final int SAMPLER_MAX_LOD = 1;

    private static final int BYTES_PER_PIXEL = 4;

    /**
     * The shader stages that may sample the mirrored image. Both are listed because the descriptor set layout
     * declares the samplers for vertex and fragment use, and an image barrier has to cover every stage that reads
     * the image; naming a stage that never samples it is harmless.
     */
    private static final int SAMPLING_STAGES =
            VK_PIPELINE_STAGE_VERTEX_SHADER_BIT | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;

    /**
     * The stages and accesses a shared (foreign) image is known to be used by, on both sides of the handshake.
     * <p>
     * The owner's window into the image is not visible here - the off-target colour image is cleared by a transfer,
     * drawn into as a colour attachment and sampled or written by the post chain's fragment shaders - so the barrier
     * that moves it names all of them. Both masks are the same set because either direction can follow any of those
     * uses. Every access flag below is supported by at least one of the named stages, which is what validation
     * checks (VUID-VkImageMemoryBarrier-srcAccessMask-02815 and its destination twin).
     */
    private static final int WORKING_STAGES = VK_PIPELINE_STAGE_TRANSFER_BIT
            | VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
    private static final int WORKING_ACCESS = VK_ACCESS_TRANSFER_WRITE_BIT
            | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT;

    /** Layout handshakes recorded and skipped for shared images, so a run says whether the caller's calls arrived. */
    private static long foreignLayoutTransitions;
    private static long foreignLayoutSkips;
    /** Recordings whose submission was accepted, and recordings that were dropped with their transitions rolled back. */
    private static long foreignLayoutCommits;
    private static long foreignLayoutAbandons;
    private static long foreignLayoutDroppedTransitions;
    /**
     * Transitions recorded with no submission token open for their command buffer.
     * <p>
     * The pre-N05 fallback: nothing can confirm such a recording, so the fact is taken as soon as the barrier is
     * recorded. A run that reports this above zero is running without the token binding, which is exactly the state
     * the audit describes - the number is what says whether the fix is in effect.
     */
    private static long foreignLayoutUnbound;
    /** Wrappers of one image that declared a layout contradicting the shared fact; the recorded handshake wins. */
    private static long foreignLayoutDeclaredMismatches;
    /**
     * How many handshake lines a run prints. The order matters more than the count: a hand-back recorded after the
     * chain's forward transition and before the pass that samples the image is exactly the sequence that leaves a
     * sampled image in GENERAL, and it is visible only as lines in sequence. Bounded so a per-frame handshake cannot
     * flood a log, with a periodic line afterwards so a long run still shows the counters.
     */
    private static final int SHARED_LAYOUT_LOGS = 24;
    private static final int SHARED_LAYOUT_PERIODIC = 200;
    private static int sharedLayoutLogs;

    /**
     * One foreign image's layout fact, shared by every wrapper that stands for that image (audit N05).
     * <p>
     * {@link #confirmed} is the layout the image is in as far as accepted submissions go: a transition recorded into
     * a command buffer does not change it, only {@link #commitLayoutRecording(VkCommandBuffer)} does. That is the
     * whole point - the wrapper used to move this fact at recording time, so a barrier that was recorded and then
     * dropped with its command buffer made the next {@code ensure*} skip a transition the image still needed.
     */
    static final class ForeignLayoutState {
        final long image;
        /** Wrappers naming this image; the fact is dropped when the last of them is deleted. */
        int wrappers;
        int confirmed;

        ForeignLayoutState(long image, int confirmed) {
            this.image = image;
            this.confirmed = confirmed;
        }
    }

    private static final java.util.Map<Long, ForeignLayoutState> FOREIGN_STATES =
            new java.util.HashMap<Long, ForeignLayoutState>();

    /**
     * The transitions recorded into one command buffer and not yet accepted by the queue.
     * <p>
     * A recording is one command-buffer recording session: {@link #beginLayoutRecording(VkCommandBuffer)} opens it,
     * {@link #commitLayoutRecording(VkCommandBuffer)} makes its transitions fact, and abandoning it - explicitly, or
     * implicitly when the next recording opens on the same handle - drops them. Within a recording, a second
     * transition for the same image chains from the first one's target ({@link #pending}), which is valid because
     * both barriers execute in the same submission, in order.
     */
    private static final class LayoutRecording {
        final long commandBuffer;
        final long id;
        /**
         * Per image: {@code {oldLayout, newLayout}} of the last transition recorded for it, where {@code oldLayout} is
         * the layout the image is in at the start of the recording and {@code newLayout} the layout the recording
         * leaves it in. {@code newLayout} is what a later transition in the same recording must name as its
         * {@code oldLayout}.
         */
        final java.util.LinkedHashMap<Long, int[]> pending = new java.util.LinkedHashMap<Long, int[]>();
        /** True for the fallback recording opened when the caller never declared one; see {@link #foreignLayoutUnbound}. */
        final boolean unbound;

        LayoutRecording(long commandBuffer, long id, boolean unbound) {
            this.commandBuffer = commandBuffer;
            this.id = id;
            this.unbound = unbound;
        }
    }

    private static final java.util.Map<Long, LayoutRecording> RECORDINGS =
            new java.util.HashMap<Long, LayoutRecording>();
    private static long layoutRecordingIds;

    /** {@return one line with the shared-image layout handshake counters}, for the run's report. */
    public static String foreignLayoutSummary() {
        synchronized (VulkanGlTexture.class) {
            return "transitions=" + foreignLayoutTransitions + " skips=" + foreignLayoutSkips
                    + " commits=" + foreignLayoutCommits + " abandons=" + foreignLayoutAbandons
                    + " dropped=" + foreignLayoutDroppedTransitions + " open=" + RECORDINGS.size()
                    + " unbound=" + foreignLayoutUnbound + " declaredMismatches=" + foreignLayoutDeclaredMismatches;
        }
    }

    /**
     * Opens a layout recording for a command buffer, so the transitions recorded into it become fact only when the
     * submission that carries them is accepted (audit N05).
     * <p>
     * Call this from the recording entry point of the command buffer the handshake is recorded into, and
     * {@link #commitLayoutRecording(VkCommandBuffer)} once {@code vkQueueSubmit} has returned success. A recording
     * that is abandoned rather than committed - the submit was refused, the buffer was reset, or the next recording
     * opened first - drops its transitions, and the next {@link #ensureSampledLayout} or
     * {@link #ensureGeneralLayout} re-records the barrier that never executed.
     * <p>
     * Opening a recording on a handle that already has an uncommitted one abandons the previous one, counted as
     * {@code abandons} in {@link #foreignLayoutSummary()}. It is safe to call this per frame, and it is the only way
     * the wrapper can distinguish "the previous submission carried my barrier" from "the previous recording was
     * thrown away": both leave the same command buffer handle behind.
     */
    public static void beginLayoutRecording(VkCommandBuffer commandBuffer) {
        if (commandBuffer == null || commandBuffer.address() == 0L) {
            throw new IllegalArgumentException("A layout recording needs a live command buffer");
        }
        beginLayoutRecording(commandBuffer.address(), "frame recording");
    }

    /**
     * Device-free form of {@link #beginLayoutRecording(VkCommandBuffer)}, so the rollback rule can be exercised
     * without a GPU.
     *
     * @param commandBuffer the command buffer address the recording belongs to
     * @param label         what opened the recording, printed by the abandon line
     */
    static void beginLayoutRecording(long commandBuffer, String label) {
        synchronized (VulkanGlTexture.class) {
            LayoutRecording previous = RECORDINGS.remove(Long.valueOf(commandBuffer));
            if (previous != null && !previous.pending.isEmpty()) {
                // The previous recording was never confirmed. Its barriers may never have executed, so its
                // transitions are dropped and the layouts stay where the last accepted submission left them.
                foreignLayoutDroppedTransitions += previous.pending.size();
                foreignLayoutAbandons++;
                System.out.println("[Celtium] CELTIUM_SHARED_LAYOUT_ABANDON cmd=0x" + Long.toHexString(commandBuffer)
                        + " token=" + previous.id + " dropped=" + previous.pending.size()
                        + " reason=[a new recording began before this one was confirmed: " + label + "]"
                        + " abandons=" + foreignLayoutAbandons);
            }
            RECORDINGS.put(Long.valueOf(commandBuffer),
                    new LayoutRecording(commandBuffer, ++layoutRecordingIds, false));
        }
    }

    /**
     * Makes every transition recorded into this command buffer fact: the submission that carries them was accepted by
     * the queue.
     * <p>
     * Call this immediately after {@code vkQueueSubmit} returned success - not before it, and not for a recovery
     * submission that re-records the buffer, because the transitions were in the recording that was discarded.
     */
    public static void commitLayoutRecording(VkCommandBuffer commandBuffer) {
        if (commandBuffer != null) {
            commitLayoutRecording(commandBuffer.address());
        }
    }

    /** Device-free form of {@link #commitLayoutRecording(VkCommandBuffer)}, so the ledger can be tested without a GPU. */
    static void commitLayoutRecording(long address) {
        if (address == 0L) {
            return;
        }
        synchronized (VulkanGlTexture.class) {
            LayoutRecording recording = RECORDINGS.remove(Long.valueOf(address));
            if (recording == null) {
                return;
            }
            int applied = 0;
            for (java.util.Map.Entry<Long, int[]> entry : recording.pending.entrySet()) {
                ForeignLayoutState state = FOREIGN_STATES.get(entry.getKey());
                if (state == null) {
                    // The last wrapper of this image was deleted between recording and commit; the image is retired,
                    // so there is no fact left to move.
                    continue;
                }
                state.confirmed = entry.getValue()[1];
                applied++;
            }
            foreignLayoutCommits++;
            if (applied > 0 && (sharedLayoutLogs < SHARED_LAYOUT_LOGS
                    || foreignLayoutCommits % SHARED_LAYOUT_PERIODIC == 0)) {
                sharedLayoutLogs++;
                System.out.println("[Celtium] CELTIUM_SHARED_LAYOUT_COMMIT cmd=0x" + Long.toHexString(address)
                        + " token=" + recording.id + " applied=" + applied + " commits=" + foreignLayoutCommits);
            }
        }
    }

    /**
     * Drops the transitions recorded into a command buffer without applying them, because the submission that would
     * have carried them was refused or its recording thrown away. The reason is printed once so a failed run says
     * which path abandoned the recording.
     */
    public static void abandonLayoutRecording(VkCommandBuffer commandBuffer, String reason) {
        if (commandBuffer != null) {
            abandonLayoutRecording(commandBuffer.address(), reason);
        }
    }

    /** Device-free form of {@link #abandonLayoutRecording(VkCommandBuffer, String)}. */
    static void abandonLayoutRecording(long address, String reason) {
        if (address == 0L) {
            return;
        }
        synchronized (VulkanGlTexture.class) {
            LayoutRecording recording = RECORDINGS.remove(Long.valueOf(address));
            if (recording == null) {
                return;
            }
            foreignLayoutAbandons++;
            if (!recording.pending.isEmpty()) {
                foreignLayoutDroppedTransitions += recording.pending.size();
            }
            System.out.println("[Celtium] CELTIUM_SHARED_LAYOUT_ABANDON cmd=0x" + Long.toHexString(address)
                    + " token=" + recording.id + " dropped=" + recording.pending.size()
                    + " reason=[" + reason + "] abandons=" + foreignLayoutAbandons);
        }
    }

    /**
     * Declares the layout a foreign image is in and returns the fact all its wrappers share.
     * <p>
     * The first wrapper of an image establishes the fact from the owner's declaration. A later wrapper of the same
     * image does not get a second, private belief: it shares this one, and a declaration that contradicts it is
     * counted and printed rather than silently adopted, because two wrappers disagreeing about their barrier's
     * {@code oldLayout} is the class of fault this exists to remove. Only the recorded handshake moves the fact.
     */
    static ForeignLayoutState declareForeignImage(long image, int declaredLayout) {
        synchronized (VulkanGlTexture.class) {
            Long key = Long.valueOf(image);
            ForeignLayoutState state = FOREIGN_STATES.get(key);
            if (state == null) {
                state = new ForeignLayoutState(image, declaredLayout);
                FOREIGN_STATES.put(key, state);
            } else if (state.confirmed != declaredLayout) {
                foreignLayoutDeclaredMismatches++;
                if (foreignLayoutDeclaredMismatches <= 4) {
                    System.out.println("[Celtium] CELTIUM_SHARED_LAYOUT_DECLARED image=0x" + Long.toHexString(image)
                            + " declared=" + layoutName(declaredLayout) + " known=" + layoutName(state.confirmed)
                            + " (the wrappers of one image share one layout fact and the recorded handshake wins)"
                            + " mismatches=" + foreignLayoutDeclaredMismatches);
                }
            }
            state.wrappers++;
            return state;
        }
    }

    /** Drops one wrapper's reference to its image's shared fact, removing the fact with the last wrapper. */
    private void releaseForeignImage() {
        if (this.foreignState == null) {
            return;
        }
        synchronized (VulkanGlTexture.class) {
            ForeignLayoutState state = this.foreignState;
            this.foreignState = null;
            state.wrappers--;
            if (state.wrappers <= 0) {
                FOREIGN_STATES.remove(Long.valueOf(state.image));
            }
        }
    }

    /** {@return the confirmed layout of a foreign image, or {@code UNDEFINED} when nothing has declared it}. */
    static int foreignLayoutOf(long image) {
        synchronized (VulkanGlTexture.class) {
            ForeignLayoutState state = FOREIGN_STATES.get(Long.valueOf(image));
            return state == null ? VK_IMAGE_LAYOUT_UNDEFINED : state.confirmed;
        }
    }

    /**
     * {@return the layout the image will be in once the open recording for this command buffer executes}, falling
     * back to the confirmed layout when no transition for it is pending there.
     */
    static int recordingLayoutOf(long commandBuffer, long image) {
        synchronized (VulkanGlTexture.class) {
            LayoutRecording recording = RECORDINGS.get(Long.valueOf(commandBuffer));
            if (recording != null) {
                int[] pending = recording.pending.get(Long.valueOf(image));
                if (pending != null) {
                    return pending[1];
                }
            }
            ForeignLayoutState state = FOREIGN_STATES.get(Long.valueOf(image));
            return state == null ? VK_IMAGE_LAYOUT_UNDEFINED : state.confirmed;
        }
    }

    /**
     * Decides the {@code oldLayout} of a barrier that must leave a foreign image in {@code wanted}.
     * <p>
     * Pure: it changes nothing, so a barrier whose recording then fails leaves the ledger exactly as it was and the
     * retry declares the same source layout. The transition is registered by {@link #noteForeignTransition} once the
     * barrier is actually in the command buffer.
     *
     * @return the layout the barrier must declare as its source, or {@code -1} when the image is already (or will
     *         already be, through a barrier recorded earlier in this same command buffer) in {@code wanted}
     */
    static int foreignTransitionFrom(long commandBuffer, long image, int wanted) {
        synchronized (VulkanGlTexture.class) {
            Long key = Long.valueOf(image);
            LayoutRecording recording = RECORDINGS.get(Long.valueOf(commandBuffer));
            int current = confirmedOf(key);
            if (recording != null) {
                int[] pending = recording.pending.get(key);
                if (pending != null) {
                    current = pending[1];
                }
            }
            return current == wanted ? -1 : current;
        }
    }

    /**
     * Registers a barrier that was just recorded: the image will be in {@code to} if the submission carrying this
     * command buffer is accepted, and this is the layout a later transition in the same recording must chain from.
     * <p>
     * With no recording open for the command buffer one is opened as {@code unbound} and the fact is applied
     * immediately - the pre-N05 contract, kept so a caller that has not adopted the token API still works, and
     * counted in {@link #foreignLayoutSummary()} so a run says the binding is not in effect.
     *
     * @param from       the layout the barrier declared as its source
     * @param to         the layout the barrier leaves the image in
     * @param unboundOut receives whether the recording was unbound, for the caller's log line; may be {@code null}
     */
    static void noteForeignTransition(long commandBuffer, long image, int from, int to, boolean[] unboundOut) {
        synchronized (VulkanGlTexture.class) {
            Long key = Long.valueOf(image);
            Long address = Long.valueOf(commandBuffer);
            LayoutRecording recording = RECORDINGS.get(address);
            if (recording == null) {
                recording = new LayoutRecording(commandBuffer, ++layoutRecordingIds, true);
                RECORDINGS.put(address, recording);
                foreignLayoutUnbound++;
                if (foreignLayoutUnbound <= 3 || foreignLayoutUnbound % 500 == 0) {
                    System.out.println("[Celtium] CELTIUM_SHARED_LAYOUT_UNBOUND cmd=0x"
                            + Long.toHexString(commandBuffer) + " image=0x" + Long.toHexString(image)
                            + " (no submission token is open for this command buffer, so a recorded transition is"
                            + " treated as accepted at recording time; call VulkanGlTexture.beginLayoutRecording /"
                            + " commitLayoutRecording around the submission to bind it) count=" + foreignLayoutUnbound);
                }
            }
            recording.pending.put(key, new int[] {from, to});
            if (unboundOut != null) {
                unboundOut[0] = recording.unbound;
            }
            if (recording.unbound) {
                ForeignLayoutState state = FOREIGN_STATES.get(key);
                if (state != null) {
                    state.confirmed = to;
                }
            }
        }
    }

    private static int confirmedOf(Long image) {
        ForeignLayoutState state = FOREIGN_STATES.get(image);
        return state == null ? VK_IMAGE_LAYOUT_UNDEFINED : state.confirmed;
    }

    private final VulkanContext context;

    /**
     * Whether the image, its memory and its view were created by this class and may therefore be destroyed by it.
     * <p>
     * False for a wrapper built by {@link #shareExternal}: the renderer's own image (the GL-less off-target pair the
     * post chain adopts) is handed over so the immediate path can sample it by GL name, and the wrapper must never
     * destroy, resize or write it. One ownership flag rather than a second class: the sampling, sampler, layout and
     * handle logic is the same object either way, and only the three places that treat the image as a resource of
     * their own - destroy, recreate and the uploads - have to know the difference.
     */
    private boolean ownsImage = true;

    /**
     * The layout fact this wrapper shares with every other wrapper of the same foreign image.
     * <p>
     * Only meaningful while {@link #ownsImage} is false; an owned mirror keeps its per-level bookkeeping in
     * {@link #levelLayouts} instead. The fact lives in {@link #FOREIGN_STATES}, keyed by the {@code VkImage} handle,
     * so a published image and a second wrapper built for it (an adopted target, a second GL name) can never hold two
     * different beliefs about the layout their barrier will declare as {@code oldLayout} (audit N05).
     */
    private ForeignLayoutState foreignState;

    /**
     * Generations for wrappers built by {@link #shareExternal}, from one counter.
     * <p>
     * The generation exists so a cache keyed on a view handle cannot serve a descriptor built on a retired view when
     * a driver hands the same handle back (audit S12). An owned mirror bumps its instance field on every recreate;
     * a foreign wrapper is never recreated in place, so a fresh wrapper - the only thing a re-publish produces -
     * takes the next value of this counter and the key changes even if every handle is reused.
     */
    private static final java.util.concurrent.atomic.AtomicLong SHARED_GENERATIONS =
            new java.util.concurrent.atomic.AtomicLong();

    private int width;
    private int height;
    private long image;
    private long memory;
    private long view;
    private long sampler;

    private long stagingBuffer;
    private long stagingMemory;
    /** Host address of the persistently mapped staging buffer, or 0 while it is not mapped. */
    private long stagingAddress;
    private long stagingCapacity;
    private boolean stagingCoherent;

    private boolean deleted;

    private VulkanGlTexture(VulkanContext context) {
        this.context = context;
    }

    /**
     * Creates an empty image of the given size, its device local memory, an image view and a sampler.
     * <p>
     * The image starts out in {@code VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL} with undefined contents, so it can be
     * bound to a descriptor before the first upload without violating the layout rules. The sampler uses linear
     * filtering with linear mipmap mode, clamps to the edge on all three axes and has anisotropy and comparison
     * sampling disabled, mirroring the sampler state Minecraft uses for terrain textures.
     *
     * @param context the live Vulkan device wrapper
     * @param width   initial image width in pixels; the first upload resizes the image if the GL texture differs
     * @param height  initial image height in pixels
     * @return a created texture; delete it with {@link #delete()}
     * @throws IllegalArgumentException if the size is not positive or exceeds the device limit
     * @throws IllegalStateException    if the device cannot sample this format with linear filtering, or if Vulkan
     *                                  object creation fails
     */
    public static VulkanGlTexture create(VulkanContext context, int width, int height) {
        return create(context, width, height, true);
    }

    /**
     * @param nearestFilter {@code true} for the pixel-art block atlas, {@code false} for the light map, whose
     *                      OpenGL texture is filtered linearly
     */
    public static VulkanGlTexture create(VulkanContext context, int width, int height, boolean nearestFilter) {
        return create(context, width, height, nearestFilter, false, false);
    }

    /**
     * @param nearestFilter {@code true} for the pixel-art block atlas, {@code false} for the light map, whose
     *                      OpenGL texture is filtered linearly
     * @param wrapRepeatU   {@code true} when the GL texture repeats on S (GL_REPEAT) rather than clamping
     * @param wrapRepeatV   {@code true} when the GL texture repeats on T
     */
    public static VulkanGlTexture create(VulkanContext context, int width, int height, boolean nearestFilter,
            boolean wrapRepeatU, boolean wrapRepeatV) {
        if (context == null) {
            throw new NullPointerException("context");
        }
        VulkanGlTexture result = new VulkanGlTexture(context);
        result.nearestFilter = nearestFilter;
        result.wrapRepeatU = wrapRepeatU;
        result.wrapRepeatV = wrapRepeatV;
        try {
            // One variable at a time: the level count is computed here so the sampler can be told the truth about the
            // chain, while the creation order stays exactly as it was. The previous measurement changed the order and the
            // clamp together, so it could not say which of them moved the frame.
            result.mipLevels = mipLevelsFor(width, height);
            result.createSampler();
            result.createImageResources(width, height);
            return result;
        } catch (RuntimeException | LinkageError error) {
            // Release whatever was already created so a failed create() cannot leak device objects.
            result.delete();
            throw error;
        }
    }

    /**
     * Wraps an image the caller already owns under this class's sampling surface, so the immediate path can resolve
     * it like any other mirrored texture.
     * <p>
     * This is the shape the GL-less outline chain needs: the chain's output lives in the renderer's own off-target
     * colour image, and the game's composite binds it by GL texture name. The registry stores this wrapper under that
     * name, the batch's bound texture resolves it, and the wrapper supplies the one thing the descriptor needs - a
     * view and a sampler.
     * <p>
     * <b>Ownership.</b> The wrapper creates the sampler (and nothing else) and owns only that. The image, its memory
     * and its view belong to the caller: {@link #delete()} does not destroy them, no upload path may write them, and
     * no resize may replace them. The content is declared live by the caller, so the wrapper reports
     * {@link #hasFullContent()} true immediately - the sampling side refuses a mirror that never held whole content,
     * and for a foreign image the owner has just said the content is the frame that was rendered into it.
     * <p>
     * <b>Layout.</b> A foreign image is not at rest in {@code SHADER_READ_ONLY_OPTIMAL}: the off-target image is the
     * render pass's colour attachment in {@code GENERAL} (and the next frame's pass attaches it again), while the
     * immediate pipelines declare {@code SHADER_READ_ONLY_OPTIMAL} in their descriptors. The caller moves it with
     * {@link #ensureSampledLayout} before it is sampled and back with {@link #ensureGeneralLayout} before it is
     * attached again; the wrapper records what it has done so a repeated call records no second barrier.
     *
     * @param context        the live Vulkan device wrapper, used for the sampler this wrapper creates
     * @param image          the caller's {@code VkImage}; never destroyed here
     * @param view           the caller's {@code VkImageView}; never destroyed here
     * @param width          the image width in pixels
     * @param height         the image height in pixels
     * @param declaredLayout the layout the caller promises the image is in right now; the first transition uses it
     *                       as its {@code oldLayout}, so a wrong promise is a validation error rather than a silent
     *                       layout mismatch. The GL-less off-target image is published in
     *                       {@code VK_IMAGE_LAYOUT_GENERAL}.
     * @return the wrapper; drop it with {@link #delete()}, which releases the sampler and touches nothing of the image
     * @throws IllegalArgumentException if a handle is 0, the size is not positive, or the declared layout is
     *                                  {@code UNDEFINED} (an undefined foreign image has no live content to publish)
     */
    public static VulkanGlTexture shareExternal(VulkanContext context, long image, long view, int width, int height,
            int declaredLayout) {
        if (context == null) {
            throw new NullPointerException("context");
        }
        if (image == 0L || view == 0L) {
            throw new IllegalArgumentException("A shared external image needs a live image and view, got image=0x"
                    + Long.toHexString(image) + " view=0x" + Long.toHexString(view));
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("A shared external image of " + width + "x" + height
                    + " pixels is not usable");
        }
        if (declaredLayout == VK_IMAGE_LAYOUT_UNDEFINED) {
            // Publishing an image whose content the caller has not established would sample undefined texels; the
            // first transition could not name a preserving oldLayout either, and declaring UNDEFINED would discard
            // whatever the frame drew. Refused rather than guessed.
            throw new IllegalArgumentException("A shared external image cannot be published in UNDEFINED: its owner"
                    + " declares the content live in the layout it was left in");
        }
        VulkanGlTexture result = new VulkanGlTexture(context);
        result.ownsImage = false;
        result.image = image;
        result.view = view;
        result.memory = 0L;
        result.width = width;
        result.height = height;
        // One level: the off-target image is created with mipLevels 1 and its view covers exactly that. The sampler
        // is told the same number, so its LOD clamp cannot reach a level the view does not have.
        result.mipLevels = 1;
        // The one layout fact for this image: every wrapper of the same VkImage shares it, and only an accepted
        // submission moves it (see the recording API below).
        result.foreignState = declareForeignImage(image, declaredLayout);
        result.fullContentArrived = true;
        result.generation = SHARED_GENERATIONS.incrementAndGet();
        // Linear and clamp-to-edge, the state a game framebuffer texture is created with: the composite is a
        // full-screen quad whose texture coordinates span 0..1 exactly, so filtering and wrapping only matter at the
        // edges - and there clamping is what the reference does.
        result.nearestFilter = false;
        result.wrapRepeatU = false;
        result.wrapRepeatV = false;
        try {
            result.createSampler();
        } catch (RuntimeException | LinkageError error) {
            // The image is the caller's; only a half-created wrapper is dropped here.
            result.delete();
            throw error;
        }
        return result;
    }

    /**
     * {@return whether this wrapper stands for exactly this image, view and size}.
     * <p>
     * The registry's publish is idempotent through this: re-publishing the same handles returns the same wrapper, so
     * the sampler is created once and the descriptor keys the immediate path cached stay valid. A different handle or
     * size is a different image and gets a new wrapper.
     */
    boolean standsFor(long image, long view, int width, int height) {
        return !this.ownsImage && this.image == image && this.view == view && this.width == width
                && this.height == height;
    }

    /** {@return whether the image is owned by the caller of {@link #shareExternal} rather than by this wrapper}. */
    public boolean isExternal() {
        return !this.ownsImage;
    }

    /**
     * Records the transition that makes a foreign image samplable, if it is not already in that layout.
     * <p>
     * Called by the owner (the backend) at a point it controls, in the command buffer that will do the sampling and
     * before the sampling command is recorded. Idempotent within a recording: a second call that would leave the
     * image in the same layout records nothing, so a call site may be reached once per frame without a barrier per
     * frame - and a call that repeats the layout a previous call in the same command buffer already recorded is also
     * a skip, because both barriers execute in that one submission or neither does.
     * <p>
     * The barrier is pending, not fact, until the submission carrying it is accepted: the caller opens the recording
     * with {@link #beginLayoutRecording(VkCommandBuffer)} and confirms it with
     * {@link #commitLayoutRecording(VkCommandBuffer)}. A recording that is abandoned leaves the image's recorded
     * layout untouched, so the next call here re-records the barrier the dropped buffer never executed (audit N05).
     *
     * @param commandBuffer the command buffer the sampling draw is recorded into
     * @throws IllegalStateException if this is an owned mirror (its layout is this class's own bookkeeping), if the
     *                               wrapper was deleted, or if the image was never given a known layout
     */
    public void ensureSampledLayout(VkCommandBuffer commandBuffer) {
        requireForeign("ensureSampledLayout");
        transitionForeignLayout(commandBuffer, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
    }

    /**
     * Records the transition that hands a foreign image back to the layout its owner attaches it in, if it is not
     * already there.
     * <p>
     * The counterpart of {@link #ensureSampledLayout}: the off-target image is a render pass colour attachment in
     * {@code GENERAL} and the next frame's pass attaches it again, so a frame that sampled it must say so before the
     * pass begins. Called by the owner in the command buffer that is about to attach the image (or to sample/attach
     * it through the post chain, which assumes {@code GENERAL} for an adopted target).
     */
    public void ensureGeneralLayout(VkCommandBuffer commandBuffer) {
        requireForeign("ensureGeneralLayout");
        transitionForeignLayout(commandBuffer, VK_IMAGE_LAYOUT_GENERAL);
    }

    /**
     * {@return the layout this image is in as far as accepted submissions go}, shared with every other wrapper of the
     * same image; {@code UNDEFINED} for an owned mirror, whose layouts this class keeps per level instead.
     * <p>
     * Deliberately not "the layout the last recording asked for": a barrier that was recorded into a command buffer
     * the queue never received does not move the image, so it must not move this fact either (audit N05).
     */
    public int foreignLayout() {
        requireAlive();
        return this.foreignState == null ? VK_IMAGE_LAYOUT_UNDEFINED : this.foreignState.confirmed;
    }

    /**
     * {@return the layout the open recording for this command buffer would leave the image in, or the confirmed
     * layout when nothing is pending there}, for reports and acceptance checks.
     */
    public int foreignLayoutIn(VkCommandBuffer commandBuffer) {
        requireForeign("a layout report");
        if (commandBuffer == null || commandBuffer.address() == 0L) {
            throw new IllegalArgumentException("A pending-layout report needs a command buffer");
        }
        return recordingLayoutOf(commandBuffer.address(), this.foreignState.image);
    }

    private void requireForeign(String what) {
        requireAlive();
        if (this.ownsImage) {
            throw new IllegalStateException(what + " is for an image this class does not own; a mirrored texture's"
                    + " layouts are recorded by its own upload path");
        }
        if (this.foreignState == null || this.foreignState.confirmed == VK_IMAGE_LAYOUT_UNDEFINED) {
            throw new IllegalStateException(what + ": the shared image's layout was never declared");
        }
    }

    /**
     * Records one barrier moving a foreign image between the two layouts this handshake knows.
     * <p>
     * The access masks cover every way the owner uses the image, because the wrapper cannot see the owner's
     * recording: the off-target colour image is cleared ({@code TRANSFER_WRITE}), drawn into as a colour attachment
     * ({@code COLOR_ATTACHMENT_WRITE}) and sampled or written by the post chain's fragment shaders
     * ({@code SHADER_READ} / {@code SHADER_WRITE}). Naming a stage or access that the image does not actually see is
     * harmless; leaving out one it does see is the missing-dependency bug this exists to prevent.
     * <p>
     * The source layout is the one the shared fact (or an earlier barrier already recorded in the same command
     * buffer) says the image is in, and the transition only becomes fact when that command buffer's submission is
     * accepted. If the recording fails between the decision and the ledger update the ledger is left untouched, so a
     * retry declares the same source layout.
     */
    private void transitionForeignLayout(VkCommandBuffer commandBuffer, int newLayout) {
        if (commandBuffer == null || commandBuffer.address() == 0L) {
            throw new IllegalArgumentException("A shared image layout transition needs a command buffer");
        }
        final long address = commandBuffer.address();
        boolean[] unbound = new boolean[1];
        final int oldLayout = foreignTransitionFrom(address, this.foreignState.image, newLayout);
        if (oldLayout < 0) {
            synchronized (VulkanGlTexture.class) {
                foreignLayoutSkips++;
                if (sharedLayoutLogs < SHARED_LAYOUT_LOGS) {
                    sharedLayoutLogs++;
                    System.out.println("[Celtium] CELTIUM_SHARED_LAYOUT image=0x" + Long.toHexString(this.image)
                            + " keep=" + layoutName(newLayout) + " skip#" + foreignLayoutSkips
                            + " transitions=" + foreignLayoutTransitions);
                }
            }
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default()
                    .srcAccessMask(WORKING_ACCESS)
                    .dstAccessMask(WORKING_ACCESS)
                    .oldLayout(oldLayout)
                    .newLayout(newLayout)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(this.image);
            // One level, one layer: the off-target image has no chain, and a range that named levels the view does
            // not cover would be the same class of error the mirror's own layout bookkeeping was written to avoid.
            barrier.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
            vkCmdPipelineBarrier(commandBuffer, WORKING_STAGES, WORKING_STAGES, 0, null, null, barrier);
        }
        // The barrier is in the command buffer now, so the transition is pending: it becomes fact when this command
        // buffer's submission is accepted, and it is dropped if the recording is abandoned. Nothing was written to the
        // ledger before the barrier existed, so a recording failure needs no rollback - the next call re-decides from
        // the same source layout.
        noteForeignTransition(address, this.foreignState.image, oldLayout, newLayout, unbound);
        synchronized (VulkanGlTexture.class) {
            foreignLayoutTransitions++;
            // The order of these lines against the owner's own recording is the measurement: a hand-back printed
            // after the chain's forward transition and before the pass that samples the image is exactly the
            // sequence that leaves a sampled image in GENERAL.
            if (sharedLayoutLogs < SHARED_LAYOUT_LOGS
                    || foreignLayoutTransitions % SHARED_LAYOUT_PERIODIC == 0) {
                sharedLayoutLogs++;
                System.out.println("[Celtium] CELTIUM_SHARED_LAYOUT image=0x" + Long.toHexString(this.image)
                        + " view=0x" + Long.toHexString(this.view) + " " + layoutName(oldLayout) + " -> "
                        + layoutName(newLayout) + " cmd=0x" + Long.toHexString(address)
                        + " token=" + (unbound[0] ? "unbound" : "pending")
                        + " transition#" + foreignLayoutTransitions);
            }
        }
    }

    /**
     * Copies mip level 0 of a GL texture into this Vulkan image.
     * <p>
     * The GL texture is queried and read with the GL context current: its level-0 size and internal format come from
     * {@code glGetTexLevelParameteri}, the pixels from {@code glGetTexImage} converted to RGBA bytes (GL performs the
     * conversion, so no per-internal-format table is needed and every colour texture comes back as tightly packed
     * RGBA8). The pixels are then memcpy'd into a persistently mapped host visible staging buffer and copied into the
     * image with {@code vkCmdCopyBufferToImage}, bracketed by image memory barriers
     * ({@code SHADER_READ_ONLY_OPTIMAL} to {@code TRANSFER_DST_OPTIMAL} and back). The one-shot command buffer is
     * submitted and waited for.
     * <p>
     * If the GL texture's level-0 size differs from the current image size, the image, its memory and its view are
     * destroyed and recreated at the new size before the copy: the mirror always matches the GL texture exactly
     * instead of being stretched or partially written. A size change is a full device object replacement, so it is
     * expensive and only expected when the atlas is rebuilt - and because it also replaces the image view handle,
     * any descriptor set written with the old {@link #view()} has to be written again afterwards (call
     * {@code VulkanTerrainPipeline.bindTextures} after the upload, not before it).
     * <p>
     * Every piece of GL state this touches is restored before returning: the pack alignment, the 2D binding of the
     * texture unit that was active on entry, and the active texture unit itself. Restoring the exact previous
     * binding matters because Minecraft caches per-unit texture bindings - leaving a different texture bound here
     * would make the cache lie and Minecraft would skip a rebind it actually needs.
     * <p>
     * <b>Without a GL context</b> (the stub is installed) there is nothing to read back, so the pixels are the ones the
     * game uploaded and the stub kept, and none of the GL state above is touched. Everything after the pixels arrive is
     * the same code: same hash, same resize, same copy and barriers, same {@code fullContentArrived}-adjacent
     * bookkeeping and the same generation bump. A texture level the stub never captured is not uploaded at all - the
     * exception below is what the callers record, and the mirror is left without content so the descriptor decision
     * refuses it instead of sampling noise.
     *
     * @param glTextureId     the GL name of the source texture
     * @param glTextureTarget the GL target, which must be {@code GL_TEXTURE_2D}
     * @throws IllegalArgumentException if the target is not {@code GL_TEXTURE_2D} or the id is 0
     * @throws IllegalStateException    if the GL texture has no level-0 storage, a GL call fails, this texture was
     *                                  deleted, or a GL-less run has no captured upload for the texture level (counted
     *                                  by {@link #capturedUploadSummary})
     */

    /** Full-image uploads from GL: how often, how many bytes, how long, and which GL textures they came from. */
    private static long fullUploads;
    private static long fullUploadBytes;
    private static long fullUploadNanos;
    private static final java.util.Map<Integer, Integer> fullUploadsByGlId =
            new java.util.concurrent.ConcurrentHashMap<Integer, Integer>();
    private static final java.util.Map<Integer, Long> fullUploadBytesByGlId =
            new java.util.concurrent.ConcurrentHashMap<Integer, Long>();

    /**
     * GL-less fills, split so a run can tell a mirror that was filled from the upload the game itself made apart from
     * one that was left empty because the stub had captured nothing.
     * <p>
     * Kept apart from {@link #fullUploads} on purpose: that one counts calls, and a call that ends in the miss below
     * still lands there, so it cannot answer "were the pixels in the image real". These two can.
     */
    private static long capturedUploads;
    private static long capturedUploadBytes;
    private static long capturedUploadMisses;
    /** Whether a descriptor read of a null view/sampler handle is reported; see {@link #logZeroHandle}. */
    private static final boolean HANDLE_LOG = Boolean.getBoolean("celtium.vulkan.handleLog");
    private static int zeroHandleLogs;

    /** The GL names each capture diagnostic has already named, so a per-frame light map cannot flood the log. */
    private static final java.util.Set<Integer> capturedUploadIdsLogged = new java.util.HashSet<Integer>();
    private static final java.util.Set<Integer> capturedUploadMissIdsLogged = new java.util.HashSet<Integer>();
    /** How many distinct GL names each capture diagnostic prints before it stays quiet. */
    private static final int CAPTURE_LOG_IDS = 8;

    /**
     * {@return a one-line summary of the full GL uploads, so the cost of mirroring can be read next to the frames}.
     * <p>
     * A sprite patch that ends up triggering a whole-image readback is the case worth seeing: the count, the bytes and
     * the wall time together say whether the mirror path is CPU-bound on copies or waiting on the GPU.
     */
    public static String fullUploadSummary() {
        StringBuilder text = new StringBuilder();
        text.append("count=").append(fullUploads)
                .append(" bytes=").append(fullUploadBytes)
                .append(" millis=").append(fullUploadNanos / 1000000L)
                .append(" byGlId=[");
        boolean first = true;
        for (java.util.Map.Entry<Integer, Integer> entry : fullUploadsByGlId.entrySet()) {
            if (!first) {
                text.append(' ');
            }
            first = false;
            Long bytes = fullUploadBytesByGlId.get(entry.getKey());
            text.append(entry.getKey()).append(':').append(entry.getValue())
                    .append('/').append(bytes == null ? 0L : bytes.longValue());
        }
        return text.append(']').toString();
    }

    /**
     * {@return one line with the GL-less capture counters}, so a run log distinguishes "this mirror was filled from the
     * upload the game made" from "the stub had nothing and the mirror was deliberately left empty".
     * <p>
     * {@code filled} counts the images that actually received the captured pixels, {@code skippedNoCapture} the calls
     * that threw instead of uploading a buffer nothing had written. A run in which the block atlas renders has
     * {@code filled} at least one; a GL-less run with {@code skippedNoCapture} rising is a run in which the stub did
     * not see the game's upload, which is the one thing to check before looking at the frame.
     */
    public static String capturedUploadSummary() {
        synchronized (VulkanGlTexture.class) {
            return "filled=" + capturedUploads + " bytes=" + capturedUploadBytes
                    + " skippedNoCapture=" + capturedUploadMisses;
        }
    }

    /**
     * Counts one image that was given the pixels the game uploaded, and names the first few GL textures they came from.
     * <p>
     * Called after the copy, not when the capture was read: a byte count recorded before the transfer would claim
     * content a failed transfer never delivered, which is the mistake audit A07 records for the pixel hash.
     */
    private static void recordCapturedFill(int glTextureId, GlLevel level) {
        long bytes = (long) level.width * (long) level.height * BYTES_PER_PIXEL;
        synchronized (VulkanGlTexture.class) {
            capturedUploads++;
            capturedUploadBytes += bytes;
            if (capturedUploadIdsLogged.size() < CAPTURE_LOG_IDS
                    && capturedUploadIdsLogged.add(Integer.valueOf(glTextureId))) {
                System.out.println("[Celtium] CELTIUM_CAPTURED_UPLOAD glTexture=" + glTextureId + " size="
                        + level.width + "x" + level.height + " bytes=" + bytes + " filled=" + capturedUploads
                        + " (from the game's own upload; no readback and no GL context)");
            }
        }
    }

    /**
     * Counts and describes one GL-less upload that could not be filled, and returns the exception that carries the
     * reason to the caller's existing failure field.
     * <p>
     * The exception is the mechanism on purpose: every caller of {@link #uploadFromGlTexture} already catches it and
     * records it, and it is also what keeps "fully initialized" from being marked afterwards - the descriptor decision
     * then refuses the mirror instead of sampling an image with no content.
     */
    private static IllegalStateException captureMiss(int glTextureId, int level, String reason) {
        synchronized (VulkanGlTexture.class) {
            capturedUploadMisses++;
            if (capturedUploadMissIdsLogged.size() < CAPTURE_LOG_IDS
                    && capturedUploadMissIdsLogged.add(Integer.valueOf(glTextureId))) {
                System.out.println("[Celtium] CELTIUM_CAPTURE_MISS glTexture=" + glTextureId + " level=" + level
                        + " misses=" + capturedUploadMisses + " reason=" + reason);
            }
        }
        return new IllegalStateException("GL-less mirror of GL texture " + glTextureId + " level " + level + ": "
                + reason + "; the mirror is left without content rather than filled with uninitialized memory");
    }

    public void uploadFromGlTexture(int glTextureId, int glTextureTarget) {
        long celtium$uploadStart = System.nanoTime();
        final int celtium$uploadGlId = glTextureId;
        if (suppressedReadbackIds.contains(Integer.valueOf(glTextureId))) {
            // Diagnostic only; see suppressedReadbackIds. Counted before the upload counters so a suppressed call is not
            // reported as a transfer that happened, and named once so a run's log says the property took effect.
            suppressedReadbacks++;
            if (suppressedReadbacks == 1) {
                System.out.println("[Celtium] CELTIUM_READBACK_SUPPRESSED glTexture=" + glTextureId
                        + " (diagnostic: the announcement is this mirror's only source)");
            }
            return;
        }
        try {
        requireOwned("an upload from a GL texture");
        // Where the pixels come from. With an OpenGL context they come from the readback, exactly as they always have -
        // this flag is false there and nothing below changes. Without one there is nothing to read back, so they come
        // from the upload the game itself made, which the stub kept; the fallback is inside readGlLevel0 so that both
        // sources return the same GlLevel and every line after this one is shared rather than duplicated.
        final boolean fromCapture = org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled();
        this.poisonColour = poisonFor(glTextureId);
        this.flatPoisonColour = flatPoisonFor(glTextureId);
        this.encodeLabel = glTextureId;
        GlLevel level = readGlLevel0(glTextureId, glTextureTarget);
        try {
            long hash = 1469598103934665603L;
            for (int i = 0; i < level.width * level.height * BYTES_PER_PIXEL; i++) {
                hash = (hash ^ (level.pixels.get(i) & 0xFF)) * 1099511628211L;
            }
            if (hash == lastPixelHash && level.width == width && level.height == height) {
                // The content is identical to what is already on the device: skip the copy, the command buffer and
                // the queue drain it would otherwise cost. The light map in particular is re-read every frame but
                // only changes when the sun moves or a torch flickers.
                return;
            }
            lastPixelHash = hash;
            dumpForDebugging(glTextureId, level);
            dumpSourcePixels(glTextureId, level);
            // The chain is generated here rather than copied from OpenGL. Copying GL's own levels needs the readback path
            // and measured 5.60%, against 0.16-0.28% for this generated chain with the sampler clamped at 1.0 - five
            // configurations were measured and this is the best, so the copying path is gone rather than kept behind a flag.
            boolean recreated = level.width != width || level.height != height;
            if (recreated) {
                // The count matters as much as the size: the previous attempt kept the size-derived chain and then read
                // levels OpenGL does not have, which measured 12.66% with ten validation errors.
                recreateImageResources(level.width, level.height);
            }
            System.out.println("[Celtium] CELTIUM_MIRROR size=" + level.width + "x" + level.height
                    + " imageLevels=" + this.mipLevels
                    + " view=" + Long.toHexString(this.view) + " image=" + Long.toHexString(this.image)
                    + " recreated=" + recreated);
            copyToImage(level.pixels, level.width, level.height, 0);
            dumpMirrorPixels(glTextureId);
            if (!level.owned) {
                // A captured upload is the one source whose arrival is worth counting separately: it says the mirror
                // holds the pixels the game uploaded rather than bytes nothing ever wrote.
                recordCapturedFill(glTextureId, level);
            }
            // The comparison reads the same GL texture back and needs a context; the captured pixels are already the
            // upload the mirror was built from, so comparing them with themselves would be an instrument that only
            // prints its own input.
            if (SAMPLER_DIAGNOSTICS && !fromCapture && level.width >= 128 && level.height >= 128 && !levelZeroCompared) {
                levelZeroCompared = true;
                try {
                    compareLevelZeroWithGl(glTextureId);
                } catch (RuntimeException | LinkageError error) {
                    System.out.println("[Celtium] CELTIUM_LEVEL0 comparison failed: " + error);
                }
            }
        } finally {
            // Only a buffer this class allocated may be freed: a captured upload belongs to the stub, and freeing it
            // would hand the stub's own memory back to the allocator.
            if (level.owned) {
                MemoryUtil.memFree(level.pixels);
            }
        }
        } finally {
            long celtium$elapsed = System.nanoTime() - celtium$uploadStart;
            synchronized (VulkanGlTexture.class) {
                fullUploads++;
                fullUploadNanos += celtium$elapsed;
                fullUploadsByGlId.merge(Integer.valueOf(celtium$uploadGlId), Integer.valueOf(1), (a, b) ->
                        Integer.valueOf(a.intValue() + b.intValue()));
                fullUploadBytesByGlId.merge(Integer.valueOf(celtium$uploadGlId), Long.valueOf(fullUploadBytes),
                        (a, b) -> Long.valueOf(a.longValue() + b.longValue()));
            }
        }
    }

    /**
     * Uploads pixels the caller already has, without reading anything back from OpenGL.
     * <p>
     * This is the ownership path: an upload through the game's utility arrives with its image in hand, so the mirror can be
     * built from the same data GL is about to be given rather than from a readback of what GL received. It deliberately does
     * **not** mirror OpenGL's mip levels - that needs the readback - so the registry uses it only when
     * {@code celtium.vulkan.mirrorGlMips} is off, which is the default and what every run in this project has measured.
     * <p>
     * Additive rather than extracted on purpose: the readback path has early returns and diagnostics of its own, and pulling a
     * shared middle out of it left the file not compiling the first time this was attempted.
     *
     * @return whether the image was written, false when the content was already there
     */
    /** {@return whether a patch was written into a sub-region}, for the animated writes that cover part of a texture. */
    /** {@return whether the image has been given its whole content at least once} (audit A04). */
    public boolean hasFullContent() {
        return this.fullContentArrived;
    }

    /**
     * Records that the image has been given its whole content.
     * <p>
     * The registry keeps the same fact in a set of its own, and the two were written by different code: a full supply
     * called the registry, while the descriptor decision asked this object, so the decision kept reading false however
     * many times the image had been supplied whole. One fact, one owner: the registry's marking lands here as well until
     * the set is retired.
     */
    void markFullContentArrived() {
        this.fullContentArrived = true;
    }

    /**
     * Writes one region of level 0, leaving the levels above it exactly as they were.
     * <p>
     * Kept for callers that have only level-0 data (and for the CPU contract check). The per-level entry point is
     * {@link #uploadPatchedLevels}, which is what an animated sprite's announcement carries and what keeps the levels
     * a sampler can reach in step with level 0 (audit N07).
     */
    public boolean uploadPatchFromPixels(int pixelWidth, int pixelHeight, int x, int y, java.nio.ByteBuffer rgba) {
        return uploadPatchedLevels(pixelWidth, pixelHeight, x, y, new java.nio.ByteBuffer[] {rgba}, 1);
    }

    /**
     * Writes a region of each mip level the producer supplied, at that level's own scaled region.
     * <p>
     * This is Minecraft's own contract, mirrored rather than invented: {@code TextureUtil.uploadTextureMipmap} walks
     * {@code data[mipLevel]} and calls {@code glTexSubImage2D(level, x >> level, y >> level, width >> level,
     * height >> level)} for each one, breaking as soon as a scaled extent reaches zero. An animated sprite therefore
     * patches level 0 <em>and</em> every level above it in one call, and the levels the sampler can reach (level 0 and,
     * with this class's {@code maxLod} of 1.0, level 1) stay in the same phase as the frame the game just drew.
     * <p>
     * <b>Levels the producer did not supply keep their previous content</b> - the same thing OpenGL does for the same
     * texture after the same call, because {@code glTexSubImage2D} does not regenerate a chain either. That is why
     * this path does not blit a new chain over the whole image: it would replace the untouched levels with content the
     * GL reference does not have. Each call reports how many mirror levels it left untouched
     * ({@link #mirrorLevelSummary()}), so "level 1 is stale because the producer only sent level 0" is a number in the
     * run rather than a silence.
     * <p>
     * All supplied levels travel in one staging buffer and one command buffer: one submit and one queue drain per
     * patch, not one per level.
     *
     * @param pixelWidth     the level-0 width of the region
     * @param pixelHeight    the level-0 height of the region
     * @param x              the level-0 x offset of the region inside the image
     * @param y              the level-0 y offset of the region inside the image
     * @param levels         per level, the tightly packed RGBA8 rows of that level's region; {@code null} where the
     *                       producer supplied nothing (an entry after a gap is not read)
     * @param suppliedLevels how many entries of {@code levels} are usable, at least 1
     * @return whether the image was written
     * @throws IllegalArgumentException if no usable level was supplied, if a region falls outside the image level, or
     *                                  if a level's buffer does not hold its declared extent (audit N08)
     */
    public boolean uploadPatchedLevels(int pixelWidth, int pixelHeight, int x, int y, java.nio.ByteBuffer[] levels,
            int suppliedLevels) {
        return uploadPatchedLevels(pixelWidth, pixelHeight, x, y, levels, suppliedLevels, -1);
    }

    /**
     * The same patch upload with the source GL name, so the mirror dump can name the levels it just wrote.
     *
     * @param glTextureId the GL name the pixels came from, or {@code -1} when the caller has none; used only by the
     *                    diagnostic dump, which is what makes a per-level census possible after a patch
     */
    public boolean uploadPatchedLevels(int pixelWidth, int pixelHeight, int x, int y, java.nio.ByteBuffer[] levels,
            int suppliedLevels, int glTextureId) {
        requireOwned("a patch upload");
        if (levels == null || suppliedLevels <= 0 || levels.length < suppliedLevels) {
            throw new IllegalArgumentException("A patch upload of " + pixelWidth + "x" + pixelHeight + " at " + x + ","
                    + y + " declares " + suppliedLevels + " supplied mip level(s) but carries "
                    + (levels == null ? "no array" : levels.length + " entries"));
        }
        if (pixelWidth <= 0 || pixelHeight <= 0) {
            throw new IllegalArgumentException("A patch upload of " + pixelWidth + "x" + pixelHeight + " pixels has no"
                    + " positive extent, so no texel is addressed");
        }
        if (x < 0 || y < 0 || x + pixelWidth > width || y + pixelHeight > height) {
            // Sticky, like uploadFromImage: a patch over a poisoned mirror is poisoned as well.
            refusePatch("region " + pixelWidth + "x" + pixelHeight + " at " + x + "," + y + " outside the mirror "
                    + width + "x" + height);
            return false;
        }
        final int mirrorLevels = Math.max(1, this.mipLevels);
        // The depth the producer actually supplied, using Minecraft's own break rule: a level whose scaled extent
        // reaches zero is not uploaded, and a null entry means there is no data for that level or any above it. The
        // patch then writes as many of those levels as the mirror has - a producer that supplies more levels than the
        // mirror's chain is counted, not silently truncated.
        int producerDepth = 0;
        for (int level = 0; level < suppliedLevels; level++) {
            if ((pixelWidth >> level) <= 0 || (pixelHeight >> level) <= 0) {
                break;
            }
            if (levels[level] == null) {
                break;
            }
            producerDepth++;
        }
        if (producerDepth == 0) {
            refusePatch("no usable mip level: level 0 is " + (levels[0] == null ? "absent" : "present")
                    + " and the region is " + pixelWidth + "x" + pixelHeight + " at " + x + "," + y);
            return false;
        }
        final int depth = Math.min(producerDepth, mirrorLevels);
        // Every region and every buffer is checked before anything is copied, so a patch that would write past a
        // level or read past a buffer leaves the image untouched instead of half-written (audit N08).
        long[] offsets = new long[depth];
        long packed = 0L;
        for (int level = 0; level < depth; level++) {
            int levelWidth = Math.max(1, width >> level);
            int levelHeight = Math.max(1, height >> level);
            int patchWidth = pixelWidth >> level;
            int patchHeight = pixelHeight >> level;
            int levelX = x >> level;
            int levelY = y >> level;
            if (levelX + patchWidth > levelWidth || levelY + patchHeight > levelHeight) {
                refusePatch("level " + level + " region " + patchWidth + "x" + patchHeight + " at " + levelX + ","
                        + levelY + " outside the mirror level " + levelWidth + "x" + levelHeight);
                return false;
            }
            long bytes = requiredUploadBytes("mip level " + level + " of a patch at " + x + "," + y, patchWidth,
                    patchHeight, levels[level]);
            offsets[level] = packed;
            packed += bytes;
        }

        // All levels in one staging allocation: offsets stay multiples of four because every RGBA8 row is.
        ensureStaging(packed);
        for (int level = 0; level < depth; level++) {
            putStagingPixels(stagingAddress + offsets[level], levels[level], pixelWidth >> level,
                    pixelHeight >> level);
        }
        if (!stagingCoherent) {
            flushStaging();
        }

        VkCommandBuffer commandBuffer = context.beginCommands();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
            VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
            for (int level = 0; level < depth; level++) {
                int patchWidth = pixelWidth >> level;
                int patchHeight = pixelHeight >> level;
                int oldLayout = levelLayout(level);
                // A patch reads what is already in the level (it writes a region, not the whole level) and a previous
                // frame may still have it bound for sampling, so both the shader reads and any transfer role are named
                // as sources. A broader source mask only orders more; a missing one is a lost dependency.
                barrier.get(0).sType$Default()
                        .srcAccessMask(VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_TRANSFER_READ_BIT
                                | VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        // The layout this level is recorded in. UNDEFINED means nothing has established its content,
                        // and declaring UNDEFINED is then exactly right: there is nothing to preserve.
                        .oldLayout(oldLayout)
                        .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .image(image);
                barrier.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, level, 1, 0, 1);
                vkCmdPipelineBarrier(commandBuffer, SAMPLING_STAGES | VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, barrier);

                region.get(0).bufferOffset(offsets[level]).bufferRowLength(0).bufferImageHeight(0);
                region.get(0).imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .mipLevel(level).baseArrayLayer(0).layerCount(1);
                region.get(0).imageOffset().set(x >> level, y >> level, 0);
                region.get(0).imageExtent().set(patchWidth, patchHeight, 1);
                vkCmdCopyBufferToImage(commandBuffer, stagingBuffer, image,
                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);

                barrier.get(0).srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                        .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                        .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
                vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT, SAMPLING_STAGES, 0, null, null,
                        barrier);
            }
        } finally {
            // Submitted and waited for by the helper, so the image holds the levels when this returns.
            context.endCommands(commandBuffer);
        }
        this.currentLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        for (int level = 0; level < depth; level++) {
            setLevelLayout(level, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        }
        recordPatchWrite(depth, producerDepth, mirrorLevels, Math.max(0, producerDepth - mirrorLevels));
        boolean wholeImage = x == 0 && y == 0 && pixelWidth >= width && pixelHeight >= height;
        if (wholeImage) {
            // A "patch" that covers level 0 of the image is a full initialization for the level-0 content: the light
            // map arrives this way. Levels above it are still exactly what this patch wrote (the producer's own data
            // where supplied, the previous content elsewhere), which the census above counts.
            this.fullContentArrived = true;
        }
        // The dump is the measurement that says which levels this patch actually reached: each level's write count is
        // printed next to whether it is behind level 0, so a patch that covered only level 0 is visible as a number
        // rather than inferred from the code (audit N07). Diagnostic only, and the dump's own caps apply.
        dumpMirrorPixels(glTextureId);
        // The image no longer holds what the last full upload put there, so the hash identifying that content is stale. Without
        // this a later full upload of the same pixels is skipped as a duplicate and the patch survives in a texture that should
        // have been replaced wholesale (audit A06).
        this.lastPixelHash = 0L;
        return true;
    }

    /**
     * Counts one patch's level coverage and writes it onto this object's per-level provenance.
     * <p>
     * The two numbers audit N07 asks for: which levels this write put pixels into, and how many of the levels it did
     * <em>not</em> write are inside the chain the producer was describing. Only the second is a claim about staleness,
     * and it is counted against the producer's own chain depth - the level at which a scaled extent reached zero, the
     * same rule {@code TextureUtil.uploadTextureMipmap} breaks on. Counting against the mirror's level count instead
     * would call the atlas's seven deeper levels "untouched" for a sixteen-by-sixteen sprite whose chain is five
     * levels long: levels the sampler cannot reach and that belong to other sprites' regions. A level above the
     * producer's chain is not stale, it is outside the sprite.
     */
    private void recordPatchWrite(int depth, int producerDepth, int mirrorLevels, int beyondMirror) {
        this.writeSerial++;
        long serial = this.writeSerial;
        if (this.levelWrites.length < mirrorLevels) {
            this.levelWrites = java.util.Arrays.copyOf(this.levelWrites, mirrorLevels);
            this.levelLastWrite = java.util.Arrays.copyOf(this.levelLastWrite, mirrorLevels);
        }
        for (int level = 0; level < depth; level++) {
            this.levelWrites[level]++;
            this.levelLastWrite[level] = serial;
        }
        if (depth > 0) {
            this.levelZeroWriteSerial = serial;
        }
        synchronized (VulkanGlTexture.class) {
            for (int level = 0; level < depth; level++) {
                if (level < PATCH_LEVEL_HISTOGRAM.length) {
                    PATCH_LEVEL_HISTOGRAM[level]++;
                }
            }
            patchLevelsWritten += depth;
            // Levels inside the producer's chain that this write did not reach, plus levels the producer supplied
            // that this image has no room for. Both are levels the producer believes it patched and the image did not.
            patchLevelsBeyondProducerChain += Math.max(0, producerDepth - depth) + beyondMirror;
            if (depth <= SAMPLER_MAX_LOD) {
                // The sampler can read a level this patch did not write. For a producer that supplies its own chain
                // this cannot happen; it is the case N07 names, and it is counted rather than inferred.
                patchReachUnwritten++;
            }
            if (beyondMirror > 0 && patchBeyondMirrorLogs < 4) {
                patchBeyondMirrorLogs++;
                System.out.println("[Celtium] CELTIUM_MIRROR_PATCH_BEYOND_MIRROR image=0x"
                        + Long.toHexString(this.image) + " supplied=" + (depth + beyondMirror) + " mirrorLevels="
                        + mirrorLevels + " (the producer patched " + beyondMirror + " level(s) this image does not"
                        + " have; they were not written) logs=" + patchBeyondMirrorLogs);
            }
        }
    }

    private static int patchBeyondMirrorLogs;

    /** {@return how many writes a level has received}, for the mirror dump's provenance line. */
    private long levelWrites(int level) {
        return level >= 0 && level < this.levelWrites.length ? this.levelWrites[level] : 0L;
    }

    /**
     * Records a whole-image upload: level 0 was copied and every level above it was regenerated from it, so all of
     * them now hold content from this one write and none of them is behind.
     */
    private void recordWholeImageWrite() {
        this.writeSerial++;
        long serial = this.writeSerial;
        int mirrorLevels = Math.max(1, this.mipLevels);
        if (this.levelWrites.length < mirrorLevels) {
            this.levelWrites = java.util.Arrays.copyOf(this.levelWrites, mirrorLevels);
            this.levelLastWrite = java.util.Arrays.copyOf(this.levelLastWrite, mirrorLevels);
        }
        for (int level = 0; level < mirrorLevels; level++) {
            this.levelWrites[level]++;
            this.levelLastWrite[level] = serial;
        }
        this.levelZeroWriteSerial = serial;
    }

    /** {@return whether a level's last write predates the newest level-0 write}, i.e. it is behind level 0. */
    private boolean levelBehind(int level) {
        return level > 0 && level < this.levelLastWrite.length && this.levelLastWrite[level] < this.levelZeroWriteSerial;
    }

    /**
     * {@return one line with the per-level patch census}, so a run says which mip levels the animated writes reached
     * and whether any level the sampler can read was left behind (audit N07).
     * <p>
     * {@code levels=[0:12 1:12 2:12 3:12 4:12]} is a producer that supplies its whole chain - Minecraft's
     * {@code TextureAtlasSprite} generates one per animated frame, and {@code TextureUtil.uploadTextureMipmap} hands
     * every level of it over. {@code levels=[0:12]} alone, with {@code beyondProducerChain} rising, is a producer that
     * supplies level 0 only; its higher levels then hold what the last whole upload or patch put there, which is the
     * same content OpenGL holds for that texture after the same {@code glTexSubImage2D}.
     * <p>
     * {@code samplerReach} is the highest level this class's samplers can read ({@link #SAMPLER_MAX_LOD}) and
     * {@code reachUnwritten} counts the patches that left a level inside that reach unwritten. That number being zero
     * is the guarantee audit N07 asks for: the levels the sampler can reach are the levels the producer patched. The
     * levels above the reach are counted separately and are not a staleness claim - the sampler never reads them, and
     * for a sprite inside the atlas they are other sprites' regions.
     */
    public static String mirrorLevelSummary() {
        synchronized (VulkanGlTexture.class) {
            StringBuilder text = new StringBuilder("levels=[");
            for (int level = 0; level < PATCH_LEVEL_HISTOGRAM.length; level++) {
                if (PATCH_LEVEL_HISTOGRAM[level] > 0) {
                    text.append(level).append(':').append(PATCH_LEVEL_HISTOGRAM[level]).append(' ');
                }
            }
            return text.append("] written=").append(patchLevelsWritten)
                    .append(" beyondProducerChain=").append(patchLevelsBeyondProducerChain)
                    .append(" samplerReach=").append(SAMPLER_MAX_LOD)
                    .append(" reachUnwritten=").append(patchReachUnwritten)
                    .append(" refusals=").append(patchRefusals).toString();
        }
    }

    /**
     * Patches that left a level inside the sampler's reach unwritten.
     * <p>
     * Zero is the N07 guarantee: the sampler blends level 0 with level 1, so a producer that patched level 0 and not
     * level 1 would leave the frame reading last frame's blur. Counted per patch rather than derived, so it cannot be
     * hidden by another patch of the same texture having supplied more levels.
     */
    private static long patchReachUnwritten;

    /** Counts a patch refused before any copy, and prints the reason once so a run says why the mirror did not move. */
    private static void refusePatch(String reason) {
        synchronized (VulkanGlTexture.class) {
            patchRefusals++;
            if (patchRefusalReasons.size() < 8 && patchRefusalReasons.add(reason)) {
                System.out.println("[Celtium] CELTIUM_MIRROR_PATCH_REFUSED " + reason
                        + " refusals=" + patchRefusals);
            }
        }
    }

    private static long patchRefusals;
    private static final java.util.Set<String> patchRefusalReasons = new java.util.HashSet<String>();

    /**
     * Validates a pixel buffer against the extent a caller declares for it, and refuses with the reason when the
     * buffer cannot supply that many bytes (audit N08).
     * <p>
     * One place decides the slice every write path uses, so the hash and the copy can never disagree about where the
     * pixels start or how many there are:
     * <ul>
     *   <li>the extent must be positive and its byte count must fit an {@code int};</li>
     *   <li>the buffer must be direct, because the copy takes its native address;</li>
     *   <li>at least {@code width * height * 4} bytes must be readable <em>from the buffer's current position</em>,
     *       which is where {@code MemoryUtil.memAddress} and the absolute-index hash both start. The position and
     *       limit themselves are never modified.</li>
     * </ul>
     *
     * @param what        what is being validated, used in the refusal message
     * @param pixelWidth  the declared width in texels
     * @param pixelHeight the declared height in texels
     * @param pixels      the buffer the pixels would be copied from
     * @return the number of bytes the extent needs, as a {@code long} that is known to fit an {@code int}
     * @throws IllegalArgumentException if the buffer cannot supply the declared extent
     */
    public static long requiredUploadBytes(String what, int pixelWidth, int pixelHeight, java.nio.ByteBuffer pixels) {
        if (pixels == null) {
            throw new IllegalArgumentException(what + ": no pixel buffer was supplied for a declared extent of "
                    + pixelWidth + "x" + pixelHeight);
        }
        if (pixelWidth <= 0 || pixelHeight <= 0) {
            throw new IllegalArgumentException(what + ": the declared extent " + pixelWidth + "x" + pixelHeight
                    + " is not positive, so no texel is addressed");
        }
        // multiplyExact, not a cast and a comparison. `(long) w * (long) h * 4` still overflows long for
        // w = h = Integer.MAX_VALUE - the product is 2^64 - and a wrapped negative value passes the
        // `required > Integer.MAX_VALUE` test below, so the helper accepted an extent whose byte count was
        // -17179869180 (audit A04). Checked arithmetic refuses the multiplication itself.
        long required;
        try {
            required = Math.multiplyExact(Math.multiplyExact((long) pixelWidth, (long) pixelHeight),
                    (long) BYTES_PER_PIXEL);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(what + ": the declared extent " + pixelWidth + "x" + pixelHeight
                    + " overflows when converted to RGBA8 bytes, so no byte count can be trusted for it");
        }
        if (required > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(what + ": the declared extent " + pixelWidth + "x" + pixelHeight
                    + " needs " + required + " bytes, which exceeds the largest storable image");
        }
        if (!pixels.isDirect()) {
            throw new IllegalArgumentException(what + ": the pixel buffer is a heap buffer, so its bytes have no"
                    + " native address to copy from; a direct buffer is required");
        }
        int remaining = pixels.remaining();
        if (remaining < (int) required) {
            throw new IllegalArgumentException(what + ": the pixel buffer holds " + remaining + " readable bytes from"
                    + " position " + pixels.position() + " (limit " + pixels.limit() + "), but " + required
                    + " are required for " + pixelWidth + "x" + pixelHeight + " RGBA8 texels");
        }
        return required;
    }

    /**
     * The FNV-1a hash of exactly the bytes a copy of this extent would take: {@code bytes} readable bytes starting at
     * the buffer's current position (audit N08).
     * <p>
     * The hash decides whether an upload is skipped as a duplicate, so it has to describe the same slice
     * {@code MemoryUtil.memAddress} hands the copy. Hashing from absolute index 0 while the copy starts at the
     * position made a payload whose bytes before the position were unchanged look like the old content: the window
     * ended before the changed texels, the hash matched, and the new pixels were never uploaded. The caller's
     * position and limit are not modified.
     *
     * @param pixels the buffer the copy would read
     * @param bytes  the number of bytes the copy would read, already validated against {@code pixels.remaining()}
     * @return the hash of that slice
     */
    public static long hashUploadSlice(java.nio.ByteBuffer pixels, int bytes) {
        if (pixels == null || bytes < 0 || bytes > pixels.remaining()) {
            throw new IllegalArgumentException("A slice hash of " + bytes + " bytes does not fit the buffer's "
                    + (pixels == null ? "missing storage" : pixels.remaining() + " readable bytes"));
        }
        final int base = pixels.position();
        long hash = 1469598103934665603L;
        for (int i = 0; i < bytes; i++) {
            hash = (hash ^ (pixels.get(base + i) & 0xFF)) * 1099511628211L;
        }
        return hash;
    }

    public boolean uploadFromImage(int pixelWidth, int pixelHeight, java.nio.ByteBuffer rgba) {
        requireOwned("a whole-image upload");
        // The poisons are STICKY: they are set by the GL-name entry point and never cleared, so a later write through
        // this path - the announced image, the captured surface - cannot put the real pixels back and make a poisoned
        // run look like a run that never sampled this image. Measured at draw time is the only measurement that counts.
        requireAlive();
        // The hash and the copy read the same slice: the declared extent must fit the buffer's remaining bytes from
        // its current position, which is where MemoryUtil.memAddress starts (audit N08). Validated before the hash, so
        // a short buffer is refused by name instead of hashing past its end and copying native bytes nothing owns.
        long required = requiredUploadBytes("a whole-image upload of " + pixelWidth + "x" + pixelHeight, pixelWidth,
                pixelHeight, rgba);
        long hash = hashUploadSlice(rgba, (int) required);
        if (hash == lastPixelHash && pixelWidth == width && pixelHeight == height) {
            this.imageUploadsSkipped++;
            return false;
        }
        if (pixelWidth != width || pixelHeight != height) {
            recreateImageResources(pixelWidth, pixelHeight);
        }
        copyToImage(rgba, pixelWidth, pixelHeight, 0);
        this.fullContentArrived = true;
        // Recorded only once the pixels are actually in the image: a hash written before the transfer would claim content that
        // a failed transfer never delivered, and the next retry would then be skipped as a duplicate (audit A07).
        lastPixelHash = hash;
        // The announcement path: this is where the light map and the direct-held textures arrive whole, so a dump here
        // says whether the image the descriptors sample got those pixels.
        dumpMirrorPixels(-1);
        return true;
    }

    /**
     * The native {@code VkImageView} handle of this texture, for {@code VkDescriptorImageInfo}.
     * <p>
     * The handle is a plain {@code long} because LWJGL 3.4 removed the non-dispatchable Vulkan handle wrapper
     * classes ({@code VkImageView}, {@code VkSampler}, ...); only dispatchable handles such as {@code VkDevice}
     * still have Java types. It stays valid until {@link #delete()} or until an upload resizes the image (a resize
     * creates a new view, so any descriptor holding the old handle must be rewritten by calling
     * {@code VulkanTerrainPipeline.bindTextures} again).
     *
     * @return the image view handle
     */
    public long view() {
        logZeroHandle("view");
        return view;
    }

    /**
     * Reports a mirror whose view or sampler is null at the moment a descriptor reads it.
     * <p>
     * Only the pathological case is printed: a descriptor built from handle 0 samples as nothing at all, which is what a
     * black texture looks like from the frame while the image itself holds the right pixels - the one combination the
     * mirror dump and the poison test cannot see. Silent while {@code celtium.vulkan.handleLog} is unset.
     */
    private void logZeroHandle(String what) {
        if (!HANDLE_LOG || (view != 0L && sampler != 0L) || zeroHandleLogs >= 40) {
            return;
        }
        zeroHandleLogs++;
        System.out.println("[Celtium] CELTIUM_ZERO_HANDLE read=" + what
                + " obj=" + Integer.toHexString(System.identityHashCode(this))
                + " size=" + this.width + "x" + this.height
                + " view=" + Long.toHexString(this.view) + " sampler=" + Long.toHexString(this.sampler)
                + " deleted=" + this.deleted + " full=" + this.fullContentArrived);
    }

    /**
     * The native {@code VkSampler} handle of this texture, for {@code VkDescriptorImageInfo}. See {@link #view()}
     * for why the handle is a {@code long}. It stays valid until {@link #delete()}.
     *
     * @return the sampler handle
     */
    /** Identity for caches: the view plus the generation, so a reused handle cannot resurrect a stale entry. */
    public long key() {
        return (this.view * 31L) ^ this.generation;
    }

    public long sampler() {
        logZeroHandle("sampler");
        return sampler;
    }

    /**
     * The current image width. Before the first upload this is the size passed to {@link #create}; afterwards it is
     * the level-0 width of the GL texture that was uploaded.
     *
     * @return the image width in pixels
     */
    public int width() {
        return width;
    }

    /**
     * The current image height. See {@link #width()} for the value before the first upload.
     *
     * @return the image height in pixels
     */
    public int height() {
        return height;
    }

    /**
     * {@return how many mip levels the image has}, which is the number of levels a patch can address and the number a
     * sampler's LOD clamp could reach if it were not clamped below it (see {@link #mirrorLevelSummary()} for the
     * patch census and the class comment for the level contract).
     */
    public int mipLevels() {
        return Math.max(1, this.mipLevels);
    }

    /**
     * Destroys the image view, the image, its memory, the sampler and the staging buffer.
     * <p>
     * Idempotent: a second call, or a call after a partially failed {@link #create}, does nothing. The caller must
     * ensure the device is idle with respect to this texture before deleting it, and that no descriptor set still
     * references the handles.
     */
    public void delete() {
        if (deleted) {
            return;
        }
        deleted = true;
        destroyStaging();
        // An image this wrapper does not own is dropped, not destroyed: the renderer's off-target pair outlives this
        // wrapper (the next publish builds a new one for the same image), and destroying it here would take the
        // silhouette with it. The handles are cleared so nothing can reach the retired image through this object.
        destroyImageResources();
        // The shared layout fact is dropped with the last wrapper that named the image, so a later image that reuses
        // the handle starts from its own declaration rather than from a retired one (audit N05).
        releaseForeignImage();
        if (sampler != 0L) {
            final long retiredSampler = sampler;
            VulkanRetireQueue.retire(() -> vkDestroySampler(context.device(), retiredSampler, null));
            sampler = 0L;
        }
        width = 0;
        height = 0;
    }

    /**
     * The levels a mirrored texture of this size can have, shared so the sampler and the image agree on one number.
     * <p>
     * The chain is generated but not sampled: the clamp below is 1.0, which is the configuration that measures 0.26%
     * against the GL reference. The levels are kept rather than removed because every configuration with a different
     * clamp measured worse (see the comment on maxLod) and this is the state that was measured rather than a theory
     * about which levels ought to exist.
     */
    private static int mipLevelsFor(int width, int height) {
        int levels = 1;
        while ((1 << levels) <= Math.max(width, height)) {
            levels++;
        }
        return levels;
    }

    private void createImageResources(int newWidth, int newHeight) {
        requireOwned("creating an image");
        this.mipLevels = mipLevelsFor(newWidth, newHeight);
        if (newWidth <= 0 || newHeight <= 0) {
            throw new IllegalArgumentException("A mirrored texture size of " + newWidth + "x" + newHeight
                    + " pixels is not usable");
        }
        VkDevice device = context.device();
        try {
            try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(context.physicalDevice(), properties);
            int maxDimension = properties.limits().maxImageDimension2D();
            if (newWidth > maxDimension || newHeight > maxDimension) {
                throw new IllegalArgumentException("A mirrored texture of " + newWidth + "x" + newHeight
                        + " pixels exceeds the device limit of " + maxDimension + " per dimension");
            }
            int requiredFeatures = VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT
                    | VK_FORMAT_FEATURE_TRANSFER_DST_BIT
                    | VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT;
            VkFormatProperties features = VkFormatProperties.calloc(stack);
            vkGetPhysicalDeviceFormatProperties(context.physicalDevice(), IMAGE_FORMAT, features);
            if ((features.optimalTilingFeatures() & requiredFeatures) != requiredFeatures) {
                throw new IllegalStateException("Vulkan format " + IMAGE_FORMAT + " cannot be sampled with linear"
                        + " filtering on this device, so terrain textures cannot be mirrored");
            }

            VkImageCreateInfo createImage = VkImageCreateInfo.calloc(stack).sType$Default()
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(IMAGE_FORMAT)
                    .mipLevels(this.mipLevels)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            createImage.extent().set(newWidth, newHeight, 1);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateImage(device, createImage, null, handle), "vkCreateImage(mirrored texture)");
            image = handle.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device, image, requirements);
            int memoryType = findMemoryType(requirements.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, "image memory");
            VkMemoryAllocateInfo allocate = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(memoryType);
            if (org.soutalab.celtium.vulkan.VulkanAllocationFault.shouldFail("mirrored texture")) {
                // Thrown like any other failure on this path: the callers already handle a resource that could not be
                // built, and the point of the injection is to check that they still do.
                throw new IllegalStateException("injected allocation failure: mirrored texture");
            }
            if (org.soutalab.celtium.vulkan.VulkanAllocationFault.shouldFail("VulkanGlTexture:483")) {
                throw new IllegalStateException("injected allocation failure: VulkanGlTexture:483");
            }
            check(vkAllocateMemory(device, allocate, null, handle), "vkAllocateMemory(mirrored texture)");
            memory = handle.get(0);
            check(vkBindImageMemory(device, image, memory, 0L), "vkBindImageMemory(mirrored texture)");
            if (org.soutalab.celtium.vulkan.VulkanAllocationFault.shouldFail("VulkanGlTexture:afterBind")) {
                // After the bind, so the cleanup has an image AND its memory to release. The two sites above fire
                // before vkAllocateMemory, which is why the audit could say the image path's partial cleanup was never
                // demonstrated: with them the handle in the log was stale and no image had been created at all (N17).
                throw new IllegalStateException("injected allocation failure after vkBindImageMemory: VulkanGlTexture");
            }

            VkImageViewCreateInfo createView = VkImageViewCreateInfo.calloc(stack).sType$Default()
                    .image(image)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(IMAGE_FORMAT);
            this.generation++;
            createView.subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, this.mipLevels, 0, 1);
            check(vkCreateImageView(device, createView, null, handle), "vkCreateImageView(mirrored texture)");
            view = handle.get(0);
            }
        } catch (RuntimeException | LinkageError error) {
            // A half-built image - the image created but its memory missing, or the view missing - must not survive as
            // this object's image: a later patch or descriptor would name it and the object would claim a size it no
            // longer has content for. Whatever was created is retired and the object is left empty and unusable by
            // name, so the next upload rebuilds it and the descriptor decision refuses it until then (audit N17).
            long releasedImage = image;
            long releasedMemory = memory;
            long releasedView = view;
            destroyImageResources();
            this.width = 0;
            this.height = 0;
            this.fullContentArrived = false;
            this.lastPixelHash = 0L;
            this.levelWrites = new long[0];
            this.levelLastWrite = new long[0];
            synchronized (VulkanGlTexture.class) {
                imageReleases++;
                if (imageReleases <= 4) {
                    System.out.println("[Celtium] CELTIUM_IMAGE_RELEASED image=0x" + Long.toHexString(releasedImage)
                            + " memory=0x" + Long.toHexString(releasedMemory) + " view=0x"
                            + Long.toHexString(releasedView) + " requested=" + newWidth + "x" + newHeight
                            + " bytes=[" + error + "] (a half-built image was retired and the object left empty, so"
                            + " no descriptor can be built on it) releases=" + imageReleases);
                }
            }
            throw error;
        }
        this.width = newWidth;
        this.height = newHeight;
        // The new image is empty: any content fact from the image it replaces is gone with it.
        this.levelWrites = new long[this.mipLevels];
        this.levelLastWrite = new long[this.mipLevels];
        // Make the resting layout true for a brand new image too, so a descriptor bound before the first upload is
        // already valid. The contents stay undefined until the first upload, which is correct for a fresh mirror.
        transitionToShaderRead();
    }

    private void createSampler() {
        VkDevice device = context.device();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSamplerCreateInfo create = VkSamplerCreateInfo.calloc(stack).sType$Default()
                    // Minecraft's block atlas is a pixel-art texture: the OpenGL texture uses GL_NEAREST for
                    // magnification and GL_NEAREST_MIPMAP_LINEAR for minification, so linear magnification here
                    // blurred every magnified texel and the frame measured four times fewer texture edges than
                    // the OpenGL reference. NEAREST filters with a linear mipmap mode reproduce that combination.
                    .magFilter(this.nearestFilter ? VK_FILTER_NEAREST : VK_FILTER_LINEAR)
                    .minFilter(this.nearestFilter ? VK_FILTER_NEAREST : VK_FILTER_LINEAR)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
                    // The wrap mode of the source GL texture, carried through at mirror creation: a repeating
                    // texture (GUI sprites, item icons) that is clamped here samples its edge texels instead.
                    .addressModeU(this.wrapRepeatU ? VK_SAMPLER_ADDRESS_MODE_REPEAT
                            : VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(this.wrapRepeatV ? VK_SAMPLER_ADDRESS_MODE_REPEAT
                            : VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .mipLodBias(0.0f)
                    .anisotropyEnable(false)
                    // Ignored while anisotropy is disabled, but set to the neutral value so the struct never
                    // contains the zero that calloc leaves behind.
                    .maxAnisotropy(1.0f)
                    .compareEnable(false)
                    .compareOp(VK_COMPARE_OP_NEVER)
                    .minLod(0.0f)
                    // Measured law, five configurations against the same GL reference: 1.0 gives 0.21-0.28%; the full
                    // chain's highest level (8 or 9) gives 8.77-8.82%; four levels give 7.37%; 0.0 gives 13.51% both with a
                    // nine-level image and with a single-level one. The driver was then asked directly, and it reports the
                    // textures this scene binds as one level with maxLevel/maxLod 0, minFilter/magFilter 9728 (GL_NEAREST)
                    // and no bias - so copying what it reports makes the frame fifty times worse than clamping at 1.0, and
                    // the clamp is not the variable that explains the difference. The value stays at the measured best and
                    // the next instrument is to log the sampler that is actually created rather than infer it from this
                    // configuration (audit S23).
                    // Measured best. Mirroring OpenGL's own levels is implemented and validation-clean, and it measures
                    // 5.60% **whatever this clamp is**: 5.60% with a clamp of 1.0 and 5.60% with a clamp of 4.0, against
                    // 0.16-0.28% for the generated chain at this clamp. Clamping to the same levels therefore changes
                    // nothing, which rules out both the clamp and the shader's LOD as the difference and points at the
                    // recreate that mirroring performs when the level count differs - a new image and view while the
                    // descriptor may still name the old one. That is the next thing to check (audit S23).
                    // The full table, one variable at a time, against the same reference:
                    //   generated chain, clamp 0.0 -> 13.51%      mirrored levels, clamp 0.0 -> 13.52%
                    //   generated chain, clamp 1.0 -> 0.16-0.28%  mirrored levels, clamp 1.0 -> 5.56-5.60%
                    //                                             mirrored levels, clamp 4.0 -> 5.60%
                    // Level 0 alone is far off in **both** modes, with identical texels on both sides, so the reference is
                    // heavily mip-blurred. Blending in a level 1 brings the frame to 0.2% when that level is generated here
                    // and only to 5.6% when it is copied from OpenGL - so the levels this project generates resemble the
                    // reference's blur and the ones the game itself holds do not. The difference is therefore in what the
                    // sampler reads at level 1 and above, and the next instrument is to dump one level from each side and
                    // compare the numbers rather than the frame (audit S23).
                    // The same number the patch census reports as the sampler's reach (SAMPLER_MAX_LOD): the clamp and
                    // the claim about which levels must not be stale move together or neither is a statement about
                    // this sampler.
                    .maxLod((float) SAMPLER_MAX_LOD)
                    .borderColor(VK_BORDER_COLOR_FLOAT_TRANSPARENT_BLACK)
                    .unnormalizedCoordinates(false);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateSampler(device, create, null, handle), "vkCreateSampler(mirrored texture)");
            sampler = handle.get(0);
            if (SAMPLER_DIAGNOSTICS) {
                // What was actually handed to the driver, rather than what a configuration is called. The clamp's value is
                // the variable every mip measurement has disagreed about, and this is the only place that knows it.
                System.out.println("[Celtium] CELTIUM_SAMPLER size=" + this.width + "x" + this.height
                        + " levels=" + this.mipLevels
                        + " minFilter=" + create.minFilter() + " magFilter=" + create.magFilter()
                        + " mipmapMode=" + create.mipmapMode()
                        + " minLod=" + create.minLod() + " maxLod=" + create.maxLod()
                        + " bias=" + create.mipLodBias() + " nearest=" + this.nearestFilter);
            }
        }
    }

    private void recreateImageResources(int newWidth, int newHeight) {
        requireOwned("resizing the image");
        destroyImageResources();
        createImageResources(newWidth, newHeight);
        // The new image is in VK_IMAGE_LAYOUT_UNDEFINED for every level, and the next write may be a patch, which only
        // transitions level 0. Levels 1..N then stay UNDEFINED while the descriptor that samples the view expects
        // SHADER_READ_ONLY_OPTIMAL, which is the validation error the world-switch scene produced (ten of them, one per
        // level, "current layout is VK_IMAGE_LAYOUT_UNDEFINED"). Moving every level here - contents are undefined, only
        // the layout changes - is what the create path already does for level 0.
        transitionToShaderRead();
    }

    /**
     * Drops the image, its view and its memory, and with them every claim about the image's content (audit N17).
     * <p>
     * The caller of a resize has already destroyed the previous pixels, so a hash or a "whole content arrived" flag
     * left over from them would describe an image that no longer exists: the next upload of the same pixels would be
     * skipped as a duplicate and the descriptor would be built on an image holding nothing. Clearing the content facts
     * here - not after the new image has been created - keeps that window closed even when the creation itself fails.
     */
    private void destroyImageResources() {
        if (!this.ownsImage) {
            // A foreign image and its view belong to whoever published them. Only the wrapper's references go.
            view = 0L;
            image = 0L;
            memory = 0L;
            return;
        }
        this.fullContentArrived = false;
        this.lastPixelHash = 0L;
        this.levelWrites = new long[0];
        this.levelLastWrite = new long[0];
        this.levelLayouts = new int[0];
        VkDevice device = context.device();
        if (view != 0L) {
            // Retired rather than destroyed: a command buffer recorded earlier may still name this view (audit S12).
            final long retiredView = view;
            VulkanRetireQueue.retire(() -> vkDestroyImageView(device, retiredView, null));
            view = 0L;
        }
        if (image != 0L) {
            final long retiredImage = image;
            VulkanRetireQueue.retire(() -> vkDestroyImage(device, retiredImage, null));
            image = 0L;
        }
        if (memory != 0L) {
            final long retiredMemory = memory;
            VulkanRetireQueue.retire(() -> vkFreeMemory(device, retiredMemory, null));
            memory = 0L;
        }
    }

    private void transitionToShaderRead() {
        VkCommandBuffer commandBuffer = context.beginCommands();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default()
                    // The contents are undefined, so nothing has to be made available: only the layout changes.
                    .srcAccessMask(0)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    // The image is only used by this device and never handed to another queue family, so no
                    // ownership transfer is needed.
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image);
            // Every level, not just level 0: the descriptor samples the whole view, so a level left in UNDEFINED is a
            // validation error at draw time (VUID-vkCmdDraw-None-09600), and after a resize the levels above 0 have
            // never been transitioned by anything else.
            barrier.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, Math.max(1, this.mipLevels), 0, 1);
            vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, SAMPLING_STAGES,
                    0, null, null, barrier);
            for (int covered = 0; covered < Math.max(1, this.mipLevels); covered++) {
                setLevelLayout(covered, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            }
        } finally {
            context.endCommands(commandBuffer);
        }
    }

    /** {@return whether this GL name is one the dump was asked for}, so a per-frame light map cannot flood the log. */
    private static boolean mirrorDumpWanted(int glTextureId) {
        if (MIRROR_DUMP_DIR == null || MIRROR_DUMP_DIR.isEmpty()) {
            return false;
        }
        if (MIRROR_DUMP_IDS.isEmpty() || glTextureId < 0) {
            // A negative id is a caller that has no GL name to give (the announcement and patch paths); those are the
            // directly held textures, which is exactly where a black light map would come from.
            return true;
        }
        for (String part : MIRROR_DUMP_IDS.split(",")) {
            if (part.trim().equals(Integer.toString(glTextureId))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Writes one line of numbers describing a pixel buffer: how much of it is not zero, the byte sum and the extremes.
     * <p>
     * The count of non-zero bytes is the fact a black frame is made of: a readback of an all-zero surface and an image
     * that received one both report zero here, and a texture of real content cannot. The byte sum distinguishes
     * "black" from "empty" when alpha is the only non-zero channel, which is exactly the difference between a mirror a
     * shader draws as transparent and one it draws as black.
     */
    private static String pixelSummary(java.nio.ByteBuffer pixels, int width, int height) {
        long nonZero = 0;
        long sum = 0;
        int min = 255;
        int max = 0;
        long count = Math.min((long) width * height * BYTES_PER_PIXEL, pixels.capacity());
        for (int i = 0; i < count; i++) {
            int value = pixels.get(i) & 0xFF;
            if (value != 0) {
                nonZero++;
            }
            sum += value;
            if (value < min) {
                min = value;
            }
            if (value > max) {
                max = value;
            }
        }
        return "bytes=" + count + " nonZero=" + nonZero + " sum=" + sum + " min=" + min + " max=" + max;
    }

    /**
     * Writes the pixels that arrived from the source, before the copy, when the mirror dump is on.
     * <p>
     * This is the half that says whether the readback (or the capture) delivered content at all; the device-side half
     * below says whether the image received it. Both are written for the same upload so the two can be compared.
     */
    private void dumpSourcePixels(int glTextureId, GlLevel level) {
        if (!mirrorDumpWanted(glTextureId)) {
            return;
        }
        System.out.println("[Celtium] CELTIUM_SRC glTexture=" + glTextureId + " owned=" + level.owned + " size="
                + level.width + "x" + level.height + " " + pixelSummary(level.pixels, level.width, level.height));
        if ((long) level.width * level.height > MIRROR_DUMP_MAX_PIXELS) {
            return;
        }
        writePng(level.pixels, level.width, level.height,
                new java.io.File(MIRROR_DUMP_DIR, "src-gl" + glTextureId + "-" + level.width + "x" + level.height
                        + "-n" + this.mirrorDumps + "-obj" + Integer.toHexString(System.identityHashCode(this))
                        + ".png"));
    }

    /** How many times this object's image has been dumped; part of the file name so successive uploads are kept apart. */
    private int mirrorDumps;

    /**
     * Reads this mirror's mip levels back from the device and writes them as PNGs, with one line of numbers each.
     * <p>
     * This is the measurement the frame cannot make: it separates "the mirror holds the right pixels and the sampling
     * is wrong" from "the mirror holds black". Every level is read, not only level 0: the sampler blends the levels the
     * clamp admits (mipmap mode LINEAR, {@code maxLod} 1.0), so a correct level 0 with a level 1 that was never written
     * also draws black, and those two faults have different fixes. The line names each level's recorded layout as well,
     * because a level that holds content while the descriptor's declared layout says otherwise is a third fault, and it
     * is invisible in the pixels.
     */
    private void dumpMirrorPixels(int glTextureId) {
        if (!mirrorDumpWanted(glTextureId)) {
            return;
        }
        if (this.mirrorDumps >= MIRROR_DUMPS_PER_OBJECT) {
            return;
        }
        if (this.mirrorDumps == 0) {
            synchronized (VulkanGlTexture.class) {
                if (mirrorDumpsWritten >= MIRROR_DUMP_LIMIT) {
                    return;
                }
                mirrorDumpsWritten++;
            }
        }
        this.mirrorDumps++;
        for (int level = 0; level < Math.max(1, this.mipLevels); level++) {
            if (levelLayout(level) != VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                // The dump transitions the level through TRANSFER_SRC and back. A level that is not at rest in
                // SHADER_READ_ONLY is a level this diagnostic does not understand, and moving it would change the frame
                // it is measuring.
                System.out.println("[Celtium] CELTIUM_MIRROR_DUMP glTexture=" + glTextureId + " level=" + level
                        + " skipped: layout is " + levelLayout(level));
                continue;
            }
            java.nio.ByteBuffer read = readMirrorLevel(level);
            if (read == null) {
                continue;
            }
            try {
                int levelWidth = Math.max(1, this.width >> level);
                int levelHeight = Math.max(1, this.height >> level);
                // The provenance is part of the line because the pixels alone cannot say whether a level is behind:
                // a level whose last write predates the newest level-0 write does not contain that content, which is
                // exactly the state audit N07 describes for a sprite that patches level 0 only.
                System.out.println("[Celtium] CELTIUM_MIRROR_LEVEL glTexture=" + glTextureId + " level=" + level
                        + " size=" + levelWidth + "x" + levelHeight + " layout=" + layoutName(levelLayout(level))
                        + " writes=" + levelWrites(level) + " behind=" + (levelBehind(level) ? "yes" : "no")
                        + " " + pixelSummary(read, levelWidth, levelHeight));
                if ((long) levelWidth * levelHeight > MIRROR_DUMP_MAX_PIXELS) {
                    // The numbers above are the measurement; a megapixel PNG would only cost the frame it measures.
                    continue;
                }
                writePng(read, levelWidth, levelHeight,
                        new java.io.File(MIRROR_DUMP_DIR, "mirror-gl" + glTextureId + "-" + levelWidth + "x"
                                + levelHeight + "-l" + level + "-n" + this.mirrorDumps + "-obj"
                                + Integer.toHexString(System.identityHashCode(this)) + ".png"));
            } finally {
                MemoryUtil.memFree(read);
            }
        }
    }

    /** {@return the name of a layout this class records}, so a level line says which state it is in, not only its pixels. */
    private static String layoutName(int layout) {
        switch (layout) {
            case VK_IMAGE_LAYOUT_UNDEFINED:
                return "UNDEFINED";
            case VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL:
                return "SHADER_READ";
            case VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL:
                return "TRANSFER_SRC";
            case VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL:
                return "TRANSFER_DST";
            case VK_IMAGE_LAYOUT_GENERAL:
                return "GENERAL";
            case VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL:
                return "COLOR_ATTACHMENT";
            default:
                return Integer.toString(layout);
        }
    }

    /**
     * Copies one mip level of this image into host memory.
     * <p>
     * Reuses the staging buffer the uploads already own, copies it into a fresh Java-owned buffer before returning so a
     * concurrent upload cannot overwrite the pixels under the reader, and puts the image back into the layout the
     * sampling side expects.
     *
     * @return the level's tightly packed RGBA8 rows, or {@code null} when there is nothing to read
     */
    private java.nio.ByteBuffer readMirrorLevel(int level) {
        if (this.image == 0L || this.width <= 0 || this.height <= 0 || level < 0 || level >= Math.max(1, this.mipLevels)) {
            return null;
        }
        int levelWidth = Math.max(1, this.width >> level);
        int levelHeight = Math.max(1, this.height >> level);
        long bytes = (long) levelWidth * levelHeight * BYTES_PER_PIXEL;
        ensureStaging(bytes);
        VkCommandBuffer commands = context.beginCommands();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default()
                    .srcAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(this.image);
            barrier.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, level, 1, 0, 1);
            vkCmdPipelineBarrier(commands, SAMPLING_STAGES, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, barrier);
            VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
            region.get(0).bufferOffset(0L).bufferRowLength(0).bufferImageHeight(0);
            region.get(0).imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(level).baseArrayLayer(0)
                    .layerCount(1);
            region.get(0).imageOffset().set(0, 0, 0);
            region.get(0).imageExtent().set(levelWidth, levelHeight, 1);
            vkCmdCopyImageToBuffer(commands, this.image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, this.stagingBuffer, region);
            barrier.get(0).srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT).dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_TRANSFER_BIT, SAMPLING_STAGES, 0, null, null, barrier);
        }
        // Submitted and waited for by the context helper, so the staging buffer holds the level when this returns.
        context.endCommands(commands);
        if (!stagingCoherent) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkMappedMemoryRange.Buffer range = VkMappedMemoryRange.calloc(1, stack);
                range.get(0).sType$Default().memory(stagingMemory).offset(0L).size(VK_WHOLE_SIZE);
                check(vkInvalidateMappedMemoryRanges(context.device(), range), "vkInvalidateMappedMemoryRanges(dump)");
            }
        }
        java.nio.ByteBuffer copy = MemoryUtil.memAlloc((int) bytes);
        MemoryUtil.memCopy(stagingAddress, MemoryUtil.memAddress(copy), bytes);
        return copy;
    }

    /**
     * Writes RGBA8 rows to a PNG, top row first, exactly as the pixels are laid out in memory.
     * <p>
     * No vertical flip: the source dump and the device dump have to be comparable row for row, and a flip here would
     * silently apply to one of them only.
     */
    private static void writePng(java.nio.ByteBuffer pixels, int width, int height, java.io.File target) {
        try {
            target.getParentFile().mkdirs();
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int offset = ((y * width) + x) * BYTES_PER_PIXEL;
                    if (offset + 3 >= pixels.capacity()) {
                        continue;
                    }
                    int r = pixels.get(offset) & 0xFF;
                    int g = pixels.get(offset + 1) & 0xFF;
                    int b = pixels.get(offset + 2) & 0xFF;
                    int a = pixels.get(offset + 3) & 0xFF;
                    image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
            javax.imageio.ImageIO.write(image, "png", target);
        } catch (Throwable ignored) {
            // Debug-only path: never let a dump failure affect rendering.
        }
    }

    /**
     * Writes the mirrored pixels to a PNG when {@code -Dceltium.vulkan.dumpTextures=<dir>} is set. Used while
     * bringing the backend up: comparing what GL hands over with what the shader sees separates an upload problem
     * from a sampling problem. One file per texture per run.
     */
    private void dumpForDebugging(int glTextureId, GlLevel level) {
        String directory = System.getProperty("celtium.vulkan.dumpTextures");
        if (directory == null || directory.isEmpty()) {
            return;
        }
        try {
            // The object identity is part of the name because one GL texture can be mirrored by more than one
            // VulkanGlTexture - the terrain keeps its own atlas and the registry keeps the immediate path's copy - and a
            // comparison that cannot tell those apart cannot say which one the terrain actually samples.
            java.io.File target = new java.io.File(directory, "gl-texture-" + glTextureId + "-" + level.width + "x"
                    + level.height + "-obj" + Integer.toHexString(System.identityHashCode(this)) + ".png");
            // One file per texture is enough when the question is "what shape does the mirror have". When the question is
            // "is the mirror stale right now", the last upload is the one that matters, so the overwrite mode keeps it.
            if (target.isFile() && !Boolean.getBoolean("celtium.vulkan.dumpTexturesEvery")) {
                return;
            }
            target.getParentFile().mkdirs();
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(level.width, level.height,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < level.height; y++) {
                for (int x = 0; x < level.width; x++) {
                    int offset = ((y * level.width) + x) * BYTES_PER_PIXEL;
                    int r = level.pixels.get(offset) & 0xFF;
                    int g = level.pixels.get(offset + 1) & 0xFF;
                    int b = level.pixels.get(offset + 2) & 0xFF;
                    int a = level.pixels.get(offset + 3) & 0xFF;
                    image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
            javax.imageio.ImageIO.write(image, "png", target);
        } catch (Throwable ignored) {
            // Debug-only path: never let a dump failure affect rendering.
        }
    }

    /**
     * Generates the mip chain by blitting each level from the one above it. The OpenGL atlas is sampled with mips,
     * so without this every distant or grazing-angle surface aliases into noise instead of averaging like the
     * OpenGL frame does.
     */
    private void recordMipChain(VkCommandBuffer commandBuffer, int pixelWidth, int pixelHeight, MemoryStack stack) {
        if (this.mipLevels <= 1) {
            return;
        }
        int levelWidth = pixelWidth;
        int levelHeight = pixelHeight;
        // Generating the chain here used to average texels across sprite borders, which is why
        // sampling it deeply measured 7.37-8.77% against the reference while OpenGL's own per-sprite levels measure
        // better. uploadFromGlTexture copies each of GL's levels instead, so this loop exists only for the other mode.
        for (int level = 1; level < this.mipLevels; level++) {
            int nextWidth = Math.max(1, levelWidth / 2);
            int nextHeight = Math.max(1, levelHeight / 2);

            // Level 0 is in TRANSFER_DST_OPTIMAL after the copy; every later level was already moved to
            // TRANSFER_SRC_OPTIMAL by the post-blit barrier of the previous iteration, so only the first pass needs
            // this transition. Doing it twice is an invalid oldLayout and validation rejects it.
            if (level == 1) {
            VkImageMemoryBarrier.Buffer toSource = VkImageMemoryBarrier.calloc(1, stack);
            toSource.get(0).sType$Default()
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image);
            toSource.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, level - 1, 1, 0, 1);
            vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0,
                    null, null, toSource);
            setLevelLayout(level - 1, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
            }

            VkImageBlit.Buffer blit = VkImageBlit.calloc(1, stack);
            blit.get(0).srcSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(level - 1)
                    .baseArrayLayer(0).layerCount(1);
            blit.get(0).srcOffsets().get(0).set(0, 0, 0);
            blit.get(0).srcOffsets().get(1).set(levelWidth, levelHeight, 1);
            blit.get(0).dstSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(level)
                    .baseArrayLayer(0).layerCount(1);
            blit.get(0).dstOffsets().get(0).set(0, 0, 0);
            blit.get(0).dstOffsets().get(1).set(nextWidth, nextHeight, 1);
            vkCmdBlitImage(commandBuffer, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, image,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, blit, VK_FILTER_LINEAR);

            // The blit leaves the destination in TRANSFER_DST_OPTIMAL. Moving it to TRANSFER_SRC_OPTIMAL right away
            // keeps every level in one known layout, so the single transition to SHADER_READ_ONLY_OPTIMAL at the end
            // can cover the whole chain with one barrier instead of one per level (validation rejects a range whose
            // members sit in different layouts).
            VkImageMemoryBarrier.Buffer toSrc = VkImageMemoryBarrier.calloc(1, stack);
            toSrc.get(0).sType$Default()
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image);
            toSrc.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, level, 1, 0, 1);
            vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0,
                    null, null, toSrc);
            setLevelLayout(level, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);

            levelWidth = nextWidth;
            levelHeight = nextHeight;
        }
    }

    /** Records a level's layout, growing the array when a recreated image has more levels than the previous one had. */
    private void setLevelLayout(int level, int layout) {
        if (level < 0) {
            return;
        }
        if (level >= this.levelLayouts.length) {
            this.levelLayouts = java.util.Arrays.copyOf(this.levelLayouts, level + 1);
        }
        this.levelLayouts[level] = layout;
    }

    /** {@return the recorded layout of a level}, or {@code UNDEFINED} when nothing is known about it yet. */
    private int levelLayout(int level) {
        return level >= 0 && level < this.levelLayouts.length ? this.levelLayouts[level] : VK_IMAGE_LAYOUT_UNDEFINED;
    }

    private void copyToImage(ByteBuffer pixels, int pixelWidth, int pixelHeight) {
        copyToImage(pixels, pixelWidth, pixelHeight, 0);
    }

    private void copyToImage(ByteBuffer pixels, int pixelWidth, int pixelHeight, int level) {
        copyToImage(pixels, pixelWidth, pixelHeight, level, 0, 0);
    }

    /**
     * Writes one level's pixels into mapped staging memory at {@code destination}.
     * <p>
     * The single place the poison, coordinate-encode and row-order rules are applied, so the per-level patch path
     * (audit N07) and the whole-image path cannot drift apart on which pixels reach the device. The source address is
     * {@link MemoryUtil#memAddress(ByteBuffer)}, which starts at the buffer's current position, so the bytes written
     * are exactly the slice {@link #requiredUploadBytes} validated.
     */
    private void putStagingPixels(long destination, ByteBuffer pixels, int pixelWidth, int pixelHeight) {
        long bytes = (long) pixelWidth * (long) pixelHeight * BYTES_PER_PIXEL;
        if (this.flatPoisonColour != 0) {
            // One colour over every texel: the frame then answers "does this draw sample this image" with no masking
            // case, because there is no second colour to land on.
            int colour = this.flatPoisonColour;
            for (long offset = 0; offset < bytes; offset += BYTES_PER_PIXEL) {
                MemoryUtil.memPutByte(destination + offset, (byte) (colour >> 16));
                MemoryUtil.memPutByte(destination + offset + 1, (byte) (colour >> 8));
                MemoryUtil.memPutByte(destination + offset + 2, (byte) colour);
                MemoryUtil.memPutByte(destination + offset + 3, (byte) (colour >>> 24));
            }
        } else if (encodeIds.contains(Integer.valueOf(this.encodeLabel))) {
            // Coordinate encoding: R and G carry the sampled texel's position in the atlas, so the frame reads back the
            // texture coordinates the shader used. A single flat colour cannot say whether a UV is degenerate or merely
            // landing on an empty texel, and that is the difference between a broken vertex attribute and an empty atlas.
            int rowBytes = pixelWidth * BYTES_PER_PIXEL;
            for (int y = 0; y < pixelHeight; y++) {
                long rowBase = destination + (long) y * rowBytes;
                int g = pixelHeight > 1 ? (y * 255) / (pixelHeight - 1) : 0;
                for (int x = 0; x < pixelWidth; x++) {
                    int r = pixelWidth > 1 ? (x * 255) / (pixelWidth - 1) : 0;
                    long offset = rowBase + (long) x * BYTES_PER_PIXEL;
                    MemoryUtil.memPutByte(offset, (byte) r);
                    MemoryUtil.memPutByte(offset + 1, (byte) g);
                    MemoryUtil.memPutByte(offset + 2, (byte) 0x80);
                    MemoryUtil.memPutByte(offset + 3, (byte) 0xFF);
                }
            }
        } else if (this.poisonColour != 0) {
            // A per-texel checkerboard of the colour and its inverse rather than the pixels: the frame then says both
            // whether anything samples this image and whether its texture coordinates vary across it. One flat colour
            // could not tell a correct mapping into this image apart from a mapping that is degenerate and lands on a
            // single texel - and a degenerate mapping samples the atlas's empty corner, which is black.
            int argb = this.poisonColour;
            int inverse = argb ^ 0x00FFFFFF;
            int rowBytes = pixelWidth * BYTES_PER_PIXEL;
            for (int y = 0; y < pixelHeight; y++) {
                long rowBase = destination + (long) y * rowBytes;
                for (int x = 0; x < pixelWidth; x++) {
                    int colour = ((x + y) & 1) == 0 ? argb : inverse;
                    long offset = rowBase + (long) x * BYTES_PER_PIXEL;
                    MemoryUtil.memPutByte(offset, (byte) (colour >> 16));
                    MemoryUtil.memPutByte(offset + 1, (byte) (colour >> 8));
                    MemoryUtil.memPutByte(offset + 2, (byte) colour);
                    MemoryUtil.memPutByte(offset + 3, (byte) (colour >>> 24));
                }
            }
        } else if (FLIP_ROWS) {
            // Off, and measured: flipping every mirrored row turned the terrain almost black (band luminance 3.2 against
            // the reference's 131) because the atlas's UVs and this upload are both in OpenGL's row order, so the flip
            // inverted every block face. The earlier note that a flip "made the whole terrain black" was right, and this
            // round's attempt to overrule it is recorded as the mistake it was. The light map's darkness has another
            // cause; the flip is kept only as a lab flag.
            int rowBytes = pixelWidth * BYTES_PER_PIXEL;
            for (int y = 0; y < pixelHeight; y++) {
                MemoryUtil.memCopy(MemoryUtil.memAddress(pixels) + (long) y * rowBytes,
                        destination + (long) (pixelHeight - 1 - y) * rowBytes, rowBytes);
            }
        } else {
            MemoryUtil.memCopy(MemoryUtil.memAddress(pixels), destination, bytes);
        }
    }

    /**
     * Copies pixels into the image, optionally preserving what is already there.
     * <p>
     * A whole upload rewrites every level, so it starts from {@code UNDEFINED} and records a mip chain. A region patch
     * must not, and no longer comes through here: it writes only the levels its producer supplied, each in its own
     * layout, in {@link #uploadPatchedLevels}.
     */
    private void copyToImage(ByteBuffer pixels, int pixelWidth, int pixelHeight, int level, int offsetX, int offsetY) {
        // Every path that reaches the staging buffer goes through the same slice validation: the declared extent must
        // fit the buffer's remaining bytes from its current position, which is where the address the copy takes starts
        // (audit N08). Refused by name, before ensureStaging allocates anything.
        long bytes = requiredUploadBytes("a whole-image upload of " + pixelWidth + "x" + pixelHeight, pixelWidth,
                pixelHeight, pixels);
        ensureStaging(bytes);
        // The staging buffer is host visible and persistently mapped, so the pixels go straight from the readback
        // buffer into device visible memory without an intermediate Java array. The row order is kept as GL
        // returned it; a vertical flip here made the whole terrain black, so the orientation question is not
        // settled and must be answered with a controlled pattern instead of by flipping this copy.
        putStagingPixels(stagingAddress, pixels, pixelWidth, pixelHeight);
        if (!stagingCoherent) {
            // Non-coherent host memory needs an explicit flush before the copy; coherent memory (the common case)
            // is made available to the device by the queue submission itself.
            flushStaging();
        }

        VkCommandBuffer commandBuffer = context.beginCommands();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
            // The whole chain is re-uploaded from scratch, so the old layout may be anything - including the
            // VK_IMAGE_LAYOUT_UNDEFINED an image starts in, which validation rejects as an oldLayout for a
            // first transition. Every mip level is covered because the levels are regenerated below.
            barrier.get(0).sType$Default()
                    .srcAccessMask(0)
                    .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image);
            barrier.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, level, this.mipLevels, 0, 1);
            if (level == 0) { vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0, null, null, barrier); }

            VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
            // A row length of 0 means the rows are tightly packed, which is exactly how GL returned them.
            region.get(0).bufferOffset(0L).bufferRowLength(0).bufferImageHeight(0);
            region.get(0).imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(level).baseArrayLayer(0).layerCount(1);
            region.get(0).imageOffset().set(offsetX, offsetY, 0);
            region.get(0).imageExtent().set(pixelWidth, pixelHeight, 1);
            vkCmdCopyBufferToImage(commandBuffer, stagingBuffer, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
            this.currentLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
            for (int covered = level; covered < this.mipLevels; covered++) {
                setLevelLayout(covered, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
            }

            recordMipChain(commandBuffer, pixelWidth, pixelHeight, stack);

            // Reuse the same barrier struct for the way back: the image and subresource range are unchanged, only
            // the access masks and layouts differ.
            // Every level ends in a different transfer layout (the blit chain leaves levels in TRANSFER_SRC or
            // TRANSFER_DST), so the range covers them all and the source accesses include both transfer roles.
            barrier.get(0)
                    .srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT | VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    // Mirrored levels are copied, not blitted, so the image is still in TRANSFER_DST when they are done -
                    // declaring TRANSFER_SRC here is what validation rejected with fifteen errors, the same class of
                    // mistake the Intel run caught in the mip chain (audit S23).
                    .oldLayout(this.mipLevels > 1 ? VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL : this.currentLayout)
                    .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            // The tail transition belongs to whichever call finishes the chain: the last mirrored level, or level 0
                // when the chain is generated by blitting below. Getting this wrong left images in a transfer layout -
                // ten validation errors and no SHADER_READ_ONLY for the atlas (audit S23).
                if (level == this.mipLevels - 1 || level == 0) {
                    vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT, SAMPLING_STAGES, 0, null, null,
                            barrier);
                    for (int covered = 0; covered < this.mipLevels; covered++) {
                        setLevelLayout(covered, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
                    }
                }
        } finally {
            // Always close the one-shot command buffer, also when recording threw half way through, so the
            // context's helper never keeps an open command buffer around.
            context.endCommands(commandBuffer);
        }
        // The copy and the blitted chain are submitted and waited for, so every level now holds content from this
        // write: level 0 from the copy, the levels above it from the chain. Recorded after the wait, never before, so
        // a failed upload cannot claim levels it did not write (audit N07).
        recordWholeImageWrite();
    }

    /**
     * Diagnostic: read this mirror's level 0 back from the device and compare it, byte for byte, with the level 0 the
     * OpenGL texture currently holds. Every other instrument in this area has compared frames or reasoned about
     * coordinates; this compares the two images themselves.
     */
    public void compareLevelZeroWithGl(int glTextureId) {
        requireOwned("a level-0 comparison");
        int w = this.width;
        int h = this.height;
        if (w <= 0 || h <= 0) {
            return;
        }
        long bytes = (long) w * h * BYTES_PER_PIXEL;
        ensureStaging(bytes);
        VkCommandBuffer commands = context.beginCommands();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default()
                    .srcAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(this.image);
            barrier.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
            vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0,
                    null, null, barrier);
            VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
            region.get(0).bufferOffset(0L).bufferRowLength(0).bufferImageHeight(0);
            region.get(0).imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0)
                    .layerCount(1);
            region.get(0).imageOffset().set(0, 0, 0);
            region.get(0).imageExtent().set(w, h, 1);
            vkCmdCopyImageToBuffer(commands, this.image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, this.stagingBuffer,
                    region);
            barrier.get(0).srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT).dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_TRANSFER_BIT, SAMPLING_STAGES, 0, null, null, barrier);
        }
        context.endCommands(commands);

        GlLevel gl = readGlLevel0(glTextureId, GL_TEXTURE_2D);
        try {
            long differing = 0;
            long total = 0;
            int maxDiff = 0;
            long sumDiff = 0;
            for (int i = 0; i < w * h * BYTES_PER_PIXEL; i++) {
                int ours = MemoryUtil.memGetByte(this.stagingAddress + i) & 0xFF;
                int theirs = gl.pixels.get(i) & 0xFF;
                int d = Math.abs(ours - theirs);
                total++;
                sumDiff += d;
                if (d != 0) {
                    differing++;
                }
                if (d > maxDiff) {
                    maxDiff = d;
                }
            }
            System.out.println("[Celtium] CELTIUM_LEVEL0 size=" + w + "x" + h
                    + " bytes=" + total + " differing=" + differing
                    + " fraction=" + String.format("%.4f", (double) differing / Math.max(1L, total))
                    + " meanAbsDiff=" + String.format("%.2f", (double) sumDiff / Math.max(1L, total))
                    + " maxAbsDiff=" + maxDiff);
        } finally {
            // Same rule as the upload path: only a buffer this class allocated is freed, a captured upload is the
            // stub's. This diagnostic is gated off in a GL-less run, but the free must not depend on that gate.
            if (gl.owned) {
                MemoryUtil.memFree(gl.pixels);
            }
        }
    }

    /**
     * Makes the cached staging buffer at least {@code required} bytes, recreating it when it is too small.
     * <p>
     * The buffer, its memory and its mapping are the three things this can create, and a failure part-way through
     * must not leave any of them behind: the fields used to be assigned as each step succeeded, so an injected
     * allocation failure after {@code vkCreateBuffer} left a buffer no later call would ever size or free, and an
     * object whose staging area was half-built but whose capacity still read zero. The cleanup below releases
     * everything created so far and leaves the object exactly as it was before the call, so the next upload can try
     * again and a failed one cannot be mistaken for a working staging area (audit N17).
     */
    private void ensureStaging(long required) {
        if (stagingBuffer != 0L && stagingCapacity >= required) {
            return;
        }
        destroyStaging();
        VkDevice device = context.device();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo create = VkBufferCreateInfo.calloc(stack).sType$Default()
                    .size(required)
                    // TRANSFER_DST as well as SRC: the pixels are written into the image from here, and
                    // compareLevelZeroWithGl - and the mirror dump - copy the image back out into this same buffer.
                    // Without DST the readback copy is a validation error, and the buffer keeps whatever the last
                    // upload left in it, so the comparison reports a perfect match by comparing an upload with
                    // itself (VUID-vkCmdCopyImageToBuffer-dstBuffer-00191, measured: ten messages a run).
                    .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateBuffer(device, create, null, handle), "vkCreateBuffer(staging)");
            stagingBuffer = handle.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device, stagingBuffer, requirements);
            int memoryType = findMemoryType(requirements.memoryTypeBits(), VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                    VK_MEMORY_PROPERTY_HOST_COHERENT_BIT, "staging memory");
            VkMemoryAllocateInfo allocate = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(memoryType);
            if (org.soutalab.celtium.vulkan.VulkanAllocationFault.shouldFail("VulkanGlTexture:979")) {
                throw new IllegalStateException("injected allocation failure: VulkanGlTexture:979");
            }
            check(vkAllocateMemory(device, allocate, null, handle), "vkAllocateMemory(staging)");
            stagingMemory = handle.get(0);
            check(vkBindBufferMemory(device, stagingBuffer, stagingMemory, 0L), "vkBindBufferMemory(staging)");

            // Map once and keep the mapping: a stale mapping would be a bug, and remapping per upload is pure
            // overhead for a buffer that is reused for every refresh.
            PointerBuffer pointer = stack.mallocPointer(1);
            check(vkMapMemory(device, stagingMemory, 0L, VK_WHOLE_SIZE, 0, pointer), "vkMapMemory(staging)");
            stagingAddress = pointer.get(0);
            stagingCapacity = requirements.size();
            stagingCoherent = isHostCoherent(memoryType);
        } catch (RuntimeException | LinkageError error) {
            // The buffer may exist with no memory, the memory with no mapping, or the mapping with no capacity
            // recorded. All three are released and the capacity stays zero, so nothing can copy through a staging
            // area that was never finished (audit N17).
            long releasedBuffer = stagingBuffer;
            long releasedMemory = stagingMemory;
            boolean releasedMapping = stagingAddress != 0L;
            destroyStaging();
            synchronized (VulkanGlTexture.class) {
                stagingReleases++;
                if (stagingReleases <= 4 || stagingReleases % 100 == 0) {
                    System.out.println("[Celtium] CELTIUM_STAGING_RELEASED buffer=0x"
                            + Long.toHexString(releasedBuffer) + " memory=0x" + Long.toHexString(releasedMemory)
                            + " unmapped=" + releasedMapping + " requested=" + required + " bytes=[" + error
                            + "] (a staging allocation that failed part-way released what it created; the next upload"
                            + " rebuilds it) releases=" + stagingReleases);
                }
            }
            throw error;
        }
    }

    /** Staging allocations that failed part-way and were released; a run says whether the path was exercised. */
    private static long stagingReleases;
    /** Images whose creation failed part-way and whose remains were retired; see {@link #createImageResources}. */
    private static long imageReleases;

    private void destroyStaging() {
        VkDevice device = context.device();
        if (stagingAddress != 0L && stagingMemory != 0L) {
            vkUnmapMemory(device, stagingMemory);
            stagingAddress = 0L;
        }
        if (stagingBuffer != 0L) {
            vkDestroyBuffer(device, stagingBuffer, null);
            stagingBuffer = 0L;
        }
        if (stagingMemory != 0L) {
            vkFreeMemory(device, stagingMemory, null);
            stagingMemory = 0L;
        }
        stagingCapacity = 0L;
        stagingCoherent = false;
    }

    private void flushStaging() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkMappedMemoryRange.Buffer range = VkMappedMemoryRange.calloc(1, stack);
            // VK_WHOLE_SIZE flushes the whole mapped range, which avoids having to round the written length up to
            // nonCoherentAtomSize (flushing more than was written is always allowed).
            range.get(0).sType$Default().memory(stagingMemory).offset(0L).size(VK_WHOLE_SIZE);
            check(vkFlushMappedMemoryRanges(context.device(), range), "vkFlushMappedMemoryRanges(staging)");
        }
    }

    private boolean isHostCoherent(int memoryTypeIndex) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceMemoryProperties properties = VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(context.physicalDevice(), properties);
            return (properties.memoryTypes(memoryTypeIndex).propertyFlags()
                    & VK_MEMORY_PROPERTY_HOST_COHERENT_BIT) != 0;
        }
    }

    /**
     * Picks a memory type index for the given requirement mask.
     *
     * @param typeBits      the {@code memoryTypeBits} reported by {@code vkGetImageMemoryRequirements} or
     *                      {@code vkGetBufferMemoryRequirements}
     * @param required      properties the type must have
     * @param preferred     properties that are preferred but not required
     * @param what          a description used in the error message
     * @return the index of a suitable memory type
     * @throws IllegalStateException if no type satisfies the required properties
     */
    private int findMemoryType(int typeBits, int required, int preferred, String what) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceMemoryProperties properties = VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(context.physicalDevice(), properties);
            int fallback = -1;
            for (int i = 0; i < properties.memoryTypeCount(); i++) {
                if ((typeBits & (1 << i)) == 0) {
                    continue;
                }
                int flags = properties.memoryTypes(i).propertyFlags();
                if ((flags & required) != required) {
                    continue;
                }
                if ((flags & preferred) == preferred) {
                    return i;
                }
                if (fallback < 0) {
                    fallback = i;
                }
            }
            if (fallback < 0) {
                throw new IllegalStateException("No Vulkan memory type for " + what + " satisfies 0x"
                        + Integer.toHexString(required) + " within the allowed mask 0x"
                        + Integer.toHexString(typeBits));
            }
            return fallback;
        }
    }

    /**
     * Reads level 0 of a GL texture into a direct buffer, restoring all GL state it touches.
     * <p>
     * With a GL context this is the readback, and the buffer is freshly allocated here. Without one it is the upload the
     * game itself made, which the stub kept: same shape, same RGBA8 rows, no GL state to save or restore. Whether the
     * caller owns the buffer is what {@link GlLevel#owned} says.
     *
     * @return the pixel buffer plus the source size
     */
    private static GlLevel readGlLevel0(int glTextureId, int glTextureTarget) {
        return readGlLevel(glTextureId, glTextureTarget, 0);
    }

    /** How many levels the GL texture actually has, from its own {@code GL_TEXTURE_MAX_LEVEL}. */

    private static GlLevel readGlLevel(int glTextureId, int glTextureTarget, int level) {
        if (glTextureTarget != GL_TEXTURE_2D) {
            throw new IllegalArgumentException("Only GL_TEXTURE_2D can be mirrored, but the caller passed target 0x"
                    + Integer.toHexString(glTextureTarget) + "; a cube map or array texture would need one copy"
                    + " per layer");
        }
        if (glTextureId == 0) {
            throw new IllegalArgumentException("GL texture name 0 is not a texture");
        }
        if (org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()) {
            // No context to read from: the only pixels that exist for this texture are the ones the game handed to GL,
            // and the stub kept them. Taken before any of the GL state below is touched, because under the stub those
            // queries answer recorded values and the binding they report is not a binding anything can be restored to.
            return readCapturedLevel(glTextureId, level);
        }
        // The unit that is active on entry is reused instead of switching to unit 0, so the state that changes is
        // as small as possible. Whatever unit it is, its binding and the pack alignment are restored below.
        int previousUnit = glGetInteger(GL_ACTIVE_TEXTURE);
        int previousBinding = glGetInteger(GL_TEXTURE_BINDING_2D);
        int previousPackAlignment = glGetInteger(GL_PACK_ALIGNMENT);
        // Discard errors left behind by earlier GL work so that the checks below can only report failures caused by
        // this method. Nothing is lost: Minecraft does not read the GL error state.
        while (glGetError() != GL_NO_ERROR) {
            // Drain the error queue.
        }
        ByteBuffer pixels = null;
        try {
            // The classic bind-and-query path is used instead of GL 4.5's glGetTextureLevelParameteri /
            // glGetTextureImage: direct state access needs ARB_direct_state_access, and Minecraft's context is only
            // guaranteed to be a compatibility context, so the query that works everywhere wins over the shorter one.
            glBindTexture(GL_TEXTURE_2D, glTextureId);
            int textureWidth = glGetTexLevelParameteri(GL_TEXTURE_2D, level, GL_TEXTURE_WIDTH);
            int textureHeight = glGetTexLevelParameteri(GL_TEXTURE_2D, level, GL_TEXTURE_HEIGHT);
            int internalFormat = glGetTexLevelParameteri(GL_TEXTURE_2D, level, GL_TEXTURE_INTERNAL_FORMAT);
            checkGl("querying GL texture " + glTextureId);
            if (textureWidth <= 0 || textureHeight <= 0) {
                throw new IllegalStateException("GL texture " + glTextureId + " has no level-0 storage ("
                        + textureWidth + "x" + textureHeight + ", internal format 0x"
                        + Integer.toHexString(internalFormat) + ")");
            }
            long bytes = (long) textureWidth * (long) textureHeight * BYTES_PER_PIXEL;
            if (bytes > Integer.MAX_VALUE) {
                throw new IllegalStateException("GL texture " + glTextureId + " is too large to mirror ("
                        + bytes + " bytes)");
            }
            pixels = MemoryUtil.memAlloc((int) bytes);
            // Rows of RGBA bytes are always a multiple of 4 bytes, but a pack alignment of 1 removes the last way
            // a driver could pad the readback and is the value the tightly packed Vulkan copy assumes.
            glPixelStorei(GL_PACK_ALIGNMENT, 1);
            glGetTexImage(GL_TEXTURE_2D, level, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            // The internal format is part of the message because GL only permits this conversion for colour
            // formats; an integer or depth texture would be rejected here and the format identifies it.
            checkGl("reading level " + level + " of GL texture " + glTextureId + " as RGBA bytes (internal format 0x"
                    + Integer.toHexString(internalFormat) + ")");
            return new GlLevel(pixels, textureWidth, textureHeight);
        } catch (RuntimeException | Error error) {
            if (pixels != null) {
                MemoryUtil.memFree(pixels);
            }
            throw error;
        } finally {
            // GL state is per unit, so the binding of the unit we used has to be restored before the active unit
            // itself is put back.
            glPixelStorei(GL_PACK_ALIGNMENT, previousPackAlignment);
            glBindTexture(GL_TEXTURE_2D, previousBinding);
            glActiveTexture(previousUnit);
        }
    }

    /**
     * The level of a GL texture as the bytes the game itself uploaded, for a run in which there is no GL context.
     * <p>
     * Under the stub there is no texture storage to read and {@code glGetTexImage} writes nothing: the pixels only exist
     * in the upload the game made, which the stub keeps. Asking it for them is the whole substitute for the readback,
     * and the answer is used exactly like a readback buffer - tightly packed RGBA8, the rows in the order the game
     * supplied them, no flip here and none there - so only the ownership differs.
     * <p>
     * Nothing is returned when the stub has no capture. A buffer nothing wrote, memcpy'd into the image, is a texture of
     * noise that the renderer samples long before any check could notice it; leaving the image without content instead
     * is what makes {@link #hasFullContent} false and the descriptor decision refuse the mirror.
     *
     * @return the captured pixels, owned by the stub (the caller must not free them) plus the captured size
     * @throws IllegalStateException if the stub captured nothing usable for this texture level
     */
    private static GlLevel readCapturedLevel(int glTextureId, int level) {
        // The one size source for this path: the capture reports the extent it was uploaded with, and the pixels and
        // that extent come from the same call, so no second query can disagree with them.
        int[] capturedSize = new int[2];
        ByteBuffer captured = org.soutalab.celtium.vulkan.gl.CeltiumGlStub.textureUploadRgba8(glTextureId, level,
                capturedSize);
        if (captured == null) {
            throw captureMiss(glTextureId, level, "the stub captured no upload for this texture level");
        }
        int capturedWidth = capturedSize[0];
        int capturedHeight = capturedSize[1];
        if (capturedWidth <= 0 || capturedHeight <= 0) {
            throw captureMiss(glTextureId, level, "the captured upload reports size " + capturedWidth + "x"
                    + capturedHeight);
        }
        long bytes = (long) capturedWidth * (long) capturedHeight * BYTES_PER_PIXEL;
        if (bytes > Integer.MAX_VALUE) {
            throw captureMiss(glTextureId, level, "the captured upload is too large to mirror (" + bytes + " bytes)");
        }
        if (!captured.isDirect()) {
            throw captureMiss(glTextureId, level, "the captured upload is not a direct buffer, so its bytes have no"
                    + " address to copy from");
        }
        if (captured.position() != 0) {
            // The hash and the mip dump read this buffer with absolute indices while the copy takes its address, and
            // MemoryUtil.memAddress returns the address of the position - so a buffer that does not start at 0 would be
            // hashed from one byte and uploaded from another. Refused rather than repaired: an upload whose bytes are
            // not the ones the game handed over is exactly the class of silent wrongness this path exists to remove.
            throw captureMiss(glTextureId, level, "the captured upload starts at position " + captured.position()
                    + ", expected 0");
        }
        if (captured.limit() < (int) bytes) {
            throw captureMiss(glTextureId, level, "the captured upload holds " + captured.limit()
                    + " readable bytes, expected " + bytes + " for " + capturedWidth + "x" + capturedHeight);
        }
        return new GlLevel(captured, capturedWidth, capturedHeight, false);
    }

    private static void checkGl(String operation) {
        int error = glGetError();
        if (error != GL_NO_ERROR) {
            throw new IllegalStateException(operation + " failed with GL error 0x" + Integer.toHexString(error));
        }
    }

    private void requireAlive() {
        if (deleted) {
            throw new IllegalStateException("The mirrored texture was deleted");
        }
    }

    /**
     * Refuses a write, a resize or a diagnostic copy on a wrapper whose image belongs to someone else.
     * <p>
     * A shared image is handed over for sampling only. Writing it would overwrite the frame the owner rendered into
     * it, resizing it would replace a resource the owner still holds, and reading it through the mirror dump would
     * transition it out of the layout the handshake promised. All three are refused with the reason rather than
     * silently doing the wrong thing to another owner's image.
     */
    private void requireOwned(String what) {
        requireAlive();
        if (!this.ownsImage) {
            throw new IllegalStateException("This wrapper shares an image the renderer owns, so it cannot be used for "
                    + what + "; the content of a shared image is what its owner rendered into it");
        }
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " returned " + result);
        }
    }

    /** One GL level-0 readback or captured upload: the pixels plus the size they were read or captured with. */
    private static final class GlLevel {
        final ByteBuffer pixels;
        final int width;
        final int height;
        /** Whether this class allocated {@link #pixels} and has to free it; a captured upload belongs to the stub. */
        final boolean owned;

        GlLevel(ByteBuffer pixels, int width, int height) {
            this(pixels, width, height, true);
        }

        GlLevel(ByteBuffer pixels, int width, int height, boolean owned) {
            this.pixels = pixels;
            this.width = width;
            this.height = height;
            this.owned = owned;
        }
    }
}
