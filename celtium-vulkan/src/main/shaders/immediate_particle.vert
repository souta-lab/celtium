#version 450

// Celtium - the particle layout (DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP): 3 floats position, 2 floats
// texture coordinates, 4 unsigned bytes colour, 2 unsigned shorts lightmap. The element order differs from BLOCK,
// which is exactly why it needs its own vertex layout rather than sharing one pipeline.

layout(push_constant) uniform PushConstants {
    mat4 u_ProjectionMatrix;
    mat4 u_ModelViewMatrix;
    layout(offset = 128) float u_AlphaRef;
} celtium;

layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec2 a_TexCoord;
layout(location = 2) in vec4 a_Color;
layout(location = 3) in uvec2 a_LightCoord;

layout(location = 0) out vec2 v_TexCoord;
layout(location = 1) out vec4 v_Color;
layout(location = 2) flat out uvec2 v_LightCoord;

void main() {
    v_TexCoord = a_TexCoord;
    v_Color = a_Color;
    v_LightCoord = a_LightCoord;
    gl_Position = celtium.u_ProjectionMatrix * celtium.u_ModelViewMatrix * vec4(a_Position, 1.0);
}
