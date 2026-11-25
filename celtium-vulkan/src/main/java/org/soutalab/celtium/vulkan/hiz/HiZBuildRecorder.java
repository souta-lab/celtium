package org.soutalab.celtium.vulkan.hiz;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * Records the P3 {@code HiZBuild} compute pass across every mip level of a {@link HiZPyramidPlan}.
 *
 * <p>Command emission goes through a thin {@link CommandSink} so the exact barrier/dispatch
 * sequence and 24-byte push-constant layout can be verified in unit tests without a live Vulkan
 * device, and bound directly to {@code vkCmd*} calls during FrameGraph integration.
 *
 * <p>Layout contract:
 * <ul>
 *   <li>On entry, the source depth attachment is in {@code DEPTH_STENCIL_READ_ONLY_OPTIMAL}
 *       (guaranteed by the FrameGraph barrier before P3), and all mips of the Hi-Z image are in
 *       {@code GENERAL} (or transitioned from {@code UNDEFINED} to {@code GENERAL} at pass start).</li>
 *   <li>After each mip {@code k} is written, a single-mip barrier transitions {@code hiz[k]} from
 *       {@code GENERAL} ({@code SHADER_WRITE}) to {@code SHADER_READ_ONLY_OPTIMAL}
 *       ({@code SHADER_READ}), making it immediately readable by mip {@code k + 1} (and leaving
 *       the entire pyramid uniformly in {@code SHADER_READ_ONLY_OPTIMAL} on exit for next frame's
 *       P1 {@code TerrainCull}).</li>
 * </ul>
 */
public final class HiZBuildRecorder {

    /** Size in bytes of {@code HiZPushConstants} in {@code hiz_build.comp} (6 x 4B). */
    public static final int PUSH_CONSTANT_BYTES = 24;

    /** Push-constant flag bit 0: reverse-Z min reduction instead of standard max reduction. */
    public static final int FLAG_REVERSE_Z = 1;

    /** Push-constant flag bit 1: mip 0 reads the full-res depth attachment. */
    public static final int FLAG_SRC_DEPTH_ATTACHMENT = 1 << 1;

    private final boolean reverseZ;
    private long framesRecorded;
    private long totalDispatches;
    private long totalBarriers;
    private int lastWidth;
    private int lastHeight;
    private int lastMipCount;

    public HiZBuildRecorder(boolean reverseZ) {
        this.reverseZ = reverseZ;
    }

    public HiZBuildRecorder() {
        this(false);
    }

    /**
     * Packs the 24-byte {@code HiZPushConstants} block in little-endian layout matching
     * {@code hiz_build.comp}.
     */
    public static void packPushConstants(HiZPyramidPlan.MipDispatch mip,
                                         boolean reverseZ,
                                         ByteBuffer out) {
        if (out.remaining() < PUSH_CONSTANT_BYTES) {
            throw new IllegalArgumentException(
                    "push constant buffer needs " + PUSH_CONSTANT_BYTES + " bytes, has " + out.remaining());
        }
        int flags = (reverseZ ? FLAG_REVERSE_Z : 0)
                | (mip.readsDepthAttachment() ? FLAG_SRC_DEPTH_ATTACHMENT : 0);
        int pos = out.position();
        ByteOrder prevOrder = out.order();
        try {
            out.order(ByteOrder.LITTLE_ENDIAN);
            out.putInt(pos, mip.srcWidth());
            out.putInt(pos + 4, mip.srcHeight());
            out.putInt(pos + 8, mip.dstWidth());
            out.putInt(pos + 12, mip.dstHeight());
            out.putInt(pos + 16, flags);
            out.putInt(pos + 20, mip.mipLevel());
        } finally {
            out.order(prevOrder);
        }
    }

    /**
     * Records the complete Hi-Z mip chain build into {@code sink}.
     *
     * @param plan           precomputed mip chain plan for the current swapchain/depth extent
     * @param sink           Vulkan command sink (or test spy)
     * @param hizNeedsInit   {@code true} if the Hi-Z image starts in {@code UNDEFINED} or
     *                       {@code SHADER_READ_ONLY_OPTIMAL} and must first be transitioned to
     *                       {@code GENERAL} across all mips before writing
     */
    public void record(HiZPyramidPlan plan, CommandSink sink, boolean hizNeedsInit) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(sink, "sink");

        int mips = plan.mipLevels();
        if (hizNeedsInit) {
            sink.transitionAllMipsToGeneral(mips);
            totalBarriers++;
        }

        sink.bindPipeline();

        for (int i = 0; i < mips; i++) {
            HiZPyramidPlan.MipDispatch mip = plan.mip(i);
            int flags = (reverseZ ? FLAG_REVERSE_Z : 0)
                    | (mip.readsDepthAttachment() ? FLAG_SRC_DEPTH_ATTACHMENT : 0);
            sink.bindMipDescriptor(i, mip.readsDepthAttachment() ? -1 : (i - 1));
            sink.pushConstants(mip.srcWidth(), mip.srcHeight(), mip.dstWidth(), mip.dstHeight(), flags, i);
            sink.dispatch(mip.groupsX(), mip.groupsY(), 1);
            sink.transitionMipGeneralToShaderRead(i);
            totalDispatches++;
            totalBarriers++;
        }

        framesRecorded++;
        lastWidth = plan.depthWidth();
        lastHeight = plan.depthHeight();
        lastMipCount = mips;
    }

    public boolean isReverseZ() {
        return reverseZ;
    }

    public long framesRecorded() {
        return framesRecorded;
    }

    public long totalDispatches() {
        return totalDispatches;
    }

    public long totalBarriers() {
        return totalBarriers;
    }

    public int lastWidth() {
        return lastWidth;
    }

    public int lastHeight() {
        return lastHeight;
    }

    public int lastMipCount() {
        return lastMipCount;
    }

    /** Thin command abstraction for recording P3 Hi-Z dispatches and per-mip barriers. */
    public interface CommandSink {
        /** Transitions all {@code mipCount} levels of the Hi-Z image to {@code GENERAL} for compute write. */
        void transitionAllMipsToGeneral(int mipCount);

        /** Binds the {@code hiz_build.comp} compute pipeline. */
        void bindPipeline();

        /**
         * Binds the descriptor set for writing {@code dstMip}.
         *
         * @param dstMip destination Hi-Z mip level {@code [0, mipCount)}
         * @param srcMip source Hi-Z mip level ({@code dstMip - 1}), or {@code -1} when reading the
         *               full-resolution depth attachment
         */
        void bindMipDescriptor(int dstMip, int srcMip);

        /** Uploads the 24-byte {@code HiZPushConstants} block. */
        void pushConstants(int srcWidth, int srcHeight, int dstWidth, int dstHeight, int flags, int mipLevel);

        /** Issues {@code vkCmdDispatch(groupsX, groupsY, groupsZ)}. */
        void dispatch(int groupsX, int groupsY, int groupsZ);

        /**
         * Emits a {@code COMPUTE_SHADER / SHADER_WRITE -> COMPUTE_SHADER / SHADER_READ} image
         * barrier transitioning {@code hiz[mipLevel]} from {@code GENERAL} to
         * {@code SHADER_READ_ONLY_OPTIMAL}.
         */
        void transitionMipGeneralToShaderRead(int mipLevel);
    }
}
