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
// storage buffer at set 1 and indexed by gl_InstanceIndex. The lit block is 216 bytes (its light coordinate sits at
// 208) and the record array's stride is 224, which is what the trailing pad is for.
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
    vec4 u_LightCoordPad;      // 208: xy the light-map coordinate, zw pad to the stride
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
    v_Color = vec4(a_Color.rgb * lit * lightmap * groupColour.rgb, a_Color.a * groupColour.a);
    gl_Position = record.u_ProjectionMatrix * vec4(a_Position, 1.0);
}
