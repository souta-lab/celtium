// Canonical 48-byte SectionDesc and 16-byte TerrainVertex16 GLSL unpack helpers.
// Matches SECTION-DESC-SPEC-2026-11-25.md, SectionDesc.java, and TerrainVertex16.java bit-for-bit.
// Shared between Tier-A (terrain_cull_a.comp / terrain_a.vert) and Tier-B (arch/mesh-b).

#ifndef CELTIUM_TERRAIN_SECTION_DESC_GLSL
#define CELTIUM_TERRAIN_SECTION_DESC_GLSL

const uint CELTIUM_SECTION_DESC_STRIDE_UINTS = 12u;
const uint CELTIUM_SECTION_BUCKET_COUNT = 7u;

const uint CELTIUM_BUCKET_DOWN       = 0u;
const uint CELTIUM_BUCKET_UP         = 1u;
const uint CELTIUM_BUCKET_NORTH      = 2u;
const uint CELTIUM_BUCKET_SOUTH      = 3u;
const uint CELTIUM_BUCKET_WEST       = 4u;
const uint CELTIUM_BUCKET_EAST       = 5u;
const uint CELTIUM_BUCKET_UNASSIGNED = 6u;

const uint CELTIUM_SECTION_FLAG_OVERSIZE     = 1u << 0u;
const uint CELTIUM_SECTION_FLAG_SORTED_INDEX = 1u << 1u;
const uint CELTIUM_SECTION_FLAG_ANIMATED     = 1u << 2u;

const uint CELTIUM_UNUSED_SORTED_INDEX_BASE = 0xFFFFFFFFu;

const float CELTIUM_V16_POS_SCALE  = 32.0 / 4096.0;   // 1/128 block
const float CELTIUM_V16_POS_OFFSET = -8.0;
const float CELTIUM_V16_TEX_SCALE  = 1.0 / 131072.0;  // 2^-17

struct CeltiumSectionDesc {
    ivec3 sectionPos;
    uint  slabId;
    uint  vertexOffset;
    uint  quadStart;
    uint  bucketCounts[7];
    uint  passOrdinal;
    uint  flags;
    uint  generation;
    uint  sortedIndexBase;
};

struct CeltiumVertex16 {
    vec3  localPos;
    uint  normalId;
    uint  materialBits;
    ivec2 lightCoord; // x = block [0..255], y = sky [0..255]
    vec2  texCoord;
    vec4  color;
};

CeltiumVertex16 celtiumUnpackVertex16(uvec4 packed) {
    uint w0 = packed.x;
    uint w1 = packed.y;
    uint w2 = packed.z;
    uint w3 = packed.w;

    uint qx = w0 & 0xFFFu;
    uint qy = (w0 >> 12u) & 0xFFFu;
    uint qz = ((w0 >> 24u) & 0xFFu) | ((w1 & 0xFu) << 8u);

    uint normalId     = (w1 >> 4u) & 0x7u;
    uint materialBits = (w1 >> 7u) & 0xFu;
    uint lightBlock   = (w1 >> 11u) & 0xFFu;
    uint lightSky     = (w1 >> 19u) & 0xFFu;
    uint uvLowU       = (w1 >> 27u) & 0x3u;
    uint uvLowV       = (w1 >> 29u) & 0x3u;

    uint qU = ((w2 & 0xFFFFu) << 2u) | uvLowU;
    uint qV = ((w2 >> 16u) << 2u) | uvLowV;

    CeltiumVertex16 v;
    v.localPos     = vec3(qx, qy, qz) * CELTIUM_V16_POS_SCALE + CELTIUM_V16_POS_OFFSET;
    v.normalId     = normalId;
    v.materialBits = materialBits;
    v.lightCoord   = ivec2(int(lightBlock), int(lightSky));
    v.texCoord     = vec2(qU, qV) * CELTIUM_V16_TEX_SCALE;
    v.color        = unpackUnorm4x8(w3);
    return v;
}

bool celtiumFaceBucketVisible(uint bucket, vec3 boxMin, vec3 boxMax, vec3 cameraPos) {
    if (bucket == CELTIUM_BUCKET_DOWN)  return cameraPos.y < boxMax.y;
    if (bucket == CELTIUM_BUCKET_UP)    return cameraPos.y > boxMin.y;
    if (bucket == CELTIUM_BUCKET_NORTH) return cameraPos.z < boxMax.z;
    if (bucket == CELTIUM_BUCKET_SOUTH) return cameraPos.z > boxMin.z;
    if (bucket == CELTIUM_BUCKET_WEST)  return cameraPos.x < boxMax.x;
    if (bucket == CELTIUM_BUCKET_EAST)  return cameraPos.x > boxMin.x;
    return true; // CELTIUM_BUCKET_UNASSIGNED
}

#endif
