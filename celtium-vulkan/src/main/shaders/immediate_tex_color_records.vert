#version 450

// Celtium - the record-reading variant of immediate_tex_color.vert.
//
// The immediate path draws about two hundred groups a frame, and a frame's groups use only 61-69 distinct materials: the
// push block - and with it one vkCmdPushConstants plus one draw - is paid per group even when consecutive groups agree on
// everything except their matrices. This variant takes the same block from a storage buffer instead, indexed by
// gl_InstanceIndex, so a run of groups that agree on material can be recorded as one indirect command per group with the
// record index as its firstInstance - no push per group, and the CPU only writes records.
//
// The struct mirrors the push layout of immediate_tex_color.vert byte for byte: 0 matrix, 64/80/96 the texture affines
// and pad, 112 the alpha reference and the current colour, 128..207 the fog block. The offset comments are the contract
// with the writer (celtium$writeDrawRecord).
struct DrawRecord {
    mat4 u_ProjectionMatrix;   // 0: projection alone - the model view is baked into the positions
    vec4 u_TexA;               // 64
    vec4 u_TexB;               // 80
    vec4 u_TexPad;             // 96
    vec4 u_AlphaColour;        // 112: x alpha reference, yzw the current glColor's rgb
    vec4 u_FogColor;           // 128
    vec4 u_FogParams;          // 144: x density, y start, z end, w mode + 8 * shape
    vec4 u_FogRow0;            // 160
    vec4 u_FogRow1;            // 176
    vec4 u_FogRow2;            // 192
};

layout(std430, set = 0, binding = 2) readonly buffer DrawRecords {
    DrawRecord records[];
};

layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec2 a_TexCoord;
layout(location = 2) in vec4 a_Color;

layout(location = 0) out vec2 v_TexCoord;
layout(location = 1) out vec4 v_Color;
layout(location = 2) out float v_FogDistance;

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
        v_TexCoord = vec2(record.u_TexA.x * a_TexCoord.x + record.u_TexA.z * a_TexCoord.y + record.u_TexB.x,
                record.u_TexA.y * a_TexCoord.x + record.u_TexA.w * a_TexCoord.y + record.u_TexB.y);
    } else {
        v_TexCoord = a_TexCoord;
    }
    v_Color = a_Color;
    v_FogDistance = (int(record.u_FogParams.w + 0.5) & 7) == 0 ? 0.0 : celtiumFogDistance(record, a_Position);
    gl_Position = record.u_ProjectionMatrix * vec4(a_Position, 1.0);
}
