package org.soutalab.celtium.vulkan;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Counts the display-list replays the game performs, which is the drawing this renderer cannot see.
 * <p>
 * Entity, player, item and TESR models are compiled into GL display lists by {@code ModelRenderer} and replayed with
 * {@code GlStateManager.callList}; the sky and the stars are three more such lists built by {@code RenderGlobal}. None of
 * that passes through {@code WorldVertexBufferUploader}, so the immediate funnel never sees it - measured: the entity
 * layout (format 8) has zero arrivals in every scene, including third person where the player model is drawn.
 * <p>
 * This is a measurement, not a renderer: it records how often each list is replayed so the size of the missing path is
 * known before anything is moved. The holder is MC-free on purpose - the mixin lives in the Forge module and only writes
 * these numbers.
 * <p>
 * An id that is not a list - 0, or the -1 the game passes for a list it never built - is not a list identity and is
 * counted as unknown rather than filed under a key of its own; the same rule applies to a compile site whose list could
 * not be read. What the per-list maps hold is only what carries an identity a replay can be matched against.
 */
public final class VulkanDisplayListCensus {

    private static final Map<Integer, Integer> REPLAYS = new LinkedHashMap<Integer, Integer>();
    private static long total;
    private static int distinctLists;
    private static boolean reportedFirst;
    /** The value a GL query that did not answer returns. Zero is a real answer, so it cannot mean "unknown". */
    private static final int UNKNOWN = Integer.MIN_VALUE;

    private VulkanDisplayListCensus() {
    }

    /**
     * Replays whose id was not a display list at all, and compile sites whose list never could be established.
     * <p>
     * GL's {@code glGenLists} hands out positive names, 0 means "nothing is being compiled" and the game passes -1 for
     * a list it never built ({@code ModelRenderer.displayList} starts at -1 and is replayed that way until the part is
     * compiled). Filing those under their own key made -1 the busiest entry in the per-list table, which is an identity
     * no draw can ever use; they are counted as unknown instead, which is also why {@code summary()} reports them.
     */
    private static long replaysWithUnknownList;
    private static long originsWithUnknownList;
    private static final Map<String, Integer> UNKNOWN_ORIGIN_REASONS = new LinkedHashMap<String, Integer>();

    /** The texture bound when a list was compiled and when it was replayed, plus the lists whose read found none. */
    private static final Map<Integer, Integer> COMPILE_TEXTURES = new LinkedHashMap<Integer, Integer>();
    private static final Map<Integer, Integer> REPLAY_TEXTURES = new LinkedHashMap<Integer, Integer>();
    private static final Map<Integer, Integer> REPLAY_TEXTURE_UNITS = new LinkedHashMap<Integer, Integer>();
    private static final java.util.Set<Integer> UNKNOWN_TEXTURE_LISTS = new java.util.HashSet<Integer>();

    private static final Map<Integer, String> ORIGINS = new LinkedHashMap<Integer, String>();

    /**
     * Records where a display list's geometry came from, the first time that list is compiled into.
     * <p>
     * The id alone cannot decide whether a list may be cancelled - a mod that allocates a list first shifts every id -
     * but the call site can: a list compiled from {@code RenderGlobal.renderSky} is the sky, which the immediate path
     * already draws as {@code POSITION_TEX}, while a list compiled from {@code ModelRenderer} is a model part, which
     * does not have a native pipeline yet. This is the identity the cancellation rule needs.
     */
    public static synchronized void originIfAbsent(int list, String origin) {
        if (list <= 0) {
            // A compile site with no list id. The uploader only calls this while it believes a list is being compiled,
            // so a non-positive id here is an identity that could not be established: counted as unknown and kept out of
            // the per-list map rather than dropped silently or filed under 0, which is a real answer and not an id.
            originUnknown(list, "no-list-identity");
            return;
        }
        if (ORIGINS.containsKey(Integer.valueOf(list))) {
            return;
        }
        ORIGINS.put(Integer.valueOf(list), origin);
        if (ORIGINS.size() <= 6) {
            System.out.println("[Celtium] CELTIUM_DISPLAY_LIST_ORIGIN list=" + list + " origin=" + origin);
        }
    }

