package org.soutalab.celtium.shaderpack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A read-only, fully snapshotted view of a shaderpack's {@code shaders/}
 * subtree. Implementations copy the relevant bytes into memory at open time
 * (bounded by {@link PackLimits}), so a pack cannot change under the loader and
 * every read after {@link #files()} is deterministic.
 *
 * <p>Only files under {@code shaders/} are visible to the frontend; anything
 * else in the container is counted as {@link #skippedOutsideShaders()}.</p>
 */
public interface PackSource {

    /** All regular files under {@code shaders/}, canonical paths, sorted. */
    List<PackPath> files();

    /** Whether {@code path} is a regular file in the snapshot. */
    boolean exists(PackPath path);

    /** Reads a previously listed file. Content was already validated at open. */
    byte[] read(PackPath path) throws PackException;

    /** Display name for diagnostics - the archive or directory file name. */
    String label();

    /** Files seen outside {@code shaders/} and ignored. */
    int skippedOutsideShaders();

    /**
     * Opens a shaderpack that is either a zip archive or a directory whose root
     * contains {@code shaders/}.
     *
     * @throws PackException for anything that makes the container untrustworthy
     *         or unreadable (traversal entries, bombs, symlink escapes, I/O)
     */
    static PackSource open(Path input, PackLimits limits, List<Diagnostic> diagnostics) throws PackException {
        try {
            if (Files.isDirectory(input)) {
                return new DirectoryPackSource(input, limits, diagnostics);
            }
            if (Files.isRegularFile(input)) {
                return new ZipPackSource(input, limits, diagnostics);
            }
        } catch (IOException e) {
            throw new PackException(DiagnosticCode.IO_ERROR, "cannot read " + input + ": " + e, e);
        }
        throw new PackException(DiagnosticCode.IO_ERROR, "not a file or directory: " + input);
    }

    /** Map view used internally by the loader. Keys are sorted (TreeMap). */
    static PackSource of(String label, Map<PackPath, byte[]> snapshot, int skipped) {
        TreeMap<PackPath, byte[]> ordered = new TreeMap<>(snapshot);
        return new PackSource() {
            @Override
            public List<PackPath> files() {
                return List.copyOf(ordered.keySet());
            }

            @Override
            public boolean exists(PackPath path) {
                return ordered.containsKey(path);
            }

            @Override
            public byte[] read(PackPath path) throws PackException {
                byte[] content = ordered.get(path);
                if (content == null) {
                    throw new PackException(DiagnosticCode.IO_ERROR, "no such file in pack: " + path);
                }
                return content;
            }

            @Override
            public String label() {
                return label;
            }

            @Override
            public int skippedOutsideShaders() {
                return skipped;
            }
        };
    }
}
