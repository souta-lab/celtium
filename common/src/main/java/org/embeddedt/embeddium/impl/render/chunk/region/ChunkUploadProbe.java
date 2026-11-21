package org.embeddedt.embeddium.impl.render.chunk.region;

import org.embeddedt.embeddium.impl.common.util.NativeBuffer;
import org.embeddedt.embeddium.impl.gl.util.VertexRange;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.LocalSectionIndex;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.embeddedt.embeddium.impl.render.chunk.data.MeshBufferSegment;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataUnsafe;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.zip.CRC32C;

/**
 * Issue 422 probe: direct observation of the terrain chunk mesh upload path (candidate C4 in the
 * 2026-11-21 follow-up triage - vertex/index/chunk-offset upload corruption).
 * <p>
 * The reporter's symptoms are geometry drawn at wrong positions (whole chunks of grass/water/blocks
 * shifted, e.g. +y200, occasional huge black quads) plus distance-dependent defects (black water and
 * missing leaves only at large render distances). Vertex positions in a built mesh are section-local,
 * so a displaced chunk means the draw read the wrong bytes or the wrong offsets: either the arena
 * segment a section's draw row points at does not hold that section's mesh, or the row's per-facing
 * offsets do not match the allocation and ranges the mesh was built with.
 * <p>
 * This probe records, for every mesh upload: the section's chunk coordinates, the pass, the vertex
 * and index byte counts and the counts implied by the mesh's own ranges, the arena segment offsets
 * and lengths the upload was given, and a CRC32C of the source buffers. It checks the invariants the
 * path relies on and prints violations as {@code CELTIUM_C4PROBE ANOMALY} lines:
 * <ul>
 *     <li>range layout: the storage row writer accumulates ranges in facing order and ignores
 *     {@link VertexRange#vertexStart()}, so a range whose start is not the cumulative sum of the
 *     preceding facings' counts (or that overlaps another range, or runs past the mesh's total)
 *     makes the row point at the wrong vertices;</li>
 *     <li>count consistency: the vertex byte count must be a whole multiple of the mesh's total
 *     vertex count, and an arena segment's length must be exactly the upload's element count;</li>
 *     <li>overlap: a new segment must not overlap another live section's segment in the same arena;</li>
 *     <li>source stability: the source buffer's checksum must not change between the pre-upload
 *     snapshot and the post-upload record (a mutation in between means the bytes copied were not
 *     the bytes the builder finished with);</li>
 *     <li>row readback: after {@link SectionRenderDataStorage#setMeshes} writes the off-heap row,
 *     the per-facing offsets are read back and compared with the allocation and ranges.</li>
 * </ul>
 * A re-upload of the same section with identical byte counts but a different checksum is printed as
 * {@code CELTIUM_C4PROBE REUPLOAD-DRIFT}. A rebuild after a real block/lighting change can do this
 * legitimately, so a drift line is an observation to correlate (a section whose chunk did not change,
 * drifting repeatedly, is the suspicious shape), not a verdict on its own.
 * <p>
 * Enabled with {@code -Dceltium.issue422.c4probe=true}; per-upload lines additionally need
 * {@code -Dceltium.issue422.c4probeVerbose=true}. Both default off, and every hook in the upload
 * path is guarded by {@link #ENABLED}, so with the flag off nothing here runs and nothing is
 * allocated. All entry points are called from the render thread (mesh upload and storage mutation
 * happen there); the tables are still kept under a lock so a stray caller cannot corrupt them.
 * <p>
 * What this probe cannot see: the staging-to-device transfer itself. The existing
 * {@code -Dceltium.vulkan.arenaVerify} check covers {@code VulkanArena}, which the terrain path
 * does not use, so the transfer segment of this path stays unverified by either instrument; the
 * checks above bound the CPU-side path (build output, allocation, storage row) that feeds it.
 */
public final class ChunkUploadProbe {
    public static final boolean ENABLED = Boolean.getBoolean("celtium.issue422.c4probe");
    public static final boolean VERBOSE = Boolean.getBoolean("celtium.issue422.c4probeVerbose");

    /** Anomaly lines are the evidence a reporter is asked to send back; cap them so a broken run cannot flood the log. */
    private static final int ANOMALY_LINE_CAP = 300;
    /** One summary line per this many recorded uploads, so a clean run still shows the probe was alive and counting. */
    private static final int SUMMARY_EVERY = 500;
    /** Horizontal chunk distance from the build-time camera at or above which an upload is tagged far=1. */
    private static final int FAR_CHUNK_DISTANCE = 16;

