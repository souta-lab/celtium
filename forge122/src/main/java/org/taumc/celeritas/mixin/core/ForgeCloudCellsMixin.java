package org.taumc.celeritas.mixin.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.CloudRenderer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.taumc.celeritas.core.CeltiumNativeDraw;

/**
 * Forge's clouds with geometry that only covers the clouds.
 * <p>
 * Forge's {@code CloudRenderer} builds one mesh for the whole cloud field: a textured top and bottom plane over 12×12
 * sections, and side planes at every texel column of the radius whether a cloud is there or not, and lets the alpha
 * test cut the clouds out of them. Every masked plane still costs its fragments, twice (the depth pass and the colour
 * pass). Measured on the Intel UHD 630 world scene: the two cloud draws were 320 + 280 µs of a ~2.2 ms GPU frame, the
 * largest immediate draws of the frame, and Celeritas pays the same for the same mesh.
 * <p>
 * This keeps everything else of Forge's {@code render} - the translation, the texture matrix, the colour texture, the
 * blend, both colour-mask passes - and replaces only what the two {@code drawArrays} calls draw. The replacement is a
 * box per cloud texel in range, with the side faces that touch a neighbouring cloud left out. Each face samples its own
 * texel's centre, so the picture is the same texels Forge's alpha test keeps, at the same shades and alpha. The mesh
 * depends on which texels are in range, so it is rebuilt when the integer texel offset changes (every 12 blocks of
 * drift or movement), the cloud mode, or the render distance. {@code -Dceltium.native.cloudCells=false} keeps Forge's
 * mesh.
 */
@Mixin(value = CloudRenderer.class, remap = false)
public abstract class ForgeCloudCellsMixin {
    @Unique
    private static final boolean CELTIUM_CELLS = !"false".equalsIgnoreCase(
            System.getProperty("celtium.native.cloudCells"));
    @Unique
    private static final float CELTIUM_PX = 1 / 256F;
    @Unique
    private static final int CELTIUM_HEIGHT = 4;
    @Unique
    private static final float CELTIUM_ALPHA = 0.8F;
    /** Forge's vertex alpha is 0.8 and its alpha test passes above 0.1, so a texel counts when its alpha is over 0.125. */
    @Unique
    private static final int CELTIUM_TEXEL_ALPHA_MIN = 32;

    @Shadow(remap = false)
    private int cloudMode;
    @Shadow(remap = false)
    private int renderDistance;
    @Shadow(remap = false)
    @org.spongepowered.asm.mixin.Final
    private ResourceLocation texture;

    @Unique
    private int celtium$cloudTicks;
    @Unique
    private float celtium$partialTicks;
    @Unique
    private boolean[] celtium$mask;
    @Unique
    private int celtium$maskW;
    @Unique
    private int celtium$maskH;
    @Unique
    private VertexBuffer celtium$cells;
    @Unique
    private int celtium$builtU = Integer.MIN_VALUE;
    @Unique
    private int celtium$builtV = Integer.MIN_VALUE;
    @Unique
    private int celtium$builtMode = -1;
    @Unique
    private int celtium$builtDistance = -1;
    @Unique
    private static int celtium$builds;

    @Inject(method = "render", at = @At("HEAD"), remap = false, require = 0)
    private void celtium$noteFrame(int cloudTicks, float partialTicks, CallbackInfoReturnable<Boolean> info) {
        this.celtium$cloudTicks = cloudTicks;
        this.celtium$partialTicks = partialTicks;
    }

    @Inject(method = "onResourceManagerReload", at = @At("HEAD"), remap = false, require = 0)
    private void celtium$forgetMask(CallbackInfo info) {
        this.celtium$mask = null;
        this.celtium$builtMode = -1;
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/vertex/VertexBuffer;func_177358_a(I)V", remap = false),
            remap = false, require = 0)
    private void celtium$drawCells(VertexBuffer forgeMesh, int mode) {
        VertexBuffer cells = CELTIUM_CELLS ? celtium$cellsForThisFrame() : null;
        if (cells == null) {
            forgeMesh.drawArrays(mode);
            return;
        }
        cells.bindBuffer();
        cells.drawArrays(GL11.GL_QUADS);
        // Forge's mesh stays the bound one for the rest of its render, which unbinds it.
        forgeMesh.bindBuffer();
    }

    /** {@return the cell mesh for this frame's texel offset}, rebuilt when it moved, or null to keep Forge's mesh. */
    @Unique
    private VertexBuffer celtium$cellsForThisFrame() {
        Minecraft mc = Minecraft.getMinecraft();
        Entity entity = mc.getRenderViewEntity();
        if (entity == null || (this.cloudMode != 1 && this.cloudMode != 2)) {
            return null;
        }
        if (this.celtium$mask == null && !celtium$loadMask(mc)) {
            return null;
        }
        // Forge's own offset arithmetic, so the texel this mesh starts at is the one its texture matrix starts at.
        float partialTicks = this.celtium$partialTicks;
        double totalOffset = this.celtium$cloudTicks + partialTicks;
        double x = entity.prevPosX + (entity.posX - entity.prevPosX) * partialTicks + totalOffset * 0.03;
        double z = entity.prevPosZ + (entity.posZ - entity.prevPosZ) * partialTicks;
        int scale = this.cloudMode == 2 ? 12 : 8;
        if (this.cloudMode == 2) {
            z += 0.33 * scale;
        }
        int offU = celtium$fullCoord(x, scale);
        int offV = celtium$fullCoord(z, scale);
        if (this.celtium$cells == null || offU != this.celtium$builtU || offV != this.celtium$builtV
                || this.cloudMode != this.celtium$builtMode || this.renderDistance != this.celtium$builtDistance) {
            celtium$build(offU, offV, scale);
        }
        return this.celtium$cells;
    }

