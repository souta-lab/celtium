package org.soutalab.celtium.vulkan;

/**
 * Whether the open immediate group can take the next batch: the colour, texture matrix, scissor, fog and tex-gen
 * comparisons against what the group captured, and the texture-matrix reads they share. Moved out of {@link
 * VulkanTerrainGpuBackend} unchanged (2026-11-06).
 */
final class GroupChange {
    private final VulkanTerrainGpuBackend b;

    GroupChange(VulkanTerrainGpuBackend backend) {
        this.b = backend;
    }

    /**
     * {@return whether the colour the game has current differs from the one captured with {@code currentGroupColor}}.
     * <p>
     * BH-04: the group key compared the format, texture, state key, pass and matrices, but not the current colour, so
     * two colourless draws in one group were both drawn with the first one's tint. A {@code null} captured colour means
     * the group's format does not take a colour at all, and then there is nothing to compare.
     */
    boolean celtium$colourDiffers() {
        if (b.currentGroupColor == null) {
            return true;
        }
        return b.currentGroupColor[0] != b.immediateState.colorRed()
                || b.currentGroupColor[1] != b.immediateState.colorGreen()
                || b.currentGroupColor[2] != b.immediateState.colorBlue()
                || b.currentGroupColor[3] != b.immediateState.colorAlpha();
    }

    /**
     * {@return whether the live texture affine differs from the one captured with {@code currentGroupTexMatrix}}.
     * <p>
     * BH-04's texture-matrix half: the affine is per-draw state (the item glint scrolls it, and the renderer's own
     * effects set it), and it is captured once when a group opens. A batch offered after it changed used to join the
     * open group and be drawn with the earlier transform. {@code null} means the identity, so a move to or from the
     * identity is a change like any other.
     */
    boolean celtium$textureAffineMoved() {
        // Compared through the scratch rather than by building the affine twice: this runs once per offered batch
        // (~280 a frame), and the old form allocated a float[6] on every call to answer a yes/no question.
        boolean live = celtium$readTextureAffine(b.textureAffineScratch);
        float[] captured = b.currentGroupTexMatrix;
        if (!live || captured == null) {
            return live != (captured != null);
        }
        for (int i = 0; i < 6; i++) {
            if (b.textureAffineScratch[i] != captured[i]) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@return whether the live fog differs from the one captured with {@code currentGroupFog}}.
     * <p>
     * BH-04's fog half, and the reason the fog was captured per group in the first place: {@code renderSky} brackets
     * the sky dome with fog enabled and the sun and moon with it disabled inside one pass, so a group that spanned
     * such a bracket drew both halves under one of the two. {@code null} is fog off, which is a state to compare.
     */
    /**
     * {@return whether the live clip rectangle is the one the open group was captured under}.
     * <p>
     * The comparison is between two <em>clips</em>, not two boxes: a rectangle is only in force when the test is
     * enabled <em>and</em> {@code glScissor} has been called, so "test off with a box recorded" and "test on with a box
     * recorded" are different states and must split the group even when the four numbers are equal. The live side is
     * read through the value readers rather than {@code scissorBox()} because this runs once per offered batch, and
     * that method builds an array.
     */
    boolean celtium$sameScissor(boolean liveApplies) {
        boolean capturedApplies = b.currentGroupScissorEnabled && b.currentGroupScissor != null;
        if (capturedApplies != liveApplies) {
            return false;
        }
        if (!liveApplies) {
            return true;
        }
        int[] captured = b.currentGroupScissor;
        return captured[0] == b.rasterState.scissorX()
                && captured[1] == b.rasterState.scissorY()
                && captured[2] == b.rasterState.scissorWidth()
                && captured[3] == b.rasterState.scissorHeight();
    }

    boolean celtium$fogMoved() {
        // The group's own copy is taken once per group by the snapshot; the comparison per offer allocates nothing.
        return b.fog.movedFrom(b.currentGroupFog);
    }

    static final boolean FORCE_IDENTITY_TEXMAT = Boolean.getBoolean("celtium.forceIdentityTexmat");

    /** {@return {@code out}, filled with the live texgen eye rows under unit 0's texture matrix}. */
    float[] celtium$texGenEyeRows(float[] out) {
        NativeTexGen.current().eyeRows(b.immediateState.peekTextureMatrix(0), out);
        return out;
    }

    /** {@return whether the live texgen rows differ from the open group's}; the end portal's layers differ only here. */
    boolean celtium$texGenMoved() {
        float[] captured = b.currentGroupTexGen;
        if (captured == null) {
            return true;
        }
        return !java.util.Arrays.equals(celtium$texGenEyeRows(b.texGenScratch), captured);
    }

    /**
     * The 2D affine part of the texture matrix, or {@code null} when it is the identity. {@code matrixMode(5890)}
     * routes glTranslate/glRotate/glScale onto the mirror's texture stack; the item glint animates its texture this
     * way and the renderer uses the same channel for its own overlays. Only the UV transform matters, so the result
     * is {@code [a,b,c,d,tx,ty]} in column-major terms ({@code u'=a*u+c*v+tx, v'=b*u+d*v+ty}).
     */
    float[] celtium$textureAffine() {
        if (!celtium$readTextureAffine(b.textureAffineScratch)) {
            return null;
        }
        // Copied here and nowhere else: a group keeps its own snapshot, and capturing is a few hundred times a frame
        // rather than once per offer. Equal affines share the previous array - most model draws run with the same
        // item transform - and a changed one gets a new array so groups already holding the old keep its values.
        float[] last = b.lastTextureAffine;
        if (last != null && java.util.Arrays.equals(last, b.textureAffineScratch)) {
            return last;
        }
        float[] fresh = b.textureAffineScratch.clone();
        b.lastTextureAffine = fresh;
        return fresh;
    }

    /**
     * Reads unit 0's texture affine into {@code out}.
     *
     * @return whether the live affine is anything other than the identity
     */
    boolean celtium$readTextureAffine(float[] out) {
        if (FORCE_IDENTITY_TEXMAT) {
            return false;
        }
        try {            // Unit 0's matrix explicitly: the lightmap unit's matrix is a different transform on a different stack,
            // and reading the shared stack would return whichever unit was last active.
            float[] matrix = b.immediateState.peekTextureMatrix(0);
            if (matrix == null) {
                return false;
            }
            float a = matrix[0], b = matrix[1], c = matrix[4], d = matrix[5], tx = matrix[12], ty = matrix[13];
            if (a == 1.0f && b == 0.0f && c == 0.0f && d == 1.0f && tx == 0.0f && ty == 0.0f) {
                return false;
            }
            out[0] = a;
            out[1] = b;
            out[2] = c;
            out[3] = d;
            out[4] = tx;
            out[5] = ty;
            return true;
        } catch (RuntimeException | LinkageError error) {
            return false;
        }
    }
}
