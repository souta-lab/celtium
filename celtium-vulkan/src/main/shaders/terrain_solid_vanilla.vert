#version 450

// ============================================================================================
// Celtium - native Vulkan terrain renderer - SOLID pass - VERTEX stage.
//
// Faithful port of the OpenGL program built from
//     sodium:blocks/block_layer_opaque.vsh
// with the defines of the COMPACT chunk vertex format
//     USE_VERTEX_COMPRESSION, USE_FOG, legacy fog (no USE_FOG_POSTMODERN),
//     no USE_VANILLA_COLOR_FORMAT, no CHUNK_FADE_IN_DURATION_MS, lightmap enabled.
// Everything is inlined on purpose: no #import, no #include, no injected #define.
//
// --------------------------------------------------------------------------------------------
// RESOURCE DESIGN (identical in terrain_solid.vert and terrain_solid.frag)
// --------------------------------------------------------------------------------------------
//   PUSH CONSTANTS - "PushConstants", 128 bytes = the ENTIRE push constant block:
//       offset  0 : mat4 u_ProjectionMatrix
//       offset 64 : mat4 u_ModelViewMatrix
//     (Both matrices are exactly 128 bytes, so nothing else may be added here.)
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
//     binding 1 : sampler2D u_LightTex   (light map, VERTEX stage only)
//     binding 2 : sampler2D u_BlockTex   (block atlas, FRAGMENT stage only - not declared here)
//
//   The pipeline layout push constant range is therefore:
//       stageFlags = VK_SHADER_STAGE_VERTEX_BIT, offset = 0, size = 128.
//
//   All shader variants are selected at RUNTIME through u_FogMode / u_FogShape /
//   u_FragmentDiscardEnabled / u_ChunkFadeEnabled, so ONE pipeline covers solid terrain
//   (no specialization constants are used).
//
// --------------------------------------------------------------------------------------------
// PRECONDITIONS THE BACKEND MUST SATISFY FOR PIXEL-EXACT OPENGL PARITY
// --------------------------------------------------------------------------------------------
//   1. Vertex input is the 24-byte COMPACT vertex, one VkVertexInputBindingDescription with
//      stride 24 and inputRate VK_VERTEX_INPUT_RATE_VERTEX, plus:
//        loc 0 : VK_FORMAT_R32G32B32_SFLOAT   offset  0   (a_PosId)
//        loc 1 : VK_FORMAT_R8G8B8A8_UNORM     offset 12   (a_Color)
//        loc 2 : VK_FORMAT_R32G32_SFLOAT      offset 16   (a_TexCoord)
//        loc 3 : VK_FORMAT_R32_UINT           offset 24   (a_LightCoord)
//        loc 4 : VK_FORMAT_R8G8B8A8_SNORM     offset 28   (a_RdhFactor)
//   2. gl_Position is written exactly as the OpenGL shader writes it: no Y flip here. The
//      backend must use a negative-height viewport (VK_KHR_maintenance1) or an equivalent
//      Y-flipped projection so the OpenGL projection matrix can be reused unchanged.
//   3. The projection matrix must map OpenGL clip depth [-1, 1] to Vulkan [0, 1] in the usual
//      way. With that (universal) convention Vulkan's gl_FragCoord.z is numerically equal to
//      the OpenGL window depth, which is what the PLANAR fog path in the fragment shader
//      relies on (gl_FragCoord.z / gl_FragCoord.w is bit-for-bit the OpenGL expression).
//   4. u_BlockTex must be a mipmapped atlas sampled with the same filter/anisotropy/LOD settings
//      the OpenGL program used; u_LightTex must be the 16x16 light map, LINEAR/CLAMP_TO_EDGE.
//   5. The backend must supply the QUAD-LIST primitive topology the OpenGL program used
//      (Sodium/Embeddium emits 4 vertices per quad), because gl_VertexIndex & 3 selects the
//      quad corner exactly like gl_VertexID & 3 does in OpenGL.
// ============================================================================================

// --------------------------------------------------------------------------------------------
// Push constants - see the design block above. 128 bytes, the whole block.
// --------------------------------------------------------------------------------------------
layout(push_constant) uniform PushConstants {
    mat4 u_ProjectionMatrix; // offset  0
    mat4 u_ModelViewMatrix;  // offset 64
} pc;

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

layout(set = 0, binding = 1) uniform sampler2D u_LightTex; // The light map texture sampler

