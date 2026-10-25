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
            if (j == 0) {
                // The positions are the same in every layer - only the colour and the set of blocks still in it
                // change - so they are built once per draw instead of once per layer through BufferBuilder's
                // per-element calls (the emit loop was most of this producer's cost in the warm profile).
                prepareVertices(batch, layers, buffer);
            }
            int colour = (int) (r * 255.0F) | ((int) (g * 255.0F) << 8) | ((int) (b * 255.0F) << 16) | (255 << 24);
            int vertices = layerVertices[j];
            int[] data = VERTICES;
            for (int v = 0; v < vertices; v++) {
                data[v * 4 + 3] = colour;
            }
            buffer.addVertexData(vertices * 4 == data.length ? data : java.util.Arrays.copyOf(data, vertices * 4));
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

    /** POSITION_COLOR vertices of every face of every block, four ints each (x, y, z, colour), blocks by passes. */
    private static int[] VERTICES = new int[0];
    /** Vertices that are still in layer j: the blocks are ordered by their layer count, so each layer is a prefix. */
    private static int[] layerVertices = new int[16];
    private static int[] order = new int[64];

    /**
     * Fills {@link #VERTICES} with the batch's faces in vanilla's vertex order, the blocks sorted by their layer
     * count (most first) so a layer's blocks are a prefix, and {@link #layerVertices} with each layer's prefix length.
     * Positions carry the buffer's translation exactly as {@code BufferBuilder.pos} would add it.
     */
    private static void prepareVertices(Batch batch, int layers, BufferBuilder buffer) {
        int n = batch.count;
        if (order.length < n) {
            order = new int[Math.max(n, order.length * 2)];
        }
        for (int i = 0; i < n; i++) {
            int k = i;
            // Insertion by descending pass count; batches are a few dozen blocks.
            while (k > 0 && batch.passes[order[k - 1]] < batch.passes[i]) {
                order[k] = order[k - 1];
                k--;
            }
            order[k] = i;
        }
        int total = 0;
        for (int i = 0; i < n; i++) {
            total += Integer.bitCount(batch.faces[i]) * 4;
        }
        if (VERTICES.length != total * 4) {
            VERTICES = new int[total * 4];
        }
        if (layerVertices.length < layers) {
            layerVertices = new int[layers];
        }
        java.util.Arrays.fill(layerVertices, 0, layers, 0);
        org.taumc.celeritas.mixin.core.BufferBuilderAccessor access =
                (org.taumc.celeritas.mixin.core.BufferBuilderAccessor) buffer;
        double ox = access.celtium$xOffset(), oy = access.celtium$yOffset(), oz = access.celtium$zOffset();
        int at = 0;
        for (int o = 0; o < n; o++) {
            int i = order[o];
            int start = at;
            at = faceVertices(VERTICES, at, batch.x[i] + ox, batch.y[i] + oy, batch.z[i] + oz, batch.faces[i],
                    batch.offset[i]);
            int added = (at - start) / 4;
            for (int j = 0; j < Math.min(batch.passes[i], layers); j++) {
                layerVertices[j] += added;
            }
        }
    }

    private static int vertex(int[] data, int at, double x, double y, double z) {
        data[at] = Float.floatToRawIntBits((float) x);
        data[at + 1] = Float.floatToRawIntBits((float) y);
        data[at + 2] = Float.floatToRawIntBits((float) z);
        return at + 4;
    }

    /** The faces in vanilla's TileEntityEndPortalRenderer vertex order, as raw ints; the colour slot is filled per layer. */
    private static int faceVertices(int[] d, int at, double x, double y, double z, int faces, float f) {
        if ((faces & SOUTH) != 0) {
            at = vertex(d, at, x, y, z + 1.0D);
            at = vertex(d, at, x + 1.0D, y, z + 1.0D);
            at = vertex(d, at, x + 1.0D, y + 1.0D, z + 1.0D);
            at = vertex(d, at, x, y + 1.0D, z + 1.0D);
        }
        if ((faces & NORTH) != 0) {
            at = vertex(d, at, x, y + 1.0D, z);
            at = vertex(d, at, x + 1.0D, y + 1.0D, z);
            at = vertex(d, at, x + 1.0D, y, z);
            at = vertex(d, at, x, y, z);
        }
        if ((faces & EAST) != 0) {
            at = vertex(d, at, x + 1.0D, y + 1.0D, z);
            at = vertex(d, at, x + 1.0D, y + 1.0D, z + 1.0D);
            at = vertex(d, at, x + 1.0D, y, z + 1.0D);
            at = vertex(d, at, x + 1.0D, y, z);
        }
        if ((faces & WEST) != 0) {
            at = vertex(d, at, x, y, z);
            at = vertex(d, at, x, y, z + 1.0D);
            at = vertex(d, at, x, y + 1.0D, z + 1.0D);
            at = vertex(d, at, x, y + 1.0D, z);
        }
        if ((faces & DOWN) != 0) {
            at = vertex(d, at, x, y, z);
            at = vertex(d, at, x + 1.0D, y, z);
            at = vertex(d, at, x + 1.0D, y, z + 1.0D);
            at = vertex(d, at, x, y, z + 1.0D);
        }
        if ((faces & UP) != 0) {
            at = vertex(d, at, x, y + (double) f, z + 1.0D);
            at = vertex(d, at, x + 1.0D, y + (double) f, z + 1.0D);
            at = vertex(d, at, x + 1.0D, y + (double) f, z);
            at = vertex(d, at, x, y + (double) f, z);
        }
        return at;
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
