#version 450
#extension GL_EXT_nonuniform_qualifier : require

// Celtium - the model instance fragment stage: immediate_tex_color_lit.frag with the texture taken from the shared
// array and the combine / alpha reference from the instance instead of the push block. The
// run push block carries independent fog, applied after texturing/lighting/combine and before blending.

layout(push_constant) uniform RunConstants {
    mat4 projection;
    vec4 fogColor;
    vec4 fogParams;
} runState;

layout(set = 0, binding = 0) uniform sampler2D textures[];

layout(location = 0) in vec2 v_TexCoord;
layout(location = 1) in vec4 v_Color;
layout(location = 2) flat in int v_Texture;
layout(location = 3) flat in vec4 v_Combine;
layout(location = 4) flat in float v_AlphaRef;
layout(location = 5) in float v_FogDistance;
layout(location = 6) flat in int v_AlphaFunction;
layout(location = 0) out vec4 fragColor;

// Same comparison contract as the native immediate program, including an enabled reference of zero.
bool alphaPass(int function, float alpha, float reference) {
    if (function == 0x0200) return false;
    if (function == 0x0201) return alpha < reference;
    if (function == 0x0202) return alpha == reference;
    if (function == 0x0203) return alpha <= reference;
    if (function == 0x0204) return alpha > reference;
    if (function == 0x0205) return alpha != reference;
    if (function == 0x0206) return alpha >= reference;
    return true; // disabled (0) or ALWAYS
}

void main() {
    vec4 colour = texture(textures[nonuniformEXT(v_Texture)], v_TexCoord) * v_Color;
    if (!alphaPass(v_AlphaFunction, colour.a, v_AlphaRef)) {
        discard;
    }
    if (v_Combine.w > 0.0) {
        colour.rgb = mix(colour.rgb, v_Combine.rgb, v_Combine.w);
    }
    int mode = int(runState.fogParams.w + 0.5) & 7;
    if (mode != 0) {
        float visibility;
        if (mode == 1) {
            float scaled = v_FogDistance * runState.fogParams.x;
            visibility = clamp(exp(-(scaled * scaled)), 0.0, 1.0);
        } else if (mode == 2) {
            visibility = clamp(exp(-v_FogDistance * runState.fogParams.x), 0.0, 1.0);
        } else {
            float span = max(runState.fogParams.z - runState.fogParams.y, 0.000001);
            visibility = clamp((runState.fogParams.z - v_FogDistance) / span, 0.0, 1.0);
        }
        colour.rgb = mix(colour.rgb, runState.fogColor.rgb, (1.0 - visibility) * runState.fogColor.a);
    }
    fragColor = colour;
}
