package org.soutalab.celtium.shaderpack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Container hardening: hostile zips and directory trees must fail or be contained. */
class PackSourceTest {

    @TempDir
    Path tmp;

    private PackSource openZip(Map<String, ?> files) throws IOException {
        return openZip(files, PackLimits.DEFAULT, new ArrayList<>());
    }

    private PackSource openZip(Map<String, ?> files, PackLimits limits, List<Diagnostic> diags)
            throws IOException {
        Path zip = FixturePack.writeZip(tmp.resolve("pack.zip"), files);
        try {
            return PackSource.open(zip, limits, diags);
        } catch (PackException e) {
            throw new RuntimeException(e);
        }
    }

    private PackException openZipFails(Map<String, ?> files) throws IOException {
        return openZipFails(files, PackLimits.DEFAULT);
    }

    private PackException openZipFails(Map<String, ?> files, PackLimits limits) throws IOException {
        Path zip = FixturePack.writeZip(tmp.resolve("pack.zip"), files);
        return assertThrows(PackException.class, () -> PackSource.open(zip, limits, new ArrayList<>()));
    }

    @Test
    void emptyArchiveIsRejected() throws IOException {
        PackException e = openZipFails(Map.of());
        assertEquals(DiagnosticCode.EMPTY_ARCHIVE, e.code());
    }

    @Test
    void archiveWithoutShadersHasNoShadersFiles() throws IOException {
        // open succeeds on the container; the loader reports NO_SHADER_ROOT.
        PackSource src = openZip(Map.of("readme.txt", "hi", "textures/rock.png", new byte[4]));
        assertTrue(src.files().isEmpty());
        assertEquals(2, src.skippedOutsideShaders());
    }

    @Test
    void traversalEntriesAreRejected() throws IOException {
        PackException e = openZipFails(Map.of(
                "shaders/x.vsh", "x",
                "../escape.txt", "y"));
        assertEquals(DiagnosticCode.ENTRY_TRAVERSAL_REJECTED, e.code());
    }

    @Test
    void traversalInsideShadersIsContainedNotAccepted() throws Exception {
        // "shaders/../x" canonicalizes to "/x" - it escapes shaders/ but not the
        // archive root, so it is safely counted as outside rather than loaded.
        PackSource src = openZip(Map.of(
                "shaders/x.vsh", "x",
                "shaders/../outside.txt", "y"));
        assertEquals(List.of(PackPath.absolute("/shaders/x.vsh")), src.files());
        assertEquals(1, src.skippedOutsideShaders());
    }

    @Test
    void driveLetterEntryIsRejected() throws IOException {
        PackException e = openZipFails(Map.of("C:/evil.txt", "y", "shaders/x.vsh", "x"));
        assertEquals(DiagnosticCode.ENTRY_TRAVERSAL_REJECTED, e.code());
    }

    @Test
    void duplicateCanonicalNamesAreRejected() throws IOException {
        PackException e = openZipFails(Map.of("shaders/x.vsh", "a", "shaders/./x.vsh", "b"));
        assertEquals(DiagnosticCode.DUPLICATE_ENTRY, e.code());
    }

    @Test
    void entryLimitIsEnforced() throws IOException {
        PackLimits limits = new PackLimits(5, 1024 * 1024, 1024 * 1024, 250, 1024 * 1024, 64, 8192, 8L << 20, 65536);
        FixturePack.Builder b = FixturePack.pack();
        for (int i = 0; i < 10; i++) {
            b.file("shaders/f" + i + ".glsl", "x");
        }
        Path zip = b.zip(tmp.resolve("pack.zip"));
        PackException e = assertThrows(PackException.class,
                () -> PackSource.open(zip, limits, new ArrayList<>()));
        assertEquals(DiagnosticCode.ENTRY_LIMIT_EXCEEDED, e.code());
    }

    @Test
    void fileSizeLimitIsEnforced() throws IOException {
        PackLimits limits = new PackLimits(1024, 1024 * 1024, 16, 250, 1024 * 1024, 64, 8192, 8L << 20, 65536);
        byte[] big = new byte[64];
        PackException e = openZipFails(Map.of("shaders/big.glsl", big), limits);
        assertEquals(DiagnosticCode.FILE_LIMIT_EXCEEDED, e.code());
    }

    @Test
    void zipBombRatioIsRejected() throws IOException {
        // 1.2 MB of zeros deflate to ~1.2 KB - a ratio of ~1000x.
        byte[] zeros = new byte[1250 * 1024];
        PackException e = openZipFails(Map.of("shaders/bomb.glsl", zeros));
        assertEquals(DiagnosticCode.SUSPICIOUS_COMPRESSION, e.code());
    }

