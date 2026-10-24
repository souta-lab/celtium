#version 450

// Celtium - POSITION_COLOR geometry drawn while GL texture-coordinate generation is on (NativeTexGen): the end portal
// and end gateway. The S, T and Q rows of (texture matrix * generated coordinate), re-expressed over this group's vertex
// positions, arrive at 64/80/96; the coordinate is projective, so (s, t, q) is interpolated and divided per fragment
// exactly as the fixed pipeline does. Same 208-byte block as the textured family: projection at 0, fog at 128.

layout(push_constant) uniform PushConstants {
    mat4 u_ProjectionMatrix;
    vec4 u_GenRowS;
    vec4 u_GenRowT;
    vec4 u_GenRowQ;
    layout(offset = 112) float u_AlphaRef;
    layout(offset = 116) float u_ColorR;
    layout(offset = 120) float u_ColorG;
    layout(offset = 124) float u_ColorB;
    layout(offset = 128) vec4 u_FogColor;
    layout(offset = 144) vec4 u_FogParams;
    layout(offset = 160) vec4 u_FogRow0;
    layout(offset = 176) vec4 u_FogRow1;
    layout(offset = 192) vec4 u_FogRow2;
} celtium;

layout(location = 0) in vec3 a_Position;
layout(location = 2) in vec4 a_Color;

layout(location = 0) out vec3 v_Gen;
layout(location = 1) out vec4 v_Color;
layout(location = 2) out float v_FogDistance;

float celtiumFogDistance(vec3 position) {
    vec3 eye = vec3(dot(position, celtium.u_FogRow0.xyz) + celtium.u_FogRow0.w,
                    dot(position, celtium.u_FogRow1.xyz) + celtium.u_FogRow1.w,
                    dot(position, celtium.u_FogRow2.xyz) + celtium.u_FogRow2.w);
    int selector = int(celtium.u_FogParams.w + 0.5);
    return (selector >> 3) == 2 ? abs(eye.z) : length(eye);
}

void main() {
    vec4 position = vec4(a_Position, 1.0);
    v_Gen = vec3(dot(celtium.u_GenRowS, position), dot(celtium.u_GenRowT, position), dot(celtium.u_GenRowQ, position));
    v_Color = a_Color;
    v_FogDistance = (int(celtium.u_FogParams.w + 0.5) & 7) == 0 ? 0.0 : celtiumFogDistance(a_Position);
    gl_Position = celtium.u_ProjectionMatrix * position;
}
