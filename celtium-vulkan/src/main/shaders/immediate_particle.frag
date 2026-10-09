#version 450

layout(set = 0, binding = 0) uniform sampler2D u_Texture;
layout(set = 0, binding = 1) uniform sampler2D u_Lightmap;

layout(location = 0) in vec2 v_TexCoord;
layout(location = 1) in vec4 v_Color;
layout(location = 2) in vec2 v_Lightmap;
layout(location = 0) out vec4 fragColor;

layout(push_constant) uniform PushConstants {
    layout(offset = 128) float u_AlphaRef;
} celtium;

void main() {

    vec4 textureColour = texture(u_Texture, v_TexCoord);
    vec4 colour = textureColour * v_Color * texture(u_Lightmap, v_Lightmap);
    colour.a = textureColour.a * v_Color.a;
    if (celtium.u_AlphaRef > 0.0 && colour.a < celtium.u_AlphaRef) {
        discard;
    }
    fragColor = colour;
}
