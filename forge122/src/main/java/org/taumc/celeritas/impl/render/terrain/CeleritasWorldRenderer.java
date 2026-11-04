package org.taumc.celeritas.impl.render.terrain;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.DestroyBlockProgress;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.client.MinecraftForgeClient;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.embeddedt.embeddium.impl.render.terrain.SimpleWorldRenderer;
import org.taumc.celeritas.impl.render.terrain.fog.GLStateManagerFogService;
import org.taumc.celeritas.CeleritasVintage;
import org.taumc.celeritas.mixin.core.terrain.ActiveRenderInfoAccessor;

import java.util.*;

/**
 * Provides an extension to vanilla's {@link net.minecraft.client.renderer.RenderGlobal}.
 */
public class CeleritasWorldRenderer extends SimpleWorldRenderer<WorldClient, VintageRenderSectionManager, BlockRenderLayer, TileEntity, CeleritasWorldRenderer.TileEntityRenderContext>  {

    /**
     * The capture-condition counters for the validation fixture, without building any debug strings.
     * See {@code RenderSectionManager.celtium$captureCountersPacked}.
     */
    public long celtium$captureCountersPacked() {
        var manager = this.getRenderSectionManager();
        return manager == null ? -1L : manager.celtium$captureCountersPacked();
    }

    /** Bounded report countdown for the render lists. */
    private int celtium$listReportCountdown;

    /**
     * The layers whose geometry has been reported once, by name.
     * <p>
     * A custom layer (GTCEu adds {@code BLOOM} to {@code BlockRenderLayer}) reaches {@code drawChunkLayer} whether or
     * not any section was built for it: the pass exists because the render-pass configuration creates one for every
     * enum constant, and the draw is a no-op when the lists hold no geometry for that pass. The two cases look the
     * same in a picture - nothing appears - so the first draw of each layer reports whether the lists carry the pass.
     */
    private final java.util.Set<String> celtium$layerGeometryReported = new java.util.HashSet<String>();

    /** Draws seen per layer, so the geometry census waits for a world that has been built rather than the loading screen. */
    private final java.util.Map<String, Integer> celtium$layerDrawCounts =
            new java.util.HashMap<String, Integer>();

    /** The draw at which each layer's geometry is reported: after the first frames, when the lists hold the terrain. */
    private static final int LAYER_GEOMETRY_REPORT_DRAW = 120;
    public record TileEntityRenderContext(Map<Integer, DestroyBlockProgress> damagedBlocks, float partialTicks) {}

    /**
     * @return The CeleritasWorldRenderer based on the current dimension
     */
    public static CeleritasWorldRenderer instance() {
        return SimpleWorldRenderer.Provider.getWorldRenderer(Minecraft.getMinecraft().renderGlobal);
    }

    /**
     * @return The CeleritasWorldRenderer based on the current dimension, or null if none is attached
     */
    public static CeleritasWorldRenderer instanceNullable() {
        return SimpleWorldRenderer.Provider.getWorldRendererNullable(Minecraft.getMinecraft().renderGlobal);
    }

    @Override
    public int getMinimumBuildHeight() {
        return 0;
    }

    @Override
    public int getMaximumBuildHeight() {
        return this.world.getHeight();
    }

    @Override
    public int getEffectiveRenderDistance() {
        return Minecraft.getMinecraft().gameSettings.renderDistanceChunks;
    }

    @Override
    protected ChunkRenderMatrices createChunkRenderMatrices() {
        // EntityRenderer sets up the camera and updates ActiveRenderInfo before drawing terrain. Under
        // GL-less, the query entry points now read the renderer's real state mirror, including Forge camera
        // events, interpolated rotation, eye height and the current FOV. Keep these in OpenGL convention;
        // the Vulkan backend remaps clip depth exactly once when it pushes the projection.
        return new ChunkRenderMatrices(ActiveRenderInfoAccessor.getProjectionMatrix(), ActiveRenderInfoAccessor.getModelViewMatrix());
    }

    @Override
    protected VintageRenderSectionManager createRenderSectionManager(CommandList commandList) {
        return VintageRenderSectionManager.create(chooseVertexType(), this.world, this.getEffectiveRenderDistance(), commandList);
    }

    /**
     * Performs a render pass for the given {@link BlockRenderLayer} and draws all visible chunks for it.
     */
    /** Frames between tracker reports; a dimension change is visible within a second at 60 fps. */
    private int celtium$trackerReportCountdown = 20;

