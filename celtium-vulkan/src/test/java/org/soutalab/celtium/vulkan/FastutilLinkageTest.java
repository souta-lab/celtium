package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * No call into fastutil may pass a primitive key as a box.
 * <p>
 * The module compiles against Minecraft 1.12.2's fastutil 7.1.0, where a {@code int}-keyed map still carries the
 * {@code Map}-compatibility overloads ({@code get(Integer)}, {@code put(Integer, Long)}, {@code add(Integer)}). javac
 * prefers those in overload resolution, so the call site links to a method the newer fastutil the game actually runs
 * does not declare: Cleanroom 0.7 ships 8.5.18, whose {@code Int2ObjectOpenHashMap} has {@code get(int)} only. The
 * measured consequence (leg {@code r9-supply}, AMD, Supersymmetry): a {@code glDrawArrays} from
 * {@code Gui.func_73729_b} reached the renderer, the copy path asked the registry for the batch's texture, the
 * {@code get(Integer)} at {@link VulkanTextureRegistry#get(int, boolean)} threw {@code NoSuchMethodError}, and the
 * whole GUI quad was refused with "copy failed" - a missing draw, not a wrong colour. That is the class of defect this
 * case exists to keep out: a linkage difference that no unit test executes and no build error reports.
 * <p>
 * The rule is about the <em>first</em> parameter (the key) on purpose: a value may legitimately be a boxed type
 * ({@code Int2ObjectOpenHashMap<Integer>}), and the upper bound of a {@code Map}-compatibility bridge is not what
 * differs between fastutil generations. The class files are read directly, as in {@link HugeMethodLimitTest}.
 */
class FastutilLinkageTest {
    private static final String FASTUTIL = "it/unimi/dsi/fastutil/";
    private static final java.util.Set<String> BOXED = java.util.Set.of(
            "java/lang/Integer", "java/lang/Long", "java/lang/Short", "java/lang/Byte",
            "java/lang/Character", "java/lang/Boolean", "java/lang/Float", "java/lang/Double");

    @Test
    void noFastutilCallPassesABoxedKey() throws IOException, URISyntaxException {
        Path root = Paths.get(VulkanContext.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertTrue(Files.isDirectory(root), "expected the compiled classes directory, got " + root);
        List<String> boxed = new ArrayList<>();
        int calls = 0;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                try (InputStream in = Files.newInputStream(file)) {
                    calls += scan(new DataInputStream(in), root.relativize(file).toString(), boxed);
                }
            }
        }
        // A scan that read nothing cannot pass: the renderer calls fastutil from dozens of places, so a number this
        // small means the directory or the parser is wrong rather than that the rule holds.
        assertTrue(calls > 30, "the scan found only " + calls + " fastutil calls; is it reading the right directory?");
        assertTrue(boxed.isEmpty(), "fastutil calls passing a boxed key: the primitive-key overloads do not exist in "
                + "every fastutil the game may run (7.1.0 has them, 8.5.18 does not), so a boxed key is a "
                + "NoSuchMethodError waiting for the platform that lacks it: " + boxed);
    }

    /** Counts this class's fastutil calls and names the ones whose key is boxed. */
    private static int scan(DataInputStream in, String name, List<String> boxed) throws IOException {
        if (in.readInt() != 0xCAFEBABE) {
            return 0;
        }
        in.readUnsignedShort();
        in.readUnsignedShort();
        int count = in.readUnsignedShort();
        String[] utf8 = new String[count];
        int[] classNames = new int[count];
        int[] nameAndTypeName = new int[count];
        int[] nameAndTypeDescriptor = new int[count];
        int[] memberOwner = new int[count];
        int[] memberNameAndType = new int[count];
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> utf8[i] = in.readUTF();
                case 3, 4 -> in.readInt();
                case 5, 6 -> { in.readLong(); i++; }
                case 7 -> classNames[i] = in.readUnsignedShort();
                case 8, 16, 19, 20 -> in.readUnsignedShort();
                case 9, 10, 11, 12 -> {
                    int first = in.readUnsignedShort();
                    int second = in.readUnsignedShort();
                    if (tag == 12) {
                        nameAndTypeName[i] = first;
                        nameAndTypeDescriptor[i] = second;
                    } else if (tag == 10 || tag == 11) {
                        // A method reference, and only those: a field read of a fastutil type has no key to box.
                        memberOwner[i] = first;
                        memberNameAndType[i] = second;
                    }
                }
                case 15 -> { in.readUnsignedByte(); in.readUnsignedShort(); }
                case 17, 18 -> { in.readUnsignedShort(); in.readUnsignedShort(); }
                default -> throw new IOException(name + ": constant pool tag " + tag);
            }
        }
        int found = 0;
        for (int i = 1; i < count; i++) {
            if (memberOwner[i] == 0) {
                continue;
            }
            String owner = utf8[classNames[memberOwner[i]]];
            if (owner == null || !owner.startsWith(FASTUTIL)) {
                continue;
            }
            found++;
            String method = utf8[nameAndTypeName[memberNameAndType[i]]];
            String descriptor = utf8[nameAndTypeDescriptor[memberNameAndType[i]]];
            if (descriptor == null || !descriptor.startsWith("(L")) {
                continue;
            }
            int end = descriptor.indexOf(';');
            if (end > 2 && BOXED.contains(descriptor.substring(2, end))) {
                boxed.add(name + ": " + owner.replace('/', '.') + "." + method + descriptor);
            }
        }
        return found;
    }
}
