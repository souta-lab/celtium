#version 450

// Celtium - immediate-mode geometry with a texture and no colour: POSITION_TEX vertices, the shape RenderGlobal
// uses for the sky, the clouds and the water surface (3 floats position, 2 floats texture coordinates, 20 bytes).

layout(push_constant) uniform PushConstants {
    mat4 u_ProjectionMatrix;
    // The region the model-view columns once occupied is dead in every immediate shader: nothing reads it, so the
    // texture matrix's 2D affine lives there instead - a,b,c,d in u_TexA and tx,ty plus the enable flag in u_TexB -
    // while the model view arrives already multiplied into u_ProjectionMatrix.
    vec4 u_TexA;
    vec4 u_TexB;
    // 96: the current colour's alpha. This layout has no colour element (POSITION_TEX and POSITION_TEX_NORMAL), so the
    // game's current colour arrives in the push block - RGB at 116/120/124 and its alpha here, in the first float of the
    // third dead column. Without it the fragment alpha was hardcoded 1.0: the underwater overlay
    // (ItemRenderer.renderWaterOverlayTexture, color(f,f,f,0.5) with SRC_ALPHA/ONE_MINUS_SRC_ALPHA) drew as an opaque
    // quad over the whole frame under GL-less. The third column was u_TexPad and nothing read it.
    layout(offset = 96) float u_ColorA;
    layout(offset = 112) float u_AlphaRef;
    layout(offset = 116) float u_ColorR;
    layout(offset = 120) float u_ColorG;
    layout(offset = 124) float u_ColorB;

    // ---- fixed-function fog, appended after the original 128 bytes (declared identically in the fragment stage) ----
    layout(offset = 128) vec4 u_FogColor;   // rgb = GL_FOG_COLOR, a = blend weight (1.0 in vanilla)
    layout(offset = 144) vec4 u_FogParams;  // x density, y start, z end, w = mode + 8 * shape
    layout(offset = 160) vec4 u_FogRow0;    // the model view's row 0 (m00,m01,m02) and its translation tx
    layout(offset = 176) vec4 u_FogRow1;    // row 1 (m10,m11,m12) and ty
    layout(offset = 192) vec4 u_FogRow2;    // row 2 (m20,m21,m22) and tz
} celtium;

layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec2 a_TexCoord;

layout(location = 0) out vec2 v_TexCoord;
layout(location = 1) out vec4 v_Color;
layout(location = 2) out float v_FogDistance;

/**
 * The fog coordinate, in eye space: {@code M * position} for this batch's own model view.
 * <p>
 * The model view is not the texture matrix in the block above - that affine applies to the texture coordinates - so the
 * fog rows are carried separately at 160..207. Planar fog (the eye-space depth) is the OpenGL default; radial is what
 * {@code EntityRenderer.setupFog} asks for with {@code GL_NV_fog_distance}.
 */
float celtiumFogDistance(vec3 position) {
    vec3 eye = vec3(dot(position, celtium.u_FogRow0.xyz) + celtium.u_FogRow0.w,
                    dot(position, celtium.u_FogRow1.xyz) + celtium.u_FogRow1.w,
                    dot(position, celtium.u_FogRow2.xyz) + celtium.u_FogRow2.w);
    int selector = int(celtium.u_FogParams.w + 0.5);
    return (selector >> 3) == 2 ? abs(eye.z) : length(eye);
}

void main() {
    if (celtium.u_TexB.z > 0.5) {
        // The GL texture matrix, carried as a 2D affine for the item glint and the renderer's own texture-matrix
        // effects: u' = a*u + c*v + tx, v' = b*u + d*v + ty.
        v_TexCoord = vec2(celtium.u_TexA.x * a_TexCoord.x + celtium.u_TexA.z * a_TexCoord.y + celtium.u_TexB.x,
                celtium.u_TexA.y * a_TexCoord.x + celtium.u_TexA.w * a_TexCoord.y + celtium.u_TexB.y);
    } else {
        v_TexCoord = a_TexCoord;
    }
    // No per-vertex colour in this format: the texture carries the colour and the current glColor tints it, exactly
    // as the fixed pipeline multiplies. The one exception is a combine draw (negative alpha reference), where the
    // colour slots carry the constant the fragment mixes toward - applying it here too would double the tint.
    v_Color = celtium.u_AlphaRef < 0.0 ? vec4(1.0)
            : vec4(celtium.u_ColorR, celtium.u_ColorG, celtium.u_ColorB, celtium.u_ColorA);
    gl_Position = celtium.u_ProjectionMatrix * vec4(a_Position, 1.0);
    // Mode 0 means "no fog for this batch", and the rows above are then whatever the previous group left in the shared
    // push block, so the coordinate is not computed at all in that case.
    v_FogDistance = (int(celtium.u_FogParams.w + 0.5) & 7) == 0 ? 0.0 : celtiumFogDistance(a_Position);
}
