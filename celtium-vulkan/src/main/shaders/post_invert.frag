#version 450
// invert.fsh: mix toward the inverted colour by InverseAmount (enderman spectator view).
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 o_Color;

layout(set = 0, binding = 0) uniform sampler2D DiffuseSampler;

layout(push_constant) uniform Push {
    mat4 ProjMat;   // @0
    vec2 InSize;    // @64
    vec2 OutSize;   // @72
    float Time;     // @80
    float InverseAmount;// @88
} celtium;

void main() {
    vec4 diffuse = texture(DiffuseSampler, texCoord);
    vec4 inverted = 1.0 - diffuse;
    vec4 outColor = mix(diffuse, inverted, celtium.InverseAmount);
    o_Color = vec4(outColor.rgb, 1.0);
}
