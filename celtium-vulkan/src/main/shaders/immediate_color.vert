#version 450

// Celtium - immediate-mode geometry (Forge API swap): untextured POSITION_COLOR vertices, the format
// DefaultVertexFormats.POSITION_COLOR declares (3 floats position, 4 unsigned bytes colour).
// Push constants follow the same convention as the terrain and UI pipelines: projection at 0, model view at 64.

layout(push_constant) uniform PushConstants {
    mat4 u_ProjectionMatrix;
    mat4 u_ModelViewMatrix;
} celtium;

layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec4 a_Color;

layout(location = 0) out vec4 v_Color;

void main() {
    v_Color = a_Color;
    gl_Position = celtium.u_ProjectionMatrix * celtium.u_ModelViewMatrix * vec4(a_Position, 1.0);
}
