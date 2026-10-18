package org.soutalab.celtium.shaderpack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.TreeMap;

/**
 * Snapshots the {@code shaders/} subtree of an extracted pack directory.
 *
 * <p>Safety: the tree is walked without following links and every file is
 * additionally verified by {@code toRealPath} containment, so a symlink inside
 * the pack that points outside is rejected rather than read. Anything that is
 * not a regular file or directory (fifos, devices, sockets) is rejected too.</p>
 */
final class DirectoryPackSource implements PackSource {

    private final PackSource delegate;

    DirectoryPackSource(Path root, PackLimits limits, List<Diagnostic> diagnostics)
            throws PackException, IOException {
        Path realRoot = root.toRealPath();
        Path shadersDir = realRoot.resolve("shaders");
        if (!Files.isDirectory(shadersDir)) {
            throw new PackException(DiagnosticCode.NO_SHADER_ROOT,
                    "directory has no shaders/ subdirectory: " + root);
        }
        Path shadersReal = shadersDir.toRealPath();

        TreeMap<PackPath, byte[]> snapshot = new TreeMap<>();
        int[] entries = {0};
        long[] totalBytes = {0};

        try {
            Files.walkFileTree(shadersReal, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String display = "/" + realRoot.relativize(file).toString().replace('\\', '/');
                if (attrs.isSymbolicLink()) {
                    // Links are never followed (NOFOLLOW walk). Where the link
                    // would land decides the verdict: outside shaders/ is an
                    // escape attempt -> error; inside is tolerated but skipped.
                    Path real;
                    try {
                        real = file.toRealPath();
                    } catch (java.nio.file.NoSuchFileException dangling) {
                        diagnostics.add(Diagnostic.warning(DiagnosticCode.SYMLINK_ESCAPE,
                                SourceLocation.file(display), "dangling symlink skipped"));
                        return FileVisitResult.CONTINUE;
                    }
                    if (!real.startsWith(shadersReal)) {
                        throw fail(new PackException(DiagnosticCode.SYMLINK_ESCAPE,
                                "symlink escapes shaders/: " + display));
                    }
                    diagnostics.add(Diagnostic.warning(DiagnosticCode.SYMLINK_ESCAPE,
                            SourceLocation.file(display), "symlinked file skipped (links are not followed)"));
                    return FileVisitResult.CONTINUE;
                }
                if (++entries[0] > limits.maxEntries()) {
                    throw fail(new PackException(DiagnosticCode.ENTRY_LIMIT_EXCEEDED,
                            "pack has more than " + limits.maxEntries() + " files"));
                }
                if (!attrs.isRegularFile()) {
                    throw fail(new PackException(DiagnosticCode.NOT_REGULAR_FILE,
                            "not a regular file: " + display));
                }
                long size = attrs.size();
                if (size > limits.maxFileBytes()) {
                    throw fail(new PackException(DiagnosticCode.FILE_LIMIT_EXCEEDED,
                            display + " is " + size + " bytes, limit is " + limits.maxFileBytes()));
                }
                PackPath path;
                try {
                    path = PackPath.absolute(display);
                } catch (PackException e) {
                    throw fail(e);
                }
                byte[] content;
                try (InputStream in = Files.newInputStream(file)) {
                    content = readBounded(in, limits.maxFileBytes(), path);
                }
                totalBytes[0] += content.length;
                if (totalBytes[0] > limits.maxTotalUncompressedBytes()) {
                    throw fail(new PackException(DiagnosticCode.TOTAL_SIZE_LIMIT_EXCEEDED,
                            "pack expands past " + limits.maxTotalUncompressedBytes() + " bytes"));
                }
                snapshot.put(path, content);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                // Directories count toward the entry budget, like the ZIP path counts directory entries
                // (audit #351): a pack of nested empty directories used to walk unbounded without ever
                // tripping ENTRY_LIMIT_EXCEEDED. Symlinked dirs count too - they are still visits.
                if (++entries[0] > limits.maxEntries()) {
                    throw fail(new PackException(DiagnosticCode.ENTRY_LIMIT_EXCEEDED,
                            "pack has more than " + limits.maxEntries() + " entries"));
                }
                // A symlinked directory inside the pack is skipped, not followed -
                // with NOFOLLOW_LINKS attrs describes the link itself.
                if (attrs.isSymbolicLink()) {
                    diagnostics.add(Diagnostic.warning(DiagnosticCode.SYMLINK_ESCAPE,
                            SourceLocation.file("/" + realRoot.relativize(dir).toString().replace('\\', '/')),
                            "symlinked directory skipped (links are not followed)"));
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            private byte[] readBounded(InputStream in, long limit, PackPath path) throws IOException {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                long read = 0;
                int n;
                while ((n = in.read(buffer)) >= 0) {
                    read += n;
                    if (read > limit) {
                        throw fail(new PackException(DiagnosticCode.FILE_LIMIT_EXCEEDED,
                                path + " exceeds " + limit + " bytes while reading"));
                    }
                    out.write(buffer, 0, n);
                }
                return out.toByteArray();
            }

            /** {@code PackException} is checked and cannot leave the visitor API, so it travels as an IOException cause. */
            private IOException fail(PackException e) {
                return new IOException(e);
            }
            });
        } catch (IOException e) {
            if (e.getCause() instanceof PackException pe) {
                throw pe;
            }
            throw e;
        }

        this.delegate = PackSource.of(root.getFileName().toString(), snapshot, 0);
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
