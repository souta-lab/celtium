package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mod-program report census prints one line per program and outcome, up to its cap - and past the cap
 * it used to print nothing at all, ever, with no sign that reports were being suppressed. A run whose
 * sixty-fifth distinct refusal never appears reads exactly like a run that had sixty-four. The suppressed
 * reports are counted and surfaced in aggregate; this pins both halves.
 */
class ModProgramReportCensusTest {

    @Test
    void reportsPastTheCensusAreCountedInAggregate() {
        ModPrograms programs = new ModPrograms(null);
        java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
        java.io.PrintStream original = System.out;
        System.setOut(new java.io.PrintStream(captured));
        try {
            for (int i = 0; i < 64; i++) {
                programs.celtium$reportModProgram(1000 + i, false, "refusal " + i);
            }
            for (int i = 0; i < 64; i++) {
                programs.celtium$reportModProgram(2000 + i, false, "refusal " + i);
            }
        } finally {
            System.setOut(original);
        }
        String out = captured.toString();
        assertEquals(64, programs.modProgramReported.size(), "the census holds its cap");
        assertTrue(out.contains("CELTIUM_MOD_PROGRAM_NOT_DRAWN program=1000"),
                "the first census prints in full; got: " + out);
        assertFalse(out.contains("program=2000"), "a report past the cap is not printed in full");
        assertTrue(out.contains("CELTIUM_MOD_PROGRAM_SUPPRESSED suppressed=64"),
                "but the suppressed reports surface in aggregate; got: " + out);
    }
}
