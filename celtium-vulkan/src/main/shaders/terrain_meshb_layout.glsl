// ============================================================================================
// Celtium - new rendering architecture - terrain Tier-B (mesh shader) shared layout contract.
//
// This include is the GLSL half of the terrain data contract, mirroring the canonical
// definition owned by the Tier-A terrain engine (lane arch-terrain):
// night-queue/SECTION-DESC-SPEC-2026-11-25.md, ruled canonical by coord-migration (#8650).
// The Java mirror is org.soutalab.celtium.vulkan.meshb.MeshTerrainLayout, held to this
// file by MeshTerrainLayoutTest. If the canonical definition changes, this file and that
// class are the only consumer-side files that move.
//
// Stage files must declare this storage buffer, with this exact name, BEFORE including
// this file (the accessors below read it):
//   layout(set = 0, binding = 0, std430) readonly buffer CeltiumSectionDescs { uint celtiumDescData[]; };
// The slab vertex buffer (binding 2) and the per-quad RDH stream (binding 5) are
// mesh-stage only and declared by the mesh file itself; the unpack function here takes
// the vertex words directly.
//
// --------------------------------------------------------------------------------------------
// SectionDesc - 48 bytes per record, twelve uint words, GPU-resident. One record per
// (section, pass); records live in per-pass arrays, so a dispatch reads one pass' array.
// Sections are 16x16x16 and grid-aligned, so the world AABB is sectionPos * 16 .. +16 and
// is derived, never stored.
//   word 0..2 : section position (i32 x, y, z, in sections)
//   word 3    : slab id (u32)
//   word 4    : vertex offset (u32, section's first vertex, slab-relative, in vertices)
//   word 5    : quad start (u32, section's first quad, slab-relative, in quads)
//   word 6    : bucketCount[0] (low u16) | bucketCount[1] (high u16)
//   word 7    : bucketCount[2] (low) | bucketCount[3] (high)
//   word 8    : bucketCount[4] (low) | bucketCount[5] (high)
//   word 9    : bucketCount[6] (low u16) | pass ordinal (u8, bits 16..23) | flags (u8, 24..31)
//   word 10   : generation (u32, the record's version for update/invalidation detection)
//   word 11   : sorted index base (u32, 0xFFFFFFFF when unused)
// Buckets are contiguous in bucket order inside the section's slab run, so bucket b's
// first quad is quadStart + sum(counts[0..b-1]) - offsets are derived, never stored.
// A record whose total quad count is 0 is never issued by the producer.
//
// Bucket order is the producer's ModelQuadFacing.VALUES order (NOT EnumFacing):
//   0 = +X, 1 = +Y, 2 = +Z, 3 = -X, 4 = -Y, 5 = -Z, 6 = Unassigned.
// Flags (word 9 bits 24..31): bit 24 OVERSIZE (a bucket count exceeded u16 at build time;
// the section is Tier-A ineligible and stays with the legacy path), bit 25 SORTED_INDEX
// (sortedIndexBase is live), bit 26 ANIMATED (holds animated sprites, tick-updated).
//
// --------------------------------------------------------------------------------------------
// TerrainVertex16 - 16 bytes per vertex (4 x u32), indexless: a quad is four consecutive
// vertices, triangulated as (0, 1, 2) and (2, 3, 0) - the Celeritas
// QuadPrimitiveType.TRIANGULATED pattern the mesher's corner order is built against.
//   word 0    : posX 12 bits [0..11] | posY 12 bits [12..23] | posZ low 8 bits [24..31]
//   word 1    : posZ high 4 bits [0..3] | normalId 3 bits [4..6] | material 4 bits [7..10]
//               | lightBlock 8 bits [11..18] | lightSky 8 bits [19..26]
//               | uvLowU 2 bits [27..28] | uvLowV 2 bits [29..30] | spare [31]
//   word 2    : texU high 16 bits [0..15] | texV high 16 bits [16..31]
//   word 3    : colour RGBA8 (the COMPACT format's a_Color, unchanged)
// Position keeps the COMPACT format's model space: section-relative with origin -8 and
// range 32, quantized to 12 bits per axis (step 32/4096 = 1/128 block):
// stored = round((local + 8) * 128), local = stored * (32/4096) - 8.
// UV keeps the COMPACT 18-bit encoding: value = ((high << 2) | low) * (1/131072).
// Light components are lightmap texel coordinates (0..255), as in the COMPACT format.
// normalId 0..5 uses the bucket numbering; 6 = none (flat derivation), 7 = reserved.
// material is the COMPACT format's 4-bit material params (bit 0 mip select, bits 1..2
// alpha cutoff index - see celtiumMaterialAlphaCutoff).
//
// The bilinear colour correction factor (the 24-byte format's a_RdhFactor) is NOT in
// this format; per the spec's ruling (ii) it travels as a separate per-quad stream of
// one u32 per quad, addressed by slab quad id (desc.quadStart + section-relative quad).
// ============================================================================================

#ifndef CELTIUM_TERRAIN_MESHB_LAYOUT_GLSL
#define CELTIUM_TERRAIN_MESHB_LAYOUT_GLSL

const uint CELTIUM_MESHB_DESC_WORDS = 12u;
const uint CELTIUM_MESHB_BUCKETS_PER_SECTION = 7u;
const uint CELTIUM_MESHB_QUADS_PER_MESHLET = 64u;
const uint CELTIUM_MESHB_VERTICES_PER_QUAD = 4u;

