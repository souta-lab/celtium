package org.soutalab.celtium.harness;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two harness file locations are one configuration in two places: the {@code config} subdirectory
 * is the base, the working directory's file overrides it key by key. The earlier first-file-wins load
 * silently dropped every key that lived only in the shadowed file.
 */
class HarnessConfigFileValuesTest {

    private static void write(Path file, String... lines) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, String.join("\n", lines));
    }

    @Test
    void workingDirectoryFileAloneIsRead() throws IOException {
        Path dir = Files.createTempDirectory("harness-config-test");
        write(dir.resolve("celtium-harness.properties"), "frameBudgetLines=2000");

        Properties values = HarnessConfig.FileValues.loadFrom(dir);

        assertEquals("2000", values.getProperty("frameBudgetLines"));
    }

    @Test
    void configSubdirectoryFileAloneIsRead() throws IOException {
        Path dir = Files.createTempDirectory("harness-config-test");
        write(dir.resolve("config").resolve("celtium-harness.properties"), "frameBudgetLines=3000");

        Properties values = HarnessConfig.FileValues.loadFrom(dir);

        assertEquals("3000", values.getProperty("frameBudgetLines"));
    }

    @Test
    void bothFilesMergeAndTheWorkingDirectoryFileWinsPerKey() throws IOException {
        Path dir = Files.createTempDirectory("harness-config-test");
        write(dir.resolve("config").resolve("celtium-harness.properties"),
                "frameBudgetLines=3000", "periodicLogs=false");
        write(dir.resolve("celtium-harness.properties"), "frameBudgetLines=2000");

        Properties values = HarnessConfig.FileValues.loadFrom(dir);

        assertEquals("2000", values.getProperty("frameBudgetLines"));
        // The key that lives only in the config file survives: this is the one the old load dropped.
        assertEquals("false", values.getProperty("periodicLogs"));
    }

    @Test
    void noFileIsAnEmptyConfiguration() throws IOException {
        Path dir = Files.createTempDirectory("harness-config-test");

        Properties values = HarnessConfig.FileValues.loadFrom(dir);

        assertTrue(values.isEmpty());
    }
}
