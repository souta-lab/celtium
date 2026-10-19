package org.soutalab.celtium.vulkan;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts the renderer's own immediate-path vertices into the attributes a shaderpack's program declares.
 * <p>
 * A pack expects OptiFine's attribute set - {@code a_Position}, {@code a_Colour}, {@code a_TexCoord0}, {@code a_Normal},
 * {@code mc_Entity}, {@code mc_midTexCoord} - while the game hands the immediate path one of the formats in
 * {@link VulkanImmediateFormats} (position, colour, texture coordinates, lightmap, a packed normal). Drawing terrain with
 * a pack means producing the former from the latter, and this is that conversion, kept pure so the mapping can be tested
 * without a GPU.
 * <p>
 * The attributes a pack declares but the game's format does not carry are filled with the values that make the shader
 * behave like the shaderless path: {@code mc_Entity} and {@code mc_midTexCoord} are zero (no block identity), and a
 * missing normal is {@code (0, 0, 1)} so lighting resolves to the face-on case rather than to black.
 */
public final class ShaderPackVertexAdapter {
    /** Attribute names a pack's vertex stage may use, in the order this class produces them. */
    public static final String[] ATTRIBUTES = {"a_Position", "a_Colour", "a_TexCoord0", "a_Normal", "mc_Entity",
            "mc_midTexCoord"};

    private ShaderPackVertexAdapter() {
    }

    /** {@return the byte offset of each element of an immediate format, or null when the format is not converted}. */
    private static int[] offsets(int formatId) {
        return switch (formatId) {
            // pos3f col4ub tex2f lightmap2s - the last element is the lightmap, not a normal, so a_Normal stays
            // unfilled (the test caught it reading 100/127 from the lightmap's first coordinate)
            case 3 -> new int[] {0, 12, 16, -1, -1, -1};
            // pos3f tex2f col4ub
            case 2 -> new int[] {0, 20, 12, -1, -1, -1};
            // pos3f tex2f
            case 6 -> new int[] {0, -1, 12, -1, -1, -1};
            // pos3f tex2f col4ub nrm3b pad1b
            case 8 -> new int[] {0, 20, 12, 24, -1, -1};
            default -> null;
        };
    }

    /** {@return the component count of each attribute}, matching {@link #ATTRIBUTES}. */
    private static int[] components() {
        return new int[] {4, 4, 4, 3, 4, 4};
    }

