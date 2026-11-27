package org.soutalab.celtium.gradle.remapper;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.extras.ClassAnalysisDescCompleter;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Reobfuscates one built jar from development names to the names the game ships with.
 *
 * <p>The jar the mod is developed against carries readable (deobfuscated) names, while the
 * Minecraft jar in a real installation uses the obfuscated SRG names. This task rewrites
 * the mod jar's own classes, and the mixin configuration inside it, from the first naming
 * to the second, using the TSRG mapping file that the MCP toolchain produces.
 *
 * <p>Two properties of the output matter beyond the renaming itself:
 *
 * <ul>
 *   <li>Field mappings in a TSRG v1 file carry no descriptors, so the mapping tree is
 *       first completed from the deobfuscated Minecraft jar; without that step every
 *       field would silently keep its development name.</li>
 *   <li>The finished archive is rewritten into a deterministic byte form (fixed entry
 *       timestamps, name order, no directory entries), so rebuilding the same sources
 *       yields the same SHA-256 and an evidence record can name the jar it measured.</li>
 * </ul>
 *
 * <p>The task declares all of its files, so Gradle's up-to-date checking applies as usual.
 * The conversion itself sits in a static method that knows nothing about Gradle, which is
 * what the unit tests exercise.
 */
public abstract class ReobfuscateCodeAndMixinsTask extends DefaultTask {

    /** The namespace a TSRG file calls its readable side. */
    private static final String DEVELOPMENT_NAMESPACE = "source";

    /** The namespace a TSRG file calls its shipped side. */
    private static final String SHIPPED_NAMESPACE = "target";

    /** The jar written against development names. */
    @InputFile
    public abstract RegularFileProperty getInputJar();

    /** The TSRG mapping file that pairs the two namings. */
    @InputFile
    public abstract RegularFileProperty getMappingsFile();

    /** The deobfuscated Minecraft jar, mined for the member descriptors TSRG omits. */
    @InputFile
    public abstract RegularFileProperty getMinecraftJar();

    /** Jars the input's classes were compiled against; the remapper reads, never copies, them. */
    @Classpath
    public abstract ConfigurableFileCollection getRemapClasspath();

