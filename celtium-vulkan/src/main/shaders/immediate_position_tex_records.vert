#version 450
// Record-reading form of immediate_position_tex.vert: projection, texture affine, colour alpha (pushed at 96 in
// this layout), rgb (116..124) and the fog block all come from the record.

struct DrawRecord {
    mat4 u_ProjectionMatrix;
    vec4 u_TexA;
    vec4 u_TexB;
    vec4 u_TexPad;
    vec4 u_AlphaColour;
    vec4 u_FogColor;
    vec4 u_FogParams;
    vec4 u_FogRow0;
    vec4 u_FogRow1;
    vec4 u_FogRow2;
    vec4 u_RecordPad;
};
layout(std430, set = 1, binding = 0) readonly buffer DrawRecords { DrawRecord records[]; };

layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec2 a_TexCoord;
layout(location = 0) out vec2 v_TexCoord;
layout(location = 1) out vec4 v_Color;
layout(location = 2) out float v_FogDistance;
layout(location = 3) flat out vec4 v_Matte;
layout(location = 4) flat out vec4 v_FogColour;
layout(location = 5) flat out vec4 v_FogParams;

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
    v_Color = vec4(record.u_AlphaColour.yzw, record.u_TexPad.x);
    v_Matte = record.u_AlphaColour;
    v_FogColour = record.u_FogColor;
    v_FogParams = record.u_FogParams;
    v_FogDistance = (int(record.u_FogParams.w + 0.5) & 7) == 0 ? 0.0 : celtiumFogDistance(record, a_Position);
    gl_Position = record.u_ProjectionMatrix * vec4(a_Position, 1.0);
}