    @Unique
    private static int celtium$fullCoord(double coord, int scale) {
        return ((int) coord / scale) - (coord < 0 ? 1 : 0);
    }

    @Unique
    private boolean celtium$loadMask(Minecraft mc) {
        try (java.io.InputStream in = mc.getResourceManager().getResource(this.texture).getInputStream()) {
            java.awt.image.BufferedImage image = TextureUtil.readBufferedImage(in);
            int w = image.getWidth();
            int h = image.getHeight();
            int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
            boolean[] mask = new boolean[w * h];
            for (int i = 0; i < argb.length; i++) {
                mask[i] = (argb[i] >>> 24) > CELTIUM_TEXEL_ALPHA_MIN;
            }
            this.celtium$mask = mask;
            this.celtium$maskW = w;
            this.celtium$maskH = h;
            return true;
        } catch (java.io.IOException | RuntimeException failure) {
            System.out.println("[Celtium] CELTIUM_CLOUD_CELLS mask unavailable, keeping Forge's mesh: " + failure);
            return false;
        }
    }

    @Unique
    private boolean celtium$cloudAt(int offU, int offV, int i, int j) {
        int u = Math.floorMod(offU + i, this.celtium$maskW);
        int v = Math.floorMod(offV + j, this.celtium$maskH);
        return this.celtium$mask[v * this.celtium$maskW + u];
    }

    @Unique
    private void celtium$build(int offU, int offV, int scale) {
        boolean fancy = this.cloudMode == 2;
        // Forge's range: sections out to ceilToScale(renderDistance * 32) blocks either side of the camera.
        int extent = (int) Math.ceil((this.renderDistance * 2 * 16) / (float) scale);
        float bottom = fancy ? 0.7F : 1.0F;
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        int boxes = 0;
        for (int i = -extent; i < extent; i++) {
            for (int j = -extent; j < extent; j++) {
                if (!celtium$cloudAt(offU, offV, i, j)) {
                    continue;
                }
                boxes++;
                float x0 = i * scale;
                float x1 = x0 + scale;
                float z0 = j * scale;
                float z1 = z0 + scale;
                // The texel's centre, before Forge's texture matrix adds the integer offset.
                float u = (i + 0.5F) * CELTIUM_PX;
                float v = (j + 0.5F) * CELTIUM_PX;
                celtium$quad(buffer, u, v, bottom, x0, 0, z0, x1, 0, z0, x1, 0, z1, x0, 0, z1);
                if (!fancy) {
                    continue;
                }
                celtium$quad(buffer, u, v, 1.0F, x0, CELTIUM_HEIGHT, z0, x0, CELTIUM_HEIGHT, z1,
                        x1, CELTIUM_HEIGHT, z1, x1, CELTIUM_HEIGHT, z0);
                if (!celtium$cloudAt(offU, offV, i - 1, j)) {
                    celtium$quad(buffer, u, v, 0.9F, x0, 0, z0, x0, CELTIUM_HEIGHT, z0,
                            x0, CELTIUM_HEIGHT, z1, x0, 0, z1);
                }
                if (!celtium$cloudAt(offU, offV, i + 1, j)) {
                    celtium$quad(buffer, u, v, 0.9F, x1, 0, z1, x1, CELTIUM_HEIGHT, z1,
                            x1, CELTIUM_HEIGHT, z0, x1, 0, z0);
                }
                if (!celtium$cloudAt(offU, offV, i, j - 1)) {
                    celtium$quad(buffer, u, v, 0.8F, x1, 0, z0, x1, CELTIUM_HEIGHT, z0,
                            x0, CELTIUM_HEIGHT, z0, x0, 0, z0);
                }
                if (!celtium$cloudAt(offU, offV, i, j + 1)) {
                    celtium$quad(buffer, u, v, 0.8F, x0, 0, z1, x0, CELTIUM_HEIGHT, z1,
                            x1, CELTIUM_HEIGHT, z1, x1, 0, z1);
                }
            }
        }
        buffer.finishDrawing();
        buffer.reset();
        if (this.celtium$cells == null) {
            this.celtium$cells = new VertexBuffer(DefaultVertexFormats.POSITION_TEX_COLOR);
        }
        this.celtium$cells.bufferData(buffer.getByteBuffer());
        this.celtium$builtU = offU;
        this.celtium$builtV = offV;
        this.celtium$builtMode = this.cloudMode;
        this.celtium$builtDistance = this.renderDistance;
        celtium$builds++;
        if (celtium$builds <= 3 || celtium$builds % 50 == 0) {
            System.out.println("[Celtium] CELTIUM_CLOUD_CELLS build=" + celtium$builds + " boxes=" + boxes
                    + " extent=" + extent + " scale=" + scale + " fancy=" + fancy + " offset=" + offU + "," + offV);
        }
    }

    @Unique
    private static void celtium$quad(BufferBuilder buffer, float u, float v, float shade,
            float ax, float ay, float az, float bx, float by, float bz,
            float cx, float cy, float cz, float dx, float dy, float dz) {
        buffer.pos(ax, ay, az).tex(u, v).color(shade, shade, shade, CELTIUM_ALPHA).endVertex();
        buffer.pos(bx, by, bz).tex(u, v).color(shade, shade, shade, CELTIUM_ALPHA).endVertex();
        buffer.pos(cx, cy, cz).tex(u, v).color(shade, shade, shade, CELTIUM_ALPHA).endVertex();
        buffer.pos(dx, dy, dz).tex(u, v).color(shade, shade, shade, CELTIUM_ALPHA).endVertex();
    }
}
