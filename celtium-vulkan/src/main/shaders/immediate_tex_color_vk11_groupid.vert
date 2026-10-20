#version 460

// gl_DrawID needs GLSL 460 in this toolchain (450 rejects it even with the extension) and the vulkan1.1 target
// environment the _vk11_ marker selects; both are build-time requirements of the per-group data merge.
#extension GL_ARB_shader_draw_parameters : require

// Celtium - the probe that proves gl_DrawID is usable in this build: the per-group data merge draws many groups in one
// command, and gl_DrawID is what tells a group which block of that data is its own. Vulkan 1.1 and the
// shaderDrawParameters feature are the two things it needs, so this shader is compiled at vulkan1.1 (the '_vk11_' in its
// name is the build's marker) and its presence failing means the merge cannot be attempted at all.
//
// Not drawn yet: the pipeline that will consume it is the next step. It is compiled, and keeping it in the build makes
// the toolchain requirement a build error rather than a runtime surprise.

layout(push_constant) uniform PushConstants {
    mat4 u_ProjectionMatrix;
} celtium;

layout(std430, set = 0, binding = 6) readonly buffer CeltiumGroupBlocks {
    mat4 u_ModelView[];
} groupBlocks;

layout(location = 0) in vec3 a_Position;

void main() {
    mat4 modelView = groupBlocks.u_ModelView[gl_DrawID];
    gl_Position = celtium.u_ProjectionMatrix * modelView * vec4(a_Position, 1.0);
}
