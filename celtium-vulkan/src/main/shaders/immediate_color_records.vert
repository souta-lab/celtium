#version 450

// Celtium - immediate-mode geometry (Forge API swap): untextured POSITION_COLOR vertices, the format
// DefaultVertexFormats.POSITION_COLOR declares (3 floats position, 4 unsigned bytes colour).
// Push constants follow the same convention as the terrain and UI pipelines: projection at 0, model view at 64.

// Celtium - the record-reading form of immediate_color.vert: the POSITION_COLOR family, which is where the sky's
// colour batches and the colour-only fills arrive. The same 208-byte block comes from a draw-record storage buffer
// indexed by gl_InstanceIndex; this family samples nothing, so the records live at set 0 and the layout declares no
// other set. The array's stride is 224, which is what the trailing pad is for.
struct DrawRecord {
    mat4 u_ProjectionMatrix;   // 0
    vec4 u_MvColumn0;          // 64: dead model-view column
    vec4 u_MvColumn1;          // 80
    vec4 u_MvColumn2;          // 96
    vec4 u_AlphaColour;        // 112: x alpha reference, yzw the current glColor's rgb
    vec4 u_FogColor;           // 128
    vec4 u_FogParams;          // 144
    vec4 u_FogRow0;            // 160
    vec4 u_FogRow1;            // 176
    vec4 u_FogRow2;            // 192
    vec4 u_RecordPad;          // 208: pad to the 224-byte stride
};

layout(std430, set = 0, binding = 0) readonly buffer DrawRecords {
    DrawRecord records[];
};

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
float celtiumFogDistance(in DrawRecord record, vec3 position) {
    vec3 eye = vec3(dot(position, record.u_FogRow0.xyz) + record.u_FogRow0.w,
                    dot(position, record.u_FogRow1.xyz) + record.u_FogRow1.w,
                    dot(position, record.u_FogRow2.xyz) + record.u_FogRow2.w);
    int selector = int(record.u_FogParams.w + 0.5);
    return (selector >> 3) == 2 ? abs(eye.z) : length(eye);
}

void main() {
    DrawRecord record = records[gl_InstanceIndex];
    v_Color = a_Color;
    gl_Position = record.u_ProjectionMatrix * vec4(a_Position, 1.0);
    // Mode 0 means "no fog for this batch", and the rows above are then whatever the previous group left in the shared
    // push block, so the coordinate is not computed at all in that case.
    v_FogDistance = (int(record.u_FogParams.w + 0.5) & 7) == 0 ? 0.0 : celtiumFogDistance(record, a_Position);
}