    /**
     * Converts the renderer's own terrain vertices ({@code CompactChunkVertex}, 24 bytes) to the pack's attributes.
     * <p>
     * The terrain format is packed for bandwidth, not for shaders: positions and texture coordinates are split into a
     * high half in their own 16-bit field and a low half collected in one packed field, and the draw parameters and light
     * share a 32-bit word. The layout and the encode order are the ones {@code CompactChunkVertex.createEncoder} writes,
     * which is what makes this a decode rather than a guess:
     * <pre>
     *   +0  u16 x &gt;&gt; 5      +2  u16 y &gt;&gt; 5      +4  u16 z &gt;&gt; 5
     *   +6  u16 (x &amp; 31) | ((y &amp; 31) &lt;&lt; 5) | ((z &amp; 31) &lt;&lt; 10)
     *   +8  u32 colour     +12 u16 u &gt;&gt; 2      +14 u16 v &gt;&gt; 2
     *   +16 u32 drawParameters (material | u low &lt;&lt; 4 | v low &lt;&lt; 6 | section &lt;&lt; 8) | light &lt;&lt; 16
     *   +20 i32 rdhFactor
     * </pre>
     * Positions decode to the chunk-local space the encoder quantised (origin 8, range 32); the caller supplies the
     * section offset when it needs world coordinates.
     *
     * @return the pack attributes, or null when the vertex count or stride is not the terrain format
     */
    public static Map<String, float[]> adaptTerrain(ByteBuffer source, int stride, int vertexCount) {
        if (source == null || vertexCount <= 0 || stride < 24) {
            return null;
        }
        int[] counts = components();
        Map<String, float[]> result = new LinkedHashMap<>();
        for (int attribute = 0; attribute < ATTRIBUTES.length; attribute++) {
            result.put(ATTRIBUTES[attribute], new float[vertexCount * counts[attribute]]);
        }
        // The quantisation constants of the format: positions are stored as (origin + value) * scale, textures as
        // round(value * 2^17).
        final float modelOrigin = 8.0f;
        final float modelScaleInv = (float) (1 << 21) / 32.0f;
        final float textureScale = 1.0f / (float) (1 << 17);
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            int base = vertex * stride;
            int xHigh = source.getShort(base) & 0xFFFF;
            int yHigh = source.getShort(base + 2) & 0xFFFF;
            int zHigh = source.getShort(base + 4) & 0xFFFF;
            int low = source.getShort(base + 6) & 0xFFFF;
            int x = (xHigh << 5) | (low & 0x1F);
            int y = (yHigh << 5) | ((low >> 5) & 0x1F);
            int z = (zHigh << 5) | ((low >> 10) & 0x1F);
            float[] position = result.get("a_Position");
            position[vertex * 4] = x / modelScaleInv - modelOrigin;
            position[vertex * 4 + 1] = y / modelScaleInv - modelOrigin;
            position[vertex * 4 + 2] = z / modelScaleInv - modelOrigin;
            position[vertex * 4 + 3] = 1.0f;

            int colour = source.getInt(base + 8);
            float[] colourOut = result.get("a_Colour");
            // The format stores the colour as the four bytes GL's client array carries, so the byte order is the one the
            // renderer's own shader reads: red in the low byte.
            colourOut[vertex * 4] = (colour & 0xFF) / 255.0f;
            colourOut[vertex * 4 + 1] = ((colour >> 8) & 0xFF) / 255.0f;
            colourOut[vertex * 4 + 2] = ((colour >> 16) & 0xFF) / 255.0f;
            colourOut[vertex * 4 + 3] = ((colour >> 24) & 0xFF) / 255.0f;

            int drawParameters = source.getInt(base + 16);
            int u = ((source.getShort(base + 12) & 0xFFFF) << 2) | ((drawParameters >> 4) & 0x3);
            int v = ((source.getShort(base + 14) & 0xFFFF) << 2) | ((drawParameters >> 6) & 0x3);
            float[] tex = result.get("a_TexCoord0");
            tex[vertex * 4] = u * textureScale;
            tex[vertex * 4 + 1] = v * textureScale;
            tex[vertex * 4 + 3] = 1.0f;

            // The packed light rides in the upper half: block light in the low byte, sky light in the next.
            int light = (drawParameters >>> 16) & 0xFFFF;
            float[] lm = result.get("mc_midTexCoord");
            lm[vertex * 4] = (light & 0xFF) / 255.0f;
            lm[vertex * 4 + 1] = ((light >> 8) & 0xFF) / 255.0f;

            // The format carries no normal; the face-on default is what makes the shader light the surface rather than
            // blacking it out.
            result.get("a_Normal")[vertex * 3 + 2] = 1.0f;
        }
        return result;
    }

    /**
     * Converts {@code vertexCount} vertices.
     *
     * @param formatId the immediate format the source vertices use
     * @param source   the vertices, {@code vertexCount * stride} bytes from the buffer's position
     * @param stride   the source vertex stride
     * @return attribute name to its per-vertex components, or null when the format is not one this class converts
     */
    public static Map<String, float[]> adapt(int formatId, ByteBuffer source, int stride, int vertexCount) {
        int[] offsets = offsets(formatId);
        if (offsets == null) {
            return null;
        }
        int[] counts = components();
        Map<String, float[]> result = new LinkedHashMap<>();
        for (int attribute = 0; attribute < ATTRIBUTES.length; attribute++) {
            result.put(ATTRIBUTES[attribute], new float[vertexCount * counts[attribute]]);
        }
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            int base = vertex * stride;
            float[] position = result.get("a_Position");
            position[vertex * 4] = source.getFloat(base + offsets[0]);
            position[vertex * 4 + 1] = source.getFloat(base + offsets[0] + 4);
            position[vertex * 4 + 2] = source.getFloat(base + offsets[0] + 8);
            position[vertex * 4 + 3] = 1.0f;

            float[] colour = result.get("a_Colour");
            if (offsets[1] >= 0) {
                for (int component = 0; component < 4; component++) {
                    colour[vertex * 4 + component] = (source.get(base + offsets[1] + component) & 0xFF) / 255.0f;
                }
            } else {
                // No colour in the format: opaque white, which is what the shaderless path draws with when the batch
                // carries no colour - a black default would make surfaces vanish rather than look unlit.
                colour[vertex * 4] = 1.0f;
                colour[vertex * 4 + 1] = 1.0f;
                colour[vertex * 4 + 2] = 1.0f;
                colour[vertex * 4 + 3] = 1.0f;
            }

            float[] tex = result.get("a_TexCoord0");
            if (offsets[2] >= 0) {
                tex[vertex * 4] = source.getFloat(base + offsets[2]);
                tex[vertex * 4 + 1] = source.getFloat(base + offsets[2] + 4);
            }
            tex[vertex * 4 + 3] = 1.0f;

            float[] normal = result.get("a_Normal");
            if (offsets[3] >= 0) {
                normal[vertex * 3] = source.get(base + offsets[3]) / 127.0f;
                normal[vertex * 3 + 1] = source.get(base + offsets[3] + 1) / 127.0f;
                normal[vertex * 3 + 2] = source.get(base + offsets[3] + 2) / 127.0f;
            } else {
                normal[vertex * 3 + 2] = 1.0f;
            }
            // mc_Entity and mc_midTexCoord stay zero: the immediate path carries no block identity.
        }
        return result;
    }
}
