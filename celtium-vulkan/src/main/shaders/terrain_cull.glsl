// E10 (item 723): the terrain visibility test, shared between the compute generation path
// (terrain_cull.comp) and, later, the mesh stage-2 task shader (design section 8.2: the
// judgement GLSL is shared, never rewritten per stage). The Java reference implementation
// in GpuTerrainDrawTable.isVisible uses these exact formulas; design section 5.3 (b).
//
// A box is rejected only when its positive vertex lies outside a frustum plane, and the
// distance term measures to the box's closest point: both choices fall on the conservative
// side, matching the CPU's centre-plus-half-size form, so generation never drops a box the
// CPU would have drawn (the F-type direction of design section 2.1).
//
// SPACE CONTRACT (fixed 2026-11-25, arch/projection-diag): the planes are extracted from
// P * MV where MV carries rotation only — the camera translation lives in the draw side's
// per-region offset (region origin minus camera), never in MV. The planes therefore live
// in CAMERA-RELATIVE space, while boxMin and camera arrive in absolute world blocks.
// celtiumBoxVisible converts the box into camera-relative space before the plane test,
// exactly as vanilla Frustum.isBoxInFrustum and Embeddium's Viewport.isBoxVisible subtract
// the camera position first. Testing the absolute box against these planes unconverted
// displaces the frustum to the world origin and culls the surface sections around the
// camera (Phase 1 leg r10: only deep/interior geometry survived).

#ifndef CELTIUM_TERRAIN_CULL_GLSL
#define CELTIUM_TERRAIN_CULL_GLSL

// The region box an entry is culled by, in blocks. RenderRegion is 8x4x8 sections of 16
// blocks; GpuTerrainDrawTable.REGION_SIZE_*_BLOCKS carries the same numbers and a test
// holds the two together.
const ivec3 CELTIUM_REGION_SIZE_BLOCKS = ivec3(128, 64, 128);

bool celtiumBoxVisible(vec3 boxMin, vec3 boxSize, vec4 planes[6], vec3 camera, float maxDistance) {
    // Frustum test in camera-relative space (see the space contract above); the distance
    // term is translation-invariant, so it runs on the same converted box against the
    // origin instead of the absolute camera.
    vec3 relMin = boxMin - camera;
    vec3 relMax = relMin + boxSize;
    for (int p = 0; p < 6; p++) {
        vec3 positive = vec3(
            planes[p].x >= 0.0 ? relMax.x : relMin.x,
            planes[p].y >= 0.0 ? relMax.y : relMin.y,
            planes[p].z >= 0.0 ? relMax.z : relMin.z);
        if (dot(planes[p].xyz, positive) + planes[p].w < 0.0) {
            return false;
        }
    }
    vec3 closest = clamp(vec3(0.0), relMin, relMax);
    vec3 delta = -closest;
    return dot(delta, delta) <= maxDistance * maxDistance;
}

#endif
