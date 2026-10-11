#version 450

// Celtium - immediate-mode geometry with a texture: POSITION_TEX_COLOR vertices, which is what
// DefaultVertexFormats.POSITION_TEX_COLOR declares (3 floats position, 2 floats texture coordinates, 4 unsigned bytes
// colour). Push constants keep the terrain and UI convention: projection at 0, model view at 64.

layout(push_constant) uniform PushConstants {
    mat4 u_ProjectionMatrix;
    // The model view is affine, so only its three rotation/translation columns are passed and the shader rebuilds the
    // fourth. With the alpha reference at 112 the whole block is 116 bytes - inside the 128 Vulkan guarantees (S19) -
    // and it is declared identically in both stages, which is what the driver requires.
    vec4 u_TexA;
    vec4 u_TexB;
    vec4 u_TexPad;
    layout(offset = 112) float u_AlphaRef;
    layout(offset = 116) float u_ColorR;
    layout(offset = 120) float u_ColorG;
    layout(offset = 124) float u_ColorB;

    // BH-30: the fixed-function fog, in the 128..207 region the backend already writes for every group that carries no
    // lighting payload. This family's pipeline declares 208 bytes, so declaring the block is not an ABI change; a batch
    // offered while the game had fog off carries selector 0 and the computation below is skipped. Without it the
    // terrain sank into the fog at a distance while the item, cloud and HUD geometry drawn through this family did not.
    layout(offset = 128) vec4 u_FogColor;   // rgb = GL_FOG_COLOR, a = blend weight (1.0 in vanilla)
    layout(offset = 144) vec4 u_FogParams;  // x density, y start, z end, w = mode + 8 * shape
    layout(offset = 160) vec4 u_FogRow0;    // the model view's row 0 (m00,m01,m02) and its translation tx
    layout(offset = 176) vec4 u_FogRow1;
    layout(offset = 192) vec4 u_FogRow2;
} celtium;

layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec2 a_TexCoord;
layout(location = 2) in vec4 a_Color;

layout(location = 0) out vec2 v_TexCoord;
layout(location = 1) out vec4 v_Color;
layout(location = 2) out float v_FogDistance;

/** The fog coordinate in eye space, {@code M * position}, the same computation the position-only family uses. */
float celtiumFogDistance(vec3 position) {
    vec3 eye = vec3(dot(position, celtium.u_FogRow0.xyz) + celtium.u_FogRow0.w,
                    dot(position, celtium.u_FogRow1.xyz) + celtium.u_FogRow1.w,
                    dot(position, celtium.u_FogRow2.xyz) + celtium.u_FogRow2.w);
    int selector = int(celtium.u_FogParams.w + 0.5);
    return (selector >> 3) == 2 ? abs(eye.z) : length(eye);
}

void main() {
    if (celtium.u_TexB.z > 0.5) {
        v_TexCoord = vec2(celtium.u_TexA.x * a_TexCoord.x + celtium.u_TexA.z * a_TexCoord.y + celtium.u_TexB.x,
                celtium.u_TexA.y * a_TexCoord.x + celtium.u_TexA.w * a_TexCoord.y + celtium.u_TexB.y);
    } else {
        v_TexCoord = a_TexCoord;
    }
    v_Color = a_Color;
    v_FogDistance = (int(celtium.u_FogParams.w + 0.5) & 7) == 0 ? 0.0 : celtiumFogDistance(a_Position);
    gl_Position = celtium.u_ProjectionMatrix * vec4(a_Position, 1.0);
}
