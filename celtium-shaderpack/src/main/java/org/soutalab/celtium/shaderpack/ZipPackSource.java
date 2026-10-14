package org.soutalab.celtium.shaderpack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Snapshots the {@code shaders/} subtree of a zip archive.
 *
 * <p>Hardening, all enforced at open so a hostile archive fails fast and
 * deterministically:</p>
 * <ul>
 *   <li>entry names are canonicalized through {@link PackPath} - anything with a
 *       {@code ..} escaping the root, an absolute path, a drive-letter prefix or
 *       a NUL byte aborts the load instead of being silently skipped;</li>
 *   <li>entry count, per-file size, total uncompressed size and compression
 *       ratio are bounded by {@link PackLimits};</li>
 *   <li>encrypted entries and duplicate canonical names are rejected.</li>
 * </ul>
 */
final class ZipPackSource implements PackSource {

    private final PackSource delegate;

    ZipPackSource(Path zipPath, PackLimits limits, List<Diagnostic> diagnostics)
            throws PackException, IOException {
        TreeMap<PackPath, byte[]> snapshot = new TreeMap<>();
        int skipped = 0;
        long totalBytes = 0;
        int entries = 0;

        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            // First scan names only to locate shaders/: most packs have it at the
            // archive root, some ship <PackName>/shaders/ one level down. A
            // single nested root is accepted with a warning; several different
            // ones make the archive ambiguous and it is left rootless.
            String prefix = shadersPrefix(zip);
            if (!prefix.isEmpty()) {
                diagnostics.add(Diagnostic.warning(DiagnosticCode.NESTED_SHADERS_ROOT,
                        SourceLocation.file(zipPath.getFileName().toString()),
                        "shaders/ is nested under '" + prefix
                                + "' - accepted, but packs should keep shaders/ at the archive root"));
            }

            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry entry = en.nextElement();
                entries++;
                if (entries > limits.maxEntries()) {
                    throw new PackException(DiagnosticCode.ENTRY_LIMIT_EXCEEDED,
                            "archive has more than " + limits.maxEntries() + " entries");
                }
                if (entry.isDirectory()) {
                    continue;
                }

                String rawName = entry.getName();
                if (!prefix.isEmpty() && !rawName.startsWith(prefix)) {
                    skipped++;
                    continue;
                }
                // Windows-style absolute paths and drive letters never survive canonicalization cleanly.
                if (rawName.length() >= 2 && rawName.charAt(1) == ':') {
                    throw new PackException(DiagnosticCode.ENTRY_TRAVERSAL_REJECTED,
                            "drive-letter entry name: " + rawName);
                }
                PackPath path = PackPath.absolute("/" + rawName.substring(prefix.length()));

                if (!path.isUnder("/shaders/")) {
                    skipped++;
                    continue;
                }

                if ((entry.getMethod() == ZipEntry.DEFLATED || entry.getMethod() == ZipEntry.STORED)
                        && entry.getSize() >= 0) {
                    long size = entry.getSize();
                    if (size > limits.maxFileBytes()) {
                        throw new PackException(DiagnosticCode.FILE_LIMIT_EXCEEDED,
                                path + " expands to " + size + " bytes, limit is " + limits.maxFileBytes());
                    }
                    long compressed = entry.getCompressedSize();
                    if (size >= limits.minBytesForRatioCheck() && compressed > 0
                            && (double) size / (double) compressed > limits.maxCompressionRatio()) {
                        throw new PackException(DiagnosticCode.SUSPICIOUS_COMPRESSION,
                                path + " expands " + compressed + " -> " + size + " bytes");
                    }
                    // Reject oversized archives before inflating where the ZIP directory advertises sizes.
                    // Verify the real read length as well: malformed archives may advertise unknown sizes.
                    if (size > limits.maxTotalUncompressedBytes() - totalBytes) {
                        throw new PackException(DiagnosticCode.TOTAL_SIZE_LIMIT_EXCEEDED,
                                "archive expands past " + limits.maxTotalUncompressedBytes() + " bytes");
                    }
                }

                byte[] content;
                try (InputStream in = zip.getInputStream(entry)) {
                    content = readBounded(in, limits.maxFileBytes(), path);
                } catch (java.util.zip.ZipException encrypted) {
                    // java.util.zip does not expose the entry flags word; an
                    // encrypted entry fails here instead.
                    if (encrypted.getMessage() != null
                            && encrypted.getMessage().toLowerCase(java.util.Locale.ROOT).contains("encrypt")) {
                        throw new PackException(DiagnosticCode.ENCRYPTED_ENTRY,
                                "encrypted entry: " + path, encrypted);
                    }
                    throw encrypted;
                }
                totalBytes += content.length;
                if (totalBytes > limits.maxTotalUncompressedBytes()) {
                    throw new PackException(DiagnosticCode.TOTAL_SIZE_LIMIT_EXCEEDED,
                            "archive expands past " + limits.maxTotalUncompressedBytes() + " bytes");
                }
                if (snapshot.put(path, content) != null) {
                    throw new PackException(DiagnosticCode.DUPLICATE_ENTRY,
                            "duplicate canonical entry: " + path);
                }
            }
        }

        if (entries == 0) {
            throw new PackException(DiagnosticCode.EMPTY_ARCHIVE, "archive has no entries: " + zipPath);
        }
        this.delegate = PackSource.of(zipPath.getFileName().toString(), snapshot, skipped);
    }

    /**
     * Returns {@code ""} when {@code shaders/} sits at the archive root, or the
     * single top-level prefix {@code "<dir>/"} when every shaders tree is
     * nested under the same one directory. Ambiguous nesting yields {@code ""}
     * too (the archive then just has no usable root and the loader reports it).
     */
    private static String shadersPrefix(ZipFile zip) {
        Enumeration<? extends ZipEntry> en = zip.entries();
        String nested = null;
        boolean ambiguous = false;
        while (en.hasMoreElements()) {
            String name = en.nextElement().getName();
            if (name.startsWith("shaders/")) {
                return ""; // root-level shaders/ wins outright
            }
            int slash = name.indexOf('/');
            if (slash > 0 && name.startsWith("shaders/", slash + 1)) {
                String candidate = name.substring(0, slash + 1);
                if (nested == null) {
                    nested = candidate;
                } else if (!nested.equals(candidate)) {
                    ambiguous = true;
                }
            }
        }
        return ambiguous || nested == null ? "" : nested;
    }

    private static byte[] readBounded(InputStream in, long limit, PackPath path) throws IOException, PackException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long read = 0;
        int n;
        while ((n = in.read(buffer)) >= 0) {
            read += n;
            if (read > limit) {
                throw new PackException(DiagnosticCode.FILE_LIMIT_EXCEEDED,
                        path + " exceeds " + limit + " bytes while reading");
            }
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    @Override
    public List<PackPath> files() {
        return delegate.files();
    }

    @Override
    public boolean exists(PackPath path) {
        return delegate.exists(path);
    }

    @Override
    public byte[] read(PackPath path) throws PackException {
        return delegate.read(path);
    }

    @Override
    public String label() {
        return delegate.label();
    }

    @Override
    public int skippedOutsideShaders() {
        return delegate.skippedOutsideShaders();
    }
}
