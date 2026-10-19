#version 450

layout(set = 0, binding = 0) uniform sampler2D u_Texture;
layout(set = 0, binding = 1) uniform sampler2D u_Lightmap;

// The native shadow map, sampled exactly as the terrain shader samples it.
layout(set = 0, binding = 3) uniform sampler2D u_ShadowMap;
layout(location = 3) in vec4 v_ShadowCoord;
layout(constant_id = 0) const bool CELTIUM_SHADOW_ENABLED = false;

layout(location = 0) in vec4 v_Color;
layout(location = 1) in vec2 v_TexCoord;
layout(location = 2) in vec2 v_Lightmap;

layout(location = 0) out vec4 fragColor;

// The lightmap coordinates arrive as unsigned shorts whose upper 8 bits are the coordinate and whose lower 8 bits
// carry the blend weight, which is how Minecraft packs them. The lightmap image is 16x16 with NEAREST sampling, so
// sampling its centre for each texel reproduces what GL does with the same coordinates.
layout(push_constant) uniform PushConstants {
    mat4 u_ProjectionMatrix;
    // The model view is affine, so only its three rotation/translation columns are passed and the shader rebuilds the
    // fourth. With the alpha reference at 112 the whole block is 116 bytes - inside the 128 Vulkan guarantees (S19) -
    // and it is declared identically in both stages, which is what the driver requires.
    vec4 u_MvColumn0;
    vec4 u_MvColumn1;
    vec4 u_MvColumn2;
    layout(offset = 112) float u_AlphaRef;
    layout(offset = 116) float u_ColorR;
    layout(offset = 120) float u_ColorG;
    layout(offset = 124) float u_ColorB;
} celtium;

void main() {

    vec4 light = texture(u_Lightmap, v_Lightmap);
    vec4 colour = texture(u_Texture, v_TexCoord) * v_Color * light;
    colour.a = texture(u_Texture, v_TexCoord).a * v_Color.a;
    if (celtium.u_AlphaRef > 0.0 && colour.a < celtium.u_AlphaRef) {
        discard;
    }
    if (CELTIUM_SHADOW_ENABLED) {
        vec3 shadowCoord = v_ShadowCoord.xyz / v_ShadowCoord.w;
        vec3 shadowDx = dFdx(shadowCoord);
        vec3 shadowDy = dFdy(shadowCoord);
        float slopeTerm = abs(normalize(cross(shadowDx, shadowDy)).z);
        if (all(greaterThanEqual(shadowCoord, vec3(0.0))) && all(lessThanEqual(shadowCoord, vec3(1.0)))) {
            float bias = 0.0008 + 0.002 * (1.0 - slopeTerm);
            float lit = 0.0;
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    vec2 offset = vec2(float(x), float(y)) * (2.0 / 2048.0);
                    float depth = texture(u_ShadowMap, shadowCoord.xy + offset).r;
                    lit += shadowCoord.z - bias > depth ? 0.0 : 1.0;
                }
            }
            colour.rgb *= mix(0.2, 1.0, lit / 9.0);
        }
    }
    fragColor = colour;
}
