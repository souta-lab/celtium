#version 450

// The lit ITEM family's fragment stage (BH-30's collision). It is the shared immediate_tex_color.frag WITHOUT the fog
// input and fog math, because this family's push block spends 128..215 on the lighting payload and the fog block does
// not fit beside it on a 256-byte device (proj-channel measured 224..303 = 304 bytes). The interface rule is per
// pipeline, so giving the lit variant its own fragment removes the VUID-RuntimeSpirv-OpEntryPoint-08743 mismatch
// without touching a single offset; adding fog to this family later needs a payload that fits, not a shader edit.

layout(set = 0, binding = 0) uniform sampler2D u_Texture;

layout(location = 0) in vec2 v_TexCoord;
layout(location = 1) in vec4 v_Color;
layout(location = 0) out vec4 fragColor;

// The alpha test, at offset 128 of the push-constant block: Minecraft runs it through glAlphaFunc and cutout content
// depends on the discard. A threshold of exactly 0 leaves every fragment in, which is what a batch drawn with the
// test disabled gets.
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
    vec4 colour = texture(u_Texture, v_TexCoord) * v_Color;
    if (celtium.u_AlphaRef < 0.0) {
        // The GL_COMBINE interpolate the fixed pipeline applies for the hurt flash and the team outline: a negative
        // alpha reference marks the combine (alpha tests use non-negative references only), the constant sits in the
        // colour slots and its alpha is the mix factor. Alpha comes through unchanged - the combine setup leaves it on
        // unit 0's result. This branch is the second half of the hurt-flash path, and it was missing here after the
        // entity and hand batches moved to the lit ITEM layout: the same branch exists in
        // immediate_position_tex.frag, and the measured consequence of its absence was the reference shifting the
        // zombie head (78.9,123.7,87.7) -> (117.3,105.0,79.4) and the creeper (74.1,140.8,87.4) -> (118.1,113.2,76.0)
        // while ours moved by at most 1.8 pixels.
        colour.rgb = mix(colour.rgb, vec3(celtium.u_ColorR, celtium.u_ColorG, celtium.u_ColorB), -celtium.u_AlphaRef);
    } else if (celtium.u_AlphaRef > 0.0 && colour.a < celtium.u_AlphaRef) {
        discard;
    }
    fragColor = colour;
}
