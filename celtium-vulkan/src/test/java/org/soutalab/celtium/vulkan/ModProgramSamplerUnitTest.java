package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule that decides whether the texture unit a mod program's sampler names can be resolved.
 * <p>
 * The binding ledger holds {@link NativeTextureBindings#UNIT_COUNT} units and answers an out-of-range read
 * with unit 0's texture, so without this refusal a sampler naming a unit past the ledger - or a negative
 * one - would sample whatever the base unit holds and draw a plausible wrong picture. The image side of
 * the same prepare already refuses out-of-range units as "not bound"; this pins the sampler side to the
 * same standard, with the unit named in the reason.
 */
class ModProgramSamplerUnitTest {

    @Test
    void unitsInsideTheLedgerResolve() {
        assertNull(ModPrograms.celtium$samplerUnitRefusal("colortex0", 0));
        assertNull(ModPrograms.celtium$samplerUnitRefusal("shadowtex0", 15));
        assertNull(ModPrograms.celtium$samplerUnitRefusal("colortex7", NativeTextureBindings.UNIT_COUNT - 1));
    }

    @Test
    void unitsPastTheLedgerRefuse() {
        String refusal = ModPrograms.celtium$samplerUnitRefusal("colortex0", NativeTextureBindings.UNIT_COUNT);
        assertNotNull(refusal, "the first unit past the ledger is already another unit's texture");
        assertTrue(refusal.contains("colortex0") && refusal.contains("32"),
                "the reason names the sampler and the unit: " + refusal);
        assertNotNull(ModPrograms.celtium$samplerUnitRefusal("shadowtex1", 4096));
    }

    @Test
    void negativeUnitsRefuse() {
        assertNotNull(ModPrograms.celtium$samplerUnitRefusal("depthtex0", -1),
                "a negative unit names no unit, not unit 0");
    }
}
