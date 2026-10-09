#version 450

layout(set = 0, binding = 0) uniform sampler2D u_Texture;
layout(set = 0, binding = 1) uniform sampler2D u_Lightmap;

layout(location = 0) in vec4 v_Color;
layout(location = 1) in vec2 v_TexCoord;
layout(location = 2) flat in uvec2 v_LightCoord;

layout(location = 0) out vec4 fragColor;

// The lightmap coordinates arrive as unsigned shorts whose upper 8 bits are the coordinate and whose lower 8 bits
// carry the blend weight, which is how Minecraft packs them. The lightmap image is 16x16 with NEAREST sampling, so
// sampling its centre for each texel reproduces what GL does with the same coordinates.
layout(push_constant) uniform PushConstants {
    layout(offset = 128) float u_AlphaRef;
} celtium;

void main() {
    float u = (float(v_LightCoord.x >> 8) + 0.5) / 256.0;
    float v = (float(v_LightCoord.y >> 8) + 0.5) / 256.0;
    vec4 light = texture(u_Lightmap, vec2(u, v));
    vec4 colour = texture(u_Texture, v_TexCoord) * v_Color * light;
    colour.a = texture(u_Texture, v_TexCoord).a * v_Color.a;
    if (celtium.u_AlphaRef > 0.0 && colour.a < celtium.u_AlphaRef) {
        discard;
    }
    fragColor = colour;
}
