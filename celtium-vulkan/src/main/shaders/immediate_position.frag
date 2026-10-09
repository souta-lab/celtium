#version 450
layout(location = 0) in vec4 v_Color;

layout(push_constant) uniform PushConstants {
    mat4 u_ProjectionMatrix;
    vec4 u_MvColumn0;
    vec4 u_MvColumn1;
    vec4 u_MvColumn2;
    layout(offset = 112) float u_AlphaRef;
    layout(offset = 116) float u_ColorR;
    layout(offset = 120) float u_ColorG;
    layout(offset = 124) float u_ColorB;
} celtium;

layout(location = 0) out vec4 o_Color;

void main() {
    if (v_Color.a < celtium.u_AlphaRef) {
        discard;
    }
    o_Color = v_Color;
}
