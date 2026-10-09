#version 450

// Celtium - immediate-mode geometry with a texture: POSITION_TEX_COLOR vertices, which is what
// DefaultVertexFormats.POSITION_TEX_COLOR declares (3 floats position, 2 floats texture coordinates, 4 unsigned bytes
// colour). Push constants keep the terrain and UI convention: projection at 0, model view at 64.

layout(push_constant) uniform PushConstants {
    mat4 u_ProjectionMatrix;
    // The model view is affine, so only its three rotation/translation columns are passed and the shader rebuilds the
    // fourth. With the alpha reference at 112 the whole block is 116 bytes - inside the 128 Vulkan guarantees (S19) -
    // and it is declared identically in both stages, which is what the driver requires.
    vec4 u_MvColumn0;
    vec4 u_MvColumn1;
    vec4 u_MvColumn2;
    layout(offset = 112) float u_AlphaRef;
    layout(offset = 116) float u_ColorR;
    layout(offset = 120) float u_ColorG;
    layout(offset = 124) float u_ColorB;
} celtium;

layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec2 a_TexCoord;
layout(location = 2) in vec4 a_Color;

layout(location = 0) out vec2 v_TexCoord;
layout(location = 1) out vec4 v_Color;

void main() {
    v_TexCoord = a_TexCoord;
    v_Color = a_Color;
    gl_Position = celtium.u_ProjectionMatrix * (celtium.u_MvColumn0 * (a_Position).x + celtium.u_MvColumn1 * (a_Position).y + celtium.u_MvColumn2 * (a_Position).z + vec4(0.0, 0.0, 0.0, 1.0));
}
