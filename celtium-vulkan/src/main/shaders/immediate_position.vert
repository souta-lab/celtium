#version 450

// Celtium - immediate-mode geometry with no texture and no vertex colour: POSITION, twelve bytes, the layout
// RenderGlobal builds its sky dome, its stars and its horizon plane in (DefaultVertexFormats.POSITION) and the one the
// GL-less stub decodes out of glDrawArrays. The colour arrives in the push constant block, because the format has no
// colour element.

layout(push_constant) uniform PushConstants {
    mat4 u_ProjectionMatrix;
    // The region the model-view columns once occupied is dead in this shader: nothing reads it, and the model view
    // arrives already multiplied into u_ProjectionMatrix. The writer still fills it (with the texture matrix, or with
    // zero), so it stays declared for the layout to match its fragment stage.
    vec4 u_MvColumn0;   // 64, dead
    vec4 u_MvColumn1;   // 80, dead
    // 96: the current colour's alpha. This layout has no colour element (POSITION and POSITION_NORMAL), so the game's
    // current colour arrives in the push block - RGB at 116/120/124 and its alpha in the first float of the third dead
    // column. Without this the fragment alpha was hardcoded 1.0, which drew a batch the game had coloured with a
    // translucent alpha as if it were opaque: weather-water measured the underwater overlay (POSITION_TEX with
    // color(f,f,f,0.5), blended SRC_ALPHA/ONE_MINUS_SRC_ALPHA) as an opaque quad over the whole frame.
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

layout(location = 0) out vec4 v_Color;
layout(location = 1) out float v_FogDistance;

/**
 * The fog coordinate, in eye space: {@code M * position} for this batch's own model view.
 * <p>
 * The three rows above are the affine model view the batch was offered under, so this is exact both for camera-space
 * geometry (the sky dome, whose model view is the camera rotation alone) and for object-space geometry (a TESR drawing
 * a model at its block's translation). The fixed pipeline computes its fog from the same transformed position.
 * Planar fog - the eye-space depth - is the OpenGL default; radial is what {@code EntityRenderer.setupFog} asks for
 * with {@code GL_NV_fog_distance}, and what the terrain path in this renderer already uses.
 */
float celtiumFogDistance(vec3 position) {
    vec3 eye = vec3(dot(position, celtium.u_FogRow0.xyz) + celtium.u_FogRow0.w,
                    dot(position, celtium.u_FogRow1.xyz) + celtium.u_FogRow1.w,
                    dot(position, celtium.u_FogRow2.xyz) + celtium.u_FogRow2.w);
    int selector = int(celtium.u_FogParams.w + 0.5);
    return (selector >> 3) == 2 ? abs(eye.z) : length(eye);
}

void main() {
    // The combined projection-times-model-view arrives in u_ProjectionMatrix, so this shader multiplies once. It used to
    // rebuild the model view from the three columns with a hardcoded fourth; when the writer started sending the product and
    // zeroing the columns, that rebuild became a zero matrix and every position-only vertex collapsed to the translation point.
    gl_Position = celtium.u_ProjectionMatrix * vec4(a_Position, 1.0);
    v_Color = vec4(celtium.u_ColorR, celtium.u_ColorG, celtium.u_ColorB, celtium.u_ColorA);
    // Mode 0 means "no fog for this batch", and the rows above are then whatever the previous group left in the shared
    // push block, so the coordinate is not computed at all in that case.
    v_FogDistance = (int(celtium.u_FogParams.w + 0.5) & 7) == 0 ? 0.0 : celtiumFogDistance(a_Position);
}
