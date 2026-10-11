#version 450

// Celtium - immediate-mode geometry (Forge API swap): untextured POSITION_COLOR vertices, the format
// DefaultVertexFormats.POSITION_COLOR declares (3 floats position, 4 unsigned bytes colour).
// Push constants follow the same convention as the terrain and UI pipelines: projection at 0, model view at 64.

layout(push_constant) uniform PushConstants {
    mat4 u_ProjectionMatrix;
    // The model view is affine, so only its three rotation/translation columns are passed and the shader rebuilds the
    // fourth. With the alpha reference at 112 the whole block was 116 bytes - inside the 128 Vulkan guarantees (S19) -
    // and it is declared identically in both stages, which is what the driver requires.
    vec4 u_MvColumn0;
    vec4 u_MvColumn1;
    vec4 u_MvColumn2;
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
layout(location = 1) in vec4 a_Color;

layout(location = 0) out vec4 v_Color;
layout(location = 1) out float v_FogDistance;

/**
 * The fog coordinate, in eye space: {@code M * position} for this batch's own model view.
 * <p>
 * The three rows above are the affine model view the batch was offered under, so this is exact both for camera-space
 * geometry and for object-space geometry drawn at its own translation. The fixed pipeline computes its fog from the
 * same transformed position. Planar fog (the eye-space depth) is the OpenGL default; radial is what
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
    v_Color = a_Color;
    gl_Position = celtium.u_ProjectionMatrix * vec4(a_Position, 1.0);
    // Mode 0 means "no fog for this batch", and the rows above are then whatever the previous group left in the shared
    // push block, so the coordinate is not computed at all in that case.
    v_FogDistance = (int(celtium.u_FogParams.w + 0.5) & 7) == 0 ? 0.0 : celtiumFogDistance(a_Position);
}
