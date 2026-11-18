package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetFormatClass;
import org.soutalab.celtium.vulkan.TargetFormatCensus.Outcome;
import org.soutalab.celtium.vulkan.TargetFormatCensus.Resolution;
import org.soutalab.celtium.vulkan.TargetFormatCensus.Verdict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure rules of QUEUE 405 (the D18 census, S5/S6), exercised without a device: the verdict matrix of
 * {@link TargetFormatCensus#decide}, the widenable/depth format sets, the per-channel bit counts behind
 * the stale-answer counter, and the counting itself (as deltas - the census is process-wide, so a test
 * asserts what its own calls moved). What is pinned here is every decision that does not need a device,
 * because a wrong verdict is silent in game: the pair still renders, and only the record of what was
 * asked for would be wrong.
 */
class TargetFormatCensusTest {

    private static Resolution resolution(TargetFormatClass chosen, int slot0Gl,
            TargetFormatClass widestRequested, boolean slotsDiffer, boolean anyUnmapped,
            boolean anyWidenable, boolean pinned, boolean forcedApplied, boolean deviceRefused) {
        return new Resolution(chosen, 4102, slot0Gl, widestRequested, slotsDiffer, anyUnmapped,
                anyWidenable, pinned, forcedApplied, deviceRefused);
    }

    private static Resolution plain(TargetFormatClass chosen, int slot0Gl,
            TargetFormatClass widestRequested) {
        return resolution(chosen, slot0Gl, widestRequested, false, false, false, false, false, false);
    }

    // S5: a request served in its own class is exact, at the default and above it.
    @Test
    void aServedRequestIsExact() {
        assertEquals(new Outcome(Verdict.EXACT, "none"),
                TargetFormatCensus.decide(plain(TargetFormatClass.RGBA8_UNORM, 0x8058,
                        TargetFormatClass.RGBA8_UNORM)));
        assertEquals(new Outcome(Verdict.EXACT, "none"),
                TargetFormatCensus.decide(plain(TargetFormatClass.RGBA16_FLOAT, 0x881A,
                        TargetFormatClass.RGBA16_FLOAT)),
                "GL_RGBA16F asked for the 16F class and got it: nothing fell back");
        assertEquals(new Outcome(Verdict.EXACT, "none"),
                TargetFormatCensus.decide(plain(TargetFormatClass.R11G11B10_FLOAT, 0x8C3A,
                        TargetFormatClass.R11G11B10_FLOAT)));
    }

    // S5: an unmapped colour request is the fallback the queue item exists to name, and it outranks
    // the pin - a class exists for neither, but "no class names this" is the stronger fact.
    @Test
    void anUnmappedRequestFallsBackAndOutranksThePin() {
        assertEquals(new Outcome(Verdict.FALLBACK, "unmapped"),
                TargetFormatCensus.decide(resolution(TargetFormatClass.RGBA8_UNORM, 0x8059,
                        TargetFormatClass.RGBA8_UNORM, false, true, false, false, false, false)),
                "GL_RGB10_A2 has no class in the vocabulary: served by the default, counted as a fallback");
        assertEquals(new Outcome(Verdict.FALLBACK, "unmapped"),
                TargetFormatCensus.decide(resolution(TargetFormatClass.RGBA8_UNORM, 0x8059,
                        TargetFormatClass.RGBA16_FLOAT, false, true, false, true, false, false)));
    }

    // S5 / rule R1: a pinned target that was asked for more than the frame's class falls back by pin.
    @Test
    void aPinnedTargetAskedForMoreFallsBackByPin() {
        assertEquals(new Outcome(Verdict.FALLBACK, "main-identity-pinned"),
                TargetFormatCensus.decide(resolution(TargetFormatClass.RGBA8_UNORM, 0x881A,
                        TargetFormatClass.RGBA16_FLOAT, false, false, false, true, false, false)));
        assertEquals(new Outcome(Verdict.EXACT, "none"),
                TargetFormatCensus.decide(resolution(TargetFormatClass.RGBA8_UNORM, 0x8058,
                        TargetFormatClass.RGBA8_UNORM, false, false, false, true, false, false)),
                "a pinned target asked for the frame's own class lost nothing to the pin");
    }

    // S5: a class the device refused falls back with the device as the reason.
    @Test
    void aDeviceRefusalFallsBackWithItsReason() {
        assertEquals(new Outcome(Verdict.FALLBACK, "device-lacks-feature"),
                TargetFormatCensus.decide(resolution(TargetFormatClass.RGBA8_UNORM, 0x881A,
                        TargetFormatClass.RGBA16_FLOAT, false, false, false, false, false, true)));
    }

    // S5: a forced class is judged against the request it overrode - wider is a widening, anything
    // else that differs is a fallback, and an equal forced class is exact.
    @Test
    void aForcedClassIsJudgedAgainstTheRequest() {
        assertEquals(new Outcome(Verdict.WIDENED, "forced"),
                TargetFormatCensus.decide(resolution(TargetFormatClass.RGBA16_FLOAT, 0x8058,
                        TargetFormatClass.RGBA8_UNORM, false, false, false, false, true, false)),
                "forced 16F over an 8-bit request: the pair holds more than was asked");
        assertEquals(new Outcome(Verdict.FALLBACK, "forced"),
                TargetFormatCensus.decide(resolution(TargetFormatClass.R11G11B10_FLOAT, 0x881A,
                        TargetFormatClass.RGBA16_FLOAT, false, false, false, false, true, false)),
                "forced R11G11B10 over a 16F request: same storage, less precision - not a widening");
        assertEquals(new Outcome(Verdict.EXACT, "none"),
                TargetFormatCensus.decide(resolution(TargetFormatClass.RGBA16_FLOAT, -1,
                        TargetFormatClass.RGBA16_FLOAT, false, false, false, false, true, false)),
                "the forced path reads no request: the class it forced is the class that was served");
    }

    // S5: an unrecorded texture has no request to have met - a fallback under its own reason, never
    // an exact service (item 394 plan's UNKNOWN row).
    @Test
    void anUnrecordedRequestIsNotAnExactService() {
        assertEquals(new Outcome(Verdict.FALLBACK, "unrecorded"),
                TargetFormatCensus.decide(plain(TargetFormatClass.RGBA8_UNORM, 0,
                        TargetFormatClass.RGBA8_UNORM)));
    }

    // S5: a request RGBA8 fully represents is widened, not fallen back - the split the three-way
    // verdict exists for.
    @Test
    void aFullyRepresentableRequestIsWidenedNotFallenBack() {
        assertEquals(new Outcome(Verdict.WIDENED, "widened-to-rgba8"),
                TargetFormatCensus.decide(resolution(TargetFormatClass.RGBA8_UNORM, 0x8051,
                        TargetFormatClass.RGBA8_UNORM, false, false, true, false, false, false)),
                "GL_RGB8 served by RGBA8 loses nothing but an alpha channel it never had");
    }

    // S5: the widenable and depth sets themselves - the rows the verdicts are computed from.
    @Test
    void theWidenableAndDepthSetsAreThePlansRows() {
        assertTrue(TargetFormatCensus.isWidenableToRgba8(0x8051), "GL_RGB8");
        assertTrue(TargetFormatCensus.isWidenableToRgba8(0x1907), "GL_RGB");
        assertTrue(TargetFormatCensus.isWidenableToRgba8(0x8D62), "GL_RGB565");
        assertTrue(TargetFormatCensus.isWidenableToRgba8(0x8056), "GL_RGBA4");
        assertTrue(TargetFormatCensus.isWidenableToRgba8(0x8057), "GL_RGB5_A1");
        assertTrue(TargetFormatCensus.isWidenableToRgba8(0x8040), "GL_LUMINANCE8");
        assertTrue(TargetFormatCensus.isWidenableToRgba8(0x803C), "GL_ALPHA8");
        assertFalse(TargetFormatCensus.isWidenableToRgba8(0x8059),
                "GL_RGB10_A2 asks for 10 bits: RGBA8 cannot represent it, so it is a fallback");
        assertFalse(TargetFormatCensus.isWidenableToRgba8(0x8058), "GL_RGBA8 is mapped, not widened");
        assertFalse(TargetFormatCensus.isWidenableToRgba8(0x881A), "GL_RGBA16F is mapped, not widened");

        assertTrue(TargetFormatCensus.isDepthInternalFormat(0x81A6), "GL_DEPTH_COMPONENT24");
        assertTrue(TargetFormatCensus.isDepthInternalFormat(0x1902), "GL_DEPTH_COMPONENT");
        assertFalse(TargetFormatCensus.isDepthInternalFormat(0x881A));
        assertFalse(TargetFormatCensus.isDepthInternalFormat(0));
    }

    // S6: the per-channel bits a class actually carries - the numbers the stub's flat 8 is compared
    // against, including the packed class's missing alpha.
    @Test
    void channelBitsAreTheClasssOwn() {
        for (int channel = 0; channel < 4; channel++) {
            assertEquals(8, TargetFormatCensus.channelBits(TargetFormatClass.RGBA8_UNORM, channel));
            assertEquals(16, TargetFormatCensus.channelBits(TargetFormatClass.RGBA16_FLOAT, channel));
            assertEquals(32, TargetFormatCensus.channelBits(TargetFormatClass.RGBA32_FLOAT, channel));
        }
        assertEquals(11, TargetFormatCensus.channelBits(TargetFormatClass.R11G11B10_FLOAT, 0));
        assertEquals(11, TargetFormatCensus.channelBits(TargetFormatClass.R11G11B10_FLOAT, 1));
        assertEquals(10, TargetFormatCensus.channelBits(TargetFormatClass.R11G11B10_FLOAT, 2));
        assertEquals(0, TargetFormatCensus.channelBits(TargetFormatClass.R11G11B10_FLOAT, 3),
                "the packed class has no alpha channel: its honest alpha answer is 0, not 8 or 11");
        assertEquals(-1, TargetFormatCensus.channelBits(TargetFormatClass.DEPTH32_FLOAT, 0),
                "a depth class has no colour channels for a bit-count query to describe");
    }

    // S6: the stale-answer counter moves only on a real disagreement - the frame's own class agrees
    // with the flat 8 by construction, which is the positive control that the counter is not just
    // counting queries.
    @Test
    void theStaleBitAnswerCountsOnlyDisagreements() {
        long before = TargetFormatCensus.bitsAnsweredStale();
        assertFalse(TargetFormatCensus.noteBitsAnswer(0x0D52, 8, TargetFormatClass.RGBA8_UNORM),
                "the delivered frame is RGBA8: the flat 8 is its true answer");
        assertEquals(before, TargetFormatCensus.bitsAnsweredStale());
        assertTrue(TargetFormatCensus.noteBitsAnswer(0x0D52, 8, TargetFormatClass.RGBA16_FLOAT),
                "a 16F pair is current: answering 8 for its red bits is stale");
        assertTrue(TargetFormatCensus.noteBitsAnswer(0x0D55, 8, TargetFormatClass.R11G11B10_FLOAT),
                "the packed class has no alpha: answering 8 for its alpha bits is stale");
        assertFalse(TargetFormatCensus.noteBitsAnswer(0x0D56, 24, TargetFormatClass.RGBA16_FLOAT),
                "GL_DEPTH_BITS is not one of the colour pnames this counter judges");
        assertEquals(before + 2, TargetFormatCensus.bitsAnsweredStale());
    }

    // S5: the counting follows the verdict, and an unrecorded request raises its own counter beside
    // the fallback it is counted as.
    @Test
    void theCountersFollowTheVerdict() {
        long exactBefore = TargetFormatCensus.exact();
        long fallbacksBefore = TargetFormatCensus.fallbacks();
        long unknownBefore = TargetFormatCensus.unknownRequests();
        TargetFormatCensus.noteResolution(7L, plain(TargetFormatClass.RGBA16_FLOAT, 0x881A,
                TargetFormatClass.RGBA16_FLOAT), null);
        assertEquals(exactBefore + 1, TargetFormatCensus.exact());
        assertEquals(fallbacksBefore, TargetFormatCensus.fallbacks());
        TargetFormatCensus.noteResolution(7L, plain(TargetFormatClass.RGBA8_UNORM, 0,
                TargetFormatClass.RGBA8_UNORM), null);
        assertEquals(fallbacksBefore + 1, TargetFormatCensus.fallbacks());
        assertEquals(unknownBefore + 1, TargetFormatCensus.unknownRequests(),
                "the unrecorded decision counts as a fallback and as an unknown request");
    }

    // S5: the printed line carries the fields a leg is read by - identity, request, verdict, chosen
    // class with its Vk value, and the fallback reason - and says unknown for the device features of
    // a class the resolution never checked.
    @Test
    void theCensusLineCarriesTheDecision() {
        Resolution unmapped = new Resolution(TargetFormatClass.RGBA8_UNORM, 4102, 0x8059,
                TargetFormatClass.RGBA8_UNORM, false, true, false, false, false, false);
        String line = TargetFormatCensus.line(0x2B7L, unmapped,
                TargetFormatCensus.decide(unmapped), null);
        assertTrue(line.contains("CELTIUM_TARGET_FORMAT"), line);
        assertTrue(line.contains("identity=0x2b7"), line);
        assertTrue(line.contains("texture=4102"), line);
        assertTrue(line.contains("requested=0x8059"), line);
        assertTrue(line.contains("verdict=FALLBACK"), line);
        assertTrue(line.contains("chosen=RGBA8_UNORM"), line);
        assertTrue(line.contains("vk=37(0x25)"), line);
        assertTrue(line.contains("bytesPerPixel=4"), line);
        assertTrue(line.contains("attach=unknown"), line);
        assertTrue(line.contains("fallback=unmapped"), line);

        String checked = TargetFormatCensus.line(0x2B7L,
                plain(TargetFormatClass.RGBA16_FLOAT, 0x881A, TargetFormatClass.RGBA16_FLOAT),
                new Outcome(Verdict.EXACT, "none"), new boolean[] { true, true, true });
        assertTrue(checked.contains("attach=true blend=true sample=true"), checked);
        assertTrue(checked.contains("fallback=none"), checked);
    }

    // S5/S6: the summary segment names the counters a leg is grepped by.
    @Test
    void theSummaryNamesTheCounters() {
        String summary = TargetFormatCensus.summary();
        assertTrue(summary.contains("targetFormatExact="), summary);
        assertTrue(summary.contains("targetFormatWidened="), summary);
        assertTrue(summary.contains("targetFormatFallbacks="), summary);
        assertTrue(summary.contains("targetFormatUnknownRequest="), summary);
        assertTrue(summary.contains("targetFormatTupleWidened="), summary);
        assertTrue(summary.contains("targetFormatMainPinned="), summary);
        assertTrue(summary.contains("targetFormatDeviceRefused="), summary);
        assertTrue(summary.contains("targetFormatBitsAnsweredStale="), summary);
        assertTrue(summary.contains("targetFormatRequests["), summary);
    }
}
