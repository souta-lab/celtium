#!/usr/bin/env python3
"""BH-18's contract check: drives the real selection function with synthetic surface inputs.

The audit's "修正後の確認" asks for min=max=2, max=0, no OPAQUE, no TRANSFER_DST and a single UNDEFINED format to be
checked. A real surface never exercises those branches (measured: AMD RX 6400 and Intel UHD 630 both pin
currentExtent and report maxImageCount == 0), so the check is synthetic by necessity.

It is not a copy of the implementation: the Java harness below calls
org.soutalab.celtium.vulkan.SwapchainCreateArguments.select(...), which is the same function
VulkanSwapchain.createSwapchain calls to fill VkSwapchainCreateInfoKHR. The expectations are asserted here, and every
measured field is printed, so a failure says which condition moved.

Usage: python3 scripts/cpu-contract/swapchain_create_args_check.py
Exit: 0 when every condition holds, 1 otherwise.
"""
import os
import subprocess
import sys
import tempfile
import textwrap

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CLASSES = os.path.join(ROOT, 'celtium-vulkan', 'build', 'classes', 'java', 'main')

JAVA = r'''
import org.soutalab.celtium.vulkan.SwapchainCreateArguments;
import org.soutalab.celtium.vulkan.SwapchainCreateArguments.Result;
import org.soutalab.celtium.vulkan.SwapchainCreateArguments.Surface;

public final class SwapchainCreateArgsProbe {
    private static int failures;

    private static Surface surface(int minImages, int maxImages, int usage, int alpha,
            long currentW, long currentH, int minW, int minH, int maxW, int maxH, int... formats) {
        Surface s = new Surface();
        s.minImageCount = minImages;
        s.maxImageCount = maxImages;
        s.supportedUsage = usage;
        s.supportedCompositeAlpha = alpha;
        s.currentExtentWidth = currentW;
        s.currentExtentHeight = currentH;
        s.minExtentWidth = minW;
        s.minExtentHeight = minH;
        s.maxExtentWidth = maxW;
        s.maxExtentHeight = maxH;
        s.formats = formats;
        return s;
    }

    private static void check(String scenario, String what, boolean ok, Object measured) {
        System.out.printf("  %-6s %-46s measured=%s%n", ok ? "PASS" : "FAIL", what, String.valueOf(measured));
        if (!ok) {
            failures++;
        }
    }

    private static final int USAGE_SRC = 0x1;
    private static final int USAGE_DST = 0x2;
    private static final int USAGE_COLOR = 0x10;
    private static final int ALPHA_OPAQUE = 0x1;
    private static final int ALPHA_PRE = 0x2;
    private static final int ALPHA_POST = 0x4;
    private static final int ALPHA_INHERIT = 0x8;
    private static final int BGRA = 44;
    private static final int RGBA = 37;
    private static final int UNDEFINED = 0;
    private static final long UNSPECIFIED = 0xFFFFFFFFL;

    public static void main(String[] args) {
        // 1. minImageCount == maxImageCount == 2: the request is 2, not 3.
        System.out.println("scenario: minImageCount == maxImageCount == 2");
        Result r = SwapchainCreateArguments.select(
                surface(2, 2, USAGE_COLOR | USAGE_DST | USAGE_SRC, ALPHA_OPAQUE, 800, 600, 1, 1, 4096, 4096,
                        BGRA, 0, RGBA, 0), 800, 600, 3);
        check("min=max=2", "imageCount is 2 (not the desired 3)", r.imageCount == 2, r.imageCount);
        check("min=max=2", "clamp is named", r.imageCountClamped, r.imageCountClamped);
        System.out.println("  " + r.describe());

        // 2. maxImageCount == 0 means unlimited: the desired 3 is kept, nothing is clamped.
        System.out.println("scenario: maxImageCount == 0 (unlimited)");
        r = SwapchainCreateArguments.select(
                surface(2, 0, USAGE_COLOR | USAGE_DST | USAGE_SRC, ALPHA_OPAQUE, 800, 600, 1, 1, 4096, 4096,
                        BGRA, 0), 800, 600, 3);
        check("max=0", "imageCount is the desired 3", r.imageCount == 3, r.imageCount);
        check("max=0", "unlimited is named", r.imagesUnlimited, r.imagesUnlimited);
        check("max=0", "no clamp is claimed", !r.imageCountClamped, r.imageCountClamped);
        System.out.println("  " + r.describe());

        // 3. OPAQUE not offered: the next offered mode is chosen, in the documented order.
        System.out.println("scenario: OPAQUE not offered");
        r = SwapchainCreateArguments.select(
                surface(2, 0, USAGE_COLOR | USAGE_DST | USAGE_SRC, ALPHA_PRE | ALPHA_POST | ALPHA_INHERIT,
                        800, 600, 1, 1, 4096, 4096, BGRA, 0), 800, 600, 3);
        check("no OPAQUE", "PRE_MULTIPLIED is chosen first", r.compositeAlpha == ALPHA_PRE, r.compositeAlpha);
        r = SwapchainCreateArguments.select(
                surface(2, 0, USAGE_COLOR | USAGE_DST | USAGE_SRC, ALPHA_POST | ALPHA_INHERIT,
                        800, 600, 1, 1, 4096, 4096, BGRA, 0), 800, 600, 3);
        check("no OPAQUE/PRE", "POST_MULTIPLIED is next", r.compositeAlpha == ALPHA_POST, r.compositeAlpha);
        r = SwapchainCreateArguments.select(
                surface(2, 0, USAGE_COLOR | USAGE_DST | USAGE_SRC, ALPHA_INHERIT,
                        800, 600, 1, 1, 4096, 4096, BGRA, 0), 800, 600, 3);
        check("only INHERIT", "INHERIT is last", r.compositeAlpha == ALPHA_INHERIT, r.compositeAlpha);
        r = SwapchainCreateArguments.select(
                surface(2, 0, USAGE_COLOR | USAGE_DST | USAGE_SRC, 0, 800, 600, 1, 1, 4096, 4096, BGRA, 0),
                800, 600, 3);
        check("alpha == 0", "refused by name", r.refusal != null, r.refusal);

        // 4. TRANSFER_DST not offered: dropped, and named.
        System.out.println("scenario: no TRANSFER_DST");
        r = SwapchainCreateArguments.select(
                surface(2, 0, USAGE_COLOR | USAGE_SRC, ALPHA_OPAQUE, 800, 600, 1, 1, 4096, 4096, BGRA, 0),
                800, 600, 3);
        check("no TRANSFER_DST", "usage keeps COLOR_ATTACHMENT and TRANSFER_SRC",
                r.usage == (USAGE_COLOR | USAGE_SRC), "0x" + Integer.toHexString(r.usage));
        check("no TRANSFER_DST", "the dropped bit is named", "TRANSFER_DST".equals(r.droppedUsage), r.droppedUsage);

        // 5. A single VK_FORMAT_UNDEFINED: not adopted as a format.
        System.out.println("scenario: single VK_FORMAT_UNDEFINED");
        r = SwapchainCreateArguments.select(
                surface(2, 0, USAGE_COLOR | USAGE_DST | USAGE_SRC, ALPHA_OPAQUE, 800, 600, 1, 1, 4096, 4096,
                        UNDEFINED, 0), 800, 600, 3);
        check("only UNDEFINED", "UNDEFINED is not the chosen format", r.format != UNDEFINED, r.format);
        check("only UNDEFINED", "the default B8G8R8A8_UNORM is chosen", r.format == BGRA, r.format);
        check("only UNDEFINED", "the decision is named", r.formatNote.contains("UNDEFINED"), r.formatNote);
        check("only UNDEFINED", "colour space comes from the surface entry", r.colorSpace == 0, r.colorSpace);

        // 5b. UNDEFINED beside a real format: the real one wins and the marker is named as skipped.
        System.out.println("scenario: UNDEFINED beside a real format");
        r = SwapchainCreateArguments.select(
                surface(2, 0, USAGE_COLOR | USAGE_DST | USAGE_SRC, ALPHA_OPAQUE, 800, 600, 1, 1, 4096, 4096,
                        UNDEFINED, 0, RGBA, 1), 800, 600, 3);
        check("UNDEFINED+real", "the real format is chosen", r.format == RGBA, r.format);
        check("UNDEFINED+real", "the skipped marker is named", r.formatNote.contains("UNDEFINED"), r.formatNote);

        // 6. currentExtent unspecified: the request is clamped into min..max, and never to zero.
        System.out.println("scenario: currentExtent 0xFFFFFFFF");
        r = SwapchainCreateArguments.select(
                surface(2, 0, USAGE_COLOR | USAGE_DST | USAGE_SRC, ALPHA_OPAQUE, UNSPECIFIED, UNSPECIFIED,
                        1280, 720, 1280, 720, BGRA, 0), 640, 360, 3);
        check("unspecified extent", "clamped up to minImageExtent", r.width == 1280 && r.height == 720,
                r.width + "x" + r.height);
        check("unspecified extent", "the branch is named", r.extentNote.contains("clamped"), r.extentNote);
        r = SwapchainCreateArguments.select(
                surface(2, 0, USAGE_COLOR | USAGE_DST | USAGE_SRC, ALPHA_OPAQUE, UNSPECIFIED, UNSPECIFIED,
                        1, 1, 4096, 4096, BGRA, 0), 0, 0, 3);
        check("zero-sized request", "never zero: the minimum of 1 is used", r.width >= 1 && r.height >= 1,
                r.width + "x" + r.height);
        r = SwapchainCreateArguments.select(
                surface(2, 0, USAGE_COLOR | USAGE_DST | USAGE_SRC, ALPHA_OPAQUE, 1024, 768, 1, 1, 4096, 4096,
                        BGRA, 0), 640, 360, 3);
        check("pinned extent", "the surface's extent is used", r.width == 1024 && r.height == 768,
                r.width + "x" + r.height);

        // 6b. minImageCount == 0 is a broken answer, treated as 1 and named.
        System.out.println("scenario: minImageCount == 0");
        r = SwapchainCreateArguments.select(
                surface(0, 0, USAGE_COLOR | USAGE_DST | USAGE_SRC, ALPHA_OPAQUE, 800, 600, 1, 1, 4096, 4096,
                        BGRA, 0), 800, 600, 3);
        check("min=0", "at least one image is requested", r.imageCount >= 1, r.imageCount);
        check("min=0", "the broken answer is named", r.imageCountNote.contains("minImageCount=0"),
                r.imageCountNote);

        // 7. No colour attachment: refused by name rather than asked for.
        System.out.println("scenario: no COLOR_ATTACHMENT");
        r = SwapchainCreateArguments.select(
                surface(2, 0, USAGE_SRC | USAGE_DST, ALPHA_OPAQUE, 800, 600, 1, 1, 4096, 4096, BGRA, 0),
                800, 600, 3);
        check("no COLOR_ATTACHMENT", "refused by name", r.refusal != null, r.refusal);

        System.out.println();
        System.out.println(failures == 0 ? "BH-18 contract: all conditions hold"
                : "BH-18 contract: " + failures + " condition(s) failed");
        System.exit(failures == 0 ? 0 : 1);
    }
}
'''


def main() -> int:
    if not os.path.isdir(CLASSES):
        print('missing compiled classes at %s - run scripts/build.sh :celtium-vulkan:compileJava first' % CLASSES)
        return 1
    with tempfile.TemporaryDirectory() as tmp:
        source = os.path.join(tmp, 'SwapchainCreateArgsProbe.java')
        with open(source, 'w') as handle:
            handle.write(JAVA)
        compile_result = subprocess.run(
            ['javac', '-nowarn', '-cp', CLASSES, '-d', tmp, source],
            capture_output=True, text=True)
        if compile_result.returncode != 0:
            print(compile_result.stdout)
            print(compile_result.stderr)
            return 1
        run = subprocess.run(['java', '-cp', tmp + os.pathsep + CLASSES, 'SwapchainCreateArgsProbe'],
                             capture_output=True, text=True)
        print(run.stdout, end='')
        if run.returncode != 0:
            print(run.stderr, end='')
        return run.returncode


if __name__ == '__main__':
    sys.exit(main())
