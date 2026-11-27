package org.soutalab.celtium.gradle.remapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the conversion core against a miniature of the real setup: a "Minecraft" jar
 * holding one deobfuscated class, a TSRG file that renames it, and an input jar whose
 * classes use it.
 *
 * <p>The TSRG fixture deliberately writes its field line the way TSRG v1 does, with no
 * descriptor. The field can only come out renamed if the descriptor completion step ran,
 * which is the behaviour the production build depends on just as much.
 */
class ReobfuscateCodeAndMixinsTaskTest {

    private static final String TSRG = """
            com/example/Deobf com/example/Srg
            \tfieldDeobf field_0001_a
            \tmethodDeobf ()V func_0002_b
            """;

    @TempDir
    Path dir;

    @Test
    void renamesClassesAndMembers() throws IOException {
        Path out = run(fixture(false, false), "out.jar");

        Map<String, byte[]> entries = readEntries(out);
        assertTrue(entries.containsKey("com/example/Srg.class"), "renamed class entry is present");
        assertFalse(entries.containsKey("com/example/Deobf.class"), "development class entry is gone");

        List<String> references = memberReferences(entries.get("com/example/User.class"));
        assertTrue(references.contains("field com/example/Srg field_0001_a Ljava/lang/String;"),
                "field reference renamed, descriptor completed from the Minecraft jar: " + references);
        assertTrue(references.contains("method com/example/Srg func_0002_b ()V"),
                "method reference renamed: " + references);

        List<String> declared = declaredMembers(entries.get("com/example/Srg.class"));
        assertTrue(declared.contains("field field_0001_a Ljava/lang/String;"), "own field renamed: " + declared);
        assertTrue(declared.contains("method func_0002_b ()V"), "own method renamed: " + declared);

        assertArrayEquals("payload".getBytes(StandardCharsets.UTF_8), entries.get("data.txt"),
                "non-class files are copied byte for byte");
    }

    @Test
    void outputDoesNotDependOnInputOrderOrTimestamps() throws IOException {
        Path first = run(fixture(false, false), "first.jar");
        Path second = run(fixture(false, false), "second.jar");
        Path shuffled = run(fixture(false, true), "shuffled.jar");

        assertEquals(sha256(first), sha256(second), "same input, same bytes");
        assertEquals(sha256(first), sha256(shuffled),
                "entry order, entry timestamps and a directory entry in the input change nothing");
    }

    @Test
    void entriesCarryTheFixedTimestampAndNoDirectories() throws IOException {
        Path out = run(fixture(true, true), "out.jar");

        try (ZipFile zip = new ZipFile(out.toFile())) {
            Enumeration<? extends ZipEntry> names = zip.entries();
            assertTrue(names.hasMoreElements());
            assertEquals("META-INF/MANIFEST.MF", names.nextElement().getName(),
                    "manifest leads even though the input listed it last");
            while (names.hasMoreElements()) {
                ZipEntry entry = names.nextElement();
                assertFalse(entry.isDirectory(), "no directory entries: " + entry.getName());
                assertEquals(LocalDateTime.of(1980, 1, 1, 0, 0, 0), entry.getTimeLocal(),
                        "fixed timestamp on " + entry.getName());
            }
        }
    }

    // Fixture construction

    private record Fixture(Path inputJar, Path mappingsFile, Path minecraftJar) {
    }

    private Fixture fixture(boolean withManifest, boolean shuffled) throws IOException {
        String tag = (withManifest ? "m" : "-") + (shuffled ? "s" : "-");
        Path minecraftJar = dir.resolve("minecraft-" + tag + ".jar");
        writeJar(minecraftJar,
                List.of(Map.entry("com/example/Deobf.class", deobfClassBytes())),
                1_700_000_000_000L);

        Path mappings = dir.resolve("mappings-" + tag + ".tsrg");
        Files.writeString(mappings, TSRG);

        List<Map.Entry<String, byte[]>> entries = new ArrayList<>();
        entries.add(Map.entry("com/example/User.class", userClassBytes()));
        entries.add(Map.entry("com/example/Deobf.class", deobfClassBytes()));
        entries.add(Map.entry("data.txt", "payload".getBytes(StandardCharsets.UTF_8)));
        if (withManifest) {
            entries.add(Map.entry("META-INF/MANIFEST.MF",
                    "Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8)));
        }
        long writtenAt = 1_700_000_000_000L;
        if (shuffled) {
            Collections.reverse(entries);
            entries.add(Map.entry("com/example/", new byte[0]));
            writtenAt = 1_600_000_000_000L;
        }
        Path inputJar = dir.resolve("input-" + tag + ".jar");
        writeJar(inputJar, entries, writtenAt);
        return new Fixture(inputJar, mappings, minecraftJar);
    }

    private Path run(Fixture fixture, String outputName) throws IOException {
        Path out = dir.resolve(outputName);
        ReobfuscateCodeAndMixinsTask.remapAndNormalize(
                fixture.inputJar(), fixture.mappingsFile(), fixture.minecraftJar(), List.of(), out);
        return out;
    }

    private static byte[] deobfClassBytes() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "com/example/Deobf", null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC, "fieldDeobf", "Ljava/lang/String;", null, null).visitEnd();
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "methodDeobf", "()V", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] userClassBytes() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "com/example/User", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "use", "(Lcom/example/Deobf;)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, "com/example/Deobf", "fieldDeobf", "Ljava/lang/String;");
        method.visitInsn(Opcodes.POP);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "com/example/Deobf", "methodDeobf", "()V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    // Reading results back

    private static Map<String, byte[]> readEntries(Path jar) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> names = zip.entries();
            while (names.hasMoreElements()) {
                ZipEntry entry = names.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    entries.put(entry.getName(), in.readAllBytes());
                }
            }
        }
        return entries;
    }

    private static List<String> memberReferences(byte[] classBytes) {
        List<String> references = new ArrayList<>();
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                        references.add("field " + owner + " " + name + " " + descriptor);
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name,
                                                String descriptor, boolean isInterface) {
                        references.add("method " + owner + " " + name + " " + descriptor);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return references;
    }

    private static List<String> declaredMembers(byte[] classBytes) {
        List<String> members = new ArrayList<>();
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor,
                                           String signature, Object value) {
                members.add("field " + name + " " + descriptor);
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                members.add("method " + name + " " + descriptor);
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return members;
    }

    private static void writeJar(Path jar, List<Map.Entry<String, byte[]>> entries, long entryTime) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (Map.Entry<String, byte[]> entry : entries) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setTime(entryTime);
                out.putNextEntry(zipEntry);
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
    }

    private static String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
        byte[] hash = digest.digest(Files.readAllBytes(file));
        StringBuilder out = new StringBuilder();
        for (byte b : hash) {
            out.append(String.format("%02x", b));
        }
        return out.toString();
    }
}
