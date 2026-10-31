#version 450
// Record-reading form of immediate_position_tex.frag.
layout(set = 0, binding = 0) uniform sampler2D u_Texture;
layout(location = 0) in vec2 v_TexCoord;
layout(location = 1) in vec4 v_Color;
layout(location = 2) in float v_FogDistance;
layout(location = 3) flat in vec4 v_Matte;
layout(location = 4) flat in vec4 v_FogColour;
layout(location = 5) flat in vec4 v_FogParams;
layout(location = 0) out vec4 fragColor;
float celtiumFogAmount(float distance) {
    int mode = int(v_FogParams.w + 0.5) & 7;
    if (mode == 0) { return 0.0; }
    float weight;
    if (mode == 1) { float s = distance * v_FogParams.x; weight = clamp(exp(-(s * s)), 0.0, 1.0); }
    else if (mode == 2) { weight = clamp(exp(-(distance * v_FogParams.x)), 0.0, 1.0); }
    else { float span = max(v_FogParams.z - v_FogParams.y, 0.000001);
           weight = clamp((v_FogParams.z - distance) / span, 0.0, 1.0); }
    return (1.0 - weight) * v_FogColour.a;
}
void main() {
    vec4 colour = texture(u_Texture, v_TexCoord) * v_Color;
    if (v_Matte.x < 0.0) {
        colour.rgb = mix(colour.rgb, v_Matte.yzw, -v_Matte.x);
    } else if (v_Matte.x > 0.0 && colour.a < v_Matte.x) {
        discard;
    }
    colour.rgb = mix(colour.rgb, v_FogColour.rgb, celtiumFogAmount(v_FogDistance));
    fragColor = colour;
}
