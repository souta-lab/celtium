package org.embeddedt.embeddium.impl.render.chunk;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.gl.shader.*;
import org.embeddedt.embeddium.impl.render.chunk.fog.FogService;
import org.embeddedt.embeddium.impl.render.chunk.light.DynamicLightService;
import org.embeddedt.embeddium.impl.render.chunk.shader.*;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class ShaderChunkRenderer implements ChunkRenderer {
    private static final Logger LOGGER = LogManager.getLogger(ShaderChunkRenderer.class);

    private final Map<ChunkShaderOptions, @Nullable GlProgram<ChunkShaderInterface>> programs = new Object2ObjectOpenHashMap<>();

    protected final RenderPassConfiguration<?> renderPassConfiguration;

    protected final RenderDevice device;

    protected GlProgram<ChunkShaderInterface> activeProgram;

    protected final boolean enableLegacyGLPatches;

    protected final ChunkShaderEnvironment environment;

    private @Nullable TerrainProgramProvider programProvider;

    public ShaderChunkRenderer(RenderDevice device, RenderPassConfiguration<?> renderPassConfiguration, FogService fogService) {
        this(device, renderPassConfiguration, fogService, ShaderLoader.useLegacyGlsl());
    }

    ShaderChunkRenderer(RenderDevice device, RenderPassConfiguration<?> renderPassConfiguration, FogService fogService,
                        boolean enableLegacyGLPatches) {
        this.device = device;
        this.environment = new ChunkShaderEnvironment(fogService);
        this.renderPassConfiguration = renderPassConfiguration;
        this.enableLegacyGLPatches = enableLegacyGLPatches;
    }

    /** Installs or clears the optional terrain program override provider (null restores default behavior). */
    public void setProgramProvider(@Nullable TerrainProgramProvider programProvider) {
        this.programProvider = programProvider;
    }

    /** {@return the installed terrain program override provider}, or {@code null} when none is attached. */
    public @Nullable TerrainProgramProvider getProgramProvider() {
        return this.programProvider;
    }

    /** Queries the attached provider for {@code pass}, returning {@code null} when no provider is installed. */
    protected @Nullable TerrainProgramProvider.PassOverride resolvePassOverride(TerrainRenderPass pass) {
        return this.programProvider != null ? this.programProvider.resolvePass(pass) : null;
    }

    protected @Nullable GlProgram<ChunkShaderInterface> compileProgram(ChunkShaderOptions options) {
        GlProgram<ChunkShaderInterface> program = this.programs.get(options);

        if (program == null && !this.programs.containsKey(options)) {
            try {
                program = this.createShader("blocks/block_layer_opaque", options);
            } catch(Exception e) {
                LOGGER.error("There was an error creating a chunk program. Terrain will not render until this is fixed.", e);
            }
            this.programs.put(options, program);
        }

        return program;
    }

    protected GlProgram<ChunkShaderInterface> createShader(String path, ChunkShaderOptions options) {
        ShaderConstants constants = options.constants();

        List<GlShader> loadedShaders = new ArrayList<>();

        loadedShaders.add(ShaderLoader.loadShader(ShaderType.VERTEX,
                "sodium:" + path + ".vsh", constants));

        loadedShaders.add(ShaderLoader.loadShader(ShaderType.FRAGMENT,
                "sodium:" + path + ".fsh", constants));

        try {
            var builder = GlProgram.builder("sodium:chunk_shader");
            loadedShaders.forEach(builder::attachShader);
            int i = 0;
            for (var attr : options.pass().vertexType().getVertexFormat().getAttributes()) {
                builder.bindAttribute(attr.getName(), i++);
            }
            if (!this.enableLegacyGLPatches) {
                builder.bindFragmentData("fragColor", ChunkShaderBindingPoints.FRAG_COLOR);
            }
            return builder.link((shader) -> new DefaultChunkShaderInterface(shader, options, this.environment));
        } finally {
            loadedShaders.forEach(GlShader::delete);
        }
    }

    protected List<ChunkShaderComponent.Factory<?>> getShaderComponents() {
        var componentFactories = new ArrayList<ChunkShaderComponent.Factory<?>>(4);
        componentFactories.add(this.environment.fogService().getFogMode());
        return componentFactories;
    }

    /**
     * The fog inputs this renderer uses. A backend that draws terrain itself needs the same values; reading them
     * from GL state instead would silently diverge when a fog service provides them procedurally.
     */
    public FogService getFogService() {
        return this.environment.fogService();
    }

    /**
     * The shading-time dynamic light sources available to a backend that draws terrain itself. The
     * default is {@link DynamicLightService#EMPTY} - the vanilla look, in which a held torch lights
     * nothing - because the service needs game state only the platform renderer has; the platform's
     * renderer overrides this with its own implementation.
     */
    public DynamicLightService getDynamicLightService() {
        return DynamicLightService.EMPTY;
    }

    protected void begin(TerrainRenderPass pass) {
        pass.startDrawing();

        // Phase 0 seam: query the provider when attached; actual program/vertex/cull replacement lands in Phase 1.
        resolvePassOverride(pass);

        ChunkShaderOptions options = new ChunkShaderOptions(getShaderComponents(), pass);

        this.activeProgram = this.compileProgram(options);

        if (this.activeProgram != null) {
            this.activeProgram.bind();
            this.activeProgram.getInterface()
                    .setupState(pass);
        }
    }

    protected void end(TerrainRenderPass pass) {
        if (this.activeProgram != null) {
            this.activeProgram.getInterface().restoreState();
            this.activeProgram.unbind();
            this.activeProgram = null;
        }


        pass.endDrawing();
    }

    @Override
    public void delete(CommandList commandList) {
        this.programs.values().stream().filter(Objects::nonNull)
                .forEach(GlProgram::delete);
    }

    @Override
    public RenderPassConfiguration<?> getRenderPassConfiguration() {
        return this.renderPassConfiguration;
    }
}
