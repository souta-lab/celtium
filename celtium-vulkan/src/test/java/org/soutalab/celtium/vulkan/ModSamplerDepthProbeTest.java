package org.soutalab.celtium.vulkan;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When the depth-attach probe line for a refused sampler is printed (item 537 wall (b2)).
 * <p>
 * A sampler refused for having no native image may be a framebuffer's depth attachment whose backend depth
 * record never arrived (branch i), or an independent depth texture no framebuffer attaches (branch ii). The
 * probe prints the stub's attach record for the texture name once - the first refusal for that name - so a
 * confirmation leg can read the branch off one line instead of re-deriving it. A probe that printed on every
 * refused prepare would be per-frame noise on a hot refusal path, so the claim rule is the contract: first
 * refusal per name claims the line, later ones do not, unnamed textures never do, and the set is bounded.
 * <p>
 * What these tests prove: the claim table of {@code celtium$claimSamplerDepthProbe} only. What the printed
 * value is for a given name is the confirmation leg's measurement, not this test's.
 */
class ModSamplerDepthProbeTest {

    @Test
    void theFirstRefusalForANameClaimsTheLine() {
        IntOpenHashSet probed = new IntOpenHashSet();
        assertTrue(ModPrograms.celtium$claimSamplerDepthProbe(probed, 171),
                "the altDepth name of the Tier 1 leg claims its probe line at its first refusal");
        assertFalse(ModPrograms.celtium$claimSamplerDepthProbe(probed, 171),
                "the same name never claims a second line, however often the prepare retries");
    }

    @Test
    void eachNameClaimsItsOwnLine() {
        IntOpenHashSet probed = new IntOpenHashSet();
        assertTrue(ModPrograms.celtium$claimSamplerDepthProbe(probed, 5));
        assertTrue(ModPrograms.celtium$claimSamplerDepthProbe(probed, 171));
        assertFalse(ModPrograms.celtium$claimSamplerDepthProbe(probed, 5));
    }

    @Test
    void unnamedTexturesNeverClaim() {
        IntOpenHashSet probed = new IntOpenHashSet();
        assertFalse(ModPrograms.celtium$claimSamplerDepthProbe(probed, 0),
                "an unbound unit has no name to look up in the attach record");
        assertFalse(ModPrograms.celtium$claimSamplerDepthProbe(probed, -1));
        assertTrue(probed.isEmpty(), "a refused claim records nothing");
    }

    @Test
    void theProbeSetIsBounded() {
        IntOpenHashSet probed = new IntOpenHashSet();
        for (int name = 1; name <= 16; name++) {
            assertTrue(ModPrograms.celtium$claimSamplerDepthProbe(probed, name));
        }
        assertFalse(ModPrograms.celtium$claimSamplerDepthProbe(probed, 17),
                "past the bound the probe stays silent rather than growing with the run");
    }
}
