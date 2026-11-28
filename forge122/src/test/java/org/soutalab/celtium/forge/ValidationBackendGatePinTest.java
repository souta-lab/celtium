package org.soutalab.celtium.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * The validation fixture's live-backend gate, pinned at the source seam the offline suite can check.
 * <p>
 * {@code ValidationClient.celtium$vulkanBackend()} finds the live Vulkan backend by comparing the
 * installed instance's class name against a string. The string form is deliberate: the fixture is the
 * {@code @Mod} class, and naming the backend type in a signature made it unloadable in the
 * no-Celtium-jar reference configuration (the {@code NoClassDefFoundError} its javadoc records), so
 * the gate must not be "fixed" back into a compile-time reference. The cost of the shape is that a
 * rename on the production side silently stale-dates the literal - which is what happened when the
 * monolith {@code VulkanTerrainGpuBackend} was dismantled: the gate compared against the deleted
 * class, never matched, and every fixture leg since has reported an artificial FAILED status, a
 * FAILED_PRESENTED_IMAGE verdict and dead presented readings while the actual rendering was fine.
 * These pins tie the literal to the class the production side really installs, so the next rename
 * fails here instead of in a leg report. The gate's behaviour in a running game is covered by the
 * fixture legs (the bootstrap agreement line in the leg report is the live check).
 */
class ValidationBackendGatePinTest {

    private static final String VALIDATION_CLIENT =
            "validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java";
    private static final String BACKEND_ADAPTER =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanBackendAdapter.java";
    private static final String FRAME_COORDINATOR =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java";

    @Test
    void theGateNamesTheInstalledBackendClass() throws Exception {
        String fixture = Files.readString(findRepoFile(VALIDATION_CLIENT, "../" + VALIDATION_CLIENT));
        assertTrue(fixture.contains(
                "\"org.soutalab.celtium.vulkan.VulkanBackendAdapter\".equals(current.getClass().getName())"),
                "the gate compares the live backend's class name against the installed adapter class");
        assertEquals(1, countOccurrences(fixture, "org.soutalab.celtium.vulkan.VulkanBackendAdapter"),
                "the adapter's class name appears exactly once in the fixture: the gate literal");
    }

    @Test
    void theGateKeepsTheStringShapeAndDropsTheDeletedMonolith() throws Exception {
        String fixture = Files.readString(findRepoFile(VALIDATION_CLIENT, "../" + VALIDATION_CLIENT));
        assertFalse(fixture.contains("\"org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend\""),
                "the deleted monolith's class name is no longer a compared literal "
                        + "(its javadoc mentions are history, not the gate)");
        assertFalse(fixture.contains("import org.soutalab.celtium.vulkan.VulkanBackendAdapter;"),
                "the gate stays a string comparison; the adapter type is not compile-linked into the fixture");
    }

    @Test
    void theInstalledBackendIsTheNamedClass() throws Exception {
        String adapter = Files.readString(findRepoFile(BACKEND_ADAPTER, "../" + BACKEND_ADAPTER));
        assertTrue(adapter.contains("package org.soutalab.celtium.vulkan;"),
                "the adapter lives in the package the gate literal names");
        assertTrue(adapter.contains("public final class VulkanBackendAdapter"),
                "the class the gate literal names is the production backend adapter");
        String coordinator = Files.readString(findRepoFile(FRAME_COORDINATOR, "../" + FRAME_COORDINATOR));
        assertTrue(coordinator.contains("VulkanBackendAdapter owner;"),
                "the coordinator's installed owner is the adapter");
        assertTrue(coordinator.contains("CeltiumBackends.install(this.owner)"),
                "the adapter is what CeltiumBackends.current() hands the fixture at runtime");
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
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
