#version 450
// Vanilla post-chain vertex shader (rotscale.vsh): uv + rotated/scaled coordinate, used by spider.
// std140, not std430: for this member list - no arrays, no nested structures - both layouts place
// every member at the same offset (measured from both modules' SPIR-V by the executor's own check),
// and std140 is the uniform-block layout that needs no uniformBufferStandardLayout feature.
// Uniform-buffer variant of post_rotscale.vert: 144 bytes of block, which does not fit a device whose
// maxPushConstantsSize is the 128-byte minimum. Same members, same order, same offsets as the push
// variant - the executor checks that from both modules' SPIR-V before it runs this one.
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

layout(location = 0) out vec2 texCoord;
layout(location = 1) out vec2 scaledCoord;

void main() {
    vec2 pos = vec2((gl_VertexIndex & 1) == 0 ? 0.0 : celtium.OutSize.x,
            gl_VertexIndex < 2 ? celtium.OutSize.y : 0.0);
    vec4 outPos = celtium.ProjMat * vec4(pos, 0.0, 1.0);
    gl_Position = vec4(outPos.xy, 0.2, 1.0);

    texCoord = pos / celtium.OutSize;
    texCoord.y = 1.0 - texCoord.y;

    float deg2Rad = 0.0174532925;
    float radians = celtium.InRotation * deg2Rad;
    float cosine = cos(radians);
    float sine = sin(radians);
    float rotU = texCoord.x * cosine - texCoord.y * sine;
    float rotV = texCoord.y * cosine + texCoord.x * sine;
    scaledCoord = vec2(rotU, rotV) * celtium.InScale + celtium.InOffset;
}
