package org.soutalab.celtium.vulkan.terrain.lane1;

/**
 * The Lane 1 terrain shaders (design V040-FINAL-PLAN section 2.1): the GPU frustum cull,
 * the Hi-Z pyramid build, and the vertex-pulling draw pair (K1). The draw shaders unpack the
 * 16 B {@code PackedAxisQuad} and 80 B {@code GeneralQuad} directly from storage buffers.
 * The dynamic-light evaluation walks the cluster
 * mask with {@code findLSB} (V2-5), so per-vertex light cost tracks local density.
 * <p>
 * These are the lane's built-in (pack-OFF) programs and the cull machinery the pack form reuses;
 * a pack's own programs replace the draw pair through the S5 translation, never the cull pair.
 * Buffer layouts are the core contracts verbatim: {@code SectionDesc} at 48 B std430,
 * {@code PackedAxisQuad} as one {@code uvec4}, the light UBO at 2 KiB and the cluster masks at
 * 16 KiB as {@link LightGridBuilder} lays them out.
 */
public final class Lane1Shaders {

    private Lane1Shaders() {
    }

    /** Shared header: the descriptor and light-grid layouts every Lane 1 shader agrees on. */
    private static final String COMMON = """
            struct SectionDesc {
                ivec3 section;
                int flags;
                uint64_t axisOffset;
                uint64_t generalOffset;
                int axisCount;
                int generalCount;
                int lightBase;
                int mcEntityBase;
            };
            vec3 relativeSectionOrigin(ivec3 section, ivec3 cameraSection, vec3 cameraFrac) {
                return vec3(section - cameraSection) * 16.0 - cameraFrac;
            }
            struct LightRecord {
                vec3 position;
                float radius;
                vec3 color;
                float intensity;
            };
            """;

    /** Frustum cull: one invocation per section, emitting VkDrawIndirectCommand records. */
    public static final String CULL_COMPUTE = "#version 450\n"
            + "#extension GL_EXT_shader_explicit_arithmetic_types_int64 : require\n"
            + COMMON + """
            layout(local_size_x = 64) in;
            layout(std430, set = 0, binding = 0) readonly buffer Descs { SectionDesc descs[]; };
            layout(std430, set = 0, binding = 1) writeonly buffer Commands { uint commands[]; };
            layout(std430, set = 0, binding = 2) buffer Counters { uint counts[8]; };
            layout(set = 0, binding = 3) uniform sampler2D hiZ;
            layout(std430, set = 0, binding = 4) readonly buffer Ranges { uint ranges[]; };
            layout(push_constant) uniform CullPush {
                vec4 planes[6];
                ivec3 cameraSection; // byte 96
                uint sectionCount;   // byte 108
                vec3 cameraFrac;     // byte 112
                uint regionStride;   // byte 124
            } pc;
            bool frustumVisible(vec3 mn, vec3 mx) {
                for (int i = 0; i < 6; i++) {
                    vec3 p = vec3(pc.planes[i].x >= 0.0 ? mx.x : mn.x,
                                  pc.planes[i].y >= 0.0 ? mx.y : mn.y,
                                  pc.planes[i].z >= 0.0 ? mx.z : mn.z);
                    if (dot(pc.planes[i].xyz, p) + pc.planes[i].w < 0.0) return false;
                }
                return true;
            }
            void emitCommand(uint region, uint quadCount, uint firstQuad, uint slot) {
                if (quadCount == 0u) return;
                uint index = atomicAdd(counts[region], 1u);
                uint base = (region * pc.regionStride + index) * 4u;
                commands[base + 0u] = quadCount * 6u;
                commands[base + 1u] = 1u;
                commands[base + 2u] = firstQuad * 6u;
                commands[base + 3u] = slot;
            }
            void main() {
                uint slot = gl_GlobalInvocationID.x;
                if (slot >= pc.sectionCount) return;
                SectionDesc d = descs[slot];
                vec3 mn = relativeSectionOrigin(d.section, pc.cameraSection, pc.cameraFrac.xyz);
                vec3 mx = mn + 16.0;
                if (!frustumVisible(mn, mx)) return;
                // Hi-Z occlusion against the previous frame's pyramid is sampled by the caller-side
                // variant of this shader once the pyramid exists (K3 gate); the frustum result is
                // the conservative floor and never culls more than the pyramid would.
                atomicAdd(counts[6], 1u);
                for (uint layer = 0u; layer < 3u; layer++) {
                    uint range = slot * 12u + layer * 4u;
                    emitCommand(layer * 2u, ranges[range + 1u],
                            uint(d.axisOffset / 16u) + ranges[range], slot);
                    emitCommand(layer * 2u + 1u, ranges[range + 3u],
                            uint(d.generalOffset / 80u) + ranges[range + 2u], slot);
                }
            }
            """;

