#version 450
// Vanilla post-chain vertex shader (sobel.vsh): fullscreen quad, per-pixel uv + one-texel offset.
// Positions come from the vertex index and OutSize, in vanilla's quad order (0,h),(w,h),(0,0),(w,0).
// Normalized push schema: ProjMat, InSize, OutSize, Time, then the program's extras.
layout(push_constant) uniform Push {
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
