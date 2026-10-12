package org.taumc.celeritas.mixin.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Supplies the world projection from Minecraft's own numbers instead of mirroring the GL call that sets it.
 * <p>
 * The mirror cannot see the world projection: {@code GlStateManager.matrixMode} and {@code loadIdentity} are hooked, so the
 * mirrored projection becomes the identity, and the call that would fill it is {@code Project.gluPerspective}, whose class
 * sits outside the transforming loader - measured, not assumed: its counter has stayed at zero. What that call computes is
 * four numbers, three of which are Minecraft state and the fourth the result of {@code func_78481_a}, the field-of-view
 * modifier. Hooking that method's return gives the fourth without any accessor machinery, and the backend does the
 * {@code gluPerspective} arithmetic itself.
 * <p>
 * This is the shape the direction asks for: a renderer that knows what a world projection is, rather than a layer that
 * mirrors a GL call it cannot reach.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    /**
     * The frame's own bracket, and the top-level passes inside it.
     * <p>
     * This is the instrument the lag-spike decode asked for (plan §0.227): the profiler's {@code gameRenderer} self
     * time is 318.6 ms in spike frames against 25.0 ms in median frames and its named children explain only about
     * 72 ms of it, so roughly 246 ms belongs to no section. Bracketing the passes with our own wall clock gives that
     * residual a name - {@code world}, {@code hand}, {@code clouds}, {@code weather}, {@code gui} - and reports what
     * is left. It reads none of the profiler's numbers, so it cannot inherit their overlap: the profiler's self-time
     * sum is 4.01x the frame time and must never be added to this.
     */
    @Inject(method = "func_181560_a", at = @At("HEAD"), require = 0)
    private void celtium$frameBegin(float partialTicks, long finishTimeNano,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.beginFrame();
    }

    @Inject(method = "func_181560_a", at = @At("RETURN"), require = 0)
    private void celtium$frameEnd(float partialTicks, long finishTimeNano,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.endFrame();
    }

    @Inject(method = "func_78471_a", at = @At("HEAD"), require = 0)
    private void celtium$worldPassTimedBegin(float partialTicks, long finishTimeNano,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.begin("world");
    }

    @Inject(method = "func_78471_a", at = @At("RETURN"), require = 0)
    private void celtium$worldPassTimedEnd(float partialTicks, long finishTimeNano,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.end("world");
    }

    @Inject(method = "func_78476_b", at = @At("HEAD"), require = 0)
    private void celtium$handPassTimedBegin(float partialTicks, int pass,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.begin("hand");
    }

    @Inject(method = "func_78476_b", at = @At("RETURN"), require = 0)
    private void celtium$handPassTimedEnd(float partialTicks, int pass,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.end("hand");
    }

    /**
     * Seeds the mirror once the camera transform is complete.
     * <p>
     * The hooks cannot have seen everything: `func_78479_a` applies `loadIdentity` and the camera transform, and any of it
     * that ran before the backend existed is lost to the mirror. This runs at the end of that method, when GL holds the
     * finished world matrix - the right moment, unlike the first batch of a frame, which is offered during a pass that has
     * already reset both stacks.
     */
    @Inject(method = "func_78479_a", at = @At("RETURN"), require = 0)
    private void celtium$seedMirrorAtCameraSetup(float partialTicks, int pass,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        try {
            TerrainGpuBackend backend = TerrainGpuBackends.current();
            if (backend != null) {
                // The camera transform is complete here: this is the boundary the snapshot is taken at.
                backend.seedMirrorFromCameraSetup();
                backend.captureWorldSnapshot();
            }
        } catch (Throwable ignored) {
            // A hint must never break the frame.
        }
    }

    /**
     * The world pass's explicit boundaries.
     * <p>
     * The pass lifecycle is what decides whether a batch may be drawn natively: matrices are valid for drawing only between
     * these two points, and the snapshot taken at the camera boundary is what the batches inside them are drawn with. The
     * earlier flag was inferred from which projection was installed and proved unreliable - a first-person hand pass or a
     * custom framebuffer can install anything - so the lifecycle is stated rather than guessed.
     */
    /** Set once, when the light map's unit has been handed to the renderer. */
    private static boolean celtium$lightmapUnitSent;

    @Inject(method = "func_175068_a", at = @At("HEAD"), require = 0)
    private void celtium$worldPassBegin(int pass, float partialTicks, long finishTimeNano,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        try {
            // The light map's unit is a Minecraft constant and the renderer module does not depend on Minecraft, so the
            // number is handed over here. It is needed when the light map's GL id has been zeroed by a reallocation and a
            // batch arrives before the next announcement: the renderer then asks GL for the binding on this unit.
            if (!celtium$lightmapUnitSent) {
                TerrainGpuBackend handover = TerrainGpuBackends.current();
                if (handover instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) {
                    celtium$lightmapUnitSent = true;
                    ((org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) handover)
                            .celtium$setLightmapUnit(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit);
                }
            }
            TerrainGpuBackend backend = TerrainGpuBackends.current();
            if (backend != null) {
                backend.beginWorldPass();
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "func_175068_a", at = @At("RETURN"), require = 0)
    private void celtium$worldPassEnd(int pass, float partialTicks, long finishTimeNano,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        try {
            TerrainGpuBackend backend = TerrainGpuBackends.current();
            if (backend != null) {
                backend.endWorldPass();
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * The light-map texture unit was enabled - the entity pass ({@code RenderGlobal.renderEntities}), the first-person
     * hand, or the block-entity section. The unit is what real OpenGL modulates a draw by, so the backend needs this to
     * tell a world-lit draw from one the light map does not reach at all (the whole GUI stage, where the unit is off).
     * <p>
     * {@code func_180436_i} is {@code EntityRenderer.enableLightmap} in the SRG table, resolved there rather than
     * guessed: a wrong name here is silently dropped under {@code require = 0}, and the two counters the backend keeps
     * ({@code lightmapUnitEnables}/{@code lightmapUnitDisables} in the verification summary) are what make that visible
     * - the rule itself defaults to "enabled", so a dropped injection leaves the captured coordinate in place instead
     * of drawing every entity full-bright.
     */
    @Inject(method = "func_180436_i", at = @At("HEAD"), require = 0)
    private void celtium$lightmapUnitOn(org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        try {
            TerrainGpuBackend backend = TerrainGpuBackends.current();
            if (backend != null) {
                backend.setLightmapUnitEnabled(true);
            }
        } catch (Throwable ignored) {
        }
    }

    /** {@code func_175072_h} is {@code EntityRenderer.disableLightmap}: see {@link #celtium$lightmapUnitOn}. */
    @Inject(method = "func_175072_h", at = @At("HEAD"), require = 0)
    private void celtium$lightmapUnitOff(org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        try {
            TerrainGpuBackend backend = TerrainGpuBackends.current();
            if (backend != null) {
                backend.setLightmapUnitEnabled(false);
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "func_78481_a", at = @At("RETURN"), require = 0)
    private void celtium$reportFieldOfView(float partialTicks, boolean useFovSetting,
            CallbackInfoReturnable<Float> info) {
        try {
            TerrainGpuBackend backend = TerrainGpuBackends.current();
            if (backend == null) {
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.displayHeight <= 0) {
                return;
            }
            float aspect = (float) mc.displayWidth / (float) mc.displayHeight;
            // farPlaneDistance is renderDistanceChunks * 16 and the world pass uses farPlaneDistance * SQRT_2.
            float farPlane = mc.gameSettings.renderDistanceChunks * 16.0F * 1.41421356F;
            backend.applyDerivedProjection(info.getReturnValueF(), aspect, 0.05F, farPlane);
        } catch (Throwable ignored) {
            // A hint must never break the frame; the mirror simply keeps whatever it had.
        }
    }

    /**
     * Presents the previous frame from Vulkan at the head of this frame's world render.
     * <p>
     * The head of the world render is used because it is a point that is proven to be reached every frame, and because by
     * then the previous frame is complete - world, entities and the GUI - which is what the shared colour image holds. The
     * GL swap is deliberately left alone: the result is not used to skip it, so a failure here cannot leave the window
     * without a frame. What this establishes is that the frame content Vulkan presents is this frame's content; whether the
     * GL swap can then be skipped is the next step.
     */
    @org.spongepowered.asm.mixin.injection.Inject(method = "func_175068_a", at = @org.spongepowered.asm.mixin.injection.At("HEAD"), require = 0)
    private void celtium$presentPreviousFrame(int pass, float partialTicks, long finishTimeNano,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        try {
            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend current =
                    org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            if (current instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan
                    && !org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()) {
                // GL-less has one present site per frame and it is the swap point: this one would present the same
                // frame a second time and overwrite the skip reason with its own refusal.
                // "present" inside "world": the pass split's own bracket names the renderer's present/composite
                // share of a spike inside renderWorldPass, which the frame brackets alone leave unnamed.
                org.soutalab.celtium.vulkan.FramePassTimings.begin("present");
                try {
                    vulkan.celtium$presentFrame();
                } finally {
                    org.soutalab.celtium.vulkan.FramePassTimings.end("present");
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Presenting from Vulkan must never stop the game from rendering a frame.
        }
    }
}
