package org.soutalab.celtium.vulkan;

/**
 * The lit group's light payload - the two GL lights rotated into model space, the ambient and each diffuse - with
 * the shared-array cache, the replay variants and the replay's projection and combine colour. Moved out of
 * {@link VulkanTerrainGpuBackend} unchanged (2026-11-06).
 */
final class GroupLighting {
    private final VulkanTerrainGpuBackend b;

    GroupLighting(VulkanTerrainGpuBackend backend) {
        this.b = backend;
    }

    /**
     * The lit group's push payload: five vec4 - the two light directions rotated into model space, the scene ambient,
     * and each light's diffuse - or {@code null} when lighting is off.
     * <p>
     * GL lights live in eye space while the vertex shader only has model-space normals, so each eye-space direction is
     * pre-multiplied by the inverse modelview's rotation: {@code dot(N, M^-1 * L)} equals the fixed pipeline's
     * {@code dot(M^-T * N, L)} exactly for orthogonal transforms, which is every transform vanilla's lit draws use.
     * A disabled light's diffuse is zeroed rather than the direction skipped - the shader's dot stays a constant.
     */
    float[] celtium$lightParams(int[] state, float[] modelView) {
        // Equal payloads share the previous array: the directions depend on the modelview's rotation only, and most
        // lit groups in a frame carry the same axis-aligned orientation - the per-group twenty-float allocation was
        // pure garbage for them. A changed payload gets a new array so groups already holding one keep its values.
        float[] computed = celtium$lightParams(state, modelView, this.lightParamsScratch);
        if (computed == null) {
            return null;
        }
        float[] last = this.lastLightParams;
        if (last != null && java.util.Arrays.equals(last, computed)) {
            return last;
        }
        float[] fresh = computed.clone();
        this.lastLightParams = fresh;
        return fresh;
    }

    /** Filled by the two-arg form before it decides whether a group needs its own array. */
    final float[] lightParamsScratch = new float[20];
    float[] lastLightParams;

    /**
     * The model replay's lit payload, rebuilt only when the rotation or the light state moved.
     * <p>
     * The replay path calls this per display-list replay - 2.5 M times in one pack leg - while the payload depends only
     * on {@code modelView[0..10]} and {@link VulkanGlState#lightGeneration()}. A scene of entities sharing an
     * orientation repeats both, so the 3x3 inverse, two square roots and twenty stores were being done to arrive at
     * the bytes that were already in {@link #modelInstanceLight}. The scratch is the model path's own, never
     * {@link #lightParamsScratch}, because the two-arg form above writes that one for the per-group path.
     *
     * @return the payload to copy into the record, or null when the replay is unlit
     */
    float[] celtium$replayLight(int[] state, float[] modelView) {
        if (state == null || state.length < 9 || state[8] == 0 || modelView == null) {
            return null;
        }
        long generation = b.immediateState.lightGeneration();
        if (b.modelReplayInputCache.reuseLightPayload(generation, modelView)) {
            return b.modelInstanceLight;
        }
        this.celtium$lightParams(state, modelView, b.modelInstanceLight);
        b.modelReplayInputCache.noteLightPayload(generation, modelView);
        return b.modelInstanceLight;
    }

    /**
     * The replay's depth-remapped projection, rebuilt only when the live projection changes.
     * <p>
     * The remap is a 4x4 multiply with a constant matrix, and the projection is installed per pass, so nearly every
     * replay of a frame recomputed the same sixteen floats. The returned array is the model path's scratch; the group
     * that keeps a projection asks {@link #celtium$shareProjection} for its own copy.
     */
    float[] celtium$replayProjection(float[] projectionNow) {
        // The projection's version, not its contents: the remap is a 4x4 multiply and the cache used to read all sixteen
        // floats back to prove they had not moved. Every mutation of the projection stack bumps the version, so one
        // compare says the same thing.
        long projectionVersion = b.immediateState.projectionVersion();
        if (b.modelReplayInputCache.reuseProjection(projectionVersion)) {
            return b.modelReplayProjection;
        }
        VulkanTerrainGpuBackend.remapClipDepth(projectionNow, b.modelReplayProjection);
        b.modelReplayInputCache.noteProjection(projectionVersion);
        return b.modelReplayProjection;
    }

