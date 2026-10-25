package org.soutalab.celtium.vulkan;

import org.lwjgl.opengl.GL11;

/**
 * Translates Minecraft's immediate-mode geometry into a Vulkan submission.
 * <p>
 * This is the draw half of the Forge API swap described in {@code docs/history/forge-api-vulkan-inventory.md}: the mod-side
 * Mixin on {@code WorldVertexBufferUploader.func_181679_a(BufferBuilder)} hands over the finished buffer, and this
 * class turns it into one batch of {@link VulkanDynamicMesh} plus the pipeline selected from {@link VulkanGlState}.
 * <p>
 * The first thing that has to happen is topology conversion. Minecraft draws with {@code GL_QUADS} and the triangle
 * modes; Vulkan has no quads, so a quad becomes two triangles and the index list is generated here. Modes that have
 * no Vulkan equivalent in this renderer (points, lines) return {@code null} with a reason rather than drawing
 * something wrong.
 */
public final class VulkanImmediateDrawer {

    private VulkanImmediateDrawer() {
    }

    /**
     * Bounded cache for the generated index lists. Every offered batch used to allocate a fresh int[] here -
     * ~300 batches per frame on a busy scene is ~300 throwaway arrays plus their fill loops, which is exactly the
     * short-lived garbage that feeds the young-gen pauses the GC log measured. The content depends only on
     * (draw mode, vertex count), and the two consumers ({@link VulkanDynamicMesh#copyVertices},
     * {@link VulkanDynamicMesh#copyVerticesTransformed}) only read it - so the same array can be shared by every
     * batch with the same shape. Bounded: a pathological caller cycling vertex counts could otherwise grow the map
     * without limit; 512 entries covers every plausible scene several times over.
     */
    private static final int INDEX_CACHE_LIMIT =
            Integer.getInteger("celtium.vulkan.indexCacheLimit", 512).intValue();
    // Direct-mapped by (mode, count), so a lookup is no boxed Long: the LinkedHashMap<Long, int[]> this replaced
    // allocated a key per immediate batch (63 MB a warm minute in the Xaero scene's JFR). A collision overwrites the
    // slot - the entry is rebuilt on its next miss, which is the same bound the LRU's eviction gave.
    private static final int INDEX_CACHE_SLOTS = Integer.highestOneBit(Math.max(16, INDEX_CACHE_LIMIT) * 2 - 1);
    private static final int INDEX_CACHE_SHIFT = 64 - Integer.numberOfTrailingZeros(INDEX_CACHE_SLOTS);
    private static final long[] INDEX_CACHE_KEYS = new long[INDEX_CACHE_SLOTS];
    private static final int[][] INDEX_CACHE_VALUES = new int[INDEX_CACHE_SLOTS][];
    private static int indexCacheEntries;
    private static long indexCacheHits;
    private static long indexCacheMisses;

    /** {@return the index-list cache census}, as {@code hits/misses/entries}. */
    public static String indexCacheStats() {
        return "indexCache=" + indexCacheHits + "/" + indexCacheMisses + "/" + indexCacheEntries;
    }

    /**
     * Expands Minecraft's primitive topology into triangle-list indices.
     *
     * @param glMode      the Minecraft/OpenGL draw mode
     * @param vertexCount number of vertices in the batch
     * @return triangle-list indices, or {@code null} when this renderer has no equivalent for the mode
     */
    public static int[] expandIndices(int glMode, int vertexCount) {
        if (vertexCount <= 0) {
            return new int[0];
        }
        // The cheap validity checks stay ahead of the map lookup so a refused batch costs no more than before.
        if (glMode == GL11.GL_QUADS && vertexCount % 4 != 0) {
            return null;
        }
        if ((glMode == GL11.GL_LINES && vertexCount % 2 != 0)
                || ((glMode == GL11.GL_LINE_STRIP || glMode == GL11.GL_LINE_LOOP
                        || glMode == GL11.GL_TRIANGLE_STRIP || glMode == GL11.GL_TRIANGLE_FAN) && vertexCount < 2)) {
            return null;
        }
        long key = ((long) glMode << 32) | (vertexCount & 0xffffffffL);
        // Fibonacci hashing: the top bits of key * 2^64/phi. An xor of the count with an odd multiple of itself (the
        // first version) always cleared the low bit and thrashed - 125k misses against 20 live shapes in a warm minute.
        int slot = (int) ((key * 0x9E3779B97F4A7C15L) >>> INDEX_CACHE_SHIFT);
        int[] cached = INDEX_CACHE_VALUES[slot];
        if (cached != null && INDEX_CACHE_KEYS[slot] == key) {
            indexCacheHits++;
            return cached;
        }
        indexCacheMisses++;
        int[] indices = buildIndices(glMode, vertexCount);
        if (indices != null && indices.length > 0) {
            if (INDEX_CACHE_VALUES[slot] == null) {
                indexCacheEntries++;
            }
            INDEX_CACHE_KEYS[slot] = key;
            INDEX_CACHE_VALUES[slot] = indices;
        }
        return indices;
    }

