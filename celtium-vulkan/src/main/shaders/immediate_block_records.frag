#version 450

// Celtium - the record-reading form of immediate_block.frag (Stage 5 stage 6): the same
// sampling and the same alpha test, with the reference arriving per record through the
// vertex stage's flat output instead of the push block.

layout(set = 0, binding = 0) uniform sampler2D u_Texture;
layout(set = 0, binding = 1) uniform sampler2D u_Lightmap;

layout(location = 0) in vec4 v_Color;
layout(location = 1) in vec2 v_TexCoord;
layout(location = 2) in vec2 v_Lightmap;
layout(location = 3) flat in float v_AlphaRef;

layout(location = 0) out vec4 fragColor;

void main() {
    // The lightmap coordinates arrive as unsigned shorts whose upper 8 bits are the coordinate and whose lower 8 bits
    // carry the blend weight, which is how Minecraft packs them. The lightmap image is 16x16 with NEAREST sampling, so
    // sampling its centre for each texel reproduces what GL does with the same coordinates.
    vec4 light = texture(u_Lightmap, v_Lightmap);
    vec4 colour = texture(u_Texture, v_TexCoord) * v_Color * light;
    colour.a = texture(u_Texture, v_TexCoord).a * v_Color.a;
    if (v_AlphaRef > 0.0 && colour.a < v_AlphaRef) {
        discard;
    }
    fragColor = colour;
}
