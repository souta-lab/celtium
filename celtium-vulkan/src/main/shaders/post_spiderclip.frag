#version 450
// spiderclip.fsh: rotated/scaled sample clipped by Scissor with a Vignette blend to the blurred copy.
layout(location = 0) in vec2 texCoord;
layout(location = 1) in vec2 scaledCoord;
layout(location = 0) out vec4 o_Color;

layout(set = 0, binding = 0) uniform sampler2D DiffuseSampler;
layout(set = 0, binding = 1) uniform sampler2D BlurSampler;

layout(push_constant) uniform Push {
    mat4 ProjMat;   // @0
    vec2 InSize;    // @64
    vec2 OutSize;   // @72
    float Time;     // @80
    vec2 InScale;    // @88
    vec2 InOffset;   // @96
    float InRotation;// @104
    vec4 Scissor;    // @112
    vec4 Vignette;   // @128
} celtium;

void main() {
    vec4 scaledTexel = texture(DiffuseSampler, scaledCoord);
    vec4 blurTexel = texture(BlurSampler, texCoord);
    vec4 outTexel = scaledTexel;

    if (scaledCoord.x < celtium.Scissor.x) outTexel = blurTexel;
    if (scaledCoord.y < celtium.Scissor.y) outTexel = blurTexel;
    if (scaledCoord.x > celtium.Scissor.z) outTexel = blurTexel;
    if (scaledCoord.y > celtium.Scissor.w) outTexel = blurTexel;

    vec2 c = clamp(scaledCoord, 0.0, 1.0);

    if (scaledCoord.x < celtium.Vignette.x) outTexel = mix(blurTexel, outTexel, (celtium.Scissor.x - scaledCoord.x) / (celtium.Scissor.x - celtium.Vignette.x));
    if (scaledCoord.y < celtium.Vignette.y) outTexel = mix(blurTexel, outTexel, (celtium.Scissor.y - scaledCoord.y) / (celtium.Scissor.y - celtium.Vignette.y));
    if (scaledCoord.x > celtium.Vignette.z) outTexel = mix(blurTexel, outTexel, (celtium.Scissor.z - scaledCoord.x) / (celtium.Scissor.z - celtium.Vignette.z));
    if (scaledCoord.y > celtium.Vignette.w) outTexel = mix(blurTexel, outTexel, (celtium.Scissor.w - scaledCoord.y) / (celtium.Scissor.w - celtium.Vignette.w));

    o_Color = vec4(outTexel.rgb, 1.0);
}