    /**
     * Records a compile site that could not be attributed to a list id, with the reason it could not.
     * <p>
     * This is the counterpart of {@link #originIfAbsent}: the identity census has to be able to say "unknown" rather
     * than let a missing answer look like list 0, which is what a replay of list 0 can never find.
     */
    public static synchronized void originUnknown(int list, String reason) {
        String key = reason == null || reason.isEmpty() ? "unknown" : reason;
        originsWithUnknownList++;
        Integer seen = UNKNOWN_ORIGIN_REASONS.get(key);
        UNKNOWN_ORIGIN_REASONS.put(key, Integer.valueOf(seen == null ? 1 : seen.intValue() + 1));
        if (originsWithUnknownList == 1) {
            System.out.println("[Celtium] CELTIUM_DISPLAY_LIST_ORIGIN_UNKNOWN list=" + list + " reason=" + key
                    + " (no identity: kept out of the per-list census)");
        }
    }

    /** {@return how many compile sites had no list identity}. */
    public static synchronized long originsWithUnknownList() {
        return originsWithUnknownList;
    }

    /** {@return whether this list's compile-time texture has been recorded already}. */
    public static synchronized boolean compileTextureKnown(int list) {
        return COMPILE_TEXTURES.containsKey(Integer.valueOf(list));
    }

    /**
     * Records the texture a list is being compiled under, once per list.
     * <p>
     * Read by the uploader while it is inside {@code glNewList}, from the same GL state the capture reads per quad: a
     * display list does not bake the texture in, so the replay-time binding is the one that draws, but the compile-time
     * one is what says the geometry was compiled with a texture at all. Under the stub both are the single
     * {@code glBindTexture} record; the value is stored as read, so a failed read is visible as "unknown" rather than
     * as a plausible name.
     */
    public static synchronized void compileTexture(int list, int texture, int unit) {
        if (list <= 0 || COMPILE_TEXTURES.containsKey(Integer.valueOf(list)) || COMPILE_TEXTURES.size() >= 32) {
            return;
        }
        COMPILE_TEXTURES.put(Integer.valueOf(list), Integer.valueOf(texture));
        if (COMPILE_TEXTURES.size() <= 4) {
            System.out.println("[Celtium] CELTIUM_DISPLAY_LIST_COMPILE_TEXTURE list=" + list + " texture="
                    + value(texture) + " unit=" + value(unit));
        }
    }

    /** {@return the texture recorded while this list was compiled, or 0 when it is unknown}. */
    public static synchronized int compileTextureOf(int list) {
        Integer texture = COMPILE_TEXTURES.get(Integer.valueOf(list));
        return texture == null || texture.intValue() <= 0 ? 0 : texture.intValue();
    }

