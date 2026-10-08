package org.soutalab.celtium.validation;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
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
    private final List<Double> frameIntervals = new ArrayList<Double>();
    private boolean enabled;
    private boolean launched;
    private boolean commandsRun;
    private boolean finished;
    private boolean interopRun;
    private int framesAtInterop;
    private int fboAtInterop, colorAtInterop, depthAtInterop;
    private int worldTicks;
    private int worldFrames;
    private long previousFrame;
    private int entitiesRemoved;
    private Field flickerX;
    private Field flickerDX;
    private boolean flickerFieldsResolved;
    private boolean flickerPinned;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        enabled = Boolean.getBoolean("celtium.validation");
        if (enabled) MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (!enabled || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            if (!launched && mc.currentScreen instanceof GuiMainMenu) {
                launched = true;
                report.addProperty("scope", "fixed flat world startup, commands and GL framebuffer observation");
                report.addProperty("nativeTerrainVerified", false);
                mc.gameSettings.renderDistanceChunks = 8;
                mc.gameSettings.limitFramerate = 120;
                mc.gameSettings.enableVsync = false;
                WorldSettings settings = new WorldSettings(12345678L, GameType.CREATIVE, false, false, WorldType.FLAT);
                settings.setGeneratorOptions("3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass;1;");
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
        if (worldTicks == 190 && !Boolean.getBoolean("celtium.validation.interop")) {
            // Hide the HUD a few ticks before the capture so the saved PNG is the rendered scene only. A stock
            // Celeritas run and a Celtium run of the same seed can then be compared pixel by pixel, without
            // chat lines, the hotbar or overlay text contributing differences that are not renderer differences.
            mc.gameSettings.hideGUI = true;
        }
        if (worldTicks < 200) return;
        try {
            if (!Boolean.getBoolean("celtium.validation.interop")) {
                // The frame that was just drawn may still contain an entity the client tick did not see. Drop it and
                // capture on the next frame, so the saved scene contains terrain and the sky only.
                if (removeNonPlayerEntities(mc) > 0) return;
                report.addProperty("nonPlayerEntitiesRemoved", entitiesRemoved);
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
                if (!"opengl".equals(capabilities.get("effectiveTerrainBackend").getAsString())
                        || capabilities.get("nativeTerrainImplemented").getAsBoolean()
                        || capabilities.get("depthRoundtripVerified").getAsBoolean()) {
                    throw new IllegalStateException("Capability command changed or misreported the backend");
                }
                report.add("capabilities", capabilities);
            }
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
            if (!interopRun) ScreenShotHelper.saveScreenshot(mc.gameDir, "celtium-validation.png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
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