const uint CELTIUM_BUCKET_POS_X = 0u;
const uint CELTIUM_BUCKET_POS_Y = 1u;
const uint CELTIUM_BUCKET_POS_Z = 2u;
const uint CELTIUM_BUCKET_NEG_X = 3u;
const uint CELTIUM_BUCKET_NEG_Y = 4u;
const uint CELTIUM_BUCKET_NEG_Z = 5u;
const uint CELTIUM_BUCKET_UNASSIGNED = 6u;

const uint CELTIUM_DESC_FLAG_OVERSIZE = 0x01000000u;
const uint CELTIUM_DESC_FLAG_SORTED_INDEX = 0x02000000u;
const uint CELTIUM_DESC_FLAG_ANIMATED = 0x04000000u;

// ---- SectionDesc accessors (word-indexed; std430 struct layout would pad the ivec3) ------

uint celtiumDescWord(uint record, uint word) {
    return celtiumDescData[record * CELTIUM_MESHB_DESC_WORDS + word];
}

ivec3 celtiumDescSectionPos(uint record) {
    return ivec3(int(celtiumDescWord(record, 0u)), int(celtiumDescWord(record, 1u)),
            int(celtiumDescWord(record, 2u)));
}

uint celtiumDescSlabId(uint record) {
    return celtiumDescWord(record, 3u);
}

uint celtiumDescVertexOffset(uint record) {
    return celtiumDescWord(record, 4u);
}

uint celtiumDescQuadStart(uint record) {
    return celtiumDescWord(record, 5u);
}

uint celtiumDescBucketCount(uint record, uint bucket) {
    uint word = celtiumDescWord(record, 6u + (bucket >> 1u));
    return (bucket & 1u) == 0u ? (word & 0xFFFFu) : (word >> 16u);
}

uint celtiumDescPassOrdinal(uint record) {
    return (celtiumDescWord(record, 9u) >> 16u) & 0xFFu;
}

uint celtiumDescFlags(uint record) {
    return celtiumDescWord(record, 9u) & 0xFF000000u;
}

uint celtiumDescTotalQuads(uint record) {
    uint total = 0u;
    for (uint bucket = 0u; bucket < CELTIUM_MESHB_BUCKETS_PER_SECTION; bucket++) {
        total += celtiumDescBucketCount(record, bucket);
    }
    return total;
}

// The section-relative quad index at which a bucket starts (prefix sum of counts).
uint celtiumBucketQuadStartInSection(uint record, uint bucket) {
    uint start = 0u;
    for (uint b = 0u; b < bucket; b++) {
        start += celtiumDescBucketCount(record, b);
    }
    return start;
}

uint celtiumMeshletsForCount(uint quadCount) {
    return (quadCount + CELTIUM_MESHB_QUADS_PER_MESHLET - 1u) / CELTIUM_MESHB_QUADS_PER_MESHLET;
}

// ---- Facing test ---------------------------------------------------------------------------
// A face bucket is dropped only when the camera is provably behind every face of that
// direction inside the section: bucket +X holds faces whose planes lie within
// [boxMin.x, boxMax.x], all back-facing when camera.x <= boxMin.x. The unassigned bucket
// is never dropped. Bucket numbering is ModelQuadFacing.VALUES order (see the header).

bool celtiumBucketFacingVisible(uint bucket, vec3 boxMin, vec3 boxMax, vec3 camera) {
    if (bucket == CELTIUM_BUCKET_POS_X) return camera.x > boxMin.x;
    if (bucket == CELTIUM_BUCKET_POS_Y) return camera.y > boxMin.y;
    if (bucket == CELTIUM_BUCKET_POS_Z) return camera.z > boxMin.z;
    if (bucket == CELTIUM_BUCKET_NEG_X) return camera.x < boxMax.x;
    if (bucket == CELTIUM_BUCKET_NEG_Y) return camera.y < boxMax.y;
    if (bucket == CELTIUM_BUCKET_NEG_Z) return camera.z < boxMax.z;
    return true;
}

// ---- TerrainVertex16 unpack ----------------------------------------------------------------

void celtiumArchUnpackVertex(uvec4 w, out vec3 localPos, out vec4 color, out vec2 uv,
        out vec2 light, out uint normalId, out uint material) {
    uint posX = w.x & 0xFFFu;
    uint posY = (w.x >> 12u) & 0xFFFu;
    uint posZ = ((w.x >> 24u) & 0xFFu) | ((w.y & 0xFu) << 8u);
    localPos = vec3(float(posX), float(posY), float(posZ)) * (32.0 / 4096.0) - 8.0;
    normalId = (w.y >> 4u) & 7u;
    material = (w.y >> 7u) & 0xFu;
    light = vec2(float((w.y >> 11u) & 0xFFu), float((w.y >> 19u) & 0xFFu));
    uint uvU = ((w.z & 0xFFFFu) << 2u) | ((w.y >> 27u) & 3u);
    uint uvV = ((w.z >> 16u) << 2u) | ((w.y >> 29u) & 3u);
    uv = vec2(float(uvU), float(uvV)) * (1.0 / 131072.0);
    color = unpackUnorm4x8(w.w);
}

// ---- Material interpretation (the terrain fragment family's existing tables) ---------------

float celtiumMaterialMipBias(uint material) {
    return (material & 1u) != 0u ? 0.0 : -4.0;
}

float celtiumMaterialAlphaCutoff(uint material) {
    uint index = (material >> 1u) & 3u;
    if (index == 0u) return 0.0;
    if (index == 1u) return 0.1;
    if (index == 2u) return 0.5;
    return 1.0;
}

#endif
