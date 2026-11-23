// E10 (item 723): the terrain visibility test, shared between the compute generation path
// (terrain_cull.comp) and, later, the mesh stage-2 task shader (design section 8.2: the
// judgement GLSL is shared, never rewritten per stage). The Java reference implementation
// in GpuTerrainDrawTable.isVisible uses these exact formulas; design section 5.3 (b).
//
// A box is rejected only when its positive vertex lies outside a frustum plane, and the
// distance term measures to the box's closest point: both choices fall on the conservative
// side, matching the CPU's centre-plus-half-size form, so generation never drops a box the
// CPU would have drawn (the F-type direction of design section 2.1).

#ifndef CELTIUM_TERRAIN_CULL_GLSL
#define CELTIUM_TERRAIN_CULL_GLSL

// The region box an entry is culled by, in blocks. RenderRegion is 8x4x8 sections of 16
// blocks; GpuTerrainDrawTable.REGION_SIZE_*_BLOCKS carries the same numbers and a test
// holds the two together.
const ivec3 CELTIUM_REGION_SIZE_BLOCKS = ivec3(128, 64, 128);

bool celtiumBoxVisible(vec3 boxMin, vec3 boxSize, vec4 planes[6], vec3 camera, float maxDistance) {
    vec3 boxMax = boxMin + boxSize;
    for (int p = 0; p < 6; p++) {
        vec3 positive = vec3(
            planes[p].x >= 0.0 ? boxMax.x : boxMin.x,
            planes[p].y >= 0.0 ? boxMax.y : boxMin.y,
            planes[p].z >= 0.0 ? boxMax.z : boxMin.z);
        if (dot(planes[p].xyz, positive) + planes[p].w < 0.0) {
            return false;
        }
    }
    vec3 closest = clamp(camera, boxMin, boxMax);
    vec3 delta = camera - closest;
    return dot(delta, delta) <= maxDistance * maxDistance;
}

#endif
