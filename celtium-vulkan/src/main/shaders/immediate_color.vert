#version 450

// Celtium - immediate-mode geometry (Forge API swap): untextured POSITION_COLOR vertices, the format
// DefaultVertexFormats.POSITION_COLOR declares (3 floats position, 4 unsigned bytes colour).
// Push constants follow the same convention as the terrain and UI pipelines: projection at 0, model view at 64.

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
layout(location = 1) in vec4 a_Color;

layout(location = 0) out vec4 v_Color;

void main() {
    v_Color = a_Color;
    gl_Position = celtium.u_ProjectionMatrix * (celtium.u_MvColumn0 * (a_Position).x + celtium.u_MvColumn1 * (a_Position).y + celtium.u_MvColumn2 * (a_Position).z + vec4(0.0, 0.0, 0.0, 1.0));
}