    /** Full-resolution depth seed followed by MAX reduction for each mip. */
    public static final String HIZ_COMPUTE = "#version 450\n" + """
            layout(local_size_x = 8, local_size_y = 8) in;
            layout(set = 0, binding = 0) uniform sampler2D sourceDepth;
            layout(r32f, set = 0, binding = 1) writeonly uniform image2D targetLevel;
            layout(push_constant) uniform HiZPush { int level; } pc;
            void main() {
                ivec2 p = ivec2(gl_GlobalInvocationID.xy);
                ivec2 size = imageSize(targetLevel);
                if (any(greaterThanEqual(p, size))) return;
                int sourceLevel = max(0, pc.level - 1);
                ivec2 sourceSize = textureSize(sourceDepth, sourceLevel);
                ivec2 lo = p * sourceSize / size;
                ivec2 hi = (p + 1) * sourceSize / size;
                // Include the final row/column when a source dimension is odd.
                float d = 0.0;
                for (int y = lo.y; y < hi.y; y++) {
                    for (int x = lo.x; x < hi.x; x++) {
                        d = max(d, texelFetch(sourceDepth, ivec2(x, y), sourceLevel).r);
                    }
                }
                imageStore(targetLevel, p, vec4(d));
            }
            """;

    /** Vertex-pulling axis draw: unpacks one PackedAxisQuad per six vertices. */
    public static final String AXIS_VERTEX = "#version 450\n"
            + "#extension GL_EXT_shader_explicit_arithmetic_types_int64 : require\n"
            + COMMON + """
            layout(std430, set = 0, binding = 0) readonly buffer Descs { SectionDesc descs[]; };
            layout(std430, set = 0, binding = 1) readonly buffer AxisQuads { uvec4 quads[]; };
            layout(std430, set = 0, binding = 2) readonly buffer AuxColumn { uint auxWords[]; };
            layout(std430, set = 0, binding = 4) readonly buffer LightUbo { LightRecord lights[]; };
            layout(std430, set = 0, binding = 5) readonly buffer ClusterMasks { uint64_t masks[]; };
            layout(push_constant) uniform DrawPush {
                mat4 viewProj;
                ivec4 cameraSection;
                ivec4 gridOrigin;
                int lightCount;
                float atlasCellsAcross;
                int textureCellCount;
                int pushPad2;
                vec4 cameraFrac;
            } pc;
            layout(location = 0) out vec2 fragCell;
            layout(location = 1) out vec4 fragColor;
            layout(location = 2) out vec2 fragLightMap;
            layout(location = 3) out vec3 fragWorldPos;
            layout(location = 4) out vec3 fragDynamicLight;
            layout(location = 5) out vec2 fragLocalUv;
            const vec2 CORNERS[6] = vec2[](vec2(0,0), vec2(1,0), vec2(0,1), vec2(0,1), vec2(1,0), vec2(1,1));
            vec3 evaluateLights(vec3 worldPos) {
                ivec3 cell = ivec3(floor((worldPos - vec3(pc.gridOrigin.xyz)) / 16.0));
                if (any(lessThan(cell, ivec3(0))) || cell.x >= 16 || cell.y >= 8 || cell.z >= 16) return vec3(0.0);
                uint64_t mask = masks[(cell.y * 16 + cell.z) * 16 + cell.x];
                vec3 sum = vec3(0.0);
                while (mask != 0ul) {
                    int bit = int(findLSB(mask));
                    mask &= mask - 1ul;
                    LightRecord light = lights[bit];
                    float distance = length(light.position - worldPos);
                    float attenuation = clamp(1.0 - distance / light.radius, 0.0, 1.0);
                    sum += light.color * (light.intensity * attenuation * attenuation);
                }
                return sum;
            }
            void main() {
                uint quadIndex = uint(gl_VertexIndex) / 6u;
                uvec4 q = quads[quadIndex];
                vec2 corner = CORNERS[gl_VertexIndex % 6];
                SectionDesc d = descs[gl_InstanceIndex];
                vec3 minCorner = vec3(float(q.x & 63u), float((q.x >> 6) & 63u), float((q.x >> 12) & 63u));
                uint axis = (q.x >> 18) & 3u;
                // Winding consistency for back-face culling (transmission item 14): the fixed
                // (U, V) parametrization winds CCW seen from the -X / -Y / +Z side, so a quad
                // whose outward side is the opposite one gets its U mirrored - position and
                // UV derive from the same corner, so the rendered image is unchanged and only
                // the triangle order flips. word0 bit 20 is PackedAxisQuad.negativeFacing.
                bool negativeFacing = ((q.x >> 20) & 1u) != 0u;
                if (negativeFacing == (axis == 2u)) {
                    corner.x = 1.0 - corner.x;
                }
                uint extentU = (q.y & 63u) + 1u;
                uint extentV = ((q.y >> 6) & 63u) + 1u;
                uint textureIndex = q.y >> 12;
                vec3 local = minCorner;
                if (axis == 0u) { local.z += corner.x * float(extentU); local.y += corner.y * float(extentV); }
                else if (axis == 1u) { local.x += corner.x * float(extentU); local.z += corner.y * float(extentV); }
                else { local.x += corner.x * float(extentU); local.y += corner.y * float(extentV); }
                vec3 worldPos = vec3(d.section) * 16.0 + local;
                vec3 relative = relativeSectionOrigin(d.section, pc.cameraSection.xyz, pc.cameraFrac.xyz) + local;
                gl_Position = pc.viewProj * vec4(relative, 1.0);
                float cellU = float(q.z & 255u);
                float cellV = float((q.z >> 8) & 255u);
                fragCell = vec2(cellU, cellV);
                fragLocalUv = corner * vec2(float(extentU), float(extentV));
                float shade = float((q.z >> 24) & 255u) / 255.0;
                vec3 tint = ((q.w & (1u << 24)) != 0u)
                        ? vec3(float(q.w & 255u), float((q.w >> 8) & 255u), float((q.w >> 16) & 255u)) / 255.0
                        : vec3(1.0);
                // Per-corner smooth light (R-107): the corner-light column in the auxiliary
                // slab (uint index d.lightBase, two words per quad in section-local axis
                // order) carries per corner the sums of vanilla's four light samples and the
                // AO step - the averaged lightmap coordinate is sum/60, the AO multiplier is
                // step/15. The slot follows the post-mirror corner above, i.e. the vertex's
                // actual position on the face. (word2's per-quad light stays in the record
                // for the pack path, which computes its own per-vertex light.)
                uint localQuad = quadIndex - uint(d.axisOffset / 16u);
                uint slot = uint(corner.x) + 2u * uint(corner.y);
                uint lightWord = auxWords[uint(d.lightBase) + localQuad * 2u + (slot >> 1u)];
                uint field = (lightWord >> ((slot & 1u) * 16u)) & 0xFFFFu;
                float aoFactor = float((field >> 12) & 15u) / 15.0;
                fragColor = vec4(tint * shade * aoFactor, 1.0);
                fragLightMap = vec2(float((field >> 6) & 63u), float(field & 63u)) / 60.0;
                fragWorldPos = worldPos;
                // V2-5: the vertex stage evaluates the cluster's dynamic lights (findLSB walk).
                fragDynamicLight = evaluateLights(worldPos + vec3(0.5));
            }
            """;

