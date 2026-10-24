package org.taumc.celeritas.impl.render.producer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;

import java.nio.FloatBuffer;
import java.util.Random;

/**
 * The end portal and end gateway as one producer: every portal block of a block-entity pass is collected, and each of
 * the fifteen (gateway: sixteen) layers is then drawn once for all of them.
 * <p>
 * Measured need (leg {@code tickjfr-celtium-2}, AMD, world): {@code TileEntityEndPortalRenderer.render} was 14.6% of
 * the client thread - per block, fifteen draws, each with its own texgen setup, texture-matrix stack and offer. The
 * layers' state does not depend on the block: the colours come from a {@code Random} reseeded to 31100 on every call,
 * and the texture matrix is the frame's projection and model view. Only the number of layers (by distance), the faces
 * and the top offset are per block, so a layer drawn for all blocks at once is the same picture with one offer per
 * layer instead of one per block and layer.
 * <p>
 * What changes is order: vanilla draws block A's layers, then block B's. Within a block the layers keep their order
 * (layer 0 alpha-blends over the scene, the rest add), and portal faces of different blocks do not overlap on screen
 * except through the depth test, which the batch keeps. The batch is drawn at the end of the block-entity pass rather
 * than at each block's place in it.
 * <p>
 * Collection only happens inside {@link #begin()}/{@link #flush()}, which the block-entity pass brackets; a portal
 * rendered anywhere else is left to vanilla. {@code -Dceltium.native.endPortalBatch=false} turns the producer off.
 * Threading: render thread only.
 */
