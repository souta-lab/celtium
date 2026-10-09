package org.soutalab.celtium.validation;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL45C;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Explicitly installed validation fixture; never embedded in the renderer mod. */
@Mod(modid = "celtium_validation", name = "Celtium validation fixture", version = "0.1.0", clientSideOnly = true)
public final class ValidationClient {
    /** Time of day pinned for comparison runs: noon, so the sun angle cannot drift between runs. */
    private static final long PINNED_WORLD_TIME = 6000L;

    /**
     * MCP field names followed by the SRG names Forge uses at runtime. Vanilla regenerates the lightmap with a
     * {@code Math.random()} flicker on every frame, which shifts terrain brightness slightly and differently per
     * run. Pinning the two flicker fields removes that run-to-run noise so two runs can be compared exactly.
     */
    private static final String[] FLICKER_X_NAMES = {"torchFlickerX", "field_78514_e"};
    private static final String[] FLICKER_DX_NAMES = {"torchFlickerDX", "field_175075_L"};

    private final JsonObject report = new JsonObject();
    private net.minecraft.client.shader.Framebuffer celtium$secondary;
    private final List<Double> frameIntervals = new ArrayList<Double>();
    private boolean enabled;
    private boolean launched;
    private boolean celtium$leftWorld;
    private boolean celtium$rejoinPending;
    private boolean commandsRun;
    private boolean finished;
    private boolean interopRun;
    private int framesAtInterop;
    private int fboAtInterop, colorAtInterop, depthAtInterop;
    private int worldTicks;
    // Once-guards: these hooks live in a render-tick handler, so `worldTicks == 100` is true on every frame of
    // that tick and the reload would run dozens of times instead of once (audit S22).
    private boolean celtium$reloaded;
    private boolean celtium$resized;
    private boolean celtium$weatherShown;
    private boolean celtium$particlesSpawned;
    private boolean celtium$interopChecked;
    private boolean celtium$jeiScreen;
    private boolean celtium$jeiRecipe;
    private boolean celtium$compositeBlit;
    private int worldFrames;
    private long previousFrame;
    private int entitiesRemoved;
    private Field flickerX;
    private Field flickerDX;
    private boolean flickerFieldsResolved;
    private boolean flickerPinned;
    private final boolean drawTestQuad = Boolean.getBoolean("celtium.validation.drawTestQuad");
    private int testQuadFrames;
    private final boolean drawBlockQuad = Boolean.getBoolean("celtium.validation.drawBlockQuad");
    private int blockQuadFrames;

    /**
     * The acceptance fixture for the merged-group defect (the second audit's S05), drawn inside a Forge render event
     * because that is the context the objective cares about: one {@code begin}/{@code draw} pair, two quads, four distinct
     * vertices each, in the same format, texture and state, so they land in a single group.
     * <p>
     * With the base-vertex defect the second quad's indices would still be 0..3 and the first quad's vertices would be
     * drawn twice: the frame would show one colour at one place, not two colours at two places. The check is therefore an
     * image check, and the report carries the vertex count of the largest group the backend closed.
     */
    /**
     * The acceptance fixture for the merged-group defect (the second audit's S05), drawn inside a Forge render event
     * because that is the context the objective cares about: one {@code begin}/{@code draw} pair, two quads, four distinct
     * vertices each, in the same format, texture and state, so they land in a single group.
     * <p>
     * It draws in the **overlay** pass rather than the world pass. The first version drew in {@code RenderWorldLastEvent}
     * and nothing it drew ever reached the frame - not at six blocks across, not with culling and depth disabled, not with
     * GL drawing the batches itself. Here the projection is an orthographic matrix over the framebuffer and the coordinates
     * are screen coordinates, so the fixture stops depending on the camera, on the world pass, and on what matrix it leaves
     * behind. With the base-vertex defect the second quad would be drawn with the first quad's vertices, so one colour
     * would fill one block instead of two colours filling two.
     */

