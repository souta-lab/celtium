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
 */
public final class VulkanDisplayListCensus {

    private static final Map<Integer, Integer> REPLAYS = new LinkedHashMap<Integer, Integer>();
    private static long total;
    private static int distinctLists;
    private static boolean reportedFirst;

    private VulkanDisplayListCensus() {
    }

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
        if (list == 0 || ORIGINS.containsKey(Integer.valueOf(list))) {
            return;
        }
        ORIGINS.put(Integer.valueOf(list), origin);
        if (ORIGINS.size() <= 6) {
            System.out.println("[Celtium] CELTIUM_DISPLAY_LIST_ORIGIN list=" + list + " origin=" + origin);
        }
    }

    /** {@return the recorded origin of a list, or null}. */
    public static synchronized String originOf(int list) {
        return ORIGINS.get(Integer.valueOf(list));
    }

    /** {@return the recorded origins, for the run summary}. */
    public static synchronized String origins() {
        if (ORIGINS.isEmpty()) {
            return "none";
        }
        StringBuilder text = new StringBuilder();
        for (Map.Entry<Integer, String> entry : ORIGINS.entrySet()) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return text.toString();
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

    private static final java.util.Set<Integer> CANCEL = cancelSet();
    private static long cancelled;

    private static java.util.Set<Integer> cancelSet() {
        String spec = System.getProperty("celtium.vulkan.cancelDisplayLists", "");
        java.util.Set<Integer> ids = new java.util.HashSet<Integer>();
        for (String part : spec.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                try {
                    ids.add(Integer.valueOf(Integer.parseInt(trimmed)));
                } catch (NumberFormatException ignored) {
                    // A malformed entry simply does not cancel anything.
                }
            }
        }
        return ids;
    }

    /**
     * Whether the held-item display lists may be cancelled by their origin.
     * <p>
     * The origin chain separates them from other models: a list compiled through {@code ItemRenderer} is the held item,
     * which the immediate path already draws (measured: cancelling it changes 3-4 pixels), while a list compiled through
     * {@code ModelPlayer}/{@code ModelBiped} is the player model, which has no native pipeline yet and must keep being
     * replayed. This is a rule about what the list is, not about which id it happens to have.
     * <p>
     * On by default since it was measured with matched pairs on both GPUs: AMD 0.0155 mean / 68 pixels and Intel 0.0000
     * mean / 6 pixels against their own keep runs, while removing 1066 and 572 display-list replays per run. The
     * property is still read, so {@code -Dceltium.vulkan.cancelHandDisplayLists=false} restores the old behaviour.
     */
    private static final boolean CANCEL_BY_ORIGIN = Boolean.parseBoolean(
            System.getProperty("celtium.vulkan.cancelHandDisplayLists", "true"));

    private static final java.util.Set<Integer> TEXTURE_LOGGED = new java.util.HashSet<Integer>();

    /** {@return whether the bound texture of this list has been logged already}. */
    public static synchronized boolean textureLogged(int list) {
        return !TEXTURE_LOGGED.add(Integer.valueOf(list));
    }

    /** {@return whether this list's GL replay is being cancelled, so the native path draws it instead}. */
    public static boolean cancels(int list) {
        if (CANCEL.contains(Integer.valueOf(list))) {
            return true;
        }
        if (!CANCEL_BY_ORIGIN) {
            return false;
        }
        String origin = originOf(list);
        return origin != null && origin.contains("ItemRenderer");
    }

    private static long originCancels;

    /** {@return how many replays were cancelled because the list's origin says the native path already draws it}. */
    public static synchronized long originCancels() {
        return originCancels;
    }

    /** Records one cancelled replay. */
    public static synchronized void cancelled(int list) {
        cancelled++;
        String origin = ORIGINS.get(Integer.valueOf(list));
        if (origin != null && origin.contains("ItemRenderer")) {
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
        Integer seen = REPLAYS.get(Integer.valueOf(list));
        if (seen == null) {
            distinctLists++;
            if (!reportedFirst) {
                reportedFirst = true;
                System.out.println("[Celtium] CELTIUM_DISPLAY_LIST first replay: list=" + list);
            }
        }
        REPLAYS.put(Integer.valueOf(list), Integer.valueOf(seen == null ? 1 : seen.intValue() + 1));
    }

    private static final Map<Integer, float[]> MATRICES = new LinkedHashMap<Integer, float[]>();
    private static int matricesTaken;

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

    /** {@return the per-list counts, largest first, capped so the summary stays one line}. */
    public static synchronized String summary() {
        if (REPLAYS.isEmpty()) {
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
        return text.toString();
    }
}
