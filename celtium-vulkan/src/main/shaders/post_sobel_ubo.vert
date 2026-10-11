#version 450
// Vanilla post-chain vertex shader (sobel.vsh): fullscreen quad, per-pixel uv + one-texel offset.
// Positions come from the vertex index and OutSize, in vanilla's quad order (0,h),(w,h),(0,0),(w,0).
// std140, not std430: for this member list - no arrays, no nested structures - both layouts place
// every member at the same offset (measured from both modules' SPIR-V by the executor's own check),
// and std140 is the uniform-block layout that needs no uniformBufferStandardLayout feature.
// Uniform-buffer variant of post_sobel.vert: the same block, the same members in the same order, at the
// same set-0 binding 2 the executor writes. The block declaration is the only difference from the push
// variant, and the executor compares the two modules' member offsets before it uses this one - a shader
// variant that silently moved a member would read the wrong bytes rather than fail.
layout(std140, set = 0, binding = 2) uniform CeltiumBlock {
    mat4 ProjMat;   // @0
    vec2 InSize;    // @64
    vec2 OutSize;   // @72
    float Time;     // @80
} celtium;

layout(location = 0) out vec2 texCoord;
layout(location = 1) out vec2 oneTexel;

void main() {
    vec2 pos = vec2((gl_VertexIndex & 1) == 0 ? 0.0 : celtium.OutSize.x,
            gl_VertexIndex < 2 ? celtium.OutSize.y : 0.0);
    vec4 outPos = celtium.ProjMat * vec4(pos, 0.0, 1.0);
    gl_Position = vec4(outPos.xy, 0.2, 1.0);
    oneTexel = 1.0 / celtium.InSize;
    texCoord = pos / celtium.OutSize;
    texCoord.y = 1.0 - texCoord.y;
}