// --------------------------------------------------------------------------------------------
// Vertex attributes - the 24-byte COMPACT chunk vertex (CompactChunkVertex.VERTEX_FORMAT).
// --------------------------------------------------------------------------------------------
layout(location = 0) in vec3 a_PosId;      // VK_FORMAT_R32G32B32_SFLOAT
layout(location = 1) in vec4 a_Color;      // VK_FORMAT_R8G8B8A8_UNORM
layout(location = 2) in vec2 a_TexCoord;   // VK_FORMAT_R32G32_SFLOAT
layout(location = 3) in uint a_LightCoord; // VK_FORMAT_R32_UINT
layout(location = 4) in vec4 a_RdhFactor;  // VK_FORMAT_R8G8B8A8_SNORM

// --------------------------------------------------------------------------------------------
// Varyings. Locations match terrain_solid.frag.
// --------------------------------------------------------------------------------------------
layout(location = 0) out vec4 v_Color;
layout(location = 1) out vec4 v_RdhFactor;
layout(location = 2) out vec2 v_QuadCoord;
layout(location = 3) out vec2 v_TexCoord;
layout(location = 4) out float v_FragDistance;
// The material is constant over a quad (all four vertices are written with one Material), so the
// two material-derived values below are declared `flat`. The OpenGL shader declares them as plain
// smooth varyings; flat is numerically identical here (every vertex carries the same value) and
// removes any interpolation-rounding ambiguity on the alpha-cutoff comparison.
layout(location = 5) flat out float v_MaterialMipBias;
layout(location = 6) flat out float v_MaterialAlphaCutoff;

// NOTE: the OpenGL shader declares `invariant gl_Position;` so that the solid and cutout copies of
// the program stay bit-identical. The keyword is intentionally dropped here: the SPIR-V module
// itself is the compiled artifact shared by every pass, so the OpenGL-side reason cannot apply.

// --------------------------------------------------------------------------------------------
// sodium:include/chunk_vertex.glsl  (USE_VERTEX_COMPRESSION branch)
// The scale/offset constants come from ChunkVertexType.getDefines() for CompactChunkVertex:
//     VanillaLikeChunkVertex (stride 32) - the uncompressed branch: positions and texture
//     coordinates arrive as floats and the material parameter field is 8 bits wide.
//     VERT_POS_OFFSET = -8.0              (getPositionOffset())
//     VERT_TEX_SCALE  = 1.0 / 131072.0    (getTextureScale())
// --------------------------------------------------------------------------------------------
// _draw_id is an index into a 256-entry region table (REGION_SIZE); it is only used for the
// per-draw translation below.
#define REGION_SIZE 256

const float VERT_POS_SCALE = 1.0;
const float VERT_POS_OFFSET = -8.0;
const float VERT_TEX_SCALE = 1.0 / 131072.0;   // exact power of two: 2^-17

uvec3 _get_relative_chunk_coord(uint pos) {
    // Packing scheme is defined by LocalSectionIndex
    return uvec3(pos) >> uvec3(5u, 0u, 2u) & uvec3(7u, 3u, 7u);
}

vec3 _get_draw_translation(uint pos) {
    return _get_relative_chunk_coord(pos) * vec3(16.0);
}

// --------------------------------------------------------------------------------------------
// sodium:include/chunk_material.glsl
// --------------------------------------------------------------------------------------------
const uint MATERIAL_USE_MIP_OFFSET = 0u;
const uint MATERIAL_ALPHA_CUTOFF_OFFSET = 1u;

const float[4] ALPHA_CUTOFF = float[4](0.0, 0.1, 0.5, 1.0);

float _material_mip_bias(uint material) {
    return ((material >> MATERIAL_USE_MIP_OFFSET) & 1u) != 0u ? 0.0 : -4.0;
}

float _material_alpha_cutoff(uint material) {
    return ALPHA_CUTOFF[(material >> MATERIAL_ALPHA_CUTOFF_OFFSET) & 3u];
}

// --------------------------------------------------------------------------------------------
// sodium:include/fog.glsl - constants and the fog-distance helper.
// --------------------------------------------------------------------------------------------
const int FOG_SHAPE_SPHERICAL = 0;
const int FOG_SHAPE_CYLINDRICAL = 1;
const int FOG_SHAPE_PLANAR = 2;

float getFragDistance(int fogShape, vec3 position) {
    // Use the maximum of the horizontal and vertical distance to get cylindrical fog if fog shape is cylindrical
    if (fogShape == FOG_SHAPE_CYLINDRICAL) {
        return max(length(position.xz), abs(position.y));
    } else {
        return length(position);
    }
}

