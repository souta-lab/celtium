#version 450

// Celtium - the record-reading form of immediate_texgen.vert (Stage 5 stage 6):
// POSITION_COLOR geometry drawn while GL texture-coordinate generation is on (NativeTexGen):
// the end portal and end gateway. The S, T and Q rows of (texture matrix * generated
// coordinate), re-expressed over this group's vertex positions, sit in the record at
// 64/80/96 - the writer fills the record's texture-affine rows with them for a texgen group,
// exactly as it filled the push block's - and the coordinate is projective, so (s, t, q) is
// interpolated and divided per fragment exactly as the fixed pipeline does. The fog block
// rides the record at 128..207, as in the family's shared layout.

struct DrawRecord {
    mat4 u_ProjectionMatrix;   // 0: projection alone - the model view is baked into the positions
    vec4 u_GenRowS;            // 64
    vec4 u_GenRowT;            // 80
    vec4 u_GenRowQ;            // 96
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
layout(location = 2) in vec4 a_Color;

layout(location = 0) out vec3 v_Gen;
layout(location = 1) out vec4 v_Color;
layout(location = 2) out float v_FogDistance;
// The fog block varies per record, so it reaches the fragment stage as flat outputs, as in
// the family's other record-reading pairs.
layout(location = 3) flat out vec4 v_FogColour;
layout(location = 4) flat out vec4 v_FogParams;

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
    vec4 position = vec4(a_Position, 1.0);
    v_Gen = vec3(dot(record.u_GenRowS, position), dot(record.u_GenRowT, position),
            dot(record.u_GenRowQ, position));
    v_Color = a_Color;
    v_FogColour = record.u_FogColor;
    v_FogParams = record.u_FogParams;
    v_FogDistance = (int(record.u_FogParams.w + 0.5) & 7) == 0 ? 0.0 : celtiumFogDistance(record, a_Position);
    gl_Position = record.u_ProjectionMatrix * position;
}
