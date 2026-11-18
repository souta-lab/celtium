package org.soutalab.celtium.patch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The pure filtering half of the item 356 patch gate (step 356-B): given the mixins the plugin discovered and
 * the launch's {@link PatchLedger}, return the ones to register.
 * <p>
 * The unit is the group, never a single mixin (see {@link PatchGroups}): a group is either entirely in the
 * returned list or entirely absent, because one failing mixin aborts its whole target class and the members
 * are written to work as a set. Essential groups are never filtered, even if a ledger somehow names them -
 * the ledger already refuses to skip them, and the gate checks again so a future ledger change cannot make
 * the renderer silently disappear. An unlisted name falls to {@code mechanism} in {@link PatchGroups#of},
 * which is essential, so a table that falls behind a new mixin cannot make that mixin skippable by accident.
 * <p>
 * The input list is never modified and its order is preserved: discovery order is the plugin's, and the gate
 * has no ordering opinion of its own.
 */
public final class PatchGate {

    private PatchGate() {
    }

    /**
     * {@return a new list holding the discovered mixins whose group the ledger does not skip; with a null
     * ledger, every discovered mixin}
     */
    public static List<String> register(List<String> discovered, PatchLedger ledger) {
        List<String> out = new ArrayList<String>(discovered.size());
        for (String name : discovered) {
            String group = PatchGroups.of(name);
            if (ledger != null && !PatchGroups.isEssential(group) && ledger.isSkipped(group)) {
                continue;
            }
            out.add(name);
        }
        return out;
    }

    /** {@return the groups the gate will filter for this ledger, as group -&gt; short reason, in table order}. */
    public static Map<String, String> skippedGroups(PatchLedger ledger) {
        return ledger == null ? Map.of() : ledger.skipped();
    }
}
