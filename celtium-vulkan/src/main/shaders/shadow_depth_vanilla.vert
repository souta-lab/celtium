#version 450

// VanillaLikeChunkVertex: 32-byte stride. Region offset is relative to the camera,
// exactly as in terrain_solid_vanilla.vert; position and section id share its decode.
layout(push_constant) uniform ShadowPush {
    mat4 lightViewProjection;
    vec4 regionOffset;
} shadow;

layout(location = 0) in vec3 a_Position;
layout(location = 1) in uint a_LightCoord;

void main() {
    uint section = (a_LightCoord >> 8u) & 0xFFu;
    vec3 sectionOffset = vec3((section >> 5u) & 7u, section & 3u, (section >> 2u) & 7u) * 16.0;
    gl_Position = shadow.lightViewProjection * vec4(a_Position + shadow.regionOffset.xyz + sectionOffset, 1.0);
}
