package org.soutalab.celtium.vulkan.hiz;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable mip-chain geometry and compute dispatch plan for P3 ({@code HiZBuild}).
 *
 * <p>Matches {@code VanillaDirectGraph}'s {@code hiz} transient resource dimensions
 * ({@code max(1, depthWidth / 2) x max(1, depthHeight / 2)}, {@code VK_FORMAT_R32_SFLOAT})
 * and plans every downsample dispatch down to {@code 1 x 1}, flagging odd source dimensions
 * so {@code hiz_build.comp} folds trailing edge texels conservatively without cracks.
 *
 * <p>Pure Java with zero Vulkan handle ownership so it can be verified standalone and reused
 * across swapchain recreations without allocating during frame recording.
 */
public final class HiZPyramidPlan {

    /** Workgroup edge size matching {@code layout(local_size_x = 8, local_size_y = 8)} in {@code hiz_build.comp}. */
    public static final int LOCAL_SIZE = 8;

    /** Bytes per texel for {@code VK_FORMAT_R32_SFLOAT}. */
    public static final int BYTES_PER_TEXEL = 4;

    private final int depthWidth;
    private final int depthHeight;
    private final int baseWidth;
    private final int baseHeight;
    private final List<MipDispatch> dispatches;
    private final int totalWorkgroups;
    private final long estimatedBytes;

    private HiZPyramidPlan(int depthWidth,
                           int depthHeight,
                           int baseWidth,
                           int baseHeight,
                           List<MipDispatch> dispatches,
                           int totalWorkgroups,
                           long estimatedBytes) {
        this.depthWidth = depthWidth;
        this.depthHeight = depthHeight;
        this.baseWidth = baseWidth;
        this.baseHeight = baseHeight;
        this.dispatches = Collections.unmodifiableList(dispatches);
        this.totalWorkgroups = totalWorkgroups;
        this.estimatedBytes = estimatedBytes;
    }

    /**
     * Builds the Hi-Z pyramid plan for a main depth attachment of {@code depthWidth x depthHeight}.
     *
     * @throws IllegalArgumentException if either dimension is {@code <= 0}
     */
    public static HiZPyramidPlan forDepthExtent(int depthWidth, int depthHeight) {
        if (depthWidth <= 0 || depthHeight <= 0) {
            throw new IllegalArgumentException(
                    "depth dimensions must be positive, got " + depthWidth + "x" + depthHeight);
        }

        int baseW = Math.max(1, depthWidth / 2);
        int baseH = Math.max(1, depthHeight / 2);

        List<MipDispatch> list = new ArrayList<>();
        int srcW = depthWidth;
        int srcH = depthHeight;
        int dstW = baseW;
        int dstH = baseH;
        int mip = 0;
        int totalGroups = 0;
        long totalTexels = 0L;

        while (true) {
            int gx = (dstW + LOCAL_SIZE - 1) / LOCAL_SIZE;
            int gy = (dstH + LOCAL_SIZE - 1) / LOCAL_SIZE;
            boolean oddX = (srcW > 1) && ((srcW & 1) != 0);
            boolean oddY = (srcH > 1) && ((srcH & 1) != 0);
            list.add(new MipDispatch(mip, srcW, srcH, dstW, dstH, gx, gy, oddX, oddY));
            totalGroups += gx * gy;
            totalTexels += (long) dstW * (long) dstH;

            if (dstW == 1 && dstH == 1) {
                break;
            }
            srcW = dstW;
            srcH = dstH;
            dstW = Math.max(1, dstW / 2);
            dstH = Math.max(1, dstH / 2);
            mip++;
        }

        return new HiZPyramidPlan(
                depthWidth,
                depthHeight,
                baseW,
                baseH,
                list,
                totalGroups,
                totalTexels * BYTES_PER_TEXEL);
    }

    public int depthWidth() {
        return depthWidth;
    }

    public int depthHeight() {
        return depthHeight;
    }

