package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The LightmapIdSource failure census (roam-hunt-7's silent-failure sweep).
 * <p>
 * The three accessors answer for game-side suppliers. A supplier that throws answered exactly
 * like an absent supplier - -1 for the ids, null for the pixels - which is the right answer (the
 * backend's refusal paths report it as a missing lightmap or atlas, and a broken source must not
 * take the frame down). But nothing recorded the failure, so a supplier that threw on every
 * call was indistinguishable from one never installed: the session lost its lightmap/atlas ids
 * and the only trace was the downstream refusal with id=-1, the shape this class's own javadoc
 * records measuring 92% of a presented image black. These tests pin that the answer stays the
 * same and the failure is now counted and first-named.
 */
class LightmapIdSourceTest {

    @BeforeEach
    void reset() {
        LightmapIdSource.install(null);
        LightmapIdSource.installAtlasId(null);
        LightmapIdSource.installPixels(null);
        LightmapIdSource.resetSupplierFailuresForTest();
    }

    @AfterEach
    void uninstall() {
        LightmapIdSource.install(null);
        LightmapIdSource.installAtlasId(null);
        LightmapIdSource.installPixels(null);
        LightmapIdSource.resetSupplierFailuresForTest();
    }

    @Test
    void absenceIsNotAFailure() {
        assertEquals(-1, LightmapIdSource.lightmapTextureId());
        assertEquals(-1, LightmapIdSource.atlasTextureId());
        assertNull(LightmapIdSource.lightmapPixels());
        assertEquals(0L, LightmapIdSource.supplierFailures(),
                "no installed source is a normal state, not a failure to record");
        assertNull(LightmapIdSource.firstSupplierFailure());
    }

    @Test
    void aThrowingSupplierKeepsTheAbsenceAnswerAndIsCounted() {
        LightmapIdSource.install(() -> {
            throw new IllegalStateException("owner not built yet");
        });

        assertEquals(-1, LightmapIdSource.lightmapTextureId(),
                "the answer is unchanged: -1, as if no source were installed");
        assertEquals(-1, LightmapIdSource.lightmapTextureId());
        assertEquals(2L, LightmapIdSource.supplierFailures(), "every failed call is counted");
        assertTrue(LightmapIdSource.firstSupplierFailure().contains("lightmapTextureId"),
                "the first failure names which accessor's supplier failed");
        assertTrue(LightmapIdSource.firstSupplierFailure().contains("owner not built yet"),
                "the first failure keeps the cause");
    }

    @Test
    void theFirstFailureIsKeptAcrossAccessors() {
        LightmapIdSource.installAtlasId(() -> {
            throw new LinkageError("game side not linked");
        });
        LightmapIdSource.installPixels(() -> {
            throw new IllegalStateException("pixels gone");
        });

        assertEquals(-1, LightmapIdSource.atlasTextureId());
        assertNull(LightmapIdSource.lightmapPixels());
        assertEquals(2L, LightmapIdSource.supplierFailures());
        assertTrue(LightmapIdSource.firstSupplierFailure().contains("atlasTextureId"),
                "the first failure is the one kept - later failures do not overwrite the cause");
    }

    @Test
    void aWorkingSupplierAnswersAndRecordsNothing() {
        LightmapIdSource.install(() -> 4242);
        LightmapIdSource.installPixels(() -> new int[]{1, 2, 3});

        assertEquals(4242, LightmapIdSource.lightmapTextureId());
        assertEquals(3, LightmapIdSource.lightmapPixels().length);
        assertEquals(0L, LightmapIdSource.supplierFailures());
    }
}
