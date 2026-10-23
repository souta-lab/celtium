#version 450

// Celtium - immediate-mode geometry with a texture and no colour: POSITION_TEX vertices, the shape RenderGlobal
// uses for the sky, the clouds and the water surface (3 floats position, 2 floats texture coordinates, 20 bytes).

// Celtium - the record-reading form of immediate_position_tex.vert: the same 208-byte block, taken from the draw-record storage buffer at
// set 1 and indexed by gl_InstanceIndex. The array's stride is 224, which is what the trailing pad is for.
struct DrawRecord {
    mat4 u_ProjectionMatrix;   // 0
    vec4 u_A;                  // 64: dead model-view column, or the texture matrix's a,b,c,d
    vec4 u_B;                  // 80: the second dead column, or tx,ty and the enable flag
    vec4 u_C;                  // 96: x is the current colour's alpha for the colourless layouts
    vec4 u_AlphaColour;        // 112: x alpha reference, yzw the current glColor's rgb
    vec4 u_FogColor;           // 128
    vec4 u_FogParams;          // 144
    vec4 u_FogRow0;            // 160
    vec4 u_FogRow1;            // 176
    vec4 u_FogRow2;            // 192
    vec4 u_RecordPad;          // 208: pad to the 224-byte stride
};

layout(std430, set = 1, binding = 0) readonly buffer DrawRecords {
    DrawRecord records[];
};

// layout these shaders are bound with has carried both bindings since the texture family got the lookup.

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
float celtiumFogDistance(in DrawRecord record, vec3 position) {
    vec3 eye = vec3(dot(position, record.u_FogRow0.xyz) + record.u_FogRow0.w,
                    dot(position, record.u_FogRow1.xyz) + record.u_FogRow1.w,
                    dot(position, record.u_FogRow2.xyz) + record.u_FogRow2.w);
    int selector = int(record.u_FogParams.w + 0.5);
    return (selector >> 3) == 2 ? abs(eye.z) : length(eye);
}

void main() {
    DrawRecord record = records[gl_InstanceIndex];
    if (record.u_B.z > 0.5) {
        // The GL texture matrix, carried as a 2D affine for the item glint and the renderer's own texture-matrix
        // effects: u' = a*u + c*v + tx, v' = b*u + d*v + ty.
        v_TexCoord = vec2(record.u_A.x * a_TexCoord.x + record.u_A.z * a_TexCoord.y + record.u_B.x,
                record.u_A.y * a_TexCoord.x + record.u_A.w * a_TexCoord.y + record.u_B.y);
    } else {
        v_TexCoord = a_TexCoord;
    }
    // No per-vertex colour in this format: the texture carries the colour and the current glColor tints it, exactly
    // as the fixed pipeline multiplies. The one exception is a combine draw (negative alpha reference), where the
    // colour slots carry the constant the fragment mixes toward - applying it here too would double the tint.
    v_Color = record.u_AlphaColour.x < 0.0 ? vec4(1.0)
            : vec4(record.u_AlphaColour.y, record.u_AlphaColour.z, record.u_AlphaColour.w, record.u_C.x);
    gl_Position = record.u_ProjectionMatrix * vec4(a_Position, 1.0);
    // Mode 0 means "no fog for this batch", and the rows above are then whatever the previous group left in the shared
    // push block, so the coordinate is not computed at all in that case.
    v_FogDistance = (int(record.u_FogParams.w + 0.5) & 7) == 0 ? 0.0 : celtiumFogDistance(record, a_Position);
}
