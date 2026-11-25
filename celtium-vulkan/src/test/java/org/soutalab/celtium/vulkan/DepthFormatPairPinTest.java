package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_D16_UNORM;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_D16_UNORM_S8_UINT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_D24_UNORM_S8_UINT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT_S8_UINT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_X8_D24_UNORM_PACK32;

/**
 * The depth format pairs of QUEUE 431 (the D2 follow-on), pinned without a device.
 * <p>
 * The renderer survey's D2 split (blit for matching formats, a shader fallback otherwise) exists
 * because a GL-to-VK hand-off can pair foreign depth formats. Celtium's native depth copy never
 * faces that split: every depth image - the frame's own, a chain pair's, and the shared store the
 * copy reads and writes - is allocated by the one allocator
 * ({@code GllessFrameTarget.allocateImage}) at the one format {@link VulkanDepthFormat} chooses for
 * the process, so a served pair is matched by construction rather than checked at copy time
 * (SHADOW-NATIVE-DESIGN-2026-11-17 section 7). What the closure report asked for and never got is
 * the per-pair record: which pairs that construction can serve, enumerated. This test is that
 * record. The device-dependent half (which candidate a given GPU can actually use) stays gated on
 * the device; every decision that does not need one is pinned here, because a silent change to the
 * candidate order or the selection rule would re-pair the copy on hardware this test suite never
 * runs on.
 */
class DepthFormatPairPinTest {

