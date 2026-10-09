package org.taumc.celeritas.mixin.core;

import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;
import net.minecraft.client.renderer.BufferBuilder;
import org.soutalab.celtium.vulkan.VulkanModelQuadCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reads the quads a model part is about to compile into its display list.
 * <p>
 * This is the capture half of the model path: {@code ModelRenderer} bakes these quads into a GL display list once and
 * replays it, so the geometry never reaches the uploader the immediate path hooks. Reading them here is the only place
 * the vertices exist as data - and the replay census says how much of it there is (the sky and stars twice a frame, ten
 * model parts for a visible player).
 * <p>
 * Nothing is changed: the original body runs, the pixels are untouched, and the numbers go to a census. {@code require = 0}
 * so a renamed method degrades to "not captured" rather than a client that will not start.
 */
@Mixin(TexturedQuad.class)
public abstract class TexturedQuadMixin {

    @Inject(method = "draw", at = @At("HEAD"), require = 0)
    private void celtium$captureQuad(BufferBuilder renderer, float scale, CallbackInfo info) {
        try {
            PositionTextureVertex[] positions = ((TexturedQuad) (Object) this).vertexPositions;
            if (positions == null || positions.length == 0) {
                return;
            }
            float[] xs = new float[positions.length];
            float[] ys = new float[positions.length];
            float[] zs = new float[positions.length];
            float[] us = new float[positions.length];
            float[] vs = new float[positions.length];
            for (int i = 0; i < positions.length; i++) {
                xs[i] = (float) positions[i].vector3D.x * scale;
                ys[i] = (float) positions[i].vector3D.y * scale;
                zs[i] = (float) positions[i].vector3D.z * scale;
                us[i] = positions[i].texturePositionX;
                vs[i] = positions[i].texturePositionY;
            }
            // The plane normal, computed the way TexturedQuad.draw computes it, so the native path can carry the same
            // lighting input rather than inventing one.
            net.minecraft.util.math.Vec3d edge1 =
                    positions[1].vector3D.subtractReverse(positions[0].vector3D);
            net.minecraft.util.math.Vec3d edge2 =
                    positions[1].vector3D.subtractReverse(positions[2].vector3D);
            net.minecraft.util.math.Vec3d normal = edge2.crossProduct(edge1).normalize();
            VulkanModelQuadCapture.quad(positions.length, xs, ys, zs, us, vs,
                    (float) normal.x, (float) normal.y, (float) normal.z);
        } catch (RuntimeException | LinkageError ignored) {
            // A measurement must never stop the game's rendering.
        }
    }
}
