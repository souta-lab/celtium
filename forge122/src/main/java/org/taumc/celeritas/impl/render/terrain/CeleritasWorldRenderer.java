package org.taumc.celeritas.impl.render.terrain;

import com.github.bsideup.jabel.Desugar;
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
    @Desugar
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
        if (--this.celtium$trackerReportCountdown > 0) {
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
        super.drawChunkLayer(renderLayer, x, y, z);

        GlStateManager.resetColor();
    }

    public static CameraState captureCameraState(double ticks) {
        Entity viewEntity = Objects.requireNonNull(Minecraft.getMinecraft().getRenderViewEntity(), "Client must have view entity");

        double x = viewEntity.lastTickPosX + (viewEntity.posX - viewEntity.lastTickPosX) * ticks;
        double y = viewEntity.lastTickPosY + (viewEntity.posY - viewEntity.lastTickPosY) * ticks + (double) viewEntity.getEyeHeight();
        double z = viewEntity.lastTickPosZ + (viewEntity.posZ - viewEntity.lastTickPosZ) * ticks;

        float pitch = viewEntity.rotationPitch;
        float yaw = viewEntity.rotationYaw;
        // Push the game's own fog state once per frame, so the Vulkan terrain backend never has to ask OpenGL for it
        // (completion condition (b)). GlStateManager.fogState is public and is exactly what the GL fog service reads.
        org.soutalab.celtium.vulkan.VulkanFogMirror.set(
                net.minecraft.client.renderer.GlStateManager.fogState.fog.currentState,
                net.minecraft.client.renderer.GlStateManager.fogState.mode);
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
        int count = super.renderBlockEntities(tileEntityRenderContext);
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

    private ChunkVertexType chooseVertexType() {
        // Diagnostic: the compact path builds its colours in VintageBlockRenderer, the vanilla-like path copies the
        // game's own vertex data. Forcing one or the other separates "the brightness term is missing from one path"
        // from "the sampler is wrong", which the frame cannot distinguish on its own.
        if (Boolean.getBoolean("celtium.terrain.forceCompactFormat")) {
            return ChunkMeshFormats.COMPACT;
        }
        if (!CeleritasVintage.options().performance.useCompactVertexFormat) {
            return ChunkMeshFormats.VANILLA_LIKE;
        }

        return ChunkMeshFormats.COMPACT;
    }
}