    /** As {@link #celtium$lightParams(int[], float[])}, written into {@code into} when the caller copies it out at once. */
    float[] celtium$lightParams(int[] state, float[] modelView, float[] into) {
        if (modelView == null || modelView.length < 11) {
            return null;
        }
        // One owner of "which floats of a modelview the payload depends on": the copy below is the array this function
        // reads, and the input cache keys on it too, so the two cannot drift. Reading the modelview directly here and
        // listing the nine indices again in the cache's key was two lists that could disagree.
        ModelReplayInputCache.copyRotation(modelView, b.lightRotationScratch);
        return celtium$lightParamsFromRotation(state, b.lightRotationScratch, into);
    }

    /**
     * Inverts a rotation's upper 3x3 into {@code inv}, column-major, or writes the identity for a singular one.
     * <p>
     * Split out and static so the geometry the model replay trusts is testable without a device: {@code rotation} is
     * {@link ModelReplayInputCache#copyRotation}'s nine floats, the array the payload is built from, so a test of this
     * inverse is a test of the payload a hit reuses.
     */
    static void celtium$invertRotation(float[] rotation, float[] inv) {
        // Named in row/col so the cofactors stay readable: a b c / d e f / g h i, with the rotation array holding the
        // modelview's column-major a=m[0], d=m[1], g=m[2], b=m[4] and so on.
        float a = rotation[0], d = rotation[1], g = rotation[2];
        float b = rotation[3], e = rotation[4], h = rotation[5];
        float c = rotation[6], f = rotation[7], i22 = rotation[8];
        float det = a * (e * i22 - f * h) - b * (d * i22 - f * g) + c * (d * h - e * g);
        if (Math.abs(det) < 1.0e-8f) {
            // A singular modelview degenerates the draw anyway; identity directions refuse nothing.
            java.util.Arrays.fill(inv, 0f);
            inv[0] = inv[4] = inv[8] = 1f;
            return;
        }
        float invDet = 1.0f / det;
        inv[0] = (e * i22 - f * h) * invDet; inv[1] = (f * g - d * i22) * invDet; inv[2] = (d * h - e * g) * invDet;
        inv[3] = (c * h - b * i22) * invDet; inv[4] = (a * i22 - c * g) * invDet; inv[5] = (b * g - a * h) * invDet;
        inv[6] = (b * f - c * e) * invDet; inv[7] = (c * d - a * f) * invDet; inv[8] = (a * e - b * d) * invDet;
    }

    /**
     * The lit payload from the nine rotation floats alone, in {@link ModelReplayInputCache#copyRotation}'s order.
     *
     * @param rotation the modelview's nine rotation floats: the payload's only geometric input
     */
    float[] celtium$lightParamsFromRotation(int[] state, float[] rotation, float[] into) {
        if (state == null || state.length < 9 || state[8] == 0 || rotation == null) {
            return null;
        }
        // The inverse is a local working matrix: a member scratch rather than a nine-float allocation per lit group.
        celtium$invertRotation(rotation, b.lightInverseScratch);
        float[] inv = b.lightInverseScratch;
        float[] params = into == null ? new float[20] : into;
        if (into != null) {
            java.util.Arrays.fill(params, 0f);
        }
        float[] dirs = b.immediateState.lightDirections();
        float[] diffuse = b.immediateState.lightDiffuse();
        float[] ambient = b.immediateState.lightAmbient();
        for (int i = 0; i < 2; ++i) {
            int eye = i * 3;
            float x = inv[0] * dirs[eye] + inv[3] * dirs[eye + 1] + inv[6] * dirs[eye + 2];
            float y = inv[1] * dirs[eye] + inv[4] * dirs[eye + 1] + inv[7] * dirs[eye + 2];
            float z = inv[2] * dirs[eye] + inv[5] * dirs[eye + 1] + inv[8] * dirs[eye + 2];
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            if (len > 1.0e-6f) {
                params[i * 4] = x / len;
                params[i * 4 + 1] = y / len;
                params[i * 4 + 2] = z / len;
            }
            int diffuseBase = 12 + i * 4;
            if (b.immediateState.lightEnabled(i)) {
                params[diffuseBase] = diffuse[eye];
                params[diffuseBase + 1] = diffuse[eye + 1];
                params[diffuseBase + 2] = diffuse[eye + 2];
            }
        }
        params[8] = ambient[0];
        params[9] = ambient[1];
        params[10] = ambient[2];
        return params;
    }

    /** Native material snapshot; no GL query, unit switching or state reconciliation at model replay. */
    float[] celtium$envCombineColor() {
        return NativeTextureEnvironment.current().combinedColor();
    }

}
