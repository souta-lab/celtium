package org.taumc.celeritas.impl.render.terrain.compile.pipeline;

import org.embeddedt.embeddium.impl.gui.SodiumGameOptions;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the quad-normals shading option at its one construction seam.
 *
 * <p>{@code performance.useQuadNormalsForShading} (config key
 * {@code use_quad_normals_for_shading_v2}) feeds the light pipelines through
 * {@code LightPipelineProvider}'s third constructor argument. The only construction site in
 * this module, {@code VintageBlockRenderer}, passed a literal {@code true}, so the config key
 * changed nothing. A utf8 sweep cannot pin this - the literal leaves no trace - so this test
 * resolves the constant pool's field references of the compiled mesher and requires an actual
 * read of the {@code PerformanceSettings} field. It also pins the field's default, because
 * the wiring is only behaviour-preserving while the default stays {@code true}.
 */
class QuadNormalsOptionTest {

    @Test
    void mesherReadsTheOptionField() throws Exception {
        List<String[]> fieldRefs = fieldReferences(readClassBytes(
                "/org/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer.class"));
        boolean found = fieldRefs.stream().anyMatch(ref ->
                ref[0].equals("org/embeddedt/embeddium/impl/gui/SodiumGameOptions$PerformanceSettings")
                        && ref[1].equals("useQuadNormalsForShading"));
        assertTrue(found,
                "VintageBlockRenderer must read SodiumGameOptions.performance.useQuadNormalsForShading "
                        + "when constructing LightPipelineProvider; a literal leaves the config key dead");
    }

    @Test
    void quadNormalsDefaultStaysOn() {
        assertTrue(new SodiumGameOptions().performance.useQuadNormalsForShading,
                "wiring the option must not change default shading");
    }

    private static byte[] readClassBytes(String resource) throws IOException {
        try (InputStream in = QuadNormalsOptionTest.class.getResourceAsStream(resource)) {
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
