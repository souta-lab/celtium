package org.taumc.celeritas.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * The funnel summary's outlets, pinned at the source seam the offline suite can check.
 * <p>
 * The summary used to be printed only by a JVM shutdown hook in {@link CeltiumLightCache}, and in
 * the validation harness that print never reaches the captured console - by hook time the log
 * plumbing is already torn down - so the final counters a leg discriminates with (item 430's
 * {@code funnelRaised} among them) were exactly the values that went missing. The fix moved the
 * primary outlet into the game: a mixin on {@code Minecraft.loadWorld} prints at world exit, and
 * the funnel prints periodically while the run is alive. If either site is deleted, the counters
 * go back to being counted-but-unreadable - indistinguishable from a dead funnel - so both sites,
 * and the mixin's registration, are pinned here in the shape {@code WaitCensusCoverageTest}
 * established for the same family. The guard rules themselves are pinned as pure functions in
 * {@link CeltiumLightCacheFunnelTest}; the wiring's behaviour needs a running game and is covered
 * by the fixture legs.
 */
class CeltiumFunnelSummaryPrintSiteTest {

    @Test
    void worldExitPrintSiteExistsBesideTheShutdownHook() throws Exception {
        String cache = Files.readString(findRepoFile(
                "forge122/src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java",
                "src/main/java/org/taumc/celeritas/impl/CeltiumLightCache.java"));
        assertTrue(cache.contains("public static void printFunnelSummaryOnWorldExit()"),
                "the world-exit print exists as the funnel summary's primary outlet");
        assertTrue(cache.contains("funnelPeriodicPrintDue(funnelCalls, FUNNEL_PERIODIC_PRINT_INTERVAL)"),
                "the periodic in-run print stays wired inside the funnel");
        assertTrue(cache.contains("addShutdownHook"),
                "the JVM shutdown hook remains as the interactive last resort");
    }

    @Test
    void loadWorldMixinPrintsOnNullWorldOnly() throws Exception {
        String mixin = Files.readString(findRepoFile(
                "forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftFunnelSummaryMixin.java",
                "src/main/java/org/taumc/celeritas/mixin/core/MinecraftFunnelSummaryMixin.java"));
        assertTrue(mixin.contains("\"func_71403_a\""),
                "the one-argument loadWorld overload is hooked");
        assertTrue(mixin.contains("\"func_71353_a\""),
                "the two-argument loadWorld overload is hooked");
        assertTrue(mixin.contains("world == null"),
                "only a null world (exit) prints; assigning a world does not");
        assertTrue(mixin.contains("CeltiumLightCache.printFunnelSummaryOnWorldExit()"),
                "the mixin calls the funnel's world-exit print");
    }

    @Test
    void theMixinIsRegisteredInThePatchGroupTable() throws Exception {
        String groups = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchGroups.java",
                "../celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchGroups.java"));
        assertTrue(groups.contains("\"core.MinecraftFunnelSummaryMixin\""),
                "the mixin is registered in the patch-group table the plugin reads");
    }

    /** Test working directories differ between the module gate and a repo-root run; both shapes are tried. */
    private static Path findRepoFile(String repoRelative, String moduleRelative) {
        for (String candidate : new String[] {repoRelative, moduleRelative, "../" + repoRelative}) {
            Path path = Paths.get(candidate);
            if (Files.exists(path)) {
                return path;
            }
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " from "
                + Paths.get("").toAbsolutePath());
    }
}