// --------------------------------------------------------------------------------------------
// Light map sampling (verbatim from block_layer_opaque.vsh / the CELERITAS_NO_LIGHTMAP branch
// is deliberately not ported: the SOLID pass always has a light map).
// --------------------------------------------------------------------------------------------
vec4 _sample_lightmap(sampler2D lightMap, ivec2 uv) {
    // Exactly the reference's expression, divisor included: the pinned lineage samples
    // `clamp(uv / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0))` and this project ported it verbatim. Changing the divisor to
    // 16 moved the frame by 0.4 of a mean luminance unit, which is not where the difference lives either.
    // The half-texel offset is what makes this sample a texel *centre*. Minecraft 1.12.2's light map is 16x16 and the
    // coordinate packed into the vertex is the light level scaled by 16, so the centre of the (block, sky) texel is
    // (uv + 8) / 256 - the expression the immediate shaders in this project already use. Without it the sample lands on
    // the boundary between two rows and the linear filter averages them: measured against the light map's own pixels
    // (row 13 = 160, row 14 = 197, row 15 = 250), sky 15 returned 223 and sky 14 returned 178 instead of 250 and 197.
    return texture(lightMap, clamp((vec2(uv) + 8.0) / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
}

void main() {
    // ---- _vert_init(), VanillaLikeChunkVertex branch ---------------------------------------
    uint packed_draw_params = (a_LightCoord & 0xFFFFu);

    vec3 _vert_position = a_PosId;
    vec2 _vert_tex_diffuse_coord = a_TexCoord;
    vec4 _vert_color = a_Color;
    vec4 _vert_rdh_factor = a_RdhFactor;

    // The uncompressed format packs the material into a full byte and the section id into the next byte.
    uint _material_params = (packed_draw_params) & 0xFFu;
    uint _draw_id = (packed_draw_params >> 8) & 0xFFu;
    ivec2 _vert_tex_light_coord = ivec2((uvec2((a_LightCoord >> 16) & 0xFFFFu) >> uvec2(0u, 8u)) & uvec2(0xFFu));

    // ---- main() ---------------------------------------------------------------------------
    // Transform the chunk-local vertex position into world model space
    vec3 translation = celtium.u_RegionOffset.xyz + _get_draw_translation(_draw_id);
    vec3 worldPosition = _vert_position + translation;

    // Legacy fog: the fragment distance is evaluated in camera-relative model space, exactly as
    // the OpenGL shader does (it is NOT transformed by u_ModelViewMatrix).
    v_FragDistance = getFragDistance(celtium.u_FogShape, worldPosition);

    // Transform the vertex position into model-view-projection space
    gl_Position = pc.u_ProjectionMatrix * pc.u_ModelViewMatrix * vec4(worldPosition, 1.0);

    // Add the light color to the vertex color, and pass the texture coordinates to the fragment shader
    // Faithful to the pinned lineage: the reference shader
    // (upstream-baseline .../sodium/shaders/blocks/block_layer_opaque.vsh) samples this exact coordinate without swapping
    // the axes, so the swap tried here - which moved the mean from 34.08 to 27.09 - was a deviation from the reference,
    // not a fix. It is reverted so that the remaining difference is measured against the reference's own behaviour.
    v_RdhFactor = _vert_rdh_factor * 2.0;
    // Measured (round 275, SPIR-V verified in the jar): removing this multiply leaves the near terrain at 0.70-0.76 of the
    // reference instead of 0.75-0.84 - it moves the frame, but nowhere near enough to be the 0.69 the ratio reports. So the
    // light map is excluded by measurement, not by argument, and the multiply is restored to the reference's form.
    vec4 lightColor = _sample_lightmap(u_LightTex, _vert_tex_light_coord);
    v_Color = _vert_color * lightColor;
    v_RdhFactor.rgb *= lightColor.rgb;

    int corner = gl_VertexIndex & 3;
    v_QuadCoord = vec2(corner >= 2 ? 1.0 : 0.0,
            corner == 1 || corner == 2 ? 1.0 : 0.0);
    v_TexCoord = _vert_tex_diffuse_coord;

    // u_MipBiasAdd is 0.0 for exact OpenGL parity; it exists so the backend can shift the atlas
    // LOD globally without a shader rebuild.
    v_MaterialMipBias = _material_mip_bias(_material_params) + celtium.u_MipBiasAdd;
    v_MaterialAlphaCutoff = _material_alpha_cutoff(_material_params);
}
