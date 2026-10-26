#version 450
#extension GL_EXT_nonuniform_qualifier : require

// Celtium - the model instance fragment stage: immediate_tex_color_lit.frag with the texture taken from the shared
// array and the combine / alpha reference from the instance instead of the push block. The record
// also carries independent fog, applied after texturing/lighting/combine and before blending.

layout(set = 0, binding = 0) uniform sampler2D textures[];

layout(location = 0) in vec2 v_TexCoord;
layout(location = 1) in vec4 v_Color;
layout(location = 2) flat in int v_Texture;
layout(location = 3) flat in vec4 v_Combine;
layout(location = 4) flat in float v_AlphaRef;
layout(location = 5) in float v_FogDistance;
layout(location = 6) flat in vec4 v_FogParams;
layout(location = 7) flat in vec4 v_FogColor;
layout(location = 0) out vec4 fragColor;

void main() {
    vec4 colour = texture(textures[nonuniformEXT(v_Texture)], v_TexCoord) * v_Color;
    if (v_AlphaRef > 0.0 && colour.a < v_AlphaRef) {
        discard;
    }
    if (v_Combine.w > 0.0) {
        colour.rgb = mix(colour.rgb, v_Combine.rgb, v_Combine.w);
    }
    int mode = int(v_FogParams.w + 0.5) & 7;
    if (mode != 0) {
        float visibility;
        if (mode == 1) {
            float scaled = v_FogDistance * v_FogParams.x;
            visibility = clamp(exp(-(scaled * scaled)), 0.0, 1.0);
        } else if (mode == 2) {
            visibility = clamp(exp(-v_FogDistance * v_FogParams.x), 0.0, 1.0);
        } else {
            float span = max(v_FogParams.z - v_FogParams.y, 0.000001);
            visibility = clamp((v_FogParams.z - v_FogDistance) / span, 0.0, 1.0);
        }
        colour.rgb = mix(colour.rgb, v_FogColor.rgb, (1.0 - visibility) * v_FogColor.a);
    }
    fragColor = colour;
}
