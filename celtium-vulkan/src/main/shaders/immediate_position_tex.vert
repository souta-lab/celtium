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
    vec4 u_TexPad;
    layout(offset = 112) float u_AlphaRef;
    layout(offset = 116) float u_ColorR;
    layout(offset = 120) float u_ColorG;
    layout(offset = 124) float u_ColorB;
} celtium;

layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec2 a_TexCoord;

layout(location = 0) out vec2 v_TexCoord;
layout(location = 1) out vec4 v_Color;

void main() {
    if (celtium.u_TexB.z > 0.5) {
        // The GL texture matrix, carried as a 2D affine for the item glint and the renderer's own texture-matrix
        // effects: u' = a*u + c*v + tx, v' = b*u + d*v + ty.
        v_TexCoord = vec2(celtium.u_TexA.x * a_TexCoord.x + celtium.u_TexA.z * a_TexCoord.y + celtium.u_TexB.x,
                celtium.u_TexA.y * a_TexCoord.x + celtium.u_TexA.w * a_TexCoord.y + celtium.u_TexB.y);
    } else {
        v_TexCoord = a_TexCoord;
    }
    // No per-vertex colour in this format: the texture carries the colour, as it does in the game's own shader.
    v_Color = vec4(1.0);
    gl_Position = celtium.u_ProjectionMatrix * vec4(a_Position, 1.0);
}
