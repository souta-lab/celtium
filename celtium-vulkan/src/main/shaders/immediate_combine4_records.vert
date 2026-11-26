#version 450

// Celtium - the record-reading form of immediate_combine4.vert (Stage 5 stage 6): four
// texture-coordinate sets (POSITION + UV0..UV3, 44 bytes) for one texture sampled through
// several fixed-function texture units - Xaero's World Map draws its map tiles this way
// (GuiMap.POSITION_TEX_TEX_TEX), binding the same tile texture on units 0, 2 and 3; unit 1's
// coordinate is not read (that unit's texturing is disabled for these draws).
//
// The per-group block comes from the draw-record storage buffer at set 1, indexed by
// gl_InstanceIndex. What the push-block variant's fragment stage read out of the block - the
// combine selector at byte 92, the current colour's alpha at 96, the alpha reference and the
// constant colour at 112..127, and the fog block - travels per record through this stage's
// flat outputs, so a captured entry keeps the exact terms its group was recorded with.

struct DrawRecord {
    mat4 u_ProjectionMatrix;   // 0: projection alone - the model view is baked into the positions
    vec4 u_TexA;               // 64: the unit-0 texture affine (a, b, c, d)
    vec4 u_TexB;               // 80: (tx, ty, affine enable, combine selector at byte 92)
    vec4 u_TexPad;             // 96: x the current colour's alpha - this layout has no colour element
    vec4 u_AlphaColour;        // 112: x alpha reference, yzw the current glColor's rgb
    vec4 u_FogColor;           // 128
    vec4 u_FogParams;          // 144: x density, y start, z end, w mode + 8 * shape
    vec4 u_FogRow0;            // 160
    vec4 u_FogRow1;            // 176
    vec4 u_FogRow2;            // 192
    vec4 u_RecordPad;          // 208: padding to the array's 256-byte stride
    vec4 u_RecordPad2;         // 224
    vec4 u_RecordPad3;         // 240
};

layout(std430, set = 1, binding = 0) readonly buffer DrawRecords {
    DrawRecord records[];
};

layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec2 a_TexCoord;  // unit 0
layout(location = 3) in vec2 a_TexCoord2; // unit 2
layout(location = 4) in vec2 a_TexCoord3; // unit 3

layout(location = 0) out vec2 v_TexCoord;
layout(location = 1) out vec4 v_Color;
layout(location = 2) out float v_FogDistance;
layout(location = 3) out vec2 v_TexCoord2;
layout(location = 4) out vec2 v_TexCoord3;
layout(location = 5) flat out vec4 v_AlphaColour;  // x alpha reference, yzw the constant colour
layout(location = 6) flat out float v_Combine;     // 0: unit 0 MODULATE only; 1: the three-unit light combine
layout(location = 7) flat out vec4 v_FogColour;
layout(location = 8) flat out vec4 v_FogParams;

/** The fog coordinate in eye space, the same computation the push-block variant performs. */
float celtiumFogDistance(in DrawRecord record, vec3 position) {
    vec3 eye = vec3(dot(position, record.u_FogRow0.xyz) + record.u_FogRow0.w,
                    dot(position, record.u_FogRow1.xyz) + record.u_FogRow1.w,
                    dot(position, record.u_FogRow2.xyz) + record.u_FogRow2.w);
    int selector = int(record.u_FogParams.w + 0.5);
    return (selector >> 3) == 2 ? abs(eye.z) : length(eye);
}

void main() {
    DrawRecord record = records[gl_InstanceIndex];
    if (record.u_TexB.z > 0.5) {
        // The GL texture matrix, carried as a 2D affine for the item glint and the renderer's own texture-matrix
        // effects: u' = a*u + c*v + tx, v' = b*u + d*v + ty.
        v_TexCoord = vec2(record.u_TexA.x * a_TexCoord.x + record.u_TexA.z * a_TexCoord.y + record.u_TexB.x,
                record.u_TexA.y * a_TexCoord.x + record.u_TexA.w * a_TexCoord.y + record.u_TexB.y);
    } else {
        v_TexCoord = a_TexCoord;
    }
    v_TexCoord2 = a_TexCoord2;
    v_TexCoord3 = a_TexCoord3;
    // No per-vertex colour in this format: the texture carries the colour and the current glColor tints it, exactly
    // as the fixed pipeline multiplies. The one exception is a combine draw (negative alpha reference), where the
    // colour slots carry the constant the fragment mixes toward - applying it here too would double the tint.
    v_Color = record.u_AlphaColour.x < 0.0 ? vec4(1.0)
            : vec4(record.u_AlphaColour.yzw, record.u_TexPad.x);
    v_AlphaColour = record.u_AlphaColour;
    v_Combine = record.u_TexB.w;
    v_FogColour = record.u_FogColor;
    v_FogParams = record.u_FogParams;
    // Mode 0 means "no fog for this batch", and the rows above are then whatever the previous group left in the
    // shared block, so the coordinate is not computed at all in that case.
    v_FogDistance = (int(record.u_FogParams.w + 0.5) & 7) == 0 ? 0.0 : celtiumFogDistance(record, a_Position);
    gl_Position = record.u_ProjectionMatrix * vec4(a_Position, 1.0);
}