    private static final Object LOCK = new Object();
    /** Last upload per (region, section, pass), for the re-upload drift comparison. */
    private static final Map<String, UploadRecord> RECORDS = new HashMap<>();
    /** Live arena intervals per (region, arena kind), for the overlap check. */
    private static final Map<String, List<LiveRange>> LIVE_RANGES = new HashMap<>();
    /** Storage identity -> where its sections live, so row-verification lines can name chunk coordinates. */
    private static final Map<SectionRenderDataStorage, StorageInfo> STORAGES = new WeakHashMap<>();
    /** Pre-upload source checksums of the current upload burst, consumed by the post-upload record. */
    private static final Map<String, Snapshot> SNAPSHOTS = new HashMap<>();

    private static boolean bannerPrinted;
    private static long rebuilds;
    private static long sorts;
    private static long resizes;
    private static long drifts;
    private static long anomalies;
    private static long suppressedAnomalies;
    private static long callsSinceSummary;

    private ChunkUploadProbe() {
    }

    private static final class UploadRecord {
        private int vertexBytes;
        private int indexBytes;
        private long crcVertex;
        private long crcIndex;
        private int uploads;
    }

    private static final class LiveRange {
        private int start;
        private int end;
        private String owner;
        private int chunkX;
        private int chunkY;
        private int chunkZ;
        private String pass;
    }

    private static final class StorageInfo {
        private int regionChunkX;
        private int regionChunkY;
        private int regionChunkZ;
        private String pass;
    }

    private static final class Snapshot {
        private long crcVertex;
        private long crcIndex;
    }

    // ------------------------------------------------------------------
    // Entry points from RenderRegionManager.MeshUploader
    // ------------------------------------------------------------------

    /** Starts a new upload burst: drops any unconsumed source snapshots from a previous, interrupted burst. */
    public static void beginBurst() {
        synchronized (LOCK) {
            banner();
            SNAPSHOTS.clear();
        }
    }

    /** Checksums a rebuild's source buffers before the arena upload runs. */
    public static void snapshotSource(RenderRegion region, RenderSection section, TerrainRenderPass pass,
                                      BuiltSectionMeshParts mesh) {
        long crcVertex = crcOf(mesh.vertexBuffer());
        long crcIndex = crcOf(mesh.indexBuffer());
        synchronized (LOCK) {
            Snapshot snapshot = new Snapshot();
            snapshot.crcVertex = crcVertex;
            snapshot.crcIndex = crcIndex;
            SNAPSHOTS.put(key(region, section.getSectionIndex(), pass), snapshot);
        }
    }

    /** Records one rebuild upload after its arena segments are known. */
    public static void recordRebuild(RenderRegion region, RenderSection section, TerrainRenderPass pass,
                                     BuiltSectionMeshParts mesh, MeshBufferSegment vertexSegment,
                                     @Nullable MeshBufferSegment indexSegment, int vertexStride) {
        int vertexBytes = mesh.vertexBuffer().getLength();
        int indexBytes = mesh.indexBuffer() != null ? mesh.indexBuffer().getLength() : -1;
        long crcVertex = crcOf(mesh.vertexBuffer());
        long crcIndex = crcOf(mesh.indexBuffer());

        int totalVertices = 0;
        for (VertexRange range : mesh.ranges().values()) {
            totalVertices += range.vertexCount();
        }

        String key = key(region, section.getSectionIndex(), pass);
        String where = where(region, section, pass);

        // Range layout: the row writer accumulates in facing order, so each range must start exactly
        // where the preceding facings ended.
        int cumulative = 0;
        for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
            VertexRange range = mesh.ranges().get(facing);
            if (range == null) {
                continue;
            }
            if (range.vertexStart() != cumulative) {
                anomaly("range-order", where + " facing=" + facing + " vertexStart=" + range.vertexStart()
                        + " expectedStart=" + cumulative + " vertexCount=" + range.vertexCount());
            }
            if (range.vertexStart() < 0 || range.vertexStart() + range.vertexCount() > totalVertices) {
                anomaly("range-out-of-bounds", where + " facing=" + facing + " vertexStart=" + range.vertexStart()
                        + " vertexCount=" + range.vertexCount() + " totalVertices=" + totalVertices);
            }
            cumulative += range.vertexCount();
        }

