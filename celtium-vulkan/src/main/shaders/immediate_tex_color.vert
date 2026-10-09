#version 450

// Celtium - immediate-mode geometry with a texture: POSITION_TEX_COLOR vertices, which is what
// DefaultVertexFormats.POSITION_TEX_COLOR declares (3 floats position, 2 floats texture coordinates, 4 unsigned bytes
// colour). Push constants keep the terrain and UI convention: projection at 0, model view at 64.

layout(push_constant) uniform PushConstants {
    mat4 u_ProjectionMatrix;
    mat4 u_ModelViewMatrix;
} celtium;

layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec2 a_TexCoord;
layout(location = 2) in vec4 a_Color;

layout(location = 0) out vec2 v_TexCoord;
layout(location = 1) out vec4 v_Color;

void main() {
    v_TexCoord = a_TexCoord;
    v_Color = a_Color;
    gl_Position = celtium.u_ProjectionMatrix * celtium.u_ModelViewMatrix * vec4(a_Position, 1.0);
}
