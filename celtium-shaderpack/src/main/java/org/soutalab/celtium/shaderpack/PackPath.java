package org.soutalab.celtium.shaderpack;

/**
 * A canonical, normalized path <em>inside</em> a shaderpack. Always absolute:
 * {@code /} is the pack root (the directory that contains {@code shaders/}),
 * and every stored form starts with {@code /}. There is no way to construct a
 * PackPath that refers outside the root - {@code ..} segments that would escape
 * are rejected, never silently clamped.
 *
 * <p>This is deliberately a small JDK-only reimplementation of the semantics
 * Iris gives to {@code AbsolutePackPath}, plus strict rejection instead of
 * normalization for escapes, because a hostile pack must not be able to reach
 * {@code ../../..} at all.</p>
 */
public final class PackPath implements Comparable<PackPath> {

    private final String path;

    private PackPath(String canonical) {
        this.path = canonical;
    }

    /** The pack root itself. */
    public static PackPath root() {
        return new PackPath("/");
    }

    /**
     * Parses an absolute in-pack path ({@code "/shaders/lib/x.glsl"}).
     *
     * @throws PackException if the path is not absolute or escapes the root
     */
    public static PackPath absolute(String raw) throws PackException {
        if (raw == null || !raw.startsWith("/")) {
            throw new PackException(DiagnosticCode.MALFORMED_ENTRY_PATH,
                    "not an absolute pack path: " + raw);
        }
        return new PackPath(canonicalize(raw));
    }

    /**
     * {@link #absolute} for compile-time constants that cannot fail -
     * rethrows as {@link IllegalStateException} if one somehow does.
     */
    static PackPath absoluteUnchecked(String raw) {
        try {
            return absolute(raw);
        } catch (PackException e) {
            throw new IllegalStateException("constant path must canonicalize: " + raw, e);
        }
    }

    /**
     * Resolves an include-style path against {@code base}: a leading {@code /}
     * (or an angle-bracket include, already normalized to a leading slash by the
     * caller) is pack-root-relative, anything else is relative to {@code base}'s
     * directory.
     */
    public static PackPath resolve(PackPath baseFile, String target) throws PackException {
        String combined;
        if (target.startsWith("/")) {
            combined = target;
        } else {
            String base = baseFile.path;
            int lastSlash = base.lastIndexOf('/');
            // baseFile is always a file: everything up to and including the last '/'
            String dir = lastSlash <= 0 ? "/" : base.substring(0, lastSlash + 1);
            combined = dir + target;
        }
        return new PackPath(canonicalize(combined));
    }

    /**
     * Normalizes {@code .}/{@code ..}/duplicate separators/backslashes. A
     * {@code ..} that would climb above the root is rejected outright rather
     * than collapsed - a pack asking for it is malformed or hostile either way.
     */
    private static String canonicalize(String raw) throws PackException {
        if (raw.indexOf('\0') >= 0) {
            throw new PackException(DiagnosticCode.MALFORMED_ENTRY_PATH, "NUL byte in path");
        }
        String unix = raw.replace('\\', '/');
        String[] segments = unix.split("/");
        java.util.ArrayDeque<String> stack = new java.util.ArrayDeque<>();
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (stack.isEmpty()) {
                    throw new PackException(DiagnosticCode.ENTRY_TRAVERSAL_REJECTED,
                            "path escapes the pack root: " + raw);
                }
                stack.removeLast();
                continue;
            }
            stack.addLast(segment);
        }
        if (stack.isEmpty()) {
            return "/";
        }
        StringBuilder out = new StringBuilder(unix.length());
        for (String segment : stack) {
            out.append('/').append(segment);
        }
        return out.toString();
    }

    /** The final path segment, e.g. {@code gbuffers_terrain.vsh}. */
    public String name() {
        int slash = path.lastIndexOf('/');
        return path.substring(slash + 1);
    }

    /** {@code /a/b/c.txt} → {@code /a/b}; the parent's path of {@code /x} is {@code /}. */
    public PackPath parent() {
        int slash = path.lastIndexOf('/');
        if (slash <= 0) {
            return root();
        }
        return new PackPath(path.substring(0, slash));
    }

    public boolean isUnder(String directoryPrefix) {
        return path.startsWith(directoryPrefix);
    }

    @Override
    public String toString() {
        return path;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PackPath other && path.equals(other.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public int compareTo(PackPath other) {
        return path.compareTo(other.path);
    }
}
