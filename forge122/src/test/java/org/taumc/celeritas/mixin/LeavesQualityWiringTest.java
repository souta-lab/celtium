package org.taumc.celeritas.mixin;

import org.embeddedt.embeddium.impl.gui.SodiumGameOptions;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the leaves-quality wiring end to end.
 *
 * <p>The option spent its life bound in the video-settings GUI with no consumer anywhere:
 * vanilla drives {@code BlockLeaves.leavesFancy} from the raw graphics mode inside
 * {@code RenderGlobal.loadRenderers}, and nothing ever substituted the celtium setting.
 * The wiring is: {@code SodiumGameOptions.QualitySettings.leavesQuality} (this test pins the
 * field and its default), the GUI binding in {@code SodiumGameOptionPages} (pinned by the
 * option name and lang keys in its constant pool), and {@code RenderGlobalMixin}'s RETURN
 * inject in loadRenderers rewriting the flag via {@code setGraphicsLevel} (pinned by the
 * handler name and the {@code setGraphicsLevel}/{@code leavesQuality} references in its
 * constant pool, read from the compiled class bytes the way Mixin reads them).
 */
class LeavesQualityWiringTest {

    @Test
    void graphicsQualityResolvesAgainstVanillaMode() {
        assertTrue(SodiumGameOptions.GraphicsQuality.DEFAULT.isFancy(true));
        assertFalse(SodiumGameOptions.GraphicsQuality.DEFAULT.isFancy(false));
        assertFalse(SodiumGameOptions.GraphicsQuality.FAST.isFancy(true));
        assertFalse(SodiumGameOptions.GraphicsQuality.FAST.isFancy(false));
        assertTrue(SodiumGameOptions.GraphicsQuality.FANCY.isFancy(true));
        assertTrue(SodiumGameOptions.GraphicsQuality.FANCY.isFancy(false));
    }

    @Test
    void leavesQualityDefaultsToVanillaBehaviour() {
        assertEquals(SodiumGameOptions.GraphicsQuality.DEFAULT,
                new SodiumGameOptions().quality.leavesQuality);
    }

    @Test
    void renderGlobalMixinAppliesTheOptionAtRendererReload() throws Exception {
        Set<String> utf8 = constantPoolUtf8(readClassBytes(
                "/org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin.class"));
        assertTrue(utf8.contains("celtium$applyLeavesQuality"),
                "RenderGlobalMixin must carry the leaves-quality inject");
        assertTrue(utf8.contains("func_72712_a"),
                "the inject must name the SRG alias of loadRenderers, not only the MCP name");
        assertTrue(utf8.contains("setGraphicsLevel"),
                "the inject must drive BlockLeaves.setGraphicsLevel, the flag the mesher reads");
        assertTrue(utf8.contains("leavesQuality"),
                "the inject must read the leavesQuality option");
    }

    @Test
    void optionPagesBindLeavesQuality() throws Exception {
        Set<String> utf8 = constantPoolUtf8(readClassBytes(
                "/org/taumc/celeritas/impl/gui/SodiumGameOptionPages.class"));
        assertTrue(utf8.contains("leavesQuality"),
                "SodiumGameOptionPages must bind the leavesQuality field");
        assertTrue(utf8.contains("sodium.options.leaves_quality.name"),
                "the binding must use the leaves quality lang keys");
    }

    private static byte[] readClassBytes(String resource) throws IOException {
        try (InputStream in = LeavesQualityWiringTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "compiled class not on the test classpath: " + resource);
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
