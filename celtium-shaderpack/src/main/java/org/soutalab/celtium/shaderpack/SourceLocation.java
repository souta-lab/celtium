package org.soutalab.celtium.shaderpack;

/**
 * Where inside a pack something happened.
 *
 * @param path canonical in-pack path (always starts with {@code /}, rooted at
 *             the pack root - i.e. the parent of {@code shaders/})
 * @param line 1-based line number, or -1 when the diagnostic is not line-bound
 */
public record SourceLocation(String path, int line) implements Comparable<SourceLocation> {

    public SourceLocation {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("path must be a non-empty canonical pack path");
        }
    }

    public static SourceLocation file(String path) {
        return new SourceLocation(path, -1);
    }

    @Override
    public int compareTo(SourceLocation other) {
        int byPath = path.compareTo(other.path);
        return byPath != 0 ? byPath : Integer.compare(line, other.line);
    }

    @Override
    public String toString() {
        return line >= 0 ? path + ":" + line : path;
    }
}
