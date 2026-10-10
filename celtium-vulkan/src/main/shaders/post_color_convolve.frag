#version 450
// color_convolve.fsh: colour matrix + offset/scale + saturation (creeper/desaturate chains).
// 180 bytes of push - the executor only uses this program when maxPushConstantsSize allows.
layout(location = 0) in vec2 texCoord;
layout(location = 1) in vec2 oneTexel;
layout(location = 0) out vec4 o_Color;

layout(set = 0, binding = 0) uniform sampler2D DiffuseSampler;

layout(push_constant) uniform Push {
    mat4 ProjMat;   // @0
    vec2 InSize;    // @64
    vec2 OutSize;   // @72
    float Time;     // @80
    vec3 Gray;       // @96
    vec3 RedMatrix;  // @112
    vec3 GreenMatrix;// @128
    vec3 BlueMatrix; // @144
    vec3 Offset;     // @160
    vec3 ColorScale; // @176
    float Saturation;// @192
} celtium;

void main() {
    vec4 texel = texture(DiffuseSampler, texCoord);

    float red = dot(texel.rgb, celtium.RedMatrix);
    float green = dot(texel.rgb, celtium.GreenMatrix);
    float blue = dot(texel.rgb, celtium.BlueMatrix);
    vec3 outColor = vec3(red, green, blue);

    outColor = (outColor * celtium.ColorScale) + celtium.Offset;

    float luma = dot(outColor, celtium.Gray);
    vec3 chroma = outColor - luma;
    outColor = (chroma * celtium.Saturation) + luma;

    o_Color = vec4(outColor, 1.0);
}
