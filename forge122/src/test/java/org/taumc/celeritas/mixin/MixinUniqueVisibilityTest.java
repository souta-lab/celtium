package org.taumc.celeritas.mixin;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The shape Mixin's prepare phase enforces but the rest of the suite cannot see: a member a mixin
 * introduces with {@code @Unique} must be {@code private}, or Mixin refuses the whole mixin class
 * and the game dies at startup with the target class never defined.
 *
 * <p>That is not a theoretical shape. The VanillaFix hardening gave {@code TextureAtlasMixin} a
 * package-private {@code @Unique} helper ({@code celtium$filteringView}); the full PC gate stayed
 * green - 877 tests - because the existing tests read mixin classes by reflection without
 * initialising them, a view in which a member's visibility is present but nothing ever asks the
 * question Mixin asks. The defect was only caught by a fixture leg, after the jar was built.
 *
 * <p>This test asks Mixin's question of the same bytes Mixin reads. It walks the mixin package
 * exactly the way {@link CeleritasVintageMixinPlugin#getMixins()} discovers mixins at launch -
 * every class under {@code org.taumc.celeritas.mixin} carrying {@code @Mixin} - and requires
 * every {@code @Unique} field and method to carry {@code ACC_PRIVATE}. The class files are parsed
 * directly, with no ASM: the test runtime classpath also carries launchwrapper's ASM 5.2
 * (asm-debug-all), whose ClassReader rejects this project's release-21 class files outright, so
 * an ASM-based guard would fail - or, worse, be ordered away - for a reason that has nothing to
 * do with the shape it guards. The floors below keep the walk honest: if discovery ever breaks
 * and finds almost nothing, the test fails instead of passing over an empty set.
 */
class MixinUniqueVisibilityTest {

    private static final String MIXIN_PACKAGE_PATH = "org/taumc/celeritas/mixin";
    private static final String MIXIN_ANNOTATION = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String UNIQUE_ANNOTATION = "Lorg/spongepowered/asm/mixin/Unique;";
    private static final int ACC_PRIVATE = 0x0002;

    /** Discovery found 92 mixin classes and 69 @Unique members when this guard was written. */
    private static final int MIN_MIXIN_CLASSES = 50;
    private static final int MIN_UNIQUE_MEMBERS = 30;

    @Test
    void everyUniqueMemberOfEveryMixinIsPrivate() throws Exception {
        List<String> violations = new ArrayList<>();
        int mixinClasses = 0;
        int uniqueMembers = 0;

        for (Path classFile : mixinPackageClassFiles()) {
            ParsedClass parsed;
            try {
                parsed = ParsedClass.parse(Files.readAllBytes(classFile));
            } catch (IOException | RuntimeException e) {
                fail("could not parse " + classFile + " - the guard reads class files the way "
                        + "Mixin's prepare does and must not skip one silently: " + e);
                return;
            }
            if (!parsed.annotations.contains(MIXIN_ANNOTATION)) {
                continue;
            }
            mixinClasses++;
            for (ParsedMember member : parsed.members) {
                if (!member.annotations.contains(UNIQUE_ANNOTATION)) {
                    continue;
                }
                uniqueMembers++;
                if ((member.access & ACC_PRIVATE) == 0) {
                    violations.add(parsed.internalName.replace('/', '.') + "#" + member.name
                            + (member.method ? " (method)" : " (field)")
                            + " is @Unique but not private");
                }
            }
        }

        if (!violations.isEmpty()) {
            fail("Mixin refuses a mixin whose @Unique members are not private, at startup, "
                    + "with the target class never defined:\n" + String.join("\n", violations));
        }
        assertTrue(mixinClasses >= MIN_MIXIN_CLASSES,
                "mixin discovery found only " + mixinClasses + " @Mixin classes under "
                        + MIXIN_PACKAGE_PATH + "; the walk is broken, not the tree");
        assertTrue(uniqueMembers >= MIN_UNIQUE_MEMBERS,
                "only " + uniqueMembers + " @Unique members found across " + mixinClasses
                        + " mixins; the walk is broken, not the tree");
    }

    /** Every {@code .class} file under the mixin package, across every classpath root that has it. */
    private static List<Path> mixinPackageClassFiles() throws IOException {
        ClassLoader loader = MixinUniqueVisibilityTest.class.getClassLoader();
        Enumeration<URL> roots = loader.getResources(MIXIN_PACKAGE_PATH);
        List<Path> files = new ArrayList<>();
        while (roots.hasMoreElements()) {
            URL root = roots.nextElement();
            if (!"file".equals(root.getProtocol())) {
                fail("mixin package root is not a directory on disk: " + root
                        + " - this guard reads class files the way Mixin's prepare does and "
                        + "cannot run against a packaged root");
            }
            Path rootPath = new File(root.getPath()).toPath();
            try (Stream<Path> walk = Files.walk(rootPath)) {
                walk.filter(path -> path.getFileName().toString().endsWith(".class"))
                        .forEach(files::add);
            }
        }
        assertTrue(!files.isEmpty(), "no class files found under " + MIXIN_PACKAGE_PATH);
        return files;
    }

    private static final class ParsedMember {
        final String name;
        final int access;
        final boolean method;
        final Set<String> annotations;

        ParsedMember(String name, int access, boolean method, Set<String> annotations) {
            this.name = name;
            this.access = access;
            this.method = method;
            this.annotations = annotations;
        }
    }

    private static final class ParsedClass {
        final String internalName;
        final Set<String> annotations;
        final List<ParsedMember> members;

        private ParsedClass(String internalName, Set<String> annotations, List<ParsedMember> members) {
            this.internalName = internalName;
            this.annotations = annotations;
            this.members = members;
        }

        /**
         * The smallest class-file reader that answers this guard's questions: the class's own
         * annotations, and each field's and method's access flags and annotation types. Anything
         * else (code, values, signatures) is skipped by length, never interpreted.
         */
        static ParsedClass parse(byte[] bytes) throws IOException {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readInt() != 0xCAFEBABE) {
                throw new IOException("not a class file");
            }
            in.readUnsignedShort(); // minor
            in.readUnsignedShort(); // major - any version; the layout read here is stable
            String[] utf8 = readConstantPool(in);
            in.readUnsignedShort(); // class access flags
            String internalName = utf8[in.readUnsignedShort()];
            in.readUnsignedShort(); // super class
            int interfaces = in.readUnsignedShort();
            for (int i = 0; i < interfaces; i++) {
                in.readUnsignedShort();
            }
            List<ParsedMember> members = new ArrayList<>();
            readMembers(in, utf8, members, false);
            readMembers(in, utf8, members, true);
            Set<String> annotations = readAttributes(in, utf8);
            return new ParsedClass(internalName, annotations, members);
        }

        /** Constant pool, keeping only the Utf8 entries (annotation types and member names). */
        private static String[] readConstantPool(DataInputStream in) throws IOException {
            int count = in.readUnsignedShort();
            String[] utf8 = new String[count];
            int[] classNameIndex = new int[count];
            for (int i = 1; i < count; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1: { // Utf8
                        byte[] data = new byte[in.readUnsignedShort()];
                        in.readFully(data);
                        utf8[i] = new String(data, "UTF-8");
                        break;
                    }
                    case 3: case 4: // Integer, Float
                        in.skipBytes(4);
                        break;
                    case 5: case 6: // Long, Double - take two slots
                        in.skipBytes(8);
                        i++;
                        break;
                    case 7: // Class - remember where its name lives so this_class resolves
                        classNameIndex[i] = in.readUnsignedShort();
                        break;
                    case 8: case 16: case 19: case 20: // String, MethodType, Module, Package
                        in.skipBytes(2);
                        break;
                    case 9: case 10: case 11: case 12: case 17: case 18: // refs, NameAndType, Dynamic, InvokeDynamic
                        in.skipBytes(4);
                        break;
                    case 15: // MethodHandle
                        in.skipBytes(3);
                        break;
                    default:
                        throw new IOException("unknown constant pool tag " + tag);
                }
            }
            // this_class points at a Class entry; the class's own name is that entry's Utf8.
            String[] resolved = new String[count];
            System.arraycopy(utf8, 0, resolved, 0, count);
            for (int i = 1; i < count; i++) {
                if (classNameIndex[i] != 0) {
                    resolved[i] = utf8[classNameIndex[i]];
                }
            }
            return resolved;
        }

        private static void readMembers(DataInputStream in, String[] utf8,
                                        List<ParsedMember> members, boolean methods) throws IOException {
            int count = in.readUnsignedShort();
            for (int i = 0; i < count; i++) {
                int access = in.readUnsignedShort();
                String name = utf8[in.readUnsignedShort()];
                in.readUnsignedShort(); // descriptor
                Set<String> annotations = readAttributes(in, utf8);
                members.add(new ParsedMember(name, access, methods, annotations));
            }
        }

        /** Reads one attribute list; returns the annotation types of its Annotations attributes. */
        private static Set<String> readAttributes(DataInputStream in, String[] utf8) throws IOException {
            Set<String> annotations = new HashSet<>();
            int count = in.readUnsignedShort();
            for (int i = 0; i < count; i++) {
                String attributeName = utf8[in.readUnsignedShort()];
                int length = in.readInt();
                if ("RuntimeVisibleAnnotations".equals(attributeName)
                        || "RuntimeInvisibleAnnotations".equals(attributeName)) {
                    byte[] info = new byte[length];
                    in.readFully(info);
                    DataInputStream annotationIn =
                            new DataInputStream(new ByteArrayInputStream(info));
                    int numAnnotations = annotationIn.readUnsignedShort();
                    for (int a = 0; a < numAnnotations; a++) {
                        annotations.add(readAnnotation(annotationIn, utf8));
                    }
                } else {
                    in.skipBytes(length);
                }
            }
            return annotations;
        }

        /** Reads one annotation, returning its type descriptor and skipping its values. */
        private static String readAnnotation(DataInputStream in, String[] utf8) throws IOException {
            String type = utf8[in.readUnsignedShort()];
            int pairs = in.readUnsignedShort();
            for (int i = 0; i < pairs; i++) {
                in.readUnsignedShort(); // element name
                skipElementValue(in, utf8);
            }
            return type;
        }

        private static void skipElementValue(DataInputStream in, String[] utf8) throws IOException {
            int tag = in.readUnsignedByte();
            switch ((char) tag) {
                case 'B': case 'C': case 'D': case 'F': case 'I':
                case 'J': case 'S': case 'Z': case 's':
                    in.readUnsignedShort();
                    break;
                case 'e': // enum constant: type name + constant name
                    in.readUnsignedShort();
                    in.readUnsignedShort();
                    break;
                case 'c': // class literal
                    in.readUnsignedShort();
                    break;
                case '@': // nested annotation
                    readAnnotation(in, utf8);
                    break;
                case '[': {
                    int values = in.readUnsignedShort();
                    for (int i = 0; i < values; i++) {
                        skipElementValue(in, utf8);
                    }
                    break;
                }
                default:
                    throw new EOFException("unknown annotation element tag " + (char) tag);
            }
        }
    }
}