        // Count consistency between the buffers and the mesh's own ranges.
        if (totalVertices > 0 && vertexBytes == 0) {
            anomaly("count-empty-vertex-buffer", where + " totalVertices=" + totalVertices + " vertexBytes=0");
        }
        if (totalVertices == 0 && vertexBytes > 0) {
            anomaly("count-no-ranges", where + " vertexBytes=" + vertexBytes + " but ranges declare no vertices");
        }
        if (totalVertices > 0 && vertexBytes % totalVertices != 0) {
            anomaly("count-stride", where + " vertexBytes=" + vertexBytes + " totalVertices=" + totalVertices
                    + " (byte count is not a whole number of per-vertex strides)");
        }
        if (indexBytes >= 0 && indexBytes % 4 != 0) {
            anomaly("count-index-alignment", where + " indexBytes=" + indexBytes + " (not a whole number of int indices)");
        }

        // Segment consistency: the arena must have handed out exactly the upload's element count.
        int expectedVertexElements = ceilDiv(vertexBytes, vertexStride);
        if (vertexSegment.getLength() != expectedVertexElements) {
            anomaly("segment-length", where + " kind=vertex segmentLength=" + vertexSegment.getLength()
                    + " expected=" + expectedVertexElements + " vertexBytes=" + vertexBytes + " stride=" + vertexStride);
        }
        if (indexSegment != null) {
            if (indexBytes < 0) {
                anomaly("segment-without-data", where + " kind=index segment present but the mesh has no index buffer");
            } else if (indexSegment.getLength() != ceilDiv(indexBytes, 4)) {
                anomaly("segment-length", where + " kind=index segmentLength=" + indexSegment.getLength()
                        + " expected=" + ceilDiv(indexBytes, 4) + " indexBytes=" + indexBytes);
            }
        } else if (indexBytes > 0) {
            anomaly("segment-missing", where + " kind=index indexBytes=" + indexBytes + " but no segment was allocated");
        }

