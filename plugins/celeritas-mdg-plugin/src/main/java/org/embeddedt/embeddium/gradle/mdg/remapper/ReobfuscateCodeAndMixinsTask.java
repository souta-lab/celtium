package org.embeddedt.embeddium.gradle.mdg.remapper;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.extras.ClassAnalysisDescCompleter;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.bundling.Jar;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class ReobfuscateCodeAndMixinsTask extends Jar {
    @InputFile
    public abstract RegularFileProperty getTsrgMappings();

    @InputFile
    public abstract RegularFileProperty getDeobfMinecraftJar();

    @Optional
    @InputFiles
    public abstract ConfigurableFileCollection getClasspath();

    @InputFile
    public abstract RegularFileProperty getInput();

    private void remap(File inputJar, File outputJar) throws IOException {
        // Load TSRG mappings
        MemoryMappingTree mappingTree = new MemoryMappingTree();
        Path mappingsPath = getTsrgMappings().getAsFile().get().toPath();
        MappingReader.read(mappingsPath, mappingTree);
        ClassAnalysisDescCompleter.process(getDeobfMinecraftJar().getAsFile().get().toPath(), mappingTree.getSrcNamespace(), mappingTree);

        IMappingProvider mappings = TinyRemapperMappingsHelper.create(mappingTree, "source", "target");
        TinyRemapper remapper = TinyRemapper.newRemapper()
                .extension(new MixinExtension())
                .withMappings(mappings)
                .build();

        // Delete the output jar, otherwise we will just update the changed entries and not remove deleted entries.
        if (outputJar.exists()) {
            if (!outputJar.delete()) {
                getLogger().warn("Failed to delete jar {}", outputJar.getAbsolutePath());
            }
        }

        // Create output consumer
        try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(outputJar.toPath()).assumeArchive(true).build()) {
            outputConsumer.addNonClassFiles(inputJar.toPath()); // optional: copy resources

            // Run remap
            remapper.readInputs(inputJar.toPath());
            remapper.readClassPath(getClasspath().getFiles().stream().map(File::toPath).filter(Files::exists).toArray(Path[]::new));
            remapper.apply(outputConsumer);
            remapper.finish();
        }
    }

    /**
     * The timestamp every rewritten entry carries: 1980-01-01 00:00 local, the earliest instant a zip's DOS time can
     * express. Set through {@link java.util.zip.ZipEntry#setTimeLocal} rather than {@code setTime}, because
     * {@code setTime} converts through the default time zone and the same build would then hash differently on two
     * machines.
     */
    private static final java.time.LocalDateTime FIXED_ENTRY_TIME = java.time.LocalDateTime.of(1980, 1, 1, 0, 0, 0);

    /**
     * Rewrites the archive tinyremapper produced so the same inputs always give the same bytes.
     * <p>
     * Why this exists. This task extends {@code Jar} but overrides {@code copy()}, so the reproducible-archive settings
     * the build applies to every {@code AbstractArchiveTask} ({@code isPreserveFileTimestamps = false},
     * {@code isReproducibleFileOrder = true}) never reach the file: {@link OutputConsumerPath} writes the zip itself,
     * stamping each entry with the build's clock and in whatever order the remapper emitted them. The renderer jar
     * reproduces byte for byte; the validation fixture did not, so every evidence record's fixture hash changed with
     * the build and "this run used the jar that was tested" could not be checked from the hash. Measured before this:
     * two consecutive {@code :forge122:validationRemapJar} runs of one tree produced two different sha256 values.
     * <p>
     * The rewrite keeps every entry's bytes and drops only what is not content: directory entries (a jar does not need
     * them), the per-entry extra fields and the timestamps. Entries are written in name order with the manifest first,
     * which is the order {@code JarInputStream} expects to find it in.
     */
    private void makeReproducible(File jar) throws IOException {
        Path source = jar.toPath();
        Path rewritten = source.resolveSibling(source.getFileName() + ".reproducible");
        java.util.TreeMap<String, byte[]> entries = new java.util.TreeMap<String, byte[]>();
        byte[] manifest = null;
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> names = zip.entries();
            while (names.hasMoreElements()) {
                java.util.zip.ZipEntry entry = names.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                byte[] bytes;
                try (java.io.InputStream in = zip.getInputStream(entry)) {
                    bytes = in.readAllBytes();
                }
                if ("META-INF/MANIFEST.MF".equalsIgnoreCase(entry.getName())) {
                    manifest = bytes;
                } else {
                    entries.put(entry.getName(), bytes);
                }
            }
        }
        try (java.util.zip.ZipOutputStream out =
                     new java.util.zip.ZipOutputStream(Files.newOutputStream(rewritten))) {
            if (manifest != null) {
                writeEntry(out, "META-INF/MANIFEST.MF", manifest);
            }
            for (java.util.Map.Entry<String, byte[]> entry : entries.entrySet()) {
                writeEntry(out, entry.getKey(), entry.getValue());
            }
        }
        Files.move(rewritten, source, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static void writeEntry(java.util.zip.ZipOutputStream out, String name, byte[] bytes) throws IOException {
        java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(name);
        entry.setTimeLocal(FIXED_ENTRY_TIME);
        out.putNextEntry(entry);
        out.write(bytes);
        out.closeEntry();
    }

    @TaskAction
    @Override
    public void copy() {
        try {
            File output = getArchiveFile().get().getAsFile();
            remap(getInput().getAsFile().get(), output);
            makeReproducible(output);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
