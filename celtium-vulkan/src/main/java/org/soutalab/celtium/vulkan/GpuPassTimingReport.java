package org.soutalab.celtium.vulkan;

import java.util.Map;

import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;

/**
 * The one place that decides how GPU pass timings are reported.
 *
 * <p>The timings were collected from the timestamp pool and never printed, so the question they answer - whether a frame
 * is GPU-bound or waiting on the present - could not be asked; the present counter alone reads as GPU-bound without
 * being proof. This class owns the answer's shape: microseconds per pass, every {@value #EVERY_FRAME} frames, silent
 * unless {@code -Dceltium.gpuPassTimings=true}.
 *
 * <p>It lives outside the renderer because reporting is not rendering. The backend calls one method and keeps no format
 * knowledge of its own, which is the extraction rule the project set for that class: it may lose responsibilities, not
 * gain them.
 */
public final class GpuPassTimingReport {

    /** How often the line is printed, in frames. */
    private static final int EVERY_FRAME = 200;

    private static final boolean ENABLED = Boolean.getBoolean("celtium.gpuPassTimings");

    private GpuPassTimingReport() {
    }

    /**
     * Prints the collected pass timings when due.
     *
     * @param frameId  the frame the timings were collected in
     * @param timings  nanoseconds per pass, as the timestamp pool recorded them
     */
    public static void printIfDue(long frameId, Map<TerrainRenderPass, Long> timings) {
        if (!ENABLED || frameId % EVERY_FRAME != 0 || timings.isEmpty()) {
            return;
        }
        StringBuilder line = new StringBuilder(192);
        for (Map.Entry<TerrainRenderPass, Long> entry : timings.entrySet()) {
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(entry.getKey()).append('=').append(entry.getValue().longValue() / 1000L).append("us");
        }
        System.out.println("[Celtium] CELTIUM_PASS_GPU frame=" + frameId + " " + line);
    }
}
