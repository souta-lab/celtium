#version 450
layout(location = 0) in vec3 a_Position;

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

layout(location = 0) out vec4 v_Color;

void main() {
    // The combined projection-times-model-view arrives in u_ProjectionMatrix, so this shader multiplies once. It used to
    // rebuild the model view from the three columns with a hardcoded fourth; when the writer started sending the product and
    // zeroing the columns, that rebuild became a zero matrix and every position-only vertex collapsed to the translation point.
    gl_Position = celtium.u_ProjectionMatrix * vec4(a_Position, 1.0);
    v_Color = vec4(celtium.u_ColorR, celtium.u_ColorG, celtium.u_ColorB, 1.0);
}
