package org.soutalab.celtium.vulkan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.gl.CeltiumGlStub;

/**
 * Pins the contract of the drawing-only no-op census.
 * <p>
 * The census exists to answer one question about the picture: which drawing entry points were called and reached no
 * image. It is the only instrument that can answer it, because the general no-op census is ranked by call count and a
 * thousand {@code glHint} calls sit on top of every drawing entry point that ever arrives.
 * <p>
 * The failure mode this test is written against is the one this project keeps finding: a census that reads clean
 * because it cannot fire. A name in the drawing set that is not registered in the table can never be counted, so the
 * line would read {@code none} for ever and look like a complete drawing surface. The first test is that check, and it
 * is the reason {@link CeltiumGlStub#isNoopEntryPoint(String)} exists.
 */
class NoopDrawingCensusTest {

    @Test
    void everyDrawingNameIsRegisteredAsANoopEntryPoint() {
        for (String name : CeltiumGlStub.noopDrawingNames()) {
            assertTrue(CeltiumGlStub.isNoopEntryPoint(name),
                    name + " is in the drawing census but not registered as a no-op entry point, so a call to it could"
                            + " never be counted and the census would read 'none' for ever");
        }
    }

    @Test
    void aCalledDrawingNoopIsNamedWithItsCount() {
        CeltiumGlStub.celtium$noteNoop("glDrawElements");
        String summary = CeltiumGlStub.noopDrawingCensusSummary();
        assertTrue(summary.contains("glDrawElements="),
                "a drawing entry point that was called must be named in the census, got: " + summary);
        assertFalse(summary.equals("none"), "the census must not read 'none' after a drawing no-op was called");
    }

    @Test
    void aCalledStateNoopIsNotNamed() {
        // glHint is the archetype of the calls that bury a drawing entry point in the ranked census: it is state, it is
        // called constantly, and it must not appear in the drawing line.
        CeltiumGlStub.celtium$noteNoop("glHint");
        String summary = CeltiumGlStub.noopDrawingCensusSummary();
        assertFalse(summary.contains("glHint"), "state calls must not be reported as missing geometry, got: " + summary);
    }

    @Test
    void theSummaryNamesOnlyDrawingEntryPoints() {
        String summary = CeltiumGlStub.noopDrawingCensusSummary();
        if (summary.equals("none")) {
            return;
        }
        for (String token : summary.split(" ")) {
            int equals = token.indexOf('=');
            assertTrue(equals > 0, "every token must be name=count, got: " + token);
            String name = token.substring(0, equals);
            assertTrue(CeltiumGlStub.noopDrawingNames().contains(name),
                    name + " is not a drawing entry point but the drawing census named it");
            long count = Long.parseLong(token.substring(equals + 1));
            assertTrue(count > 0L, "a zero count must not be printed, got: " + token);
        }
    }

    /**
     * The join between the two censuses' spellings.
     * <p>
     * The stub's table holds bare names; the transformer's unrouted census holds the JVM key it rewrote, descriptor
     * included. A bare-name comparison against the second form is false for every entry, so the live drawing line
     * would read {@code none} for ever - which is why this test drives both spellings rather than only the one the
     * stub happens to use.
     */
    @Test
    void aDescriptorSpelledKeyIsStillADrawingEntryPoint() {
        assertTrue(CeltiumGlStub.isDrawingEntryPointKey("glDrawElements"),
                "the bare spelling must be recognised");
        assertTrue(CeltiumGlStub.isDrawingEntryPointKey("glDrawElements(IIIJ)V"),
                "the transformer's key spelling must be recognised, or the live drawing census can never fire");
        assertTrue(CeltiumGlStub.isDrawingEntryPointKey("glMultiDrawArrays(IJJI)V"),
                "every drawing name must survive the descriptor strip");
        assertFalse(CeltiumGlStub.isDrawingEntryPointKey("glUseProgram(I)V"),
                "a state call must not be reported as missing geometry");
        assertFalse(CeltiumGlStub.isDrawingEntryPointKey("glHint(II)V"), "a state call must not be reported");
        assertFalse(CeltiumGlStub.isDrawingEntryPointKey(""), "an empty key is not a drawing entry point");
        assertFalse(CeltiumGlStub.isDrawingEntryPointKey(null), "a null key is not a drawing entry point");
    }
}
