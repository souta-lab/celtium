#version 450

// ============================================================================================
// Celtium - new rendering architecture - Tier-A terrain - VERTEX stage.
//
// The 16-byte vertex counterpart of terrain_solid.vert: same varyings, same lighting,
// same fog and shadow behaviour, so terrain_solid.frag serves both paths unchanged.
// What differs is where the inputs come from (SECTION-DESC-SPEC-2026-11-25.md):
//
//   * The vertex is one uvec4 (TerrainVertex16): 12-bit positions, 18-bit UV, packed
//     light, material, colour. The per-vertex section index of the compact format is
//     gone; the section is the SectionDesc record the draw's firstInstance names.
//   * The bilinear colour correction (a_RdhFactor in the compact format) is a per-quad
//     constant carried in a side stream: this stage reads it from the factor arena
//     through the record's factor base (binding 7) plus the quad's index within the
//     section, derived from gl_VertexIndex and the desc's vertexOffset.
//
// Extra descriptors over terrain_solid.vert's set 0 (the Tier-A pipeline layout adds
// them; the shared bindings 0/1/3 keep their meanings, binding 5 is unused here):
//   binding 6 : storage buffer "CeltiumSectionDescs" (std430, uint per word) - the
//               same resident table the culling compute stage reads.
//   binding 7 : storage buffer "CeltiumSectionFactorBases" (std430, one uint per
//               record) - factor-stream base of each record, in uints.
//   binding 8 : storage buffer "CeltiumRdhFactors" (std430, one uint per quad) - the
//               factor arena the bases index into.
//
// Backend contract: u_RegionOffset (binding 0) carries world-origin-minus-camera for
// the Tier-A route, replacing the region translation + per-draw translation pair of
// the compact path; the section translation comes from the desc. Preconditions 2-5 of
// terrain_solid.vert (projection/depth convention, atlas settings, quad topology) are
// unchanged.
// ============================================================================================

layout(push_constant) uniform PushConstants {
    mat4 u_ProjectionMatrix;
    mat4 u_ModelViewMatrix;
} pc;

layout(std140, set = 0, binding = 0) uniform CeltiumSolidUniforms {
    vec4  u_RegionOffset;
    vec4  u_FogColor;
    float u_FogDensity;
    float u_FogStart;
    float u_FogEnd;
    float u_MipBiasAdd;
    int   u_FogMode;
    int   u_FogShape;
    int   u_FragmentDiscardEnabled;
    int   u_ChunkFadeEnabled;
    vec4  u_ChunkFadeParams;
    int   u_SolidTerrain;
    int   u_ShadowEnabled;
    float u_ShadowDarkness;
    mat4  u_ShadowMatrix;
} celtium;

layout(set = 0, binding = 1) uniform sampler2D u_LightTex;

layout(std140, set = 0, binding = 3) uniform CeltiumDynamicLights {
    vec4 u_DynamicLightInfo;
    vec4 u_DynamicLights[32];
} dynLights;

layout(std430, set = 0, binding = 6) readonly buffer CeltiumSectionDescs {
    uint u_SectionDescs[];
};
layout(std430, set = 0, binding = 7) readonly buffer CeltiumSectionFactorBases {
    uint u_SectionFactorBases[];
};
layout(std430, set = 0, binding = 8) readonly buffer CeltiumRdhFactors {
    uint u_RdhFactors[];
};

// The 16-byte Tier-A vertex (VK_FORMAT_R32G32B32A32_UINT, stride 16).
layout(location = 0) in uvec4 a_VertexData;

layout(location = 0) out vec4 v_Color;
layout(location = 1) out vec4 v_RdhFactor;
layout(location = 2) out vec2 v_QuadCoord;
layout(location = 3) out vec2 v_TexCoord;
layout(location = 4) out float v_FragDistance;
layout(location = 5) flat out float v_MaterialMipBias;
layout(location = 6) flat out float v_MaterialAlphaCutoff;
layout(location = 7) out vec4 v_ShadowPos;
layout(location = 8) out vec4 v_ShadowColor;

// 12-bit positions over the same MODEL_ORIGIN/MODEL_RANGE space as the compact format.
const float VERT_POS_SCALE = 32.0 / 4096.0; // exact power of two: 2^-7
const float VERT_POS_OFFSET = -8.0;
const float VERT_TEX_SCALE = 1.0 / 131072.0;

const uint MATERIAL_USE_MIP_OFFSET = 0u;
const uint MATERIAL_ALPHA_CUTOFF_OFFSET = 1u;
const float[4] ALPHA_CUTOFF = float[4](0.0, 0.1, 0.5, 1.0);

float _material_mip_bias(uint material) {
    return ((material >> MATERIAL_USE_MIP_OFFSET) & 1u) != 0u ? 0.0 : -4.0;
}

float _material_alpha_cutoff(uint material) {
    return ALPHA_CUTOFF[(material >> MATERIAL_ALPHA_CUTOFF_OFFSET) & 3u];
}

