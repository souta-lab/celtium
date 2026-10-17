#!/usr/bin/env python3
"""Exercise production dump request/rebuild/forward methods, including the old drop behavior."""
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
BACKEND = ROOT / "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"
SWAPCHAIN = ROOT / "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java"


def method(source, signature):
    start = source.index(signature)
    pos = source.index("{", start) + 1
    depth = 1
    while depth:
        depth += (source[pos] == "{") - (source[pos] == "}")
        pos += 1
    return source[start:pos]


def main():
    backend, swapchain = BACKEND.read_text(), SWAPCHAIN.read_text()
    assert backend.count("releaseSwapchainForRebuild();") == 3, "all three wrapper replacement routes must transfer"
    backend_methods = "\n".join(method(backend, signature) for signature in (
        "public boolean celtium$dumpPresentedImage(",
        "private void releaseSwapchainForRebuild(",
        "private void forwardPendingPresentedDumps(",
    ))
    swapchain_methods = "\n".join(method(swapchain, signature) for signature in (
        "public void requestPresentedImageDump(", "public java.util.List<java.nio.file.Path> takePendingPresentedImageDumps(",
    ))
    java = r'''
import java.util.*;
import java.nio.file.*;
class DumpHandoverCheck {
    static class VulkanSwapchain {
        final List<Path> pendingDumpPaths = new ArrayList<>();
        boolean deleted;
        void delete() { deleted = true; }
''' + swapchain_methods + r'''
    }
    VulkanSwapchain swapchain;
    final List<Path> pendingPresentedDumps = new ArrayList<>();
    Path presentedDumpPath;
    final Map<String,Long> seenDumpWriteSerialByName = new HashMap<>();
    boolean presentedImageDumpRequested;
''' + backend_methods + r'''
    static void check(boolean test,String label) { if (!test) throw new AssertionError(label); }
    public static void main(String[] args) {
        for (String reason : new String[]{"vsync", "extent", "out-of-date"}) {
            DumpHandoverCheck b = new DumpHandoverCheck();
            Path a = Paths.get("a.png"), c = Paths.get("c.png"), d = Paths.get("d.png");
            check(!b.celtium$dumpPresentedImage(null), "null request accepted");
            check(b.celtium$dumpPresentedImage(a) && b.celtium$dumpPresentedImage(c), "initial requests refused");
            b.forwardPendingPresentedDumps(); // creation failed: no request may be lost
            check(b.pendingPresentedDumps.equals(Arrays.asList(a,c)), "pre-creation requests overwritten");
            b.swapchain = new VulkanSwapchain();
            b.forwardPendingPresentedDumps();
            check(b.pendingPresentedDumps.isEmpty() && b.swapchain.pendingDumpPaths.equals(Arrays.asList(a,c)),
                  "initial forwarding changed order/ownership");
            b.swapchain.pendingDumpPaths.remove(0); // a was written: never replay it
            b.celtium$dumpPresentedImage(d);
            VulkanSwapchain old = b.swapchain;
            b.seenDumpWriteSerialByName.put(c.toString(), 1L); // new wrapper's first write can also be serial 1
            b.releaseSwapchainForRebuild();
            check(b.swapchain==null && old.deleted, "replacement did not release wrapper");
            check(b.pendingPresentedDumps.equals(Arrays.asList(c,d)), "pending requests lost during replacement");
            check(old.pendingDumpPaths.isEmpty(), "requests still owned by deleted wrapper");
            check(b.seenDumpWriteSerialByName.isEmpty(), "new wrapper serial namespace reused old attribution");
            b.forwardPendingPresentedDumps();
            check(b.pendingPresentedDumps.size()==2, "failed recreation lost pending requests");
            b.swapchain = new VulkanSwapchain();
            b.forwardPendingPresentedDumps(); b.forwardPendingPresentedDumps();
            check(b.swapchain.pendingDumpPaths.equals(Arrays.asList(c,d)) && b.pendingPresentedDumps.isEmpty(),
                  "restoration lost/duplicated requests or replayed a written capture");
            check(b.presentedDumpPath.equals(d), "last requested refresh path changed");
            System.out.println("Presented dump handover: " + reason + " PASS");
        }
    }
}
'''
    with tempfile.TemporaryDirectory(prefix="celtium-dump-handover-") as tmp:
        path = Path(tmp) / "DumpHandoverCheck.java"
        path.write_text(java)
        subprocess.run([shutil.which("javac"), "-d", tmp, str(path)], check=True)
        subprocess.run([shutil.which("java"), "-cp", tmp, "DumpHandoverCheck"], check=True)
        # Old replacement performed just delete + null. The same test must reject it.
        old = java.replace("this.pendingPresentedDumps.addAll(this.swapchain.takePendingPresentedImageDumps());", "")
        assert old != java
        path.write_text(old)
        subprocess.run([shutil.which("javac"), "-d", tmp, str(path)], check=True)
        result = subprocess.run([shutil.which("java"), "-cp", tmp, "DumpHandoverCheck"], capture_output=True, text=True)
        assert result.returncode != 0 and "pending requests lost during replacement" in result.stderr
    print("Regression rejects the previous delete-without-transfer behavior: PASS")


if __name__ == "__main__":
    main()
