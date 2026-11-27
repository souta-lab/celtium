package org.soutalab.celtium.vulkan.terrain.lane1;

import org.soutalab.celtium.core.frame.CameraPose;
import org.soutalab.celtium.core.frame.FrustumPlanes;

import java.util.HashMap;
import java.util.Map;

/**
 * The Lane 1 terrain world (design V040-FINAL-PLAN section 2.1): the registry the draw stage
 * serves - the resident {@link TerrainSectionStore}, the block-entity registry the shadow gather
 * walks (gap G-7), and the per-frame view state the two {@code prepare*} calls install. The world
 * owns no GPU objects of its own; it is the CPU-side bookkeeping the cull and draw stages share.
 */
public final class TerrainWorld {

    /** One registered block entity: block position and the registry type id the sink reports. */
    public record BlockEntityEntry(int blockX, int blockY, int blockZ, int typeId) {
    }

    private final TerrainSectionStore store;
    private final Map<Long, BlockEntityEntry> blockEntities = new HashMap<>();

    private CameraPose mainPose;
    private FrustumPlanes mainFrustum;
    private long mainFrameId = -1L;
    private CameraPose shadowPose;
    private FrustumPlanes shadowFrustum;
    private float shadowDistance;
    private long shadowFrameId = -1L;

    public TerrainWorld(TerrainSectionStore store) {
        this.store = store;
    }

    public TerrainSectionStore store() {
        return store;
    }

    // -- block entities (G-7) -------------------------------------------------

    private static long blockKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFF) << 42) | ((long) (y & 0xFFFFF) << 22) | (long) (z & 0x3FFFFF);
    }

    public synchronized void putBlockEntity(int blockX, int blockY, int blockZ, int typeId) {
        blockEntities.put(blockKey(blockX, blockY, blockZ),
                new BlockEntityEntry(blockX, blockY, blockZ, typeId));
    }

    public synchronized boolean removeBlockEntity(int blockX, int blockY, int blockZ) {
        return blockEntities.remove(blockKey(blockX, blockY, blockZ)) != null;
    }

    public synchronized int blockEntityCount() {
        return blockEntities.size();
    }

    /** Every registered block entity whose position lies inside the frustum (all six planes). */
    public synchronized java.util.List<BlockEntityEntry> blockEntitiesIn(FrustumPlanes frustum) {
        java.util.List<BlockEntityEntry> inside = new java.util.ArrayList<>();
        for (BlockEntityEntry entry : blockEntities.values()) {
            boolean in = true;
            for (int plane = 0; plane < FrustumPlanes.PLANE_COUNT; plane++) {
                if (frustum.signedDistance(plane, entry.blockX() + 0.5, entry.blockY() + 0.5,
                        entry.blockZ() + 0.5) < -1.0f) {
                    in = false;
                    break;
                }
            }
            if (in) {
                inside.add(entry);
            }
        }
        return inside;
    }

    // -- view state -------------------------------------------------------------

    public synchronized void setMainView(CameraPose pose, FrustumPlanes frustum, long frameId) {
        this.mainPose = pose;
        this.mainFrustum = frustum;
        this.mainFrameId = frameId;
    }

    public synchronized void setShadowView(CameraPose pose, FrustumPlanes frustum, float distance, long frameId) {
        this.shadowPose = pose;
        this.shadowFrustum = frustum;
        this.shadowDistance = distance;
        this.shadowFrameId = frameId;
    }

    public synchronized CameraPose mainPose() {
        return mainPose;
    }

    public synchronized FrustumPlanes mainFrustum() {
        return mainFrustum;
    }

    public synchronized long mainFrameId() {
        return mainFrameId;
    }

    public synchronized CameraPose shadowPose() {
        return shadowPose;
    }

    public synchronized FrustumPlanes shadowFrustum() {
        return shadowFrustum;
    }

    public synchronized float shadowDistance() {
        return shadowDistance;
    }

    public synchronized long shadowFrameId() {
        return shadowFrameId;
    }

    public synchronized boolean hasShadowView() {
        return shadowFrustum != null;
    }
}
