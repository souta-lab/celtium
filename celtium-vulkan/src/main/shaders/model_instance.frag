#version 450
#extension GL_EXT_nonuniform_qualifier : require

// Celtium - the model instance fragment stage: immediate_tex_color_lit.frag with the texture taken from the shared
// array and the combine / alpha reference from the instance instead of the push block.

layout(set = 0, binding = 0) uniform sampler2D textures[];

layout(location = 0) in vec2 v_TexCoord;
layout(location = 1) in vec4 v_Color;
layout(location = 2) flat in int v_Texture;
layout(location = 3) flat in vec4 v_Combine;
layout(location = 4) flat in float v_AlphaRef;
layout(location = 0) out vec4 fragColor;

void main() {
    vec4 colour = texture(textures[nonuniformEXT(v_Texture)], v_TexCoord) * v_Color;
    if (v_AlphaRef > 0.0 && colour.a < v_AlphaRef) {
        discard;
    }
    if (v_Combine.w > 0.0) {
        colour.rgb = mix(colour.rgb, v_Combine.rgb, v_Combine.w);
    }
    fragColor = colour;
}
