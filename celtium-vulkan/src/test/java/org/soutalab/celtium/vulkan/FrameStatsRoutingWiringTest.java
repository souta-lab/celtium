package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The frame-stats switch's routing, pinned by source reading in the house style
 * (see {@link BindlessSlotDumpWiringTest}): the switch resolves in a backend static
 * initialiser a device-less test cannot execute, so the wiring is what a test can hold.
 * <p>
 * The switch used to be the repeating instruments' exception - a raw
 * {@code Boolean.getBoolean("celtium.vulkan.frameStats")} read that never consulted
 * {@code HarnessConfig}, so naming {@code frameStats} in the harness file did nothing
 * and the audit runs that set it there printed no CELTIUM_FRAME_STATS lines at all.
 * Through {@code HarnessConfig.bool("frameStats", false)} the file's entry is the
 * answer, which is what "the harness file turns the instrument on" means here; that
 * the file layer and the legacy command-line layer of {@code HarnessConfig} resolve
 * as documented is pinned behaviourally in the common module
 * ({@code HarnessConfigFileValuesTest}, {@code HarnessConfigLegacyPropertyTest}).
 */
class FrameStatsRoutingWiringTest {

    private static final String BACKEND =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java";
    private static final String REPORT =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameStatsReport.java";

    @Test
    @DisplayName("the FRAME_STATS switch resolves through HarnessConfig under the file's key")
    void switchResolvesThroughHarnessConfig() throws IOException {
        assertTrue(read(BACKEND).contains(
                        "static final boolean FRAME_STATS = HarnessConfig.bool(\"frameStats\", false);"),
                "FRAME_STATS is HarnessConfig.bool(\"frameStats\", false), so the harness file's"
                        + " frameStats=true is what turns the report on");
    }

    @Test
    @DisplayName("no raw system-property read of the frameStats spelling survives in the backend or the report")
    void noRawPropertyReadSurvives() throws IOException {
        for (String source : new String[] {read(BACKEND), read(REPORT)}) {
            assertFalse(source.contains("Boolean.getBoolean(\"celtium.vulkan.frameStats\")"),
                    "the raw -D-only read is gone: it could not see the harness file at all");
            assertFalse(source.contains("System.getProperty(\"celtium.vulkan.frameStats\")"),
                    "no direct property read of the spelling survives either");
        }
    }

    @Test
    @DisplayName("the consumers still gate on the one switch")
    void consumersStillGateOnTheSwitch() throws IOException {
        assertTrue(read(BACKEND).contains("if (!FRAME_STATS)"),
                "the frame-boundary note still returns early while the switch is off");
        assertTrue(read(REPORT).contains("VulkanTerrainGpuBackend.FRAME_STATS"),
                "the report still asks the same switch for its game-loop section");
    }

    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative)).replace("\r\n", "\n");
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
        return direct;
    }
}
