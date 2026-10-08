#version 450
layout(push_constant) uniform Parameters {
    float depth;
    vec4 color;
} parameters;
layout(location = 0) out vec4 outputColor;
void main() {
    outputColor = parameters.color;
}
