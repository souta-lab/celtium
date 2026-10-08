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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Explicitly installed validation fixture; never embedded in the renderer mod. */
@Mod(modid = "celtium_validation", name = "Celtium validation fixture", version = "0.1.0", clientSideOnly = true)
public final class ValidationClient {
    private final JsonObject report = new JsonObject();
    private final List<Double> frameIntervals = new ArrayList<Double>();
    private boolean enabled;
    private boolean launched;
    private boolean commandsRun;
    private boolean finished;
    private int worldTicks;
    private int worldFrames;
    private long previousFrame;

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
        if (worldTicks < 200) return;
        try {
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
            ScreenShotHelper.saveScreenshot(mc.gameDir, "celtium-validation.png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
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
