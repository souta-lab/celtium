import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

/**
 * Verifies the pointer and byte contract the glyph capture depends on, without a GPU.
 * <p>
 * {@code MemoryUtil.memAddress(ByteBuffer)} returns the buffer's base address plus its POSITION. The capture filled
 * its vertex buffer and then took the address without flipping, so the position equalled the capacity and the backend
 * was handed a pointer one past the last written byte: glyphs were accepted and the text was missing or garbage, with
 * no validation error, because nothing was out of bounds from Vulkan's point of view - it was the wrong bytes.
 * <p>
 * This check covers both layouts the capture offers, with the write order, the address-taking and the extent check the
 * producer itself uses, and it reads every field of every vertex back from the address the backend is given:
 * <ul>
 *   <li>normal: {@code POSITION_TEX_COLOR}, stride 24 - 3 floats position, 2 floats UV, 4 unsigned bytes colour;</li>
 *   <li>isolation probe: {@code POSITION_COLOR}, stride 16 - 3 floats position, 4 unsigned bytes colour at offset 12.</li>
 * </ul>
 * It also demonstrates the defect it exists for: the unflipped address reads bytes that are not the captured ones.
 * <p>
 * Run: javac -cp &lt;lwjgl-core.jar&gt; -d /tmp scripts/cpu-contract/GlyphAddressCheck.java
 *      java -Dorg.lwjgl.librarypath=&lt;dir with liblwjgl.so&gt; -cp /tmp:&lt;lwjgl-core.jar&gt; GlyphAddressCheck
 * Prints {@code CELTIUM_CPU_CONTRACT PASS} and exits 0 when every recovered field equals the captured one, and exits
 * non-zero with the mismatches otherwise.
 */
public class GlyphAddressCheck {
    private static final int MAX_VERTICES = 4;
    private static final int STRIDE = 24;
    private static final int POSITION_COLOR_STRIDE = 16;
    private static int failures;

    public static void main(String[] args) {
        // The four glyph vertices vanilla emits for one character, with the field order the capture writes.
        float[][] position = {
                {175.0f, 71.0f, 0.0f}, {175.0f, 78.99f, 0.0f}, {179.99f, 71.0f, 0.0f}, {179.99f, 78.99f, 0.0f},
        };
        float[][] uv = {
                {0.75f, 0.25f}, {0.75f, 0.31242188f}, {0.78898436f, 0.25f}, {0.78898436f, 0.31242188f},
        };
        int red = 63;
        int green = 63;
        int blue = 63;
        int alpha = 255;

        checkNormal(position, uv, red, green, blue, alpha);
        checkIsolation(position, red, green, blue, alpha);
        checkUnflippedReadsOtherBytes();

        if (failures == 0) {
            System.out.println("CELTIUM_CPU_CONTRACT PASS");
            return;
        }
        System.out.println("CELTIUM_CPU_CONTRACT FAIL: " + failures + " mismatch(es)");
        System.exit(1);
    }

    /** The shipped path: POSITION_TEX_COLOR, written and addressed exactly as {@code submit()} does it. */
    private static void checkNormal(float[][] position, float[][] uv, int red, int green, int blue, int alpha) {
        ByteBuffer vertex = MemoryUtil.memAlloc(STRIDE * MAX_VERTICES);
        vertex.clear();
        for (int i = 0; i < MAX_VERTICES; i++) {
            vertex.putFloat(position[i][0]);
            vertex.putFloat(position[i][1]);
            vertex.putFloat(position[i][2]);
            vertex.putFloat(uv[i][0]);
            vertex.putFloat(uv[i][1]);
            vertex.put((byte) red);
            vertex.put((byte) green);
            vertex.put((byte) blue);
            vertex.put((byte) alpha);
        }
        long base = MemoryUtil.memAddress(vertex, 0);
        System.out.println("normal: position=" + vertex.position() + " capacity=" + vertex.capacity()
                + " memAddress(buf)-memAddress(buf,0)=" + (MemoryUtil.memAddress(vertex) - base));
        vertex.flip();
        report("normal: written extent is exactly " + MAX_VERTICES + "x" + STRIDE,
                vertex.position() == 0 && vertex.limit() == MAX_VERTICES * STRIDE,
                "position=" + vertex.position() + " limit=" + vertex.limit());
        long address = MemoryUtil.memAddress(vertex);
        System.out.println("normal: offered address is the first written byte: delta=" + (address - base));
        for (int i = 0; i < MAX_VERTICES; i++) {
            long at = address + (long) i * STRIDE;
            report("normal: vertex " + i + " position", equal(position[i][0], MemoryUtil.memGetFloat(at))
                    && equal(position[i][1], MemoryUtil.memGetFloat(at + 4))
                    && equal(position[i][2], MemoryUtil.memGetFloat(at + 8)),
                    MemoryUtil.memGetFloat(at) + "," + MemoryUtil.memGetFloat(at + 4) + ","
                            + MemoryUtil.memGetFloat(at + 8));
            report("normal: vertex " + i + " uv", equal(uv[i][0], MemoryUtil.memGetFloat(at + 12))
                    && equal(uv[i][1], MemoryUtil.memGetFloat(at + 16)),
                    MemoryUtil.memGetFloat(at + 12) + "," + MemoryUtil.memGetFloat(at + 16));
            report("normal: vertex " + i + " colour", byteAt(MemoryUtil.memGetByte(at + 20)) == red
                    && byteAt(MemoryUtil.memGetByte(at + 21)) == green
                    && byteAt(MemoryUtil.memGetByte(at + 22)) == blue
                    && byteAt(MemoryUtil.memGetByte(at + 23)) == alpha,
                    byteAt(MemoryUtil.memGetByte(at + 20)) + "," + byteAt(MemoryUtil.memGetByte(at + 21)) + ","
                            + byteAt(MemoryUtil.memGetByte(at + 22)) + "," + byteAt(MemoryUtil.memGetByte(at + 23)));
        }
        MemoryUtil.memFree(vertex);
    }

