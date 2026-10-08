#version 450

// ============================================================================================
// Celtium - native Vulkan terrain renderer - SOLID pass - FRAGMENT stage.
//
// Faithful port of the OpenGL program built from
//     sodium:blocks/block_layer_opaque.fsh
// with the defines of the COMPACT chunk vertex format
//     USE_FOG, legacy fog (no USE_FOG_POSTMODERN), no USE_VANILLA_COLOR_FORMAT,
//     no CHUNK_FADE_IN_DURATION_MS, lightmap enabled. USE_FRAGMENT_DISCARD is a runtime switch.
// Everything is inlined on purpose: no #import, no #include, no injected #define.
//
// --------------------------------------------------------------------------------------------
// RESOURCE DESIGN (identical in terrain_solid.vert and terrain_solid.frag)
// --------------------------------------------------------------------------------------------
//   PUSH CONSTANTS - "PushConstants", 128 bytes = the ENTIRE push constant block, declared in the
//   VERTEX stage only (the fragment stage does not read it, so the pipeline layout push constant
//   range is stageFlags = VK_SHADER_STAGE_VERTEX_BIT, offset = 0, size = 128):
//       offset  0 : mat4 u_ProjectionMatrix
//       offset 64 : mat4 u_ModelViewMatrix
//
//   DESCRIPTOR SET 0:
//     binding 0 : uniform block "CeltiumSolidUniforms" (std140, 80 bytes)
//                   offset  0 : vec4  u_RegionOffset            xyz = region origin, w unused
//                   offset 16 : vec4  u_FogColor                rgb = fog color, a = fog mix weight
//                   offset 32 : float u_FogDensity
//                   offset 36 : float u_FogStart
//                   offset 40 : float u_FogEnd
//                   offset 44 : float u_MipBiasAdd              additive global mip bias; 0.0 = GL parity
//                   offset 48 : int   u_FogMode                 0=NONE 1=EXP2 2=EXP 3=SMOOTH
//                   offset 52 : int   u_FogShape                0=SPHERICAL 1=CYLINDRICAL 2=PLANAR
//                   offset 56 : int   u_FragmentDiscardEnabled  0=off, non-zero=alpha cutoff active
//                   offset 60 : int   u_ChunkFadeEnabled        0=off (GL parity), non-zero=fade active
//                   offset 64 : vec4  u_ChunkFadeParams         x = fade duration ms, y = chunk age ms
//     binding 1 : sampler2D u_LightTex   (light map, VERTEX stage only - not declared here)
//     binding 2 : sampler2D u_BlockTex   (block atlas, FRAGMENT stage)
//
//   ONE pipeline covers every variant; the OpenGL #ifdefs become these runtime uniforms:
//       USE_FOG                    -> u_FogMode != 0
//       USE_FOG_EXP2 / _EXP /
//       USE_FOG_SMOOTH             -> u_FogMode == 1 / 2 / 3
//       USE_FRAGMENT_DISCARD       -> u_FragmentDiscardEnabled != 0
//       CHUNK_FADE_IN_DURATION_MS  -> u_ChunkFadeEnabled != 0 (u_ChunkFadeParams x/y)
//       FOG_SHAPE_PLANAR           -> u_FogShape == 2
//       USE_FOG_POSTMODERN         -> not ported (the legacy path is what the OpenGL look uses)
//       USE_VANILLA_COLOR_FORMAT   -> not ported (the COMPACT format is the non-vanilla path)
//
// --------------------------------------------------------------------------------------------
// PRECONDITIONS THE BACKEND MUST SATISFY FOR PIXEL-EXACT OPENGL PARITY
// --------------------------------------------------------------------------------------------
//   1. The projection matrix must map OpenGL clip depth [-1, 1] to Vulkan [0, 1] in the usual
//      way. With that convention Vulkan's gl_FragCoord.z is numerically equal to the OpenGL
//      window depth, and gl_FragCoord.w is 1/w_clip in both APIs, so the PLANAR fog expression
//      `gl_FragCoord.z / gl_FragCoord.w` below is bit-for-bit the OpenGL expression.
//   2. u_BlockTex must be the mipmapped block atlas with the same filtering/anisotropy options
//      the OpenGL program used, otherwise the mip-biased fetch cannot match.
// ============================================================================================

// --------------------------------------------------------------------------------------------
// Varyings - locations match terrain_solid.vert.
// --------------------------------------------------------------------------------------------
layout(location = 0) in vec4 v_Color;
layout(location = 1) in vec4 v_RdhFactor;
layout(location = 2) in vec2 v_QuadCoord;
layout(location = 3) in vec2 v_TexCoord;
layout(location = 4) in float v_FragDistance;
layout(location = 5) flat in float v_MaterialMipBias;
layout(location = 6) flat in float v_MaterialAlphaCutoff;

// --------------------------------------------------------------------------------------------
// Descriptor set 0
// --------------------------------------------------------------------------------------------
layout(std140, set = 0, binding = 0) uniform CeltiumSolidUniforms {
    vec4  u_RegionOffset;           // offset  0
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
} celtium;

