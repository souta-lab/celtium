#version 450

// Celtium - the particle layout (DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP): 3 floats position, 2 floats
// texture coordinates, 4 unsigned bytes colour, 2 unsigned shorts lightmap. The element order differs from BLOCK,
// which is exactly why it needs its own vertex layout rather than sharing one pipeline.

layout(push_constant) uniform PushConstants {
    mat4 u_ProjectionMatrix;
    // The model view is affine, so only its three rotation/translation columns are passed and the shader rebuilds the
    // fourth. With the alpha reference at 112 the whole block is 116 bytes - inside the 128 Vulkan guarantees (S19) -
    // and it is declared identically in both stages, which is what the driver requires.
    vec4 u_MvColumn0;
    vec4 u_MvColumn1;
    vec4 u_MvColumn2;
    layout(offset = 112) float u_AlphaRef;
} celtium;

layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec2 a_TexCoord;
layout(location = 2) in vec4 a_Color;
layout(location = 3) in uvec2 a_LightCoord;

layout(location = 0) out vec2 v_TexCoord;
layout(location = 1) out vec4 v_Color;
layout(location = 2) // Interpolated like the fixed pipeline's texture coordinates: flat integers lost the per-vertex blend that
    // Minecraft relies on at light boundaries. The value is the raw coordinate - the >> 8 that used to be here shifted a
    // 0..240 range to zero, so every sample landed on the same dark texel - scaled and biased exactly as vanilla's
    // texture matrix does it, scale 1/256 and translate 8.
    out vec2 v_Lightmap;

void main() {
    v_TexCoord = a_TexCoord;
    v_Color = a_Color;
    v_Lightmap = (vec2(a_LightCoord) + 8.0) / 256.0;
    gl_Position = celtium.u_ProjectionMatrix * (celtium.u_MvColumn0 * (a_Position).x + celtium.u_MvColumn1 * (a_Position).y + celtium.u_MvColumn2 * (a_Position).z + vec4(0.0, 0.0, 0.0, 1.0));
}
