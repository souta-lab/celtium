#version 450

// Celtium - Minecraft's BLOCK-family immediate vertices (DefaultVertexFormats.BLOCK, and the same 28-byte shape used
// by the particle format): 3 floats position, 4 unsigned bytes colour, 2 floats texture coordinates, 2 unsigned shorts
// lightmap coordinates. Push constants keep the terrain convention: projection at 0, model view at 64, alpha-test
// reference at 128.

layout(push_constant) uniform PushConstants {
    mat4 u_ProjectionMatrix;
    mat4 u_ModelViewMatrix;
    layout(offset = 128) float u_AlphaRef;
} celtium;

layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec4 a_Color;
layout(location = 2) in vec2 a_TexCoord;
layout(location = 3) in vec2 a_LightCoord;

layout(location = 0) out vec4 v_Color;
layout(location = 1) out vec2 v_TexCoord;
layout(location = 2) out vec2 v_LightCoord;

void main() {
    v_Color = a_Color;
    v_TexCoord = a_TexCoord;
    v_LightCoord = a_LightCoord;
    gl_Position = celtium.u_ProjectionMatrix * celtium.u_ModelViewMatrix * vec4(a_Position, 1.0);
}
