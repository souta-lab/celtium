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

    /** Records one replay of {@code list}, called from the {@code GlStateManager.callList} hook. */
    private static volatile boolean frameOpen;
    private static long replaysWithFrameOpen;
    private static long replaysWithFrameClosed;

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

    /** {@return whether this list's GL replay is being cancelled, so the native path draws it instead}. */
    public static boolean cancels(int list) {
        return CANCEL.contains(Integer.valueOf(list));
    }

    /** Records one cancelled replay. */
    public static synchronized void cancelled(int list) {
        cancelled++;
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
