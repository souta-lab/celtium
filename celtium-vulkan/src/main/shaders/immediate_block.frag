#version 450

layout(set = 0, binding = 0) uniform sampler2D u_Texture;
layout(set = 0, binding = 1) uniform sampler2D u_Lightmap;

layout(location = 0) in vec4 v_Color;
layout(location = 1) in vec2 v_TexCoord;
layout(location = 2) in vec2 v_Lightmap;

layout(location = 0) out vec4 fragColor;

// The lightmap coordinates arrive as unsigned shorts whose upper 8 bits are the coordinate and whose lower 8 bits
// carry the blend weight, which is how Minecraft packs them. The lightmap image is 16x16 with NEAREST sampling, so
// sampling its centre for each texel reproduces what GL does with the same coordinates.
layout(push_constant) uniform PushConstants {
    mat4 u_ProjectionMatrix;
    // The model view is affine, so only its three rotation/translation columns are passed and the shader rebuilds the
    // fourth. With the alpha reference at 112 the whole block is 116 bytes - inside the 128 Vulkan guarantees (S19) -
    // and it is declared identically in both stages, which is what the driver requires.
    vec4 u_MvColumn0;
    vec4 u_MvColumn1;
    vec4 u_MvColumn2;
    layout(offset = 112) float u_AlphaRef;
    layout(offset = 116) float u_ColorR;
    layout(offset = 120) float u_ColorG;
    layout(offset = 124) float u_ColorB;
} celtium;

void main() {

    vec4 light = texture(u_Lightmap, v_Lightmap);
    vec4 colour = texture(u_Texture, v_TexCoord) * v_Color * light;
    colour.a = texture(u_Texture, v_TexCoord).a * v_Color.a;
    if (celtium.u_AlphaRef > 0.0 && colour.a < celtium.u_AlphaRef) {
        discard;
    }
    fragColor = colour;
}
