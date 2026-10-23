#version 450

// Celtium - immediate-mode geometry with no texture and no vertex colour: POSITION, twelve bytes, the layout
// RenderGlobal builds its sky dome, its stars and its horizon plane in (DefaultVertexFormats.POSITION) and the one the
// GL-less stub decodes out of glDrawArrays. The colour arrives in the push constant block, because the format has no
// colour element.

// Celtium - the record-reading form of immediate_position.vert: the same 208-byte block, taken from the draw-record storage buffer at
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

layout(location = 0) in vec3 a_Position;

layout(location = 0) out vec4 v_Color;
layout(location = 1) out float v_FogDistance;

/**
 * The fog coordinate, in eye space: {@code M * position} for this batch's own model view.
 * <p>
 * The three rows above are the affine model view the batch was offered under, so this is exact both for camera-space
 * geometry (the sky dome, whose model view is the camera rotation alone) and for object-space geometry (a TESR drawing
 * a model at its block's translation). The fixed pipeline computes its fog from the same transformed position.
 * Planar fog - the eye-space depth - is the OpenGL default; radial is what {@code EntityRenderer.setupFog} asks for
 * with {@code GL_NV_fog_distance}, and what the terrain path in this renderer already uses.
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
    // The combined projection-times-model-view arrives in u_ProjectionMatrix, so this shader multiplies once. It used to
    // rebuild the model view from the three columns with a hardcoded fourth; when the writer started sending the product and
    // zeroing the columns, that rebuild became a zero matrix and every position-only vertex collapsed to the translation point.
    gl_Position = record.u_ProjectionMatrix * vec4(a_Position, 1.0);
    v_Color = vec4(record.u_AlphaColour.y, record.u_AlphaColour.z, record.u_AlphaColour.w, record.u_C.x);
    // Mode 0 means "no fog for this batch", and the rows above are then whatever the previous group left in the shared
    // push block, so the coordinate is not computed at all in that case.
    v_FogDistance = (int(record.u_FogParams.w + 0.5) & 7) == 0 ? 0.0 : celtiumFogDistance(record, a_Position);
}
