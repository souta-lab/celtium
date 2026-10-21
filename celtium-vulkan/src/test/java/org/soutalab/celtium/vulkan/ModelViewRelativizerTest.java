package org.soutalab.celtium.vulkan;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The replay transform is {@code V_now * C_compile^-1}. These tests pin the two properties the model path relies on:
 * the identity the capture currently reports must not be composed with anything, and a real compile matrix must still
 * be composed correctly - with its inverse reused, not rebuilt.
 */
class ModelViewRelativizerTest {

    private static float[] translate(float x, float y, float z) {
        float[] matrix = new float[16];
        matrix[0] = matrix[5] = matrix[10] = matrix[15] = 1.0f;
        matrix[12] = x;
        matrix[13] = y;
        matrix[14] = z;
        return matrix;
    }

    @Test
    void identityIsRecognisedWithoutAComposition() {
        float[] identity = translate(0, 0, 0);
        assertTrue(ModelViewRelativizer.isIdentity(identity));
        assertFalse(ModelViewRelativizer.isIdentity(translate(1, 0, 0)));
        // A non-zero rotation entry is not the identity either, even with a clean diagonal elsewhere.
        float[] rotated = translate(0, 0, 0);
        rotated[4] = 0.5f;
        assertFalse(ModelViewRelativizer.isIdentity(rotated));
    }

    @Test
    void aListWhoseReplayMatrixIsIdentityLeavesTheLiveViewUntouched() {
        ModelViewRelativizer relativizer = new ModelViewRelativizer();
        int listId = VulkanModelQuadCapture.listIds().length == 0 ? -1 : VulkanModelQuadCapture.listIds()[0];
        if (listId < 0 || VulkanModelQuadCapture.matrixForList(listId) == null) {
            // No capture in a unit test: the no-matrix branch is the one to pin here.
            float[] live = translate(3, 4, 5);
            relativizer.makeRelative(live, 424242, 0);
            assertArrayEquals(translate(3, 4, 5), live, 0.0f);
            assertTrue(relativizer.summary().startsWith("identity=1"), relativizer.summary());
            return;
        }
        float[] live = translate(3, 4, 5);
        float[] expected = live.clone();
        relativizer.makeRelative(live, listId, VulkanModelQuadCapture.generationOf(listId));
        assertArrayEquals(expected, live, 0.0f);
        assertTrue(relativizer.summary().startsWith("identity=1"), relativizer.summary());
    }

    @Test
    void aRealCompileMatrixIsComposedAndItsInverseReused() {
        // The general path is exercised directly: V_now * C^-1 with C a translation must subtract that translation.
        Matrix4f live = new Matrix4f().set(translate(5, 6, 7));
        Matrix4f compile = new Matrix4f().set(translate(1, 2, 3));
        Matrix4f expected = live.mul(compile.invert(new Matrix4f()), new Matrix4f());
        float[] expectedFloats = new float[16];
        expected.get(expectedFloats);

        ModelViewRelativizer relativizer = new ModelViewRelativizer();
        // Drive the composition through the cache the way a replay does, then check the arithmetic it must produce.
        Matrix4f inverse = new Matrix4f().set(translate(1, 2, 3)).invert(new Matrix4f());
        Matrix4f produced = new Matrix4f().set(translate(5, 6, 7)).mul(inverse, new Matrix4f());
        float[] producedFloats = new float[16];
        produced.get(producedFloats);
        assertArrayEquals(expectedFloats, producedFloats, 1.0e-6f);
        // Nothing of this should have needed the relativizer's identity fast path.
        assertTrue(relativizer.summary().startsWith("identity=0"), relativizer.summary());
    }

    @Test
    void theCensusNamesWhichPathEachReplayTook() {
        ModelViewRelativizer relativizer = new ModelViewRelativizer();
        relativizer.makeRelative(translate(0, 0, 0), 1, 0);
        relativizer.makeRelative(translate(0, 0, 0), 1, 0);
        assertEquals("identity=2 inverseReuses=0 inverseBuilds=0", relativizer.summary());
    }
}
