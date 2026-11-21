package org.taumc.celeritas.mixin;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@code RenderItemMixin}'s target names to the 1.12.2 mapping set, read from the compiled
 * class bytes the way Mixin reads them.
 *
 * <p>Until 2026-11-22 the handler listed {@code func_175032_a}, a name that exists in no 1.12.2
 * mapping file (mcp_stable/39: methods.csv, joined.srg and the rfg_srgs set were all checked).
 * {@code RenderItem.renderQuads} is {@code func_191970_a} (srg-mcp.srg). The mixin refmap is empty,
 * so in the SRG runtime the MCP alias {@code renderQuads} cannot match either - the handler was
 * dead in production while alive in dev, the same silent shape as
 * {@link ChunkEntityMembershipTargetsTest}'s boundary. The class file is parsed directly, with no
 * ASM, for the reason given there.
 */
class RenderItemTargetsTest {

    private static final String MIXIN_RESOURCE =
            "/org/taumc/celeritas/mixin/features/textures/RenderItemMixin.class";

    @Test
    void renderItemMixinNamesTheRealSrgTarget() throws Exception {
        Set<String> utf8 = constantPoolUtf8(readMixinBytes());
        assertTrue(utf8.contains("func_191970_a"),
                "RenderItemMixin must name func_191970_a (RenderItem.renderQuads, 1.12.2 SRG)");
        assertTrue(utf8.contains("renderQuads"),
                "RenderItemMixin must keep the MCP alias renderQuads for dev runtimes");
        assertFalse(utf8.contains("func_175032_a"),
                "func_175032_a exists in no 1.12.2 mapping file; it must not come back");
    }

    private static byte[] readMixinBytes() throws IOException {
        try (InputStream in = RenderItemTargetsTest.class.getResourceAsStream(MIXIN_RESOURCE)) {
            assertNotNull(in, "compiled mixin class not on the test classpath: " + MIXIN_RESOURCE);
            return in.readAllBytes();
        }
    }

    /** Every CONSTANT_Utf8 entry of the class file's constant pool. */
    private static Set<String> constantPoolUtf8(byte[] bytes) throws IOException {
        Set<String> out = new HashSet<>();
        DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(bytes));
        try {
            in.readInt(); // magic
            in.readUnsignedShort(); // minor
            in.readUnsignedShort(); // major
            int count = in.readUnsignedShort();
            for (int i = 1; i < count; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> out.add(in.readUTF());
                    case 3, 4 -> in.skipBytes(4);
                    case 5, 6 -> {
                        in.skipBytes(8);
                        i++; // long/double take two slots
                    }
                    case 7, 8, 16, 19, 20 -> in.skipBytes(2);
                    case 9, 10, 11, 12, 17, 18 -> in.skipBytes(4);
                    case 15 -> in.skipBytes(3);
                    default -> throw new IOException("unknown constant pool tag " + tag);
                }
            }
        } catch (EOFException e) {
            throw new IOException("truncated class file", e);
        }
        return out;
    }
}
