package org.taumc.celeritas.impl.render.terrain.fog;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import org.embeddedt.embeddium.impl.render.chunk.fog.FogService;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkFogMode;

public class GLStateManagerFogService implements FogService {
    private static boolean nativeFog() { return org.taumc.celeritas.core.CeltiumNativeDraw.enabled(); }
    private static org.soutalab.celtium.vulkan.ImmediateFogState state() {
        return org.soutalab.celtium.vulkan.ImmediateFogState.current();
    }
    public static final GLStateManagerFogService INSTANCE = new GLStateManagerFogService();

    @Override
    public float getFogEnd() {
        if (nativeFog()) return state().getFogEnd();
        return GlStateManager.fogState.end;
    }

    @Override
    public float getFogStart() {
        if (nativeFog()) return state().getFogStart();
        return GlStateManager.fogState.start;
    }

    @Override
    public float getFogDensity() {
        if (nativeFog()) return state().getFogDensity();
        return GlStateManager.fogState.density;
    }

    @Override
    public int getFogShapeIndex() {
        if (nativeFog()) return state().getFogShapeIndex();
        return 0;
    }

    @Override
    public float getFogCutoff() {
        if (nativeFog()) return state().getFogCutoff();
        return GlStateManager.fogState.end;
    }

    @Override
    public float[] getFogColor() {
        if (nativeFog()) return state().getFogColor();
        EntityRenderer entityRenderer = Minecraft.getMinecraft().entityRenderer;
        return new float[]{entityRenderer.fogColorRed, entityRenderer.fogColorGreen, entityRenderer.fogColorBlue, 1.0F};
    }

    @Override
    public ChunkFogMode getFogMode() {
        if (nativeFog()) return state().getFogMode();
        if (!GlStateManager.fogState.fog.currentState) {
            return ChunkFogMode.NONE;
        }
        return ChunkFogMode.fromGLMode(GlStateManager.fogState.mode);
    }
}
