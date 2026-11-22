package org.soutalab.celtium.patch;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The gate's filtering, against the real group table: a group leaves as a whole or not at all, essential
 * groups never leave, and the discovered list's order and contents are otherwise untouched.
 */
class PatchGateTest {

    private static PatchLedger ledger(Properties file, Map<String, String> switches) {
        return PatchLedger.of(Path.of("celtium-patches.cfg"), file, switches, false);
    }

    private static List<String> allMembers() {
        List<String> all = new ArrayList<>();
        for (String group : PatchGroups.groups()) {
            all.addAll(PatchGroups.get(group).members());
        }
        return all;
    }

    @Test
    void anEmptyLedgerRegistersEverythingInOrder() {
        List<String> discovered = allMembers();
        List<String> snapshot = new ArrayList<>(discovered);

        List<String> registered = PatchGate.register(discovered, ledger(new Properties(), Map.of()));

        assertEquals(snapshot, registered);
        assertEquals(snapshot, discovered, "the input list is not modified");
        assertEquals(97, registered.size());
    }

    @Test
    void aNullLedgerRegistersEverything() {
        List<String> discovered = allMembers();
        assertEquals(discovered, PatchGate.register(discovered, null));
        assertEquals(Map.of(), PatchGate.skippedGroups(null));
    }

    @Test
    void skippingTweaksRemovesExactlyItsNineMembers() {
        List<String> discovered = allMembers();
        PatchLedger ledger = ledger(new Properties(), Map.of("celtium.patch.tweaks", "off"));

        List<String> registered = PatchGate.register(discovered, ledger);

        assertEquals(82, registered.size());
        for (String member : PatchGroups.get("tweaks").members()) {
            assertFalse(registered.contains(member), member + " should have been filtered with its group");
        }
        List<String> expected = new ArrayList<>(discovered);
        expected.removeAll(PatchGroups.get("tweaks").members());
        assertEquals(expected, registered, "the other groups keep their members and their order");
        assertEquals(Map.of("tweaks", "kill-switch"), PatchGate.skippedGroups(ledger));
    }

    @Test
    void anEssentialGroupIsNeverFilteredEvenWhenNamed() {
        Properties file = new Properties();
        file.setProperty("off.mechanism", "true");
        file.setProperty("quarantine.textures", "apply TextureAtlasMixin: gone");
        PatchLedger ledger = ledger(file, Map.of("celtium.patch.entities", "off"));
        List<String> discovered = allMembers();

        assertEquals(discovered, PatchGate.register(discovered, ledger));
    }

    @Test
    void anUnlistedNameStaysBecauseItFallsToMechanism() {
        PatchLedger ledger = ledger(new Properties(), Map.of("celtium.patch.tweaks", "off"));
        List<String> discovered = List.of("core.MixinDirection", "core.BrandNewMixin", "core.ForgeCloudCellsMixin");

        assertEquals(List.of("core.BrandNewMixin", "core.ForgeCloudCellsMixin"),
                PatchGate.register(discovered, ledger));
    }

    @Test
    void fullyQualifiedNamesAreGroupedByTheirRelativeName() {
        PatchLedger ledger = ledger(new Properties(), Map.of("celtium.patch.sky", "off"));
        List<String> discovered = List.of(
                "org.taumc.celeritas.mixin.core.ForgeCloudCellsMixin", "core.FontRendererMixin");

        assertEquals(List.of("core.FontRendererMixin"), PatchGate.register(discovered, ledger));
    }
}
