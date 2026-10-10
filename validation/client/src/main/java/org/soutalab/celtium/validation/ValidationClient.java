package org.soutalab.celtium.validation;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
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
    /**
     * Capture staging.
     * <p>
     * The capture used to be guarded by {@code worldTicks != 200}, one exact tick. That is fragile in a way that costs
     * the whole run: if the tick is missed - the client tick can advance more than once between two frames on a slow
     * GPU - or if the entity removal inside the block returns, the guard can never be true again and the run ends with
     * no report at all. The threshold is now "at or after", the block retries until it completes, and the tick it
     * actually captured on is recorded so two runs can be compared at the same animation phase.
     */
    private boolean celtium$captureStarted;
    private int celtium$captureStartTick = -1;
    private int celtium$interopAtTick = -1;
    /**
     * What the server task did, handed to the client thread through volatile fields rather than by writing the report
     * from the server thread: the JSON object belongs to the render thread, and the interesting question is whether the
     * client actually arrives, not whether a task was scheduled.
     */
    /**
     * The server task's result, published once as an immutable object.
     * <p>
     * It used to be three volatile fields written from the server thread, which is three chances for the client to read a
     * half-updated result and three fields to keep in step. One reference to one final-field object is the whole contract:
     * whoever reads it sees a result that was complete when it was published.
     */
    private volatile TransferSnapshot celtium$transfer = new TransferSnapshot("not attempted", -1, "unknown");

    /** The last progress reading, taken on the render thread, for the shutdown hook to write without touching the game. */
    private volatile ProgressSnapshot celtium$progress;

    /** What the server task did, as one immutable value. */
    private static final class TransferSnapshot {
        final String state;
        final int completedAtTick;
        final String rotationBefore;

        TransferSnapshot(String state, int completedAtTick, String rotationBefore) {
            this.state = state;
            this.completedAtTick = completedAtTick;
            this.rotationBefore = rotationBefore;
        }
    }

    /**
     * What is known at the end of a rendered frame, as one immutable value.
     * <p>
     * The shutdown hook writes a partial report when the capture never happened, and it runs on a thread that must not read
     * the game: Minecraft's world, player and screen are owned by the client thread and may be mid-change or gone. This
     * snapshot is taken where those values are safe to read, and the hook only ever sees this.
     */
    private static final class ProgressSnapshot {
        final int ticks;
        final int frames;
        final boolean captureStarted;
        final int captureStartTick;
        final int clientDimension;
        final int worldDimension;
        final String screen;
        final java.nio.file.Path outputPath;

        ProgressSnapshot(int ticks, int frames, boolean captureStarted, int captureStartTick, int clientDimension,
                int worldDimension, String screen, java.nio.file.Path outputPath) {
            this.ticks = ticks;
            this.frames = frames;
            this.captureStarted = captureStarted;
            this.captureStartTick = captureStartTick;
            this.clientDimension = clientDimension;
            this.worldDimension = worldDimension;
            this.screen = screen;
            this.outputPath = outputPath;
        }
    }
    private int celtium$arrivedAtTick = -1;
    private int celtium$arrivedFrames = -1;
    private int celtium$lastTraceClient = Integer.MIN_VALUE;
    private int celtium$lastTraceServer = Integer.MIN_VALUE;
    private final StringBuilder celtium$dimensionEvents = new StringBuilder();
    /** Whether the report has been written, and whether the partial-report exit hook is installed. */
    private volatile boolean celtium$reportWritten;
    private boolean celtium$exitHookInstalled;

    /** Dimension travel: which dimension to move to and at which world tick (both from properties, 0 disables). */
    private final int celtium$dimensionAtTick = Integer.getInteger("celtium.validation.dimensionAtTick", 0).intValue();
    private final int celtium$dimension = Integer.getInteger("celtium.validation.dimension", -1).intValue();
    private boolean celtium$changedDimension;

    /**
     * The flat world's layers, as the superflat generator string.
     * <p>
     * Configurable because the layers decide what the scene contains and therefore how heavy it is: the version with
     * lava and fire measured about twice the frame time of the one without, and an A/B of the same build has to be able
     * to tell "the world got heavier" apart from "the renderer got slower". The default is the fuller world.
     */
    private final String celtium$worldLayers = System.getProperty("celtium.validation.worldLayers",
            "3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass,minecraft:water,minecraft:lava,minecraft:fire,"
            + "minecraft:leaves,minecraft:stained_glass,minecraft:chest,minecraft:enchanting_table;1;");
    private boolean celtium$rejoinPending;
    private boolean commandsRun;
    private boolean finished;
    private boolean interopRun;
    private int framesAtInterop;
    private int fboAtInterop, colorAtInterop, depthAtInterop;
    private int worldTicks;
    /**
     * Registry-driven block coverage.
     * <p>
     * The generator's flat world contains a handful of block types, so the terrain path was only ever exercised on those.
     * With {@code -Dceltium.validation.blockSweep=<count>} the fixture walks {@code Block.REGISTRY} and places a bounded
     * number of blocks per tick on the layer just above the ground, so the mesher and the native terrain pass have to
     * handle every registered block. Nothing is assumed about the result: the placed/failed/index counters go into the
     * report, and which blocks reached the frame is read from the renderer's own counters and the image.
     */
    private java.util.List<net.minecraft.block.Block> celtium$sweepBlocks;
    private int celtium$sweepIndex;
    private int celtium$sweepPlaced;
    private int celtium$sweepFailed;
    private int celtium$sweepTotal;
    /** Registry index this run starts at, so several runs cover the whole registry. */
    private int celtium$sweepFrom;
    /** Wall time spent inside setBlockState, and the single most expensive block seen. */
    private long celtium$sweepMillis;
    private long celtium$sweepWorstNanos;
    private String celtium$sweepWorstBlock = "none";
    /** World frame counter when the sweep started, so a run says how many frames it cost. */
    private int celtium$sweepFramesAtStart = -1;

    private void celtium$blockSweep(Minecraft mc) {
        int wanted = Integer.getInteger("celtium.validation.blockSweep", 0).intValue();
        if (wanted <= 0 || mc.world == null) {
            return;
        }
        if (celtium$sweepBlocks == null) {
            celtium$sweepBlocks = new java.util.ArrayList<net.minecraft.block.Block>();
            for (net.minecraft.block.Block block : net.minecraft.block.Block.REGISTRY) {
                if (block != null) {
                    celtium$sweepBlocks.add(block);
                }
            }
            // Coverage is split across runs by design: one run places a bounded number of blocks, so the rest of the
            // registry is reached by starting later (-Dceltium.validation.blockSweepFrom=<registry index>). Together the
            // counters say which slice a run covered.
            celtium$sweepFrom = Math.min(Math.max(0, Integer.getInteger("celtium.validation.blockSweepFrom", 0).intValue()),
                    celtium$sweepBlocks.size());
            celtium$sweepTotal = Math.min(wanted, celtium$sweepBlocks.size() - celtium$sweepFrom);
            report.addProperty("blockSweepFrom", Integer.valueOf(celtium$sweepFrom));
            report.addProperty("blockRegistrySize", Integer.valueOf(celtium$sweepBlocks.size()));
            report.addProperty("blockSweepTotal", Integer.valueOf(celtium$sweepTotal));
        }
        // Paced on purpose. Two blocks a tick for 125 ticks measured as "Too few world rendering frames for startup
        // validation: worldFrames=94 (threshold 100), worldTicks=200" on AMD and a 175 s timeout with MISSING_REPORT on
        // Intel: filling a layer dirties enough chunks that the terrain rebuilds stop the frame loop keeping up. The
        // fixture is therefore split into small sweeps - a bounded number of blocks, spaced out - and the counters say
        // how far each one got.
        int everyTicks = Math.max(1, Integer.getInteger("celtium.validation.blockSweepEvery", 4).intValue());
        if (worldTicks % everyTicks != 0) {
            return;
        }
        // How many blocks per tick. Placement measured at ~0.15-0.4 ms each (placeMillis=31 for 200 blocks), so the
        // count is not what makes a run slow - the neighbour updates were, and those are off now. Two per tick is the
        // default so that the whole 254-block registry fits inside the fixture's tick budget.
        int perTick = Math.max(1, Integer.getInteger("celtium.validation.blockSweepPerTick", 2).intValue());
        for (int n = 0; n < perTick && celtium$sweepIndex < celtium$sweepTotal; n++, celtium$sweepIndex++) {
            net.minecraft.block.Block block = celtium$sweepBlocks.get(celtium$sweepFrom + celtium$sweepIndex);
            int gx = celtium$sweepIndex % 16;
            int gz = celtium$sweepIndex / 16;
            net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(gx - 8, 4, gz - 8);
            try {
                net.minecraft.block.state.IBlockState state = block.getDefaultState();
                if (state != null) {
                    // Flag 3 = 1 | 2: no neighbour updates, but tell the client (which is this one) so the chunk is
                    // rebuilt. The first version passed 2, which leaves the neighbour update in - and every placement
                    // then made six neighbours react (water flowing, plants popping, fire spreading), which is what
                    // made the later slices time out while the earlier ones did not. This scene wants the blocks drawn,
                    // not simulated.
                    long before = System.nanoTime();
                    mc.world.setBlockState(pos, state, 3);
                    long tookNanos = System.nanoTime() - before;
                    celtium$sweepMillis += tookNanos / 1000000L;
                    if (tookNanos > celtium$sweepWorstNanos) {
                        // Which block costs the most to put in the world, by name: a slice that times the fixture out can
                        // then be compared with one that does not, instead of guessing that "later slices are worse".
                        celtium$sweepWorstNanos = tookNanos;
                        celtium$sweepWorstBlock = String.valueOf(block.getRegistryName());
                    }
                    celtium$sweepPlaced++;
                }
            } catch (Throwable error) {
                celtium$sweepFailed++;
                if (celtium$sweepFailed <= 5) {
                    report.addProperty("blockSweepFailure" + celtium$sweepFailed,
                            String.valueOf(block.getRegistryName()) + ": " + error.getClass().getSimpleName() + ": "
                                    + String.valueOf(error.getMessage()));
                }
            }
        }
        if (celtium$sweepFramesAtStart < 0) {
            celtium$sweepFramesAtStart = worldFrames;
        }
        if (celtium$sweepIndex % 5 == 0 || celtium$sweepIndex >= celtium$sweepTotal) {
            // To stdout as well: a run that the fixture times out writes no report at all, and that is exactly the run
            // whose cost has to be comparable with one that finished.
            System.out.println("celtium block sweep: index=" + celtium$sweepIndex + "/" + celtium$sweepTotal
                    + " placed=" + celtium$sweepPlaced + " failed=" + celtium$sweepFailed
                    + " placeMillis=" + celtium$sweepMillis + " worst=" + celtium$sweepWorstBlock + " ("
                    + (celtium$sweepWorstNanos / 1000000L) + " ms) frames=" + (worldFrames - celtium$sweepFramesAtStart)
                    + " tick=" + worldTicks);
        }
        report.addProperty("blockSweepPlaceMillis", Long.valueOf(celtium$sweepMillis));
        report.addProperty("blockSweepWorstBlock", celtium$sweepWorstBlock);
        report.addProperty("blockSweepWorstMillis", Long.valueOf(celtium$sweepWorstNanos / 1000000L));
        report.addProperty("blockSweepFramesAtStart", Integer.valueOf(celtium$sweepFramesAtStart));
        report.addProperty("blockSweepFrames", Integer.valueOf(worldFrames - celtium$sweepFramesAtStart));
        report.addProperty("blockSweepIndex", Integer.valueOf(celtium$sweepIndex));
        report.addProperty("blockSweepPlaced", Integer.valueOf(celtium$sweepPlaced));
        report.addProperty("blockSweepFailed", Integer.valueOf(celtium$sweepFailed));
    }

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
    private boolean celtium$screenReadback;
    private boolean celtium$postprocess;
    private boolean celtium$spectator;
    private int worldFrames;
    private long previousFrame;
    private int entitiesRemoved;
    private Field flickerX;
    private Field flickerDX;
    private boolean flickerFieldsResolved;
    private boolean flickerPinned;
    private boolean reloadDone;
    private final boolean drawTestQuad = Boolean.getBoolean("celtium.validation.drawTestQuad");
    private int testQuadFrames;
    private final boolean drawBlockQuad = Boolean.getBoolean("celtium.validation.drawBlockQuad");
    private int blockQuadFrames;
    private final boolean commandBufferTargets = Boolean.getBoolean("celtium.validation.commandBufferTargets");
    private boolean commandBufferTargetsAttempted;
    private boolean commandBufferTargetsPassed;
    private Framebuffer commandBufferSecondary;
    private DynamicTexture commandBufferWhite;

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
        // The screen itself, read from the default framebuffer. With the composite present active the renderer cancels GL's
        // blit, so whatever is there was not drawn by GL - which is the closest this fixture can come to observing the present
        // rather than inferring it. Recorded as an observation with its numbers: it does not prove which path put the pixels
        // on the screen, and the game's own screenshot helper reads its FBO rather than this.
        if (!celtium$screenReadback && worldTicks >= 186
                && Boolean.getBoolean("celtium.validation.screenReadback")) {
            celtium$screenReadback = true;
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getMinecraft();
            int width = client.displayWidth;
            int height = client.displayHeight;
            java.nio.ByteBuffer pixels = org.lwjgl.system.MemoryUtil.memAlloc(width * height * 4);
            try {
                org.lwjgl.opengl.GL30C.glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER, 0);
                org.lwjgl.opengl.GL11C.glReadBuffer(org.lwjgl.opengl.GL11C.GL_BACK);
                org.lwjgl.opengl.GL11C.glReadPixels(0, 0, width, height, org.lwjgl.opengl.GL11C.GL_RGBA,
                        org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE, pixels);
                long sumRed = 0;
                long sumGreen = 0;
                long sumBlue = 0;
                long nonBlack = 0;
                int samples = width * height;
                for (int i = 0; i < samples; i++) {
                    int red = pixels.get(i * 4) & 0xFF;
                    int green = pixels.get(i * 4 + 1) & 0xFF;
                    int blue = pixels.get(i * 4 + 2) & 0xFF;
                    sumRed += red;
                    sumGreen += green;
                    sumBlue += blue;
                    if (red + green + blue > 12) {
                        nonBlack++;
                    }
                }
                report.addProperty("screenReadbackSize", width + "x" + height);
                report.addProperty("screenReadbackMean",
                        String.format("%.1f,%.1f,%.1f", (double) sumRed / samples, (double) sumGreen / samples,
                                (double) sumBlue / samples));
                report.addProperty("screenReadbackNonBlackPercent",
                        String.format("%.1f", 100.0 * nonBlack / samples));
            } catch (RuntimeException | LinkageError error) {
                report.addProperty("screenReadbackError", String.valueOf(error));
            } finally {
                org.lwjgl.system.MemoryUtil.memFree(pixels);
            }
        }
        // S05/S06 acceptance, behind its own flag: two INDEPENDENT batches that share the state key merge into one
        // group, so the second batch's indices must carry the base vertex or it re-draws the first batch's geometry;
        // then one batch large enough to cross the vertex/index arena capacity, so the first quads must survive the
        // growth. Three probe points are reported so the image check can read the presented frame rather than guess.
        if (Boolean.getBoolean("celtium.validation.drawMergedQuads")
                && event.getType() == net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.ALL
                && worldTicks >= 150 && worldTicks <= 205) {
            int width = event.getResolution().getScaledWidth();
            int height = event.getResolution().getScaledHeight();
            net.minecraft.client.renderer.GlStateManager.disableTexture2D();
            net.minecraft.client.renderer.GlStateManager.disableDepth();
            net.minecraft.client.renderer.GlStateManager.disableCull();
            net.minecraft.client.renderer.GlStateManager.enableBlend();
            net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(
                    org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA,
                    org.lwjgl.opengl.GL11.GL_ONE, org.lwjgl.opengl.GL11.GL_ZERO);
            net.minecraft.client.renderer.GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            net.minecraft.client.renderer.Tessellator tessellator =
                    net.minecraft.client.renderer.Tessellator.getInstance();
            net.minecraft.client.renderer.BufferBuilder buffer = tessellator.getBuffer();
            // S05: two separate draw() calls, identical state, so they land in the same group's mesh. The red quad sits
            // at the left quarter, the green at the right; a missing base vertex draws the second batch over the first
            // and the green probe position stays background. Gui.drawRect's proven shape is used - POSITION format with
            // the colour carried by GlStateManager.color, which is the call sequence that demonstrably reached the
            // framebuffer where the POSITION_COLOR variant produced nothing.
            net.minecraft.client.renderer.GlStateManager.color(1.0F, 0.0F, 0.0F, 1.0F);
            buffer.begin(org.lwjgl.opengl.GL11.GL_QUADS,
                    net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION);
            celtium$quadPos(buffer, width * 0.10D, height * 0.75D, width * 0.18D, height * 0.90D);
            tessellator.draw();
            net.minecraft.client.renderer.GlStateManager.color(0.0F, 1.0F, 0.0F, 1.0F);
            buffer.begin(org.lwjgl.opengl.GL11.GL_QUADS,
                    net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION);
            celtium$quadPos(buffer, width * 0.30D, height * 0.75D, width * 0.38D, height * 0.90D);
            tessellator.draw();
            // S06: a single batch whose quads push the group mesh past its initial arena. POSITION is 12 bytes a
            // vertex, so 22000 quads (~1.06 MB) cross the 1 MiB vertex arena and the index arena alike. The first quad
            // occupies a fixed tile; if the growth dropped what it already held, that tile shows background.
            net.minecraft.client.renderer.GlStateManager.color(0.0F, 0.0F, 1.0F, 1.0F);
            int grown = Integer.getInteger("celtium.validation.mergedQuadCount", 22000).intValue();
            buffer.begin(org.lwjgl.opengl.GL11.GL_QUADS,
                    net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION);
            int columns = 64;
            int row = 0;
            double tile = 3.0D;
            for (int i = 0; i < grown; i++) {
                int col = i % columns;
                if (col == 0 && i > 0) {
                    row++;
                }
                double x0 = width * 0.45D + col * (tile + 1.0D);
                double y0 = height * 0.75D + row * (tile + 1.0D);
                celtium$quadPos(buffer, x0, y0, x0 + tile, y0 + tile);
            }
            tessellator.draw();
            net.minecraft.client.renderer.GlStateManager.enableDepth();
            net.minecraft.client.renderer.GlStateManager.enableCull();
            net.minecraft.client.renderer.GlStateManager.enableTexture2D();
            net.minecraft.client.renderer.GlStateManager.disableBlend();
            int scale = event.getResolution().getScaleFactor();
            report.addProperty("mergedQuadsEmitted", 2 + grown);
            // Image-space probe points for the upright presented dump: x = scaledX*scale, y = scaledY*scale from top.
            report.addProperty("mergedQuadProbeRed",
                    (int) (width * 0.14D * scale) + "," + (int) (height * 0.82D * scale));
            report.addProperty("mergedQuadProbeGreen",
                    (int) (width * 0.34D * scale) + "," + (int) (height * 0.82D * scale));
            report.addProperty("mergedQuadProbeFirst",
                    (int) ((width * 0.45D + 1.5D) * scale) + "," + (int) ((height * 0.75D + 1.5D) * scale));
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

    /**
     * A one-frame format-2 ordering test, independent of the camera and of the final screen composite.
     * Geometry still uses the unmodified Tessellator API; reflection observes execution, not submission.
     * The secondary target deliberately has the same RGBA8/D24 attachment contract as Minecraft's main target.
     */
    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
    public void onRenderOverlayForCommandBufferTargets(net.minecraftforge.client.event.RenderGameOverlayEvent.Post event) {
        if (!enabled || finished || !commandBufferTargets || commandBufferTargetsAttempted || worldTicks < 40
                || event.getType() != net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return;
        commandBufferTargetsAttempted = true;
        JsonObject result = new JsonObject();
        result.addProperty("attempted", true);
        result.addProperty("passed", false);
        result.addProperty("worldTicks", worldTicks);
        result.addProperty("pass", "RenderGameOverlayEvent.Post/ALL");
        result.addProperty("format", "POSITION_COLOR");
        result.addProperty("formatId", 1);
        result.addProperty("batches", 4);
        result.addProperty("depthScope", "RGBA8/D24 bridge; depth test disabled, not a depth-occlusion test");
        report.add("commandBufferTargets", result);
        CommandBufferFixtureState saved = null;
        try {
            Framebuffer main = mc.getFramebuffer();
            if (main.framebufferWidth < 256 || main.framebufferHeight < 256 || main.framebufferObject <= 0
                    || GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING) != main.framebufferObject) {
                throw new IllegalStateException("Format-2 fixture requires the main FBO bound in overlay ALL, at least 256x256");
            }
            Object backend = Class.forName("org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends")
                    .getMethod("current").invoke(null);
            if (backend == null) throw new IllegalStateException("No live backend for required format-2 target test");
            java.lang.reflect.Method counters = backend.getClass().getMethod("immediateExecutionCounters");
            result.addProperty("backendClass", backend.getClass().getName());
            saved = new CommandBufferFixtureState();
            // Construction also binds through OpenGlHelper directly, before its Framebuffer.bind hook. Drain HUD work now.
            GlStateManager.clear(0);
            // Framebuffer's constructor and clear obey these masks too. Prepare resources before any test batch is pending.
            GlStateManager.colorMask(true, true, true, true);
            GlStateManager.depthMask(true);
            GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            commandBufferSecondary = new Framebuffer(256, 256, true);
            commandBufferSecondary.setFramebufferColor(8.0F / 255.0F, 16.0F / 255.0F, 32.0F / 255.0F, 1.0F);
            commandBufferSecondary.framebufferClear();
            main.bindFramebuffer(true);
            commandBufferWhite = new DynamicTexture(1, 1);
            commandBufferWhite.getTextureData()[0] = 0xFFFFFFFF;
            commandBufferWhite.updateDynamicTexture();
            GlStateManager.bindTexture(commandBufferWhite.getGlTextureId());
            GlStateManager.enableTexture2D();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.disableCull();
            GlStateManager.disableBlend();
            GlStateManager.disableAlpha();
            GlStateManager.disableLighting();
            GlStateManager.disableFog();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            saved.pushMatrices();
            commandBufferMatrices(main.framebufferWidth, main.framebufferHeight);
            // Clear with mask zero is a flush barrier, not a pixel/depth clear. Drain unrelated HUD work before measuring.
            GlStateManager.clear(0);
            long[] before = commandBufferCounters(backend, counters);
            result.add("before", commandBufferCounterJson(before));
            int injected = Integer.getInteger("celtium.vulkan.failImmediateSubmits", 0).intValue();
            if (injected > 0) backend.getClass().getMethod("injectImmediateSubmitFailure", int.class)
                    .invoke(backend, Integer.valueOf(injected));
            if (before[4] != 0) throw new IllegalStateException("Unrelated immediate batches remained pending before fixture");
            java.awt.image.BufferedImage mainBefore = commandBufferReadback(main);
            commandBufferSavePng(mc, "celtium-command-buffer-main-before.png", mainBefore);
            result.addProperty("mainBeforePng", "screenshots/celtium-command-buffer-main-before.png");
            result.add("mainFramebuffer", framebuffer(main));
            result.add("secondaryFramebuffer", framebuffer(commandBufferSecondary));

            // One small main batch must remain pending until the bind HEAD hook flushes the OLD framebuffer.
            commandBufferQuad(16, 16, 80, 80, 0, 0, 255);
            long[] mainPending = commandBufferCounters(backend, counters);
            result.add("mainPending", commandBufferCounterJson(mainPending));
            commandBufferSecondary.bindFramebuffer(true);
            long[] mainFlushed = commandBufferCounters(backend, counters);
            result.add("afterBindSecondary", commandBufferCounterJson(mainFlushed));
            commandBufferMatrices(256, 256);
            // Two separate begin/draw pairs with identical state/texture/matrices: merging must rebase their indices.
            commandBufferQuad(24, 96, 88, 160, 255, 0, 0);
            commandBufferQuad(144, 184, 208, 240, 0, 255, 0);
            long[] secondaryPending = commandBufferCounters(backend, counters);
            result.add("secondaryPending", commandBufferCounterJson(secondaryPending));
            main.bindFramebuffer(true);
            long[] secondaryFlushed = commandBufferCounters(backend, counters);
            result.add("afterBindMain", commandBufferCounterJson(secondaryFlushed));
            commandBufferMatrices(main.framebufferWidth, main.framebufferHeight);
            commandBufferQuad(112, 16, 176, 80, 255, 255, 0);
            GlStateManager.clear(0);
            long[] after = commandBufferCounters(backend, counters);
            result.add("after", commandBufferCounterJson(after));
            long[] delta = new long[before.length];
            for (int i = 0; i < delta.length; i++) delta[i] = after[i] - before[i];
            result.add("delta", commandBufferCounterJson(delta));

            // Both reads and PNGs occur in this same event, before another frame can hide a dropped or deferred batch.
            java.awt.image.BufferedImage mainAfter = commandBufferReadback(main);
            java.awt.image.BufferedImage secondaryAfter = commandBufferReadback(commandBufferSecondary);
            commandBufferSavePng(mc, "celtium-command-buffer-main.png", mainAfter);
            commandBufferSavePng(mc, "celtium-command-buffer-secondary.png", secondaryAfter);
            result.addProperty("mainPng", "screenshots/celtium-command-buffer-main.png");
            result.addProperty("secondaryPng", "screenshots/celtium-command-buffer-secondary.png");
            int blueWrong = commandBufferRectangleMismatches(mainAfter, 16, 16, 80, 80, 0x0000FF);
            int yellowWrong = commandBufferRectangleMismatches(mainAfter, 112, 16, 176, 80, 0xFFFF00);
            int redWrong = commandBufferRectangleMismatches(secondaryAfter, 24, 96, 88, 160, 0xFF0000);
            int greenWrong = commandBufferRectangleMismatches(secondaryAfter, 144, 184, 208, 240, 0x00FF00);
            int mainBackgroundWrong = 0;
            for (int y = 0; y < mainAfter.getHeight(); y++) {
                for (int x = 0; x < mainAfter.getWidth(); x++) {
                    if (!commandBufferInside(x, y, 16, 16, 80, 80) && !commandBufferInside(x, y, 112, 16, 176, 80)
                            && (mainBefore.getRGB(x, y) & 0xFFFFFF) != (mainAfter.getRGB(x, y) & 0xFFFFFF)) {
                        mainBackgroundWrong++;
                    }
                }
            }
            int secondaryBackgroundWrong = 0;
            for (int y = 0; y < 256; y++) {
                for (int x = 0; x < 256; x++) {
                    if (!commandBufferInside(x, y, 24, 96, 88, 160) && !commandBufferInside(x, y, 144, 184, 208, 240)
                            && (secondaryAfter.getRGB(x, y) & 0xFFFFFF) != 0x081020) secondaryBackgroundWrong++;
                }
            }
            JsonObject pixels = new JsonObject();
            pixels.addProperty("mainBlue", Integer.toHexString(mainAfter.getRGB(48, 48) & 0xFFFFFF));
            pixels.addProperty("mainYellow", Integer.toHexString(mainAfter.getRGB(144, 48) & 0xFFFFFF));
            pixels.addProperty("secondaryRed", Integer.toHexString(secondaryAfter.getRGB(56, 128) & 0xFFFFFF));
            pixels.addProperty("secondaryGreen", Integer.toHexString(secondaryAfter.getRGB(176, 212) & 0xFFFFFF));
            pixels.addProperty("secondaryBackground", Integer.toHexString(secondaryAfter.getRGB(8, 8) & 0xFFFFFF));
            pixels.addProperty("mainBlueMismatchPixels", blueWrong);
            pixels.addProperty("mainYellowMismatchPixels", yellowWrong);
            pixels.addProperty("secondaryRedMismatchPixels", redWrong);
            pixels.addProperty("secondaryGreenMismatchPixels", greenWrong);
            pixels.addProperty("mainUntouchedChangedPixels", mainBackgroundWrong);
            pixels.addProperty("secondaryUntouchedMismatchPixels", secondaryBackgroundWrong);
            result.add("pixels", pixels);
            boolean mainWasPending = mainPending[0] - before[0] == 1 && mainPending[4] == 1
                    && mainPending[1] == before[1] && mainPending[2] == before[2] && mainPending[3] == before[3];
            boolean mainFlushedBeforeSecondary = mainFlushed[4] == 0 && mainFlushed[1] - before[1] == 1
                    && mainFlushed[2] - before[2] == 1 && mainFlushed[3] - before[3] == 1;
            boolean secondaryWasPending = secondaryPending[4] == 2 && secondaryPending[6] - before[6] == 2;
            boolean secondaryFlushedBeforeMain = secondaryFlushed[4] == 0 && secondaryFlushed[7] - before[7] == 2
                    && secondaryFlushed[8] - before[8] == 2;
            boolean executionPassed = delta[0] == 4 && delta[1] == 4 && delta[2] == 4 && delta[3] == 4
                    && after[4] == 0 && before[5] == 0 && after[5] == 0
                    && delta[6] == 2 && delta[7] == 2 && delta[8] == 2;
            boolean pixelsPassed = blueWrong == 0 && yellowWrong == 0 && redWrong == 0 && greenWrong == 0
                    && mainBackgroundWrong == 0 && secondaryBackgroundWrong == 0;
            boolean retryRequired = Integer.getInteger("celtium.vulkan.failImmediateSubmits", 0).intValue() > 0;
            // An injected failure may fire in an earlier native window; report both total and fixture-local retries.
            boolean retryPassed = !retryRequired || delta[9] == 1;
            result.addProperty("retryRequired", retryRequired);
            result.addProperty("retryObservedTotal", after[9]);
            result.addProperty("retryObservedInFixture", delta[9]);
            result.addProperty("retryPassed", retryPassed);
            result.addProperty("mainWasPending", mainWasPending);
            result.addProperty("mainFlushedBeforeSecondary", mainFlushedBeforeSecondary);
            result.addProperty("secondaryWasPending", secondaryWasPending);
            result.addProperty("secondaryFlushedBeforeMain", secondaryFlushedBeforeMain);
            result.addProperty("executionPassed", executionPassed);
            result.addProperty("pixelsPassed", pixelsPassed);
            commandBufferTargetsPassed = mainWasPending && mainFlushedBeforeSecondary && secondaryWasPending
                    && secondaryFlushedBeforeMain && executionPassed && pixelsPassed && retryPassed;
            // The other half of the clear ordering: a batch accepted *before* a clear of the same target must be drawn, and
            // then erased by that clear - not drawn afterwards, where it would survive. The flush-before-clear path is the
            // only thing that decides this, so the pixel check is what proves it.
            commandBufferSecondary.bindFramebuffer(true);
            long[] beforeClear = commandBufferCounters(backend, counters);
            commandBufferMatrices(256, 256);
            commandBufferQuad(16, 16, 80, 80, 0, 0, 255);
            long[] pendingBeforeClear = commandBufferCounters(backend, counters);
            // framebufferClear() binds (same target, so no switch flush) and then clears with the fixture colour, which
            // makes the clear an explicit ordering boundary for the pending batch.
            commandBufferSecondary.framebufferClear();
            long[] afterClear = commandBufferCounters(backend, counters);
            java.awt.image.BufferedImage secondaryCleared = commandBufferReadback(commandBufferSecondary);
            commandBufferSavePng(mc, "celtium-command-buffer-secondary-cleared.png", secondaryCleared);
            int blueAfterClearWrong = commandBufferRectangleMismatches(secondaryCleared, 16, 16, 80, 80, 0x081020);
            int clearedBackgroundWrong = 0;
            for (int y = 0; y < 256; y++) {
                for (int x = 0; x < 256; x++) {
                    if ((secondaryCleared.getRGB(x, y) & 0xFFFFFF) != 0x081020) clearedBackgroundWrong++;
                }
            }
            result.add("beforeClear", commandBufferCounterJson(beforeClear));
            result.add("pendingBeforeClear", commandBufferCounterJson(pendingBeforeClear));
            result.add("afterClear", commandBufferCounterJson(afterClear));
            result.addProperty("secondaryClearedPng", "screenshots/celtium-command-buffer-secondary-cleared.png");
            result.addProperty("blueAfterClearMismatchPixels", blueAfterClearWrong);
            result.addProperty("clearedBackgroundMismatchPixels", clearedBackgroundWrong);
            boolean pendingBeforeClearObserved = pendingBeforeClear[4] - beforeClear[4] == 1;
            boolean clearedAfterDrawing = afterClear[4] == 0 && afterClear[0] - beforeClear[0] == 1
                    && afterClear[1] - beforeClear[1] == 1 && afterClear[2] - beforeClear[2] == 1
                    && afterClear[3] - beforeClear[3] == 1;
            // The batch is gone from the picture: every pixel is the clear colour, including where it was drawn.
            boolean clearOrderPassed = pendingBeforeClearObserved && clearedAfterDrawing
                    && blueAfterClearWrong == 0 && clearedBackgroundWrong == 0;
            result.addProperty("pendingBeforeClearObserved", pendingBeforeClearObserved);
            result.addProperty("clearedAfterDrawing", clearedAfterDrawing);
            result.addProperty("clearOrderPassed", clearOrderPassed);
            // Depth ordering across the bridge: the near batch is drawn first and the far batch second over the same
            // rectangle, so the far one can only be missing if the depth test ran against the depth the bridge carried.
            // This also exercises the D24 -> D32 -> D24 round trip, because the second batch compares against depth the
            // first one wrote inside a Vulkan pass.
            commandBufferSecondary.bindFramebuffer(true);
            commandBufferSecondary.setFramebufferColor(0.0F, 0.0F, 0.0F, 1.0F);
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.clearDepth(1.0D);
            commandBufferSecondary.framebufferClear();
            // framebufferClear() ends with unbindFramebuffer(), so the target has to be bound again before drawing. The
            // first attempt drew into the default framebuffer, which the bridge refuses on purpose: it will not infer a
            // render target from a vertex format. Re-binding also restores the viewport the clear left behind.
            commandBufferSecondary.bindFramebuffer(true);
            GlStateManager.colorMask(true, true, true, true);
            commandBufferMatrices(256, 256);
            java.lang.reflect.Method refusal = backend.getClass().getMethod("lastRefusal");
            // Where the refusal comes from has to be visible: the contract covers the draw framebuffer, the viewport and the
            // attachment shapes, and three of those look identical in the reason string.
            java.nio.IntBuffer probeViewport = org.lwjgl.BufferUtils.createIntBuffer(4);
            GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, probeViewport);
            int probeFbo = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
            int probeColor = GL45C.glGetNamedFramebufferAttachmentParameteri(probeFbo, GL30C.GL_COLOR_ATTACHMENT0,
                    GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            int probeDepth = GL45C.glGetNamedFramebufferAttachmentParameteri(probeFbo, GL30C.GL_DEPTH_ATTACHMENT,
                    GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            int probeColorType = GL45C.glGetNamedFramebufferAttachmentParameteri(probeFbo, GL30C.GL_COLOR_ATTACHMENT0,
                    GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            int probeW = probeColorType == GL11C.GL_TEXTURE
                    ? GL45C.glGetTextureLevelParameteri(probeColor, 0, GL11C.GL_TEXTURE_WIDTH) : -1;
            int probeH = probeColorType == GL11C.GL_TEXTURE
                    ? GL45C.glGetTextureLevelParameteri(probeColor, 0, GL11C.GL_TEXTURE_HEIGHT) : -1;
            result.addProperty("depthPhaseViewport", probeViewport.get(0) + "," + probeViewport.get(1) + ","
                    + probeViewport.get(2) + "x" + probeViewport.get(3));
            result.addProperty("depthPhaseDrawFbo", probeFbo);
            result.addProperty("depthPhaseColorTexture", probeColor + " type=" + probeColorType + " " + probeW + "x" + probeH);
            result.addProperty("depthPhaseDepthAttachment", probeDepth);
            result.addProperty("depthPhaseDrawBuffer0", GL11C.glGetInteger(GL30C.GL_DRAW_BUFFER0));
            result.addProperty("depthPhaseReadBuffer", GL11C.glGetInteger(GL11C.GL_READ_BUFFER));
            // glOrtho(near=-1, far=1) looks down -z, so in this eye space a *larger* z is nearer: z=+0.5 is the near
            // plane and z=-0.5 the far one. The near batch goes first and the far one second, which is the only order
            // that distinguishes a working depth test (near stays) from none at all (the last batch wins).
            commandBufferQuadZ(96, 96, 160, 160, 0, 0, 255, 0.5F); // near, first
            long[] afterNear = commandBufferCounters(backend, counters);
            String nearRefusal = String.valueOf(refusal.invoke(backend));
            commandBufferQuadZ(96, 96, 160, 160, 255, 0, 0, -0.5F); // far, second: must be rejected
            long[] afterFar = commandBufferCounters(backend, counters);
            String farRefusal = String.valueOf(refusal.invoke(backend));
            result.add("afterNearQuad", commandBufferCounterJson(afterNear));
            result.add("afterFarQuad", commandBufferCounterJson(afterFar));
            result.addProperty("nearQuadRefusal", nearRefusal);
            result.addProperty("farQuadRefusal", farRefusal);
            GlStateManager.clear(0);
            long[] afterDepth = commandBufferCounters(backend, counters);
            java.awt.image.BufferedImage depthImage = commandBufferReadback(commandBufferSecondary);
            commandBufferSavePng(mc, "celtium-command-buffer-secondary-depth.png", depthImage);
            int nearKeptWrong = commandBufferRectangleMismatches(depthImage, 96, 96, 160, 160, 0x0000FF);
            int farLeakedPixels = 0;
            for (int y = 96; y < 160; y++) {
                for (int x = 96; x < 160; x++) {
                    if ((depthImage.getRGB(x, y) & 0xFFFFFF) == 0xFF0000) farLeakedPixels++;
                }
            }
            result.add("afterDepth", commandBufferCounterJson(afterDepth));
            result.addProperty("secondaryDepthPng", "screenshots/celtium-command-buffer-secondary-depth.png");
            result.addProperty("centerPixelAfterDepth", Integer.toHexString(depthImage.getRGB(128, 128) & 0xFFFFFF));
            result.addProperty("nearRectangleMismatchPixels", nearKeptWrong);
            result.addProperty("farLeakedPixels", farLeakedPixels);
            boolean depthCountersAdvanced = afterDepth[0] - afterClear[0] == 2 && afterDepth[1] - afterClear[1] == 2
                    && afterDepth[2] - afterClear[2] == 2 && afterDepth[3] - afterClear[3] == 2 && afterDepth[4] == 0;
            // The near colour fills the rectangle and not one pixel of the far colour survived.
            boolean depthOrderPassed = depthCountersAdvanced && nearKeptWrong == 0 && farLeakedPixels == 0;
            result.addProperty("depthCountersAdvanced", depthCountersAdvanced);
            result.addProperty("depthOrderPassed", depthOrderPassed);
            result.addProperty("depthPassed", depthOrderPassed);
            commandBufferTargetsPassed = commandBufferTargetsPassed && depthOrderPassed;
            if (!depthOrderPassed) result.addProperty("error", "depth-order check failed: the far batch was not rejected by the near batch's depth");
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            commandBufferTargetsPassed = commandBufferTargetsPassed && clearOrderPassed;
            if (!clearOrderPassed) result.addProperty("error", "clear-order check failed: the accepted batch was not drawn before the clear or survived it");
            result.addProperty("passed", commandBufferTargetsPassed);
            if (!commandBufferTargetsPassed) result.addProperty("error", "Required format-2 pending/flush/execution/pixel checks failed");
        } catch (Throwable error) {
            commandBufferTargetsPassed = false;
            result.addProperty("passed", false);
            result.addProperty("error", error.toString());
            error.printStackTrace();
        } finally {
            if (saved != null) {
                try {
                    saved.restore(mc.getFramebuffer());
                } catch (Throwable error) {
                    commandBufferTargetsPassed = false;
                    result.addProperty("passed", false);
                    result.addProperty("restoreError", error.toString());
                    error.printStackTrace();
                }
            }
        }
        System.out.println("[Celtium] CELTIUM_COMMAND_BUFFER_TARGETS " + result);
    }

    private static void commandBufferMatrices(int width, int height) {
        GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0D, width, height, 0.0D, -1.0D, 1.0D);
        GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
        GlStateManager.loadIdentity();
    }

    private static void commandBufferQuad(int left, int top, int right, int bottom, int red, int green, int blue) {
        commandBufferQuadZ(left, top, right, bottom, red, green, blue, 0.0F);
    }

    /** The same quad at an explicit depth: with the fixture's ortho (-1 near, 1 far) a smaller z is nearer. */
    private static void commandBufferQuadZ(int left, int top, int right, int bottom, int red, int green, int blue,
                                           float z) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.setTranslation(0.0D, 0.0D, 0.0D);
        // POSITION_COLOR, not POSITION_TEX_COLOR: the target-group acceptance is about which render target a batch
        // belongs to and in what order the groups are flushed, and the UVs here were a constant (0.5, 0.5) that sampled
        // one texel. Format 2 is the frame composite and belongs to the present path once
        // celtium.vulkan.presentFromComposite is on, which made this fixture report passed=false while the frame itself
        // was fine. Drawing with a format the immediate path owns keeps the property under test covered either way.
        buffer.begin(GL11C.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(left, bottom, z).color(red, green, blue, 255).endVertex();
        buffer.pos(right, bottom, z).color(red, green, blue, 255).endVertex();
        buffer.pos(right, top, z).color(red, green, blue, 255).endVertex();
        buffer.pos(left, top, z).color(red, green, blue, 255).endVertex();
        tessellator.draw();
    }

    private static long[] commandBufferCounters(Object backend, java.lang.reflect.Method method) throws Exception {
        Object value = method.invoke(backend);
        if (!(value instanceof long[]) || ((long[]) value).length != 10) {
            throw new IllegalStateException("immediateExecutionCounters must return ten execution counters");
        }
        return ((long[]) value).clone();
    }

    private static JsonObject commandBufferCounterJson(long[] values) {
        String[] names = {"accepted", "recorded", "submitted", "completed", "pending", "invalidDraws",
                "offTargetAccepted", "offTargetRecorded", "offTargetCompleted", "retries"};
        JsonObject result = new JsonObject();
        for (int i = 0; i < names.length; i++) result.addProperty(names[i], values[i]);
        return result;
    }

    /**
     * Writes the lightmap texture the game itself has in OpenGL to {@code <dumpDir>/gl-lightmap-live.png}.
     * <p>
     * The backend's own dump shows what the mirror handed the shader. Reading the same texture from GL in the same run is
     * what separates a stale mirror from a sampling difference, and neither number can be obtained from the other: the
     * terrain comparison showed a dark ground whose tint matches the daylight lightmap texel exactly.
     */
    /**
     * Writes the block atlas the game itself has in OpenGL to {@code <dumpDir>/gl-atlas-live.png}.
     * <p>
     * The terrain samples the mirrored atlas, so a texture that is subtly darker than GL's own would darken every
     * terrain pixel while leaving sky and fog untouched - which is the shape of the measured difference. Reading GL's
     * live atlas in the same run is the only way to tell that apart from a shading difference.
     */
    private static void dumpLiveAtlas(Minecraft mc, String directory) {
        try {
            Object textureMap = mc.getTextureMapBlocks();
            if (!(textureMap instanceof net.minecraft.client.renderer.texture.AbstractTexture)) return;
            int id = ((net.minecraft.client.renderer.texture.AbstractTexture) textureMap).getGlTextureId();
            int previous = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, id);
            int width = GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_WIDTH);
            int height = GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_HEIGHT);
            if (width > 0 && height > 0 && width <= 4096 && height <= 4096) {
                java.nio.ByteBuffer pixels = org.lwjgl.BufferUtils.createByteBuffer(width * height * 4);
                GL11C.glGetTexImage(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pixels);
                java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height,
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int offset = (y * width + x) * 4;
                        image.setRGB(x, y, (0xFF << 24) | ((pixels.get(offset) & 0xFF) << 16)
                                | ((pixels.get(offset + 1) & 0xFF) << 8) | (pixels.get(offset + 2) & 0xFF));
                    }
                }
                java.io.File target = new java.io.File(directory, "gl-atlas-live.png");
                target.getParentFile().mkdirs();
                javax.imageio.ImageIO.write(image, "png", target);
            }
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previous);
        } catch (Throwable ignored) {
            // Diagnostic only: the render path must not be affected by a dump failure.
        }
    }

    private static void dumpLiveLightmap(Minecraft mc, String directory) {
        try {
            java.lang.reflect.Field field = findField(mc.entityRenderer.getClass(),
                    new String[] {"lightmapTexture", "field_78513_d"});
            if (field == null) return;
            field.setAccessible(true);
            Object value = field.get(mc.entityRenderer);
            if (!(value instanceof net.minecraft.client.renderer.texture.DynamicTexture)) return;
            int id = ((net.minecraft.client.renderer.texture.DynamicTexture) value).getGlTextureId();
            java.nio.ByteBuffer pixels = org.lwjgl.BufferUtils.createByteBuffer(16 * 16 * 4);
            int previous = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, id);
            GL11C.glGetTexImage(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pixels);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previous);
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(16, 16,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int offset = (y * 16 + x) * 4;
                    image.setRGB(x, y, (0xFF << 24) | ((pixels.get(offset) & 0xFF) << 16)
                            | ((pixels.get(offset + 1) & 0xFF) << 8) | (pixels.get(offset + 2) & 0xFF));
                }
            }
            java.io.File target = new java.io.File(directory, "gl-lightmap-live.png");
            target.getParentFile().mkdirs();
            javax.imageio.ImageIO.write(image, "png", target);
        } catch (Throwable ignored) {
            // Diagnostic only: the render path must not be affected by a dump failure.
        }
    }

    private static boolean commandBufferInside(int x, int y, int left, int top, int right, int bottom) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    private static int commandBufferRectangleMismatches(java.awt.image.BufferedImage image,
            int left, int top, int right, int bottom, int expected) {
        int mismatches = 0;
        // Exclude rasterisation edges; interior checks still cover thousands of pixels, not just a lucky centre sample.
        for (int y = top + 2; y < bottom - 2; y++) {
            for (int x = left + 2; x < right - 2; x++) {
                if ((image.getRGB(x, y) & 0xFFFFFF) != expected) mismatches++;
            }
        }
        return mismatches;
    }

    private static java.awt.image.BufferedImage commandBufferReadback(Framebuffer target) {
        int previousReadFbo = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11C.glGetInteger(GL11C.GL_READ_BUFFER);
        int previousPackBuffer = GL11C.glGetInteger(org.lwjgl.opengl.GL21C.GL_PIXEL_PACK_BUFFER_BINDING);
        int[] packNames = {GL11C.GL_PACK_ALIGNMENT, GL11C.GL_PACK_ROW_LENGTH,
                GL11C.GL_PACK_SKIP_ROWS, GL11C.GL_PACK_SKIP_PIXELS};
        int[] previousPack = new int[packNames.length];
        for (int i = 0; i < packNames.length; i++) previousPack[i] = GL11C.glGetInteger(packNames[i]);
        int width = target.framebufferWidth;
        int height = target.framebufferHeight;
        java.nio.ByteBuffer pixels = java.nio.ByteBuffer.allocateDirect(width * height * 4);
        try {
            // READ-only binding does not change the tracked DRAW target or create another ordering boundary.
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, target.framebufferObject);
            GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0);
            org.lwjgl.opengl.GL15C.glBindBuffer(org.lwjgl.opengl.GL21C.GL_PIXEL_PACK_BUFFER, 0);
            for (int i = 0; i < packNames.length; i++) GL11C.glPixelStorei(packNames[i], i == 0 ? 1 : 0);
            GL11C.glReadPixels(0, 0, width, height, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pixels);
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int offset = (y * width + x) * 4;
                    int argb = (pixels.get(offset + 3) & 0xFF) << 24 | (pixels.get(offset) & 0xFF) << 16
                            | (pixels.get(offset + 1) & 0xFF) << 8 | (pixels.get(offset + 2) & 0xFF);
                    image.setRGB(x, height - 1 - y, argb);
                }
            }
            return image;
        } finally {
            for (int i = 0; i < packNames.length; i++) GL11C.glPixelStorei(packNames[i], previousPack[i]);
            org.lwjgl.opengl.GL15C.glBindBuffer(org.lwjgl.opengl.GL21C.GL_PIXEL_PACK_BUFFER, previousPackBuffer);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, previousReadFbo);
            GL11C.glReadBuffer(previousReadBuffer);
        }
    }

    private static void commandBufferSavePng(Minecraft mc, String name, java.awt.image.BufferedImage image)
            throws java.io.IOException {
        Path target = mc.gameDir.toPath().resolve("screenshots").resolve(name);
        Files.createDirectories(target.getParent());
        if (!javax.imageio.ImageIO.write(image, "png", target.toFile())) {
            throw new java.io.IOException("No PNG writer for " + name);
        }
    }

    /** Restore through GlStateManager so its cache and the backend mirror agree with GL; never use push/popAttrib. */
    private static final class CommandBufferFixtureState {
        private final int matrixMode = GL11C.glGetInteger(org.lwjgl.opengl.GL11.GL_MATRIX_MODE);
        private final int activeTexture = GL11C.glGetInteger(org.lwjgl.opengl.GL13C.GL_ACTIVE_TEXTURE);
        private final int readFramebuffer = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        private final int readBuffer = GL11C.glGetInteger(GL11C.GL_READ_BUFFER);
        private final boolean depth = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
        private final boolean depthMask = GL11C.glGetInteger(GL11C.GL_DEPTH_WRITEMASK) != 0;
        private final boolean blend = GL11C.glIsEnabled(GL11C.GL_BLEND);
        private final boolean cull = GL11C.glIsEnabled(GL11C.GL_CULL_FACE);
        private final boolean alpha = GL11C.glIsEnabled(org.lwjgl.opengl.GL11.GL_ALPHA_TEST);
        private final boolean lighting = GL11C.glIsEnabled(org.lwjgl.opengl.GL11.GL_LIGHTING);
        private final boolean fog = GL11C.glIsEnabled(org.lwjgl.opengl.GL11.GL_FOG);
        private final boolean scissor = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        private final java.nio.IntBuffer viewport = org.lwjgl.BufferUtils.createIntBuffer(4);
        private final java.nio.FloatBuffer color = org.lwjgl.BufferUtils.createFloatBuffer(4);
        private final java.nio.FloatBuffer clearColor = org.lwjgl.BufferUtils.createFloatBuffer(4);
        private final java.nio.ByteBuffer colorMask = org.lwjgl.BufferUtils.createByteBuffer(4);
        private final double clearDepth = GL11C.glGetDouble(GL11C.GL_DEPTH_CLEAR_VALUE);
        private final int texture;
        private final boolean textureEnabled;
        private boolean projectionPushed;
        private boolean modelviewPushed;

        private CommandBufferFixtureState() {
            GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
            GL11C.glGetFloatv(org.lwjgl.opengl.GL11.GL_CURRENT_COLOR, color);
            GL11C.glGetFloatv(GL11C.GL_COLOR_CLEAR_VALUE, clearColor);
            GL11C.glGetBooleanv(GL11C.GL_COLOR_WRITEMASK, colorMask);
            GlStateManager.setActiveTexture(net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit);
            texture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
            textureEnabled = GL11C.glIsEnabled(GL11C.GL_TEXTURE_2D);
        }

        private void pushMatrices() {
            GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);
            GlStateManager.pushMatrix();
            projectionPushed = true;
            GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
            GlStateManager.pushMatrix();
            modelviewPushed = true;
        }

        private void restore(Framebuffer main) {
            main.bindFramebuffer(true);
            if (modelviewPushed) {
                GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
                GlStateManager.popMatrix();
            }
            if (projectionPushed) {
                GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);
                GlStateManager.popMatrix();
            }
            GlStateManager.matrixMode(matrixMode);
            GlStateManager.viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
            GlStateManager.setActiveTexture(net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit);
            GlStateManager.bindTexture(texture);
            if (textureEnabled) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
            GlStateManager.depthMask(depthMask);
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            if (cull) GlStateManager.enableCull(); else GlStateManager.disableCull();
            if (alpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
            if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
            if (fog) GlStateManager.enableFog(); else GlStateManager.disableFog();
            GlStateManager.color(color.get(0), color.get(1), color.get(2), color.get(3));
            GlStateManager.colorMask(colorMask.get(0) != 0, colorMask.get(1) != 0, colorMask.get(2) != 0, colorMask.get(3) != 0);
            GlStateManager.clearColor(clearColor.get(0), clearColor.get(1), clearColor.get(2), clearColor.get(3));
            GlStateManager.clearDepth(clearDepth);
            if (scissor) GL11C.glEnable(GL11C.GL_SCISSOR_TEST); else GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            GlStateManager.setActiveTexture(activeTexture);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL11C.glReadBuffer(readBuffer);
        }
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

    private static void celtium$quadPos(net.minecraft.client.renderer.BufferBuilder buffer,
            double left, double top, double right, double bottom) {
        buffer.pos(left, bottom, 0.0D).endVertex();
        buffer.pos(right, bottom, 0.0D).endVertex();
        buffer.pos(right, top, 0.0D).endVertex();
        buffer.pos(left, top, 0.0D).endVertex();
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
                // Overlay ALL is the required fixture phase; do not let a copied setting make the test unreachable.
                if (commandBufferTargets) mc.gameSettings.hideGUI = false;
                WorldSettings settings = new WorldSettings(12345678L, GameType.CREATIVE, false, false, WorldType.FLAT);
                // The extra layers exercise the cutout (leaves) and translucent (glass) terrain passes, not just SOLID.
                settings.setGeneratorOptions(celtium$worldLayers);
                mc.launchIntegratedServer("celtium-validation", "Celtium validation", settings);
            }
            // Dimension travel: the same player is moved to another dimension, which re-generates terrain with a
            // different sky, fog and block set (the Nether has no sky and its own light rules). The dimension and the
            // tick are both configurable so a run can target the Nether (-1) or the End (1) without a second fixture.
            if (celtium$dimensionAtTick > 0 && worldTicks == celtium$dimensionAtTick && !celtium$changedDimension) {
                celtium$changedDimension = true;
                report.addProperty("dimensionChangeAtTick", Integer.valueOf(worldTicks));
                report.addProperty("dimensionTarget", Integer.valueOf(celtium$dimension));
                try {
                    // The transfer has to be driven by the server: the client player's own changeDimension only moves the
                    // entity locally, and the first attempt measured dimensionAfter=0 - the player stayed in the
                    // Overworld because the server never sent it anywhere. The integrated server owns the transfer, the
                    // client follows, and the run's later frames are then the new dimension's terrain.
                    final net.minecraft.server.integrated.IntegratedServer server = mc.getIntegratedServer();
                    final java.util.UUID playerId = mc.player.getUniqueID();
                    if (server == null) {
                        celtium$transfer = new TransferSnapshot("no integrated server", worldTicks, "unknown");
                    } else {
                        // The transfer runs as a scheduled server task rather than inline from the client tick: the
                        // first attempt called it from the tick hook and vanilla's transfer path dereferenced a look
                        // vector that is only set up during the server's own player tick, which is the NPE that ended
                        // that run. addScheduledTask runs it at the server's next safe point.
                        // worlds is a plain array indexed 0..n, not by dimension id: indexing it with -1 threw
                        // ArrayIndexOutOfBoundsException and the transfer never happened. getWorld maps the id properly.
                        {
                            server.addScheduledTask(new Runnable() {
                                @Override
                                public void run() {
                                    // Everything that touches the server - resolving the player, resolving the target
                                    // world, the transfer itself - happens here. Resolving the world on the client
                                    // thread was what the run log showed as "[Client thread] Loading dimension -1".
                                    net.minecraft.entity.player.EntityPlayerMP player = server.getPlayerList()
                                            .getPlayerByUUID(playerId);
                                    if (player == null) {
                                        celtium$transfer = new TransferSnapshot("no server player", worldTicks, "unknown");
                                        return;
                                    }
                                    if (player.dimension == celtium$dimension) {
                                        celtium$transfer = new TransferSnapshot("already there", worldTicks, "unknown");
                                        return;
                                    }
                                    net.minecraft.world.WorldServer target = server.getWorld(celtium$dimension);
                                    if (target == null) {
                                        celtium$transfer = new TransferSnapshot("no world for dimension " + celtium$dimension, worldTicks, "unknown");
                                        return;
                                    }
                                    // Minecraft's getVectorForRotation returns null - and getLook then dereferences it - when the
                                    // pitch is not a number, and the transfer path asks for the look vector. The values are
                                    // recorded before they are replaced, because "the rotation was NaN" is the finding here,
                                    // not something to paper over silently.
                                    final String rotation = "pitch=" + player.rotationPitch + " yaw=" + player.rotationYaw;
                                    if (Float.isNaN(player.rotationPitch) || Float.isInfinite(player.rotationPitch)) {
                                        player.rotationPitch = 0.0F;
                                    }
                                    if (Float.isNaN(player.prevRotationPitch) || Float.isInfinite(player.prevRotationPitch)) {
                                        player.prevRotationPitch = 0.0F;
                                    }
                                    if (Float.isNaN(player.rotationYaw) || Float.isInfinite(player.rotationYaw)) {
                                        player.rotationYaw = 0.0F;
                                    }
                                    if (Float.isNaN(player.prevRotationYaw) || Float.isInfinite(player.prevRotationYaw)) {
                                        player.prevRotationYaw = 0.0F;
                                    }
                                    if (Float.isNaN(player.rotationYawHead) || Float.isInfinite(player.rotationYawHead)) {
                                        player.rotationYawHead = 0.0F;
                                    }
                                    // Teleporter.placeInExistingPortal reads entity.getLastPortalVec(), which is filled in by
                                    // Entity.setPortal when an entity stands in a portal block. A transfer made without
                                    // touching a portal leaves it null and the vanilla teleporter throws on it (the trace
                                    // named Teleporter.func_180620_b, line 143). The player is marked as being in a portal
                                    // at their own position, which is what the teleporter needs - and it will then build or
                                    // find a portal at the destination, exactly as a real trip would.
                                    player.setPortal(new net.minecraft.util.math.BlockPos(player));
                                    try {
                                        server.getPlayerList().transferPlayerToDimension(player, celtium$dimension,
                                                new net.minecraft.world.Teleporter(target));
                                        // The teleporter lands the player inside the portal it found or built, and standing
                                        // in one makes the game's own portal logic in Entity.onEntityUpdate send them back
                                        // (measured with a caller chain: changeDimension from Entity.updateEntityActionState
                                        // at tick ~96, 55 ticks after the arrival, full health, chunk loaded). Stepping out
                                        // of the portal keeps the arrival. A portal is one block thick perpendicular to
                                        // its plane, so the step out has to be perpendicular: the earlier +2Z always was
                                        // wrong for a portal whose AXIS is Z (the Nether run still returned at ~148, which
                                        // is the portal counter finishing ~80 ticks in the block). The block's AXIS says
                                        // which way the plane runs - axis X means the plane runs along X and the thickness
                                        // (the way out) is Z, axis Z means thickness is X.
                                        net.minecraft.block.state.IBlockState arrival =
                                                target.getBlockState(new net.minecraft.util.math.BlockPos(player));
                                        double stepX = 0.0D, stepZ = 2.0D;
                                        if (arrival.getBlock() == net.minecraft.init.Blocks.PORTAL) {
                                            if (arrival.getValue(net.minecraft.block.BlockPortal.AXIS)
                                                    == net.minecraft.util.EnumFacing.Axis.Z) {
                                                stepX = 2.0D;
                                                stepZ = 0.0D;
                                            }
                                        }
                                        player.setPositionAndUpdate(player.posX + stepX, player.posY, player.posZ + stepZ);
                                        player.timeUntilPortal = 0;
                                        celtium$transfer = new TransferSnapshot("transferred", worldTicks, rotation);
                                    } catch (Throwable error) {
                                        celtium$transfer = new TransferSnapshot("error: " + error, worldTicks, rotation);
                                        // The frames are collected into the same field: a message naming a Forge/SRG
                                        // symbol is not enough to find the call that failed.
                                        StringBuilder trace = new StringBuilder();
                                        StackTraceElement[] frames = error.getStackTrace();
                                        for (int i = 0; i < frames.length && i < 6; i++) {
                                            trace.append(frames[i].toString()).append(" | ");
                                        }
                                        celtium$transfer = new TransferSnapshot("error: " + error + " || " + trace, worldTicks, rotation);
                                    }
                                }
                            });
                        }
                    }
                    report.addProperty("dimensionAfter", Integer.valueOf(mc.player.dimension));
                } catch (Throwable error) {
                    report.addProperty("dimensionError", String.valueOf(error));
                }
            }
            // The arrival is observed on the client, which is the side that has to be in the new dimension for the
            // capture to mean anything: a scheduled server task is not evidence that the client followed it.
            if (celtium$dimensionAtTick > 0 && celtium$arrivedAtTick < 0 && celtium$changedDimension
                    && mc.player != null && mc.world != null
                    && mc.player.dimension == celtium$dimension
                    && mc.world.provider.getDimension() == celtium$dimension) {
                celtium$arrivedAtTick = worldTicks;
                celtium$arrivedFrames = worldFrames;
            }
            // Per-tick dimension trace while a dimension run is in flight: the Nether run arrived at tick 81 and was
            // back in dimension 0 by the capture, and a single reading cannot say when or why. The server side is asked
            // for its own value on the same tick, so a client-only change can be told from a real transfer back.
            if (celtium$dimensionAtTick > 0 && celtium$changedDimension) {
                int clientDim = mc.player == null ? Integer.MIN_VALUE : mc.player.dimension;
                int worldDim = mc.world == null ? Integer.MIN_VALUE : mc.world.provider.getDimension();
                int serverDim = Integer.MIN_VALUE;
                net.minecraft.server.integrated.IntegratedServer traceServer = mc.getIntegratedServer();
                if (traceServer != null && mc.player != null) {
                    net.minecraft.entity.player.EntityPlayerMP tracePlayer =
                            traceServer.getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
                    if (tracePlayer != null) {
                        serverDim = tracePlayer.dimension;
                    }
                }
                boolean changed = celtium$lastTraceClient != clientDim || celtium$lastTraceServer != serverDim;
                if (changed || worldTicks % 5 == 0) {
                    System.out.println("[Celtium] CELTIUM_DIM_TRACE tick=" + worldTicks + " client=" + clientDim
                            + " world=" + worldDim + " server=" + serverDim
                            + " screen=" + (mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName())
                            + " arrivedAt=" + celtium$arrivedAtTick
                            // The client's own position and whether the chunk under it is loaded decide which side of the
                            // wait is stuck: no chunk means the server never finished sending it, a loaded chunk with the
                            // screen still up means only the screen is left behind.
                            + " pos=" + (mc.player == null ? "-" : (int) mc.player.posX + "," + (int) mc.player.posY
                                    + "," + (int) mc.player.posZ)
                            + " chunkLoaded=" + (mc.world == null || mc.player == null ? "-"
                                    : mc.world.isBlockLoaded(mc.player.getPosition()))
                            // Whether the player is standing in a portal block is the candidate for the return to the
                            // previous dimension: a transfer that lands inside one is sent back by the game's own
                            // portal logic, which would look exactly like this trace.
                            // Health is what distinguishes "something transferred the player back" from "the player died
                            // and respawned in the previous dimension", which is what the falling positions after the
                            // return look like.
                            + " health=" + (mc.player == null ? "-" : mc.player.getHealth() + "/" + mc.player.getMaxHealth())
                            + " onGround=" + (mc.player == null ? "-" : mc.player.onGround)
                            + " inPortal=" + (mc.player == null || mc.world == null ? "-"
                                    : (mc.world.getBlockState(mc.player.getPosition()).getBlock()
                                            == net.minecraft.init.Blocks.PORTAL)));
                }
                celtium$lastTraceClient = clientDim;
                celtium$lastTraceServer = serverDim;
                if (changed && celtium$dimensionEvents.length() < 320) {
                    celtium$dimensionEvents.append("t").append(worldTicks).append(":c=").append(clientDim)
                            .append(",s=").append(serverDim).append(' ');
                }
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
                settings.setGeneratorOptions(celtium$worldLayers);
                mc.launchIntegratedServer("celtium-validation", "Celtium validation", settings);
            }
            if (mc.world == null || mc.player == null) return;
            worldTicks++;
            // The value at the end of the run, so "the hook never fired" can be told apart from "the run never got there". Four
            // Intel runs at one budget reached tick 170 once and tick 160 not at all, which is only explicable with the number
            // in hand: the hooks advance one per game tick, and on the slower GPU a tick takes seconds of wall-clock.
            report.addProperty("worldTicksAtEnd", Integer.valueOf(worldTicks));
        celtium$installExitHook(mc);
        // Taken here, on the render thread, where the world, the player and the screen are the client's to read. The
        // shutdown hook only ever reads this value.
        celtium$progress = new ProgressSnapshot(worldTicks, worldFrames, celtium$captureStarted, celtium$captureStartTick,
                mc.player == null ? Integer.MIN_VALUE : mc.player.dimension,
                mc.world == null ? Integer.MIN_VALUE : mc.world.provider.getDimension(),
                mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getName(),
                mc.gameDir.toPath().resolve("logs/celtium-validation.json"));
            celtium$blockSweep(mc);
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
            if (worldTicks == 60 && Boolean.getBoolean("celtium.validation.keepEntities")) {
                // An entity scene, for the coverage that the terrain-only comparison deliberately leaves out. The mobs are
                // named explicitly rather than left to natural spawning so the scene is the same set every run: one of each
                // family that draws through its own path (monster model, spider legs, enderman's TESR-ish layering).
                // The player faces -Z (yaw 180, pitch 20): the spawns sit in that view cone, at ground level, so the
                // comparison sees the models rather than measuring particles behind an out-of-frame mob.
                // Eight to twelve blocks ahead, where pitch-20-down keeps a ground-standing model inside the frame:
                // anything closer lands below the view cone's lower edge and the comparison only measures particles.
                celtium$spawnEntity(mc, new net.minecraft.entity.monster.EntityCreeper(mc.world), -2.0D, -8.0D);
                celtium$spawnEntity(mc, new net.minecraft.entity.monster.EntityZombie(mc.world), 0.0D, -10.0D);
                celtium$spawnEntity(mc, new net.minecraft.entity.monster.EntitySpider(mc.world), 2.0D, -8.0D);
                celtium$spawnEntity(mc, new net.minecraft.entity.monster.EntityEnderman(mc.world), -3.0D, -12.0D);
                // TESR and item coverage in the same view cone: a chest and a sign draw through their special
                // renderers (a model display list plus the sign's text quads), and the dropped item is the item
                // entity's baked-model path rather than an entity model list.
                int px = (int) Math.floor(mc.player.posX), pz = (int) Math.floor(mc.player.posZ);
                int gy = mc.world.getHeight(px, pz - 6);
                mc.world.setBlockState(new net.minecraft.util.math.BlockPos(px - 1, gy, pz - 6),
                        net.minecraft.init.Blocks.CHEST.getDefaultState(), 3);
                mc.world.setBlockState(new net.minecraft.util.math.BlockPos(px + 1, gy, pz - 7),
                        net.minecraft.init.Blocks.STANDING_SIGN.getDefaultState(), 3);
                net.minecraft.item.ItemStack droppedStack = new net.minecraft.item.ItemStack(net.minecraft.init.Items.APPLE);
                if (Boolean.getBoolean("celtium.validation.enchantItem")) {
                    // An enchantment makes the item's glint pass draw: two texture-matrix-modulated passes over the
                    // same model, which is the state the texture-matrix work exists for.
                    droppedStack.addEnchantment(net.minecraft.init.Enchantments.SHARPNESS, 1);
                }
                net.minecraft.entity.item.EntityItem dropped = new net.minecraft.entity.item.EntityItem(mc.world,
                        mc.player.posX + 1.0D, gy + 0.5D, mc.player.posZ - 5.0D,
                        droppedStack);
                dropped.motionX = 0.0D; dropped.motionY = 0.0D; dropped.motionZ = 0.0D;
                dropped.setNoDespawn();
                celtium$spawnEntity(mc, dropped, 1.0D, -5.0D);
                // A block on the crosshair's line and inside the creative reach, so the selection outline draws: the eye
                // is ~1.62 above posY and the pitch-20-down ray crosses y~8.5 at ~3 blocks out, so a block whose top face
                // is there is what the ray points at.
                mc.world.setBlockState(new net.minecraft.util.math.BlockPos(px, (int) Math.floor(mc.player.posY), pz - 3),
                        net.minecraft.init.Blocks.STONE.getDefaultState(), 3);
                System.out.println("[Celtium] CELTIUM_ENTITIES_SPAWNED tick=" + worldTicks
                        + " nonPlayer=" + countNonPlayerEntities(mc));
            }
            if (worldTicks == 60 && Boolean.getBoolean("celtium.validation.allBlocks")) {
                // The registry sweep: one of every registered block, laid out as a wall the camera sees whole, so
                // the comparison exercises every block model the terrain passes can draw rather than the handful a
                // flat world happens to contain. Blocks that need a tile entity to show anything (the chest, the
                // sign) draw as nothing here, which is honest - their render path is the TESR's, not the chunk's.
                // Each test position gets a stone block under it so standing-on blocks stay placed; liquids and
                // gravity blocks would flow or fall out of the sweep, so they are skipped.
                int px = (int) Math.floor(mc.player.posX), pz = (int) Math.floor(mc.player.posZ);
                int gy = mc.world.getHeight(px, pz - 30);
                int placed = 0, index = 0;
                net.minecraft.block.state.IBlockState support = net.minecraft.init.Blocks.STONE.getDefaultState();
                for (net.minecraft.block.Block block : net.minecraft.block.Block.REGISTRY) {
                    if (block == net.minecraft.init.Blocks.AIR || block == net.minecraft.init.Blocks.WATER
                            || block == net.minecraft.init.Blocks.FLOWING_WATER || block == net.minecraft.init.Blocks.LAVA
                            || block == net.minecraft.init.Blocks.FLOWING_LAVA || block == net.minecraft.init.Blocks.FIRE
                            || block instanceof net.minecraft.block.BlockFalling) {
                        continue;
                    }
                    int x = px - 20 + (index % 41), y = gy + 2 * (index / 41) + 1;
                    index++;
                    try {
                        net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(x, y, pz - 30);
                        mc.world.setBlockState(pos.down(), support, 3);
                        if (mc.world.setBlockState(pos, block.getDefaultState(), 3)) {
                            placed++;
                        }
                    } catch (RuntimeException | LinkageError error) {
                        // A block whose default state cannot stand alone (a door half, a technical block) skips rather
                        // than ending the sweep.
                    }
                }
                System.out.println("[Celtium] CELTIUM_ALL_BLOCKS placed=" + placed + " tried=" + index);
            }
            if (worldTicks >= 150 && !Boolean.getBoolean("celtium.validation.interop")
                    && !Boolean.getBoolean("celtium.validation.keepEntities")) {
                // Terrain-only comparison: mob spawning is not seed-deterministic between runs, so remove every
                // non-player entity and particle well before the capture. Entity and TESR behaviour stays on the
                // existing GL path and is deliberately outside what this comparison measures - unless keepEntities asks
                // for the entity scene, in which case the mobs above stay and the capture is a coverage capture.
                entitiesRemoved += removeNonPlayerEntities(mc);
            }
            // The hurt flash: keeping hurtTime non-zero on the kept mobs makes their second model pass run under the
        // texture-env interpolate the flash is drawn through, which is the state the coverage check wants exercised.
        if (Boolean.getBoolean("celtium.validation.hurtEntities") && worldTicks >= 150 && worldTicks <= 205) {
            for (Object obj : mc.world.loadedEntityList) {
                if (obj instanceof net.minecraft.entity.EntityLivingBase && obj != mc.player) {
                    ((net.minecraft.entity.EntityLivingBase) obj).hurtTime = 10;
                    ((net.minecraft.entity.EntityLivingBase) obj).maxHurtTime = 10;
                }
            }
        }
        // The glow outline: the glowing tag routes every kept mob through the entityOutline framebuffer and its
        // silhouette composite, the same post-process shape the spectator effects take.
        if (Boolean.getBoolean("celtium.validation.glowEntities") && worldTicks >= 150 && worldTicks <= 205) {
            for (Object obj : mc.world.loadedEntityList) {
                if (obj instanceof net.minecraft.entity.Entity && obj != mc.player) {
                    ((net.minecraft.entity.Entity) obj).setGlowing(true);
                }
            }
        }
        if (worldTicks >= 20 && !Boolean.getBoolean("celtium.validation.interop")) {
                // `updateRenderer` has already run for this tick, so zeroing the flicker here affects the lightmap
                // that the next render builds, and pinning the world time keeps the sun angle identical.
                flickerPinned = pinLightmapFlicker(mc);
                mc.world.setWorldTime(PINNED_WORLD_TIME);
                // Rebuilding every chunk mesh once, after the world time is pinned, answers a question the frame alone
                // cannot: if the terrain brightens, the light level was baked into the per-vertex colour when the mesh was
                // built and is never refreshed while that mesh lives, rather than the light map texture being wrong.
                int reloadAtTick = Integer.getInteger("celtium.validation.reloadAtTick", 0).intValue();
                if (reloadAtTick > 0 && worldTicks == reloadAtTick && !reloadDone) {
                    reloadDone = true;
                    mc.renderGlobal.loadRenderers();
                    report.addProperty("renderersReloadedAtTick", worldTicks);
                }
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

    /** Puts one entity next to the player, on the client world the renderer draws from. */
    private static void celtium$spawnEntity(Minecraft mc, net.minecraft.entity.Entity entity, double dx, double dz) {
        entity.setPosition(mc.player.posX + dx, mc.player.posY, mc.player.posZ + dz);
        if (entity instanceof net.minecraft.entity.EntityLiving) {
            // Frozen where it is put, so two runs of the entity scene draw the same pose at the same place: without this
            // the mobs walk and fall between the spawn tick and the capture, and a comparison of the two paths would be
            // measuring the walk. Persistence keeps them from despawning in the meantime.
            net.minecraft.entity.EntityLiving living = (net.minecraft.entity.EntityLiving) entity;
            living.setNoAI(true);
            living.enablePersistence();
        }
        mc.world.spawnEntity(entity);
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
        if (worldTicks == 26 && !celtium$postprocess && Boolean.getBoolean("celtium.validation.postprocess")) {
            // The postprocess inventory: in 1.12.2 that is the shader group behind the "super secret settings", reached through
            // this method on the entity renderer. Called reflectively, and by name list, because it is private and because the
            // check is whether the shader path draws cleanly through the Vulkan path - not whether the name is what I think.
            celtium$postprocess = true;
            Object result = null;
            // Called directly rather than by name: the client runs from a production (SRG-named) jar, so a reflective
            // lookup of the mapped name "activateNextShader" never matches and the earlier run recorded
            // "no method matched by name" - the postprocess scene was never actually activated. This project's own jar is
            // remapped at build time, so a compiled call is the one that works in both environments.
            // EntityRenderer.switchUseShader() is the public entry the "super secret settings" key binding uses; it walks
            // the same shader list the spectator effects (creeper/spider/invert) come from. Called directly, not by name.
            mc.entityRenderer.switchUseShader();
            result = "called switchUseShader directly";
            report.addProperty("postprocessActivated", String.valueOf(result));
            // Printed as well as reported. The report is written at the end of the run and properties added early can be
            // overwritten by it - the recipe flag lost its value exactly that way - so the observation that this hook ran, and
            // what it matched, goes to stdout where nothing can take it away.
            System.out.println("[Celtium] CELTIUM_POSTPROCESS tick=" + worldTicks + " result=" + result);
        }
        if (worldTicks == 30 && !celtium$jeiScreen && Boolean.getBoolean("celtium.validation.jeiScreen")) {
            // Opens the player's inventory so that a mod with an overlay - JEI - draws into it. This is the interaction the
            // plan named as unestablished: JEI loading proves the mod, opening a screen proves that its own rendering goes
            // through the renderer under test. Closed again before the capture so the saved frame stays the normal scene.
            celtium$jeiScreen = true;
            mc.displayGuiScreen(new net.minecraft.client.gui.inventory.GuiInventory(mc.player));
            report.addProperty("jeiScreenOpenedAtTick", worldTicks);
        }
        if (worldTicks == 34 && !celtium$jeiRecipe && Boolean.getBoolean("celtium.validation.jeiRecipe")) {
            // The mod's own heavier screen, opened through its public plugin API rather than by simulating input. Guarded
            // behind its own flag because it needs JEI present and compiled in.
            //
            // At 34, and the screen at 30, rather than 180 and 170. The fixture's ticks advance one per game tick, and on the
            // slower GPU combined with JEI a tick costs seconds of wall-clock: four runs at one budget reached tick 170 once
            // and tick 160 not at all. An absolute tick number is therefore the wrong key for a hook that has to fire under a
            // wall-clock budget - what it needs is the earliest point at which the world is certainly up, which is what 30 and
            // 34 are, just after the command run at 20. The end-of-run count is reported alongside so reachability is measured
            // rather than assumed.
            celtium$jeiRecipe = true;
            try {
                Class<?> plugin = Class.forName("org.soutalab.celtium.validation.CeltiumJeiPlugin");
                Object opened = plugin.getMethod("openFirstRecipeCategory").invoke(null);
                report.addProperty("jeiRecipeOpened", Boolean.TRUE.equals(opened));
                // The tick as well as the boolean, mirroring the screen's field. The previous shape reported the boolean from a
                // path that a later report overwrote, so the run looked as though the recipe screen had never opened while the
                // screen's own tick was right there - a reporting ambiguity rather than a drawing one.
                report.addProperty("jeiRecipeOpenedAtTick", worldTicks);
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
            // The size is settable so that the two extremes can be asked for: 1x1 stands in for a minimised window
            // (Minecraft's own resize clamps a zero request to one, which is what a real minimise ends up as on this
            // harness), and a very large size stresses the framebuffer and swapchain rebuild. The default keeps the
            // behaviour every earlier resize run recorded.
            int resizeWidth = 1024;
            int resizeHeight = 640;
            String resizeTo = System.getProperty("celtium.validation.resizeTo", "");
            if (resizeTo.indexOf('x') > 0) {
                String[] parts = resizeTo.split("x");
                try {
                    resizeWidth = Integer.parseInt(parts[0].trim());
                    resizeHeight = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException error) {
                    report.addProperty("resizeToError", String.valueOf(error));
                }
            }
            mc.resize(Math.max(0, resizeWidth), Math.max(0, resizeHeight));
            report.addProperty("resizedAtTick", worldTicks);
            report.addProperty("resizedTo", resizeWidth + "x" + resizeHeight);
        }
        if (worldTicks >= 150 && !celtium$weatherShown && Boolean.getBoolean("celtium.validation.weather")) {
            celtium$weatherShown = true;
            // Rain, saturated well before the capture so its strength has stopped changing: the renderer interpolates
            // rain strength between ticks, and a value still ramping would make two runs differ for reasons that are not
            // the renderer's.
            mc.world.getWorldInfo().setRaining(true);
            mc.world.getWorldInfo().setRainTime(6000);
            mc.world.getWorldInfo().setThunderTime(6000);
            // The world info alone did not put rain on screen: the client's own rain strength stayed 0.000 through the
            // capture (measured), so nothing was actually being rendered. Both strength fields are public in 1.12.2, so
            // the scene can be set directly - which is what this switch is for.
            mc.world.prevRainingStrength = 1.0F;
            mc.world.rainingStrength = 1.0F;
            mc.world.prevThunderingStrength = 1.0F;
            mc.world.thunderingStrength = 1.0F;
            // The client world alone is not enough on every run: the integrated server keeps sending its own weather
            // state, and on the Intel run the capture found raining=false again - the server's clear weather had
            // overwritten the client's. Setting the server's world info as well is what makes the rain stick.
            String serverWeather = "no integrated server";
            if (mc.getIntegratedServer() != null) {
                net.minecraft.world.WorldServer overworld = mc.getIntegratedServer().worlds[0];
                if (overworld != null) {
                    overworld.getWorldInfo().setRaining(true);
                    overworld.getWorldInfo().setRainTime(6000);
                    overworld.getWorldInfo().setThunderTime(6000);
                    serverWeather = "server raining=" + overworld.getWorldInfo().isRaining();
                }
            }
            report.addProperty("weatherServer", serverWeather);
            // Recorded, because a PASSED run on its own does not show that the scene changed: the postprocess experiment
            // failed exactly this way (the shader was never activated and the run still passed).
            report.addProperty("weatherSet", "raining=" + mc.world.getWorldInfo().isRaining()
                    + " rainTime=" + mc.world.getWorldInfo().getRainTime()
                    + " rainStrength=" + String.format(java.util.Locale.ROOT, "%.3f", Float.valueOf(mc.world.getRainStrength(0.0f))));
        }
        // At or after the tick, not exactly on it: on one Intel run the exact-tick condition never matched (the tick
        // counter stepped over 150) and the scene silently did not happen - the same fragility the injected idle test
        // showed with its wait count. The flag keeps it to once.
        // Some scenes only exist while the player looks at a block: the block-selection outline is drawn from the ray
        // trace, which needs the camera pointed at the ground. Nothing in a headless run moves the camera, so the scene
        // has to say so itself rather than assume the outline was covered.
        // Requested two ticks before the capture so the present path writes the frame at the moment it still owns the
        // image; asking for it at the capture itself read an image the presenter had already given back, which the
        // validation layer refused.
        // Two ticks before the staged capture (which happens at tick 200). The capture tick cannot be read here: it is
        // recorded when the capture happens, so at tick 198 it is still unset.
        // The tick is a property because the presented image belongs to the frame before the request is consumed at the
        // next present: the capture is at 200, so 198 dumps the frame of 198 and 199 the frame of 199. Being able to move
        // it settles whether a remaining difference is that frame gap or the picture itself.
        if (Boolean.getBoolean("celtium.validation.dumpPresentedImage")
                && worldTicks == Integer.getInteger("celtium.validation.dumpPresentedTick", 198).intValue()) {
            try {
                org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend current =
                        org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
                if (current instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) {
                    ((org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) current).celtium$dumpPresentedImage(
                            mc.gameDir.toPath().resolve("screenshots/celtium-presented.png"));
                }
            } catch (RuntimeException | LinkageError ignored) {
                // A measurement must never stop the tick.
            }
        }
        if (Boolean.getBoolean("celtium.validation.allBlocks") && mc.player != null) {
            // The block wall is tall enough that the default downward pitch cuts its top rows off; level view plus a
            // little tilt keeps all of it inside the frame at the wall's distance.
            mc.player.rotationPitch = -14.0F;
            mc.player.prevRotationPitch = -14.0F;
        }
        if (Boolean.getBoolean("celtium.validation.lookDown") && mc.player != null) {
            mc.player.rotationPitch = 75.0F;
            mc.player.prevRotationPitch = 75.0F;
            // The block-selection outline is only drawn while the HUD is shown: drawSelectionBox is skipped when the
            // game's hideGUI is set, and that setting is copied from the instance's options. Without this the scene looks
            // right and the outline is simply never submitted - measured as zero line-primitive refusals and no outline.
            mc.gameSettings.hideGUI = false;
            // The vanilla guard for the outline is "not inside water" (EntityRenderer: !entity.isInsideOfMaterial(WATER)),
            // and the default layer stack puts the player's feet in the water layer (bedrock 3, dirt 4-5, grass 6, water 7),
            // so the outline was skipped before it was ever submitted - measured as zero line-primitive offers while the ray
            // trace hit a block. Lifting the player out of the water is the scene's job, and the harness already takes
            // worldLayers, so this scene is run with a water-free stack (see the plan); forcing a height here only moved the
            // ray out of reach (measured hit=MISS).
            // The outline is drawn from the ray trace, so the trace's own result is the thing that says whether the scene
            // reaches drawSelectionBox at all: a null or non-block result means the outline is never submitted, which is
            // what zero line-primitive offers measured.
            if (worldTicks % 40 == 0) {
                net.minecraft.util.math.RayTraceResult hit = mc.objectMouseOver;
                System.out.println("[Celtium] CELTIUM_MOUSEOVER tick=" + worldTicks + " hit="
                        + (hit == null ? "null" : hit.typeOfHit + "@" + hit.getBlockPos())
                        + " inGameHasFocus=" + mc.inGameHasFocus
                        + " hideGui=" + mc.gameSettings.hideGUI
                        + " screen=" + (mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName())
                        + " guiOpen=" + mc.currentScreen);
            }
        }
        if (worldTicks >= 150 && !celtium$spectator) {
            // The spectator effects the audit lists: creeper, spider and enderman each load their own post shader
            // (shaders/post/creeper.json, spider.json, invert.json) through EntityRenderer.loadEntityShader. The public
            // isShaderActive() is recorded beside it, because a run that passes says nothing about whether the shader
            // was ever active - the postprocess switch taught that.
            String which = System.getProperty("celtium.validation.spectator", "");
            if (!which.isEmpty()) {
                celtium$spectator = true;
                // Java 8 source level in this module: an if/else chain rather than a switch expression.
                net.minecraft.entity.Entity effectEntity = null;
                if ("creeper".equals(which)) {
                    effectEntity = new net.minecraft.entity.monster.EntityCreeper(mc.world);
                } else if ("spider".equals(which)) {
                    effectEntity = new net.minecraft.entity.monster.EntitySpider(mc.world);
                } else if ("enderman".equals(which)) {
                    effectEntity = new net.minecraft.entity.monster.EntityEnderman(mc.world);
                }
                if (effectEntity != null) {
                    // Recorded before the calls, and the call's own outcome beside it: on one Intel run the spider case
                    // produced neither property, so the earlier ordering (record after the call) could not tell an
                    // exception from a scene that never ran.
                    report.addProperty("spectatorEffect", which);
                    try {
                        effectEntity.setLocationAndAngles(mc.player.posX + 2.0D, mc.player.posY, mc.player.posZ, 0.0F, 0.0F);
                        mc.world.spawnEntity(effectEntity);
                        mc.entityRenderer.loadEntityShader(effectEntity);
                        report.addProperty("spectatorResult", "loaded");
                        report.addProperty("shaderActive", mc.entityRenderer.isShaderActive());
                    } catch (Throwable error) {
                        report.addProperty("spectatorResult", "failed: " + error);
                        report.addProperty("shaderActive", mc.entityRenderer.isShaderActive());
                    }
                }
            }
        }
        // Block-breaking coverage: the crack overlay (drawBlockDamageTexture's stage texture on the block faces) is
        // registered directly, so it exists whatever the game mode - creative onPlayerDamageBlock breaks instantly and
        // never shows it. Stage 5 is plain mid-progress damage, refreshed every tick from 150 so it is alive at 200.
        if (Boolean.getBoolean("celtium.validation.breakBlock") && mc.renderGlobal != null
                && mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == net.minecraft.util.math.RayTraceResult.Type.BLOCK
                && worldTicks >= 150 && worldTicks <= 200) {
            mc.renderGlobal.sendBlockBreakProgress(1, mc.objectMouseOver.getBlockPos(), 5);
        }
        if (worldTicks == 199 && Boolean.getBoolean("celtium.validation.breakBlock")) {
            report.addProperty("breakTarget", String.valueOf(
                    mc.objectMouseOver == null ? "no target" : mc.objectMouseOver.getBlockPos()));
        }
        if (worldTicks == 199 && Boolean.getBoolean("celtium.validation.weather")) {
            // Read at capture time, not when the rain was requested: the client's rain strength ramps in over ticks, so
            // the value recorded at tick 150 was still 0.000 and said nothing about whether rain was on screen.
            report.addProperty("weatherAtCapture", "raining=" + mc.world.getWorldInfo().isRaining()
                    + " rainStrength=" + String.format(java.util.Locale.ROOT, "%.3f", Float.valueOf(mc.world.getRainStrength(0.0f)))
                    + " rainingStrength=" + String.format(java.util.Locale.ROOT, "%.3f", Float.valueOf(mc.world.rainingStrength)));
        }
        if (worldTicks == 197 && !celtium$particlesSpawned && Boolean.getBoolean("celtium.validation.particles")) {
            celtium$particlesSpawned = true;
            // Particles with no motion, one tick before the capture. A still particle has prev == current, so the
            // partial-tick interpolation that made the hand and the player model unusable as comparison subjects gives
            // the same answer whichever frame is captured.
            for (int i = 0; i < 24; i++) {
                double px = -4.0 + (i % 6) * 1.6;
                // Raised into the open air just over the world's glass surface, at about eye level: at 6-8 the
                // particles sat inside solid blocks and at 12-13 they rose above the camera's field of view, so the
                // still-particle comparison measured nothing either time. At ~eye height they render mid-frame
                // against the terrain, where a wrong value is visible.
                double py = 9.6 + (i / 6) * 0.6;
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
                    report.addProperty("thirdPersonView", mc.gameSettings.thirdPersonView);
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
                    report.addProperty("heldItemEmpty", mc.player.getHeldItemMainhand().isEmpty());
                }
                // The equipped-progress fields are not public in 1.12.2, so the item-swap animation is left to the
                // phase the run happens to capture; the swing and arm angles are the ones that move every frame.
            }
        }
        // Captured on one exact tick rather than "the first frame at or after 200". The difference matters for the
        // GUI-visible comparison: animated textures advance per tick, so a run whose capture lands a tick later shows
        // different water, lava and fire frames, and that alone measured as a larger difference than any renderer
        // question this project was asking. One tick is one image.
        if (worldTicks < 200) return;
        if (celtium$dimensionAtTick > 0) {
            // Fail closed: a run that asked for another dimension and did not get there must not report PASSED. The
            // previous acceptance did exactly that - dimensionAfter stayed 0 while the report said PASSED - because
            // the value it read was taken before the scheduled transfer could run.
            TransferSnapshot transfer = celtium$transfer;
            report.addProperty("dimensionServerResult", transfer.state);
            report.addProperty("dimensionEvents", celtium$dimensionEvents.toString().trim());
            report.addProperty("dimensionServerTaskAtTick", Integer.valueOf(transfer.completedAtTick));
            report.addProperty("dimensionRotationBefore", transfer.rotationBefore);
            report.addProperty("dimensionArrivedAtTick", Integer.valueOf(celtium$arrivedAtTick));
            report.addProperty("dimensionArrivedFrames", Integer.valueOf(celtium$arrivedFrames));
            report.addProperty("dimensionClientAtCapture", Integer.valueOf(mc.player == null ? Integer.MIN_VALUE : mc.player.dimension));
            report.addProperty("dimensionWorldAtCapture",
                    Integer.valueOf(mc.world == null ? Integer.MIN_VALUE : mc.world.provider.getDimension()));
            if (celtium$arrivedAtTick < 0 || mc.world == null || mc.player == null
                    || mc.player.dimension != celtium$dimension
                    || mc.world.provider.getDimension() != celtium$dimension) {
                throw new IllegalStateException("dimension target " + celtium$dimension + " not reached by the client"
                        + " (server: " + transfer.state + ", arrivedAtTick=" + celtium$arrivedAtTick + ")");
            }
        }
        if (!celtium$captureStarted) {
            celtium$captureStarted = true;
            celtium$captureStartTick = worldTicks;
            report.addProperty("captureStartTick", Integer.valueOf(worldTicks));
        }
        try {
            if (commandBufferTargets && (!commandBufferTargetsAttempted || !commandBufferTargetsPassed)) {
                throw new IllegalStateException("Required commandBufferTargets fixture "
                        + (commandBufferTargetsAttempted ? "failed: " + report.get("commandBufferTargets") : "never exercised"));
            }
            if (!Boolean.getBoolean("celtium.validation.interop")) {
                // The frame that was just drawn may still contain an entity the client tick did not see. Drop it and
                // capture on the next frame, so the saved scene contains terrain and the sky only.
                // captureEntities keeps the live scene instead: for the entity comparison to measure the native
                // renderer's own output rather than the difference between "has particles" (the presented dump, taken
                // before this removal) and "has none" (the screenshot, taken after), the screenshot has to hold the
                // same live frame the presented image does. What is measured here is the renderer, so the removal that
                // only exists to make the terrain comparison deterministic is skipped on request.
                if (Boolean.getBoolean("celtium.validation.captureEntities")) {
                    report.addProperty("captureEntitiesKept", true);
                    // What the crosshair actually hits at capture time: the selection box only draws when this is a
                    // block, so recording it settles whether the outline was supposed to be in the frame at all.
                    if (mc.objectMouseOver != null) {
                        report.addProperty("selectionTarget", mc.objectMouseOver.typeOfHit
                                + (mc.objectMouseOver.typeOfHit == net.minecraft.util.math.RayTraceResult.Type.BLOCK
                                        ? " " + mc.objectMouseOver.getBlockPos() : ""));
                    } else {
                        report.addProperty("selectionTarget", "null");
                    }
                } else if (removeNonPlayerEntities(mc) > 0) return;
                report.addProperty("nonPlayerEntitiesRemoved", entitiesRemoved);
                report.addProperty("captureWidth", mc.displayWidth);
                report.addProperty("captureHeight", mc.displayHeight);
                report.addProperty("nonPlayerEntitiesAtCapture", countNonPlayerEntities(mc));
                report.addProperty("lightmapFlickerPinned", flickerPinned);
                report.addProperty("pinnedWorldTime", PINNED_WORLD_TIME);
                report.addProperty("worldTimeAtCapture", mc.world.getWorldTime());
                // The presented image's mean, taken at the capture so it belongs to the same frame as the screenshot. The
                // earlier comparison against the screenshot's mean was invalid because the two were sampled at different
                // moments; for the same reason it is not used as evidence unless the readback is on.
                try {
                    org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend current =
                            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
                    if (current instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) {
                        org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend vulkan =
                                (org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) current;
                        report.addProperty("presentedImageMeanAtCapture",
                                Double.valueOf(vulkan.celtium$presentedImageMean()));
                        report.addProperty("presentedImageFramesAtCapture",
                                Integer.valueOf(vulkan.celtium$presentedFrameCount()));
                        if (Boolean.getBoolean("celtium.validation.dumpPresentedImage")
                                && vulkan.celtium$presentedImageDumpWritten()) {
                            // The presented frame itself, so the image evidence does not have to come from OpenGL. It is
                            // written at the capture, next to the game's own screenshot, so the two are one frame apart at
                            // most and can be compared directly.
                            java.nio.file.Path presented = mc.gameDir.toPath()
                                    .resolve("screenshots/celtium-presented.png");
                            report.addProperty("presentedImageDumped",
                                    Boolean.valueOf(vulkan.celtium$presentedImageDumpWritten()));
                            report.addProperty("presentedImagePng", "screenshots/celtium-presented.png");
                        }
                    }
                } catch (RuntimeException | LinkageError ignored) {
                    // A measurement must never stop the capture.
                }
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
                // Was an absolute "worldTicks < 240", which can never be satisfied together with the capture's own
                // threshold of 200: the interop branch was unreachable. It is now relative to the tick the interop run
                // actually reported at.
                if (celtium$interopAtTick < 0) {
                    celtium$interopAtTick = worldTicks;
                }
                if (worldTicks < celtium$interopAtTick + 40) return;
                Framebuffer target = mc.getFramebuffer();
                if (worldFrames - framesAtInterop < 30 || GL11C.glGetError() != GL11C.GL_NO_ERROR
                        || target.framebufferObject != fboAtInterop || target.framebufferTexture != colorAtInterop
                        || target.depthBuffer != depthAtInterop)
                    throw new IllegalStateException("Normal world rendering did not resume with the original framebuffer after interop");
                report.addProperty("normalWorldFramesAfterInterop", worldFrames - framesAtInterop);
                ScreenShotHelper.saveScreenshot(mc.gameDir, "celtium-validation-post-interop.png", mc.displayWidth, mc.displayHeight, target);
            }
            String dumpDirectory = System.getProperty("celtium.vulkan.dumpTextures");
            if (dumpDirectory != null && !dumpDirectory.isEmpty()) {
                dumpLiveLightmap(mc, dumpDirectory);
                dumpLiveAtlas(mc, dumpDirectory);
            }
            if (worldFrames < 100 || !report.has("framebuffer")) {
                // The count is in the message on purpose: the threshold is the acceptance, and a run that misses it must say
                // by how much rather than leaving the next attempt to guess whether it was close or nowhere near.
                throw new IllegalStateException("Too few world rendering frames for startup validation: worldFrames="
                        + worldFrames + " (threshold 100), worldTicks=" + worldTicks);
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
            // The vanilla reference: the same fixture with no terrain mod installed still captures the same frame, which
            // is the ground truth the mod's own colours are judged against. Only the capture matters in that mode.
            if (Boolean.getBoolean("celtium.validation.vanillaReference")) {
                celtium$logFrameSample(mc);
                ScreenShotHelper.saveScreenshot(mc.gameDir, "celtium-validation.png", mc.displayWidth,
                        mc.displayHeight, mc.getFramebuffer());
                report.addProperty("vanillaReference", true);
                report.addProperty("status", "VANILLA_REFERENCE");
                finish(mc);
                return;
            }
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
                // Asked for at the capture itself rather than at a configured tick: the tick request is consumed at
                // the next present, and which frame that is depends on how far the render loop has run ahead of the
                // tick clock - runs kept landing it one tick before a staged effect, so the compared frame had none of
                // what the measurement was for. Asked here, the consumed present is the frame the loop produces right
                // after this tick, which is the closest frame to the one the screenshot just read.
                if (Boolean.getBoolean("celtium.validation.dumpPresentedAtCapture")) {
                    try {
                        org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend current =
                                org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
                        if (current instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) {
                            report.addProperty("presentedDumpRequestedAtCapture", Boolean.valueOf(
                                    ((org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) current)
                                            .celtium$dumpPresentedImage(mc.gameDir.toPath()
                                                    .resolve("screenshots/celtium-presented.png"))));
                        }
                    } catch (RuntimeException | LinkageError error) {
                        report.addProperty("presentedDumpRequestedAtCapture", error.toString());
                    }
                }
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

    /** Writes a partial report at exit when the capture never happened, instead of leaving no report at all. */
    private void celtium$installExitHook(final Minecraft mc) {
        if (celtium$exitHookInstalled) {
            return;
        }
        celtium$exitHookInstalled = true;
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                if (celtium$reportWritten) {
                    return;
                }
                ProgressSnapshot progress = celtium$progress;
                if (progress == null) {
                    return;
                }
                try {
                    report.addProperty("status", "NO_CAPTURE");
                    report.addProperty("worldTicksAtExit", Integer.valueOf(progress.ticks));
                    report.addProperty("worldFramesAtExit", Integer.valueOf(progress.frames));
                    report.addProperty("captureStarted", Boolean.valueOf(progress.captureStarted));
                    if (progress.captureStartTick > 0) {
                        report.addProperty("captureStartTick", Integer.valueOf(progress.captureStartTick));
                    }
                    if (progress.clientDimension != Integer.MIN_VALUE) {
                        report.addProperty("clientDimensionAtExit", Integer.valueOf(progress.clientDimension));
                    }
                    if (progress.worldDimension != Integer.MIN_VALUE) {
                        report.addProperty("clientWorldDimensionAtExit", Integer.valueOf(progress.worldDimension));
                    }
                    report.addProperty("screenAtExit", progress.screen);
                    TransferSnapshot transfer = celtium$transfer;
                    report.addProperty("dimensionServerResult", transfer.state);
                    java.nio.file.Path output = progress.outputPath;
                    java.nio.file.Files.createDirectories(output.getParent());
                    java.nio.file.Files.write(output, (new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n")
                            .getBytes(StandardCharsets.UTF_8));
                } catch (Throwable ignored) {
                    // Nothing here may keep the JVM from exiting.
                }
            }
        }, "celtium-partial-report"));
    }

    private void finish(Minecraft mc) {
        finished = true;
        celtium$reportWritten = true;
        // Test-only GL resources stay alive through their same-frame captures and are released before shutdown.
        if (commandBufferTargets) {
            try {
                if (commandBufferSecondary != null) {
                    commandBufferSecondary.deleteFramebuffer();
                    commandBufferSecondary = null;
                }
                if (commandBufferWhite != null) {
                    commandBufferWhite.deleteGlTexture();
                    commandBufferWhite = null;
                }
                mc.getFramebuffer().bindFramebuffer(true);
                report.addProperty("commandBufferTargetsResourcesReleased", true);
            } catch (Throwable error) {
                report.addProperty("status", "FAILED");
                report.addProperty("commandBufferTargetsCleanupError", error.toString());
                error.printStackTrace();
            }
        }
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
