package org.soutalab.celtium.bench;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A measurement fixture, deliberately separate from the validation fixture.
 * <p>
 * The validation fixture answers "did the renderer draw the right thing"; its timing window is a by-product (it samples
 * ticks 120-200 with a note saying it is not a performance benchmark). This fixture answers only "how long did each of
 * these frames take, under these conditions", and it writes its report even when it is killed:
 *
 * <ul>
 *   <li>a fixed number of measured frames, not a wall-clock window,</li>
 *   <li>a warm-up that must finish before measuring starts,</li>
 *   <li>raw per-frame times, so the percentiles are computed from samples rather than asserted,</li>
 *   <li>the effective frame cap, vsync flag and render distance at both ends of the window,</li>
 *   <li>process CPU jiffies and peak RSS on Linux,</li>
 *   <li>the load average at both ends of the window, because this machine is shared,</li>
 *   <li>the renderer's own chunk-queue counters and per-pass GPU timings when the backend publishes them.</li>
 * </ul>
 *
 * It changes no renderer setting that the two configurations do not share: the world, seed, layers, camera, time,
 * weather, resolution, render distance, frame cap and vsync flag are all set here, identically, for every run.
 * <p>
 * This class is compiled by {@code scripts/benchmark_celtium.py} against the instance's own classpath and shipped as a
 * separate mod jar. It never claims a speed-up; it produces the samples a speed-up would have to be argued from.
 */
@Mod(modid = "celtium_bench", name = "Celtium benchmark fixture", version = "0.1.0", clientSideOnly = true,
        acceptableRemoteVersions = "*")
public final class BenchFixture {

    private static final long PINNED_WORLD_TIME = 6000L;
    /** MCP name first, SRG name second; the same two fields the validation fixture pins, for the same reason. */
    private static final String[] FLICKER_X_NAMES = {"torchFlickerX", "field_78514_e"};
    private static final String[] FLICKER_DX_NAMES = {"torchFlickerDX", "field_175075_L"};
    /**
     * Two GameSettings flags whose runtime names the derived MCP->SRG mapping could not establish (their
     * descriptor group in the SRG jar has a different size, because Forge adds fields beside them). They are
     * resolved reflectively, MCP name first and the SRG name second, which is the same pattern this project
     * already uses for the lightmap flicker fields. The SRG names are read out of the deployed validation jar,
     * which the project's own reobf produced for exactly this runtime.
     */
    private static final String[] ENABLE_VSYNC_NAMES = {"enableVsync", "field_74352_v"};
    private static final String[] HIDE_GUI_NAMES = {"hideGUI", "field_74319_N"};
    private static final Pattern QUEUE_PATTERN = Pattern.compile(
            "Chunk Queues: U=(\\d+) \\(P0=(\\d+) \\| P1=(\\d+) \\| P2=(\\d+)\\)");

    private static final String DEFAULT_LAYERS =
            "3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass,minecraft:water,minecraft:lava,minecraft:fire,"
                    + "minecraft:leaves,minecraft:stained_glass,minecraft:chest,minecraft:enchanting_table;1;";

    private enum Phase { LAUNCH, SETTLE, STEADY, CHUNK, DONE }

    private final boolean enabled = Boolean.getBoolean("celtium.bench");
    private final int warmupTicks = Integer.getInteger("celtium.bench.warmupTicks", 240).intValue();
    private final int warmupFrames = Integer.getInteger("celtium.bench.warmupFrames", 300).intValue();
    private final int targetFrames = Integer.getInteger("celtium.bench.frames", 600).intValue();
    private final int frameLimit = Integer.getInteger("celtium.bench.framelimit", 260).intValue();
    private final boolean vsync = Boolean.getBoolean("celtium.bench.vsync");
    private final int renderDistance = Integer.getInteger("celtium.bench.renderDistance", 8).intValue();
    private final long seed = Long.getLong("celtium.bench.seed", 12345678L).longValue();
    private final String worldLayers = System.getProperty("celtium.bench.worldLayers", DEFAULT_LAYERS);
    private final boolean chunkPhase = !"false".equalsIgnoreCase(System.getProperty("celtium.bench.chunkPhase", "true"));
    private final int chunkBlocks = Integer.getInteger("celtium.bench.chunkBlocks", 64).intValue();
    private final int chunkPerTick = Integer.getInteger("celtium.bench.chunkPerTick", 2).intValue();
    private final int chunkEveryTicks = Integer.getInteger("celtium.bench.chunkEveryTicks", 2).intValue();
    private final int chunkDrainMaxFrames = Integer.getInteger("celtium.bench.chunkDrainMaxFrames", 900).intValue();
    private final int chunkDrainMaxSeconds = Integer.getInteger("celtium.bench.chunkDrainMaxSeconds", 90).intValue();
    /**
     * How many frames after the last placement every configuration is measured for, whether or not its chunk
     * queue empties earlier. It is a frame count and not a queue condition so that the reference and the fork
     * produce the same number of post-placement samples; the queue-empty frame is recorded beside it when the
     * renderer publishes queue counters at all.
     */
    private final int chunkDrainWindowFrames = Integer.getInteger("celtium.bench.chunkDrainWindowFrames", 120).intValue();
    private final int timeoutSeconds = Integer.getInteger("celtium.bench.timeoutSeconds", 420).intValue();
    private final String label = System.getProperty("celtium.bench.label", "");
    /**
     * The fixed camera pose, applied on the client every tick and set on the server once through the game's own
     * teleport. The defaults are the pose the validation fixture's terrain scene uses, so the scene a benchmark run
     * measures is one the project has already described. Vanilla fuzzes the player's spawn position by a few blocks
     * (``nextFloat() * 2 - 1`` per axis), which is why leaving the camera to the spawn made two runs of the same jar
     * differ in where they stood; that is the camera drift this pins.
     */
    private final double poseX = Double.parseDouble(System.getProperty("celtium.bench.cameraX", "0.5"));
    private final double poseY = Double.parseDouble(System.getProperty("celtium.bench.cameraY", "12.0"));
    private final double poseZ = Double.parseDouble(System.getProperty("celtium.bench.cameraZ", "0.5"));
    private final float poseYaw = Float.parseFloat(System.getProperty("celtium.bench.cameraYaw", "180.0"));
    private final float posePitch = Float.parseFloat(System.getProperty("celtium.bench.cameraPitch", "20.0"));
    private final int poseTick = Integer.getInteger("celtium.bench.cameraSetTick", 25).intValue();

