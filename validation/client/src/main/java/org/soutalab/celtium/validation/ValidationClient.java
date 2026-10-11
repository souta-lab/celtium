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

    /**
     * The colour-count smoke test. An image with at most this many distinct RGB values is a clear colour plus a quad or
     * two rather than a frame of the world. It is kept - it caught the flat-yellow and single-colour frames - but it is
     * only the smoke test: the audit's counter-example was a frame with 11751 colours and two thirds of it black, and
     * the scene gates below are what judge a picture now.
     */
    private static final int PRESENTED_DISTINCT_COLOUR_FLOOR = 16;

    /**
     * How far a channel may differ from the image's most common colour and still count as background. The same
     * 8-step tolerance the pixel comparisons elsewhere in this project use.
     */
    private static final int PRESENTED_BACKGROUND_TOLERANCE = 8;

    /** A pixel this dark in every channel counts as black: the audit's "large black region" measured this way. */
    private static final int PRESENTED_BLACK_CHANNEL_MAX = 8;

    /** The scene this run drives; {@code -Dceltium.validation.scene=<name>}. */
    private static final String SCENE_PROPERTY = "celtium.validation.scene";

    /** The identity sidecar a picture carries: the same path plus this suffix. */
    private static final String IDENTITY_SUFFIX = ".identity.json";

    /**
     * The two provenances a picture in this fixture can have.
     * <p>
     * A GL-less run has no GL default framebuffer - the game's own screenshot is black whatever the window shows - so
     * its only true picture is the image the swapchain received, written by the present that consumes the fixture's
     * request. A GL-bridge run presents nothing through Vulkan (the GL swap shows the frame), so its picture is the
     * game's own screenshot, written synchronously inside the capture. Both are a picture of the window; which one a
     * run has is recorded in the identity rather than assumed.
     */
    private static final String SOURCE_PRESENTED_DUMP = "presented-dump";
    private static final String SOURCE_GL_SCREENSHOT = "gl-screenshot";

    /** A dump request's binding: the write was observed, and the presented-frame counter named the frame that wrote it. */
    private static final String BINDING_OBSERVED_PRESENT =
            "observed present: the renderer's presented-frame counter advanced by exactly one from the request";
    /** A screenshot's binding: it is written by the capture handler itself, on this call stack. */
    private static final String BINDING_WRITE_AT_CAPTURE = "written synchronously inside the capture handler";

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

    // ---------------------------------------------------------------------------------------------------------------
    // Scenes: one fixed camera pose, one picture gate, and - for the video settings scene - a GUI stage.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * The camera a scene is captured from, in the game's own terms.
     * <p>
     * The pose is the fixture's, not the run's: the player used to be left wherever the tick-20 teleport, the server's
     * position corrections and the fluid drift put them, so two runs of the same scene captured different pictures
     * (measured: x=512.5 z=-12.5 against z=-8.5 at the same tick). A scene's picture gate can only have measured floors
     * if the pose is the same in the reference run and the run under test, so the pose is set here.
     */
    private static final class CameraPose {
        final double x;
        final double y;
        final double z;
        final float yaw;
        final float pitch;

        CameraPose(double x, double y, double z, float yaw, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        String describe() {
            return String.format(java.util.Locale.ROOT, "x=%.4f y=%.4f z=%.4f yaw=%.3f pitch=%.3f", x, y, z, yaw, pitch);
        }
    }

    /**
     * A scene: what is captured, from where, and what the picture has to show.
     * <p>
     * The floors are not copied from a run of the same kind as the run under test. They come from a real GL-bridge
     * reference run of the same scene at the same pose, and each scene records that run and its numbers beside its own
     * floor. {@code -Dceltium.validation.gate.<name>} overrides one for an experiment; the report always carries the
     * value the gate used and where it came from.
     */
    private enum Scene {
        /**
         * The standard terrain scene: the flat world at the fixture's pose, HUD hidden at tick 190, captured at tick
         * 200. Reference: GL bridge at 1280x720 and camera x=0.5 y=12.0 z=0.5 yaw=180 pitch=20, picture =
         * {@code screenshots/celtium-validation.png} written by the capture's own {@code ScreenShotHelper} call
         * (the GL bridge presents nothing through Vulkan, so the screenshot is that configuration's picture of the
         * window). Measured there: distinctColours=1535/1536, nonBackgroundFraction=0.732164/0.727735,
         * verticalBands=631/638, blackFraction=0.000000/0.000000 (see {@link #celtium$terrainGate} for the runs and
         * the floors derived from them).
         */
        TERRAIN("terrain", false, true),
        /**
         * Minecraft's own video settings screen: {@code GuiOptions} is opened through {@code displayGuiScreen} - the
         * call the pause menu makes - and its Video Settings button is dispatched through the screen's own
         * {@code mouseClicked}, which is what a real click runs. The screen has to be a {@code GuiVideoSettings} at the
         * capture, and the picture has to show a panel where the screen says its list is. Its world frame is measured
         * like the terrain scene's (1536/0.727735/638/0.000000 in both of the scene's own runs) and its panel floors
         * come from the same runs' GUI frames against their world frames (guiPanelChangedFraction=0.477975/0.478083,
         * guiPanelStrongFraction=0.455733/0.455836, guiPanelDistinctColours=15805/15877,
         * guiPanelRowAmplitude=9.344/9.441, against the world frame's 1509/1516 colours and 9.509/9.789 rows at the
         * same rectangle); see {@link #celtium$videoSettingsGate}.
         */
        VIDEO_SETTINGS("videoSettings", true, true),
        /**
         * A frame of the Nether, which is not an overworld frame: netherrack, fire, lava and no sky, with the
         * background colour of a cave rather than of daylight. It is its own scene because a floor measured on the
         * overworld does not describe it - a GL-less Nether capture measured nonBackgroundFraction=0.32598 and
         * blackFraction=0.036944 against the overworld reference's 0.728299 and 0.000000, and judging it by the
         * overworld floor failed a frame whose picture was fine (measured: .celtium-work/runtime/vuid-after2) - and it
         * has its own pose for the same reason: the overworld pose (y=12) is inside the Nether's rock. Its own measured
         * floors are in {@link #celtium$netherGate} (0.938502 non-background, 720 bands, 0.087951 black at x=1.5 y=70
         * z=1.5). Until that block carried a measured reference at that pose the gate reported
         * {@code floorsNotCalibrated} and the verdict {@code UNCALIBRATED_PRESENTED_IMAGE} instead of judging the frame
         * by another scene's numbers.
         */
        NETHER("nether", false, true),
        /**
         * A frame of the End (dimension 1). The fixture can drive it, and it is neither the overworld nor the Nether:
         * its sky is the End's, its ground is the main island and its light is its own. No GL-bridge reference run
         * exists, so it is gated as uncalibrated rather than by the overworld's floors.
         */
        END("end", false, false),
        /**
         * Any other dimension the fixture is asked to travel to (dimension 2 and up). Recorded by name and dimension
         * and gated as uncalibrated for the same reason as {@link #END}.
         */
        OTHER_DIMENSION("otherDimension", false, false);

        final String name;
        /** Whether this scene drives a GUI screen and takes a second capture with it open. */
        final boolean guiStage;
        /** Whether a measured reference exists at this pose: an uncalibrated scene records its floors as missing. */
        final boolean calibrated;

        Scene(String name, boolean guiStage, boolean calibrated) {
            this.name = name;
            this.guiStage = guiStage;
            this.calibrated = calibrated;
        }
    }

    /**
     * The numbers a scene's picture gate uses, resolved once from the scene's measured defaults and the overrides.
     * <p>
     * {@code reference} is prose on purpose: a floor without the run it came from cannot be re-derived, and the audit
     * that started this asked for exactly that provenance. It is split into three properties so a reader of a report
     * can tell the runs from the numbers from the margin: {@code referenceRuns} names the runs, {@code referenceNumbers}
     * is what they measured, and {@code margin} is the rule that turned those numbers into the floors.
     */
    private static final class SceneGate {
        final int minDistinctColours;
        final double minNonBackgroundFraction;
        final int minVerticalBands;
        final double maxBlackFraction;
        final double minPanelChangedFraction;
        final double minPanelStrongFraction;
        final int minPanelDistinctColours;
        final double minPanelRowAmplitude;
        final String reference;
        final String referenceRuns;
        final String referenceNumbers;
        final String margin;
        /** False when this scene has no measured reference yet: its floors are reported as missing, not enforced. */
        final boolean calibrated;

        SceneGate(int minDistinctColours, double minNonBackgroundFraction, int minVerticalBands, double maxBlackFraction,
                double minPanelChangedFraction, double minPanelStrongFraction, int minPanelDistinctColours,
                double minPanelRowAmplitude, String reference, String referenceRuns, String referenceNumbers,
                String margin, boolean calibrated) {
            this.calibrated = calibrated;
            this.minDistinctColours = minDistinctColours;
            this.minNonBackgroundFraction = minNonBackgroundFraction;
            this.minVerticalBands = minVerticalBands;
            this.maxBlackFraction = maxBlackFraction;
            this.minPanelChangedFraction = minPanelChangedFraction;
            this.minPanelStrongFraction = minPanelStrongFraction;
            this.minPanelDistinctColours = minPanelDistinctColours;
            this.minPanelRowAmplitude = minPanelRowAmplitude;
            this.reference = reference;
            this.referenceRuns = referenceRuns;
            this.referenceNumbers = referenceNumbers;
            this.margin = margin;
        }
    }

    /**
     * One picture the scene's acceptance is about, with the identity of the frame it came from.
     * <p>
     * The file path is unique per request. The previous shape used one fixed path for every request - the early
     * diagnostic dump, the capture's dump and the backend's every-fortieth-frame refresh all wrote
     * {@code screenshots/celtium-presented.png} - so the file the verdict read was not necessarily the frame the
     * counters described. That is measurable in the reports already on disk: {@code outline2} judged a frame with
     * 11751 colours and 596 bands, and the file left at that path is a different frame with 11628 colours and 491
     * bands. The identity below is what makes the verdict about a named frame instead of about "the file at that path".
     */
    private static final class PictureEvidence {
        final String stage;
        final String source;
        /**
         * Where the renderer was asked to write. The renderer re-requests a dump to the <em>last</em> path the fixture
         * asked for every fortieth presented frame ({@code VulkanTerrainGpuBackend}: {@code presentedDumpPath != null &&
         * presented % 40 == 0}), so a requested path is rewritten by later frames until the fixture asks for another
         * one. Measured in {@code .celtium-work/runtime/unfreeze}: the early dump's identity was completed at
         * 09:39:45.787 with imageHash=-457084848, the file at that path was rewritten at 09:39:52.443, and it then held
         * the world frame's content (imageHash=1564670080, byte-identical to the world dump) - two files that look like
         * one frozen frame, and an identity that no longer describes its file. The kept copy below is what the identity
         * names instead.
         */
        final java.nio.file.Path requestedImage;
        /** The picture the identity names: the requested file, or its kept copy when one could be taken. */
        java.nio.file.Path image;
        java.nio.file.Path identity;
        final int requestedAtTick;
        final int worldFramesAtRequest;
        final int presentedFramesAtRequest;
        final int requestedWidth;
        final int requestedHeight;
        /**
         * How this picture is tied to the frame the capture is about. A presented dump is bound by observing which
         * present wrote it; a screenshot written synchronously inside the capture handler is bound by construction.
         * They are different claims and the verdict checks the one that applies.
         */
        final String frameBinding;
        /** Filled in when the write is observed: the renderer's own counter for the frame that was written. */
        boolean completed;
        int presentedFrame = -1;
        int worldTick = -1;
        int worldFrames = -1;
        int width = -1;
        int height = -1;
        int imageHash;
        int completedAtTick = -1;
        String completionError = "none";
        /** The requested path this identity's file is a kept copy of, or "none" when the file is that path itself. */
        String keptFrom = "none";

        PictureEvidence(String stage, String source, java.nio.file.Path image, int requestedAtTick,
                int worldFramesAtRequest, int presentedFramesAtRequest, int requestedWidth, int requestedHeight,
                String frameBinding) {
            this.frameBinding = frameBinding;
            this.stage = stage;
            this.source = source;
            this.image = image;
            this.requestedImage = image;
            this.identity = image.resolveSibling(image.getFileName().toString() + IDENTITY_SUFFIX);
            this.requestedAtTick = requestedAtTick;
            this.worldFramesAtRequest = worldFramesAtRequest;
            this.presentedFramesAtRequest = presentedFramesAtRequest;
            this.requestedWidth = requestedWidth;
            this.requestedHeight = requestedHeight;
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
     * The dimension trace, bounded.
     * <p>
     * One run has to be able to say what happened on its own: what the fixture did and on which tick, what the client
     * thread saw for the client and for its world, what the server had for that player, which screen was open, and
     * whether the client's world object was replaced (the client-visible effect of the server's Respawn packet). The
     * lines go to stdout and into the report, and the budget is finite so a run that is stuck says so instead of
     * filling the log.
     */
    private static final int DIM_TRACE_MAX_LINES = 140;
    private int celtium$dimTraceLines;
    private final StringBuilder celtium$dimensionTrace = new StringBuilder();
    /** The trace as one immutable string, published for the exit hook, which must not read the game or a live builder. */
    private volatile String celtium$dimensionTraceText = "";
    /** The previous client dimension, so a change is reported as a change rather than only as a value. */
    private int celtium$lastClientDimension = Integer.MIN_VALUE;
    /** World passes drawn while the client was really in the target dimension and no screen was over them. */
    private int celtium$framesInTargetDimension;
    /** The renderer's own presented-frame counter when the arrival was first observed. */
    private int celtium$presentedFramesAtArrival = -1;
    /** Set once the client is seen in a dimension other than the target after it had arrived there. */
    private int celtium$leftTargetAtTick = -1;
    private String celtium$leftTargetEvent = "none";
    /** Where the fixture's own teleporter put the player, and what it had to do about the arrival portal. */
    private volatile String celtium$arrivalPlacement = "not attempted";
    private int celtium$portalCooldownAfterTransfer = -1;
    private int celtium$portalReentries;
    private int celtium$portalEscapes;
    /** A verified portal-free spot beside the arrival, used if the player is pushed back into a portal. */
    private double celtium$safeX;
    private double celtium$safeY;
    private double celtium$safeZ;
    private boolean celtium$safeSpotKnown;
    /** Ticks spent waiting for the target dimension to have a real frame before the capture is taken. */
    private int celtium$dimensionWaitTicks;
    /**
     * The tick at which a capture was taken and only its presented-image verdict is left.
     * <p>
     * The presented image the assertion reads is written by the present that consumes the dump the capture asks for,
     * and the capture's own render handler runs before that present. Judging the file in the capture's frame therefore
     * judges the frame from before the request: on the Nether run that was the loading frame from tick 20, 9.5 seconds
     * old, while the requested Nether frame was on disk a frame later. The verdict is taken on the next frame instead,
     * which is when the requested frame is the one on disk.
     */
    private int celtium$presentedVerdictPendingAtTick = -1;
    /** The world-frame count when the verdict was deferred, so the watchdog can tell a stuck loop from a slow picture. */
    private int celtium$presentedVerdictPendingFrames = -1;
    /** The world-frame count at the capture, so the presented-frame comparison names the window it is about. */
    private int celtium$captureWorldFrames = -1;
    /** The first world frame at which the renderer's presented-frame counter was above zero, or -1. */
    private int celtium$firstPresentedWorldFrame = -1;
    /**
     * The highest value the renderer's presented-frame counter has been seen at.
     * <p>
     * The counter belongs to the live frame target, and a dimension change recreates that target: the counter then
     * starts again from zero. A high-water mark taken every world frame is what turns "the counter is lower than the
     * run's frame count" into a measured reset instead of an assumption, and it is what the whole-run accounting
     * ({@code worldFrames} against high-water plus the counter at the capture) is checked with.
     */
    private int celtium$presentedFramesHighWater;
    private int celtium$presentedHighWaterBeforeArrival = -1;
    /**
     * The acceptance floor for a dimension run: world frames the target dimension itself drew, with no screen over
     * them, before the capture means anything. A capture taken while the download-terrain screen is up is a loading
     * frame, not the new dimension; the default is low enough to be a floor rather than a budget.
     */
    private final int celtium$minDimensionFrames = Integer.getInteger("celtium.validation.minDimensionFrames", 10).intValue();
    /**
     * How long the capture may wait for those frames. The transfer is asynchronous - the server sends the dimension
     * change, then the client downloads the new world's chunks - so the exact frame at which the scene exists is not
     * known in advance. The wait is for the acceptance's own preconditions and is bounded; a client that has left the
     * target dimension again is not waited for at all.
     */
    private final int celtium$dimensionWaitUntilTick = Integer.getInteger("celtium.validation.dimensionWaitUntilTick", 240).intValue();

    // ---------------------------------------------------------------------------------------------------------------
    // Scene, camera pose and picture identity state.
    // ---------------------------------------------------------------------------------------------------------------

    /** Identifies this run's files: a game directory is reused between runs, so a stale file is a real hazard. */
    private final String celtium$runId = System.getProperty("celtium.validation.runId",
            "run-" + Long.toHexString(System.nanoTime()) + "-" + Long.toHexString(System.identityHashCode(Thread.currentThread())));
    /** The scene this run drives; resolved once, and an unknown name is a failed run rather than a silent default. */
    private final Scene celtium$scene = celtium$resolveScene();
    private final SceneGate celtium$gate = celtium$resolveGate(celtium$scene);
    /**
     * The fixed capture pose. Position and both rotations, applied on the client every tick and set on the server once
     * through the game's own teleport, so the server's "moved too quickly" correction cannot put the camera back where
     * it was (which is what left the camera wherever the run happened to drift).
     */
    private final CameraPose celtium$pose = celtium$resolvePose(celtium$scene);
    /** Resolved once so a bad value fails the run with its text rather than throwing on a render tick. */
    private final String celtium$sceneError = celtium$sceneResolutionError;
    /** The tick the server teleport is scheduled at: after the world exists, early enough to be settled by the capture. */
    private final int celtium$poseTick = Integer.getInteger("celtium.validation.cameraSetTick", 25).intValue();
    /** Set when the renderer's view entity was not the player and had to be moved to the pose as well. */
    private String celtium$poseViewEntity = "none";
    private int celtium$poseViewEntityTicks;
    private boolean celtium$poseRequested;
    private int celtium$poseAppliedAtTick = -1;
    private int celtium$poseAppliedTicks;
    /** Written from the server thread by the scheduled teleport, read by the render thread: one volatile string. */
    private volatile String celtium$poseResult = "not attempted";
    /**
     * Capture stages. 0 = the world frame, 1 = the video settings screen is being opened and laid out, 2 = its capture
     * has been taken and only the verdict is left. The world stage is the whole acceptance for a scene with no GUI.
     */
    private int celtium$stage;
    /** Whether the picture verdict has been taken; it is taken once, on the frame whose evidence is complete. */
    private boolean celtium$pictureVerdictTaken;
    /** Whether the scene's measured floors were enforced on this run's picture: a reference exists and this run is its variant. */
    private boolean celtium$gateEnforced;
    /** Whether the world stage's picture has been taken, so the capture body does not run twice for one frame. */
    private boolean celtium$worldStageTaken;
    /** Every completed picture of this run, by stage. */
    private final java.util.Map<String, PictureEvidence> celtium$evidence =
            new java.util.LinkedHashMap<String, PictureEvidence>();
    /** Requests whose write has not been observed yet. */
    private final java.util.List<PictureEvidence> celtium$pendingDumps = new java.util.ArrayList<PictureEvidence>();
    /** The GUI screen opened for the scene's second stage, and when and how it was opened. */
    private String celtium$guiScreenOpened = "not attempted";
    private String celtium$guiScreenOpenedVia = "none";
    private int celtium$guiScreenOpenedAtTick = -1;
    private int celtium$guiScreenOpenedAtFrames = -1;
    private boolean celtium$guiClickPending;
    /** Set by the world stage for a GUI scene: the screen is opened on the next client tick, not inside a render pass. */
    private boolean celtium$guiOpenPending;
    private String celtium$guiClickResult = "not attempted";
    /** When the open screen was first seen to be a GuiVideoSettings, so its layout wait is measured from there. */
    private int celtium$guiSettingsAtTick = -1;
    private int celtium$guiSettingsAtFrames = -1;
    /** Ticks to let the screen lay out between it being open and its capture. */
    private final int celtium$guiLayoutTicks = Integer.getInteger("celtium.validation.guiLayoutTicks", 8).intValue();
    /** Frames that must have been drawn with the screen open before its picture means anything. */
    private final int celtium$guiMinFrames = Integer.getInteger("celtium.validation.guiMinFrames", 3).intValue();
    /** Bounded wait for the screen to become a GuiVideoSettings after the click. */
    private final int celtium$guiWaitTicks = Integer.getInteger("celtium.validation.guiWaitTicks", 20).intValue();

    /** Set by {@link #celtium$resolveScene()} when the configured scene name is not one this fixture drives. */
    private static String celtium$sceneResolutionError = "none";

    /**
     * The scene this run drives: the name from {@code -Dceltium.validation.scene}, and the dimension when the run
     * travels to one.
     * <p>
     * A dimension run's frames are not the scene its flags started from - the Nether has no sky and its own blocks,
     * light and background - so a run that transfers to another dimension is that dimension's scene even when it named
     * the overworld one, and the report says so.
     */
    private static Scene celtium$resolveScene() {
        String wanted = System.getProperty(SCENE_PROPERTY, "").trim();
        Scene named = null;
        if (!wanted.isEmpty()) {
            for (Scene scene : Scene.values()) {
                if (scene.name.equalsIgnoreCase(wanted)) {
                    named = scene;
                    break;
                }
            }
        }
        Scene byDimension = celtium$dimensionScene();
        if (named == null && wanted.isEmpty()) {
            return byDimension;
        }
        if (named == Scene.TERRAIN && byDimension != Scene.TERRAIN) {
            // The run travels to another dimension, so its frames belong to that scene.
            return byDimension;
        }
        if (named != null) {
            return named;
        }
        StringBuilder names = new StringBuilder();
        for (Scene scene : Scene.values()) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(scene.name);
        }
        celtium$sceneResolutionError = "unknown scene '" + wanted + "' (known scenes: " + names + ")";
        return Scene.TERRAIN;
    }

    /** The scene a dimension transfer lands in, or the terrain scene when the run stays where it starts. */
    private static Scene celtium$dimensionScene() {
        int dimension = Integer.getInteger("celtium.validation.dimension", 0).intValue();
        int atTick = Integer.getInteger("celtium.validation.dimensionAtTick", 0).intValue();
        if (atTick <= 0 || dimension == 0) {
            return Scene.TERRAIN;
        }
        if (dimension == -1) {
            return Scene.NETHER;
        }
        if (dimension == 1) {
            return Scene.END;
        }
        // Any other dimension is its own scene name with no reference of its own: judging it by the overworld's floors
        // would be exactly the mistake the Nether frame exposed, so it is gated as uncalibrated.
        return Scene.OTHER_DIMENSION;
    }

    /**
     * The pose a scene's floors were measured at: the fixture's own default for that scene, before overrides.
     * <p>
     * A pose is part of a scene, not a detail of it. The overworld pose (x=0.5 y=12 z=0.5) is clear air above the flat
     * stack - the stack's top block is the enchanting table at y=10, 0.75 high - but y=12 in the Nether is inside the
     * dimension's rock: a camera there sees whichever sections the renderer happened to have meshed, and two runs at
     * that same pose measured nonBackgroundFraction 0.183982 (background 0x120303, the netherrack around the camera)
     * and 0.420806 (background 0x000027, the void where nothing was drawn). That is not a scene two runs can be
     * compared in, so the Nether has its own pose: the arrival spot the fixture's own teleporter verifies as air with
     * ground under it (dimensionArrivalPlacement "arrival=0,70,1 ... placed=1,70,1 placedBlock=minecraft:air
     * ground=true safe=true", the same string in every run), where the frame is open Nether rather than the inside of
     * a block.
     */
    private static CameraPose celtium$referencePose(Scene scene) {
        if (scene == Scene.NETHER) {
            return new CameraPose(1.5D, 70.0D, 1.5D, 180.0F, 20.0F);
        }
        return new CameraPose(0.5D, 12.0D, 0.5D, 180.0F, 20.0F);
    }

    /**
     * The camera pose for a scene: the scene's default, overridden field by field by
     * {@code -Dceltium.validation.cameraX} and friends.
     */
    private static CameraPose celtium$resolvePose(Scene scene) {
        CameraPose base = celtium$referencePose(scene);
        double x = Double.parseDouble(System.getProperty("celtium.validation.cameraX", Double.toString(base.x)));
        double y = Double.parseDouble(System.getProperty("celtium.validation.cameraY", Double.toString(base.y)));
        double z = Double.parseDouble(System.getProperty("celtium.validation.cameraZ", Double.toString(base.z)));
        float yaw = Float.parseFloat(System.getProperty("celtium.validation.cameraYaw", Float.toString(base.yaw)));
        float pitch = Float.parseFloat(System.getProperty("celtium.validation.cameraPitch", Float.toString(base.pitch)));
        return new CameraPose(x, y, z, yaw, pitch);
    }

    /**
     * The picture gate for a scene.
     * <p>
     * Every floor here is a measured number from a real GL-bridge reference run of the same scene at the same fixed
     * pose, with margin; the runs, their numbers and the margin rule are named in the per-scene gate factories below
     * and repeated in the {@code sceneGateReference*} properties the report carries, so a floor can be re-derived
     * rather than believed.
     * <p>
     * Whether the floors are <em>enforced</em> is a separate question, decided at the verdict by the calibration key
     * ({@link #celtium$calibrationKey}): a floor measured on one variant of a scene does not describe another variant,
     * so a run at another pose, GUI scale or world stack reports {@code floorsNotCalibrated} and the verdict
     * {@code UNCALIBRATED_PRESENTED_IMAGE} rather than being judged - or failed - by a floor that belongs to a picture
     * it is not. A scene with no measured reference at all has no floors to enforce for the same reason.
     * <p>
     * The overrides exist so an experiment can move a floor; the report always says which value was used and where it
     * came from.
     */
    private static SceneGate celtium$resolveGate(Scene scene) {
        SceneGate measured = celtium$measuredGate(scene);
        int minDistinctColours = Integer.getInteger("celtium.validation.gate.minDistinctColours",
                PRESENTED_DISTINCT_COLOUR_FLOOR).intValue();
        double minNonBackground = Double.parseDouble(System.getProperty(
                "celtium.validation.gate.minNonBackgroundFraction",
                Double.toString(measured.minNonBackgroundFraction)));
        int minBands = Integer.getInteger("celtium.validation.gate.minVerticalBands",
                measured.minVerticalBands).intValue();
        double maxBlack = Double.parseDouble(System.getProperty("celtium.validation.gate.maxBlackFraction",
                Double.toString(measured.maxBlackFraction)));
        double minPanelChanged = Double.parseDouble(System.getProperty(
                "celtium.validation.gate.minPanelChangedFraction",
                Double.toString(measured.minPanelChangedFraction)));
        double minPanelStrong = Double.parseDouble(System.getProperty(
                "celtium.validation.gate.minPanelStrongFraction",
                Double.toString(measured.minPanelStrongFraction)));
        int minPanelColours = Integer.getInteger("celtium.validation.gate.minPanelDistinctColours",
                measured.minPanelDistinctColours).intValue();
        double minPanelRowAmplitude = Double.parseDouble(System.getProperty(
                "celtium.validation.gate.minPanelRowAmplitude",
                Double.toString(measured.minPanelRowAmplitude)));
        return new SceneGate(minDistinctColours, minNonBackground, minBands, maxBlack, minPanelChanged, minPanelStrong,
                minPanelColours, minPanelRowAmplitude, measured.reference, measured.referenceRuns,
                measured.referenceNumbers, measured.margin, measured.calibrated);
    }

    /** The measured gate for a scene: the scene's own reference runs, their numbers, and the floors derived from them. */
    private static SceneGate celtium$measuredGate(Scene scene) {
        switch (scene) {
            case NETHER:
                return celtium$netherGate();
            case VIDEO_SETTINGS:
                return celtium$videoSettingsGate();
            case END:
            case OTHER_DIMENSION:
                return celtium$uncalibratedGate(scene);
            case TERRAIN:
            default:
                return celtium$terrainGate();
        }
    }

    /**
     * The terrain scene's floors, measured on two GL-bridge reference runs at the fixture pose.
     * <p>
     * References (GL bridge, fresh game directory, {@code scene=terrain}, 1280x720, camera x=0.5 y=12.0 z=0.5
     * yaw=180 pitch=20, picture = the capture's own {@code screenshots/celtium-validation.png}, because the GL bridge
     * presents nothing through Vulkan):
     * <ul>
     * <li>{@code scene-ref-terrain} (run dir {@code .celtium-work/runtime/scene-ref-terrain}, 2026-10-11 01:15):
     * distinctColours=1535, nonBackgroundFraction=0.732164, verticalBands=631, blackFraction=0.000000,
     * backgroundColour=0x571716.</li>
     * <li>{@code scene-floors/terrain-a} (2026-10-11 09:11, the same scene at the same pose, for the run-to-run
     * spread): distinctColours=1536, nonBackgroundFraction=0.727735, verticalBands=638, blackFraction=0.000000,
     * backgroundColour=0x571716. The renderer's own viewport at that capture read 0.50 13.60 0.45 against the pose's
     * eye 0.5 13.62 0.5.</li>
     * </ul>
     * The floors are the smaller reference with a stated margin, in the direction that keeps them meaningful:
     * {@code nonBackgroundFraction >= 0.40} is 55% of the smaller measured 0.727735 (the two runs differ by 0.004429,
     * so the floor is 74x the measured spread); {@code verticalBands >= 250} is 39% of the smaller measured 638
     * (spread 7 rows); {@code blackFraction <= 0.05} against a measured 0.000000, which still catches the frames the
     * audit wants caught (outline2 0.6715 black at 11751 colours, outline5 0.321, tesr1 0.519 at 46 colours - all
     * three of which passed the old "more than 16 colours" test).
     */
    private static SceneGate celtium$terrainGate() {
        String runs = "gl-bridge scene-ref-terrain (2026-10-11 01:15, .celtium-work/runtime/scene-ref-terrain) and"
                + " gl-bridge scene-floors/terrain-a (.celtium-work/runtime/scene-floors/terrain-a), both scene=terrain"
                + " at x=0.5 y=12.0 z=0.5 yaw=180 pitch=20, 1280x720, picture=screenshots/celtium-validation.png"
                + " (the GL bridge's own screenshot)";
        String numbers = "scene-ref-terrain: distinctColours=1535 nonBackgroundFraction=0.732164 verticalBands=631"
                + " blackFraction=0.000000 backgroundColour=0x571716; scene-floors/terrain-a:"
                + " distinctColours=1536 nonBackgroundFraction=0.727735 verticalBands=638 blackFraction=0.000000"
                + " (spread: 1 colour, 0.004429 non-background, 7 bands, 0.000000 black)";
        String margin = "nonBackgroundFraction floor 0.40 = 55% of the smaller reference 0.727735 (74x the measured"
                + " spread of 0.004429); verticalBands floor 250 = 39% of the smaller reference 638 (spread 7 rows);"
                + " blackFraction ceiling 0.05 = measured 0.000000 + 0.05, which still catches the audit's"
                + " large-black-region frames (0.32-0.67 black)";
        String reference = "gl-bridge " + runs + ": " + numbers + " -> floors distinctColours>16,"
                + " nonBackgroundFraction>=0.40, verticalBands>=250, blackFraction<=0.05; margin: " + margin;
        return new SceneGate(PRESENTED_DISTINCT_COLOUR_FLOOR, 0.40D, 250, 0.05D, 0.0D, 0.0D, 0, 0.0D, reference, runs,
                numbers, margin, true);
    }

    /**
     * The video settings scene's floors: the terrain world floors on its world frame, and the same run's GUI frame
     * against that world frame for the panel.
     * <p>
     * References (GL bridge, fresh game directory, {@code scene=videoSettings}, same pose and extent as the terrain
     * scene; the world frame is {@code screenshots/celtium-validation-world.png} and the GUI frame is
     * {@code screenshots/celtium-validation.png}, both written synchronously by the capture that draws them; the screen
     * is {@code net.minecraft.client.gui.GuiVideoSettings}, title "Video Settings", reached through
     * {@code GuiOptions.mouseClicked} on button 101):
     * <ul>
     * <li>{@code scene-floors/video-a} (2026-10-11 09:11) and {@code scene-floors/video-b} (09:12), two runs of the
     * same scene at the same pose, for the spread. World frame: distinctColours=1536/1536,
     * nonBackgroundFraction=0.727735/0.727735, verticalBands=638/638, blackFraction=0.000000/0.000000. GUI frame:
     * distinctColours=17626/17699, nonBackgroundFraction=0.837541/0.836627, verticalBands=720/720,
     * blackFraction=0.000000/0.000000. Panel (the open screen's own list, 0,32..427,208 in GUI units, which is
     * 2,98..1279,622 in pixels at the game's scale 3): guiPanelChangedFraction=0.477975/0.478083,
     * guiPanelStrongFraction=0.455733/0.455836, guiPanelDistinctColours=15805/15877, guiPanelRowAmplitude=9.344/9.441;
     * the same rectangle of the world frame measures guiWorldPanelDistinctColours=1509/1516 and
     * guiWorldPanelRowAmplitude=9.509/9.789, and the panel mean luma 101.001/100.497 against the world's
     * 105.481/104.521.</li>
     * <li>{@code scene-ref-video2} (01:18) is the earlier GL-bridge run of this scene. Its world-frame numbers
     * (1540/0.728299/638/0.000000) agree with the two above; its panel numbers were measured before the panel
     * rectangle was scaled, on the 425x174 corner region rather than on the list, so they are not used here.</li>
     * </ul>
     * The panel floors are a fraction of the measured panel, and each one sits between the measured panel and the
     * world frame at that rectangle: {@code panelChangedFraction >= 0.30} against a measured 0.478 (a frame in which the
     * screen never drew is the world frame itself, 0.000 by construction); {@code panelStrongFraction >= 0.25} against a
     * measured 0.456 (the share differing by more than 32 per channel); {@code panelDistinctColours >= 4000} against a
     * measured 15805 and 1509 in the world frame there (the floor is 2.6x the world's count and 25% of the panel's);
     * {@code panelRowAmplitude >= 0.25} is a flatness guard only - the measured 9.344 is large, but the world frame at
     * the same rectangle measures 9.509, so row amplitude does not separate this scene's panel from the world frame at
     * this GUI scale and the floor is not claimed to.
     * <p>
     * The panel floors are measured at the game's GUI scale 3, which is what a fresh game directory resolves to at
     * 1280x720: the list rectangle is a different region of the frame at another scale (427x176 GUI units is 427x176
     * pixels at scale 1 and 1281x528 at scale 3), so a run at another scale is reported as uncalibrated rather than
     * judged by these numbers.
     */
    private static SceneGate celtium$videoSettingsGate() {
        String runs = "gl-bridge scene-floors/video-a (2026-10-11 09:11) and scene-floors/video-b (09:12), both"
                + " scene=videoSettings at x=0.5 y=12.0 z=0.5 yaw=180 pitch=20, 1280x720, guiScale=3, world frame="
                + "screenshots/celtium-validation-world.png and gui frame=screenshots/celtium-validation.png (the GL"
                + " bridge's own screenshots), screen=net.minecraft.client.gui.GuiVideoSettings"
                + " (net.minecraft.client.gui.GuiVideoSettings was open at the capture in both), plus gl-bridge"
                + " scene-ref-video2 (01:18) for the world frame";
        String numbers = "world frame: distinctColours=1536/1536 nonBackgroundFraction=0.727735/0.727735"
                + " verticalBands=638/638 blackFraction=0.000000/0.000000; gui frame:"
                + " guiPanelChangedFraction=0.477975/0.478083 guiPanelStrongFraction=0.455733/0.455836"
                + " guiPanelDistinctColours=15805/15877 guiPanelRowAmplitude=9.344/9.441"
                + " guiWorldPanelDistinctColours=1509/1516 guiWorldPanelRowAmplitude=9.509/9.789"
                + " guiPanelMeanLuma=101.001/100.497 guiWorldPanelMeanLuma=105.481/104.521"
                + " (spread: 0.000108 changed, 0.000103 strong, 72 colours, 0.097 row amplitude)";
        String margin = "world floors as the terrain scene's (nonBackgroundFraction>=0.40 = 55% of 0.727735,"
                + " verticalBands>=250 = 39% of 638, blackFraction<=0.05 against 0.000000); panel floors"
                + " panelChangedFraction>=0.30 = 63% of the measured 0.478 (a frame where the screen never drew"
                + " measures 0.000), panelStrongFraction>=0.25 = 55% of the measured 0.456, panelDistinctColours>=4000 ="
                + " 25% of the measured 15805 and 2.6x the 1509 the world frame has at the same rectangle,"
                + " panelRowAmplitude>=0.25 as a flatness guard only (measured 9.344, but the world frame measures 9.509"
                + " there, so this floor does not separate panel from world at this scale)";
        String reference = "gl-bridge " + runs + ": " + numbers + " -> floors distinctColours>16,"
                + " nonBackgroundFraction>=0.40, verticalBands>=250, blackFraction<=0.05, panelChangedFraction>=0.30,"
                + " panelStrongFraction>=0.25, panelDistinctColours>=4000, panelRowAmplitude>=0.25 (flatness guard);"
                + " margin: " + margin;
        return new SceneGate(PRESENTED_DISTINCT_COLOUR_FLOOR, 0.40D, 250, 0.05D, 0.30D, 0.25D, 4000, 0.25D, reference,
                runs, numbers, margin, true);
    }

    /**
     * The Nether scene's floors, measured on GL-bridge reference runs of the Nether at the scene's own pose.
     * <p>
     * References (GL bridge, fresh game directory, {@code scene=nether} via
     * {@code -Dceltium.validation.dimensionAtTick=80 -Dceltium.validation.dimension=-1}, 1280x720, camera x=1.5 y=70.0
     * z=1.5 yaw=180 pitch=20 - the arrival spot the fixture's own teleporter verifies as air with ground under it -
     * picture = the capture's own {@code screenshots/celtium-validation.png}):
     * <ul>
     * <li>{@code scene-floors/nether-c1} (2026-10-11 09:43) and {@code scene-floors/nether-c2} (09:44), two runs of
     * the same scene at the same pose. The renderer's own viewport at the capture read x=1.5000 y=71.6000 z=1.4500,
     * the pose's eye, in both ({@code rendererCameraAtPose=true}). Measured: distinctColours=1937/1894,
     * nonBackgroundFraction=0.938502/0.938502, verticalBands=720/720, blackFraction=0.087951/0.087951,
     * backgroundColour=0x200201. Spread: 43 colours, 0.000000 non-background, 0 bands, 0.000000 black.</li>
     * <li>The earlier runs at the overworld's pose (x=0.5 y=12 z=0.5) are <em>not</em> this scene and are not used:
     * y=12 in the Nether is inside the dimension's rock, where the frame is whichever sections the renderer happened to
     * have meshed, measuring nonBackgroundFraction 0.183982 (background 0x120303) and 0.420806 (background 0x000027,
     * the void where nothing was drawn) at the same pose - see {@link #celtium$referencePose}.</li>
     * </ul>
     * A Nether frame of this fixture is dark - netherrack lit only by what light reaches it - so the overworld's
     * blackFraction ceiling of 0.05 would fail this scene's own correct frames (measured 0.087951): the scene's
     * ceiling is its own. {@code nonBackgroundFraction >= 0.80} is 85% of the measured 0.938502; the two runs measured
     * that fraction to the same six decimals, so the 15% is an allowance for content a frame may legitimately not
     * show, not a spread - and a frame missing a section would show the void in its place, which is exactly the
     * missing-content case the floor exists to catch. {@code verticalBands >= 288} is 40% of the measured 720 (the same
     * 720 the in-rock frames measured; a flat or untextured frame is far below). {@code blackFraction <= 0.18} is 2x
     * the measured 0.087951, which still catches the audit's large-black-region frames (0.32-0.67 black).
     */
    private static SceneGate celtium$netherGate() {
        String runs = "gl-bridge scene-floors/nether-c1 (2026-10-11 09:43) and scene-floors/nether-c2 (09:44), both"
                + " scene=nether at x=1.5 y=70.0 z=1.5 yaw=180 pitch=20 (the arrival spot the fixture's teleporter"
                + " verifies as air with ground: dimensionArrivalPlacement 'arrival=0,70,1 ... placed=1,70,1"
                + " placedBlock=minecraft:air ground=true safe=true'), 1280x720, picture=screenshots/celtium-validation.png"
                + " (the GL bridge's own screenshot), renderer viewport at the capture x=1.5000 y=71.6000 z=1.4500 in"
                + " both (the pose's eye)";
        String numbers = "distinctColours=1937/1894 nonBackgroundFraction=0.938502/0.938502 verticalBands=720/720"
                + " blackFraction=0.087951/0.087951 backgroundColour=0x200201 (spread: 43 colours, 0.000000"
                + " non-background, 0 bands, 0.000000 black). The overworld-pose runs at x=0.5 y=12 z=0.5 are a different"
                + " scene variant: 0.183982 non-background (background 0x120303, inside the rock) and 0.420806"
                + " (background 0x000027, the void where no section was drawn)";
        String margin = "nonBackgroundFraction floor 0.80 = 85% of the measured 0.938502 (the runs agree to six"
                + " decimals, so this is an allowance for content a frame may legitimately not show, and a missing"
                + " section shows the void instead and fails); verticalBands floor 288 = 40% of the measured 720;"
                + " blackFraction ceiling 0.18 = 2x the measured 0.087951, because the overworld's 0.05 would fail this"
                + " scene's own correct frames (and the audit's large-black frames are 0.32-0.67, still caught)";
        String reference = "gl-bridge " + runs + ": " + numbers + " -> floors distinctColours>16,"
                + " nonBackgroundFraction>=0.80, verticalBands>=288, blackFraction<=0.18; margin: " + margin;
        return new SceneGate(PRESENTED_DISTINCT_COLOUR_FLOOR, 0.80D, 288, 0.18D, 0.0D, 0.0D, 0, 0.0D, reference, runs,
                numbers, margin, true);
    }

    /**
     * A scene with no measured reference: no floors are enforced, and the report says so in words.
     * <p>
     * It must not pass silently either, which is what {@code UNCALIBRATED_PRESENTED_IMAGE} is for: the picture is
     * still held to the smoke test, the identity, the frame binding and the camera pose, but nothing here claims it
     * shows the scene, because nobody has measured what the scene looks like from this pose yet.
     */
    private static SceneGate celtium$uncalibratedGate(Scene scene) {
        String runs = "none: no GL-bridge reference run of scene " + scene.name + " at its own pose (see"
                + " celtium$referencePose) exists yet";
        String numbers = "none measured for this scene";
        String margin = "no floors: a floor without a measured reference would be another scene's number, which is what"
                + " failed a Nether frame whose picture was fine (.celtium-work/runtime/vuid-after2:"
                + " nonBackgroundFraction=0.32598 against the overworld floor of 0.40)";
        String reference = "not calibrated, scene " + scene.name + ": " + runs + ". No floor of any other scene is"
                + " applied here - not the overworld's (nonBackgroundFraction>=0.40, verticalBands>=250,"
                + " blackFraction<=0.05) and not the Nether's (0.80/288/0.18) - and the run's own"
                + " sceneCalibrationKey is compared with the key its scene's floors would need. The picture is held to"
                + " the smoke test, its identity, the frame binding and the camera pose only.";
        return new SceneGate(PRESENTED_DISTINCT_COLOUR_FLOOR, 0.0D, 0, 1.0D, 0.0D, 0.0D, 0, 0.0D, reference, runs,
                numbers, margin, false);
    }

    /**
     * The calibration key of a run: everything that decides what the picture contains.
     * <p>
     * This is the audit's requirement made concrete - the key carries the scene, the pose, the world stack and every
     * variant flag that changes the frame (registry sweeps, the all-blocks wall, the entity set, and the GUI scale the
     * panel is laid out at) - so a floor measured on one picture cannot be applied to a different one. The key is a
     * string so the report can carry both the run's key and the key its scene's floors were measured under, and a
     * reader can see the difference without reading this file.
     * <p>
     * It is why the measured run at pitch=-14 of a scene whose floors were taken at pitch=+20 is uncalibrated rather
     * than failed: the key differs, so no floor of that scene describes that picture.
     */
    private static String celtium$calibrationKey(Scene scene, CameraPose pose, int guiScale) {
        String stack = System.getProperty("celtium.validation.worldLayers", celtium$DEFAULT_WORLD_LAYERS);
        int sweep = Integer.getInteger("celtium.validation.blockSweep", 0).intValue();
        StringBuilder key = new StringBuilder();
        key.append("scene=").append(scene.name);
        key.append(" pose=").append(pose.describe());
        key.append(" stack=").append(celtium$DEFAULT_WORLD_LAYERS.equals(stack) ? "default" : stack);
        key.append(" blockSweep=").append(sweep);
        key.append(" allBlocks=").append(Boolean.getBoolean("celtium.validation.allBlocks"));
        key.append(" entities=").append(celtium$entityVariant());
        key.append(" guiScale=").append(guiScale > 0 ? Integer.toString(guiScale) : "unreadable");
        return key.toString();
    }

    /**
     * The key the scene's floors were measured under, or the reason there is none.
     * <p>
     * Built from constants, never from the live properties the candidate run sets. The previous form called
     * {@link #celtium$calibrationKey} with the reference pose and scale, and that method reads
     * {@code celtium.validation.worldLayers}, {@code blockSweep}, {@code allBlocks} and the entity flags from the
     * running JVM - so the reference variant followed the candidate, the two keys always matched, and
     * {@code sceneCalibrationKeyMatches=true} meant nothing. The audit found it by running a candidate with
     * allBlocks and seeing the reference reported as allBlocks too, against references that were measured on plain
     * terrain (A03). A key that describes a measurement must not move when the thing it is compared against moves.
     */
    private static String celtium$referenceCalibrationKey(Scene scene) {
        if (!scene.calibrated) {
            return "none: scene " + scene.name + " has no measured reference at any pose";
        }
        StringBuilder key = new StringBuilder();
        key.append("scene=").append(scene.name);
        key.append(" pose=").append(celtium$referencePose(scene).describe());
        key.append(" stack=").append(celtium$REFERENCE_STACK);
        key.append(" blockSweep=").append(celtium$REFERENCE_BLOCK_SWEEP);
        key.append(" allBlocks=").append(celtium$REFERENCE_ALL_BLOCKS);
        key.append(" entities=").append(celtium$REFERENCE_ENTITIES);
        key.append(" guiScale=").append(celtium$REFERENCE_GUI_SCALE);
        return key.toString();
    }

    /** The world stack every measured reference used: the fixture's default, stated once. */
    private static final String celtium$REFERENCE_STACK = "default";
    /** The block sweep every measured reference used: none. */
    private static final int celtium$REFERENCE_BLOCK_SWEEP = 0;
    /** Whether the measured references ran with the all-blocks wall: they did not. */
    private static final boolean celtium$REFERENCE_ALL_BLOCKS = false;
    /** The entity variant the measured references ran with: none of the glow/keep/capture flags. */
    private static final String celtium$REFERENCE_ENTITIES = "none";

    /** The entity flags that change the frame, as one token: the measured references were taken with none of them. */
    private static String celtium$entityVariant() {
        StringBuilder variant = new StringBuilder();
        String[] flags = {"celtium.validation.keepEntities", "celtium.validation.captureEntities",
                "celtium.validation.glowEntities", "celtium.validation.spawnEntities"};
        for (String flag : flags) {
            if (Boolean.getBoolean(flag)) {
                if (variant.length() > 0) {
                    variant.append('+');
                }
                variant.append(flag.substring(flag.lastIndexOf('.') + 1));
            }
        }
        return variant.length() == 0 ? "none" : variant.toString();
    }

    /** The fixture's default flat world: the world every measured reference above was taken in. */
    private static final String celtium$DEFAULT_WORLD_LAYERS =
            "3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass,minecraft:water,minecraft:lava,minecraft:fire,"
            + "minecraft:leaves,minecraft:stained_glass,minecraft:chest,minecraft:enchanting_table;1;";


    /**
     * The flat world's layers, as the superflat generator string.
     * <p>
     * Configurable because the layers decide what the scene contains and therefore how heavy it is: the version with
     * lava and fire measured about twice the frame time of the one without, and an A/B of the same build has to be able
     * to tell "the world got heavier" apart from "the renderer got slower". The default is the fuller world.
     */
    private final String celtium$worldLayers = System.getProperty("celtium.validation.worldLayers",
            celtium$DEFAULT_WORLD_LAYERS);
    private boolean celtium$rejoinPending;
    private boolean commandsRun;
    private boolean finished;
    private boolean interopRun;
    private int framesAtInterop;
    private int fboAtInterop, colorAtInterop, depthAtInterop;
    private int worldTicks;
    /** Whether this run has already asked for the presented image to be dumped; the request is one-shot. */
    private static boolean celtium$presentedDumpRequested;
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
    /** Screen-space pixels of the merged-quad probes, so the GL_BACK readback can sample what the screen shows. */
    private int celtium$probeRedX = -1;
    private int celtium$probeRedY = -1;
    private int celtium$probeGreenX = -1;
    private int celtium$probeGreenY = -1;
    private int celtium$probeFirstX = -1;
    private int celtium$probeFirstY = -1;
    private boolean celtium$postprocess;
    private boolean celtium$spectator;
    /**
     * How many presented-image assertions failed at the capture. Only the GL-less stub ever runs that check, and only a
     * non-zero value changes the verdict, so the GL bridge's PASSED is untouched.
     */
    private int celtium$presentedImageFailures;
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
                // The merged-quad probes, sampled where the screen - not the game FBO - would show them: the fixture's
                // overlay draws land on the default framebuffer, which is exactly what this readback covers. GL's
                // origin is bottom-left, so the image-space y is flipped.
                if (celtium$probeRedX >= 0) {
                    report.addProperty("screenReadbackProbeRed", celtium$samplePixel(pixels, width, height,
                            celtium$probeRedX, celtium$probeRedY));
                    report.addProperty("screenReadbackProbeGreen", celtium$samplePixel(pixels, width, height,
                            celtium$probeGreenX, celtium$probeGreenY));
                    report.addProperty("screenReadbackProbeFirst", celtium$samplePixel(pixels, width, height,
                            celtium$probeFirstX, celtium$probeFirstY));
                }
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
            celtium$probeRedX = (int) (width * 0.14D * scale);
            celtium$probeRedY = (int) (height * 0.82D * scale);
            celtium$probeGreenX = (int) (width * 0.34D * scale);
            celtium$probeGreenY = (int) (height * 0.82D * scale);
            celtium$probeFirstX = (int) ((width * 0.45D + 1.5D) * scale);
            celtium$probeFirstY = (int) ((height * 0.75D + 1.5D) * scale);
            report.addProperty("mergedQuadProbeRed", celtium$probeRedX + "," + celtium$probeRedY);
            report.addProperty("mergedQuadProbeGreen", celtium$probeGreenX + "," + celtium$probeGreenY);
            report.addProperty("mergedQuadProbeFirst", celtium$probeFirstX + "," + celtium$probeFirstY);
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

    /** {@return the RGB at image-space (top-origin) x,y} in a GL back-buffer readback (bottom-origin). */
    private static String celtium$samplePixel(java.nio.ByteBuffer pixels, int width, int height, int x, int y) {
        int glY = height - 1 - y;
        if (x < 0 || x >= width || glY < 0 || glY >= height) {
            return "outside";
        }
        int index = (glY * width + x) * 4;
        return (pixels.get(index) & 0xFF) + "," + (pixels.get(index + 1) & 0xFF) + ","
                + (pixels.get(index + 2) & 0xFF);
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
        // N02 acceptance probe: display-list add/recompile/delete/re-use, off unless -Dceltium.validation.modelLifecycle.
        ModelListLifecycleProbe.tick(mc, worldTicks);
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
                                        // The vanilla transfer lands the player inside the portal it finds or builds, and
                                        // the game's own portal rule in Entity.onEntityUpdate then sends any entity whose
                                        // bounding box is inside a portal block from -1 straight back to 0 - after
                                        // getMaxInPortalTime() ticks of contact, which is one for a player who cannot take
                                        // damage, as in this creative world. That is the return trip the Nether runs
                                        // measured (dimension -1 at tick 82, back at 0 by tick 148), and it is why they
                                        // ended with no capture.
                                        //
                                        // A real trip leaves the player beside the destination portal and with the portal
                                        // cooldown set. The fixture drives the same server transfer, so it uses a Teleporter
                                        // that runs the vanilla placement first and then steps the player to a verified,
                                        // portal-free spot next to it - the step a player would take themselves. What was
                                        // chosen is recorded and reported; nothing is silently moved.
                                        net.minecraft.world.Teleporter teleporter = new net.minecraft.world.Teleporter(target) {
                                            @Override
                                            public void placeInPortal(net.minecraft.entity.Entity entityIn, float rotationYawIn) {
                                                super.placeInPortal(entityIn, rotationYawIn);
                                                celtium$settleArrival(this.world, entityIn, rotationYawIn);
                                            }
                                        };
                                        server.getPlayerList().transferPlayerToDimension(player, celtium$dimension,
                                                teleporter);
                                        // Entity.onEntityUpdate sets exactly this immediately before changeDimension, so a
                                        // player who came through a portal cannot be sent back for 300 ticks. The fixture
                                        // used to zero it, which re-armed the return trip instead.
                                        player.timeUntilPortal = player.getPortalCooldown();
                                        celtium$portalCooldownAfterTransfer = player.timeUntilPortal;
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
                // The renderer's own presented-frame counter at the moment the client arrives: the delta to the value
                // at the capture is the renderer's answer to "was a frame drawn in this dimension", independent of the
                // fixture's own world-frame counter.
                celtium$presentedFramesAtArrival = celtium$presentedFrameCount();
                celtium$presentedHighWaterBeforeArrival = celtium$presentedFramesHighWater;
                celtium$dimensionTrace("CELTIUM_DIM_ARRIVED tick=" + worldTicks + " client=" + mc.player.dimension
                        + " world=" + mc.world.provider.getDimension() + " screen=" + celtium$screenName(mc)
                        + " worldFrames=" + worldFrames + " presentedFrames=" + celtium$presentedFramesAtArrival
                        + " presentedHighWater=" + celtium$presentedHighWaterBeforeArrival
                        + " clientWorldId=" + Integer.toHexString(System.identityHashCode(mc.world))
                        + " placement=[" + celtium$arrivalPlacement + "]");
            }
            // Per-tick dimension trace while a dimension run is in flight: the Nether run arrived at tick 81 and was
            // back in dimension 0 by the capture, and a single reading cannot say when or why. The server side is asked
            // for its own value on the same tick, so a client-only change can be told from a real transfer back.
            if (celtium$dimensionAtTick > 0 && celtium$changedDimension) {
                int clientDim = mc.player == null ? Integer.MIN_VALUE : mc.player.dimension;
                int worldDim = mc.world == null ? Integer.MIN_VALUE : mc.world.provider.getDimension();
                int serverDim = Integer.MIN_VALUE;
                net.minecraft.entity.player.EntityPlayerMP tracePlayer = null;
                net.minecraft.server.integrated.IntegratedServer traceServer = mc.getIntegratedServer();
                if (traceServer != null && mc.player != null) {
                    tracePlayer = traceServer.getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
                    if (tracePlayer != null) {
                        serverDim = tracePlayer.dimension;
                    }
                }
                boolean firstObservation = celtium$lastClientDimension == Integer.MIN_VALUE;
                boolean dimensionChanged = !firstObservation && celtium$lastClientDimension != clientDim;
                boolean changed = dimensionChanged || celtium$lastTraceClient != clientDim || celtium$lastTraceServer != serverDim;
                if (dimensionChanged) {
                    // This is the client-visible effect of the server's Respawn packet: the dimension value has changed,
                    // the world object behind it is a different one, and the game has put its download-terrain screen up
                    // while the new world's chunks arrive. Reported together because any one of them alone can be
                    // produced without the others.
                    celtium$dimensionTrace("CELTIUM_DIM_CHANGE tick=" + worldTicks + " from=" + celtium$lastClientDimension
                            + " client=" + clientDim + " world=" + worldDim + " server=" + serverDim
                            + " screen=" + celtium$screenName(mc)
                            + " clientWorldId=" + (mc.world == null ? "-" : Integer.toHexString(System.identityHashCode(mc.world)))
                            + " doneLoadingTerrain=" + celtium$doneLoadingTerrain(mc)
                            + " arrivedAt=" + celtium$arrivedAtTick);
                    if (celtium$arrivedAtTick >= 0 && clientDim != celtium$dimension && celtium$leftTargetAtTick < 0) {
                        celtium$leftTargetAtTick = worldTicks;
                        celtium$leftTargetEvent = "client dimension " + clientDim + " (target " + celtium$dimension
                                + ") at tick " + worldTicks + ", screen=" + celtium$screenName(mc)
                                + ", server=" + serverDim;
                        celtium$dimensionTrace("CELTIUM_DIM_LEFT tick=" + worldTicks + " " + celtium$leftTargetEvent);
                    }
                }
                // Kept outside the change branch: the first traced tick only establishes the starting dimension, and a
                // value that was never stored would make every later tick look like the first one.
                celtium$lastClientDimension = clientDim;
                if (changed || worldTicks % 5 == 0) {
                    String clientPosition = mc.player == null ? "-" : (int) mc.player.posX + "," + (int) mc.player.posY
                            + "," + (int) mc.player.posZ;
                    String serverPosition = tracePlayer == null ? "-" : (int) tracePlayer.posX + "," + (int) tracePlayer.posY
                            + "," + (int) tracePlayer.posZ;
                    celtium$dimensionTrace("CELTIUM_DIM_TRACE tick=" + worldTicks + " client=" + clientDim
                            + " world=" + worldDim + " server=" + serverDim
                            + " screen=" + celtium$screenName(mc)
                            + " arrivedAt=" + celtium$arrivedAtTick
                            // The client's own position and whether the chunk under it is loaded decide which side of the
                            // wait is stuck: no chunk means the server never finished sending it, a loaded chunk with the
                            // screen still up means only the screen is left behind.
                            + " cPos=" + clientPosition
                            + " sPos=" + serverPosition
                            + " chunkLoaded=" + (mc.world == null || mc.player == null ? "-"
                                    : mc.world.isBlockLoaded(mc.player.getPosition()))
                            // Whether either side's bounding box is inside a portal block is the candidate for the return
                            // to the previous dimension: a transfer that lands inside one is sent back by the game's own
                            // portal logic, which would look exactly like this trace. Both sides are asked, because the
                            // server's position is the one its portal rule reads while the client's is the one that
                            // decides what the frame shows.
                            + " cInPortal=" + (mc.player == null || mc.world == null ? "-"
                                    : celtium$overlapsPortal(mc.world, mc.player))
                            + " sInPortal=" + (tracePlayer == null || tracePlayer.world == null ? "-"
                                    : celtium$overlapsPortal(tracePlayer.world, tracePlayer))
                            + " sTimeUntilPortal=" + (tracePlayer == null ? "-" : tracePlayer.timeUntilPortal)
                            // Health is what distinguishes "something transferred the player back" from "the player died
                            // and respawned in the previous dimension", which is what the falling positions after the
                            // return look like.
                            + " health=" + (mc.player == null ? "-" : mc.player.getHealth() + "/" + mc.player.getMaxHealth())
                            + " onGround=" + (mc.player == null ? "-" : mc.player.onGround)
                            + " framesInTarget=" + celtium$framesInTargetDimension);
                }
                celtium$lastTraceClient = clientDim;
                celtium$lastTraceServer = serverDim;
                if (changed && celtium$dimensionEvents.length() < 320) {
                    celtium$dimensionEvents.append("t").append(worldTicks).append(":c=").append(clientDim)
                            .append(",s=").append(serverDim).append(' ');
                }
                // The arrival portal is a trap: an entity whose bounding box is inside a portal block is sent from -1
                // back to 0 by the game's own rule. The teleporter places the player clear of it, and this is the guard
                // for the remaining ways back in - falling, a collision pushing the player, or the client's own position
                // being applied by the server. Every re-entry and every step out is reported with its tick, the count is
                // bounded, and a player who is still inside a portal at the capture fails the capture below.
                if (celtium$arrivedAtTick >= 0 && celtium$safeSpotKnown && mc.player != null && mc.world != null
                        && mc.player.dimension == celtium$dimension && worldTicks < 195 && celtium$portalEscapes < 8
                        && celtium$overlapsPortal(mc.world, mc.player)) {
                    celtium$portalReentries++;
                    celtium$portalEscapes++;
                    celtium$dimensionTrace("CELTIUM_DIM_PORTAL_ESCAPE tick=" + worldTicks + " at="
                            + (int) mc.player.posX + "," + (int) mc.player.posY + "," + (int) mc.player.posZ
                            + " to=" + (int) celtium$safeX + "," + (int) celtium$safeY + "," + (int) celtium$safeZ
                            + " escape=" + celtium$portalEscapes);
                    mc.player.setPositionAndUpdate(celtium$safeX, celtium$safeY, celtium$safeZ);
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
            if (celtium$presentedVerdictPendingAtTick >= 0
                    && ((worldTicks > celtium$presentedVerdictPendingAtTick + 5
                            && worldFrames <= celtium$presentedVerdictPendingFrames)
                        || worldTicks > celtium$presentedVerdictPendingAtTick + 40)) {
                // The capture happened and its verdict was left to the first frame whose requested dump is on disk. Two
                // ways out, both recorded: the render loop stopped producing frames at all, or the picture never
                // arrived within 40 ticks. The first shape of this waited five ticks, which the capture itself spends
                // in one catch-up burst - runTick runs up to ten times per iteration - so it finished a run whose very
                // next frame would have carried the picture (measured: watchdog at tick 207 for a capture at 201,
                // after which the report judged at shutdown instead of on the frame).
                report.addProperty("presentedVerdictWatchdogAtTick", Integer.valueOf(worldTicks));
                report.addProperty("presentedVerdictWatchdogFrames", Integer.valueOf(worldFrames));
                report.addProperty("presentedVerdictWatchdogReason", worldFrames <= celtium$presentedVerdictPendingFrames
                        ? "no world frame was rendered after the capture" : "the requested picture did not arrive");
                celtium$presentedVerdictPendingAtTick = -1;
                finish(mc);
                return;
            }
        celtium$installExitHook(mc);
        // Taken here, on the render thread, where the world, the player and the screen are the client's to read. The
        // shutdown hook only ever reads this value.
        celtium$progress = new ProgressSnapshot(worldTicks, worldFrames, celtium$captureStarted, celtium$captureStartTick,
                mc.player == null ? Integer.MIN_VALUE : mc.player.dimension,
                mc.world == null ? Integer.MIN_VALUE : mc.world.provider.getDimension(),
                mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getName(),
                mc.gameDir.toPath().resolve("logs/celtium-validation.json"));
            celtium$blockSweep(mc);
            // The fixture's camera. It used to be a client-side setPositionAndRotation at tick 20 and nothing else,
            // and the server's own movement check rejected a 500-block step as "moved too quickly" and put the client
            // back where the server had it - so the camera was wherever the run's drift and the inherited save left the
            // player, and two runs of one scene captured different pictures (measured: x=512.5 z=-12.5 against z=-8.5).
            // Setting it on the server through the game's own teleport moves the tracked position too, so no correction
            // follows, and re-applying it on the client every tick holds it against gravity, fluids and the partial
            // tick. What the camera actually was at the capture is measured and gated below.
            if (worldTicks == celtium$poseTick && !celtium$poseRequested) {
                celtium$poseRequested = true;
                celtium$poseResult = celtium$teleportPoseOnServer(mc);
            }
            if (worldTicks >= celtium$poseTick) {
                celtium$applyPose(mc);
                celtium$poseAppliedAtTick = worldTicks;
                celtium$poseAppliedTicks++;
            }
            // A GUI scene's screen is opened here, on the client tick after the world frame was captured: inside the
            // world render pass it would have been half of the frame it was captured in as well as of the next.
            if (celtium$guiOpenPending && worldTicks >= celtium$captureStartTick + 1) {
                celtium$guiOpenPending = false;
                celtium$openGuiScene(mc);
            }
            if (worldTicks == 20) {
                mc.player.capabilities.isFlying = true;
                // Early, so a run that never reaches its capture still says which scene, which run id and which pose
                // it was: the report is the only thing a failed run leaves behind. The gate's provenance is recorded
                // here too, so a run that dies before its verdict still names the reference its floors came from.
                report.addProperty("scene", celtium$scene.name);
                report.addProperty("sceneGuiStage", Boolean.valueOf(celtium$scene.guiStage));
                report.addProperty("runId", celtium$runId);
                report.addProperty("cameraRequested", celtium$pose.describe());
                report.addProperty("sceneGateSource", celtium$gate.reference);
                report.addProperty("sceneGateReferenceRuns", celtium$gate.referenceRuns);
                report.addProperty("sceneGateReferenceNumbers", celtium$gate.referenceNumbers);
                report.addProperty("sceneGateMargin", celtium$gate.margin);
                report.addProperty("sceneGateCalibrated", Boolean.valueOf(celtium$gate.calibrated));
                boolean earlyVariantMeasured = celtium$variantMeasuredAtVerdict();
                report.addProperty("sceneGateFloorsEnforced",
                        Boolean.valueOf(celtium$gate.calibrated && earlyVariantMeasured));
                report.addProperty("sceneVariant", celtium$sceneVariantDescription());
                report.addProperty("sceneCalibrationKey", celtium$runCalibrationKey());
                report.addProperty("sceneReferenceCalibrationKey", celtium$referenceCalibrationKey(celtium$scene));
                report.addProperty("sceneCalibrationKeyMatches", Boolean.valueOf(earlyVariantMeasured));
                if (!celtium$gate.calibrated || !earlyVariantMeasured) {
                    report.addProperty("floorsNotCalibrated", "FLOORS NOT CALIBRATED for scene " + celtium$scene.name
                            + " (" + celtium$sceneDimension() + "): " + celtium$gate.referenceRuns + "; the picture is"
                            + " held to the smoke test, its identity, the frame binding and the camera pose only, and"
                            + " the verdict is UNCALIBRATED_PRESENTED_IMAGE (not PASSED, not FAILED). "
                            + celtium$gate.reference);
                }
                if (!"none".equals(celtium$sceneError)) {
                    report.addProperty("sceneError", celtium$sceneError);
                }
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
                // Two blocks right of centre so the selection-outline stone at (px, pz-3) does not sit in front of
                // it - the item is the lit ITEM-format path the pixel comparison needs actually visible.
                net.minecraft.entity.item.EntityItem dropped = new net.minecraft.entity.item.EntityItem(mc.world,
                        mc.player.posX + 2.5D, gy + 0.5D, mc.player.posZ - 5.0D,
                        droppedStack);
                dropped.motionX = 0.0D; dropped.motionY = 0.0D; dropped.motionZ = 0.0D;
                dropped.setNoDespawn();
                celtium$spawnEntity(mc, dropped, 2.5D, -5.0D);
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
            Minecraft mc = Minecraft.getMinecraft();
            // The first world frame the renderer had presented anything for, and the high-water mark of its presented
            // counter. The presented-image assertion compares that counter with this fixture's own world-frame counter,
            // and a dimension change recreates the frame target the counter lives in, so the two only track inside one
            // target's lifetime. Both numbers are sampled here, every world frame, so the report can say which of the
            // two it is looking at instead of inferring it.
            int presentedNow = celtium$presentedFrameCount();
            if (presentedNow > 0 && celtium$firstPresentedWorldFrame < 0) {
                celtium$firstPresentedWorldFrame = worldFrames;
            }
            if (presentedNow > celtium$presentedFramesHighWater) {
                celtium$presentedFramesHighWater = presentedNow;
            }
            // Frames the target dimension itself drew, with nothing over them. The capture's claim for a dimension run is
            // that a frame of the new dimension was drawn there; a frame counted while the download-terrain screen is up
            // is that screen's background, not the scene, and a frame of the previous dimension is neither.
            if (celtium$dimensionAtTick > 0 && mc.world != null && mc.player != null
                    && mc.player.dimension == celtium$dimension
                    && mc.world.provider.getDimension() == celtium$dimension
                    && mc.currentScreen == null) {
                celtium$framesInTargetDimension++;
            }
            if (worldTicks == 120 || !report.has("framebuffer")) {
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
        // Every render tick: see whether a requested dump has been written, and complete its identity from the
        // renderer's own counters if it has. This is the frame-bound step - the identity is taken from the observation
        // that the file appeared while the presented-frame counter had advanced by exactly one from the request.
        celtium$completePendingDumps();
        // Stage two of a GL-less capture: the present between the two render ticks has written the frame the capture
        // asked for, so the picture verdict and the report belong to this frame rather than to the last one. The
        // verdict reads the identity completed above and the capture-time snapshot; it never compares a live counter
        // with a capture-time one, which is how a passing Intel run failed at shutdown with
        // "presentedImageFramesAtCapture=150 is not within one of worldFrames=152".
        if (celtium$presentedVerdictPendingAtTick >= 0) {
            if (!celtium$pendingVerdictEvidenceComplete()) {
                // The present that writes the picture happens after this event (updateDisplay is the last thing in the
                // loop iteration), so the frame the capture asked for is not on disk yet. Judging here judged the file
                // from before the request; the verdict now waits for the identity the write completes.
                return;
            }
            celtium$presentedVerdictPendingAtTick = -1;
            report.addProperty("presentedImageVerificationTick", Integer.valueOf(worldTicks));
            report.addProperty("presentedImageVerificationWorldFrames", Integer.valueOf(worldFrames));
            report.addProperty("presentedImageVerificationPresentedFrames", Integer.valueOf(celtium$presentedFrameCount()));
            celtium$judgePictures(mc, "on the frame after the capture");
            celtium$setStatusFromPictures();
            finish(mc);
            return;
        }
        // The GUI stage: the video settings screen has been opened and is being laid out; once it has had its ticks
        // and frames, capture it - the screen is drawn before this event, so the picture holds it - and leave the
        // verdict to the next frame for the same reason a GL-less world capture does.
        if (celtium$stage >= 1) {
            celtium$advanceGuiStage(mc);
            return;
        }
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
        String held = System.getProperty("celtium.validation.heldItem");
        if (worldTicks == 30 && held != null && !held.isEmpty() && !"none".equals(held)) {
            // A held item is the lit ITEM-format path at its largest: the first-person renderer draws it through
            // the same enableStandardItemLighting the dropped item uses, at a size the comparison can see. Set this
            // early so the ~10-tick equip animation has finished and the hand sits still at the capture. Enchanted
            // adds the glint pass on top.
            net.minecraft.item.ItemStack inHand = new net.minecraft.item.ItemStack(
                    net.minecraft.init.Items.APPLE);
            if ("enchanted".equals(held)) {
                inHand.addEnchantment(net.minecraft.init.Enchantments.SHARPNESS, 1);
            }
            mc.player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, inHand);
            report.addProperty("heldItem", held);
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
        // Requested as soon as the world is running rather than a few ticks before the capture: the capture is
        // trigger-driven (it starts once the fixture's preconditions hold, measured at world frame 61 with the
        // preconditions met), so any fixed tick near an assumed capture tick either fires too late or never. The
        // request is one-shot and the file is what the picture assertions read, so being early is the safe direction;
        // presentedImagePngAgeMs reports how early it was.
        // The early dump, kept as a diagnostic only. It used to share the capture's path, which is how a run could end
        // with the verdict judging a file an earlier or later request had written: this one goes to its own per-run
        // path, carries its own identity, and is never what the verdict reads.
        if (Boolean.getBoolean("celtium.validation.dumpPresentedImage") && !celtium$presentedDumpRequested
                && worldTicks >= Integer.getInteger("celtium.validation.dumpPresentedTick", 20).intValue()) {
            celtium$presentedDumpRequested = true;
            celtium$requestPresentedDump(mc, "early");
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
            report.addProperty("dimensionTarget", Integer.valueOf(celtium$dimension));
            report.addProperty("dimensionFramesInTarget", Integer.valueOf(celtium$framesInTargetDimension));
            report.addProperty("dimensionMinFrames", Integer.valueOf(celtium$minDimensionFrames));
            report.addProperty("dimensionFramesSinceArrival",
                    Integer.valueOf(celtium$arrivedFrames < 0 ? -1 : worldFrames - celtium$arrivedFrames));
            report.addProperty("dimensionWorldFramesAtCapture", Integer.valueOf(worldFrames));
            report.addProperty("dimensionArrivalPlacement", celtium$arrivalPlacement);
            report.addProperty("dimensionPortalCooldownAfterTransfer", Integer.valueOf(celtium$portalCooldownAfterTransfer));
            report.addProperty("dimensionPortalReentries", Integer.valueOf(celtium$portalReentries));
            report.addProperty("dimensionPortalEscapes", Integer.valueOf(celtium$portalEscapes));
            report.addProperty("dimensionLeftTargetAtTick", Integer.valueOf(celtium$leftTargetAtTick));
            report.addProperty("dimensionLeftTargetEvent", celtium$leftTargetEvent);
            report.addProperty("dimensionScreenAtCapture", celtium$screenName(mc));
            report.addProperty("dimensionDoneLoadingTerrainAtCapture", celtium$doneLoadingTerrain(mc));
            report.addProperty("dimensionClientInPortalAtCapture", Boolean.valueOf(
                    mc.world != null && mc.player != null && celtium$overlapsPortal(mc.world, mc.player)));
            // The server's own value for this player, read at the capture: the client can hold a dimension the server
            // never agreed to, and then the world it renders is not the one that was asked for.
            int serverDimensionAtCapture = Integer.MIN_VALUE;
            net.minecraft.entity.player.EntityPlayerMP captureServerPlayer = null;
            net.minecraft.server.integrated.IntegratedServer captureServer = mc.getIntegratedServer();
            if (captureServer != null && mc.player != null) {
                captureServerPlayer = captureServer.getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
                if (captureServerPlayer != null) {
                    serverDimensionAtCapture = captureServerPlayer.dimension;
                }
            }
            report.addProperty("dimensionServerAtCapture", Integer.valueOf(serverDimensionAtCapture));
            report.addProperty("dimensionServerInPortalAtCapture", Boolean.valueOf(
                    captureServerPlayer != null && captureServerPlayer.world != null
                            && celtium$overlapsPortal(captureServerPlayer.world, captureServerPlayer)));
            report.addProperty("dimensionServerTimeUntilPortalAtCapture",
                    Integer.valueOf(captureServerPlayer == null ? -1 : captureServerPlayer.timeUntilPortal));
            // The renderer's own frame counter, measured at the arrival and here: the difference is the renderer's
            // answer to "was a frame presented in this dimension", independent of the fixture's own counters.
            int presentedAtCapture = celtium$presentedFrameCount();
            report.addProperty("dimensionPresentedFramesAtArrival", Integer.valueOf(celtium$presentedFramesAtArrival));
            report.addProperty("dimensionPresentedFramesAtCapture", Integer.valueOf(presentedAtCapture));
            report.addProperty("dimensionPresentedFramesDrawn", Integer.valueOf(
                    celtium$presentedFramesAtArrival < 0 || presentedAtCapture < 0 ? -1
                            : presentedAtCapture - celtium$presentedFramesAtArrival));
            report.addProperty("dimensionPresentedHighWaterBeforeArrival", Integer.valueOf(celtium$presentedHighWaterBeforeArrival));
            report.addProperty("dimensionPresentedHighWaterAtCapture", Integer.valueOf(celtium$presentedFramesHighWater));
            // The measurement that says the renderer's counter is per frame target rather than per run: it had already
            // reached this value before the transfer, and the value at the arrival is lower. Without this, the
            // whole-run comparison in the presented-image check reads a counter reset as frames that never reached the
            // window.
            report.addProperty("dimensionPresentedCounterResetAtArrival",
                    Boolean.valueOf(celtium$presentedHighWaterBeforeArrival > 0
                            && celtium$presentedFramesAtArrival < celtium$presentedHighWaterBeforeArrival));
            report.addProperty("dimensionWorldFramesAtArrival", Integer.valueOf(celtium$arrivedFrames < 0 ? -1 : celtium$arrivedFrames));
            // The whole-run accounting, so the two counters' divergence is a sum the report shows rather than a gap a
            // reader has to explain: frames presented before the frame target was recreated, plus frames presented
            // after it, against the frames this fixture counted.
            int presentedAccounted = Math.max(0, celtium$presentedHighWaterBeforeArrival) + Math.max(0, presentedAtCapture);
            report.addProperty("dimensionWorldFramesAccountedByPresented", Integer.valueOf(presentedAccounted));
            report.addProperty("dimensionWorldFramesUnaccountedByPresented", Integer.valueOf(worldFrames - presentedAccounted));
            java.util.List<String> gateFailures = celtium$dimensionGateFailures(mc, serverDimensionAtCapture, presentedAtCapture);
            String gate = celtium$join(gateFailures);
            report.addProperty("dimensionGate", gate);
            report.addProperty("dimensionGateWaitedTicks", Integer.valueOf(celtium$dimensionWaitTicks));
            report.addProperty("dimensionTrace", celtium$dimensionTrace.toString().trim());
            if (!gateFailures.isEmpty()) {
                String message = "dimension target " + celtium$dimension + " was not a real scene at tick " + worldTicks
                        + ": " + gate;
                // The transfer is asynchronous and the exact frame at which the new world exists is not known in advance,
                // so the capture waits - bounded - for the acceptance's own preconditions (the target dimension, frames
                // of it, no screen over them). A client that has left the target dimension again is not waited for: that
                // is the failure this gate exists to report, and it is reported immediately.
                if (celtium$leftTargetAtTick < 0 && mc.player != null && mc.player.dimension == celtium$dimension
                        && worldTicks < celtium$dimensionWaitUntilTick) {
                    if (celtium$dimensionWaitTicks == 0) {
                        celtium$dimensionTrace("CELTIUM_DIM_GATE_WAIT tick=" + worldTicks + " " + gate);
                    }
                    celtium$dimensionWaitTicks++;
                    return;
                }
                report.addProperty("dimensionGateFailed", message);
                System.out.println("[Celtium] CELTIUM_DIM_GATE FAILED: " + message);
                fail(mc, new IllegalStateException(message));
                return;
            }
        }
        if (!celtium$captureStarted) {
            celtium$captureStarted = true;
            celtium$captureStartTick = worldTicks;
            report.addProperty("captureStartTick", Integer.valueOf(worldTicks));
        } else if (celtium$worldStageTaken) {
            // The world stage of this scene is already captured. The capture body used to run again on the next frame -
            // which, with the verdict deferred one frame under GL-less, meant a second screenshot and a second dump
            // request to the same path, deleting the very file the verdict was about to judge. A scene with a GUI stage
            // reaches here too: its remaining work is the screen, which frame() drives.
            return;
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
                report.addProperty("presentedImageMeanAtCapture",
                        Double.valueOf(celtium$presentedImageMean()));
                report.addProperty("presentedImageFramesAtCapture",
                        Integer.valueOf(celtium$presentedFrameCount()));
                // The picture this scene's acceptance is about, with the identity of the frame it came from. The
                // world stage is taken here, inside the frame the capture is about; the verdict reads it (and, for the
                // video settings scene, the screenshot taken with the screen open) rather than "the file at a path".
                celtium$takeWorldStageEvidence(mc);
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
            if (worldFrames < Integer.getInteger("celtium.validation.minWorldFrames", 100).intValue() || !report.has("framebuffer")) {
                // The count is in the message on purpose: the threshold is the acceptance, and a run that misses it must say
                // by how much rather than leaving the next attempt to guess whether it was close or nowhere near.
                throw new IllegalStateException("Too few world rendering frames for startup validation: worldFrames="
                        + worldFrames + " (threshold " + Integer.getInteger("celtium.validation.minWorldFrames", 100).intValue()
                        + "), worldTicks=" + worldTicks);
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
                celtium$logFrameSample(mc);
                // The game's own screenshot, at the game's own path: the fixture's convention, the harness's evidence
                // that a picture was written, and - in a GL-bridge run, where Vulkan presents nothing - the scene's
                // own picture. Its identity is written next to it here, synchronously, so it names this frame. A scene
                // with a GUI stage writes the world frame to its own file, because celtium-validation.png is that
                // scene's subject: the screen, captured later.
                celtium$captureGlScreenshot(mc, celtium$scene.guiStage ? "celtium-validation-world.png"
                        : "celtium-validation.png", "world");
            }
            // The verdict carries the picture assertions. A run whose picture of the window is flat, mostly black, or
            // not a frame this run's renderer counted must not report PASSED: that is how a black band and missing
            // terrain passed once already. Under GL-less, the frame the capture's dump request asked for is written by
            // the present that follows this render pass, so the verdict waits one frame for it; in a GL-bridge run the
            // screenshot above is synchronous and the verdict is taken here.
            celtium$worldStageTaken = true;
            celtium$captureWorldFrames = worldFrames;
            report.addProperty("worldFramesAtCapture", Integer.valueOf(worldFrames));
            report.addProperty("firstPresentedWorldFrame", Integer.valueOf(celtium$firstPresentedWorldFrame));
            if (celtium$scene.guiStage) {
                // The world frame is the video settings scene's reference; the acceptance is the screen. The screen is
                // opened on the next client tick rather than here: this handler runs inside the world render pass, and
                // a screen that appears mid-pass would be half of this frame as well as the next.
                celtium$stage = 1;
                celtium$guiOpenPending = true;
                report.addProperty("guiStageStartedAtTick", Integer.valueOf(worldTicks));
                report.addProperty("guiStageStartedAtFrames", Integer.valueOf(worldFrames));
                return;
            }
            if (celtium$glLessInstalled() && !Boolean.getBoolean("celtium.validation.presentedImageAtCapture")) {
                // The screenshot and the report properties are taken; only the picture verdict is left, and it is
                // taken on the first frame whose requested dump has been written and observed.
                celtium$presentedVerdictPendingAtTick = worldTicks;
                celtium$presentedVerdictPendingFrames = worldFrames;
                report.addProperty("presentedVerdictDeferredAtTick", Integer.valueOf(worldTicks));
                return;
            }
            celtium$judgePictures(mc, "at the capture");
            celtium$setStatusFromPictures();
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

    /**
     * Whether the GL-less stub is answering GL calls in this process.
     * <p>
     * The renderer's classes are not on the classpath in every configuration this fixture runs in - the vanilla reference
     * runs it with no terrain mod at all, which is why the backend lookups above swallow linkage errors - so a missing
     * class has to answer false and leave the GL-bridge path exactly as it was, rather than ending the capture.
     */
    private static boolean celtium$glLessInstalled() {
        try {
            return org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled();
        } catch (RuntimeException | LinkageError absent) {
            return false;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Dimension transfer: the destination placement, the trace, and the capture's dimension gate.
    // ---------------------------------------------------------------------------------------------------------------

    /** One bounded trace line: to stdout, and into the report so the finding survives even when the log does not. */
    private synchronized void celtium$dimensionTrace(String line) {
        if (celtium$dimTraceLines >= DIM_TRACE_MAX_LINES) {
            if (celtium$dimTraceLines == DIM_TRACE_MAX_LINES) {
                celtium$dimTraceLines++;
                celtium$dimensionTrace.append("... trace budget of ").append(DIM_TRACE_MAX_LINES)
                        .append(" lines reached; further lines omitted\n");
                celtium$dimensionTraceText = celtium$dimensionTrace.toString();
                System.out.println("[Celtium] CELTIUM_DIM_TRACE budget of " + DIM_TRACE_MAX_LINES + " lines reached");
            }
            return;
        }
        celtium$dimTraceLines++;
        celtium$dimensionTrace.append(line).append('\n');
        celtium$dimensionTraceText = celtium$dimensionTrace.toString();
        System.out.println("[Celtium] " + line);
    }

    /** The open screen's simple class name, or "none". */
    private static String celtium$screenName(Minecraft mc) {
        return mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName();
    }

    /**
     * The client's own "terrain finished downloading" flag, read reflectively.
     * <p>
     * {@code NetHandlerPlayClient.doneLoadingTerrain} is set false by the server's Respawn packet and true when the
     * first chunk arrives, which is what closes the download-terrain screen. The screen's class name is already in the
     * trace; this separates "the chunks have arrived and only the screen's own frame is left" from "the chunks have not
     * arrived yet", which the screen alone cannot.
     */
    private static String celtium$doneLoadingTerrain(Minecraft mc) {
        try {
            Object handler = mc.getConnection();
            if (handler == null) {
                return "no-connection";
            }
            Field field = findField(handler.getClass(), new String[] {"doneLoadingTerrain", "field_147309_h"});
            return field == null ? "unresolved" : String.valueOf(field.getBoolean(handler));
        } catch (Throwable error) {
            return "unreadable: " + error.getClass().getSimpleName();
        }
    }

    /**
     * Whether the entity's own bounding box overlaps a portal block.
     * <p>
     * This is the test the game's collision pass makes before it marks an entity as being in a portal, and it is the
     * state {@code Entity.onEntityUpdate} reads when it sends an entity from the Nether back to the Overworld. The block
     * at the entity's feet alone is not it: a player standing on a portal's bottom block with their body inside one is
     * in a portal as far as the game is concerned.
     */
    private static boolean celtium$overlapsPortal(net.minecraft.world.World world, net.minecraft.entity.Entity entity) {
        if (world == null || entity == null) {
            return false;
        }
        net.minecraft.util.math.AxisAlignedBB box = entity.getEntityBoundingBox().grow(-0.001D, -0.001D, -0.001D);
        int minX = net.minecraft.util.math.MathHelper.floor(box.minX);
        int maxX = net.minecraft.util.math.MathHelper.floor(box.maxX);
        int minY = net.minecraft.util.math.MathHelper.floor(box.minY);
        int maxY = net.minecraft.util.math.MathHelper.floor(box.maxY);
        int minZ = net.minecraft.util.math.MathHelper.floor(box.minZ);
        int maxZ = net.minecraft.util.math.MathHelper.floor(box.maxZ);
        // A player's box covers at most 2x3x2 blocks; the clamp is there so a broken box cannot turn a trace line into
        // a scan of the world.
        if (maxX - minX > 3) maxX = minX + 3;
        if (maxY - minY > 5) maxY = minY + 5;
        if (maxZ - minZ > 3) maxZ = minZ + 3;
        net.minecraft.util.math.BlockPos.MutableBlockPos cursor = new net.minecraft.util.math.BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (world.getBlockState(cursor.setPos(x, y, z)).getBlock() == net.minecraft.init.Blocks.PORTAL) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Whether a position can hold a standing player: no portal, no solid block at the feet or head, no lava. */
    private static boolean celtium$passableSpot(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos) {
        net.minecraft.block.state.IBlockState feet = world.getBlockState(pos);
        net.minecraft.block.state.IBlockState head = world.getBlockState(pos.up());
        if (feet.getBlock() == net.minecraft.init.Blocks.PORTAL || head.getBlock() == net.minecraft.init.Blocks.PORTAL) {
            return false;
        }
        if (feet.getMaterial().isSolid() || head.getMaterial().isSolid()) {
            return false;
        }
        return feet.getMaterial() != net.minecraft.block.material.Material.LAVA
                && head.getMaterial() != net.minecraft.block.material.Material.LAVA;
    }

    /**
     * Places the arriving player beside the destination portal instead of inside it, and records what it chose.
     * <p>
     * The rule this exists for is vanilla: {@code Entity.onEntityUpdate} teleports any entity whose bounding box is
     * inside a portal block to dimension 0 when its dimension is -1, after {@code getMaxInPortalTime()} ticks of
     * contact - one, for a player who cannot take damage, as in this creative world. A fixture-driven transfer that
     * leaves the player standing in the portal it arrived in therefore ends with the player back in the Overworld.
     * <p>
     * The vanilla placement runs first, so the arrival is still found or built by the game exactly as a real trip makes
     * it; this then walks a bounded, deterministic list of nearby spots and takes the first one that is neither a portal
     * block nor inside solid terrain, preferring one with ground under it. The arrival block, its axis, the offset taken
     * and the destination block are reported either way, so a placement the fixture guessed is visible in the report
     * rather than implied by the player's later position.
     */
    private void celtium$settleArrival(net.minecraft.world.WorldServer world, net.minecraft.entity.Entity entity,
            float rotationYaw) {
        int arrivalX = net.minecraft.util.math.MathHelper.floor(entity.posX);
        int arrivalY = net.minecraft.util.math.MathHelper.floor(entity.posY);
        int arrivalZ = net.minecraft.util.math.MathHelper.floor(entity.posZ);
        net.minecraft.util.math.BlockPos arrival = new net.minecraft.util.math.BlockPos(arrivalX, arrivalY, arrivalZ);
        net.minecraft.block.state.IBlockState arrivalState = world.getBlockState(arrival);
        boolean arrivalIsPortal = arrivalState.getBlock() == net.minecraft.init.Blocks.PORTAL;
        String axis = "none";
        if (arrivalIsPortal) {
            try {
                axis = String.valueOf(arrivalState.getValue(net.minecraft.block.BlockPortal.AXIS));
            } catch (RuntimeException error) {
                axis = "unreadable";
            }
        }
        // A portal is one block thick perpendicular to its plane, so one step leaves it. AXIS=X means the plane runs
        // along X and the thickness - the way out - is Z; AXIS=Z means the thickness is X. When the arrival block is not
        // a portal, all four horizontal directions are candidates.
        int[][] perpendiculars;
        if (arrivalIsPortal && "X".equals(axis)) {
            perpendiculars = new int[][] {{0, 0, 1}, {0, 0, -1}};
        } else if (arrivalIsPortal) {
            perpendiculars = new int[][] {{1, 0, 0}, {-1, 0, 0}};
        } else {
            perpendiculars = new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
        }
        java.util.List<int[]> offsets = new java.util.ArrayList<int[]>();
        for (int[] direction : perpendiculars) {
            offsets.add(new int[] {direction[0], 0, direction[2]});
            offsets.add(new int[] {direction[0] * 2, 0, direction[2] * 2});
            offsets.add(new int[] {direction[0] * 3, 0, direction[2] * 3});
            offsets.add(new int[] {direction[0], 1, direction[2]});
            offsets.add(new int[] {direction[0] * 2, 1, direction[2] * 2});
        }
        offsets.add(new int[] {0, 1, 0});
        offsets.add(new int[] {0, 2, 0});
        offsets.add(new int[] {0, 3, 0});
        int[] chosen = null;
        int[] fallback = null;
        boolean hasGround = false;
        for (int[] offset : offsets) {
            net.minecraft.util.math.BlockPos candidate = arrival.add(offset[0], offset[1], offset[2]);
            if (!celtium$passableSpot(world, candidate)) {
                continue;
            }
            if (world.getBlockState(candidate.down()).getMaterial().isSolid()) {
                chosen = offset;
                hasGround = true;
                break;
            }
            if (fallback == null) {
                fallback = offset;
            }
        }
        if (chosen == null) {
            chosen = fallback;
        }
        boolean safe = chosen != null;
        if (safe) {
            net.minecraft.util.math.BlockPos destination = arrival.add(chosen[0], chosen[1], chosen[2]);
            entity.setLocationAndAngles(destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D,
                    rotationYaw, entity.rotationPitch);
            entity.motionX = 0.0D;
            entity.motionY = 0.0D;
            entity.motionZ = 0.0D;
            celtium$safeX = destination.getX() + 0.5D;
            celtium$safeY = destination.getY();
            celtium$safeZ = destination.getZ() + 0.5D;
            celtium$safeSpotKnown = true;
        }
        net.minecraft.util.math.BlockPos placed = new net.minecraft.util.math.BlockPos(
                net.minecraft.util.math.MathHelper.floor(entity.posX), net.minecraft.util.math.MathHelper.floor(entity.posY),
                net.minecraft.util.math.MathHelper.floor(entity.posZ));
        celtium$arrivalPlacement = "arrival=" + arrivalX + "," + arrivalY + "," + arrivalZ
                + " arrivalBlock=" + String.valueOf(arrivalState.getBlock().getRegistryName())
                + " arrivalIsPortal=" + arrivalIsPortal + " axis=" + axis
                + " step=" + (safe ? chosen[0] + "," + chosen[1] + "," + chosen[2] : "none")
                + " placed=" + placed.getX() + "," + placed.getY() + "," + placed.getZ()
                + " placedBlock=" + String.valueOf(world.getBlockState(placed).getBlock().getRegistryName())
                + " placedInPortal=" + celtium$overlapsPortal(world, entity)
                + " ground=" + hasGround + " safe=" + safe
                + " candidates=" + offsets.size();
        celtium$dimensionTrace("CELTIUM_DIM_PLACEMENT tick=" + worldTicks + " dim=" + entity.dimension + " "
                + celtium$arrivalPlacement + " timeUntilPortal=" + entity.timeUntilPortal);
    }

    /** The renderer's presented-frame counter, or -1 when the live terrain backend is not the Vulkan one. */
    private static int celtium$presentedFrameCount() {
        try {
            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend current =
                    org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            if (current instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) {
                return ((org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) current).celtium$presentedFrameCount();
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A measurement must never stop the tick; -1 says it could not be taken.
        }
        return -1;
    }

    /** Every reason the capture tick cannot be accepted as a frame of the target dimension, with its numbers. */
    private java.util.List<String> celtium$dimensionGateFailures(Minecraft mc, int serverDimensionAtCapture,
            int presentedAtCapture) {
        java.util.List<String> failures = new java.util.ArrayList<String>();
        if (celtium$arrivedAtTick < 0) {
            failures.add("the client never arrived in dimension " + celtium$dimension + " (arrivedAtTick=-1, server result '"
                    + celtium$transfer.state + "')");
        }
        if (mc.player == null) {
            failures.add("there is no client player");
        } else if (mc.player.dimension != celtium$dimension) {
            failures.add("the client player's dimension is " + mc.player.dimension + ", not " + celtium$dimension
                    + (celtium$leftTargetAtTick >= 0 ? " (left at tick " + celtium$leftTargetAtTick + ": "
                            + celtium$leftTargetEvent + ")" : ""));
        }
        if (mc.world == null) {
            failures.add("there is no client world");
        } else if (mc.world.provider.getDimension() != celtium$dimension) {
            failures.add("the client world's provider dimension is " + mc.world.provider.getDimension() + ", not "
                    + celtium$dimension);
        }
        if (serverDimensionAtCapture != celtium$dimension) {
            failures.add("the server has this player in dimension " + serverDimensionAtCapture + ", not " + celtium$dimension);
        }
        if (celtium$framesInTargetDimension < celtium$minDimensionFrames) {
            failures.add("world frames drawn in dimension " + celtium$dimension + " with no screen over them: "
                    + celtium$framesInTargetDimension + " (minimum " + celtium$minDimensionFrames
                    + ", worldFrames at capture " + worldFrames + ")");
        }
        if (mc.currentScreen != null) {
            failures.add("a screen is open over the frame: " + mc.currentScreen.getClass().getName()
                    + " (a capture behind the download-terrain screen is a loading frame, not the new dimension)");
        }
        if (mc.world != null && mc.player != null && celtium$overlapsPortal(mc.world, mc.player)) {
            failures.add("the client player is inside a portal block, so the frame is the portal overlay and the game"
                    + " is about to transfer them away again");
        }
        if (celtium$presentedFramesAtArrival >= 0 && presentedAtCapture >= 0
                && presentedAtCapture <= celtium$presentedFramesAtArrival) {
            failures.add("the renderer presented no frame after the arrival in dimension " + celtium$dimension
                    + " (presented frame counter " + celtium$presentedFramesAtArrival + " at the arrival, "
                    + presentedAtCapture + " at the capture)");
        }
        return failures;
    }

    /** The failures as one line, so a report can carry them without a nested object. */
    private static String celtium$join(java.util.List<String> values) {
        if (values.isEmpty()) {
            return "none";
        }
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                line.append(" | ");
            }
            line.append(values.get(i));
        }
        return line.toString();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Picture evidence: the request, the identity that names the frame, and the scene's verdict.
    //
    // The shape of the claim this section makes, and why it is not "more than 16 colours":
    //
    //   * The scene has a fixed camera pose, so two runs of it are pictures of the same thing.
    //   * The picture is requested at a path unique to this run, scene and stage, and the request records the frame
    //     counters at the moment it was made.
    //   * The write is *observed*: when the file appears, the renderer's presented-frame counter must have advanced by
    //     exactly one from the request, which is what proves which present wrote it. The observation, the world tick,
    //     the extent and a hash of the pixels are written to a sidecar identity file next to the image.
    //   * The verdict reads that identity back from disk, refuses to judge an image whose identity does not match the
    //     capture it is asserting about, and refuses an image whose pixels no longer hash to what the identity names.
    //   * The judgement itself is the scene's gate: measured floors from a real GL-bridge reference run of the same
    //     scene at the same pose, named in the report beside the number they gate.
    // ---------------------------------------------------------------------------------------------------------------

    /** The picture source this configuration has, decided by whether the GL-less stub is answering GL calls. */
    private static String celtium$evidenceSource() {
        return celtium$glLessInstalled() ? SOURCE_PRESENTED_DUMP : SOURCE_GL_SCREENSHOT;
    }

    /** A per-run, per-stage path: no two requests share a file, which is what makes an identity meaningful. */
    private java.nio.file.Path celtium$picturePath(String stage, String kind) {
        return Minecraft.getMinecraft().gameDir.toPath()
                .resolve("screenshots/celtium-" + kind + "-" + stage + "-" + celtium$runId + ".png");
    }

    /** A path as the report writes it: relative to the game directory when it is inside it, with forward slashes. */
    private static String celtium$relative(java.nio.file.Path path) {
        java.nio.file.Path game = Minecraft.getMinecraft().gameDir.toPath().toAbsolutePath();
        java.nio.file.Path absolute = path.toAbsolutePath();
        String text = absolute.startsWith(game) ? game.relativize(absolute).toString() : absolute.toString();
        return text.replace(java.io.File.separatorChar, '/');
    }

    /** The live Vulkan terrain backend, or null when this run's terrain is drawn some other way. */
    private static org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend celtium$vulkanBackend() {
        try {
            org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend current =
                    org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
            if (current instanceof org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) {
                return (org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend) current;
            }
        } catch (RuntimeException | LinkageError absent) {
            // The renderer's classes are not on the classpath in every configuration this fixture runs in, and a
            // missing class has to answer "no Vulkan backend" rather than end the capture.
        }
        return null;
    }

    private static double celtium$presentedImageMean() {
        org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend backend = celtium$vulkanBackend();
        return backend == null ? -1.0D : backend.celtium$presentedImageMean();
    }

    /**
     * Asks the renderer for the frame this capture is about, at a path nothing else will write.
     * <p>
     * The identity this records is the *request*: the world tick the frame's content belongs to, the fixture's own
     * world-frame count, and the renderer's presented-frame counter before the present that will write it. The
     * completion - which present actually wrote the file - is observed in {@link #celtium$completePendingDumps()}.
     */
    private PictureEvidence celtium$requestPresentedDump(Minecraft mc, String stage) {
        java.nio.file.Path image = celtium$picturePath(stage, "presented");
        PictureEvidence evidence = new PictureEvidence(stage, SOURCE_PRESENTED_DUMP, image, worldTicks, worldFrames,
                celtium$presentedFrameCount(), mc.displayWidth, mc.displayHeight, BINDING_OBSERVED_PRESENT);
        try {
            // A stale file would make "the image appeared" mean nothing. The path is per-run, but re-running the same
            // command with a pinned run id in the same game directory is exactly what the harness does, so both files
            // are cleared before the request.
            java.nio.file.Files.deleteIfExists(image);
            java.nio.file.Files.deleteIfExists(evidence.identity);
        } catch (java.io.IOException error) {
            evidence.completionError = "a stale file at " + celtium$relative(image) + " could not be cleared: " + error;
        }
        boolean requested = false;
        try {
            org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend backend = celtium$vulkanBackend();
            if (backend != null) {
                requested = backend.celtium$dumpPresentedImage(image);
            }
        } catch (RuntimeException | LinkageError error) {
            evidence.completionError = "the presented-image dump could not be requested: " + error;
        }
        if (requested) {
            celtium$pendingDumps.add(evidence);
        } else if ("none".equals(evidence.completionError)) {
            evidence.completionError = "the live terrain backend is not the Vulkan one, so no presented image can be"
                    + " dumped for this capture";
        }
        report.addProperty("pictureRequested_" + stage, celtium$relative(image) + " at tick " + evidence.requestedAtTick
                + " after " + evidence.presentedFramesAtRequest + " presented frames, world frame "
                + evidence.worldFramesAtRequest + " (" + evidence.requestedWidth + "x" + evidence.requestedHeight + ")");
        return evidence;
    }

    /**
     * Writes the game's own screenshot and, in a configuration where that screenshot is the picture, its identity.
     * <p>
     * Under the GL-less stub the game's screenshot reads a GL default framebuffer that does not exist, so it is one
     * black colour by construction. It is still written - it is the fixture's and the harness's convention for "a
     * screenshot was saved" - and the report says what it is worth, so no field reads as picture evidence without
     * being one. The GL-bridge run is the opposite case: Vulkan presents nothing there, the screenshot is the window,
     * and it is written synchronously by this call, which is what binds it to this frame.
     */
    private void celtium$captureGlScreenshot(Minecraft mc, String fileName, String stage) {
        try {
            ScreenShotHelper.saveScreenshot(mc.gameDir, fileName, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        } catch (Throwable error) {
            report.addProperty("pictureScreenshotError_" + stage, error.toString());
            return;
        }
        report.addProperty("pictureScreenshot_" + stage, "screenshots/" + fileName);
        if (celtium$glLessInstalled()) {
            report.addProperty("pictureScreenshotUsable_" + stage, Boolean.FALSE);
            report.addProperty("pictureScreenshotNote_" + stage, "black by construction under the GL-less stub: there"
                    + " is no GL default framebuffer to read, so the presented dump is this configuration's picture");
            return;
        }
        report.addProperty("pictureScreenshotUsable_" + stage, Boolean.TRUE);
        PictureEvidence evidence = new PictureEvidence(stage, SOURCE_GL_SCREENSHOT,
                mc.gameDir.toPath().resolve("screenshots").resolve(fileName), worldTicks, worldFrames,
                celtium$presentedFrameCount(), mc.displayWidth, mc.displayHeight, BINDING_WRITE_AT_CAPTURE);
        // Written by the call above, on this thread, inside the capture, so the frame is named by construction rather
        // than by a present observed. The renderer's presented-frame counter is meaningless here - a GL-bridge run
        // presents nothing through Vulkan, and the counter it keeps belongs to another path entirely (measured: 86
        // against 110 world frames in the reference run) - so it is recorded as not applicable rather than compared.
        evidence.presentedFrame = -1;
        evidence.worldTick = worldTicks;
        evidence.worldFrames = worldFrames;
        evidence.completedAtTick = worldTicks;
        celtium$readAndHash(evidence);
        celtium$writeIdentity(evidence);
        celtium$evidence.put(stage, evidence);
        System.out.println("[Celtium] CELTIUM_PICTURE_IDENTITY stage=" + stage + " source=" + SOURCE_GL_SCREENSHOT
                + " file=" + celtium$relative(evidence.image) + " presentedFrame=" + evidence.presentedFrame
                + " worldTick=" + evidence.worldTick + " worldFrames=" + evidence.worldFrames + " " + evidence.width
                + "x" + evidence.height + " hash=" + Integer.toHexString(evidence.imageHash)
                + " error=" + evidence.completionError);
    }

    /** Fills in the extent and pixel hash of an evidence record from the file on disk. */
    private void celtium$readAndHash(PictureEvidence evidence) {
        try {
            Picture picture = Picture.read(evidence.image);
            if (picture == null) {
                evidence.completionError = "the image at " + celtium$relative(evidence.image) + " could not be read";
                return;
            }
            evidence.width = picture.width;
            evidence.height = picture.height;
            evidence.imageHash = picture.hash();
            evidence.completed = true;
        } catch (Throwable error) {
            evidence.completionError = "the image at " + celtium$relative(evidence.image) + " could not be read: " + error;
        }
    }

    /**
     * Writes the identity sidecar: the fields the verdict checks an image against.
     * <p>
     * It is written even when the completion failed, and carries the failure text, so a run that could not name its
     * frame leaves the reason on disk next to the image instead of an unexplained refusal.
     */
    private void celtium$writeIdentity(PictureEvidence evidence) {
        JsonObject identity = new JsonObject();
        identity.addProperty("runId", celtium$runId);
        identity.addProperty("scene", celtium$scene.name);
        identity.addProperty("stage", evidence.stage);
        identity.addProperty("source", evidence.source);
        identity.addProperty("image", celtium$relative(evidence.image));
        identity.addProperty("requestedImage", celtium$relative(evidence.requestedImage));
        identity.addProperty("keptFrom", evidence.keptFrom);
        identity.addProperty("worldTick", evidence.worldTick);
        identity.addProperty("worldFrames", evidence.worldFrames);
        identity.addProperty("presentedFrame", evidence.presentedFrame);
        identity.addProperty("width", evidence.width);
        identity.addProperty("height", evidence.height);
        identity.addProperty("imageHash", evidence.imageHash);
        identity.addProperty("requestedAtTick", evidence.requestedAtTick);
        identity.addProperty("requestedWorldFrames", evidence.worldFramesAtRequest);
        identity.addProperty("requestedPresentedFrame", evidence.presentedFramesAtRequest);
        identity.addProperty("completedAtTick", evidence.completedAtTick);
        identity.addProperty("frameBinding", evidence.frameBinding);
        identity.addProperty("identitySource", SOURCE_GL_SCREENSHOT.equals(evidence.source)
                ? "fixture-write-at-capture" : "fixture-observed-completion");
        identity.addProperty("completionError", evidence.completionError == null ? "none" : evidence.completionError);
        identity.addProperty("writtenAtMillis", Long.valueOf(System.currentTimeMillis()));
        try {
            java.nio.file.Files.createDirectories(evidence.identity.getParent());
            java.nio.file.Files.write(evidence.identity,
                    (new GsonBuilder().setPrettyPrinting().create().toJson(identity) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            report.addProperty("pictureIdentityWriteError_" + evidence.stage, error.toString());
        }
        report.addProperty("pictureIdentity_" + evidence.stage, identity.toString());
    }

    /**
     * Observes the presented-image requests that have been written, and completes their identities.
     * <p>
     * This is where the frame becomes a fact rather than an inference. The request was made inside the capture's frame
     * and is consumed by the next present, so when the file appears the renderer's presented-frame counter must read
     * exactly one more than it did at the request. If it reads anything else, a second present intervened and the frame
     * the file holds cannot be named: the mismatch is recorded and the verdict refuses the picture instead of judging
     * whatever happens to be at that path.
     */
    private void celtium$completePendingDumps() {
        if (celtium$pendingDumps.isEmpty()) {
            return;
        }
        java.util.Iterator<PictureEvidence> iterator = celtium$pendingDumps.iterator();
        while (iterator.hasNext()) {
            PictureEvidence evidence = iterator.next();
            if (!java.nio.file.Files.isRegularFile(evidence.image)) {
                continue;
            }
            iterator.remove();
            int observed = celtium$presentedFrameCount();
            int expected = evidence.presentedFramesAtRequest + 1;
            evidence.completedAtTick = worldTicks;
            if (observed != expected) {
                evidence.completionError = "the image appeared while the renderer's presented-frame counter read "
                        + observed + ", not the " + expected + " this request expected, so the frame it holds cannot be"
                        + " named";
            } else {
                evidence.presentedFrame = observed;
                evidence.worldTick = evidence.requestedAtTick;
                evidence.worldFrames = evidence.worldFramesAtRequest;
            }
            celtium$readAndHash(evidence);
            if (observed != expected) {
                evidence.completed = false;
            } else {
                // The file the renderer wrote is copied before the identity is written, because the renderer keeps
                // rewriting the last path it was given (see PictureEvidence.requestedImage). The identity then names a
                // file nothing else writes, so "the image is not the one its identity names" can only mean a real
                // problem rather than the renderer's own refresh.
                celtium$keepCompletedCopy(evidence);
            }
            celtium$writeIdentity(evidence);
            celtium$evidence.put(evidence.stage, evidence);
            System.out.println("[Celtium] CELTIUM_PICTURE_IDENTITY stage=" + evidence.stage + " source="
                    + SOURCE_PRESENTED_DUMP + " file=" + celtium$relative(evidence.image) + " requested="
                    + celtium$relative(evidence.requestedImage) + " keptFrom=" + evidence.keptFrom
                    + " presentedFrame=" + evidence.presentedFrame + " expected=" + expected + " worldTick="
                    + evidence.worldTick + " worldFrames=" + evidence.worldFrames + " " + evidence.width + "x"
                    + evidence.height + " hash=" + Integer.toHexString(evidence.imageHash) + " error="
                    + evidence.completionError);
        }
    }

    /**
     * Copies a just-completed dump to a path the renderer will never write again, and makes that copy the evidence.
     * <p>
     * The renderer re-requests a dump to the last path the fixture asked for every fortieth presented frame, so the
     * requested path stops being the captured frame as soon as the next refresh lands (measured in
     * {@code .celtium-work/runtime/unfreeze}: the early dump's identity hash -457084848, the file rewritten 6.7 s later
     * and then byte-identical to the world dump at 1564670080). Copying the file the moment its frame is named is what
     * keeps "the picture this verdict is about" a file that still exists, and the copy is re-hashed so the identity and
     * the bytes it names are checked against each other rather than assumed.
     */
    private void celtium$keepCompletedCopy(PictureEvidence evidence) {
        if (!evidence.completed) {
            return;
        }
        try {
            String name = evidence.requestedImage.getFileName().toString();
            String keptName = name.endsWith(".png")
                    ? name.substring(0, name.length() - 4) + "-kept.png" : name + "-kept.png";
            java.nio.file.Path kept = evidence.requestedImage.resolveSibling(keptName);
            java.nio.file.Files.copy(evidence.requestedImage, kept,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Picture copy = Picture.read(kept);
            if (copy == null) {
                evidence.completionError = "the kept copy at " + celtium$relative(kept) + " has no pixels";
                return;
            }
            if (copy.hash() != evidence.imageHash) {
                evidence.completionError = "the kept copy at " + celtium$relative(kept) + " hashes to 0x"
                        + Integer.toHexString(copy.hash()) + ", not the 0x" + Integer.toHexString(evidence.imageHash)
                        + " the requested file was read as: the renderer rewrote the requested path while it was being"
                        + " copied";
                return;
            }
            evidence.image = kept;
            evidence.identity = kept.resolveSibling(kept.getFileName().toString() + IDENTITY_SUFFIX);
            evidence.keptFrom = celtium$relative(evidence.requestedImage);
            report.addProperty("pictureKept_" + evidence.stage, celtium$relative(kept) + " (copy of "
                    + evidence.keptFrom + ", hash 0x" + Integer.toHexString(evidence.imageHash)
                    + ", taken when the frame was named; the renderer's every-40th-frame refresh rewrites the requested"
                    + " path afterwards)");
        } catch (Throwable error) {
            evidence.completionError = "the completed picture at " + celtium$relative(evidence.requestedImage)
                    + " could not be copied to a path the renderer will not rewrite: " + error;
        }
    }

    /**
     * Whether every picture the scene's verdict needs has been written and its identity completed.
     * <p>
     * The deferred verdict waits on this rather than on a tick count: the present that writes the picture is the last
     * thing in the loop iteration, so the frame after the capture is the first one whose file exists, and a run that
     * is merely slow (ticks catching up in bursts) must not be judged before its evidence is there.
     */
    private boolean celtium$pendingVerdictEvidenceComplete() {
        PictureEvidence world = celtium$evidence.get("world");
        if (world == null || !world.completed) {
            return false;
        }
        if (!celtium$scene.guiStage) {
            return true;
        }
        PictureEvidence gui = celtium$evidence.get("gui");
        return gui != null && gui.completed;
    }

    /** The world stage's picture: the presented dump under GL-less, the game's screenshot in a GL-bridge run. */
    private void celtium$takeWorldStageEvidence(Minecraft mc) {
        if (!Boolean.getBoolean("celtium.validation.dumpPresentedImage")) {
            if (celtium$glLessInstalled()) {
                report.addProperty("pictureEvidenceProblem", "GL-less without -Dceltium.vulkan.presentReadback and"
                        + " -Dceltium.validation.dumpPresentedImage: the game's own screenshot is black by construction"
                        + " and no presented image was requested, so this run has no picture of the window at all");
            }
            return;
        }
        celtium$requestPresentedDump(mc, "world");
        report.addProperty("presentedDumpRequestedAtCapture", Boolean.TRUE);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The camera pose: set once on the server through the game's own teleport, held on the client every tick.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Moves the server's player to the scene's pose, through the call the game's own {@code /tp} uses.
     * <p>
     * A client-side {@code setPositionAndRotation} alone does not survive: the server's movement check reads a step of
     * hundreds of blocks as "moved too quickly" and sends the client back to the position the server had, which is why
     * the fixture's camera was wherever the run's drift and the inherited save had left the player. Moving the server's
     * own player first - and its flying state with it - means the position the client then reports is the one the
     * server already has.
     */
    private String celtium$teleportPoseOnServer(final Minecraft mc) {
        final net.minecraft.server.integrated.IntegratedServer server = mc.getIntegratedServer();
        if (server == null || mc.player == null) {
            return "no integrated server";
        }
        final java.util.UUID playerId = mc.player.getUniqueID();
        final int tick = worldTicks;
        try {
            server.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    try {
                        net.minecraft.entity.player.EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
                        if (player == null) {
                            celtium$poseResult = "no server player for " + playerId;
                            return;
                        }
                        player.capabilities.allowFlying = true;
                        player.capabilities.isFlying = true;
                        player.sendPlayerAbilities();
                        player.rotationYaw = celtium$pose.yaw;
                        player.rotationPitch = celtium$pose.pitch;
                        player.prevRotationYaw = celtium$pose.yaw;
                        player.prevRotationPitch = celtium$pose.pitch;
                        // setPositionAndUpdate is EntityPlayerMP's own teleport: it sends the position-look packet the
                        // client applies, so both sides end up at the pose with no correction in flight.
                        player.setPositionAndUpdate(celtium$pose.x, celtium$pose.y, celtium$pose.z);
                        player.motionX = 0.0D;
                        player.motionY = 0.0D;
                        player.motionZ = 0.0D;
                        player.fallDistance = 0.0F;
                        celtium$poseResult = "server-side teleport at tick " + tick + " to " + celtium$pose.describe();
                    } catch (Throwable error) {
                        celtium$poseResult = "server-side teleport failed: " + error;
                    }
                }
            });
        } catch (Throwable error) {
            return "could not schedule the server-side teleport: " + error;
        }
        return "scheduled at tick " + tick + " to " + celtium$pose.describe();
    }

    /**
     * Holds the client's player at the scene's pose.
     * <p>
     * Both the position and the previous-position fields are set, because the renderer interpolates between them over
     * the partial tick: setting only the current position would leave the camera moving with the frame's phase, which
     * is the difference a pixel comparison between two runs would measure instead of the renderer.
     */
    private void celtium$applyPose(Minecraft mc) {
        if (mc.player == null) {
            return;
        }
        mc.player.capabilities.allowFlying = true;
        mc.player.capabilities.isFlying = true;
        celtium$setPoseOn(mc.player);
        // The picture is drawn from the renderer's *view entity*, which is not always the player entity the fixture's
        // camera gate reads: a dimension change replaces mc.player, and the renderer can keep the old entity, which the
        // server left at the arrival position. Measured: .celtium-work/runtime/scene-floors/nether-a rendered from
        // (1.34, 62.32, 1.29) - the Nether arrival - while mc.player read exactly the pinned pose (0.5, 12.0, 0.5), so
        // the frame was of another camera. The pose is therefore applied to both, and the renderer's own viewport is
        // gated at the capture (celtium$gateCamera) rather than assumed.
        net.minecraft.entity.Entity view = mc.getRenderViewEntity();
        if (view != null && view != mc.player) {
            celtium$poseViewEntity = view.getClass().getName();
            celtium$poseViewEntityTicks++;
            celtium$setPoseOn(view);
        }
    }

    /** Sets the scene's position and both rotations on an entity, through the game's own setter. */
    private void celtium$setPoseOn(net.minecraft.entity.Entity entity) {
        entity.motionX = 0.0D;
        entity.motionY = 0.0D;
        entity.motionZ = 0.0D;
        entity.fallDistance = 0.0F;
        entity.setPositionAndRotation(celtium$pose.x, celtium$pose.y, celtium$pose.z, celtium$pose.yaw, celtium$pose.pitch);
        // The head and body yaw fields are EntityLivingBase's, not Entity's: a view entity that is not a living entity
        // (a camera entity a mod installed) gets the position and the Entity-level rotations and nothing else.
        if (entity instanceof net.minecraft.entity.EntityLivingBase) {
            net.minecraft.entity.EntityLivingBase living = (net.minecraft.entity.EntityLivingBase) entity;
            living.rotationYawHead = celtium$pose.yaw;
            living.prevRotationYawHead = celtium$pose.yaw;
            living.renderYawOffset = celtium$pose.yaw;
            living.prevRenderYawOffset = celtium$pose.yaw;
        }
    }

    /**
     * The renderer's own camera at the capture, from the viewport line the world renderer publishes.
     * <p>
     * This is the camera the picture in the file was drawn from. {@code mc.player} being at the pose says the fixture
     * asked for the right camera; this says the renderer used it.
     */
    private double[] celtium$rendererViewport() {
        if (!report.has("rendererDebug") || !report.get("rendererDebug").isJsonArray()) {
            return null;
        }
        com.google.gson.JsonArray debug = report.getAsJsonArray("rendererDebug");
        for (int i = 0; i < debug.size(); i++) {
            String line = debug.get(i).getAsString();
            java.util.regex.Matcher match = java.util.regex.Pattern
                    .compile("Viewport: (-?[0-9.]+) (-?[0-9.]+) (-?[0-9.]+)").matcher(line);
            if (match.find()) {
                try {
                    return new double[] {Double.parseDouble(match.group(1)), Double.parseDouble(match.group(2)),
                            Double.parseDouble(match.group(3))};
                } catch (NumberFormatException unreadable) {
                    return null;
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The video settings stage: open the game's own options screen, press its button, let it lay out, capture it.
    // ---------------------------------------------------------------------------------------------------------------

    /** Opens Minecraft's own options screen - the call the pause menu's Options button makes. */
    private void celtium$openGuiScene(Minecraft mc) {
        celtium$guiScreenOpenedAtTick = worldTicks;
        celtium$guiScreenOpenedAtFrames = worldFrames;
        try {
            net.minecraft.client.gui.GuiOptions options =
                    new net.minecraft.client.gui.GuiOptions((net.minecraft.client.gui.GuiScreen) null, mc.gameSettings);
            mc.displayGuiScreen(options);
            celtium$guiScreenOpened = celtium$screenName(mc);
            celtium$guiScreenOpenedVia = "Minecraft.displayGuiScreen(new GuiOptions(null, gameSettings))";
            celtium$guiClickPending = true;
        } catch (Throwable error) {
            celtium$guiScreenOpened = "none";
            celtium$guiScreenOpenedVia = "failed: " + error;
        }
        report.addProperty("guiScreenOpened", celtium$guiScreenOpened);
        report.addProperty("guiScreenOpenedVia", celtium$guiScreenOpenedVia);
        report.addProperty("guiScreenOpenedAtTick", Integer.valueOf(worldTicks));
    }

    /**
     * Presses the options screen's Video Settings button through the screen's own click dispatch.
     * <p>
     * {@code GuiScreen.mouseClicked} is the code a real click runs: it finds the button under the point and calls the
     * screen's {@code actionPerformed}, whose handler for id 101 is
     * {@code gameSettings.saveOptions(); displayGuiScreen(new GuiVideoSettings(this, gameSettings))}. Dispatching there
     * rather than constructing the screen here is what makes this "the game's own code path" rather than a copy of it.
     * If the button cannot be found the same two calls are made directly, and the report says which path was taken.
     */
    private void celtium$clickVideoSettingsButton(Minecraft mc) {
        net.minecraft.client.gui.GuiScreen screen = mc.currentScreen;
        if (screen == null) {
            celtium$guiClickResult = "no screen is open to click in";
            report.addProperty("guiClickResult", celtium$guiClickResult);
            return;
        }
        try {
            Object buttonList = celtium$fieldValue(screen, new String[] {"buttonList", "field_146292_n"});
            java.util.List<?> buttons = buttonList instanceof java.util.List ? (java.util.List<?>) buttonList
                    : new java.util.ArrayList<Object>();
            Object target = null;
            String how = "none";
            for (Object candidate : buttons) {
                Object id = celtium$fieldValue(candidate, new String[] {"id", "field_146127_k"});
                if (id instanceof Integer && ((Integer) id).intValue() == 101) {
                    target = candidate;
                    how = "button id 101";
                    break;
                }
            }
            if (target == null) {
                String label = net.minecraft.client.resources.I18n.format("options.video", new Object[0]);
                for (Object candidate : buttons) {
                    Object text = celtium$fieldValue(candidate, new String[] {"displayString", "field_146126_j"});
                    if (label.equals(String.valueOf(text))) {
                        target = candidate;
                        how = "button labelled '" + label + "'";
                        break;
                    }
                }
            }
            if (target == null) {
                celtium$openVideoSettingsDirectly(mc, "no Video Settings button in " + buttons.size() + " options buttons");
                return;
            }
            int x = ((Number) celtium$fieldValue(target, new String[] {"x", "field_146128_h"})).intValue();
            int y = ((Number) celtium$fieldValue(target, new String[] {"y", "field_146129_i"})).intValue();
            int width = ((Number) celtium$fieldValue(target, new String[] {"width", "field_146120_f"})).intValue();
            int height = ((Number) celtium$fieldValue(target, new String[] {"height", "field_146121_g"})).intValue();
            java.lang.reflect.Method click = celtium$requireMethod(screen.getClass(),
                    new String[] {"mouseClicked", "func_73864_a"}, int.class, int.class, int.class);
            click.invoke(screen, Integer.valueOf(x + width / 2), Integer.valueOf(y + height / 2), Integer.valueOf(0));
            celtium$guiClickResult = "clicked " + how + " at " + (x + width / 2) + "," + (y + height / 2)
                    + " through GuiScreen.mouseClicked";
        } catch (Throwable error) {
            celtium$openVideoSettingsDirectly(mc, "the click dispatch failed: " + error);
            return;
        }
        report.addProperty("guiClickResult", celtium$guiClickResult);
    }

    /** The same two calls the game's own handler makes for id 101, recorded as their own provenance. */
    private void celtium$openVideoSettingsDirectly(Minecraft mc, String why) {
        try {
            mc.gameSettings.saveOptions();
            mc.displayGuiScreen(new net.minecraft.client.gui.GuiVideoSettings(mc.currentScreen, mc.gameSettings));
            celtium$guiScreenOpenedVia = why + "; opened with the two calls the game's own handler makes"
                    + " (gameSettings.saveOptions + displayGuiScreen(new GuiVideoSettings(parent, gameSettings)))";
            report.addProperty("guiVideoSettingsOpenedVia", celtium$guiScreenOpenedVia);
        } catch (Throwable error) {
            celtium$guiScreenOpenedVia = why + "; and the direct open failed too: " + error;
            report.addProperty("guiVideoSettingsOpenedVia", celtium$guiScreenOpenedVia);
        }
    }

    /**
     * Drives the video settings stage: click the button, wait for the screen and its layout, then capture it.
     * <p>
     * The screen is up when this runs - it is drawn before the render tick ends, so the picture taken here holds it.
     * The wait is for the screen's own layout (its list builds its content as it is scrolled and laid out), and it is
     * bounded: a screen that never becomes a {@code GuiVideoSettings} fails the run with the class it actually is
     * instead of waiting out the timeout.
     */
    private void celtium$advanceGuiStage(Minecraft mc) {
        if (celtium$guiClickPending && worldTicks >= celtium$guiScreenOpenedAtTick + 1) {
            celtium$guiClickPending = false;
            celtium$clickVideoSettingsButton(mc);
            // The button is the game's, but a mod can claim it: with Celeritas installed the click through
            // GuiScreen.mouseClicked on button id 101 opened org.taumc.celeritas.impl.gui.CeleritasVideoOptionsScreen
            // in the reference run, not GuiVideoSettings. What the scene asserts is Minecraft's own screen, so the
            // diversion is recorded and the screen this scene is about is then opened with the same two calls the
            // vanilla handler makes - it is the same screen the game would show without that mod in the way.
            if (mc.currentScreen != null && !"GuiVideoSettings".equals(celtium$screenName(mc))) {
                report.addProperty("guiClickDivertedTo", mc.currentScreen.getClass().getName());
                celtium$openVideoSettingsDirectly(mc, "the Video Settings button opened "
                        + mc.currentScreen.getClass().getName() + ", not Minecraft's own GuiVideoSettings");
                report.addProperty("guiScreenOpenedVia", celtium$guiScreenOpenedVia);
            }
        }
        String screenName = celtium$screenName(mc);
        report.addProperty("guiScreenCurrent", screenName);
        if (mc.currentScreen == null) {
            report.addProperty("guiScreenVerdict", "the screen was closed before its capture");
            fail(mc, new IllegalStateException("scene " + celtium$scene.name + ": the video settings screen was closed"
                    + " before it could be captured (opened via " + celtium$guiScreenOpenedVia + "; click result "
                    + celtium$guiClickResult + ")"));
            return;
        }
        if (!"GuiVideoSettings".equals(screenName)) {
            if (worldTicks - celtium$guiScreenOpenedAtTick > celtium$guiWaitTicks) {
                report.addProperty("guiScreenVerdict", "the screen never became GuiVideoSettings");
                fail(mc, new IllegalStateException("scene " + celtium$scene.name + ": the screen opened via "
                        + celtium$guiScreenOpenedVia + " (" + celtium$guiClickResult + ") is "
                        + mc.currentScreen.getClass().getName() + ", not GuiVideoSettings, after "
                        + (worldTicks - celtium$guiScreenOpenedAtTick) + " ticks"));
            }
            return;
        }
        if (celtium$guiSettingsAtTick < 0) {
            celtium$guiSettingsAtTick = worldTicks;
            celtium$guiSettingsAtFrames = worldFrames;
            report.addProperty("guiSettingsSeenAtTick", Integer.valueOf(worldTicks));
        }
        int layoutTicks = worldTicks - celtium$guiSettingsAtTick;
        int layoutFrames = worldFrames - celtium$guiSettingsAtFrames;
        if (layoutTicks < celtium$guiLayoutTicks || layoutFrames < celtium$guiMinFrames) {
            return;
        }
        celtium$stage = 2;
        report.addProperty("guiScreenVerdict", "GuiVideoSettings was open at the capture");
        report.addProperty("guiScreenClassAtCapture", mc.currentScreen.getClass().getName());
        report.addProperty("guiScreenTitleAtCapture", celtium$guiScreenTitle(mc));
        report.addProperty("guiCaptureTick", Integer.valueOf(worldTicks));
        report.addProperty("guiCaptureWorldFrames", Integer.valueOf(worldFrames));
        report.addProperty("guiLayoutTicks", Integer.valueOf(layoutTicks));
        report.addProperty("guiLayoutFrames", Integer.valueOf(layoutFrames));
        // The scene's own picture: in a GL-bridge run this screenshot is the window and is written synchronously here
        // (the screen is already drawn); under GL-less it is the black-by-construction file and the presented dump is
        // the picture.
        celtium$captureGlScreenshot(mc, "celtium-validation.png", "gui");
        if (Boolean.getBoolean("celtium.validation.dumpPresentedImage")) {
            celtium$requestPresentedDump(mc, "gui");
        }
        if (celtium$glLessInstalled()) {
            celtium$presentedVerdictPendingAtTick = worldTicks;
            celtium$presentedVerdictPendingFrames = worldFrames;
            report.addProperty("presentedVerdictDeferredAtTick", Integer.valueOf(worldTicks));
            return;
        }
        celtium$judgePictures(mc, "at the video settings capture");
        celtium$setStatusFromPictures();
        finish(mc);
    }

    /** The open screen's title text, read from its own field: the report says which screen it was, not only its class. */
    private static String celtium$guiScreenTitle(Minecraft mc) {
        try {
            Object title = celtium$fieldValue(mc.currentScreen, new String[] {"screenTitle", "field_146500_a", "title", "field_146503_f"});
            return title == null ? "unreadable" : String.valueOf(title);
        } catch (Throwable error) {
            return "unreadable: " + error;
        }
    }

    /**
     * The rectangle the open video settings screen's own list occupies, read from the live screen object.
     * <p>
     * This is what "the expected panel" means concretely: the screen knows where its list is, so the gate does not have
     * to guess a layout. {@code GuiVideoSettings.optionsRowList} is a {@code GuiListExtended} whose {@code GuiSlot} base
     * carries left/top/right/bottom, all public fields; the names are given in both the development and the SRG forms
     * because the client runs from a production jar.
     */
    private int[] celtium$panelRect(Minecraft mc) {
        if (mc.currentScreen == null) {
            return null;
        }
        try {
            Object list = celtium$fieldValue(mc.currentScreen, new String[] {"optionsRowList", "field_146501_h"});
            if (list == null) {
                return null;
            }
            int left = ((Number) celtium$fieldValue(list, new String[] {"left", "field_148152_e"})).intValue();
            int top = ((Number) celtium$fieldValue(list, new String[] {"top", "field_148153_b"})).intValue();
            int right = ((Number) celtium$fieldValue(list, new String[] {"right", "field_148151_d"})).intValue();
            int bottom = ((Number) celtium$fieldValue(list, new String[] {"bottom", "field_148154_c"})).intValue();
            if (right <= left || bottom <= top) {
                return null;
            }
            // The screen's own coordinates are GUI-scaled - the same space the buttons are laid out in - while the
            // picture is in framebuffer pixels. Taking the rectangle unscaled measured the top-left corner of the
            // frame instead of the panel: at the reference run's scale of 3 the list reads (0,32)..(427,208) and the
            // panel it describes is (0,96)..(1281,624). The scale factor is the game's own, from ScaledResolution.
            net.minecraft.client.gui.ScaledResolution resolution = new net.minecraft.client.gui.ScaledResolution(mc);
            int scale = Math.max(1, resolution.getScaleFactor());
            report.addProperty("guiListRectGuiSpace", left + "," + top + ".." + right + "," + bottom);
            report.addProperty("guiScaleFactor", Integer.valueOf(scale));
            int pixelLeft = left * scale;
            int pixelTop = top * scale;
            int pixelRight = Math.max(pixelLeft + 1, right * scale);
            int pixelBottom = Math.max(pixelTop + 1, bottom * scale);
            if (pixelRight <= pixelLeft || pixelBottom <= pixelTop) {
                return null;
            }
            return new int[] {Math.max(0, pixelLeft), Math.max(0, pixelTop), pixelRight, pixelBottom};
        } catch (Throwable error) {
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The verdict.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Judges the scene's pictures: identity first, then the scene's gate.
     * <p>
     * Taken once, on the frame whose evidence is complete, and never against a live counter: the frame assertion reads
     * the identity the dump's own write was observed under and the capture-time snapshot, which is what the Intel run
     * needed when its shutdown re-check compared a capture-time 150 against a live 152.
     * <p>
     * A scene with no measured reference is not a pass and not a failure: the picture is still held to the smoke test,
     * the identity, the frame binding and the camera pose, and the verdict says {@code UNCALIBRATED_PRESENTED_IMAGE}
     * with the reason in {@code floorsNotCalibrated}.
     */
    private void celtium$judgePictures(Minecraft mc, String when) {
        if (celtium$pictureVerdictTaken) {
            return;
        }
        celtium$pictureVerdictTaken = true;
        java.util.List<String> failures = new java.util.ArrayList<String>();
        report.addProperty("pictureVerdict", "judged " + when);
        // The scene's floors were measured at this pose, so a scene whose camera is not at the pose is not the scene
        // the floors describe: two runs of it are not comparable, and the gate says so with both sets of numbers.
        celtium$gateCamera(failures);
        report.addProperty("pictureEvidenceSource", celtium$evidenceSource());
        report.addProperty("sceneGateReference", celtium$gate.reference);
        report.addProperty("sceneGateReferenceRuns", celtium$gate.referenceRuns);
        report.addProperty("sceneGateReferenceNumbers", celtium$gate.referenceNumbers);
        report.addProperty("sceneGateMargin", celtium$gate.margin);
        report.addProperty("sceneGateCalibrated", Boolean.valueOf(celtium$gate.calibrated));
        boolean variantMeasured = celtium$variantMeasuredAtVerdict();
        celtium$gateEnforced = celtium$gate.calibrated && variantMeasured;
        report.addProperty("sceneGateFloorsEnforced", Boolean.valueOf(celtium$gateEnforced));
        report.addProperty("sceneVariant", celtium$sceneVariantDescription());
        report.addProperty("sceneCalibrationKey", celtium$runCalibrationKey());
        report.addProperty("sceneReferenceCalibrationKey", celtium$referenceCalibrationKey(celtium$scene));
        report.addProperty("sceneCalibrationKeyMatches", Boolean.valueOf(variantMeasured));
        report.addProperty("sceneGateFloors", (celtium$gate.calibrated ? "" : "NOT_CALIBRATED (smoke test only): ")
                + "distinctColours>" + celtium$gate.minDistinctColours
                + " nonBackgroundFraction>=" + celtium$gate.minNonBackgroundFraction
                + " verticalBands>=" + celtium$gate.minVerticalBands
                + " blackFraction<=" + celtium$gate.maxBlackFraction
                + " panelChangedFraction>=" + celtium$gate.minPanelChangedFraction
                + " panelStrongFraction>=" + celtium$gate.minPanelStrongFraction
                + " panelDistinctColours>=" + celtium$gate.minPanelDistinctColours
                + " panelRowAmplitude>=" + celtium$gate.minPanelRowAmplitude);
        if (!celtium$gateEnforced) {
            // Words, not a missing field: a reader of the report has to be able to tell "no reference yet" from "the
            // picture passed" without knowing this fixture's internals.
            report.addProperty("floorsNotCalibrated", "FLOORS NOT CALIBRATED for scene " + celtium$scene.name + " ("
                    + celtium$sceneDimension() + "): " + (celtium$gate.calibrated
                            ? "this scene has a measured reference, but this run is not the variant it describes ("
                                    + celtium$sceneVariantDescription() + "); the reference is " + celtium$gate.referenceRuns
                            : celtium$gate.referenceRuns)
                    + ". This run is judged by the smoke test (more than " + celtium$gate.minDistinctColours
                    + " distinct colours), the picture identity, the frame binding and the camera pose only, and its"
                    + " verdict is UNCALIBRATED_PRESENTED_IMAGE - not PASSED, because no floor says the picture shows"
                    + " this scene, and not FAILED, because no floor measured for this scene says it does not."
                    + " Reference provenance: " + celtium$gate.reference);
        }
        if (!"none".equals(celtium$sceneError)) {
            failures.add("the run could not be attributed to a scene: " + celtium$sceneError);
        }
        int worldFramesAtCapture = report.has("worldFramesAtCapture")
                ? report.get("worldFramesAtCapture").getAsInt() : -1;
        PictureEvidence worldEvidence = celtium$evidence.get("world");
        Picture worldPicture = celtium$judgeStage("world", worldEvidence, worldFramesAtCapture, failures);
        Picture primary = worldPicture;
        if (celtium$scene.guiStage) {
            int guiFramesAtCapture = report.has("guiCaptureWorldFrames")
                    ? report.get("guiCaptureWorldFrames").getAsInt() : -1;
            PictureEvidence guiEvidence = celtium$evidence.get("gui");
            Picture guiPicture = celtium$judgeStage("gui", guiEvidence, guiFramesAtCapture, failures);
            primary = guiPicture;
            if (guiPicture != null) {
                String screenClass = report.has("guiScreenClassAtCapture")
                        ? report.get("guiScreenClassAtCapture").getAsString() : "none";
                if (!"net.minecraft.client.gui.GuiVideoSettings".equals(screenClass)
                        && !"GuiVideoSettings".equals(screenClass)) {
                    failures.add("scene " + celtium$scene.name + ": the screen at the capture was " + screenClass
                            + ", not GuiVideoSettings, so the picture is not the video settings screen (opened via "
                            + celtium$guiScreenOpenedVia + "; " + celtium$guiClickResult + ")");
                }
                if (worldPicture == null) {
                    failures.add("scene " + celtium$scene.name + ": the video settings gate needs the world frame of the"
                            + " same run as its reference and there is none to judge");
                } else {
                    failures.addAll(celtium$gateGuiPanel(worldPicture, guiPicture));
                }
            }
        }
        celtium$presentedImageFailures = failures.size();
        report.addProperty("presentedImageAssertionFailures", Integer.valueOf(failures.size()));
        report.addProperty("presentedImageAssertions", celtium$presentedImageAssertionMessage(failures));
        for (int i = 0; i < failures.size(); i++) {
            System.out.println("[Celtium] CELTIUM_PRESENTED_IMAGE " + failures.get(i));
        }
        report.addProperty("presentedImageFrameCountAboveZero",
                Boolean.valueOf(primary != null && celtium$presentedImageFailures == 0));
    }

    /** The GUI scale the video settings panel floors were measured at, in the game's own ScaledResolution units. */
    private static final int celtium$REFERENCE_GUI_SCALE = 3;

    /** The GUI scale a ScaledResolution gives this window, or -1 when it cannot be read. */
    private static int celtium$guiScale() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            return mc == null ? -1 : new net.minecraft.client.gui.ScaledResolution(mc).getScaleFactor();
        } catch (Throwable unreadable) {
            return -1;
        }
    }

    /**
     * Whether this run's picture is the one the scene's floors describe: the scene has a measured reference and this
     * run's calibration key equals the key those floors were measured under.
     * <p>
     * The key is built here rather than at construction because part of it - the GUI scale - is a live game setting,
     * and the pose is the run's resolved pose rather than the scene's default.
     */
    private boolean celtium$variantMeasuredAtVerdict() {
        if (!celtium$gate.calibrated) {
            return false;
        }
        return celtium$calibrationKey(celtium$scene, celtium$pose, celtium$guiScale())
                .equals(celtium$referenceCalibrationKey(celtium$scene));
    }

    /** The run's calibration key as the report carries it. */
    private String celtium$runCalibrationKey() {
        return celtium$calibrationKey(celtium$scene, celtium$pose, celtium$guiScale());
    }

    /** The scene's variant as words: the world stack and the coverage flags that decide what the picture contains. */
    private static String celtium$sceneVariantDescription() {
        String stack = System.getProperty("celtium.validation.worldLayers", celtium$DEFAULT_WORLD_LAYERS);
        int sweep = Integer.getInteger("celtium.validation.blockSweep", 0).intValue();
        int sweepFrom = Integer.getInteger("celtium.validation.blockSweepFrom", 0).intValue();
        StringBuilder variant = new StringBuilder();
        variant.append(celtium$DEFAULT_WORLD_LAYERS.equals(stack) ? "default flat stack" : "worldLayers=" + stack);
        if (sweep > 0) {
            variant.append(", blockSweep=").append(sweep).append(" from registry index ").append(sweepFrom);
        }
        if (Boolean.getBoolean("celtium.validation.allBlocks")) {
            variant.append(", allBlocks wall");
        }
        if (!"none".equals(celtium$entityVariant())) {
            variant.append(", entity flags ").append(celtium$entityVariant());
        }
        int scale = celtium$guiScale();
        if (scale > 0) {
            variant.append(", guiScale=").append(scale).append(scale == celtium$REFERENCE_GUI_SCALE
                    ? " (the scale the panel floors were measured at)"
                    : " (NOT the scale the panel floors were measured at: " + celtium$REFERENCE_GUI_SCALE + ")");
        }
        return variant.toString();
    }

    /** The scene's dimension as words, so a failure names the world the picture is of, not only the scene's name. */
    private static String celtium$sceneDimension() {
        int dimension = Integer.getInteger("celtium.validation.dimension", 0).intValue();
        int atTick = Integer.getInteger("celtium.validation.dimensionAtTick", 0).intValue();
        if (atTick <= 0 || dimension == 0) {
            return "overworld (dimension 0), no transfer requested";
        }
        return "dimension " + dimension + " (transfer requested at tick " + atTick + ")";
    }

    /**
     * Identity check and scene gate for one stage's picture, with every measured number recorded.
     * <p>
     * The identity is read back from the sidecar on disk, not from the in-memory record, so what is verified is what a
     * reader of the run can verify: the same file, the same fields, the same pixels.
     */
    private Picture celtium$judgeStage(String stage, PictureEvidence evidence, int captureWorldFrames,
            java.util.List<String> failures) {
        String where = "scene=" + celtium$scene.name + " stage=" + stage;
        if (evidence == null) {
            failures.add("no picture was requested for " + where + ", so the scene's acceptance has no image"
                    + " (source for this configuration: " + celtium$evidenceSource() + ")");
            return null;
        }
        report.addProperty("pictureFile_" + stage, celtium$relative(evidence.image));
        report.addProperty("pictureRequestedAtTick_" + stage, Integer.valueOf(evidence.requestedAtTick));
        if (!evidence.completed) {
            failures.add("no picture to judge for " + where + ": nothing was written for the request at tick "
                    + evidence.requestedAtTick + " (world frame " + evidence.worldFramesAtRequest + ", after "
                    + evidence.presentedFramesAtRequest + " presented frames) at " + celtium$relative(evidence.image)
                    + (evidence.completionError == null || "none".equals(evidence.completionError) ? ""
                            : " - " + evidence.completionError));
            return null;
        }
        Picture picture = null;
        try {
            picture = Picture.read(evidence.image);
        } catch (Throwable error) {
            failures.add("the picture for " + where + " at " + celtium$relative(evidence.image)
                    + " could not be read: " + error);
            return null;
        }
        if (picture == null) {
            failures.add("the picture for " + where + " at " + celtium$relative(evidence.image) + " has no pixels");
            return null;
        }
        celtium$verifyIdentity(evidence, stage, captureWorldFrames, picture, failures);
        java.util.List<String> gateFailures = celtium$gatePicture(picture, stage);
        failures.addAll(gateFailures);
        return picture;
    }

    /**
     * Refuses a picture whose completed identity does not match the capture this verdict is about.
     * <p>
     * Every field is checked against the value this run recorded at the capture, and the pixels are hashed again: an
     * image written to the same path after its identity was completed - which the backend's every-fortieth-frame
     * re-request of the fixture's last path does - fails here instead of being judged as the captured frame.
     */
    private void celtium$verifyIdentity(PictureEvidence evidence, String stage, int captureWorldFrames, Picture picture,
            java.util.List<String> failures) {
        String where = "scene=" + celtium$scene.name + " stage=" + stage + " file=" + celtium$relative(evidence.image)
                + " identity=" + celtium$relative(evidence.identity);
        JsonObject identity = null;
        try {
            if (java.nio.file.Files.isRegularFile(evidence.identity)) {
                identity = JsonParser.parseString(new String(java.nio.file.Files.readAllBytes(evidence.identity),
                        StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } catch (Throwable error) {
            identity = null;
        }
        if (identity == null) {
            failures.add("no completed identity next to the picture for " + where + ": the frame it holds cannot be"
                    + " tied to the capture it is asserted about");
            return;
        }
        celtium$checkIdentity(identity, "runId", celtium$runId, where, failures);
        celtium$checkIdentity(identity, "scene", celtium$scene.name, where, failures);
        celtium$checkIdentity(identity, "stage", stage, where, failures);
        celtium$checkIdentity(identity, "source", celtium$evidenceSource(), where, failures);
        celtium$checkIdentity(identity, "image", celtium$relative(evidence.image), where, failures);
        celtium$checkIdentity(identity, "worldTick", evidence.requestedAtTick, where, failures);
        celtium$checkIdentity(identity, "width", evidence.requestedWidth, where, failures);
        celtium$checkIdentity(identity, "height", evidence.requestedHeight, where, failures);
        celtium$checkIdentity(identity, "frameBinding", evidence.frameBinding, where, failures);
        if (SOURCE_PRESENTED_DUMP.equals(evidence.source)) {
            celtium$checkIdentity(identity, "presentedFrame", evidence.presentedFrame, where, failures);
        } else if (evidence.presentedFrame != -1) {
            failures.add("the screenshot-sourced picture for " + where + " records presentedFrame="
                    + evidence.presentedFrame + " although no present wrote it; the identity is not the one this"
                    + " fixture writes for a synchronous screenshot");
        }
        celtium$checkIdentity(identity, "imageHash", evidence.imageHash, where, failures);
        String completionError = identity.has("completionError") ? identity.get("completionError").getAsString() : "none";
        if (!"none".equals(completionError)) {
            failures.add("the identity of the picture for " + where + " records that its frame could not be named: "
                    + completionError);
        }
        if (picture.hash() != evidence.imageHash) {
            failures.add("the image at " + celtium$relative(evidence.image) + " is not the one its identity names: the"
                    + " pixels hash to 0x" + Integer.toHexString(picture.hash()) + ", the identity records 0x"
                    + Integer.toHexString(evidence.imageHash) + " (it was written again after the identity was"
                    + " completed) for " + where);
        }
        // The frame-count comparison is only a claim about a presented dump: it says the present that wrote the file
        // is the frame the fixture drew at the capture. A screenshot is written inside that frame's own handler, so it
        // needs no such inference.
        if (SOURCE_PRESENTED_DUMP.equals(evidence.source) && captureWorldFrames >= 0 && evidence.presentedFrame >= 0) {
            boolean counterRestarted = report.has("dimensionPresentedCounterResetAtArrival")
                    && report.get("dimensionPresentedCounterResetAtArrival").getAsBoolean()
                    && celtium$presentedFramesAtArrival >= 0;
            if (counterRestarted) {
                // The renderer's presented-frame counter belongs to the live frame target, and a dimension change
                // recreates that target: the counter then starts again from zero. Comparing it with the whole run's
                // world-frame count would read the restart as frames that never reached the window (measured on the
                // Nether run: presentedFrame 205 against 248 world frames, -43). What the assertion is for - the frame
                // in the file is one the renderer presented in the window the counter spans - is checked inside that
                // window, and the whole-run numbers are recorded beside it.
                int sinceArrival = evidence.presentedFrame - celtium$presentedFramesAtArrival;
                int framesInTarget = celtium$framesInTargetDimension;
                report.addProperty("pictureFrameDelta_" + stage, Integer.valueOf(sinceArrival - framesInTarget));
                report.addProperty("pictureFrameScope_" + stage, "dimension window: presented counter restarted at the"
                        + " arrival (" + celtium$presentedFramesAtArrival + " at it, high-water "
                        + celtium$presentedHighWaterBeforeArrival + " before); the picture's frame " + evidence.presentedFrame
                        + " is " + sinceArrival + " presents into the window, which drew " + framesInTarget + " frames");
                if (sinceArrival < framesInTarget - 1) {
                    failures.add("the picture for " + where + " is " + sinceArrival + " presents after the arrival in"
                            + " dimension " + celtium$dimension + ", fewer than the " + framesInTarget + " frames that"
                            + " dimension drew (presentedFrame=" + evidence.presentedFrame + ", at arrival="
                            + celtium$presentedFramesAtArrival + ")");
                }
                return;
            }
            int delta = evidence.presentedFrame - captureWorldFrames;
            report.addProperty("pictureFrameDelta_" + stage, Integer.valueOf(delta));
            if (Math.abs(delta) > 1) {
                failures.add("the picture for " + where + " is the frame the renderer presented at "
                        + evidence.presentedFrame + ", which is " + delta + " away from the " + captureWorldFrames
                        + " world frames the capture recorded: the frame in the file is not the frame the capture is"
                        + " about");
            }
        }
    }

    /** One identity field against what this run recorded: name, expected value, and the number measured. */
    private void celtium$checkIdentity(JsonObject identity, String field, Object expected, String where,
            java.util.List<String> failures) {
        if (!identity.has(field)) {
            failures.add("the identity of the picture for " + where + " has no '" + field + "' field, so the frame it"
                    + " names cannot be compared with the capture (expected " + expected + ")");
            return;
        }
        String measured = identity.get(field).getAsString();
        String wanted = String.valueOf(expected);
        if (!wanted.equals(measured)) {
            failures.add("the identity of the picture for " + where + " says " + field + "=" + measured + ", but this"
                    + " capture recorded " + field + "=" + wanted + ": the image is not the frame the capture is about");
        }
    }

    /** The scene's gate on one picture, with every measured number recorded beside the floor it is judged against. */
    private java.util.List<String> celtium$gatePicture(Picture picture, String stage) {
        java.util.List<String> failures = new java.util.ArrayList<String>();
        PresentedImageStats stats = picture.stats();
        boolean primary = "world".equals(stage) ? !celtium$scene.guiStage : celtium$scene.guiStage;
        String prefix = "world".equals(stage) ? "worldFrame" : "guiFrame";
        // What a failure names: the scene and the world it is of, the stage, the file, its identity sidecar, where the
        // picture came from, and the frame it is: a reader has to be able to open the image a verdict is about.
        String label = "scene=" + celtium$scene.name + " (" + celtium$sceneDimension() + ") stage=" + stage
                + " file=" + celtium$relative(celtium$imageOfEvidence(stage))
                + " identity=" + celtium$relative(celtium$identityOfEvidence(stage))
                + " source=" + celtium$evidenceSource() + " extent=" + stats.width + "x" + stats.height
                + " worldTick=" + celtium$tickOfEvidence(stage) + " presentedFrame=" + celtium$frameOfEvidence(stage);
        report.addProperty(prefix + "StatsSource", celtium$evidenceSource() + " " + stats.width + "x" + stats.height);
        report.addProperty(prefix + "DistinctColours", Integer.valueOf(stats.distinctColours));
        report.addProperty(prefix + "NonBackgroundFraction", Double.valueOf(stats.nonBackgroundFraction));
        report.addProperty(prefix + "VerticalBands", Integer.valueOf(stats.verticalBands));
        report.addProperty(prefix + "BlackFraction", Double.valueOf(stats.blackFraction));
        report.addProperty(prefix + "BackgroundColour", celtium$hexColour(stats.backgroundColour));
        report.addProperty(prefix + "Pixels", Integer.valueOf(stats.width * stats.height));
        if (primary) {
            // The canonical names, so existing tools and reports stay readable: they describe the picture this scene's
            // acceptance is about.
            report.addProperty("presentedImageStatsSource", celtium$evidenceSource() + " " + stats.width + "x"
                    + stats.height + " (" + celtium$relative(celtium$imageOfEvidence(stage)) + ")");
            report.addProperty("presentedDistinctColours", Integer.valueOf(stats.distinctColours));
            report.addProperty("presentedNonBackgroundFraction", Double.valueOf(stats.nonBackgroundFraction));
            report.addProperty("presentedVerticalBands", Integer.valueOf(stats.verticalBands));
            report.addProperty("presentedBlackFraction", Double.valueOf(stats.blackFraction));
            report.addProperty("presentedBackgroundColour", celtium$hexColour(stats.backgroundColour));
            report.addProperty("presentedBackgroundTolerance", Integer.valueOf(PRESENTED_BACKGROUND_TOLERANCE));
            report.addProperty("presentedImagePixels", Integer.valueOf(stats.width * stats.height));
            report.addProperty("presentedImageNotSingleColour", Boolean.valueOf(stats.distinctColours > 1));
            report.addProperty("presentedImageDistinctColoursAboveFloor",
                    Boolean.valueOf(stats.distinctColours > celtium$gate.minDistinctColours));
            report.addProperty("presentedImagePng", celtium$relative(celtium$imageOfEvidence(stage)));
        }
        if (celtium$gateEnforced) {
            report.addProperty(prefix + "Gate", "CALIBRATED against " + celtium$gate.referenceRuns);
        } else {
            report.addProperty(prefix + "Gate", "NOT_CALIBRATED: " + celtium$gate.referenceRuns
                    + "; this picture is held to the smoke test, its identity and the camera pose only (verdict"
                    + " UNCALIBRATED_PRESENTED_IMAGE, floorsNotCalibrated)");
        }
        if (stats.distinctColours <= 1) {
            failures.add("the picture is a single colour for " + label + ": presentedDistinctColours="
                    + stats.distinctColours + " over " + (stats.width * stats.height) + " pixels");
            return failures;
        }
        if (stats.distinctColours <= celtium$gate.minDistinctColours) {
            failures.add("presentedDistinctColours=" + stats.distinctColours + " is not above the smoke floor of "
                    + celtium$gate.minDistinctColours + " for " + label + " (nonBackgroundFraction="
                    + stats.nonBackgroundFraction + ", verticalBands=" + stats.verticalBands + ", blackFraction="
                    + stats.blackFraction + ")");
        }
        if (celtium$gateEnforced && stats.nonBackgroundFraction < celtium$gate.minNonBackgroundFraction) {
            failures.add("presentedNonBackgroundFraction=" + stats.nonBackgroundFraction + " is below the scene floor of "
                    + celtium$gate.minNonBackgroundFraction + " for " + label + " (backgroundColour="
                    + celtium$hexColour(stats.backgroundColour) + " covers "
                    + Math.round((1.0D - stats.nonBackgroundFraction) * 1000000.0D) / 10000.0D + "% of the frame; scene"
                    + " floor measured in " + celtium$gate.referenceRuns + ")");
        }
        if (celtium$gateEnforced && stats.verticalBands < celtium$gate.minVerticalBands) {
            failures.add("presentedVerticalBands=" + stats.verticalBands + " is below the scene floor of "
                    + celtium$gate.minVerticalBands + " for " + label + ": the frame has too few row transitions to be"
                    + " a picture of this scene (scene floor measured in " + celtium$gate.referenceRuns + ")");
        }
        if (celtium$gateEnforced && stats.blackFraction > celtium$gate.maxBlackFraction) {
            failures.add("presentedBlackFraction=" + stats.blackFraction + " is above the scene ceiling of "
                    + celtium$gate.maxBlackFraction + " for " + label + ": " + Math.round(stats.blackFraction * 10000.0D)
                    / 100.0D + "% of the frame is black in every channel, which is the large-black-region defect the"
                    + " colour count alone passed (outline2: 11751 colours, two thirds black; scene ceiling measured in "
                    + celtium$gate.referenceRuns + ")");
        }
        return failures;
    }

    /**
     * The GUI gate: the screen's own list rectangle on the GUI frame, against the same rectangle of the world frame.
     * <p>
     * This is what makes the video settings scene more than a smoke test. The panel is the region the open screen says
     * its list occupies; the reference is the frame of the same run with no screen over it. A correct capture changes
     * that region almost completely (the screen's own dark background and its rows of widgets), gives it many more
     * distinct colours than the world there, and gives it row structure the darkened terrain does not have. A frame in
     * which the screen never drew fails all four.
     */
    private java.util.List<String> celtium$gateGuiPanel(Picture world, Picture gui) {
        java.util.List<String> failures = new java.util.ArrayList<String>();
        Minecraft mc = Minecraft.getMinecraft();
        int[] panel = celtium$panelRect(mc);
        if (panel == null) {
            failures.add("scene " + celtium$scene.name + ": the open screen's own list rectangle could not be read"
                    + " (GuiVideoSettings.optionsRowList), so the panel region of the picture cannot be located");
            return failures;
        }
        int x0 = Math.max(0, panel[0] + 2);
        int y0 = Math.max(0, panel[1] + 2);
        int x1 = Math.min(gui.width, panel[2] - 2);
        int y1 = Math.min(gui.height, panel[3] - 2);
        if (x1 - x0 < 8 || y1 - y0 < 8) {
            failures.add("scene " + celtium$scene.name + ": the list rectangle the screen reports (" + panel[0] + ","
                    + panel[1] + ")..(" + panel[2] + "," + panel[3] + ") is too small to judge");
            return failures;
        }
        double changed = gui.changedFraction(world, x0, y0, x1, y1, PRESENTED_BACKGROUND_TOLERANCE);
        double strong = gui.changedFraction(world, x0, y0, x1, y1, 32);
        int panelColours = gui.regionDistinctColours(x0, y0, x1, y1);
        int worldColours = world.regionDistinctColours(x0, y0, x1, y1);
        double panelRows = gui.regionRowAmplitude(x0, y0, x1, y1);
        double worldRows = world.regionRowAmplitude(x0, y0, x1, y1);
        double panelLuma = gui.regionMeanLuma(x0, y0, x1, y1);
        double worldLuma = world.regionMeanLuma(x0, y0, x1, y1);
        report.addProperty("guiPanelRect", x0 + "," + y0 + ".." + x1 + "," + y1 + " (from the screen's own list,"
                + panel[0] + "," + panel[1] + ".." + panel[2] + "," + panel[3] + ")");
        report.addProperty("guiPanelChangedFraction", Double.valueOf(changed));
        report.addProperty("guiPanelStrongFraction", Double.valueOf(strong));
        report.addProperty("guiPanelDistinctColours", Integer.valueOf(panelColours));
        report.addProperty("guiWorldPanelDistinctColours", Integer.valueOf(worldColours));
        report.addProperty("guiPanelRowAmplitude", Double.valueOf(panelRows));
        report.addProperty("guiWorldPanelRowAmplitude", Double.valueOf(worldRows));
        report.addProperty("guiPanelMeanLuma", Double.valueOf(panelLuma));
        report.addProperty("guiWorldPanelMeanLuma", Double.valueOf(worldLuma));
        String label = "scene=" + celtium$scene.name + " (" + celtium$sceneDimension() + ") stage=gui screen="
                + celtium$screenName(mc) + " panel=" + x0 + "," + y0 + ".." + x1 + "," + y1 + " guiFrame=[file="
                + celtium$relative(celtium$imageOfEvidence("gui")) + " identity="
                + celtium$relative(celtium$identityOfEvidence("gui")) + " source=" + celtium$evidenceSource()
                + " worldTick=" + celtium$tickOfEvidence("gui") + " presentedFrame=" + celtium$frameOfEvidence("gui")
                + "] worldFrame=[file=" + celtium$relative(celtium$imageOfEvidence("world")) + " identity="
                + celtium$relative(celtium$identityOfEvidence("world")) + " worldTick="
                + celtium$tickOfEvidence("world") + "] panel floors measured in " + celtium$gate.referenceRuns;
        if (changed < celtium$gate.minPanelChangedFraction) {
            failures.add("the video settings panel did not draw: guiPanelChangedFraction=" + changed + " is below the"
                    + " reference floor of " + celtium$gate.minPanelChangedFraction + " for " + label);
        }
        if (strong < celtium$gate.minPanelStrongFraction) {
            failures.add("the video settings panel has too little of its own chrome: guiPanelStrongFraction=" + strong
                    + " is below the reference floor of " + celtium$gate.minPanelStrongFraction + " (a fraction of the"
                    + " panel differing from the world frame by more than 32 per channel) for " + label);
        }
        if (panelColours < celtium$gate.minPanelDistinctColours) {
            failures.add("the video settings panel has too few colours to be a screen of rows and labels:"
                    + " guiPanelDistinctColours=" + panelColours + " is below the reference floor of "
                    + celtium$gate.minPanelDistinctColours + " (the world frame has " + worldColours + " there) for "
                    + label);
        }
        if (panelRows < celtium$gate.minPanelRowAmplitude) {
            failures.add("the video settings panel has no row structure: guiPanelRowAmplitude=" + panelRows
                    + " is below the reference floor of " + celtium$gate.minPanelRowAmplitude
                    + " (the world frame has " + worldRows + " there) for " + label);
        }
        return failures;
    }

    /**
     * The camera the capture was taken from, against the pose the scene's floors were measured at.
     * <p>
     * This is the other half of "two runs are comparable": the pose is the fixture's, it is set on both sides, and what
     * the client's player actually was is read back and compared rather than assumed. A run that drifted is reported
     * with both poses, because a gate measured at one camera cannot judge a picture taken from another.
     */
    private void celtium$gateCamera(java.util.List<String> failures) {
        Minecraft mc = Minecraft.getMinecraft();
        report.addProperty("cameraRequested", celtium$pose.describe());
        report.addProperty("cameraPoseSource", "fixture pose: server-side teleport at tick " + celtium$poseTick + " ("
                + celtium$poseResult + "), re-applied on the client every tick (" + celtium$poseAppliedTicks
                + " times, last at tick " + celtium$poseAppliedAtTick + ")"
                + (celtium$poseViewEntityTicks > 0
                        ? ", and to the renderer's view entity too (" + celtium$poseViewEntity + " on "
                                + celtium$poseViewEntityTicks + " ticks, where it was not the player entity)"
                        : ", the renderer's view entity was the player entity on every tick"));
        if (mc.player == null) {
            failures.add("scene " + celtium$scene.name + ": there is no client player, so the capture camera cannot be"
                    + " compared with the scene's pose (" + celtium$pose.describe() + ")");
            return;
        }
        double dx = mc.player.posX - celtium$pose.x;
        double dy = mc.player.posY - celtium$pose.y;
        double dz = mc.player.posZ - celtium$pose.z;
        double dyaw = Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(mc.player.rotationYaw - celtium$pose.yaw));
        double dpitch = Math.abs(mc.player.rotationPitch - celtium$pose.pitch);
        String measured = String.format(java.util.Locale.ROOT, "x=%.4f y=%.4f z=%.4f yaw=%.3f pitch=%.3f",
                mc.player.posX, mc.player.posY, mc.player.posZ, mc.player.rotationYaw, mc.player.rotationPitch);
        report.addProperty("cameraAtVerdict", measured);
        boolean held = Math.abs(dx) <= 1.0E-6D && Math.abs(dy) <= 1.0E-6D && Math.abs(dz) <= 1.0E-6D
                && dyaw <= 1.0E-3D && dpitch <= 1.0E-3D;
        report.addProperty("cameraHeld", Boolean.valueOf(held));
        if (!held) {
            failures.add("scene " + celtium$scene.name + ": the camera at the capture is not the pose the scene's"
                    + " floors were measured at (requested " + celtium$pose.describe() + ", measured " + measured
                    + "): two runs of this scene are not pixel-comparable and the floors do not describe this picture");
        }
        // The camera the picture was actually drawn from. mc.player above is the fixture's side of the claim; the world
        // renderer publishes the viewport it used, and the two can disagree - after the Nether transfer one run rendered
        // from (1.34, 62.32, 1.29), the arrival position, while mc.player read exactly the pose (measured:
        // .celtium-work/runtime/scene-floors/nether-a against nether-b). A picture taken from another camera is not a
        // picture of this scene, so it is refused here rather than judged by the scene's floors.
        double[] viewport = celtium$rendererViewport();
        double eyeY = celtium$pose.y + mc.player.getEyeHeight();
        if (viewport == null) {
            report.addProperty("rendererCameraAtCapture", "unavailable: the renderer published no viewport line at the"
                    + " capture (no world renderer in this configuration, or the capture never ran)");
        } else {
            double vdx = viewport[0] - celtium$pose.x;
            double vdy = viewport[1] - eyeY;
            double vdz = viewport[2] - celtium$pose.z;
            // The renderer's viewport is the eye position and carries the game's own view bobbing and partial-tick
            // interpolation, which the measured runs put within 0.05 of the pose for x/z and 0.02 for y (terrain and
            // video: 0.50 13.60 0.45 against a pose of 0.5, 12.0, 0.5 and an eye height of 1.62). The tolerance is
            // wide enough for that and far narrower than any camera that is not this pose: the stale one was 50 blocks
            // away.
            boolean atPose = Math.abs(vdx) <= 0.25D && Math.abs(vdz) <= 0.25D && Math.abs(vdy) <= 0.50D;
            report.addProperty("rendererCameraAtCapture", String.format(java.util.Locale.ROOT,
                    "x=%.4f y=%.4f z=%.4f (the world renderer's own viewport at the capture; the pose's eye is"
                            + " x=%.4f y=%.4f z=%.4f, tolerance 0.25 horizontally and 0.50 vertically)",
                    viewport[0], viewport[1], viewport[2], celtium$pose.x, eyeY, celtium$pose.z));
            report.addProperty("rendererCameraAtPose", Boolean.valueOf(atPose));
            if (!atPose) {
                failures.add("scene " + celtium$scene.name + ": the renderer drew this picture from "
                        + String.format(java.util.Locale.ROOT, "x=%.2f y=%.2f z=%.2f", viewport[0], viewport[1], viewport[2])
                        + ", not from the pose's eye " + String.format(java.util.Locale.ROOT, "x=%.2f y=%.2f z=%.2f",
                                celtium$pose.x, eyeY, celtium$pose.z)
                        + " the scene's floors were measured at (mc.player reads " + measured + "): the frame in the"
                        + " file is a picture of another camera position, so the scene's floors do not describe it"
                        + " (this is the stale-view-entity case: the renderer kept an entity the dimension transfer had"
                        + " moved, measured in .celtium-work/runtime/scene-floors/nether-a)");
            }
        }
    }

    /** The tick the picture of a stage was taken at, or -1: read from the record rather than from a live counter. */
    private int celtium$tickOfEvidence(String stage) {
        PictureEvidence evidence = celtium$evidence.get(stage);
        return evidence == null ? -1 : evidence.worldTick;
    }

    /** The presented-frame counter the picture of a stage was written under, or -1. */
    private int celtium$frameOfEvidence(String stage) {
        PictureEvidence evidence = celtium$evidence.get(stage);
        return evidence == null ? -1 : evidence.presentedFrame;
    }

    /** The file the picture of a stage came from, or a placeholder when there is none. */
    private java.nio.file.Path celtium$imageOfEvidence(String stage) {
        PictureEvidence evidence = celtium$evidence.get(stage);
        return evidence == null ? Minecraft.getMinecraft().gameDir.toPath().resolve("screenshots/<none>")
                : evidence.image;
    }

    /** The identity sidecar of the picture of a stage, or a placeholder when there is none. */
    private java.nio.file.Path celtium$identityOfEvidence(String stage) {
        PictureEvidence evidence = celtium$evidence.get(stage);
        return evidence == null
                ? Minecraft.getMinecraft().gameDir.toPath().resolve("screenshots/<none>" + IDENTITY_SUFFIX)
                : evidence.identity;
    }

    /**
     * The run's status from the picture verdict.
     * <p>
     * Three outcomes, not two: {@code PASSED} when every picture is the frame its identity names and the scene's own
     * measured floors hold; {@code FAILED_PRESENTED_IMAGE} when they do not; and
     * {@code UNCALIBRATED_PRESENTED_IMAGE} when the scene has no measured reference, which is neither - it must not
     * read as a pass, and it must not train a reader to ignore failures by being one.
     * <p>
     * A run that has already failed for a reason of its own keeps that verdict - a dimension run whose gate failed can
     * still have presented a clean frame, and that picture passing must not turn the failure into a pass - and the
     * picture verdict is recorded under its own name either way.
     */
    private void celtium$setStatusFromPictures() {
        boolean enforced = report.has("sceneGateFloorsEnforced")
                && report.get("sceneGateFloorsEnforced").getAsBoolean();
        String verdict;
        if (celtium$presentedImageFailures > 0) {
            verdict = "FAILED_PRESENTED_IMAGE";
        } else if (!enforced) {
            verdict = "UNCALIBRATED_PRESENTED_IMAGE";
        } else {
            verdict = "PASSED";
        }
        report.addProperty("presentedImageVerdict", verdict);
        String status = report.has("status") ? report.get("status").getAsString() : null;
        if ("FAILED".equals(status) || "NO_CAPTURE".equals(status)) {
            return;
        }
        report.addProperty("status", verdict);
    }

    /** The assertion results as one line: PASSED, uncalibrated, or every failure with the numbers and the frame it measured. */
    private String celtium$presentedImageAssertionMessage(java.util.List<String> failures) {
        if (failures.isEmpty()) {
            if (!celtium$gateEnforced) {
                return "UNCALIBRATED: this scene has no measured reference, so no non-background, band, black or panel"
                        + " floor was applied; the smoke test, the picture identity, the frame binding and the camera"
                        + " pose held. The run is neither passed nor failed on a picture nobody has measured"
                        + " (see floorsNotCalibrated)";
            }
            return "PASSED: every picture is the frame its identity names, and the scene's gate (distinct colours,"
                    + " non-background fraction, row bands, black fraction; panel statistics for a GUI scene) held";
        }
        StringBuilder message = new StringBuilder("FAILED: ");
        for (int i = 0; i < failures.size(); i++) {
            if (i > 0) {
                message.append(" | ");
            }
            message.append(failures.get(i));
        }
        return message.toString();
    }

    /** A packed RGB value as the report's hex form, matching the other colour properties. */
    private static String celtium$hexColour(int rgb) {
        return "0x" + String.format(java.util.Locale.ROOT, "%06X", Integer.valueOf(rgb & 0xFFFFFF));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Picture arithmetic. The numbers live in the fixture on purpose: what counts as a picture is the instrument's
    // question, not the renderer's.
    // ---------------------------------------------------------------------------------------------------------------

    /** An image as pixels, with the measurements the gates use. */
    private static final class Picture {
        final int width;
        final int height;
        final int[] pixels;

        Picture(int width, int height, int[] pixels) {
            this.width = width;
            this.height = height;
            this.pixels = pixels;
        }

        /** {@return the image at this path as packed-RGB pixels}, or null when it has none. */
        static Picture read(java.nio.file.Path path) throws java.io.IOException {
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(path.toFile());
            if (image == null) {
                return null;
            }
            int width = image.getWidth();
            int height = image.getHeight();
            if (width <= 0 || height <= 0) {
                return null;
            }
            int[] argb = new int[width * height];
            image.getRGB(0, 0, width, height, argb, 0, width);
            int[] pixels = new int[argb.length];
            for (int i = 0; i < argb.length; i++) {
                pixels[i] = argb[i] & 0xFFFFFF;
            }
            return new Picture(width, height, pixels);
        }

        /** A hash of the pixels: the identity's proof that the file on disk is still the frame it was taken from. */
        int hash() {
            int hash = 1;
            for (int i = 0; i < pixels.length; i++) {
                hash = 31 * hash + pixels[i];
            }
            return hash * 31 + pixels.length;
        }

        int pixel(int x, int y) {
            return pixels[y * width + x];
        }

        /** The whole-frame statistics: distinct colours, the most common one, and the shares that judge a picture. */
        PresentedImageStats stats() {
            int verticalBands = 1;
            for (int y = 1; y < height; y++) {
                if (!celtium$rowsIdentical(pixels, (y - 1) * width, y * width, width)) {
                    verticalBands++;
                }
            }
            // Distinct colours and the most common one in one sorted pass. The most common colour is the background:
            // it is what the window would be showing if nothing had been drawn, and the share of everything else is
            // what says whether anything was.
            int[] sorted = pixels.clone();
            java.util.Arrays.sort(sorted);
            int distinctColours = 0;
            int backgroundColour = 0;
            int backgroundCount = 0;
            int runStart = 0;
            for (int i = 0; i <= sorted.length; i++) {
                if (i == sorted.length || sorted[i] != sorted[runStart]) {
                    int run = i - runStart;
                    distinctColours++;
                    if (run > backgroundCount) {
                        backgroundCount = run;
                        backgroundColour = sorted[runStart];
                    }
                    runStart = i;
                }
            }
            int nonBackground = 0;
            int black = 0;
            for (int i = 0; i < pixels.length; i++) {
                if (!celtium$withinBackgroundTolerance(pixels[i], backgroundColour)) {
                    nonBackground++;
                }
                if (celtium$isBlack(pixels[i])) {
                    black++;
                }
            }
            // Rounded to six decimals: reports of the same scene are compared between runs, and the digits beyond that
            // are float noise rather than measurement.
            double fraction = Math.round((double) nonBackground * 1000000.0D / pixels.length) / 1000000.0D;
            double blackFraction = Math.round((double) black * 1000000.0D / pixels.length) / 1000000.0D;
            return new PresentedImageStats(width, height, distinctColours, fraction, verticalBands, backgroundColour,
                    blackFraction);
        }

        /** The distinct colours inside a rectangle. */
        int regionDistinctColours(int x0, int y0, int x1, int y1) {
            int[] region = new int[Math.max(0, (x1 - x0) * (y1 - y0))];
            int at = 0;
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    region[at++] = pixel(x, y);
                }
            }
            java.util.Arrays.sort(region, 0, at);
            int distinct = 0;
            for (int i = 0; i < at; i++) {
                if (i == 0 || region[i] != region[i - 1]) {
                    distinct++;
                }
            }
            return distinct;
        }

        /** The mean luma of a rectangle, 0..255. */
        double regionMeanLuma(int x0, int y0, int x1, int y1) {
            long total = 0;
            long count = 0;
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    int pixel = pixel(x, y);
                    total += (299 * ((pixel >> 16) & 0xFF) + 587 * ((pixel >> 8) & 0xFF) + 114 * (pixel & 0xFF)) / 1000;
                    count++;
                }
            }
            return count == 0 ? 0.0D : Math.round((double) total / count * 1000.0D) / 1000.0D;
        }

        /**
         * How much a rectangle's row means change from row to row.
         * <p>
         * A screen of option rows has structure at the row pitch; the same region of the darkened world does not have
         * that pitch. It is a measurement of "there is a panel here", not of brightness.
         */
        double regionRowAmplitude(int x0, int y0, int x1, int y1) {
            if (y1 - y0 < 2) {
                return 0.0D;
            }
            double total = 0.0D;
            double previous = rowMeanLuma(x0, x1, y0);
            for (int y = y0 + 1; y < y1; y++) {
                double current = rowMeanLuma(x0, x1, y);
                total += Math.abs(current - previous);
                previous = current;
            }
            return Math.round(total / (y1 - y0 - 1) * 1000.0D) / 1000.0D;
        }

        private double rowMeanLuma(int x0, int x1, int y) {
            long total = 0;
            for (int x = x0; x < x1; x++) {
                int pixel = pixel(x, y);
                total += (299 * ((pixel >> 16) & 0xFF) + 587 * ((pixel >> 8) & 0xFF) + 114 * (pixel & 0xFF)) / 1000;
            }
            return (double) total / Math.max(1, x1 - x0);
        }

        /**
         * The share of a rectangle's pixels that differ from another picture's by more than a tolerance per channel.
         * <p>
         * This is how "the screen is in the picture" is measured: the GUI frame against the world frame of the same run
         * at the same camera pose.
         */
        double changedFraction(Picture other, int x0, int y0, int x1, int y1, int tolerance) {
            if (other == null || other.width != width || other.height != height) {
                return 0.0D;
            }
            long changed = 0;
            long count = 0;
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    int a = pixel(x, y);
                    int b = other.pixel(x, y);
                    int difference = Math.max(Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)),
                            Math.max(Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF)),
                                    Math.abs((a & 0xFF) - (b & 0xFF))));
                    if (difference > tolerance) {
                        changed++;
                    }
                    count++;
                }
            }
            return count == 0 ? 0.0D : Math.round((double) changed * 1000000.0D / count) / 1000000.0D;
        }
    }

    /**
     * What a picture looks like as numbers, rather than as one mean.
     * <p>
     * The mean cannot separate a frame of terrain from a clear colour with a quad over it - both have a plausible mean.
     * These numbers can. {@code distinctColours} counts the distinct RGB values. {@code nonBackgroundFraction} is the
     * share of pixels that are neither the most common colour nor within {@link #PRESENTED_BACKGROUND_TOLERANCE} of it.
     * {@code verticalBands} is the number of maximal runs of consecutive pixel-identical rows. {@code blackFraction} is
     * the share of pixels that are black in every channel, which is the measurement that catches a frame with a large
     * black region however many colours the rest of it has.
     */
    private static final class PresentedImageStats {
        final int width;
        final int height;
        final int distinctColours;
        final double nonBackgroundFraction;
        final int verticalBands;
        final int backgroundColour;
        final double blackFraction;

        PresentedImageStats(int width, int height, int distinctColours, double nonBackgroundFraction, int verticalBands,
                int backgroundColour, double blackFraction) {
            this.width = width;
            this.height = height;
            this.distinctColours = distinctColours;
            this.nonBackgroundFraction = nonBackgroundFraction;
            this.verticalBands = verticalBands;
            this.backgroundColour = backgroundColour;
            this.blackFraction = blackFraction;
        }
    }

    /** Whether two rows of packed-RGB pixels are the same picture. */
    private static boolean celtium$rowsIdentical(int[] pixels, int firstRow, int secondRow, int width) {
        for (int x = 0; x < width; x++) {
            if (pixels[firstRow + x] != pixels[secondRow + x]) {
                return false;
            }
        }
        return true;
    }

    /** Whether a packed-RGB pixel is the background colour or within the tolerance of it. */
    private static boolean celtium$withinBackgroundTolerance(int pixel, int background) {
        return Math.abs(((pixel >> 16) & 0xFF) - ((background >> 16) & 0xFF)) <= PRESENTED_BACKGROUND_TOLERANCE
                && Math.abs(((pixel >> 8) & 0xFF) - ((background >> 8) & 0xFF)) <= PRESENTED_BACKGROUND_TOLERANCE
                && Math.abs((pixel & 0xFF) - (background & 0xFF)) <= PRESENTED_BACKGROUND_TOLERANCE;
    }

    /** Whether every channel of a pixel is at or below the black floor. */
    private static boolean celtium$isBlack(int pixel) {
        return ((pixel >> 16) & 0xFF) <= PRESENTED_BLACK_CHANNEL_MAX
                && ((pixel >> 8) & 0xFF) <= PRESENTED_BLACK_CHANNEL_MAX
                && (pixel & 0xFF) <= PRESENTED_BLACK_CHANNEL_MAX;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Reflective reads: the fixture runs both in a development environment (MCP names) and against a production jar
    // (SRG names), so every member it reaches for by name is given in both forms.
    // ---------------------------------------------------------------------------------------------------------------

    /** The value of a field by name, searching the class hierarchy, or null when it cannot be read. */
    private static Object celtium$fieldValue(Object owner, String[] names) {
        if (owner == null) {
            return null;
        }
        Field field = celtium$findField(owner.getClass(), names);
        if (field == null) {
            return null;
        }
        try {
            return field.get(owner);
        } catch (IllegalAccessException | RuntimeException error) {
            return null;
        }
    }

    /** A field by name, searching the class hierarchy: GuiSlot's geometry is inherited by GuiListExtended. */
    private static Field celtium$findField(Class<?> owner, String[] names) {
        for (Class<?> type = owner; type != null && type != Object.class; type = type.getSuperclass()) {
            Field field = findField(type, names);
            if (field != null) {
                return field;
            }
        }
        return null;
    }

    /** A method by name and signature, searching the class hierarchy, or a NoSuchMethodException with the names tried. */
    private static java.lang.reflect.Method celtium$requireMethod(Class<?> owner, String[] names, Class<?>... parameters)
            throws NoSuchMethodException {
        for (Class<?> type = owner; type != null && type != Object.class; type = type.getSuperclass()) {
            for (String name : names) {
                try {
                    java.lang.reflect.Method method = type.getDeclaredMethod(name, parameters);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException | RuntimeException absent) {
                    // Try the next name or the next class.
                }
            }
        }
        StringBuilder tried = new StringBuilder();
        for (String name : names) {
            if (tried.length() > 0) {
                tried.append('/');
            }
            tried.append(name);
        }
        throw new NoSuchMethodException(tried + " on " + owner.getName());
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
                    // The dimension run's own state, so a run that ends before the capture tick says where the player
                    // was and what the transfer did instead of leaving NO_CAPTURE as the whole answer. These are
                    // volatile snapshots: the hook must not read the game.
                    report.addProperty("dimensionArrivedAtTick", Integer.valueOf(celtium$arrivedAtTick));
                    report.addProperty("dimensionLeftTargetAtTick", Integer.valueOf(celtium$leftTargetAtTick));
                    report.addProperty("dimensionLeftTargetEvent", celtium$leftTargetEvent);
                    report.addProperty("dimensionFramesInTarget", Integer.valueOf(celtium$framesInTargetDimension));
                    report.addProperty("dimensionArrivalPlacement", celtium$arrivalPlacement);
                    report.addProperty("firstPresentedWorldFrame", Integer.valueOf(celtium$firstPresentedWorldFrame));
                    report.addProperty("dimensionPortalReentries", Integer.valueOf(celtium$portalReentries));
                    report.addProperty("dimensionPortalEscapes", Integer.valueOf(celtium$portalEscapes));
                    report.addProperty("dimensionTrace", celtium$dimensionTraceText.trim());
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
        // The picture verdict belongs to the capture whose identity it names, so it is taken once, where that evidence
        // is complete - at the capture in a GL-bridge run, on the frame after it under GL-less. Reaching here without a
        // verdict means the run ended before that point: the verdict is then taken against whatever evidence exists,
        // which is a refusal when the picture never arrived. This used to be a second check with the *live* world-frame
        // counter against the capture-time snapshot, which failed a passing Intel run at shutdown (150 against 152).
        if (!celtium$pictureVerdictTaken) {
            celtium$completePendingDumps();
            celtium$judgePictures(mc, "at shutdown, without a verdict at the capture");
            celtium$setStatusFromPictures();
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