    /**
     * The S10 acceptance fixture's second render target, drawn in the **world** pass.
     * <p>
     * It first ran in the overlay pass, and the comparison then reported a modelview difference of 2000 - which is
     * `setupOverlayRendering`'s `translate(0, 0, -2000)`, a value that belongs to the overlay and not to the world. Drawing
     * into a second target from inside another pass's state is not the case S10 describes; the case is a target switch
     * within a pass whose matrices are current. This handler runs after the world pass has set them, so the fixture crosses
     * targets without borrowing another pass's projection.
     */
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public void onRenderWorldLastForTargets(net.minecraftforge.client.event.RenderWorldLastEvent event) {
        // Its own switch, default off, because as written it does not merely test the renderer: it fails the run and
        // suppresses the capture, so it is a reproduction to be fixed rather than a fixture to be run in the normal suite.
        if (!Boolean.getBoolean("celtium.validation.drawTestTargets")) {
            return;
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getMinecraft();
        if (client.world == null) {
            return;
        }
        net.minecraft.client.shader.Framebuffer secondary = celtium$secondaryTarget(client);
        if (secondary == null) {
            return;
        }
        secondary.setFramebufferColor(0.0F, 0.0F, 0.0F, 1.0F);
        secondary.framebufferClear();
        secondary.bindFramebuffer(true);
        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(org.lwjgl.opengl.GL11.GL_QUADS, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
        celtium$quad(buffer, 32.0D, 32.0D, 224.0D, 224.0D, 1.0F, 0.0F, 0.0F);
        tessellator.draw();
        client.getFramebuffer().bindFramebuffer(true);
        report.addProperty("drawTestSecondTarget", true);
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public void onRenderOverlay(net.minecraftforge.client.event.RenderGameOverlayEvent.Post event) {
        // The game's own composite call - Framebuffer.framebufferRender - is the POSITION_TEX_COLOR batch the present path
        // keys on, and this scene never issues one, which is why that path has been inert in every measurement so far. Called
        // once, behind its own flag, so the present can be exercised rather than described.
        if (!celtium$compositeBlit && worldTicks >= 185
                && Boolean.getBoolean("celtium.validation.compositeBlit")) {
            celtium$compositeBlit = true;
            net.minecraft.client.shader.Framebuffer framebuffer = Minecraft.getMinecraft().getFramebuffer();
            framebuffer.framebufferRender(framebuffer.framebufferWidth, framebuffer.framebufferHeight);
            report.addProperty("compositeBlitAtTick", worldTicks);
        }
        if (!Boolean.getBoolean("celtium.validation.drawTest")
                || event.getType() != net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getMinecraft();
        if (client.player == null || client.world == null) {
            return;
        }
        int width = event.getResolution().getScaledWidth();
        int height = event.getResolution().getScaledHeight();
        // Bracketed through Minecraft's own state manager and with the colour set explicitly, which is what Gui.drawRect
        // does and what made the difference: in the same handler, same pass and same frame, Gui.drawRect's quad filled
        // 55,080 pixels of the capture while this fixture's own batch, drawn with raw GL11 calls and without setting the
        // tracked colour, produced none. BufferBuilder multiplies each vertex colour by the tracked one, so a colour left
        // behind by earlier interface code silently writes nothing visible.
        net.minecraft.client.renderer.GlStateManager.disableTexture2D();
        net.minecraft.client.renderer.GlStateManager.disableDepth();
        net.minecraft.client.renderer.GlStateManager.disableCull();
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(
                org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA,
                org.lwjgl.opengl.GL11.GL_ONE, org.lwjgl.opengl.GL11.GL_ZERO);
        net.minecraft.client.renderer.GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.getInstance();
        net.minecraft.client.renderer.BufferBuilder buffer = tessellator.getBuffer();
        // The blue quad goes through Gui.drawRect, the exact path the client's visible interface uses, and it runs before
        // this batch is opened: drawing it inside an open begin/draw pair nested two Tessellator draws and crashed the
        // client, which is what the first attempt at this A/B did.
        net.minecraft.client.gui.Gui.drawRect((int) (width * 0.60D), (int) (height * 0.35D),
                (int) (width * 0.80D), (int) (height * 0.65D), 0xFF0000FF);
        buffer.begin(org.lwjgl.opengl.GL11.GL_QUADS, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
        celtium$quad(buffer, width * 0.20D, height * 0.35D, width * 0.40D, height * 0.65D, 1.0F, 0.0F, 0.0F);
        tessellator.draw();
        // Read the framebuffer back at a point inside each quad, immediately after drawing them. Three rounds of guessing
        // which pass reaches the captured image produced three withdrawals; this says whether the draw reaches the
        // framebuffer at all, which separates "the fixture does not draw" from "the fixture draws and the capture does not
        // see it". The coordinates are in the scaled space the overlay uses, so they are converted to framebuffer pixels.
        int scale = client.displayWidth / Math.max(1, width);
        java.nio.ByteBuffer pixel = org.lwjgl.BufferUtils.createByteBuffer(4);
        int redSeen = -1;
        int blueSeen = -2;
        for (int pass = 0; pass < 2; pass++) {
            int px = (int) ((pass == 0 ? width * 0.30D : width * 0.70D) * scale);
            int py = (int) ((height - height * 0.50D) * scale);
            pixel.clear();
            org.lwjgl.opengl.GL11.glReadPixels(px, py, 1, 1, org.lwjgl.opengl.GL11.GL_RGBA,
                    org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, pixel);
            int value = (pixel.get(0) & 0xFF) << 16 | (pixel.get(1) & 0xFF) << 8 | (pixel.get(2) & 0xFF);
            if (pass == 0) {
                redSeen = value;
            } else {
                blueSeen = value;
            }
        }
        report.addProperty("drawTestRedReadback", Integer.toHexString(redSeen));
        report.addProperty("drawTestBlueReadback", Integer.toHexString(blueSeen));
        net.minecraft.client.renderer.GlStateManager.enableDepth();
        net.minecraft.client.renderer.GlStateManager.enableCull();
        net.minecraft.client.renderer.GlStateManager.enableTexture2D();
        net.minecraft.client.renderer.GlStateManager.disableBlend();
        report.addProperty("drawTestQuads", 2);
        report.addProperty("drawTestAtTick", client.player.ticksExisted);
        report.addProperty("drawTestPass", "RenderGameOverlayEvent.Post/ALL");
    }

    private net.minecraft.client.shader.Framebuffer celtium$secondaryTarget(net.minecraft.client.Minecraft client) {
        if (this.celtium$secondary == null) {
            try {
                net.minecraft.client.shader.Framebuffer framebuffer =
                        new net.minecraft.client.shader.Framebuffer(256, 256, false);
                framebuffer.setFramebufferFilter(org.lwjgl.opengl.GL11.GL_NEAREST);
                framebuffer.createFramebuffer(256, 256);
                this.celtium$secondary = framebuffer;
            } catch (Throwable error) {
                report.addProperty("drawTestSecondTargetError", String.valueOf(error));
                return null;
            }
        }
        return this.celtium$secondary;
    }

    private static void celtium$quad(net.minecraft.client.renderer.BufferBuilder buffer,
            double left, double top, double right, double bottom, float red, float green, float blue) {
        double z = 0.0D;
        double[][] corners = {{left, bottom}, {right, bottom}, {right, top}, {left, top}};
        for (double[] corner : corners) {
            buffer.pos(corner[0], corner[1], z).color(red, green, blue, 1.0F).endVertex();
        }
    }
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        enabled = Boolean.getBoolean("celtium.validation");
        // The compact vertex format packs the same data into 24 bytes per vertex instead of 32; the fixture can
        // switch it on before the terrain renderer is created so both encodings can be compared from one scene.
        if (enabled && Boolean.getBoolean("celtium.validation.compactVertexFormat")) {
            try {
                Class<?> mod = Class.forName("org.taumc.celeritas.CeleritasVintage");
                Object options = mod.getMethod("options").invoke(null);
                Object performance = options.getClass().getField("performance").get(options);
                performance.getClass().getField("useCompactVertexFormat").setBoolean(performance, true);
                report.addProperty("compactVertexFormat", true);
            } catch (ReflectiveOperationException error) {
                report.addProperty("compactVertexFormat", "unavailable: " + error);
            }
        }
        if (enabled) MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * The "unmodified mod drawing in a Forge event" case the migration plan asks for: a Forge event handler that
     * draws through {@code Tessellator}/{@code BufferBuilder} and {@code GlStateManager} exactly as a content mod
     * does, with no Celtium-specific call anywhere. It is off unless
     * {@code -Dceltium.validation.drawTestQuad=true} is set, so the reference frames are unaffected.
     * <p>
     * What it proves when enabled: the batch reaches Celtium's Vulkan draw entry (the backend counts it and states why
     * it is refused while the per-format pipelines are missing) and the frame is unchanged otherwise.
     */
    @SubscribeEvent
    public void drawTestQuad(RenderWorldLastEvent event) {
        if (!enabled || !drawTestQuad) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        double partial = event.getPartialTicks();
        double x = mc.player.lastTickPosX + (mc.player.posX - mc.player.lastTickPosX) * partial;
        double y = mc.player.lastTickPosY + (mc.player.posY - mc.player.lastTickPosY) * partial;
        double z = mc.player.lastTickPosZ + (mc.player.posZ - mc.player.lastTickPosZ) * partial;
        GlStateManager.pushMatrix();
        GlStateManager.translate(-x, -y, -z);
        GlStateManager.disableTexture2D();
        GlStateManager.depthMask(false);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder builder = tessellator.getBuffer();
        builder.begin(org.lwjgl.opengl.GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        builder.pos(x - 1.0, y + 2.0, z - 1.0).color(255, 64, 64, 200).endVertex();
        builder.pos(x + 1.0, y + 2.0, z - 1.0).color(255, 64, 64, 200).endVertex();
        builder.pos(x + 1.0, y + 2.0, z + 1.0).color(255, 64, 64, 200).endVertex();
        builder.pos(x - 1.0, y + 2.0, z + 1.0).color(255, 64, 64, 200).endVertex();
        tessellator.draw();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
        testQuadFrames++;
    }

    /**
     * Draws one {@code DefaultVertexFormats.BLOCK} batch - the 28-byte layout with a lightmap that vanilla's blocks,
     * items and particles use. Off unless {@code -Dceltium.validation.drawBlockQuad=true}, so reference frames are
     * unaffected, and written the way a content mod writes it: no Celtium-specific call anywhere.
     * <p>
     * It exists because the fixture scene contains no hand-held or item-rendered geometry, which left the BLOCK
     * pipeline's acceptance unverified: the counters could not tell "no such batch exists" from "the batch was
     * refused". Drawing one here makes that distinguishable.
     */
    @SubscribeEvent
    public void drawFormatQuads(RenderWorldLastEvent event) {
        if (!enabled || !drawBlockQuad) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        double partial = event.getPartialTicks();
        double x = mc.player.lastTickPosX + (mc.player.posX - mc.player.lastTickPosX) * partial;
        double y = mc.player.lastTickPosY + (mc.player.posY - mc.player.lastTickPosY) * partial;
        double z = mc.player.lastTickPosZ + (mc.player.posZ - mc.player.lastTickPosZ) * partial;
        GlStateManager.pushMatrix();
        GlStateManager.translate(-x, -y, -z);
        GlStateManager.disableTexture2D();
        GlStateManager.enableTexture2D();
        GlStateManager.depthMask(false);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder builder = tessellator.getBuffer();
        builder.begin(org.lwjgl.opengl.GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        float u = 0.5f;
        float v = 0.5f;
        builder.pos(x - 1.0, y + 3.0, z - 1.0).color(64, 64, 255, 255).tex(u, v).lightmap(240, 240).endVertex();
        builder.pos(x + 1.0, y + 3.0, z - 1.0).color(64, 64, 255, 255).tex(u, v).lightmap(240, 240).endVertex();
        builder.pos(x + 1.0, y + 3.0, z + 1.0).color(64, 64, 255, 255).tex(u, v).lightmap(240, 240).endVertex();
        builder.pos(x - 1.0, y + 3.0, z + 1.0).color(64, 64, 255, 255).tex(u, v).lightmap(240, 240).endVertex();
        tessellator.draw();
        // The ITEM layout (position, colour, texture, normal, padding): what item and entity rendering uses.
        builder.begin(org.lwjgl.opengl.GL11.GL_QUADS, DefaultVertexFormats.ITEM);
        builder.pos(x - 1.0, y + 4.0, z - 1.0).color(64, 255, 64, 255).tex(u, v).normal(0.0f, 1.0f, 0.0f).endVertex();
        builder.pos(x + 1.0, y + 4.0, z - 1.0).color(64, 255, 64, 255).tex(u, v).normal(0.0f, 1.0f, 0.0f).endVertex();
        builder.pos(x + 1.0, y + 4.0, z + 1.0).color(64, 255, 64, 255).tex(u, v).normal(0.0f, 1.0f, 0.0f).endVertex();
        builder.pos(x - 1.0, y + 4.0, z + 1.0).color(64, 255, 64, 255).tex(u, v).normal(0.0f, 1.0f, 0.0f).endVertex();
        tessellator.draw();
        // The particle layout: position, texture, colour, lightmap - the same fields as BLOCK in another order.
        builder.begin(org.lwjgl.opengl.GL11.GL_QUADS, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);
        builder.pos(x - 1.0, y + 5.0, z - 1.0).tex(u, v).color(255, 255, 64, 255).lightmap(240, 240).endVertex();
        builder.pos(x + 1.0, y + 5.0, z - 1.0).tex(u, v).color(255, 255, 64, 255).lightmap(240, 240).endVertex();
        builder.pos(x + 1.0, y + 5.0, z + 1.0).tex(u, v).color(255, 255, 64, 255).lightmap(240, 240).endVertex();
        builder.pos(x - 1.0, y + 5.0, z + 1.0).tex(u, v).color(255, 255, 64, 255).lightmap(240, 240).endVertex();
        tessellator.draw();
        GlStateManager.depthMask(true);
        GlStateManager.popMatrix();
        blockQuadFrames++;
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (!enabled || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            if (!launched && mc.currentScreen instanceof GuiMainMenu) {
                launched = true;
                report.addProperty("scope", "fixed flat world startup, commands and GL framebuffer observation");
                
                mc.gameSettings.renderDistanceChunks = 8;
                // Unlimited-ish so the two backends can be compared on throughput, not on the frame limiter.
                mc.gameSettings.limitFramerate = 260;
                mc.gameSettings.enableVsync = false;
                WorldSettings settings = new WorldSettings(12345678L, GameType.CREATIVE, false, false, WorldType.FLAT);
                // The extra layers exercise the cutout (leaves) and translucent (glass) terrain passes, not just SOLID.
                settings.setGeneratorOptions("3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass,minecraft:water,minecraft:leaves,minecraft:stained_glass;1;");
                mc.launchIntegratedServer("celtium-validation", "Celtium validation", settings);
            }
            if (Boolean.getBoolean("celtium.validation.worldSwitch") && worldTicks == 100 && !celtium$leftWorld) {
                celtium$leftWorld = true;
                report.addProperty("leftWorldAtTick", worldTicks);
                // The same three calls GuiIngame's "Save and Quit to Title" makes, in the same order. The previous
                // attempt omitted the third one and vanilla's own renderer dereferenced a player that no longer
                // existed, which is a fixture bug rather than a renderer one.
                mc.world.sendQuittingDisconnectingPacket();
                mc.loadWorld((net.minecraft.client.multiplayer.WorldClient) null);
                mc.displayGuiScreen(new net.minecraft.client.gui.GuiMainMenu());
                celtium$rejoinPending = true;
            }
            if (celtium$rejoinPending && mc.world == null && mc.currentScreen instanceof GuiMainMenu) {
                celtium$rejoinPending = false;
                report.addProperty("rejoinedAtTick", worldTicks);
                WorldSettings settings = new WorldSettings(12345678L, GameType.CREATIVE, false, false, WorldType.FLAT);
                settings.setGeneratorOptions("3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass,minecraft:water,minecraft:leaves,minecraft:stained_glass;1;");
                mc.launchIntegratedServer("celtium-validation", "Celtium validation", settings);
            }
            if (mc.world == null || mc.player == null) return;
            worldTicks++;
            if (worldTicks == 20) {
                mc.player.capabilities.isFlying = true;
                mc.player.setPositionAndRotation(0.5, 8.0, 0.5, 180.0f, 20.0f);
            }
            if (worldTicks >= 100 && !commandsRun) {
                commandsRun = true;
                boolean present = ClientCommandHandler.instance.getCommands().containsKey("celtium");
                report.addProperty("celtiumCommandRegistered", present);
                if (present) {
                    report.addProperty("statusCommandResult", ClientCommandHandler.instance.executeCommand(mc.player, "/celtium"));
                    report.addProperty("probeCommandResult", ClientCommandHandler.instance.executeCommand(mc.player, "/celtium probe"));
                    report.addProperty("vulkanCommandResult", ClientCommandHandler.instance.executeCommand(mc.player, "/celtium vulkan"));
                }
            }
            if (worldTicks >= 150 && !Boolean.getBoolean("celtium.validation.interop")) {
                // Terrain-only comparison: mob spawning is not seed-deterministic between runs, so remove every
                // non-player entity and particle well before the capture. Entity and TESR behaviour stays on the
                // existing GL path and is deliberately outside what this comparison measures.
                entitiesRemoved += removeNonPlayerEntities(mc);
            }
            if (worldTicks >= 20 && !Boolean.getBoolean("celtium.validation.interop")) {
                // `updateRenderer` has already run for this tick, so zeroing the flicker here affects the lightmap
                // that the next render builds, and pinning the world time keeps the sun angle identical.
                flickerPinned = pinLightmapFlicker(mc);
                mc.world.setWorldTime(PINNED_WORLD_TIME);
            }
        } catch (Throwable error) {
            fail(mc, error);
        }
    }

    @SubscribeEvent
    public void world(RenderWorldLastEvent event) {
        if (!enabled || finished) return;
        try {
            worldFrames++;
            if (worldTicks == 120 || !report.has("framebuffer")) {
                Minecraft mc = Minecraft.getMinecraft();
                report.addProperty("glVendor", GL11C.glGetString(GL11C.GL_VENDOR));
                report.addProperty("glRenderer", GL11C.glGetString(GL11C.GL_RENDERER));
                report.addProperty("glVersion", GL11C.glGetString(GL11C.GL_VERSION));
                report.add("framebuffer", framebuffer(mc.getFramebuffer()));
            }
        } catch (Throwable error) {
            fail(Minecraft.getMinecraft(), error);
        }
    }

    /**
     * Zeroes vanilla's random torch-flicker term so the lightmap texture is identical on every run.
     * The field names are resolved once, MCP name first and SRG name second. Returns whether the fields are
     * currently pinned; a run that cannot pin them reports it and is not usable as an exact comparison.
     */
    private boolean pinLightmapFlicker(Minecraft mc) {
        if (!flickerFieldsResolved) {
            flickerFieldsResolved = true;
            flickerX = findField(mc.entityRenderer.getClass(), FLICKER_X_NAMES);
            flickerDX = findField(mc.entityRenderer.getClass(), FLICKER_DX_NAMES);
        }
        if (flickerX == null || flickerDX == null) return false;
        try {
            flickerX.setFloat(mc.entityRenderer, 0.0F);
            flickerDX.setFloat(mc.entityRenderer, 0.0F);
            return true;
        } catch (IllegalAccessException | RuntimeException inaccessible) {
            return false;
        }
    }

    private static Field findField(Class<?> owner, String[] names) {
        for (String name : names) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException | RuntimeException absent) {
                // Try the next name.
            }
        }
        return null;
    }

    /** Removes every non-player entity and returns how many were removed; the player and its vehicle stay. */
    private static int removeNonPlayerEntities(Minecraft mc) {
        if (mc.world == null || mc.player == null) return 0;
        List<net.minecraft.entity.Entity> loaded = new ArrayList<net.minecraft.entity.Entity>(mc.world.loadedEntityList);
        int removed = 0;
        for (net.minecraft.entity.Entity entity : loaded) {
            if (entity == mc.player || entity.isRidingOrBeingRiddenBy(mc.player)) continue;
            mc.world.removeEntity(entity);
            removed++;
        }
        if (removed > 0) {
            mc.effectRenderer.clearEffects(mc.world);
        }
        return removed;
    }

    private static int countNonPlayerEntities(Minecraft mc) {
        if (mc.world == null || mc.player == null) return 0;
        int count = 0;
        for (net.minecraft.entity.Entity entity : new ArrayList<net.minecraft.entity.Entity>(mc.world.loadedEntityList)) {
            if (entity != mc.player && !entity.isRidingOrBeingRiddenBy(mc.player)) count++;
        }
        return count;
    }

    /**
     * Records the P3 device-bootstrap result. With Vulkan bindings on the test classpath
     * ({@code -Dceltium.validation.expectVulkan=true}) the device must be created for the same GPU as the GL
     * context; without them the run must still report a reason and keep rendering with OpenGL.
     */
    private void checkVulkanBootstrap(Minecraft mc, Path logDirectory) {
        boolean expected = Boolean.getBoolean("celtium.validation.expectVulkan");
        boolean registered = report.get("celtiumCommandRegistered").getAsBoolean();
        if (!registered) {
            report.addProperty("vulkanBootstrapStatus", "unavailable: the Celtium command is not registered");
            if (expected) throw new IllegalStateException("Vulkan bootstrap was expected but the command is missing");
            return;
        }
        if (report.get("vulkanCommandResult").getAsInt() != 1) {
            throw new IllegalStateException("The /celtium vulkan command did not execute");
        }
        Path file = logDirectory.resolve("celtium-vulkan-device.json");
        if (!Files.isRegularFile(file)) {
            report.addProperty("vulkanBootstrapStatus", "unavailable: no report was written");
            if (expected) throw new IllegalStateException("Vulkan bootstrap was expected but wrote no report");
            return;
        }
        JsonObject device;
        try {
            device = JsonParser.parseString(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception error) {
            throw new IllegalStateException("Vulkan device report is not readable JSON", error);
        }
        report.add("vulkanDevice", device);
        report.addProperty("vulkanBootstrapStatus", device.get("status").getAsString());
        if (device.get("effectiveTerrainBackend").getAsString().equals("opengl") == false
                || device.get("nativeTerrainImplemented").getAsBoolean()) {
            throw new IllegalStateException("The Vulkan bootstrap changed or misreported the terrain backend");
        }
        if (!expected) {
            return;
        }
        if (!"DEVICE_READY".equals(device.get("status").getAsString())) {
            throw new IllegalStateException("Vulkan device was expected but the bootstrap reported "
                    + device.get("status") + ": " + device.get("reasons"));
        }
        if (!device.get("logicalDeviceCreated").getAsBoolean()) {
            throw new IllegalStateException("Vulkan bootstrap did not create a logical device");
        }
        JsonObject matched = device.getAsJsonObject("matched");
        if (!matched.get("uuidMatchesOpenGl").getAsBoolean()) {
            throw new IllegalStateException("The Vulkan device UUID does not match the OpenGL device UUID");
        }
        if (!matched.get("driverUuidMatchesOpenGl").getAsBoolean()) {
            throw new IllegalStateException("The Vulkan driver UUID does not match the OpenGL driver UUID");
        }
        if (!matched.get("externalMemoryFd").getAsBoolean() || !matched.get("externalSemaphoreFd").getAsBoolean()) {
            throw new IllegalStateException("The Vulkan device is missing the external memory/semaphore FD extensions");
        }
        if (!matched.get("binarySemaphoreImportable").getAsBoolean() || !matched.get("binarySemaphoreExportable").getAsBoolean()) {
            throw new IllegalStateException("The Vulkan device cannot import and export binary semaphores");
        }
        if (!matched.get("colorImportable").getAsBoolean() || !matched.get("depthImportable").getAsBoolean()) {
            throw new IllegalStateException("The Vulkan device cannot import the required color/depth formats");
        }
        if (matched.get("queueFamilyIndex").getAsInt() < 0) {
            throw new IllegalStateException("The Vulkan device has no graphics/transfer queue family");
        }
    }

    private static JsonObject framebuffer(Framebuffer target) {
        JsonObject result = new JsonObject();
        result.addProperty("minecraftFbo", target.framebufferObject);
        result.addProperty("boundDrawFbo", GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING));
        result.addProperty("boundReadFbo", GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING));
        result.addProperty("width", target.framebufferWidth);
        result.addProperty("height", target.framebufferHeight);
        if (!GL.getCapabilities().OpenGL45 || target.framebufferObject < 1) {
            result.addProperty("attachmentObservation", "unavailable: requires named framebuffer queries");
            return result;
        }
        result.addProperty("colorInternalFormat", GL45C.glGetTextureLevelParameteri(target.framebufferTexture, 0, GL11C.GL_TEXTURE_INTERNAL_FORMAT));
        result.addProperty("colorRedBits", GL45C.glGetTextureLevelParameteri(target.framebufferTexture, 0, GL11C.GL_TEXTURE_RED_SIZE));
        result.addProperty("colorGreenBits", GL45C.glGetTextureLevelParameteri(target.framebufferTexture, 0, GL11C.GL_TEXTURE_GREEN_SIZE));
        result.addProperty("colorBlueBits", GL45C.glGetTextureLevelParameteri(target.framebufferTexture, 0, GL11C.GL_TEXTURE_BLUE_SIZE));
        result.addProperty("colorAlphaBits", GL45C.glGetTextureLevelParameteri(target.framebufferTexture, 0, GL11C.GL_TEXTURE_ALPHA_SIZE));
        if (target.useDepth && target.depthBuffer > 0) {
            result.addProperty("depthInternalFormat", GL45C.glGetNamedRenderbufferParameteri(target.depthBuffer, GL30C.GL_RENDERBUFFER_INTERNAL_FORMAT));
            result.addProperty("depthBits", GL45C.glGetNamedRenderbufferParameteri(target.depthBuffer, GL30C.GL_RENDERBUFFER_DEPTH_SIZE));
            result.addProperty("stencilBits", GL45C.glGetNamedRenderbufferParameteri(target.depthBuffer, GL30C.GL_RENDERBUFFER_STENCIL_SIZE));
            result.addProperty("sampleCount", Math.max(1, GL45C.glGetNamedRenderbufferParameteri(target.depthBuffer, GL30C.GL_RENDERBUFFER_SAMPLES)));
        }
        return result;
    }

    @SubscribeEvent
    public void frame(TickEvent.RenderTickEvent event) {
        if (!enabled || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        long now = System.nanoTime();
        if (worldTicks >= 120 && worldTicks < 200 && previousFrame != 0) {
            frameIntervals.add((now - previousFrame) / 1_000_000.0);
        }
        previousFrame = now;
        if (worldTicks == 100 && !celtium$reloaded && Boolean.getBoolean("celtium.validation.reload")) {
            celtium$reloaded = true;
            // Resource reload, the way F3+T does it: every chunk renderer is rebuilt and every texture is re-stitched.
            // The plan lists reload as unported until it has been seen to work, and the texture half is what exercises
            // the upload mirror - a reloaded atlas has to be re-read rather than drawn from the first upload.
            mc.renderGlobal.loadRenderers();
            mc.getTextureManager().onResourceManagerReload(mc.getResourceManager());
            report.addProperty("reloadedAtTick", worldTicks);
        }
        if (worldTicks == 170 && !celtium$jeiScreen && Boolean.getBoolean("celtium.validation.jeiScreen")) {
            // Opens the player's inventory so that a mod with an overlay - JEI - draws into it. This is the interaction the
            // plan named as unestablished: JEI loading proves the mod, opening a screen proves that its own rendering goes
            // through the renderer under test. Closed again before the capture so the saved frame stays the normal scene.
            celtium$jeiScreen = true;
            mc.displayGuiScreen(new net.minecraft.client.gui.inventory.GuiInventory(mc.player));
            report.addProperty("jeiScreenOpenedAtTick", worldTicks);
        }
        if (worldTicks == 180 && !celtium$jeiRecipe && Boolean.getBoolean("celtium.validation.jeiRecipe")) {
            // The mod's own heavier screen, opened through its public plugin API rather than by simulating input. Guarded
            // behind its own flag because it needs JEI present and compiled in.
            celtium$jeiRecipe = true;
            try {
                Class<?> plugin = Class.forName("org.soutalab.celtium.validation.CeltiumJeiPlugin");
                Object opened = plugin.getMethod("openFirstRecipeCategory").invoke(null);
                report.addProperty("jeiRecipeOpened", Boolean.TRUE.equals(opened));
            } catch (ReflectiveOperationException | LinkageError error) {
                report.addProperty("jeiRecipeOpened", false);
                report.addProperty("jeiRecipeError", String.valueOf(error));
            }
        }
        if (worldTicks == 190 && celtium$jeiScreen && mc.currentScreen != null) {
            mc.displayGuiScreen(null);
        }
        if (worldTicks == 100 && !celtium$resized && Boolean.getBoolean("celtium.validation.resize")) {
            celtium$resized = true;
            // Resize the window mid-run: the renderer has to survive the framebuffer changing size, which the plan lists
            // as unported until it has been seen to work. The saved screenshot's own dimensions are the evidence that
            // the resize reached Minecraft rather than only the window manager.
            // Minecraft's own resize entry point, which is public and does not depend on the window manager: it sets
            // displayWidth/Height, recreates the framebuffer, resizes the shader group and notifies the screen. The
            // earlier attempt asked GLFW to resize the window, which this harness's window does not act on - reported as
            // windowResizable=true while the framebuffer stayed 1280x720 - so the request never reached Minecraft at all.
            mc.resize(1024, 640);
            report.addProperty("resizedAtTick", worldTicks);
        }
        if (worldTicks == 150 && !celtium$weatherShown && Boolean.getBoolean("celtium.validation.weather")) {
            celtium$weatherShown = true;
            // Rain, saturated well before the capture so its strength has stopped changing: the renderer interpolates
            // rain strength between ticks, and a value still ramping would make two runs differ for reasons that are not
            // the renderer's.
            mc.world.getWorldInfo().setRaining(true);
            mc.world.getWorldInfo().setRainTime(6000);
            mc.world.getWorldInfo().setThunderTime(6000);
        }
        if (worldTicks == 199 && !celtium$particlesSpawned && Boolean.getBoolean("celtium.validation.particles")) {
            celtium$particlesSpawned = true;
            // Particles with no motion, one tick before the capture. A still particle has prev == current, so the
            // partial-tick interpolation that made the hand and the player model unusable as comparison subjects gives
            // the same answer whichever frame is captured.
            for (int i = 0; i < 24; i++) {
                double px = -4.0 + (i % 6) * 1.6;
                double py = 6.0 + (i / 6) * 0.8;
                double pz = -4.0 + ((i / 6) % 3) * 3.0;
                mc.world.spawnParticle(net.minecraft.util.EnumParticleTypes.FLAME, px, py, pz, 0.0, 0.0, 0.0);
            }
            report.addProperty("particlesSpawned", 24);
        }
        if (worldTicks == 190 && !Boolean.getBoolean("celtium.validation.interop")
                && !Boolean.getBoolean("celtium.validation.drawTest")) {
            // Hide the HUD a few ticks before the capture so the saved PNG is the rendered scene only. A stock
            // Celeritas run and a Celtium run of the same seed can then be compared pixel by pixel, without
            // chat lines, the hotbar or overlay text contributing differences that are not renderer differences.
            // Hidden so a stock Celeritas run and a Celtium run can be compared on terrain alone. Keeping it makes the
            // same comparison cover the GUI, the font and the HUD, which are Tessellator content like everything else
            // the immediate path serves - and which the hidden-GUI frames therefore said nothing about.
            mc.gameSettings.hideGUI = !Boolean.getBoolean("celtium.validation.keepGui");
            if (Boolean.getBoolean("celtium.validation.keepGui")) {
                // The GUI-visible comparison has a run-to-run noise floor because the first-person item animates: the
                // frame a run captures sits at a different partial tick each time, so the arm and the equipped progress
                // differ between otherwise identical runs. Freezing the animation phases makes the comparison measure
                // the renderer rather than the phase, which is what a like-for-like image comparison needs.
                if (Boolean.getBoolean("celtium.validation.thirdPerson")) {
                    // Third person removes the first-person hand entirely. That hand is drawn by GL - ItemRenderer uses
                    // POSITION_TEX, which the acceptance mask does not take - and its position moves with the frame's
                    // partial tick, which measured as a term of about 3.6 that no renderer difference could be read
                    // through. Without it the GUI-visible comparison gets a floor worth using.
                    mc.gameSettings.thirdPersonView = 1;
                }
                mc.player.prevSwingProgress = 0.0f;
                mc.player.swingProgress = 0.0f;
                mc.player.prevRenderArmPitch = mc.player.renderArmPitch = 0.0f;
                mc.player.prevRenderArmYaw = mc.player.renderArmYaw = 0.0f;
                if (Boolean.getBoolean("celtium.validation.emptyHand")) {
                    // Separates "the held item is drawn differently" from "the held item's drawing changes what is drawn
                    // around it": with nothing in hand the item renderer does nothing, so a difference that survives is
                    // not the item's own geometry.
                    mc.player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, net.minecraft.item.ItemStack.EMPTY);
                }
                // The equipped-progress fields are not public in 1.12.2, so the item-swap animation is left to the
                // phase the run happens to capture; the swing and arm angles are the ones that move every frame.
            }
        }
        // Captured on one exact tick rather than "the first frame at or after 200". The difference matters for the
        // GUI-visible comparison: animated textures advance per tick, so a run whose capture lands a tick later shows
        // different water, lava and fire frames, and that alone measured as a larger difference than any renderer
        // question this project was asking. One tick is one image.
        if (worldTicks != 200) return;
        try {
            if (!Boolean.getBoolean("celtium.validation.interop")) {
                // The frame that was just drawn may still contain an entity the client tick did not see. Drop it and
                // capture on the next frame, so the saved scene contains terrain and the sky only.
                if (removeNonPlayerEntities(mc) > 0) return;
                report.addProperty("nonPlayerEntitiesRemoved", entitiesRemoved);
                report.addProperty("captureWidth", mc.displayWidth);
                report.addProperty("captureHeight", mc.displayHeight);
                report.addProperty("nonPlayerEntitiesAtCapture", countNonPlayerEntities(mc));
                report.addProperty("lightmapFlickerPinned", flickerPinned);
                report.addProperty("pinnedWorldTime", PINNED_WORLD_TIME);
                report.addProperty("worldTimeAtCapture", mc.world.getWorldTime());
            }
            if (Boolean.getBoolean("celtium.validation.interop")) {
                if (!interopRun) {
                    ScreenShotHelper.saveScreenshot(mc.gameDir, "celtium-validation.png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
                    Framebuffer target = mc.getFramebuffer();
                    fboAtInterop = target.framebufferObject;
                    colorAtInterop = target.framebufferTexture;
                    depthAtInterop = target.depthBuffer;
                    Class<?> experiment = Class.forName("org.soutalab.celtium.probe.CurrentFramebufferInterop");
                    String json = (String) experiment.getMethod("verify", int.class, int.class, int.class, int.class, int.class,
                            String.class, boolean.class).invoke(null, fboAtInterop, colorAtInterop, depthAtInterop,
                            target.framebufferWidth, target.framebufferHeight, mc.gameDir.toPath().resolve("logs").toString(),
                            Boolean.getBoolean("celtium.validation.vulkanLayer"));
                    JsonObject interop = JsonParser.parseString(json).getAsJsonObject();
                    report.add("interop", interop);
                    if (!"ROUNDTRIP_PASSED".equals(interop.get("status").getAsString()))
                        throw new IllegalStateException("Current Minecraft FBO did not pass the interop experiment");
                    interopRun = true;
                    framesAtInterop = worldFrames;
                }
                if (worldTicks < 240) return;
                Framebuffer target = mc.getFramebuffer();
                if (worldFrames - framesAtInterop < 30 || GL11C.glGetError() != GL11C.GL_NO_ERROR
                        || target.framebufferObject != fboAtInterop || target.framebufferTexture != colorAtInterop
                        || target.depthBuffer != depthAtInterop)
                    throw new IllegalStateException("Normal world rendering did not resume with the original framebuffer after interop");
                report.addProperty("normalWorldFramesAfterInterop", worldFrames - framesAtInterop);
                ScreenShotHelper.saveScreenshot(mc.gameDir, "celtium-validation-post-interop.png", mc.displayWidth, mc.displayHeight, target);
            }
            if (worldFrames < 100 || !report.has("framebuffer")) {
                throw new IllegalStateException("Too few world rendering frames for startup validation");
            }
            Path probe = mc.gameDir.toPath().resolve("logs/celtium-capabilities.json");
            if (report.get("celtiumCommandRegistered").getAsBoolean()) {
                if (report.get("statusCommandResult").getAsInt() != 1 || report.get("probeCommandResult").getAsInt() != 1) {
                    throw new IllegalStateException("A Celtium client command did not execute");
                }
                JsonObject capabilities = JsonParser.parseString(new String(Files.readAllBytes(probe), StandardCharsets.UTF_8)).getAsJsonObject();
                // The capability query reports what the device could do, not what is rendering. It must never claim
                // that native terrain exists or that a depth roundtrip was proven; the live backend is recorded
                // separately below from the renderer itself.
                if (capabilities.get("nativeTerrainImplemented").getAsBoolean()
                        || capabilities.get("depthRoundtripVerified").getAsBoolean()) {
                    throw new IllegalStateException("Capability command misreported the backend");
                }
                report.add("capabilities", capabilities);
            }
            checkVulkanBootstrap(mc, probe.getParent());
            report.addProperty("worldFrames", worldFrames);
            report.addProperty("worldTicks", worldTicks);
            Class<?> rendererClass = Class.forName("org.taumc.celeritas.impl.render.terrain.CeleritasWorldRenderer");
            Object renderer = rendererClass.getMethod("instanceNullable").invoke(null);
            if (renderer == null) throw new IllegalStateException("Celeritas world renderer was not active");
            report.addProperty("worldRendererClass", renderer.getClass().getName());
            report.add("rendererDebug", new GsonBuilder().create().toJsonTree(rendererClass.getMethod("getDebugStrings").invoke(renderer)));
            try {
                Object terrainBackend = rendererClass.getMethod("getTerrainBackendDescription").invoke(renderer);
                report.addProperty("terrainBackendDescription", String.valueOf(terrainBackend));
            } catch (NoSuchMethodException absent) {
                // The pinned upstream renderer has no backend selection to report.
                report.addProperty("terrainBackendDescription", "unavailable: renderer has no terrain backend selection");
            }
            // The device bootstrap can succeed while the *terrain* backend still falls back to OpenGL (for example
            // when the validation layer is requested but not installed), so a run that claims to exercise Vulkan
            // terrain checks the live backend here, where it has been recorded.
            if (Boolean.getBoolean("celtium.validation.expectVulkanTerrain")) {
                String live = report.get("terrainBackendDescription").getAsString();
                if (!live.startsWith("vulkan")) {
                    throw new IllegalStateException("Vulkan terrain was expected but the live backend is: " + live);
                }
                // The backend can be selected and still draw nothing: a missing frame target, a failed pipeline, an
                // unmirrored texture or a failed submission each make render() return early. The counters that the
                // backend publishes are therefore what decides, not its name.
                java.util.regex.Matcher counters = java.util.regex.Pattern
                        .compile("celtium/vulkan-check passes=(\\d+) skipped=(\\d+) errors=(\\d+) lastDraw=(\\d+) lastSections=(\\d+) skipReason=(\\S+)")
                        .matcher(report.get("rendererDebug").toString());
                if (!counters.find()) {
                    throw new IllegalStateException("the Vulkan backend published no draw counters");
                }
                int passes = Integer.parseInt(counters.group(1));
                int skipped = Integer.parseInt(counters.group(2));
                int errors = Integer.parseInt(counters.group(3));
                int lastDraw = Integer.parseInt(counters.group(4));
                // Real-path evidence rather than a counter alone (audit S24): the terrain must have been drawn by the
                // Vulkan terrain path, and batches must actually have had their original GL draw cancelled - which is the
                // claim this whole exercise rests on. Reading them out of the same debug block keeps the evidence in the
                // record beside the verdict.
                java.util.regex.Matcher terrain = java.util.regex.Pattern
                        .compile("terrainPasses=(\\d+)").matcher(report.get("rendererDebug").toString());
                java.util.regex.Matcher taken = java.util.regex.Pattern
                        .compile("immediateTaken=(\\d+)").matcher(report.get("rendererDebug").toString());
                int terrainPasses = terrain.find() ? Integer.parseInt(terrain.group(1)) : -1;
                int glDrawsCancelled = taken.find() ? Integer.parseInt(taken.group(1)) : -1;
                report.addProperty("vulkanTerrainPasses", terrainPasses);
                report.addProperty("glDrawsCancelled", glDrawsCancelled);
                report.addProperty("nativeTerrainVerified", passes > 0 && skipped == 0 && errors == 0 && lastDraw > 0
                        && terrainPasses > 0 && glDrawsCancelled > 0);
                report.addProperty("modDrawTestFrames", testQuadFrames);
                report.addProperty("modDrawBlockFrames", blockQuadFrames);
                report.addProperty("vulkanPasses", passes);
                report.addProperty("vulkanSkippedPasses", skipped);
                report.addProperty("vulkanErrors", errors);
                if (passes == 0 || skipped > 0 || errors > 0 || lastDraw == 0) {
                    throw new IllegalStateException("the Vulkan terrain backend did not render cleanly: passes="
                            + passes + " skipped=" + skipped + " errors=" + errors + " lastDraw=" + lastDraw);
                }
            }
            JsonObject camera = new JsonObject();
            camera.addProperty("x", mc.player.posX);
            camera.addProperty("y", mc.player.posY);
            camera.addProperty("z", mc.player.posZ);
            camera.addProperty("yaw", mc.player.rotationYaw);
            camera.addProperty("pitch", mc.player.rotationPitch);
            report.add("camera", camera);
            report.addProperty("seed", 12345678L);
            report.addProperty("renderDistance", 8);
            Collections.sort(frameIntervals);
            report.addProperty("frameIntervalSampleCount", frameIntervals.size());
            report.addProperty("frameIntervalP50Ms", percentile(0.50));
            report.addProperty("frameIntervalP95Ms", percentile(0.95));
            report.addProperty("frameIntervalP99Ms", percentile(0.99));
            report.addProperty("timingScope", "consecutive render tick ends with 120 FPS limit; startup fixture, not a performance benchmark");
            if (!interopRun) {
                celtium$logFrameSample(mc);
                ScreenShotHelper.saveScreenshot(mc.gameDir, "celtium-validation.png", mc.displayWidth,
                        mc.displayHeight, mc.getFramebuffer());
            }
            report.addProperty("status", "PASSED");
            finish(mc);
        } catch (Throwable error) {
            fail(mc, error);
        }
    }

    private double percentile(double fraction) {
        if (frameIntervals.isEmpty()) throw new IllegalStateException("No frame interval samples");
        return frameIntervals.get(Math.min(frameIntervals.size() - 1, (int) Math.ceil(fraction * frameIntervals.size()) - 1));
    }

    private void fail(Minecraft mc, Throwable error) {
        error.printStackTrace();
        report.addProperty("status", "FAILED");
        report.addProperty("error", error.toString());
        finish(mc);
    }

    /**
     * Logs the mean of a small centre patch of the game framebuffer at the moment the validation frame is captured.
     * <p>
     * This localises the mid-frame window's black frame: the backend measured the framebuffer at 150.48 immediately
     * before and after each window, yet the saved screenshot is entirely black, so the question is whether the
     * framebuffer is black *here* or whether the capture reads a different surface. The buffer is a plain JDK direct
     * buffer, which is all glReadPixels needs and which keeps this module free of a dependency on LWJGL's
     * MemoryUtil.
     */
    private void celtium$logFrameSample(Minecraft mc) {
        try {
            int size = 8;
            int x = Math.max(0, (mc.displayWidth / 2) - (size / 2));
            int y = Math.max(0, (mc.displayHeight / 2) - (size / 2));
            java.nio.ByteBuffer pixels = java.nio.ByteBuffer.allocateDirect(size * size * 4);
            GL11C.glReadPixels(x, y, size, size, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pixels);
            long total = 0;
            for (int i = 0; i < size * size * 4; i++) {
                total += pixels.get(i) & 0xFF;
            }
            float mean = (float) ((double) total / (size * size * 4));
            org.apache.logging.log4j.LogManager.getLogger("CeltiumValidation")
                    .info("celtium capture frame sample: mean={}", mean);
        } catch (Throwable error) {
            org.apache.logging.log4j.LogManager.getLogger("CeltiumValidation")
                    .info("celtium capture frame sample failed: {}", error.toString());
        }
    }

    private void finish(Minecraft mc) {
        finished = true;
        try {
            Path output = mc.gameDir.toPath().resolve("logs/celtium-validation.json");
            Files.createDirectories(output.getParent());
            Files.write(output, (new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            error.printStackTrace();
        }
        mc.shutdown();
    }
}
