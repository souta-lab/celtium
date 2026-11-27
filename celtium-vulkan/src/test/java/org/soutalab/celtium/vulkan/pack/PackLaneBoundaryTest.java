package org.soutalab.celtium.vulkan.pack;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The D-V040-2 boundary, mechanically: the pack engine (the shader interpretation tree and
 * the Vulkan pack executor) never references the Lane 3 converter tree. Semantics may be
 * shared by contract; code and execution paths may not (weather contract ruling section 3).
 */
class PackLaneBoundaryTest {

    @Test
    void packEngineSourcesNeverReferenceTheConverterTree() throws IOException {
        Path root = sourceRoot();
        List<Path> trees = List.of(
                root.resolve("org/soutalab/celtium/shader"),
                root.resolve("org/soutalab/celtium/vulkan/pack"));
        List<String> offenders = new ArrayList<>();
        for (Path tree : trees) {
            try (Stream<Path> walk = Files.walk(tree)) {
                for (Path file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(file);
                    if (source.contains("org.soutalab.celtium.glcompat")
                            || source.contains("celtium/glcompat")) {
                        offenders.add(root.relativize(file).toString());
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "pack engine sources referencing the Lane 3 converter tree: " + offenders);
    }

    private static Path sourceRoot() {
        Path direct = Path.of("src/main/java");
        if (Files.isDirectory(direct)) {
            return direct;
        }
        return Path.of("celtium-vulkan").resolve(direct);
    }
}