    private Phase phase = Phase.LAUNCH;
    private boolean finished;
    private volatile boolean reportWritten;
    private int worldTicks;
    private int worldFrames;
    private int settleFrames;
    private int measuredFrames;
    /** Set when the first eligible steady-state frame opens the window; the window must open exactly once. */
    private boolean windowOpen;
    private long previousFrameNanos;
    private long lastFrameNanos;
    private long windowStartNanos;
    private long windowEndNanos;
    private long startedNanos;
    private long partialDeadlineNanos;
    private String failure;

    private final List<Double> frameMs = new ArrayList<Double>();
    private final List<Double> chunkFrameMs = new ArrayList<Double>();

    private long cpuJiffiesAtStart = -1L;
    private long cpuJiffiesAtEnd = -1L;
    private long rssKbAtStart = -1L;
    private long rssKbAtEnd = -1L;
    private long rssPeakKbAtEnd = -1L;
    private long minorFaultsAtStart = -1L;
    private long minorFaultsAtEnd = -1L;
    private String loadAtStart = "unknown";
    private String loadAtEnd = "unknown";

    private int queueAtStart = -1;
    private int queueAtEnd = -1;
    private int queueMax = -1;
    private List<String> debugAtStart = new ArrayList<String>();
    private List<String> debugAtEnd = new ArrayList<String>();
    private List<String> debugLastSeen = new ArrayList<String>();

    private double cameraX = Double.NaN;
    private double cameraY = Double.NaN;
    private double cameraZ = Double.NaN;
    private float cameraYaw = Float.NaN;
    private float cameraPitch = Float.NaN;
    private long worldTimeAtStart = -1L;
    private long worldTimeAtEnd = -1L;
    private long worldSeed = -1L;
    private boolean flickerPinned;
    private boolean flickerResolved;
    private boolean poseRequested;
    private int poseAppliedTicks;
    private volatile String poseResult = "not attempted";
    private Field flickerX;
    private Field flickerDX;
    private Field vsyncField;
    private Field hideGuiField;
    private String vsyncApplied = "not attempted";
    private String capabilitiesProbe = "not attempted";

    private int chunkPlaced;
    private int chunkPlaceFailures;
    private long chunkPlaceNanos;
    private long chunkLastPlaceNanos;
    private boolean chunkPlacedAll;
    private int chunkPhaseFrames;
    private int chunkFramesAfterPlacement;
    private int chunkDrainFrames;
    private long chunkPhaseStartNanos;
    private long chunkDrainStartNanos;
    private int chunkQueueAtDrainStart = -1;
    private boolean chunkDrained;
    /** Frame index (after the last placement) at which the renderer's queue counters first read zero, or -1. */
    private int chunkQueueEmptyFrame = -1;
    private boolean chunkQueueCountersReadable;
    private int chunkQueueAtPhaseEnd = -1;
    private String chunkDrainCriterion = "not reached";
    private int limitFramerateAtStart = -1;
    private int limitFramerateAtEnd = -1;
    private boolean enableVsyncAtStart;
    private boolean enableVsyncAtEnd;
    private int renderDistanceAtStart = -1;
    private int renderDistanceAtEnd = -1;
    private int displayWidthAtWindow = -1;
    private int displayHeightAtWindow = -1;
    private boolean guiHiddenAtWindow;
    private String glRendererString;
    private String glVersionString;
    private volatile boolean fromExitHook;

    private static Method debugStringsMethod;
    private static Object rendererInstance;
    private static boolean debugStringsUnavailable;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (enabled) {
            MinecraftForge.EVENT_BUS.register(this);
            startedNanos = System.nanoTime();
            System.out.println("[CeltiumBench] armed label=\"" + label + "\" frames=" + targetFrames
                    + " warmupTicks=" + warmupTicks + " warmupFrames=" + warmupFrames
                    + " framelimit=" + frameLimit + " vsync=" + vsync + " renderDistance=" + renderDistance
                    + " chunkPhase=" + chunkPhase);
        }
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (!enabled || finished || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        try {
            if (phase == Phase.LAUNCH) {
                if (!(mc.currentScreen instanceof net.minecraft.client.gui.GuiMainMenu)) {
                    return;
                }
                applySettingsAndLaunch(mc);
                phase = Phase.SETTLE;
                return;
            }
            if (System.nanoTime() > partialDeadlineNanos) {
                recordPartial("timeout after " + timeoutSeconds + " s in phase " + phase);
                return;
            }
            if (mc.world == null || mc.player == null) {
                return;
            }
            worldTicks++;
            // Identical scene for every configuration: the same sun angle, the same lightmap, no weather.
            pinScene(mc);
            if (worldTicks == 2) {
                probeCapabilities(mc);
            }
            if (phase == Phase.CHUNK) {
                placeChunkBlocks(mc);
            }
        } catch (Throwable error) {
            fail(mc, error);
        }
    }

