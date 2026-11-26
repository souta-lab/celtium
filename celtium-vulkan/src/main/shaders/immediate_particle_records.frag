#version 450

// Celtium - the record-reading variant of immediate_particle.frag: the same evaluation the push variant
// performs - texture times vertex colour times the light map sample, the alpha product taken from the
// texture and the vertex colour alone, and the alpha-reference discard - with the reference arriving per
// record through the vertex stage's flat varying instead of the push block. No fog, no combine: the push
// variant applied neither, and this stage must not start.

layout(set = 0, binding = 0) uniform sampler2D u_Texture;
layout(set = 0, binding = 1) uniform sampler2D u_Lightmap;

layout(location = 0) in vec2 v_TexCoord;
layout(location = 1) in vec4 v_Color;
layout(location = 2) in vec2 v_Lightmap;
layout(location = 3) flat in float v_AlphaRef;
layout(location = 0) out vec4 fragColor;

void main() {
    vec4 textureColour = texture(u_Texture, v_TexCoord);
    vec4 colour = textureColour * v_Color * texture(u_Lightmap, v_Lightmap);
    colour.a = textureColour.a * v_Color.a;
    if (v_AlphaRef > 0.0 && colour.a < v_AlphaRef) {
        discard;
    }
    fragColor = colour;
}
