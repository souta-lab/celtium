#version 450

// Celtium - the record-reading form of immediate_block.vert (Stage 5 stage 6): Minecraft's
// BLOCK-family immediate vertices (DefaultVertexFormats.BLOCK, 28 bytes: 3 floats position,
// 4 unsigned bytes colour, 2 floats texture coordinates, 2 unsigned shorts lightmap
// coordinates), with the per-group block taken from the draw-record storage buffer at set 1
// and indexed by gl_InstanceIndex.
//
// The struct mirrors the family's 128-byte push block: projection at 0, the texture affine
// at 64/80/96, the alpha reference and the current colour at 112. The fragment stage's only
// push term was the alpha reference, so it travels as a flat varying and the pipeline
// declares no push range at all.

struct DrawRecord {
    mat4 u_ProjectionMatrix;   // 0: projection alone - the model view is baked into the positions
    vec4 u_TexA;               // 64
    vec4 u_TexB;               // 80
    vec4 u_TexPad;             // 96
    vec4 u_AlphaColour;        // 112: x alpha reference, yzw the current glColor's rgb
    vec4 u_RecordPad;          // 128: padding to the array's 256-byte stride
    vec4 u_RecordPad2;         // 144
    vec4 u_RecordPad3;         // 160
    vec4 u_RecordPad4;         // 176
    vec4 u_RecordPad5;         // 192
    vec4 u_RecordPad6;         // 208
    vec4 u_RecordPad7;         // 224
    vec4 u_RecordPad8;         // 240
};

layout(std430, set = 1, binding = 0) readonly buffer DrawRecords {
    DrawRecord records[];
};

layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec4 a_Color;
layout(location = 2) in vec2 a_TexCoord;
layout(location = 3) in uvec2 a_LightCoord;

layout(location = 0) out vec4 v_Color;
layout(location = 1) out vec2 v_TexCoord;
// Interpolated like the fixed pipeline's texture coordinates: flat integers lost the per-vertex blend that
// Minecraft relies on at light boundaries. The value is the raw coordinate scaled and biased exactly as
// vanilla's texture matrix does it, scale 1/256 and translate 8.
layout(location = 2) out vec2 v_Lightmap;
layout(location = 3) flat out float v_AlphaRef;

void main() {
    DrawRecord record = records[gl_InstanceIndex];
    v_Color = a_Color;
    if (record.u_TexB.z > 0.5) {
        v_TexCoord = vec2(record.u_TexA.x * a_TexCoord.x + record.u_TexA.z * a_TexCoord.y + record.u_TexB.x,
                record.u_TexA.y * a_TexCoord.x + record.u_TexA.w * a_TexCoord.y + record.u_TexB.y);
    } else {
        v_TexCoord = a_TexCoord;
    }
    v_Lightmap = (vec2(a_LightCoord) + 8.0) / 256.0;
    v_AlphaRef = record.u_AlphaColour.x;
    gl_Position = record.u_ProjectionMatrix * vec4(a_Position, 1.0);
}