    @SubscribeEvent
    public void world(RenderWorldLastEvent event) {
        if (!enabled || finished) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        long now = System.nanoTime();
        lastFrameNanos = now;
        try {
            if (mc.world == null || mc.player == null || mc.currentScreen != null) {
                previousFrameNanos = now;
                return;
            }
            worldFrames++;
            if (phase == Phase.SETTLE) {
                if (worldTicks >= warmupTicks && worldFrames >= warmupFrames) {
                    phase = Phase.STEADY;
                }
                previousFrameNanos = now;
                return;
            }
            if (phase == Phase.STEADY) {
                // The first eligible frame only opens the window: its interval spans the last warm-up frame, so it is
                // not one of the measured samples. `windowOpen` and not `measuredFrames == 0` is the latch: the
                // measured-frame counter is still zero at this point, so testing it would re-open the window every
                // frame and never sample anything.
                if (!windowOpen) {
                    windowOpen = true;
                    beginWindow(mc, now);
                    previousFrameNanos = now;
                    return;
                }
                if (previousFrameNanos != 0L) {
                    frameMs.add(Double.valueOf((now - previousFrameNanos) / 1000000.0));
                }
                measuredFrames++;
                if (measuredFrames % 10 == 0) {
                    sampleQueues(mc);
                }
                if (measuredFrames >= targetFrames) {
                    endWindow(mc, now);
                }
                previousFrameNanos = now;
                return;
            }
            if (phase == Phase.CHUNK) {
                if (previousFrameNanos != 0L) {
                    chunkFrameMs.add(Double.valueOf((now - previousFrameNanos) / 1000000.0));
                }
                chunkPhaseFrames++;
                int queue = sampleQueues(mc);
                if (queue >= 0) {
                    chunkQueueCountersReadable = true;
                }
                if (chunkPlacedAll) {
                    chunkFramesAfterPlacement++;
                }
                if (chunkDrainFrames == 0 && chunkPlacedAll) {
                    chunkDrainFrames = chunkFramesAfterPlacement;
                    chunkQueueAtDrainStart = queue;
                    chunkDrainStartNanos = now;
                }
                if (chunkPlacedAll && queue == 0 && chunkFramesAfterPlacement >= 5 && chunkQueueEmptyFrame < 0) {
                    chunkQueueEmptyFrame = chunkFramesAfterPlacement;
                }
                boolean windowElapsed = chunkFramesAfterPlacement >= chunkDrainWindowFrames;
                long chunkElapsed = chunkPhaseStartNanos == 0L ? 0L : now - chunkPhaseStartNanos;
                if (chunkPlacedAll && windowElapsed) {
                    // The window is a fixed number of post-placement frames: the same sample count for every
                    // configuration, whether its queue drains at once or never. Whether the queue reached zero,
                    // and at which frame, is reported beside the samples instead of gating them, because a scene
                    // whose blocks keep updating (lava, fire) has a queue that never rests and that is a fact
                    // about the scene, not a failure of the measurement.
                    chunkDrained = true;
                    chunkQueueAtPhaseEnd = queue;
                    chunkDrainCriterion = chunkQueueCountersReadable
                            ? ("fixed " + chunkDrainWindowFrames + "-frame window; queue "
                               + (chunkQueueEmptyFrame > 0
                                  ? "empty at frame " + chunkQueueEmptyFrame
                                  : "still " + queue + " at frame " + chunkFramesAfterPlacement))
                            : ("no queue counters published, fixed " + chunkDrainWindowFrames + "-frame window");
                    finish("chunk phase complete: " + chunkFramesAfterPlacement + " frames after the last placement ("
                            + chunkDrainCriterion + ")", false);
                } else if (chunkFramesAfterPlacement >= chunkDrainMaxFrames
                        || chunkElapsed > chunkDrainMaxSeconds * 1000000000L) {
                    recordPartial("chunk phase exceeded " + chunkDrainMaxFrames + " frames / "
                            + chunkDrainMaxSeconds + " s without reaching the "
                            + chunkDrainWindowFrames + "-frame post-placement window (placed=" + chunkPlaced
                            + ", queue=" + queue + ")");
                }
                previousFrameNanos = now;
                return;
            }
            previousFrameNanos = now;
        } catch (Throwable error) {
            fail(mc, error);
        }
    }

    // ------------------------------------------------------------------ scene setup

