package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bound-target terrain route's default: on, with {@code -Dceltium.vulkan.terrainToBoundTarget=false} as the
 * only opt-out (item 644).
 * <p>
 * Item 384's bloom-halo verdict was routing/timing, in two layers. Inside the route, the deferred pair clear
 * landed after the terrain pass and erased the mask the pass had drawn; that is fixed (30854465a consumes the
 * owed clear before the pass for both shapes of pair depth, abf094ae2 acquires the pair whole - neither was in
 * the verdict leg's build). The second layer is this one: the route itself was opt-in
 * ({@code Boolean.getBoolean}, default false), so in the default build the bloom layer's terrain still drew into
 * the game's framebuffer and the mod's bloom framebuffer stayed empty - item 343's R3 named the two gates
 * explicitly. The chain's own switch had already graduated to the mod-programs rule (on unless the value reads
 * "false"); this pins the terrain route to the same rule, in the value table and in the field's wiring.
 * <p>
 * What this proves: the parse rule and the call site's shape. It does not prove a frame's mask reaches the
 * chain's sample; that is the acceptance leg's presented-image measurement, run with no switch set.
 */
class BoundTargetRouteDefaultTest {

    /** Absent or affirmative values leave the route on: the default build routes. */
    @Test
    void theRouteIsOnUnlessTheValueReadsFalse() {
        assertTrue(PackTerrainJudgments.celtium$terrainToBoundTargetEnabled(null), "absent");
        assertTrue(PackTerrainJudgments.celtium$terrainToBoundTargetEnabled("true"), "explicit on");
        assertTrue(PackTerrainJudgments.celtium$terrainToBoundTargetEnabled(""), "empty");
        assertTrue(PackTerrainJudgments.celtium$terrainToBoundTargetEnabled("0"),
                "only the literal \"false\" opts out, the mod-programs rule");
        assertTrue(PackTerrainJudgments.celtium$terrainToBoundTargetEnabled("off"),
                "only the literal \"false\" opts out, the mod-programs rule");
    }

    /** The opt-out is the literal value, case-insensitively. */
    @Test
    void theOptOutIsTheLiteralFalse() {
        assertFalse(PackTerrainJudgments.celtium$terrainToBoundTargetEnabled("false"));
        assertFalse(PackTerrainJudgments.celtium$terrainToBoundTargetEnabled("FALSE"));
        assertFalse(PackTerrainJudgments.celtium$terrainToBoundTargetEnabled("False"));
    }

    /** The wiring: the field reads the property through the pure rule with "true" as the absent value. */
    @Test
    void theFieldReadsThePropertyThroughTheRule() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"));
        assertTrue(source.contains(
                        "celtium$terrainToBoundTargetEnabled(System.getProperty("
                                + "\"celtium.vulkan.terrainToBoundTarget\", \"true\"))"),
                "the field is initialised through the pure rule with the default on");
        assertFalse(source.contains("Boolean.getBoolean(\"celtium.vulkan.terrainToBoundTarget\")"),
                "the opt-in read is gone: Boolean.getBoolean defaults to false, which is the gate item 644 removes");
    }

    /** Test working directories differ between the module gate and a repo-root run; both shapes are tried. */
    private static Path findRepoFile(String repoRelative) {
        Path direct = Paths.get(repoRelative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromModule = Paths.get("..").resolve(repoRelative);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " from " + Paths.get("").toAbsolutePath());
    }
}
