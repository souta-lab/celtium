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
 * Pins the fast block renderer toggle's three-way wiring.
 *
 * <p>The toggle originally read and wrote only a static field in {@code ChunkBuilderMeshingTask},
 * initialised from {@code -Dceleritas.useVintageFastBlockRenderer}, so a change applied on the
 * options page was in no config file and silently reverted on the next launch. Persisting it by
 * simply deleting the static broke a compat surface instead: Demonica's guard writes that field
 * from the class's initialiser to force the fast renderer off, and a stock Demonica install then
 * crashed with {@code NoSuchFieldError} before the world loaded. The settled shape keeps all three
 * ends: the value persists in {@code performance.useFastBlockRenderer} (config key
 * {@code use_fast_block_renderer}, seeded from the same property for a fresh config), the static
 * {@code USE_NEW_BLOCK_RENDERER} is restored and seeded from the persisted value in the meshing
 * task's own initialiser - so a compat write injected at the end of that initialiser lands after
 * the seed and wins - the mesher reads the static, and the options page's binding writes both (a
 * GUI change applies at once and persists) while its getter reads the static, the runtime truth.
 * These tests pin the ends of that wiring, because a utf8 sweep cannot pin a lambda or a field read.
 */
class FastBlockRendererOptionTest {

    private static final String OPTIONS_OWNER = "org/embeddedt/embeddium/impl/gui/SodiumGameOptions$PerformanceSettings";
    private static final String FIELD = "useFastBlockRenderer";
    private static final String MESHER_OWNER = "org/taumc/celeritas/impl/render/terrain/compile/task/ChunkBuilderMeshingTask";
    private static final String STATIC_FIELD = "USE_NEW_BLOCK_RENDERER";

    @Test
    void mesherSeedsTheStaticFromTheOptionFieldAndReadsTheStatic() throws Exception {
        List<String[]> fieldRefs = fieldReferences(readClassBytes(
                "/org/taumc/celeritas/impl/render/terrain/compile/task/ChunkBuilderMeshingTask.class"));
        assertTrue(fieldRefs.stream().anyMatch(ref -> ref[0].equals(OPTIONS_OWNER) && ref[1].equals(FIELD)),
                "ChunkBuilderMeshingTask's initialiser must seed USE_NEW_BLOCK_RENDERER from "
                        + "SodiumGameOptions.performance.useFastBlockRenderer");
        assertTrue(fieldRefs.stream().anyMatch(ref -> ref[0].equals(MESHER_OWNER) && ref[1].equals(STATIC_FIELD)),
                "ChunkBuilderMeshingTask must carry and read USE_NEW_BLOCK_RENDERER itself: the field is a "
                        + "binary-compat surface (Demonica's guard writes it), and the mesher reads it");
    }

    @Test
    void optionsPageBindsBothTheOptionFieldAndTheStatic() throws Exception {
        List<String[]> fieldRefs = fieldReferences(readClassBytes(
                "/org/taumc/celeritas/impl/gui/SodiumGameOptionPages.class"));
        assertTrue(fieldRefs.stream().anyMatch(ref -> ref[0].equals(OPTIONS_OWNER) && ref[1].equals(FIELD)),
                "SodiumGameOptionPages must write SodiumGameOptions.performance.useFastBlockRenderer "
                        + "so the toggle persists");
        assertTrue(fieldRefs.stream().anyMatch(ref -> ref[0].equals(MESHER_OWNER) && ref[1].equals(STATIC_FIELD)),
                "SodiumGameOptionPages must also write/read ChunkBuilderMeshingTask.USE_NEW_BLOCK_RENDERER "
                        + "so a GUI change applies at once and the row shows the runtime value");
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
