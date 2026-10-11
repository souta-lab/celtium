#version 450
// color_convolve.fsh: colour matrix + offset/scale + saturation (creeper/desaturate chains).
// std140, not std430: for this member list - no arrays, no nested structures - both layouts place
// every member at the same offset (measured from both modules' SPIR-V by the executor's own check),
// and std140 is the uniform-block layout that needs no uniformBufferStandardLayout feature.
// Uniform-buffer variant of post_color_convolve.frag: 192 bytes of block, which does not fit a device
// whose maxPushConstantsSize is the 128-byte minimum, so the executor writes it to the set-0 binding 2
// buffer instead. Same members, same order, same offsets as the push variant - the executor checks that
// from both modules' SPIR-V before it runs this one.
layout(location = 0) in vec2 texCoord;
layout(location = 1) in vec2 oneTexel;
layout(location = 0) out vec4 o_Color;

layout(set = 0, binding = 0) uniform sampler2D DiffuseSampler;

layout(std140, set = 0, binding = 2) uniform CeltiumBlock {
    mat4 ProjMat;   // @0
    vec2 InSize;    // @64
    vec2 OutSize;   // @72
    float Time;     // @80
    vec3 Gray;       // @96
    vec3 RedMatrix;  // @112
    vec3 GreenMatrix;// @128
    vec3 BlueMatrix; // @144
    vec3 Offset;     // @160
    vec3 ColorScale; // @176
    float Saturation;// @188
} celtium;

void main() {
    vec4 texel = texture(DiffuseSampler, texCoord);

    float red = dot(texel.rgb, celtium.RedMatrix);
    float green = dot(texel.rgb, celtium.GreenMatrix);
    float blue = dot(texel.rgb, celtium.BlueMatrix);
    vec3 outColor = vec3(red, green, blue);

    outColor = (outColor * celtium.ColorScale) + celtium.Offset;

    float luma = dot(outColor, celtium.Gray);
    vec3 chroma = outColor - luma;
    outColor = (chroma * celtium.Saturation) + luma;

    o_Color = vec4(outColor, 1.0);
}
