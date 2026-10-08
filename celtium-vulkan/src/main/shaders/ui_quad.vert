#version 450

// Celtium - full-Vulkan roadmap stage S4/S6 foundation: the first non-terrain geometry drawn by Vulkan.
// Vertex layout is VulkanDynamicMesh.Layout.positionTexLightColor() (24 bytes):
//   location 0 : vec3  position   offset  0
//   location 1 : vec2  uv         offset 12
//   location 2 : uvec2 light      offset 16   (kept for layout compatibility with Minecraft's format)
//   location 3 : vec4  color      offset 20   (unsigned bytes, normalised)
// Push constants follow the terrain convention: projection at 0, model view at 64.

layout(push_constant) uniform PushConstants {
    mat4 u_ProjectionMatrix;
    mat4 u_ModelViewMatrix;
} celtium;

layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec2 a_Uv;
layout(location = 2) in uvec2 a_Light;
layout(location = 3) in vec4 a_Color;

layout(location = 0) out vec2 v_Uv;
layout(location = 1) out vec4 v_Color;

void main() {
    v_Uv = a_Uv;
    v_Color = a_Color;
    gl_Position = celtium.u_ProjectionMatrix * celtium.u_ModelViewMatrix * vec4(a_Position, 1.0);
}
