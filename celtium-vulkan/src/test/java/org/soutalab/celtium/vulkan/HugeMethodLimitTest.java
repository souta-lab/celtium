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
 * No renderer method may exceed HotSpot's {@code HugeMethodLimit} (8000 bytes of bytecode): above it the JIT never
 * compiles the method and it runs interpreted for the whole session. Measured once already - leg
 * {@code tickjfr-celtium-2} had {@code drawPendingImmediate} at 8282 bytes, interpreted, 29.9% of the client thread.
 * The class files are read directly (a minimal parse of the constant pool and the Code attributes).
 */
class HugeMethodLimitTest {
    private static final int HUGE_METHOD_LIMIT = 8000;

    @Test
    void noRendererMethodIsTooLargeToCompile() throws IOException, URISyntaxException {
        Path root = Paths.get(VulkanContext.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertTrue(Files.isDirectory(root), "expected the compiled classes directory, got " + root);
        List<String> tooLarge = new ArrayList<>();
        int methods = 0;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                try (InputStream in = Files.newInputStream(file)) {
                    methods += scan(new DataInputStream(in), root.relativize(file).toString(), tooLarge);
                }
            }
        }
        assertTrue(methods > 1000, "the scan found only " + methods + " methods; is it reading the right directory?");
        assertTrue(tooLarge.isEmpty(), "methods over " + HUGE_METHOD_LIMIT + " bytes of bytecode: " + tooLarge);
    }

    private static int scan(DataInputStream in, String name, List<String> tooLarge) throws IOException {
        if (in.readInt() != 0xCAFEBABE) {
            return 0;
        }
        in.readUnsignedShort();
        in.readUnsignedShort();
        int count = in.readUnsignedShort();
        String[] utf8 = new String[count];
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> utf8[i] = in.readUTF();
                case 3, 4 -> in.readInt();
                case 5, 6 -> { in.readLong(); i++; }
                case 7, 8, 16, 19, 20 -> in.readUnsignedShort();
                case 9, 10, 11, 12, 17, 18 -> in.readInt();
                case 15 -> { in.readUnsignedByte(); in.readUnsignedShort(); }
                default -> throw new IOException(name + ": constant pool tag " + tag);
            }
        }
        in.readUnsignedShort();
        in.readUnsignedShort();
        in.readUnsignedShort();
        int interfaces = in.readUnsignedShort();
        for (int i = 0; i < interfaces; i++) in.readUnsignedShort();
        int fields = in.readUnsignedShort();
        for (int i = 0; i < fields; i++) {
            in.readUnsignedShort();
            in.readUnsignedShort();
            in.readUnsignedShort();
            skipAttributes(in);
        }
        int methodCount = in.readUnsignedShort();
        for (int i = 0; i < methodCount; i++) {
            in.readUnsignedShort();
            String method = utf8[in.readUnsignedShort()];
            in.readUnsignedShort();
            int attributes = in.readUnsignedShort();
            for (int a = 0; a < attributes; a++) {
                String attribute = utf8[in.readUnsignedShort()];
                int length = in.readInt();
                if ("Code".equals(attribute)) {
                    in.readUnsignedShort();
                    in.readUnsignedShort();
                    int codeLength = in.readInt();
                    if (codeLength > HUGE_METHOD_LIMIT) {
                        tooLarge.add(name + "#" + method + "=" + codeLength);
                    }
                    in.skipNBytes(length - 8);
                } else {
                    in.skipNBytes(length);
                }
            }
        }
        return methodCount;
    }

    private static void skipAttributes(DataInputStream in) throws IOException {
        int attributes = in.readUnsignedShort();
        for (int a = 0; a < attributes; a++) {
            in.readUnsignedShort();
            in.skipNBytes(in.readInt());
        }
    }
}
