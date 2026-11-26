package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contracts for the Tier-A per-pass ignition counters (residuals R-21): every terrain pass that
 * reaches the Tier-A arming decision in {@code recordRenderPass} counts one attempt on its own
 * fallback path ({@code terrain.engine.<pass name>}), verdicted fast when the Tier-A engine
 * records the pass and fallback when the legacy loop does. The decision point lives inside a
 * device-bound method - the pass head dispatches real cull compute - so it cannot be driven on
 * a CPU-only instance; the wiring is pinned by source contracts in the shape
 * {@code FallbackWiringContractTest} established, and the per-pass path names are pinned
 * behaviourally through the registry itself.
 */
class TierAPassIgnitionContractTest {

    private static final String BACKEND =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java";

    @BeforeEach
    void clearRegistry() {
        FallbackPaths.clearForTests();
    }

    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative));
    }

    private static Path findRepoFile(String repoRelative) {
        Path direct = Paths.get(repoRelative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromModule = Paths.get("..").resolve(repoRelative);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " from "
                + Paths.get("").toAbsolutePath());
    }

    private static int count(String text, String needle) {
        int found = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            found++;
            from += needle.length();
        }
        return found;
    }

    /**
     * The per-pass paths hang off the existing {@code terrain.engine} standard name, one counter
     * set per pass name, independent of each other: the summary's fastRate is per pass, which is
     * the number the overhaul guardrail watches (a pass the new engine never ignites on must not
     * hide inside an all-pass average).
     */
    @Test
    void perPassPathsRegisterIndependentlyUnderTheTerrainEngineStem() {
        assertEquals("terrain.engine", FallbackPaths.TERRAIN_ENGINE);

        FallbackPaths.Handle solid = FallbackPaths.register(FallbackPaths.TERRAIN_ENGINE + ".solid");
        solid.attempt();
        solid.verdict(true);
        solid.attempt();
        solid.verdict(true);
        solid.attempt();
        solid.verdict(false);
        FallbackPaths.Handle translucent =
                FallbackPaths.register(FallbackPaths.TERRAIN_ENGINE + ".translucent");
        translucent.attempt();
        translucent.verdict(false);

        assertEquals(3L, solid.attempts());
        assertEquals(2L, solid.fastHits());
        assertEquals(1L, solid.fallbacks());
        assertEquals(1L, translucent.attempts());
        assertEquals(0L, translucent.fastHits());
        assertEquals(1L, translucent.fallbacks());

        // Registering the same per-pass name again resolves the same shared counts.
        assertEquals(3L, FallbackPaths.register("terrain.engine.solid").attempts());

        String summary = FallbackPaths.summary();
        assertTrue(summary.contains("terrain.engine.solid:attempts=3,fast=2,fallback=1,fastRate=0.667"),
                summary);
        assertTrue(summary.contains(
                "terrain.engine.translucent:attempts=1,fast=0,fallback=1,fastRate=0.000"), summary);
    }

    /**
     * The arming decision is the single {@code tierAArmed} computation in {@code recordRenderPass}:
     * the attempt and its verdict are counted exactly once, immediately after the decision is
     * fixed, and the pack route's early return stays before the decision point - a pack-routed
     * pass never reaches this counter, so its attempts cannot show up here verdict-less.
     */
    @Test
    void passHeadCountsOneAttemptAndVerdictsOnTheArmingDecision() throws IOException {
        String backend = read(BACKEND);

        assertEquals(1, count(backend, "terrainEnginePassPath(pass.name())"), "one handle lookup");
        assertEquals(1, count(backend, "terrainEnginePath.attempt()"), "one attempt site");
        assertEquals(1, count(backend, "terrainEnginePath.verdict(tierAArmed)"), "one verdict site");

        int packRoute = backend.indexOf("celtium$tryRenderPackTerrainPass");
        int decision = backend.indexOf("boolean tierAArmed = tierAEngine != null;");
        int attempt = backend.indexOf("terrainEnginePath.attempt()");
        int verdict = backend.indexOf("terrainEnginePath.verdict(tierAArmed)");
        assertTrue(packRoute >= 0 && decision > packRoute,
                "the pack route is answered before the arming decision is reached");
        assertTrue(attempt > decision && verdict > attempt,
                "attempt then verdict, both immediately after the arming decision is fixed");
    }

    /**
     * Handles are held per pass name in a static cache, each registered from the
     * {@code terrain.engine} stem plus the pass name - the wiring site never re-registers, and
     * no second registry appears beside {@link FallbackPaths}' own map.
     */
    @Test
    void perPassHandlesAreCachedAndRegisteredFromTheStandardStem() throws IOException {
        String backend = read(BACKEND);

        assertTrue(backend.contains(
                        "private static final java.util.concurrent.ConcurrentHashMap<String, FallbackPaths.Handle>"),
                "the per-pass handles live in one static cache");
        assertTrue(backend.contains("TERRAIN_ENGINE_PASS_PATHS.computeIfAbsent(passName,"),
                "a pass name resolves its handle once through the cache");
        assertTrue(backend.contains(
                        "FallbackPaths.register(FallbackPaths.TERRAIN_ENGINE + \".\" + name)"),
                "each cached handle is registered from the terrain.engine stem plus the pass name");
        assertTrue(backend.contains(
                        "private static FallbackPaths.Handle terrainEnginePassPath(String passName)"),
                "the lookup helper is the single registration site");
    }
}