    /**
     * Reports the chunk tracker's known and ready counts, and the section count, so a leg can say which side of the
     * terrain pipeline is stuck. Logged from the per-layer draw because that is the once-per-frame entry point this
     * implementation already has, throttled to a line every twenty frames.
     */
    private void celtium$reportTrackerState() {
        if (!org.soutalab.celtium.vulkan.PeriodicLog.ENABLED || --this.celtium$trackerReportCountdown > 0) {
            return;
        }
        this.celtium$trackerReportCountdown = 20;
        try {
            net.minecraft.client.multiplayer.WorldClient world = net.minecraft.client.Minecraft.getMinecraft().world;
            if (world == null) {
                return;
            }
            org.embeddedt.embeddium.impl.render.chunk.map.ChunkTracker tracker =
                    org.embeddedt.embeddium.impl.render.chunk.map.ChunkTrackerHolder.get(world);
            int sections = this.getRenderSectionManager() != null
                    ? this.getRenderSectionManager().getTotalSections() : -1;
            System.out.println("[Celtium] CELTIUM_TRACKER known=" + tracker.knownChunkCount()
                    + " ready=" + tracker.readyChunkCount()
                    + " sections=" + sections
                    // Visible and queued are the two counts that say whether the pass has work. Measured 2026-10-15
                    // (leg gv-cohort-vis1): sections=3920 with visible=1 - so the visibility search is the limit, and
                    // an unbuilt graph leaves that search with only its starting section, which is exactly one. The
                    // queue state says whether the graph still has work waiting or has gone quiet.
                    + " visible=" + (this.getRenderSectionManager() != null
                            ? this.getRenderSectionManager().getVisibleChunkCount() : -1)
                    + " queueEmpty=" + (this.getRenderSectionManager() != null
                            ? this.getRenderSectionManager().getBuilder().isBuildQueueEmpty() : true)
                    + " dimension=" + world.provider.getDimension()
                    + " worldFrames=" + this.celtium$trackerReportCountdown);
        } catch (Throwable error) {
            System.out.println("[Celtium] CELTIUM_TRACKER unavailable: " + error);
        }
    }

    @Override
    public void drawChunkLayer(BlockRenderLayer renderLayer, double x, double y, double z) {
        this.celtium$reportTrackerState();
        this.celtium$reportLayerGeometry(renderLayer);

        // Measured 2026-10-15 (leg gv-cohort-fru7): visible=83 while no draw reached the backend, so the question
        // is whether the render lists the backend iterates are empty. SectionCollector.visit adds a section to a
        // render list only when its flags are non-zero, and flags are set from build results - so an empty list
        // means the sections are visible but not built, which is a different next step from a culling one.
        if (org.soutalab.celtium.vulkan.PeriodicLog.ENABLED && this.celtium$listReportCountdown-- <= 0) {
            this.celtium$listReportCountdown = 40;
            var manager = this.getRenderSectionManager();
            if (manager != null) {
                int lists = 0;
                int sections = 0;
                var listIterator = manager.getRenderLists().iterator();
                while (listIterator.hasNext()) {
                    var list = listIterator.next();
                    lists++;
                    var iterator = list.sectionsWithGeometryIterator();
                    if (iterator != null) {
                        while (iterator.hasNext()) {
                            iterator.nextByteAsInt();
                            sections++;
                        }
                    }
                }
                // The manager already reports the build queues - "Chunk Queues: U=.. (P0=.. | P1=.. | P2=..) S=..", where
                // U is the results waiting to be processed and P2 is the INITIAL_BUILD queue that feeds first-time
                // meshes. Reading it here rather than adding a counter of my own: the numbers that separate "no task
                // was ever queued" from "tasks ran and produced nothing" already exist.
                // Only the queue line is wanted here: asking getDebugStrings() for it built the whole F3 collection -
                // the backend's transfer-queue and verification summaries included - once every forty frames, ~12% of
                // the client thread by JFR. The manager now answers with that one line.
                String queues = " " + manager.getBuildQueueDebugLine();
                System.out.println("[Celtium] CELTIUM_RENDERLIST lists=" + lists + " sectionsWithGeometry=" + sections
                        + " layer=" + renderLayer + " world=" + System.identityHashCode(this.world) + queues);
            }
        }

        super.drawChunkLayer(renderLayer, x, y, z);

        GlStateManager.resetColor();
    }

