#version 450
// blur.fsh: directional box blur along BlurDir at one-texel steps, keeping the summed alpha.
layout(location = 0) in vec2 texCoord;
layout(location = 1) in vec2 oneTexel;
layout(location = 0) out vec4 o_Color;

layout(set = 0, binding = 0) uniform sampler2D DiffuseSampler;

layout(push_constant) uniform Push {
    mat4 ProjMat;   // @0
    vec2 InSize;    // @64
    vec2 OutSize;   // @72
    float Time;     // @80
    vec2 BlurDir;   // @88
    float Radius;   // @96
} celtium;

void main() {
    vec4 blurred = vec4(0.0);
    float totalStrength = 0.0;
    float totalAlpha = 0.0;
    float totalSamples = 0.0;
    for (float r = -celtium.Radius; r <= celtium.Radius; r += 1.0) {
        vec4 sample_ = texture(DiffuseSampler, texCoord + oneTexel * r * celtium.BlurDir);
        totalAlpha = totalAlpha + sample_.a;
        totalSamples = totalSamples + 1.0;
        float strength = 1.0 - abs(r / celtium.Radius);
        totalStrength = totalStrength + strength;
        blurred = blurred + sample_;
    }
    o_Color = vec4(blurred.rgb / (celtium.Radius * 2.0 + 1.0), totalAlpha);
}