    /** Vertex-pulling general draw: reads the 80 B GeneralQuad stream. */
    public static final String GENERAL_VERTEX = "#version 450\n"
            + "#extension GL_EXT_shader_explicit_arithmetic_types_int64 : require\n"
            + COMMON + """
            layout(std430, set = 0, binding = 0) readonly buffer Descs { SectionDesc descs[]; };
            // Flat word stream, 20 words per GeneralQuad (12 position floats, then
            // textureIndex / lightWord / normalPacked / flags as bit-cast ints). A struct
            // array member cannot be indexed dynamically without GL_EXT_nonuniform_qualifier,
            // so the shader reads the same 80 B record through a flat float array instead.
            layout(std430, set = 0, binding = 1) readonly buffer GeneralQuads { float generalWords[]; };
            layout(std430, set = 0, binding = 2) readonly buffer AuxColumn { uint auxWords[]; };
            // Per-sprite atlas rectangles (uvMin.xy, uvSize.zw), in the same index space as
            // the cell table: the consumer-side restore of ruling 5.4-2 - no fixed cell size
            // is assumed, so 32x32 / 64x64 / HD sprites keep their real extent.
            layout(std430, set = 0, binding = 9) readonly buffer TextureRects { vec4 textureRects[]; };
            layout(push_constant) uniform DrawPush {
                mat4 viewProj;
                ivec4 cameraSection;
                ivec4 gridOrigin;
                int lightCount;
                float atlasCellsAcross;
                int textureCellCount;
                int pushPad2;
                vec4 cameraFrac;
            } pc;
            layout(location = 0) out vec2 fragAtlasUv;
            layout(location = 1) out vec4 fragColor;
            layout(location = 2) out vec2 fragLightMap;
            layout(location = 3) out vec3 fragWorldPos;
            layout(location = 4) out vec3 fragDynamicLight;
            const int CORNER_INDEX[6] = int[](0, 1, 2, 0, 2, 3);
            void main() {
                uint quadIndex = uint(gl_VertexIndex) / 6u;
                int corner = CORNER_INDEX[gl_VertexIndex % 6];
                SectionDesc d = descs[gl_InstanceIndex];
                uint wordBase = quadIndex * 20u;
                vec3 local = vec3(generalWords[wordBase + uint(corner * 3)],
                                  generalWords[wordBase + uint(corner * 3) + 1u],
                                  generalWords[wordBase + uint(corner * 3) + 2u]);
                vec3 worldPos = vec3(d.section) * 16.0 + local;
                vec3 relative = relativeSectionOrigin(d.section, pc.cameraSection.xyz, pc.cameraFrac.xyz) + local;
                gl_Position = pc.viewProj * vec4(relative, 1.0);
                // The 80 B record's per-corner sprite-local UVs (words 16..19, Q1.15 packed:
                // u in bits 0..15, v in bits 16..31) composed with the sprite's real atlas
                // rectangle: atlasUv = uvMin + localUv * uvSize (UV ruling 5.4-2 / 5.4-3).
                // Out-of-range indices fall back to entry 0, the first cell's rectangle,
                // mirroring the cell table's fallback.
                int textureIndex = floatBitsToInt(generalWords[wordBase + 12u]);
                vec4 rect = textureRects[0];
                if (textureIndex > 0 && textureIndex < pc.textureCellCount) {
                    rect = textureRects[textureIndex];
                }
                int uvBits = floatBitsToInt(generalWords[wordBase + 16u + uint(corner)]);
                vec2 localUv = vec2(float(uvBits & 0xFFFF), float((uvBits >> 16) & 0xFFFF)) / 32768.0;
                fragAtlasUv = rect.xy + localUv * rect.zw;
                int flags = floatBitsToInt(generalWords[wordBase + 15u]);
                vec3 tint = ((flags & 1) != 0)
                        ? vec3(float((flags >> 8) & 255), float((flags >> 16) & 255), float((flags >> 24) & 255)) / 255.0
                        : vec3(1.0);
                // Per-corner smooth light (R-107): in the section's corner-light column the
                // general quads' words follow all axis quads; the slot is the model corner
                // index itself. Sums and AO step as in the axis shader (sum/60, step/15).
                // (The record's per-quad lightWord stays for the pack path.)
                uint localQuad = quadIndex - uint(d.generalOffset / 80u);
                uint gslot = uint(corner);
                uint glightWord = auxWords[uint(d.lightBase)
                        + (uint(d.axisCount) + localQuad) * 2u + (gslot >> 1u)];
                uint gfield = (glightWord >> ((gslot & 1u) * 16u)) & 0xFFFFu;
                float aoFactor = float((gfield >> 12) & 15u) / 15.0;
                fragColor = vec4(tint * aoFactor, 1.0);
                fragLightMap = vec2(float((gfield >> 6) & 63u), float(gfield & 63u)) / 60.0;
                fragWorldPos = worldPos;
                fragDynamicLight = vec3(0.0);
            }
            """;

