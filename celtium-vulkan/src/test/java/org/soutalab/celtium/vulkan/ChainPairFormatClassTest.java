package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetFormatClass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure rules of QUEUE 404 (format-class-parameterised chain pairs), exercised without a device: the
 * request resolution, the widest-class comparison, the pair rebuild rule, the render-pass variant key and
 * the pipeline-key fold. The device-dependent halves (pass creation, the format feature check) are gated
 * on PC; what is pinned here is every decision that does not need a device, because a wrong decision here
 * is silent in game - the pair would come up in the wrong format and only the pixels would say so.
 */
class ChainPairFormatClassTest {

    // T5 / rule R1: the pin and the default resolution.
    @Test
    void aPinnedTargetNeverLeavesTheDefaultClass() {
        assertEquals(TargetFormatClass.RGBA8_UNORM, VulkanColorFormat.pinnedOrRequested(true, 0x881A),
                "the main target asked for RGBA16F: pinned, so the request is not honoured");
        assertEquals(TargetFormatClass.RGBA8_UNORM, VulkanColorFormat.pinnedOrRequested(true, 0x8C3A));
        assertEquals(TargetFormatClass.RGBA16_FLOAT, VulkanColorFormat.pinnedOrRequested(false, 0x881A),
                "an off-screen pair is not pinned: its request is honoured");
    }

    // T4 / rule R2: unrecorded, unmapped and non-colour requests resolve to the default class.
    @Test
    void anUnrecordedOrUnmappedRequestResolvesToTheDefault() {
        assertEquals(TargetFormatClass.RGBA8_UNORM, VulkanColorFormat.forRequest(0),
                "0 is what the stub answers for a texture it never recorded");
        assertEquals(TargetFormatClass.RGBA8_UNORM, VulkanColorFormat.forRequest(0x8059),
                "GL_RGBA8_SNORM is a real format the class table does not map");
        assertEquals(TargetFormatClass.RGBA8_UNORM, VulkanColorFormat.forRequest(0x81A6),
                "a depth internal format is not a colour class");
        assertEquals(TargetFormatClass.RGBA8_UNORM, VulkanColorFormat.forRequest(0x8058));
        assertEquals(TargetFormatClass.RGBA8_UNORM, VulkanColorFormat.forRequest(0x1908));
        assertEquals(TargetFormatClass.RGBA16_FLOAT, VulkanColorFormat.forRequest(0x881A));
        assertEquals(TargetFormatClass.RGBA32_FLOAT, VulkanColorFormat.forRequest(0x8814));
        assertEquals(TargetFormatClass.R11G11B10_FLOAT, VulkanColorFormat.forRequest(0x8C3A));
    }

    // T4 / rule R4 with the C2 correction: widest is by bytes per pixel, never by enum or Vk order.
    @Test
    void theWidestClassIsMeasuredInBytesPerPixel() {
        assertEquals(TargetFormatClass.RGBA32_FLOAT,
                VulkanColorFormat.widest(TargetFormatClass.RGBA32_FLOAT, TargetFormatClass.R11G11B10_FLOAT),
                "R11G11B10 outranks RGBA32F in both enum and Vk order but is 4 bytes against 16");
        assertEquals(TargetFormatClass.RGBA32_FLOAT,
                VulkanColorFormat.widest(TargetFormatClass.R11G11B10_FLOAT, TargetFormatClass.RGBA32_FLOAT));
        assertEquals(TargetFormatClass.RGBA16_FLOAT,
                VulkanColorFormat.widest(TargetFormatClass.RGBA8_UNORM, TargetFormatClass.RGBA16_FLOAT));
        assertEquals(TargetFormatClass.R11G11B10_FLOAT,
                VulkanColorFormat.widest(TargetFormatClass.RGBA8_UNORM, TargetFormatClass.R11G11B10_FLOAT),
                "equal bytes: the tie keeps the higher-precision packing, not the default");
        assertEquals(TargetFormatClass.RGBA8_UNORM,
                VulkanColorFormat.widest(TargetFormatClass.RGBA8_UNORM, TargetFormatClass.RGBA8_UNORM));
        assertEquals(TargetFormatClass.RGBA8_UNORM, VulkanColorFormat.widest(null, null));
        assertEquals(TargetFormatClass.RGBA16_FLOAT,
                VulkanColorFormat.widest(null, TargetFormatClass.RGBA16_FLOAT));
    }