    /** Width of mip 0 of the Hi-Z image ({@code max(1, depthWidth / 2)}). */
    public int baseWidth() {
        return baseWidth;
    }

    /** Height of mip 0 of the Hi-Z image ({@code max(1, depthHeight / 2)}). */
    public int baseHeight() {
        return baseHeight;
    }

    public int mipLevels() {
        return dispatches.size();
    }

    public List<MipDispatch> dispatches() {
        return dispatches;
    }

    public MipDispatch mip(int level) {
        return dispatches.get(level);
    }

    public int totalWorkgroups() {
        return totalWorkgroups;
    }

    /** Estimated GPU memory footprint for the full {@code R32_SFLOAT} mip chain in bytes. */
    public long estimatedBytes() {
        return estimatedBytes;
    }

    /**
     * Selects the conservative Hi-Z mip level for an object whose projected screen-space
     * bounding box covers {@code screenSpanPixels} pixels (max of width and height in
     * full-resolution depth pixels). Because mip 0 is already half resolution, a span of
     * {@code <= 2} full-res pixels maps to mip 0.
     */
    public int selectMipLevel(float screenSpanPixels) {
        if (screenSpanPixels <= 2.0f) {
            return 0;
        }
        // Half-res span at mip 0 is screenSpanPixels * 0.5f.
        double halfSpan = screenSpanPixels * 0.5;
        int mip = (int) Math.ceil(Math.log(halfSpan) / Math.log(2.0));
        if (mip < 0) {
            return 0;
        }
        int maxMip = dispatches.size() - 1;
        return Math.min(mip, maxMip);
    }

    /**
     * Reference conservative occlusion test matching {@code hiz_build.comp} and {@code terrain_cull_a.comp}.
     *
     * @param boxNearestDepth nearest depth of the tested AABB (min Z in standard-Z, max Z in reverse-Z)
     * @param hizSampledDepth reduced depth read from the Hi-Z pyramid
     * @param reverseZ        {@code false} for standard Vulkan [0,1] near=0..far=1; {@code true} for reverse-Z
     * @return {@code true} if the box is strictly behind the occluder and may be culled
     */
    public static boolean isOccluded(float boxNearestDepth, float hizSampledDepth, boolean reverseZ) {
        return reverseZ
                ? (boxNearestDepth < hizSampledDepth)
                : (boxNearestDepth > hizSampledDepth);
    }

    /** Single mip-level downsample dispatch descriptor. */
    public static final class MipDispatch {
        private final int mipLevel;
        private final int srcWidth;
        private final int srcHeight;
        private final int dstWidth;
        private final int dstHeight;
        private final int groupsX;
        private final int groupsY;
        private final boolean oddSrcX;
        private final boolean oddSrcY;

        public MipDispatch(int mipLevel,
                           int srcWidth,
                           int srcHeight,
                           int dstWidth,
                           int dstHeight,
                           int groupsX,
                           int groupsY,
                           boolean oddSrcX,
                           boolean oddSrcY) {
            this.mipLevel = mipLevel;
            this.srcWidth = srcWidth;
            this.srcHeight = srcHeight;
            this.dstWidth = dstWidth;
            this.dstHeight = dstHeight;
            this.groupsX = groupsX;
            this.groupsY = groupsY;
            this.oddSrcX = oddSrcX;
            this.oddSrcY = oddSrcY;
        }

        public int mipLevel() {
            return mipLevel;
        }

        public int srcWidth() {
            return srcWidth;
        }

        public int srcHeight() {
            return srcHeight;
        }

        public int dstWidth() {
            return dstWidth;
        }

        public int dstHeight() {
            return dstHeight;
        }

        public int groupsX() {
            return groupsX;
        }

        public int groupsY() {
            return groupsY;
        }

        public boolean oddSrcX() {
            return oddSrcX;
        }

        public boolean oddSrcY() {
            return oddSrcY;
        }

        public boolean readsDepthAttachment() {
            return mipLevel == 0;
        }
    }
}
