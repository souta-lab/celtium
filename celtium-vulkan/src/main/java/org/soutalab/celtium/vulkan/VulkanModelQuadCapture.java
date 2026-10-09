package org.soutalab.celtium.vulkan;

/**
 * Captures the quads Minecraft compiles into model display lists, which is the geometry this renderer cannot see.
 * <p>
 * {@code ModelRenderer} compiles each model part's {@code TexturedQuad}s into a GL display list once and replays it with
 * {@code GlStateManager.callList}; the replay never touches {@code WorldVertexBufferUploader}, so the immediate funnel
 * misses it - measured, the entity layout has zero arrivals even in third person where the player model is drawn, while
 * the replay census sees the sky and stars twice a frame and the player's ten model parts when the player is visible.
 * <p>
 * The capture is deliberately a measurement first: it records what the quads contain (vertices, texture coordinates and
 * the plane the normal is derived from) and how many arrive, without changing a single pixel of the rendering. Drawing
 * them natively needs two matrices - the one baked in at compile time, exactly as GL bakes it into the list, and the one
 * current at replay, which is the entity's transform - and the backend already mirrors both.
 */
public final class VulkanModelQuadCapture {

    /** Per-display-list geometry, keyed by the list GL is compiling into. */
    private static final java.util.LinkedHashMap<Integer, ListGeometry> LISTS =
            new java.util.LinkedHashMap<Integer, ListGeometry>();

    private static final class ListGeometry {
        int quads;
        int listId;
        float[] modelView = new float[16];
        String source = "unknown";
        /** Model-space vertex data, three floats per vertex, four vertices per quad, in submission order. */
        float[] positions = new float[4 * 3 * 64];
        /** Texture coordinates, two floats per vertex. */
        float[] uvs = new float[4 * 2 * 64];
        /** The plane normal the quad was drawn with, three floats per quad. */
        float[] normals = new float[3 * 64];
        int vertices;

        void ensure() {
            if (quads * 4 + 4 <= positions.length / 3) {
                return;
            }
            positions = java.util.Arrays.copyOf(positions, positions.length * 2);
            uvs = java.util.Arrays.copyOf(uvs, uvs.length * 2);
            normals = java.util.Arrays.copyOf(normals, normals.length * 2);
        }
    }

    private static long quads;
    private static long vertices;
    private static float minX = Float.MAX_VALUE;
    private static float maxX = -Float.MAX_VALUE;
    private static float minU = Float.MAX_VALUE;
    private static float maxU = -Float.MAX_VALUE;
    private static boolean reportedFirst;
    private static String firstSummary = "none";

    private VulkanModelQuadCapture() {
    }

