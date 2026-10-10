#version 450
// blit.fsh: the chain's final copy pass - DiffuseSampler modulated by ColorModulate.
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 o_Color;

layout(set = 0, binding = 0) uniform sampler2D DiffuseSampler;

layout(push_constant) uniform Push {
    mat4 ProjMat;   // @0
    vec2 InSize;    // @64
    vec2 OutSize;   // @72
    float Time;     // @80
    vec4 ColorModulate; // @96
} celtium;

void main() {
    o_Color = texture(DiffuseSampler, texCoord) * celtium.ColorModulate;
}
