#!/usr/bin/env python3
"""The present must drain queued immediate work before it asks whether a frame is waiting.

Owner: **splash-visibility** (startup loading-screen defect). What it exists to catch: under the GL-less window,
`Minecraft.updateDisplay` -> `celtium$presentFrame` blitted the frame image while immediate groups for the bound
target were still queued - the draws since the last bind boundary waited for the *next* window, so the presented
picture lagged one flush behind every frame. Measured at startup (leg f3-ab-f3on): the splash's composite reached
the frame image three seconds after the updateDisplay that should have presented it, and the first presented
loading frame was pure white. GL's `updateDisplay` presents everything drawn since the last swap; the present has
to do the same.

This is a source-order contract, because the flush itself is a Vulkan queue submission that no CPU-only test can
observe. It checks, inside `celtium$presentFrame`, that the pending drain runs after the swap point's ensure and
before the `lastFrameReachedQueue` gate - an ordering that cannot regress without this check failing. The ensure
at the swap point is the existence-only `ensureSwapchainForPresent()` since R-06 split the swap point in two:
the full `ensureSwapchain()` consumes a pending rebuild, and consuming it at the head would drop the swapchain
generation the frame being presented borrowed (the ordering the transition frame's
VUID-vkCmdDrawIndexed-renderPass-02684 and VUID-vkQueueSubmit-pCommandBuffers-00070 were measured on), so the
rebuild is consumed by `consumeSwapchainRebuild()` after the present attempt instead - and this check pins that
half of the split as well. It also checks that
a finish follows the drain before the gate: the drain records into the unified frame buffer, and a buffer nobody
finishes is never queued, so drained groups would miss the very present that drained them (defect B4, proven on
paper in OVERHAUL-B4-HARM-2026-11-26; the post-drain finish is what PRESENT_DRAIN_FINISH_GUARD gates, default on
since that proof). It also checks the
written-frame binding end to end (swapchain write serial -> backend presented-frame stamp -> fixture read),
because that binding is what makes the startup image nameable by the fixture instead of "the file appeared while
the counter read 16".

Usage: scripts/cpu-contract/present_drain_check.py [workspace-root]
Exit 0 when every clause holds, 1 with the first missing clause printed.
"""
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[2]
BACKEND = ROOT / "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"
PRESENT = ROOT / "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java"
SWAPCHAIN = ROOT / "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanSwapchain.java"
FIXTURE = ROOT / "validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java"


def method(source, signature):
    start = source.index(signature)
    begin = source.index("{", start)
    depth = 1
    end = begin + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def check(condition, label):
    if not condition:
        raise AssertionError(label)


def main():
    backend = BACKEND.read_text()
    present_source = PRESENT.read_text()
    swapchain = SWAPCHAIN.read_text()
    fixture = FIXTURE.read_text()

    # The present was split into presentFrame -> presentFrameImpl -> presentFrameBody so each stays under HotSpot's
    # HugeMethodLimit; the contract is about the order across that chain, so the three bodies are read in order.
    # The chain itself was extracted from the backend into PresentSwapchain (refactoring plan R10, item 704);
    # the backend's celtium$presentFrame() is now a one-line delegation to it, and the state the chain gates on
    # (lastFrameReachedQueue, presentFlushFailures, the immediate flush) stayed backend-owned, so inside the
    # extracted class those reads are spelled backend.<field>, not this.<field>.
    check("presentSwapchain.celtium$presentFrame()" in backend,
          "the backend's presentFrame must delegate to the extracted PresentSwapchain chain")
    present = (method(present_source, "public boolean celtium$presentFrame()")
               + method(present_source, "private boolean celtium$presentFrameImpl()")
               + method(present_source, "private boolean celtium$presentFrameBody()"))
    check("ensureSwapchainForPresent()" in present,
          "presentFrame no longer ensures a swapchain at the swap point")
    check("ensureSwapchain()" not in present,
          "the present chain must not call the full ensureSwapchain(): it consumes a pending rebuild, and "
          "consuming it at the swap point's head drops the generation the presented frame borrowed (R-06)")
    check("consumeSwapchainRebuild()" in present,
          "presentFrame no longer consumes a pending swapchain rebuild after the present attempt")
    check("flushPendingBeforeGlDraw()" in present,
          "presentFrame does not drain pending immediate work before presenting")
    check("PRESENT_FLUSH_PENDING" in present,
          "the drain must keep an opt-out switch for comparison legs")
    drain = present.index("flushPendingBeforeGlDraw()")
    swap = present.index("ensureSwapchainForPresent()")
    gate = present.index("if (!backend.lastFrameReachedQueue)")
    check(swap < drain < gate,
          "the drain must run after ensureSwapchainForPresent() and before the completed-frame gate "
          "- a drain placed after the gate still presents a frame behind")
    # R-06: the rebuild consumption waits until the present attempt has been submitted against the
    # generation this frame borrowed. The attempt is presentDirect for a borrowed image and
    # presentImage for the composite fallback; the consumption follows both. The tokens carry the
    # backend.swapchain receiver because the drain block's comment names presentDirect() in prose,
    # ahead of the gate.
    attempt = min(present.index("backend.swapchain.presentDirect("),
                  present.index("backend.swapchain.presentImage("))
    consume = present.index("consumeSwapchainRebuild()")
    check(gate < attempt < consume,
          "the swapchain rebuild must be consumed after the present attempt - consuming it before the "
          "attempt drops the borrowed generation under the live present (R-06)")
    # B4: the drain records into the unified frame buffer, which is only queued by a finish. Without a
    # finish between the drain and the gate, the drained groups stay in the open buffer and miss this
    # present - one frame late on the composite path, and on the direct-present path recorded against
    # an image that has already been presented and released.
    finish = present.index("celtium$finishFrameBuffer()", drain)
    check(drain < finish < gate,
          "a finish must follow the present-time drain before the completed-frame gate "
          "- drained groups left in the open frame buffer are never queued for this present (B4)")
    check("presentFlushFailures" in present, "a failed drain must be counted, not silent")
    check("presentDrainUnfinished" in present,
          "a drain that leaves the frame buffer open at the gate must be counted, not silent (B4 census)")

    # The written-frame binding: the swapchain counts each successful write per file name, the backend stamps
    # the presented-frame counter when a write lands, and the fixture reads it back. The binding is keyed on the
    # file name because several requests can be pending at once - a single "last write" misbinds an earlier file.
    write = method(swapchain, "private boolean writePresentedImage(")
    check("dumpWriteSerial++" in write and "dumpWriteSerialByPath" in write,
          "a successful presented-image write must stamp the serial for that path")
    check("dumpWriteSerialByPath()" in swapchain,
          "the swapchain must expose the per-path write serials")
    check("seenDumpWriteSerialByName" in backend and "presentedDumpWrittenFrames" in backend,
          "the backend must stamp the presented frame when a write lands")
    check("celtium$presentedDumpWrittenFrameFor(" in backend,
          "the backend must expose the written-frame binding to the fixture, keyed by file name")
    complete = method(fixture, "private void celtium$completePendingDumps()")
    check("celtium$presentedDumpWrittenFrame(evidence)" in complete,
          "the fixture must name the frame from the write when the record exists")
    check("presented-frame counter read" in complete,
          "the observed-counter fallback must stay for a backend that predates the record")

    print("present drain + written-frame binding: contract PASS")


if __name__ == "__main__":
    main()