    /** The isolation probe's path: POSITION_COLOR, colour at offset 12, addressed the same way. */
    private static void checkIsolation(float[][] position, int red, int green, int blue, int alpha) {
        ByteBuffer isolated = MemoryUtil.memAlloc(POSITION_COLOR_STRIDE * MAX_VERTICES);
        isolated.clear();
        for (int i = 0; i < MAX_VERTICES; i++) {
            isolated.putFloat(position[i][0]);
            isolated.putFloat(position[i][1]);
            isolated.putFloat(position[i][2]);
            isolated.put((byte) red);
            isolated.put((byte) green);
            isolated.put((byte) blue);
            isolated.put((byte) alpha);
        }
        isolated.flip();
        report("isolation: written extent is exactly " + MAX_VERTICES + "x" + POSITION_COLOR_STRIDE,
                isolated.position() == 0 && isolated.limit() == MAX_VERTICES * POSITION_COLOR_STRIDE,
                "position=" + isolated.position() + " limit=" + isolated.limit());
        long address = MemoryUtil.memAddress(isolated);
        for (int i = 0; i < MAX_VERTICES; i++) {
            long at = address + (long) i * POSITION_COLOR_STRIDE;
            report("isolation: vertex " + i + " position", equal(position[i][0], MemoryUtil.memGetFloat(at))
                    && equal(position[i][1], MemoryUtil.memGetFloat(at + 4))
                    && equal(position[i][2], MemoryUtil.memGetFloat(at + 8)),
                    MemoryUtil.memGetFloat(at) + "," + MemoryUtil.memGetFloat(at + 4) + ","
                            + MemoryUtil.memGetFloat(at + 8));
            report("isolation: vertex " + i + " colour", byteAt(MemoryUtil.memGetByte(at + 12)) == red
                    && byteAt(MemoryUtil.memGetByte(at + 13)) == green
                    && byteAt(MemoryUtil.memGetByte(at + 14)) == blue
                    && byteAt(MemoryUtil.memGetByte(at + 15)) == alpha,
                    byteAt(MemoryUtil.memGetByte(at + 12)) + "," + byteAt(MemoryUtil.memGetByte(at + 13)) + ","
                            + byteAt(MemoryUtil.memGetByte(at + 14)) + "," + byteAt(MemoryUtil.memGetByte(at + 15)));
        }
        MemoryUtil.memFree(isolated);
    }

    /**
     * The defect itself, so the check cannot pass by accident: the unflipped address is one past the written bytes, so
     * what it reads is not the captured geometry. Distinct values make that visible without depending on what happens
     * to follow the allocation.
     */
    private static void checkUnflippedReadsOtherBytes() {
        ByteBuffer buffer = MemoryUtil.memAlloc(STRIDE * MAX_VERTICES + 32);
        buffer.clear();
        for (int i = 0; i < MAX_VERTICES; i++) {
            for (int field = 0; field < 6; field++) {
                buffer.putFloat(1000.0f + i * 10 + field);
            }
        }
        long base = MemoryUtil.memAddress(buffer, 0);
        long unflipped = MemoryUtil.memAddress(buffer);
        float firstBeforeFlip = MemoryUtil.memGetFloat(unflipped);
        buffer.flip();
        float firstAfterFlip = MemoryUtil.memGetFloat(MemoryUtil.memAddress(buffer));
        System.out.println("defect: unflipped delta=" + (unflipped - base) + " reads " + firstBeforeFlip
                + ", flipped delta=" + (MemoryUtil.memAddress(buffer) - base) + " reads " + firstAfterFlip);
        report("defect: the unflipped address does not read the first written vertex", firstBeforeFlip != 1000.0f,
                "read " + firstBeforeFlip);
        report("defect: the flipped address reads the first written vertex", firstAfterFlip == 1000.0f,
                "read " + firstAfterFlip);
        MemoryUtil.memFree(buffer);
    }

    private static void report(String what, boolean ok, String detail) {
        if (!ok) {
            failures++;
        }
        System.out.println((ok ? "  ok   " : "  FAIL ") + what + " (" + detail + ")");
    }

    private static boolean equal(float a, float b) {
        return Math.abs(a - b) < 0.0001f;
    }

    private static int byteAt(byte value) {
        return value & 0xFF;
    }
}
