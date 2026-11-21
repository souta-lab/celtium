package org.taumc.celeritas.impl.render.terrain.compile.task;

import org.embeddedt.embeddium.impl.gui.SodiumGameOptions;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the fast block renderer toggle as a persisted option.
 *
 * <p>The toggle used to read and write only a static field in {@code ChunkBuilderMeshingTask},
 * initialised from {@code -Dceleritas.useVintageFastBlockRenderer}, so a change applied on the
 * options page was in no config file and silently reverted on the next launch. The option now
 * lives in {@code performance.useFastBlockRenderer} (config key {@code use_fast_block_renderer}),
 * seeded from the same property for a fresh config. These tests pin the two ends of the wiring -
 * the mesher must read the field, and the options page must bind it - plus the seeded default,
 * because a utf8 sweep cannot pin a lambda or a field read.
 */
class FastBlockRendererOptionTest {

    private static final String OPTIONS_OWNER = "org/embeddedt/embeddium/impl/gui/SodiumGameOptions$PerformanceSettings";
    private static final String FIELD = "useFastBlockRenderer";

    @Test
    void mesherReadsTheOptionField() throws Exception {
        List<String[]> fieldRefs = fieldReferences(readClassBytes(
                "/org/taumc/celeritas/impl/render/terrain/compile/task/ChunkBuilderMeshingTask.class"));
        assertTrue(fieldRefs.stream().anyMatch(ref -> ref[0].equals(OPTIONS_OWNER) && ref[1].equals(FIELD)),
                "ChunkBuilderMeshingTask must read SodiumGameOptions.performance.useFastBlockRenderer; "
                        + "a static field leaves the GUI toggle unpersisted");
    }

    @Test
    void optionsPageBindsTheOptionField() throws Exception {
        List<String[]> fieldRefs = fieldReferences(readClassBytes(
                "/org/taumc/celeritas/impl/gui/SodiumGameOptionPages.class"));
        assertTrue(fieldRefs.stream().anyMatch(ref -> ref[0].equals(OPTIONS_OWNER) && ref[1].equals(FIELD)),
                "SodiumGameOptionPages must bind the fast block renderer row to "
                        + "SodiumGameOptions.performance.useFastBlockRenderer");
    }

    @Test
    void defaultIsSeededFromTheLegacyProperty() {
        assertEquals(Boolean.getBoolean("celeritas.useVintageFastBlockRenderer"),
                new SodiumGameOptions().performance.useFastBlockRenderer,
                "a fresh config must start from -Dceleritas.useVintageFastBlockRenderer, as the removed static did");
    }

    private static byte[] readClassBytes(String resource) throws IOException {
        try (InputStream in = FastBlockRendererOptionTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "compiled class not on the test classpath: " + resource);
            return in.readAllBytes();
        }
    }

    /** Every CONSTANT_Fieldref of the class file, as {owner internal name, field name}. */
    private static List<String[]> fieldReferences(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(bytes));
        try {
            in.readInt(); // magic
            in.readUnsignedShort(); // minor
            in.readUnsignedShort(); // major
            int count = in.readUnsignedShort();
            int[] tags = new int[count];
            Object[] values = new Object[count];
            for (int i = 1; i < count; i++) {
                int tag = in.readUnsignedByte();
                tags[i] = tag;
                switch (tag) {
                    case 1 -> values[i] = in.readUTF();
                    case 3, 4 -> in.skipBytes(4);
                    case 5, 6 -> {
                        in.skipBytes(8);
                        i++; // long/double take two slots
                    }
                    case 7, 8, 16, 19, 20 -> values[i] = in.readUnsignedShort();
                    case 9, 10, 11, 12, 17, 18 -> values[i] = new int[]{in.readUnsignedShort(), in.readUnsignedShort()};
                    case 15 -> in.skipBytes(3);
                    default -> throw new IOException("unknown constant pool tag " + tag);
                }
            }
            List<String[]> out = new ArrayList<>();
            for (int i = 1; i < count; i++) {
                if (tags[i] != 9) {
                    continue;
                }
                int[] ref = (int[]) values[i];
                int classNameIndex = (Integer) values[ref[0]];
                String owner = (String) values[classNameIndex];
                int[] nameAndType = (int[]) values[ref[1]];
                String name = (String) values[nameAndType[0]];
                out.add(new String[]{owner, name});
            }
            return out;
        } catch (EOFException e) {
            throw new IOException("truncated class file", e);
        }
    }
}
