#version 450

// Celtium - the shadow map's depth-only pass.
//
// CompactChunkVertex: 24-byte stride, position is four packed unsigned shorts.
// Decode position and section id as in terrain_solid.vert; the region offset is camera-relative.
layout(push_constant) uniform ShadowPush {
    mat4 lightViewProjection;
    vec4 regionOffset;
} shadow;

layout(location = 0) in uvec4 a_PosId;
layout(location = 1) in uint a_LightCoord;

void main() {
    uvec3 packed = (a_PosId.xyz << 5u) | ((uvec3(a_PosId.w) >> uvec3(0u, 5u, 10u)) & uvec3(31u));
    vec3 position = vec3(packed) * (32.0 / 2097152.0) - 8.0;
    uint section = (a_LightCoord >> 8u) & 0xFFu;
    vec3 sectionOffset = vec3((section >> 5u) & 7u, section & 3u, (section >> 2u) & 7u) * 16.0;
    gl_Position = shadow.lightViewProjection * vec4(position + shadow.regionOffset.xyz + sectionOffset, 1.0);
}