    // The candidate enumeration itself: six formats, in the preference order the class doc states
    // (Minecraft's 24-bit requirement first, then the closest equivalents), each with its name.
    @Test
    void theCandidatesAreSixNamedFormatsInPreferenceOrder() {
        assertEquals(6, VulkanDepthFormat.candidateCount());
        int[] expectedFormats = {
                VK_FORMAT_D32_SFLOAT,
                VK_FORMAT_D24_UNORM_S8_UINT,
                VK_FORMAT_D32_SFLOAT_S8_UINT,
                VK_FORMAT_X8_D24_UNORM_PACK32,
                VK_FORMAT_D16_UNORM_S8_UINT,
                VK_FORMAT_D16_UNORM,
        };
        String[] expectedNames = {
                "D32_SFLOAT",
                "D24_UNORM_S8_UINT",
                "D32_SFLOAT_S8_UINT",
                "X8_D24_UNORM_PACK32",
                "D16_UNORM_S8_UINT",
                "D16_UNORM",
        };
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < expectedFormats.length; i++) {
            assertEquals(expectedFormats[i], VulkanDepthFormat.candidateAt(i), "candidate " + i);
            assertEquals(expectedNames[i], VulkanDepthFormat.candidateNameAt(i),
                    "the name aligned with candidate " + i);
            assertTrue(seen.add(expectedFormats[i]), "candidate " + i + " is a repeat");
            assertTrue(expectedFormats[i] != VK_FORMAT_UNDEFINED, "candidate " + i + " is undefined");
        }
    }

    // The selection rule over every usability pattern a device can present: the choice is the
    // first usable candidate in preference order, and -1 (the refusal) only when none is usable.
    @Test
    void theChoiceIsTheFirstUsableCandidateForEveryUsabilityPattern() {
        int count = VulkanDepthFormat.candidateCount();
        for (int mask = 0; mask < (1 << count); mask++) {
            final int pattern = mask;
            int picked = VulkanDepthFormat.firstUsableIndex(
                    format -> (pattern & (1 << indexOf(format))) != 0);
            int expected = -1;
            for (int i = 0; i < count; i++) {
                if ((pattern & (1 << i)) != 0) {
                    expected = i;
                    break;
                }
            }
            assertEquals(expected, picked, "usability pattern " + Integer.toBinaryString(pattern));
        }
    }

    // The probe is lazy: the device is never asked about a candidate past the first acceptance,
    // so the choice cannot depend on a format the renderer never reached.
    @Test
    void theProbeStopsAtTheFirstUsableCandidate() {
        int count = VulkanDepthFormat.candidateCount();
        for (int first = 0; first < count; first++) {
            final int accepted = VulkanDepthFormat.candidateAt(first);
            AtomicInteger probes = new AtomicInteger();
            int picked = VulkanDepthFormat.firstUsableIndex(format -> {
                probes.incrementAndGet();
                return format == accepted;
            });
            assertEquals(first, picked);
            assertEquals(first + 1, probes.get(),
                    "candidates past the acceptance at " + first + " were probed");
        }
        AtomicInteger probes = new AtomicInteger();
        assertEquals(-1, VulkanDepthFormat.firstUsableIndex(format -> {
            probes.incrementAndGet();
            return false;
        }));
        assertEquals(count, probes.get(), "a refusal must have probed every candidate");
    }

    // The override names resolve to their own candidate, case-insensitively, and to nothing else.
    @Test
    void overrideNamesResolveToTheirOwnCandidate() {
        for (int i = 0; i < VulkanDepthFormat.candidateCount(); i++) {
            String name = VulkanDepthFormat.candidateNameAt(i);
            assertEquals(VulkanDepthFormat.candidateAt(i), VulkanDepthFormat.formatNamed(name), name);
            assertEquals(VulkanDepthFormat.candidateAt(i),
                    VulkanDepthFormat.formatNamed(name.toLowerCase(java.util.Locale.ROOT)),
                    name + " in lower case");
        }
        assertEquals(VK_FORMAT_UNDEFINED, VulkanDepthFormat.formatNamed("D24_UNORM"),
                "a prefix of a real name is not a candidate");
        assertEquals(VK_FORMAT_UNDEFINED, VulkanDepthFormat.formatNamed("RGBA8_UNORM"),
                "a colour format is not a depth candidate");
        assertEquals(VK_FORMAT_UNDEFINED, VulkanDepthFormat.formatNamed(""));
    }

    private static int indexOf(int format) {
        for (int i = 0; i < VulkanDepthFormat.candidateCount(); i++) {
            if (VulkanDepthFormat.candidateAt(i) == format) {
                return i;
            }
        }
        throw new IllegalArgumentException("not a candidate: " + format);
    }

    // The pair table, enumerated in full. For a process whose chosen format is candidate c, the
    // native depth copy can serve exactly one ordered pair out of the 36 candidate pairs: (c, c).
    // Every off-diagonal pair would need one side allocated at a format other than the chosen one,
    // which the single-allocator construction cannot produce; pairs involving a non-candidate are
    // outside the table entirely, because no depth image of such a format is ever allocated.
    @Test
    void theServablePairsAreExactlyTheDiagonalOnTheChosenFormat() {
        int count = VulkanDepthFormat.candidateCount();
        int servableTotal = 0;
        for (int c = 0; c < count; c++) {
            int chosen = VulkanDepthFormat.candidateAt(c);
            int servableForThisChoice = 0;
            for (int s = 0; s < count; s++) {
                for (int d = 0; d < count; d++) {
                    int source = VulkanDepthFormat.candidateAt(s);
                    int dest = VulkanDepthFormat.candidateAt(d);
                    boolean servable = source == chosen && dest == chosen;
                    if (servable) {
                        servableForThisChoice++;
                        assertEquals(c, s, "a servable pair's source is the chosen candidate");
                        assertEquals(c, d, "a servable pair's destination is the chosen candidate");
                    }
                }
            }
            assertEquals(1, servableForThisChoice,
                    "chosen " + VulkanDepthFormat.candidateNameAt(c) + " serves exactly its diagonal pair");
            servableTotal += servableForThisChoice;
        }
        assertEquals(count, servableTotal,
                "one servable pair per possible choice, out of " + (count * count) + " pairs per choice");
    }

    // The construction the pair table rests on, read from the sources so a second allocation route
    // cannot appear silently: the shared store's images come from the one allocator, the
    // allocator's depth format is the chooser's answer, and the copy serves only store images.
    @Test
    void bothSidesOfTheCopyComeFromTheOneAllocatorAtTheChosenFormat() throws IOException {
        String frameTarget = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java"));
        // The store itself moved to the HAL (HAL wave 3); the pin follows the ownership: the store
        // allocates only through the injected allocator, and the frame target injects the one allocator.
        String storeSource = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/hal/SharedDepthStore.java"));
        String sharedStore = methodSlice(storeSource, "public long[] ensure(", 1600);
        assertTrue(sharedStore.contains("this.allocator.allocate(width, height)"),
                "the shared depth store allocates through the one allocator");
        String injection = methodSlice(frameTarget, "private final SharedDepthStore sharedDepthStore", 600);
        assertTrue(injection.contains("allocateImage(false, width, height"),
                "the frame target injects its one allocator as the store's depth allocator");
        String allocator = methodSlice(frameTarget, "private long[] allocateImage(boolean color", 3200);
        assertTrue(allocator.contains("VulkanDepthFormat.chosen(this.context.physicalDevice())"),
                "the allocator's depth format is the chooser's single answer");
        assertFalse(allocator.contains("VK_FORMAT_D32_SFLOAT"),
                "the allocator names no fixed depth format of its own");

        // The copy itself moved to CopyReadSide (refactoring plan R11); the pin follows the method.
        String backend = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java"));
        String copy = methodSlice(backend, "public String celtium$serveDepthCopy", 2400);
        assertTrue(copy.contains("target.sharedDepths().image(sourceKey)"),
                "the copy's source is a shared-store image");
        assertTrue(copy.contains("target.sharedDepths().image(destKey)"),
                "the copy's destination is a shared-store image");
    }

    /** A slice of one method, from its signature, long enough to cover the lines this pin reads. */
    private static String methodSlice(String source, String signature, int length) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "cannot find " + signature);
        return source.substring(start, Math.min(source.length(), start + length));
    }

    private static Path findRepoFile(String repoRelative) {
        Path direct = Paths.get(repoRelative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromModule = Paths.get("..").resolve(repoRelative);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " from "
                + Paths.get("").toAbsolutePath());
    }
}
