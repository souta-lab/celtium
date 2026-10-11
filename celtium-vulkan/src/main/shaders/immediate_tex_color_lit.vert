#version 450

// Celtium - the lit variant of immediate_tex_color.vert: POSITION_TEX_COLOR vertices plus the fixed-function
// lighting the ITEM format carries. The normal the trailing element of every vertex already holds becomes a real
// attribute, and the push block's extension carries the light directions (already rotated into model space by the
// CPU, where the modelview inverse was available), the scene ambient and each light's diffuse.
//
// The eval is the GL fixed-function equation with colour-material on: vertex colour doubles as the ambient and
// diffuse material, so lit = colour * (ambient + sum_i diffuse_i * max(dot(N, L_i), 0)). Flat shading needs no
// special casing - vanilla sets one normal per quad, so every vertex of a quad evaluates identically.

layout(push_constant) uniform PushConstants {
    mat4 u_ProjectionMatrix;
    vec4 u_TexA;
    vec4 u_TexB;
    vec4 u_TexPad;
    layout(offset = 112) float u_AlphaRef;
    layout(offset = 116) float u_ColorR;
    layout(offset = 120) float u_ColorG;
    layout(offset = 124) float u_ColorB;
    layout(offset = 128) vec4 u_Light0Dir; // xyz model space
    layout(offset = 144) vec4 u_Light1Dir;
    layout(offset = 160) vec4 u_Ambient;
    layout(offset = 176) vec4 u_Diffuse0;
    layout(offset = 192) vec4 u_Diffuse1;
    // The world light level: the light-map coordinate pair the game set for this draw with
    // OpenGlHelper.setLightmapTextureCoords, captured per draw by LightmapCoordsMixin. Vanilla applies it on texture
    // unit 1 through the fixed-function texture matrix, whose scale is 1/256, so the sample is u_LightCoord / 256.
    layout(offset = 208) vec2 u_LightCoord;
} celtium;

layout(set = 0, binding = 0) uniform sampler2D u_Texture;
layout(set = 0, binding = 1) uniform sampler2D u_Lightmap;

layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec2 a_TexCoord;
layout(location = 2) in vec4 a_Color;
layout(location = 3) in vec3 a_Normal;

layout(location = 0) out vec2 v_TexCoord;
layout(location = 1) out vec4 v_Color;

void main() {
    if (celtium.u_TexB.z > 0.5) {
        v_TexCoord = vec2(celtium.u_TexA.x * a_TexCoord.x + celtium.u_TexA.z * a_TexCoord.y + celtium.u_TexB.x,
                celtium.u_TexA.y * a_TexCoord.x + celtium.u_TexA.w * a_TexCoord.y + celtium.u_TexB.y);
    } else {
        v_TexCoord = a_TexCoord;
    }
    vec3 n = normalize(a_Normal);
    vec3 lit = celtium.u_Ambient.rgb
            + celtium.u_Diffuse0.rgb * max(dot(n, celtium.u_Light0Dir.xyz), 0.0)
            + celtium.u_Diffuse1.rgb * max(dot(n, celtium.u_Light1Dir.xyz), 0.0);
    // The world light level, the term that makes an entity dark at night: the light map is the 16x16 texture vanilla
    // modulates unit 1 with, and the coordinate is the pair the game set for this entity. Everything else here is the
    // fixed-function lighting, which is a function of the normal and the light directions and so is identical at noon
    // and at midnight - which is why the batch stayed bright without this line.
    // The same conversion the particle family uses, and the one vanilla's unit-1 texture matrix performs
    // (scale 1/256, translate 8/256): a raw light coordinate of 0 must land on the centre of the light map's first
    // texel (0.5/16 = 8/256), not on its corner. Dividing without the +8 bias sampled half a texel off, which on a
    // 16x16 light map is the neighbouring texel at the boundaries.
    vec3 lightmap = texture(u_Lightmap, (celtium.u_LightCoord + 8.0) / 256.0).rgb;
    v_Color = vec4(a_Color.rgb * lit * lightmap, a_Color.a);
    gl_Position = celtium.u_ProjectionMatrix * vec4(a_Position, 1.0);
}