    /** {@return the textures recorded per list and the lists whose texture could not be read}. */
    public static synchronized String textures() {
        StringBuilder text = new StringBuilder();
        appendTextures(text, "compile", COMPILE_TEXTURES, null);
        // The unit is part of the answer for a replay-time read: the stub records the binding per unit, so a read on the
        // wrong unit finds 0 rather than a wrong name.
        appendTextures(text, "replay", REPLAY_TEXTURES, REPLAY_TEXTURE_UNITS);
        if (!UNKNOWN_TEXTURE_LISTS.isEmpty()) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append("unknownLists=").append(UNKNOWN_TEXTURE_LISTS.size());
        }
        return text.length() == 0 ? "none" : text.toString();
    }

    private static void appendTextures(StringBuilder text, String phase, Map<Integer, Integer> textures,
            Map<Integer, Integer> units) {
        for (Map.Entry<Integer, Integer> entry : textures.entrySet()) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(phase).append(':').append(entry.getKey()).append('=')
                    .append(value(entry.getValue().intValue()));
            if (units != null) {
                Integer unit = units.get(entry.getKey());
                if (unit != null) {
                    text.append("@unit").append(unit);
                }
            }
        }
    }

    /** {@return an int as text, with the sentinel spelled out rather than printed as a number}. */
    private static String value(int number) {
        return number == UNKNOWN ? "unknown" : String.valueOf(number);
    }

    /** {@return the GL integer for {@code pname}, or {@link #UNKNOWN} when GL could not answer}. */
    private static int glInt(int pname) {
        try {
            return org.lwjgl.opengl.GL11.glGetInteger(pname);
        } catch (RuntimeException | LinkageError error) {
            return UNKNOWN;
        }
    }

    /** {@return the recorded origin of a list, or null}. */
    public static synchronized String originOf(int list) {
        return ORIGINS.get(Integer.valueOf(list));
    }

    /** {@return the recorded origins, for the run summary}. */
    public static synchronized String origins() {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<Integer, String> entry : ORIGINS.entrySet()) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(entry.getKey()).append('=').append(entry.getValue());
        }
        if (originsWithUnknownList > 0) {
            if (text.length() > 0) {
                text.append(" | ");
            }
            // "unknown" is spelled out rather than left implicit: a compile site with no list id must not read as list 0.
            text.append("unknown=").append(originsWithUnknownList).append(UNKNOWN_ORIGIN_REASONS);
        }
        if (!COMPILE_TEXTURES.isEmpty() || !REPLAY_TEXTURES.isEmpty() || !UNKNOWN_TEXTURE_LISTS.isEmpty()) {
            if (text.length() > 0) {
                text.append(" | ");
            }
            text.append("textures=").append(textures());
        }
        return text.length() == 0 ? "none" : text.toString();
    }

    /** Records one replay of {@code list}, called from the {@code GlStateManager.callList} hook. */
    private static volatile boolean frameOpen;
    private static long replaysWithFrameOpen;
    private static long replaysWithFrameClosed;

    private static volatile boolean renderPassOpen;
    private static long replaysWithPassOpen;
    private static long replaysWithPassClosed;

    /**
     * Called by the renderer whenever a Vulkan render pass opens or closes.
     * <p>
     * Whether a display list can be drawn natively where it is replayed depends on this: recording into an open pass is
     * the same thing the immediate path does, while a closed one needs a pass of its own. Measured rather than assumed,
     * because the answer decides how the model draw has to be built.
     */
    public static void setRenderPassOpen(boolean open) {
        renderPassOpen = open;
    }

    /** {@return replays that happened while a render pass was open}. */
    public static synchronized long replaysWithPassOpen() {
        return replaysWithPassOpen;
    }

    /** {@return replays that happened while no render pass was open}. */
    public static synchronized long replaysWithPassClosed() {
        return replaysWithPassClosed;
    }

    /** Called by the renderer when a frame opens or closes, so the replays can be split by phase. */
    public static void setFrameOpen(boolean open) {
        frameOpen = open;
    }

    /** {@return replays that happened while a frame was open}. */
    public static synchronized long replaysWithFrameOpen() {
        return replaysWithFrameOpen;
    }

    /** {@return replays that happened while no frame was open}. */
    public static synchronized long replaysWithFrameClosed() {
        return replaysWithFrameClosed;
    }



    private static final java.util.Set<Integer> TEXTURE_LOGGED = new java.util.HashSet<Integer>();

    private static final java.util.Set<Integer> MATRIX_LOGGED = new java.util.HashSet<Integer>();

    /** {@return whether the model matrix of this list has been logged already}. */
    public static synchronized boolean matrixLogged(int list) {
        return MATRIX_LOGGED.add(Integer.valueOf(list));
    }

    /** {@return the bound texture of this list has been logged already}. */
    public static synchronized boolean textureLogged(int list) {
        return !TEXTURE_LOGGED.add(Integer.valueOf(list));
    }
    private static long cancelled;
    /** Cancelled replays per list, paired with {@link #REPLAYS} so the lists nothing drew can be named. */
    private static final Map<Integer, Integer> CANCELLED_PER_LIST = new LinkedHashMap<Integer, Integer>();
    /** How many new lists have printed their first replay, so the line cannot flood the log. */
    private static final java.util.Set<Integer> REPLAY_TRACED = new java.util.HashSet<Integer>();
    private static int replayTraces;




    private static long originCancels;

    /** {@return how many replays were cancelled because the list's origin says the native path already draws it}. */
    public static synchronized long originCancels() {
        return originCancels;
    }

    /** Records one cancelled replay. */
    public static synchronized void cancelled(int list) {
        cancelled++;
        if (list > 0) {
            // Per list as well as in total: whether a list's replay was taken over by the native path is the difference
            // between a draw that happened and one that reached a GL body with no context, and only the pair
            // (replays, cancels) says which lists those are.
            Integer seen = CANCELLED_PER_LIST.get(Integer.valueOf(list));
            CANCELLED_PER_LIST.put(Integer.valueOf(list), Integer.valueOf(seen == null ? 1 : seen.intValue() + 1));
        }
        String origin = ORIGINS.get(Integer.valueOf(list));
        if (origin != null && (origin.contains("ItemRenderer") || origin.contains("ModelPlayer")
                || origin.contains("ModelBiped"))) {
            originCancels++;
        }
        if (cancelled <= 3) {
            System.out.println("[Celtium] CELTIUM_DISPLAY_LIST_CANCELLED list=" + list);
        }
    }

    /** {@return how many replays were cancelled}. */
    public static synchronized long cancelled() {
        return cancelled;
    }

    public static synchronized void replay(int list) {
        total++;
        if (frameOpen) {
            replaysWithFrameOpen++;
        } else {
            replaysWithFrameClosed++;
        }
        if (renderPassOpen) {
            replaysWithPassOpen++;
        } else {
            replaysWithPassClosed++;
        }
        if (list <= 0) {
            // Not a display list: 0 is what GL answers when nothing is being compiled and -1 is what the game passes for
            // a list it never built (measured: callList(-1) was the busiest entry in the per-list table). Counted as
            // unknown - the totals still add up because total() counts it - and kept out of the per-list map, so no
            // replay matrix or texture is filed under an id that no draw can use.
            replaysWithUnknownList++;
            if (replaysWithUnknownList == 1) {
                System.out.println("[Celtium] CELTIUM_DISPLAY_LIST_UNKNOWN replay list=" + list
                        + " (no list identity: kept out of the per-list census)");
            }
            return;
        }
        // The matrix in effect at replay, next to the one captured while the list was compiled. Whether a display list
        // bakes the transform or is replayed under the current matrix decides how a native replacement has to combine
        // them, and this pair is what answers it instead of an argument about the specification.
        if (!MATRICES.containsKey(Integer.valueOf(list)) && matricesTaken < 24) {
            try {
                java.nio.FloatBuffer matrix = org.lwjgl.BufferUtils.createFloatBuffer(16);
                org.lwjgl.opengl.GL11.glGetFloatv(org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX, matrix);
                float[] values = new float[16];
                matrix.get(values);
                MATRICES.put(Integer.valueOf(list), values);
                matricesTaken++;
            } catch (RuntimeException | LinkageError ignored) {
                // Measurement only.
            }
        }
        noteReplayTexture(list);
        Integer seen = REPLAYS.get(Integer.valueOf(list));
        if (seen == null) {
            distinctLists++;
            if (!reportedFirst) {
                reportedFirst = true;
                System.out.println("[Celtium] CELTIUM_DISPLAY_LIST first replay: list=" + list);
            }
            celtium$traceFirstReplay(list);
        }
        REPLAYS.put(Integer.valueOf(list), Integer.valueOf(seen == null ? 1 : seen.intValue() + 1));
    }

    /**
     * Records the texture bound when a list is replayed, the first time the read finds one.
     * <p>
     * The model path binds its own texture - a player model samples its skin, not the block atlas the immediate path last
     * used - and the same read the {@code callList} hook performs decides which texture a native draw would use. Doing it
     * here keeps the answer in the run summary even when the hook's one-shot print has already been used up, and a read
     * that finds nothing is counted and named instead of being recorded as a name of 0.
     */
    private static void noteReplayTexture(int list) {
        if (REPLAY_TEXTURES.containsKey(Integer.valueOf(list)) || REPLAY_TEXTURES.size() >= 32) {
            return;
        }
        int unit = glInt(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
        int texture = glInt(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
        if (texture > 0) {
            REPLAY_TEXTURES.put(Integer.valueOf(list), Integer.valueOf(texture));
            REPLAY_TEXTURE_UNITS.put(Integer.valueOf(list), Integer.valueOf(unit));
            return;
        }
        if (UNKNOWN_TEXTURE_LISTS.add(Integer.valueOf(list))) {
            if (UNKNOWN_TEXTURE_LISTS.size() == 1) {
                System.out.println("[Celtium] CELTIUM_DISPLAY_LIST_TEXTURE_UNKNOWN list=" + list + " unit=" + value(unit)
                        + " texture=" + value(texture)
                        + " (the stub records glBindTexture per unit; 0 means none was bound when the list was replayed)");
            }
        }
    }

    private static final Map<Integer, float[]> MATRICES = new LinkedHashMap<Integer, float[]>();
    private static int matricesTaken;

    /**
     * Prints what a list that is being replayed for the first time holds, and whether it was ever captured.
     * <p>
     * This is the line that separates the two ways a display list can be missing from the frame. {@code captured=no} says
     * the geometry never reached the capture - nothing was compiled through {@code TexturedQuad.draw} under this id - so
     * the GL body is replaying geometry no native draw can be built from, while {@code captured=yes} with a replay that
     * is never cancelled says the geometry is in hand and the draw is being refused. The texture pair does the same for
     * the image: a replay-time binding the compile never saw would draw the model with someone else's texture.
     */
    private static void celtium$traceFirstReplay(int list) {
        if (replayTraces >= 8 || !REPLAY_TRACED.add(Integer.valueOf(list))) {
            return;
        }
        replayTraces++;
        int texture = glInt(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
        int captured = VulkanModelQuadCapture.quadsForList(list);
        int submitted = VulkanModelQuadCapture.submittedForList(list);
        System.out.println("[Celtium] CELTIUM_DISPLAY_LIST_REPLAY list=" + list
                + " captured=" + (captured > 0 ? "yes" : "no") + " quads=" + captured + " submitted=" + submitted
                + " owner=" + VulkanModelQuadCapture.ownerForList(list)
                + " replayTexture=" + value(texture)
                + " compileTexture=" + value(VulkanModelQuadCapture.textureForList(list))
                + celtium$transformPair(list)
                + (captured > 0 ? "" : " (this list has no captured geometry: without a context nothing draws it)"));
    }

    /**
     * {@return the live modelview's translation next to the one the list was compiled under, as text}.
     * <p>
     * The pair is how "the model is drawn at the camera" is read from a log instead of from the picture: a display list
     * is replayed under the matrix current at replay, so a list whose live and compile-time translations are both zero
     * while its owner is a tile entity renderer - which translates to its block before drawing - is a list whose
     * transform never reached the mirror. Both come from the same {@code glGetFloatv} the capture uses, so the line
     * measures the mirror, not an assumption about it.
     */
    private static String celtium$transformPair(int list) {
        float[] compile = VulkanModelQuadCapture.compileMatrixForList(list);
        float[] live = null;
        try {
            java.nio.FloatBuffer matrix = org.lwjgl.BufferUtils.createFloatBuffer(16);
            org.lwjgl.opengl.GL11.glGetFloatv(org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX, matrix);
            live = new float[16];
            matrix.get(live);
        } catch (RuntimeException | LinkageError ignored) {
            // A missing read is not a wrong draw; the pair is simply not reported.
        }
        if (compile == null && live == null) {
            return "";
        }
        return " compileT=" + translation(compile) + " liveT=" + translation(live);
    }

    /** {@return the translation of a column-major matrix as text}, or {@code unknown}. */
    private static String translation(float[] matrix) {
        if (matrix == null) {
            return "unknown";
        }
        return "(" + round3(matrix[12]) + "," + round3(matrix[13]) + "," + round3(matrix[14]) + ")";
    }

    /** {@return a float with three decimals}. */
    private static String round3(float value) {
        return String.valueOf(Math.round(value * 1000.0f) / 1000.0f);
    }

    /** {@return the replay-time modelview matrix of a list, or null when it has not been replayed yet}. */
    public static synchronized float[] replayMatrix(int list) {
        return MATRICES.get(Integer.valueOf(list));
    }

    /** {@return the number of replays seen so far}. */
    public static synchronized long total() {
        return total;
    }

    /** {@return how many distinct lists have been replayed}. */
    public static synchronized int distinctLists() {
        return distinctLists;
    }

    /** {@return how many replays carried no list identity and were kept out of the per-list counts}. */
    public static synchronized long replaysWithUnknownList() {
        return replaysWithUnknownList;
    }

    /** {@return the per-list counts, largest first, capped so the summary stays one line}. */
    public static synchronized String summary() {
        if (REPLAYS.isEmpty() && replaysWithUnknownList == 0) {
            return "none";
        }
        java.util.List<Map.Entry<Integer, Integer>> entries =
                new java.util.ArrayList<Map.Entry<Integer, Integer>>(REPLAYS.entrySet());
        entries.sort((a, b) -> b.getValue().intValue() - a.getValue().intValue());
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < Math.min(entries.size(), 8); i++) {
            if (i > 0) {
                text.append(' ');
            }
            text.append(entries.get(i).getKey()).append(':').append(entries.get(i).getValue());
        }
        if (entries.size() > 8) {
            text.append(" +").append(entries.size() - 8).append(" more");
        }
        if (replaysWithUnknownList > 0 || !UNKNOWN_TEXTURE_LISTS.isEmpty()) {
            // Counted here as well as in origins(), because the per-list table alone cannot explain the replay total once
            // some replays have no list to be counted under.
            text.append(" unknown[list=").append(replaysWithUnknownList)
                    .append(" textureLists=").append(UNKNOWN_TEXTURE_LISTS.size()).append(']');
        }
        // Replays no native draw took over: replays minus cancels, per list. Under a context these are the lists GL drew
        // itself; without one they are the pieces of the scene nothing drew, and naming them is what turns "the model is
        // missing" into a list id and its capture state.
        StringBuilder notDrawn = new StringBuilder();
        int shown = 0;
        for (Map.Entry<Integer, Integer> entry : REPLAYS.entrySet()) {
            int list = entry.getKey().intValue();
            Integer drawn = CANCELLED_PER_LIST.get(Integer.valueOf(list));
            int missing = entry.getValue().intValue() - (drawn == null ? 0 : drawn.intValue());
            if (missing <= 0) {
                continue;
            }
            if (shown >= 8) {
                notDrawn.append(" +more");
                break;
            }
            shown++;
            if (notDrawn.length() > 0) {
                notDrawn.append(' ');
            }
            notDrawn.append(list).append(':').append(missing)
                    .append('/').append(VulkanModelQuadCapture.quadsForList(list) > 0 ? "captured" : "uncaptured");
        }
        if (notDrawn.length() > 0) {
            text.append(" notDrawn[").append(notDrawn).append(']');
        }
        return text.length() == 0 ? "none" : text.toString();
    }
}
