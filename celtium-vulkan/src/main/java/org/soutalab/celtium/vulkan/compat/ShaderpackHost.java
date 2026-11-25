package org.soutalab.celtium.vulkan.compat;

import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;

import java.lang.reflect.Method;

/**
 * The host half of the shader pack connection: where the pack's programs meet celtium's own terrain
 * and frame graph. In the old architecture this meeting had no owner - Demonica drove the Celeritas
 * render loop, the backend asked a reflection bridge for the pack's terrain program inline, mid-draw,
 * and the pack's pass structure was whatever the GL framebuffer binds happened to trace out. The host
 * makes the meeting explicit and frame-scoped:
 * <ul>
 *   <li>it holds the active pack's {@link Profile} - which stages exist and how many targets and
 *   fullscreen passes they declare - set when a pack loads and cleared when it unloads;</li>
 *   <li>it builds the frame's {@link ShaderpackDag} from that profile, once, for the FrameGraph
 *   compiler;</li>
 *   <li>it resolves the terrain program for a stage through the pack's own entry point, the same
 *   reflection surface {@code DemonicaTerrainBridge} used, so celtium keeps no compile-time dependency
 *   on Demonica;</li>
 *   <li>it publishes the frame's cull plan: the shadow cull (light camera) and the main cull (player
 *   camera) are independent dispatches over the same resident sections, and the terrain stages consume
 *   them in parallel rather than the shadow pass re-walking the world on the CPU.</li>
 * </ul>
 * The host owns no Vulkan objects and records nothing; it is the contract the graph compiler and the
 * terrain engine both read at frame start.
 */
public final class ShaderpackHost {

    /**
     * What the loaded pack declares: whether it renders shadows, how many colour targets its gbuffers
     * stage writes, and how many fullscreen passes its deferred and composite stages run. A pack that
     * declares a stage it cannot fill is refused by the DAG validation, not patched over.
     */
    public record Profile(boolean shadowEnabled, int gbufferTargets, int deferredPasses,
            int compositePasses) {
    }

    /**
     * The pack's own entry points, reached reflectively: {@code ShaderTerrain.packProgram} answers the
     * program for a terrain pass under the pack (the {@code shadow} program while the shadow stage is
     * being rendered, {@code gbuffers_terrain} / {@code gbuffers_water} otherwise), and Iris's {@code
     * ShadowRenderingState} says which of those the pack is currently doing. Absent Demonica, every
     * answer is the vanilla one.
     */
    public interface PackProgramSource {
        /** {@return the pack's program for the pass}, or null when no pack supplies one. */
        GlProgram<? extends ChunkShaderInterface> packProgram(TerrainRenderPass pass,
                RenderPassConfiguration<?> configuration);

        /** {@return whether the pack is currently rendering its shadow stage}. */
        boolean shadowsBeingRendered();
    }

    /** The reflective source over Demonica/Iris; resolves once, then answers from the cached methods. */
    public static final class ReflectionPackProgramSource implements PackProgramSource {
        private volatile boolean resolved;
        private Method packProgramMethod;
        private volatile boolean shadowResolved;
        private Method shadowMethod;

        @Override
        @SuppressWarnings("unchecked")
        public GlProgram<? extends ChunkShaderInterface> packProgram(TerrainRenderPass pass,
                RenderPassConfiguration<?> configuration) {
            if (pass == null || configuration == null) {
                return null;
            }
            Method method = resolvePackProgram();
            if (method == null) {
                return null;
            }
            try {
                return (GlProgram<? extends ChunkShaderInterface>) method.invoke(null, pass, configuration);
            } catch (Throwable ignored) {
                return null;
            }
        }

        @Override
        public boolean shadowsBeingRendered() {
            Method method = resolveShadowState();
            if (method == null) {
                return false;
            }
            try {
                return Boolean.TRUE.equals(method.invoke(null));
            } catch (Throwable ignored) {
                return false;
            }
        }

        private Method resolvePackProgram() {
            if (this.resolved) {
                return this.packProgramMethod;
            }
            synchronized (this) {
                if (!this.resolved) {
                    try {
                        Class<?> cls = Class.forName("com.demonica.celeritas.terrain.ShaderTerrain", false,
                                ReflectionPackProgramSource.class.getClassLoader());
                        this.packProgramMethod = cls.getMethod("packProgram",
                                TerrainRenderPass.class, RenderPassConfiguration.class);
                    } catch (Throwable ignored) {
                        this.packProgramMethod = null;
                    }
                    this.resolved = true;
                }
                return this.packProgramMethod;
            }
        }

        private Method resolveShadowState() {
            if (this.shadowResolved) {
                return this.shadowMethod;
            }
            synchronized (this) {
                if (!this.shadowResolved) {
                    try {
                        Class<?> cls = Class.forName("net.coderbot.iris.shadows.ShadowRenderingState", false,
                                ReflectionPackProgramSource.class.getClassLoader());
                        this.shadowMethod = cls.getMethod("areShadowsCurrentlyBeingRendered");
                    } catch (Throwable ignored) {
                        this.shadowMethod = null;
                    }
                    this.shadowResolved = true;
                }
                return this.shadowMethod;
            }
        }
    }

    private final PackProgramSource source;
    private volatile Profile profile;
    private volatile ShaderpackDag dag;

    public ShaderpackHost(PackProgramSource source) {
        this.source = source;
    }

    /** A host over the reflective Demonica surface, the production wiring. */
    public static ShaderpackHost demonica() {
        return new ShaderpackHost(new ReflectionPackProgramSource());
    }

    /**
     * Sets the loaded pack's profile, or clears it with null when the pack unloads. The DAG is rebuilt
     * lazily on the next {@link #dag()} call; a profile the DAG validation refuses throws here at load
     * time - by building once eagerly - rather than on the first frame.
     */
    public void setProfile(Profile profile) {
        ShaderpackDag built = profile == null ? null : ShaderpackDag.standard(profile.shadowEnabled(),
                profile.gbufferTargets(), profile.deferredPasses(), profile.compositePasses());
        this.profile = profile;
        this.dag = built;
    }

    /** {@return whether a pack profile is loaded}; without one the frame takes the vanilla graph. */
    public boolean packActive() {
        return this.profile != null;
    }

    /** {@return the frame's pack DAG}, or null when no pack is active. */
    public ShaderpackDag dag() {
        return this.dag;
    }

    /**
     * {@return the terrain program the pack supplies for a terrain-drawing stage's pass} - the shadow
     * program while the pack renders its shadow stage, the gbuffers program otherwise - or null when
     * the stage draws no terrain or no pack supplies a program (the vanilla program then stands).
     */
    public GlProgram<? extends ChunkShaderInterface> terrainProgram(ShaderpackStage stage,
            TerrainRenderPass pass, RenderPassConfiguration<?> configuration) {
        if (stage == null || !stage.drawsTerrain()) {
            return null;
        }
        return this.source.packProgram(pass, configuration);
    }

    /** {@return whether the pack is currently inside its shadow stage}. */
    public boolean shadowsBeingRendered() {
        return this.source.shadowsBeingRendered();
    }
}