    // T3 / rule R3: the pair rebuild rule - a class change rebuilds like a resize does.
    @Test
    void aClassChangeRebuildsThePair() {
        assertFalse(GllessFrameTarget.celtium$pairRebuildNeeded(true, true,
                TargetFormatClass.RGBA8_UNORM, TargetFormatClass.RGBA8_UNORM), "nothing changed");
        assertTrue(GllessFrameTarget.celtium$pairRebuildNeeded(true, true,
                TargetFormatClass.RGBA8_UNORM, TargetFormatClass.RGBA16_FLOAT), "the class changed");
        assertTrue(GllessFrameTarget.celtium$pairRebuildNeeded(false, true,
                TargetFormatClass.RGBA8_UNORM, TargetFormatClass.RGBA8_UNORM), "the size changed");
        assertTrue(GllessFrameTarget.celtium$pairRebuildNeeded(true, false,
                TargetFormatClass.RGBA8_UNORM, TargetFormatClass.RGBA8_UNORM), "the shared mode changed");
    }

    // T2 / S4: the render-pass variant key separates (count, format) and nothing else.
    @Test
    void thePassVariantKeySeparatesCountAndFormat() {
        long oneRgba8 = VulkanTerrainGpuBackend.celtium$chainPassKey(1, 37);
        assertEquals(oneRgba8, VulkanTerrainGpuBackend.celtium$chainPassKey(1, 37), "the key is stable");
        assertNotEquals(oneRgba8, VulkanTerrainGpuBackend.celtium$chainPassKey(2, 37),
                "the count is part of the key");
        assertNotEquals(oneRgba8, VulkanTerrainGpuBackend.celtium$chainPassKey(1, 97),
                "the format is part of the key");
        assertNotEquals(VulkanTerrainGpuBackend.celtium$chainPassKey(2, 97),
                VulkanTerrainGpuBackend.celtium$chainPassKey(1, 109),
                "count and format do not trade places");
    }

    // T1 / S4: the pipeline-key fold - identity for the default class, separated and flag-preserving else.
    @Test
    void thePipelineKeyFoldIsTheIdentityForTheDefaultClass() {
        long key = 0x1234_5678_9ABC_DEF0L;
        assertEquals(key, VulkanTerrainGpuBackend.celtium$pipelineKey(key, 0),
                "class 0 folds to the key itself, so every existing pipeline cache entry stays valid");
        long lineKey = key | VulkanTerrainGpuBackend.LINE_KEY_BIT;
        assertEquals(lineKey, VulkanTerrainGpuBackend.celtium$pipelineKey(lineKey, 0));
    }

    @Test
    void thePipelineKeyFoldSeparatesClassesAndKeepsTheLineBit() {
        long key = 0x1234_5678_9ABC_DEF0L;
        long folded16 = VulkanTerrainGpuBackend.celtium$pipelineKey(key, 1);
        long folded32 = VulkanTerrainGpuBackend.celtium$pipelineKey(key, 2);
        assertNotEquals(key, folded16);
        assertNotEquals(folded16, folded32, "two non-default classes must not share a pipeline entry");
        assertEquals(folded16, VulkanTerrainGpuBackend.celtium$pipelineKey(key, 1), "the fold is stable");

        long lineKey = key | VulkanTerrainGpuBackend.LINE_KEY_BIT;
        long foldedLine = VulkanTerrainGpuBackend.celtium$pipelineKey(lineKey, 1);
        assertTrue((foldedLine & VulkanTerrainGpuBackend.LINE_KEY_BIT) != 0,
                "a line batch keeps its line bit through the fold");
        assertEquals(0L, folded16 & VulkanTerrainGpuBackend.LINE_KEY_BIT,
                "a non-line batch does not gain the line bit");
        assertNotEquals(foldedLine, folded16, "line and non-line batches of one class stay apart");
    }