    private void applySettingsAndLaunch(Minecraft mc) {
        mc.gameSettings.renderDistanceChunks = renderDistance;
        // "Unlimited-ish" is what the validation fixture uses and it is the highest value the option accepts; the
        // achieved frame rate is reported, so whether this cap (or the 60 Hz compositor) bound the run is visible
        // rather than assumed.
        mc.gameSettings.limitFramerate = frameLimit;
        vsyncField = findField(mc.gameSettings.getClass(), ENABLE_VSYNC_NAMES);
        hideGuiField = findField(mc.gameSettings.getClass(), HIDE_GUI_NAMES);
        applyVsync(mc, vsync);
        limitFramerateAtStart = mc.gameSettings.limitFramerate;
        enableVsyncAtStart = readBoolean(mc.gameSettings, vsyncField, false);
        renderDistanceAtStart = mc.gameSettings.renderDistanceChunks;
        WorldSettings settings = new WorldSettings(seed, GameType.CREATIVE, false, false, WorldType.FLAT);
        settings.setGeneratorOptions(worldLayers);
        settings.enableCommands();
        partialDeadlineNanos = System.nanoTime() + timeoutSeconds * 1000000000L;
        installExitHook();
        System.out.println("[CeltiumBench] launching world seed=" + seed + " layers=\"" + worldLayers + "\"");
        mc.launchIntegratedServer("celtium-bench", "Celtium benchmark", settings);
    }

    /**
     * Setting the field is what the options screen does; the display call is what actually changes the swap interval.
     * Both are attempted and the outcome is recorded, because "vsync was off" is a control this measurement is not
     * allowed to assume.
     */
    private void applyVsync(Minecraft mc, boolean wanted) {
        String field = "unresolved";
        if (vsyncField != null) {
            try {
                vsyncField.setBoolean(mc.gameSettings, wanted);
                field = vsyncField.getName();
            } catch (Throwable error) {
                field = "write failed: " + error.getClass().getSimpleName();
            }
        }
        try {
            for (Method method : mc.getClass().getMethods()) {
                if (method.getParameterTypes().length == 0
                        && method.getName().toLowerCase(Locale.ROOT).contains("vsync")) {
                    method.invoke(mc);
                    vsyncApplied = "GameSettings." + field + " + Minecraft." + method.getName() + "()";
                    return;
                }
            }
        } catch (Throwable ignored) {
            // Fall through to the field-only answer; the achieved frame rate is the evidence either way.
        }
        vsyncApplied = "GameSettings." + field + " only (no no-arg vsync method on Minecraft)";
    }

    private static boolean readBoolean(Object target, Field field, boolean fallback) {
        if (field == null) {
            return fallback;
        }
        try {
            return field.getBoolean(target);
        } catch (Throwable absent) {
            return fallback;
        }
    }

    private void pinScene(Minecraft mc) {
        mc.world.setWorldTime(PINNED_WORLD_TIME);
        holdPose(mc);
        if (!flickerResolved) {
            flickerResolved = true;
            flickerX = findField(mc.entityRenderer.getClass(), FLICKER_X_NAMES);
            flickerDX = findField(mc.entityRenderer.getClass(), FLICKER_DX_NAMES);
        }
        if (flickerX != null && flickerDX != null) {
            try {
                flickerX.setFloat(mc.entityRenderer, 0.0F);
                flickerDX.setFloat(mc.entityRenderer, 0.0F);
                flickerPinned = true;
            } catch (Throwable ignored) {
                flickerPinned = false;
            }
        }
        // No weather in either configuration: rain changes the frame cost and would arrive at a different tick.
        if (mc.world.getWorldInfo().isRaining()) {
            mc.world.getWorldInfo().setRaining(false);
        }
        if (mc.world.getWorldInfo().isThundering()) {
            mc.world.getWorldInfo().setThundering(false);
        }
        if (mc.world.getWorldInfo().getRainTime() < 100000) {
            mc.world.getWorldInfo().setRainTime(1000000);
        }
        if (mc.world.getWorldInfo().getThunderTime() < 100000) {
            mc.world.getWorldInfo().setThunderTime(1000000);
        }
    }

    /**
     * Runs the renderer's own capability command, outside the measured window.
     * <p>
     * The benchmark's device evidence must be the renderer's, not a restatement of it: the same query the
     * {@code /celtium probe} command runs writes {@code logs/celtium-capabilities.json}, which lists the Vulkan devices
     * the loader enumerated. The pinned upstream reference has no such class, and that absence is recorded as a fact
     * rather than treated as a failure.
     */
    private void probeCapabilities(Minecraft mc) {
        try {
            Class<?> entry = Class.forName("org.soutalab.celtium.probe.CeltiumProbe");
            String json = (String) entry.getMethod("inspectCurrentContextJson").invoke(null);
            Path output = mc.gameDir.toPath().resolve("logs/celtium-capabilities.json");
            Files.createDirectories(output.getParent());
            Files.write(output, (json + "\n").getBytes(StandardCharsets.UTF_8));
            capabilitiesProbe = "written";
        } catch (Throwable error) {
            capabilitiesProbe = "unavailable: " + error.getClass().getSimpleName();
        }
        System.out.println("[CeltiumBench] capability probe: " + capabilitiesProbe);
    }

