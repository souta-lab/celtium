package org.soutalab.celtium.patch;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The patch-group table's own invariants, at the seam the offline suite can check: the counts below are the
 * census the gate's acceptance is written against, so a table edit that changes them fails here first and the
 * new numbers have to be adopted deliberately. Completeness against the source tree is the Python checker's
 * job ({@code scripts/check_patch_groups.py}); this class pins the Java side's reading of the table.
 */
class PatchGroupsTest {

    @Test
    void theTableHasFourteenGroupsAndOneHundredFourMembersWithNoDuplicates() {
        assertEquals(14, PatchGroups.groups().size());
        int members = 0;
        Set<String> seen = new HashSet<>();
        for (String group : PatchGroups.groups()) {
            List<String> groupMembers = PatchGroups.get(group).members();
            assertFalse(groupMembers.isEmpty(), "group " + group + " lists no mixins");
            members += groupMembers.size();
            for (String member : groupMembers) {
                assertTrue(seen.add(member), member + " is listed twice");
            }
        }
        assertEquals(104, members);
        assertEquals(104, seen.size());
    }

    @Test
    void theEssentialGroupsAreTheFourThatMayNeverBeSkipped() {
        Set<String> essential = new HashSet<>();
        for (String group : PatchGroups.groups()) {
            if (PatchGroups.isEssential(group)) {
                essential.add(group);
            }
        }
        assertEquals(Set.of("mechanism", "terrain_geometry", "entities", "textures"), essential);
        assertFalse(PatchGroups.isEssential("tweaks"));
        assertFalse(PatchGroups.isEssential("no-such-group"), "an unknown id is not essential, just unknown");
    }

    @Test
    void ofResolvesTheCollidingSimpleNamesByRelativeName() {
        assertEquals("textures", PatchGroups.of("core.RenderItemMixin"));
        assertEquals("textures", PatchGroups.of("features.textures.RenderItemMixin"));
        assertEquals("textures", PatchGroups.of("core.TextureUtilMixin"));
        assertEquals("textures", PatchGroups.of("features.mipmaps.TextureUtilMixin"));
        assertEquals("textures", PatchGroups.of("features.mipmaps.TextureAtlasSpriteMixin"));
        assertEquals("textures", PatchGroups.of("features.textures.TextureAtlasSpriteMixin"));
        assertEquals("tweaks", PatchGroups.of("core.MixinDirection"));
        assertEquals("mechanism", PatchGroups.of("RenderItemMixin"),
                "a bare simple name is not a key: it is unlisted, so it falls to mechanism");
    }

    @Test
    void ofStripsThePackagePrefixAndFallsBackToMechanism() {
        assertEquals("sky", PatchGroups.of("org.taumc.celeritas.mixin.core.ForgeCloudCellsMixin"));
        assertEquals("mechanism", PatchGroups.of("core.NotInTheTableMixin"),
                "an unlisted name falls to the group that is never skipped");
        assertEquals("mechanism", PatchGroups.of(null));
    }

    @Test
    void titlesAndCostsFallBackForUnknownGroups() {
        assertEquals("renderer core", PatchGroups.title("mechanism"));
        assertEquals("no-such-group", PatchGroups.title("no-such-group"));
        assertFalse(PatchGroups.cost("tweaks").isEmpty());
        assertEquals("", PatchGroups.cost("no-such-group"));
        assertTrue(PatchGroups.isKnown("tweaks"));
        assertFalse(PatchGroups.isKnown("no-such-group"));
        assertNull(PatchGroups.get("no-such-group"));
    }

    @Test
    void groupOrderIsTheTableOrder() {
        assertEquals(List.of("mechanism", "terrain_geometry", "leaf_culling", "entities", "textures", "model_phase_instrument",
                "pack_phase_instrument", "hud_census_instrument", "upstream_arrival_instrument", "text", "particles", "sky", "shaders", "tweaks"), PatchGroups.groups());
    }
}
