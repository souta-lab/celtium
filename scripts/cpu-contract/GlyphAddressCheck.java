import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

/**
 * Verifies the pointer contract the glyph capture depends on, without a GPU.
 * <p>
 * {@code MemoryUtil.memAddress(ByteBuffer)} returns the buffer's base address plus its POSITION. The capture filled
 * its vertex buffer and then took the address without flipping, so the position equalled the capacity and the backend
 * was handed a pointer one past the last written byte: glyphs were accepted and the text was missing or garbage, with
 * no validation error, because nothing was out of bounds from Vulkan's point of view - it was the wrong bytes.
 * <p>
 * Run: javac -cp <lwjgl-core.jar> -d /tmp scripts/cpu-contract/GlyphAddressCheck.java
 *      java -Dorg.lwjgl.librarypath=<dir with liblwjgl.so> -cp /tmp:<lwjgl-core.jar> GlyphAddressCheck
 * Expected: memAddress(buf) - memAddress(buf,0) equals the position (96 here), and 0 after flip(), with the first
 * written vertex recovered from the flipped address.
 */
public class GlyphAddressCheck {
    public static void main(String[] args) {
        ByteBuffer b = MemoryUtil.memAlloc(96);
        b.clear();
        for (int i = 0; i < 4; i++) {
            b.putFloat(1.0f + i).putFloat(2.0f).putFloat(0.0f).putFloat(0.1f * i).putFloat(0.2f)
             .put((byte) 0x11).put((byte) 0x22).put((byte) 0x33).put((byte) 0xFF);
        }
        long base = MemoryUtil.memAddress(b, 0);
        long afterWrite = MemoryUtil.memAddress(b);
        System.out.println("position=" + b.position() + " capacity=" + b.capacity());
        System.out.println("memAddress(buf) - memAddress(buf,0) = " + (afterWrite - base));
        b.flip();
        long afterFlip = MemoryUtil.memAddress(b);
        System.out.println("after flip: position=" + b.position() + " limit=" + b.limit()
                + " delta=" + (afterFlip - base));
        // Recover the first written vertex from the address the backend would be given.
        System.out.println("first vertex x=" + Float.intBitsToFloat(MemoryUtil.memGetInt(afterFlip))
                + " expected 1.0");
        MemoryUtil.memFree(b);
    }
}
