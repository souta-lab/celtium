#version 450
// entity_sobel.fsh: silhouette edge strength from alpha differences, glow-outline chain's first pass.
layout(location = 0) in vec2 texCoord;
layout(location = 1) in vec2 oneTexel;
layout(location = 0) out vec4 o_Color;

layout(set = 0, binding = 0) uniform sampler2D DiffuseSampler;

layout(push_constant) uniform Push {
    mat4 ProjMat;   // @0
    vec2 InSize;    // @64
    vec2 OutSize;   // @72
    float Time;     // @80
} celtium;

void main() {
    vec4 center = texture(DiffuseSampler, texCoord);
    vec4 left = texture(DiffuseSampler, texCoord - vec2(oneTexel.x, 0.0));
    vec4 right = texture(DiffuseSampler, texCoord + vec2(oneTexel.x, 0.0));
    vec4 up = texture(DiffuseSampler, texCoord - vec2(0.0, oneTexel.y));
    vec4 down = texture(DiffuseSampler, texCoord + vec2(0.0, oneTexel.y));
    float leftDiff = abs(center.a - left.a);
    float rightDiff = abs(center.a - right.a);
    float upDiff = abs(center.a - up.a);
    float downDiff = abs(center.a - down.a);
    float total = clamp(leftDiff + rightDiff + upDiff + downDiff, 0.0, 1.0);
    vec3 outColor = center.rgb * center.a + left.rgb * left.a + right.rgb * right.a
            + up.rgb * up.a + down.rgb * down.a;
    o_Color = vec4(outColor * 0.2, total);
}
