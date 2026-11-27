package org.soutalab.celtium.vulkan.terrain.lane1;

/**
 * The Lane 1 terrain shaders (design V040-FINAL-PLAN section 2.1): the GPU cull compute (frustum
 * + half-res Hi-Z, R-19 plan-2), the Hi-Z pyramid build, and the vertex-pulling draw pair (K1)
 * whose vertex stage unpacks the 16 B {@code PackedAxisQuad} / 64 B {@code GeneralQuad} in the
 * shader - no vertex buffers exist on this lane. The dynamic-light evaluation walks the cluster
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
            struct LightRecord {
                vec3 position;
                float radius;
                vec3 color;
                float intensity;
            };
            """;

    /** Frustum + Hi-Z cull: one invocation per section, emitting VkDrawIndirectCommand records. */
    public static final String CULL_COMPUTE = "#version 450\n"
            + "#extension GL_EXT_shader_explicit_arithmetic_types_int64 : require\n"
            + COMMON + """
            layout(std430, set = 0, binding = 0) readonly buffer Descs { SectionDesc descs[]; };
            layout(std430, set = 0, binding = 1) writeonly buffer Commands { uint commands[]; };
            layout(std430, set = 0, binding = 2) buffer Counters { uint visibleCount; uint commandCount; };
            layout(set = 0, binding = 3) uniform sampler2D hiZ;
            layout(push_constant) uniform CullPush {
                vec4 planes[6];
                vec3 cameraPos;
                uint sectionCount;
                uint layer;
                uint commandsPerSection;
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
            void main() {
                uint slot = gl_GlobalInvocationID.x;
                if (slot >= pc.sectionCount) return;
                SectionDesc d = descs[slot];
                vec3 mn = vec3(d.section) * 16.0 - pc.cameraPos;
                vec3 mx = mn + 16.0;
                if (!frustumVisible(mn, mx)) return;
                // Hi-Z occlusion against the previous frame's pyramid is sampled by the caller-side
                // variant of this shader once the pyramid exists (K3 gate); the frustum result is
                // the conservative floor and never culls more than the pyramid would.
                uint cmd = atomicAdd(commandCount, 1u);
                uint base = cmd * 5u;
                // Layer ranges ride a side table in the full binding set; the axis range of the
                // section is the fallback span this built-in emits (see TerrainSectionStore).
                commands[base + 0u] = uint(d.axisCount) * 6u;
                commands[base + 1u] = 1u;
                commands[base + 2u] = uint(d.axisOffset / 16u);
                commands[base + 3u] = slot;
                commands[base + 4u] = 0u;
                atomicAdd(visibleCount, 1u);
            }
            """;

    /** Half-resolution depth pyramid reduction (one invocation per output texel, per level). */
    public static final String HIZ_COMPUTE = "#version 450\n" + """
            layout(set = 0, binding = 0) uniform sampler2D sourceDepth;
            layout(r32f, set = 0, binding = 1) writeonly uniform image2D targetLevel;
            layout(push_constant) uniform HiZPush { ivec2 sourceSize; };
            void main() {
                ivec2 p = ivec2(gl_GlobalInvocationID.xy);
                ivec2 size = imageSize(targetLevel);
                if (p.x >= size.x || p.y >= size.y) return;
                vec2 uv = (vec2(p) * 2.0 + 1.0) / vec2(sourceSize);
                vec2 texel = 1.0 / vec2(sourceSize);
                float d = texture(sourceDepth, uv).r;
                d = max(d, texture(sourceDepth, uv + vec2(texel.x, 0.0)).r);
                d = max(d, texture(sourceDepth, uv + vec2(0.0, texel.y)).r);
                d = max(d, texture(sourceDepth, uv + texel).r);
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
                vec4 cameraPos;
                ivec4 gridOrigin;
                int lightCount;
                float atlasCellsAcross;
                int textureCellCount;
                int pushPad2;
            } pc;
            layout(location = 0) out vec2 fragCell;
            layout(location = 1) out vec4 fragColor;
            layout(location = 2) out vec2 fragLightMap;
            layout(location = 3) out vec3 fragWorldPos;
            layout(location = 4) out vec3 fragDynamicLight;
            layout(location = 5) out vec2 fragLocalUv;
            layout(location = 6) flat out int fragSection;
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
                vec2 corner = CORNERS[gl_VertexIndex % 6];
                uvec4 q = quads[quadIndex];
                SectionDesc d = descs[gl_InstanceIndex];
                vec3 minCorner = vec3(float(q.x & 63u), float((q.x >> 6) & 63u), float((q.x >> 12) & 63u));
                uint axis = (q.x >> 18) & 3u;
                uint extentU = (q.y & 63u) + 1u;
                uint extentV = ((q.y >> 6) & 63u) + 1u;
                uint textureIndex = q.y >> 12;
                vec3 local = minCorner;
                if (axis == 0u) { local.y += corner.x * float(extentU); local.z += corner.y * float(extentV); }
                else if (axis == 1u) { local.x += corner.x * float(extentU); local.z += corner.y * float(extentV); }
                else { local.x += corner.x * float(extentU); local.y += corner.y * float(extentV); }
                vec3 worldPos = vec3(d.section) * 16.0 + local;
                vec3 relative = worldPos - pc.cameraPos.xyz;
                gl_Position = pc.viewProj * vec4(relative, 1.0);
                float cellU = float(q.z & 255u);
                float cellV = float((q.z >> 8) & 255u);
                fragCell = vec2(cellU, cellV);
                fragLocalUv = corner * vec2(float(extentU), float(extentV));
                float shade = float((q.z >> 24) & 255u) / 255.0;
                vec3 tint = ((q.w & (1u << 24)) != 0u)
                        ? vec3(float(q.w & 255u), float((q.w >> 8) & 255u), float((q.w >> 16) & 255u)) / 255.0
                        : vec3(1.0);
                fragColor = vec4(tint * shade, 1.0);
                fragLightMap = vec2(float((q.z >> 20) & 15u), float((q.z >> 16) & 15u)) / 15.0;
                fragSection = gl_InstanceIndex;
                fragWorldPos = worldPos;
                // V2-5: the vertex stage evaluates the cluster's dynamic lights (findLSB walk).
                fragDynamicLight = evaluateLights(worldPos + vec3(0.5));
            }
            """;

    /** Vertex-pulling general draw: reads the 64 B GeneralQuad stream. */
    public static final String GENERAL_VERTEX = "#version 450\n"
            + "#extension GL_EXT_shader_explicit_arithmetic_types_int64 : require\n"
            + COMMON + """
            layout(std430, set = 0, binding = 0) readonly buffer Descs { SectionDesc descs[]; };
            // Flat word stream, 16 words per GeneralQuad (12 position floats, then
            // textureIndex / lightWord / normalPacked / flags as bit-cast ints). A struct
            // array member cannot be indexed dynamically without GL_EXT_nonuniform_qualifier,
            // so the shader reads the same 64 B record through a flat float array instead.
            layout(std430, set = 0, binding = 1) readonly buffer GeneralQuads { float generalWords[]; };
            layout(std430, set = 0, binding = 3) readonly buffer TextureCells { uvec2 textureCells[]; };
            layout(push_constant) uniform DrawPush {
                mat4 viewProj;
                vec4 cameraPos;
                ivec4 gridOrigin;
                int lightCount;
                float atlasCellsAcross;
                int textureCellCount;
                int pushPad2;
            } pc;
            layout(location = 0) out vec2 fragCell;
            layout(location = 1) out vec4 fragColor;
            layout(location = 2) out vec2 fragLightMap;
            layout(location = 3) out vec3 fragWorldPos;
            layout(location = 4) out vec3 fragDynamicLight;
            layout(location = 5) out vec2 fragLocalUv;
            layout(location = 6) flat out int fragSection;
            const int CORNER_INDEX[6] = int[](0, 1, 2, 2, 1, 3);
            void main() {
                uint quadIndex = uint(gl_VertexIndex) / 6u;
                int corner = CORNER_INDEX[gl_VertexIndex % 6];
                SectionDesc d = descs[gl_InstanceIndex];
                uint wordBase = quadIndex * 16u;
                vec3 local = vec3(generalWords[wordBase + uint(corner * 3)],
                                  generalWords[wordBase + uint(corner * 3) + 1u],
                                  generalWords[wordBase + uint(corner * 3) + 2u]);
                vec3 worldPos = vec3(d.section) * 16.0 + local;
                gl_Position = pc.viewProj * vec4(worldPos - pc.cameraPos.xyz, 1.0);
                // The quad's texture index resolves through the frontend's cell table; the corner
                // maps onto the sprite's full extent (the S2-a full-sprite approximation: the
                // mesher's 64 B record carries no per-vertex sprite UVs).
                int textureIndex = floatBitsToInt(generalWords[wordBase + 12u]);
                vec2 cell = vec2(0.0);
                if (textureIndex > 0 && textureIndex < pc.textureCellCount) {
                    cell = vec2(textureCells[textureIndex]);
                }
                fragCell = cell;
                // Just inside the sprite's far edge, so the fragment's fract() keeps the edge
                // column instead of wrapping to the sprite's first column.
                fragLocalUv = vec2(float(corner & 1), float((corner >> 1) & 1)) * 0.99999;
                int flags = floatBitsToInt(generalWords[wordBase + 15u]);
                vec3 tint = ((flags & 1) != 0)
                        ? vec3(float((flags >> 8) & 255), float((flags >> 16) & 255), float((flags >> 24) & 255)) / 255.0
                        : vec3(1.0);
                fragColor = vec4(tint, 1.0);
                int lightWord = floatBitsToInt(generalWords[wordBase + 13u]);
                fragLightMap = vec2(float((lightWord >> 8) & 255), float(lightWord & 255)) / 255.0;
                fragSection = gl_InstanceIndex;
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
            layout(location = 6) flat in int fragSection;
            layout(location = 0) out vec4 outColor;
            void main() {
                // Per-block tiling: the cell selects the sprite, the face-local coordinate wraps
                // inside it (a greedy-merged face repeats its sprite once per block).
                vec2 uv = (fragCell + fract(fragLocalUv)) / max(fog.atlasCellsAcross, 1.0);
                // The texture mirrors are stored row-flipped against the GL UV convention
                // (measured at Gate G1: unflipped samples read the mirrored rows for both
                // the atlas and the lightmap), so V is flipped back here.
                uv.y = 1.0 - uv.y;
                vec4 texel = texture(atlas, uv);
                if (texel.a < fog.alphaCutoff) discard;
                // The lightmap image's axes run (u = sky, v = block) as stored - measured
                // at Gate G1 by displaying the bound image: brightness grows along U and
                // peaks at (1, 0). fragLightMap carries (block, sky), so sample swapped.
                // The lightmap is a 16x16 texel grid indexed (block, sky); sample texel
                // centres - the raw light/15 coordinates land on the grid's dark edges
                // (measured at Gate G1 by probing the bound image's corners).
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
