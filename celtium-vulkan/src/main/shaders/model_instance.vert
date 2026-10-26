#version 450
#extension GL_EXT_nonuniform_qualifier : require

// Celtium - lit model display lists drawn as instances (VulkanModelInstances): one record per replayed list, read by
// gl_InstanceIndex (the indirect command's firstInstance), the mesh from the shared model pool and the texture by index
// from the shared array. The shading is immediate_tex_color_lit.vert's, term for term; only where the per-draw values
// come from changes (a record instead of a push block).

struct Instance {
    mat4 mvp;           // 0: remapped projection * model view, combined on the CPU as the per-group path does
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
    ivec4 indices;      // 224: x texture, y light map (indices into the shared array)
    vec4 fogParams;     // 240: density, start, end, selector (mode + 8 * shape)
    vec4 fogColor;      // 256: rgb, blend weight
    vec4 fogRow0;       // 272: model-view rows, independent of the clip transform
    vec4 fogRow1;       // 288
    vec4 fogRow2;       // 304
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
layout(location = 6) flat out vec4 v_FogParams;
layout(location = 7) flat out vec4 v_FogColor;

void main() {
    Instance r = instances[gl_InstanceIndex];
    if (r.texB.z > 0.5) {
        v_TexCoord = vec2(r.texA.x * a_TexCoord.x + r.texA.z * a_TexCoord.y + r.texB.x,
                r.texA.y * a_TexCoord.x + r.texA.w * a_TexCoord.y + r.texB.y);
    } else {
        v_TexCoord = a_TexCoord;
    }
    vec3 n = normalize(a_Normal);
    vec3 lit = r.ambient.rgb
            + r.diffuse0.rgb * max(dot(n, r.light0Dir.xyz), 0.0)
            + r.diffuse1.rgb * max(dot(n, r.light1Dir.xyz), 0.0);
    vec3 lightmap = textureLod(textures[nonuniformEXT(r.indices.y)], (r.lightCoord.xy + 8.0) / 256.0, 0.0).rgb;
    v_Color = vec4(a_Color.rgb * lit * lightmap * r.colour.rgb, a_Color.a * r.colour.a);
    v_Texture = r.indices.x;
    v_Combine = r.combine;
    v_AlphaRef = r.texB.w;
    v_FogParams = r.fogParams;
    v_FogColor = r.fogColor;
    int selector = int(r.fogParams.w + 0.5);
    v_FogDistance = 0.0;
    if ((selector & 7) != 0) {
        vec4 position = vec4(a_Position, 1.0);
        vec3 eye = vec3(dot(r.fogRow0, position), dot(r.fogRow1, position), dot(r.fogRow2, position));
        v_FogDistance = (selector >> 3) == 2 ? abs(eye.z) : length(eye);
    }
    gl_Position = r.mvp * vec4(a_Position, 1.0);
}
