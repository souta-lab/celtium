#version 450

// Celtium - the texgen family's fragment stage: the projective coordinate is divided here, then the fixed-function
// modulate (texture * vertex colour) and fog, as in immediate_tex_color.frag. Vanilla's texgen users draw with the
// alpha test off, so none is applied.

layout(set = 0, binding = 0) uniform sampler2D u_Texture;

layout(location = 0) in vec3 v_Gen;
layout(location = 1) in vec4 v_Color;
layout(location = 2) in float v_FogDistance;
layout(location = 0) out vec4 fragColor;

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

float celtiumFogAmount(float distance) {
    int mode = int(celtium.u_FogParams.w + 0.5) & 7;
    if (mode == 0) {
        return 0.0;
    }
    float weight;
    if (mode == 1) {
        float scaled = distance * celtium.u_FogParams.x;
        weight = clamp(exp(-(scaled * scaled)), 0.0, 1.0);
    } else if (mode == 2) {
        weight = clamp(exp(-(distance * celtium.u_FogParams.x)), 0.0, 1.0);
    } else {
        float span = max(celtium.u_FogParams.z - celtium.u_FogParams.y, 0.000001);
        weight = clamp((celtium.u_FogParams.z - distance) / span, 0.0, 1.0);
    }
    return (1.0 - weight) * celtium.u_FogColor.a;
}

void main() {
    vec2 uv = v_Gen.xy / v_Gen.z;
    vec4 colour = texture(u_Texture, uv) * v_Color;
    colour.rgb = mix(colour.rgb, celtium.u_FogColor.rgb, celtiumFogAmount(v_FogDistance));
    fragColor = colour;
}