const int FOG_SHAPE_SPHERICAL = 0;
const int FOG_SHAPE_CYLINDRICAL = 1;
const int FOG_SHAPE_PLANAR = 2;

float getFragDistance(int fogShape, vec3 position) {
    if (fogShape == FOG_SHAPE_CYLINDRICAL) {
        return max(length(position.xz), abs(position.y));
    } else {
        return length(position);
    }
}

// Identical to terrain_solid.vert's light map sampling (see its header for why the
// divisor is 16 and the half-texel offset matters).
vec4 _sample_lightmap(sampler2D lightMap, vec2 uv) {
    return texture(lightMap, clamp((vec2(uv) + 8.0) / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
}

// Identical to terrain_solid.vert's dynamic raise (camera-relative positions both sides).
float _dynamic_block_light(vec3 position) {
    float level = 0.0;
    int count = int(dynLights.u_DynamicLightInfo.x + 0.5);
    vec3 relativePosition = position;
    for (int i = 0; i < 32; i++) {
        if (i >= count) {
            break;
        }
        vec4 source = dynLights.u_DynamicLights[i];
        level = max(level, source.w - distance(relativePosition, source.xyz));
    }
    return level;
}

void main() {
    uint record = uint(gl_InstanceIndex);
    uint base = record * 12u;
    ivec3 section = ivec3(int(u_SectionDescs[base + 0u]), int(u_SectionDescs[base + 1u]), int(u_SectionDescs[base + 2u]));
    uint vertexOffset = u_SectionDescs[base + 4u];

    // ---- TerrainVertex16 expansion ---------------------------------------------------
    uvec4 data = a_VertexData;
    uvec3 position = uvec3(data.x & 0xFFFu, (data.x >> 12u) & 0xFFFu,
            (data.x >> 24u) | ((data.y & 0xFu) << 8u));
    // word1 bits [4..6] carry the bucket-order normal id; the solid fragment path is
    // lightmap-lit and does not consume it (the compact path has no normal either).
    uint material = (data.y >> 7u) & 0xFu;
    ivec2 lightCoord = ivec2(int((data.y >> 11u) & 0xFFu), int((data.y >> 19u) & 0xFFu));
    uvec2 texCoord = uvec2(((data.z & 0xFFFFu) << 2u) | ((data.y >> 27u) & 0x3u),
            ((data.z >> 16u) << 2u) | ((data.y >> 29u) & 0x3u));

    vec3 _vert_position = vec3(position) * VERT_POS_SCALE + VERT_POS_OFFSET;
    vec2 _vert_tex_diffuse_coord = vec2(texCoord) * VERT_TEX_SCALE;
    vec4 _vert_color = unpackUnorm4x8(data.w);

    // The per-quad correction factor: this vertex' quad within the section, from the
    // desc's vertexOffset (the shared index buffer's values make gl_VertexIndex the
    // slab vertex index, so the difference is the section-local vertex index).
    uint localQuad = (uint(gl_VertexIndex) - vertexOffset) >> 2u;
    vec4 _vert_rdh_factor = unpackSnorm4x8(u_RdhFactors[u_SectionFactorBases[record] + localQuad]);

    // ---- main() (mirrors terrain_solid.vert) ------------------------------------------
    vec3 translation = vec3(section * 16) + celtium.u_RegionOffset.xyz;
    vec3 worldPosition = _vert_position + translation;

    v_FragDistance = getFragDistance(celtium.u_FogShape, worldPosition);
    gl_Position = pc.u_ProjectionMatrix * pc.u_ModelViewMatrix * vec4(worldPosition, 1.0);

    v_RdhFactor = _vert_rdh_factor * 2.0;
    vec2 lightUv = vec2(lightCoord);
    float dynamicLevel = _dynamic_block_light(worldPosition);
    if (dynamicLevel > 0.0) {
        lightUv.x = max(lightUv.x, min(dynamicLevel, 15.0) * 16.0);
    }
    vec4 lightColor = _sample_lightmap(u_LightTex, lightUv);
    v_Color = _vert_color * lightColor;
    if (celtium.u_ShadowEnabled != 0) {
        v_ShadowPos = celtium.u_ShadowMatrix * vec4(worldPosition, 1.0);
        vec2 shadowedCoord = vec2(lightUv.x, lightUv.y * (1.0 - celtium.u_ShadowDarkness));
        v_ShadowColor = _vert_color * _sample_lightmap(u_LightTex, shadowedCoord);
    } else {
        v_ShadowPos = vec4(0.0);
        v_ShadowColor = v_Color;
    }
    v_RdhFactor.rgb *= lightColor.rgb;

    int corner = gl_VertexIndex & 3;
    v_QuadCoord = vec2(corner >= 2 ? 1.0 : 0.0,
            corner == 1 || corner == 2 ? 1.0 : 0.0);
    v_TexCoord = _vert_tex_diffuse_coord;

    v_MaterialMipBias = _material_mip_bias(material) + celtium.u_MipBiasAdd;
    v_MaterialAlphaCutoff = _material_alpha_cutoff(material);
}
