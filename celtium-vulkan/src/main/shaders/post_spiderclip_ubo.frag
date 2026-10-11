#version 450
// spiderclip.fsh: rotated/scaled sample clipped by Scissor with a Vignette blend to the blurred copy.
// std140, not std430: for this member list - no arrays, no nested structures - both layouts place
// every member at the same offset (measured from both modules' SPIR-V by the executor's own check),
// and std140 is the uniform-block layout that needs no uniformBufferStandardLayout feature.
// Uniform-buffer variant of post_spiderclip.frag: 144 bytes of block, which does not fit a device whose
// maxPushConstantsSize is the 128-byte minimum. Same members, same order, same offsets as the push
// variant - the executor checks that from both modules' SPIR-V before it runs this one.
layout(location = 0) in vec2 texCoord;
layout(location = 1) in vec2 scaledCoord;
layout(location = 0) out vec4 o_Color;

layout(set = 0, binding = 0) uniform sampler2D DiffuseSampler;
layout(set = 0, binding = 1) uniform sampler2D BlurSampler;

layout(std140, set = 0, binding = 2) uniform CeltiumBlock {
    mat4 ProjMat;    // @0
    vec2 InSize;     // @64
    vec2 OutSize;    // @72
    float Time;      // @80
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