    // T5 / rule R8: the forced-class property parse.
    @Test
    void theForcedClassParsesNamesAndRefusesTheRest() {
        assertNull(FrameTargetJudgments.celtium$parseForcedTargetFormatClass(null));
        assertNull(FrameTargetJudgments.celtium$parseForcedTargetFormatClass("default"));
        assertNull(FrameTargetJudgments.celtium$parseForcedTargetFormatClass(""));
        assertEquals(TargetFormatClass.RGBA16_FLOAT,
                FrameTargetJudgments.celtium$parseForcedTargetFormatClass("RGBA16_FLOAT"));
        assertEquals(TargetFormatClass.RGBA16_FLOAT,
                FrameTargetJudgments.celtium$parseForcedTargetFormatClass("rgba16_float"),
                "the name is case-insensitive, like the other celtium properties");
        assertNull(FrameTargetJudgments.celtium$parseForcedTargetFormatClass("DEPTH32_FLOAT"),
                "a depth class is not a colour class: forcing one must not take effect");
        assertNull(FrameTargetJudgments.celtium$parseForcedTargetFormatClass("not-a-class"));
    }

    // QUEUE 395 section 5.1: the window path's pair class - the forced class reaches pairs no
    // registration junction resolves (a ShaderGroup group pair), while an unforced run follows the pair.
    @Test
    void theWindowPairClassFollowsThePairUnlessForced() {
        assertEquals(TargetFormatClass.RGBA8_UNORM, PackTerrainJudgments.celtium$windowPairClass(
                null, false, TargetFormatClass.RGBA8_UNORM, null), "no forcing: the pair's class stands");
        assertEquals(TargetFormatClass.RGBA16_FLOAT, PackTerrainJudgments.celtium$windowPairClass(
                null, false, TargetFormatClass.RGBA16_FLOAT, null),
                "no forcing: a junction-resolved non-default class is followed, not re-resolved");
        assertEquals(TargetFormatClass.RGBA16_FLOAT, PackTerrainJudgments.celtium$windowPairClass(
                TargetFormatClass.RGBA16_FLOAT, false, TargetFormatClass.RGBA8_UNORM,
                TargetFormatClass.RGBA16_FLOAT), "forced: a group pair leaves the default class");
        assertEquals(TargetFormatClass.RGBA8_UNORM, PackTerrainJudgments.celtium$windowPairClass(
                TargetFormatClass.RGBA16_FLOAT, false, TargetFormatClass.RGBA8_UNORM,
                TargetFormatClass.RGBA8_UNORM),
                "forced but unusable on this device: the device check's fallback stands");
        assertEquals(TargetFormatClass.RGBA8_UNORM, PackTerrainJudgments.celtium$windowPairClass(
                TargetFormatClass.RGBA16_FLOAT, true, TargetFormatClass.RGBA8_UNORM,
                TargetFormatClass.RGBA16_FLOAT), "rule R1: a pinned identity is never forced");
    }

    // Stage 1 BGRA ignition / rule R1 revised: on a BGRA frame the pin serves the frame's class, because
    // the pinned target is composed into that frame, byte order included.
    @Test
    void aPinnedTargetOnABgraFrameIsServedTheFrameClass() {
        assertEquals(TargetFormatClass.BGRA8_UNORM, VulkanColorFormat.pinnedOrRequested(
                true, 0x881A, TargetFormatClass.BGRA8_UNORM),
                "pinned on a BGRA frame: BGRA, whatever GL asked for");
        assertEquals(TargetFormatClass.BGRA8_UNORM, VulkanColorFormat.pinnedOrRequested(
                true, 0x8058, TargetFormatClass.BGRA8_UNORM));
        assertEquals(TargetFormatClass.RGBA16_FLOAT, VulkanColorFormat.pinnedOrRequested(
                false, 0x881A, TargetFormatClass.BGRA8_UNORM),
                "unpinned: the frame's class does not leak into a pair's own resolution");
        assertEquals(TargetFormatClass.BGRA8_UNORM, PackTerrainJudgments.celtium$windowPairClass(
                TargetFormatClass.RGBA16_FLOAT, true, TargetFormatClass.RGBA8_UNORM,
                TargetFormatClass.RGBA16_FLOAT, TargetFormatClass.BGRA8_UNORM),
                "rule R1 on a BGRA frame: a pinned identity is never forced - it stays in the frame's class");
    }
}
