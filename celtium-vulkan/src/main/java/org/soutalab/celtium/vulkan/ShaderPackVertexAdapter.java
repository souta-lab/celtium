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
