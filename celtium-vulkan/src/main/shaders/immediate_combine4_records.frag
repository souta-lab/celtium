#version 450

// Celtium - the record-reading form of immediate_combine4.frag (Stage 5 stage 6): unit 0
// MODULATE, or the three-unit light combine Xaero's World Map sets up in
// GuiMap.setupTextures (v_Combine = 1). The combine selector, the alpha reference, the
// constant colour and the fog block arrive per record through the vertex stage's flat
// outputs, so this stage declares no push block at all.
//
// The fixed-function fog is applied here, after the alpha test and the combine, exactly
// where OpenGL applies it: the fog coordinate arrives interpolated from the vertex stage
// and the mode decides the fragment's weight. GL's own formulas, not approximations of
// them:
//   LINEAR  f = (end - d) / (end - start), clamped to 0..1
//   EXP     f = exp(-density * d)
//   EXP2    f = exp(-(density * d)^2)
// f is the fragment's own weight; the blended colour is mix(fragment, fogColour, 1 - f). The
// fragment's alpha is not touched, which is also what the fixed pipeline does.

layout(set = 0, binding = 0) uniform sampler2D u_Texture;

layout(location = 0) in vec2 v_TexCoord;
layout(location = 1) in vec4 v_Color;
layout(location = 2) in float v_FogDistance;
layout(location = 3) in vec2 v_TexCoord2;
layout(location = 4) in vec2 v_TexCoord3;
layout(location = 5) flat in vec4 v_AlphaColour;  // x alpha reference, yzw the constant colour
layout(location = 6) flat in float v_Combine;
layout(location = 7) flat in vec4 v_FogColour;
layout(location = 8) flat in vec4 v_FogParams;
layout(location = 0) out vec4 fragColor;

/** How much fog this fragment gets: 0 for none, 1 for the fog colour entirely. Mode 0 is "fog off for this batch". */
float celtiumFogAmount(float distance) {
    int mode = int(v_FogParams.w + 0.5) & 7;
    if (mode == 0) {
        return 0.0;
    }
    float weight;
    if (mode == 1) {        // GL_EXP2: exp(-(density * d)^2), base e - the terrain block's sodium fog uses base 2 here
        float scaled = distance * v_FogParams.x;
        weight = clamp(exp(-(scaled * scaled)), 0.0, 1.0);
    } else if (mode == 2) { // GL_EXP: exp(-density * d)
        weight = clamp(exp(-(distance * v_FogParams.x)), 0.0, 1.0);
    } else {                // GL_LINEAR
        float span = max(v_FogParams.z - v_FogParams.y, 0.000001);
        weight = clamp((v_FogParams.z - distance) / span, 0.0, 1.0);
    }
    return (1.0 - weight) * v_FogColour.a;
}

void main() {
    vec4 colour;
    if (v_Combine > 0.5) {
        // The three units' fixed-function environments, each clamped as GL clamps a combiner's output:
        //   unit 0 COMBINE: rgb = SUBTRACT(primary.rgb, texture.a), alpha = REPLACE(primary.a)
        //   unit 2 COMBINE: rgb = ADD(previous.rgb, texture.a),      alpha = REPLACE(primary.a)
        //   unit 3 MODULATE: previous * texture
        vec4 t0 = texture(u_Texture, v_TexCoord);
        vec4 t2 = texture(u_Texture, v_TexCoord2);
        vec4 t3 = texture(u_Texture, v_TexCoord3);
        vec3 rgb = clamp(v_Color.rgb - vec3(t0.a), 0.0, 1.0);
        rgb = clamp(rgb + vec3(t2.a), 0.0, 1.0);
        colour = vec4(rgb * t3.rgb, v_Color.a * t3.a);
    } else {
        colour = texture(u_Texture, v_TexCoord) * v_Color;
    }
    if (v_AlphaColour.x < 0.0) {
        // The GL_COMBINE interpolate the fixed pipeline applied for the hurt flash and the team outline: the constant
        // sits in the colour slots and its alpha is the mix factor. Alpha comes through unchanged - the combine setup
        // leaves it on unit 0's result.
        colour.rgb = mix(colour.rgb, v_AlphaColour.yzw, -v_AlphaColour.x);
    } else if (v_AlphaColour.x > 0.0 && colour.a < v_AlphaColour.x) {
        discard;
    }
    colour.rgb = mix(colour.rgb, v_FogColour.rgb, celtiumFogAmount(v_FogDistance));
    fragColor = colour;
}