layout(set = 0, binding = 2) uniform sampler2D u_BlockTex; // The block texture

layout(location = 0) out vec4 fragColor; // The output fragment for the color framebuffer

// --------------------------------------------------------------------------------------------
// sodium:include/fog.glsl - verbatim, with the runtime fog mode replacing the OpenGL #ifdefs.
// --------------------------------------------------------------------------------------------
const int FOG_SHAPE_SPHERICAL = 0;
const int FOG_SHAPE_CYLINDRICAL = 1;
const int FOG_SHAPE_PLANAR = 2;

const int FOG_MODE_NONE = 0;   // replaces "USE_FOG undefined"
const int FOG_MODE_EXP2 = 1;   // replaces USE_FOG_EXP2
const int FOG_MODE_EXP = 2;    // replaces USE_FOG_EXP
const int FOG_MODE_SMOOTH = 3; // replaces USE_FOG_SMOOTH

vec4 _linearFog(vec4 fragColor, float fragDistance, vec4 fogColor, float fogStart, float fogEnd) {
    if (fragDistance <= fogStart) {
        return fragColor;
    }
    float factor = fragDistance < fogEnd ? smoothstep(fogStart, fogEnd, fragDistance) : 1.0; // alpha value of fog is used as a weight
    vec3 blended = mix(fragColor.rgb, fogColor.rgb, factor * fogColor.a);

    return vec4(blended, fragColor.a); // alpha value of fragment cannot be modified
}

vec4 _exp2Fog(vec4 fragColor, float fragDistance, vec4 fogColor, float fogDensity) {
    float dist = fragDistance * fogDensity;
    float factor = clamp(1.0 / exp2(dist * dist), 0.0, 1.0);
    vec3 blended = mix(fogColor.rgb, fragColor.rgb, factor * fogColor.a);

    return vec4(blended, fragColor.a); // alpha value of fragment cannot be modified
}

vec4 _expFog(vec4 fragColor, float fragDistance, vec4 fogColor, float fogDensity) {
    float dist = fragDistance * fogDensity;
    float factor = clamp(1.0 / exp(dist), 0.0, 1.0);
    vec3 blended = mix(fogColor.rgb, fragColor.rgb, factor * fogColor.a);

    return vec4(blended, fragColor.a); // alpha value of fragment cannot be modified
}

void main() {
    vec4 diffuseColor = texture(u_BlockTex, v_TexCoord, v_MaterialMipBias);


    // #ifdef USE_FRAGMENT_DISCARD
    if (celtium.u_FragmentDiscardEnabled != 0 && diffuseColor.a < v_MaterialAlphaCutoff) {
        discard;
    }

    vec4 m_color = v_Color;
    // Apply correction factor to make the resulting color very close to what true bilinear interpolation would obtain
    // min(x, y) * (1 - max(x, y)) == min(x, y) - (x * y)
    float correctionWeight = min(v_QuadCoord.x, v_QuadCoord.y)
            - (v_QuadCoord.x * v_QuadCoord.y);
    m_color += v_RdhFactor * correctionWeight;


    // Apply per-vertex color
    diffuseColor.rgb *= m_color.rgb;

    // Apply ambient occlusion "shade"
    diffuseColor.rgb *= m_color.a;

    // #ifdef USE_FOG
    if (celtium.u_FogMode == FOG_MODE_NONE) {
        fragColor = diffuseColor;
        return;
    }

    if (celtium.u_ChunkFadeEnabled != 0) {
        // Make chunk fade in over a short duration (OpenGL: CHUNK_FADE_IN_DURATION_MS > 0).
        // The age is a per-draw uniform here: the first version does not carry per-chunk ages.
        float fadeDuration = celtium.u_ChunkFadeParams.x;
        float fade = fadeDuration > 0.0
                ? (clamp(celtium.u_ChunkFadeParams.y, 0.0, fadeDuration) / fadeDuration)
                : 1.0;
        diffuseColor = vec4(mix(celtium.u_FogColor.rgb, diffuseColor.rgb, fade), diffuseColor.a);
    }

    // Legacy fog
    float fragDistance;
    if (celtium.u_FogShape == FOG_SHAPE_PLANAR) {
        fragDistance = gl_FragCoord.z / gl_FragCoord.w;
    } else {
        fragDistance = v_FragDistance;
    }

    if (celtium.u_FogMode == FOG_MODE_EXP2) {
        fragColor = _exp2Fog(diffuseColor, fragDistance, celtium.u_FogColor, celtium.u_FogDensity);
    } else if (celtium.u_FogMode == FOG_MODE_EXP) {
        fragColor = _expFog(diffuseColor, fragDistance, celtium.u_FogColor, celtium.u_FogDensity);
    } else { // FOG_MODE_SMOOTH
        fragColor = _linearFog(diffuseColor, fragDistance, celtium.u_FogColor, celtium.u_FogStart, celtium.u_FogEnd);
    }
}