    /**
     * Reports, once per layer, whether the render lists carry the passes that layer maps to.
     * <p>
     * {@code SortedRenderLists.hasPass} answers from the regions' own pass sets, which are filled from build results -
     * so a false here means no section was built with geometry for that pass, and a true means the geometry exists and
     * the question moves to the draw. The layer name is the key because the enum is extended at runtime (GTCEu's
     * {@code BLOOM}) and a name is what a log reader can match against the mod.
     */
    private void celtium$reportLayerGeometry(BlockRenderLayer renderLayer) {
        String name = renderLayer.name();
        if (this.celtium$layerGeometryReported.contains(name)) {
            return;
        }
        Integer count = this.celtium$layerDrawCounts.get(name);
        int draws = count == null ? 0 : count.intValue();
        if (draws < LAYER_GEOMETRY_REPORT_DRAW) {
            this.celtium$layerDrawCounts.put(name, Integer.valueOf(draws + 1));
            return;
        }
        try {
            var manager = this.getRenderSectionManager();
            if (manager == null) {
                return;
            }
            var lists = manager.getRenderLists();
            // The first draws of a world are the loading screen's empty lists: reporting there says "no geometry" for
            // every layer and settles nothing. The count above waits for the world; this checks the lists are carrying
            // regions at all before the pass flags are believed.
            if (lists.getNumRegions() == 0) {
                return;
            }
            this.celtium$layerGeometryReported.add(name);
            var passes = manager.getRenderPassConfiguration().vanillaRenderStages().get(renderLayer);
            StringBuilder report = new StringBuilder();
            if (passes != null) {
                for (var pass : passes) {
                    report.append(pass).append('=').append(lists.hasPass(pass)).append(' ');
                }
            }
            System.out.println("[Celtium] CELTIUM_LAYER_GEOMETRY layer=" + renderLayer
                    + " regions=" + lists.getNumRegions() + " passes=[" + report.toString().trim() + "]");
        } catch (Throwable error) {
            System.out.println("[Celtium] CELTIUM_LAYER_GEOMETRY unavailable: " + error);
        }
    }

    public static CameraState captureCameraState(double ticks) {
        Entity viewEntity = Objects.requireNonNull(Minecraft.getMinecraft().getRenderViewEntity(), "Client must have view entity");

        double x = viewEntity.lastTickPosX + (viewEntity.posX - viewEntity.lastTickPosX) * ticks;
        double y = viewEntity.lastTickPosY + (viewEntity.posY - viewEntity.lastTickPosY) * ticks + (double) viewEntity.getEyeHeight();
        double z = viewEntity.lastTickPosZ + (viewEntity.posZ - viewEntity.lastTickPosZ) * ticks;

        float pitch = viewEntity.rotationPitch;
        float yaw = viewEntity.rotationYaw;
        float fogDistance = GLStateManagerFogService.INSTANCE.getFogCutoff();

        return new CameraState(x, y, z, pitch, yaw, fogDistance);
    }


    @Override
    protected void renderBlockEntityList(List<TileEntity> list, TileEntityRenderContext tileEntityRenderContext) {
        int pass = MinecraftForgeClient.getRenderPass();
        float partialTicks = tileEntityRenderContext.partialTicks;

        for (TileEntity tileEntity : list) {
            if(!tileEntity.shouldRenderInPass(pass))
                continue;

            try {
                TileEntityRendererDispatcher.instance.render(tileEntity, partialTicks, -1);
            } catch(RuntimeException e) {
                if(tileEntity.isInvalid()) {
                    CeleritasVintage.logger().error("Suppressing crash from invalid tile entity", e);
                } else {
                    throw e;
                }
            }
        }
    }

    @Override
    public int renderBlockEntities(TileEntityRenderContext tileEntityRenderContext) {
        int pass = MinecraftForgeClient.getRenderPass();
        TileEntityRendererDispatcher.instance.preDrawBatch();
        org.taumc.celeritas.impl.render.producer.EndPortalProducer.begin();
        int count;
        try {
            count = super.renderBlockEntities(tileEntityRenderContext);
        } finally {
            // Drawn at the end of the pass, before Forge's own fast-TESR batch, and also when a renderer threw: a
            // collected portal must never be carried into a later pass or frame.
            org.taumc.celeritas.impl.render.producer.EndPortalProducer.flush();
        }
        TileEntityRendererDispatcher.instance.drawBatch(pass);
        return count;
    }

    /**
     * Returns whether or not the entity intersects with any visible chunks in the graph.
     * @return True if the entity is visible, otherwise false
     */
    public boolean isEntityVisible(Entity entity) {
        if (!CeleritasVintage.options().performance.useEntityCulling || this.renderSectionManager.isInShadowPass()) {
            return true;
        }

        // Ensure entities with outlines or nametags are always visible
        if (entity.isGlowing() || entity.getAlwaysRenderNameTagForRender()) {
            return true;
        }

        //? if <1.21.2
        AxisAlignedBB box = entity.getRenderBoundingBox();
        //? if >=1.21.2
        /*AABB box = renderer.getBoundingBoxForCulling(entity);*/

        return this.isBoxVisible(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    private static final boolean FORCE_COMPACT_FORMAT = Boolean.getBoolean("celtium.terrain.forceCompactFormat");

    private ChunkVertexType chooseVertexType() {
        // Diagnostic: the compact path builds its colours in VintageBlockRenderer, the vanilla-like path copies the
        // game's own vertex data. Forcing one or the other separates "the brightness term is missing from one path"
        // from "the sampler is wrong", which the frame cannot distinguish on its own.
        if (FORCE_COMPACT_FORMAT) {
            return ChunkMeshFormats.COMPACT;
        }
        if (!CeleritasVintage.options().performance.useCompactVertexFormat) {
            return ChunkMeshFormats.VANILLA_LIKE;
        }

        return ChunkMeshFormats.COMPACT;
    }
}
