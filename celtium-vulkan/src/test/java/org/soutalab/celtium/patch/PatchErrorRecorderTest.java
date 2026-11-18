package org.soutalab.celtium.patch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The recorder's decisions (item 356-C): only celtium's own mixins are filed, a quarantinable failure lands
 * in the ledger under its group with the phase in the reason, an essential failure files nothing and says it
 * cannot be switched off, and a ledger that cannot be written is reported instead of swallowed. Every test
 * writes only below its {@code @TempDir}, for the same reason as {@link PatchLedgerTest}.
 */
class PatchErrorRecorderTest {

    @TempDir
    Path temp;

    private Path ledgerFile() {
        return this.temp.resolve("celtium-patches.cfg");
    }

    private PatchLedger ledger() {
        return PatchLedger.load(ledgerFile(), Map.of(), false);
    }

    @Test
    void aForeignMixinIsIgnoredAndWritesNothing() {
        PatchErrorRecorder.Result result = PatchErrorRecorder.record(ledger(), "apply",
                "com.example.other.mixin.SomeMixin", new IllegalStateException("gone"));

        assertEquals(PatchErrorRecorder.Outcome.IGNORED, result.outcome());
        assertEquals("", result.group());
        assertEquals("", result.line());
        assertFalse(Files.exists(ledgerFile()), "another mod's failure is not ours to file");
    }

    @Test
    void aNullMixinAndANonMixinOwnClassAreIgnored() {
        assertEquals(PatchErrorRecorder.Outcome.IGNORED,
                PatchErrorRecorder.record(ledger(), "apply", null, new IllegalStateException("gone")).outcome());
        assertEquals(PatchErrorRecorder.Outcome.IGNORED,
                PatchErrorRecorder.record(ledger(), "prepare",
                        "org.taumc.celeritas.core.CeltiumGlStub", new IllegalStateException("gone")).outcome(),
                "celtium's own package is not enough: only the mixin package is recorded");
        assertFalse(Files.exists(ledgerFile()));
    }

    @Test
    void aQuarantinableApplyFailureIsFiledUnderItsGroup() throws IOException {
        PatchErrorRecorder.Result result = PatchErrorRecorder.record(ledger(), "apply",
                "org.taumc.celeritas.mixin.core.CloudDrawProbeMixin",
                new IllegalStateException("@Inject could not find the anchor"));

        assertEquals(PatchErrorRecorder.Outcome.QUARANTINED, result.outcome());
        assertEquals("sky", result.group());
        assertEquals("CELTIUM_PATCH_QUARANTINED group=sky mixin=CloudDrawProbeMixin"
                + " error=IllegalStateException: @Inject could not find the anchor", result.line());

        PatchLedger reloaded = PatchLedger.load(ledgerFile(), Map.of(), false);
        assertTrue(reloaded.isSkipped("sky"), "the next launch skips the group this launch failed in");
        assertEquals("apply CloudDrawProbeMixin: IllegalStateException: @Inject could not find the anchor",
                reloaded.skip("sky").reason());
    }

    @Test
    void aPrepareFailureCarriesThePreparePhase() {
        PatchErrorRecorder.Result result = PatchErrorRecorder.record(ledger(), "prepare",
                "org.taumc.celeritas.mixin.core.MixinDirection",
                new IllegalArgumentException("no such target"));

        assertEquals(PatchErrorRecorder.Outcome.QUARANTINED, result.outcome());
        assertEquals("tweaks", result.group());
        PatchLedger reloaded = PatchLedger.load(ledgerFile(), Map.of(), false);
        assertEquals("prepare MixinDirection: IllegalArgumentException: no such target",
                reloaded.skip("tweaks").reason());
    }

