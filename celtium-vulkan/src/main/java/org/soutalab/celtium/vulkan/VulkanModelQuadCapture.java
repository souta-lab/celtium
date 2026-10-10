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
 * <p>
 * The geometry's identity is read from GL rather than assumed: the list being compiled and the bound texture both come
 * from the state the GL-less stub records when the game calls {@code glNewList} and {@code glBindTexture}. Under a real
 * context the capture behaves exactly as before. A quad whose list or texture cannot be established, and a quad whose
 * four vertices carry no area, are refused with a named reason and counted instead of being filed under an id no replay
 * can find.
 */
public final class VulkanModelQuadCapture {

    /** What a GL query that did not answer returns. Zero is a real answer, so it cannot mean "unknown". */
    private static final int UNKNOWN = Integer.MIN_VALUE;

    /** Refusal reasons, in the order the counters are reported. */
    private static final int REFUSED_UNKNOWN_LIST = 0;
    private static final int REFUSED_UNKNOWN_TEXTURE = 1;
    private static final int REFUSED_DEGENERATE = 2;
    private static final String[] REFUSAL_NAMES = { "unknown-list", "unknown-texture", "degenerate-quad" };
    private static final long[] REFUSALS = new long[REFUSAL_NAMES.length];
    /** One print per reason is the whole story; the counters carry the rest. */
    private static final java.util.Set<Integer> REFUSAL_REPORTED = new java.util.HashSet<Integer>();

    /** The call site the vertices arrive from, named from the stack when the first quad appears. */
    private static String source = "not seen yet";
    private static boolean sourceReported;

    /** Per-display-list geometry, keyed by the list GL is compiling into. */
    private static final java.util.LinkedHashMap<Integer, ListGeometry> LISTS =
            new java.util.LinkedHashMap<Integer, ListGeometry>();

