#version 450
#extension GL_EXT_nonuniform_qualifier : require

// Native model poses are per-instance. Projection and fog are immutable for a run and come from
// its push block, so projection * model-view is evaluated here rather than rebuilt on the CPU.
layout(push_constant) uniform RunConstants {
    mat4 projection;
    vec4 fogColor;
    vec4 fogParams;
} runState;

struct Instance {
    mat4 modelView;     // 0: captured pose, in eye space
    vec4 colour;        // 64: the current glColor (1 when none), which the lit vertex colour is scaled by
    vec4 combine;       // 80: rgb the texture-env interpolate constant, w its amount (0 when no combine)
    vec4 texA;          // 96: texture affine a, b, c, d
    vec4 texB;          // 112: tx, ty, enabled (> 0.5), alpha reference (GREATER; 0 = off)
    vec4 light0Dir;     // 128: model space
    vec4 light1Dir;     // 144
    vec4 ambient;       // 160
    vec4 diffuse0;      // 176
    vec4 diffuse1;      // 192
    vec4 lightCoord;    // 208: xy the light-map coordinate
    ivec4 indices;      // 224: texture, light map, alpha comparison, lighting enabled
    vec4 pad;           // 240
};

layout(std430, set = 1, binding = 0) readonly buffer Instances {
    Instance instances[];
};

layout(set = 0, binding = 0) uniform sampler2D textures[];

layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec2 a_TexCoord;
layout(location = 2) in vec4 a_Color;
layout(location = 3) in vec3 a_Normal;

layout(location = 0) out vec2 v_TexCoord;
layout(location = 1) out vec4 v_Color;
layout(location = 2) flat out int v_Texture;
layout(location = 3) flat out vec4 v_Combine;
layout(location = 4) flat out float v_AlphaRef;
layout(location = 5) out float v_FogDistance;
layout(location = 6) flat out int v_AlphaFunction;

void main() {
    Instance r = instances[gl_InstanceIndex];
    if (r.texB.z > 0.5) {
        v_TexCoord = vec2(r.texA.x * a_TexCoord.x + r.texA.z * a_TexCoord.y + r.texB.x,
                r.texA.y * a_TexCoord.x + r.texA.w * a_TexCoord.y + r.texB.y);
    } else {
        v_TexCoord = a_TexCoord;
    }
    vec3 rgb = a_Color.rgb * r.colour.rgb;
    if (r.indices.w != 0) {
        vec3 n = normalize(a_Normal);
        vec3 lit = r.ambient.rgb
                + r.diffuse0.rgb * max(dot(n, r.light0Dir.xyz), 0.0)
                + r.diffuse1.rgb * max(dot(n, r.light1Dir.xyz), 0.0);
        vec3 lightmap = textureLod(textures[nonuniformEXT(r.indices.y)], (r.lightCoord.xy + 8.0) / 256.0, 0.0).rgb;
        // GL clamps the lit primary colour to [0,1] before the texture stages: material * (ambient + both diffuse terms)
        // exceeds 1 on a face both lights reach (0.4 + 0.6 + 0.6 at most), and unclamped it drew the first-person arm
        // x1.16 brighter than Celeritas. The light map modulates after the clamp, as unit 1 does.
        rgb = clamp(rgb * lit, 0.0, 1.0) * lightmap;
    }
    v_Color = vec4(rgb, a_Color.a * r.colour.a);
    v_AlphaFunction = r.indices.z;
    v_Texture = r.indices.x;
    v_Combine = r.combine;
    v_AlphaRef = r.texB.w;
    vec4 eye = r.modelView * vec4(a_Position, 1.0);
    int selector = int(runState.fogParams.w + 0.5);
    v_FogDistance = (selector & 7) == 0 ? 0.0 : (selector >> 3) == 2 ? abs(eye.z) : length(eye.xyz);
    gl_Position = runState.projection * eye;
}
