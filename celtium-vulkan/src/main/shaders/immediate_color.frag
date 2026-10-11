#version 450

// Celtium - immediate-mode POSITION_COLOR geometry, fragment stage.
//
// The fixed-function fog is applied here, after the alpha test, exactly where OpenGL applies it: the fog coordinate
// arrives interpolated from the vertex stage and the mode decides the fragment's weight. GL's own formulas:
//   LINEAR  f = (end - d) / (end - start), clamped to 0..1
//   EXP     f = exp(-density * d)
//   EXP2    f = exp(-(density * d)^2)
// f is the fragment's own weight; the blended colour is mix(fragment, fogColour, 1 - f). The fragment's alpha is not
// touched, which is also what the fixed pipeline does. A batch offered while the game had fog off (the sky's own
// sunrise fan, the whole GUI) carries mode 0 and is left exactly as it was.

layout(location = 0) in vec4 v_Color;
layout(location = 1) in float v_FogDistance;
layout(location = 0) out vec4 fragColor;

// The alpha test, at offset 112 of the push-constant block: Minecraft runs it through glAlphaFunc and cutout content
// depends on the discard. A threshold of exactly 0 leaves every fragment in, which is what a batch drawn with the
// test disabled gets.
layout(push_constant) uniform PushConstants {
    mat4 u_ProjectionMatrix;
    // The model view is affine, so only its three rotation/translation columns are passed and the shader rebuilds the
    // fourth. With the alpha reference at 112 the whole block was 116 bytes - inside the 128 Vulkan guarantees (S19) -
    // and it is declared identically in both stages, which is what the driver requires. The fog block appended after it
    // takes the block to 208, the size the lit ITEM pipeline already required of the device.
    vec4 u_MvColumn0;
    vec4 u_MvColumn1;
    vec4 u_MvColumn2;
    layout(offset = 112) float u_AlphaRef;
    layout(offset = 116) float u_ColorR;
    layout(offset = 120) float u_ColorG;
    layout(offset = 124) float u_ColorB;

    // ---- fixed-function fog, appended after the original 128 bytes (declared identically in the vertex stage) ----
    layout(offset = 128) vec4 u_FogColor;   // rgb = GL_FOG_COLOR, a = blend weight (1.0 in vanilla)
    layout(offset = 144) vec4 u_FogParams;  // x density, y start, z end, w = mode + 8 * shape
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

void main() {
    vec4 colour = v_Color;
    if (celtium.u_AlphaRef > 0.0 && colour.a < celtium.u_AlphaRef) {
        discard;
    }
    colour.rgb = mix(colour.rgb, celtium.u_FogColor.rgb, celtiumFogAmount(v_FogDistance));
    fragColor = colour;
}
