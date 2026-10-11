#version 450

layout(set = 0, binding = 0) uniform sampler2D u_Texture;

layout(location = 0) in vec2 v_TexCoord;
layout(location = 1) in vec4 v_Color;
layout(location = 2) in float v_FogDistance;
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
    // The texture affine's padding slot, declared here exactly as the vertex stage declares it: BH-03's
    // alpha-test comparison selector rides u_TexPad.y (the vertex stage reads .z for the affine enable), so
    // the fragment must declare the same field or the two stages disagree and the shader does not compile.
    layout(offset = 96) vec4 u_TexPad;
    layout(offset = 112) float u_AlphaRef;
    layout(offset = 116) float u_ColorR;
    layout(offset = 120) float u_ColorG;
    layout(offset = 124) float u_ColorB;

    // BH-30: the fog block, declared exactly as the vertex stage declares it. The backend writes it for every group
    // without a lighting payload; mode 0 means "no fog for this batch" and leaves the colour untouched.
    layout(offset = 128) vec4 u_FogColor;
    layout(offset = 144) vec4 u_FogParams;
    layout(offset = 160) vec4 u_FogRow0;
    layout(offset = 176) vec4 u_FogRow1;
    layout(offset = 192) vec4 u_FogRow2;
} celtium;

/** How much fog this fragment gets: 0 for none, 1 for the fog colour entirely. Mode 0 is "fog off for this batch". */
float celtiumFogAmount(float distance) {
    int mode = int(celtium.u_FogParams.w + 0.5) & 7;
    if (mode == 0) {
        return 0.0;
    }
    float weight;
    if (mode == 1) {        // GL_EXP2: exp(-(density * d)^2), base e - the terrain block's sodium fog uses base 2 here
        float scaled = distance * celtium.u_FogParams.x;
        weight = clamp(exp(-(scaled * scaled)), 0.0, 1.0);
    } else if (mode == 2) { // GL_EXP: exp(-density * d)
        weight = clamp(exp(-(distance * celtium.u_FogParams.x)), 0.0, 1.0);
    } else {                // GL_LINEAR
        float span = max(celtium.u_FogParams.z - celtium.u_FogParams.y, 0.000001);
        weight = clamp((celtium.u_FogParams.z - distance) / span, 0.0, 1.0);
    }
    return (1.0 - weight) * celtium.u_FogColor.a;
}

/**
 * BH-03: the GL alpha comparison, so the fragment stops implementing GREATER alone. The selector arrives in the spare
 * {@code u_TexPad.y} at offset 100 as the raw GL enum, with 0 meaning "the test is disabled" - no GL enum is 0, so a
 * reference of 0 stays distinguishable from a disabled test, which the old {@code alphaRef > 0.0} guard conflated.
 */
bool celtiumAlphaPass(int func, float alpha, float reference) {
    if (func == 0x0200) return false;              // GL_NEVER
    if (func == 0x0201) return alpha < reference;   // GL_LESS
    if (func == 0x0202) return alpha == reference;  // GL_EQUAL
    if (func == 0x0203) return alpha <= reference;  // GL_LEQUAL
    if (func == 0x0204) return alpha > reference;   // GL_GREATER
    if (func == 0x0205) return alpha != reference;  // GL_NOTEQUAL
    if (func == 0x0206) return alpha >= reference;  // GL_GEQUAL
    return true;                                    // GL_ALWAYS
}

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
    } else {
        int alphaFunc = int(celtium.u_TexPad.y + 0.5);
        if (alphaFunc != 0 && !celtiumAlphaPass(alphaFunc, colour.a, celtium.u_AlphaRef)) {
            discard;
        }
    }
    colour.rgb = mix(colour.rgb, celtium.u_FogColor.rgb, celtiumFogAmount(v_FogDistance));
    fragColor = colour;
}
