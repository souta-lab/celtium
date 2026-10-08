#version 450

layout(binding = 0) uniform sampler2D u_Texture;

layout(location = 0) in vec2 v_Uv;
layout(location = 1) in vec4 v_Color;

layout(location = 0) out vec4 fragColor;

void main() {
    // The UI path multiplies the sampled texel by the vertex colour, exactly as Minecraft's GUI batch does; the
    // light map coordinate the layout carries is not used here.
    fragColor = texture(u_Texture, v_Uv) * v_Color;
}