    /** Where the reobfuscated jar is written. */
    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    @TaskAction
    public void remap() {
        List<Path> classpath = new ArrayList<>();
        for (File entry : getRemapClasspath().getFiles()) {
            if (entry.exists()) {
                classpath.add(entry.toPath());
            }
        }
        try {
            remapAndNormalize(
                    getInputJar().get().getAsFile().toPath(),
                    getMappingsFile().get().getAsFile().toPath(),
                    getMinecraftJar().get().getAsFile().toPath(),
                    classpath,
                    getOutputJar().get().getAsFile().toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Runs the whole conversion: read the mappings, rename the jar's contents, then put
     * the resulting archive into its deterministic form.
     */
    static void remapAndNormalize(Path inputJar, Path mappingsFile, Path minecraftJar,
                                  List<Path> classpath, Path outputJar) throws IOException {
        MemoryMappingTree tree = new MemoryMappingTree();
        MappingReader.read(mappingsFile, tree);
        // TSRG v1 records no field descriptors. Recover them from the Minecraft jar's own
        // classes, or fields fall out of the mapping entirely.
        ClassAnalysisDescCompleter.process(minecraftJar, DEVELOPMENT_NAMESPACE, tree);

        TinyRemapper remapper = TinyRemapper.newRemapper()
                .extension(new MixinExtension())
                .withMappings(providerFor(tree))
                .build();
        try {
            // The output consumer updates an existing archive in place; a stale file would
            // keep entries that the input no longer has.
            Files.deleteIfExists(outputJar);
            try (OutputConsumerPath consumer =
                         new OutputConsumerPath.Builder(outputJar).assumeArchive(true).build()) {
                consumer.addNonClassFiles(inputJar);
                remapper.readInputs(inputJar);
                if (!classpath.isEmpty()) {
                    remapper.readClassPath(classpath.toArray(new Path[0]));
                }
                remapper.apply(consumer);
            }
        } finally {
            remapper.finish();
        }
        makeDeterministic(outputJar);
    }

    /**
     * Feeds the mapping tree to tiny-remapper. Members are keyed by their development
     * (name, descriptor) pair, because that is the form the input jar carries; a class
     * without a shipped name keeps its development name.
     */
    private static IMappingProvider providerFor(MemoryMappingTree tree) {
        return acceptor -> {
            for (MappingTree.ClassMapping cls : tree.getClasses()) {
                String fromClass = cls.getName(DEVELOPMENT_NAMESPACE);
                String toClass = cls.getName(SHIPPED_NAMESPACE);
                acceptor.acceptClass(fromClass, toClass != null ? toClass : fromClass);
                for (MappingTree.FieldMapping field : cls.getFields()) {
                    String fromDesc = field.getDesc(DEVELOPMENT_NAMESPACE);
                    String toName = field.getName(SHIPPED_NAMESPACE);
                    if (fromDesc == null || toName == null) {
                        continue;
                    }
                    acceptor.acceptField(
                            new IMappingProvider.Member(fromClass, field.getName(DEVELOPMENT_NAMESPACE), fromDesc),
                            toName);
                }
                for (MappingTree.MethodMapping method : cls.getMethods()) {
                    String fromDesc = method.getDesc(DEVELOPMENT_NAMESPACE);
                    String toName = method.getName(SHIPPED_NAMESPACE);
                    if (fromDesc == null || toName == null) {
                        continue;
                    }
                    acceptor.acceptMethod(
                            new IMappingProvider.Member(fromClass, method.getName(DEVELOPMENT_NAMESPACE), fromDesc),
                            toName);
                }
            }
        };
    }

    /**
     * Rewrites an archive so that its bytes depend only on its contents.
     *
     * <p>The remapper stamps each entry with the wall clock and emits entries in its own
     * working order, so two builds of the same sources hash differently. This pass keeps
     * every entry's bytes untouched and drops everything that is not content: timestamps
     * are pinned to the earliest instant a zip can express, directory entries and per-entry
     * extra fields disappear, and the entries are laid out with the manifest first and the
     * rest in name order. Compression is whatever {@link ZipOutputStream} does by default.
     * The rewrite goes to a sibling file first and is then moved over the original, so a
     * failure cannot leave a half-written archive behind.
     */
    private static void makeDeterministic(Path archive) throws IOException {
        byte[] manifest = null;
        Map<String, byte[]> entries = new TreeMap<>();
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            Enumeration<? extends ZipEntry> names = zip.entries();
            while (names.hasMoreElements()) {
                ZipEntry entry = names.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                byte[] content;
                try (InputStream in = zip.getInputStream(entry)) {
                    content = in.readAllBytes();
                }
                if (entry.getName().equalsIgnoreCase("META-INF/MANIFEST.MF")) {
                    manifest = content;
                } else {
                    // A repeated name overwrites, matching how a reader sees the archive:
                    // the last entry with a given name is the one that takes effect.
                    entries.put(entry.getName(), content);
                }
            }
        }

        Path rewritten = archive.resolveSibling(archive.getFileName() + ".reproducible");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(rewritten))) {
            if (manifest != null) {
                writeEntry(out, "META-INF/MANIFEST.MF", manifest);
            }
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                writeEntry(out, entry.getKey(), entry.getValue());
            }
        }
        Files.move(rewritten, archive, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void writeEntry(ZipOutputStream out, String name, byte[] content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        // setTimeLocal, not setTime: setTime converts through the machine's default time
        // zone, so the same build would stamp different bytes on differently zoned machines.
        entry.setTimeLocal(LocalDateTime.of(1980, 1, 1, 0, 0, 0));
        out.putNextEntry(entry);
        out.write(content);
        out.closeEntry();
    }
}
