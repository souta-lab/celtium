#version 450

// Celtium - the shadow map's depth-only pass.
//
// The COMPACT chunk vertex starts with a position (three floats) and carries colour, texture coordinates, a light
// coordinate and the shading factors after it; only the position is needed to fill a depth map, so this stage declares
// location 0 and nothing else. The pipeline's vertex binding still describes the full 24-byte stride, which is what the
// buffer actually holds.
//
// The matrix is the sun's orthographic view-projection from ShadowLightMatrix (column-major), already in Vulkan's clip
// convention: x and y in [-1, 1], z in [0, 1].

layout(push_constant) uniform PushConstants {
    mat4 u_LightViewProjection;
} celtium;

layout(location = 0) in vec3 a_Position;

void main() {
    gl_Position = celtium.u_LightViewProjection * vec4(a_Position, 1.0);
}