    @Test
    void filesOutsideShadersAreCountedNotRead() throws Exception {
        PackSource src = openZip(Map.of(
                "shaders/x.vsh", "x",
                "shaders/img.png", "not-really-png",
                "docs/notes.txt", "n",
                "meta.json", "{}"));
        assertEquals(2, src.files().size());
        assertEquals(2, src.skippedOutsideShaders());
    }

    @Test
    void nestedShadersRootIsAcceptedWithWarning() throws Exception {
        List<Diagnostic> diags = new ArrayList<>();
        Path zip = FixturePack.writeZip(tmp.resolve("pack.zip"), Map.of(
                "MyPack/shaders/x.vsh", "x",
                "MyPack/readme.txt", "r"));
        PackSource src = PackSource.open(zip, PackLimits.DEFAULT, diags);
        assertEquals(List.of(PackPath.absolute("/shaders/x.vsh")), src.files());
        assertTrue(diags.stream().anyMatch(d -> d.code() == DiagnosticCode.NESTED_SHADERS_ROOT));
    }

    @Test
    void ambiguousNestedRootsAreNotPicked() throws IOException {
        // two top-level dirs each containing shaders/ - we take neither.
        PackSource src = openZip(Map.of(
                "A/shaders/x.vsh", "x",
                "B/shaders/y.vsh", "y"));
        assertTrue(src.files().isEmpty(), "ambiguous nesting must yield no shaders root");
    }

    // ---- directory source ---------------------------------------------------

    @Test
    void directoryWithoutShadersFails() throws IOException {
        Path dir = tmp.resolve("empty");
        Files.createDirectories(dir);
        PackException e = assertThrows(PackException.class,
                () -> PackSource.open(dir, PackLimits.DEFAULT, new ArrayList<>()));
        assertEquals(DiagnosticCode.NO_SHADER_ROOT, e.code());
    }

    @Test
    void directoryPackReadsShadersOnly() throws Exception {
        Path dir = FixturePack.minimal().dir(tmp.resolve("packdir"));
        FixturePack.writeDir(dir, Map.of("outside.txt", "nope"));
        PackSource src = PackSource.open(dir, PackLimits.DEFAULT, new ArrayList<>());
        assertTrue(src.files().stream().allMatch(p -> p.isUnder("/shaders/")));
        assertTrue(src.exists(PackPath.absolute("/shaders/program/composite.glsl")));
    }

    @Test
    void directorySymlinkEscapeIsRejected() throws IOException {
        assumeTrue(canSymlink());
        Path dir = FixturePack.minimal().dir(tmp.resolve("packdir"));
        Path outside = tmp.resolve("outside-secret.txt");
        Files.writeString(outside, "secret");
        Files.createSymbolicLink(dir.resolve("shaders/program/leak.txt"), outside);
        PackException e = assertThrows(PackException.class,
                () -> PackSource.open(dir, PackLimits.DEFAULT, new ArrayList<>()));
        assertEquals(DiagnosticCode.SYMLINK_ESCAPE, e.code());
    }

    @Test
    void directoryInternalSymlinkIsSkippedWithWarning() throws Exception {
        assumeTrue(canSymlink());
        Path dir = FixturePack.minimal().dir(tmp.resolve("packdir"));
        Files.createSymbolicLink(dir.resolve("shaders/program/alias.glsl"),
                Path.of("composite.glsl")); // relative link inside shaders/
        List<Diagnostic> diags = new ArrayList<>();
        PackSource src = PackSource.open(dir, PackLimits.DEFAULT, diags);
        assertFalse(src.exists(PackPath.absolute("/shaders/program/alias.glsl")));
        assertTrue(diags.stream().anyMatch(d -> d.code() == DiagnosticCode.SYMLINK_ESCAPE
                && d.severity() == Severity.WARNING));
    }

    @Test
    void directoryFifosAreRejected() throws IOException {
        Path dir = FixturePack.minimal().dir(tmp.resolve("packdir"));
        Process mkfifo;
        try {
            mkfifo = new ProcessBuilder("mkfifo",
                    dir.resolve("shaders/program/pipe.glsl").toString()).start();
        } catch (IOException missingTool) {
            assumeTrue(false, "mkfifo not available on this platform");
            return;
        }
        try {
            assumeTrue(mkfifo.waitFor() == 0, "mkfifo failed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            assumeTrue(false);
        }
        PackException e = assertThrows(PackException.class,
                () -> PackSource.open(dir, PackLimits.DEFAULT, new ArrayList<>()));
        assertEquals(DiagnosticCode.NOT_REGULAR_FILE, e.code());
    }

    private boolean canSymlink() {
        try {
            Path probe = tmp.resolve("probe-link");
            Files.createSymbolicLink(probe, tmp.resolve("probe-target"));
            Files.deleteIfExists(probe);
            return true;
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            return false;
        }
    }
}
