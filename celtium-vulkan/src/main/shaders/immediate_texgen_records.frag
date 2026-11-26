#version 450

// Celtium - the record-reading form of immediate_texgen.frag (Stage 5 stage 6): the
// projective coordinate is divided here, then the fixed-function modulate (texture * vertex
// colour) and fog, as in immediate_tex_color.frag. The fog block arrives per record through
// the vertex stage's flat outputs. Vanilla's texgen users draw with the alpha test off, so
// none is applied.

layout(set = 0, binding = 0) uniform sampler2D u_Texture;

layout(location = 0) in vec3 v_Gen;
layout(location = 1) in vec4 v_Color;
layout(location = 2) in float v_FogDistance;
layout(location = 3) flat in vec4 v_FogColour;
layout(location = 4) flat in vec4 v_FogParams;
layout(location = 0) out vec4 fragColor;

float celtiumFogAmount(float distance) {
    int mode = int(v_FogParams.w + 0.5) & 7;
    if (mode == 0) {
        return 0.0;
    }
    float weight;
    if (mode == 1) {        // GL_EXP2
        float scaled = distance * v_FogParams.x;
        weight = clamp(exp(-(scaled * scaled)), 0.0, 1.0);
    } else if (mode == 2) { // GL_EXP
        weight = clamp(exp(-(distance * v_FogParams.x)), 0.0, 1.0);
    } else {                // GL_LINEAR
        float span = max(v_FogParams.z - v_FogParams.y, 0.000001);
        weight = clamp((v_FogParams.z - distance) / span, 0.0, 1.0);
    }
    return (1.0 - weight) * v_FogColour.a;
}

void main() {
    vec2 uv = v_Gen.xy / v_Gen.z;
    vec4 colour = texture(u_Texture, uv) * v_Color;
    colour.rgb = mix(colour.rgb, v_FogColour.rgb, celtiumFogAmount(v_FogDistance));
    fragColor = colour;
}
