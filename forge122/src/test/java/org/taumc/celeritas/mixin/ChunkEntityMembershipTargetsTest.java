package org.taumc.celeritas.mixin;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pins the SRG targets of {@code ChunkEntityMembershipMixin}'s injections to the membership boundary
 * its design states, read from the compiled class bytes the way Mixin reads them.
 *
 * <p>The boundary is a list of SRG names, and a wrong name fails silently: the handler attaches to a
 * different method and the cache keeps working through whatever coverage remains. That is exactly
 * what shipped until 2026-11-22 - the list named {@code func_76631_c} where {@code removeEntity} was
 * meant. {@code func_76631_c} is {@code Chunk.onLoad}; {@code removeEntity} is {@code func_76622_b}
 * (mcp_stable/39 methods.csv). The mislabel was masked because {@code removeEntity} delegates to
 * {@code removeEntityAtIndex} ({@code func_76608_a}, which was hooked) and because the accidental
 * {@code onLoad} hook covered the load boundary the design also wants. The names below are the
 * intended set: addEntity, removeEntity, removeEntityAtIndex, onLoad (load boundary), onUnload.
 *
 * <p>The class file is parsed directly, with no ASM, for the same reason as
 * {@link MixinUniqueVisibilityTest}: the test classpath's ASM 5.2 cannot read this project's
 * class files.
 */
class ChunkEntityMembershipTargetsTest {

    private static final String MIXIN_RESOURCE =
            "/org/taumc/celeritas/mixin/core/terrain/ChunkEntityMembershipMixin.class";

    private static final Set<String> EXPECTED_TARGETS = Set.of(
            "func_76612_a", // Chunk.addEntity
            "func_76622_b", // Chunk.removeEntity
            "func_76608_a", // Chunk.removeEntityAtIndex
            "func_76631_c", // Chunk.onLoad (load boundary)
            "func_76623_d"  // Chunk.onUnload
    );

    @Test
    void membershipMixinHooksExactlyTheStatedBoundary() throws Exception {
        Set<String> found = new TreeSet<>();
        for (String utf8 : constantPoolUtf8(readMixinBytes())) {
            if (utf8.matches("func_\\d+_[a-z]")) {
                found.add(utf8);
            }
        }
        assertEquals(new TreeSet<>(EXPECTED_TARGETS), found,
                "ChunkEntityMembershipMixin's SRG targets drifted from the stated membership boundary");
    }

    private static byte[] readMixinBytes() throws IOException {
        try (InputStream in = ChunkEntityMembershipTargetsTest.class.getResourceAsStream(MIXIN_RESOURCE)) {
            assertNotNull(in, "compiled mixin class not on the test classpath: " + MIXIN_RESOURCE);
            return in.readAllBytes();
        }
    }

    /** Every CONSTANT_Utf8 entry of the class file's constant pool, in order. */
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
