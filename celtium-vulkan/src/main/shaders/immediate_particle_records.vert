#version 450

// Celtium - the record-reading variant of immediate_particle.vert (Stage 5 S5-3): the same particle layout
// (DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP: 3 floats position, 2 floats texture coordinates, 4
// unsigned bytes colour, 2 unsigned shorts lightmap), with the push block taken from the draw-record storage
// buffer at set 1, indexed by gl_InstanceIndex, instead of push constants.
//
// The struct mirrors the push layout of immediate_particle.vert byte for byte over the fields this family
// reads: 0 the combined matrix (the model view is baked into the positions), 64/80 the texture affine, 112
// the alpha reference. The particle fragment stage applies no fog and no combine, so the fog block the
// record carries at 128.. is declared for the stride and never read, exactly as the push variant's fragment
// stage ignored the colour slots at 116..
struct DrawRecord {
    mat4 u_ProjectionMatrix;   // 0
    vec4 u_TexA;               // 64
    vec4 u_TexB;               // 80
    vec4 u_TexPad;             // 96
    vec4 u_AlphaColour;        // 112: x alpha reference (the colour slots yzw are unread by this family)
    vec4 u_FogColor;           // 128
    vec4 u_FogParams;          // 144
    vec4 u_FogRow0;            // 160
    vec4 u_FogRow1;            // 176
    vec4 u_FogRow2;            // 192
    vec4 u_RecordPad;          // 208
    vec4 u_RecordPad2;         // 224
    vec4 u_RecordPad3;         // 240
};

layout(std430, set = 1, binding = 0) readonly buffer DrawRecords {
    DrawRecord records[];
};

layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec2 a_TexCoord;
layout(location = 2) in vec4 a_Color;
layout(location = 3) in uvec2 a_LightCoord;

layout(location = 0) out vec2 v_TexCoord;
layout(location = 1) out vec4 v_Color;
layout(location = 2) // Interpolated like the fixed pipeline's texture coordinates: flat integers lost the per-vertex blend that
    // Minecraft relies on at light boundaries. The value is the raw coordinate scaled and biased exactly as
    // vanilla's texture matrix does it, scale 1/256 and translate 8.
    out vec2 v_Lightmap;
// Per record, not per draw: the alpha reference varies between the groups a segment merges, so it travels
// with the record through a flat varying, as the textured records pair carries its own.
layout(location = 3) flat out float v_AlphaRef;

void main() {
    DrawRecord record = records[gl_InstanceIndex];
    if (record.u_TexB.z > 0.5) {
        v_TexCoord = vec2(record.u_TexA.x * a_TexCoord.x + record.u_TexA.z * a_TexCoord.y + record.u_TexB.x,
                record.u_TexA.y * a_TexCoord.x + record.u_TexA.w * a_TexCoord.y + record.u_TexB.y);
    } else {
        v_TexCoord = a_TexCoord;
    }
    v_Color = a_Color;
    v_Lightmap = (vec2(a_LightCoord) + 8.0) / 256.0;
    v_AlphaRef = record.u_AlphaColour.x;
    gl_Position = record.u_ProjectionMatrix * vec4(a_Position, 1.0);
}
