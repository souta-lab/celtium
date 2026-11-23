package org.soutalab.celtium.vulkan;

/**
 * The debug texture-id probes: the three {@code -Dceltium.debug.*} id sets (dump / replace / skip) parsed once
 * from their properties, plus the dump-once bookkeeping the window diagnostics keep.
 * <p>
 * Extracted from {@code VulkanTerrainGpuBackend}'s immediate-mode state zone (item 704, R3) because the unit is
 * severable: the parsers are pure functions of the properties, the sets are read at exactly two backend sites
 * (the skip check in the window flush, the replacement in prepare) and the dump state is touched only by
 * {@code BackendDiagnostics}.
 */
final class TextureIdProbes {

    /** {@code -Dceltium.debug.dumpTextureIds=23}: write those textures' images to the temp directory once. */
    static final it.unimi.dsi.fastutil.ints.IntOpenHashSet DUMP_TEXTURE_IDS = dumpTextureIds();
    int dumpedTextureImages;
    final it.unimi.dsi.fastutil.ints.IntOpenHashSet dumpedTextureIds = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();

    private static it.unimi.dsi.fastutil.ints.IntOpenHashSet dumpTextureIds() {
        String spec = System.getProperty("celtium.debug.dumpTextureIds");
        if (spec == null || spec.trim().isEmpty()) {
            return null;
        }
        it.unimi.dsi.fastutil.ints.IntOpenHashSet ids = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
        for (String part : spec.split(",")) {
            try {
                ids.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
                // A malformed entry selects nothing.
            }
        }
        return ids.isEmpty() ? null : ids;
    }

    /**
     * {@code -Dceltium.debug.replaceTextures=23:10}: draw groups that sample the first id with the second id's
     * descriptor, keeping the draw and its state. A replacement rather than a skip, because a skip changes what the
     * frame has drawn and therefore the copy chain's cache keys, which is a confound a replacement does not have.
     */
    static final it.unimi.dsi.fastutil.ints.Int2IntMap REPLACE_TEXTURE_IDS = replaceTextureIds();

    private static it.unimi.dsi.fastutil.ints.Int2IntMap replaceTextureIds() {
        String spec = System.getProperty("celtium.debug.replaceTextures");
        if (spec == null || spec.trim().isEmpty()) {
            return null;
        }
        it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap pairs = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
        // -1 means "not named": an id is a GL name, and 0 is the valid "no texture" replacement.
        pairs.defaultReturnValue(-1);
        for (String part : spec.split(",")) {
            int colon = part.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            try {
                pairs.put(Integer.parseInt(part.substring(0, colon).trim()),
                        Integer.parseInt(part.substring(colon + 1).trim()));
            } catch (NumberFormatException ignored) {
                // A malformed pair selects nothing.
            }
        }
        return pairs.isEmpty() ? null : pairs;
    }

    /** {@code -Dceltium.debug.skipTextures=3,4}: drop the draws that sample these GL texture names, for a probe. */
    static final it.unimi.dsi.fastutil.ints.IntOpenHashSet SKIP_TEXTURE_IDS = skipTextureIds();

    private static it.unimi.dsi.fastutil.ints.IntOpenHashSet skipTextureIds() {
        String spec = System.getProperty("celtium.debug.skipTextures");
        if (spec == null || spec.trim().isEmpty()) {
            return null;
        }
        it.unimi.dsi.fastutil.ints.IntOpenHashSet ids = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
        for (String part : spec.split(",")) {
            try {
                ids.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
                // A malformed entry simply does not select a texture.
            }
        }
        return ids.isEmpty() ? null : ids;
    }
}
