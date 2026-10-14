package org.soutalab.celtium.shaderpack;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds tiny synthetic shaderpacks (zip archives or extracted directories)
 * for tests. Everything is in-memory shaped - no fixture files are checked in.
 */
final class FixturePack {

    /**
     * Writes a zip. Keys are verbatim archive entry names - {@code ../} and
     * other hostile names are written as-is on purpose; the loader's job is to
     * reject them.
     */
    static Path writeZip(Path file, Map<String, ?> files) throws IOException {
        try (OutputStream os = Files.newOutputStream(file);
             ZipOutputStream zip = new ZipOutputStream(os)) {
            for (Map.Entry<String, ?> e : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                Object content = e.getValue();
                if (content != null) { // null means a directory entry
                    byte[] bytes = content instanceof byte[] b ? b
                            : content.toString().getBytes(StandardCharsets.UTF_8);
                    zip.write(bytes);
                }
                zip.closeEntry();
            }
        }
        return file;
    }

    /** Writes an extracted pack directory. All keys must be relative file names. */
    static Path writeDir(Path dir, Map<String, ?> files) throws IOException {
        for (Map.Entry<String, ?> e : files.entrySet()) {
            Path target = dir.resolve(e.getKey()).normalize();
            if (!target.startsWith(dir)) {
                throw new IllegalArgumentException("fixture path escapes dir: " + e.getKey());
            }
            if (e.getValue() == null) {
                Files.createDirectories(target);
                continue;
            }
            Files.createDirectories(target.getParent());
            byte[] bytes = e.getValue() instanceof byte[] b ? b
                    : e.getValue().toString().getBytes(StandardCharsets.UTF_8);
            Files.write(target, bytes);
        }
        return dir;
    }

    /** Fluent builder so tests read like the pack they describe. */
    static Builder pack() {
        return new Builder();
    }

    static final class Builder {
        private final Map<String, Object> files = new LinkedHashMap<>();

        Builder file(String path, String content) {
            files.put(path, content);
            return this;
        }

        Builder file(String path, byte[] content) {
            files.put(path, content);
            return this;
        }

        Builder dirEntry(String path) {
            files.put(path.endsWith("/") ? path : path + "/", null);
            return this;
        }

        /** The accumulated entry map - for tests that pass it to PackSource helpers. */
        Map<String, ?> files() {
            return java.util.Collections.unmodifiableMap(files);
        }

        Path zip(Path file) throws IOException {
            return FixturePack.writeZip(file, files);
        }

        Path dir(Path dir) throws IOException {
            return FixturePack.writeDir(dir, files);
        }
    }

    /** Minimal valid pack: one overworld composite pair and a shared include. */
    static Builder minimal() {
        return pack()
                .file("shaders/world0/composite.vsh",
                        """
                        #version 120
                        #define VSH
                        #include "/program/composite.glsl"
                        """)
                .file("shaders/world0/composite.fsh",
                        """
                        #version 120
                        #define FSH
                        #include "/program/composite.glsl"
                        """)
                .file("shaders/program/composite.glsl",
                        """
                        void main() {}
                        """)
                .file("shaders/shaders.properties",
                        "screen = composite\n");
    }
}