    /** The shared terrain fragment stage for the built-in (pack-OFF) programs. */
    public static final String TERRAIN_FRAGMENT = "#version 450\n" + """
            layout(set = 0, binding = 6) uniform sampler2D atlas;
            layout(set = 0, binding = 7) uniform sampler2D lightMap;
            layout(set = 0, binding = 8) uniform FogBlock {
                vec4 fogColor;
                vec3 fogCameraPos;
                float fogStart;
                float fogEnd;
                float fogDensity;
                int fogMode;
                float alphaCutoff;
                float atlasCellsAcross;
            } fog;
            layout(location = 0) in vec2 fragCell;
            layout(location = 5) in vec2 fragLocalUv;
            layout(location = 1) in vec4 fragColor;
            layout(location = 2) in vec2 fragLightMap;
            layout(location = 3) in vec3 fragWorldPos;
            layout(location = 4) in vec3 fragDynamicLight;
            layout(location = 0) out vec4 outColor;
            void main() {
                // Per-block tiling: the cell selects the sprite, the face-local coordinate wraps
                // inside it (a greedy-merged face repeats its sprite once per block).
                vec2 uv = (fragCell + fract(fragLocalUv)) / max(fog.atlasCellsAcross, 1.0);
                vec4 texel = texture(atlas, uv);
                if (texel.a < fog.alphaCutoff) discard;
                // The lightmap is a 16x16 texel grid with u = block light, v = sky light
                // (measured at Gate G1 with a self-calibrating ramp displayed through this
                // very sample path). fragLightMap carries (block, sky); sample texel
                // centres - the raw light/15 coordinates land on the grid's dark edges.
                vec3 light = texture(lightMap, (fragLightMap * 15.0 + 0.5) / 16.0).rgb;
                vec3 rgb = texel.rgb * fragColor.rgb * (light + fragDynamicLight);

                if (fog.fogMode != 0) {
                    float dist = length(fragWorldPos - fog.fogCameraPos);
                    float factor;
                    if (fog.fogMode == 1) {
                        factor = clamp((dist - fog.fogStart) / max(fog.fogEnd - fog.fogStart, 0.001), 0.0, 1.0);
                    } else if (fog.fogMode == 2) {
                        factor = 1.0 - exp(-fog.fogDensity * dist);
                    } else {
                        float d = fog.fogDensity * dist;
                        factor = 1.0 - exp(-d * d);
                    }
                    rgb = mix(rgb, fog.fogColor.rgb, clamp(factor, 0.0, 1.0));
                }
                outColor = vec4(rgb, texel.a * fragColor.a);
            }
            """;

