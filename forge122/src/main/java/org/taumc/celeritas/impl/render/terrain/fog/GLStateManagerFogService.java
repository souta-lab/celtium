package org.taumc.celeritas.impl.render.terrain.fog;

import org.embeddedt.embeddium.impl.render.chunk.fog.FogService;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkFogMode;

public class GLStateManagerFogService implements FogService {
    private static org.soutalab.celtium.vulkan.ImmediateFogState state() {
        return org.soutalab.celtium.vulkan.ImmediateFogState.current();
    }
    public static final GLStateManagerFogService INSTANCE = new GLStateManagerFogService();

    @Override
    public float getFogEnd() {
        return state().getFogEnd();
    }

    @Override
    public float getFogStart() {
        return state().getFogStart();
    }

    @Override
    public float getFogDensity() {
        return state().getFogDensity();
    }

    @Override
    public int getFogShapeIndex() {
        return state().getFogShapeIndex();
    }

    @Override
    public float getFogCutoff() {
        return state().getFogCutoff();
    }

    @Override
    public float[] getFogColor() {
        return state().getFogColor();
    }

    @Override
    public ChunkFogMode getFogMode() {
        return state().getFogMode();
    }
}
