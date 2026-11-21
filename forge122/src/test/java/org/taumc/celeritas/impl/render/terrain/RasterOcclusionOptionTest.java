package org.taumc.celeritas.impl.render.terrain;

import org.embeddedt.embeddium.impl.gui.SodiumGameOptions;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the raster-occlusion option's one seam.
 *
 * <p>The toggle is bound in the GUI and saved, and the machinery behind it is real
 * (SectionGraph, SectionLattice and OcclusionCuller all take the flag and only build the
 * RasterOccluder when it is set) - but the single input to that chain is
 * {@code VintageRenderSectionManager.useRasterOcclusionCulling()}, which returned a constant
 * {@code false} from the commit that implemented the feature onward, so the option changed
 * nothing. A utf8 sweep cannot pin this: the override method and the option field share the
 * name {@code useRasterOcclusionCulling}, so the class file mentions it either way. This test
 * resolves the constant pool's field references and requires an actual read of the
 * {@code PerformanceSettings} field. It also pins the field's default, because the wiring is
 * only behaviour-preserving for existing users while the default stays off.
 */
class RasterOcclusionOptionTest {

    @Test
    void sectionManagerReadsTheOptionField() throws Exception {
        List<String[]> fieldRefs = fieldReferences(readClassBytes(
                "/org/taumc/celeritas/impl/render/terrain/VintageRenderSectionManager.class"));
        boolean found = fieldRefs.stream().anyMatch(ref ->
                ref[0].equals("org/embeddedt/embeddium/impl/gui/SodiumGameOptions$PerformanceSettings")
                        && ref[1].equals("useRasterOcclusionCulling"));
        assertTrue(found,
                "VintageRenderSectionManager must read SodiumGameOptions.performance.useRasterOcclusionCulling; "
                        + "a constant return leaves the GUI toggle with no effect");
    }

    @Test
    void rasterOcclusionDefaultsToOff() {
        assertFalse(new SodiumGameOptions().performance.useRasterOcclusionCulling,
                "wiring the option must not change default behaviour");
    }

    private static byte[] readClassBytes(String resource) throws IOException {
        try (InputStream in = RasterOcclusionOptionTest.class.getResourceAsStream(resource)) {
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
