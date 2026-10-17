#!/usr/bin/env python3
"""Execute production pair-framebuffer replacement against a pending-work mock (no GPU).

The extracted backend methods, not a duplicate implementation, must settle before
freeing a nonempty pair cache. A failed wait must preserve the old cache and stop
replacement. The native-device resize legs remain the separate integration proof.
"""
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"


def method(source, signature):
    start = source.index(signature)
    begin = source.index("{", start)
    depth, end = 1, begin + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def main():
    source = (Path(sys.argv[1]) if len(sys.argv) > 1 else SOURCE).read_text()
    signatures = [
        "private long ensureOffTargetFramebuffer(",
        "private long ensureChainPairFramebuffer(",
        "private void destroyOffTargetFramebuffer(",
        "private void destroyChainPairFramebuffer(",
    ]
    if "private void destroyPairFramebuffer(" in source:
        signatures.append("private void destroyPairFramebuffer(")
    methods = "\n".join(method(source, signature) for signature in signatures)
    java = r'''
import java.util.*;
class FramebufferReleaseCheck {
    static final class RenderTargetRegistry {
        enum ImageSlot { DEFAULT_PAIR, CHAIN_PAIR; String label() { return name(); } }
        long[] fb = new long[2], pass = new long[2];
        long pairFramebuffer(ImageSlot slot) { return fb[slot.ordinal()]; }
        long pairFramebufferRenderPass(ImageSlot slot) { return pass[slot.ordinal()]; }
        void clearPairFramebuffer(ImageSlot slot) { fb[slot.ordinal()] = pass[slot.ordinal()] = 0; }
        void notePairFramebuffer(ImageSlot slot, long handle, long renderPass) {
            fb[slot.ordinal()] = handle; pass[slot.ordinal()] = renderPass;
        }
    }
    static final class Context { Object device() { return this; } }
    static final class GllessFrameTarget {
        boolean offTargetMatches(int w, int h) { return true; }
        boolean chainPairMatches(int w, int h) { return true; }
        long offTargetColorView() { return 10; }
        long offTargetDepthView() { return 11; }
        long chainPairColorView() { return 20; }
        long chainPairDepthView() { return 21; }
    }
    final Context context = new Context();
    final RenderTargetRegistry targetRegistry = new RenderTargetRegistry();
    final GllessFrameTarget target = new GllessFrameTarget();
    final List<String> events = new ArrayList<>();
    final Set<Long> destroyed = new HashSet<>();
    int targetWidth = 1280, targetHeight = 720, waits, creates, framebufferSettleFailures;
    boolean pending = true, allowSettle = true;
    GllessFrameTarget offTargetTarget() { return target; }
    boolean settleGpu(String reason) {
        waits++; events.add("wait");
        if (!allowSettle) return false;
        pending = false; return true;
    }
    void vkDestroyFramebuffer(Object device, long handle, Object allocator) {
        if (pending) throw new AssertionError("framebuffer freed before GPU completion");
        if (handle == 0 || !destroyed.add(handle)) throw new AssertionError("invalid/double free");
        events.add("destroy");
    }
    long ensurePairFramebuffer(long pass, long color, long depth, String label) {
        creates++; events.add("create"); return 100 + creates;
    }
''' + methods + r'''
    long ensure(RenderTargetRegistry.ImageSlot slot, long pass) {
        return slot == RenderTargetRegistry.ImageSlot.DEFAULT_PAIR
            ? ensureOffTargetFramebuffer(pass) : ensureChainPairFramebuffer(pass);
    }
    void release(RenderTargetRegistry.ImageSlot slot) {
        if (slot == RenderTargetRegistry.ImageSlot.DEFAULT_PAIR) destroyOffTargetFramebuffer();
        else destroyChainPairFramebuffer();
    }
    static void check(boolean test, String label) { if (!test) throw new AssertionError(label); }
    public static void main(String[] args) {
        int cases = 0;
        for (RenderTargetRegistry.ImageSlot slot : RenderTargetRegistry.ImageSlot.values()) {
            FramebufferReleaseCheck b = new FramebufferReleaseCheck();
            // Empty cache: creation must not add an idle wait to the per-pass path.
            long first = b.ensure(slot, 7);
            check(b.waits == 0 && b.creates == 1, "first use waited or failed to create");
            check(b.ensure(slot, 7) == first && b.waits == 0 && b.creates == 1,
                  "cache hit mutated or waited");
            b.events.clear();
            long second = b.ensure(slot, 8);
            check(second != first && b.destroyed.contains(first), "replacement did not free old cache");
            check(b.events.equals(Arrays.asList("wait", "destroy", "create")),
                  "replacement order differs: " + b.events);
            cases += 3;
            // GPU refuses to settle: preserve handle and render-pass cache; do not create over it.
            b.pending = true; b.allowSettle = false; b.events.clear();
            try { b.ensure(slot, 9); throw new AssertionError("failed wait allowed replacement"); }
            catch (IllegalStateException expected) { }
            check(b.targetRegistry.pairFramebuffer(slot) == second
                  && b.targetRegistry.pairFramebufferRenderPass(slot) == 8,
                  "failed wait erased the old cache");
            check(!b.destroyed.contains(second) && b.creates == 2 && b.framebufferSettleFailures == 1,
                  "failed wait destroyed/created or was not counted");
            check(b.events.equals(Arrays.asList("wait")), "failed wait changed native resources");
            cases++;
            // Retry and repeated release: free exactly once, and do not wait on an empty cache.
            b.allowSettle = true; b.release(slot);
            int waits = b.waits; b.release(slot);
            check(b.destroyed.size() == 2 && b.waits == waits
                  && b.targetRegistry.pairFramebuffer(slot) == 0, "release is not idempotent");
            cases++;
        }
        System.out.println("Pair framebuffer release: " + cases + " pending/failed/empty/cache/retry cases PASS");
    }
}
'''
    with tempfile.TemporaryDirectory(prefix="celtium-framebuffer-release-") as tmp:
        path = Path(tmp) / "FramebufferReleaseCheck.java"
        path.write_text(java)
        subprocess.run([shutil.which("javac"), "-d", tmp, str(path)], check=True)
        subprocess.run([shutil.which("java"), "-cp", tmp, "FramebufferReleaseCheck"], check=True)
    # A failed wait sets gpuStalled; the off-target catch must not draw elsewhere.
    recording = method(source, "private void recordImmediateOnlyPass(")
    catch = recording.index("} catch (RuntimeException | LinkageError error)")
    fallback = recording.index("framebuffer = ensureFramebuffer(renderPass)", catch)
    guard = recording[catch:fallback]
    assert "if (this.gpuStalled)" in guard and "throw error;" in guard, \
        "stalled pair replacement still falls through to a frame-image pass"
    print("Stalled pair replacement refuses the recording rather than changing its target: PASS")


if __name__ == "__main__":
    main()
