#version 450
// bits.fsh: mosaic quantisation + saturation, the creeper view's second pass.
layout(location = 0) in vec2 texCoord;
layout(location = 1) in vec2 oneTexel;
layout(location = 0) out vec4 o_Color;

layout(set = 0, binding = 0) uniform sampler2D DiffuseSampler;

layout(push_constant) uniform Push {
    mat4 ProjMat;   // @0
    vec2 InSize;    // @64
    vec2 OutSize;   // @72
    float Time;     // @80
    float Resolution; // @88
    float Saturation; // @92
    float MosaicSize; // @96
} celtium;

void main() {
    vec2 mosaicInSize = celtium.InSize / celtium.MosaicSize;
    vec2 fractPix = fract(texCoord * mosaicInSize) / mosaicInSize;

    vec4 baseTexel = texture(DiffuseSampler, texCoord - fractPix);

    vec3 fractTexel = baseTexel.rgb - fract(baseTexel.rgb * celtium.Resolution) / celtium.Resolution;
    float luma = dot(fractTexel, vec3(0.3, 0.59, 0.11));
    vec3 chroma = (fractTexel - luma) * celtium.Saturation;
    baseTexel.rgb = luma + chroma;
    baseTexel.a = 1.0;

    o_Color = baseTexel;
}
