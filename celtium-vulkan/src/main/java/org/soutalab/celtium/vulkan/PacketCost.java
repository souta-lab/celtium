package org.soutalab.celtium.vulkan;

/**
 * Where one immediate draw packet's time goes, split at the only boundary every family shares.
 * <p>
 * The frame's cost is packets times cost-per-packet (183 packets and a 0.62 ms difference on the measured leg), and
 * "packet" was one number: a pipeline bind, a 208-byte push, a vertex/index bind pair and the draw call. Telling
 * "our own preparation is expensive" from "the driver's recording calls are expensive" needs them apart, and the one
 * place every immediate family passes through is {@link VulkanDynamicMesh#bindAndDraw(VkCommandBuffer, long[])} - so
 * that is where the bind/draw half is measured, and the push half is the caller's total minus it.
 * <p>
 * Static and gated on the batch-timing switch because the draw path is on the hot path of every packet and cannot be
 * given a per-instance owner without threading one through five pipeline families. Off, every method is one boolean
 * read and a branch.
 */
final class PacketCost {

    private static final boolean ENABLED = VulkanTerrainGpuBackend.BATCH_TIMINGS;

    private static long bindNanos;
    private static long drawNanos;
    private static long packets;
    /** Packets whose buffers were already bound, i.e. the bind pair was skipped entirely. */
    private static long bindSkips;

    private PacketCost() {
    }

    static boolean enabled() {
        return ENABLED;
    }

    static long start() {
        return ENABLED ? System.nanoTime() : 0L;
    }

    /** The pipeline-bind half: how often the packet's pipeline differs from what is already bound. */
    private static long pipelineBindNanos;
    private static long pipelineBinds;

    static long beginPipelineBind() {
        return ENABLED ? System.nanoTime() : 0L;
    }

    static void notePipelineBind(long nanos) {
        if (ENABLED) {
            pipelineBindNanos += nanos;
            pipelineBinds++;
        }
    }

    static long pipelineBindMillis() {
        return pipelineBindNanos / 1000000L;
    }

    /** {@return microseconds per pipeline bind, or -1 before the first one}. */
    static long pipelineBindMicros() {
        return pipelineBinds == 0L ? -1L : pipelineBindNanos / pipelineBinds / 1000L;
    }

    /** {@return how many packets had to bind a pipeline, i.e. how often consecutive packets changed it}. */
    static long pipelineBinds() {
        return pipelineBinds;
    }

    /** The push-constant half: the cache comparison plus the driver call that follows it. */
    private static long pushNanos;

    static long beginPush() {
        return ENABLED ? System.nanoTime() : 0L;
    }

    static void notePush(long nanos) {
        if (ENABLED) {
            pushNanos += nanos;
        }
    }

    /** Endpoint for the push bracket; returns the elapsed nanos for {@link #notePush}. */
    static long endPush(long start) {
        return ENABLED ? System.nanoTime() - start : 0L;
    }

    static void noteBind(long nanos) {
        bindNanos += nanos;
    }

    static void noteDraw(long nanos) {
        drawNanos += nanos;
    }

    static void notePacket(boolean buffersAlreadyBound) {
        packets++;
        if (buffersAlreadyBound) {
            bindSkips++;
        }
    }

    static long pushMillis() {
        return pushNanos / 1000000L;
    }

    /** {@return microseconds per packet in the push half, or -1 before any packet}. */
    static long pushMicros() {
        return packets == 0L ? -1L : pushNanos / packets / 1000L;
    }

    static long bindMillis() {
        return bindNanos / 1000000L;
    }

    static long drawMillis() {
        return drawNanos / 1000000L;
    }

    static long packets() {
        return packets;
    }

    /** {@return microseconds per packet in the bind half, or -1 before any packet}. */
    static long bindMicros() {
        return packets == 0L ? -1L : bindNanos / packets / 1000L;
    }

    /** {@return microseconds per packet in the draw-call half, or -1 before any packet}. */
    static long drawMicros() {
        return packets == 0L ? -1L : drawNanos / packets / 1000L;
    }

    /** {@return how many packets needed no buffer bind at all}. */
    static long bindSkips() {
        return bindSkips;
    }
}
