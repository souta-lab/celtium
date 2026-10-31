#version 450

// Celtium - the record-reading textured fragment stage.
//
// The run-level push block cannot carry what changes between the groups a run merges: the alpha reference, the current
// colour, the alpha-test selector and the fog block. They arrive per record through the vertex stage's flat outputs, so
// a run may cross a material-free group boundary (alpha and colour changes) and still be one indirect command range.

layout(set = 0, binding = 0) uniform sampler2D u_Texture;

layout(location = 0) in vec2 v_TexCoord;
layout(location = 1) in vec4 v_Color;
layout(location = 2) in float v_FogDistance;
layout(location = 3) flat in vec4 v_AlphaColour;   // x alpha reference (negative = combine), yzw current colour
layout(location = 4) flat in vec4 v_Matte;         // x alpha-test selector, y combine reference, zw unused
layout(location = 5) flat in vec4 v_FogColour;
layout(location = 6) flat in vec4 v_FogParams;
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

bool celtiumAlphaPass(int func, float alpha, float reference) {
    if (func == 0x0200) return false;              // GL_NEVER
    if (func == 0x0201) return alpha < reference;   // GL_LESS
    if (func == 0x0202) return alpha == reference;  // GL_EQUAL
    if (func == 0x0203) return alpha <= reference;  // GL_LEQUAL
    if (func == 0x0204) return alpha > reference;   // GL_GREATER
    if (func == 0x0205) return alpha != reference;  // GL_NOTEQUAL
    if (func == 0x0206) return alpha >= reference;  // GL_GEQUAL
    return true;                                    // GL_ALWAYS
}

void main() {
    vec4 colour = texture(u_Texture, v_TexCoord) * v_Color;
    if (v_AlphaColour.x < 0.0) {
        if (v_Matte.y > 0.0 && colour.a <= v_Matte.y) {
            discard;
        }
        colour.rgb = mix(colour.rgb, v_AlphaColour.yzw, -v_AlphaColour.x);
    } else {
        int alphaFunc = int(v_Matte.x + 0.5);
        if (alphaFunc != 0 && !celtiumAlphaPass(alphaFunc, colour.a, v_AlphaColour.x)) {
            discard;
        }
    }
    colour.rgb = mix(colour.rgb, v_FogColour.rgb, celtiumFogAmount(v_FogDistance));
    fragColor = colour;
}