    private static final class ListGeometry {
        int quads;
        int listId;
        /** The texture the list's first captured quad arrived under, or {@link #UNKNOWN}. */
        int texture = UNKNOWN;
        float[] modelView = new float[16];
        /** Where the vertices came from, named from the stack rather than assumed. */
        String source = "unknown";
        /** Model-space vertex data, three floats per vertex, four vertices per quad, in submission order. */
        float[] positions = new float[4 * 3 * 64];
        /** Texture coordinates, two floats per vertex. */
        float[] uvs = new float[4 * 2 * 64];
        /** The plane normal the quad was drawn with, three floats per quad. */
        float[] normals = new float[3 * 64];
        int vertices;
        /** Quads whose modelview differs from the list's first quad, which is the one the list keeps. */
        int quadsWithOtherMatrix;
        /** Quads whose bound texture differs from the list's first quad, for the same reason. */
        int quadsWithOtherTexture;

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
     * Groups the quads by the display list GL is compiling into and keeps the modelview matrix and the texture of the
     * first quad of each list.
     * <p>
     * The identity is read, not assumed. The list id comes from {@code GL_LIST_INDEX} - the list {@code glNewList}
     * opened, 0 when none is being compiled - and the texture from {@code GL_TEXTURE_BINDING_2D} on the unit
     * {@code GL_ACTIVE_TEXTURE} names. Under the stub both answers are the game's own recorded calls rather than a
     * context's leftovers, which is what makes them usable at all: while {@code GL_LIST_INDEX} answered 0 for
     * everything, every quad landed in list 0 and no replay could ever find its mesh.
     * <p>
     * The vertices arrive as Java arrays: the capture hook on {@code TexturedQuad.draw} reads
     * {@code TexturedQuad.vertexPositions} - the model's own objects - and passes them here, exactly the values the
     * vanilla body submits to the Tessellator. Nothing in this class reads a GL buffer, so nothing here depends on the
     * stub's buffer store; what the stub does decide is the identity, and a quad whose list or texture cannot be
     * established is refused and counted rather than filed under an id no replay can find.
     *
     * @param count how many vertices the quad has (four in every vanilla model)
     * @param xs    x of each vertex, already scaled by the model's scale
     * @param us    u of each vertex
     */
    public static synchronized void quad(int count, float[] xs, float[] ys, float[] zs, float[] us, float[] vs,
            float nx, float ny, float nz) {
        celtium$noteSourceOnce();
        boolean glless = org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled();
        int listId = celtium$glInt(org.lwjgl.opengl.GL11.GL_LIST_INDEX);
        int texture = UNKNOWN;
        if (glless) {
            if (listId <= 0) {
                // "No list is being compiled" is an answer, not an identity: a quad filed under list 0 is one no
                // callList can ever reach, which is exactly the state this capture started in.
                celtium$refuse(REFUSED_UNKNOWN_LIST,
                        "list=" + celtium$value(listId) + " " + celtium$textureDiagnostic(), count);
                return;
            }
            texture = celtium$glInt(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
            if (texture <= 0) {
                // A model quad is drawn with a texture bound (the arm above is compiled under the player's 64x64 skin),
                // so a name of 0 here is the read failing, not a textureless draw.
                celtium$refuse(REFUSED_UNKNOWN_TEXTURE,
                        "list=" + listId + " texture=" + celtium$value(texture) + " " + celtium$textureDiagnostic(),
                        count);
                return;
            }
            String unusable = celtium$unusable(count, xs, ys, zs, us, vs);
            if (unusable != null) {
                // A model face is a rectangle in one plane, so one constant coordinate is normal - the first quad this
                // capture saw had x constant at 0.063 and the normal (1,0,0) that says it is a box's +X face. What is
                // refused here is the area the four vertices span, not a coordinate.
                celtium$refuse(REFUSED_DEGENERATE, "list=" + listId + " " + unusable, count);
                return;
            }
        } else if (listId == UNKNOWN) {
            // The GL bridge keeps the behaviour it has always had: a query that fails is list 0, which is what the old
            // catch around this read produced. The identity rules above are the GL-less path's, where the stub answers
            // from what the game actually did.
            listId = 0;
        }
        ListGeometry geometry = LISTS.get(Integer.valueOf(listId));
        if (geometry == null) {
            geometry = new ListGeometry();
            geometry.listId = listId;
            geometry.texture = texture;
            geometry.source = source;
            try {
                java.nio.FloatBuffer matrix = org.lwjgl.BufferUtils.createFloatBuffer(16);
                org.lwjgl.opengl.GL11.glGetFloatv(org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX, matrix);
                matrix.get(geometry.modelView);
            } catch (RuntimeException | LinkageError ignored) {
                // A missing matrix is not a wrong quad; matrixForList then reports the identity, exactly as before.
            }
            LISTS.put(Integer.valueOf(listId), geometry);
        } else {
            if (texture != UNKNOWN && texture != geometry.texture) {
                // One texture per list, the first quad's, the same approximation the matrix keeps. A model part binds
                // one texture; counting the quads that arrive under another says how much of that approximation is real.
                geometry.quadsWithOtherTexture++;
            }
            // The capture keeps one matrix per list, the first quad's - but a list is compiled part by part and each part
            // can set its own transform. Counting the quads whose matrix differs from the first says how much of the model
            // path's residual that approximation can explain.
            try {
                java.nio.FloatBuffer matrix = org.lwjgl.BufferUtils.createFloatBuffer(16);
                org.lwjgl.opengl.GL11.glGetFloatv(org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX, matrix);
                float[] now = new float[16];
                matrix.get(now);
                boolean same = true;
                for (int i = 0; i < 16; i++) {
                    if (Math.abs(now[i] - geometry.modelView[i]) > 1e-4f) {
                        same = false;
                        break;
                    }
                }
                if (!same) {
                    geometry.quadsWithOtherMatrix++;
                }
            } catch (RuntimeException | LinkageError ignored) {
                // A missing count is not a wrong draw.
            }
        }
        geometry.ensure();
        int base = geometry.quads * 4;
        for (int i = 0; i < count && i < 4; i++) {
            geometry.positions[(base + i) * 3] = xs[i];
            geometry.positions[(base + i) * 3 + 1] = ys[i];
            geometry.positions[(base + i) * 3 + 2] = zs[i];
            geometry.uvs[(base + i) * 2] = us[i];
            geometry.uvs[(base + i) * 2 + 1] = vs[i];
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
            // The identity and the normal are printed with the shape: a constant coordinate alone reads as a degenerate
            // quad, while a normal of (1,0,0) next to x=0.063 says it is a box's +X face, which is what the first quad
            // this capture ever saw was (the right arm's box, compiled under the player's 64x64 skin).
            firstSummary = "vertices=" + count + " list=" + listId + " texture=" + celtium$value(texture)
                    + " x=" + round(xs[0]) + ".." + round(xs[count - 1]) + " u=" + round(us[0]) + ".."
                    + round(us[count - 1]) + " n=(" + round(nx) + "," + round(ny) + "," + round(nz) + ")";
            System.out.println("[Celtium] CELTIUM_MODEL_QUAD first: " + firstSummary);
        }
    }

    /**
     * {@return the GL integer for {@code pname}, or {@link #UNKNOWN} when GL could not answer}.
     * <p>
     * Under the stub these are the recorded values themselves: {@code GL_LIST_INDEX} is the list {@code glNewList}
     * opened and {@code GL_TEXTURE_BINDING_2D} the name {@code glBindTexture} put on the active unit.
     */
    private static int celtium$glInt(int pname) {
        try {
            return org.lwjgl.opengl.GL11.glGetInteger(pname);
        } catch (RuntimeException | LinkageError error) {
            return UNKNOWN;
        }
    }

    /** {@return an int as text, with the sentinel spelled out rather than printed as a number}. */
    private static String celtium$value(int value) {
        return value == UNKNOWN ? "unknown" : String.valueOf(value);
    }

    /** {@return how the texture was read, so a refusal says which unit and which name it found}. */
    private static String celtium$textureDiagnostic() {
        int unit = celtium$glInt(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
        int texture = celtium$glInt(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
        return "read=GL_TEXTURE_BINDING_2D unit=" + celtium$value(unit) + " texture=" + celtium$value(texture);
    }

    /**
     * {@return why the submitted vertices cannot be geometry, or {@code null} when they can}.
     * <p>
     * The test is the area the vertices span against their own longest edge: a box face is a rectangle in one plane, so
     * a constant x (or y, or z) is legitimate - the first quad captured had x constant at 0.063, which is 1/16 in model
     * units and the +X face of the right arm - while a quad with no area covers no pixel and would only put a
     * degenerate mesh into the native path. Vertices that cannot be used at all (too few, more than the four the mesh
     * layout holds, arrays shorter than the count, or not finite) are refused under the same reason: both mean the
     * arguments hold no geometry.
     */
    private static String celtium$unusable(int count, float[] xs, float[] ys, float[] zs, float[] us, float[] vs) {
        if (count < 3 || count > 4 || xs == null || ys == null || zs == null || us == null || vs == null
                || xs.length < count || ys.length < count || zs.length < count || us.length < count
                || vs.length < count) {
            return "detail=vertices-unusable count=" + count;
        }
        for (int i = 0; i < count; i++) {
            if (!Float.isFinite(xs[i]) || !Float.isFinite(ys[i]) || !Float.isFinite(zs[i])) {
                return "detail=vertices-unusable vertex=" + i + " value=(" + xs[i] + "," + ys[i] + "," + zs[i] + ")";
            }
        }
        // The two triangles of the fan triangulation: both flat means the quad covers nothing.
        double first = celtium$triangleArea(count, 0, 1, 2, xs, ys, zs);
        double second = celtium$triangleArea(count, 0, 2, count - 1, xs, ys, zs);
        double longest = 0.0;
        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;
            double dx = (double) xs[next] - xs[i];
            double dy = (double) ys[next] - ys[i];
            double dz = (double) zs[next] - zs[i];
            longest = Math.max(longest, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        // Relative, so a legitimately small face (a sixteenth of a block is 0.0625 on a side) is not mistaken for a
        // sliver, while a quad whose area is a billionth of its own edge squared is.
        if (longest <= 0.0 || first + second <= 1.0e-9 * longest * longest) {
            return "detail=zero-area longestEdge=" + round((float) longest) + " area=" + round((float) (first + second));
        }
        return null;
    }

    /** {@return the area of one triangle of the quad, or 0 when the indices do not name three distinct vertices}. */
    private static double celtium$triangleArea(int count, int a, int b, int c, float[] xs, float[] ys, float[] zs) {
        if (a >= count || b >= count || c >= count || a == b || b == c || a == c) {
            return 0.0;
        }
        double abx = (double) xs[b] - xs[a], aby = (double) ys[b] - ys[a], abz = (double) zs[b] - zs[a];
        double acx = (double) xs[c] - xs[a], acy = (double) ys[c] - ys[a], acz = (double) zs[c] - zs[a];
        double cx = aby * acz - abz * acy;
        double cy = abz * acx - abx * acz;
        double cz = abx * acy - aby * acx;
        return 0.5 * Math.sqrt(cx * cx + cy * cy + cz * cz);
    }

    /** Records one refused quad, with the reason, the first call site of each reason and a periodic count. */
    private static void celtium$refuse(int reason, String detail, int count) {
        REFUSALS[reason]++;
        if (REFUSAL_REPORTED.add(Integer.valueOf(reason))) {
            System.out.println("[Celtium] CELTIUM_MODEL_QUAD_REFUSED reason=" + REFUSAL_NAMES[reason] + " " + detail
                    + " vertices=" + count + " callSite=" + celtium$callSite(4)
                    + " (kept out of the census: " + refusals() + ")");
        }
        long refused = REFUSALS[REFUSED_UNKNOWN_LIST] + REFUSALS[REFUSED_UNKNOWN_TEXTURE]
                + REFUSALS[REFUSED_DEGENERATE];
        if (refused > 0 && refused % 500 == 0) {
            System.out.println("[Celtium] CELTIUM_MODEL_QUAD_REFUSALS " + refusals());
        }
    }

    /** Names the call site the vertices arrive from, once, so the source is read from the stack and not assumed. */
    private static void celtium$noteSourceOnce() {
        if (sourceReported) {
            return;
        }
        sourceReported = true;
        source = celtium$callSite(4);
        // The vertices are the caller's float arrays - the model's own PositionTextureVertex data - and no GL buffer is
        // read to get them. Saying so here makes the next run answer "where did they come from" without a debugger.
        System.out.println("[Celtium] CELTIUM_MODEL_QUAD_SOURCE data=java-float-arrays-no-gl-buffer-read source=" + source);
    }

    /** {@return up to {@code frames} stack frames naming where the capture was entered from}. */
    private static String celtium$callSite(int frames) {
        StackTraceElement[] trace = new Throwable().getStackTrace();
        StringBuilder text = new StringBuilder();
        int taken = 0;
        for (StackTraceElement frame : trace) {
            if (frame.getClassName().startsWith("org.soutalab.celtium.vulkan.VulkanModelQuadCapture")) {
                continue;
            }
            if (taken > 0) {
                text.append(" <- ");
            }
            text.append(frame.getClassName()).append('.').append(frame.getMethodName());
            if (++taken >= frames) {
                break;
            }
        }
        return taken == 0 ? "unknown" : text.toString();
    }

    private static String round(float value) {
        return String.valueOf(Math.round(value * 1000.0f) / 1000.0f);
    }

    /** One list's geometry, copied out so the renderer can build a mesh without holding the capture's lock. */
    public static final class Snapshot {
        public final int listId;
        public final int quads;
        public final float[] positions;
        public final float[] uvs;

        Snapshot(int listId, int quads, float[] positions, float[] uvs) {
            this.listId = listId;
            this.quads = quads;
            this.positions = positions;
            this.uvs = uvs;
        }
    }

    /** {@return whether this list's geometry has been captured}. */
    public static synchronized boolean hasList(int listId) {
        return LISTS.containsKey(Integer.valueOf(listId));
    }

    /**
     * {@return the geometry of one captured list, or null}.
     * <p>
     * The positions come back in model space, exactly as {@code TexturedQuad.draw} submitted them, and the caller
     * applies the matrix that is current at replay - measured, not assumed: the same list shows a different translation
     * at compile and replay time while still drawing correctly, so a display list is replayed under the current matrix.
     */
    public static synchronized Snapshot snapshot(int listId) {
        ListGeometry geometry = LISTS.get(Integer.valueOf(listId));
        if (geometry == null || geometry.quads == 0) {
            return null;
        }
        int floats = geometry.quads * 4;
        float[] positions = new float[floats * 3];
        float[] uvs = new float[floats * 2];
        System.arraycopy(geometry.positions, 0, positions, 0, positions.length);
        System.arraycopy(geometry.uvs, 0, uvs, 0, uvs.length);
        return new Snapshot(geometry.listId, geometry.quads, positions, uvs);
    }

    /** {@return every captured list id, for the renderer to walk}. */
    public static synchronized int[] listIds() {
        int[] ids = new int[LISTS.size()];
        int i = 0;
        for (Integer id : LISTS.keySet()) {
            ids[i++] = id.intValue();
        }
        return ids;
    }

    /** {@return the total number of stored vertex floats, for the native path's buffer sizing}. */
    public static synchronized long storedFloats() {
        long total = 0;
        for (ListGeometry geometry : LISTS.values()) {
            total += (long) geometry.quads * 4 * 5 + (long) geometry.quads * 3;
        }
        return total;
    }

    /** {@return the translation of the first stored list's compile-time modelview, as "x,y,z"}. */
    public static synchronized String firstStoredMatrixTranslation() {
        for (ListGeometry geometry : LISTS.values()) {
            return round(geometry.modelView[12]) + "," + round(geometry.modelView[13]) + ","
                    + round(geometry.modelView[14]);
        }
        return "none";
    }

    /**
     * {@return a copy of the modelview a list was compiled under}, or {@code null} when that list was never seen.
     * <p>
     * The stored vertices are in model space (measured: a part ranges over about +/-0.6), so the matrix they pair with is
     * this one - the transform in effect when the list was compiled - and not the one in effect at replay, which for the
     * model path carried no entity translation at all.
     */
    public static synchronized float[] matrixForList(int listId) {
        ListGeometry geometry = LISTS.get(Integer.valueOf(listId));
        if (geometry == null) {
            return null;
        }
        return geometry.modelView.clone();
    }

    /**
     * {@return the coordinate range of the largest list's stored vertices and its compile-time translation}.
     * <p>
     * Which space the vertices are in is the question the model path's residual turns on: a range of a few units means
     * model space and the replay matrix has to place them, a range of tens or hundreds means world space and it must not.
     */
    public static synchronized String storedBoundsSummary() {
        ListGeometry largest = null;
        for (ListGeometry geometry : LISTS.values()) {
            if (largest == null || geometry.vertices > largest.vertices) {
                largest = geometry;
            }
        }
        if (largest == null || largest.vertices == 0) {
            return "no vertices";
        }
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < largest.vertices; i++) {
            float x = largest.positions[i * 3], y = largest.positions[i * 3 + 1], z = largest.positions[i * 3 + 2];
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
        }
        int others = 0;
        int otherTextures = 0;
        for (ListGeometry geometry : LISTS.values()) {
            others += geometry.quadsWithOtherMatrix;
            otherTextures += geometry.quadsWithOtherTexture;
        }
        return "list " + largest.listId + " vertices=" + largest.vertices
                + " x=" + round(minX) + ".." + round(maxX) + " y=" + round(minY) + ".." + round(maxY)
                + " z=" + round(minZ) + ".." + round(maxZ)
                + " compileT=(" + round(largest.modelView[12]) + "," + round(largest.modelView[13]) + ","
                + round(largest.modelView[14]) + ") quadsWithOtherMatrix=" + others + "/" + quads
                + " quadsWithOtherTexture=" + otherTextures + "/" + quads;
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
                + round(largest.positions[2]) + ") uv0=(" + round(largest.uvs[0]) + "," + round(largest.uvs[1]) + ") n0=("
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

    /** {@return the four counters, as one string for the run summary}. */
    public static synchronized String refusals() {
        return "captured=" + quads + " refusedUnknownList=" + REFUSALS[REFUSED_UNKNOWN_LIST]
                + " refusedUnknownTexture=" + REFUSALS[REFUSED_UNKNOWN_TEXTURE]
                + " refusedDegenerate=" + REFUSALS[REFUSED_DEGENERATE];
    }

    /** {@return quads refused because the display list being compiled could not be established}. */
    public static synchronized long refusedUnknownList() {
        return REFUSALS[REFUSED_UNKNOWN_LIST];
    }

    /** {@return quads refused because the texture bound at capture could not be established}. */
    public static synchronized long refusedUnknownTexture() {
        return REFUSALS[REFUSED_UNKNOWN_TEXTURE];
    }

    /** {@return quads refused because their vertices span no area}. */
    public static synchronized long refusedDegenerate() {
        return REFUSALS[REFUSED_DEGENERATE];
    }

    /** {@return the call site the captured vertices arrived from, named from the stack}. */
    public static synchronized String source() {
        return source;
    }

    /**
     * {@return the texture this list's first captured quad arrived under, or 0 when it is not known}.
     * <p>
     * This is the capture-time binding, read from the same recorded state the backend's replay-time read uses. The
     * backend still asks GL at replay (a display list does not bake the texture in), so this is the value a native draw
     * can be cross-checked against rather than the one it draws with.
     */
    public static synchronized int textureForList(int listId) {
        ListGeometry geometry = LISTS.get(Integer.valueOf(listId));
        return geometry == null || geometry.texture == UNKNOWN || geometry.texture <= 0 ? 0 : geometry.texture;
    }

    /** {@return a one-line description of the captured extent, for the run summary}. */
    public static synchronized String summary() {
        long refused = REFUSALS[REFUSED_UNKNOWN_LIST] + REFUSALS[REFUSED_UNKNOWN_TEXTURE]
                + REFUSALS[REFUSED_DEGENERATE];
        if (quads == 0 && refused == 0) {
            return "none";
        }
        String counts = refusals();
        if (quads == 0) {
            // Every quad was refused: the counters are the whole result, so they are reported where the "none" used to
            // be - a run whose capture is entirely refused must not look like a run that saw no quads at all.
            return "no quads " + counts + " source=" + source;
        }
        return quads + " quads/" + vertices + " verts x[" + round(minX) + ".." + round(maxX) + "] u["
                + round(minU) + ".." + round(maxU) + "] " + counts + " source=" + source;
    }

    /** {@return the first captured quad's shape}. */
    public static synchronized String firstSummary() {
        return firstSummary;
    }
}
