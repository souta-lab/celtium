#version 450

// The lit ITEM family's fragment stage (BH-30's collision). It shares the immediate_tex_color.frag's alpha test and
// combine, and as of the fog payload at 216 it also applies the fixed-function fog: 128..215 is the lighting, so the
// fog block moved past it. Without it the lit family was the unfogged part of the immediate path - entities, TESRs,
// the held item and dropped items stayed crisp in water, in the Nether and under the blindness effect.

layout(set = 0, binding = 0) uniform sampler2D u_Texture;

// bias and the same 3x3 pattern. The whole block is behind a specialisation constant, so a pipeline built without the
// pass compiles it out.

layout(location = 0) in vec2 v_TexCoord;
layout(location = 1) in vec4 v_Color;
layout(location = 2) in float v_FogDistance;
layout(location = 3) flat in vec4 v_FogColor;
layout(location = 4) flat in vec4 v_FogParams;
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

// The fog arrives as the vertex stage's flat pair rather than from this block: the lighting payload owns 128..215 and
// the record-reading variant carries the fog per draw record, so the stage that knows both is the vertex stage.

/** How much fog this fragment gets: 0 for none, 1 for the fog colour entirely. Mode 0 is "fog off for this batch". */
float celtiumFogAmount(float distance) {
    int mode = int(v_FogParams.w + 0.5) & 7;
    if (mode == 0) {
        return 0.0;
    }
    float weight;
    if (mode == 1) {        // GL_EXP2: exp(-(density * d)^2), base e - the same curve the shared block uses
        float scaled = distance * v_FogParams.x;
        weight = clamp(exp(-(scaled * scaled)), 0.0, 1.0);
    } else if (mode == 2) { // GL_EXP: exp(-density * d)
        weight = clamp(exp(-(distance * v_FogParams.x)), 0.0, 1.0);
    } else {                // GL_LINEAR
        float span = max(v_FogParams.z - v_FogParams.y, 0.000001);
        weight = clamp((v_FogParams.z - distance) / span, 0.0, 1.0);
    }
    return (1.0 - weight) * v_FogColor.a;
}

void main() {
    vec4 colour = texture(u_Texture, v_TexCoord) * v_Color;
    if (celtium.u_AlphaRef < 0.0) {
        // The alpha test still applies under the combine: GL's COMBINE_ALPHA takes the texture's alpha and the test
        // runs on it. Without this the transparent overlay boxes of a hurt mob (a skeleton's headwear) were drawn as
        // solid tinted boxes. The reference the combine displaced from 112 travels at 104.
        if (celtium.u_MvColumn2.z > 0.0 && colour.a <= celtium.u_MvColumn2.z) {
            discard;
        }
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
    // The fixed-function fog, after the alpha test and the combine and before blending - where GL applies it. The
    // fragment's alpha is not touched, which is also what the fixed pipeline does.
    float fog = celtiumFogAmount(v_FogDistance);
    if (fog > 0.0) {
        colour.rgb = mix(colour.rgb, v_FogColor.rgb, fog);
    }
    fragColor = colour;
}