    @Test
    void anEssentialFailureFilesNothingAndSaysItCannotBeSwitchedOff() {
        PatchErrorRecorder.Result result = PatchErrorRecorder.record(ledger(), "apply",
                "org.taumc.celeritas.mixin.core.GlStateManagerMixin",
                new IllegalStateException("gone"));

        assertEquals(PatchErrorRecorder.Outcome.ESSENTIAL, result.outcome());
        assertEquals("mechanism", result.group());
        assertTrue(result.line().startsWith(
                "CELTIUM_PATCH_ESSENTIAL group=mechanism mixin=GlStateManagerMixin"
                        + " error=IllegalStateException: gone"), result.line());
        assertTrue(result.line().contains("cannot be switched off"), result.line());
        assertFalse(Files.exists(ledgerFile()), "an essential group is never quarantined");
    }

    @Test
    void anAuditedEssentialGroupIsEssentialToo() {
        PatchErrorRecorder.Result result = PatchErrorRecorder.record(ledger(), "apply",
                "org.taumc.celeritas.mixin.features.textures.TextureAtlasMixin",
                new IllegalStateException("gone"));

        assertEquals(PatchErrorRecorder.Outcome.ESSENTIAL, result.outcome());
        assertEquals("textures", result.group());
        assertFalse(Files.exists(ledgerFile()));
    }

    @Test
    void anUnlistedOwnMixinFallsToMechanismAndIsEssential() {
        PatchErrorRecorder.Result result = PatchErrorRecorder.record(ledger(), "apply",
                "org.taumc.celeritas.mixin.core.BrandNewMixin", new IllegalStateException("gone"));

        assertEquals(PatchErrorRecorder.Outcome.ESSENTIAL, result.outcome(),
                "a mixin the table has not caught up with takes the safe side, like the gate");
        assertEquals("mechanism", result.group());
    }

    @Test
    void aMissingLedgerIsReportedAsAFailedRecord() {
        PatchErrorRecorder.Result result = PatchErrorRecorder.record(null, "apply",
                "org.taumc.celeritas.mixin.core.RainDrawProbeMixin", new IllegalStateException("gone"));

        assertEquals(PatchErrorRecorder.Outcome.RECORD_FAILED, result.outcome());
        assertEquals("sky", result.group());
        assertTrue(result.line().startsWith(
                "CELTIUM_PATCH_RECORD_FAILED group=sky mixin=RainDrawProbeMixin"), result.line());
        assertTrue(result.line().contains("next launch will fail the same way"), result.line());
    }

    @Test
    void anUnwritableLedgerIsReportedAsAFailedRecord() throws IOException {
        Path directory = Files.createDirectory(this.temp.resolve("a-directory"));
        PatchLedger ledger = PatchLedger.load(directory, Map.of(), false);

        PatchErrorRecorder.Result result = PatchErrorRecorder.record(ledger, "apply",
                "org.taumc.celeritas.mixin.core.RainDrawProbeMixin", new IllegalStateException("gone"));

        assertEquals(PatchErrorRecorder.Outcome.RECORD_FAILED, result.outcome(),
                "the ledger path is a directory, so the atomic move cannot land: " + result.line());
    }

    @Test
    void aNullErrorIsRecordedAsNone() {
        PatchErrorRecorder.Result result = PatchErrorRecorder.record(ledger(), "apply",
                "org.taumc.celeritas.mixin.core.CloudDrawProbeMixin", null);

        assertEquals(PatchErrorRecorder.Outcome.QUARANTINED, result.outcome());
        assertTrue(result.line().endsWith("error=none"), result.line());
        PatchLedger reloaded = PatchLedger.load(ledgerFile(), Map.of(), false);
        assertEquals("apply CloudDrawProbeMixin: none", reloaded.skip("sky").reason());
    }

    @Test
    void isOwnMixinMatchesOnlyTheMixinPackage() {
        assertTrue(PatchErrorRecorder.isOwnMixin("org.taumc.celeritas.mixin.core.ParticleMixin"));
        assertFalse(PatchErrorRecorder.isOwnMixin("org.taumc.celeritas.mixin2.ParticleMixin"));
        assertFalse(PatchErrorRecorder.isOwnMixin(null));
        assertEquals("ParticleMixin", PatchErrorRecorder.simpleName("org.taumc.celeritas.mixin.core.ParticleMixin"));
        assertEquals("ParticleMixin", PatchErrorRecorder.simpleName("ParticleMixin"));
    }
}
