#version 450

layout(location = 0) in vec4 v_Color;
layout(location = 0) out vec4 fragColor;

// The alpha test, at offset 128 of the push-constant block: Minecraft runs it through glAlphaFunc and cutout content
// depends on the discard. A threshold of exactly 0 leaves every fragment in, which is what a batch drawn with the
// test disabled gets.
layout(push_constant) uniform PushConstants {
    layout(offset = 128) float u_AlphaRef;
} celtium;

void main() {
    vec4 colour = v_Color;
    if (celtium.u_AlphaRef > 0.0 && colour.a < celtium.u_AlphaRef) {
        discard;
    }
    fragColor = colour;
}
