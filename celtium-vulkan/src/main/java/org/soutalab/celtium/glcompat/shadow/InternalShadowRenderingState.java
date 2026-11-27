package org.soutalab.celtium.glcompat.shadow;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.joml.Matrix4f;

public final class InternalShadowRenderingState {
    private static final Matrix4f MODEL_VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();

    private static boolean matricesAvailable;
    private static boolean active;
    private static boolean renderShadowEntities = true;
    private static boolean renderShadowPlayer = true;
    private static boolean renderShadowBlockEntities = true;

    private InternalShadowRenderingState() {
    }

    public static void update(Matrix4f modelView, Matrix4f projection) {
        MODEL_VIEW.set(modelView);
        PROJECTION.set(projection);
        matricesAvailable = true;
    }

    public static void begin(Matrix4f modelView, Matrix4f projection) {
        begin(modelView, projection, true, true, true);
    }

    public static void begin(Matrix4f modelView,
                             Matrix4f projection,
                             boolean renderShadowEntities,
                             boolean renderShadowPlayer,
                             boolean renderShadowBlockEntities) {
        update(modelView, projection);
        active = true;
        InternalShadowRenderingState.renderShadowEntities = renderShadowEntities;
        InternalShadowRenderingState.renderShadowPlayer = renderShadowPlayer;
        InternalShadowRenderingState.renderShadowBlockEntities = renderShadowBlockEntities;
    }

    public static void end() {
        active = false;
        renderShadowEntities = true;
        renderShadowPlayer = true;
        renderShadowBlockEntities = true;
    }

    public static boolean areShadowsCurrentlyBeingRendered() {
        return active;
    }

    public static boolean fillShadowMatrices(Matrix4f modelView, Matrix4f projection) {
        if (!matricesAvailable) {
            return false;
        }

        modelView.set(MODEL_VIEW);
        projection.set(PROJECTION);
        return true;
    }

    public static void clear() {
        matricesAvailable = false;
        active = false;
        renderShadowEntities = true;
        renderShadowPlayer = true;
        renderShadowBlockEntities = true;
        MODEL_VIEW.identity();
        PROJECTION.identity();
    }

    public static boolean shouldRenderShadowEntities() {
        return renderShadowEntities;
    }

    public static boolean shouldRenderShadowPlayer() {
        return renderShadowPlayer;
    }

    public static boolean shouldRenderShadowBlockEntities() {
        return renderShadowBlockEntities;
    }
}
