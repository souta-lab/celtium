package org.soutalab.celtium.glcompat.hooks;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.DisplayListManager;

public final class GLSMHooks {
    public static DeferredBlendHandler blendHandler;
    public static DeferredAlphaHandler alphaHandler;
    public static DeferredDepthColorHandler depthColorHandler;
    /** Host builder for the {@code mc_midTexCoord}/{@code at_tangent} attributes the FFP draw path rebuilds. */
    public static ImmediateExtendedAttribHandler immediateExtendedHandler;
    /** Optional host-owned observer for diagnostics at the native draw boundary. */
    public static DrawCallObserver drawCallObserver;
    public static ShaderWorkSubmitter shaderWorkSubmitter;
    public static ShaderTransformPostProcessor postTransformProcessor;
    /** Host program that widens lines for shader-pack draws, used instead of the FFP line-quad path. */
    public static LineDrawHandler lineDrawHandler;
    /** Host callback run after a shader pack reload, when caches derived from the previous pack's programs are stale. */
    public static Runnable shaderPackReloaded;
    public static PerFrameUniformBlock perFrameUniformBlock;
    public static PerFrameUniformBlock perPassUniformBlock;
    /** Waits for asynchronously prepared uniform-block layouts before a backend compiles a dependent shader. */
    public static Runnable uniformBlockBarrier;
    /** Host observer that turns {@code glColor} changes into the enchantment glint tint while glint draws. */
    public static GlintColorHandler glintColorHandler;
    /** Pending shader-pack program selection that has to run outside display-list recording. */
    public static PendingProgramSelection pendingProgramSelection;

    /** Resolves a program selection deferred while a batch or a display list owned the program. */
    public static void resolvePendingProgram() {
        final PendingProgramSelection pending = pendingProgramSelection;
        if (pending != null && DisplayListManager.getRecordMode() != DisplayListManager.RecordMode.COMPILE) pending.resolvePendingProgram();
    }

    /**
     * Optional host callback notified after each executed {@code GL_TEXTURE_2D} bind request,
     * including cache hits. The host keeps vanilla's {@code GlStateManager.TEXTURES[].textureName}
     * mirror in sync for mods that read it reflectively to restore a prior binding.
     */
    public static volatile TextureBindSyncCallback textureBindSyncCallback;

    /** Receives texture binds so the host can synchronize state outside GLSM. */
    public interface TextureBindSyncCallback {
        /** Reports the active texture unit and the texture id passed to the executed bind request. */
        void onTextureBound(int textureUnit, int textureId);
    }

    /** Dispatches a texture bind to the host callback when one has been registered. */
    public static void notifyTextureBindSync(int textureUnit, int textureId) {
        final TextureBindSyncCallback callback = textureBindSyncCallback;
        if (callback != null) {
            callback.onTextureBound(textureUnit, textureId);
        }
    }

    /** Escape hatch: -Dactinium.glsmHooksAlwaysActive=true forces the consumer gate on. */
    private static final boolean ALWAYS_ACTIVE = Boolean.getBoolean("actinium.glsmHooksAlwaysActive");

    /**
     * Cheap gate for per-call snapshot/event work in GLStateManager hot paths. Written by the
     * pipeline host (Iris PipelineManager): true while a consumer with observable side effects
     * (a DeferredWorldRenderingPipeline) is active, false otherwise. Defaults to true so behavior
     * is unchanged when no host maintains it.
     */
    public static volatile boolean consumersActive = true;

    /** Whether hook consumers may act on events, i.e. snapshots and event posts must run. */
    public static boolean hasActiveConsumers() {
        return ALWAYS_ACTIVE || consumersActive;
    }

    /** Dispatches the shader pack reload notification to the host when one has been registered. */
    public static void notifyShaderPackReloaded() {
        final Runnable callback = shaderPackReloaded;
        if (callback != null) {
            callback.run();
        }
    }

    // The Angelica event-bus surface (14 EventBus fields + shared event instances) is not
    // carried over: its only consumers were the host's Iris pipeline hooks, which have no
    // celtium counterpart. State-change notification inside celtium flows through
    // LightStateRegister dirty flags (see the Lane 2 <-> Lane 3 sync contract).

    private GLSMHooks() {
    }
}
