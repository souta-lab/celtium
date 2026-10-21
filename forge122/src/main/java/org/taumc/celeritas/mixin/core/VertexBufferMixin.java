package org.taumc.celeritas.mixin.core;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.renderer.vertex.VertexFormat;
import org.soutalab.celtium.vulkan.NativeGeometryBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.core.CeltiumNativeDraw;
import org.taumc.celeritas.core.CeltiumVertexFormats;

import java.nio.ByteBuffer;

/** Native ownership of the public VertexBuffer lifecycle (sky, stars, horizon, Forge clouds and API users). */
@Mixin(VertexBuffer.class)
public abstract class VertexBufferMixin {
    @Shadow @Final private VertexFormat vertexFormat;
    @Shadow private int count;
    @Shadow private int glBufferId;
    @Unique private NativeGeometryBuffer celtium$geometry;

    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/OpenGlHelper;glGenBuffers()I"), require = 1)
    private int celtium$createNativeBuffer() {
        if (!CeltiumNativeDraw.enabled()) return OpenGlHelper.glGenBuffers();
        this.celtium$geometry = new NativeGeometryBuffer();
        return this.celtium$geometry.id();
    }

    @Inject(method = "bindBuffer", at = @At("HEAD"), cancellable = true, require = 1)
    private void celtium$bind(CallbackInfo callback) {
        if (this.celtium$geometry != null) {
            this.celtium$geometry.bind();
            callback.cancel();
        }
    }

    @Inject(method = "bufferData", at = @At("HEAD"), cancellable = true, require = 1)
    private void celtium$upload(ByteBuffer data, CallbackInfo callback) {
        if (this.celtium$geometry != null) {
            this.celtium$geometry.upload(data, this.vertexFormat.getSize(), CeltiumVertexFormats.id(this.vertexFormat));
            this.count = this.celtium$geometry.vertexCount();
            NativeGeometryBuffer.unbind();
            callback.cancel();
        }
    }

    @Inject(method = "drawArrays", at = @At("HEAD"), cancellable = true, require = 1)
    private void celtium$draw(int mode, CallbackInfo callback) {
        if (this.celtium$geometry != null) {
            CeltiumNativeDraw.vertexBuffer(this.celtium$geometry, mode);
            callback.cancel();
        }
    }

    @Inject(method = "unbindBuffer", at = @At("HEAD"), cancellable = true, require = 1)
    private void celtium$unbind(CallbackInfo callback) {
        if (this.celtium$geometry != null) {
            NativeGeometryBuffer.unbind();
            callback.cancel();
        }
    }

    @Inject(method = "deleteGlBuffers", at = @At("HEAD"), cancellable = true, require = 1)
    private void celtium$delete(CallbackInfo callback) {
        if (this.celtium$geometry != null) {
            this.celtium$geometry.close();
            this.glBufferId = -1;
            this.count = 0;
            callback.cancel();
        }
    }
}
