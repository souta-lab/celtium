#version 450
#extension GL_GOOGLE_include_directive : require

#include "terrain_section_desc.glsl"

// New-architecture Tier-A terrain vertex shader consuming 16-byte TerrainVertex16
// and GPU-resident 48-byte SectionDesc[] (indexed by gl_InstanceIndex = recordIndex).
// Output varyings match terrain_solid.frag identically.

layout(push_constant) uniform PushConstants {
    mat4 u_ProjectionMatrix; // offset 0
    mat4 u_ModelViewMatrix;  // offset 64
} pc;

layout(std140, set = 0, binding = 0) uniform CeltiumSolidUniforms {
    vec4  u_CameraOrigin;           // offset  0: xyz = camera world origin in blocks (sectionPos*16 - u_CameraOrigin)
    vec4  u_FogColor;               // offset 16
    float u_FogDensity;             // offset 32
    float u_FogStart;               // offset 36
    float u_FogEnd;                 // offset 40
    float u_MipBiasAdd;             // offset 44
    int   u_FogMode;                // offset 48
    int   u_FogShape;               // offset 52
    int   u_FragmentDiscardEnabled; // offset 56
    int   u_ChunkFadeEnabled;       // offset 60
    vec4  u_ChunkFadeParams;        // offset 64
    int   u_SolidTerrain;           // offset 80
    int   u_ShadowEnabled;          // offset 84
    float u_ShadowDarkness;         // offset 88
    mat4  u_ShadowMatrix;           // offset 96
} celtium;

layout(set = 0, binding = 1) uniform sampler2D u_LightTex;

layout(std140, set = 0, binding = 3) uniform CeltiumDynamicLights {
    vec4 u_DynamicLightInfo;
    vec4 u_DynamicLights[32];
} dynLights;

layout(std430, set = 0, binding = 5) readonly buffer SectionDescBuffer {
    uint descData[];
};

// 16-byte compressed terrain vertex (TerrainVertex16.VERTEX_FORMAT: R32G32B32A32_UINT, stride 16).
layout(location = 0) in uvec4 a_Packed;

// Output varyings matching terrain_solid.frag.
layout(location = 0) out vec4 v_Color;
layout(location = 1) out vec4 v_RdhFactor;
layout(location = 2) out vec2 v_QuadCoord;
layout(location = 3) out vec2 v_TexCoord;
layout(location = 4) out float v_FragDistance;
layout(location = 5) flat out float v_MaterialMipBias;
layout(location = 6) flat out float v_MaterialAlphaCutoff;
layout(location = 7) out vec4 v_ShadowPos;
layout(location = 8) out vec4 v_ShadowColor;

const float[4] ALPHA_CUTOFF = float[4](0.0, 0.1, 0.5, 1.0);
const int FOG_SHAPE_CYLINDRICAL = 1;

float _material_mip_bias(uint material) {
    return (material & 1u) != 0u ? 0.0 : -4.0;
}

float _material_alpha_cutoff(uint material) {
    return ALPHA_CUTOFF[(material >> 1u) & 3u];
}

float getFragDistance(int fogShape, vec3 position) {
    if (fogShape == FOG_SHAPE_CYLINDRICAL) {
        return max(length(position.xz), abs(position.y));
    }
    return length(position);
}

vec4 _sample_lightmap(sampler2D lightMap, vec2 uv) {
    return texture(lightMap, clamp((uv + 8.0) / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
}

float _dynamic_block_light(vec3 position) {
    float level = 0.0;
    int count = int(dynLights.u_DynamicLightInfo.x + 0.5);
    for (int i = 0; i < 32; i++) {
        if (i >= count) {
            break;
        }
        vec4 source = dynLights.u_DynamicLights[i];
        level = max(level, source.w - distance(position, source.xyz));
    }
    return level;
}

void main() {
    CeltiumVertex16 v = celtiumUnpackVertex16(a_Packed);

    uint base = uint(gl_InstanceIndex) * CELTIUM_SECTION_DESC_STRIDE_UINTS;
    ivec3 sectionPos = ivec3(
        int(descData[base + 0u]),
        int(descData[base + 1u]),
        int(descData[base + 2u])
    );

    vec3 sectionOriginCameraRel = vec3(sectionPos * 16) - celtium.u_CameraOrigin.xyz;
    vec3 worldPosition = v.localPos + sectionOriginCameraRel;

    v_FragDistance = getFragDistance(celtium.u_FogShape, worldPosition);
    gl_Position = pc.u_ProjectionMatrix * pc.u_ModelViewMatrix * vec4(worldPosition, 1.0);

    vec2 lightCoord = vec2(v.lightCoord);
    float dynamicLevel = _dynamic_block_light(worldPosition);
    if (dynamicLevel > 0.0) {
        lightCoord.x = max(lightCoord.x, min(dynamicLevel, 15.0) * 16.0);
    }
    vec4 lightColor = _sample_lightmap(u_LightTex, lightCoord);
    v_Color = v.color * lightColor;
    // a_RdhFactor is dropped in TerrainVertex16 (SECTION-DESC-SPEC note); neutral zero keeps parity when unused.
    v_RdhFactor = vec4(0.0);

    if (celtium.u_ShadowEnabled != 0) {
        v_ShadowPos = celtium.u_ShadowMatrix * vec4(worldPosition, 1.0);
        vec2 shadowedCoord = vec2(lightCoord.x, lightCoord.y * (1.0 - celtium.u_ShadowDarkness));
        v_ShadowColor = v.color * _sample_lightmap(u_LightTex, shadowedCoord);
    } else {
        v_ShadowPos = vec4(0.0);
        v_ShadowColor = v_Color;
    }

    int corner = gl_VertexIndex & 3;
    v_QuadCoord = vec2(corner >= 2 ? 1.0 : 0.0, (corner == 1 || corner == 2) ? 1.0 : 0.0);
    v_TexCoord = v.texCoord;
    v_MaterialMipBias = _material_mip_bias(v.materialBits) + celtium.u_MipBiasAdd;
    v_MaterialAlphaCutoff = _material_alpha_cutoff(v.materialBits);
}