    /**
     * Records one quad.
     *
     * @param count how many vertices the quad has (four in every vanilla model)
     * @param xs    x of each vertex, already scaled by the model's scale
     * @param us    u of each vertex
     */
    /**
     * Groups the quads by the display list GL is compiling into and keeps the modelview matrix of the first quad of
     * each list.
     * <p>
     * The list id comes from {@code GL_LIST_INDEX}, which names the list being compiled (0 when none is). The matrix is
     * the one in effect while the part is compiled - a display list stores transformed vertices, so the geometry has to
     * be paired with the transform it was compiled under. Both are read here rather than assumed.
     */
    public static synchronized void quad(int count, float[] xs, float[] us, float[] ys, float[] zs,
            float nx, float ny, float nz) {
        int listId = 0;
        try {
            listId = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_LIST_INDEX);
        } catch (RuntimeException | LinkageError ignored) {
            listId = -2;
        }
        ListGeometry geometry = LISTS.get(Integer.valueOf(listId));
        if (geometry == null) {
            geometry = new ListGeometry();
            geometry.listId = listId;
            try {
                java.nio.FloatBuffer matrix = org.lwjgl.BufferUtils.createFloatBuffer(16);
                org.lwjgl.opengl.GL11.glGetFloatv(org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX, matrix);
                matrix.get(geometry.modelView);
            } catch (RuntimeException | LinkageError ignored) {
                geometry.source = "matrix unavailable";
            }
            LISTS.put(Integer.valueOf(listId), geometry);
        }
        geometry.ensure();
        int base = geometry.quads * 4;
        for (int i = 0; i < count && i < 4; i++) {
            geometry.positions[(base + i) * 3] = xs[i];
            geometry.positions[(base + i) * 3 + 1] = ys[i];
            geometry.positions[(base + i) * 3 + 2] = zs[i];
            geometry.uvs[(base + i) * 2] = us[i];
        }
        int normalBase = geometry.quads * 3;
        geometry.normals[normalBase] = nx;
        geometry.normals[normalBase + 1] = ny;
        geometry.normals[normalBase + 2] = nz;
        geometry.vertices += count;
        geometry.quads++;
        quads++;
        vertices += count;
        for (int i = 0; i < count; i++) {
            minX = Math.min(minX, xs[i]);
            maxX = Math.max(maxX, xs[i]);
            minU = Math.min(minU, us[i]);
            maxU = Math.max(maxU, us[i]);
        }
        if (!reportedFirst) {
            reportedFirst = true;
            firstSummary = "vertices=" + count + " x=" + round(xs[0]) + ".." + round(xs[count - 1])
                    + " u=" + round(us[0]) + ".." + round(us[count - 1]);
            System.out.println("[Celtium] CELTIUM_MODEL_QUAD first: " + firstSummary);
        }
    }

    private static String round(float value) {
        return String.valueOf(Math.round(value * 1000.0f) / 1000.0f);
    }

    /** {@return the total number of stored vertex floats, for the native path's buffer sizing}. */
    public static synchronized long storedFloats() {
        long total = 0;
        for (ListGeometry geometry : LISTS.values()) {
            total += (long) geometry.quads * 4 * 5 + (long) geometry.quads * 3;
        }
        return total;
    }

    /** {@return the largest list's first stored vertex, as a shape sanity check}. */
    public static synchronized String firstStoredVertex() {
        ListGeometry largest = null;
        for (ListGeometry geometry : LISTS.values()) {
            if (largest == null || geometry.quads > largest.quads) {
                largest = geometry;
            }
        }
        if (largest == null || largest.quads == 0) {
            return "none";
        }
        return "list " + largest.listId + " v0=(" + round(largest.positions[0]) + "," + round(largest.positions[1]) + ","
                + round(largest.positions[2]) + ") uv0=(" + round(largest.uvs[0]) + ") n0=("
                + round(largest.normals[0]) + "," + round(largest.normals[1]) + "," + round(largest.normals[2]) + ")";
    }


    /**
     * {@return a comparison of the compile-time and replay-time modelview matrices for the captured lists}.
     * <p>
     * Translation and scale are enough to see the relationship: if the replay matrix is the compile matrix preceded by
     * an entity transform, the translations differ; if the list baked the transform, they agree.
     */
    public static synchronized String matrixComparison() {
        StringBuilder text = new StringBuilder();
        int shown = 0;
        for (ListGeometry geometry : LISTS.values()) {
            if (shown >= 3) {
                break;
            }
            float[] replay = VulkanDisplayListCensus.replayMatrix(geometry.listId);
            if (replay == null) {
                continue;
            }
            shown++;
            if (text.length() > 0) {
                text.append(" | ");
            }
            text.append("list ").append(geometry.listId)
                    .append(" compile(t=").append(round(geometry.modelView[12])).append(',')
                    .append(round(geometry.modelView[13])).append(',').append(round(geometry.modelView[14])).append(')')
                    .append(" replay(t=").append(round(replay[12])).append(',').append(round(replay[13])).append(',')
                    .append(round(replay[14])).append(')');
        }
        return text.length() == 0 ? "no list seen both ways" : text.toString();
    }

    /** {@return how many distinct display lists the captured quads belong to}. */
    public static synchronized int lists() {
        return LISTS.size();
    }

    /** {@return per-list quad counts, largest first, capped for one summary line}. */
    public static synchronized String listSummary() {
        if (LISTS.isEmpty()) {
            return "none";
        }
        java.util.List<ListGeometry> all = new java.util.ArrayList<ListGeometry>(LISTS.values());
        all.sort((a, b) -> b.quads - a.quads);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < Math.min(all.size(), 8); i++) {
            if (i > 0) {
                text.append(' ');
            }
            text.append(all.get(i).listId).append(':').append(all.get(i).quads);
        }
        if (all.size() > 8) {
            text.append(" +").append(all.size() - 8).append(" more");
        }
        return text.toString();
    }

    /** {@return how many quads have been captured}. */
    public static synchronized long quads() {
        return quads;
    }

    /** {@return how many vertices those quads carried}. */
    public static synchronized long vertices() {
        return vertices;
    }

    /** {@return a one-line description of the captured extent, for the run summary}. */
    public static synchronized String summary() {
        if (quads == 0) {
            return "none";
        }
        return quads + " quads/" + vertices + " verts x[" + round(minX) + ".." + round(maxX) + "] u["
                + round(minU) + ".." + round(maxU) + "]";
    }

    /** {@return the first captured quad's shape}. */
    public static synchronized String firstSummary() {
        return firstSummary;
    }
}
