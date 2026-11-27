package com.demonica.celeritas.terrain;

import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;

/**
 * Test stub of Demonica's shader-terrain entry point, standing in for the guest
 * class {@code DemonicaTerrainBridge} binds reflectively. It exists only on the
 * test classpath: its presence makes the bridge's resolution succeed, and its
 * switch lets a test make the guest throw - the outcome the bridge used to
 * collapse into the same silent {@code null} as "no pack program" (drawpath
 * audit DP-06).
 */
public final class ShaderTerrain {

    /** When set, {@link #packProgram} throws instead of answering. */
    public static volatile boolean throwOnCall;

    private ShaderTerrain() {
    }

    public static GlProgram<? extends ChunkShaderInterface> packProgram(
            TerrainRenderPass pass, RenderPassConfiguration<?> configuration) {
        if (throwOnCall) {
            throw new IllegalStateException("stub pack program failure");
        }
        return null;
    }

    /** Restores the quiet default (not throwing). */
    public static void resetStub() {
        throwOnCall = false;
    }
}
