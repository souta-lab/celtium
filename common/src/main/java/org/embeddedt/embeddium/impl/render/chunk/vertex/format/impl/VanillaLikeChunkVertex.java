package org.embeddedt.embeddium.impl.render.chunk.vertex.format.impl;

import org.embeddedt.embeddium.impl.gl.attribute.GlVertexAttributeFormat;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import static org.taumc.celeritas.lwjgl.LWJGLServiceProvider.LWJGL;

/**
 * This vertex format is less performant and uses more VRAM than {@link CompactChunkVertex}, but should be completely
 * compatible with mods & resource packs that need high precision for models.
 */
public class VanillaLikeChunkVertex implements ChunkVertexType {
    private static final boolean LIGHT_DIAGNOSTICS =
            Boolean.getBoolean("celtium.vulkan.lightDiagnostics");
    private static int lightSamplesPrinted;
    private static long colorCount, colorR, colorG, colorB, colorA;
    private static int colorPrints;
    /** Per material-pass totals: [count, r, g, b, a]. */
    private static final java.util.Map<Integer, long[]> COLOR_TOTALS = new java.util.concurrent.ConcurrentHashMap<>();

    public static final int STRIDE = 32;

    public static final GlVertexFormat VERTEX_FORMAT = GlVertexFormat.builder(STRIDE)
            .addElement("a_PosId", 0, GlVertexAttributeFormat.FLOAT, 3, false, false)
            .addElement("a_Color", 12, GlVertexAttributeFormat.UNSIGNED_BYTE, 4, true, false)
            .addElement("a_TexCoord", 16, GlVertexAttributeFormat.FLOAT, 2, false, false)
            .addElement("a_LightCoord", 24, GlVertexAttributeFormat.UNSIGNED_INT, 1, false, true)
            .addElement("a_RdhFactor", 28, GlVertexAttributeFormat.BYTE, 4, true, false)
            .build();

    @Override
    public float getPositionScale() {
        return 1f;
    }

    @Override
    public float getPositionOffset() {
        return 0;
    }

    @Override
    public float getTextureScale() {
        return 1f;
    }

    @Override
    public GlVertexFormat getVertexFormat() {
        return VERTEX_FORMAT;
    }

    @Override
    public ChunkVertexEncoder createEncoder() {
        return (ptr, material, vertex, sectionIndex) -> {
            LWJGL.memPutFloat(ptr + 0, vertex.x);
            LWJGL.memPutFloat(ptr + 4, vertex.y);
            LWJGL.memPutFloat(ptr + 8, vertex.z);
            if (LIGHT_DIAGNOSTICS) {
                // The int is Minecraft's 0xAABBGGRR packing, so in little-endian memory the bytes are R,G,B,A - which is
                // exactly what VK_FORMAT_R8G8B8A8_UNORM reads. Decoding it the other way round labels A as "r" and R as "a",
                // which is how this diagnostic used to print and why the numbers looked wrong.
                // Aggregated per pass as well: the whole-scene mean is dominated by sky and water, so it cannot say what
                // the terrain's colour is - and the terrain is what the reference comparison disagrees about.
                int c = vertex.color;
                int r = c & 0xFF, g = (c >> 8) & 0xFF, b = (c >> 16) & 0xFF, a = (c >> 24) & 0xFF;
                long[] total = COLOR_TOTALS.computeIfAbsent(material.bits() & 0xFF, k -> new long[5]);
                total[0]++; total[1] += r; total[2] += g; total[3] += b; total[4] += a;
                colorCount++;
                if (colorCount % 20000 == 0 && colorPrints < 6) {
                    colorPrints++;
                    StringBuilder text = new StringBuilder("[Celtium] CELTIUM_VERTEX_COLOR_BY_PASS n=" + colorCount);
                    for (var entry : COLOR_TOTALS.entrySet()) {
                        long[] t = entry.getValue();
                        if (t[0] == 0) continue;
                        text.append(" pass").append(entry.getKey()).append("=").append(t[0]).append(":")
                                .append(t[1] / t[0]).append(",").append(t[2] / t[0]).append(",")
                                .append(t[3] / t[0]).append(",").append(t[4] / t[0]);
                    }
                    System.out.println(text);
                }
            }
            LWJGL.memPutInt(ptr + 12, vertex.color);
            LWJGL.memPutFloat(ptr + 16, encodeTexture(vertex.u));
            LWJGL.memPutFloat(ptr + 20, encodeTexture(vertex.v));
            if (LIGHT_DIAGNOSTICS && lightSamplesPrinted < 8) {
                lightSamplesPrinted++;
                System.out.println("[Celtium] CELTIUM_VERTEX_LIGHT format=vanillalike raw=" + vertex.light
                        + " block=" + (vertex.light & 0xFF) + " sky=" + ((vertex.light >> 16) & 0xFF)
                        + " encoded=" + encodeLight(vertex.light));
            }
            LWJGL.memPutInt(ptr + 24, (encodeDrawParameters(material, sectionIndex) << 0) | (encodeLight(vertex.light) << 16));
            LWJGL.memPutInt(ptr + 28, vertex.rdhFactor);

            return ptr + STRIDE;
        };
    }

    private static int encodeDrawParameters(Material material, int sectionIndex) {
        return (((sectionIndex & 0xFF) << 8) | ((material.bits() & 0xFF) << 0));
    }

    private static int encodeLight(int light) {
        int block = light & 0xFF;
        int sky = (light >> 16) & 0xFF;
        return ((block << 0) | (sky << 8));
    }

    private static float encodeTexture(float value) {
        return Math.min(0.99999997F, value);
    }
}
