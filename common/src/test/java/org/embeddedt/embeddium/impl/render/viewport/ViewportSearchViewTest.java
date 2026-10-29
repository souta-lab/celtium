package org.embeddedt.embeddium.impl.render.viewport;

import org.embeddedt.embeddium.impl.render.viewport.frustum.Frustum;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewportSearchViewTest {
    private static final Frustum FRUSTUM = (a, b, c, d, e, f) -> true;

    @Test
    void publishedSearchIsReusedOnlyForTheSamePositionPlanesAndOffset() {
        Viewport original = view(10.25, new Matrix4f(), new Vector3f(0, 1.6f, 0));
        assertTrue(original.sameSearchView(view(10.25, new Matrix4f(), new Vector3f(0, 1.6f, 0))));
        assertFalse(original.sameSearchView(view(10.35, new Matrix4f(), new Vector3f(0, 1.6f, 0))),
                "motion inside one block still changes frustum tests");
        assertFalse(original.sameSearchView(view(10.25, new Matrix4f().rotateY(0.00001f), new Vector3f(0, 1.6f, 0))),
                "even a small camera rotation can expose a section at the frustum edge");
        assertFalse(original.sameSearchView(view(10.25, new Matrix4f(), new Vector3f(0.1f, 1.6f, 0))),
                "a mod camera transform changes the frustum's offset independently of the entity");
        assertFalse(original.sameSearchView(new Viewport(FRUSTUM, new Vector3d(10.25, 0, 0))));
    }

    @Test
    void searchViewRetainsMatrixAndOffsetSnapshots() {
        Matrix4f matrix = new Matrix4f();
        Vector3f offset = new Vector3f(0, 1.6f, 0);
        Viewport captured = view(10.25, matrix, offset);
        matrix.rotateY(0.3f);
        offset.x = 2;
        assertTrue(captured.sameSearchView(view(10.25, new Matrix4f(), new Vector3f(0, 1.6f, 0))));
    }

    private static Viewport view(double x, Matrix4f matrix, Vector3f offset) {
        return new Viewport(FRUSTUM, new Vector3d(x, 0, 0), matrix, offset);
    }
}