public final class EndPortalProducer {
    public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("celtium.native.endPortalBatch"));

    private static final ResourceLocation END_SKY_TEXTURE = new ResourceLocation("textures/environment/end_sky.png");
    private static final ResourceLocation END_PORTAL_TEXTURE = new ResourceLocation("textures/entity/end_portal.png");
    private static final Random RANDOM = new Random(31100L);
    private static final FloatBuffer MODELVIEW = GLAllocation.createDirectFloatBuffer(16);
    private static final FloatBuffer PROJECTION = GLAllocation.createDirectFloatBuffer(16);
    private static final FloatBuffer PLANE = GLAllocation.createDirectFloatBuffer(16);

    /** Face bits, in vanilla's emission order. */
    public static final int SOUTH = 1, NORTH = 2, EAST = 4, WEST = 8, DOWN = 16, UP = 32;

    private static final Batch PORTALS = new Batch();
    private static final Batch GATEWAYS = new Batch();
    private static boolean collecting;
    private static long batchedBlocks;
    private static long flushes;

    private EndPortalProducer() {
    }

    /** Opens collection for one block-entity pass. */
    public static void begin() {
        collecting = ENABLED;
    }

    /** {@return whether a portal render should be collected instead of drawn now}. */
    public static boolean collecting() {
        return collecting;
    }

    /**
     * Collects one block. {@code gateway} is true for the end gateway's body, which vanilla draws with fog disabled,
     * one extra layer and a full-height top.
     */
    public static void collect(boolean gateway, double x, double y, double z, int faces, int passes, float offset) {
        (gateway ? GATEWAYS : PORTALS).add(x, y, z, faces, passes, offset);
    }

    /** Draws everything collected since {@link #begin()} and closes collection. */
    public static void flush() {
        collecting = false;
        if (PORTALS.count == 0 && GATEWAYS.count == 0) {
            return;
        }
        flushes++;
        if (PORTALS.count > 0) {
            draw(PORTALS);
        }
        if (GATEWAYS.count > 0) {
            GlStateManager.disableFog();
            draw(GATEWAYS);
            GlStateManager.enableFog();
        }
        PORTALS.count = 0;
        GATEWAYS.count = 0;
    }

    /** {@return a one-line census}, for the frame report. */
    public static String describe() {
        return "endPortalBatch enabled=" + ENABLED + " blocks=" + batchedBlocks + " flushes=" + flushes;
    }

    /** {@code TileEntityEndPortalRenderer.render}, with the block loop moved inside the layer loop. */
    private static void draw(Batch batch) {
        batchedBlocks += batch.count;
        Minecraft mc = Minecraft.getMinecraft();
        GlStateManager.disableLighting();
        RANDOM.setSeed(31100L);
        GlStateManager.getFloat(2982, MODELVIEW);
        GlStateManager.getFloat(2983, PROJECTION);
        int layers = 0;
        for (int i = 0; i < batch.count; i++) {
            layers = Math.max(layers, batch.passes[i]);
        }
        boolean portalTexture = false;
        for (int j = 0; j < layers; ++j) {
            GlStateManager.pushMatrix();
            float f1 = 2.0F / (float) (18 - j);
            if (j == 0) {
                mc.getTextureManager().bindTexture(END_SKY_TEXTURE);
                f1 = 0.15F;
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            }
            if (j >= 1) {
                mc.getTextureManager().bindTexture(END_PORTAL_TEXTURE);
                portalTexture = true;
                mc.entityRenderer.setupFogColor(true);
            }
            if (j == 1) {
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
            }
            GlStateManager.texGen(GlStateManager.TexGen.S, 9216);
            GlStateManager.texGen(GlStateManager.TexGen.T, 9216);
            GlStateManager.texGen(GlStateManager.TexGen.R, 9216);
            GlStateManager.texGen(GlStateManager.TexGen.S, 9474, plane(1.0F, 0.0F, 0.0F, 0.0F));
            GlStateManager.texGen(GlStateManager.TexGen.T, 9474, plane(0.0F, 1.0F, 0.0F, 0.0F));
            GlStateManager.texGen(GlStateManager.TexGen.R, 9474, plane(0.0F, 0.0F, 1.0F, 0.0F));
            GlStateManager.enableTexGenCoord(GlStateManager.TexGen.S);
            GlStateManager.enableTexGenCoord(GlStateManager.TexGen.T);
            GlStateManager.enableTexGenCoord(GlStateManager.TexGen.R);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(5890);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.translate(0.5F, 0.5F, 0.0F);
            GlStateManager.scale(0.5F, 0.5F, 1.0F);
            float f2 = (float) (j + 1);
            GlStateManager.translate(17.0F / f2, (2.0F + f2 / 1.5F) * ((float) Minecraft.getSystemTime() % 800000.0F / 800000.0F), 0.0F);
            GlStateManager.rotate((f2 * f2 * 4321.0F + f2 * 9.0F) * 2.0F, 0.0F, 0.0F, 1.0F);
            GlStateManager.scale(4.5F - f2 / 4.0F, 4.5F - f2 / 4.0F, 1.0F);
            GlStateManager.multMatrix(PROJECTION);
            GlStateManager.multMatrix(MODELVIEW);
            float r = (RANDOM.nextFloat() * 0.5F + 0.1F) * f1;
            float g = (RANDOM.nextFloat() * 0.5F + 0.4F) * f1;
            float b = (RANDOM.nextFloat() * 0.5F + 0.5F) * f1;
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(7, DefaultVertexFormats.POSITION_COLOR);
            for (int i = 0; i < batch.count; i++) {
                if (batch.passes[i] > j) {
                    emit(buffer, batch.x[i], batch.y[i], batch.z[i], batch.faces[i], batch.offset[i], r, g, b);
                }
            }
            tessellator.draw();
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(5888);
            mc.getTextureManager().bindTexture(END_SKY_TEXTURE);
        }
        GlStateManager.disableBlend();
        GlStateManager.disableTexGenCoord(GlStateManager.TexGen.S);
        GlStateManager.disableTexGenCoord(GlStateManager.TexGen.T);
        GlStateManager.disableTexGenCoord(GlStateManager.TexGen.R);
        GlStateManager.enableLighting();
        if (portalTexture) {
            mc.entityRenderer.setupFogColor(false);
        }
    }

    private static void emit(BufferBuilder buffer, double x, double y, double z, int faces, float f, float r, float g,
            float b) {
        if ((faces & SOUTH) != 0) {
            buffer.pos(x, y, z + 1.0D).color(r, g, b, 1.0F).endVertex();
            buffer.pos(x + 1.0D, y, z + 1.0D).color(r, g, b, 1.0F).endVertex();
            buffer.pos(x + 1.0D, y + 1.0D, z + 1.0D).color(r, g, b, 1.0F).endVertex();
            buffer.pos(x, y + 1.0D, z + 1.0D).color(r, g, b, 1.0F).endVertex();
        }
        if ((faces & NORTH) != 0) {
            buffer.pos(x, y + 1.0D, z).color(r, g, b, 1.0F).endVertex();
            buffer.pos(x + 1.0D, y + 1.0D, z).color(r, g, b, 1.0F).endVertex();
            buffer.pos(x + 1.0D, y, z).color(r, g, b, 1.0F).endVertex();
            buffer.pos(x, y, z).color(r, g, b, 1.0F).endVertex();
        }
        if ((faces & EAST) != 0) {
            buffer.pos(x + 1.0D, y + 1.0D, z).color(r, g, b, 1.0F).endVertex();
            buffer.pos(x + 1.0D, y + 1.0D, z + 1.0D).color(r, g, b, 1.0F).endVertex();
            buffer.pos(x + 1.0D, y, z + 1.0D).color(r, g, b, 1.0F).endVertex();
            buffer.pos(x + 1.0D, y, z).color(r, g, b, 1.0F).endVertex();
        }
        if ((faces & WEST) != 0) {
            buffer.pos(x, y, z).color(r, g, b, 1.0F).endVertex();
            buffer.pos(x, y, z + 1.0D).color(r, g, b, 1.0F).endVertex();
            buffer.pos(x, y + 1.0D, z + 1.0D).color(r, g, b, 1.0F).endVertex();
            buffer.pos(x, y + 1.0D, z).color(r, g, b, 1.0F).endVertex();
        }
        if ((faces & DOWN) != 0) {
            buffer.pos(x, y, z).color(r, g, b, 1.0F).endVertex();
            buffer.pos(x + 1.0D, y, z).color(r, g, b, 1.0F).endVertex();
            buffer.pos(x + 1.0D, y, z + 1.0D).color(r, g, b, 1.0F).endVertex();
            buffer.pos(x, y, z + 1.0D).color(r, g, b, 1.0F).endVertex();
        }
        if ((faces & UP) != 0) {
            buffer.pos(x, y + (double) f, z + 1.0D).color(r, g, b, 1.0F).endVertex();
            buffer.pos(x + 1.0D, y + (double) f, z + 1.0D).color(r, g, b, 1.0F).endVertex();
            buffer.pos(x + 1.0D, y + (double) f, z).color(r, g, b, 1.0F).endVertex();
            buffer.pos(x, y + (double) f, z).color(r, g, b, 1.0F).endVertex();
        }
    }

    private static FloatBuffer plane(float a, float b, float c, float d) {
        PLANE.clear();
        PLANE.put(a).put(b).put(c).put(d);
        PLANE.flip();
        return PLANE;
    }

    /** Primitive arrays, reused across frames: one block costs a few array writes, not an object. */
    private static final class Batch {
        int count;
        double[] x = new double[64], y = new double[64], z = new double[64];
        int[] faces = new int[64], passes = new int[64];
        float[] offset = new float[64];

        void add(double px, double py, double pz, int faceBits, int layerCount, float top) {
            if (this.count == this.x.length) {
                int size = this.count * 2;
                this.x = java.util.Arrays.copyOf(this.x, size);
                this.y = java.util.Arrays.copyOf(this.y, size);
                this.z = java.util.Arrays.copyOf(this.z, size);
                this.faces = java.util.Arrays.copyOf(this.faces, size);
                this.passes = java.util.Arrays.copyOf(this.passes, size);
                this.offset = java.util.Arrays.copyOf(this.offset, size);
            }
            this.x[this.count] = px;
            this.y[this.count] = py;
            this.z[this.count] = pz;
            this.faces[this.count] = faceBits;
            this.passes[this.count] = layerCount;
            this.offset[this.count] = top;
            this.count++;
        }
    }
}
