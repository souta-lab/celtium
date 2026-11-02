#version 450

// Celtium - the lit variant of immediate_tex_color.vert: POSITION_TEX_COLOR vertices plus the fixed-function
// lighting the ITEM format carries. The normal the trailing element of every vertex already holds becomes a real
// attribute, and the push block's extension carries the light directions (already rotated into model space by the
// CPU, where the modelview inverse was available), the scene ambient and each light's diffuse.
//
// The eval is the GL fixed-function equation with colour-material on: vertex colour doubles as the ambient and
// diffuse material, so lit = colour * (ambient + sum_i diffuse_i * max(dot(N, L_i), 0)). Flat shading needs no
// special casing - vanilla sets one normal per quad, so every vertex of a quad evaluates identically.

// Celtium - the record-reading form of immediate_tex_color_lit.vert: the same block, taken from the draw-record
// storage buffer at set 1 and indexed by gl_InstanceIndex. The lit block is 256 bytes: light coordinate at 208, the
// bindless texture index the record writer stores at 216 (unread here), and the fog block at 224/240.
struct DrawRecord {
    mat4 u_ProjectionMatrix;   // 0
    vec4 u_TexA;               // 64
    vec4 u_TexB;               // 80
    vec4 u_TexPad;             // 96
    vec4 u_AlphaColour;        // 112: x alpha reference, yzw the current glColor's rgb
    vec4 u_Light0Dir;          // 128
    vec4 u_Light1Dir;          // 144
    vec4 u_Ambient;            // 160
    vec4 u_Diffuse0;           // 176
    vec4 u_Diffuse1;           // 192
    vec4 u_LightCoordPad;      // 208: xy the light-map coordinate, zw the bindless index (unused by this path)
    vec4 u_FogColor;           // 224
    vec4 u_FogParams;          // 240: x density, y start, z end, w mode + 8 * shape
};

layout(std430, set = 1, binding = 0) readonly buffer DrawRecords {
    DrawRecord records[];
};

layout(set = 0, binding = 0) uniform sampler2D u_Texture;
layout(set = 0, binding = 1) uniform sampler2D u_Lightmap;

// view, so the matrix here is lightWorld * inverse(modelView) and needs no further transform. Binding 3 is the map.

layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec2 a_TexCoord;
layout(location = 2) in vec4 a_Color;
layout(location = 3) in vec3 a_Normal;

layout(location = 0) out vec2 v_TexCoord;
layout(location = 1) out vec4 v_Color;
layout(location = 2) out float v_FogDistance;
layout(location = 3) flat out vec4 v_FogColor;
layout(location = 4) flat out vec4 v_FogParams;

/**
 * The same eye-space reconstruction as immediate_tex_color_lit.vert: this family's block carries the combined
 * projection (P * MV) and no model view, so the eye position comes from the perspective structure - clip.x = P00 *
 * eye.x, clip.y = P11 * eye.y, clip.w = -eye.z - with P00/P11 riding in the lighting payload's unused .w slots.
 */
float celtiumFogDistance(vec4 clip, DrawRecord record) {
    float sx = record.u_Light0Dir.w;
    float sy = record.u_Light1Dir.w;
    vec3 eye = (sx != 0.0 && sy != 0.0)
            ? vec3(clip.x / sx, clip.y / sy, -clip.w)
            : vec3(0.0, 0.0, -clip.w);
    int selector = int(record.u_FogParams.w + 0.5);
    return (selector >> 3) == 2 ? abs(eye.z) : length(eye);
}

void main() {
    DrawRecord record = records[gl_InstanceIndex];
    if (record.u_TexB.z > 0.5) {
        v_TexCoord = vec2(record.u_TexA.x * a_TexCoord.x + record.u_TexA.z * a_TexCoord.y + record.u_TexB.x,
                record.u_TexA.y * a_TexCoord.x + record.u_TexA.w * a_TexCoord.y + record.u_TexB.y);
    } else {
        v_TexCoord = a_TexCoord;
    }
    vec3 n = normalize(a_Normal);
    vec3 lit = record.u_Ambient.rgb
            + record.u_Diffuse0.rgb * max(dot(n, record.u_Light0Dir.xyz), 0.0)
            + record.u_Diffuse1.rgb * max(dot(n, record.u_Light1Dir.xyz), 0.0);
    // The world light level, the term that makes an entity dark at night: the light map is the 16x16 texture vanilla
    // modulates unit 1 with, and the coordinate is the pair the game set for this entity. Everything else here is the
    // fixed-function lighting, which is a function of the normal and the light directions and so is identical at noon
    // and at midnight - which is why the batch stayed bright without this line.
    // The same conversion the particle family uses, and the one vanilla's unit-1 texture matrix performs
    // (scale 1/256, translate 8/256): a raw light coordinate of 0 must land on the centre of the light map's first
    // texel (0.5/16 = 8/256), not on its corner. Dividing without the +8 bias sampled half a texel off, which on a
    // 16x16 light map is the neighbouring texel at the boundaries.
    vec3 lightmap = texture(u_Lightmap, (record.u_LightCoordPad.xy + 8.0) / 256.0).rgb;
    // The current glColor, which fixed-function multiplies into the vertex colour: the hurt flash's red
    // (glColor(1,0,0,0.3)) and the slime gel's translucent white (glColor(1,1,1,0.25)) both arrive this way, and a
    // drop leaves the gel opaque white and the flash invisible - the two operator reports this line exists for. The
    // combine case is excluded: under a negative alpha reference the colour slots carry the combine constant, which
    // the fragment stage mixes in itself, and multiplying it in here would tint twice.
    vec4 groupColour = record.u_AlphaColour.x < 0.0 ? vec4(1.0)
            : vec4(record.u_AlphaColour.y, record.u_AlphaColour.z, record.u_AlphaColour.w, record.u_TexPad.x);
    // GL clamps the lit primary colour to [0,1] before the texture stages: material * (ambient + both diffuse terms)
    // exceeds 1 on a face both lights reach (0.4 + 0.6 + 0.6 at most), and unclamped it drew the first-person arm
    // x1.16 brighter than Celeritas. The light map modulates after the clamp, as unit 1 does.
    v_Color = vec4(clamp(a_Color.rgb * groupColour.rgb * lit, 0.0, 1.0) * lightmap, a_Color.a * groupColour.a);
    vec4 clip = record.u_ProjectionMatrix * vec4(a_Position, 1.0);
    v_FogDistance = (int(record.u_FogParams.w + 0.5) & 7) == 0 ? 0.0 : celtiumFogDistance(clip, record);
    v_FogColor = record.u_FogColor;
    v_FogParams = record.u_FogParams;
    gl_Position = clip;
}