    /**
     * Holds the client's player at the fixed pose, and once - at {@code celtium.bench.cameraSetTick} - asks the
     * server to teleport its own player there too. Both halves are needed: the client-side hold alone leaves the
     * server's "moved too quickly" correction free to put the camera back where it was, which is exactly the drift
     * that made two runs of one jar stand in different places.
     */
    private void holdPose(Minecraft mc) {
        try {
            mc.player.capabilities.allowFlying = true;
            mc.player.capabilities.isFlying = true;
            mc.player.motionX = 0.0D;
            mc.player.motionY = 0.0D;
            mc.player.motionZ = 0.0D;
            mc.player.fallDistance = 0.0F;
            mc.player.setPositionAndRotation(poseX, poseY, poseZ, poseYaw, posePitch);
            mc.player.rotationYawHead = poseYaw;
            mc.player.prevRotationYawHead = poseYaw;
            mc.player.renderYawOffset = poseYaw;
            mc.player.prevRenderYawOffset = poseYaw;
            poseAppliedTicks++;
        } catch (Throwable error) {
            poseResult = "client pose failed: " + error;
        }
        if (!poseRequested && worldTicks >= poseTick) {
            poseRequested = true;
            poseResult = teleportPoseOnServer(mc);
        }
    }

    private String teleportPoseOnServer(Minecraft mc) {
        final net.minecraft.server.integrated.IntegratedServer server = mc.getIntegratedServer();
        if (server == null || mc.player == null) {
            return "no integrated server";
        }
        final int tick = worldTicks;
        try {
            server.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    try {
                        // The singleplayer player list, not a UUID lookup: `EntityPlayerSP.getUniqueID()` is
                        // inherited from Entity, and the derived MCP->SRG mapping does not rewrite the call site
                        // (javac emits the static receiver type), so it survives as an unmapped name and throws
                        // NoSuchMethodError at runtime. Every member used here is one the mapping rewrote.
                        java.util.List<net.minecraft.entity.player.EntityPlayerMP> players =
                                server.getServer().getPlayerList().getPlayers();
                        if (players.isEmpty()) {
                            poseResult = "server player list is empty";
                            return;
                        }
                        net.minecraft.entity.player.EntityPlayerMP player = players.get(0);
                        player.capabilities.allowFlying = true;
                        player.capabilities.isFlying = true;
                        player.sendPlayerAbilities();
                        player.rotationYaw = poseYaw;
                        player.rotationPitch = posePitch;
                        player.prevRotationYaw = poseYaw;
                        player.prevRotationPitch = posePitch;
                        // setPositionAndUpdate is EntityPlayerMP's own teleport: it sends the position-look packet
                        // the client applies, so both sides end up at the pose with no correction in flight.
                        player.setPositionAndUpdate(poseX, poseY, poseZ);
                        player.motionX = 0.0D;
                        player.motionY = 0.0D;
                        player.motionZ = 0.0D;
                        player.fallDistance = 0.0F;
                        poseResult = "server-side teleport at tick " + tick + " to "
                                + poseX + "," + poseY + "," + poseZ + " yaw=" + poseYaw + " pitch=" + posePitch;
                    } catch (Throwable error) {
                        poseResult = "server-side teleport failed: " + error;
                    }
                }
            });
        } catch (Throwable error) {
            return "could not schedule the server-side teleport: " + error;
        }
        return "scheduled at tick " + tick;
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

    // ------------------------------------------------------------------ measurement

    private void beginWindow(Minecraft mc, long now) {
        windowStartNanos = now;
        cpuJiffiesAtStart = cpuJiffies();
        minorFaultsAtStart = minorFaults();
        rssKbAtStart = rssKb("VmRSS");
        loadAtStart = loadAverage();
        cameraX = mc.player.posX;
        cameraY = mc.player.posY;
        cameraZ = mc.player.posZ;
        cameraYaw = mc.player.rotationYaw;
        cameraPitch = mc.player.rotationPitch;
        worldTimeAtStart = mc.world.getWorldTime();
        worldSeed = worldSeed(mc);
        // Captured here, on the client thread with a live GL context: the exit hook must never call GL, and a
        // shutdown-hook GL call aborts the JVM ("No context is current") instead of reporting anything.
        displayWidthAtWindow = mc.displayWidth;
        displayHeightAtWindow = mc.displayHeight;
        guiHiddenAtWindow = readBoolean(mc.gameSettings, hideGuiField, false);
        glRendererString = glString(org.lwjgl.opengl.GL11.GL_RENDERER);
        glVersionString = glString(org.lwjgl.opengl.GL11.GL_VERSION);
        debugAtStart = debugStrings();
        queueAtStart = queueDepth(debugAtStart);
        System.out.println("[CeltiumBench] window open ticks=" + worldTicks + " frames=" + worldFrames
                + " camera=" + cameraX + "," + cameraY + "," + cameraZ + " load=" + loadAtStart
                + " queue=" + queueAtStart);
    }

    private void endWindow(Minecraft mc, long now) {
        windowEndNanos = now;
        cpuJiffiesAtEnd = cpuJiffies();
        minorFaultsAtEnd = minorFaults();
        rssKbAtEnd = rssKb("VmRSS");
        rssPeakKbAtEnd = rssKb("VmHWM");
        loadAtEnd = loadAverage();
        worldTimeAtEnd = mc.world.getWorldTime();
        debugAtEnd = debugStrings();
        queueAtEnd = queueDepth(debugAtEnd);
        limitFramerateAtEnd = mc.gameSettings.limitFramerate;
        enableVsyncAtEnd = readBoolean(mc.gameSettings, vsyncField, false);
        renderDistanceAtEnd = mc.gameSettings.renderDistanceChunks;
        System.out.println("[CeltiumBench] window close ticks=" + worldTicks + " frames=" + worldFrames
                + " samples=" + frameMs.size() + " queue=" + queueAtEnd + " queueMax=" + queueMax
                + " load=" + loadAtEnd);
        if (!chunkPhase) {
            recordPartial("steady state measured; chunk phase disabled");
            return;
        }
        phase = Phase.CHUNK;
        chunkPhaseStartNanos = System.nanoTime();
        chunkDrainFrames = 0;
        System.out.println("[CeltiumBench] chunk phase: " + chunkBlocks + " blocks, " + chunkPerTick + "/tick");
    }

    /** Places a fixed block pattern, so both configurations rebuild the same chunks in the same order. */
    private void placeChunkBlocks(Minecraft mc) {
        if (chunkPlacedAll || chunkPlaced >= chunkBlocks) {
            return;
        }
        if (worldTicks % Math.max(1, chunkEveryTicks) != 0) {
            return;
        }
        for (int n = 0; n < chunkPerTick && chunkPlaced < chunkBlocks; n++, chunkPlaced++) {
            int gx = chunkPlaced % 16;
            int gz = chunkPlaced / 16;
            BlockPos pos = new BlockPos(gx - 8, 4, gz - 8);
            try {
                long before = System.nanoTime();
                // Flag 3 = tell the client (this one) so the chunk is rebuilt, without neighbour updates: the same
                // placement the validation fixture uses for its block sweep.
                mc.world.setBlockState(pos, Blocks.STONE.getDefaultState(), 3);
                chunkPlaceNanos += System.nanoTime() - before;
                chunkLastPlaceNanos = System.nanoTime();
            } catch (Throwable error) {
                chunkPlaceFailures++;
            }
        }
        if (chunkPlaced >= chunkBlocks) {
            chunkPlacedAll = true;
            System.out.println("[CeltiumBench] chunk placement done placed=" + chunkPlaced
                    + " failures=" + chunkPlaceFailures + " placeMillis=" + (chunkPlaceNanos / 1000000L)
                    + " tick=" + worldTicks + " frames=" + worldFrames);
        }
    }

    private int sampleQueues(Minecraft mc) {
        List<String> strings = debugStrings();
        int queue = queueDepth(strings);
        if (queue >= 0) {
            debugLastSeen = strings;
            if (queue > queueMax) {
                queueMax = queue;
            }
        }
        return queue;
    }

    private static int queueDepth(List<String> strings) {
        if (strings == null) {
            return -1;
        }
        for (String line : strings) {
            Matcher matcher = QUEUE_PATTERN.matcher(line);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1)) + Integer.parseInt(matcher.group(2))
                        + Integer.parseInt(matcher.group(3)) + Integer.parseInt(matcher.group(4));
            }
        }
        return -1;
    }

    /**
     * The renderer's own debug strings, read reflectively so this fixture links against no renderer class: the same
     * source runs against the fork and against the pinned upstream reference, which is the point of the comparison.
     */
    private static List<String> debugStrings() {
        if (debugStringsUnavailable) {
            return null;
        }
        try {
            if (debugStringsMethod == null) {
                Class<?> rendererClass = Class.forName("org.taumc.celeritas.impl.render.terrain.CeleritasWorldRenderer");
                Object renderer = rendererClass.getMethod("instanceNullable").invoke(null);
                if (renderer == null) {
                    return null;
                }
                rendererInstance = renderer;
                debugStringsMethod = rendererClass.getMethod("getDebugStrings");
            }
            Object value = debugStringsMethod.invoke(rendererInstance);
            if (!(value instanceof Collection)) {
                return null;
            }
            List<String> out = new ArrayList<String>();
            for (Object entry : (Collection<?>) value) {
                out.add(String.valueOf(entry));
            }
            return out;
        } catch (Throwable absent) {
            debugStringsUnavailable = true;
            return null;
        }
    }

    private static long worldSeed(Minecraft mc) {
        try {
            return mc.getIntegratedServer().worlds[0].getWorldInfo().getSeed();
        } catch (Throwable absent) {
            return -1L;
        }
    }

    // ------------------------------------------------------------------ proc metrics

    private static long cpuJiffies() {
        try {
            String stat = new String(Files.readAllBytes(java.nio.file.Paths.get("/proc/self/stat")), StandardCharsets.UTF_8);
            int close = stat.lastIndexOf(')');
            if (close < 0) {
                return -1L;
            }
            String[] fields = stat.substring(close + 2).trim().split("\\s+");
            // After the comm field the first token is field 3 (state), so utime (14) and stime (15) are 11 and 12.
            return Long.parseLong(fields[11]) + Long.parseLong(fields[12]);
        } catch (Throwable absent) {
            return -1L;
        }
    }

    private static long minorFaults() {
        try {
            String stat = new String(Files.readAllBytes(java.nio.file.Paths.get("/proc/self/stat")), StandardCharsets.UTF_8);
            int close = stat.lastIndexOf(')');
            if (close < 0) {
                return -1L;
            }
            String[] fields = stat.substring(close + 2).trim().split("\\s+");
            return Long.parseLong(fields[7]);
        } catch (Throwable absent) {
            return -1L;
        }
    }

    private static long rssKb(String key) {
        try {
            for (String line : new String(Files.readAllBytes(java.nio.file.Paths.get("/proc/self/status")),
                    StandardCharsets.UTF_8).split("\n")) {
                if (line.startsWith(key + ":")) {
                    String value = line.substring(key.length() + 1).trim();
                    return Long.parseLong(value.split("\\s+")[0]);
                }
            }
        } catch (Throwable absent) {
            // Reported as -1.
        }
        return -1L;
    }

    private static String loadAverage() {
        try {
            String text = new String(Files.readAllBytes(java.nio.file.Paths.get("/proc/loadavg")), StandardCharsets.UTF_8).trim();
            String[] parts = text.split("\\s+");
            return parts[0] + " " + parts[1] + " " + parts[2];
        } catch (Throwable absent) {
            return "unknown";
        }
    }

    // ------------------------------------------------------------------ reporting

    private void fail(Minecraft mc, Throwable error) {
        failure = error.getClass().getName() + ": " + String.valueOf(error.getMessage());
        error.printStackTrace();
        recordPartial("fixture error");
    }

    private void recordPartial(String reason) {
        finish(reason, true);
    }

    /**
     * Ends the run and writes the report. {@code partial=false} marks the fixture's own normal end (the
     * steady-state window plus a completed chunk phase); {@code partial=true} marks a run that ended early and
     * whose samples therefore cover less than the plan.
     */
    private void finish(String reason, boolean partial) {
        if (finished) {
            return;
        }
        finished = true;
        phase = Phase.DONE;
        writeReport(reason, partial);
        try {
            Minecraft.getMinecraft().shutdown();
        } catch (Throwable ignored) {
            // The report is already on disk.
        }
    }

    private void installExitHook() {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (!reportWritten) {
                    fromExitHook = true;
                    writeReport("process exit before the window closed", true);
                }
            }, "CeltiumBench-report"));
        } catch (Throwable ignored) {
            // A missing hook only costs the partial report of a killed run.
        }
    }

    private synchronized void writeReport(String reason, boolean partial) {
        if (reportWritten) {
            return;
        }
        reportWritten = true;
        Minecraft mc = Minecraft.getMinecraft();
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        field(json, "schemaVersion", 1, true);
        field(json, "label", label, true);
        field(json, "status", failure != null ? "FIXTURE_ERROR" : (partial ? "PARTIAL" : "COMPLETE"), true);
        field(json, "reason", reason, true);
        field(json, "error", failure, true);
        field(json, "measuredFrames", measuredFrames, true);
        field(json, "warmupTicks", warmupTicks, true);
        field(json, "warmupFrames", warmupFrames, true);
        field(json, "worldTicks", worldTicks, true);
        field(json, "worldFrames", worldFrames, true);
        field(json, "windowSeconds", windowStartNanos == 0L || windowEndNanos == 0L ? -1.0
                : (windowEndNanos - windowStartNanos) / 1e9, true);
        field(json, "cpuJiffiesStart", cpuJiffiesAtStart, true);
        field(json, "cpuJiffiesEnd", cpuJiffiesAtEnd, true);
        field(json, "minorFaultsStart", minorFaultsAtStart, true);
        field(json, "minorFaultsEnd", minorFaultsAtEnd, true);
        field(json, "rssKbStart", rssKbAtStart, true);
        field(json, "rssKbEnd", rssKbAtEnd, true);
        field(json, "rssPeakKbEnd", rssPeakKbAtEnd, true);
        field(json, "loadAverageStart", loadAtStart, true);
        field(json, "loadAverageEnd", loadAtEnd, true);
        field(json, "queueAtWindowStart", queueAtStart, true);
        field(json, "queueAtWindowEnd", queueAtEnd, true);
        field(json, "queueMaxDuringWindow", queueMax, true);
        field(json, "framelimitSetting", frameLimit, true);
        field(json, "framelimitAtStart", limitFramerateAtStart, true);
        field(json, "framelimitAtEnd", limitFramerateAtEnd, true);
        field(json, "vsyncRequested", vsync, true);
        field(json, "vsyncAtStart", enableVsyncAtStart, true);
        field(json, "vsyncAtEnd", enableVsyncAtEnd, true);
        field(json, "vsyncAppliedHow", vsyncApplied, true);
        field(json, "renderDistanceAtStart", renderDistanceAtStart, true);
        field(json, "renderDistanceAtEnd", renderDistanceAtEnd, true);
        field(json, "displayWidth", displayWidthAtWindow, true);
        field(json, "displayHeight", displayHeightAtWindow, true);
        field(json, "guiHidden", Boolean.valueOf(guiHiddenAtWindow), true);
        field(json, "vsyncFieldResolvedAs", vsyncField == null ? null : vsyncField.getName(), true);
        field(json, "hideGuiFieldResolvedAs", hideGuiField == null ? null : hideGuiField.getName(), true);
        field(json, "seedRequested", seed, true);
        field(json, "worldSeed", worldSeed, true);
        field(json, "worldLayers", worldLayers, true);
        field(json, "pinnedWorldTime", PINNED_WORLD_TIME, true);
        field(json, "worldTimeAtStart", worldTimeAtStart, true);
        field(json, "worldTimeAtEnd", worldTimeAtEnd, true);
        field(json, "lightmapFlickerPinned", flickerPinned, true);
        field(json, "cameraPoseX", poseX, true);
        field(json, "cameraPoseY", poseY, true);
        field(json, "cameraPoseZ", poseZ, true);
        field(json, "cameraPoseYaw", poseYaw, true);
        field(json, "cameraPosePitch", posePitch, true);
        field(json, "cameraSetTick", poseTick, true);
        field(json, "cameraPoseAppliedTicks", poseAppliedTicks, true);
        field(json, "cameraPoseResult", poseResult, true);
        field(json, "cameraX", cameraX, true);
        field(json, "cameraY", cameraY, true);
        field(json, "cameraZ", cameraZ, true);
        field(json, "cameraYaw", cameraYaw, true);
        field(json, "cameraPitch", cameraPitch, true);
        field(json, "chunkPhaseEnabled", chunkPhase, true);
        field(json, "chunkBlocksRequested", chunkBlocks, true);
        field(json, "chunkBlocksPlaced", chunkPlaced, true);
        field(json, "chunkPlaceFailures", chunkPlaceFailures, true);
        field(json, "chunkPlaceMillis", chunkPlaceNanos / 1000000L, true);
        field(json, "chunkPhaseFrames", chunkPhaseFrames, true);
        field(json, "chunkFramesAfterPlacement", chunkFramesAfterPlacement, true);
        field(json, "chunkFramesFromFirstDrainSample", chunkDrainFrames, true);
        field(json, "chunkDrained", chunkDrained, true);
        field(json, "chunkQueueCountersReadable", chunkQueueCountersReadable, true);
        field(json, "chunkQueueEmptyAtFrame", chunkQueueEmptyFrame, true);
        field(json, "chunkQueueAtPhaseEnd", chunkQueueAtPhaseEnd, true);
        field(json, "chunkDrainWindowFrames", chunkDrainWindowFrames, true);
        field(json, "chunkDrainCriterion", chunkDrainCriterion, true);
        field(json, "chunkQueueAtDrainStart", chunkQueueAtDrainStart, true);
        field(json, "chunkDrainMaxFrames", chunkDrainMaxFrames, true);
        field(json, "chunkDrainMaxSeconds", chunkDrainMaxSeconds, true);
        field(json, "chunkPhaseSeconds", chunkPhaseStartNanos == 0L || windowEndNanos == 0L ? -1.0
                : (lastFrameNanos - chunkPhaseStartNanos) / 1e9, true);
        field(json, "glRendererString", glRendererString, true);
        field(json, "glVersionString", glVersionString, true);
        field(json, "reportWrittenFromExitHook", Boolean.valueOf(fromExitHook), true);
        field(json, "capabilitiesProbe", capabilitiesProbe, true);
        numbers(json, "frameTimesMs", frameMs, true);
        numbers(json, "chunkDrainFrameTimesMs", chunkFrameMs, true);
        strings(json, "debugStringsAtWindowStart", debugAtStart, true);
        strings(json, "debugStringsAtWindowEnd", debugAtEnd, true);
        strings(json, "debugStringsLastChunkSample", debugLastSeen, false);
        json.append("}\n");
        try {
            Path output = mc.gameDir.toPath().resolve(
                    System.getProperty("celtium.bench.output", "logs/celtium-benchmark.json"));
            Files.createDirectories(output.getParent());
            Files.write(output, json.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("[CeltiumBench] report written: " + output + " status="
                    + (failure != null ? "FIXTURE_ERROR" : (partial ? "PARTIAL" : "COMPLETE")) + " reason=" + reason);
        } catch (IOException error) {
            System.out.println("[CeltiumBench] report write failed: " + error);
        }
    }

    private static String glString(int name) {
        try {
            return org.lwjgl.opengl.GL11.glGetString(name);
        } catch (Throwable absent) {
            return null;
        }
    }

    private static void field(StringBuilder json, String name, Object value, boolean comma) {
        json.append("  \"").append(escape(name)).append("\": ");
        if (value == null) {
            json.append("null");
        } else if (value instanceof Number) {
            double asDouble = ((Number) value).doubleValue();
            if (Double.isNaN(asDouble) || Double.isInfinite(asDouble)) {
                json.append("null");
            } else if (value instanceof Integer || value instanceof Long) {
                json.append(value.toString());
            } else {
                json.append(String.format(Locale.ROOT, "%.6f", asDouble));
            }
        } else if (value instanceof Boolean) {
            json.append(value.toString());
        } else {
            json.append('"').append(escape(String.valueOf(value))).append('"');
        }
        json.append(comma ? ",\n" : "\n");
    }

    private static void numbers(StringBuilder json, String name, List<Double> values, boolean comma) {
        json.append("  \"").append(escape(name)).append("\": [");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                json.append(", ");
            }
            json.append(String.format(Locale.ROOT, "%.6f", values.get(i).doubleValue()));
        }
        json.append(comma ? "],\n" : "]\n");
    }

    private static void strings(StringBuilder json, String name, List<String> values, boolean comma) {
        json.append("  \"").append(escape(name)).append("\": [");
        List<String> safe = values == null ? new ArrayList<String>() : values;
        for (int i = 0; i < safe.size(); i++) {
            if (i > 0) {
                json.append(", ");
            }
            json.append('"').append(escape(safe.get(i))).append('"');
        }
        json.append(comma ? "],\n" : "]\n");
    }

    private static String escape(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }
}