    /**
     * The general stream's fragment stage: identical to {@link #TERRAIN_FRAGMENT} except the
     * atlas UV arrives fully composed from the vertex stage (per-corner sprite-local UVs
     * restored through the sprite rectangle table), so there is no cell arithmetic and no
     * fract() tiling - a general quad is one face with its own UVs, partial rectangles
     * included (UV ruling 5.4-3, the end-to-end sampling wiring).
     */
    public static final String GENERAL_FRAGMENT = "#version 450\n" + """
            layout(set = 0, binding = 6) uniform sampler2D atlas;
            layout(set = 0, binding = 7) uniform sampler2D lightMap;
            layout(set = 0, binding = 8) uniform FogBlock {
                vec4 fogColor;
                vec3 fogCameraPos;
                float fogStart;
                float fogEnd;
                float fogDensity;
                int fogMode;
                float alphaCutoff;
                float atlasCellsAcross;
            } fog;
            layout(location = 0) in vec2 fragAtlasUv;
            layout(location = 1) in vec4 fragColor;
            layout(location = 2) in vec2 fragLightMap;
            layout(location = 3) in vec3 fragWorldPos;
            layout(location = 4) in vec3 fragDynamicLight;
            layout(location = 0) out vec4 outColor;
            void main() {
                vec4 texel = texture(atlas, fragAtlasUv);
                if (texel.a < fog.alphaCutoff) discard;
                // Same lightmap sampling contract as the shared stage: texel centres of the
                // 16x16 grid, (block, sky) carried by fragLightMap.
                vec3 light = texture(lightMap, (fragLightMap * 15.0 + 0.5) / 16.0).rgb;
                vec3 rgb = texel.rgb * fragColor.rgb * (light + fragDynamicLight);

                if (fog.fogMode != 0) {
                    float dist = length(fragWorldPos - fog.fogCameraPos);
                    float factor;
                    if (fog.fogMode == 1) {
                        factor = clamp((dist - fog.fogStart) / max(fog.fogEnd - fog.fogStart, 0.001), 0.0, 1.0);
                    } else if (fog.fogMode == 2) {
                        factor = 1.0 - exp(-fog.fogDensity * dist);
                    } else {
                        float d = fog.fogDensity * dist;
                        factor = 1.0 - exp(-d * d);
                    }
                    rgb = mix(rgb, fog.fogColor.rgb, clamp(factor, 0.0, 1.0));
                }
                outColor = vec4(rgb, texel.a * fragColor.a);
            }
            """;
}