    /** Builds the index list for one (mode, count) pair - the pure-arithmetic half of {@link #expandIndices}. */
    private static int[] buildIndices(int glMode, int vertexCount) {
        switch (glMode) {
            case GL11.GL_QUADS: {
                int quads = vertexCount / 4;
                int[] indices = new int[quads * 6];
                for (int quad = 0; quad < quads; quad++) {
                    int base = quad * 4;
                    int out = quad * 6;
                    // Minecraft's quad winding is 0,1,2,3; both triangles keep it.
                    indices[out] = base;
                    indices[out + 1] = base + 1;
                    indices[out + 2] = base + 2;
                    indices[out + 3] = base;
                    indices[out + 4] = base + 2;
                    indices[out + 5] = base + 3;
                }
                return indices;
            }
            case GL11.GL_TRIANGLES: {
                int[] indices = new int[vertexCount];
                for (int i = 0; i < vertexCount; i++) {
                    indices[i] = i;
                }
                return indices;
            }
            case GL11.GL_TRIANGLE_FAN: {
                int[] indices = new int[(vertexCount - 2) * 3];
                for (int i = 0; i < vertexCount - 2; i++) {
                    indices[i * 3] = 0;
                    indices[i * 3 + 1] = i + 1;
                    indices[i * 3 + 2] = i + 2;
                }
                return indices;
            }
            case GL11.GL_LINES: {
                // Two vertices per segment, in the order Minecraft wrote them. The segment count is what the batch's
                // vertex count says, so an odd count is not a line list and is refused rather than guessed at.
                int[] indices = new int[vertexCount];
                for (int i = 0; i < vertexCount; i++) {
                    indices[i] = i;
                }
                return indices;
            }
            case GL11.GL_LINE_STRIP:
            case GL11.GL_LINE_LOOP: {
                // A strip of n vertices is n-1 segments; a loop adds the closing one. Both are drawn as a line list.
                int segments = glMode == GL11.GL_LINE_LOOP ? vertexCount : vertexCount - 1;
                int[] indices = new int[segments * 2];
                for (int segment = 0; segment < segments; segment++) {
                    indices[segment * 2] = segment;
                    indices[segment * 2 + 1] = (segment + 1) % vertexCount;
                }
                return indices;
            }
            case GL11.GL_TRIANGLE_STRIP: {
                int[] indices = new int[(vertexCount - 2) * 3];
                for (int i = 0; i < vertexCount - 2; i++) {
                    // Odd triangles swap the first two vertices to keep the winding consistent.
                    if ((i & 1) == 0) {
                        indices[i * 3] = i;
                        indices[i * 3 + 1] = i + 1;
                        indices[i * 3 + 2] = i + 2;
                    } else {
                        indices[i * 3] = i + 1;
                        indices[i * 3 + 1] = i;
                        indices[i * 3 + 2] = i + 2;
                    }
                }
                return indices;
            }
            default:
                return null;
        }
    }

    /** {@return whether this is a line primitive mode, which the line pipeline can draw}. */
    public static boolean isLineMode(int glMode) {
        return glMode == GL11.GL_LINES || glMode == GL11.GL_LINE_STRIP || glMode == GL11.GL_LINE_LOOP;
    }

    /** {@return a human-readable reason why a mode is not drawable here, or null when it is} */
    public static String unsupportedReason(int glMode) {
        switch (glMode) {
            case GL11.GL_QUADS:
            case GL11.GL_TRIANGLES:
            case GL11.GL_TRIANGLE_FAN:
            case GL11.GL_TRIANGLE_STRIP:
                return null;
            case GL11.GL_LINES:
            case GL11.GL_LINE_STRIP:
            case GL11.GL_LINE_LOOP:
                return "line primitives are not implemented in the Vulkan terrain renderer yet";
            case GL11.GL_POINTS:
                return "point primitives are not implemented in the Vulkan terrain renderer yet";
            default:
                return "unknown draw mode " + glMode;
        }
    }
}
