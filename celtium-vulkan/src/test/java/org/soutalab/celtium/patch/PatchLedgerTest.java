package org.soutalab.celtium.patch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The ledger's decision order and file behaviour. Every test writes only below its {@code @TempDir}: the
 * production path is the working directory's {@code config/celtium-patches.cfg}, and a test that used it
 * would leave a stray ledger in the developer's real game directory.
 */
class PatchLedgerTest {

    @TempDir
    Path temp;

    private Path ledgerFile() {
        return this.temp.resolve("celtium-patches.cfg");
    }

    private static Properties fileProps(String... keyValues) {
        Properties properties = new Properties();
        for (int i = 0; i < keyValues.length; i += 2) {
            properties.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return properties;
    }

    private PatchLedger ledger(Properties file, Map<String, String> switches) {
        return PatchLedger.of(ledgerFile(), file, switches, false);
    }

    @Test
    void anEmptyLedgerSkipsNothingAndReportsZeroes() {
        PatchLedger ledger = ledger(new Properties(), Map.of());

        assertEquals(List.of(), ledger.skippedGroups());
        assertEquals(List.of(), ledger.ignoredLines());
        assertFalse(ledger.unreadable());
        assertEquals("CELTIUM_PATCH_TABLE groups=12 essential=4 quarantinable=8 off=0 quarantined=0 mixins=86/86",
                ledger.tableLine(86, 86));
    }

    @Test
    void anOffFileEntrySkipsTheGroupWithItsRemedy() {
        PatchLedger ledger = ledger(fileProps("off.tweaks", "true"), Map.of());

        assertTrue(ledger.isSkipped("tweaks"));
        assertEquals(PatchLedger.SkipKind.OFF_FILE, ledger.skip("tweaks").kind());
        assertTrue(ledger.skippedLine("tweaks").contains("CELTIUM_PATCH_SKIPPED group=tweaks why=off-file"),
                ledger.skippedLine("tweaks"));
        assertTrue(ledger.skippedLine("tweaks").contains("remedy=delete off.tweaks"), ledger.skippedLine("tweaks"));
        assertEquals(Map.of("tweaks", "off-file"), ledger.skipped());
    }

    @Test
    void aQuarantineSkipsTheGroupAndCarriesItsReason() {
        PatchLedger ledger = ledger(fileProps("quarantine.sky", "apply CloudDrawProbeMixin: anchor missing"),
                Map.of());

        assertTrue(ledger.isSkipped("sky"));
        assertEquals(PatchLedger.SkipKind.QUARANTINE, ledger.skip("sky").kind());
        String line = ledger.skippedLine("sky");
        assertTrue(line.contains("why=quarantined reason=apply CloudDrawProbeMixin: anchor missing"), line);
        assertTrue(line.contains("remedy=delete quarantine.sky"), line);
        assertTrue(ledger.tableLine(86, 83).contains("off=0 quarantined=1"), ledger.tableLine(86, 83));
    }

    @Test
    void theKillSwitchBeatsTheFileAndOffBeatsQuarantine() {
        PatchLedger bySwitch = ledger(
                fileProps("quarantine.tweaks", "apply MixinDirection: gone", "off.tweaks", "true"),
                Map.of("celtium.patch.tweaks", "off"));
        assertEquals(PatchLedger.SkipKind.KILL_SWITCH, bySwitch.skip("tweaks").kind());
        assertTrue(bySwitch.skippedLine("tweaks").contains("switch=-Dceltium.patch.tweaks=off"),
                bySwitch.skippedLine("tweaks"));

        PatchLedger byFile = ledger(
                fileProps("quarantine.tweaks", "apply MixinDirection: gone", "off.tweaks", "true"), Map.of());
        assertEquals(PatchLedger.SkipKind.OFF_FILE, byFile.skip("tweaks").kind(),
                "the decision order is kill switch, then off, then quarantine");
    }

    @Test
    void anEssentialGroupIsNeverSkippedAndTheAttemptIsReported() {
        PatchLedger ledger = ledger(
                fileProps("off.mechanism", "true", "quarantine.mechanism", "apply GlStateManagerMixin: gone"),
                Map.of("celtium.patch.mechanism", "off"));

        assertFalse(ledger.isSkipped("mechanism"));
        assertEquals(3, ledger.ignoredLines().size(), "one line per ignored source: " + ledger.ignoredLines());
        for (String line : ledger.ignoredLines()) {
            assertTrue(line.startsWith("CELTIUM_PATCH_IGNORED group=mechanism why=essential"), line);
        }
    }

    @Test
    void unknownKeysAreReportedNotSwallowed() {
        PatchLedger ledger = ledger(fileProps("off.twaks", "true"),
                Map.of("celtium.patch.twaks", "off", "celtium.patch.file", "elsewhere.cfg",
                        "celtium.patch.census", "true"));

        assertEquals(List.of(), ledger.skippedGroups());
        assertEquals(2, ledger.ignoredLines().size(), "the typo in both places: " + ledger.ignoredLines());
        for (String line : ledger.ignoredLines()) {
            assertTrue(line.contains("why=unknown-group"), line);
        }
    }

    @Test
    void aMissingFileIsAFirstLaunchAndDecisionsWriteNothing() throws IOException {
        Path file = ledgerFile();
        PatchLedger ledger = PatchLedger.load(file, Map.of("celtium.patch.tweaks", "off"), false);

        assertTrue(ledger.isSkipped("tweaks"));
        assertFalse(ledger.unreadable());
        assertFalse(Files.exists(file), "reading a ledger - and a -D switch - never writes the file");
    }

    @Test
    void anUnreadableFileLeavesEveryGroupOnAndSaysSo() throws IOException {
        Path directory = Files.createDirectory(this.temp.resolve("a-directory"));
        PatchLedger ledger = PatchLedger.load(directory, Map.of(), false);

        assertTrue(ledger.unreadable());
        assertEquals(List.of(), ledger.skippedGroups());
        assertTrue(ledger.tableLine(86, 86).contains("status=unreadable"), ledger.tableLine(86, 86));
    }

    @Test
    void recordQuarantineRoundTripsThroughTheFile() throws IOException {
        PatchLedger ledger = PatchLedger.load(ledgerFile(), Map.of(), false);

        assertTrue(ledger.recordQuarantine("tweaks", "apply MixinDirection: InvalidMixinException: gone"));
        assertTrue(Files.isRegularFile(ledgerFile()));
        assertFalse(Files.exists(this.temp.resolve("celtium-patches.cfg.tmp")),
                "the temporary is moved into place, not left behind");

        PatchLedger reloaded = PatchLedger.load(ledgerFile(), Map.of(), false);
        assertTrue(reloaded.isSkipped("tweaks"));
        assertEquals("apply MixinDirection: InvalidMixinException: gone", reloaded.skip("tweaks").reason());
    }

    @Test
    void recordQuarantineRefusesEssentialAndUnknownGroups() {
        PatchLedger ledger = ledger(new Properties(), Map.of());

        assertFalse(ledger.recordQuarantine("mechanism", "apply GlStateManagerMixin: gone"));
        assertFalse(ledger.recordQuarantine("no-such-group", "apply Whatever: gone"));
        assertFalse(Files.exists(ledgerFile()), "a refused record writes nothing");
    }

    @Test
    void aQuarantineIsNeverClearedAutomatically() throws IOException {
        Files.writeString(ledgerFile(), "quarantine.tweaks=apply MixinDirection: gone\n");
        PatchLedger first = PatchLedger.load(ledgerFile(), Map.of(), false);
        PatchLedger second = PatchLedger.load(ledgerFile(), Map.of(), false);

        assertTrue(first.isSkipped("tweaks"));
        assertTrue(second.isSkipped("tweaks"),
                "reading the ledger again - the shape of a later, healthy launch - does not clear it");
    }

    @Test
    void quarantineReasonsAreOneLineAndCapped() {
        String reason = PatchLedger.quarantineReason("apply", "MixinDirection",
                new IllegalStateException("first line\nsecond line"));
        assertEquals("apply MixinDirection: IllegalStateException: first line second line", reason);

        String longReason = PatchLedger.quarantineReason("apply", "MixinDirection",
                new IllegalStateException("x".repeat(400)));
        assertTrue(longReason.length() <= 240);
    }

    @Test
    void theCensusFlagIsCarriedFromConstruction() {
        PatchLedger ledger = PatchLedger.of(ledgerFile(), new Properties(), Map.of(), true);
        assertTrue(ledger.census());
        assertFalse(ledger(new Properties(), Map.of()).census());
    }
}
