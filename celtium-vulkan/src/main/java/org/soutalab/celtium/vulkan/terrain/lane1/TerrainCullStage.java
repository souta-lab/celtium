package org.soutalab.celtium.vulkan.terrain.lane1;

import org.soutalab.celtium.core.frame.CameraPose;
import org.soutalab.celtium.core.frame.FrustumPlanes;
import org.soutalab.celtium.core.geometry.SectionDesc;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

/**
 * The Lane 1 culling stage (design V040-FINAL-PLAN section 2.1): per frame it turns the resident
 * descriptors plus a view into the visible section set and the light grid the terrain passes
 * consume. The production path is the GPU one - the cull compute shader ({@link Lane1Shaders})
 * runs frustum + half-resolution Hi-Z occlusion (plan ruling R-19 plan-2, falsification condition
 * K3) against the descriptor buffer and writes the indirect command stream directly. This class
 * is the CPU mirror of that first stage: the same frustum test over the same descriptors, used
 * for the shadow block-entity gather, for diagnostics, and as the fallback path when a device
 * cannot run the compute cull. The Hi-Z pyramid itself is GPU-resident; this class only computes
 * its shape.
 */
public final class TerrainCullStage {

    private final TerrainWorld world;

    public TerrainCullStage(TerrainWorld world) {
        this.world = world;
    }

    /** Sections whose 16-cubed AABB is not fully outside any frustum plane, in descriptor order. */
    public List<SectionDesc> visibleSections(FrustumPlanes frustum, CameraPose pose) {
        List<SectionDesc> visible = new ArrayList<>();
        for (SectionDesc desc : world.store().descriptors()) {
            if (sectionVisible(frustum, pose, desc)) {
                visible.add(desc);
            }
        }
        return visible;
    }

    /**
     * Convenience form for views whose origin is (0, 0, 0).
     */
    public List<SectionDesc> visibleSections(FrustumPlanes frustum) {
        return visibleSections(frustum, new CameraPose(0.0, 0.0, 0.0, 0.0f, 0.0f));
    }

    /**
     * Conservative AABB/frustum test: culled only when every corner is outside one plane.
     * Shifts the section's world AABB by the view's origin in double precision before
     * evaluating the origin-relative planes.
     */
    public static boolean sectionVisible(FrustumPlanes frustum, CameraPose pose, SectionDesc desc) {
        double minX = desc.sectionX() * 16.0 - pose.x();
        double minY = desc.sectionY() * 16.0 - pose.y();
        double minZ = desc.sectionZ() * 16.0 - pose.z();
        double maxX = minX + 16.0;
        double maxY = minY + 16.0;
        double maxZ = minZ + 16.0;
        for (int plane = 0; plane < FrustumPlanes.PLANE_COUNT; plane++) {
            boolean allOutside = true;
            for (int corner = 0; corner < 8; corner++) {
                double x = (corner & 1) == 0 ? minX : maxX;
                double y = (corner & 2) == 0 ? minY : maxY;
                double z = (corner & 4) == 0 ? minZ : maxZ;
                if (frustum.signedDistance(plane, x, y, z) >= 0.0f) {
                    allOutside = false;
                    break;
                }
            }
            if (allOutside) {
                return false;
            }
        }
        return true;
    }

    /**
     * Convenience form for views whose origin is (0, 0, 0).
     */
    public static boolean sectionVisible(FrustumPlanes frustum, SectionDesc desc) {
        return sectionVisible(frustum, new CameraPose(0.0, 0.0, 0.0, 0.0f, 0.0f), desc);
    }

    /**
     * The half-resolution Hi-Z pyramid shape for a render target: level 0 is the half-res depth
     * reduction, each further level halves again down to 1 texel. Returns the level count.
     */
    public static int hiZPyramidLevels(int width, int height) {
        int w = Math.max(1, width / 2);
        int h = Math.max(1, height / 2);
        int levels = 1;
        while (w > 1 || h > 1) {
            w = Math.max(1, w / 2);
            h = Math.max(1, h / 2);
            levels++;
        }
        return levels;
    }

    /** Builds the frame's light grid around a centre (usually the camera) into caller segments. */
    public LightGridBuilder.Result buildLightGrid(List<LightGridBuilder.PointLight> lights,
                                                  double centerX, double centerY, double centerZ,
                                                  MemorySegment ubo, MemorySegment masks) {
        return LightGridBuilder.build(lights, centerX, centerY, centerZ, ubo, masks);
    }
}
