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
    public static synchronized void replay(int list) {
        total++;
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