        synchronized (LOCK) {
            // Source stability across the upload.
            Snapshot snapshot = SNAPSHOTS.remove(key);
            if (snapshot != null && (snapshot.crcVertex != crcVertex || snapshot.crcIndex != crcIndex)) {
                anomaly("source-mutated", where + " crcVertex " + hex(snapshot.crcVertex) + "->" + hex(crcVertex)
                        + " crcIndex " + hex(snapshot.crcIndex) + "->" + hex(crcIndex)
                        + " (source buffer changed between the pre-upload snapshot and the upload)");
            }

            // Re-upload drift against the previous record for this section and pass.
            UploadRecord previous = RECORDS.get(key);
            int uploads = previous != null ? previous.uploads + 1 : 1;
            if (previous != null && previous.vertexBytes == vertexBytes && previous.indexBytes == indexBytes
                    && (previous.crcVertex != crcVertex || previous.crcIndex != crcIndex)) {
                drifts++;
                System.out.println("[Celtium] CELTIUM_C4PROBE REUPLOAD-DRIFT " + where
                        + " vertexBytes=" + vertexBytes + " indexBytes=" + indexBytes
                        + " crcVertex " + hex(previous.crcVertex) + "->" + hex(crcVertex)
                        + " crcIndex " + hex(previous.crcIndex) + "->" + hex(crcIndex)
                        + " reuploads=" + uploads);
            }
            UploadRecord record = new UploadRecord();
            record.vertexBytes = vertexBytes;
            record.indexBytes = indexBytes;
            record.crcVertex = crcVertex;
            record.crcIndex = crcIndex;
            record.uploads = uploads;
            RECORDS.put(key, record);

            checkOverlap(region, "geo", key, section, pass, vertexSegment);
            if (indexSegment != null) {
                checkOverlap(region, "idx", key, section, pass, indexSegment);
            }

            rebuilds++;
            if (VERBOSE) {
                int camDist = cameraDistanceChunks(section);
                System.out.println("[Celtium] CELTIUM_C4PROBE UPLOAD " + where
                        + " far=" + (camDist >= FAR_CHUNK_DISTANCE ? 1 : 0) + " camDist=" + camDist
                        + " vertexBytes=" + vertexBytes + " vertexCount=" + totalVertices
                        + " vertexSeg=[" + vertexSegment.getOffset() + ",+" + vertexSegment.getLength() + ")"
                        + " indexBytes=" + indexBytes
                        + (indexSegment != null
                                ? " indexSeg=[" + indexSegment.getOffset() + ",+" + indexSegment.getLength() + ")"
                                : " indexSeg=none")
                        + " crcVertex=" + hex(crcVertex) + " crcIndex=" + hex(crcIndex)
                        + " reuploads=" + uploads);
            }
            tickLocked();
        }
    }

    /** Records one sort (index-only) upload. Re-sorts legitimately change content, so no drift check applies. */
    public static void recordSort(RenderRegion region, RenderSection section, TerrainRenderPass pass,
                                  NativeBuffer indexData, MeshBufferSegment indexSegment) {
        int indexBytes = indexData.getLength();
        long crcIndex = crcOf(indexData);
        String where = where(region, section, pass);

        if (indexBytes % 4 != 0) {
            anomaly("count-index-alignment", where + " kind=sort indexBytes=" + indexBytes
                    + " (not a whole number of int indices)");
        }
        if (indexSegment.getLength() != ceilDiv(indexBytes, 4)) {
            anomaly("segment-length", where + " kind=sort-index segmentLength=" + indexSegment.getLength()
                    + " expected=" + ceilDiv(indexBytes, 4) + " indexBytes=" + indexBytes);
        }

        synchronized (LOCK) {
            checkOverlap(region, "idx", key(region, section.getSectionIndex(), pass), section, pass, indexSegment);
            sorts++;
            if (VERBOSE) {
                int camDist = cameraDistanceChunks(section);
                System.out.println("[Celtium] CELTIUM_C4PROBE SORT " + where
                        + " far=" + (camDist >= FAR_CHUNK_DISTANCE ? 1 : 0) + " camDist=" + camDist
                        + " indexBytes=" + indexBytes
                        + " indexSeg=[" + indexSegment.getOffset() + ",+" + indexSegment.getLength() + ")"
                        + " crcIndex=" + hex(crcIndex));
            }
            tickLocked();
        }
    }

    /** The arena grew during this region's upload burst: segment contents were copied and rows are rebased. */
    public static void recordResize(RenderRegion region) {
        synchronized (LOCK) {
            resizes++;
            System.out.println("[Celtium] CELTIUM_C4PROBE RESIZE region=" + region.getId()
                    + " regionChunks=(" + region.getChunkX() + "," + region.getChunkY() + "," + region.getChunkZ() + ")"
                    + " (geometry/index arena buffer changed during upload; live segments were copied and storage rows rebased)");
        }
    }

    /** Drops all probe state for one section (its meshes are being removed or rebuilt). */
    public static void forgetSection(RenderRegion region, int sectionIndex) {
        String prefix = region.getId() + "|" + sectionIndex + "|";
        synchronized (LOCK) {
            RECORDS.keySet().removeIf(k -> k.startsWith(prefix));
            SNAPSHOTS.keySet().removeIf(k -> k.startsWith(prefix));
            for (List<LiveRange> ranges : LIVE_RANGES.values()) {
                ranges.removeIf(range -> range.owner.startsWith(prefix));
            }
        }
    }

    /** Drops all probe state for one region (the region is being deleted). */
    public static void forgetRegion(RenderRegion region) {
        String prefix = region.getId() + "|";
        synchronized (LOCK) {
            RECORDS.keySet().removeIf(k -> k.startsWith(prefix));
            SNAPSHOTS.keySet().removeIf(k -> k.startsWith(prefix));
            LIVE_RANGES.remove(region.getId() + "|geo");
            LIVE_RANGES.remove(region.getId() + "|idx");
            for (List<LiveRange> ranges : LIVE_RANGES.values()) {
                ranges.removeIf(range -> range.owner.startsWith(prefix));
            }
        }
    }

    /** Drops all probe state (the region manager itself is being deleted). */
    public static void forgetAll() {
        synchronized (LOCK) {
            RECORDS.clear();
            SNAPSHOTS.clear();
            LIVE_RANGES.clear();
        }
    }

    // ------------------------------------------------------------------
    // Entry points from SectionRenderDataStorage
    // ------------------------------------------------------------------

    /** Registers a storage so row-verification lines can name the chunk coordinates of its sections. */
    public static void registerStorage(SectionRenderDataStorage storage, RenderRegion region, TerrainRenderPass pass) {
        synchronized (LOCK) {
            StorageInfo info = new StorageInfo();
            info.regionChunkX = region.getChunkX();
            info.regionChunkY = region.getChunkY();
            info.regionChunkZ = region.getChunkZ();
            info.pass = pass.name();
            STORAGES.put(storage, info);
        }
    }

    /**
     * Reads back the row {@link SectionRenderDataStorage#setMeshes} just wrote and compares every
     * facing's offsets with the allocation and ranges they must have been derived from.
     */
    public static void verifyRow(SectionRenderDataStorage storage, int localSectionIndex,
                                 MeshBufferSegment allocation, @Nullable MeshBufferSegment indexAllocation,
                                 Map<ModelQuadFacing, VertexRange> ranges, int verticesPerArenaElement,
                                 SectionRenderDataUnsafe.Strategy strategy, ChunkPrimitiveType primitiveType) {
        long ptr = storage.getDataPointer(localSectionIndex);
        int baseVertex = allocation.getOffset() * verticesPerArenaElement;
        int baseIndex = indexAllocation != null ? indexAllocation.getOffset() * 4 : 0;
        String where = storageWhere(storage, localSectionIndex);

        int cumulativeVertex = 0;
        int cumulativeIndex = 0;
        for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
            VertexRange range = ranges.get(ModelQuadFacing.VALUES[facing]);
            int vertexCount = range != null ? range.vertexCount() : 0;

            int expectedVertex = baseVertex + cumulativeVertex;
            int actualVertex = strategy.getVertexOffset(ptr, facing);
            if (actualVertex != expectedVertex) {
                anomaly("row-vertex-offset", where + " facing=" + facing
                        + " expected=" + expectedVertex + " actual=" + actualVertex
                        + " (storage row does not point at the uploaded segment)");
            }

            if (strategy == SectionRenderDataUnsafe.Strategy.FULL) {
                int expectedElements = SectionRenderDataUnsafe.elementsForVertices(vertexCount, primitiveType);
                int actualElements = strategy.getElementCount(ptr, facing, primitiveType);
                if (actualElements != expectedElements) {
                    anomaly("row-element-count", where + " facing=" + facing
                            + " expected=" + expectedElements + " actual=" + actualElements);
                }
                int expectedIndex = baseIndex + cumulativeIndex;
                int actualIndex = strategy.getIndexOffset(ptr, facing);
                if (actualIndex != expectedIndex) {
                    anomaly("row-index-offset", where + " facing=" + facing
                            + " expected=" + expectedIndex + " actual=" + actualIndex
                            + " (storage row does not point at the uploaded segment)");
                }
                cumulativeIndex += expectedElements * 4;
            }

            cumulativeVertex += vertexCount;
        }
    }

    /**
     * Reads back the index offsets {@link SectionRenderDataStorage#replaceIndexBuffer} just rewrote:
     * each facing's offset must be the base plus the preceding facings' element counts, taken from
     * the row itself. FULL layout only; COMPACT stores no index offsets.
     */
    public static void verifyIndexRow(SectionRenderDataStorage storage, int localSectionIndex,
                                      @Nullable MeshBufferSegment indexAllocation,
                                      SectionRenderDataUnsafe.Strategy strategy, ChunkPrimitiveType primitiveType) {
        if (strategy != SectionRenderDataUnsafe.Strategy.FULL) {
            return;
        }
        long ptr = storage.getDataPointer(localSectionIndex);
        int expected = indexAllocation != null ? indexAllocation.getOffset() * 4 : 0;
        String where = storageWhere(storage, localSectionIndex);

        for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
            int actual = strategy.getIndexOffset(ptr, facing);
            if (actual != expected) {
                anomaly("row-index-offset", where + " facing=" + facing
                        + " expected=" + expected + " actual=" + actual + " (after replaceIndexBuffer)");
            }
            expected += strategy.getElementCount(ptr, facing, primitiveType) * 4;
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private static void banner() {
        if (!bannerPrinted) {
            bannerPrinted = true;
            System.out.println("[Celtium] CELTIUM_C4PROBE enabled verbose=" + VERBOSE
                    + " (issue 422 chunk mesh upload probe; anomalies print as CELTIUM_C4PROBE ANOMALY lines)");
        }
    }

    private static String key(RenderRegion region, int sectionIndex, TerrainRenderPass pass) {
        return region.getId() + "|" + sectionIndex + "|" + pass.name();
    }

    private static String where(RenderRegion region, RenderSection section, TerrainRenderPass pass) {
        return "section=(" + section.getChunkX() + "," + section.getChunkY() + "," + section.getChunkZ() + ")"
                + " region=" + region.getId() + " pass=" + pass.name();
    }

    private static String storageWhere(SectionRenderDataStorage storage, int localSectionIndex) {
        StorageInfo info;
        synchronized (LOCK) {
            info = STORAGES.get(storage);
        }
        if (info == null) {
            return "sectionIndex=" + localSectionIndex + " (unregistered storage)";
        }
        int chunkX = info.regionChunkX + LocalSectionIndex.unpackX(localSectionIndex);
        int chunkY = info.regionChunkY + LocalSectionIndex.unpackY(localSectionIndex);
        int chunkZ = info.regionChunkZ + LocalSectionIndex.unpackZ(localSectionIndex);
        return "section=(" + chunkX + "," + chunkY + "," + chunkZ + ") pass=" + info.pass;
    }

    /** Caller must hold LOCK. Replaces the owner's previous range of this arena kind, then checks the rest. */
    private static void checkOverlap(RenderRegion region, String kind, String ownerKey,
                                     RenderSection section, TerrainRenderPass pass, MeshBufferSegment segment) {
        List<LiveRange> ranges = LIVE_RANGES.computeIfAbsent(region.getId() + "|" + kind, k -> new ArrayList<>());
        ranges.removeIf(range -> range.owner.equals(ownerKey));

        int start = segment.getOffset();
        int end = start + segment.getLength();
        for (LiveRange other : ranges) {
            if (start < other.end && other.start < end) {
                anomaly("segment-overlap", where(region, section, pass) + " kind=" + kind
                        + " seg=[" + start + ",+" + segment.getLength() + ")"
                        + " overlaps section=(" + other.chunkX + "," + other.chunkY + "," + other.chunkZ + ")"
                        + " pass=" + other.pass + " seg=[" + other.start + ",+" + (other.end - other.start) + ")"
                        + " (two live sections share arena space; one of them will be drawn with the other's mesh)");
            }
        }

        LiveRange range = new LiveRange();
        range.start = start;
        range.end = end;
        range.owner = ownerKey;
        range.chunkX = section.getChunkX();
        range.chunkY = section.getChunkY();
        range.chunkZ = section.getChunkZ();
        range.pass = pass.name();
        ranges.add(range);
    }

    private static void anomaly(String type, String detail) {
        synchronized (LOCK) {
            anomalies++;
            if (anomalies <= ANOMALY_LINE_CAP) {
                System.out.println("[Celtium] CELTIUM_C4PROBE ANOMALY type=" + type + " " + detail);
            } else {
                suppressedAnomalies++;
            }
        }
    }

    /** Caller must hold LOCK. */
    private static void tickLocked() {
        if (++callsSinceSummary >= SUMMARY_EVERY) {
            callsSinceSummary = 0;
            System.out.println("[Celtium] CELTIUM_C4PROBE SUMMARY rebuilds=" + rebuilds + " sorts=" + sorts
                    + " resizes=" + resizes + " drifts=" + drifts + " anomalies=" + anomalies
                    + " suppressedAnomalies=" + suppressedAnomalies + " liveSections=" + RECORDS.size());
        }
    }

    private static int ceilDiv(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    private static String hex(long crc) {
        return Long.toHexString(crc);
    }

    /** Horizontal chunk distance between the section and the camera position recorded when it was built; -1 if unknown. */
    private static int cameraDistanceChunks(RenderSection section) {
        double cameraX = section.lastCameraX;
        double cameraZ = section.lastCameraZ;
        if (cameraX == 0.0 && cameraZ == 0.0) {
            return -1;
        }
        int camChunkX = (int) Math.floor(cameraX / 16.0);
        int camChunkZ = (int) Math.floor(cameraZ / 16.0);
        return Math.max(Math.abs(section.getChunkX() - camChunkX), Math.abs(section.getChunkZ() - camChunkZ));
    }

    private static long crcOf(@Nullable NativeBuffer buffer) {
        if (buffer == null) {
            return -1L;
        }
        ByteBuffer bytes = buffer.getDirectBuffer().duplicate();
        int length = Math.min(buffer.getLength(), bytes.capacity());
        if (length <= 0) {
            return 0L;
        }
        bytes.position(0);
        bytes.limit(length);
        CRC32C crc = new CRC32C();
        byte[] chunk = new byte[8192];
        while (bytes.hasRemaining()) {
            int n = Math.min(chunk.length, bytes.remaining());
            bytes.get(chunk, 0, n);
            crc.update(chunk, 0, n);
        }
        return crc.getValue();
    }
}
